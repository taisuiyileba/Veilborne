package com.suiyi.veilborne.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.suiyi.veilborne.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Painted ocean dome with slow non-rigid advection and smooth pole/horizon blending. */
final class SeaSky {
    static net.minecraft.client.renderer.ShaderInstance shader;
    static void draw(Matrix4f matrix,double progress,double time,double strength){
        double sea=TideTimeline.sea(progress)*strength;if(sea<.001)return;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        for(int j=0;j<32;j++)for(int i=0;i<64;i++){
            double a=i*Math.PI*2/64,aa=(i+1)*Math.PI*2/64,c=-Math.PI/2+j*Math.PI/32,cc=c+Math.PI/32;
            base(b,matrix,a,c,sea);base(b,matrix,aa,c,sea);base(b,matrix,aa,cc,sea);base(b,matrix,a,cc,sea);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        // Vanilla's textured-color shader discards alpha below 0.1 and cuts a visible ring into the ebb.
        RenderSystem.setShader(()->shader!=null?shader:GameRenderer.getPositionTexColorShader());
        RenderSystem.setShaderTexture(0,Veilborne.id("textures/environment/celestial_sea.png"));
        b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        for(int j=0;j<36;j++)for(int i=0;i<96;i++){
            double a=(double)i/96,aa=(double)(i+1)/96,c=(double)j/36,cc=(double)(j+1)/36;
            vertex(b,matrix,a,c,time,sea,progress);vertex(b,matrix,aa,c,time,sea,progress);vertex(b,matrix,aa,cc,time,sea,progress);vertex(b,matrix,a,cc,time,sea,progress);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }
    private static Vec3 sphere(double az,double alt){double r=TideRenderer.DOME;return new Vec3(Math.cos(az)*Math.cos(alt)*r,Math.sin(alt)*r,Math.sin(az)*Math.cos(alt)*r);}
    private static void base(BufferBuilder b,Matrix4f m,double az,double alt,double sea){
        Vec3 p=sphere(az,alt);double h=Math.max(0,Math.sin(alt));
        b.addVertex(m,(float)p.x,(float)p.y,(float)p.z).setColor((float)(.035-h*.023),(float)(.082-h*.046),(float)(.14-h*.06),(float)(sea*.993));
    }
    private static void vertex(BufferBuilder b,Matrix4f m,double az,double elevation,double time,double sea,double progress){
        Vec3 p=sphere(az*Math.PI*2,elevation*Math.PI*.5);
        double angle=az+time*.00008;
        double folded=1-Math.abs(2*(angle-Math.floor(angle))-1);
        double u=folded+Math.sin(elevation*11+az*17+time*.055)*.004;
        double v=1-elevation+Math.sin(az*13+time*.038+elevation*9)*.006;
        double fade=TideTimeline.smooth(0,.07,elevation)*(1-TideTimeline.smooth(.90,1,elevation));
        double opening=1-TideTimeline.smooth(.03,.20,progress),retreat=TideTimeline.smooth(.85,1,progress);
        double edgeElevation=elevation+Math.sin(az*44+time*.017)*.025+Math.sin(az*94-time*.01)*.012;
        fade*=TideTimeline.smooth(Math.max(opening,retreat)*.92-.16,Math.max(opening,retreat)*.92+.12,edgeElevation);
        float light=(float)(.72+.24*TideConfig.BRIGHTNESS.get());
        b.addVertex(m,(float)p.x,(float)p.y,(float)p.z).setUv((float)u,(float)v).setColor(light,light,light,(float)(sea*fade));
    }
}
