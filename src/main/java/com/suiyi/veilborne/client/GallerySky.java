package com.suiyi.veilborne.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.suiyi.veilborne.*;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The sky around the gallery: the corridor floats inside the mirror. Dark silver haze above and below, the painted
 * inverted horizon wrapped all around at eye level, great faint panes drifting past, and a few cold glints.
 */
final class GallerySky extends DimensionSpecialEffects {
    GallerySky(){super(Float.NaN,false,SkyType.NONE,false,false);}
    @Override public Vec3 getBrightnessDependentFogColor(Vec3 color,float brightness){return new Vec3(.18,.21,.25);}
    @Override public boolean isFoggyAt(int x,int z){return false;}
    @Override public boolean renderClouds(ClientLevel level,int ticks,float partial,PoseStack pose,double x,double y,double z,Matrix4f modelView,Matrix4f projection){return true;}
    @Override public boolean renderSnowAndRain(ClientLevel level,int ticks,float partial,net.minecraft.client.renderer.LightTexture light,double x,double y,double z){return true;}
    @Override public boolean renderSky(ClientLevel level,int ticks,float partial,Matrix4f modelView,Camera camera,Matrix4f projection,boolean foggy,Runnable setupFog){
        double time=(ticks+partial)/20.0;
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();RenderSystem.depthMask(false);RenderSystem.disableDepthTest();
        double R=TideRenderer.DOME;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        for(int j=0;j<24;j++)for(int i=0;i<48;i++){
            double a0=i*Math.PI*2/48,a1=(i+1)*Math.PI*2/48,e0=-Math.PI/2+j*Math.PI/24,e1=e0+Math.PI/24;
            dome(b,modelView,a0,e0,R);dome(b,modelView,a1,e0,R);dome(b,modelView,a1,e1,R);dome(b,modelView,a0,e1,R);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        // The inverted distance, a band around the horizon, mirrored above and below as if seen in a pool.
        RenderSystem.setShader(()->SeaSky.shader!=null?SeaSky.shader:GameRenderer.getPositionTexColorShader());
        RenderSystem.setShaderTexture(0,MirrorRenderer.HORIZON);
        b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        for(int side=-1;side<=1;side+=2)for(int j=0;j<10;j++)for(int i=0;i<64;i++){
            double u0=(double)i/64,u1=(double)(i+1)/64,v0=.52+j*.048,v1=v0+.048;
            band(b,modelView,u0,v0,side,R,time);band(b,modelView,u1,v0,side,R,time);band(b,modelView,u1,v1,side,R,time);band(b,modelView,u0,v1,side,R,time);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        // Drifting panes and glints.
        TideFx panes=TideFx.begin(MirrorRenderer.PANE,modelView);
        Random rng=new Random(77);
        for(int k=0;k<14;k++){
            double az=rng.nextDouble()*Math.PI*2+time*.004*(k%2==0?1:-1),el=(rng.nextDouble()-.5)*1.1;
            Vec3 c=new Vec3(Math.cos(az)*Math.cos(el),Math.sin(el),Math.sin(az)*Math.cos(el)).scale(R*.85);
            double yaw=az+Math.PI/2+time*.02*(k%3-1),w=6+rng.nextDouble()*8,h=w*2.2;
            Vec3 right=new Vec3(Math.cos(yaw),0,Math.sin(yaw)).scale(w/2),up=new Vec3(0,h/2,0);
            int v=rng.nextInt(4);float u0=v*.25f,u1=u0+.25f;
            panes.quad(c.subtract(right).subtract(up),c.add(right).subtract(up),c.add(right).add(up),c.subtract(right).add(up),u0,1,u1,0,0xa8c4d6,.16+.08*Math.sin(time*.3+k));
        }
        panes.end(true);
        TideFx stars=TideFx.begin(MirrorRenderer.STAR,modelView);
        for(int k=0;k<90;k++){
            double az=rng.nextDouble()*Math.PI*2,el=Math.asin(rng.nextDouble()*2-1);
            Vec3 c=new Vec3(Math.cos(az)*Math.cos(el),Math.sin(el),Math.sin(az)*Math.cos(el)).scale(R*.95);
            stars.billboard(c,camera.rotation(),.35+rng.nextDouble()*.5,time*.2+k,0,0,1,1,k%7==0?0xffe6b8:0xdcecff,.35+.3*Math.sin(time*.7+k*1.3));
        }
        stars.end(true);
        RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();
        return true;
    }
    private static void dome(BufferBuilder b,Matrix4f m,double az,double el,double r){
        double h=Math.sin(el);
        float k=(float)(.16+.1*Math.exp(-h*h*18));
        b.addVertex(m,(float)(Math.cos(az)*Math.cos(el)*r),(float)(h*r),(float)(Math.sin(az)*Math.cos(el)*r)).setColor(k*.9f,k*1.02f,k*1.16f,1f);
    }
    private static void band(BufferBuilder b,Matrix4f m,double u,double v,int side,double r,double time){
        double az=u*Math.PI*2+time*.003,el=(1-v)*Math.PI/2*side;
        float a=(float)(.55*Math.sin(Math.min(1,(v-.52)/.48)*Math.PI));
        b.addVertex(m,(float)(Math.cos(az)*Math.cos(el)*r*.98),(float)(Math.sin(el)*r*.98),(float)(Math.sin(az)*Math.cos(el)*r*.98)).setUv((float)u,(float)v).setColor(.9f,.95f,1f,a);
    }
}
