package com.suiyi.veilborne.client;

import com.suiyi.veilborne.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * 叶鱼: leaves that swim. Near you they go in schools of a dozen or two, each leaf following its place in the school a
 * moment late, so when the school turns the turn runs back through it the way it does through fish. Now and then a school
 * dives: at the ground its leaves come apart into pollen, and where the pollen settles a flower pattern opens on the land;
 * then the school climbs again and the lost leaves drift down out of the canopy to rejoin it. High up, far off, larger
 * shoals turn over and over against the leaves like starlings. Flower lamps keep a few circling them at any time; one
 * sometimes rests on a window sill indoors. The lost school is drawn here too, and the golden fish it becomes.
 */
final class DreamFlock {
    static final ResourceLocation FISH=Veilborne.id("textures/environment/dream_fish.png"),GLOW=Veilborne.id("textures/environment/glow.png"),STAR=Veilborne.id("textures/environment/star.png");
    private static final Matrix4f I=new Matrix4f();
    private static final RandomSource random=RandomSource.create();
    static final int GREEN=0xa6e27a,JADE=0xa8f0c4,GOLD=0xffd46a,EMBER=0xf0a050;
    static final class Fish {
        Vec3 pos,vel=Vec3.ZERO,slot;double phase,size,bank;int variant,tint;boolean gone;int goneAt;
        Fish(Vec3 pos,Vec3 slot){this.pos=pos;this.slot=slot;}
    }
    static final class School {
        Vec3 center,vel;double heading,seed,climb;int state,stateAt,born;Vec3 dive;boolean bloomed;
        final List<Fish> fish=new ArrayList<>();
    }
    static final int CRUISE=0,DIVE=1,RISE=2,LEAVE=3;
    static final List<School> schools=new ArrayList<>();
    private static int rustleAt;
    static void clear(){schools.clear();}

    // ------------------------------------------------------------------ simulation
    static void tick(Minecraft mc,double p){
        var level=mc.level;Vec3 me=mc.player.position();
        double want=DreamTimeline.schools(p)*DreamClient.strength();
        int q=TideConfig.QUALITY.get();
        int target=TideConfig.DREAM_SCHOOLS.get()?(int)Math.round(want*new int[]{2,4,6}[q]):0;
        int alive=0;for(School s:schools)if(s.state!=LEAVE)alive++;
        if(alive<target&&DreamClient.ticks%15==0)spawn(mc,me,q);
        if(alive>target)for(School s:schools)if(s.state!=LEAVE){s.state=LEAVE;s.stateAt=DreamClient.ticks;break;}
        double gold=DreamTimeline.gold(p);
        for(School s:schools)step(mc,s,me,gold);
        schools.removeIf(s->s.center.distanceTo(me)>90||s.state==LEAVE&&DreamClient.ticks-s.stateAt>200);
    }
    private static void spawn(Minecraft mc,Vec3 me,int q){
        School s=new School();
        double a=random.nextDouble()*Math.PI*2,r=16+random.nextDouble()*20;
        double x=me.x+Math.cos(a)*r,z=me.z+Math.sin(a)*r;
        double g=Surface.top(mc.level,Mth.floor(x),Mth.floor(z));
        s.center=new Vec3(x,Math.max(g,me.y)+22+random.nextDouble()*10,z);
        s.heading=random.nextDouble()*Math.PI*2;s.vel=Vec3.ZERO;s.seed=random.nextDouble()*100;s.born=DreamClient.ticks;s.stateAt=DreamClient.ticks;
        s.climb=6+random.nextDouble()*8;
        int n=new int[]{12,18,26}[q]+random.nextInt(6);
        for(int i=0;i<n;i++){
            Vec3 slot=new Vec3((random.nextDouble()-.5)*6,(random.nextDouble()-.5)*2.2,(random.nextDouble()-.5)*4.2);
            Fish f=new Fish(s.center.add(slot).add(0,4+random.nextDouble()*8,0),slot);
            f.phase=random.nextDouble()*Math.PI*2;f.size=.8+random.nextDouble()*.55;f.variant=random.nextInt(3);
            f.tint=random.nextInt(10)<6?GREEN:JADE;
            s.fish.add(f);
        }
        schools.add(s);
    }
    private static void step(Minecraft mc,School s,Vec3 me,double gold){
        var level=mc.level;int now=DreamClient.ticks;
        double t=(now+s.seed*40)/20.0;
        // The leader wanders on a slowly turning heading, and keeps within sight of you.
        s.heading+=(Math.sin(t*.37+s.seed)*.035+Math.sin(t*.11+s.seed*2)*.02);
        Vec3 toMe=me.subtract(s.center);double far=Math.hypot(toMe.x,toMe.z);
        if(far>30){double want=Math.atan2(toMe.z,toMe.x);s.heading+=Mth.wrapDegrees(Math.toDegrees(want-s.heading))*Math.PI/180*.03;}
        Vec3 dir=new Vec3(Math.cos(s.heading),0,Math.sin(s.heading));
        double ground=Surface.top(level,Mth.floor(s.center.x),Mth.floor(s.center.z));
        double y;
        switch(s.state){
            case DIVE -> {y=ground+.6;if(s.center.y-ground<2.2&&!s.bloomed){s.bloomed=true;bloom(mc,s,ground);}if(now-s.stateAt>90){s.state=RISE;s.stateAt=now;}}
            case RISE -> {y=ground+s.climb;if(now-s.stateAt>160){s.state=CRUISE;s.stateAt=now;}}
            case LEAVE -> y=s.center.y+3;
            default -> {
                y=Math.max(ground,me.y-4)+s.climb+2*Math.sin(t*.4+s.seed);
                if(now-s.stateAt>300+(int)(s.seed*3)%200&&far<34&&random.nextInt(60)==0){s.state=DIVE;s.stateAt=now;s.bloomed=false;}
            }
        }
        double speed=s.state==DIVE?.26:s.state==LEAVE?.3:.2;
        Vec3 aim=s.center.add(dir.scale(4)).add(0,Mth.clamp(y-s.center.y,-2.5,2),0);
        Vec3 v=aim.subtract(s.center).normalize().scale(speed);
        s.vel=s.vel.scale(.9).add(v.scale(.1));
        s.center=s.center.add(s.vel);
        if(s.center.distanceTo(me)<9&&now-rustleAt>80&&TideConfig.SOUND.get()){
            rustleAt=now;
            mc.getSoundManager().play(new SimpleSoundInstance(Veilborne.DREAM_RUSTLE.get().getLocation(),SoundSource.AMBIENT,(float)(.5*DreamClient.strength()*TideConfig.SOUND_VOLUME.get()),.9f+random.nextFloat()*.25f,RandomSource.create(),false,0,SoundInstance.Attenuation.LINEAR,s.center.x,s.center.y,s.center.z,false));
        }
        double cs=Math.cos(s.heading),sn=Math.sin(s.heading);
        for(Fish f:s.fish){
            if(f.gone){
                // Lost leaves come back down out of the canopy while the school climbs.
                if(s.state!=DIVE&&now-f.goneAt>60+(int)(f.phase*20)){f.gone=false;f.pos=s.center.add(f.slot.x,14+f.phase*3,f.slot.z);f.vel=Vec3.ZERO;}
                continue;
            }
            Vec3 slot=new Vec3(f.slot.x*cs-f.slot.z*sn,f.slot.y,f.slot.x*sn+f.slot.z*cs);
            Vec3 wave=new Vec3(Math.sin(t*1.3+f.phase)*.4,Math.sin(t*.9+f.phase*2)*.35,Math.cos(t*1.1+f.phase)*.4);
            Vec3 goal=s.center.add(slot).add(wave);
            Vec3 steer=goal.subtract(f.pos).scale(.055);
            Vec3 nv=f.vel.scale(.88).add(steer);
            double l=nv.length();if(l>.45)nv=nv.scale(.45/l);
            // Banking into the turn.
            Vec3 lat=nv.subtract(f.vel);
            Vec3 fwd=nv.lengthSqr()>1e-6?nv.normalize():dir;
            f.bank=Mth.lerp(.15,f.bank,Mth.clamp((fwd.x*lat.z-fwd.z*lat.x)*18,-1.1,1.1));
            f.vel=nv;f.pos=f.pos.add(nv);
            if(gold>0&&f.tint!=GOLD&&random.nextDouble()<gold*.004)f.tint=random.nextBoolean()?GOLD:EMBER;
            if(s.state==DIVE){
                double gy=Surface.top(level,Mth.floor(f.pos.x),Mth.floor(f.pos.z));
                if(f.pos.y-gy<1.3){
                    f.gone=true;f.goneAt=now;
                    for(int k=0;k<5;k++)level.addParticle(Veilborne.DREAM_POLLEN.get(),f.pos.x,f.pos.y,f.pos.z,(random.nextDouble()-.5)*.08,.02+random.nextDouble()*.03,(random.nextDouble()-.5)*.08);
                }
            }
        }
    }
    /** Where the school came down, flowers open on the land: a larger pattern, and a few small ones round it. */
    private static void bloom(Minecraft mc,School s,double ground){
        if(!TideConfig.DREAM_PATTERNS.get())return;
        Vec3 c=new Vec3(s.center.x,ground,s.center.z);
        DreamClient.blooms.add(new DreamClient.Bloom(c,DreamClient.ticks,0,4.5+random.nextDouble()*2,random.nextDouble()));
        for(int i=0;i<3;i++){
            double a=random.nextDouble()*Math.PI*2,r=5+random.nextDouble()*5;
            double x=c.x+Math.cos(a)*r,z=c.z+Math.sin(a)*r;
            DreamClient.blooms.add(new DreamClient.Bloom(new Vec3(x,Surface.top(mc.level,Mth.floor(x),Mth.floor(z)),z),DreamClient.ticks+10+i*14,0,2+random.nextDouble()*1.6,random.nextDouble()));
        }
        if(TideConfig.SOUND.get())mc.getSoundManager().play(new SimpleSoundInstance(Veilborne.DREAM_BLOOM.get().getLocation(),SoundSource.AMBIENT,(float)(.6*DreamClient.strength()*TideConfig.SOUND_VOLUME.get()),1.1f+random.nextFloat()*.3f,RandomSource.create(),false,0,SoundInstance.Attenuation.LINEAR,c.x,c.y+1,c.z,false));
    }

    // ------------------------------------------------------------------ drawing
    /** One leaf-fish: a leaf with a tail, turned about its own length to face you (so it is never seen edge-on), rolling
     *  a little as it banks into its turns, its rear half flicking. pos is relative to the camera. */
    static void fish(TideFx fx,Vec3 pos,Vec3 fwd,double bank,double size,double phase,int variant,int rgb,double alpha,double time){
        Vec3 f=fwd.lengthSqr()<1e-6?new Vec3(1,0,0):fwd.normalize();
        Vec3 view=pos.lengthSqr()<1e-6?new Vec3(0,-1,0):pos.normalize();
        Vec3 side=f.cross(view);
        if(side.lengthSqr()<1e-6)side=f.cross(new Vec3(0,1,0));
        if(side.lengthSqr()<1e-6)side=new Vec3(1,0,0);
        side=side.normalize();
        Vec3 up=side.cross(f).normalize();
        // Bank: roll the leaf about its length, a little.
        double roll=bank*.45+Math.sin(time*2.3+phase)*.18;
        side=side.scale(Math.cos(roll)).add(up.scale(Math.sin(roll)));
        double wig=Math.sin(time*9+phase)*.45;
        Vec3 tailDir=f.scale(Math.cos(wig)).add(side.scale(Math.sin(wig))).normalize();
        Vec3 tailSide=tailDir.cross(up).normalize().scale(Math.cos(bank)).add(up.scale(Math.sin(bank)));
        Vec3 head=pos.add(f.scale(size*.5)),mid=pos,tail=pos.subtract(tailDir.scale(size*.5));
        double w=size*.36;
        float u0=(variant&1)*.5f,v0=(variant>>1)*.5f;
        fx.vertex(head.x-side.x*w*.3,head.y-side.y*w*.3,head.z-side.z*w*.3,u0,v0,rgb,alpha);
        fx.vertex(head.x+side.x*w*.3,head.y+side.y*w*.3,head.z+side.z*w*.3,u0,v0+.5f,rgb,alpha);
        fx.vertex(mid.x+side.x*w,mid.y+side.y*w,mid.z+side.z*w,u0+.25f,v0+.5f,rgb,alpha);
        fx.vertex(mid.x-side.x*w,mid.y-side.y*w,mid.z-side.z*w,u0+.25f,v0,rgb,alpha);
        fx.vertex(mid.x-side.x*w,mid.y-side.y*w,mid.z-side.z*w,u0+.25f,v0,rgb,alpha);
        fx.vertex(mid.x+side.x*w,mid.y+side.y*w,mid.z+side.z*w,u0+.25f,v0+.5f,rgb,alpha);
        fx.vertex(tail.x+tailSide.x*w,tail.y+tailSide.y*w,tail.z+tailSide.z*w,u0+.5f,v0+.5f,rgb,alpha);
        fx.vertex(tail.x-tailSide.x*w,tail.y-tailSide.y*w,tail.z-tailSide.z*w,u0+.5f,v0,rgb,alpha);
    }
    static void render(Vec3 camera,Quaternionf facing,double p,double s,double time,float partial){
        var mc=Minecraft.getInstance();
        boolean on=DreamClient.active();
        TideFx fx=TideFx.begin(FISH,I),glow=TideFx.begin(GLOW,I),star=TideFx.begin(STAR,I);
        double night=1-DreamClient.dayness(partial);
        if(on&&TideConfig.DREAM_SCHOOLS.get()){
            for(School sc:schools){
                double fade=sc.state==LEAVE?1-(DreamClient.ticks-sc.stateAt+partial)/200.0:Math.min(1,(DreamClient.ticks-sc.born+partial)/60.0);
                fade*=s;
                for(Fish f:sc.fish){
                    if(f.gone)continue;
                    Vec3 at=f.pos.add(f.vel.scale(partial)).subtract(camera);
                    if(at.lengthSqr()>80*80)continue;
                    fish(fx,at,f.vel,f.bank,f.size,f.phase,f.variant,f.tint,fade*.95,time);
                    glow.billboard(at,facing,f.size*1.1,0,f.tint,fade*(.05+.12*night));
                }
            }
            murmurations(camera,facing,p,s,time,star);
        }
        // Flower lamps keep their little schools at any time.
        for(BlockPos lamp:DreamClient.lamps){
            Vec3 c=Vec3.atCenterOf(lamp).subtract(camera);
            if(c.lengthSqr()>40*40)continue;
            for(int i=0;i<6;i++){
                double a=time*(.6+.07*i)+i*1.047,r=1.1+.35*Math.sin(i*1.7+time*.3),y=.2+.5*Math.sin(time*.8+i);
                Vec3 at=c.add(Math.cos(a)*r,y,Math.sin(a)*r),fwd=new Vec3(-Math.sin(a),.1*Math.cos(time*.8+i),Math.cos(a));
                fish(fx,at,fwd,.5,.46,i*1.3,i%3,i%3==0?GOLD:GREEN,.95,time);
            }
            glow.billboard(c,facing,1.4,0,0xffc864,.25);
        }
        // Indoors, now and then a leaf-fish rests on a window sill.
        if(on)for(int i=0;i<Math.min(2,DreamClient.openings.size());i++){
            var o=DreamClient.openings.get(i);
            if(((DreamClient.ticks/600)+i)%3!=0)continue;
            Vec3 out=Vec3.atLowerCornerOf(o.out().getNormal());
            Vec3 at=Vec3.atBottomCenterOf(o.inside()).add(out.scale(.35)).add(0,1.02,0).subtract(camera);
            double flutter=Math.sin(time*5)*.12*Math.max(0,Math.sin(time*.7));
            fish(fx,at,new Vec3(-out.z,0,out.x),flutter,.42,i,1,GREEN,.95*s,time*.15);
        }
        // The lost school, and the golden fish it becomes.
        for(Entity en:mc.level.entitiesForRendering())if(en instanceof LeafSchool ls&&en.distanceToSqr(camera)<64*64)school(fx,glow,star,ls,camera,facing,time,partial);
        for(DreamClient.Merge m:DreamClient.merges){
            double t=(DreamClient.ticks-m.born()+partial)/20.0;
            if(t>2.6)continue;
            Vec3 c=m.at().subtract(camera);
            star.billboard(c,facing,.6+t*1.2,t,0xfff0c0,Math.max(0,1-t/2.6));
            glow.billboard(c,facing,2+t*2,0,GOLD,Math.max(0,.6-t*.22));
        }
        fx.end(false);glow.end(true);star.end(true);
    }
    /** High and far off, larger shoals turn over and over against the leaves. */
    private static void murmurations(Vec3 camera,Quaternionf facing,double p,double s,double time,TideFx star){
        var st=DreamClient.state;
        double show=DreamTimeline.schools(p)*s;
        if(show<.02)return;
        Vec3 c0=DreamClient.center();
        for(int k=0;k<3;k++){
            double a=time*.012*(k%2==0?1:-1)+k*2.1+DreamTimeline.u(st.seed(),200+k)*6;
            double R=90+40*k,H=60+25*k;
            Vec3 c=c0.add(Math.cos(a)*R,H+8*Math.sin(time*.07+k),Math.sin(a)*R);
            int n=70;
            for(int i=0;i<n;i++){
                double u=i/(double)n,ph=u*37.7+k*3;
                double stretch=1+.6*Math.sin(time*.23+k);
                Vec3 off=new Vec3(Math.sin(ph+time*.31)*14*stretch+Math.sin(ph*2.3+time*.17)*6,Math.sin(ph*1.7+time*.27)*5,Math.cos(ph*1.3+time*.29)*9+Math.cos(ph*3.1)*4);
                Vec3 at=c.add(off).subtract(camera);
                double d=at.length();
                star.billboard(at,facing,Math.max(.5,d*.006),0,i%5==0?0xe8c860:0x7fc880,show*.55);
            }
        }
    }
    private static void school(TideFx fx,TideFx glow,TideFx star,LeafSchool ls,Vec3 camera,Quaternionf facing,double time,float partial){
        Vec3 c=ls.getPosition(partial).subtract(camera);
        float vis=ls.visibility(partial);
        double clock=ls.stateClock()+partial;
        switch(ls.state()){
            case MERGE -> {
                double k=Math.min(1,clock/LeafSchool.MERGE_TICKS);
                for(int i=0;i<LeafSchool.FISH;i++){
                    double a=i*.698+time*(1.6+k*3),r=(1-k)*1.6+.1;
                    Vec3 at=c.add(Math.cos(a)*r,Math.sin(i*1.3+time*2)*.4*(1-k),Math.sin(a)*r);
                    fish(fx,at,new Vec3(-Math.sin(a),0,Math.cos(a)),.6,.5*(1-k)+.1,i,i%3,k>.5?GOLD:GREEN,vis*(1-k*.8),time);
                }
                glow.billboard(c,facing,.6+k*2.2,0,GOLD,vis*k*.7);
                if(k>.6)fish(fx,c,new Vec3(Math.cos(time),0,Math.sin(time)),.3,2.4*(k-.6)/.4,0,3,GOLD,vis*(k-.6)/.4,time*.6);
            }
            case ASCEND -> {
                double a=clock*.09;
                Vec3 fwd=new Vec3(-Math.sin(a),.5,Math.cos(a));
                fish(fx,c,fwd,.5,2.4,0,3,GOLD,vis,time*.5);
                glow.billboard(c,facing,3,0,GOLD,vis*.45);
                star.billboard(c.add(fwd.scale(-1.2)),facing,.5,time,0xfff0c0,vis*.6);
            }
            default -> {
                Vec3 into=ls.into();
                for(int i=0;i<LeafSchool.FISH;i++){
                    double ph=i*1.9;
                    // Stuck, they keep bumping at the glass; following, they stream along behind the light.
                    Vec3 off=new Vec3(Math.sin(time*1.7+ph)*.55,Math.sin(time*1.3+ph*2)*.45,Math.cos(time*1.5+ph)*.55);
                    double bump=ls.state()==LeafSchool.State.STUCK?Math.max(0,Math.sin(time*3+ph))*.35:0;
                    Vec3 at=c.add(off).add(into.scale(bump));
                    Vec3 fwd=ls.state()==LeafSchool.State.STUCK?into.add(off.scale(.6)):ls.getDeltaMovement().add(off.scale(.3));
                    fish(fx,at,fwd,Math.sin(time+ph)*.6,.6,ph,i%3,i%3==0?GOLD:GREEN,vis*.95,time);
                }
                glow.billboard(c,facing,1.3,0,JADE,vis*.18);
            }
        }
    }
    private DreamFlock(){}
}
