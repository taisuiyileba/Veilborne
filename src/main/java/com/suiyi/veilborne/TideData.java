package com.suiyi.veilborne;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class TideData extends SavedData {
    /** A star-tide pool the whale's landing left: fishing within reach of it until {@code until} brings up things of the sea above. */
    public record Pool(BlockPos at,long until){}
    public static final int POOLS=6;
    /** Pools outlive the weather that made them, so they are never cleared when one begins. */
    public final List<Pool> pools=new ArrayList<>();
    public boolean active;
    public long seed;
    public int age, duration=TideTimeline.DEFAULT_DURATION, radius=320, flags, vortexAt, landingAt;
    public long quietTicks=24000;
    public BlockPos center=BlockPos.ZERO, vortex=BlockPos.ZERO, landing=BlockPos.ZERO;
    public static TideData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(TideData::new,TideData::load),"veilborne_tide");
    }
    public double progress() { return TideTimeline.progress(age,duration); }
    public double strength(double x,double z) {
        return active ? TideTimeline.region(Math.hypot(x-center.getX(),z-center.getZ()),radius) : 0;
    }
    public boolean takeFlag(int bit) {
        if((flags&bit)!=0) return false;
        flags|=bit; setDirty(); return true;
    }
    public void begin(BlockPos origin,long eventSeed,int ticks,int eventRadius) {
        active=true; center=origin.immutable(); seed=eventSeed; duration=ticks; radius=eventRadius; age=0;flags=0;
        vortex=BlockPos.ZERO; landing=BlockPos.ZERO;vortexAt=landingAt=0; setDirty();
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        tag.putBoolean("Active",active);tag.putLong("Seed",seed);tag.putInt("Age",age);
        tag.putInt("Duration",duration);tag.putInt("Radius",radius);tag.putInt("Flags",flags);
        tag.putLong("QuietTicks",quietTicks);tag.putLong("Center",center.asLong());
        tag.putLong("Vortex",vortex.asLong());tag.putLong("Landing",landing.asLong());
        tag.putInt("VortexAt",vortexAt);tag.putInt("LandingAt",landingAt);
        tag.putLongArray("PoolAt",pools.stream().mapToLong(q->q.at().asLong()).toArray());tag.putLongArray("PoolUntil",pools.stream().mapToLong(Pool::until).toArray());return tag;
    }
    public static TideData load(CompoundTag tag,HolderLookup.Provider registries) {
        TideData d=new TideData();d.active=tag.getBoolean("Active");d.seed=tag.getLong("Seed");
        d.duration=Math.max(1200,tag.getInt("Duration"));d.age=Math.max(0,Math.min(tag.getInt("Age"),d.duration));
        d.radius=Math.max(96,Math.min(1024,tag.getInt("Radius")));d.flags=tag.getInt("Flags");
        d.quietTicks=Math.max(0,tag.getLong("QuietTicks"));d.center=BlockPos.of(tag.getLong("Center"));
        d.vortex=BlockPos.of(tag.getLong("Vortex"));d.landing=BlockPos.of(tag.getLong("Landing"));
        d.vortexAt=tag.getInt("VortexAt");d.landingAt=tag.getInt("LandingAt");
        long[] at=tag.getLongArray("PoolAt"),until=tag.getLongArray("PoolUntil");
        for(int i=0;i<Math.min(at.length,until.length)&&d.pools.size()<POOLS;i++)d.pools.add(new Pool(BlockPos.of(at[i]),until[i]));
        if(d.age>=d.duration)d.active=false;return d;
    }
}
