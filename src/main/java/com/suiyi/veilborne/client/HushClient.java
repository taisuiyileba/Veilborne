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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Client half of 无声白昼: interpolated state, the pale disc and the black sun, how much of the world's sound is let through
 * (read by the sound mixin), the few sounds the weather itself makes and their silences, the clock the clouds hang on, and
 * everything the colour pass needs to know about: lamps and lights people carry, still candles, raised lights, knocks,
 * footsteps, the island round a found bell and the rings its ringing sends out.
 */
public final class HushClient {
    public static HushPacket state;
    public static float age,previousAge,intensity,exposure=1;
    public static int ticks,records;
    private static Level lastLevel;
    private static Drone drone;
    private static double lastProgress=-1;
    private static int harmonyAt=900,whisperAt=120;
    private static final RandomSource random=RandomSource.create();
    /** The clouds' clock: how many ticks they have lost, and how fast they move now. */
    static double cloudLost;static float cloudSpeed=1;
    /** 0..1 of the pale disc the black sun covers, as drawn this frame. */
    static double covered;
    /** Lamps and lights people carry near you: where, how far their warmth reaches. */
    record Lamp(Vec3 at,double radius,double strength){}
    static final List<Lamp> lamps=new ArrayList<>();
    static final List<BlockPos> candles=new ArrayList<>(),lanterns=new ArrayList<>(),feathers=new ArrayList<>();
    /** Someone raised a light: where, how far it reaches, when. */
    record Flash(Vec3 at,double reach,int born){}
    static final List<Flash> flashes=new ArrayList<>();
    record Knock(Vec3 at,int born){}
    static final List<Knock> knocks=new ArrayList<>();
    record Print(Vec3 at,int born){}
    static final List<Print> prints=new ArrayList<>();
    private static final Map<Integer,Vec3> lastStep=new HashMap<>();
    static final List<MoonClient.Opening> openings=new ArrayList<>();
    /** The hollow bell played back: which weather, where, when; and the black sun answering. */
    static int echoAt=-10000,echoKind=-1,heardAt=-10000;static Vec3 echoPos=Vec3.ZERO,heardPos=Vec3.ZERO;

    public static void receive(HushPacket p){
        if(state==null||state.seed()!=p.seed()){lastProgress=-1;}
        state=p;records=p.records();age=previousAge=p.age();
    }
    public static void cue(HushCue c){
        var mc=Minecraft.getInstance();
        if(mc.player==null)return;
        switch(c.kind()){
            case HushCue.FLASH -> flashes.add(new Flash(c.at(),c.value(),ticks));
            case HushCue.KNOCK -> knocks.add(new Knock(c.at(),ticks));
            case HushCue.ECHO -> echo(mc,c.at(),c.value());
            case HushCue.HEARD -> {heardAt=ticks;heardPos=c.at();if(TideConfig.SOUND.get())play(mc,Veilborne.HUSH_KNOCK.get(),.9f,.62f,c.at().subtract(mc.player.getEyePosition()).normalize().scale(60).add(mc.player.getEyePosition()));}
            default -> {}
        }
    }
    public static double progress(float partial){return state==null?0:HushTimeline.progress(previousAge+(age-previousAge)*partial,state.duration());}
    public static double strength(){return intensity;}
    public static boolean active(){return state!=null&&intensity>.001;}
    static float ageNow(float partial){return previousAge+(age-previousAge)*partial;}
    public static double dayness(float partial){
        var level=Minecraft.getInstance().level;
        return level==null?0:Mth.clamp((level.getSkyDarken(partial)-.2)/.8,0,1);
    }
    /** The pale disc: the sun, or the moon if that is what the black sun is after. */
    static Vec3 white(float partial){
        var level=Minecraft.getInstance().level;
        return HushTimeline.whiteDir(level==null?0:level.getTimeOfDay(partial),state!=null&&state.moon());
    }
    static Vec3 black(float partial){return state==null?new Vec3(0,1,0):HushTimeline.blackDir(state.seed(),white(partial),progress(partial));}
    static void clear(){
        state=null;intensity=0;lastProgress=-1;flashes.clear();knocks.clear();prints.clear();lastStep.clear();covered=0;
        if(drone!=null){drone.stop2();drone=null;}
    }
    /** 0..1: how much the island of sound round a found bell holds you. */
    static double islandHere(){
        if(state==null||!state.found())return 0;
        var mc=Minecraft.getInstance();
        if(mc.player==null)return 0;
        double s=HushTimeline.island((int)age-state.foundAt());
        if(s<=0)return 0;
        double d=mc.player.position().distanceTo(state.bellPoint());
        return s*(1-TideTimeline.smooth(HushTimeline.ISLAND_RANGE*s-2,HushTimeline.ISLAND_RANGE*s+1,d));
    }
    /** Still candles near you keep a little quiet at any time; a hollow bell rung empty makes a moment of it. */
    static double localQuiet(SoundSource src,boolean step){
        if(src==SoundSource.HOSTILE||src==SoundSource.PLAYERS&&!step)return 1;
        double q=1;
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&!candles.isEmpty()){
            Vec3 me=mc.player.position();
            for(BlockPos c:candles){double d=Vec3.atCenterOf(c).distanceTo(me);q=Math.min(q,.3+.7*TideTimeline.smooth(2.5,6.5,d));}
        }
        if(echoKind==4){double t=(ticks-echoAt)/20.0;if(t>=0&&t<7)q*=1-.94*TideTimeline.smooth(0,.6,t)*(1-TideTimeline.smooth(4.5,7,t));}
        return q;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        if(mc.level!=lastLevel){
            if(lastLevel==null||mc.level==null)clear();else{flashes.clear();knocks.clear();prints.clear();lamps.clear();candles.clear();lanterns.clear();}
            cloudLost=0;cloudSpeed=1;
            lastLevel=mc.level;
        }
        if(mc.level==null||mc.player==null){clear();return;}
        if(mc.isPaused())return;
        ticks++;previousAge=age;
        if(ticks%20==0)scanLamps(mc);
        flashes.removeIf(f->ticks-f.born()>60);knocks.removeIf(k->ticks-k.born()>70);prints.removeIf(p->ticks-p.born()>44);
        if(state!=null&&state.active())age=Math.min(age+1,state.duration());
        double target=0;
        if(state!=null&&state.active()&&mc.level.dimension().equals(Level.OVERWORLD))
            target=HushTimeline.region(Math.hypot(mc.player.getX()-state.center().getX(),mc.player.getZ()-state.center().getZ()),state.radius());
        intensity+=(target-intensity)*.05f;
        if(intensity<.001){intensity=0;cloudSpeed=1;if(drone!=null){drone.stop2();drone=null;}refreshVolumes(mc);return;}
        double p=progress(1);
        // The clouds' clock slows to a stop and later runs on.
        cloudSpeed=(float)(1-(1-HushTimeline.cloudSpeed(p))*intensity);
        cloudLost+=1-cloudSpeed;
        Vec3 w=white(1),b=black(1);
        covered=HushTimeline.coverage(b,w)*HushTimeline.blackVisible(p)*intensity;
        if(ticks%10==0){
            BlockPos eye=BlockPos.containing(mc.player.getEyePosition());
            float e=mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING,eye.getX(),eye.getZ())<=eye.getY()?1:.35f;
            if(mc.player.isUnderWater())e=.14f;
            exposure+=(e-exposure)*.4f;
        }
        sounds(mc,p);
        footsteps(mc,p);
        HushGround.tick(mc,p);
        if(ticks%30==0)scanOpenings(mc);
        if(ticks%4==0)refreshVolumes(mc);
    }
    /** Sounds already playing that are not ticked by the engine (music, most one-shots) are recalculated now and then. */
    private static float lastGains=-1;
    private static void refreshVolumes(Minecraft mc){
        double p=progress(1);
        float g=(float)(intensity*(HushTimeline.music(p)+HushTimeline.ambient(p)+HushTimeline.animals(p)+HushTimeline.blocks(p))+islandHere()*3+(candles.isEmpty()?0:candles.size()*.1));
        if(Math.abs(g-lastGains)<.01)return;
        lastGains=g;
        mc.getSoundManager().updateSourceVolume(SoundSource.AMBIENT,mc.options.getSoundSourceVolume(SoundSource.AMBIENT));
    }

    // ------------------------------------------------------------------ sound
    private static float volume(){return (float)(TideConfig.SOUND_VOLUME.get()*intensity);}
    private static boolean crossed(double cue,double p){return lastProgress<cue&&p>=cue;}
    private static void sounds(Minecraft mc,double p){
        boolean on=TideConfig.SOUND.get();
        if(on&&drone==null){drone=new Drone();mc.getSoundManager().play(drone);}
        if(!on&&drone!=null){drone.stop2();drone=null;}
        Vec3 eye=mc.player.getEyePosition();
        if(on&&lastProgress>=0){
            if(crossed(.004,p))play(mc,Veilborne.HUSH_OMEN.get(),volume()*.8f,1,eye.add(0,40,0));
            if(crossed(1.05/12,p))mc.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(Veilborne.HUSH_FADE.get(),1,volume()*.7f));
            if(crossed(HushTimeline.TOTAL0-.005,p))mc.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(Veilborne.HUSH_HEART.get(),1,volume()));
            if(crossed(HushTimeline.TOTAL1,p))play(mc,Veilborne.HUSH_TING.get(),volume()*.7f,1,eye.add(black(1).scale(60)));
            if(crossed(HushTimeline.BIRD,p))bird(mc);
            if(crossed(.905,p))mc.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(Veilborne.HUSH_RETURN.get(),1,volume()*.85f));
            // The thunder: late, low, from where the line stood, whatever the distance.
            int th=HushTimeline.thunderTick(state.duration());
            if(lastProgress*state.duration()<th&&p*state.duration()>=th){
                Vec3 at=lineBase();
                play(mc,Veilborne.HUSH_THUNDER.get(),volume()*(.6f+.4f*exposure),.92f+random.nextFloat()*.06f,eye.add(at.subtract(eye).normalize().scale(90)));
            }
        }
        lastProgress=p;
        // A harmony so low you are not sure you heard it, a long while apart; never while the sun is covered.
        if(on&&p>1.4/12&&p<HushTimeline.BIRD-.02&&HushTimeline.totality(p)<.01&&--harmonyAt<=0){
            mc.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(Veilborne.HARMONY.get(random.nextInt(4)).get(),.98f+random.nextFloat()*.04f,volume()*(.2f+.2f*exposure)));
            harmonyAt=1100+random.nextInt(900);
        }
        // Near the procession: a sound that is not there, whose subtitle is.
        if(on&&--whisperAt<=0){
            whisperAt=110+random.nextInt(60);
            for(var e:mc.level.entitiesForRendering())if(e instanceof HushProcession proc&&proc.present()&&e.distanceToSqr(mc.player)<24*24){
                double[] m=HushProcession.MEMBERS[random.nextInt(HushProcession.MEMBERS.length)];
                Vec3 at=proc.member(proc.renderPath(1),m[0],m[1]).add(0,1.6,0);
                mc.getSoundManager().play(new SimpleSoundInstance(Veilborne.HUSH_WHISPER.get().getLocation(),SoundSource.AMBIENT,.6f,1,RandomSource.create(),false,0,SoundInstance.Attenuation.LINEAR,at.x,at.y,at.z,false));
                break;
            }
        }
    }
    /** The first bird of the return sings from the nearest tree, or from somewhere off to one side. */
    private static void bird(Minecraft mc){
        BlockPos o=mc.player.blockPosition();Vec3 at=null;double best=1e9;
        for(int i=0;i<300;i++){
            BlockPos b=o.offset(random.nextInt(33)-16,random.nextInt(14)-2,random.nextInt(33)-16);
            if(mc.level.getBlockState(b).getBlock() instanceof LeavesBlock&&mc.level.getBlockState(b.above()).isAir()){
                double d=b.distSqr(o);if(d>16&&d<best){best=d;at=Vec3.atCenterOf(b);}
            }
        }
        if(at==null){double a=random.nextDouble()*Math.PI*2;at=mc.player.getEyePosition().add(Math.cos(a)*12,4,Math.sin(a)*12);}
        birdAt=at;
        mc.getSoundManager().play(new SimpleSoundInstance(Veilborne.HUSH_BIRD.get().getLocation(),SoundSource.AMBIENT,volume()*1.2f+.2f,1,RandomSource.create(),false,0,SoundInstance.Attenuation.LINEAR,at.x,at.y,at.z,false));
    }
    static Vec3 birdAt=null;
    static void play(Minecraft mc,SoundEvent event,float volume,float pitch,Vec3 at){
        mc.getSoundManager().play(new SimpleSoundInstance(event.getLocation(),SoundSource.WEATHER,volume,pitch,RandomSource.create(),false,0,SoundInstance.Attenuation.NONE,at.x,at.y,at.z,false));
    }
    /** Where the white line stands: far out on the horizon from the event's centre. */
    static Vec3 lineBase(){
        Vec3 c=Vec3.atBottomCenterOf(state.center()),d=HushTimeline.lineDir(state.seed());
        return c.add(d.scale(HushTimeline.LINE_DISTANCE));
    }
    private static final class Drone extends AbstractTickableSoundInstance {
        Drone(){super(Veilborne.HUSH_DRONE.get(),SoundSource.WEATHER,RandomSource.create());looping=true;delay=0;relative=true;attenuation=Attenuation.NONE;volume=.01f;}
        @Override public void tick(){
            if(Minecraft.getInstance().level==null||!TideConfig.SOUND.get()||intensity<=.001f){stop();return;}
            double p=progress(1);
            // The pressure of a silence: it grows with the hush, and even it stops while the sun is covered.
            volume=(float)(intensity*.55*HushTimeline.hush(p)*(1-HushTimeline.totality(p))*(1-islandHere())*(.5+.5*exposure)*TideConfig.SOUND_VOLUME.get());
            pitch=1;
        }
        void stop2(){stop();}
    }
    /** The hollow bell played back: a little of a weather's sound, or, empty, a moment of silence. */
    private static void echo(Minecraft mc,Vec3 at,int kind){
        echoAt=ticks;echoKind=kind;echoPos=at;
        if(!TideConfig.SOUND.get())return;
        Vec3 up=at.add(0,6,0);
        switch(kind){
            case 0 -> {play(mc,Veilborne.WHALE.get(),.8f,1,at.add(8,14,-6));play(mc,Veilborne.CHIME.get(),.5f,1,up);}
            case 1 -> {play(mc,Veilborne.LULLABY.get(random.nextInt(Veilborne.LULLABY.size())).get(),.7f,1,up);play(mc,Veilborne.MOON_BELL.get(),.4f,1,at);}
            case 2 -> {play(mc,Veilborne.HYMN.get(random.nextInt(Veilborne.HYMN.size())).get(),.7f,1,up);play(mc,Veilborne.MIRROR_OMEN.get(),.4f,1,at);}
            case 3 -> {play(mc,Veilborne.WEAVE.get(random.nextInt(Veilborne.WEAVE.size())).get(),.7f,1,up);play(mc,Veilborne.THREAD_LOOM.get(),.35f,1,at.add(20,6,0));}
            case 5 -> {play(mc,Veilborne.DREAM_PHRASES.get(random.nextInt(Veilborne.DREAM_PHRASES.size())).get(),.7f,1,up);play(mc,Veilborne.DREAM_CALL.get(),.4f,1,at.add(-14,12,10));}
            default -> play(mc,Veilborne.HUSH_HOLLOW.get(),.8f,1,at);
        }
    }

    // ------------------------------------------------------------------ raising a light
    @SubscribeEvent public static void use(PlayerInteractEvent.RightClickItem e){
        if(!e.getLevel().isClientSide||!HushService.isLight(e.getItemStack()))return;
        var mc=Minecraft.getInstance();
        if(e.getEntity()!=mc.player||!active()||HushTimeline.hush(progress(1))<.5)return;
        if(!flashes.isEmpty()&&flashes.get(flashes.size()-1).born()>ticks-HushService.FLASH_COOLDOWN)return;
        Player pl=mc.player;
        Vec3 hand=pl.getEyePosition().add(pl.getLookAngle().scale(.6)).add(0,-.25,0);
        flashes.add(new Flash(hand,HushService.silentLantern(pl)?HushService.LANTERN_REACH:HushService.FLASH_REACH,ticks));
    }

    // ------------------------------------------------------------------ footsteps, and what the colour pass reads
    /** Each step anyone takes leaves a moment of colour where their foot came down, since it makes no sound. */
    private static void footsteps(Minecraft mc,double p){
        if(HushTimeline.hush(p)<.3){lastStep.clear();return;}
        for(Player pl:mc.level.players()){
            if(pl.isSpectator()||!pl.onGround()||pl.distanceToSqr(mc.player)>40*40)continue;
            Vec3 now=pl.position(),was=lastStep.get(pl.getId());
            if(was==null||was.distanceToSqr(now)>9){lastStep.put(pl.getId(),now);continue;}
            if(Math.hypot(now.x-was.x,now.z-was.z)>.92){
                lastStep.put(pl.getId(),now);
                Vec3 side=new Vec3(-(now.z-was.z),0,now.x-was.x).normalize().scale((prints.size()&1)==0?.16:-.16);
                prints.add(new Print(now.add(side).add(0,.04,0),ticks));
                if(prints.size()>24)prints.remove(0);
            }
        }
    }
    /** Lamps, still candles and silent lanterns near you, at any time (candles keep their quiet even without the weather). */
    private static void scanLamps(Minecraft mc){
        lamps.clear();candles.clear();lanterns.clear();feathers.clear();
        BlockPos o=mc.player.blockPosition();
        var level=mc.level;
        List<Lamp> found=new ArrayList<>();
        BlockPos.MutableBlockPos m=new BlockPos.MutableBlockPos();
        for(int x=-18;x<=18;x++)for(int z=-18;z<=18;z++){
            if(!level.hasChunk((o.getX()+x)>>4,(o.getZ()+z)>>4))continue;
            for(int y=-8;y<=10;y++){
                m.set(o.getX()+x,o.getY()+y,o.getZ()+z);
                BlockState s=level.getBlockState(m);
                if(s.isAir())continue;
                if(s.is(Veilborne.STILL_CANDLE.get())){if(candles.size()<12)candles.add(m.immutable());continue;}
                if(s.is(Veilborne.STILL_FEATHER.get())){if(feathers.size()<6)feathers.add(m.immutable());continue;}
                int light=s.getLightEmission(level,m);
                if(light<8)continue;
                boolean lantern=s.is(Veilborne.SILENT_LANTERN.get());
                if(lantern&&lanterns.size()<8)lanterns.add(m.immutable());
                found.add(new Lamp(Vec3.atCenterOf(m),lantern?11:1.2+light*.27,lantern?1:.62+light*.02));
            }
        }
        Vec3 me=mc.player.position();
        found.sort(Comparator.comparingDouble(l->l.at().distanceToSqr(me)));
        lamps.addAll(found.subList(0,Math.min(24,found.size())));
    }
    private static boolean roofless(Level level,BlockPos p){return level.getHeight(Heightmap.Types.MOTION_BLOCKING,p.getX(),p.getZ())<=p.getY();}
    /** Windows and doorways seen from inside, where the pale light comes in. */
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

    @SubscribeEvent public static void fog(ViewportEvent.ComputeFogColor e){
        if(state==null||intensity<.001)return;
        double p=progress((float)e.getPartialTick());
        double s=intensity*HushTimeline.pale(p)*(1-HushTimeline.skyColour(p))*.9;
        if(s<=0)return;
        double day=dayness((float)e.getPartialTick());
        double v=Mth.lerp(day,.74,.86)+.08*covered;
        e.setRed((float)(e.getRed()*(1-s)+v*.985*s));e.setGreen((float)(e.getGreen()*(1-s)+v*.99*s));e.setBlue((float)(e.getBlue()*(1-s)+v*s));
    }
    private HushClient(){}
}
