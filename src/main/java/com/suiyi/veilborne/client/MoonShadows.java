package com.suiyi.veilborne.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.suiyi.veilborne.*;
import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 月影: long, slender moon-shadows cast away from the true moon, moving a beat behind their owners. Casters are
 * flattened along the moonlight and draped over the terrain vertex by vertex. Overlapping limbs must not
 * darken twice, so silhouettes only write a shared value into the framebuffer's alpha channel, and one
 * full-screen pass then multiplies the colour by it and restores alpha. Moon bearers cast shadows too,
 * and so does the empty place in their line, where nobody walks.
 */
final class MoonShadows {
    static final Collector COLLECT=new Collector();
    private static float[] verts=new float[4096];
    private static int count;
    private static Vec3 camera=Vec3.ZERO,dir=new Vec3(1,0,0),across=new Vec3(0,0,1);
    private static double reach=1.7;
    private static boolean frame;

    /** Called once per frame before any caster: fixes the light for this frame. */
    static boolean begin(Vec3 cam,float partial){
        count=0;camera=cam;frame=false;
        if(!TideConfig.MOON_SHADOWS.get()||!MoonClient.active())return false;
        double k=MoonTimeline.shadows(MoonClient.progress(partial))*MoonClient.strength();
        if(k<.02)return false;
        Vec3 moon=MoonClient.trueDir(partial);
        double h=Math.hypot(moon.x,moon.z);
        if(h<1e-3||moon.y<.05)return false;
        dir=new Vec3(-moon.x/h,0,-moon.z/h);across=new Vec3(-dir.z,0,dir.x);
        reach=Math.min(3.2,h/moon.y)*1.35;
        frame=true;
        return true;
    }
    static boolean casting(){return frame;}
    static void anchor(Vec3 feet){COLLECT.anchor=feet;}

    /**
     * Every player nearby. The shadow stays at its owner's feet, where they are this frame; only its pose (limbs,
     * turning, crouching) is taken from a few ticks back, so it moves a beat late without ever sliding behind like
     * network lag.
     */
    static void players(float partial){
        if(!frame)return;
        var mc=Minecraft.getInstance();
        for(Player pl:mc.level.players()){
            var q=MoonClient.history.get(pl.getId());
            if(q==null||q.size()<2||!(pl instanceof AbstractClientPlayer who))continue;
            MoonClient.Frame f=lagged(q,partial,TideConfig.MOON_SHADOW_LAG.get());
            double x=Mth.lerp(partial,pl.xo,pl.getX()),y=Mth.lerp(partial,pl.yo,pl.getY()),z=Mth.lerp(partial,pl.zo,pl.getZ());
            BlockPos at=BlockPos.containing(x,y+1,z);
            if(!mc.level.canSeeSky(at)||pl.isInWater())continue;
            double ground=MoonProcession.ground(mc.level,x,z);
            if(y-ground>5)continue;
            anchor(new Vec3(x,Math.min(y,ground+.2),z));
            PoseStack pose=new PoseStack();
            pose.translate(x-camera.x,y-camera.y,z-camera.z);
            pose.mulPose(Axis.YP.rotationDegrees(180-f.body()));
            pose.scale(-1,-1,1);pose.scale(.9375f,.9375f,.9375f);pose.translate(0,-1.501,0);
            var model=GhostFigure.model(who);
            if(model==null)continue;
            model.setAllVisible(true);model.crouching=f.crouch();model.riding=false;model.young=false;model.attackTime=0;
            model.leftArmPose=model.rightArmPose=net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;
            model.setupAnim(who,f.limb(),f.speed(),pl.tickCount+partial,Mth.wrapDegrees(f.head()-f.body()),f.pitch());
            model.renderToBuffer(pose,COLLECT,0xf000f0,OverlayTexture.NO_OVERLAY,-1);
        }
    }
    /**
     * The pose {@code lag} ticks ago. Frames are recorded at the end of each client tick, so the newest two
     * bracket what is on screen now; lag 0 interpolates exactly as the player's own model does.
     */
    private static MoonClient.Frame lagged(ArrayDeque<MoonClient.Frame> q,float partial,int lag){
        MoonClient.Frame[] a=q.toArray(new MoonClient.Frame[0]);
        int i=Math.max(0,a.length-2-lag);
        MoonClient.Frame f0=a[i],f1=a[Math.min(a.length-1,i+1)];
        float t=partial;
        return new MoonClient.Frame(Mth.lerp(t,f0.x(),f1.x()),Mth.lerp(t,f0.y(),f1.y()),Mth.lerp(t,f0.z(),f1.z()),
            Mth.rotLerp(t,f0.body(),f1.body()),Mth.rotLerp(t,f0.head(),f1.head()),Mth.lerp(t,f0.pitch(),f1.pitch()),
            Mth.lerp(t,f0.limb(),f1.limb()),Mth.lerp(t,f0.speed(),f1.speed()),f1.crouch());
    }

    /** Darkens the collected silhouettes in one step. */
    static void draw(){
        if(!frame||count==0)return;
        double p=MoonClient.progress(1);
        double k=.36*MoonTimeline.shadows(p)*MoonClient.strength()*(1-.25*MoonClient.dayness(1));
        BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION);
        for(int i=0;i+2<count;i+=3)b.addVertex(verts[i],verts[i+1],verts[i+2]);
        MeshData mesh=b.build();
        if(mesh==null)return;
        RenderSystem.disableCull();RenderSystem.depthMask(false);
        RenderSystem.colorMask(false,false,false,true);RenderSystem.disableBlend();
        screen(1,1,1,1);
        RenderSystem.enableDepthTest();RenderSystem.depthFunc(515);
        RenderSystem.setShader(GameRenderer::getPositionShader);
        RenderSystem.setShaderColor(1,1,1,(float)(1-k));
        BufferUploader.drawWithShader(mesh);
        RenderSystem.setShaderColor(1,1,1,1);
        RenderSystem.colorMask(true,true,true,false);RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ZERO,GlStateManager.DestFactor.DST_ALPHA);
        screen(1,1,1,1);
        // A faint violet in the shade, so it reads as moonlight rather than a hole.
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE_MINUS_DST_ALPHA,GlStateManager.DestFactor.ONE);
        screen(.05f,.03f,.12f,1);
        RenderSystem.colorMask(false,false,false,true);RenderSystem.disableBlend();
        screen(1,1,1,1);
        RenderSystem.colorMask(true,true,true,true);RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.enableCull();
        count=0;
    }
    private static void screen(float r,float g,float b,float a){
        if(MoonRenderer.screenShader==null)return;
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(()->MoonRenderer.screenShader);
        RenderSystem.setShaderColor(r,g,b,a);
        BufferBuilder q=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION);
        q.addVertex(-1,-1,0);q.addVertex(1,-1,0);q.addVertex(1,1,0);q.addVertex(-1,1,0);
        BufferUploader.drawWithShader(q.buildOrThrow());
        RenderSystem.setShaderColor(1,1,1,1);
        RenderSystem.enableDepthTest();
    }

    /** Receives model vertices (camera-relative) and lays them along the moonlight onto the ground. */
    static final class Collector implements VertexConsumer {
        Vec3 anchor=Vec3.ZERO;
        @Override public VertexConsumer addVertex(float x,float y,float z){
            var level=Minecraft.getInstance().level;
            if(level==null||!frame)return this;
            double wx=x+camera.x,wy=y+camera.y,wz=z+camera.z;
            double h=Math.max(0,wy-anchor.y);
            double fx=wx+dir.x*h*reach-anchor.x,fz=wz+dir.z*h*reach-anchor.z;
            double along=fx*dir.x+fz*dir.z,side=(fx*across.x+fz*across.z)*.72;
            double gx=anchor.x+dir.x*along+across.x*side,gz=anchor.z+dir.z*along+across.z*side;
            double gy=Math.max(anchor.y-3,MoonProcession.ground(level,gx,gz))+.02;
            if(count+3>verts.length)verts=java.util.Arrays.copyOf(verts,verts.length*2);
            verts[count++]=(float)(gx-camera.x);verts[count++]=(float)(gy-camera.y);verts[count++]=(float)(gz-camera.z);
            return this;
        }
        @Override public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        @Override public VertexConsumer setUv(float u,float v){return this;}
        @Override public VertexConsumer setUv1(int u,int v){return this;}
        @Override public VertexConsumer setUv2(int u,int v){return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
    private MoonShadows(){}
}
