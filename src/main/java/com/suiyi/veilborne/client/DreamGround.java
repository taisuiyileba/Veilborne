package com.suiyi.veilborne.client;

import com.suiyi.veilborne.*;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * 苍翠梦潮 near you, drawn after the particles: the petal path lighting up from your feet to the dream tree; the dream
 * tree's hanging lamp-flowers, and the fireflies that gather round you when you wait there quietly; the seeds (the
 * wandering one, and the falling ones with the thread of light that ties a guided seed to your hands, and the little
 * sprout when one lands); the vine enclosure, its vines standing round you and the light you are chasing; the seed the deer
 * lets fall to you and the antler twig at the end, each coming down out of the sky on a trail; the buds of light at the
 * roots' growing tips, and where they grip the land; the light in the deer's chest, its eyes and its lamps; shafts of
 * sunlight slanting down through the canopy; and seeds swimming slowly round the crops.
 */
final class DreamGround {
    private static final ResourceLocation GLOW=Veilborne.id("textures/environment/glow.png"),STAR=Veilborne.id("textures/environment/star.png"),
        PETAL=Veilborne.id("textures/environment/dream_petal.png"),SEED=Veilborne.id("textures/environment/dream_seed.png"),
        VINE=Veilborne.id("textures/environment/dream_vine.png"),SHAFT=Veilborne.id("textures/environment/dream_shaft.png"),
        TWIG=Veilborne.id("textures/environment/dream_twig.png");
    private static final Matrix4f I=new Matrix4f();
    static final int HONEY=0xffc85a,EMERALD=0x5ee08a,PALE=0xfff0c8;

    static void render(Vec3 camera,Quaternionf facing,double p,double s,double time,float partial){
        var st=DreamClient.state;
        var mc=Minecraft.getInstance();
        TideFx glow=TideFx.begin(GLOW,I),star=TideFx.begin(STAR,I),petal=TideFx.begin(PETAL,I),seed=TideFx.begin(SEED,I),vine=TideFx.begin(VINE,I);
        if(DreamClient.active()&&st!=null){
            path(petal,glow,st,camera,facing,p,s,time,partial);
            tree(glow,star,st,camera,facing,p,s,time,partial);
            enclosure(vine,glow,star,st,camera,facing,s,time,partial);
            falling(seed,glow,star,st,camera,facing,s,time,partial);
            tips(star,glow,camera,facing,p,s,time);
            deerGlows(star,glow,camera,facing,time,partial);
            crops(seed,glow,camera,facing,p,s,time);
        }
        seeds(seed,glow,star,camera,facing,time,partial);
        petal.end(false);vine.end(false);seed.end(false);glow.end(true);star.end(true);
        if(DreamClient.active()&&st!=null)shafts(camera,p,s,time,partial);
    }

    // ------------------------------------------------------------------ the petal path
    private static void path(TideFx petal,TideFx glow,DreamPacket st,Vec3 camera,Quaternionf facing,double p,double s,double time,float partial){
        if(!st.hasPath())return;
        List<BlockPos> pts=st.path();
        double since=DreamClient.ageNow(partial)-st.pathAt(),lit=DreamTimeline.pathLit(since);
        double fade=(1-TideTimeline.smooth(DreamTimeline.PATH_UNTIL-.3/13,DreamTimeline.PATH_UNTIL,p))*s;
        if(fade<.01)return;
        int n=pts.size();
        for(int i=0;i<n;i++){
            double f=i/(double)(n-1);
            double on=TideTimeline.smooth(f-.05,f+.02,lit);
            if(on<.01)break;
            Vec3 c=Vec3.atBottomCenterOf(pts.get(i)).subtract(camera);
            if(c.lengthSqr()>80*80)continue;
            // A light runs along it again and again, from your end toward the tree.
            double run=Math.exp(-Math.pow(((time*.35-f)%1+1)%1-.5,2)/.004);
            for(int k=0;k<3;k++){
                double a=i*2.4+k*2.1,r=.18+.32*k;
                Vec3 at=c.add(Math.cos(a)*r,.03+.005*k,Math.sin(a)*r);
                petal.flat(at,.18+.05*k,a+time*.05*(k-1),0,0,1,1,k==1?HONEY:0xffe2a0,on*fade*.95);
            }
            glow.flat(c.add(0,.04,0),.7,0,0,0,1,1,HONEY,on*fade*(.22+.5*run));
        }
    }

    // ------------------------------------------------------------------ the dream tree
    private static void tree(TideFx glow,TideFx star,DreamPacket st,Vec3 camera,Quaternionf facing,double p,double s,double time,float partial){
        if(!st.hasTree())return;
        double growth=DreamTimeline.treeGrowth(DreamClient.ageNow(partial)-st.treeAt())*(1-DreamTimeline.treeWither(p));
        double key=DreamTimeline.u(st.seed(),77);
        for(Vec3 l:DreamGrove.treeLamps(growth,key)){
            Vec3 c=l.subtract(camera);
            double tw=.8+.2*Math.sin(time*2+l.x);
            star.billboard(c,facing,.22,0,PALE,growth*tw*s);
            glow.billboard(c,facing,.9,0,HONEY,growth*.4*s);
        }
        Vec3 base=Vec3.atBottomCenterOf(st.tree()).subtract(camera);
        glow.billboard(base.add(0,4,0),facing,6*growth+.1,0,EMERALD,growth*.12*s);
        // Wait there quietly, and the fireflies come to you: the tree is listening.
        if(DreamClient.listening()){
            var mc=Minecraft.getInstance();
            double k=Math.min(1,(DreamClient.ticks-DreamClient.stillSince-DreamService.QUIET_TICKS+partial)/60.0);
            Vec3 me=mc.player.getPosition(partial).subtract(camera);
            for(int i=0;i<9;i++){
                double a=time*.5+i*.698,r=1.3+.4*Math.sin(time*.7+i),y=.6+.9*Math.sin(time*.9+i*1.3)+.9;
                star.billboard(me.add(Math.cos(a)*r,y,Math.sin(a)*r),facing,.12,0,HONEY,k*s*(.6+.4*Math.sin(time*3+i)));
            }
        }
        // Where the deer's gift will land, a light waits once it has been given.
        if(st.has(DreamData.GLANCE_PLAYER)&&!st.has(DreamData.GIFT_DROPPED))gift(star,glow,st,camera,facing,time,partial);
    }
    private static void gift(TideFx star,TideFx glow,DreamPacket st,Vec3 camera,Quaternionf facing,double time,float partial){
        double t=(DreamClient.ageNow(partial)-st.giftAt())/DreamTimeline.GIFT_FALL;
        if(t<0||t>1)return;
        Vec3 end=Vec3.atBottomCenterOf(st.glance()).add(0,.5,0);
        DeerModel.Pose pose=DeerModel.lastPose;
        Vec3 from=pose==null?end.add(0,40,0):DeerModel.lastCamera.add(pose.occiput().add(pose.hu().scale(9)).subtract(DeerModel.lastCamera).scale(DeerModel.lastScale));
        double e=1-(1-t)*(1-t);
        Vec3 at=from.lerp(end,e).add(Math.sin(t*9)*.8*(1-t),0,Math.cos(t*7)*.8*(1-t));
        for(int k=1;k<=6;k++){
            double tt=Math.max(0,t-k*.02),ee=1-(1-tt)*(1-tt);
            Vec3 tr=from.lerp(end,ee).subtract(camera);
            glow.billboard(tr,facing,.5*(1-k/7.0),0,HONEY,.4*(1-k/7.0));
        }
        star.billboard(at.subtract(camera),facing,.35,time,PALE,1);
        glow.billboard(at.subtract(camera),facing,1.2,0,HONEY,.5);
    }

    // ------------------------------------------------------------------ the vine enclosure
    private static void enclosure(TideFx vine,TideFx glow,TideFx star,DreamPacket st,Vec3 camera,Quaternionf facing,double s,double time,float partial){
        if(!st.has(DreamData.ENCLOSURE))return;
        double now=DreamClient.ageNow(partial);
        double closed=st.enclosureOpen()?-1:now-st.closedAt();
        double v=DreamTimeline.vines(now-st.enclosureAt(),closed);
        if(v<.01)return;
        Vec3 c=Vec3.atBottomCenterOf(st.enclosure()).subtract(camera);
        var level=Minecraft.getInstance().level;Vec3 cw=Vec3.atBottomCenterOf(st.enclosure());
        int n=46;
        for(int i=0;i<n;i++){
            double a=i*Math.PI*2/n;
            double gapD=Math.abs(Mth.wrapDegrees(Math.toDegrees(a-st.enclosureExit())));
            if(gapD<22)continue;
            double r=DreamTimeline.RING+.25*Math.sin(i*1.7);
            double x=cw.x+Math.cos(a)*r,z=cw.z+Math.sin(a)*r;
            double gy=Surface.top(level,Mth.floor(x),Mth.floor(z));
            double h=(1.6+1.1*Math.abs(Math.sin(i*2.3)))*v*(st.has(DreamData.ENCLOSURE_WON)?1+.3*Math.sin(time*2+i):1);
            Vec3 base=new Vec3(x,gy,z).subtract(camera);
            // A vine standing up out of the ground, a little bent, turned toward you.
            Vec3 top=base.add(Math.sin(time*.8+i)*.25,h,Math.cos(time*.7+i)*.25);
            Vec3 side=top.subtract(base).cross(base).normalize().scale(.42);
            vine.quad(base.subtract(side),base.add(side),top.add(side),top.subtract(side),0,1,1,0,st.has(DreamData.ENCLOSURE_WON)?0xe8f0a0:0xb8f0b8,.92*s);
            if(st.has(DreamData.ENCLOSURE_WON)&&i%3==0)star.billboard(top,facing,.16,0,HONEY,v*s);
        }
        // The light to chase: it comes, hovers, and if not caught in time goes out.
        if(st.enclosureOpen()&&now>=st.lightAt()){
            Vec3 l=DreamService.lightPoint(level,st.enclosure(),st.seed(),st.lightStep(),st.enclosureExit()).subtract(camera);
            double t=(now-st.lightAt())/DreamTimeline.LIGHT_TICKS;
            double pulse=.8+.2*Math.sin(time*6);
            Vec3 bob=l.add(0,Math.sin(time*2)*.2,0);
            star.billboard(bob,facing,.35*pulse,time,PALE,1-t*.6);
            glow.billboard(bob,facing,1.5,0,HONEY,(.6-.3*t)*s);
            for(int k=0;k<st.lightStep();k++){
                double a=time*.8+k*1.2566;
                star.billboard(c.add(Math.cos(a)*1.1,2.6+.2*Math.sin(time+k),Math.sin(a)*1.1),facing,.14,0,HONEY,.9*s);
            }
        }
    }

    // ------------------------------------------------------------------ the seeds
    private static void seeds(TideFx seed,TideFx glow,TideFx star,Vec3 camera,Quaternionf facing,double time,float partial){
        var mc=Minecraft.getInstance();
        for(Entity en:mc.level.entitiesForRendering()){
            if(!(en instanceof DreamSeed ds)||en.distanceToSqr(camera)>64*64)continue;
            Vec3 c=ds.getPosition(partial).subtract(camera);
            float vis=ds.visibility(partial);
            double clock=ds.stateClock()+partial;
            if(ds.state()==DreamSeed.State.LAND){
                // The sprout: a stem of light and two leaves opening, then a flower of it.
                double k=Math.min(1,clock/DreamSeed.LAND_TICKS);
                Vec3 g=c.add(0,-.3,0);
                for(int i=0;i<6;i++){Vec3 a=g.add(0,k*.9*i/6,0);glow.billboard(a,facing,.12,0,EMERALD,.8);}
                seed.billboard(g.add(0,k*.9,0),facing,.18+.2*k,time,.5f,0,1,.5f,0xffffff,1);
                star.billboard(g.add(0,k*.9,0),facing,.2+.5*k*k,time,PALE,k);
                glow.billboard(g.add(0,.4,0),facing,1+k*1.6,0,HONEY,.4*k);
                continue;
            }
            double spin=time*1.2+ds.getId();
            seed.billboard(c,facing,.24,Math.sin(spin)*.3,0,0,.5f,.5f,0xffffff,vis);
            // The wisp of fluff above it.
            seed.billboard(c.add(0,.28,0),facing,.3,Math.sin(spin*.7)*.2,.5f,.5f,1,1,0xffffff,vis*.8);
            glow.billboard(c,facing,.9,0,HONEY,vis*.5);
            star.billboard(c,facing,.14,spin,PALE,vis*(.6+.4*Math.sin(time*3+ds.getId())));
            if(ds.state()==DreamSeed.State.FOLLOW){
                Entity g=mc.level.getEntity(ds.guide());
                if(g instanceof Player pl){
                    Vec3 hand=DreamSeed.hands(pl).subtract(camera);
                    for(int i=1;i<8;i++){Vec3 a=c.lerp(hand,i/8.0).add(0,Math.sin(i*.9+time*3)*.05,0);star.billboard(a,facing,.05,0,HONEY,vis*.6*(1-i/9.0));}
                }
            }
        }
    }
    /** The antler twig coming down at the end, on a trail of honey light. */
    private static void falling(TideFx seed,TideFx glow,TideFx star,DreamPacket st,Vec3 camera,Quaternionf facing,double s,double time,float partial){
        if(!st.has(DreamData.TWIG)||st.has(DreamData.TWIG_DROPPED))return;
        double t=(DreamClient.ageNow(partial)-st.twigAt())/DreamTimeline.TWIG_FALL;
        if(t<0||t>1)return;
        Vec3 end=Vec3.atBottomCenterOf(st.twig()).add(0,.4,0);
        Vec3 from=end.add(8,70,-5);
        double e=1-Math.pow(1-t,2.2);
        Vec3 at=from.lerp(end,e).add(Math.sin(t*11)*1.5*(1-t),0,Math.cos(t*8)*1.5*(1-t));
        for(int k=1;k<=10;k++){double tt=Math.max(0,t-k*.018);Vec3 tr=from.lerp(end,1-Math.pow(1-tt,2.2)).subtract(camera);glow.billboard(tr,facing,.6*(1-k/11.0),0,HONEY,.35*(1-k/11.0));}
        glow.billboard(at.subtract(camera),facing,1.6,0,HONEY,.6);
        star.billboard(at.subtract(camera),facing,.5,time,PALE,1);
        TideFx twig=TideFx.begin(TWIG,I);
        twig.billboard(at.subtract(camera),facing,.55,time*1.4,0xffffff,1);
        twig.end(false);
    }

    // ------------------------------------------------------------------ the roots' growing tips, and where they grip
    private static void tips(TideFx star,TideFx glow,Vec3 camera,Quaternionf facing,double p,double s,double time){
        if(!TideConfig.SKY.get())return;
        Vec3 c0=DreamClient.center();
        double grow=DreamTimeline.rootGrow(p),road=DreamTimeline.road(p),back=DreamTimeline.rootWithdraw(p);
        double far=Minecraft.getInstance().gameRenderer.getDepthFar()*.9;
        for(DreamGrove.Tip t:DreamGrove.tips){
            double g=t.road()?road:grow;
            double reach=TideTimeline.smooth(t.delay(),t.delay()+.45,g)*(1-TideTimeline.smooth(t.delay()*.35,t.delay()*.35+.62,back));
            boolean growing=reach>.002&&reach<.995;
            Vec3 at=c0.add(t.top().lerp(t.end(),reach)).subtract(camera);
            double d=at.length(),k=Math.min(1,far/d);
            if(growing){
                star.billboard(at.scale(k),facing,(1.2+d*.012)*k,time*.3,PALE,s*.9);
                glow.billboard(at.scale(k),facing,(4+d*.03)*k,0,HONEY,s*.35);
            }else if(reach>=.995&&!t.reaches()){
                // A root that hangs free keeps a small light at its end.
                glow.billboard(at.scale(k),facing,(1.5+d*.012)*k,0,EMERALD,s*.3*(.7+.3*Math.sin(time+t.delay()*40)));
            }
        }
    }

    // ------------------------------------------------------------------ the deer's lights
    private static void deerGlows(TideFx star,TideFx glow,Vec3 camera,Quaternionf facing,double time,float partial){
        DeerModel.Pose pose=DeerModel.lastPose;
        if(pose==null||DeerModel.pose(partial)==null)return;
        double k=DeerModel.lastScale;Vec3 cam=DeerModel.lastCamera;
        double vis=Math.min(1,pose.vis());
        Vec3 heart=DeerModel.heart(pose).subtract(cam).scale(k);
        double beat=.75+.25*Math.pow(Math.max(0,Math.sin(time*1.1)),6);
        glow.billboard(heart,facing,(9+2*beat)*k,0,HONEY,vis*.28*beat);
        glow.billboard(heart,facing,(4)*k,0,PALE,vis*.22*beat);
        for(DeerModel.Lamp l:DeerModel.lamps){
            Vec3 c=l.at().subtract(cam).scale(k);
            glow.billboard(c,facing,l.size()*2.2*k,0,HONEY,vis*.5);
            star.billboard(c,facing,l.size()*.6*k,0,PALE,vis*.8);
        }
        // Fireflies in the woodland on its antlers.
        Vec3 crown=pose.occiput().add(pose.hu().scale(34));
        for(int i=0;i<14;i++){
            double a=time*.3+i*.449,r=10+8*Math.sin(i*1.9);
            Vec3 at=crown.add(Math.cos(a)*r,Math.sin(time*.5+i)*9,Math.sin(a)*r).subtract(cam).scale(k);
            star.billboard(at,facing,.5*k,0,HONEY,vis*(.4+.4*Math.sin(time*2+i*3)));
        }
    }

    // ------------------------------------------------------------------ light through the canopy
    /** A few broad shafts of light slanting down through gaps in the canopy, some way off; faint jade ones at night. */
    private static void shafts(Vec3 camera,double p,double s,double time,float partial){
        double show=DreamTimeline.light(p)*(1-DreamTimeline.leafLoss(p)*.7)*s;
        if(show<.02)return;
        var mc=Minecraft.getInstance();
        double day=DreamClient.dayness(partial);
        float angle=mc.level.getTimeOfDay(partial)*(float)Math.PI*2;
        Vec3 sun=new Vec3(-Math.sin(angle),Math.cos(angle),0);
        if(sun.y<.2)sun=new Vec3(sun.x,.2,sun.z).normalize();
        TideFx fx=TideFx.begin(SHAFT,I);
        var st=DreamClient.state;
        Vec3 me=mc.player.getPosition(partial);
        for(int i=0;i<7;i++){
            double a=DreamTimeline.u(st.seed(),300+i)*Math.PI*2+i*.9,r=30+60*DreamTimeline.u(st.seed(),320+i);
            double x=me.x+Math.cos(a)*r,z=me.z+Math.sin(a)*r;
            x=Math.floor(x/48)*48+24;z=Math.floor(z/48)*48+24;
            double gy=Surface.top(mc.level,Mth.floor(x),Mth.floor(z));
            Vec3 foot=new Vec3(x,gy,z).subtract(camera);
            double len=70;
            Vec3 top=foot.add(sun.scale(len/sun.y));
            double sway=Math.sin(time*.13+i)*.35+.65;
            Vec3 side=top.subtract(foot).cross(foot).normalize().scale(3.2+1.4*Math.sin(i*2.1));
            int col=day>.4?0xfff0c0:0x9fe8c8;
            double al=show*mix(.05,.11,day)*sway;
            fx.quad(foot.subtract(side),foot.add(side),top.add(side.scale(1.4)),top.subtract(side.scale(1.4)),0,1,1,0,col,al);
        }
        fx.end(true);
    }
    private static double mix(double a,double b,double t){return a+(b-a)*t;}

    // ------------------------------------------------------------------ round the crops
    private static void crops(TideFx seed,TideFx glow,Vec3 camera,Quaternionf facing,double p,double s,double time){
        double show=DreamTimeline.schools(p)*s;
        if(show<.02)return;
        for(int i=0;i<DreamClient.crops.size();i++){
            Vec3 c=Vec3.atBottomCenterOf(DreamClient.crops.get(i)).subtract(camera);
            double a=time*.45+i*2.3,r=.9;
            Vec3 at=c.add(Math.cos(a)*r,.9+.2*Math.sin(time*1.3+i),Math.sin(a)*r);
            seed.billboard(at,facing,.14,Math.sin(time+i)*.3,0,0,.5f,.5f,0xffffff,show*.9);
            glow.billboard(at,facing,.5,0,HONEY,show*.3);
        }
    }
    private DreamGround(){}
}
