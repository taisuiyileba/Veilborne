package com.suiyi.veilborne.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.suiyi.veilborne.*;
import java.util.Random;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The bloom's night: a violet dome that spreads outward from the moon and later withdraws into it, a painted
 * cloud layer turned so its lit side faces the moon, and stars. By day the same sky is pale lilac and silver.
 */
final class MoonSky {
    private static final net.minecraft.resources.ResourceLocation SKY=Veilborne.id("textures/environment/moon_sky.png");
    /** Angular reach of the moon's night: grows through the cracking, shrinks back when the shell closes. */
    static double reach(double p){return 3.6*TideTimeline.smooth(.03,.24,p)*(1-TideTimeline.smooth(.87,.99,p));}
    static double reveal(Vec3 dir,Vec3 moon,double reach,double az,double time){
        double ang=Math.acos(Mth.clamp(dir.dot(moon),-1,1));
        double edge=ang+Math.sin(az*7+time*.05)*.06+Math.sin(az*17-time*.03)*.03;
        return 1-TideTimeline.smooth(reach-.55,reach,edge);
    }
    static void draw(Matrix4f m,double p,double time,double s,Vec3 moon,double day){
        double veil=MoonTimeline.veil(p)*s;
        // The vanilla stars are hidden together with the vanilla moon (SkyVeilMixin), so these take their place on
        // the same curve from the first moment of the halo, before the violet night has spread.
        double starlight=MoonTimeline.moon(p)*s;
        if(veil<.001&&starlight<.001)return;
        double reach=reach(p);
        BufferBuilder b;
        if(veil>=.001){
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
            for(int j=0;j<32;j++)for(int i=0;i<64;i++){
                double a=i*Math.PI*2/64,aa=(i+1)*Math.PI*2/64,c=-Math.PI/2+j*Math.PI/32,cc=c+Math.PI/32;
                base(b,m,a,c,veil,moon,reach,day,time);base(b,m,aa,c,veil,moon,reach,day,time);base(b,m,aa,cc,veil,moon,reach,day,time);base(b,m,a,cc,veil,moon,reach,day,time);
            }
            BufferUploader.drawWithShader(b.buildOrThrow());
            // Painted clouds and petal haze. The panorama is turned so its brightest part sits under the moon.
            double moonAz=Math.atan2(moon.z,moon.x);
            RenderSystem.setShader(()->SeaSky.shader!=null?SeaSky.shader:GameRenderer.getPositionTexColorShader());
            RenderSystem.setShaderTexture(0,SKY);
            b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
            for(int j=0;j<36;j++)for(int i=0;i<96;i++){
                double a=(double)i/96,aa=(double)(i+1)/96,c=(double)j/36,cc=(double)(j+1)/36;
                pano(b,m,a,c,time,veil,moon,moonAz,reach,day);pano(b,m,aa,c,time,veil,moon,moonAz,reach,day);
                pano(b,m,aa,cc,time,veil,moon,moonAz,reach,day);pano(b,m,a,cc,time,veil,moon,moonAz,reach,day);
            }
            BufferUploader.drawWithShader(b.buildOrThrow());
        }
        // Stars, fewer by day and none in the moon's glare.
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        b=Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_COLOR);
        TideMesh mesh=new TideMesh(b,m);
        Random rng=new Random(MoonClient.state.seed()*7+1);
        int stars=380+TideConfig.QUALITY.get()*200;
        for(int i=0;i<stars;i++){
            double az=rng.nextDouble()*Math.PI*2,alt=.05+Math.asin(rng.nextDouble())*.98;
            Vec3 dir=MoonTimeline.sphere(az,alt);
            double glare=TideTimeline.smooth(.93,.985,dir.dot(moon));
            double tw=.3+.4*Math.pow(Math.sin(time*.31+i*1.7),2);
            double a=starlight*(1-day*.92)*tw*(1-glare);
            if(a<.01)continue;
            int color=i%11==0?0xffe0b8:i%3==0?0xd8ccff:0xeef0ff;
            mesh.star(dir.scale(TideRenderer.DOME*.97),.03+rng.nextDouble()*.07,color,a);
        }
        MeshData stars2=b.build();if(stars2!=null)BufferUploader.drawWithShader(stars2);
    }
    private static void base(BufferBuilder b,Matrix4f m,double az,double alt,double veil,Vec3 moon,double reach,double day,double time){
        Vec3 dir=MoonTimeline.sphere(az,alt),p=dir.scale(TideRenderer.DOME);
        double h=Math.max(0,Math.sin(alt)),glow=Math.pow(Math.max(0,dir.dot(moon)),5),halo=Math.pow(Math.max(0,dir.dot(moon)),40);
        double below=alt<0?Math.min(1,-alt*4):0;
        double r=Mth.lerp(day,.11,.64)-h*Mth.lerp(day,.08,.2)+glow*Mth.lerp(day,.12,.14)+halo*.08;
        double g=Mth.lerp(day,.085,.61)-h*Mth.lerp(day,.064,.2)+glow*Mth.lerp(day,.1,.14)+halo*.08;
        double bl=Mth.lerp(day,.2,.8)-h*Mth.lerp(day,.12,.16)+glow*Mth.lerp(day,.2,.12)+halo*.1;
        r*=1-below*.7;g*=1-below*.7;bl*=1-below*.6;
        double a=veil*.993*reveal(dir,moon,reach,az,time);
        b.addVertex(m,(float)p.x,(float)p.y,(float)p.z).setColor((float)r,(float)g,(float)bl,(float)a);
    }
    private static void pano(BufferBuilder b,Matrix4f m,double u,double elevation,double time,double veil,Vec3 moon,double moonAz,double reach,double day){
        double az=u*Math.PI*2,alt=elevation*Math.PI*.5;
        Vec3 dir=MoonTimeline.sphere(az,alt),p=dir.scale(TideRenderer.DOME*.99);
        double tu=u-moonAz/(Math.PI*2)+.5+time*.00006;
        // A slow ripple in the clouds; a whole number of waves around the sky, so azimuth 0 has no step.
        double tv=1-elevation+Math.sin(u*Math.PI*4+time*.03+elevation*9)*.004;
        // The painted source carries its own zenith cap (tools/art/moon.py), so only a little of the dome shows there.
        double fade=TideTimeline.smooth(0,.07,elevation)*(1-.3*TideTimeline.smooth(.92,1,elevation));
        double a=veil*fade*reveal(dir,moon,reach,az,time)*(1-day*.35);
        float light=(float)Mth.lerp(day,.8+.18*TideConfig.BRIGHTNESS.get(),1.15);
        b.addVertex(m,(float)p.x,(float)p.y,(float)p.z).setUv((float)tu,(float)tv).setColor(Math.min(1,light),Math.min(1,light),Math.min(1,light*1.02f),(float)a);
    }
    private MoonSky(){}
}
