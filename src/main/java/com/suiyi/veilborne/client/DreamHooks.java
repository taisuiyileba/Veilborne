package com.suiyi.veilborne.client;

import com.suiyi.veilborne.DreamTimeline;
import com.suiyi.veilborne.TideConfig;
import com.suiyi.veilborne.TideTimeline;
import net.minecraft.client.Minecraft;

/** Public entry points for the mixins, which live outside this package. */
public final class DreamHooks {
    /**
     * 0..1: how far the real sun, moon and stars are hidden. The canopy covers them once its front has spread past the sun's
     * height in the sky; while the leaves fall they stay hidden (the sky shader draws them in the gaps, without the square
     * halo vanilla's sun would show through a half-open canopy), and they come back as the bare veins fade.
     */
    public static float veil(float partial){
        if(!DreamClient.active()||!TideConfig.SKY.get())return 0;
        double p=DreamClient.progress(partial);
        var level=Minecraft.getInstance().level;
        double sunY=level==null?1:Math.abs(Math.cos(level.getTimeOfDay(partial)*Math.PI*2));
        double zen=Math.acos(Math.min(1,sunY))/(Math.PI/2);
        double front=DreamTimeline.canopy(p)*1.14-.08;
        double covered=TideTimeline.smooth(zen-.02,zen+.08,front);
        return (float)(covered*DreamClient.strength()*DreamTimeline.sky(p)*(1-DreamTimeline.veinsFade(p)));
    }
    /** 0..1 how far the ordinary clouds have given way to the canopy. */
    public static float cloudVeil(float partial){
        if(!DreamClient.active()||!TideConfig.SKY.get())return 0;
        return (float)(DreamTimeline.cloudVeil(DreamClient.progress(partial))*DreamClient.strength());
    }
    /** 0..1 how green the clouds' undersides are before the forest comes out of them. */
    public static float cloudGreen(float partial){
        if(!DreamClient.active()||!TideConfig.SKY.get())return 0;
        return (float)(DreamTimeline.cloudGreen(DreamClient.progress(partial))*DreamClient.strength());
    }
    public static boolean active(){return DreamClient.active();}
    public static double progress(){return DreamClient.progress(1);}
    public static double strength(){return DreamClient.strength();}
    public static double dayness(){return DreamClient.dayness(1);}
    private DreamHooks(){}
}
