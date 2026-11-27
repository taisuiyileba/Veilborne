package com.suiyi.veilborne.client;

import com.suiyi.veilborne.MirrorTimeline;
import net.minecraft.core.BlockPos;

/** Public entry points for the mixins, which live outside this package. */
public final class MirrorHooks {
    public static void changed(BlockPos pos){MirrorWorld.changed(pos);}
    /** 0..1: how much the mirror sky covers the ordinary sky right now (clouds, sun, moon and stars step aside). */
    public static float veil(float partial){
        if(!MirrorClient.active())return 0;
        double p=MirrorClient.progress(partial);
        return (float)(MirrorTimeline.mirror(p)*(1-MirrorTimeline.shatter(p))*MirrorClient.strength());
    }
    public static boolean active(){return MirrorClient.active();}
    public static double progress(){return MirrorClient.progress(1);}
    public static double strength(){return MirrorClient.strength();}
    public static double dayness(){return MirrorClient.dayness(1);}
    private MirrorHooks(){}
}
