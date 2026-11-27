package com.suiyi.veilborne;

import net.minecraft.world.phys.Vec3;

/**
 * 镜天巡礼 choreography shared by server and client. The sky silvers from the zenith into an uneven mirror that
 * holds the land upside down, not quite as it is; a colossal pilgrim walks through that inverted land and stops
 * over one watcher to look down at them; then the mirror cracks like very slow lightning and falls away.
 * Everything follows from progress and the event seed, so every player sees the same facets crack in the same order.
 */
public final class MirrorTimeline {
    public static final int DEFAULT_DURATION = 13 * 60 * 20;
    public enum Phase { OMEN, SILVER, REFLECT, PILGRIM, CRACK, CLEAR, QUIET }
    public static final double[] BOUNDS = {0, 1/13.0, 3/13.0, 7/13.0, 9.5/13.0, 11.5/13.0, 1};
    private static double smooth(double a, double b, double x) { return TideTimeline.smooth(a, b, x); }
    public static double progress(double age, int duration) { return TideTimeline.progress(age, duration); }
    public static double region(double distance, double radius) { return TideTimeline.region(distance, radius); }
    public static Phase phase(double p) {
        for (int i = 0; i < 6; i++) if (p < BOUNDS[i + 1]) return Phase.values()[i];
        return Phase.QUIET;
    }
    /** 镜兆: still water shows the clear sky of after the weather, footsteps return late. It lingers to the end. */
    public static double omen(double p) { return smooth(0, .035, p) * (1 - smooth(.95, 1, p)); }
    /** How far the silver has crept down from the zenith: 0 none, 1 down to the horizon. */
    public static double silver(double p) { return smooth(.055, .22, p); }
    /** The mirror as a whole. */
    public static double mirror(double p) { return smooth(.05, .1, p) * (1 - smooth(.93, .995, p)); }
    /** The inverted land inside the mirror, which surfaces a little after the silver. */
    public static double reflection(double p) { return smooth(.11, .27, p) * (1 - smooth(.9, .975, p)); }
    /** Upright shards turning slowly in mid-air. */
    public static double panes(double p) { return smooth(.13, .25, p) * (1 - smooth(.86, .93, p)); }
    /** Share of facet seams cracked open: a few hairlines while the land shows, nearly all of them in the cracking. */
    public static double crack(double p) { return .16 * smooth(.27, .5, p) + .84 * smooth(.73, .87, p); }
    /** Facets coming out of the dome and falling as light: the real sky returns through the gaps. */
    public static double shatter(double p) { return smooth(.885, .985, p); }
    /** Late echoes of footsteps and faint afterimages. */
    public static double echoes(double p) { return smooth(0, .05, p) * (1 - smooth(.92, 1, p)); }
    /** The extra door, visible only in the mirror. */
    public static double door(double p) { return smooth(.29, .33, p) * (1 - smooth(.86, .9, p)); }
    public static boolean doorOpen(double p) { return p >= .3 && p < .875; }
    /** Anyone still in the gallery is walked back out before the mirror breaks. */
    public static boolean galleryOpen(double p) { return p < .9; }

    // ------------------------------------------------------------------ the pilgrim
    public static final double PILGRIM_START = 7 / 13.0, PILGRIM_END = 9.5 / 13.0;
    /**
     * Path length in blocks; it stops halfway, {@link #BESIDE} blocks to one side of the watcher, and turns to them.
     * Seen from there it hangs from the sky ground to a little above the horizon: head, hat and raised mirror low,
     * at the height a face would be if a giant bent to look at you.
     */
    public static final double PATH = 440, STRIDE = 34, HEIGHT = 100, BESIDE = 72;
    /** Share of its walk spent standing over the watcher, looking down. */
    public static final double STOP = .16;
    public static double pilgrim(double p) { return smooth(PILGRIM_START + .002, PILGRIM_START + .02, p) * (1 - smooth(PILGRIM_END - .02, PILGRIM_END - .002, p)); }
    /** 0..1 through its walk. */
    public static double walk(double p) { return TideTimeline.clamp((p - PILGRIM_START) / (PILGRIM_END - PILGRIM_START)); }
    /**
     * Distance along the path. It slows into its stop, stands, then walks on. Between the halves it covers
     * the same ground at the same pace, so the steps line up with the stride either side of the stop.
     */
    public static double along(double w) {
        double a = (1 - STOP) / 2, b = a + STOP;
        if (w < a) { double t = w / a; return PATH / 2 * (t < .85 ? t / .925 : (.85 + (t - .85) - (t - .85) * (t - .85) / .3) / .925); }
        if (w < b) return PATH / 2;
        double t = (w - b) / a;
        return PATH / 2 + PATH / 2 * (t < .15 ? (t * t / .3) / .925 : (t - .075) / .925);
    }
    /** 0..1 while it stands by the watcher: it looks down at them, raises its mirror and holds it there. */
    public static double gaze(double w) {
        double a = (1 - STOP) / 2, b = a + STOP;
        return smooth(a - .01, a + .03, w) * (1 - smooth(b - .03, b + .005, w));
    }
    /** 0..1 turned from its path toward the watcher; it turns a little before it looks, and back before it walks. */
    public static double turn(double w) {
        double a = (1 - STOP) / 2, b = a + STOP;
        return smooth(a - .02, a + .015, w) * (1 - smooth(b - .02, b + .012, w));
    }
    public static Vec3 heading(double yaw) { return new Vec3(Math.cos(yaw), 0, Math.sin(yaw)); }
    /** Real-space ground point under the pilgrim (its reflection stands on this point's mirror image). */
    public static Vec3 pilgrimAt(Vec3 origin, double yaw, double s) {
        Vec3 d = heading(yaw), side = new Vec3(-d.z, 0, d.x);
        double t = s - PATH / 2;
        double sway = 7 * Math.sin(t / 61) * (1 - Math.exp(-t * t / 900));
        return origin.add(d.scale(t)).add(side.scale(BESIDE + sway));
    }
    /** Step phase: whole numbers are footfalls, alternating feet. */
    public static double stepPhase(double s) { return s / STRIDE; }

    // ------------------------------------------------------------------ small helpers
    /** Directions are 0..3 for south, west, north, east (Direction.get2DDataValue). */
    public static Vec3 faceNormal(int face) {
        return switch (face & 3) { case 0 -> new Vec3(0, 0, 1); case 1 -> new Vec3(-1, 0, 0); case 2 -> new Vec3(0, 0, -1); default -> new Vec3(1, 0, 0); };
    }
    /** Centre of the door's light frame on the real wall, one block up from its sill. */
    public static Vec3 doorCenter(net.minecraft.core.BlockPos wall, int face) {
        Vec3 n = faceNormal(face);
        return Vec3.atCenterOf(wall).add(n.scale(.51)).add(0, .5, 0);
    }
    /** A free-standing doorway stands in its own block, its opening centred there. */
    public static Vec3 doorCenter(net.minecraft.core.BlockPos at, int face, boolean free) {
        return free ? Vec3.atCenterOf(at).add(0, .5, 0) : doorCenter(at, face);
    }
    /** True if a look ray from {@code eye} passes within {@code radius} of {@code target}, no further than {@code reach}. */
    public static boolean aimed(Vec3 eye, Vec3 look, Vec3 target, double radius, double reach) {
        Vec3 to = target.subtract(eye);
        double along = to.dot(look.normalize());
        if (along < 0 || along > reach) return false;
        return to.subtract(look.normalize().scale(along)).length() < radius + along * .02;
    }
    private MirrorTimeline() {}
}
