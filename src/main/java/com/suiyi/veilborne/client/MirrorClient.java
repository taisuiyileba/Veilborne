package com.suiyi.veilborne.client;

import com.suiyi.veilborne.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * Client half of 镜天巡礼: interpolated state, the height of the sky's mirror plane, the pilgrim's pose and footfalls,
 * late footstep echoes, afterimages, the drone and hymn, still water that shows the sky of afterwards, and the
 * trail the echo mirror replays. Also the gallery: its drone and the flash on the way in and out.
 */
public final class MirrorClient {
    public static MirrorPacket state;
    public static float age,previousAge,intensity,exposure=1,flash;
    public static int ticks;
    private static Level lastLevel;
    private static Drone drone;
    private static double lastProgress=-1,lastStep=Double.NaN;
    private static int hymnAt=80,hymnNext;
    private static final RandomSource random=RandomSource.create();
    /** Height of the sky mirror: the land is reflected across y = mirrorH. */
    static double mirrorH=Double.NaN,baseGround=Double.NaN,mirrorTarget=Double.NaN;
    record Ripple(Vec3 at,int born){}
    record Echo(ResourceLocation sound,double x,double y,double z,float volume,float pitch,int at){}
    record Pilgrim(Vec3 base,double yaw,double s,double gaze,double visible,double walk){}
    static final List<Ripple> ripples=new ArrayList<>();
    private static final List<Echo> echoes=new ArrayList<>();
    static final Map<Integer,ArrayDeque<MoonClient.Frame>> history=new HashMap<>();
    static final ArrayDeque<Vec3> trail=new ArrayDeque<>();
    static List<Vec3> echoTrail=List.of();
    static int echoAt=-10000;
    static final List<BlockPos> water=new ArrayList<>(),lanterns=new ArrayList<>();
    static final List<MoonClient.Opening> openings=new ArrayList<>();
    static final int HISTORY=40;

    public static void receive(MirrorPacket p){
        if(state==null||state.seed()!=p.seed()){lastProgress=-1;ripples.clear();lastStep=Double.NaN;}
        state=p;TideClient.journalRecords=p.records();age=previousAge=p.age();
    }
    public static double progress(float partial){return state==null?0:MirrorTimeline.progress(previousAge+(age-previousAge)*partial,state.duration());}
    public static double strength(){return intensity;}
    public static boolean active(){return state!=null&&intensity>.001;}
    static float ageNow(float partial){return previousAge+(age-previousAge)*partial;}
    static boolean inGallery(){var l=Minecraft.getInstance().level;return l!=null&&MirrorGallery.inGallery(l);}
    public static double dayness(float partial){
        var level=Minecraft.getInstance().level;
        return level==null?0:Mth.clamp((level.getSkyDarken(partial)-.2)/.8,0,1);
    }
    /** The prism is held up to the eye. */
    static boolean scoping(){
        var p=Minecraft.getInstance().player;
        return p!=null&&p.isUsingItem()&&MirrorItem.isPrism(p.getUseItem());
    }
    static void clear(){
        state=null;intensity=0;lastProgress=-1;ripples.clear();echoes.clear();history.clear();water.clear();openings.clear();
        mirrorH=baseGround=mirrorTarget=Double.NaN;lastStep=Double.NaN;
        if(drone!=null){drone.stop2();drone=null;}
    }

    /** The pilgrim's walk, or null when it is not out. Its base is the real ground point its reflection stands on. */
    static Pilgrim pilgrim(float partial){
        if(state==null||!state.has(MirrorData.PILGRIM))return null;
        double p=progress(partial),vis=MirrorTimeline.pilgrim(p);
        if(vis<.002)return null;
        double w=MirrorTimeline.walk(p),s=MirrorTimeline.along(w);
        Vec3 origin=Vec3.atBottomCenterOf(state.pilgrimOrigin());
        Vec3 at=MirrorTimeline.pilgrimAt(origin,state.pilgrimYaw(),s);
        Vec3 ahead=MirrorTimeline.pilgrimAt(origin,state.pilgrimYaw(),s+2),behind=MirrorTimeline.pilgrimAt(origin,state.pilgrimYaw(),s-2);
        double yaw=Math.atan2(ahead.z-behind.z,ahead.x-behind.x);
        double turn=MirrorTimeline.turn(w);
        if(turn>0){
            // Stopped beside the watcher, it turns to face them.
            double face=Math.atan2(origin.z-at.z,origin.x-at.x);
            yaw+=Math.atan2(Math.sin(face-yaw),Math.cos(face-yaw))*turn;
        }
        double y=groundAt(at.x,at.z);
        return new Pilgrim(new Vec3(at.x,y,at.z),yaw,s,MirrorTimeline.gaze(w),vis,w);
    }
    static double groundAt(double x,double z){
        var level=Minecraft.getInstance().level;
        if(level!=null&&level.hasChunk(Mth.floor(x)>>4,Mth.floor(z)>>4)){
            int y=Surface.top(level,Mth.floor(x),Mth.floor(z));
            if(y>level.getMinBuildHeight()+2)return Double.isNaN(baseGround)?y:Math.max(baseGround-12,Math.min(baseGround+18,y));
        }
        return Double.isNaN(baseGround)?64:baseGround;
    }
    /** A real-space point moved to where the sky mirror shows it, camera-relative. */
    static Vec3 reflect(Vec3 world,Vec3 camera){return new Vec3(world.x-camera.x,2*mirrorH-world.y-camera.y,world.z-camera.z);}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        if(mc.level!=lastLevel){
            boolean gallery=mc.level!=null&&MirrorGallery.inGallery(mc.level),wasGallery=lastLevel!=null&&MirrorGallery.inGallery(lastLevel);
            if(gallery!=wasGallery&&mc.level!=null)flash=1;
            if(lastLevel==null||mc.level==null)clear();
            else{history.clear();water.clear();openings.clear();lanterns.clear();}
            lastLevel=mc.level;
        }
        if(mc.level==null||mc.player==null){clear();return;}
        if(mc.isPaused())return;
        ticks++;previousAge=age;flash=Math.max(0,flash-.035f);
        if(state!=null&&state.active())age=Math.min(age+1,state.duration());
        if(ticks%5==0){trail.addLast(mc.player.position());while(trail.size()>120)trail.removeFirst();}
        if(ticks%40==0)scanLanterns(mc);
        boolean gallery=inGallery();
        if(gallery||intensity>.001)recordHistory(mc);else history.clear();
        double target=0;
        if(state!=null&&state.active()&&mc.level.dimension().equals(Level.OVERWORLD))
            target=MirrorTimeline.region(Math.hypot(mc.player.getX()-state.center().getX(),mc.player.getZ()-state.center().getZ()),state.radius());
        intensity+=(target-intensity)*.06f;
        if(gallery){galleryTick(mc);return;}
        if(intensity<.001){intensity=0;if(drone!=null){drone.stop2();drone=null;}echoes.clear();MirrorWorld.idle();return;}
        double p=progress(1);
        if(ticks%10==0){
            BlockPos eye=BlockPos.containing(mc.player.getEyePosition());
            float e=mc.level.canSeeSky(eye)?1:.3f;
            if(mc.player.isUnderWater())e=.14f;
            exposure+=(e-exposure)*.4f;
        }
        plane(mc);
        sounds(mc,p);
        particles(mc,p);
        footfalls(mc,p);
        if(ticks%10==0)scanWater(mc);
        if(ticks%30==0)scanOpenings(mc);
        ripples.removeIf(r->ticks-r.born()>150);
        playEchoes(mc);
    }
    private static void galleryTick(Minecraft mc){
        if(TideConfig.SOUND.get()&&drone==null){drone=new Drone();mc.getSoundManager().play(drone);}
        playEchoes(mc);
    }

    // ------------------------------------------------------------------ the mirror plane
    private static void plane(Minecraft mc){
        var pl=mc.player;
        if(ticks%40==0||Double.isNaN(baseGround)){
            double sum=0;int n=0;
            for(int i=-3;i<=3;i++)for(int j=-3;j<=3;j++){
                int x=Mth.floor(pl.getX())+i*14,z=Mth.floor(pl.getZ())+j*14;
                if(!mc.level.hasChunk(x>>4,z>>4))continue;
                int y=Surface.top(mc.level,x,z);if(y<=mc.level.getMinBuildHeight()+2)continue;
                sum+=y;n++;
            }
            if(n>0){double g=sum/n;baseGround=Double.isNaN(baseGround)?g:baseGround+(g-baseGround)*.5;}
            if(Double.isNaN(baseGround))baseGround=pl.getY();
        }
        mirrorTarget=Math.max(baseGround+58,pl.getEyeY()+30);
        mirrorH=Double.isNaN(mirrorH)?mirrorTarget:mirrorH+Mth.clamp(mirrorTarget-mirrorH,-.4,.4);
    }

    // ------------------------------------------------------------------ sound
    private static float volume(){return (float)(TideConfig.SOUND_VOLUME.get()*intensity);}
    private static void sounds(Minecraft mc,double p){
        boolean on=TideConfig.SOUND.get();
        if(on&&drone==null){drone=new Drone();mc.getSoundManager().play(drone);}
        if(!on&&drone!=null){drone.stop2();drone=null;}
        if(on&&lastProgress>=0){
            Vec3 up=mc.player.getEyePosition().add(0,90,0);
            if(crossed(.004,p)||crossed(.045,p))play(mc,Veilborne.MIRROR_OMEN.get(),volume()*.8f,1,up);
            if(crossed(.06,p)||crossed(.14,p))play(mc,Veilborne.MIRROR_SILVER.get(),volume()*.7f,crossed(.14,p)?.9f:1,up);
            for(double cue:new double[]{.31,.44,.74,.78,.82,.855})if(crossed(cue,p))play(mc,Veilborne.MIRROR_CRACK.get(),volume()*(cue<.5?.45f:.8f),.85f+random.nextFloat()*.25f,up.add((random.nextDouble()-.5)*80,0,(random.nextDouble()-.5)*80));
            if(crossed(.885,p))mc.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(Veilborne.MIRROR_SHATTER.get(),1,volume()));
        }
        lastProgress=p;
        // 巡礼歌: phrases of one slow chant while the pilgrim walks, played in order with gaps, a few out of place.
        if(on&&MirrorTimeline.pilgrim(p)>.3&&--hymnAt<=0){
            int phrase=hymnNext;
            mc.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(Veilborne.HYMN.get(phrase).get(),.97f+random.nextFloat()*.06f,volume()*(.35f+.35f*exposure)));
            hymnNext=(phrase+1+(random.nextFloat()<.2f?1:0))%5;
            hymnAt=150+random.nextInt(120);
        }
    }
    private static boolean crossed(double cue,double p){return lastProgress<cue&&p>=cue;}
    static void play(Minecraft mc,SoundEvent event,float volume,float pitch,Vec3 at){
        mc.getSoundManager().play(new SimpleSoundInstance(event.getLocation(),SoundSource.WEATHER,volume,pitch,RandomSource.create(),false,0,SoundInstance.Attenuation.NONE,at.x,at.y,at.z,false));
    }
    private static final class Drone extends AbstractTickableSoundInstance {
        Drone(){super(Veilborne.MIRROR_DRONE.get(),SoundSource.WEATHER,RandomSource.create());looping=true;delay=0;relative=true;attenuation=Attenuation.NONE;volume=.01f;}
        @Override public void tick(){
            if(Minecraft.getInstance().level==null||!TideConfig.SOUND.get()){stop();return;}
            if(inGallery()){volume=(float)(.32*TideConfig.SOUND_VOLUME.get());pitch=.84f;return;}
            if(intensity<=.001f){stop();return;}
            double p=progress(1);
            volume=(float)(intensity*(.14+.5*MirrorTimeline.mirror(p)+.15*MirrorTimeline.pilgrim(p))*(.3+exposure*.7)*TideConfig.SOUND_VOLUME.get());
            pitch=.92f+exposure*.08f;
        }
        void stop2(){stop();}
    }
    /** Some footsteps come back a beat late, a little lower, as if from the other side of the glass. */
    @SubscribeEvent public static void sound(PlaySoundEvent e){
        SoundInstance s=e.getSound();
        if(s==null||s instanceof EchoSound||!TideConfig.MIRROR_ECHOES.get())return;
        var mc=Minecraft.getInstance();
        if(mc.player==null||!s.getLocation().getPath().endsWith(".step"))return;
        boolean gallery=inGallery();
        if(!gallery&&(!active()||MirrorTimeline.echoes(progress(1))<.2))return;
        if(Math.abs(s.getX()-mc.player.getX())>2||Math.abs(s.getZ()-mc.player.getZ())>2||random.nextFloat()>(gallery?.8f:.55f))return;
        echoes.add(new Echo(s.getLocation(),s.getX(),s.getY(),s.getZ(),.45f,.92f,ticks+(gallery?14:9)+random.nextInt(4)));
    }
    private static void playEchoes(Minecraft mc){
        echoes.removeIf(e->{
            if(ticks<e.at())return false;
            mc.getSoundManager().play(new EchoSound(e));
            return true;
        });
    }
    private static final class EchoSound extends SimpleSoundInstance {
        EchoSound(Echo e){super(e.sound(),SoundSource.PLAYERS,e.volume()*TideConfig.SOUND_VOLUME.get().floatValue()*1.2f,e.pitch(),RandomSource.create(),false,0,SoundInstance.Attenuation.LINEAR,e.x(),e.y()+.4,e.z(),false);}
    }

    // ------------------------------------------------------------------ particles
    private static void particles(Minecraft mc,double p){
        var level=mc.level;var pl=mc.player;
        int q=TideConfig.QUALITY.get();
        // 镜兆: glints lift off still water.
        if(!water.isEmpty()&&random.nextFloat()<.35f*intensity*MirrorTimeline.omen(p)){
            BlockPos w=water.get(random.nextInt(water.size()));
            level.addParticle(Veilborne.MIRROR_MOTE.get(),w.getX()+random.nextDouble(),w.getY()+.05,w.getZ()+random.nextDouble(),0,.012+random.nextDouble()*.01,0);
        }
        // Twin motes: one rises from the ground as its mirror image comes down, and they meet in a spark.
        double twins=intensity*MirrorTimeline.mirror(p)*(1-MirrorTimeline.shatter(p));
        for(int i=0;i<1+q;i++){
            if(random.nextFloat()>twins*.6f)continue;
            double x=pl.getX()+(random.nextDouble()-.5)*36,z=pl.getZ()+(random.nextDouble()-.5)*36;
            int ground=Surface.top(level,Mth.floor(x),Mth.floor(z));
            if(Math.abs(ground-pl.getY())>10||!level.canSeeSky(new BlockPos(Mth.floor(x),ground,Mth.floor(z))))continue;
            double h=1.4+random.nextDouble()*2.2,v=h/30;
            level.addParticle(Veilborne.MIRROR_MOTE.get(),x,ground+.05,z,0,v,0);
            level.addParticle(Veilborne.MIRROR_MOTE.get(),x,ground+.05+2*h,z,0,-v,0);
        }
        // Flakes of the breaking mirror come down as light.
        double flakes=intensity*(MirrorTimeline.crack(p)*.25*smoothFrom(p,.72)+MirrorTimeline.shatter(p))*(1-smoothFrom(p,.985));
        for(int i=0;i<2+q*2;i++){
            if(random.nextFloat()>flakes)continue;
            double x=pl.getX()+(random.nextDouble()-.5)*40,z=pl.getZ()+(random.nextDouble()-.5)*40;
            int ground=level.getHeight(Heightmap.Types.MOTION_BLOCKING,Mth.floor(x),Mth.floor(z));
            double y=Math.max(ground+6,pl.getY()+10+random.nextDouble()*14);
            level.addParticle(Veilborne.FLAKE.get(),x,y,z,(random.nextDouble()-.5)*.02,-.03,(random.nextDouble()-.5)*.02);
        }
    }
    private static double smoothFrom(double p,double a){return TideTimeline.smooth(a,a+.03,p);}
    /** Each footfall of the pilgrim: a ring across the glass, a deep ring of the staff, dust where the foot lands. */
    private static void footfalls(Minecraft mc,double p){
        Pilgrim g=pilgrim(1);
        if(g==null){lastStep=Double.NaN;return;}
        double step=Math.floor(MirrorTimeline.stepPhase(g.s()));
        if(!Double.isNaN(lastStep)&&step!=lastStep&&g.gaze()<.2){
            double side=((int)step&1)==0?1:-1;
            Vec3 d=MirrorTimeline.heading(g.yaw()),across=new Vec3(-d.z,0,d.x);
            Vec3 foot=g.base().add(d.scale(4)).add(across.scale(side*4));
            ripples.add(new Ripple(new Vec3(foot.x,groundAt(foot.x,foot.z),foot.z),ticks));
            if(ripples.size()>4)ripples.remove(0);
            double dist=Math.hypot(foot.x-mc.player.getX(),foot.z-mc.player.getZ());
            if(TideConfig.SOUND.get())mc.getSoundManager().play(new SimpleSoundInstance(Veilborne.MIRROR_STEP.get().getLocation(),SoundSource.WEATHER,
                (float)(volume()*Math.max(.25,1-dist/260)),.95f+random.nextFloat()*.08f,RandomSource.create(),false,0,SoundInstance.Attenuation.NONE,foot.x,foot.y+40,foot.z,false));
            if(dist<48)for(int i=0;i<24;i++){
                double a=random.nextDouble()*Math.PI*2,r=random.nextDouble()*5;
                double x=foot.x+Math.cos(a)*r,z=foot.z+Math.sin(a)*r;
                mc.level.addParticle(Veilborne.MIRROR_MOTE.get(),x,Surface.top(mc.level,Mth.floor(x),Mth.floor(z))+.1,z,Math.cos(a)*.01,.03+random.nextDouble()*.03,Math.sin(a)*.01);
            }
        }
        lastStep=step;
    }

    // ------------------------------------------------------------------ scans
    private static void scanWater(Minecraft mc){
        water.clear();
        var pl=mc.player;var level=mc.level;
        int r=TideConfig.QUALITY.get()==0?8:12;
        for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++){
            if(x*x+z*z>r*r)continue;
            int wx=Mth.floor(pl.getX())+x,wz=Mth.floor(pl.getZ())+z;
            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING,wx,wz);
            BlockPos top=new BlockPos(wx,y-1,wz);
            if(Math.abs(y-pl.getY())>8||!level.getFluidState(top).is(FluidTags.WATER)||!level.canSeeSky(top.above()))continue;
            water.add(top);
            if(water.size()>400)return;
        }
    }
    private static void scanLanterns(Minecraft mc){
        lanterns.clear();
        BlockPos o=mc.player.blockPosition();
        for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)for(int y=-5;y<=6;y++){
            BlockPos p=o.offset(x,y,z);
            if(mc.level.getBlockState(p).is(Veilborne.MIRROR_LANTERN.get()))lanterns.add(p);
            if(lanterns.size()>=16)return;
        }
    }
    private static boolean roofless(Level level,BlockPos p){return level.getHeight(Heightmap.Types.MOTION_BLOCKING,p.getX(),p.getZ())<=p.getY();}
    /** Doorways and windows seen from inside, where the silver light comes in. */
    private static void scanOpenings(Minecraft mc){
        openings.clear();
        var level=mc.level;BlockPos o=BlockPos.containing(mc.player.getEyePosition());
        if(roofless(level,o))return;
        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)for(int y=-2;y<=1;y++){
            BlockPos pos=o.offset(x,y,z);
            if(!level.getBlockState(pos).isAir()||roofless(level,pos)||level.getBlockState(pos.below()).isAir())continue;
            faces:
            for(Direction face:Direction.Plane.HORIZONTAL)for(int up=0;up<=2;up++){
                BlockPos n=pos.relative(face).above(up);var st=level.getBlockState(n);
                if(up>0&&!level.getBlockState(pos.above(up)).isAir())break;
                boolean window=st.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS)||st.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_PANES);
                boolean open=up==0&&st.isAir()&&roofless(level,n);
                if(open||window&&roofless(level,n.relative(face))){openings.add(new MoonClient.Opening(pos,face));break faces;}
            }
            if(openings.size()>=24)return;
        }
    }
    private static void recordHistory(Minecraft mc){
        Set<Integer> seen=new HashSet<>();
        for(Player pl:mc.level.players()){
            if(pl.distanceToSqr(mc.player)>48*48||pl.isSpectator())continue;
            seen.add(pl.getId());
            var q=history.computeIfAbsent(pl.getId(),k->new ArrayDeque<>());
            q.addLast(new MoonClient.Frame(pl.getX(),pl.getY(),pl.getZ(),pl.yBodyRot,pl.yHeadRot,pl.getXRot(),pl.walkAnimation.position(),Math.min(1,pl.walkAnimation.speed()),pl.isCrouching()));
            while(q.size()>HISTORY)q.removeFirst();
        }
        history.keySet().retainAll(seen);
    }
    /** The pose {@code lag} ticks ago, interpolated the way the live model is. */
    static MoonClient.Frame lagged(ArrayDeque<MoonClient.Frame> q,float partial,int lag){
        MoonClient.Frame[] a=q.toArray(new MoonClient.Frame[0]);
        int i=Math.max(0,a.length-2-lag);
        MoonClient.Frame f0=a[i],f1=a[Math.min(a.length-1,i+1)];
        return new MoonClient.Frame(Mth.lerp(partial,f0.x(),f1.x()),Mth.lerp(partial,f0.y(),f1.y()),Mth.lerp(partial,f0.z(),f1.z()),
            Mth.rotLerp(partial,f0.body(),f1.body()),Mth.rotLerp(partial,f0.head(),f1.head()),Mth.lerp(partial,f0.pitch(),f1.pitch()),
            Mth.lerp(partial,f0.limb(),f1.limb()),Mth.lerp(partial,f0.speed(),f1.speed()),f1.crouch());
    }
    /** 回响镜: replay the last half minute of footsteps. */
    static void showEcho(){echoTrail=new ArrayList<>(trail);echoAt=ticks;}

    @SubscribeEvent public static void fog(ViewportEvent.ComputeFogColor e){
        if(inGallery()){e.setRed(.18f);e.setGreen(.21f);e.setBlue(.25f);return;}
        if(state==null||intensity<.001)return;
        double p=progress((float)e.getPartialTick()),day=dayness((float)e.getPartialTick());
        double s=intensity*MirrorTimeline.mirror(p)*(1-.6*MirrorTimeline.shatter(p))*.7;
        if(s<=0)return;
        double r=Mth.lerp(day,.2,.66),g=Mth.lerp(day,.24,.71),b=Mth.lerp(day,.29,.76);
        e.setRed((float)(e.getRed()*(1-s)+r*s));e.setGreen((float)(e.getGreen()*(1-s)+g*s));e.setBlue((float)(e.getBlue()*(1-s)+b*s));
    }
    private MirrorClient(){}
}
