package com.suiyi.veilborne.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.suiyi.veilborne.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;

/**
 * The land around the camera, meshed again so it can be drawn reflected: upside down in the sky mirror, or behind
 * the glass of a standing mirror. Near chunks keep their real block models and textures (only the faces that can
 * be seen from above, plus undersides in the gallery). Farther chunks become coarse columns in map colours; the
 * sky mirror's haze blends the two. A chunk that changes is rebuilt only after a while, so the reflection keeps the
 * old state for a few seconds: the late reflection.
 *
 * Mirror-only additions (the extra tower, the extra door) are meshed into the chunks that hold them, so they are
 * drawn by the same passes and never exist in the real world.
 */
final class MirrorWorld {
    static ShaderInstance shader,clipShader;
    /** Surface kinds, packed into the vertex normal's z (see mirror_world.fsh). */
    static final int FACE=0,WATER=2,FALL=3,LAMP=4,PLAIN=5,PLAIN_LIGHT=6,EXTRA=7,EXTRA_LIGHT=8;
    private static final long LATE_MS=9000;
    static final class Mesh {
        final int cx,cz;final boolean far;
        VertexBuffer solid,clear;
        int minY=Integer.MAX_VALUE,maxY=Integer.MIN_VALUE;
        long built;boolean extras;
        Mesh(int cx,int cz,boolean far){this.cx=cx;this.cz=cz;this.far=far;}
        void close(){if(solid!=null)solid.close();if(clear!=null)clear.close();solid=clear=null;}
    }
    private static final Map<Long,Mesh> meshes=new HashMap<>();
    private static final Map<Long,Long> dirty=new HashMap<>();
    private static Level level;
    private static long lastUse;
    private static RenderTarget target;
    private static final ByteBufferBuilder SOLID=new ByteBufferBuilder(1<<20),CLEAR=new ByteBufferBuilder(1<<18);
    private static final RandomSource RANDOM=RandomSource.create();
    /** Mirror-only blocks: world position to state. Rebuilt chunks pick them up. */
    private static final Map<Long,BlockState> extras=new HashMap<>();
    private static final Map<Long,Integer> extraKinds=new HashMap<>();
    private static long extrasKey=Long.MIN_VALUE;
    static int built,pending,unlit;

    static long key(int cx,int cz){return ((long)cx<<32)^(cz&0xffffffffL);}
    /** Mixin hook (LevelRenderer.blockChanged): the reflection will catch up later. */
    static void changed(BlockPos pos){
        long k=key(pos.getX()>>4,pos.getZ()>>4);
        if(meshes.containsKey(k))dirty.putIfAbsent(k,System.currentTimeMillis());
    }
    static void clear(){
        for(Mesh m:meshes.values())m.close();
        meshes.clear();dirty.clear();extras.clear();extraKinds.clear();extrasKey=Long.MIN_VALUE;
    }
    /** Drops the meshes and the target too. Not from inside a frame: the sky may be holding the target. */
    static void release(){
        clear();
        if(target!=null){target.destroyBuffers();target=null;}
    }
    static int nearRadius(){return Math.min(Minecraft.getInstance().options.getEffectiveRenderDistance(),new int[]{3,5,6}[TideConfig.QUALITY.get()]);}
    static int farRadius(){return Math.min(Minecraft.getInstance().options.getEffectiveRenderDistance(),new int[]{7,10,12}[TideConfig.QUALITY.get()]);}
    static double builtRadius(){return farRadius()*16-8;}

    // ------------------------------------------------------------------ mirror-only additions
    /** Places the mirror's additions for this event: the extra door and a watchtower that is not really there. */
    private static int retry;
    static void extras(MirrorPacket st){
        long k=st==null?0:st.seed()^st.door().asLong()*31^st.doorFace()^(st.has(MirrorData.FREESTANDING)?7:0);
        // The tower waits for its chunk to arrive; look again now and then.
        if(k==extrasKey&&(MirrorExtras.towerPlaced||st==null||!st.active()||++retry%90!=0))return;
        Set<Long> touched=new HashSet<>();
        for(long pos:extras.keySet())touched.add(key(BlockPos.getX(pos)>>4,BlockPos.getZ(pos)>>4));
        extras.clear();extraKinds.clear();extrasKey=k;
        if(st!=null&&st.active()){
            MirrorExtras.door(st,extras,extraKinds);
            MirrorExtras.tower(st,level,extras,extraKinds);
        }
        for(long pos:extras.keySet())touched.add(key(BlockPos.getX(pos)>>4,BlockPos.getZ(pos)>>4));
        for(long c:touched){Mesh m=meshes.remove(c);if(m!=null)m.close();}
    }

    // ------------------------------------------------------------------ building
    /**
     * Builds what is missing around {@code camera} within the time budget and drops what has fallen out of range.
     * {@code gallery}: mesh every block in the gallery's height band, undersides included.
     */
    static void update(Vec3 camera,long budgetNanos,boolean gallery){
        var mc=Minecraft.getInstance();
        if(mc.level!=level){clear();level=mc.level;}
        if(level==null)return;
        lastUse=System.currentTimeMillis();
        int ccx=Mth.floor(camera.x)>>4,ccz=Mth.floor(camera.z)>>4;
        int near=gallery?3:nearRadius(),far=gallery?3:farRadius();
        meshes.values().removeIf(m->{
            boolean drop=Math.max(Math.abs(m.cx-ccx),Math.abs(m.cz-ccz))>far+1||m.far!=(Math.max(Math.abs(m.cx-ccx),Math.abs(m.cz-ccz))>near)&&Math.max(Math.abs(m.cx-ccx),Math.abs(m.cz-ccz))<=far;
            if(drop)m.close();return drop;
        });
        long now=System.currentTimeMillis();
        List<long[]> wanted=new ArrayList<>();
        for(int dx=-far;dx<=far;dx++)for(int dz=-far;dz<=far;dz++){
            int cx=ccx+dx,cz=ccz+dz;long k=key(cx,cz);
            Mesh m=meshes.get(k);
            Long changed=dirty.get(k);
            boolean stale=changed!=null&&now-changed>LATE_MS;
            if(m!=null&&!stale)continue;
            if(level.getChunkSource().getChunk(cx,cz,false)==null)continue;
            if(Math.max(Math.abs(dx),Math.abs(dz))<=near&&!lit(cx,cz)){unlit++;continue;}
            wanted.add(new long[]{k,dx*dx+dz*dz,cx,cz});
        }
        wanted.sort(Comparator.comparingLong(a->a[1]));
        pending=wanted.size();
        long start=System.nanoTime();
        for(long[] w:wanted){
            if(System.nanoTime()-start>budgetNanos)break;
            int cx=(int)w[2],cz=(int)w[3];
            boolean isFar=Math.max(Math.abs(cx-ccx),Math.abs(cz-ccz))>near;
            Mesh old=meshes.remove(w[0]);if(old!=null)old.close();
            dirty.remove(w[0]);
            Mesh m=isFar?buildFar(cx,cz):buildNear(cx,cz,gallery);
            if(m!=null){meshes.put(w[0],m);built++;}
            pending--;
        }
    }
    /**
     * A chunk's light arrives a few frames after its blocks (vanilla holds back its own section builds the same way).
     * Meshed before then every face is unlit, and since a mesh is only rebuilt when a block in it changes, that part of
     * the reflection stayed black: the gallery wall went dark in patches. Wait for the chunk's light, and for that of
     * the neighbours its edge faces read.
     */
    private static boolean lit(int cx,int cz){
        var light=level.getLightEngine();
        if(!light.lightOnInSection(SectionPos.of(cx,0,cz)))return false;
        for(Direction d:Direction.Plane.HORIZONTAL){
            int nx=cx+d.getStepX(),nz=cz+d.getStepZ();
            if(level.getChunkSource().getChunk(nx,nz,false)!=null&&!light.lightOnInSection(SectionPos.of(nx,0,nz)))return false;
        }
        return true;
    }
    /** Release everything after the mirror has not been needed for a while. */
    static void idle(){if((!meshes.isEmpty()||target!=null)&&System.currentTimeMillis()-lastUse>30000)release();}

    private static final Direction[] SKY_FACES={Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};
    private static final Direction[] ALL_FACES=Direction.values();
    private static Mesh buildNear(int cx,int cz,boolean gallery){
        LevelChunk chunk=level.getChunkSource().getChunk(cx,cz,false);
        if(chunk==null)return null;
        Mesh mesh=new Mesh(cx,cz,false);
        BufferBuilder solid=new BufferBuilder(SOLID,VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        BufferBuilder clear=new BufferBuilder(CLEAR,VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        int x0=cx*16,z0=cz*16;
        int[][] ground=new int[18][18];
        for(int i=0;i<18;i++)for(int j=0;j<18;j++)ground[i][j]=groundTop(x0+i-1,z0+j-1);
        var dispatcher=Minecraft.getInstance().getBlockRenderer();
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        Direction[] faces=gallery?ALL_FACES:SKY_FACES;
        for(int lx=0;lx<16;lx++)for(int lz=0;lz<16;lz++){
            int x=x0+lx,z=z0+lz;
            int top,low;
            if(gallery){low=MirrorGallery.Y-3;top=MirrorGallery.Y+7;}
            else{
                top=chunk.getHeight(Heightmap.Types.WORLD_SURFACE,lx,lz)+1;
                low=Math.min(ground[lx+1][lz+1],Math.min(Math.min(ground[lx][lz+1],ground[lx+2][lz+1]),Math.min(ground[lx+1][lz],ground[lx+1][lz+2])))-2;
                low=Math.max(low,top-40);
            }
            for(int y=low;y<top;y++){
                pos.set(x,y,z);
                long wp=pos.asLong();
                BlockState extra=extras.get(wp);
                BlockState state=extra!=null?extra:chunk.getBlockState(pos);
                if(state.isAir())continue;
                int kindBase=extra!=null?extraKinds.getOrDefault(wp,EXTRA):-1;
                if(extra!=null)mesh.extras=true;
                FluidState fluid=state.getFluidState();
                if(!fluid.isEmpty())fluid(clear,pos,state,fluid,x0,z0,mesh);
                if(state.getRenderShape()!=RenderShape.MODEL)continue;
                var model=dispatcher.getBlockModel(state);
                long seed=state.getSeed(pos);
                boolean translucent=ItemBlockRenderTypes.getChunkRenderType(state)==RenderType.translucent();
                BufferBuilder out=translucent?clear:solid;
                int emit=state.getLightEmission(level,pos);
                for(Direction d:faces){
                    BlockPos np=pos.relative(d);
                    BlockState neighbour=extras.getOrDefault(np.asLong(),null);
                    if(neighbour==null){
                        if(!Block.shouldRenderFace(state,level,pos,d,np))continue;
                    }else if(neighbour.isSolidRender(level,np)&&state.isSolidRender(level,pos))continue;
                    RANDOM.setSeed(seed);
                    List<BakedQuad> quads=model.getQuads(state,d,RANDOM,ModelData.EMPTY,null);
                    if(!quads.isEmpty())emitQuads(out,quads,state,pos,x0,z0,kindBase,emit,np,mesh);
                }
                RANDOM.setSeed(seed);
                List<BakedQuad> loose=model.getQuads(state,null,RANDOM,ModelData.EMPTY,null);
                if(!loose.isEmpty())emitQuads(out,loose,state,pos,x0,z0,kindBase,emit,pos.above(),mesh);
            }
        }
        mesh.solid=upload(solid);mesh.clear=upload(clear);
        mesh.built=System.currentTimeMillis();
        if(mesh.solid==null&&mesh.clear==null){mesh.minY=0;mesh.maxY=0;}
        return mesh;
    }
    /** The ground beneath leaves and plants: where tree trunks meet the soil. */
    private static int groundTop(int x,int z){
        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING,x,z);
        BlockPos.MutableBlockPos p=new BlockPos.MutableBlockPos(x,y-1,z);
        int min=Math.max(level.getMinBuildHeight(),y-24);
        while(p.getY()>min){
            BlockState s=level.getBlockState(p);
            if((s.blocksMotion()||!s.getFluidState().isEmpty())&&!(s.getBlock() instanceof LeavesBlock)&&!s.is(BlockTags.LOGS))break;
            p.move(0,-1,0);
        }
        return p.getY()+1;
    }
    private static final float[] SHADE={.5f,1f,.8f,.8f,.6f,.6f};
    private static void emitQuads(BufferBuilder out,List<BakedQuad> quads,BlockState state,BlockPos pos,int x0,int z0,int kindBase,int emit,BlockPos lightAt,Mesh mesh){
        int sky=level.getBrightness(LightLayer.SKY,lightAt),block=Math.max(level.getBrightness(LightLayer.BLOCK,lightAt),emit);
        int tintCache=-2,tintIndex=-99;
        for(BakedQuad q:quads){
            int[] v=q.getVertices();
            float r=1,g=1,b=1;
            if(q.isTinted()){
                if(q.getTintIndex()!=tintIndex){tintIndex=q.getTintIndex();tintCache=Minecraft.getInstance().getBlockColors().getColor(state,level,pos,tintIndex);}
                if(tintCache!=-1){r=(tintCache>>16&255)/255f;g=(tintCache>>8&255)/255f;b=(tintCache&255)/255f;}
            }
            float shade=q.isShade()?SHADE[q.getDirection().get3DDataValue()]:1;
            int kind=kindBase>=0?kindBase:emit>=10?LAMP:FACE;
            float nx=block/15f,ny=sky/15f,nz=kind/127f;
            int stride=v.length/4;
            for(int i=0;i<4;i++){
                int o=i*stride;
                float x=Float.intBitsToFloat(v[o])+pos.getX()-x0,y=Float.intBitsToFloat(v[o+1])+pos.getY(),z=Float.intBitsToFloat(v[o+2])+pos.getZ()-z0;
                out.addVertex(x,y,z).setUv(Float.intBitsToFloat(v[o+4]),Float.intBitsToFloat(v[o+5])).setColor(r*shade,g*shade,b*shade,1f).setNormal(nx,ny,nz);
                if(y<mesh.minY)mesh.minY=(int)Math.floor(y);if(y>mesh.maxY)mesh.maxY=(int)Math.ceil(y);
            }
        }
    }
    private static void fluid(BufferBuilder out,BlockPos pos,BlockState state,FluidState fluid,int x0,int z0,Mesh mesh){
        var ext=IClientFluidTypeExtensions.of(fluid);
        var atlas=Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite still=atlas.getSprite(ext.getStillTexture(fluid,level,pos)),flow=atlas.getSprite(ext.getFlowingTexture(fluid,level,pos));
        int tint=ext.getTintColor(fluid,level,pos);
        if(fluid.is(net.minecraft.tags.FluidTags.WATER))tint=BiomeColors.getAverageWaterColor(level,pos)|0xff000000;
        float r=(tint>>16&255)/255f,g=(tint>>8&255)/255f,b=(tint&255)/255f;
        boolean lava=fluid.is(net.minecraft.tags.FluidTags.LAVA);
        int sky=level.getBrightness(LightLayer.SKY,pos.above()),block=lava?15:level.getBrightness(LightLayer.BLOCK,pos.above());
        float h=fluid.getHeight(level,pos);
        float lx=pos.getX()-x0,ly=pos.getY(),lz=pos.getZ()-z0;
        mesh.minY=Math.min(mesh.minY,pos.getY());mesh.maxY=Math.max(mesh.maxY,pos.getY()+1);
        if(!level.getFluidState(pos.above()).getType().isSame(fluid.getType())){
            float nz=(lava?LAMP:WATER)/127f;
            quad(out,lx,ly+h,lz,lx+1,ly+h,lz,lx+1,ly+h,lz+1,lx,ly+h,lz+1,still.getU0(),still.getV0(),still.getU1(),still.getV1(),r,g,b,block/15f,sky/15f,nz);
        }
        // Falling water on a cliff side: its texture runs upward in the reflection, the wrong way.
        for(Direction d:Direction.Plane.HORIZONTAL){
            BlockPos np=pos.relative(d);
            if(level.getFluidState(np).getType().isSame(fluid.getType())||level.getBlockState(np).isSolidRender(level,np))continue;
            float u0=flow.getU0(),u1=flow.getU(.5f),v0=flow.getV0(),v1=flow.getV(.5f);
            float ax=lx+(d==Direction.EAST?1:0),az=lz+(d==Direction.SOUTH?1:0);
            float bx=ax+(d.getAxis()==Direction.Axis.Z?1:0),bz=az+(d.getAxis()==Direction.Axis.X?1:0);
            float nz=(lava?LAMP:FALL)/127f;
            quad(out,ax,ly,az,bx,ly,bz,bx,ly+h,bz,ax,ly+h,az,u0,v1,u1,v0,r*.8f,g*.8f,b*.8f,block/15f,sky/15f,nz);
        }
    }
    private static void quad(BufferBuilder out,float x1,float y1,float z1,float x2,float y2,float z2,float x3,float y3,float z3,float x4,float y4,float z4,
                             float u0,float v0,float u1,float v1,float r,float g,float b,float nx,float ny,float nz){
        out.addVertex(x1,y1,z1).setUv(u0,v0).setColor(r,g,b,1f).setNormal(nx,ny,nz);
        out.addVertex(x2,y2,z2).setUv(u1,v0).setColor(r,g,b,1f).setNormal(nx,ny,nz);
        out.addVertex(x3,y3,z3).setUv(u1,v1).setColor(r,g,b,1f).setNormal(nx,ny,nz);
        out.addVertex(x4,y4,z4).setUv(u0,v1).setColor(r,g,b,1f).setNormal(nx,ny,nz);
    }
    /** Far land: 2x2 columns in map colours, each a flat top with skirts down to its neighbours. */
    private static Mesh buildFar(int cx,int cz){
        LevelChunk chunk=level.getChunkSource().getChunk(cx,cz,false);
        if(chunk==null)return null;
        Mesh mesh=new Mesh(cx,cz,true);
        BufferBuilder b=new BufferBuilder(SOLID,VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        int x0=cx*16,z0=cz*16;
        int[][] h=new int[10][10];int[][] col=new int[10][10];
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        for(int i=0;i<10;i++)for(int j=0;j<10;j++){
            int best=Integer.MIN_VALUE,color=0x7a8a6a;
            for(int a=0;a<2;a++)for(int c=0;c<2;c++){
                int x=x0+(i-1)*2+a,z=z0+(j-1)*2+c;
                int y=level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z);
                if(y>best){best=y;if(i>0&&i<9&&j>0&&j<9)color=farColour(pos.set(x,y-1,z));}
            }
            h[i][j]=best;col[i][j]=color;
        }
        int sky=15;
        for(int i=1;i<9;i++)for(int j=1;j<9;j++){
            float lx=(i-1)*2,lz=(j-1)*2,y=h[i][j];
            int c=col[i][j];
            float r=(c>>16&255)/255f,g=(c>>8&255)/255f,bl=(c&255)/255f;
            int kind=(c>>>24)==1?LAMP:PLAIN;
            float nz=(kind==LAMP?PLAIN_LIGHT:PLAIN)/127f;
            quad(b,lx,y,lz,lx+2,y,lz,lx+2,y,lz+2,lx,y,lz+2,0,0,1,1,r,g,bl,0,1,nz);
            mesh.minY=Math.min(mesh.minY,(int)y-8);mesh.maxY=Math.max(mesh.maxY,(int)y);
            int[][] n={{i,j-1},{i,j+1},{i-1,j},{i+1,j}};
            for(int k=0;k<4;k++){
                int ny=h[n[k][0]][n[k][1]];
                if(ny>=y)continue;
                float lo=Math.max(ny,y-24),sr=r*.72f,sg=g*.72f,sb=bl*.72f;
                switch(k){
                    case 0 -> quad(b,lx,lo,lz,lx+2,lo,lz,lx+2,y,lz,lx,y,lz,0,0,1,1,sr,sg,sb,0,1,nz);
                    case 1 -> quad(b,lx,lo,lz+2,lx+2,lo,lz+2,lx+2,y,lz+2,lx,y,lz+2,0,0,1,1,sr,sg,sb,0,1,nz);
                    case 2 -> quad(b,lx,lo,lz,lx,lo,lz+2,lx,y,lz+2,lx,y,lz,0,0,1,1,sr*.9f,sg*.9f,sb*.9f,0,1,nz);
                    default -> quad(b,lx+2,lo,lz,lx+2,lo,lz+2,lx+2,y,lz+2,lx+2,y,lz,0,0,1,1,sr*.9f,sg*.9f,sb*.9f,0,1,nz);
                }
                mesh.minY=Math.min(mesh.minY,(int)lo);
            }
        }
        mesh.solid=upload(b);mesh.built=System.currentTimeMillis();
        return mesh;
    }
    private static int farColour(BlockPos pos){
        BlockState s=level.getBlockState(pos);
        if(!s.getFluidState().isEmpty()){
            if(s.getFluidState().is(net.minecraft.tags.FluidTags.LAVA))return 0x01ff8a30;
            return BiomeColors.getAverageWaterColor(level,pos)&0xffffff;
        }
        if(s.getLightEmission(level,pos)>=12)return 0x01ffd89a;
        int tint=Minecraft.getInstance().getBlockColors().getColor(s,level,pos,0);
        if(tint!=-1&&(s.is(BlockTags.LEAVES)||s.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)||s.is(net.minecraft.world.level.block.Blocks.SHORT_GRASS)||s.is(net.minecraft.world.level.block.Blocks.VINE)))
            return scale(tint,s.is(BlockTags.LEAVES)?.78:.92);
        return s.getMapColor(level,pos).col;
    }
    private static int scale(int c,double f){return (int)Math.min(255,(c>>16&255)*f)<<16|(int)Math.min(255,(c>>8&255)*f)<<8|(int)Math.min(255,(c&255)*f);}
    private static VertexBuffer upload(BufferBuilder b){
        MeshData data=b.build();
        if(data==null)return null;
        VertexBuffer vb=new VertexBuffer(VertexBuffer.Usage.STATIC);
        vb.bind();vb.upload(data);VertexBuffer.unbind();
        return vb;
    }

    // ------------------------------------------------------------------ drawing
    /** The sky mirror's own colour and depth target, the size of the screen or a fraction of it. */
    static RenderTarget target(){
        var main=Minecraft.getInstance().getMainRenderTarget();
        double scale=new double[]{.5,.75,1}[TideConfig.QUALITY.get()];
        int w=Math.max(1,(int)(main.width*scale)),h=Math.max(1,(int)(main.height*scale));
        if(target==null){target=new TextureTarget(w,h,true,Minecraft.ON_OSX);target.setClearColor(0,0,0,0);target.setFilterMode(org.lwjgl.opengl.GL11.GL_LINEAR);}
        else if(target.width!=w||target.height!=h)target.resize(w,h,Minecraft.ON_OSX);
        return target;
    }
    record Draw(Matrix4f modelView,Matrix4f projection,Matrix4f mirror,float scale,Vec3 camera,float[] fog,float time,float day,float warm,float radius){}
    /**
     * Draws the built chunks through {@code program} (the sky's own shader, or the clipped one for standing mirrors).
     * {@code clearPass}: water and glass, blended over what is already there. {@code nearOnly} with {@code around}
     * and {@code reach}: only chunks near a standing mirror.
     */
    static void draw(ShaderInstance program,Draw d,boolean clearPass,boolean nearOnly,Vec3 around,double reach){
        if(program==null||meshes.isEmpty())return;
        RenderSystem.setShader(()->program);
        RenderSystem.setShaderTexture(0,TextureAtlas.LOCATION_BLOCKS);
        for(int i=0;i<12;i++)program.setSampler("Sampler"+i,RenderSystem.getShaderTexture(i));
        if(program.MODEL_VIEW_MATRIX!=null)program.MODEL_VIEW_MATRIX.set(d.modelView());
        if(program.PROJECTION_MATRIX!=null)program.PROJECTION_MATRIX.set(d.projection());
        if(program.COLOR_MODULATOR!=null)program.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        program.safeGetUniform("MirrorMat").set(d.mirror());
        program.safeGetUniform("MirrorScale").set(d.scale());
        program.safeGetUniform("MirrorOrigin").set((float)(d.camera().x%1024),(float)(d.camera().y%1024),(float)(d.camera().z%1024));
        program.safeGetUniform("MirrorFog").set(d.fog()[0],d.fog()[1],d.fog()[2],d.fog()[3]);
        program.safeGetUniform("MirrorLook").set(d.time(),d.day(),d.warm(),d.radius());
        program.apply();
        var offset=program.getUniform("ChunkOffset");
        Matrix4f mvp=new Matrix4f(d.projection()).mul(d.modelView()).mul(new Matrix4f().scale(d.scale())).mul(d.mirror());
        FrustumIntersection frustum=new FrustumIntersection(mvp);
        for(Mesh m:meshes.values()){
            VertexBuffer vb=clearPass?m.clear:m.solid;
            if(vb==null)continue;
            if(nearOnly&&(m.far||Math.hypot(m.cx*16+8-around.x,m.cz*16+8-around.z)>reach+12))continue;
            float ox=(float)(m.cx*16-d.camera().x),oy=(float)(-d.camera().y),oz=(float)(m.cz*16-d.camera().z);
            if(!frustum.testAab(ox,oy+m.minY,oz,ox+16,oy+m.maxY+1,oz+16))continue;
            if(offset!=null){offset.set(ox,oy,oz);offset.upload();}
            vb.bind();vb.draw();
        }
        if(offset!=null){offset.set(0f,0f,0f);}
        VertexBuffer.unbind();
        program.clear();
    }
    /** One buffer whose positions are already relative to {@code d.mirror()}'s origin (the pilgrim). */
    static void drawBuffer(ShaderInstance program,Draw d,VertexBuffer vb){
        if(program==null||vb==null)return;
        RenderSystem.setShader(()->program);
        RenderSystem.setShaderTexture(0,TextureAtlas.LOCATION_BLOCKS);
        for(int i=0;i<12;i++)program.setSampler("Sampler"+i,RenderSystem.getShaderTexture(i));
        if(program.MODEL_VIEW_MATRIX!=null)program.MODEL_VIEW_MATRIX.set(d.modelView());
        if(program.PROJECTION_MATRIX!=null)program.PROJECTION_MATRIX.set(d.projection());
        if(program.COLOR_MODULATOR!=null)program.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        program.safeGetUniform("MirrorMat").set(d.mirror());
        program.safeGetUniform("MirrorScale").set(d.scale());
        program.safeGetUniform("MirrorOrigin").set((float)(d.camera().x%1024),(float)(d.camera().y%1024),(float)(d.camera().z%1024));
        program.safeGetUniform("MirrorFog").set(d.fog()[0],d.fog()[1],d.fog()[2],d.fog()[3]);
        program.safeGetUniform("MirrorLook").set(d.time(),d.day(),d.warm(),d.radius());
        program.safeGetUniform("ChunkOffset").set(0f,0f,0f);
        program.apply();
        vb.bind();vb.draw();VertexBuffer.unbind();
        program.clear();
    }
    /** Affine reflection across the horizontal plane y = h, for camera-relative positions. */
    static Matrix4f skyReflection(double h,Vec3 camera){
        return new Matrix4f().translate(0,(float)(2*(h-camera.y)),0).scale(1,-1,1);
    }
    /** Affine reflection across a vertical plane through {@code point} (camera-relative) with unit normal {@code n}. */
    static Matrix4f planeReflection(Vec3 point,Vec3 n){
        float nx=(float)n.x,ny=(float)n.y,nz=(float)n.z,d=(float)(2*point.dot(n));
        return new Matrix4f(1-2*nx*nx,-2*ny*nx,-2*nz*nx,0, -2*nx*ny,1-2*ny*ny,-2*nz*ny,0, -2*nx*nz,-2*ny*nz,1-2*nz*nz,0, d*nx,d*ny,d*nz,1);
    }
    static int meshCount(){return meshes.size();}
    private MirrorWorld(){}
}
