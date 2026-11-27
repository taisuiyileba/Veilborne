package com.suiyi.veilborne.client;

import com.suiyi.veilborne.*;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Small client-side life around the bloom's visitors, and the silver dial's needle. */
final class MoonAmbience {
    private static final RandomSource random=RandomSource.create();
    /** The guide's bell, petals trailing from the rear lantern, and the procession dissolving into petals and gathering again. */
    static void procession(MoonProcession proc){
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.isPaused())return;
        double s=proc.renderPath(1);
        var st=proc.state();
        boolean standing=proc.visibilityServer();
        if(standing&&proc.tickCount%64==0&&TideConfig.SOUND.get()){
            Vec3 guide=proc.member(s,0,0);
            mc.level.playLocalSound(guide.x,guide.y+2,guide.z,Veilborne.MOON_BELL.get(),SoundSource.AMBIENT,(float)(.55*TideConfig.SOUND_VOLUME.get()),.95f+random.nextFloat()*.1f,false);
        }
        if(standing&&proc.tickCount%3==0){
            Vec3 rear=proc.member(s,MoonProcession.MEMBERS[5][0],0);
            mc.level.addParticle(Veilborne.PETAL.get(),rear.x+(random.nextDouble()-.5)*.6,rear.y+1.3,rear.z+(random.nextDouble()-.5)*.6,0,-.01,0);
            Vec3 shell=proc.member(s,MoonProcession.LITTER,0);
            if(random.nextInt(3)==0)mc.level.addParticle(Veilborne.MOON_MOTE.get(),shell.x+(random.nextDouble()-.5),shell.y+2,shell.z+(random.nextDouble()-.5),0,.01,0);
        }
        int clock=proc.stateClock();
        boolean burst=st==MoonProcession.State.SCATTER&&clock<14||st==MoonProcession.State.DEPART&&clock<30;
        boolean gather=st==MoonProcession.State.GATHER&&clock<24;
        if(burst||gather){
            for(double[] m:MoonProcession.MEMBERS){
                Vec3 at=proc.member(s,m[0],m[1]);
                for(int i=0;i<3;i++){
                    double a=random.nextDouble()*Math.PI*2,h=random.nextDouble()*2.2,r=gather?1.8+random.nextDouble():.2;
                    double x=at.x+Math.cos(a)*r,y=at.y+h,z=at.z+Math.sin(a)*r;
                    double v=gather?-.05:.06+random.nextDouble()*.05;
                    mc.level.addParticle(Veilborne.PETAL.get(),x,y,z,Math.cos(a)*v,gather?.0:.03,Math.sin(a)*v);
                }
            }
        }
    }
    static void decoy(MoonDecoy decoy){
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.isPaused()||random.nextInt(3)!=0)return;
        mc.level.addParticle(Veilborne.MOON_MOTE.get(),decoy.getX()+(random.nextDouble()-.5)*.8,decoy.getY()+random.nextDouble()*1.9,decoy.getZ()+(random.nextDouble()-.5)*.8,0,.012,0);
    }
    /** 0..1 around the dial face, 0 being straight ahead: the true moon while the bloom is on, a slow drift otherwise. */
    static float needle(LivingEntity holder){
        double time=MoonClient.ticks/20.0;
        if(holder==null)return (float)((time*.03)%1);
        if(!MoonClient.active()){
            double wander=.08*Math.sin(time*.7)+.03*Math.sin(time*2.3);
            return (float)Mth.positiveModulo(wander,1);
        }
        Vec3 d=MoonClient.trueDir(1);
        double target=Math.toDegrees(Math.atan2(-d.x,d.z));
        double rel=Mth.wrapDegrees(target-holder.getYRot())/360.0;
        // A faint tremble while three moons hang: the needle knows, but it hesitates.
        if(MoonTimeline.threeActive(MoonClient.progress(1))&&!MoonClient.state.broken())rel+=.012*Math.sin(time*9);
        return (float)Mth.positiveModulo(rel,1);
    }
    private MoonAmbience(){}
}
