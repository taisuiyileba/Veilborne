package com.suiyi.veilborne.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Textured additive light: curtains, beams, sigils and star sprites. Vertices are camera-relative.
 * Uses the mod's sea shader, which keeps arbitrarily faint alpha instead of clipping it.
 * Several batches may be open at once (a glow layer under a sprite layer), so each open batch writes into
 * its own buffer from a small pool rather than the shared Tesselator, whose single buffer would interleave them.
 */
final class TideFx {
    private static final ByteBufferBuilder[] POOL=new ByteBufferBuilder[6];
    private static final boolean[] BUSY=new boolean[POOL.length];
    private final BufferBuilder buffer;
    private final ResourceLocation texture;
    private final Matrix4f matrix;
    private final int slot;
    private TideFx(ResourceLocation texture,Matrix4f matrix){
        this.texture=texture;this.matrix=matrix;
        int free=-1;
        for(int i=0;i<POOL.length&&free<0;i++)if(!BUSY[i])free=i;
        slot=free;
        if(free>=0){
            if(POOL[free]==null)POOL[free]=new ByteBufferBuilder(1<<16);
            BUSY[free]=true;
            buffer=new BufferBuilder(POOL[free],VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        }else buffer=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
    }
    static TideFx begin(ResourceLocation texture,Matrix4f matrix){return new TideFx(texture,matrix);}

    void vertex(double x,double y,double z,float u,float v,int rgb,double alpha){
        buffer.addVertex(matrix,(float)x,(float)y,(float)z).setUv(u,v)
            .setColor((rgb>>16&255)/255f,(rgb>>8&255)/255f,(rgb&255)/255f,(float)Math.max(0,Math.min(1,alpha)));
    }
    void quad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,float u0,float v0,float u1,float v1,int rgb,double alpha){
        vertex(a.x,a.y,a.z,u0,v0,rgb,alpha);vertex(b.x,b.y,b.z,u1,v0,rgb,alpha);
        vertex(c.x,c.y,c.z,u1,v1,rgb,alpha);vertex(d.x,d.y,d.z,u0,v1,rgb,alpha);
    }
    /** Flat sigil lying on the ground, rotated in place. */
    void disc(Vec3 center,double radius,double spin,int rgb,double alpha){
        Vec3[] k=new Vec3[4];
        for(int i=0;i<4;i++){double a=spin+Math.PI/4+i*Math.PI/2;k[i]=center.add(Math.cos(a)*radius*Math.sqrt(2),0,Math.sin(a)*radius*Math.sqrt(2));}
        quad(k[0],k[1],k[2],k[3],0,0,1,1,rgb,alpha);
    }
    void billboard(Vec3 p,Quaternionf facing,double size,double spin,int rgb,double alpha){
        float[][] corners={{-1,-1,0,1},{-1,1,0,0},{1,1,1,0},{1,-1,1,1}};
        float cs=(float)Math.cos(spin),sn=(float)Math.sin(spin);
        for(float[] c:corners){
            Vector3f v=new Vector3f((c[0]*cs-c[1]*sn)*(float)size,(c[0]*sn+c[1]*cs)*(float)size,0).rotate(facing);
            vertex(p.x+v.x,p.y+v.y,p.z+v.z,c[2],c[3],rgb,alpha);
        }
    }
    /**
     * Vertical ribbon turned toward the camera, split into segments so its alpha can follow a profile
     * (top fade-in, body, dissolving tail). Texture scrolls along its length by vOffset.
     */
    void curtain(Vec3 top,double length,double width,Vec3 camera,float vOffset,float vScale,int rgb,double alpha,java.util.function.DoubleUnaryOperator profile,double sway,double time){
        int segments=12;
        for(int i=0;i<segments;i++){
            double t0=(double)i/segments,t1=(double)(i+1)/segments;
            Vec3 a=top.add(Math.sin(time*.13+t0*3)*sway*t0,-length*t0,Math.cos(time*.1+t0*2)*sway*t0*.5);
            Vec3 b=top.add(Math.sin(time*.13+t1*3)*sway*t1,-length*t1,Math.cos(time*.1+t1*2)*sway*t1*.5);
            Vec3 side=b.subtract(a).cross(a.subtract(camera)).normalize().scale(width);
            float v0=(float)(t0*vScale+vOffset),v1=(float)(t1*vScale+vOffset);
            double al0=alpha*profile.applyAsDouble(t0),al1=alpha*profile.applyAsDouble(t1);
            vertex(a.x-side.x,a.y-side.y,a.z-side.z,0,v0,rgb,al0);vertex(a.x+side.x,a.y+side.y,a.z+side.z,1,v0,rgb,al0);
            vertex(b.x+side.x,b.y+side.y,b.z+side.z,1,v1,rgb,al1);vertex(b.x-side.x,b.y-side.y,b.z-side.z,0,v1,rgb,al1);
        }
    }
    /** Open cylinder of light; the texture scrolls upward. */
    void column(Vec3 base,double radius,double height,int sides,float vOffset,int rgb,double alpha){
        for(int i=0;i<sides;i++){
            double a=i*Math.PI*2/sides,b=(i+1)*Math.PI*2/sides;
            Vec3 p=base.add(Math.cos(a)*radius,0,Math.sin(a)*radius),q=base.add(Math.cos(b)*radius,0,Math.sin(b)*radius);
            float u0=(float)i/sides*3,u1=(float)(i+1)/sides*3;
            vertex(p.x,p.y,p.z,u0,vOffset+1,rgb,alpha);vertex(q.x,q.y,q.z,u1,vOffset+1,rgb,alpha);
            vertex(q.x,q.y+height,q.z,u1,vOffset,rgb,0);vertex(p.x,p.y+height,p.z,u0,vOffset,rgb,0);
        }
    }
    /** A sprite from a sub-rectangle of the texture, lying flat and turned in place. */
    void flat(Vec3 center,double radius,double spin,float u0,float v0,float u1,float v1,int rgb,double alpha){
        Vec3[] k=new Vec3[4];
        for(int i=0;i<4;i++){double a=spin+Math.PI/4+i*Math.PI/2;k[i]=center.add(Math.cos(a)*radius*Math.sqrt(2),0,Math.sin(a)*radius*Math.sqrt(2));}
        quad(k[0],k[1],k[2],k[3],u0,v0,u1,v1,rgb,alpha);
    }
    void billboard(Vec3 p,Quaternionf facing,double size,double spin,float u0,float v0,float u1,float v1,int rgb,double alpha){
        float[][] corners={{-1,-1,u0,v1},{-1,1,u0,v0},{1,1,u1,v0},{1,-1,u1,v1}};
        float cs=(float)Math.cos(spin),sn=(float)Math.sin(spin);
        for(float[] c:corners){
            Vector3f v=new Vector3f((c[0]*cs-c[1]*sn)*(float)size,(c[0]*sn+c[1]*cs)*(float)size,0).rotate(facing);
            vertex(p.x+v.x,p.y+v.y,p.z+v.z,c[2],c[3],rgb,alpha);
        }
    }
    /** A soft line between two points, turned toward the eye; samples the middle row of a round glow texture. */
    void ribbon(Vec3 a,Vec3 b,Vec3 eye,double width,int rgb,double alpha){
        Vec3 side=b.subtract(a).cross(a.subtract(eye));
        if(side.lengthSqr()<1e-12)return;
        side=side.normalize().scale(width);
        vertex(a.x-side.x,a.y-side.y,a.z-side.z,0,.5f,rgb,alpha);vertex(a.x+side.x,a.y+side.y,a.z+side.z,1,.5f,rgb,alpha);
        vertex(b.x+side.x,b.y+side.y,b.z+side.z,1,.5f,rgb,alpha);vertex(b.x-side.x,b.y-side.y,b.z-side.z,0,.5f,rgb,alpha);
    }
    void end(){end(true);}
    /** Additive light, or ordinary alpha blending for solid-looking sprites such as falling porcelain. */
    void end(boolean additive){
        MeshData mesh=buffer.build();
        if(mesh==null){release();return;}
        RenderSystem.setShader(()->SeaSky.shader!=null?SeaSky.shader:GameRenderer.getPositionTexColorShader());
        RenderSystem.setShaderTexture(0,texture);
        RenderSystem.enableBlend();
        if(additive)RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE);
        else RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);RenderSystem.disableCull();
        BufferUploader.drawWithShader(mesh);
        release();
        RenderSystem.defaultBlendFunc();
    }
    /** The mesh is uploaded and closed by now, so the pooled buffer can take the next batch. */
    private void release(){if(slot>=0)BUSY[slot]=false;}
}
