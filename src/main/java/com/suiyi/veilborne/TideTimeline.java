package com.suiyi.veilborne;

/** Shared deterministic choreography. No client clock or day-time dependency. */
public final class TideTimeline {
    public static final int DEFAULT_DURATION = 14 * 60 * 20;
    public enum Phase { OMEN, OPENING, FLOOD, WHALE, AFTERGLOW, EBB, QUIET }
    public static double clamp(double x) { return Math.max(0, Math.min(1, x)); }
    public static double smooth(double a, double b, double x) {
        double t = clamp((x-a)/(b-a)); return t*t*(3-2*t);
    }
    public static double progress(double age, int duration) { return clamp(age/Math.max(1,duration)); }
    public static Phase phase(double p) {
        if (p < 1.0/14) return Phase.OMEN;
        if (p < 3.0/14) return Phase.OPENING;
        if (p < 8.0/14) return Phase.FLOOD;
        if (p < 10.0/14) return Phase.WHALE;
        if (p < 12.0/14) return Phase.AFTERGLOW;
        if (p < 1) return Phase.EBB;
        return Phase.QUIET;
    }
    public static double sea(double p) { return smooth(.025,.23,p) * (1-smooth(.85,1,p)); }
    public static double whale(double p) { return smooth(.53,.59,p) * (1-smooth(.76,.84,p)); }
    public static double region(double distance, double radius) { return 1-smooth(radius*.72,radius,distance); }
    private TideTimeline() {}
}
