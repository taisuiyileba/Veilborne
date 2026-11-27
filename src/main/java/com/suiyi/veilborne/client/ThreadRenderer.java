package com.suiyi.veilborne.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.suiyi.veilborne.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * 绯线缝世 on screen. Sky: one pass lays the cloth over the sky (thread_sky.fsh). World: the golden needle, depth-tested so the
 * land and the trees stand in front of it and it vanishes into the earth. Threads: the sutures across the wound and the
 * dim returns behind it, the thread that follows the needle's eye, the world stitch's two threads, the strikes' threads and
 * the needle-light that falls down them, the knot, the strings on every living thing during the pull, and the lamps' threads.
 * Ground and walls are ThreadGround; the moment's film is ThreadOverlay.
 */
public final class ThreadRenderer {
    static ShaderInstance skyShader;
    static final ResourceLocation SKY=Veilborne.id("textures/environment/thread_sky.png"),GLOW=Veilborne.id("textures/environment/glow.png"),
        STAR=Veilborne.id("textures/environment/star.png");
    static final int SCARLET=0xe8283a,DEEP=0xa0141f,GOLD=0xffc86a,PALE=0xffe6c0;

    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        var stage=e.getStage();
        float partial=e.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera=e.getCamera().getPosition();
        double time=(ThreadClient.ticks+partial)/20.0;
        Matrix4f view=new Matrix4f(e.getModelViewMatrix()),proj=new Matrix4f(e.getProjectionMatrix());
        boolean on=ThreadClient.active();
        if(stage==RenderLevelStageEvent.Stage.AFTER_SKY){
            if(on&&TideConfig.SKY.get())sky(view,proj,camera,partial,time);
        }else if(stage==RenderLevelStageEvent.Stage.AFTER_ENTITIES){
            if(!on)return;
            double p=ThreadClient.progress(partial);
            if(TideConfig.SKY.get()){
                ThreadTimeline.Pose pose=ThreadClient.needle(partial);
                if(pose!=null&&pose.visible()>.002&&ThreadNeedle.shader!=null){
                    seam(ThreadNeedle.shader,camera,p,time);
                    needleLook(pose,camera,p,time,partial);
                    ThreadNeedle.draw(pose,camera,view,proj,heat(p),partial);
                }
            }
            ThreadGround.entities(camera,p,ThreadClient.strength(),time,partial);
        }else if(stage==RenderLevelStageEvent.Stage.AFTER_PARTICLES){
            Quaternionf facing=e.getCamera().rotation();
            RenderSystem.enableBlend();RenderSystem.disableCull();RenderSystem.depthMask(false);RenderSystem.enableDepthTest();
            double p=on?ThreadClient.progress(partial):0;
            if(ThreadLines.shader!=null){
                seam(ThreadLines.shader,camera,p,time);
                ThreadLines lines=ThreadLines.begin();
                if(on)threads(lines,camera,p,ThreadClient.strength(),time,partial);
                lamps(lines,camera,time);
                spirits(lines,camera,time,partial);
                lines.end(view,proj);
            }
            if(on)ThreadGround.render(camera,facing,p,ThreadClient.strength(),time,partial);
            if(on)skyGlints(camera,facing,p,ThreadClient.strength(),time,partial);
            spiritGlow(camera,facing,time,partial);
            RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();
        }else if(stage==RenderLevelStageEvent.Stage.AFTER_LEVEL){
            ThreadOverlay.level(partial);
        }
    }

    // ------------------------------------------------------------------ shared uniforms
    static void seam(ShaderInstance sh,Vec3 camera,double p,double time){
        var s=ThreadClient.seam();
        if(s==null){
            sh.safeGetUniform("DomeInfo").set(0f,-1e5f,0f,1f);sh.safeGetUniform("SeamState").set(0f,-10f,0f,(float)(time%3600));return;
        }
        Vec3 c=s.center().subtract(camera);
        sh.safeGetUniform("DomeInfo").set((float)c.x,(float)c.y,(float)c.z,(float)ThreadTimeline.DOME_RADIUS);
        sh.safeGetUniform("SeamM").set((float)s.m().x,(float)s.m().y,(float)s.m().z,(float)ThreadTimeline.ARC);
        sh.safeGetUniform("SeamH").set((float)s.h().x,(float)s.h().y,(float)s.h().z,(float)ThreadTimeline.WIDTH);
        sh.safeGetUniform("SeamState").set((float)ThreadTimeline.tear(p),(float)ThreadTimeline.closeFront(p),(float)s.key(),(float)(time%3600));
        double sewn=p<ThreadTimeline.SEW_START?0:TideTimeline.smooth(0,.1,ThreadTimeline.sew(p))*(1-ThreadTimeline.fall(p));
        sh.safeGetUniform("SeamLook").set((float)ThreadTimeline.LINE,(float)Math.min(1,ThreadTimeline.tug(p)*1.2+.2*sewn),(float)ThreadTimeline.cool(p),(float)ThreadTimeline.starband(p));
    }

    // ------------------------------------------------------------------ the sky
    private static void sky(Matrix4f view,Matrix4f proj,Vec3 camera,float partial,double time){
        var st=ThreadClient.state;
        double p=ThreadClient.progress(partial),s=ThreadClient.strength(),day=ThreadClient.dayness(partial);
        double veil=ThreadTimeline.veil(p)*s,hair=ThreadTimeline.hairline(p)*s,band=ThreadTimeline.starband(p)*(1-TideTimeline.smooth(.985,1,p))*s;
        if(veil<.002&&hair<.002&&band<.002||skyShader==null)return;
        var mc=Minecraft.getInstance();
        Matrix4f projView=new Matrix4f(proj).mul(view),inv=new Matrix4f(projView).invert();
        skyShader.safeGetUniform("InvProjView").set(inv);
        seam(skyShader,camera,p,time);
        skyShader.safeGetUniform("SkyLook").set((float)(time%3600),(float)day,(float)(.8+.2*TideConfig.BRIGHTNESS.get()),(float)Math.min(1,veil));
        skyShader.safeGetUniform("Omen").set((float)hair,(float)ThreadTimeline.tug(p),(float)band,0f);
        double since=(ThreadClient.ticks+partial-ThreadClient.flashAt)/20.0;
        skyShader.safeGetUniform("Flash").set((float)since,(float)ThreadClient.flashAzimuth,since<6?(float)(ThreadClient.flashStrength*s):0f,0f);
        Vec3 palm=palm(p);
        skyShader.safeGetUniform("Hand").set((float)palm.x,(float)palm.y,(float)palm.z,(float)(handStrength(p)*s));
        float angle=mc.level.getTimeOfDay(partial)*(float)Math.PI*2;
        skyShader.safeGetUniform("SunDir").set((float)-Math.sin(angle),(float)Math.cos(angle),0f,1-mc.level.getRainLevel(partial));
        RenderSystem.setShaderTexture(0,SKY);
        RenderSystem.setShader(()->skyShader);
        RenderSystem.enableBlend();RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();RenderSystem.depthMask(false);
        BufferBuilder q=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION);
        q.addVertex(-1,-1,0);q.addVertex(1,-1,0);q.addVertex(1,1,0);q.addVertex(-1,1,0);
        BufferUploader.drawWithShader(q.buildOrThrow());
        RenderSystem.defaultBlendFunc();RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.disableBlend();
    }
    /** Where the hand's shadow lies on the cloth: it comes in low from one side and closes over the seam's middle. */
    static Vec3 palm(double p){
        var s=ThreadClient.seam();
        double h=Mth.clamp(ThreadTimeline.hand(p),0,1);
        Vec3 from=s.dir(.3,.95),to=s.dir(.02,.16);
        double t=TideTimeline.smooth(0,1,h);
        return from.scale(1-t).add(to.scale(t)).normalize();
    }
    static double handStrength(double p){
        if(p<ThreadTimeline.SEW_START)return 0;
        double q=ThreadTimeline.sew(p);
        return TideTimeline.smooth(ThreadTimeline.HAND0,ThreadTimeline.HAND0+.03,q)*(1-TideTimeline.smooth(ThreadTimeline.TUG1,ThreadTimeline.TUG1+.06,q));
    }

    // ------------------------------------------------------------------ the needle's look
    private static void needleLook(ThreadTimeline.Pose pose,Vec3 camera,double p,double time,float partial){
        var sh=ThreadNeedle.shader;var s=ThreadClient.seam();
        Vec3 mid=pose.at(pose.tip()-Math.PI/2);
        Vec3 light=ThreadTimeline.middle(s).subtract(mid).normalize().add(0,.4,0).normalize();
        double day=ThreadClient.dayness(partial),dist=mid.distanceTo(camera);
        sh.safeGetUniform("NeedleLight").set((float)light.x,(float)light.y,(float)light.z,(float)(.7+.5*ThreadTimeline.tug(p)));
        sh.safeGetUniform("NeedleSky").set((float)Mth.lerp(day,.3,.86),(float)Mth.lerp(day,.08,.5),(float)Mth.lerp(day,.09,.4),(float)day);
        sh.safeGetUniform("NeedleLook").set((float)(time%3600),(float)Mth.clamp((dist-150)/3000,0,.14),(float)(.8+.2*TideConfig.BRIGHTNESS.get()),0f);
    }
    /** The point glows for a while after it has gone through: the cloth when it comes out of the wound, the earth at the far foot. */
    static double heat(double p){
        double c=ThreadTimeline.clock(p);
        var st=ThreadClient.state;
        double secondsPer=ThreadTimeline.span()*st.duration()/20;
        double h=0;
        if(c>=0&&c<ThreadTimeline.STITCHES){
            int k=(int)Math.floor(c);double u=c-k,exit=ThreadTimeline.exitAt(k);
            h=Math.max(Math.exp(-u*secondsPer*.8),u>=exit?Math.exp(-(u-exit)*secondsPer*.5):0);
        }
        if(st.hasLand()&&p>=ThreadTimeline.SEW_START){
            double b=ThreadTimeline.strikeB(st.footA(),st.footB()),since=(p-b)*st.duration()/20;
            if(since>=0)h=Math.max(h,Math.exp(-since*.35));
        }
        return h;
    }

    // ------------------------------------------------------------------ threads
    private static final double R=ThreadTimeline.DOME_RADIUS;
    /** A path over the cloth between two points of it, at radius r: great-circle, so it keeps to the cloth. */
    static void onCloth(List<Vec3> out,Vec3 a,Vec3 b,double r,int n,Vec3 camera){
        Vec3 c=ThreadClient.seam().center();
        Vec3 da=a.subtract(c).normalize(),db=b.subtract(c).normalize();
        for(int i=0;i<=n;i++){double t=i/(double)n;out.add(c.add(da.scale(1-t).add(db.scale(t)).normalize().scale(r)).subtract(camera));}
    }
    /** From a point of the cloth to a point behind it, never dipping in front of the cloth on the way. */
    static void behind(List<Vec3> out,Vec3 a,Vec3 b,int n,Vec3 camera){
        Vec3 c=ThreadClient.seam().center();
        Vec3 da=a.subtract(c),db=b.subtract(c);
        double ra=Math.max(R+3,da.length()),rb=Math.max(R+3,db.length());
        da=da.normalize();db=db.normalize();
        for(int i=0;i<=n;i++){double t=i/(double)n;out.add(c.add(da.scale(1-t).add(db.scale(t)).normalize().scale(Mth.lerp(t,ra,rb)+Math.sin(Math.PI*t)*2)).subtract(camera));}
    }
    private static void threads(ThreadLines L,Vec3 camera,double p,double s,double time,float partial){
        var st=ThreadClient.state;var seam=ThreadClient.seam();
        double b=TideConfig.BRIGHTNESS.get()*s;
        double cool=ThreadTimeline.cool(p),fall=ThreadTimeline.fall(p);
        int suture=mix(SCARLET,GOLD,cool);
        double fadeOut=1-TideTimeline.smooth(.9,.985,p);
        ThreadTimeline.Pose pose=ThreadClient.needle(partial);
        if(pose!=null&&TideConfig.SKY.get())needleHalo(L,pose,camera,b);
        // The sutures, in stitch order: behind the cloth to each entry hole, across the wound in front, behind again.
        double c=ThreadTimeline.clock(p);
        if(c>=0&&TideConfig.SKY.get()){
            Vec3 prev=seam.point(ThreadTimeline.stitchAlong(0)-ThreadTimeline.spacing()*.5,ThreadTimeline.chord(0,p,false)/R);
            for(int k=0;k<ThreadTimeline.STITCHES;k++){
                double u=c-k;
                if(u<0)break;
                Vec3 a=ThreadTimeline.hole(seam,k,p,true),bb=ThreadTimeline.hole(seam,k,p,false);
                boolean current=u<ThreadTimeline.ROTATE&&p<ThreadTimeline.SEW_START;
                if(current&&u<ThreadTimeline.eyeInAt(k)){
                    if(pose!=null){List<Vec3> back=new ArrayList<>();behind(back,prev,pose.eye(),10,camera);L.strip(back,DEEP,.8*b*fadeOut,.0016,3,true);}
                    break;
                }
                List<Vec3> back=new ArrayList<>();behind(back,prev,a,10,camera);
                L.strip(back,DEEP,.8*b*fadeOut,.0016,3,true);
                double h0=ThreadTimeline.chord(k,p,true);
                ThreadTimeline.Pose circle=ThreadTimeline.skyPose(seam,ThreadTimeline.stitchAlong(k),h0,0,1);
                double b1=ThreadTimeline.entryAngle(h0);
                if(current&&pose!=null){
                    // Following the eye through the front of the cloth.
                    List<Vec3> front=new ArrayList<>();
                    double eye=pose.tip()-Math.PI-2*Math.PI*k;
                    for(int i=0;i<=16;i++)front.add(circle.at(Mth.lerp(i/16.0,b1,eye)).subtract(camera));
                    L.strip(front,SCARLET,b*fadeOut,.0013,2.5,true);
                    break;
                }
                double taut=ThreadTimeline.taut(k,p);
                List<Vec3> bar=new ArrayList<>(),tight=new ArrayList<>();
                onCloth(tight,a,bb,R-1.5,12,camera);
                for(int i=0;i<=12;i++){
                    Vec3 loose=circle.at(Mth.lerp(i/12.0,b1,Math.PI-b1)).subtract(camera);
                    bar.add(loose.lerp(tight.get(i),taut));
                }
                double pulled=.8+.6*ThreadTimeline.tug(p)+.2*Math.sin(time*2-k);
                L.strip(bar,suture,b*pulled*fadeOut*(1-.5*ThreadTimeline.starband(p)),.0015,3,true);
                prev=bb;
            }
            // After the last stitch the thread runs on behind the cloth to wherever the needle is.
            if(c>=ThreadTimeline.STITCHES&&p<ThreadTimeline.SEW_START&&pose!=null){List<Vec3> back=new ArrayList<>();behind(back,prev,pose.eye(),10,camera);L.strip(back,DEEP,.7*b,.0016,3,true);}
        }
        // The world stitch: from the seam's middle down to the land and back.
        if(st.hasLand()&&ThreadTimeline.landLoop(p)&&!ThreadClient.snipped()){
            Vec3 m=ThreadTimeline.middle(seam),m2=ThreadTimeline.middleBack(seam);
            Vec3 in=seam.center().subtract(m).normalize();
            m=m.add(in.scale(1.5));m2=m2.add(in.scale(1.5));
            Vec3 a=st.footA().add(0,.05,0),bb=st.footB().add(0,.05,0);
            double q=ThreadTimeline.sew(p),roll=(q-ThreadTimeline.DESCEND)/(ThreadTimeline.THROUGH-ThreadTimeline.DESCEND);
            double tug=ThreadTimeline.tug(p),glow=b*(1-fall)*(1+1.2*tug);
            if(pose!=null&&(q<ThreadTimeline.DESCEND||q<ThreadTimeline.THROUGH&&roll<ThreadTimeline.landEyeIn(st.footA(),st.footB())))
                vibrating(L,m,pose.eye(),camera,SCARLET,glow,time,0,false);
            else vibrating(L,m.lerp(a,fall*.9),a,camera,SCARLET,glow,time,tug,false);
            if(q>=ThreadTimeline.THROUGH){
                if(q<ThreadTimeline.ASCEND&&pose!=null)vibrating(L,bb,pose.eye(),camera,SCARLET,glow,time,0,false);
                else vibrating(L,m2.lerp(bb,fall*.9),bb,camera,SCARLET,glow,time,tug,false);
            }
        }
        // The strikes: needle-light falls down a new thread; the thread stays, tied between the land and the cloth.
        for(int i=0;i<ThreadData.STRIKES;i++){
            BlockPos g=st.strike(i);
            if(g.equals(BlockPos.ZERO))continue;
            int at=ThreadTimeline.strikeTick(i,st.duration());
            double now=ThreadClient.ageNow(partial);
            if(now<at-24)continue;
            Vec3 foot=Vec3.atBottomCenterOf(g).add(0,.05,0),top=ThreadTimeline.strikeTop(seam,i,p);
            if(now<at){
                double t=TideTimeline.smooth(0,1,(now-(at-24))/24.0);
                t=t*t;
                Vec3 glint=top.lerp(foot,t);
                L.segment(top.subtract(camera),glint.subtract(camera),SCARLET,.1*b,.9*b,.0012,.04,false);
                Vec3 streak=glint.add(top.subtract(glint).normalize().scale(24+30*(1-t)));
                L.segment(streak.subtract(camera),glint.subtract(camera),GOLD,0,1.6*b,.004,.12,false);
                L.segment(streak.subtract(camera),glint.subtract(camera),PALE,0,1.2*b,.0015,.05,false);
            }else vibrating(L,top.lerp(foot,fall*.9),foot,camera,SCARLET,b*.85*(1-fall)*fadeOut,time+i,ThreadTimeline.tug(p)*.5,false);
        }
        // The knot at the seam's middle, and its two loose ends once the thread is cut.
        double knot=ThreadTimeline.knot(p)*b;
        if(knot>.01){
            Vec3 m=ThreadTimeline.middle(seam),n=seam.center().subtract(m).normalize();
            Vec3 t1=seam.tangent(0),t2=n.cross(t1).normalize();
            m=m.add(n.scale(3));
            // Drawn tight: the loops shrink onto the knot as it is pulled, then hold.
            double pull=1-.35*TideTimeline.smooth(ThreadTimeline.KNOT,ThreadTimeline.CUT,p);
            for(int strand=0;strand<2;strand++){
                List<Vec3> ring=new ArrayList<>();
                double r=10*pull,turn=time*.05+strand*.05;
                for(int i=0;i<=72;i++){
                    double a=i/72.0*Math.PI*2;
                    double x=Math.sin(a)+2*Math.sin(2*a),y=Math.cos(a)-2*Math.cos(2*a),z=-Math.sin(3*a);
                    double cx=x*Math.cos(turn)-y*Math.sin(turn),cy=x*Math.sin(turn)+y*Math.cos(turn);
                    ring.add(m.add(t1.scale(cx*r)).add(t2.scale(cy*r*.8)).add(n.scale(z*r*.6+strand*.8)).subtract(camera));
                }
                L.strip(ring,strand==0?suture:GOLD,knot*(strand==0?.95:.6),strand==0?.0024:.0012,strand==0?4.5:2,true);
            }
            if(p>ThreadTimeline.CUT)for(int end=0;end<2;end++){
                // The two cut ends hang down from the knot and drift.
                double cut=TideTimeline.smooth(ThreadTimeline.CUT,ThreadTimeline.CUT+.01,p);
                List<Vec3> tail=new ArrayList<>();
                for(int i=0;i<=16;i++){double t=i/16.0;tail.add(m.add(t1.scale((end==0?-1:1)*(12+t*34*cut))).add(n.scale(t*t*80*cut)).add(t2.scale(Math.sin(time*.8+t*4+end)*t*10)).subtract(camera));}
                L.strip(tail,suture,knot*.8,.002,3.5,true);
            }
        }
        // Strings on every living thing while the thread is pulled: they rise from each head to the seam's middle.
        double strings=ThreadClient.snipped()?0:ThreadTimeline.strings(p)*s;
        if(strings>.01&&TideConfig.THREAD_STRINGS.get()){
            var mc=Minecraft.getInstance();
            Vec3 m=ThreadTimeline.middle(seam);
            List<Entity> tied=new ArrayList<>();
            for(Player pl:mc.level.players())if(!pl.isSpectator()&&pl.distanceToSqr(camera)<64*64)tied.add(pl);
            tied.addAll(mc.level.getEntitiesOfClass(Animal.class,mc.player.getBoundingBox().inflate(32)).stream().limit(20).toList());
            for(Entity en:tied){
                Vec3 head=en.getPosition(partial).add(0,en.getBbHeight()+.35,0);
                if(en==mc.player&&mc.options.getCameraType().isFirstPerson())head=camera.add(0,.6,0);
                double sway=Math.sin(time*1.3+en.getId())*.15;
                L.segment(head.add(sway,0,0).subtract(camera),m.subtract(camera),SCARLET,strings*.95*b,strings*.5*b,.0009,.012,false);
            }
        }
    }
    /** A soft gold light about the needle, the width of the needle again, so it reads as gold on red however far off it is. */
    private static void needleHalo(ThreadLines L,ThreadTimeline.Pose pose,Vec3 camera,double b){
        double vis=Mth.clamp(pose.visible(),0,1)*b;
        if(vis<.01)return;
        int n=30;Vec3 prev=null;double prevR=0;
        for(int i=0;i<=n;i++){
            double t=i/(double)n;
            Vec3 c=pose.at(pose.tip()-Math.PI+Math.PI*t).subtract(camera);
            double r=ThreadNeedle.section(t)[1];
            if(prev!=null)L.segment(prev,c,GOLD,vis*.2,vis*.2,.0045,(r+prevR)*1.25+.4,true);
            prev=c;prevR=r;
        }
    }
    /** The knot's light, and the strikes' falling glints, as sprites: brought inside the far plane at the same apparent size. */
    static void skyGlints(Vec3 camera,Quaternionf facing,double p,double s,double time,float partial){
        var st=ThreadClient.state;var seam=ThreadClient.seam();
        if(seam==null)return;
        double b=TideConfig.BRIGHTNESS.get()*s,far=Minecraft.getInstance().gameRenderer.getDepthFar()*.8;
        TideFx fx=null;
        double knot=ThreadTimeline.knot(p)*b;
        if(knot>.01&&TideConfig.SKY.get()){
            Vec3 m=ThreadTimeline.middle(seam);
            m=m.add(seam.center().subtract(m).normalize().scale(3)).subtract(camera);
            double k=Math.min(1,far/m.length()),flare=1+1.5*Math.exp(-Math.max(0,p-ThreadTimeline.KNOT)*st.duration()/60.0);
            fx=TideFx.begin(STAR,new Matrix4f());
            fx.billboard(m.scale(k),facing,(float)(22*k*flare),time*.2,0xffd28a,knot*.9);
            fx.billboard(m.scale(k),facing,(float)(60*k),-time*.1,0xe8283a,knot*.3);
        }
        double now=ThreadClient.ageNow(partial);
        for(int i=0;i<ThreadData.STRIKES;i++){
            BlockPos g=st.strike(i);
            if(g.equals(BlockPos.ZERO))continue;
            int at=ThreadTimeline.strikeTick(i,st.duration());
            if(now<at-24||now>=at)continue;
            double t=TideTimeline.smooth(0,1,(now-(at-24))/24.0);t=t*t;
            Vec3 foot=Vec3.atBottomCenterOf(g),top=ThreadTimeline.strikeTop(seam,i,p);
            Vec3 glint=top.lerp(foot,t).subtract(camera);
            double k=Math.min(1,far/glint.length());
            if(fx==null)fx=TideFx.begin(STAR,new Matrix4f());
            fx.billboard(glint.scale(k),facing,(float)((2.5+glint.length()*.012)*k),time*3,0xfff0d0,b);
            fx.billboard(glint.scale(k),facing,(float)((7+glint.length()*.03)*k),-time,0xffb050,b*.45);
        }
        if(fx!=null)fx.end(true);
    }
    /** A taut thread between two points; plucked by the pull, it shivers in a standing wave. */
    static void vibrating(ThreadLines L,Vec3 top,Vec3 foot,Vec3 camera,int rgb,double alpha,double time,double pluck,boolean sky){
        if(alpha<.005)return;
        Vec3 d=top.subtract(foot),side=d.cross(new Vec3(0,1,0));
        if(side.lengthSqr()<1e-6)side=new Vec3(1,0,0);
        side=side.normalize();
        int n=28;
        List<Vec3> pts=new ArrayList<>();
        for(int i=0;i<=n;i++){
            double t=i/(double)n;
            // Most of the thread's length is far overhead; bunch the points near the land, where it can be seen to move.
            double tt=t*t;
            double wave=Math.sin(Math.PI*Math.min(1,tt*6))*(.05+pluck*.9)*Math.sin(time*(14+pluck*18)+t*3);
            pts.add(foot.add(d.scale(tt)).add(side.scale(wave)).subtract(camera));
        }
        L.strip(pts,rgb,alpha,.0011,.02,sky);
    }
    static int mix(int a,int b,double t){
        int r=(int)Mth.lerp(t,a>>16&255,b>>16&255),g=(int)Mth.lerp(t,a>>8&255,b>>8&255),bl=(int)Mth.lerp(t,a&255,b&255);
        return r<<16|g<<8|bl;
    }

    // ------------------------------------------------------------------ sky-weaving lamps (any time)
    private static void lamps(ThreadLines L,Vec3 camera,double time){
        var lamps=ThreadClient.lamps;
        var level=Minecraft.getInstance().level;
        // A lamp sewn to one far away: its thread rises into the sky toward the other end, and fades there.
        for(BlockPos a:lamps){
            if(!(level.getBlockEntity(a) instanceof ThreadBlocks.LoomLink link)||!link.linked())continue;
            boolean here=link.partnerDim().equals(level.dimension());
            if(here&&link.partner().distSqr(a)<=10.5*10.5&&lamps.contains(link.partner()))continue;
            Vec3 pa=Vec3.atBottomCenterOf(a).add(0,.82,0);
            Vec3 toward=here?new Vec3(link.partner().getX()-a.getX(),0,link.partner().getZ()-a.getZ()):Vec3.ZERO;
            Vec3 dir=toward.lengthSqr()<1?new Vec3(0,0,0):toward.normalize();
            List<Vec3> pts=new ArrayList<>();
            long key=a.asLong();
            for(int i=0;i<=24;i++){
                double t=i/24.0,sway=Math.sin(time*.7+t*4+key%5)*.25*t;
                pts.add(pa.add(dir.scale(t*22)).add(-dir.z*sway,t*t*34+t*4,dir.x*sway).subtract(camera));
            }
            L.strip(pts,SCARLET,.7*(link.pending()?.55:1),.0009,.02,false);
            double run=(time*.25+(key&15)*.06)%1;
            int at=(int)(run*24);
            if(at<24)L.segment(pts.get(at),pts.get(at+1),GOLD,.9,.9,.002,.05,false);
        }
        if(lamps.size()<2)return;
        Set<Long> done=new HashSet<>();
        for(BlockPos a:lamps){
            if(!level.getBlockState(a).is(Veilborne.LOOM_LANTERN.get()))continue;
            List<BlockPos> near=new ArrayList<>(lamps);
            near.remove(a);
            near.sort(Comparator.comparingDouble(o->o.distSqr(a)));
            for(int k=0;k<Math.min(2,near.size());k++){
                BlockPos bb=near.get(k);
                if(bb.distSqr(a)>10.5*10.5||!level.getBlockState(bb).is(Veilborne.LOOM_LANTERN.get()))continue;
                long key=Math.min(a.asLong(),bb.asLong())*31+Math.max(a.asLong(),bb.asLong());
                if(!done.add(key))continue;
                Vec3 pa=Vec3.atBottomCenterOf(a).add(0,.82,0),pb=Vec3.atBottomCenterOf(bb).add(0,.82,0);
                double dist=pa.distanceTo(pb),sag=.12*dist+.2;
                List<Vec3> pts=new ArrayList<>();
                for(int i=0;i<=16;i++){double t=i/16.0;pts.add(pa.lerp(pb,t).add(0,-sag*4*t*(1-t)+Math.sin(time*.9+t*3+key%7)*.03*Math.sin(Math.PI*t),0).subtract(camera));}
                L.strip(pts,SCARLET,.75,.0009,.018,false);
                // A bead of light running along it now and then.
                double run=(time*.18+(key&15)*.07)%1;
                int at=(int)(run*16);
                if(at<16)L.segment(pts.get(at),pts.get(at+1),GOLD,.9,.9,.002,.04,false);
            }
        }
    }

    // ------------------------------------------------------------------ thread spirits
    private static void spirits(ThreadLines L,Vec3 camera,double time,float partial){
        var mc=Minecraft.getInstance();
        for(Entity en:mc.level.entitiesForRendering()){
            if(!(en instanceof ThreadSpirit sp)||en.distanceToSqr(camera)>48*48)continue;
            Vec3 c=sp.getPosition(partial).add(0,.3,0);
            double t=time*2+sp.getId();
            for(int loop=0;loop<3;loop++){
                List<Vec3> pts=new ArrayList<>();
                for(int i=0;i<=24;i++){
                    double a=i/24.0*Math.PI*2;
                    double x=Math.sin(a*(2+loop)+t*(.6+loop*.2))*.38,y=Math.sin(a*3+t*.7+loop)*.3,z=Math.cos(a*(1+loop)+t*.5)*.38;
                    pts.add(c.add(x,y,z).subtract(camera));
                }
                L.strip(pts,loop==1?GOLD:SCARLET,.8,.0012,.012,false);
            }
            // Two loose ends trailing behind it.
            Vec3 back=sp.getDeltaMovement().lengthSqr()>1e-5?sp.getDeltaMovement().normalize().scale(-1):new Vec3(0,-1,0);
            for(int end=0;end<2;end++){
                List<Vec3> tail=new ArrayList<>();
                for(int i=0;i<=8;i++){double s=i/8.0;tail.add(c.add(back.scale(s*.9)).add(Math.sin(t*2+s*5+end*2)*s*.18,-s*.2,Math.cos(t*2.3+s*4+end)*s*.18).subtract(camera));}
                L.strip(tail,SCARLET,.6,.001,.01,false);
            }
        }
    }
    private static void spiritGlow(Vec3 camera,Quaternionf facing,double time,float partial){
        var mc=Minecraft.getInstance();
        TideFx fx=null;
        for(Entity en:mc.level.entitiesForRendering()){
            if(!(en instanceof ThreadSpirit sp)||en.distanceToSqr(camera)>48*48)continue;
            if(fx==null)fx=TideFx.begin(STAR,new Matrix4f());
            Vec3 c=sp.getPosition(partial).add(0,.3,0).subtract(camera);
            fx.billboard(c,facing,.22+.05*Math.sin(time*6+sp.getId()),time,0xffd28a,.95);
            fx.billboard(c,facing,.6,-time*.5,0xe8283a,.35);
        }
        if(fx!=null)fx.end(true);
    }
    private ThreadRenderer(){}
}
