package com.suiyi.veilborne.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.suiyi.veilborne.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * 无声白昼 in the air and on the ground near you. The air has stopped: dust hangs where it was, with a glint here and there
 * where it catches the light; under the trees leaves hang in mid-fall, and over water drops hang where they splashed. The
 * last wind carries all of it a little way in the omen and stops dead. Walk through it and it gives way around you and
 * stays where you left it; after the bird it all comes down. Indoors the pale light stands in the windows. The one bell
 * hangs on its thread and sways, the only thing that moves; the feather hangs in mid-fall until the bird, then comes down.
 * Knocks show on the door as rings. With a silent lantern in hand, the procession's footprints show on the ground.
 */
final class HushGround {
    private static final ResourceLocation MOTE=Veilborne.id("textures/environment/hush_mote.png"),LEAF=Veilborne.id("textures/environment/hush_leaf.png"),
        DROP=Veilborne.id("textures/environment/hush_drop.png"),FEATHER=Veilborne.id("textures/environment/hush_feather.png"),
        RIPPLE=Veilborne.id("textures/environment/hush_ripple.png"),PRINT=Veilborne.id("textures/environment/hush_print.png"),
        SHAFT=Veilborne.id("textures/environment/hush_shaft.png"),GLOW=Veilborne.id("textures/environment/glow.png"),STAR=Veilborne.id("textures/environment/star.png");
    private static final Matrix4f I=new Matrix4f();
    static final double CELL=2.5;

    // ------------------------------------------------------------------ the held air
    /** A mote pushed aside: how far, how fast it is still going. */
    private static final class Push { Vec3 off=Vec3.ZERO,vel=Vec3.ZERO; int touched; }
    private static final Map<Long,Push> pushed=new HashMap<>();
    static int hash(int x,int y,int z,int salt){return MirrorRenderer.hash3(x+salt*7919,y+salt*104729,z+salt*1299709);}
    static double h01(int x,int y,int z,int salt){return (hash(x,y,z,salt)>>>8)/16777216.0;}
    static long key(int x,int y,int z,int k){return ((long)(x&0xfffff)<<42)|((long)(y&0x3ff)<<32)|((long)(z&0xfffff)<<12)|k;}
    /** The last wind: which way it blew, and how far it had carried the air when it stopped. */
    static Vec3 wind(double p){
        var st=HushClient.state;
        double a=Math.floorMod(st.seed()>>>23,360L)*Math.PI/180;
        return new Vec3(Math.cos(a),.05,Math.sin(a)).scale(HushTimeline.gust(p)*22);
    }
    /** Motes in cell (x,y,z): up to three, each at a fixed spot in its cell, kind 0 dust, 1 leaf, 2 drop. */
    static int count(int x,int y,int z){int h=hash(x,y,z,3)&15;return h<6?0:h<11?1:h<14?2:3;}
    static Vec3 spot(int x,int y,int z,int k){
        return new Vec3((x+h01(x,y,z,10+k))*CELL,(y+h01(x,y,z,20+k))*CELL,(z+h01(x,y,z,30+k))*CELL);
    }
    /** Called each client tick: the motes near anyone moving are pushed aside and stay where they are left. */
    static void tick(Minecraft mc,double p){
        if(!TideConfig.HUSH_MOTES.get()||HushTimeline.stillness(p)<.05){pushed.clear();return;}
        Vec3 w=wind(p);
        int now=HushClient.ticks;
        for(Player pl:mc.level.players()){
            if(pl.isSpectator()||pl.distanceToSqr(mc.player)>24*24)continue;
            Vec3 v=pl.getDeltaMovement();
            if(v.lengthSqr()<1e-4)continue;
            Vec3 mid=pl.position().add(0,.9,0);
            int cx=Mth.floor((mid.x-w.x)/CELL),cy=Mth.floor((mid.y-w.y)/CELL),cz=Mth.floor((mid.z-w.z)/CELL);
            for(int x=cx-1;x<=cx+1;x++)for(int y=cy-1;y<=cy+1;y++)for(int z=cz-1;z<=cz+1;z++){
                int n=count(x,y,z);
                for(int k=0;k<n;k++){
                    long id=key(x,y,z,k);
                    Push pu=pushed.get(id);
                    Vec3 at=spot(x,y,z,k).add(w).add(pu==null?Vec3.ZERO:pu.off);
                    Vec3 d=at.subtract(mid);
                    double dh=Math.hypot(d.x,d.z);
                    if(dh>.95||Math.abs(d.y)>1.2)continue;
                    if(pu==null){pu=new Push();pushed.put(id,pu);}
                    Vec3 out=dh<1e-3?new Vec3(1,0,0):new Vec3(d.x/dh,0,d.z/dh);
                    // Swept along a little and aside, turning round you as the air closes behind.
                    pu.vel=pu.vel.add(v.scale(.22)).add(out.scale(.035)).add(new Vec3(-out.z,0,out.x).scale(.02*Math.signum(v.x*out.z-v.z*out.x+1e-6)));
                    pu.touched=now;
                }
            }
        }
        Vec3 me=mc.player.position();
        pushed.values().removeIf(pu->now-pu.touched>2400);
        for(Push pu:pushed.values()){
            // Heavy air: it moves on a moment and stops where it is, it does not spring back.
            pu.off=pu.off.add(pu.vel);pu.vel=pu.vel.scale(.84);
            if(pu.vel.lengthSqr()<1e-7)pu.vel=Vec3.ZERO;
        }
        if(pushed.size()>600)pushed.clear();
    }
    private static void air(Vec3 camera,Quaternionf facing,double p,double s,double time){
        if(!TideConfig.HUSH_MOTES.get())return;
        double still=HushTimeline.stillness(p),omen=TideTimeline.smooth(.002,.02,p);
        double show=Math.max(still,omen*(1-TideTimeline.smooth(.9,.95,p)))*s;
        if(show<.01)return;
        var level=Minecraft.getInstance().level;
        int q=TideConfig.QUALITY.get();
        double radius=new double[]{8.5,12.5,16}[q];
        Vec3 w=wind(p);
        // After the bird the held things come down again.
        double fall=TideTimeline.smooth(HushTimeline.BIRD,HushTimeline.BIRD+.06,p);
        TideFx dust=TideFx.begin(MOTE,I),glint=TideFx.begin(STAR,I),leaves=TideFx.begin(LEAF,I),drops=TideFx.begin(DROP,I);
        int cx=Mth.floor((camera.x-w.x)/CELL),cy=Mth.floor((camera.y-w.y)/CELL),cz=Mth.floor((camera.z-w.z)/CELL),rc=(int)Math.ceil(radius/CELL);
        BlockPos.MutableBlockPos m=new BlockPos.MutableBlockPos();
        for(int x=cx-rc;x<=cx+rc;x++)for(int z=cz-rc;z<=cz+rc;z++)for(int y=cy-3;y<=cy+3;y++){
            int n=count(x,y,z);
            for(int k=0;k<n;k++){
                Vec3 base=spot(x,y,z,k);
                Push pu=pushed.get(key(x,y,z,k));
                Vec3 at=base.add(w).add(pu==null?Vec3.ZERO:pu.off);
                double h=h01(x,y,z,40+k);
                if(fall>0)at=at.add(Math.sin(h*40+fall*6)*fall*.6,-fall*fall*(3+h*4),Math.cos(h*30+fall*5)*fall*.6);
                Vec3 v=at.subtract(camera);
                double d=v.length();
                if(d>radius||d<.35)continue;
                m.set(Mth.floor(at.x),Mth.floor(at.y),Mth.floor(at.z));
                var bs=level.getBlockState(m);
                if(!bs.isAir()&&bs.getFluidState().isEmpty())continue;
                double fade=show*(1-TideTimeline.smooth(radius*.72,radius,d))*(1-fall*.85);
                // What it is depends on where it hangs: a leaf under the trees, a drop over water, otherwise dust.
                int kind=0;
                if(h<.34&&m.getY()-1>level.getMinBuildHeight()){
                    m.move(0,4,0);boolean canopy=level.getBlockState(m).getBlock() instanceof LeavesBlock;
                    m.move(0,-1,0);canopy|=level.getBlockState(m).getBlock() instanceof LeavesBlock;
                    m.move(0,-4,0);boolean water=level.getFluidState(m).is(FluidTags.WATER);
                    m.move(0,-1,0);water|=level.getFluidState(m).is(FluidTags.WATER);
                    kind=water?2:canopy?1:0;
                }
                if(kind==1){
                    // Leaves hang edge-on or flat, each at its own angle, and never turn.
                    double yaw=h*40,pitch=(h01(x,y,z,50+k)-.5)*2.2,size=.14+.06*h01(x,y,z,60+k);
                    Vec3 a=new Vec3(Math.cos(yaw),Math.sin(pitch)*.6,Math.sin(yaw)).normalize().scale(size),b=new Vec3(-Math.sin(yaw),Math.cos(pitch),Math.cos(yaw)).normalize().scale(size);
                    leaves.quad(v.subtract(a).subtract(b),v.add(a).subtract(b),v.add(a).add(b),v.subtract(a).add(b),0,0,1,1,0x4a5244,fade*.9);
                }else if(kind==2){
                    drops.billboard(v,facing,.035+.02*h01(x,y,z,60+k),0,0xe8eef2,fade*.95);
                }else{
                    // Pale dust, not dark specks: dark ones read as flies, and nothing here flies.
                    double size=.012+.016*h01(x,y,z,60+k);
                    dust.billboard(v,facing,size,h*6,0xa4a4aa,fade*.6);
                    // Some of it catches the light, and holds it: a glint that does not twinkle.
                    if(h>.9)glint.billboard(v,facing,size*3.2,0,0xffffff,fade*.55);
                }
            }
        }
        leaves.end(false);drops.end(false);dust.end(false);glint.end(true);
    }

    // ------------------------------------------------------------------ everything else in the air and on the ground
    static void render(Vec3 camera,Quaternionf facing,double p,double s,double time,float partial){
        var st=HushClient.state;
        if(HushClient.active()&&st!=null){
            air(camera,facing,p,s,time);
            rings(camera,partial);
            bellThread(camera,st,p,s,time,partial);
            prints(camera,time,partial);
        }
        crumbs(partial);
    }
    /** Indoors the pale light stands in the windows and doorways, dust hanging in it; brightest while the sun is covered. */
    private static void windows(Vec3 camera,double p,double s,double time){
        double pale=HushTimeline.pale(p)*s*(1-HushTimeline.skyColour(p));
        if(pale<.01||HushClient.openings.isEmpty())return;
        double bright=pale*(.5+.8*HushClient.covered)*TideConfig.BRIGHTNESS.get();
        TideFx shaft=TideFx.begin(SHAFT,I),floor=TideFx.begin(GLOW,I);
        for(MoonClient.Opening o:HushClient.openings){
            Vec3 out=Vec3.atLowerCornerOf(o.out().getNormal()),across=new Vec3(-out.z,0,out.x);
            Vec3 sill=Vec3.atBottomCenterOf(o.inside()).add(out.scale(.48)).subtract(camera);
            // A slanted slab of light from the opening down onto the floor inside.
            Vec3 fall=out.scale(-2.4).add(0,-1.6,0);
            for(int k=-1;k<=1;k++){
                Vec3 side=across.scale(k*.3);
                Vec3 top=sill.add(0,1.7,0).add(side),bottom=sill.add(0,.15,0).add(side);
                shaft.quad(top,top.add(fall),bottom.add(fall).add(out.scale(-.5)),bottom,0,0,1,1,0xf6f4ee,bright*(.18-.05*Math.abs(k)));
            }
            Vec3 patch=sill.add(out.scale(-2.1)).add(0,.02,0);
            floor.quad(patch.add(across.scale(.6)).add(out.scale(1)),patch.add(across.scale(.6)).add(out.scale(-1.2)),patch.subtract(across.scale(.6)).add(out.scale(-1.2)),patch.subtract(across.scale(.6)).add(out.scale(1)),0,0,1,1,0xfaf8f2,bright*.28);
        }
        shaft.end(true);floor.end(true);
    }
    /** Each knock shows on the door as three rings going out from where it was struck. */
    private static void rings(Vec3 camera,float partial){
        if(HushClient.knocks.isEmpty())return;
        TideFx fx=TideFx.begin(RIPPLE,I);
        var level=Minecraft.getInstance().level;
        for(var k:HushClient.knocks){
            double t=HushClient.ticks-k.born()+partial;
            // The door's face: the knock point sits just off it, toward whoever knocked.
            Vec3 c=k.at().subtract(camera);
            Vec3 n=null;
            for(var e:level.entitiesForRendering())if(e instanceof HushCoat coat&&coat.position().distanceTo(k.at())<2.5){Vec3 d=coat.position().subtract(k.at());n=new Vec3(d.x,0,d.z).normalize();}
            if(n==null)continue;
            Vec3 u=new Vec3(-n.z,0,n.x),w=new Vec3(0,1,0);
            for(int off:new int[]{0,6,12}){
                double tt=t-off;if(tt<0||tt>30)continue;
                double r=.1+tt*.03,a=(1-tt/30)*.8;
                Vec3 cc=c.add(n.scale(.02));
                fx.quad(cc.subtract(u.scale(r)).subtract(w.scale(r)),cc.add(u.scale(r)).subtract(w.scale(r)),cc.add(u.scale(r)).add(w.scale(r)),cc.subtract(u.scale(r)).add(w.scale(r)),0,0,1,1,0x2a2a2e,a);
            }
        }
        fx.end(false);
    }
    /** The bell's thread: from what holds it, or up into nothing. */
    private static void bellThread(Vec3 camera,HushPacket st,double p,double s,double time,float partial){
        if(!st.hasBell()||!bellShown(st,p,partial))return;
        Vec3 top=bellTop(st),bell=bellSwing(st,time).subtract(camera).add(0,.28,0);
        TideFx fx=TideFx.begin(GLOW,I);
        Vec3 t=top.subtract(camera);
        int n=8;
        for(int i=0;i<n;i++){
            Vec3 a=t.lerp(bell,i/(double)n),b=t.lerp(bell,(i+1)/(double)n);
            double fade=st.bellTop().equals(BlockPos.ZERO)?TideTimeline.smooth(0,.7,i/(double)n):1;
            fx.ribbon(a,b,Vec3.ZERO,.012,0x2e2c2a,.85*fade*bellAlpha(st,p,partial));
        }
        fx.end(false);
    }
    static boolean bellShown(HushPacket st,double p,float partial){return bellAlpha(st,p,partial)>.01;}
    static double bellAlpha(HushPacket st,double p,float partial){
        if(!st.hasBell())return 0;
        double since=(HushClient.ageNow(partial)-st.bellAt())/20.0;
        double a=TideTimeline.smooth(0,2,since);
        if(st.has(HushData.BELL_DROPPED))return 0;
        if(!st.found())a*=1-TideTimeline.smooth(HushTimeline.BELL_UNTIL,HushTimeline.BELL_UNTIL+.01,p);
        return a;
    }
    static Vec3 bellTop(HushPacket st){
        return st.bellTop().equals(BlockPos.ZERO)?st.bellPoint().add(0,6,0):Vec3.atBottomCenterOf(st.bellTop()).subtract(0,.02,0);
    }
    /** The bell swings a little on its thread, the only thing in the world still moving; faster just after it rings. */
    static Vec3 bellSwing(HushPacket st,double time){
        Vec3 top=bellTop(st),hang=st.bellPoint();
        double len=top.y-hang.y;
        int since=HushTimeline.sinceRing((int)HushClient.age,st.bellAt(),st.seed());
        double ring=since>=0?Math.exp(-since/40.0):0;
        double a=(.05+.1*ring)*Math.sin(time*2.1),b=(.03+.06*ring)*Math.sin(time*1.7+1);
        return top.add(Math.sin(a)*len,-len*Math.cos(a)*Math.cos(b),Math.sin(b)*len);
    }
    /** Drawn with the entities, so the land stands in front of it: the bell itself. */
    static void entities(Vec3 camera,double p,double s,double time,float partial){
        var st=HushClient.state;
        if(st==null||!st.hasBell()||!bellShown(st,p,partial))return;
        Vec3 at=bellSwing(st,time);
        if(at.distanceTo(camera)>96)return;
        var mc=Minecraft.getInstance();
        var buffers=mc.renderBuffers().bufferSource();
        PoseStack pose=new PoseStack();
        pose.translate(at.x-camera.x,at.y-camera.y,at.z-camera.z);
        Vec3 top=bellTop(st);Vec3 d=top.subtract(at);
        pose.mulPose(Axis.ZP.rotation((float)Math.atan2(-d.x,d.y)));
        pose.mulPose(Axis.XP.rotation((float)Math.atan2(d.z,d.y)));
        pose.mulPose(Axis.YP.rotation((float)(Math.floorMod(st.seed(),360L)*Math.PI/180)));
        float sc=.62f;pose.scale(sc,sc,sc);
        mc.getItemRenderer().renderStatic(new ItemStack(Veilborne.HOLLOW_BELL.get()),ItemDisplayContext.FIXED,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,pose,buffers,mc.level,0);
        buffers.endBatch();
    }
    /** The feather: it hangs in mid-fall, quite still, and after the bird comes down turning, to land where it drops. */
    static Vec3 featherAt(HushPacket st,double p,double time,float partial){
        Vec3 ground=Vec3.atBottomCenterOf(st.feather());
        Vec3 hang=ground.add(.2,HushService.FEATHER_HEIGHT,-.15);
        double since=HushClient.ageNow(partial)-Math.round(HushTimeline.BIRD*st.duration());
        if(since<=0)return hang;
        double t=Math.min(1,since/HushService.FEATHER_FALL);
        double y=hang.y-(hang.y-ground.y-.06)*t;
        return new Vec3(hang.x+Math.sin(since*.11)*.35*(1-t)*t*4,y,hang.z+Math.cos(since*.13)*.3*(1-t)*t*4);
    }
    private static void feather(Vec3 camera,HushPacket st,double p,double s,double time,float partial){
        if(!st.hasFeather()||st.has(HushData.LANDED))return;
        double since=(HushClient.ageNow(partial)-st.featherAt())/20.0;
        double a=TideTimeline.smooth(0,3,since)*s;
        if(a<.01)return;
        Vec3 at=featherAt(st,p,time,partial).subtract(camera);
        double fallen=HushClient.ageNow(partial)-Math.round(HushTimeline.BIRD*st.duration());
        double spin=fallen>0?fallen*.09:.6,tilt=fallen>0?Math.sin(fallen*.15)*.5:.35;
        Vec3 u=new Vec3(Math.cos(spin),Math.sin(tilt)*.4,Math.sin(spin)).normalize().scale(.3),v=new Vec3(-Math.sin(spin)*.3,1,Math.cos(spin)*.3).normalize().scale(.13);
        TideFx fx=TideFx.begin(FEATHER,I);
        fx.quad(at.subtract(u).subtract(v),at.add(u).subtract(v),at.add(u).add(v),at.subtract(u).add(v),0,1,1,0,0xffffff,a);
        fx.end(false);
    }
    /** With a silent lantern in hand, the footprints the procession never leaves. */
    private static void prints(Vec3 camera,double time,float partial){
        var mc=Minecraft.getInstance();
        if(!HushService.silentLantern(mc.player))return;
        TideFx fx=null;
        for(Entity en:mc.level.entitiesForRendering()){
            if(!(en instanceof HushProcession proc)||en.distanceToSqr(camera)>64*64)continue;
            if(fx==null)fx=TideFx.begin(PRINT,I);
            double s=proc.renderPath(partial);
            for(int i=0;i<HushProcession.MEMBERS.length;i++){
                double[] m=HushProcession.MEMBERS[i];
                double lead=s-m[0];
                for(double back=.35;back<18;back+=.72){
                    double at=lead-back;if(at<0)break;
                    int step=(int)Math.floor(at/.72);
                    double side=(step&1)==0?.14:-.14;
                    Vec3 c=proc.road(at),q=proc.road(at+.4);Vec3 t=q.subtract(c).normalize();
                    double x=c.x-t.z*(m[1]+side),z=c.z+t.x*(m[1]+side);
                    double y=HushProcession.ground(mc.level,x,z)+.02;
                    double a=(1-back/18)*.55;
                    fx.flat(new Vec3(x,y,z).subtract(camera),i==2?.1:.13,Math.atan2(t.z,t.x)+Math.PI/2,0,0,1,1,0x44423e,a);
                }
            }
        }
        if(fx!=null)fx.end(false);
    }
    /** Graphite crumbs along the eraser's line while something is rubbed out. */
    private static int crumbTick=-1;
    private static void crumbs(float partial){
        var mc=Minecraft.getInstance();
        if(crumbTick==HushClient.ticks||mc.level==null)return;
        crumbTick=HushClient.ticks;
        for(Entity en:mc.level.entitiesForRendering()){
            if(en.distanceToSqr(mc.player)>48*48)continue;
            if(en instanceof HushCoat c){
                float e=c.erased(partial);
                if(e<=0||e>=1)continue;
                double y=c.getY()+2.05-e*2.05;
                for(int k=0;k<4;k++)mc.level.addParticle(Veilborne.HUSH_CRUMB.get(),c.getX()+(Math.random()-.5)*.7,y,c.getZ()+(Math.random()-.5)*.7,0,0,0);
            }else if(en instanceof HushProcession proc){
                float e=proc.erased(partial);
                if(e<=0||e>=1)continue;
                double s=proc.renderPath(partial);
                for(double[] m:HushProcession.MEMBERS){
                    Vec3 at=proc.member(s,m[0],m[1]);
                    double y=at.y+1.95-e*1.95;
                    for(int k=0;k<2;k++)mc.level.addParticle(Veilborne.HUSH_CRUMB.get(),at.x+(Math.random()-.5)*.6,y,at.z+(Math.random()-.5)*.6,0,0,0);
                }
            }
        }
    }

    // ------------------------------------------------------------------ amber, drawn after the colour is gone
    static Vec3 processionLantern(HushProcession proc,float partial){
        double s=proc.renderPath(partial);
        Vec3 lead=proc.member(s,0,0);
        float yaw=proc.yawAt(s,0);
        double lift=proc.answering()?Math.min(1,(proc.stateClock()+partial)/14.0)*(1-TideTimeline.smooth(HushProcession.ANSWER_TICKS-20,HushProcession.ANSWER_TICKS,proc.stateClock()+partial)):0;
        double a=Math.toRadians(yaw);
        Vec3 fwd=new Vec3(-Math.sin(a),0,Math.cos(a));
        return lead.add(fwd.scale(.62-.2*lift)).add(0,1.28+.95*lift,0);
    }
    static void glows(Vec3 camera,Quaternionf facing,double p,double s,double time,float partial){
        var mc=Minecraft.getInstance();
        // The jay's feather keeps its blue, as the bell keeps its bronze: drawn after the colour has gone from everything else.
        var st=HushClient.state;
        if(HushClient.active()&&st!=null){
            feather(camera,st,p,s,time,partial);
            // The pale light in the windows, after the colour pass so that it stays the one clean white indoors.
            windows(camera,p,s,time);
        }
        TideFx fx=TideFx.begin(STAR,I),soft=TideFx.begin(GLOW,I);
        for(Entity en:mc.level.entitiesForRendering()){
            if(!(en instanceof HushProcession proc)||en.distanceToSqr(camera)>96*96)continue;
            float vis=proc.visibility(partial);
            if(vis<.02)continue;
            Vec3 l=processionLantern(proc,partial).subtract(camera);
            double ans=proc.answering()?1-TideTimeline.smooth(HushProcession.ANSWER_TICKS-20,HushProcession.ANSWER_TICKS,proc.stateClock()+partial):0;
            fx.billboard(l,facing,.16+.12*ans,0,0xffd79a,vis*.95);
            soft.billboard(l,facing,.9+1.6*ans,0,HushRenderer.AMBER,vis*(.35+.4*ans));
        }
        for(var f:HushClient.flashes){
            double t=(HushClient.ticks-f.born()+partial)/20.0;
            double a=(1-TideTimeline.smooth(.1,1.4,t));
            if(a<=0)continue;
            Vec3 c=f.at().subtract(camera);
            fx.billboard(c,facing,.35+t*.8,t,0xfff0d0,a);
            soft.billboard(c,facing,1.2+f.reach()*.25*TideTimeline.smooth(0,.5,t),0,HushRenderer.AMBER,a*.5);
        }
        for(BlockPos lp:HushClient.lanterns){
            Vec3 c=Vec3.atCenterOf(lp).subtract(camera).add(0,-.12,0);
            if(c.lengthSqr()>48*48)continue;
            soft.billboard(c,facing,.7,0,HushRenderer.AMBER,.45*s);
        }
        soft.end(true);fx.end(true);
    }
    private HushGround(){}
}
