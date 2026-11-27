package com.suiyi.veilborne.client;

import com.suiyi.veilborne.HushTimeline;
import com.suiyi.veilborne.TideConfig;
import com.suiyi.veilborne.TideTimeline;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/** Public entry points for the mixins, which live outside this package. */
public final class HushHooks {
    /**
     * How loud a sound may be under the silent daylight, 0..1. The weather's own sounds, warnings, hostile mobs, hurt and
     * interface sounds keep their voice; music goes first, then the wind and the caves, the animals, the blocks (muffled, not
     * gone) and last your own footsteps. Inside the island round a found bell everything sounds again. A still candle keeps a
     * little of this quiet near it at any time.
     */
    public static float gain(SoundInstance sound){
        if(sound==null)return 1;
        ResourceLocation loc=sound.getLocation();
        if(loc==null||"veilborne".equals(loc.getNamespace()))return 1;
        SoundSource src=sound.getSource();
        if(src==SoundSource.MASTER||src==SoundSource.VOICE||src==SoundSource.HOSTILE)return 1;
        String path=loc.getPath();
        boolean step=path.endsWith(".step")||path.endsWith(".fall")||path.endsWith(".swim");
        double g=1;
        if(HushClient.active()&&TideConfig.HUSH_SILENCE.get()){
            double p=HushClient.progress(1);
            double layer=switch(src){
                case MUSIC,RECORDS -> HushTimeline.music(p);
                case AMBIENT,WEATHER -> HushTimeline.ambient(p);
                case NEUTRAL -> step?HushTimeline.steps(p):HushTimeline.animals(p);
                case BLOCKS -> step?HushTimeline.steps(p):HushTimeline.blocks(p);
                case PLAYERS -> step?HushTimeline.steps(p):1;
                default -> 1;
            };
            double w=HushClient.strength()*(1-HushClient.islandHere());
            g=1-(1-layer)*w;
        }
        g*=HushClient.localQuiet(src,step);
        return (float)Math.max(g,.0006);
    }
    /** Clouds stop in the sky and go on later from where they stopped: the ticks and partial tick they are drawn at. */
    public static double cloudTime(int ticks,float partial){return ticks-HushClient.cloudLost+partial*HushClient.cloudSpeed;}
    public static boolean cloudsHeld(){return HushClient.cloudLost!=0||HushClient.cloudSpeed<1;}
    /** 0..1 how much the clouds thin while the black sun is up. */
    public static float cloudThin(float partial){
        if(!HushClient.active())return 0;
        double p=HushClient.progress(partial);
        return (float)(HushClient.strength()*.45*TideTimeline.smooth(HushTimeline.RISE,HushTimeline.RISE+.8/12,p)*(1-TideTimeline.smooth(HushTimeline.SET-.3/12,HushTimeline.SET,p)));
    }
    /** 0..1: how far the drawn pale sky covers the ordinary one (the real sun, moon and stars step aside). */
    public static float veil(float partial){
        if(!HushClient.active())return 0;
        return (float)(HushTimeline.pale(HushClient.progress(partial))*HushClient.strength());
    }
    public static boolean active(){return HushClient.active();}
    public static double progress(){return HushClient.progress(1);}
    public static double strength(){return HushClient.strength();}
    public static double dayness(){return HushClient.dayness(1);}
    /** The eclipse as the eye sees it, 0..1 of the pale disc covered. */
    public static double covered(){return HushClient.covered;}
    private HushHooks(){}
}
