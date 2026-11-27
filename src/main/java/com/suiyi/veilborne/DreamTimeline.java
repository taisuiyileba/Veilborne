package com.suiyi.veilborne;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * 苍翠梦潮 choreography shared by server, client and tests. Its motion language is growth: nothing arrives, everything grows.
 * Seeds of light rise out of the grass; the clouds take a green underside and a forest comes out of them upside down, a
 * canopy spreading from the zenith to the horizon; luminous aerial roots grow down from its hanging crowns until they grip
 * the hilltops. Leaves come loose and swim like fish, turn together, dive, and near the ground dissolve into pollen, and
 * where the pollen settles the slopes light up with flower patterns, ring outside ring. A dream tree grows near you and a
 * path of petals leads to it. Then the sky lets down a road of roots, one after another toward you, and a deer walks along
 * it: a stag the height of a hill, almost transparent, a whole woodland growing on its antlers, and at every hoof-fall a
 * ring of flowers opens on the land. It stops by the dream tree and lowers its head to look. After it the forest turns
 * gold and lets its leaves go, dream seeds come down with them to be guided to the soil, the canopy thins to bare branch
 * veins, the roots draw back up, and what is left is an ordinary cloud.
 */
public final class DreamTimeline {
    public static final int DEFAULT_DURATION = 13 * 60 * 20;
    public enum Phase { OMEN, ROOTS, LEAVES, DEER, SEEDFALL, RETURN, QUIET }
    public static final double[] BOUNDS = {0, 1 / 13.0, 3 / 13.0, 7 / 13.0, 9.5 / 13, 11.5 / 13, 1};
    static double smooth(double a, double b, double x) { return TideTimeline.smooth(a, b, x); }
    static double clamp(double x) { return TideTimeline.clamp(x); }
    public static double progress(double age, int duration) { return TideTimeline.progress(age, duration); }
    public static double region(double distance, double radius) { return TideTimeline.region(distance, radius); }
    public static Phase phase(double p) {
        for (int i = 0; i < 6; i++) if (p < BOUNDS[i + 1]) return Phase.values()[i];
        return Phase.QUIET;
    }

    // ------------------------------------------------------------------ the sky
    /** Seeds of light rising out of the grass: the whole omen, and faintly all through. */
    public static double fluff(double p) { return smooth(0, .025, p) * (1 - .65 * smooth(1.3 / 13, 2.2 / 13, p)) * (1 - smooth(12 / 13.0, 12.8 / 13, p)); }
    /** The clouds take a green underside before the forest comes out of them, and lose it last. */
    public static double cloudGreen(double p) { return smooth(.35 / 13, 1.15 / 13, p) * (1 - smooth(12.45 / 13, 12.97 / 13, p)); }
    /** The ordinary clouds give way to the canopy, and at the very end come back. */
    public static double cloudVeil(double p) { return smooth(1.1 / 13, 2.1 / 13, p) * (1 - smooth(12.15 / 13, 12.85 / 13, p)); }
    /** How far the canopy has spread from the zenith toward the horizon (0..1 of the way, as seen from the event's centre). */
    public static double canopy(double p) { return smooth(1 / 13.0, 2.75 / 13, p); }
    /** The drawn sky as a whole. */
    public static double sky(double p) { return smooth(.95 / 13, 1.25 / 13, p) * (1 - smooth(12.5 / 13, 12.97 / 13, p)); }
    /** Growth of the roots, 0..1 across their window; each root starts at its own delay inside it. */
    public static final double ROOT0 = 1.35 / 13, ROOT1 = 3.1 / 13;
    public static double rootGrow(double p) { return clamp((p - ROOT0) / (ROOT1 - ROOT0)); }
    /** The roots drawn back up into the canopy at the end: 0 down, 1 gone. */
    public static double rootWithdraw(double p) { return smooth(11.55 / 13, 12.55 / 13, p); }
    /** Leaves let go: 0 the full canopy, 1 bare branches. */
    public static double leafLoss(double p) { return smooth(9.6 / 13, 11.3 / 13, p); }
    /** How golden the forest has turned before it lets its leaves go. */
    public static double gold(double p) { return smooth(9.2 / 13, 10.6 / 13, p); }
    /** The bare branch veins fade into ordinary cloud. */
    public static double veinsFade(double p) { return smooth(11.75 / 13, 12.7 / 13, p); }
    public static double fireflies(double p) { return smooth(1.6 / 13, 2.6 / 13, p) * (1 - smooth(11.9 / 13, 12.6 / 13, p)); }
    /** Emerald light: how far the skylight and the fog are tinted. */
    public static double light(double p) { return smooth(1 / 13.0, 2.6 / 13, p) * (1 - smooth(12.2 / 13, 12.9 / 13, p)); }
    /** Leaf-light moving on the ground and the outside walls; thinner once the leaves have gone. */
    public static double dapple(double p) { return light(p) * (1 - .6 * leafLoss(p)); }
    /** Leaf-fish schools in the air. */
    public static double schools(double p) { return smooth(3 / 13.0, 3.45 / 13, p) * (1 - smooth(9.3 / 13, 9.85 / 13, p)); }
    /** The golden leaf-fall, while the dream seeds come down. */
    public static double goldFall(double p) { return smooth(9.5 / 13, 10.1 / 13, p) * (1 - smooth(11.3 / 13, 11.9 / 13, p)); }
    /** Is the world under the canopy (for the record of its arrival): the roots have reached the land. */
    public static final double ARRIVED = 2.7 / 13;

    // ------------------------------------------------------------------ the canopy's shape
    /** The canopy is a dome anchored over the event: its apex this high over the centre, curving down toward the horizon. */
    public static final double APEX = 235, DOME_R = 1150;
    /** Height of the canopy over the event's ground at a horizontal distance from its centre. */
    public static double domeHeight(double r) { return APEX - DOME_R + Math.sqrt(Math.max(0, DOME_R * DOME_R - r * r)); }
    /** A tree of the inverted forest whose crown hangs lower than the rest, with its aerial roots. Positions relative to the
     *  centre; {@code crown} the height its crown hangs down to; {@code size} its scale. */
    public record Tree(double x, double z, double crown, double size, long key) {}
    public static List<Tree> trees(long seed, int count) {
        List<Tree> out = new ArrayList<>();
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < count; i++) {
            long k = mix(seed, i);
            double a = i * golden + u(k, 1) * .7, r = 60 + 300 * Math.sqrt((i + .35 + .3 * u(k, 2)) / count);
            double size = .75 + .5 * u(k, 3);
            double x = Math.cos(a) * r, z = Math.sin(a) * r;
            out.add(new Tree(x, z, domeHeight(r) - (42 + 26 * u(k, 4)) * size, size, k));
        }
        return out;
    }
    /** Roots of one tree: where each one leaves the crown (relative to the centre), where it is headed on the ground (x, z),
     *  and whether it reaches the ground or hangs free, ending in mid air; {@code delay} 0..1 staggers them. */
    public record Root(double x, double y, double z, double tx, double tz, boolean reaches, double hang, double delay, double width, long key) {}
    public static List<Root> roots(Tree t) {
        List<Root> out = new ArrayList<>();
        int n = 2 + (int) (u(t.key(), 5) * 3);
        for (int j = 0; j < n; j++) {
            long k = mix(t.key(), 40 + j);
            double a = u(k, 1) * Math.PI * 2, spread = (j == 0 ? 2 : 6 + 8 * u(k, 2)) * t.size();
            double sx = t.x() + Math.cos(a) * spread, sz = t.z() + Math.sin(a) * spread;
            boolean reaches = j == 0 || u(k, 3) < .45;
            double reach = reaches ? 10 + 30 * u(k, 4) : 4 + 10 * u(k, 4);
            double tx = sx + Math.cos(a) * reach, tz = sz + Math.sin(a) * reach;
            out.add(new Root(sx, t.crown() + 6 * t.size(), sz, tx, tz, reaches, reaches ? 0 : 50 + 70 * u(k, 5), .05 + .35 * Math.min(1, Math.hypot(t.x(), t.z()) / 360) + .15 * u(k, 6), (j == 0 ? 2.4 : 1.3 + .7 * u(k, 7)) * t.size(), k));
        }
        return out;
    }

    // ------------------------------------------------------------------ the dream tree, the path, the seeds
    public static final double TREE_AT = 3.3 / 13, PATH_FROM = 3.55 / 13, PATH_UNTIL = 7.2 / 13, WANDER_FROM = 3.9 / 13, WANDER_UNTIL = 6.6 / 13,
        LOST_FROM = 3.75 / 13, LOST_UNTIL = 7 / 13.0, SEEDFALL_FROM = 9.75 / 13, SEEDFALL_UNTIL = 11.2 / 13, TWIG_AT = 11.7 / 13, WITHER = 11.8 / 13;
    public static final int TREE_GROW = 900, TWIG_FALL = 110, GIFT_FALL = 110, PATH_POINTS = 64;
    /** 0..1 how grown the dream tree is, ticks after it sprouted; it withers into light at the end. */
    public static double treeGrowth(double since) { return since < 0 ? 0 : smooth(0, TREE_GROW, since); }
    public static double treeWither(double p) { return smooth(WITHER, WITHER + .5 / 13, p); }
    /** How far the petal path has lit, from its start toward the tree, 0..1, ticks after it was laid. */
    public static double pathLit(double since) { return since < 0 ? 0 : smooth(0, 240, since); }

    // ------------------------------------------------------------------ the vine enclosure
    /** Touch the wandering seed and a ring of vines grows round you, with a gap to leave by; lights come one after another. */
    public static final int LIGHTS = 5, ENCLOSE_TICKS = 70, LIGHT_TICKS = 170, WON_TICKS = 120;
    public static final double RING = 6.5, TOUCH = 1.25;
    /** Where the k-th light comes, inside the ring, never in the gap; it floats a little over the ground. */
    public static Vec3 lightSpot(Vec3 center, long seed, int k, double exit) {
        double a = exit + Math.PI * (.35 + 1.3 * u(mix(seed, 900 + k), 1)), r = 2.2 + 2.8 * u(mix(seed, 900 + k), 2);
        if (k > 0) a += Math.PI * .45 * (k % 2 == 0 ? 1 : -1);
        return center.add(Math.cos(a) * r, 0, Math.sin(a) * r);
    }
    /** 0..1 how grown the vines are, ticks after the enclosure opened; they draw back after it is won or given up. */
    public static double vines(double since, double closedSince) {
        double up = smooth(0, ENCLOSE_TICKS, since);
        return closedSince < 0 ? up : up * (1 - smooth(0, 80, closedSince));
    }

    // ------------------------------------------------------------------ the deer
    public static final double ROAD = 6.35 / 13, DEER_START = 7 / 13.0, DEER_END = 9.5 / 13;
    /**
     * Path length in blocks; it stops halfway, {@link #BESIDE} blocks to one side of the dream tree, turns toward it and
     * lowers its head. At a walk it covers {@link #STRIDE} blocks a gait cycle: slow, and very large.
     */
    public static final double PATH = 520, STRIDE = 58, BESIDE = 46, STOP = .22, SHOULDER = 44;
    public static double deer(double p) { return smooth(DEER_START + .002, DEER_START + .025, p) * (1 - smooth(DEER_END - .025, DEER_END - .002, p)); }
    public static double walk(double p) { return clamp((p - DEER_START) / (DEER_END - DEER_START)); }
    /** Distance along the path: it slows into its stop, stands, walks on; either side of the stop at one steady pace. */
    public static double along(double w) {
        double a = (1 - STOP) / 2, b = a + STOP;
        if (w < a) { double t = w / a; return PATH / 2 * (t < .85 ? t / .925 : (.85 + (t - .85) - (t - .85) * (t - .85) / .3) / .925); }
        if (w < b) return PATH / 2;
        double t = (w - b) / a;
        return PATH / 2 + PATH / 2 * (t < .15 ? (t * t / .3) / .925 : (t - .075) / .925);
    }
    /** 0..1 while it stands: it turns toward the tree, then lowers its head to look, then lifts it and walks on. */
    public static double turn(double w) {
        double a = (1 - STOP) / 2, b = a + STOP;
        return smooth(a - .02, a + .02, w) * (1 - smooth(b - .025, b + .01, w));
    }
    public static double gaze(double w) {
        double a = (1 - STOP) / 2, b = a + STOP;
        return smooth(a + .005, a + .05, w) * (1 - smooth(b - .05, b - .008, w));
    }
    /** The moment of the glance, as progress: when its head is lowest. */
    public static double glanceP() { double a = (1 - STOP) / 2; return DEER_START + (a + .06) * (DEER_END - DEER_START); }
    public static double stopFrom() { return DEER_START + (1 - STOP) / 2 * (DEER_END - DEER_START); }
    public static double stopUntil() { return stopFrom() + STOP * (DEER_END - DEER_START); }
    /** The deer's road: through {@code origin} (beside which it stops), heading along {@code yaw}, offset to {@code side}. */
    public record Route(Vec3 origin, double yaw, int side) {
        public Vec3 heading() { return new Vec3(Math.cos(yaw), 0, Math.sin(yaw)); }
        public Vec3 lateral() { return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)).scale(side); }
        /** Ground-plane point on the path (y is the origin's; the caller puts it on the land). */
        public Vec3 at(double s) {
            double t = s - PATH / 2;
            double sway = 9 * Math.sin(t / 71) * (1 - Math.exp(-t * t / 1600));
            return origin.add(heading().scale(t)).add(lateral().scale(BESIDE + sway));
        }
        public Vec3 tangent(double s) { Vec3 d = at(s + 1).subtract(at(s - 1)); return new Vec3(d.x, 0, d.z).normalize(); }
        /** The side of the path toward the tree. */
        public Vec3 inward(double s) { Vec3 t = tangent(s); return new Vec3(-t.z, 0, t.x).scale(-side); }
    }
    /** Gait phase: whole numbers are cycles; each leg plants at its own offset. Legs: 0 left hind, 1 left fore, 2 right hind,
     *  3 right fore, in a walk's lateral sequence. */
    public static final double[] LEG_PHASE = {0, .25, .5, .75}, LEG_ALONG = {-19, 15, -19, 15}, LEG_SIDE = {-6, -5.2, 6, 5.2};
    public static final double DUTY = .68;
    public static double gait(double s) { return s / STRIDE; }
    /** Where a leg's k-th step comes down, along the path, and where it rests before it lifts again. */
    public static double plantAlong(int leg, int k) { return (k + LEG_PHASE[leg]) * STRIDE + DUTY * STRIDE / 2 + LEG_ALONG[leg]; }
    public static Vec3 plant(Route r, int leg, int k) {
        double s = plantAlong(leg, k);
        return r.at(s).add(new Vec3(-r.tangent(s).z, 0, r.tangent(s).x).scale(LEG_SIDE[leg]));
    }
    /** For a leg at gait phase g: which step it is on, and 0..1 through its swing (negative while it stands). */
    public static int step(int leg, double g) { return (int) Math.floor(g - LEG_PHASE[leg]); }
    public static double swing(int leg, double g) {
        double f = g - LEG_PHASE[leg] - Math.floor(g - LEG_PHASE[leg]);
        return f < DUTY ? -1 : (f - DUTY) / (1 - DUTY);
    }
    /** Progress at which the body is {@code s} blocks along (inverts {@link #along}; the stop is skipped). */
    public static double atAlong(double s) {
        double lo = 0, hi = 1;
        if (s <= 0) return DEER_START;
        if (s >= PATH) return DEER_END;
        boolean second = s > PATH / 2 + 1e-6;
        double a = (1 - STOP) / 2;
        if (second) lo = a + STOP; else hi = a;
        for (int i = 0; i < 40; i++) { double m = (lo + hi) / 2; if (along(m) < s) lo = m; else hi = m; }
        return DEER_START + (lo + hi) / 2 * (DEER_END - DEER_START);
    }
    public interface Ground { double y(double x, double z); }
    /** A hoof coming down: where, and when (progress). */
    public record Footfall(Vec3 at, double p, int leg) {}
    public static List<Footfall> footfalls(Route r, Ground g) {
        List<Footfall> out = new ArrayList<>();
        for (int leg = 0; leg < 4; leg++) for (int k = -1; k * STRIDE < PATH + STRIDE; k++) {
            double bodyS = (k + LEG_PHASE[leg]) * STRIDE;
            if (bodyS < 0 || bodyS > PATH) continue;
            Vec3 at = plant(r, leg, k);
            out.add(new Footfall(new Vec3(at.x, g.y(at.x, at.z), at.z), atAlong(bodyS), leg));
        }
        out.sort((a, b) -> Double.compare(a.p(), b.p()));
        return out;
    }
    /** The road of roots let down before it: pairs either side of its path, one after another, ahead of it toward the tree. */
    public record RoadRoot(Vec3 foot, double along, double delay) {}
    public static List<RoadRoot> roadRoots(Route r) {
        List<RoadRoot> out = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            double s = 40 + i * 40, side = (i % 2 == 0 ? 1 : -1) * (24 + 5 * Math.sin(i * 1.7));
            Vec3 c = r.at(s), t = r.tangent(s);
            out.add(new RoadRoot(c.add(new Vec3(-t.z, 0, t.x).scale(side)), s, i / 12.0 * .8));
        }
        return out;
    }
    /** 0..1 how far the road's roots have come down (each root starts at its own delay within it). */
    public static double road(double p) { return clamp((p - ROAD) / (DEER_START + .15 / 13 - ROAD)); }
    /** Hoof rings open in three seconds and glow for forty, then settle to a faint flower shadow that stays till the end,
     *  so a seed brought down in the seedfall can still find one. */
    public static double hoofRing(double seconds) { return seconds < 0 ? 0 : smooth(0, 3, seconds) * (1 - .72 * smooth(30, 48, seconds)); }

    // ------------------------------------------------------------------ small helpers
    public static long mix(long seed, long k) {
        long z = seed + k * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
    /** A deterministic 0..1 from a key and a channel. */
    public static double u(long key, int channel) { return (mix(key, channel * 7919L) >>> 11) / (double) (1L << 53); }
    private DreamTimeline() {}
}
