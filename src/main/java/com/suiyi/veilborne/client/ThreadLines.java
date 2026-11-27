package com.suiyi.veilborne.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Threads of light for 绯线缝世, drawn with thread_line: soft ribbons turned to the eye whose width never drops below about a
 * pixel however far they reach, so a thread tied to the sky 700 blocks up reads as the same thread all the way. Points are
 * true camera-relative positions; the shader brings anything past the far plane nearer without changing how it looks.
 * Sky threads are marked so that behind the cloth they only show through the wound.
 */
final class ThreadLines {
    static ShaderInstance shader;
    private static final ByteBufferBuilder BYTES=new ByteBufferBuilder(1<<18);
    private final BufferBuilder buffer=new BufferBuilder(BYTES,VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
    private int quads;
    static ThreadLines begin(){return new ThreadLines();}

    /** A polyline; {@code px} is the half-width as a fraction of distance (about .0011 per pixel), {@code min} the least half-width in blocks. */
    void strip(List<Vec3> pts,int rgb,double alpha,double px,double min,boolean sky){
        for(int i=1;i<pts.size();i++)segment(pts.get(i-1),pts.get(i),rgb,alpha,alpha,px,min,sky);
    }
    void segment(Vec3 a,Vec3 b,int rgb,double alphaA,double alphaB,double px,double min,boolean sky){
        if(alphaA<.002&&alphaB<.002)return;
        Vec3 along=b.subtract(a);
        if(along.lengthSqr()<1e-10)return;
        Vec3 sa=along.cross(a),sb=along.cross(b);
        if(sa.lengthSqr()<1e-10)sa=along.cross(new Vec3(0,1,0));
        if(sb.lengthSqr()<1e-10)sb=sa;
        double wa=Math.max(min,a.length()*px),wb=Math.max(min,b.length()*px);
        sa=sa.normalize().scale(wa);sb=sb.normalize().scale(wb);
        float f=sky?1:0;
        float r=(rgb>>16&255)/255f,g=(rgb>>8&255)/255f,bl=(rgb&255)/255f;
        float aa=(float)Math.min(1,Math.max(0,alphaA)),ab=(float)Math.min(1,Math.max(0,alphaB));
        buffer.addVertex((float)(a.x-sa.x),(float)(a.y-sa.y),(float)(a.z-sa.z)).setUv(-1,f).setColor(r,g,bl,aa);
        buffer.addVertex((float)(a.x+sa.x),(float)(a.y+sa.y),(float)(a.z+sa.z)).setUv(1,f).setColor(r,g,bl,aa);
        buffer.addVertex((float)(b.x+sb.x),(float)(b.y+sb.y),(float)(b.z+sb.z)).setUv(1,f).setColor(r,g,bl,ab);
        buffer.addVertex((float)(b.x-sb.x),(float)(b.y-sb.y),(float)(b.z-sb.z)).setUv(-1,f).setColor(r,g,bl,ab);
        quads++;
    }
    /** Seam uniforms must already be set on the shader. */
    void end(Matrix4f modelView,Matrix4f projection){
        MeshData mesh=buffer.build();
        if(mesh==null||shader==null){if(mesh!=null)mesh.close();return;}
        shader.safeGetUniform("FarFit").set(Minecraft.getInstance().gameRenderer.getDepthFar()*.9f);
        RenderSystem.setShader(()->shader);
        RenderSystem.enableBlend();RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);RenderSystem.enableDepthTest();RenderSystem.disableCull();
        RenderSystem.getModelViewStack().pushMatrix();RenderSystem.getModelViewStack().set(modelView);RenderSystem.applyModelViewMatrix();
        BufferUploader.drawWithShader(mesh);
        RenderSystem.getModelViewStack().popMatrix();RenderSystem.applyModelViewMatrix();
        RenderSystem.defaultBlendFunc();
    }
}
