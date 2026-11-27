package com.suiyi.veilborne.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.suiyi.veilborne.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The golden needle as geometry: a tube swept along its half circle, round at the waist, flattened around the eye at the back
 * so the slot passes through its thin side, and tapering to a point. Rebuilt every frame from ThreadTimeline's pose. Each ring
 * knows how far above the land it is, so wherever the needle is inside the earth (and the earth there has not been drawn)
 * the shader cuts it away; the land hides the rest by depth.
 */
final class ThreadNeedle {
    static ShaderInstance shader;
    private static final ByteBufferBuilder BYTES=new ByteBufferBuilder(1<<19);
    private static VertexBuffer buffer;

    /** Radius at a point along the needle (0 the eye end, 1 the point), across its thin (b) and wide (n) sides. */
    static double[] section(double t){
        double T=ThreadTimeline.NEEDLE_THICK;
        double cap=Math.sqrt(Mth.clamp(t/.012,0,1));
        double eye=TideTimeline.smooth(.004,.02,t)*(1-TideTimeline.smooth(.075,.11,t));
        double point=t<.68?1:Math.pow(Math.max(0,1-(t-.68)/.32),1.25);
        double r=T*(.04+.96*cap)*Math.max(.02,point);
        return new double[]{r*(1-.38*eye),r*(1+.32*eye)};
    }
    static void draw(ThreadTimeline.Pose pose,Vec3 camera,Matrix4f modelView,Matrix4f projection,double heat,float partial){
        if(shader==null||pose==null||pose.visible()<.002)return;
        int q=TideConfig.QUALITY.get();
        int rings=new int[]{64,96,128}[q],around=new int[]{10,14,18}[q];
        var level=Minecraft.getInstance().level;
        Vec3 b=pose.u().cross(pose.w()).normalize();
        double baseY=ThreadClient.state.center().getY();
        BufferBuilder builder=new BufferBuilder(BYTES,VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        Vec3[][] ring=new Vec3[rings+1][around+1];Vec3[][] normal=new Vec3[rings+1][around+1];float[] clear=new float[rings+1];
        for(int i=0;i<=rings;i++){
            double t=i/(double)rings,beta=pose.tip()-Math.PI+Math.PI*t;
            Vec3 c=pose.at(beta),n=c.subtract(pose.o()).normalize();
            double[] r=section(t);
            // How far above the land this ring is (only matters near it).
            double ground=baseY;
            if(c.y<baseY+90&&level.hasChunk(Mth.floor(c.x)>>4,Mth.floor(c.z)>>4))ground=Surface.top(level,Mth.floor(c.x),Mth.floor(c.z));
            clear[i]=(float)(c.y-ground);
            for(int k=0;k<=around;k++){
                double a=k/(double)around*Math.PI*2;
                Vec3 off=n.scale(Math.cos(a)*r[1]).add(b.scale(Math.sin(a)*r[0]));
                ring[i][k]=c.add(off);
                normal[i][k]=n.scale(Math.cos(a)/Math.max(.01,r[1])).add(b.scale(Math.sin(a)/Math.max(.01,r[0]))).normalize();
                if(i==rings)normal[i][k]=pose.heading(beta);
            }
        }
        int alpha=(int)Math.round(Mth.clamp(pose.visible(),0,1)*255);
        for(int i=0;i<rings;i++)for(int k=0;k<around;k++){
            int[][] idx={{i,k},{i+1,k},{i+1,k+1},{i,k+1}};
            for(int[] v:idx){
                Vec3 p=ring[v[0]][v[1]],n=normal[v[0]][v[1]];
                double t=v[0]/(double)rings;
                float cl=clear[v[0]]+(float)(p.y-pose.at(pose.tip()-Math.PI+Math.PI*t).y);
                int red=(int)Mth.clamp((.5+cl/64)*255,0,255);
                int h=(int)Mth.clamp(heat*TideTimeline.smooth(.7,1,t)*255,0,255);
                builder.addVertex((float)(p.x-camera.x),(float)(p.y-camera.y),(float)(p.z-camera.z)).setUv((float)t,v[1]/(float)around)
                    .setColor(red,0,h,alpha).setNormal((float)n.x,(float)n.y,(float)n.z);
            }
        }
        MeshData mesh=builder.build();
        if(mesh==null)return;
        if(buffer==null)buffer=new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        buffer.bind();buffer.upload(mesh);
        shader.safeGetUniform("FarFit").set(Minecraft.getInstance().gameRenderer.getDepthFar()*.9f);
        RenderSystem.disableCull();RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.disableBlend();RenderSystem.depthFunc(515);
        buffer.drawWithShader(modelView,projection,shader);
        VertexBuffer.unbind();
        RenderSystem.enableCull();
    }
}
