package com.suiyi.veilborne.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.suiyi.veilborne.*;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class TideRenderer {
    private static final ResourceLocation LIGHTFALL=Veilborne.id("textures/environment/lightfall.png");
    private static final ResourceLocation STAR=Veilborne.id("textures/environment/star.png");
    private static final ResourceLocation RING=Veilborne.id("textures/environment/tide_ring.png");
    private static final ResourceLocation GLOW=Veilborne.id("textures/environment/glow.png");
    /** Camera-centred sky geometry stays well inside the shortest far plane (two-chunk render distance). */
    static final double DOME=110;

    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        var stage=e.getStage();
        // Additive glow particles leave their blend function behind; restore it for whatever renders next.
        if(stage==RenderLevelStageEvent.Stage.AFTER_PARTICLES)RenderSystem.defaultBlendFunc();
        if(TideClient.state==null||TideClient.strength()<.001)return;
        boolean sky=stage==RenderLevelStageEvent.Stage.AFTER_SKY;
        boolean world=stage==RenderLevelStageEvent.Stage.AFTER_ENTITIES;
        boolean ground=stage==RenderLevelStageEvent.Stage.AFTER_PARTICLES;
        if(!sky&&!world&&!ground)return;
        float partial=e.getPartialTick().getGameTimeDeltaPartialTick(false);
        double p=TideClient.progress(partial),s=TideClient.strength(),time=(TideClient.previousAge+partial)/20;
        Vec3 camera=e.getCamera().getPosition();
        if(world){
            if(TideConfig.SKY.get())WhaleRenderer.render(new Matrix4f(e.getModelViewMatrix()),camera,p,time,TideTimeline.whale(p)*s);
            return;
        }
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();RenderSystem.depthMask(false);
        if(sky){
            RenderSystem.disableDepthTest();
            if(TideConfig.SKY.get()){
                SeaSky.draw(new Matrix4f(e.getModelViewMatrix()),p,time,s);
                BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_COLOR);
                // LevelRenderer installs its view transform globally only after the sky pass.
                sky(new TideMesh(b,new Matrix4f(e.getModelViewMatrix())),p,time,s);
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                MeshData mesh=b.build();if(mesh!=null)BufferUploader.drawWithShader(mesh);
            }
        }else{
            RenderSystem.enableDepthTest();
            Matrix4f identity=new Matrix4f();
            Quaternionf facing=e.getCamera().rotation();
            BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_COLOR);
            ground(new TideMesh(b,new Matrix4f().translate((float)-camera.x,(float)-camera.y,(float)-camera.z)),p,time,s,partial);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            MeshData mesh=b.build();if(mesh!=null)BufferUploader.drawWithShader(mesh);
            if(TideConfig.SKY.get())lightfalls(identity,camera,facing,p,time,s);
            encounters(identity,camera,facing,p,time,s);
        }
        RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();
    }
    private static Vec3 sphere(double az,double alt,double r){return new Vec3(Math.cos(az)*Math.cos(alt)*r,Math.sin(alt)*r,Math.sin(az)*Math.cos(alt)*r);}

    private static void sky(TideMesh m,double p,double time,double strength){
        double sea=TideTimeline.sea(p)*strength;
        if(sea<.001)return;
        Random rng=new Random(TideClient.state.seed());
        int stars=350+TideConfig.QUALITY.get()*250;
        for(int i=0;i<stars;i++){
            double az=rng.nextDouble()*Math.PI*2,alt=.03+rng.nextDouble()*1.5;
            Vec3 v=sphere(az,alt,DOME*.97);double r=(.03+rng.nextDouble()*.08);
            double alpha=sea*(.32+.35*Math.pow(Math.sin(time*.28+i),2));
            m.star(v,r,i%9==0?0xffd8a0:0xa5d9fa,alpha);
        }
        // Distant tidal curtains near the horizon: broad soft bands with narrow bright flow lines.
        for(int layer=0;layer<3;layer++){
            for(int i=0;i<128;i++){
                double a=i*Math.PI*2/128,b=(i+1)*Math.PI*2/128;
                double alt=.10+layer*.105+Math.sin(a*2+time*.017+layer*.5)*.065+Math.sin(a*5-time*.012)*.022;
                double alt2=.10+layer*.105+Math.sin(b*2+time*.017+layer*.5)*.065+Math.sin(b*5-time*.012)*.022;
                int color=layer%3==0?0x617bba:0x247d99;
                for(int glow=0;glow<3;glow++){
                    double width=.015+glow*.033;
                    m.quad(sphere(a,alt-width,DOME*.93),sphere(b,alt2-width,DOME*.93),sphere(b,alt2+width,DOME*.93),sphere(a,alt+width,DOME*.93),color,sea*(.018-glow*.005));
                }
                m.line(sphere(a,alt,DOME*.91),sphere(b,alt2,DOME*.91),.02,layer%2==0?0x92d9da:0x739dc9,sea*.06);
            }
        }
        // A luminous haze band along the horizon, so the sea's edge glows instead of ending in black.
        for(int i=0;i<96;i++){
            double a=i*Math.PI*2/96,b=(i+1)*Math.PI*2/96;
            double breathe=.75+.25*Math.sin(a*3+time*.05);
            for(int k=0;k<3;k++){
                double lo=-.02+k*.035,hi=lo+.12+k*.05;
                m.quad(sphere(a,lo,DOME*.9),sphere(b,lo,DOME*.9),sphere(b,hi,DOME*.9),sphere(a,hi,DOME*.9),k==0?0x2a8ea3:0x1d5f86,sea*.045*breathe);
            }
        }
    }

    /** 光瀑: soft falls pour from the sea overhead and break into drifting star dust well above the ground. */
    private static void lightfalls(Matrix4f matrix,Vec3 camera,Quaternionf facing,double p,double time,double strength){
        double sea=TideTimeline.sea(p)*strength*TideConfig.BRIGHTNESS.get();if(sea<.01)return;
        var center=TideClient.state.center();
        Vec3 origin=new Vec3(center.getX(),center.getY()+136,center.getZ()+30).subtract(camera);
        int falls=4+TideConfig.QUALITY.get()*2;
        TideFx fx=TideFx.begin(LIGHTFALL,matrix);
        for(int k=0;k<falls;k++){
            double a=k*2.399+TideClient.state.seed()%50,r=48+k*17;
            double pulse=.7+.3*Math.sin(time*.21+k*1.7);
            double reveal=TideTimeline.smooth(.1+k*.012,.2+k*.012,p);
            Vec3 top=origin.add(Math.cos(a)*r,Math.sin(k*1.3)*6,Math.sin(a)*r);
            double length=58+k%3*9;
            java.util.function.DoubleUnaryOperator profile=t->TideTimeline.smooth(0,.12,t)*(1-TideTimeline.smooth(.55,1,t));
            fx.curtain(top,length,12,Vec3.ZERO,(float)(-time*.05-k*.3),1.4f,0x5fc4d6,sea*reveal*pulse*.42,profile,2.5,time+k);
            fx.curtain(top,length*.92,3.2,Vec3.ZERO,(float)(-time*.11-k*.7),2.2f,0xb8f4f4,sea*reveal*pulse*.7,profile,2.5,time+k);
        }
        fx.end();
        TideFx dust=TideFx.begin(STAR,matrix);
        for(int k=0;k<falls;k++){
            double a=k*2.399+TideClient.state.seed()%50,r=48+k*17;
            Vec3 top=origin.add(Math.cos(a)*r,Math.sin(k*1.3)*6,Math.sin(a)*r);
            double reveal=TideTimeline.smooth(.1+k*.012,.2+k*.012,p);
            for(int d=0;d<14;d++){
                double age=(time*.09+d*.0713+k*.13)%1;
                Vec3 v=top.add(Math.sin(d*1.7+k+time*.3)*(1.5+age*9),-40-age*44,Math.cos(d*2.3+k+time*.25)*(1.5+age*9));
                dust.billboard(v,facing,.55+age*.5,time*.5+d,d%4==0?0xffe0a8:0x9ae9f2,sea*reveal*Math.sin(age*Math.PI)*.8);
            }
        }
        dust.end();
    }

    private static void encounters(Matrix4f matrix,Vec3 camera,Quaternionf facing,double p,double time,double strength){
        var d=TideClient.state;
        if(!d.vortex().equals(BlockPos.ZERO)){
            // 浮潮涡: a golden sigil warns first; a rising column carries the tide once it turns cyan.
            Vec3 v=d.vortex().getBottomCenter().add(0,.05,0).subtract(camera);
            double t=TideClient.age-d.vortexAt();
            boolean warning=t<100;
            double in=TideTimeline.smooth(0,20,t)*(1-TideTimeline.smooth(290,340,t));
            double beat=warning?.75+.25*Math.sin(t*.35):1;
            TideFx ring=TideFx.begin(RING,matrix);
            ring.disc(v,3.75,time*(warning?.25:.8),warning?0xffb948:0x7fe6ef,strength*in*beat*.95);
            ring.disc(v.add(0,.02,0),3.2,-time*.4,warning?0xa8864a:0x3fa9c2,strength*in*.45);
            ring.end();
            TideFx glow=TideFx.begin(GLOW,matrix);
            glow.disc(v.add(0,.03,0),4.6,0,warning?0x8a6a30:0x2a7f94,strength*in*.5);
            glow.end();
            if(t>=80){
                double lift=TideTimeline.smooth(80,110,t)*(1-TideTimeline.smooth(270,320,t));
                TideFx column=TideFx.begin(LIGHTFALL,matrix);
                column.column(v,3.5,8.5,20,(float)(time*.35),0x6fe1ec,strength*lift*.42);
                column.column(v,2.2,6,14,(float)(time*.55+.3),0xc9fbff,strength*lift*.28);
                column.end();
            }
            TideFx motes=TideFx.begin(STAR,matrix);
            for(int i=0;i<22;i++){
                double a=time*(warning?.6:1.6)+i*Math.PI*2/22,r=1.1+Math.sin(i*1.9+time*.2)*1.6+1.2;
                double rise=((time*(warning?.25:.7)+i*.137)%1);
                motes.billboard(v.add(Math.cos(a)*r,rise*(warning?2.5:8),Math.sin(a)*r),facing,.14+.1*Math.sin(i+time*3),time+i,warning?0xffd996:0xb7ffff,strength*in*Math.sin(rise*Math.PI));
            }
            motes.end();
        }
        if(!d.landing().equals(BlockPos.ZERO)&&p<.85){
            // 鲸落一鳞: a beam marks where the scale will land; the scale falls as a star with a trail.
            Vec3 v=d.landing().getBottomCenter().subtract(camera);
            double fall=1-TideTimeline.smooth(0,160,TideClient.age-d.landingAt());
            double fade=1-TideTimeline.smooth(.8,.85,p);
            Vec3 scale=v.add(0,fall*110+.5,0);
            TideFx beam=TideFx.begin(LIGHTFALL,matrix);
            java.util.function.DoubleUnaryOperator profile=t->Math.pow(1-t,1.5)*TideTimeline.smooth(0,.03,t);
            beam.curtain(v.add(0,56,0),56,1.8,Vec3.ZERO,(float)(time*.2),1,0x7fdce6,strength*fade*(.35+.45*(1-fall)),t->profile.applyAsDouble(1-t),0,time);
            beam.curtain(v.add(0,40,0),40,.5,Vec3.ZERO,(float)(time*.35),1,0xd9fbff,strength*fade*(.3+.5*(1-fall)),t->profile.applyAsDouble(1-t),0,time);
            beam.curtain(scale.add(0,14,0),14,.6+fall*.8,Vec3.ZERO,(float)(time*.6),1,0xffe0a3,strength*fade*fall*.9,t->TideTimeline.smooth(0,1,t),0,time);
            beam.end();
            TideFx star=TideFx.begin(STAR,matrix);
            star.billboard(scale,facing,.8+fall*3.2,time*.7,0xffe6b0,strength*fade);
            star.billboard(scale,facing,2.2+fall*6,-time*.2,0x8fd9e6,strength*fade*.4);
            star.end();
            TideFx ring=TideFx.begin(RING,matrix);
            ring.disc(v.add(0,.04,0),2.3,time*.3,0xffd79d,strength*fade*(1-fall)*.8);
            ring.end();
        }
    }

    private static void ground(TideMesh m,double p,double time,double strength,float partial){
        double sea=TideTimeline.sea(p)*strength*TideConfig.BRIGHTNESS.get();
        if(!TideConfig.GROUND.get())return;
        for(var ripple:TideClient.ripples){
            double t=(TideClient.ticks+partial-ripple.born())/65;
            m.ring(ripple.pos(),.08+t*(ripple.water()?2.1:.9),.014,0x8ae5eb,sea*(1-t)*.45,time);
            if(ripple.water())m.ring(ripple.pos(),.04+t*1.3,.009,0xe4d7ab,sea*(1-t)*.3,-time);
        }
        // The whale's passage sweeps a soft pool of blue light across the land beneath it.
        double whale=TideTimeline.whale(p)*strength;
        Vec3 track=whale>.01?WhaleRenderer.position(p,time):null;
        for(var pos:TideClient.surfaces){
            long hash=pos.asLong()*31;double phase=(hash&255)*.03;
            double wake=track==null?0:whale*Math.exp(-(Math.pow(pos.getX()-track.x,2)+Math.pow(pos.getZ()-track.z+30,2))/(46*46));
            double alpha=sea*(.055+.09*Math.pow(Math.sin(time*.4+phase),4)+wake*.3);
            Vec3 v=pos.getBottomCenter().add(0,.025,0);
            double r=.3+Math.sin(time*.17+phase)*.17;
            m.ring(v,r,.018,0x72cedb,alpha,phase+time*.2);
            m.line(v.add(-.4,0,-.2),v.add(.4,0,.2),.008,0xb5e8e5,alpha*.7);
            if(wake>.05)m.ring(v,.6+wake*.4,.03,0x9fe7f0,sea*wake*.35,-time*.3);
            if(!Minecraft.getInstance().level.getFluidState(pos.below()).isEmpty()){
                double a=time*.025+phase;
                Vec3 u=new Vec3(Math.cos(a)*.13,0,Math.sin(a)*.13),w=new Vec3(-u.z,0,u.x);
                Vec3 c=v.add(Math.sin(phase)*.2,.008,Math.cos(phase)*.2);
                m.quad(c.add(u),c.add(w),c.subtract(u),c.subtract(w),0xb5ecf5,sea*(.15+.1*Math.sin(time+phase)));
            } else if((hash&15)==0){
                double t=(time*.15+phase)%1;
                Vec3 stone=v.add(Math.sin(time*.2+phase)*.3,t*1.8,Math.cos(phase)*.3);
                m.box(stone.x,stone.y,stone.z,.10,.08,.12,0x7f9dac,sea*Math.sin(t*Math.PI)*.25);
            }
        }
        for(var wall:TideClient.walls){
            Vec3 normal=Vec3.atLowerCornerOf(wall.face().getNormal());
            Vec3 center=wall.pos().getCenter().add(normal.scale(.504));
            Vec3 u=new Vec3(-normal.z,0,normal.x);
            double phase=wall.pos().asLong()%103*.09;
            for(int j=0;j<3;j++){
                double y=Math.sin(time*.27+phase+j*1.5)*.24+j*.14-.13;
                Vec3 a=center.add(u.scale(-.43)).add(0,y,0),b=center.add(u.scale(.43)).add(0,y+Math.sin(time*.3+phase)*.17,0);
                m.line(a,b,.014,0x66d4e2,sea*.13);
            }
        }
        // Small schools below the ceiling; slim diamonds and forked tails remain legible in motion.
        for(int i=0;i<9;i++){
            double a=time*.065+i*2.399,r=12+(i%3)*7;
            Vec3 fish=new Vec3(TideClient.state.center().getX()+Math.cos(a)*r,TideClient.state.center().getY()+12+i*.8,TideClient.state.center().getZ()+Math.sin(a)*r);
            Vec3 forward=new Vec3(-Math.sin(a),Math.sin(time*.7+i)*.1,Math.cos(a)),side=new Vec3(forward.z,0,-forward.x);
            m.quad(fish.add(forward.scale(.6)),fish.add(side.scale(.14)),fish.subtract(forward.scale(.5)),fish.subtract(side.scale(.14)),0x99dfec,sea*.45);
            Vec3 tail=fish.subtract(forward.scale(.5));
            m.tri(tail,tail.subtract(forward.scale(.3)).add(side.scale(.22)),tail.subtract(forward.scale(.3)).subtract(side.scale(.22)),0xcbdab5,sea*.4);
        }
    }
    private TideRenderer(){}
}
