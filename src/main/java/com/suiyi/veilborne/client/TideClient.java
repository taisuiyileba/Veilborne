package com.suiyi.veilborne.client;

import com.suiyi.veilborne.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;

public final class TideClient {
    public static TidePacket state;
    public static float age,previousAge,exposure=1,intensity;
    public static int ticks;
    public static int journalRecords;
    private static Level lastLevel;
    private static SeaLoop loop;
    private static double lastProgress=-1;
    private static Vec3 lastFoot=Vec3.ZERO;
    public record Ripple(Vec3 pos,int born,boolean water){}
    public record SurfacePatch(BlockPos pos,Direction face){}
    public static final List<Ripple> ripples=new ArrayList<>();
    public static final List<BlockPos> surfaces=new ArrayList<>();
    public static final List<SurfacePatch> walls=new ArrayList<>();
    public static void receive(TidePacket p){
        if(state==null||state.seed()!=p.seed()){lastProgress=-1;ripples.clear();}
        state=p;journalRecords=p.records();age=previousAge=p.age();
    }
    public static double progress(float partial){return state==null?0:TideTimeline.progress(previousAge+(age-previousAge)*partial,state.duration());}
    public static double strength(){return intensity;}
    public static void clear(){
        state=null;intensity=0;lastProgress=-1;ripples.clear();surfaces.clear();walls.clear();
        if(loop!=null){loop.finish();loop=null;}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        if(mc.level!=lastLevel){clear();if(mc.level==null)journalRecords=0;lastLevel=mc.level;}
        if(mc.level==null||mc.player==null){clear();return;}
        if(mc.isPaused())return;
        ticks++;previousAge=age;
        if(state!=null&&state.active())age=Math.min(age+1,state.duration());
        double target=0;
        if(state!=null&&state.active()&&mc.level.dimension().equals(Level.OVERWORLD)){
            double dist=Math.hypot(mc.player.getX()-state.center().getX(),mc.player.getZ()-state.center().getZ());
            target=TideTimeline.region(dist,state.radius());
        }
        intensity+=(target-intensity)*.06f;
        if(intensity<.001){intensity=0;if(loop!=null){loop.finish();loop=null;}return;}
        double p=progress(1),sea=TideTimeline.sea(p);
        if(ticks%10==0){
            BlockPos eye=BlockPos.containing(mc.player.getEyePosition());
            float e=mc.level.canSeeSky(eye)?1:.3f;
            if(mc.player.isUnderWater())e=.14f;
            exposure+=(e-exposure)*.4f;
        }
        if(TideConfig.SOUND.get()&&loop==null){loop=new SeaLoop();mc.getSoundManager().play(loop);}
        if(!TideConfig.SOUND.get()&&loop!=null){loop.finish();loop=null;}
        if(lastProgress>=0&&TideConfig.SOUND.get()){
            for(double cue:new double[]{.035,.215,.57,.65,.79})if(lastProgress<cue&&p>=cue)
                mc.getSoundManager().play(new SimpleSoundInstance(Veilborne.WHALE.get().getLocation(),SoundSource.WEATHER,(float)(TideConfig.SOUND_VOLUME.get()*intensity*(.3+exposure*.7)),.9f+exposure*.1f,RandomSource.create(),false,0,SoundInstance.Attenuation.NONE,state.center().getX(),state.center().getY()+95,state.center().getZ()+128,false));
            if(lastProgress<.86&&p>=.86)mc.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(Veilborne.EBB.get(),1,(float)(TideConfig.SOUND_VOLUME.get()*intensity)));
        }
        lastProgress=p;
        int count=(3+TideConfig.QUALITY.get()*3);
        // The first sign: drops leave the water and rise; later, star motes lift from every open surface.
        double omen=.35+.65*TideTimeline.smooth(0,.08,p);
        for(int i=0;i<count;i++){
            double x=mc.player.getX()+(mc.level.random.nextDouble()-.5)*32,z=mc.player.getZ()+(mc.level.random.nextDouble()-.5)*32;
            BlockPos surface=Surface.topPos(mc.level,BlockPos.containing(x,0,z));
            if(Math.abs(surface.getY()-mc.player.getY())>=14||!mc.level.canSeeSky(surface))continue;
            boolean water=mc.level.getFluidState(surface.below()).is(net.minecraft.tags.FluidTags.WATER);
            if(water&&mc.level.random.nextFloat()<intensity*omen*(.5+sea*.5))
                mc.level.addParticle(Veilborne.DROPLET.get(),x,surface.getY()-.05,z,0,.03+mc.level.random.nextDouble()*.03,0);
            else if(!water&&mc.level.random.nextFloat()<intensity*(.12+sea*.88))
                mc.level.addParticle(Veilborne.MOTE.get(),x,surface.getY()+.08+mc.level.random.nextDouble()*3,z,.007,.014+mc.level.random.nextDouble()*.018,.003);
        }
        if(ticks%8==0&&sea>.1){
            surfaces.clear();int step=TideConfig.QUALITY.get()==0?6:4;
            for(int x=-20;x<=20;x+=step)for(int z=-20;z<=20;z+=step){
                BlockPos pos=Surface.topPos(mc.level,mc.player.blockPosition().offset(x,0,z));
                if(Math.abs(pos.getY()-mc.player.getY())<10&&mc.level.canSeeSky(pos))surfaces.add(pos);
            }
        }
        if(ticks%30==0&&sea>.1){
            walls.clear();BlockPos origin=mc.player.blockPosition();
            for(int x=-10;x<=10;x+=2)for(int z=-10;z<=10;z+=2)for(int dy=0;dy<5;dy+=2){
                BlockPos pos=origin.offset(x,dy,z);
                if(!mc.level.getBlockState(pos).isSolidRender(mc.level,pos))continue;
                for(Direction face:Direction.Plane.HORIZONTAL){
                    BlockPos outside=pos.relative(face);
                    if(mc.level.getBlockState(outside).isAir()&&mc.level.canSeeSky(outside.above()))walls.add(new SurfacePatch(pos,face));
                }
            }
        }
        if(mc.player.onGround()&&mc.player.position().distanceToSqr(lastFoot)>.95&&ticks%4==0){
            lastFoot=mc.player.position();
            if(mc.level.canSeeSky(mc.player.blockPosition()))ripples.add(new Ripple(lastFoot.add(0,.024,0),ticks,false));
        }
        if(ticks%10==0&&!surfaces.isEmpty()){
            BlockPos pos=surfaces.get(mc.level.random.nextInt(surfaces.size()));
            if(!mc.level.getFluidState(pos.below()).isEmpty())ripples.add(new Ripple(pos.getBottomCenter().add(0,.015,0),ticks,true));
        }
        ripples.removeIf(r->ticks-r.born()>65);
        if(ripples.size()>40)ripples.subList(0,ripples.size()-40).clear();
    }
    /** Sea water still drips from the shoreless skiff, and its shadow ripples on the ground below. */
    public static void skiffAmbience(TideSkiff skiff){
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.isPaused())return;
        float alpha=skiff.visibility(0);
        var random=mc.level.random;
        float yaw=skiff.getYRot()*net.minecraft.util.Mth.DEG_TO_RAD;
        double fx=-Math.sin(yaw),fz=Math.cos(yaw);
        if(random.nextFloat()<.45f*alpha){
            double along=(random.nextDouble()-.35)*2.2,across=(random.nextDouble()-.5)*.9;
            mc.level.addParticle(Veilborne.MOTE.get(),skiff.getX()+fx*along+fz*across,skiff.getY()-.05,skiff.getZ()+fz*along-fx*across,0,-.035-random.nextDouble()*.03,0);
        }
        if(skiff.tickCount%14==0&&alpha>.5f){
            BlockPos ground=Surface.topPos(mc.level,skiff.blockPosition());
            if(skiff.getY()-ground.getY()<5)ripples.add(new Ripple(new Vec3(skiff.getX()+fx*random.nextDouble(),ground.getY()+.03,skiff.getZ()+fz*random.nextDouble()),ticks,true));
        }
    }
    @SubscribeEvent public static void fog(ViewportEvent.ComputeFogColor e){
        double s=intensity*TideTimeline.sea(progress(1))*.68;
        if(s<=0)return;
        e.setRed((float)(e.getRed()*(1-s)+.026*s));
        e.setGreen((float)(e.getGreen()*(1-s)+.075*s));
        e.setBlue((float)(e.getBlue()*(1-s)+.17*s));
    }
    private static final class SeaLoop extends AbstractTickableSoundInstance {
        SeaLoop(){super(Veilborne.SEA.get(),SoundSource.WEATHER,RandomSource.create());looping=true;delay=0;relative=true;attenuation=Attenuation.NONE;volume=.01f;}
        @Override public void tick(){
            if(Minecraft.getInstance().level==null||intensity<=.001f||!TideConfig.SOUND.get()){stop();return;}
            volume=(float)(intensity*(.15+.65*TideTimeline.sea(progress(1)))*(.25+exposure*.75)*TideConfig.SOUND_VOLUME.get());
            pitch=.8f+exposure*.2f;
        }
        void finish(){stop();}
    }
    private TideClient(){}
}
