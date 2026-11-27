package com.suiyi.veilborne.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.suiyi.veilborne.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;

/**
 * Draws the celestial whale in the opaque world pass, so ridges and trees can pass in front of it.
 * The whale shader lights it from the sea above, runs caustics across its back, glows at the rim and
 * in its pleats and stars, and fades into the sea haze with distance. Fading uses a depth pre-pass so
 * a half-transparent whale never shows its own far side.
 */
final class WhaleRenderer {
    static ShaderInstance shader;
    private static final ResourceLocation SKIN=Veilborne.id("textures/environment/whale_skin.png"),GLOW=Veilborne.id("textures/environment/whale_glow.png");
    private static final ResourceLocation WRECK=Veilborne.id("textures/environment/wreck.png"),WRECK_GLOW=Veilborne.id("textures/environment/wreck_glow.png");
    private static final double SCALE=.95;
    private static VertexBuffer body,wreck;

    /** The whale's path across the region: a long arc that swings nearer overhead at its midpoint. */
    static Vec3 position(double progress,double time){
        var c=TideClient.state.center();
        double s=path(progress);
        return new Vec3(c.getX()-220+440*s,c.getY()+96+10*Math.sin(Math.PI*s)+Math.sin(time*.07)*2.5,c.getZ()+135-78*Math.sin(Math.PI*s));
    }
    static double path(double progress){return Math.max(0,Math.min(1,(progress-.53)/.31));}

    static void render(Matrix4f modelView,Vec3 camera,double progress,double time,double visibility){
        if(shader==null||visibility<.002)return;
        double s=path(progress);
        Vec3 at=position(progress,time);
        double dx=440,dy=10*Math.PI*Math.cos(Math.PI*s),dz=-78*Math.PI*Math.cos(Math.PI*s);
        double yaw=Math.atan2(-dz,dx),pitch=Math.atan2(dy,Math.hypot(dx,dz))*.6,roll=Math.sin(time*.21)*.05-dz/Math.hypot(dx,dz)*.12;
        Vector3d rel=new Vector3d(at.x-camera.x,at.y-camera.y,at.z-camera.z);
        // Keep the whole animal inside the far plane at short render distances, preserving its apparent size.
        double far=Minecraft.getInstance().gameRenderer.getDepthFar()*.9,reach=rel.length()+70*SCALE;
        double k=Math.min(1,far/reach);
        Matrix4d m=new Matrix4d().translation(rel.mul(k)).rotateY(yaw).rotateZ(pitch).rotateX(roll).scale(SCALE*k);
        int alpha=(int)Math.round(Math.min(1,visibility)*255);
        int step=TideConfig.QUALITY.get()==0?2:1;
        body=upload(body,e->WhaleModel.body(e,time,step),m,alpha);
        wreck=upload(wreck,e->WhaleModel.wreck(e,time),m,alpha);

        double distance=rel.length()/k;
        float haze=(float)Math.min(.32,Math.max(0,(distance-70)/480));
        shader.safeGetUniform("TideTime").set((float)(time%3600));
        shader.safeGetUniform("TideOrigin").set((float)(camera.x%2048),(float)(camera.y%2048),(float)(camera.z%2048));
        shader.safeGetUniform("TideFog").set(.08f,.19f,.27f,haze);
        Matrix4f projection=RenderSystem.getProjectionMatrix();

        RenderSystem.disableCull();RenderSystem.enableDepthTest();RenderSystem.depthFunc(515);
        halo(0,0);
        boolean translucent=alpha<250;
        if(translucent){
            RenderSystem.colorMask(false,false,false,false);RenderSystem.depthMask(true);RenderSystem.disableBlend();
            draw(body,SKIN,GLOW,modelView,projection);draw(wreck,WRECK,WRECK_GLOW,modelView,projection);
            RenderSystem.colorMask(true,true,true,true);RenderSystem.depthMask(false);
        }else RenderSystem.depthMask(true);
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        draw(body,SKIN,GLOW,modelView,projection);
        draw(wreck,WRECK,WRECK_GLOW,modelView,projection);
        // A soft additive aura around the silhouette, the cheap cousin of bloom.
        RenderSystem.depthMask(false);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ONE);
        // The aura widens with distance: a soft bloom from the ground, never a bubble up close.
        double aura=Math.max(.25,Math.min(2.2,distance*.011))*k;
        halo((float)aura,(float)(.34*TideConfig.BRIGHTNESS.get()));
        draw(body,SKIN,GLOW,modelView,projection);
        halo(0,0);
        RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();RenderSystem.depthMask(true);RenderSystem.enableCull();
    }
    private static void halo(float width,float strength){shader.safeGetUniform("TideHalo").set(width,strength);}
    private static void draw(VertexBuffer buffer,ResourceLocation tex,ResourceLocation glow,Matrix4f modelView,Matrix4f projection){
        if(buffer==null)return;
        RenderSystem.setShaderTexture(0,tex);RenderSystem.setShaderTexture(1,glow);
        buffer.bind();buffer.drawWithShader(modelView,projection,shader);VertexBuffer.unbind();
    }
    private static VertexBuffer upload(VertexBuffer target,java.util.function.Consumer<WhaleModel.Emitter> geometry,Matrix4d m,int alpha){
        BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        Vector3d p=new Vector3d(),n=new Vector3d();
        geometry.accept((x,y,z,u,v,nx,ny,nz,argb)->{
            m.transformPosition(p.set(x,y,z));
            m.transformDirection(n.set(nx,ny,nz));n.normalize();
            b.addVertex((float)p.x,(float)p.y,(float)p.z).setUv((float)u,(float)v)
                .setColor(argb>>16&255,argb>>8&255,argb&255,Math.min(alpha,argb>>>24)).setNormal((float)n.x,(float)n.y,(float)n.z);
        });
        MeshData mesh=b.build();
        if(mesh==null)return target;
        if(target==null)target=new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        target.bind();target.upload(mesh);VertexBuffer.unbind();
        return target;
    }
    private WhaleRenderer(){}
}
