package com.suiyi.veilborne;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * 绯线缝世 choreography and geometry shared by server, client and shaders (include/thread_seam.glsl mirrors the seam).
 * The sky is cloth: a dome of it hangs over the region, anchored to the event centre. It tears open along one long seam
 * from the middle outward; behind it is a darkness with no stars. A curved golden needle longer than a mountain comes
 * through from the other side and sutures the wound stitch by stitch, each stitch drawn tight until the edges meet. Then
 * it comes down out of the sky and sews the land to it: it rolls through the earth like a wheel, in at one foot and out
 * at the other, and goes back up. A hand nobody sees pulls the thread tight, and everything is tugged toward the seam.
 * The knot is tied, the thread is cut, and the seam cools into a band of stars.
 */
public final class ThreadTimeline {
    public static final int DEFAULT_DURATION = 13 * 60 * 20;
    public enum Phase { OMEN, TEAR, NEEDLE, SEW, KNOT, FADE, QUIET }
    public static final double[] BOUNDS = {0, 1/13.0, 3/13.0, 7/13.0, 9.5/13.0, 11.5/13.0, 1};
    static double smooth(double a, double b, double x) { return TideTimeline.smooth(a, b, x); }
    static double clamp(double x) { return TideTimeline.clamp(x); }
    public static double progress(double age, int duration) { return TideTimeline.progress(age, duration); }
    public static double region(double distance, double radius) { return TideTimeline.region(distance, radius); }
    public static Phase phase(double p) {
        for (int i = 0; i < 6; i++) if (p < BOUNDS[i + 1]) return Phase.values()[i];
        return Phase.QUIET;
    }

    // ------------------------------------------------------------------ the arc of the weather
    /** 引线: fibres drift one way, red gossamer hangs between tall things. It lingers, fainter, to the end. */
    public static double omen(double p) { return smooth(0, .03, p) * (1 - smooth(.95, 1, p)); }
    /** A gold hairline flickers where the sky is about to tear. */
    public static double hairline(double p) { return smooth(.025, .065, p) * (1 - smooth(.1, .15, p)); }
    /** The crimson cloth over the ordinary sky. */
    public static double veil(double p) { return smooth(.055, .19, p) * (1 - smooth(.9, .99, p)); }
    /** How far the tear has run from the middle toward both ends, 0..1. */
    public static double tear(double p) { return smooth(1.1 / 13, 2.85 / 13, p); }
    /** The golden needle: it comes out of the dark behind the wound and goes back into it after the world is sewn. */
    public static double needle(double p) { return smooth(3 / 13.0, 3.18 / 13, p) * (1 - smooth(9.66 / 13, 9.9 / 13, p)); }
    /** The sutured seam cools from scarlet to gold. */
    public static double cool(double p) { return smooth(9.6 / 13, 11.7 / 13, p); }
    /** And the gold line becomes an ordinary band of stars. */
    public static double starband(double p) { return smooth(11.1 / 13, 12.7 / 13, p); }
    /** Everything leaning a little toward the seam: dust, fibres, the clouds. */
    public static double pull(double p) { return smooth(.04, .12, p) * (1 - smooth(.74, .82, p)) * .6 + tug(p); }

    // ------------------------------------------------------------------ the cloth and the seam
    /** The dome of cloth: its centre is DROP below the event's ground, so its crown is RADIUS-DROP overhead. */
    public static final double DOME_RADIUS = 820, DOME_DROP = 240;
    /** The seam is an arc of a great circle of the dome, ARC either side of its middle; its middle leans TILT off the zenith. */
    public static final double ARC = Math.toRadians(62), TILT = Math.toRadians(18);
    /** Widest half-opening of the wound (radians at the dome centre), the half-width left once it is drawn shut, and how
     *  far outside the edge the needle goes in (blocks). */
    public static final double WIDTH = .11, LINE = .0055, MARGIN = 13;
    /** The needle: a half circle of this radius, this thick at its waist. */
    public static final double NEEDLE_RADIUS = 110, NEEDLE_THICK = 5.2;

    /** One seam: the dome's centre and three unit vectors, m toward the seam's middle, h along it there, n = m x h across it. */
    public record Seam(Vec3 center, Vec3 m, Vec3 h, Vec3 n, double key) {
        /** Unit vector from the dome centre at seam coordinates: a along the arc, d across it from the (meandering) seam line. */
        public Vec3 dir(double a, double d) {
            double dd = d + meander(a, key);
            Vec3 along = m.scale(Math.cos(a)).add(h.scale(Math.sin(a)));
            return along.scale(Math.cos(dd)).add(n.scale(Math.sin(dd))).normalize();
        }
        public Vec3 point(double a, double d, double r) { return center.add(dir(a, d).scale(r)); }
        public Vec3 point(double a, double d) { return point(a, d, DOME_RADIUS); }
        /** Along-arc angle of a unit vector from the dome centre. */
        public double along(Vec3 u) { return Math.atan2(u.dot(h), u.dot(m)); }
        /** Across angle from the seam line. */
        public double across(Vec3 u) { double a = along(u); return Math.asin(Math.max(-1, Math.min(1, u.dot(n)))) - meander(a, key); }
        /** Seam tangent at a (unit, pointing toward larger a). */
        public Vec3 tangent(double a) { return m.scale(-Math.sin(a)).add(h.scale(Math.cos(a))); }
    }
    public static Seam seam(BlockPos center, long seed) {
        double yaw = Math.toRadians(Math.floorMod(seed >>> 13, 360L));
        Vec3 side = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), h = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 m = new Vec3(0, Math.cos(TILT), 0).add(side.scale(Math.sin(TILT))).normalize();
        Vec3 c = new Vec3(center.getX() + .5, center.getY() - DOME_DROP, center.getZ() + .5);
        return new Seam(c, m, h, m.cross(h).normalize(), key(seed));
    }
    /** The event seed folded to 0..1 for shaders. */
    public static double key(long seed) { return Math.floorMod(seed, 997L) / 997.0; }
    public static double meander(double a, double key) { return .021 * Math.sin(3.1 * a + key * 40) + .011 * Math.sin(7.3 * a + key * 91 + 1.3); }
    /** Widest at the middle, pinched shut at both ends. */
    public static double profile(double a) { double t = Math.min(1, Math.abs(a) / ARC); return Math.pow(Math.max(0, 1 - t * t), .7); }
    /** 0..1 how far the tear has reached this part of the seam. */
    public static double opened(double a, double tear) { return smooth(0, .18, tear * 1.18 - Math.abs(a) / ARC); }
    /** 0..1 how far the edges here have been drawn together. */
    public static double closed(double a, double front) { return smooth(front + .03, front - .06, a); }
    /** Half-width of the wound here (radians at the dome centre), without the ragged detail the shader adds. */
    public static double halfWidth(double a, double p) {
        double open = opened(a, tear(p));
        return lerp(WIDTH * profile(a) * open, LINE * open, closed(a, closeFront(p)));
    }
    /** Half-width here once fully torn and not yet stitched: where the needle goes in. */
    public static double openWidth(double a) { return WIDTH * profile(a); }
    static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    // ------------------------------------------------------------------ the stitches
    public static final int STITCHES = 13;
    public static final double STITCH_START = 3.15 / 13, STITCH_END = 6.95 / 13;
    /** Share of a stitch spent turning the needle through; the rest slides it on beneath the cloth to the next. */
    public static final double ROTATE = .8, TIGHTEN = .14;
    public static double span() { return (STITCH_END - STITCH_START) / STITCHES; }
    /** Stitch clock: k + u, where k is the stitch and u runs 0..1 through it. Negative before the first. */
    public static double clock(double p) { return (p - STITCH_START) / span(); }
    public static double spacing() { return 1.8 * ARC / (STITCHES - 1); }
    public static double stitchAlong(int k) { return -.9 * ARC + k * spacing(); }
    /** Along-arc angle behind which the wound is drawn shut; each stitch closes its own stretch as it is pulled tight. */
    public static double closeFront(double p) {
        double c = clock(p) - ROTATE;
        if (c < 0) return -10;
        int k = (int) Math.floor(c);
        if (k >= STITCHES) return 10;
        return stitchAlong(k) - spacing() / 2 + spacing() * smooth(0, TIGHTEN / (1 - ROTATE) * .6, c - k);
    }
    /** 0..1 how taut stitch k's front bar is (0 while it still follows the needle's curve). */
    public static double taut(int k, double p) { return smooth(k + ROTATE, k + ROTATE + TIGHTEN, clock(p)); }
    /** Half chord (blocks) of stitch k: its entry and exit holes sit MARGIN outside the edges, which move as they close. */
    public static double chord(int k, double p, boolean atEntry) {
        double a = stitchAlong(k), hw = atEntry ? openWidth(a) : halfWidth(a, p);
        return Math.min(NEEDLE_RADIUS * .985, hw * DOME_RADIUS + MARGIN);
    }
    /** The two holes of stitch k on the cloth: entry on the +n side, exit on the -n side. */
    public static Vec3 hole(Seam s, int k, double p, boolean entry) {
        double c = chord(k, p, false) / DOME_RADIUS;
        return s.point(stitchAlong(k), entry ? c : -c);
    }

    // ------------------------------------------------------------------ the needle
    /**
     * The needle's pose: a half circle of radius NEEDLE_RADIUS about o in the plane of u and w, spanning circle angles
     * [tip - pi, tip] (the eye at tip - pi); points are o + R (cos b u + sin b w). It moves tip first, toward larger b.
     */
    public record Pose(Vec3 o, Vec3 u, Vec3 w, double tip, double visible) {
        public Vec3 at(double b) { return o.add(u.scale(Math.cos(b) * NEEDLE_RADIUS)).add(w.scale(Math.sin(b) * NEEDLE_RADIUS)); }
        public Vec3 tipPoint() { return at(tip); }
        public Vec3 eye() { return at(tip - Math.PI); }
        /** Tangent of travel at circle angle b. */
        public Vec3 heading(double b) { return u.scale(-Math.sin(b)).add(w.scale(Math.cos(b))); }
    }
    /**
     * A sky stitch: the circle's centre sits behind the cloth (outside the dome) so that the circle meets the cloth at the two
     * holes; w points in, toward the land. Entry angle b1 = asin(d/R) on the +n side, exit pi - b1 on the -n side.
     */
    public static Pose skyPose(Seam s, double a, double half, double tip, double visible) {
        Vec3 f = s.point(a, 0), in = s.center.subtract(f).normalize();
        Vec3 across = s.n.subtract(in.scale(s.n.dot(in))).normalize();
        double c = flat(half), d = Math.sqrt(Math.max(0, NEEDLE_RADIUS * NEEDLE_RADIUS - c * c));
        // The cloth is curved: the two holes' midpoint lies a little inside it, and the circle's centre is d behind that.
        return new Pose(f.add(in.scale(DOME_RADIUS * (1 - Math.cos(half / DOME_RADIUS)) - d)), across, in, tip, visible);
    }
    /** Straight-line half distance between two holes that lie {@code half} blocks either side of the seam along the cloth. */
    static double flat(double half) { return DOME_RADIUS * Math.sin(half / DOME_RADIUS); }
    public static double entryAngle(double half) { double c = flat(half); return Math.asin(Math.sqrt(Math.max(0, NEEDLE_RADIUS * NEEDLE_RADIUS - c * c)) / NEEDLE_RADIUS); }
    /** The needle while it sutures the sky (null outside the stitching). */
    public static Pose stitching(Seam s, double p) {
        double vis = needle(p);
        if (vis <= 0) return null;
        double c = clock(p);
        if (c < 0) {
            // Coming up out of the dark behind the first stitch.
            double b1 = entryAngle(chord(0, p, true));
            return skyPose(s, stitchAlong(0), chord(0, p, true), b1 - .45 + .45 * smooth(-1.2, 0, c), vis * smooth(-1.4, -.2, c));
        }
        int k = Math.min(STITCHES - 1, (int) Math.floor(c));
        double u = Math.min(1, c - k);
        double h0 = chord(k, p, true), b1 = entryAngle(h0), base = 2 * Math.PI * k;
        if (u < ROTATE || k == STITCHES - 1) {
            double t = smooth(0, 1, Math.min(1, u / ROTATE));
            return skyPose(s, stitchAlong(k), h0, base + b1 + t * (2 * Math.PI - 2 * b1), vis);
        }
        double t = smooth(0, 1, (u - ROTATE) / (1 - ROTATE));
        double h1 = chord(k + 1, p, true), b2 = entryAngle(h1);
        double a = lerp(stitchAlong(k), stitchAlong(k + 1), t);
        return skyPose(s, a, lerp(h0, h1, t), lerp(base + 2 * Math.PI - b1, base + 2 * Math.PI + b2, t), vis);
    }
    /** Share of stitch k's clock at which its point comes back out through the exit hole (the eased turn, inverted). */
    public static double exitAt(int k) {
        double b1 = entryAngle(chord(k, 0, true)), t = (Math.PI - 2 * b1) / (2 * Math.PI - 2 * b1), lo = 0, hi = 1;
        for (int i = 0; i < 30; i++) { double m = (lo + hi) / 2; if (m * m * (3 - 2 * m) < t) lo = m; else hi = m; }
        return ROTATE * (lo + hi) / 2;
    }
    /** Share of stitch k's clock at which the eye (and so the thread) goes in through the entry hole. */
    public static double eyeInAt(int k) {
        double b1 = entryAngle(chord(k, 0, true)), t = Math.PI / (2 * Math.PI - 2 * b1), lo = 0, hi = 1;
        for (int i = 0; i < 30; i++) { double m = (lo + hi) / 2; if (m * m * (3 - 2 * m) < t) lo = m; else hi = m; }
        return ROTATE * (lo + hi) / 2;
    }
    /** Share of the roll through the earth at which the eye follows the point in at the first foot. */
    public static double landEyeIn(Vec3 a, Vec3 b) { double g = landAngle(a, b); return Math.PI / (2 * Math.PI - 2 * g); }
    /** 0..1 through stitch k's turn, or -1 outside it. */
    public static double turning(int k, double p) {
        double u = clock(p) - k;
        return u < 0 || u > ROTATE ? -1 : u / ROTATE;
    }

    // ------------------------------------------------------------------ the world stitch (缝世)
    public static final double SEW_START = 7 / 13.0, SEW_END = 9.5 / 13.0;
    /** Moments within the sewing, as shares of the phase. */
    public static final double EMERGE = .02, DESCEND = .12, THROUGH = .30, ASCEND = .40, HAND0 = .38, HAND1 = .53,
        TUG0 = .52, TUG1 = .585, MOMENT0 = .525, MOMENT1 = .61, LAND_WARN = .02;
    /** How far apart the land stitch's two feet lie (half, blocks). */
    public static final double LAND_HALF = 100;
    public static double sew(double p) { return clamp((p - SEW_START) / (SEW_END - SEW_START)); }
    public static double pAtSew(double q) { return SEW_START + q * (SEW_END - SEW_START); }
    /** The pull: a hard catch, then it lets go slowly. */
    public static double tug(double p) { double q = sew(p); return smooth(TUG0, TUG0 + .008, q) * (1 - smooth(TUG0 + .02, TUG1, q)); }
    /** Threads on every living thing, rising to the seam: they show a little before the pull and fade after it. */
    public static double strings(double p) { double q = sew(p); return p < SEW_START ? 0 : smooth(TUG0 - .035, TUG0, q) * (1 - smooth(TUG1 + .02, TUG1 + .1, q)); }
    /** 缝合一个瞬间: the last few seconds laid over the present like layers of film. */
    public static double moment(double p) { double q = sew(p); return p < SEW_START ? 0 : smooth(MOMENT0, MOMENT0 + .01, q) * (1 - smooth(MOMENT1 - .05, MOMENT1, q)); }
    /** The hand's shadow crossing the sky and the land: -1 before, 0..1 across, then 2. */
    public static double hand(double p) { double q = sew(p); return p < SEW_START || q < HAND0 ? -1 : q > HAND1 ? 2 : (q - HAND0) / (HAND1 - HAND0); }
    /** The land loop is in place from the needle's descent until its threads fall after the cut. */
    public static boolean landLoop(double p) { return p >= pAtSew(EMERGE) && p < CUT + .02; }
    public static final double KNOT = 9.56 / 13, CUT = 9.74 / 13, POCKET_OPEN = 9.8 / 13, POCKET_CLOSE = 12.85 / 13;
    /** 0..1 as the cut threads fall away. */
    public static double fall(double p) { return smooth(CUT, CUT + .014, p); }
    /** The knot tied at the seam's middle. */
    public static double knot(double p) { return smooth(KNOT, KNOT + .006, p) * (1 - smooth(.97, 1, p)); }
    public static boolean pocketOpen(double p) { return p >= POCKET_OPEN && p < POCKET_CLOSE; }

    /** The middle of the seam, where the needle comes out to the land and goes back. */
    public static Vec3 middle(Seam s) { return s.point(0, 0); }
    public static Vec3 middleBack(Seam s) { return s.point(.035, 0); }
    /** The land stitch's circle: through both feet, in the vertical plane that holds them, its centre above the ground. */
    public static Pose landPose(Vec3 a, Vec3 b, double tip) {
        Vec3 mid = a.add(b).scale(.5), u = b.subtract(a);
        double half = Math.min(NEEDLE_RADIUS * .985, u.length() / 2);
        u = u.normalize();
        Vec3 w = new Vec3(0, 1, 0).subtract(u.scale(u.y)).normalize();
        double lift = Math.sqrt(NEEDLE_RADIUS * NEEDLE_RADIUS - half * half);
        return new Pose(mid.add(w.scale(lift)), u, w, tip, 1);
    }
    /** asin(lift/R): the feet sit at circle angles -pi + g (A, where it goes in) and -g (B, where it comes out). */
    public static double landAngle(Vec3 a, Vec3 b) {
        double half = Math.min(NEEDLE_RADIUS * .985, b.subtract(a).length() / 2);
        return Math.asin(Math.sqrt(NEEDLE_RADIUS * NEEDLE_RADIUS - half * half) / NEEDLE_RADIUS);
    }
    /** Share of the roll through the earth at which the tip breaks out at B. */
    public static double breakout(Vec3 a, Vec3 b) { double g = landAngle(a, b); return (Math.PI - 2 * g) / (2 * Math.PI - 2 * g); }
    public static double strikeA() { return pAtSew(DESCEND); }
    public static double strikeB(Vec3 a, Vec3 b) { return pAtSew(DESCEND + (THROUGH - DESCEND) * breakout(a, b)); }
    /** The needle's pose through the sewing; null before its emergence, and it never leaves the sky without both feet. */
    public static Pose sewing(Seam s, Vec3 a, Vec3 b, double p) {
        double q = sew(p), g = landAngle(a, b), vis = needle(p);
        double hMid = LINE * DOME_RADIUS + MARGIN, b1 = entryAngle(hMid);
        // Out of the sewn seam's middle: the tip comes through toward the land.
        Pose out = skyPose(s, 0, hMid, b1 + (Math.PI / 2 - b1) * smooth(0, EMERGE, q) + 2 * Math.PI * STITCHES, vis);
        Pose arrive = landPose(a, b, -Math.PI + g + 2 * Math.PI * (STITCHES + 1));
        if (q < EMERGE) return out;
        if (q < DESCEND) return blend(out, arrive, smooth(EMERGE, DESCEND, q), vis, 70);
        double roll = 2 * Math.PI - 2 * g;
        if (q < THROUGH) { Pose l = landPose(a, b, arrive.tip() + roll * (q - DESCEND) / (THROUGH - DESCEND)); return new Pose(l.o(), l.u(), l.w(), l.tip(), vis); }
        Pose done = landPose(a, b, arrive.tip() + roll);
        Pose back = skyPose(s, .035, hMid, done.tip() + Math.PI * .7, vis);
        if (q < ASCEND) return blend(done, back, smooth(THROUGH, ASCEND, q), vis, 40);
        return back;
    }
    /** A rigid move between two poses along a path that bows outward by {@code bow} blocks, turning smoothly on the way. */
    static Pose blend(Pose from, Pose to, double t, double visible, double bow) {
        Quaterniond qa = frame(from), qb = frame(to), q = new Quaterniond(qa).slerp(qb, t);
        Vector3d u = q.transform(new Vector3d(1, 0, 0)), w = q.transform(new Vector3d(0, 1, 0));
        Vec3 o = from.o().lerp(to.o(), t).add(0, Math.sin(Math.PI * t) * bow, 0);
        return new Pose(o, new Vec3(u.x, u.y, u.z), new Vec3(w.x, w.y, w.z), lerp(from.tip(), to.tip(), t), visible);
    }
    static Quaterniond frame(Pose p) {
        Vec3 z = p.u().cross(p.w());
        return new Quaterniond().setFromNormalized(new org.joml.Matrix3d(p.u().x, p.u().y, p.u().z, p.w().x, p.w().y, p.w().z, z.x, z.y, z.z));
    }

    // ------------------------------------------------------------------ the needle falling on the land (落针区)
    /** Three stitches during the suturing reach down to the land; stitch k's strike falls this far into its turn. */
    public static final int[] STRIKE_STITCH = {2, 5, 8};
    public static final double STRIKE_AT = .45;
    public static final int STRIKE_WARN = 120, LAND_WARN_TICKS = 100;
    public static final double STRIKE_RANGE = 7, LAND_RANGE = 9.5;
    public static double strikeP(int i) { return STITCH_START + (STRIKE_STITCH[i] + STRIKE_AT) * span(); }
    /** Tick of the strike and of its warning for a weather of this length. */
    public static int strikeTick(int i, int duration) { return (int) Math.round(strikeP(i) * duration); }
    public static int landTick(boolean b, Vec3 a, Vec3 bb, int duration) { return (int) Math.round((b ? strikeB(a, bb) : strikeA()) * duration); }
    public static int landWarnTick(int duration) { return (int) Math.round(pAtSew(LAND_WARN) * duration); }
    /** 0..1 as a strike's warning builds, or -1 outside its warning. */
    public static double warning(int age, int strikeAt, int warn) { return age < strikeAt - warn || age >= strikeAt ? -1 : (age - (strikeAt - warn)) / (double) warn; }
    /** Where a strike's thread meets the cloth: the entry hole of its stitch. */
    public static Vec3 strikeTop(Seam s, int i, double p) { return hole(s, STRIKE_STITCH[i], p, true); }
    public static boolean inZone(Vec3 center, double range, Vec3 at) { return Math.hypot(at.x - center.x, at.z - center.z) <= range && Math.abs(at.y - center.y) < 6; }

    /** Nearest point on the land stitch's line between its feet, flat. */
    public static Vec3 nearestOnLine(Vec3 a, Vec3 b, Vec3 at) {
        Vec3 ab = new Vec3(b.x - a.x, 0, b.z - a.z);
        double t = clamp(new Vec3(at.x - a.x, 0, at.z - a.z).dot(ab) / Math.max(1e-6, ab.lengthSqr()));
        return a.add(b.subtract(a).scale(t));
    }
    private ThreadTimeline() {}
}
