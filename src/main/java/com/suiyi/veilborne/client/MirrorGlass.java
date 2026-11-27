package com.suiyi.veilborne.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.suiyi.veilborne.*;
import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Standing mirrors that really reflect: the three frames of the misaligned signposts and the long wall of the
 * gallery. The land behind the glass is the reflected chunk meshes (MirrorWorld through mirror_clip), and the people
 * in it are pale likenesses (mirror_ghost), both clipped to the glass and drawn at its depth.
 * One frame reflects the present; one reflects it late; one reflects somewhere warmer, where someone stands behind
 * you. The gallery wall is always a beat late, and on its far pedestal there is something the corridor lacks.
 */
final class MirrorGlass {
    private static final ByteBufferBuilder GHOSTS=new ByteBufferBuilder(1<<16);

    /** A posed model's surfaces, reflected, as pale rim-lit light for the ghost shader. */
    static final class Reflected implements VertexConsumer {
        final BufferBuilder out;final Matrix4f mirror;final float r,g,b,alpha;
        private final Vector3f p=new Vector3f(),n=new Vector3f();
        Reflected(BufferBuilder out,Matrix4f mirror,int rgb,float alpha){this.out=out;this.mirror=mirror;r=(rgb>>16&255)/255f;g=(rgb>>8&255)/255f;b=(rgb&255)/255f;this.alpha=alpha;}
        @Override public void addVertex(float x,float y,float z,int argb,float u,float v,int overlay,int light,float nx,float ny,float nz){
            mirror.transformPosition(p.set(x,y,z));mirror.transformDirection(n.set(nx,ny,nz));
            float d=p.length(),cos=d<1e-4f?-1:n.dot(p)/d/Math.max(1e-4f,n.length());
            float rim=(1+Math.min(cos,0))*(1+Math.min(cos,0));
            float a=cos>.05f?0:alpha*(.3f+.62f*rim);
            out.addVertex(p.x,p.y,p.z).setColor(r,g,b,a);
        }
        @Override public VertexConsumer addVertex(float x,float y,float z){mirror.transformPosition(p.set(x,y,z));out.addVertex(p.x,p.y,p.z).setColor(r,g,b,alpha*.5f);return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        @Override public VertexConsumer setUv(float u,float v){return this;}
        @Override public VertexConsumer setUv1(int u,int v){return this;}
        @Override public VertexConsumer setUv2(int u,int v){return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
    private static void clip(ShaderInstance s,Vec3 q,Vec3 n,Vec3 u,double hu,double hv){
        if(s==null)return;
        s.safeGetUniform("ClipPlane").set((float)n.x,(float)n.y,(float)n.z,(float)-n.dot(q));
        s.safeGetUniform("ClipCenter").set((float)q.x,(float)q.y,(float)q.z);
        s.safeGetUniform("ClipU").set((float)u.x,(float)u.y,(float)u.z,(float)hu);
        s.safeGetUniform("ClipV").set(0f,1f,0f,(float)hv);
    }
    private static float[] fog(double day){return new float[]{(float)Mth.lerp(day,.17,.72),(float)Mth.lerp(day,.21,.77),(float)Mth.lerp(day,.31,.82),1/420f};}

    // ------------------------------------------------------------------ the three frames
    static void frames(Matrix4f view,Matrix4f proj,Vec3 camera,float partial,double time){
        var mc=Minecraft.getInstance();
        double day=MirrorClient.dayness(partial);
        for(MirrorFrames f:mc.level.getEntities(Veilborne.FRAMES.get(),mc.player.getBoundingBox().inflate(48),x->true)){
            float vis=f.visibility(partial);
            if(vis<.02)continue;
            for(int i=0;i<3;i++){
                if(f.broken(i,partial)>=.999)continue;
                Vec3 n=f.normal(i),glass=f.glass(i).add(0,(vis-1)*MirrorFrames.HEIGHT,0);
                float shiver=f.shiver(i,partial);
                glass=glass.add(new Vec3(-n.z,0,n.x).scale(Math.sin(time*50)*.03*shiver));
                Vec3 q=glass.subtract(camera);
                if(n.dot(q.scale(-1))<.05)continue;
                int kind=f.kind(i);
                Vec3 u=new Vec3(-n.z,0,n.x);
                double hu=MirrorFrames.WIDTH/2-.07,hv=MirrorFrames.HEIGHT/2-.07;
                Matrix4f mirror=MirrorWorld.planeReflection(q,n);
                float fade=vis*(1-f.broken(i,partial));
                backdrop(camera,glass,n,u,hu,hv,day,kind==MirrorFrames.ELSEWHERE,fade);
                RenderSystem.setShaderColor(1,1,1,fade);
                reflectLand(view,proj,camera,mirror,q,n,u,hu,hv,day,time,kind==MirrorFrames.ELSEWHERE?1:0,glass);
                RenderSystem.setShaderColor(1,1,1,1);
                reflectPeople(camera,mirror,q,n,u,hu,hv,partial,kind==MirrorFrames.LATE?16:0,kind==MirrorFrames.ELSEWHERE,fade,time);
                sheen(camera,glass,n,u,hu,hv,fade,time,shiver);
            }
        }
    }
    /**
     * What the glass shows where no land is: the mirror sky itself, and in it the land upside down in silver haze
     * (the same painted distance the sky uses), taken from the direction the mirror looks. Somewhere else it is a
     * warm evening.
     */
    private static void backdrop(Vec3 camera,Vec3 glass,Vec3 n,Vec3 u,double hu,double hv,double day,boolean elsewhere,float alpha){
        Vec3 c=glass.subtract(camera).add(n.scale(.001));
        Vec3 r=u.scale(hu),up=new Vec3(0,hv,0);
        float yaw=(float)(Math.atan2(n.z,n.x)/(Math.PI*2)+.5);
        float d=(float)day;
        int tint=elsewhere?0xffc48c:((int)Mth.lerp(d,0x3b,0xff)<<16)|((int)Mth.lerp(d,0x48,0xff)<<8)|(int)Mth.lerp(d,0x66,0xff);
        TideFx fx=TideFx.begin(MirrorRenderer.HORIZON,new Matrix4f());
        fx.quad(c.subtract(r).subtract(up),c.add(r).subtract(up),c.add(r).add(up),c.subtract(r).add(up),yaw+.06f,.98f,yaw-.06f,.55f,tint,alpha);
        fx.end(false);
    }
    private static void reflectLand(Matrix4f view,Matrix4f proj,Vec3 camera,Matrix4f mirror,Vec3 q,Vec3 n,Vec3 u,double hu,double hv,double day,double time,float warm,Vec3 around){
        if(MirrorWorld.clipShader==null)return;
        clip(MirrorWorld.clipShader,q,n,u,hu,hv);
        var d=new MirrorWorld.Draw(view,proj,mirror,1,camera,fog(day),(float)(time%3600),(float)day,warm,4096);
        RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.disableBlend();
        MirrorWorld.draw(MirrorWorld.clipShader,d,false,true,around,40);
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.depthMask(false);
        MirrorWorld.draw(MirrorWorld.clipShader,d,true,true,around,40);
    }
    /** Everyone near the glass, reflected: in step, {@code lag} ticks late, or with a stranger standing behind them. */
    private static void reflectPeople(Vec3 camera,Matrix4f mirror,Vec3 q,Vec3 n,Vec3 u,double hu,double hv,float partial,int lag,boolean stranger,float alpha,double time){
        var shader=MirrorRenderer.ghostShader;
        if(shader==null)return;
        var mc=Minecraft.getInstance();
        BufferBuilder b=new BufferBuilder(GHOSTS,VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        int drawn=0;
        for(Player pl:mc.level.players()){
            ArrayDeque<MoonClient.Frame> hist=MirrorClient.history.get(pl.getId());
            if(hist==null||hist.size()<2||!(pl instanceof AbstractClientPlayer who))continue;
            MoonClient.Frame f=MirrorClient.lagged(hist,partial,Math.min(lag,hist.size()-2));
            if(Math.hypot(f.x()-camera.x-q.x,f.z()-camera.z-q.z)>20)continue;
            PoseStack pose=new PoseStack();pose.translate(f.x()-camera.x,f.y()-camera.y,f.z()-camera.z);
            GhostFigure.posed(who,pose,new Reflected(b,mirror,0xe6eef6,.9f*alpha),-1,f,0,0,true,pl.tickCount+partial);
            drawn++;
            if(stranger&&pl==mc.player){
                // Somewhere else, someone stands a little behind you and to one side, quite still.
                Vec3 back=new Vec3(f.x(),f.y(),f.z()).subtract(n.scale(2.2)).add(u.scale(.9));
                MoonClient.Frame still=new MoonClient.Frame(back.x,back.y,back.z,f.body(),f.body()+(float)Math.sin(time*.3)*8,4,0,0,false);
                PoseStack sp=new PoseStack();sp.translate(back.x-camera.x,back.y-camera.y,back.z-camera.z);
                GhostFigure.posed(who,sp,new Reflected(b,mirror,0xffe2c0,.7f*alpha),-1,still,0,0,false,(float)time*20);
            }
        }
        MeshData mesh=b.build();
        if(mesh==null||drawn==0){if(mesh!=null)mesh.close();return;}
        clip(shader,q,n,u,hu,hv);
        RenderSystem.setShader(()->shader);
        RenderSystem.enableDepthTest();RenderSystem.depthMask(false);RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();
        BufferUploader.drawWithShader(mesh);
    }
    /** A faint silver sheen on the glass, a glint sweeping across it, and a flash of cracks when a wrong one shivers. */
    private static void sheen(Vec3 camera,Vec3 glass,Vec3 n,Vec3 u,double hu,double hv,float alpha,double time,float shiver){
        Vec3 c=glass.subtract(camera).add(n.scale(.004));
        Vec3 r=u.scale(hu),up=new Vec3(0,hv,0);
        TideFx fx=TideFx.begin(MirrorRenderer.GLOW,new Matrix4f());
        fx.quad(c.subtract(r).subtract(up),c.add(r).subtract(up),c.add(r).add(up),c.subtract(r).add(up),0,0,1,1,0xb8cfe0,alpha*.06);
        double sweep=(time*.18)%3-1;
        Vec3 s=c.add(u.scale(sweep*hu));
        fx.quad(s.subtract(u.scale(.12)).subtract(up).add(u.scale(-.25)),s.add(u.scale(.12)).subtract(up).add(u.scale(-.25)),s.add(u.scale(.12)).add(up).add(u.scale(.25)),s.subtract(u.scale(.12)).add(up).add(u.scale(.25)),0,.4f,1,.6f,0xeaf4ff,alpha*.18);
        fx.end(true);
        if(shiver>.01){
            TideFx cracks=TideFx.begin(MirrorRenderer.WARN,new Matrix4f());
            cracks.quad(c.subtract(r).subtract(up),c.add(r).subtract(up),c.add(r).add(up),c.subtract(r).add(up),0,0,1,1,0xffe2b0,shiver*.8);
            cracks.end(true);
        }
    }

    // ------------------------------------------------------------------ the gallery wall
    static void gallery(Matrix4f view,Matrix4f proj,Vec3 camera,float partial,double time){
        var mc=Minecraft.getInstance();
        BlockPos o=MirrorGallery.originAt(camera.x);
        // Clear of the wall's own face, or the two fight for the same depth.
        double planeX=o.getX()+MirrorGallery.MIRROR_X+.05;
        if(camera.x<=planeX)return;
        Vec3 n=new Vec3(1,0,0),u=new Vec3(0,0,1);
        Vec3 glass=new Vec3(planeX,o.getY()+1.8,o.getZ()+12.5);
        Vec3 q=glass.subtract(camera);
        double hu=12.45,hv=1.79;
        Matrix4f mirror=MirrorWorld.planeReflection(q,n);
        reflectLand(view,proj,camera,mirror,q,n,u,hu,hv,.2,time,0,camera);
        // The reflection is a beat late, and on the pedestal it keeps the thing you have not yet been handed.
        reflectPeople(camera,mirror,q,n,u,hu,hv,partial,20,false,1,time);
        boolean rewarded=MirrorClient.state!=null&&MirrorClient.state.rewarded();
        Vec3 pedestal=Vec3.atLowerCornerOf(o).add(MirrorGallery.PEDESTAL);
        Vec3 mirrored=new Vec3(2*planeX-pedestal.x,pedestal.y+Math.sin(time*1.4)*.06,pedestal.z).subtract(camera);
        if(!rewarded){
            RenderSystem.disableDepthTest();
            TideFx glow=TideFx.begin(MirrorRenderer.GLOW,new Matrix4f());
            var facing=mc.gameRenderer.getMainCamera().rotation();
            glow.billboard(mirrored,facing,.9,0,0,0,1,1,0x9fe6f2,.45+.1*Math.sin(time*2));
            glow.end(true);
            TideFx item=TideFx.begin(Veilborne.id("textures/item/echo_mirror.png"),new Matrix4f());
            item.billboard(mirrored,facing,.32,Math.sin(time*.8)*.2,0,0,1,1,0xffffff,.95);
            item.end(false);
            RenderSystem.enableDepthTest();
        }
        sheen(camera,glass,n,u,hu,hv,1,time,0);
        MirrorGround.exitDoor(camera,o,time);
    }
    private MirrorGlass(){}
}
