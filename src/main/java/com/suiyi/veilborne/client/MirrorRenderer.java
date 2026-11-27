package com.suiyi.veilborne.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.suiyi.veilborne.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * 镜天巡礼 on screen. Sky: the reflected land and the pilgrim are drawn into the mirror's own target, then one pass lays
 * the uneven silver mirror over the sky (mirror_sky.fsh); facets that fall out tumble down as light. World: faint
 * afterimages. Air and ground: shards of mirror turning overhead, the glass beasts' low shards and their warning,
 * the standing mirrors and the gallery wall (MirrorGlass), and everything on the ground (MirrorGround).
 */
public final class MirrorRenderer {
    static ShaderInstance skyShader,paneShader,ghostShader;
    static final ResourceLocation HORIZON=Veilborne.id("textures/environment/mirror_horizon.png"),PANE=Veilborne.id("textures/environment/mirror_pane.png"),
        SHARD=Veilborne.id("textures/environment/mirror_shard.png"),GLOW=Veilborne.id("textures/environment/glow.png"),STAR=Veilborne.id("textures/environment/star.png"),
        WARN=Veilborne.id("textures/environment/mirror_warn.png");
    private static RenderTarget scene;
    private static VertexBuffer pilgrimBuffer;
    private static int pilgrimBuiltAt=-1;
    private static final ByteBufferBuilder PILGRIM=new ByteBufferBuilder(1<<20);
    static float lastScale=1;

    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        var stage=e.getStage();
        float partial=e.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera=e.getCamera().getPosition();
        boolean gallery=MirrorClient.inGallery();
        double time=(MirrorClient.ticks+partial)/20.0;
        if(stage==RenderLevelStageEvent.Stage.AFTER_SKY){
            if(gallery){MirrorWorld.update(camera,3_000_000L,true);return;}
            if(MirrorClient.active()&&TideConfig.SKY.get())sky(e,camera,partial,time);
        }else if(stage==RenderLevelStageEvent.Stage.AFTER_ENTITIES){
            if(gallery)return;
            if(MirrorClient.active())afterimages(camera,partial);
            MirrorGround.echoGhosts(camera,partial,time);
        }else if(stage==RenderLevelStageEvent.Stage.AFTER_PARTICLES){
            Matrix4f view=new Matrix4f(e.getModelViewMatrix()),proj=new Matrix4f(e.getProjectionMatrix());
            Quaternionf facing=e.getCamera().rotation();
            RenderSystem.enableBlend();RenderSystem.disableCull();RenderSystem.depthMask(false);RenderSystem.enableDepthTest();
            if(gallery){
                MirrorGlass.gallery(view,proj,camera,partial,time);
            }else{
                if(MirrorClient.active()){
                    double p=MirrorClient.progress(partial),s=MirrorClient.strength();
                    boolean frames=!Minecraft.getInstance().level.getEntities(Veilborne.FRAMES.get(),e.getCamera().getEntity().getBoundingBox().inflate(48),x->true).isEmpty();
                    boolean panes=MirrorTimeline.panes(p)*s>.01&&TideConfig.SKY.get(),shards=MirrorClient.state.has(MirrorData.SHARDS);
                    if(panes||shards)copyScene();
                    if(panes)panes(view,proj,camera,p,s,time);
                    if(shards)shards(view,proj,camera,facing,p,s,time,partial);
                    if(frames)MirrorGlass.frames(view,proj,camera,partial,time);
                    MirrorGround.render(camera,facing,p,s,time,partial);
                }
                MirrorGround.always(camera,facing,time,partial);
            }
            RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();
        }
    }

    // ------------------------------------------------------------------ the sky
    private static void sky(RenderLevelStageEvent e,Vec3 camera,float partial,double time){
        var st=MirrorClient.state;
        double p=MirrorClient.progress(partial),s=MirrorClient.strength(),day=MirrorClient.dayness(partial);
        double presence=MirrorTimeline.mirror(p)*s;
        if(presence<.002||skyShader==null||Double.isNaN(MirrorClient.mirrorH))return;
        var mc=Minecraft.getInstance();
        Matrix4f view=new Matrix4f(e.getModelViewMatrix()),proj=new Matrix4f(e.getProjectionMatrix());
        MirrorWorld.extras(st);
        boolean land=TideConfig.MIRROR_REFLECTIONS.get()&&MirrorTimeline.reflection(p)>.005&&MirrorWorld.shader!=null;
        RenderTarget target=MirrorWorld.target();
        target.clear(Minecraft.ON_OSX);
        double h=MirrorClient.mirrorH;
        if(land){
            MirrorWorld.update(camera,new long[]{1_200_000L,2_200_000L,3_500_000L}[TideConfig.QUALITY.get()],false);
            double vertical=2*(h-MirrorClient.baseGround)+MirrorTimeline.HEIGHT+20,reach=Math.sqrt(vertical*vertical+Math.pow(MirrorWorld.builtRadius()+40,2));
            float k=(float)Math.min(1,mc.gameRenderer.getDepthFar()*.92/reach);lastScale=k;
            target.bindWrite(true);
            RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.disableBlend();RenderSystem.disableCull();RenderSystem.depthFunc(GL11.GL_LEQUAL);
            float[] fog=haze(day,p);
            MirrorWorld.Draw d=new MirrorWorld.Draw(view,proj,MirrorWorld.skyReflection(h,camera),k,camera,fog,(float)(time%3600),(float)day,0,(float)MirrorWorld.builtRadius());
            MirrorWorld.draw(MirrorWorld.shader,d,false,false,null,0);
            pilgrim(d,camera,partial,time);
            RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.depthMask(false);
            MirrorWorld.draw(MirrorWorld.shader,d,true,false,null,0);
            RenderSystem.depthMask(true);RenderSystem.disableBlend();
        }
        // Clearing the target leaves the default framebuffer bound; draw on into the level's own again.
        mc.getMainRenderTarget().bindWrite(true);
        // The mirror over the sky.
        Matrix4f projView=new Matrix4f(proj).mul(view),inv=new Matrix4f(projView).invert();
        skyShader.safeGetUniform("InvProjView").set(inv);
        skyShader.safeGetUniform("ProjView").set(projView);
        skyShader.safeGetUniform("MirrorState").set((float)MirrorTimeline.silver(p),(float)(MirrorTimeline.reflection(p)*(land?1:.85)),(float)MirrorTimeline.crack(p),(float)MirrorTimeline.shatter(p));
        skyShader.safeGetUniform("MirrorLook").set((float)(time%3600),(float)day,(float)(.82+.18*TideConfig.BRIGHTNESS.get()),(float)(Math.floorMod(st.seed(),997L)/997.0));
        for(int i=0;i<4;i++){
            Vector4f r=new Vector4f(0,1,0,-1);
            if(i<MirrorClient.ripples.size()){
                var rp=MirrorClient.ripples.get(MirrorClient.ripples.size()-1-i);
                Vec3 dir=MirrorClient.reflect(rp.at(),camera).normalize();
                r.set((float)dir.x,(float)dir.y,(float)dir.z,(MirrorClient.ticks+partial-rp.born())/20f);
            }
            skyShader.safeGetUniform("Ripple"+i).set(r.x,r.y,r.z,r.w);
        }
        Vec3 door=st.hasDoor()?MirrorClient.reflect(MirrorTimeline.doorCenter(st.door(),st.doorFace(),st.has(MirrorData.FREESTANDING)),camera).normalize():new Vec3(0,1,0);
        skyShader.safeGetUniform("Glow0").set((float)door.x,(float)door.y,(float)door.z,(float)(MirrorTimeline.door(p)*(st.has(MirrorData.REVEALED)?1.2:.8)*MirrorTimeline.reflection(p)));
        MirrorClient.Pilgrim g=MirrorClient.pilgrim(partial);
        Vec3 glow=new Vec3(0,1,0);float glowStrength=0;
        if(g!=null){
            double[] l=g.gaze()>.5?PilgrimModel.mirrorCenter(g.s(),g.gaze()):PilgrimModel.lantern(g.s(),g.gaze(),time);
            glow=MirrorClient.reflect(pilgrimPoint(g,l),camera).normalize();
            glowStrength=(float)(g.visible()*(.7+g.gaze()*1.3*(.85+.15*Math.sin(time*3))));
        }
        skyShader.safeGetUniform("Glow1").set((float)glow.x,(float)glow.y,(float)glow.z,glowStrength);
        double a1=Math.floorMod(st.seed()>>>21,360L)*Math.PI/180,a2=a1+2.4;
        skyShader.safeGetUniform("CrackFrom").set((float)(Math.cos(a1)*.55),.83f,(float)(Math.sin(a1)*.55));
        skyShader.safeGetUniform("CrackFrom2").set((float)(Math.cos(a2)*.8),.6f,(float)(Math.sin(a2)*.8));
        RenderSystem.setShaderTexture(0,target.getColorTextureId());
        RenderSystem.setShaderTexture(1,HORIZON);
        RenderSystem.setShader(()->skyShader);
        RenderSystem.enableBlend();RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1,1,1,(float)Math.min(1,presence));
        BufferBuilder q=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION);
        q.addVertex(-1,-1,0);q.addVertex(1,-1,0);q.addVertex(1,1,0);q.addVertex(-1,1,0);
        BufferUploader.drawWithShader(q.buildOrThrow());
        RenderSystem.setShaderColor(1,1,1,1);
        falling(view,camera,p,s,time);
        RenderSystem.defaultBlendFunc();RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.disableBlend();
    }
    private static float[] haze(double day,double p){
        // Lapis-grey haze at night, silver by day: the colour the painted distance is tinted to, so they meet.
        return new float[]{(float)Mth.lerp(day,.15,.72),(float)Mth.lerp(day,.19,.77),(float)Mth.lerp(day,.29,.82),(float)(1/230.0)};
    }
    /** A point in the pilgrim's own space, moved to the real ground it stands on. */
    static Vec3 pilgrimPoint(MirrorClient.Pilgrim g,double[] local){
        double c=Math.cos(g.yaw()),sn=Math.sin(g.yaw()),k=PilgrimModel.SCALE;
        return g.base().add((local[0]*c-local[2]*sn)*k,local[1]*k,(local[0]*sn+local[2]*c)*k);
    }
    private static void pilgrim(MirrorWorld.Draw d,Vec3 camera,float partial,double time){
        MirrorClient.Pilgrim g=MirrorClient.pilgrim(partial);
        if(g==null||MirrorWorld.shader==null)return;
        if(pilgrimBuiltAt!=MirrorClient.ticks||pilgrimBuffer==null){
            pilgrimBuiltAt=MirrorClient.ticks;
            BufferBuilder b=new BufferBuilder(PILGRIM,VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
            PilgrimModel.write(b,g.s(),g.gaze(),time,TideConfig.QUALITY.get());
            MeshData mesh=b.build();
            if(mesh==null)return;
            if(pilgrimBuffer==null)pilgrimBuffer=new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            pilgrimBuffer.bind();pilgrimBuffer.upload(mesh);VertexBuffer.unbind();
        }
        Vec3 base=g.base().subtract(camera);
        Matrix4f m=new Matrix4f(d.mirror()).translate((float)base.x,(float)base.y,(float)base.z).rotateY((float)-g.yaw()).scale((float)PilgrimModel.SCALE);
        float fade=(float)g.visible();
        RenderSystem.setShaderColor(1,1,1,fade);
        // Hazed like the land at its distance, but less: it is the one thing in the mirror meant to be seen.
        float[] fog=d.fog().clone();fog[3]*=.45f;
        MirrorWorld.drawBuffer(MirrorWorld.shader,new MirrorWorld.Draw(d.modelView(),d.projection(),m,d.scale(),camera,fog,d.time(),d.day(),0,4096),pilgrimBuffer);
        RenderSystem.setShaderColor(1,1,1,1);
    }
    /** Mirrors the sky shader's cells: where facets are, which fall when, so each can be seen to leave. */
    private static final int[][] CELLS;
    static {
        List<int[]> cells=new ArrayList<>();
        for(int x=-4;x<=4;x++)for(int y=-1;y<=4;y++)for(int z=-4;z<=4;z++)cells.add(new int[]{x,y,z});
        CELLS=cells.toArray(new int[0][]);
    }
    static int hash3(int x,int y,int z){
        x=(x+1024)*1664525+1013904223;y=(y+1024)*1664525+1013904223;z=(z+1024)*1664525+1013904223;
        x+=y*z;y+=z*x;z+=x*y;
        x^=x>>>16;y^=y>>>16;z^=z>>>16;
        x+=y*z;y+=z*x;z+=x*y;
        return x^y^z;
    }
    static double h01(int x,int y,int z,int salt){return (hash3(x+salt*7919,y+salt*104729,z+salt*1299709)>>>8)/16777216.0;}
    private static void falling(Matrix4f view,Vec3 camera,double p,double s,double time){
        double shatter=MirrorTimeline.shatter(p);
        if(shatter<.001||shatter>.999)return;
        var st=MirrorClient.state;
        int seed=(int)(Math.floorMod(st.seed(),997L)/997.0*997.0);
        double dur=st.duration()/20.0,rate=1/(dur*(.985-.885));  // shatter units per second, roughly
        TideFx fx=TideFx.begin(SHARD,view);
        TideFx glints=TideFx.begin(STAR,view);
        Quaternionf facing=Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        double R=TideRenderer.DOME*.9;
        for(int[] c:CELLS){
            double sx=c[0]+.15+.7*h01(c[0],c[1],c[2],seed+1),sy=c[1]+.15+.7*h01(c[0],c[1],c[2],seed+2),sz=c[2]+.15+.7*h01(c[0],c[1],c[2],seed+3);
            double len=Math.sqrt(sx*sx+sy*sy+sz*sz);
            if(len<2.4||len>4.0)continue;
            Vec3 dir=new Vec3(sx/len,sy/len,sz/len);
            if(dir.y<.05)continue;
            double order=.62*h01(c[0],c[1],c[2],seed+4)+.38*(1-dir.y);
            double since=(shatter*1.08-order)/(rate*1.08);   // seconds since this facet fell
            if(since<0||since>6)continue;
            double tt=since;
            Vec3 pos=dir.scale(R).add(0,-(3*tt+2.2*tt*tt),0).add(dir.x*tt*2,0,dir.z*tt*2);
            double fade=Mth.clamp(tt*4,0,1)*(1-TideTimeline.smooth(3.5,6,tt));
            double spin=tt*(1.2+h01(c[0],c[1],c[2],seed+5))+c[0];
            fx.billboard(pos,facing,R*.07*(1-.4*tt/6),spin,0,0,1,1,0xffffff,s*fade*.9);
            for(int k=0;k<3;k++)glints.billboard(pos.add(Math.sin(tt*2+k)*R*.03,R*.02*k,Math.cos(tt*1.7+k)*R*.03),facing,R*.012,tt+k,k==0?0xffe6b0:0xe6f6ff,s*fade*.8);
        }
        fx.end(false);
        glints.end(true);
    }

    // ------------------------------------------------------------------ afterimages
    /** Faint likenesses a step and two steps behind anyone moving quickly under the mirror. */
    private static void afterimages(Vec3 camera,float partial){
        if(!TideConfig.MIRROR_AFTERIMAGES.get())return;
        var mc=Minecraft.getInstance();
        double k=MirrorTimeline.echoes(MirrorClient.progress(partial))*MirrorClient.strength();
        if(k<.05)return;
        var buffers=mc.renderBuffers().bufferSource();
        for(Player pl:mc.level.players()){
            var q=MirrorClient.history.get(pl.getId());
            if(q==null||q.size()<20||!(pl instanceof AbstractClientPlayer who))continue;
            if(pl==mc.player&&mc.options.getCameraType().isFirstPerson())continue;
            for(int n=1;n<=2;n++){
                MoonClient.Frame f=MirrorClient.lagged(q,partial,n*7);
                MoonClient.Frame now=MirrorClient.lagged(q,partial,0);
                double moved=Math.hypot(now.x()-f.x(),now.z()-f.z());
                if(moved<.9)continue;
                float a=(float)(k*Math.min(1,(moved-.9)/1.5)*(n==1?.42:.22));
                var pose=new PoseStack();
                pose.translate(f.x()-camera.x,f.y()-camera.y,f.z()-camera.z);
                GhostFigure.renderFrame(who,pose,buffers,a,f,0xcde7f2);
            }
        }
        buffers.endBatch();
    }

    // ------------------------------------------------------------------ glass in the air
    static void copyScene(){
        var main=Minecraft.getInstance().getMainRenderTarget();
        if(scene==null){scene=new TextureTarget(main.width,main.height,false,Minecraft.ON_OSX);scene.setFilterMode(GL11.GL_LINEAR);}
        else if(scene.width!=main.width||scene.height!=main.height)scene.resize(main.width,main.height,Minecraft.ON_OSX);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,scene.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0,0,main.width,main.height,0,0,scene.width,scene.height,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
        main.bindWrite(false);
    }
    static int sceneTexture(){return scene==null?0:scene.getColorTextureId();}
    record Pane(Vec3 center,double yaw,double width,double height,int variant,double alpha,double warm,double crack){}
    /** Upright shards turning slowly in mid-air, one to a patch of sky; through them, the world in warmer light. */
    private static void panes(Matrix4f view,Matrix4f proj,Vec3 camera,double p,double s,double time){
        if(paneShader==null)return;
        for(Pane pn:panesNear(camera,p,s,time))pane(view,proj,camera,pn,time);
        RenderSystem.enableCull();
    }
    /** The shards in the air around the camera, farthest first. */
    static List<Pane> panesNear(Vec3 camera,double p,double s,double time){
        var level=Minecraft.getInstance().level;
        List<Pane> list=new ArrayList<>();
        int cell=52,cx=Mth.floor(camera.x/cell),cz=Mth.floor(camera.z/cell);
        double vis=MirrorTimeline.panes(p)*s;
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){
            int gx=cx+x,gz=cz+z;int h=hash3(gx,7,gz);
            if(((h>>>4)&15)>6)continue;
            double u=(h>>>8&255)/255.0,v=(h>>>16&255)/255.0,w=(h>>>24&255)/255.0;
            double px=gx*cell+u*cell,pz=gz*cell+v*cell;
            double ground=level.hasChunk(Mth.floor(px)>>4,Mth.floor(pz)>>4)?level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,Mth.floor(px),Mth.floor(pz)):MirrorClient.baseGround;
            double py=ground+14+w*26+Math.sin(time*.13+u*9)*1.5;
            Vec3 c=new Vec3(px,py,pz);
            double dist=c.distanceTo(camera);
            double a=vis*TideTimeline.smooth(12,24,dist)*(1-TideTimeline.smooth(95,130,dist));
            if(a<.01)continue;
            double yaw=u*6.28+time*(.03+.04*v)*(((h>>>3)&1)==0?1:-1);
            list.add(new Pane(c,yaw,3.6+w*3.4,8+u*6,h&3,a,.6,MirrorTimeline.crack(p)));
        }
        list.sort(Comparator.comparingDouble(pn->-pn.center().distanceToSqr(camera)));
        return list;
    }
    static void pane(Matrix4f view,Matrix4f proj,Vec3 camera,Pane pn,double time){
        Vec3 rel=pn.center().subtract(camera);
        Vec3 right=new Vec3(Math.cos(pn.yaw()),0,Math.sin(pn.yaw())).scale(pn.width()/2),up=new Vec3(0,pn.height()/2,0);
        Vec3 normal=new Vec3(-Math.sin(pn.yaw()),0,Math.cos(pn.yaw()));
        double facing=Math.abs(normal.dot(rel.normalize()));
        Vector4f clip=new Matrix4f(proj).mul(view).transform(new Vector4f((float)rel.x,(float)rel.y,(float)rel.z,1));
        if(clip.w<=0)return;
        float sx=clip.x/clip.w*.5f+.5f;
        var main=Minecraft.getInstance().getMainRenderTarget();
        paneShader.safeGetUniform("ScreenSize").set((float)main.width,(float)main.height);
        paneShader.safeGetUniform("PaneInfo").set(sx,(float)pn.alpha(),(float)facing,(float)(time%3600));
        paneShader.safeGetUniform("PaneWarm").set((float)pn.warm(),(float)pn.crack(),0f,0f);
        RenderSystem.setShader(()->paneShader);
        RenderSystem.setShaderTexture(0,sceneTexture());
        RenderSystem.setShaderTexture(1,PANE);
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();
        BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        float u0=pn.variant()*.25f,u1=u0+.25f;
        Vec3 a=rel.subtract(right).subtract(up),bb=rel.add(right).subtract(up),c=rel.add(right).add(up),d=rel.subtract(right).add(up);
        b.addVertex((float)a.x,(float)a.y,(float)a.z).setUv(u0,1).setColor(1f,1f,1f,1f);
        b.addVertex((float)bb.x,(float)bb.y,(float)bb.z).setUv(u1,1).setColor(1f,1f,1f,1f);
        b.addVertex((float)c.x,(float)c.y,(float)c.z).setUv(u1,0).setColor(1f,1f,1f,1f);
        b.addVertex((float)d.x,(float)d.y,(float)d.z).setUv(u0,0).setColor(1f,1f,1f,1f);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }
    /** The beasts' low cracked shards, the geckos on them, and the warning ring on the ground. */
    private static void shards(Matrix4f view,Matrix4f proj,Vec3 camera,Quaternionf facing,double p,double s,double time,float partial){
        var st=MirrorClient.state;
        if(p>=.86)return;
        for(int i=0;i<2;i++){
            var pos=st.shard(i);
            if(pos.equals(net.minecraft.core.BlockPos.ZERO)||st.has(i==0?MirrorData.SHARD_BURST_0:MirrorData.SHARD_BURST_1))continue;
            Vec3 base=Vec3.atBottomCenterOf(pos);
            int warn=st.warn(i);
            double warning=warn>0?Mth.clamp((MirrorClient.ageNow(partial)-warn)/MirrorService.SHARD_WARN,0,1):0;
            double shake=warning>0?Math.sin(time*40)*.04*warning:0;
            double yaw=(pos.asLong()%7)*.9+Math.sin(time*.2+i)*.35+shake;
            Pane pn=new Pane(base.add(0,MirrorService.SHARD_LIFT+1.6+Math.sin(time*.6+i)*.08,0),yaw,2.1,3.2,(i+2)&3,s*(.9+.1*Math.sin(time*2)),.35,.4+warning*.6);
            pane(view,proj,camera,pn,time);
            MirrorGround.warning(base,camera,facing,warning,warn>0,time,s);
            BeastRenderer.crawlers(base.add(0,MirrorService.SHARD_LIFT+1.6,0),yaw,camera,time+i*7,warning,s,partial);
        }
    }
    private MirrorRenderer(){}
}
