package com.suiyi.veilborne.client;

import com.suiyi.veilborne.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Client half of 绯线缝世: interpolated state, the seam and the needle's pose, light that runs along the horizon when the
 * needle goes in and the loom's thud that follows as far behind as the needle is distant, the drone and the weaving song,
 * fibres drifting toward the seam, the pull on warned rings, and the scans that let the seam touch the world: rock faces and
 * trunks for gold seams, beams indoors for vibrating threads, windows for the crimson light, and sky-weaving lamps.
 */
public final class ThreadClient {
    public static ThreadPacket state;
    public static float age,previousAge,intensity,exposure=1;
    public static int ticks;
    private static Level lastLevel;
    private static Drone drone;
    private static double lastProgress=-1,lastClock=Double.NaN;
    private static int weaveAt=60,weaveNext;
    private static final RandomSource random=RandomSource.create();
    private static ThreadTimeline.Seam seam;
    private static long seamSeed;private static BlockPos seamCenter=BlockPos.ZERO;
    record Delayed(SoundEvent sound,Vec3 at,float volume,float pitch,int when){}
    private static final List<Delayed> delayed=new ArrayList<>();
    /** The last needle-in: when, where along the horizon, and how bright its light was. */
    static double flashAt=-1e9,flashAzimuth;static float flashStrength;
    /** A gold seam in a rock face or trunk: its top block, the open side, how many blocks it runs down, and when it began. */
    record Face(BlockPos pos,Direction out,int length,int start){}
    static final int FACE_LIFE=170,FACES=26;
    record Beam(Vec3 a,Vec3 b,int phase){}
    static final List<Face> faces=new ArrayList<>();
    static final List<Beam> beams=new ArrayList<>();
    static final List<MoonClient.Opening> openings=new ArrayList<>();
    static final List<BlockPos> lamps=new ArrayList<>(),anchors=new ArrayList<>();
    /** 引线: red gossamer hung from high places (treetops, roofs, posts) and strung between them. */
    record Gossamer(Vec3 a,Vec3 b,boolean hanging,int phase){}
    static final List<Gossamer> gossamer=new ArrayList<>();
    static final Map<Integer,ArrayDeque<MoonClient.Frame>> history=new HashMap<>();
    static final int HISTORY=40;

    public static void receive(ThreadPacket p){
        if(state==null||state.seed()!=p.seed()){lastProgress=-1;lastClock=Double.NaN;delayed.clear();}
        state=p;TideClient.journalRecords=p.records();age=previousAge=p.age();
    }
    public static double progress(float partial){return state==null?0:ThreadTimeline.progress(previousAge+(age-previousAge)*partial,state.duration());}
    public static double strength(){return intensity;}
    public static boolean active(){return state!=null&&intensity>.001;}
    static float ageNow(float partial){return previousAge+(age-previousAge)*partial;}
    public static double dayness(float partial){
        var level=Minecraft.getInstance().level;
        return level==null?0:Mth.clamp((level.getSkyDarken(partial)-.2)/.8,0,1);
    }
    static ThreadTimeline.Seam seam(){
        if(state==null)return null;
        if(seam==null||seamSeed!=state.seed()||!seamCenter.equals(state.center())){seam=state.seam();seamSeed=state.seed();seamCenter=state.center();}
        return seam;
    }
    /** The needle wherever it is right now, or null. */
    static ThreadTimeline.Pose needle(float partial){
        if(state==null)return null;
        double p=progress(partial);
        if(p>=ThreadTimeline.SEW_START){
            if(!state.hasLand())return p<ThreadTimeline.pAtSew(ThreadTimeline.ASCEND)?ThreadTimeline.stitching(seam(),Math.min(p,ThreadTimeline.STITCH_END)):null;
            return ThreadTimeline.sewing(seam(),state.footA(),state.footB(),p);
        }
        return ThreadTimeline.stitching(seam(),p);
    }
    static void clear(){
        state=null;intensity=0;lastProgress=-1;lastClock=Double.NaN;delayed.clear();faces.clear();beams.clear();openings.clear();history.clear();
        if(drone!=null){drone.stop2();drone=null;}
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        if(mc.level!=lastLevel){
            if(lastLevel==null||mc.level==null)clear();else{history.clear();faces.clear();beams.clear();openings.clear();lamps.clear();}
            lastLevel=mc.level;
        }
        if(mc.level==null||mc.player==null){clear();return;}
        if(mc.isPaused())return;
        ticks++;previousAge=age;
        if(ticks%40==0)scanLamps(mc);
        if(state!=null&&state.active())age=Math.min(age+1,state.duration());
        double target=0;
        if(state!=null&&state.active()&&mc.level.dimension().equals(Level.OVERWORLD))
            target=ThreadTimeline.region(Math.hypot(mc.player.getX()-state.center().getX(),mc.player.getZ()-state.center().getZ()),state.radius());
        intensity+=(target-intensity)*.06f;
        if(intensity<.001){intensity=0;if(drone!=null){drone.stop2();drone=null;}delayed.clear();history.clear();return;}
        double p=progress(1);
        if(ticks%10==0){
            BlockPos eye=BlockPos.containing(mc.player.getEyePosition());
            float e=mc.level.canSeeSky(eye)?1:.3f;
            if(mc.player.isUnderWater())e=.14f;
            exposure+=(e-exposure)*.4f;
        }
        if(ThreadTimeline.sew(p)>.3&&ThreadTimeline.sew(p)<.75)recordHistory(mc);else if(!history.isEmpty())history.clear();
        sounds(mc,p);
        particles(mc,p);
        if(ticks%10==0)scanFaces(mc);
        if(ticks%30==0){scanBeams(mc);scanOpenings(mc);}
        if(ticks%60==0||gossamer.isEmpty()&&ticks%10==0)scanGossamer(mc);
        pocketSound(mc,p);
        delayed.removeIf(d->{
            if(ticks<d.when())return false;
            if(TideConfig.SOUND.get())play(mc,d.sound(),d.volume(),d.pitch(),d.at());
            return true;
        });
    }

    // ------------------------------------------------------------------ sound
    private static float volume(){return (float)(TideConfig.SOUND_VOLUME.get()*intensity);}
    private static void sounds(Minecraft mc,double p){
        boolean on=TideConfig.SOUND.get();
        if(on&&drone==null){drone=new Drone();mc.getSoundManager().play(drone);}
        if(!on&&drone!=null){drone.stop2();drone=null;}
        Vec3 eye=mc.player.getEyePosition();
        Vec3 up=eye.add(0,90,0);
        if(on&&lastProgress>=0){
            if(crossed(.004,p))play(mc,Veilborne.THREAD_OMEN.get(),volume()*.8f,1,up);
            if(crossed(.045,p))play(mc,Veilborne.THREAD_OMEN.get(),volume()*.6f,.84f,up);
            for(double cue:new double[]{1.12/13,1.6/13,2.2/13,2.75/13})if(crossed(cue,p))
                play(mc,Veilborne.THREAD_TEAR.get(),volume()*(.55f+.1f*(float)(cue*13-1)),.9f+random.nextFloat()*.15f,ThreadTimeline.middle(seam()));
            if(crossed(ThreadTimeline.pAtSew(ThreadTimeline.TUG0),p))mc.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(Veilborne.THREAD_TUG.get(),1,volume()));
            if(crossed(ThreadTimeline.KNOT,p))play(mc,Veilborne.THREAD_KNOT.get(),volume(),.8f,ThreadTimeline.middle(seam()));
            if(crossed(ThreadTimeline.CUT,p))mc.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(Veilborne.THREAD_SNAP.get(),.8f,volume()));
            if(crossed(.9,p))mc.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(Veilborne.THREAD_FADE.get(),1,volume()*.9f));
            stitchCues(mc,p);
        }
        lastProgress=p;
        // 织歌: a slow weaving song while the world is sewn, phrases in order with long gaps, now and then one out of place.
        double q=ThreadTimeline.sew(p);
        if(on&&p>ThreadTimeline.SEW_START&&q>.1&&q<.95&&--weaveAt<=0){
            int phrase=weaveNext;
            mc.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(Veilborne.WEAVE.get(phrase).get(),.97f+random.nextFloat()*.05f,volume()*(.32f+.38f*exposure)));
            weaveNext=(phrase+1+(random.nextFloat()<.2f?1:0))%5;
            weaveAt=160+random.nextInt(120);
        }
    }
    /** Each stitch: when the point goes into the cloth the horizon lights first; the loom's thud comes as late as the needle is far. */
    private static void stitchCues(Minecraft mc,double p){
        double c=ThreadTimeline.clock(p);
        if(!Double.isNaN(lastClock)&&c>=0&&c<ThreadTimeline.STITCHES){
            int k=(int)Math.floor(c);
            if(Math.floor(lastClock)<k||lastClock<0)needleIn(mc,ThreadTimeline.hole(seam(),k,p,true),1);
            double exit=ThreadTimeline.exitAt(k);
            if(lastClock-k<exit&&c-k>=exit)delay(Veilborne.THREAD_PIERCE.get(),ThreadTimeline.hole(seam(),k,p,false),volume()*.7f,1+random.nextFloat()*.1f);
        }
        if(state.hasLand()){
            Vec3 a=state.footA(),b=state.footB();
            double q0=ThreadTimeline.pAtSew(ThreadTimeline.EMERGE);
            if(crossed(q0,p))needleIn(mc,ThreadTimeline.middle(seam()),.8f);
            if(crossed(ThreadTimeline.strikeA(),p))flash(mc,a,1.3f);
            if(crossed(ThreadTimeline.strikeB(a,b),p))flash(mc,b,1.3f);
        }
        lastClock=c;
    }
    private static void needleIn(Minecraft mc,Vec3 at,float strength){
        flash(mc,at,strength);
        delay(Veilborne.THREAD_LOOM.get(),at,volume()*(.75f+.25f*strength),.94f+random.nextFloat()*.1f);
    }
    private static void flash(Minecraft mc,Vec3 at,float strength){
        Vec3 eye=mc.player.getEyePosition();
        flashAt=ticks;flashAzimuth=Math.atan2(at.z-eye.z,at.x-eye.x);flashStrength=strength*(float)(TideConfig.BRIGHTNESS.get()*1.0);
    }
    /** Sound travels about 320 blocks a second; light gets there first. */
    private static void delay(SoundEvent sound,Vec3 at,float volume,float pitch){
        var mc=Minecraft.getInstance();
        double dist=at.distanceTo(mc.player.getEyePosition());
        Vec3 dir=at.subtract(mc.player.getEyePosition()).normalize();
        delayed.add(new Delayed(sound,mc.player.getEyePosition().add(dir.scale(Math.min(dist,96))),volume,pitch,ticks+(int)Math.min(80,dist/16)));
    }
    private static boolean crossed(double cue,double p){return lastProgress<cue&&p>=cue;}
    static void play(Minecraft mc,SoundEvent event,float volume,float pitch,Vec3 at){
        mc.getSoundManager().play(new SimpleSoundInstance(event.getLocation(),SoundSource.WEATHER,volume,pitch,RandomSource.create(),false,0,SoundInstance.Attenuation.NONE,at.x,at.y,at.z,false));
    }
    private static final class Drone extends AbstractTickableSoundInstance {
        Drone(){super(Veilborne.THREAD_DRONE.get(),SoundSource.WEATHER,RandomSource.create());looping=true;delay=0;relative=true;attenuation=Attenuation.NONE;volume=.01f;}
        @Override public void tick(){
            if(Minecraft.getInstance().level==null||!TideConfig.SOUND.get()||intensity<=.001f){stop();return;}
            double p=progress(1);
            double tear=ThreadTimeline.tear(p),veil=ThreadTimeline.veil(p);
            volume=(float)(intensity*(.1+.35*veil+.15*tear+.25*ThreadTimeline.strings(p))*(.35+exposure*.65)*TideConfig.SOUND_VOLUME.get());
            pitch=(float)(.9+exposure*.08+.06*ThreadTimeline.tug(p));
        }
        void stop2(){stop();}
    }

    // ------------------------------------------------------------------ particles
    /** Where things lean: the needle while it sews, the land stitch while it is pulled, otherwise the seam's middle. */
    /** Someone cut the land stitch with shears: its thread, the pull and what goes with the pull do not happen. */
    static boolean snipped(){return state!=null&&state.has(ThreadData.SNIPPED);}
    static Vec3 pullTarget(double p){
        if(state.hasLand()&&!snipped()&&ThreadTimeline.landLoop(p)){
            var pl=Minecraft.getInstance().player;
            return ThreadTimeline.nearestOnLine(state.footA(),state.footB(),pl.position()).add(0,6,0);
        }
        var n=needle(1);
        return n!=null&&n.visible()>.3?n.tipPoint():ThreadTimeline.middle(seam());
    }
    private static void particles(Minecraft mc,double p){
        var level=mc.level;var pl=mc.player;
        int q=TideConfig.QUALITY.get();
        double lean=ThreadTimeline.pull(p)+ThreadTimeline.omen(p)*.4;
        Vec3 target=pullTarget(p);
        boolean pocket=state.hasPocket()&&ThreadTimeline.pocketOpen(p);
        Vec3 calm=pocket?Vec3.atBottomCenterOf(state.pocket()):null;
        for(int i=0;i<1+q;i++){
            if(random.nextFloat()>intensity*Math.min(1,.2+lean*.8))continue;
            double x=pl.getX()+(random.nextDouble()-.5)*34,z=pl.getZ()+(random.nextDouble()-.5)*34;
            // Not in your face: a fibre spawned right at the eye fills the screen as a blot.
            if(Math.hypot(x-pl.getX(),z-pl.getZ())<3.5)continue;
            int ground=Surface.top(level,Mth.floor(x),Mth.floor(z));
            double y=Math.max(ground+.3,pl.getY()-2)+random.nextDouble()*7;
            if(!level.canSeeSky(BlockPos.containing(x,y,z))||calm!=null&&Math.hypot(x-calm.x,z-calm.z)<ThreadService.POCKET_RANGE)continue;
            Vec3 to=new Vec3(target.x-x,0,target.z-z).normalize().scale(.02+.07*lean);
            level.addParticle(Veilborne.THREAD_FIBER.get(),x,y,z,to.x,.002+random.nextDouble()*.004,to.z);
        }
        // Warned rings: fibres and sparks spiral in toward the middle as the warning builds.
        for(int i=0;i<ThreadData.STRIKES;i++){
            BlockPos s=state.strike(i);
            if(s.equals(BlockPos.ZERO))continue;
            int at=ThreadTimeline.strikeTick(i,state.duration());
            warned(level,Vec3.atBottomCenterOf(s),ThreadTimeline.STRIKE_RANGE,ThreadTimeline.warning((int)age,at,ThreadTimeline.STRIKE_WARN),at);
        }
        if(state.hasLand()){
            Vec3 a=state.footA(),b=state.footB();
            for(int f=0;f<2;f++){
                int at=ThreadTimeline.landTick(f==1,a,b,state.duration()),from=Math.min(ThreadTimeline.landWarnTick(state.duration()),at-ThreadTimeline.LAND_WARN_TICKS);
                warned(level,f==0?a:b,ThreadTimeline.LAND_RANGE,age<from||age>=at?-1:(age-from)/(double)(at-from),at);
            }
            // The land seam while the world is sewn: sparks along the line between the feet.
            if(ThreadTimeline.landLoop(p)&&p>ThreadTimeline.strikeB(a,b)&&!snipped()){
                double t=ThreadTimeline.tug(p);
                for(int k=0;k<1+q+(int)(t*6);k++){
                    Vec3 on=a.lerp(b,random.nextDouble());
                    if(on.distanceTo(pl.position())>48)continue;
                    double y=Surface.top(level,Mth.floor(on.x),Mth.floor(on.z));
                    level.addParticle(Veilborne.THREAD_EMBER.get(),on.x+(random.nextDouble()-.5)*.8,y+.1,on.z+(random.nextDouble()-.5)*.8,0,.02+t*.06,0);
                }
            }
        }
    }
    private static void warned(Level level,Vec3 c,double range,double w,int at){
        var pl=Minecraft.getInstance().player;
        if(c.distanceTo(pl.position())>80)return;
        if(w>=0){
            for(int k=0;k<2+(int)(w*6);k++){
                double a=random.nextDouble()*Math.PI*2,r=range*(.4+random.nextDouble()*.6);
                double x=c.x+Math.cos(a)*r,z=c.z+Math.sin(a)*r;
                Vec3 in=new Vec3(c.x-x,0,c.z-z).scale(.02+.04*w).add(new Vec3(-Math.sin(a),0,Math.cos(a)).scale(.05));
                level.addParticle(random.nextFloat()<.4f?Veilborne.THREAD_EMBER.get():Veilborne.THREAD_FIBER.get(),x,Surface.top(level,Mth.floor(x),Mth.floor(z))+.15,z,in.x,.01+w*.02,in.z);
            }
        }else if(age>=at&&age<at+3){
            for(int k=0;k<40;k++){
                double a=random.nextDouble()*Math.PI*2,sp=.2+random.nextDouble()*.5;
                level.addParticle(Veilborne.THREAD_EMBER.get(),c.x,c.y+.3,c.z,Math.cos(a)*sp,.1+random.nextDouble()*.35,Math.sin(a)*sp);
            }
        }
    }

    // ------------------------------------------------------------------ scans
    private static boolean seamable(BlockState s){return s.is(BlockTags.BASE_STONE_OVERWORLD)||s.is(BlockTags.LOGS)||s.is(BlockTags.TERRACOTTA)||s.is(net.minecraft.world.level.block.Blocks.SANDSTONE)||s.is(net.minecraft.world.level.block.Blocks.COBBLESTONE)||s.is(net.minecraft.world.level.block.Blocks.STONE_BRICKS);}
    /** Rock faces and trunks near you, side faces open to the air, where gold seams can open and be stitched shut. */
    private static void scanFaces(Minecraft mc){
        if(!TideConfig.THREAD_SEAMS.get()){faces.clear();return;}
        var level=mc.level;BlockPos o=mc.player.blockPosition();
        // Each seam lives out its whole life (opens, is stitched shut, fades) and is then replaced; never popped mid-way.
        faces.removeIf(f->ticks-f.start()>FACE_LIFE||f.pos().distManhattan(o)>30||!seamable(level.getBlockState(f.pos())));
        for(int i=0;i<160&&faces.size()<FACES;i++){
            int x=o.getX()+random.nextInt(25)-12,y=o.getY()+random.nextInt(10)-3,z=o.getZ()+random.nextInt(25)-12;
            BlockPos b=new BlockPos(x,y,z);
            if(!seamable(level.getBlockState(b)))continue;
            // Mostly on the side turned toward you, where it can be seen.
            Direction d=random.nextFloat()<.8f?Direction.getNearest(o.getX()-x,0,o.getZ()-z):Direction.Plane.HORIZONTAL.getRandomDirection(random);
            if(!level.getBlockState(b.relative(d)).isAir())continue;
            boolean taken=false;for(Face f:faces)if(f.pos().distManhattan(b)<3)taken=true;
            if(taken)continue;
            // It runs down the face as far as the rock goes, up to three blocks.
            int len=1;
            while(len<3&&seamable(level.getBlockState(b.below(len)))&&level.getBlockState(b.below(len).relative(d)).isAir())len++;
            faces.add(new Face(b,d,len,ticks+random.nextInt(90)));
        }
    }
    private static boolean roofless(Level level,BlockPos p){return level.getHeight(Heightmap.Types.MOTION_BLOCKING,p.getX(),p.getZ())<=p.getY();}
    /** Indoors: the undersides of wooden beams overhead, where threads hum. */
    private static void scanBeams(Minecraft mc){
        beams.clear();
        var level=mc.level;BlockPos o=BlockPos.containing(mc.player.getEyePosition());
        if(roofless(level,o))return;
        for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)for(int y=1;y<=4;y++){
            BlockPos b=o.offset(x,y,z);
            BlockState s=level.getBlockState(b);
            if(!(s.is(BlockTags.LOGS)||s.is(BlockTags.PLANKS)||s.is(BlockTags.WOODEN_SLABS))||!level.getBlockState(b.below()).isAir())continue;
            Vec3 c=Vec3.atBottomCenterOf(b).add(0,-.01,0);
            boolean ew=((b.getX()*7+b.getZ()*3)&1)==0;
            beams.add(new Beam(c.add(ew?-.5:0,0,ew?0:-.5),c.add(ew?.5:0,0,ew?0:.5),random.nextInt(100)));
            if(beams.size()>=40)return;
        }
    }
    /** Doorways and windows seen from inside, where the crimson light comes in. */
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
    /** Sky-weaving lamps and tuning anchors nearby, at any time. */
    private static void scanLamps(Minecraft mc){
        lamps.clear();anchors.clear();
        BlockPos o=mc.player.blockPosition();
        for(int x=-16;x<=16;x++)for(int z=-16;z<=16;z++)for(int y=-6;y<=8;y++){
            BlockPos p=o.offset(x,y,z);
            var st=mc.level.getBlockState(p);
            if(st.is(Veilborne.LOOM_LANTERN.get())&&lamps.size()<24)lamps.add(p.immutable());
            else if(st.is(Veilborne.TUNING_ANCHOR.get())&&anchors.size()<8)anchors.add(p.immutable());
        }
    }
    /** High places near you: where the top of a column stands well above the ground under it. */
    private static void scanGossamer(Minecraft mc){
        gossamer.clear();
        var level=mc.level;BlockPos o=mc.player.blockPosition();
        List<Vec3> high=new ArrayList<>();
        RandomSource r=RandomSource.create(o.asLong()/97);
        for(int i=0;i<320&&high.size()<20;i++){
            int x=o.getX()+r.nextInt(33)-16,z=o.getZ()+r.nextInt(33)-16;
            if(!level.hasChunk(x>>4,z>>4))continue;
            int top=level.getHeight(Heightmap.Types.MOTION_BLOCKING,x,z),ground=Surface.top(level,x,z);
            if(top-ground<3||Math.abs(top-o.getY())>18)continue;
            Vec3 h=new Vec3(x+.5,top,z+.5);
            boolean near=false;for(Vec3 v:high)if(v.distanceToSqr(h)<9)near=true;
            if(!near)high.add(h);
        }
        for(int i=0;i<high.size();i++){
            Vec3 a=high.get(i);
            // Hung from the top edge on an open side, so it hangs in the air beside the column and not inside it.
            BlockPos top=BlockPos.containing(a.x,a.y-1,a.z);
            for(Direction d:Direction.Plane.HORIZONTAL){
                if(!level.getBlockState(top.relative(d)).isAir())continue;
                Vec3 edge=a.add(d.getStepX()*.54,-.02,d.getStepZ()*.54);
                gossamer.add(new Gossamer(edge,edge.add(0,-(2+r.nextInt(4)),0),true,r.nextInt(100)));
                break;
            }
            for(int j=i+1;j<high.size();j++){
                Vec3 b=high.get(j);double d=a.distanceTo(b);
                if(d>3.5&&d<12&&r.nextFloat()<.7f)gossamer.add(new Gossamer(a,b,false,r.nextInt(100)));
            }
        }
    }
    /** Inside the pocket an old room ticks. */
    private static void pocketSound(Minecraft mc,double p){
        if(!TideConfig.SOUND.get()||!state.hasPocket()||!ThreadTimeline.pocketOpen(p)||state.has(ThreadData.SEWN_UP)||ticks%80!=0)return;
        Vec3 c=Vec3.atBottomCenterOf(state.pocket());
        double d=c.distanceTo(mc.player.position());
        if(d<18)mc.getSoundManager().play(new SimpleSoundInstance(Veilborne.THREAD_POCKET.get().getLocation(),SoundSource.AMBIENT,(float)(volume()*(1-d/18)),1,RandomSource.create(),false,0,SoundInstance.Attenuation.NONE,c.x,c.y+1,c.z,false));
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

    @SubscribeEvent public static void fog(ViewportEvent.ComputeFogColor e){
        if(state==null||intensity<.001)return;
        double p=progress((float)e.getPartialTick()),day=dayness((float)e.getPartialTick());
        double s=intensity*ThreadTimeline.veil(p)*.62;
        if(s<=0)return;
        double r=Mth.lerp(day,.2,.66),g=Mth.lerp(day,.05,.34),b=Mth.lerp(day,.06,.27);
        e.setRed((float)(e.getRed()*(1-s)+r*s));e.setGreen((float)(e.getGreen()*(1-s)+g*s));e.setBlue((float)(e.getBlue()*(1-s)+b*s));
    }
    private ThreadClient(){}
}
