package com.suiyi.veilborne;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TideConfig {
    public static final ModConfigSpec SERVER, CLIENT;
    public static final ModConfigSpec.BooleanValue NATURAL, VORTEX, AUTO_JOURNAL;
    public static final ModConfigSpec.IntValue MIN_DAYS, MAX_DAYS, DURATION, RADIUS;
    public static final ModConfigSpec.BooleanValue MOON_NATURAL, MOON_VISITORS, MOON_LANTERNS;
    public static final ModConfigSpec.IntValue MOON_DURATION;
    public static final ModConfigSpec.BooleanValue MIRROR_NATURAL, MIRROR_VISITORS, MIRROR_BEASTS, MIRROR_GALLERY;
    public static final ModConfigSpec.IntValue MIRROR_DURATION;
    public static final ModConfigSpec.BooleanValue MIRROR_REFLECTIONS, MIRROR_AFTERIMAGES, MIRROR_ECHOES;
    public static final ModConfigSpec.BooleanValue THREAD_NATURAL, THREAD_STRIKES, THREAD_VISITORS, THREAD_TUG;
    public static final ModConfigSpec.IntValue THREAD_DURATION;
    public static final ModConfigSpec.BooleanValue THREAD_STRINGS, THREAD_MOMENT, THREAD_SEAMS;
    public static final ModConfigSpec.BooleanValue HUSH_NATURAL, HUSH_VISITORS;
    public static final ModConfigSpec.IntValue HUSH_DURATION;
    public static final ModConfigSpec.BooleanValue HUSH_SILENCE, HUSH_FADE, HUSH_MOTES, HUSH_BLUR;
    public static final ModConfigSpec.BooleanValue DREAM_NATURAL, DREAM_VISITORS;
    public static final ModConfigSpec.IntValue DREAM_DURATION;
    public static final ModConfigSpec.BooleanValue DREAM_DAPPLE, DREAM_SCHOOLS, DREAM_PATTERNS;
    public static final ModConfigSpec.IntValue QUALITY, MOON_SHADOW_LAG;
    public static final ModConfigSpec.BooleanValue SOUND, SKY, GROUND, LIGHT, MOON_SHADOWS, LULLABY;
    public static final ModConfigSpec.DoubleValue SOUND_VOLUME, BRIGHTNESS;
    static {
        var s=new ModConfigSpec.Builder();
        NATURAL=s.comment("Allow naturally scheduled inverted seas in the Overworld.").define("naturalWeather",true);
        MIN_DAYS=s.defineInRange("minimumQuietDays",3,1,100);
        MAX_DAYS=s.defineInRange("maximumQuietDays",6,1,100);
        DURATION=s.defineInRange("durationSeconds",840,120,3600);
        RADIUS=s.defineInRange("regionRadius",320,96,1024);
        VORTEX=s.comment("Enable the telegraphed, non-destructive flotation encounter.").define("floatingVortices",true);
        AUTO_JOURNAL=s.define("giveJournalOnFirstWitness",true);
        MOON_NATURAL=s.comment("Allow naturally scheduled moon-husk blooms. They begin only after dusk.").define("naturalMoonBloom",true);
        MOON_DURATION=s.defineInRange("moonBloomDurationSeconds",720,120,3600);
        MOON_VISITORS=s.comment("The moon bearers' procession and the fallen little moon.").define("moonVisitors",true);
        MOON_LANTERNS=s.comment("The last moon bearer leaves one silver flower lantern by the road. Players can break it.").define("moonLanterns",true);
        MIRROR_NATURAL=s.comment("Allow naturally scheduled mirror-sky pilgrimages, by day or night.").define("naturalMirrorSky",true);
        MIRROR_DURATION=s.defineInRange("mirrorSkyDurationSeconds",780,120,3600);
        MIRROR_VISITORS=s.comment("The late traveller and the three misaligned mirrors.").define("mirrorVisitors",true);
        MIRROR_BEASTS=s.comment("Glass beasts on low cracked shards. They only wake if you walk up to their shard after its warning.").define("mirrorBeasts",true);
        MIRROR_GALLERY=s.comment("The short mirror gallery behind the extra door (a small void dimension; players always return where they entered).").define("mirrorGallery",true);
        THREAD_NATURAL=s.comment("Allow naturally scheduled scarlet-thread sewings of the sky, by day or night.").define("naturalScarletSeam",true);
        THREAD_DURATION=s.defineInRange("scarletSeamDurationSeconds",780,120,3600);
        THREAD_STRIKES=s.comment("The needle strikes the land inside warned rings (4-5 damage and a throw; buildings are never harmed). Off: its three strikes stay in the sky, and the land stitch's feet harm nobody.").define("threadStrikes",true);
        THREAD_VISITORS=s.comment("The needle walker and the unsewn pocket.").define("threadVisitors",true);
        THREAD_TUG=s.comment("When the thread is pulled tight, everyone out under it is tugged gently toward the land stitch.").define("threadTug",true);
        HUSH_NATURAL=s.comment("Allow naturally scheduled silent daylights, by day or night.").define("naturalSilentDaylight",true);
        HUSH_DURATION=s.defineInRange("silentDaylightDurationSeconds",720,120,3600);
        HUSH_VISITORS=s.comment("The silent procession, the empty coat that knocks at a door, and the one bell that still rings.").define("hushVisitors",true);
        DREAM_NATURAL=s.comment("Allow naturally scheduled verdant dreams, by day or night.").define("naturalVerdantDream",true);
        DREAM_DURATION=s.defineInRange("verdantDreamDurationSeconds",780,120,3600);
        DREAM_VISITORS=s.comment("The petal path to the dream tree, the wandering seed and its vine enclosure, and the lost school at a window.").define("dreamVisitors",true);
        SERVER=s.build();
        var c=new ModConfigSpec.Builder();
        QUALITY=c.comment("0=light, 1=balanced, 2=cinematic. Gameplay is identical.").defineInRange("quality",1,0,2);
        SOUND=c.define("weatherSoundscape",true);
        SOUND_VOLUME=c.defineInRange("soundscapeVolume",.75,0,1);
        SKY=c.define("celestialSea",true);
        GROUND=c.define("surfaceCaustics",true);
        LIGHT=c.comment("Tint skylight during the tide while preserving lamps and torches.").define("tideLight",true);
        BRIGHTNESS=c.defineInRange("effectBrightness",.85,.15,1);
        MOON_SHADOWS=c.comment("Long moon-shadows cast away from the true moon.").define("moonShadows",true);
        MOON_SHADOW_LAG=c.comment("Ticks the moon-shadow's movements trail its owner (20 ticks = 1 second). The shadow always stays at your feet; only its limbs and turning lag. 0 keeps it in step.").defineInRange("moonShadowLag",4,0,10);
        LULLABY=c.comment("Fragments of a lullaby inside the flower tide.").define("moonLullaby",true);
        MIRROR_REFLECTIONS=c.comment("Show the real land around you upside down in the mirror sky. Off: a painted distant reflection only.").define("mirrorReflections",true);
        MIRROR_AFTERIMAGES=c.comment("Faint afterimages behind a moving player under the mirror sky.").define("mirrorAfterimages",true);
        MIRROR_ECHOES=c.comment("Some footsteps come back a beat late under the mirror sky.").define("mirrorEchoes",true);
        THREAD_STRINGS=c.comment("Thin threads rising from every living thing to the seam while the world is being pulled tight.").define("threadStrings",true);
        THREAD_MOMENT=c.comment("The last few seconds laid over the present like layers of film when the thread is pulled.").define("threadMoment",true);
        THREAD_SEAMS=c.comment("Gold seams that open and are stitched shut on nearby rock faces and tree trunks.").define("threadSeams",true);
        HUSH_SILENCE=c.comment("Let the silent daylight silence the world (music, wind, animals, blocks and footsteps). Warnings, hostile mobs, hurt sounds and subtitles always stay.").define("hushSilence",true);
        HUSH_FADE=c.comment("Let the silent daylight drain the colour from the world; lamps keep a little sepia warmth.").define("hushFade",true);
        HUSH_MOTES=c.comment("Dust, leaves and drops hanging still in the air around you.").define("hushMotes",true);
        HUSH_BLUR=c.comment("Soften the far distance while the colour is gone.").define("hushBlur",true);
        DREAM_DAPPLE=c.comment("Leaf-light moving on the ground and on outside walls under the dream forest.").define("dreamDapple",true);
        DREAM_SCHOOLS=c.comment("Schools of leaf-fish swimming through the air around you.").define("dreamLeafFish",true);
        DREAM_PATTERNS=c.comment("Flower patterns that light up on the land where pollen settles and where the deer steps.").define("dreamPatterns",true);
        CLIENT=c.build();
    }
    /** Lets only one weather begin naturally (0 sea, 1 moon, 2 mirror, 3 thread, 4 hush, 5 dream); the lifecycle checks use it. */
    public static void naturalOnly(int weather){
        ModConfigSpec.BooleanValue[] all={NATURAL,MOON_NATURAL,MIRROR_NATURAL,THREAD_NATURAL,HUSH_NATURAL,DREAM_NATURAL};
        for(int i=0;i<all.length;i++)all[i].set(i==weather);
    }
    private TideConfig() {}
}
