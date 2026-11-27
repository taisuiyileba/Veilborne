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
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Client half of 月骸花潮: interpolated state, the flower field around the player, petals, lamps, shadows and sound. */
public final class MoonClient {
    public static MoonPacket state;
    public static float age,previousAge,exposure=1,intensity;
    public static int ticks;
    private static net.minecraft.world.level.Level lastLevel;
    private static Drone drone;
    private static double lastProgress=-1;
    private static int lastBrokenAt,lullabyAt=200,lullabyNext;
    private static final RandomSource random=RandomSource.create();

    /** A flower shadow on the ground. Positions are world-anchored so the field does not follow the camera. */
    static final class Patch {
        final double x,y,z;final int variant;final float rot,size,threshold;final long key;final boolean water,path;
        Patch(double x,double y,double z,int variant,float rot,float size,float threshold,long key,boolean water,boolean path){
            this.x=x;this.y=y;this.z=z;this.variant=variant;this.rot=rot;this.size=size;this.threshold=threshold;this.key=key;this.water=water;this.path=path;
        }
    }
    record Bloom(Vec3 pos,int born,int variant,float rot,float size){}
    record Opening(BlockPos inside,Direction out){}
    record Frame(double x,double y,double z,float body,float head,float pitch,float limb,float speed,boolean crouch){}
    static final List<Patch> field=new ArrayList<>();
    static final List<Bloom> blooms=new ArrayList<>();
    static final Map<Long,Integer> trampled=new HashMap<>();
    static final List<BlockPos> lamps=new ArrayList<>();
    static final List<Opening> openings=new ArrayList<>();
    static final Map<Integer,ArrayDeque<Frame>> history=new HashMap<>();
    static float pathReveal;
    private static double fieldX=Double.NaN,fieldZ;
    private static int fieldTick;

    public static void receive(MoonPacket p){
        if(state==null||state.seed()!=p.seed()){lastProgress=-1;blooms.clear();trampled.clear();field.clear();fieldX=Double.NaN;lastBrokenAt=p.brokenAt();}
        state=p;TideClient.journalRecords=p.records();age=previousAge=p.age();
    }
    public static double progress(float partial){return state==null?0:MoonTimeline.progress(previousAge+(age-previousAge)*partial,state.duration());}
    public static double strength(){return intensity;}
    public static boolean active(){return state!=null&&intensity>.001;}
    public static double merge(float partial){
        if(state==null)return 0;
        return MoonData.merge(progress(partial),(int)(previousAge+(age-previousAge)*partial),state.brokenAt(),state.broken());
    }
    public static Vec3 trueDir(float partial){return state==null?new Vec3(0,1,0):MoonTimeline.trueDir(state.seed(),progress(partial),merge(partial));}
    /** Petals are always blown from the true moon: one of the clues when three hang in the sky. */
    public static Vec3 wind(float partial){
        Vec3 d=trueDir(partial);double h=Math.hypot(d.x,d.z);
        return h<1e-4?Vec3.ZERO:new Vec3(-d.x/h,0,-d.z/h);
    }
    /** 0 at night, 1 in full daylight. The bloom keeps a paler sky by day. */
    public static double dayness(float partial){
        var level=Minecraft.getInstance().level;
        return level==null?0:Mth.clamp((level.getSkyDarken(partial)-.2)/.8,0,1);
    }
    public static void clear(){
        state=null;intensity=0;lastProgress=-1;field.clear();blooms.clear();trampled.clear();lamps.clear();openings.clear();history.clear();fieldX=Double.NaN;pathReveal=0;
        if(drone!=null){drone.stop2();drone=null;}
    }
    static Vec3 littleMoon(float partial){
        if(state==null||state.littleMoon().equals(BlockPos.ZERO))return null;
        double t=(ticks+partial)/20.0;
        return Vec3.atBottomCenterOf(state.littleMoon()).add(0,MoonTimeline.LITTLE_MOON_HEIGHT+Math.sin(t*.9)*.12,0);
    }
    static double littleMoonAlpha(float partial){
        if(state==null||state.littleMoon().equals(BlockPos.ZERO))return 0;
        double since=previousAge+(age-previousAge)*partial-state.littleMoonAt();
        return TideTimeline.smooth(0,60,since)*(1-TideTimeline.smooth(.84,.88,progress(partial)));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        if(mc.level!=lastLevel){clear();lastLevel=mc.level;}
        if(mc.level==null||mc.player==null){clear();return;}
        if(mc.isPaused())return;
        ticks++;previousAge=age;
        if(state!=null&&state.active())age=Math.min(age+1,state.duration());
        double target=0;
        if(state!=null&&state.active()&&mc.level.dimension().equals(Level.OVERWORLD))
            target=MoonTimeline.region(Math.hypot(mc.player.getX()-state.center().getX(),mc.player.getZ()-state.center().getZ()),state.radius());
        intensity+=(target-intensity)*.06f;
        if(intensity<.001){intensity=0;if(drone!=null){drone.stop2();drone=null;}history.clear();return;}
        double p=progress(1);
        if(ticks%10==0){
            BlockPos eye=BlockPos.containing(mc.player.getEyePosition());
            float e=mc.level.canSeeSky(eye)?1:.3f;
            if(mc.player.isUnderWater())e=.14f;
            exposure+=(e-exposure)*.4f;
        }
        sounds(mc,p);
        petals(mc,p);
        if(ticks%10==0||Double.isNaN(fieldX))sampleField(mc);
        if(ticks%20==0)scanLamps(mc);
        if(ticks%30==0)scanOpenings(mc);
        trample(mc);
        recordShadows(mc);
        MoonRenderer.tick();
        blooms.removeIf(b->ticks-b.born()>420);
        if(blooms.size()>90)blooms.subList(0,blooms.size()-90).clear();
        trampled.values().removeIf(t->ticks-t>200);
        float reveal=0;
        for(var e:mc.level.entitiesForRendering())if(e instanceof MoonProcession proc&&proc.walking()){
            double s=proc.renderPath(1);
            double near=Math.min(mc.player.position().distanceTo(proc.member(s,0,0)),mc.player.position().distanceTo(proc.member(s,MoonProcession.LITTER,0)));
            if(near>3.5&&near<14)reveal=1;
        }
        pathReveal+=(reveal-pathReveal)*.05f;
    }

    // ------------------------------------------------------------------ sound
    private static float volume(){return (float)(TideConfig.SOUND_VOLUME.get()*intensity);}
    private static void sounds(Minecraft mc,double p){
        boolean on=TideConfig.SOUND.get();
        if(on&&drone==null){drone=new Drone();mc.getSoundManager().play(drone);}
        if(!on&&drone!=null){drone.stop2();drone=null;}
        if(on&&lastProgress>=0){
            Vec3 moon=mc.player.getEyePosition().add(trueDir(1).scale(120));
            if(crossed(.004,p)||crossed(.05,p))play(mc,Veilborne.MOON_OMEN.get(),volume()*.8f,1,moon);
            for(double cue:new double[]{.085,.16,.23,.4})if(crossed(cue,p))play(mc,Veilborne.MOON_CRACK.get(),volume()*.35f,.8f+random.nextFloat()*.3f,moon);
            if(crossed(.585,p))play(mc,Veilborne.MOON_MOLT.get(),volume(),1,moon);
            if(crossed(.875,p))mc.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(Veilborne.MOON_CLOSE.get(),1,volume()));
            if(state.broken()&&state.brokenAt()!=lastBrokenAt)play(mc,Veilborne.MOON_CRACK.get(),volume(),1.15f,moon);
        }
        if(state!=null)lastBrokenAt=state.broken()?state.brokenAt():0;
        lastProgress=p;
        // 不完整的摇篮曲: phrases of one tune, played out of order with gaps, only inside the flower tide.
        if(on&&TideConfig.LULLABY.get()&&MoonTimeline.bloom(p)>.35&&p<.86&&exposure>.5&&--lullabyAt<=0){
            int phrase=lullabyNext;
            mc.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(Veilborne.LULLABY.get(phrase).get(),.95f+random.nextFloat()*.1f,volume()*.5f));
            lullabyNext=(phrase+1+(random.nextFloat()<.35f?1:0))%6;
            lullabyAt=140+random.nextInt(200);
        }
    }
    private static boolean crossed(double cue,double p){return lastProgress<cue&&p>=cue;}
    private static void play(Minecraft mc,SoundEvent event,float volume,float pitch,Vec3 at){
        mc.getSoundManager().play(new SimpleSoundInstance(event.getLocation(),SoundSource.WEATHER,volume,pitch,RandomSource.create(),false,0,SoundInstance.Attenuation.NONE,at.x,at.y,at.z,false));
    }
    private static final class Drone extends AbstractTickableSoundInstance {
        Drone(){super(Veilborne.MOON_DRONE.get(),SoundSource.WEATHER,RandomSource.create());looping=true;delay=0;relative=true;attenuation=Attenuation.NONE;volume=.01f;}
        @Override public void tick(){
            if(Minecraft.getInstance().level==null||intensity<=.001f||!TideConfig.SOUND.get()){stop();return;}
            double p=progress(1);
            volume=(float)(intensity*(.18+.6*MoonTimeline.veil(p)+.15*MoonTimeline.halo(p))*(.3+exposure*.7)*TideConfig.SOUND_VOLUME.get());
            pitch=.9f+exposure*.1f;
        }
        void stop2(){stop();}
    }

    // ------------------------------------------------------------------ petals
    private static void petals(Minecraft mc,double p){
        double density=MoonTimeline.petals(p)*intensity;
        if(density<.01)return;
        var level=mc.level;var pl=mc.player;
        int count=1+TideConfig.QUALITY.get()*2;
        Vec3 wind=wind(1).scale(.012);
        for(int i=0;i<count;i++){
            if(random.nextFloat()>density)continue;
            double x=pl.getX()+(random.nextDouble()-.5)*44,z=pl.getZ()+(random.nextDouble()-.5)*44;
            int ground=level.getHeight(Heightmap.Types.MOTION_BLOCKING,Mth.floor(x),Mth.floor(z));
            double y=Math.max(ground+5,pl.getY()+7+random.nextDouble()*12);
            if(y-ground>30)continue;
            level.addParticle(Veilborne.PETAL.get(),x,y,z,wind.x,-.02,wind.z);
        }
        // Lamps draw petals into small spirals.
        if(ticks%2==0)for(int i=0;i<Math.min(6,lamps.size());i++){
            BlockPos lamp=lamps.get(i);
            if(random.nextFloat()>density*.9)continue;
            double a=random.nextDouble()*Math.PI*2,r=1.2+random.nextDouble()*1.2;
            level.addParticle(Veilborne.PETAL.get(),lamp.getX()+.5+Math.cos(a)*r,lamp.getY()+2.2+random.nextDouble()*1.6,lamp.getZ()+.5+Math.sin(a)*r,-Math.sin(a)*.03,-.008,Math.cos(a)*.03);
        }
        // A few petals drift in through open doorways, but no further than the threshold.
        if(ticks%16==0&&!openings.isEmpty()){
            Opening o=openings.get(random.nextInt(openings.size()));
            Vec3 out=Vec3.atLowerCornerOf(o.out().getNormal());
            Vec3 at=Vec3.atCenterOf(o.inside()).add(out.scale(1.2)).add(0,.6,0);
            if(level.getBlockState(o.inside().relative(o.out())).isAir())level.addParticle(Veilborne.PETAL.get(),at.x,at.y,at.z,-out.x*.03,-.004,-out.z*.03);
        }
        // Around the little moon they rise instead of fall.
        Vec3 moon=littleMoon(1);
        if(moon!=null&&littleMoonAlpha(1)>.3&&ticks%2==0){
            double a=random.nextDouble()*Math.PI*2,r=.8+random.nextDouble()*2.6;
            level.addParticle(Veilborne.PETAL.get(),moon.x+Math.cos(a)*r,moon.y-MoonTimeline.LITTLE_MOON_HEIGHT+.1,moon.z+Math.sin(a)*r,0,.02,0);
        }
    }
    /** A petal touched open ground: a flower shadow opens where it fell. */
    static void landed(Vec3 at){
        var level=Minecraft.getInstance().level;
        if(level==null||!active()||!level.canSeeSky(BlockPos.containing(at).above()))return;
        blooms.add(new Bloom(at,ticks,random.nextInt(4),random.nextFloat()*6.28f,.32f+random.nextFloat()*.28f));
    }

    // ------------------------------------------------------------------ the flower field
    private static long hash(long x,long z){long h=x*0x9E3779B97F4A7C15L^z*0xC2B2AE3D27D4EB4FL;h^=h>>>31;h*=0x94D049BB133111EBL;return h^h>>>29;}
    private static double unit(long h,int shift){return ((h>>>shift)&0xffff)/65535.0;}
    private static double noise(double x,double z){
        int x0=Mth.floor(x),z0=Mth.floor(z);double tx=x-x0,tz=z-z0;
        tx=tx*tx*(3-2*tx);tz=tz*tz*(3-2*tz);
        double a=unit(hash(x0,z0),7),b=unit(hash(x0+1,z0),7),c=unit(hash(x0,z0+1),7),d=unit(hash(x0+1,z0+1),7);
        return Mth.lerp(tz,Mth.lerp(tx,a,b),Mth.lerp(tx,c,d));
    }
    private static void sampleField(Minecraft mc){
        var pl=mc.player;var level=mc.level;
        if(!Double.isNaN(fieldX)&&Math.hypot(pl.getX()-fieldX,pl.getZ()-fieldZ)<3&&ticks-fieldTick<60)return;
        fieldX=pl.getX();fieldZ=pl.getZ();fieldTick=ticks;
        field.clear();
        double step=new double[]{2.5,1.9,1.45}[TideConfig.QUALITY.get()];
        double radius=26;
        int gx0=Mth.floor((pl.getX()-radius)/step),gx1=Mth.floor((pl.getX()+radius)/step);
        int gz0=Mth.floor((pl.getZ()-radius)/step),gz1=Mth.floor((pl.getZ()+radius)/step);
        var center=state.center();
        for(int gx=gx0;gx<=gx1;gx++)for(int gz=gz0;gz<=gz1;gz++){
            long h=hash(gx*7919L+13,gz*104729L+7);
            double x=(gx+unit(h,3))*step,z=(gz+unit(h,19))*step;
            if(Math.hypot(x-pl.getX(),z-pl.getZ())>radius)continue;
            double clump=noise(x/9.0,z/9.0);
            if(unit(h,35)>.25+.75*clump)continue;
            int y=Surface.top(level,Mth.floor(x),Mth.floor(z));
            if(Math.abs(y-pl.getY())>12)continue;
            BlockPos top=BlockPos.containing(x,y,z);
            if(!level.canSeeSky(top))continue;
            var below=level.getBlockState(top.below());
            if(below.is(Blocks.LAVA)||below.is(Blocks.MAGMA_BLOCK))continue;
            boolean water=level.getFluidState(top.below()).is(FluidTags.WATER);
            boolean path=below.is(Blocks.DIRT_PATH)||below.is(Blocks.GRAVEL)||below.is(Blocks.COARSE_DIRT)||below.is(BlockTags.SLABS);
            double far=Mth.clamp(Math.hypot(x-center.getX(),z-center.getZ())/(state.radius()*.6),0,1);
            float threshold=(float)(.1+.5*noise(x/23.0+40,z/23.0)+.3*far+.1*unit(h,51)-(path?.18:0));
            field.add(new Patch(x,y+(water?-.08:0),z,(int)(unit(h,11)*4)&3,(float)(unit(h,27)*6.283),(float)(.34+.3*unit(h,43)+(path?.08:0)),threshold,h,water,path));
        }
    }
    private static void trample(Minecraft mc){
        var pl=mc.player;
        if(!pl.onGround()||pl.getDeltaMovement().horizontalDistanceSqr()<1e-4)return;
        for(Patch patch:field){
            if(Math.abs(patch.x-pl.getX())<.8&&Math.abs(patch.z-pl.getZ())<.8&&Math.abs(patch.y-pl.getY())<1.2&&!trampled.containsKey(patch.key)){
                trampled.put(patch.key,ticks);
                for(int i=0;i<4;i++)mc.level.addParticle(Veilborne.MOON_MOTE.get(),patch.x+(random.nextDouble()-.5)*.6,patch.y+.1,patch.z+(random.nextDouble()-.5)*.6,0,.02+random.nextDouble()*.02,0);
            }
        }
        blooms.removeIf(b->{
            boolean hit=Math.abs(b.pos().x-pl.getX())<.7&&Math.abs(b.pos().z-pl.getZ())<.7&&Math.abs(b.pos().y-pl.getY())<1.2&&ticks-b.born()>20;
            if(hit)for(int i=0;i<3;i++)mc.level.addParticle(Veilborne.MOON_MOTE.get(),b.pos().x,b.pos().y+.1,b.pos().z,(random.nextDouble()-.5)*.02,.03,(random.nextDouble()-.5)*.02);
            return hit;
        });
    }
    private static void scanLamps(Minecraft mc){
        lamps.clear();
        BlockPos o=mc.player.blockPosition();var level=mc.level;
        List<BlockPos> found=new ArrayList<>();
        for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)for(int y=-4;y<=6;y++){
            BlockPos pos=o.offset(x,y,z);
            var st=level.getBlockState(pos);
            if(!st.isAir()&&st.getLightEmission(level,pos)>=10&&level.canSeeSky(pos.above()))found.add(pos);
        }
        found.sort(Comparator.comparingDouble(b->b.distSqr(o)));
        lamps.addAll(found.subList(0,Math.min(12,found.size())));
    }
    /** Doorways and windows from the inside: where a little of the flower light spills in. */
    /** Nothing above this block: judged by the synced heightmap rather than sky light. */
    private static boolean roofless(net.minecraft.world.level.Level level,BlockPos p){return level.getHeight(Heightmap.Types.MOTION_BLOCKING,p.getX(),p.getZ())<=p.getY();}
    private static void scanOpenings(Minecraft mc){
        openings.clear();
        var level=mc.level;BlockPos o=BlockPos.containing(mc.player.getEyePosition());
        if(roofless(level,o))return;
        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)for(int y=-2;y<=1;y++){
            BlockPos pos=o.offset(x,y,z);
            if(!level.getBlockState(pos).isAir()||roofless(level,pos)||level.getBlockState(pos.below()).isAir())continue;
            // A doorway beside this floor block, or a window in the wall above it; either way the light lands here.
            faces:
            for(Direction face:Direction.Plane.HORIZONTAL)for(int up=0;up<=2;up++){
                BlockPos n=pos.relative(face).above(up);var st=level.getBlockState(n);
                if(up>0&&!level.getBlockState(pos.above(up)).isAir())break;
                boolean window=st.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS)||st.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_PANES)||up==0&&st.is(BlockTags.DOORS);
                boolean open=up==0&&st.isAir()&&roofless(level,n);
                if(open||window&&roofless(level,n.relative(face))){openings.add(new Opening(pos,face));break faces;}
            }
            if(openings.size()>=28)return;
        }
    }
    // ------------------------------------------------------------------ moon shadows
    /** Poses kept per player: enough for the longest configurable lag plus the two ticks it interpolates between. */
    static final int HISTORY=13;
    private static void recordShadows(Minecraft mc){
        if(!TideConfig.MOON_SHADOWS.get())return;
        Set<Integer> seen=new HashSet<>();
        for(Player pl:mc.level.players()){
            if(pl.distanceToSqr(mc.player)>48*48||pl.isSpectator())continue;
            seen.add(pl.getId());
            var q=history.computeIfAbsent(pl.getId(),k->new ArrayDeque<>());
            q.addLast(new Frame(pl.getX(),pl.getY(),pl.getZ(),pl.yBodyRot,pl.yHeadRot,pl.getXRot(),pl.walkAnimation.position(),Math.min(1,pl.walkAnimation.speed()),pl.isCrouching()));
            while(q.size()>HISTORY)q.removeFirst();
        }
        history.keySet().retainAll(seen);
    }
    @SubscribeEvent public static void fog(ViewportEvent.ComputeFogColor e){
        if(state==null||intensity<.001)return;
        double p=progress((float)e.getPartialTick()),day=dayness((float)e.getPartialTick());
        double s=intensity*MoonTimeline.veil(p)*.72;
        if(s<=0)return;
        double r=Mth.lerp(day,.085,.62),g=Mth.lerp(day,.066,.58),b=Mth.lerp(day,.15,.74);
        e.setRed((float)(e.getRed()*(1-s)+r*s));e.setGreen((float)(e.getGreen()*(1-s)+g*s));e.setBlue((float)(e.getBlue()*(1-s)+b*s));
    }
    private MoonClient(){}
}
