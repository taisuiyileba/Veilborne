package com.suiyi.veilborne;

import net.minecraft.world.phys.Vec3;

/**
 * 无声白昼 choreography shared by server, client and tests. Its motion language is hovering: nothing here flows or blooms,
 * things stop. Sound leaves first, one layer after another (music, the wind and the caves, the animals, the blocks, last of
 * all your own footsteps). Then colour: a grey tide rolls in from the horizon until the world is a pale, sourceless
 * daylight, even at night, and only lamps keep a little amber. Dust, leaves and drops stop where they are and hang.
 * A black sun that gives no light rises out of the north, where no sun ever goes, and climbs toward the pale sun (or the
 * moon, whichever will be up), drinking the world's colours in thin ribbons. It covers it: for most of a minute nothing
 * sounds, nothing moves, and instead of going dark the world goes white and shadowless under a black corona. It lets go
 * and sinks back into the north; a white line stands on the horizon and its thunder comes half a minute too late. Then a
 * bird sings, footsteps come back, colour spreads outward from where you stand, and the sky is the last to get it back.
 */
public final class HushTimeline {
    public static final int DEFAULT_DURATION = 12 * 60 * 20;
    public enum Phase { OMEN, FADE, SUN, ECLIPSE, THUNDER, RETURN, QUIET }
    public static final double[] BOUNDS = {0, 1 / 12.0, 3 / 12.0, 6.25 / 12, 8.5 / 12, 10.5 / 12, 1};
    static double smooth(double a, double b, double x) { return TideTimeline.smooth(a, b, x); }
    static double clamp(double x) { return TideTimeline.clamp(x); }
    public static double progress(double age, int duration) { return TideTimeline.progress(age, duration); }
    public static double region(double distance, double radius) { return TideTimeline.region(distance, radius); }
    public static Phase phase(double p) {
        for (int i = 0; i < 6; i++) if (p < BOUNDS[i + 1]) return Phase.values()[i];
        return Phase.QUIET;
    }

    // ------------------------------------------------------------------ the sound, layer by layer (1 normal .. 0 gone)
    /** The first bird of the return, and the moment the held things start to move again. */
    public static final double BIRD = .882;
    static double layer(double p, double out0, double out1, double in0, double in1, double depth) {
        return 1 - depth * smooth(out0, out1, p) * (1 - smooth(in0, in1, p));
    }
    public static double music(double p) { return layer(p, .004, .035, .955, .995, 1); }
    public static double ambient(double p) { return layer(p, .02, .07, .93, .975, 1); }
    public static double animals(double p) { return layer(p, .04, .1, .905, .94, 1); }
    /** Blocks are muffled rather than lost: breaking and placing still answer you, a long way off. */
    public static double blocks(double p) { return layer(p, .06, .12, .895, .925, .84); }
    public static double steps(double p) { return layer(p, .07, .13, BIRD + .004, BIRD + .03, 1); }
    /** How far into the hush the world is, for the drone and the prompts: 0 before, 1 once even the steps are gone. */
    public static double hush(double p) { return 1 - steps(p); }

    // ------------------------------------------------------------------ the colour
    /** The pale sky over the ordinary one (clouds stay, frozen; the sun and moon give way to the drawn ones). */
    public static double pale(double p) { return smooth(.3 / 12, 2.2 / 12, p) * (1 - smooth(.955, .995, p)); }
    /** How far out from you the grey has reached (blocks): the tide rolls in from the horizon to your feet. The client
     *  scales both fronts down to its own view distance, so the tide always comes in from the edge of what you can see. */
    public static final double FAR = 320;
    public static double greyFront(double p) { return FAR * (1 - smooth(1.05 / 12, 2.75 / 12, p)) - 40 * smooth(2.5 / 12, 2.75 / 12, p); }
    /** How far out colour has come back at the return (blocks), spreading from where you stand; the sky follows last. */
    public static double colourFront(double p) { return (FAR + 80) * smooth(.9, .968, p) - 30 * (1 - smooth(.9, .905, p)); }
    /** The drained colour's own strength, as the grey is drawn from the world at all. */
    public static double drain(double p) { return smooth(.9 / 12, 1.3 / 12, p) * (1 - smooth(.985, 1, p)); }
    public static double skyColour(double p) { return smooth(.955, .995, p); }

    // ------------------------------------------------------------------ stillness
    /** Dust, leaves and drops stop and hang; after the bird they fall again. */
    public static double stillness(double p) { return smooth(.012, .07, p) * (1 - smooth(BIRD, BIRD + .04, p)); }
    /** The last wind: it runs through the omen and stops dead at the tree line. How far it has carried the air, 0..1. */
    public static double gust(double p) {
        // Integral of a speed that falls from 1 to 0 across .012 .. .055: the air moves, slows and stops.
        double a = .012, b = .055;
        if (p <= a) return p / b;
        if (p >= b) return (a + (b - a) * .5) / b;
        double t = (p - a) / (b - a);
        return (a + (b - a) * (t - t * t * t + t * t * t * t / 2)) / b;
    }
    /** Clouds drift at this share of their usual speed. */
    public static double cloudSpeed(double p) { return 1 - smooth(.01, .06, p) * (1 - smooth(.93, .98, p)); }

    // ------------------------------------------------------------------ the black sun
    public static final double RISE = 3 / 12.0, TOTAL0 = 7.0 / 12, TOTAL1 = 7.8 / 12, SET = 10.35 / 12;
    /** Angular radii (radians): the black sun is larger than the pale disc, so it covers it with room to spare. */
    public static final double BLACK_RADIUS = Math.toRadians(5.4), WHITE_RADIUS = Math.toRadians(3.6);
    /** 0..1 along its way from the north horizon to the pale sun, reached exactly at totality. */
    public static double approach(double p) { return smooth(RISE - .1 / 12, TOTAL0, p); }
    /** 0..1 along its way back down into the north. */
    public static double withdraw(double p) { return smooth(TOTAL1, SET, p); }
    public static double blackVisible(double p) { return smooth(RISE - .15 / 12, RISE + .3 / 12, p) * (1 - smooth(SET - .25 / 12, SET, p)); }
    public static double totality(double p) { return smooth(TOTAL0 - .006, TOTAL0 + .002, p) * (1 - smooth(TOTAL1 - .002, TOTAL1 + .006, p)); }
    /** The black corona: rays of darkness on the white sky while it covers the sun. */
    public static double corona(double p) { return smooth(TOTAL0 - .1 / 12, TOTAL0 + .05 / 12, p) * (1 - smooth(TOTAL1 - .05 / 12, TOTAL1 + .12 / 12, p)); }
    /** The world's colours going up into it in ribbons (positive), and given back as it sinks (negative). */
    public static double ribbons(double p) {
        return smooth(3.6 / 12, 5 / 12.0, p) * (1 - smooth(TOTAL0 - .1 / 12, TOTAL0, p)) - .55 * smooth(TOTAL1 + .1 / 12, 8.6 / 12, p) * (1 - smooth(9.6 / 12, SET, p));
    }
    /** Where it rises and where it sets: low in the north, a little to either side, fixed by the event seed. */
    public static Vec3 riseDir(long seed) {
        double az = Math.toRadians(-90 + (Math.floorMod(seed >>> 7, 50L) - 25));
        return horizon(az, -.05);
    }
    public static Vec3 setDir(long seed) {
        double az = Math.toRadians(-90 + (Math.floorMod(seed >>> 7, 50L) - 25) + 38);
        return horizon(az, -.07);
    }
    /** Azimuth measured from +x toward +z (north is -z, so -90 degrees). */
    static Vec3 horizon(double az, double elev) { return new Vec3(Math.cos(az) * Math.cos(elev), Math.sin(elev), Math.sin(az) * Math.cos(elev)); }
    /** The vanilla sun's direction at a time of day (0..1 as Level.getTimeOfDay gives it). */
    public static Vec3 sunDir(double timeOfDay) { double a = timeOfDay * Math.PI * 2; return new Vec3(-Math.sin(a), Math.cos(a), 0); }
    /** The pale disc the black sun covers: the sun, or the moon if that is what will be up at totality. */
    public static Vec3 whiteDir(double timeOfDay, boolean moon) { Vec3 s = sunDir(timeOfDay); return moon ? s.scale(-1) : s; }
    /** Where the black sun is: from the north horizon along a great circle to the pale disc, holding it, then back down. */
    public static Vec3 blackDir(long seed, Vec3 white, double p) {
        Vec3 rise = riseDir(seed);
        if (p < TOTAL1) return slerp(rise, white, approach(p));
        return slerp(white, setDir(seed), withdraw(p));
    }
    public static Vec3 slerp(Vec3 a, Vec3 b, double t) {
        double dot = Math.max(-1, Math.min(1, a.dot(b)));
        double th = Math.acos(dot);
        if (th < 1e-5) return a;
        if (Math.PI - th < 1e-3) { // opposite: go round through the zenith
            Vec3 up = new Vec3(0, 1, 0);
            return t < .5 ? slerp(a, up, t * 2) : slerp(up, b, t * 2 - 1);
        }
        double s = Math.sin(th);
        return a.scale(Math.sin((1 - t) * th) / s).add(b.scale(Math.sin(t * th) / s)).normalize();
    }
    /** 0..1 how much of the pale disc is covered, from the two discs' separation (area of overlap). */
    public static double coverage(Vec3 black, Vec3 white) {
        double d = Math.acos(Math.max(-1, Math.min(1, black.dot(white)))), R = BLACK_RADIUS, r = WHITE_RADIUS;
        if (d >= R + r) return 0;
        if (d <= R - r) return 1;
        double a = (d * d + r * r - R * R) / (2 * d * r), b = (d * d + R * R - r * r) / (2 * d * R);
        double area = r * r * Math.acos(clampS(a)) + R * R * Math.acos(clampS(b)) - .5 * Math.sqrt(Math.max(0, (-d + r + R) * (d + r - R) * (d - r + R) * (d + r + R)));
        return clamp(area / (Math.PI * r * r));
    }
    private static double clampS(double x) { return Math.max(-1, Math.min(1, x)); }

    // ------------------------------------------------------------------ the late thunder
    /** A straight white line stands on the horizon; the thunder comes this long after it, wherever you are. */
    public static final double LINE = 8.72 / 12;
    public static final int LINE_HOLD = 110, LINE_FADE = 70, THUNDER_DELAY = 34 * 20;
    public static final double LINE_DISTANCE = 380, LINE_HEIGHT = 430;
    public static int lineTick(int duration) { return (int) Math.round(LINE * duration); }
    public static int thunderTick(int duration) { return lineTick(duration) + THUNDER_DELAY; }
    /** Horizontal direction from the event centre toward the line, fixed by the seed. */
    public static Vec3 lineDir(long seed) { double az = Math.floorMod(seed >>> 17, 360L) * Math.PI / 180; return new Vec3(Math.cos(az), 0, Math.sin(az)); }
    /** 0..1 how bright the line is at this age: on at once, held quite still, then fading. */
    public static double line(int age, int duration) {
        int t = age - lineTick(duration);
        if (t < 0 || t > LINE_HOLD + LINE_FADE) return 0;
        return t <= LINE_HOLD ? 1 : 1 - smooth(0, 1, (t - LINE_HOLD) / (double) LINE_FADE);
    }
    /** The dark afterimage it leaves where it stood, as a bright thing does in the eye. */
    public static double afterimage(int age, int duration) {
        int t = age - lineTick(duration) - LINE_HOLD;
        if (t < 0 || t > 260) return 0;
        return smooth(0, 60, t) * (1 - smooth(120, 260, t));
    }

    // ------------------------------------------------------------------ the visitors
    /** Knocks come once the world is silent; the bell hangs until late. The silent procession sets out while the black sun
     *  climbs, so that it is passing near you when the sun is covered: the whole file stops and looks up, and walks on. */
    public static final double PROCESSION_FROM = 5.2 / 12, PROCESSION_UNTIL = 8.6 / 12, KNOCK_FROM = 2.35 / 12, KNOCK_UNTIL = 6.6 / 12,
        BELL_FROM = 3.7 / 12, BELL_UNTIL = 10.3 / 12, FEATHER_AT = 2.5 / 12;
    public static final int ISLAND_TICKS = 2200, RING_EVERY = 150, RIPPLE_TICKS = 110;
    public static final double ISLAND_RANGE = 10, RIPPLE_SPEED = .36;
    /** 0..1 how the island of sound around a found bell swells and closes again. */
    public static double island(int sinceFound) {
        if (sinceFound < 0 || sinceFound > ISLAND_TICKS) return 0;
        return smooth(0, 60, sinceFound) * (1 - smooth(ISLAND_TICKS - 160, ISLAND_TICKS, sinceFound));
    }
    /** Ticks since the bell last rang, if it has been placed at {@code placedAt}; it rings every RING_EVERY ticks, not quite evenly. */
    public static int sinceRing(int age, int placedAt, long seed) {
        if (age < placedAt) return -1;
        int t = age - placedAt, k = t / RING_EVERY;
        int at = ringAt(k, seed);
        if (t >= at) return t - at;
        return k == 0 ? -1 : t - ringAt(k - 1, seed);
    }
    static int ringAt(int k, long seed) { return k * RING_EVERY + (int) Math.floorMod(seed + k * 7919L, 31L); }

    private HushTimeline() {}
}
