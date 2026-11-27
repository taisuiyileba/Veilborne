package com.suiyi.veilborne.client;

import com.suiyi.veilborne.ThreadTimeline;

/** Public entry points for the mixins, which live outside this package. */
public final class ThreadHooks {
    /** 0..1: how much of the ordinary sky the cloth covers (clouds, sun, moon and stars step aside). */
    public static float veil(float partial){
        if(!ThreadClient.active())return 0;
        return (float)(ThreadTimeline.veil(ThreadClient.progress(partial))*ThreadClient.strength());
    }
    /** 0..1: how far the clouds and the ordinary sun, moon and stars keep away; the star band keeps them off until it goes. */
    public static float clear(float partial){
        if(!ThreadClient.active())return 0;
        double p=ThreadClient.progress(partial);
        return (float)(Math.max(ThreadTimeline.veil(p),ThreadTimeline.starband(p)*(1-com.suiyi.veilborne.TideTimeline.smooth(.97,1,p)))*ThreadClient.strength());
    }
    public static boolean active(){return ThreadClient.active();}
    public static double progress(){return ThreadClient.progress(1);}
    public static double strength(){return ThreadClient.strength();}
    public static double dayness(){return ThreadClient.dayness(1);}
    private ThreadHooks(){}
}
