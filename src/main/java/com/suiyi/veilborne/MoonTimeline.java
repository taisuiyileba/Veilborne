package com.suiyi.veilborne;

import net.minecraft.world.phys.Vec3;

/**
 * 月骸花潮 choreography shared by server and client. Everything follows from progress and the event seed,
 * so every player sees the same moon crack, the same three moons and the same true one.
 */
public final class MoonTimeline {
    public static final int DEFAULT_DURATION = 12 * 60 * 20;
    public enum Phase { HALO, CRACK, BLOOM, MOLT, FAREWELL, CLOSE, QUIET }
    public static final double[] BOUNDS = {0, 1/12.0, 3/12.0, 7/12.0, 9/12.0, 10.5/12.0, 1};
    private static double smooth(double a, double b, double x) { return TideTimeline.smooth(a, b, x); }
    public static double progress(double age, int duration) { return TideTimeline.progress(age, duration); }
    public static Phase phase(double p) {
        for (int i = 0; i < 6; i++) if (p < BOUNDS[i + 1]) return Phase.values()[i];
        return Phase.QUIET;
    }
    /** The violet night spreading out from the moon. */
    public static double veil(double p) { return smooth(.03, .2, p) * (1 - smooth(.9, 1, p)); }
    /** Our moon itself: present from the first halo until the shell has closed again. */
    public static double moon(double p) { return smooth(0, .05, p) * (1 - smooth(.965, 1, p)); }
    /** 月晕: the 22-degree ring is the omen, and it lingers faintly afterwards. */
    public static double halo(double p) { return smooth(0, .035, p) * (.3 + .7 * (1 - smooth(.12, .26, p))) * (1 - smooth(.9, 1, p)); }
    /** Gold light through the cracks. */
    public static double crack(double p) { return smooth(.06, .24, p) * (1 - smooth(.9, .975, p)); }
    /** Share of shell plates that have come away: a few flakes through the bloom, most of them in the molt. */
    public static double shed(double p) { return (smooth(.22, .56, p) * .16 + smooth(.585, .68, p) * .74) * (1 - smooth(.875, .965, p)); }
    /** Brightness of the bare core under the shell. */
    public static double core(double p) { return smooth(.2, .66, p) * (1 - smooth(.88, .97, p)); }
    public static double petals(double p) {
        return smooth(.12, .3, p) * (1 - smooth(.84, .95, p)) * (.72 + .28 * smooth(.57, .64, p) * (1 - smooth(.72, .8, p)));
    }
    /** How far the flower tide has spread across the land. */
    public static double bloom(double p) { return smooth(.2, .52, p); }
    /** Flower shadows close in rings from far to near. */
    public static double closing(double p) { return smooth(.875, .985, p); }
    /** 三月同天. */
    public static double three(double p) { return smooth(.63, .655, p) * (1 - smooth(.745, .765, p)); }
    public static double split(double p) { return smooth(.63, .675, p); }
    public static boolean threeActive(double p) { return p >= .64 && p < .75; }
    public static double shadows(double p) { return smooth(.02, .1, p) * (1 - smooth(.9, .99, p)); }
    public static double region(double distance, double radius) { return TideTimeline.region(distance, radius); }

    // ------------------------------------------------------------------ the moons
    public static final double BASE_ALT = Math.toRadians(30);
    /** Final resting places of the three moons: azimuth offset and altitude, in degrees. */
    private static final double[][] SLOTS = {{-36, 22}, {0, 36}, {35, 27}};
    public static double azimuth(long seed) { return Math.floorMod(seed * 31 + 7, 3600L) / 3600.0 * Math.PI * 2; }
    public static int trueSlot(long seed) { return (int) Math.floorMod(seed >>> 9, 3L); }
    public static Vec3 sphere(double az, double alt) { return new Vec3(Math.cos(az) * Math.cos(alt), Math.sin(alt), Math.sin(az) * Math.cos(alt)); }
    public static Vec3 base(long seed) { return sphere(azimuth(seed), BASE_ALT); }
    public static Vec3 slot(long seed, int slot) { return sphere(azimuth(seed) + Math.toRadians(SLOTS[slot][0]), Math.toRadians(SLOTS[slot][1])); }
    /** Where a moon hangs: all three leave the one moon together, so none can be told apart by where it came from. */
    public static Vec3 moonDir(long seed, int slot, double split, double merge) {
        Vec3 d = slerp(base(seed), slot(seed, slot), split);
        return slot == trueSlot(seed) ? slerp(d, base(seed), merge) : d;
    }
    /** Direction of the one true moon. */
    public static Vec3 trueDir(long seed, double p, double merge) {
        return moonDir(seed, trueSlot(seed), p >= .63 && p < .8 ? split(p) : 0, merge);
    }
    public static Vec3 slerp(Vec3 a, Vec3 b, double t) {
        double dot = Math.max(-1, Math.min(1, a.dot(b))), th = Math.acos(dot);
        if (th < 1e-6) return a;
        double s = Math.sin(th);
        return a.scale(Math.sin((1 - t) * th) / s).add(b.scale(Math.sin(t * th) / s)).normalize();
    }
    /** Index of the moon the look vector points at, or -1. Angles in radians. */
    public static int aimedSlot(long seed, Vec3 look, double split, double tolerance) {
        int best = -1; double bestAngle = tolerance;
        for (int i = 0; i < 3; i++) {
            double a = Math.acos(Math.max(-1, Math.min(1, look.normalize().dot(moonDir(seed, i, split, 0)))));
            if (a < bestAngle) { bestAngle = a; best = i; }
        }
        return best;
    }

    // ------------------------------------------------------------------ the fallen little moon
    public static final double LITTLE_MOON_HEIGHT = 1.7;
    /** Standing spot from which the scattered motes line up into a constellation. */
    public static Vec3 viewpoint(long seed, Vec3 moonBase) {
        double a = Math.floorMod(seed * 17 + 3, 360L) * Math.PI / 180;
        return moonBase.add(Math.cos(a) * 3.4, 0, Math.sin(a) * 3.4);
    }
    public static boolean sightAligned(Vec3 eye, Vec3 look, Vec3 moonCenter, Vec3 spot) {
        if (Math.hypot(eye.x - spot.x, eye.z - spot.z) > .9) return false;
        Vec3 to = moonCenter.subtract(eye).normalize();
        return look.normalize().dot(to) > Math.cos(Math.toRadians(15));
    }
    private MoonTimeline() {}
}
