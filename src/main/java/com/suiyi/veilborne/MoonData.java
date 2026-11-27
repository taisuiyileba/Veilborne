package com.suiyi.veilborne;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Authoritative state of one 月骸花潮. The quiet period between weathers lives in {@link TideData}. */
public final class MoonData extends SavedData {
    public static final int PROCESSION=1, LITTLE_MOON=2, BROKEN=4, LANTERN=8, REMNANT=16;
    public boolean active;
    public long seed;
    public int age, duration=MoonTimeline.DEFAULT_DURATION, radius=320, flags, littleMoonAt, brokenAt, lanternAt;
    public BlockPos center=BlockPos.ZERO, littleMoon=BlockPos.ZERO, lantern=BlockPos.ZERO;
    public static MoonData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(MoonData::new,MoonData::load),"veilborne_moon");
    }
    public double progress() { return MoonTimeline.progress(age,duration); }
    public double strength(double x,double z) {
        return active ? MoonTimeline.region(Math.hypot(x-center.getX(),z-center.getZ()),radius) : 0;
    }
    public boolean broken() { return (flags&BROKEN)!=0; }
    /** 0..1: the true moon gliding back to its old place once the reflections are gone. */
    public static double merge(double progress,int age,int brokenAt,boolean broken) {
        double natural=TideTimeline.smooth(.745,.8,progress);
        return broken?Math.max(natural,TideTimeline.smooth(0,200,age-brokenAt)):natural;
    }
    public boolean takeFlag(int bit) {
        if((flags&bit)!=0) return false;
        flags|=bit; setDirty(); return true;
    }
    public void begin(BlockPos origin,long eventSeed,int ticks,int eventRadius) {
        active=true; center=origin.immutable(); seed=eventSeed; duration=ticks; radius=eventRadius; age=0; flags=0;
        littleMoon=BlockPos.ZERO; lantern=BlockPos.ZERO; littleMoonAt=brokenAt=lanternAt=0; setDirty();
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        tag.putBoolean("Active",active);tag.putLong("Seed",seed);tag.putInt("Age",age);
        tag.putInt("Duration",duration);tag.putInt("Radius",radius);tag.putInt("Flags",flags);
        tag.putLong("Center",center.asLong());tag.putLong("LittleMoon",littleMoon.asLong());tag.putLong("Lantern",lantern.asLong());
        tag.putInt("LittleMoonAt",littleMoonAt);tag.putInt("BrokenAt",brokenAt);tag.putInt("LanternAt",lanternAt);return tag;
    }
    public static MoonData load(CompoundTag tag,HolderLookup.Provider registries) {
        MoonData d=new MoonData();d.active=tag.getBoolean("Active");d.seed=tag.getLong("Seed");
        d.duration=Math.max(1200,tag.getInt("Duration"));d.age=Math.max(0,Math.min(tag.getInt("Age"),d.duration));
        d.radius=Math.max(96,Math.min(1024,tag.getInt("Radius")));d.flags=tag.getInt("Flags");
        d.center=BlockPos.of(tag.getLong("Center"));d.littleMoon=BlockPos.of(tag.getLong("LittleMoon"));d.lantern=BlockPos.of(tag.getLong("Lantern"));
        d.littleMoonAt=tag.getInt("LittleMoonAt");d.brokenAt=tag.getInt("BrokenAt");d.lanternAt=tag.getInt("LanternAt");
        if(d.age>=d.duration)d.active=false;return d;
    }
}
