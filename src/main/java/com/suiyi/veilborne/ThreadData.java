package com.suiyi.veilborne;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Authoritative state of one 绯线缝世. The quiet period between weathers lives in {@link TideData}. */
public final class ThreadData extends SavedData {
    /** One-shot encounter bits. */
    public static final int WALKER=1, LAND=2, POCKET=4, TUNED=8, SEWN_UP=16, KEEPSAKE=32, KNOT_UNDONE=64, SNIPPED=128, PAIRED=256;
    /** Which of the rotating encounters this weather draws. The strikes and the loose thread ends always come. */
    public static final int VARIANT_WALKER=1, VARIANT_POCKET=2;
    public static final int STRIKES=ThreadTimeline.STRIKE_STITCH.length;
    public boolean active;
    public long seed;
    public int age, duration=ThreadTimeline.DEFAULT_DURATION, radius=320, flags, variant=VARIANT_WALKER|VARIANT_POCKET;
    public int landAt, pocketAt, tuneAt, keepsakeAt;
    public BlockPos center=BlockPos.ZERO, landA=BlockPos.ZERO, landB=BlockPos.ZERO, pocket=BlockPos.ZERO, anchor=BlockPos.ZERO, keepsake=BlockPos.ZERO;
    /** Where each of the three strikes falls (ZERO until chosen, as its warning begins). */
    public BlockPos[] strikes=blank();
    private static BlockPos[] blank(){BlockPos[] b=new BlockPos[STRIKES];java.util.Arrays.fill(b,BlockPos.ZERO);return b;}
    public static ThreadData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new Factory<>(ThreadData::new,ThreadData::load),"veilborne_thread");
    }
    public double progress() { return ThreadTimeline.progress(age,duration); }
    public double strength(double x,double z) {
        return active ? ThreadTimeline.region(Math.hypot(x-center.getX(),z-center.getZ()),radius) : 0;
    }
    public ThreadTimeline.Seam seam() { return ThreadTimeline.seam(center,seed); }
    public boolean has(int bit) { return (flags&bit)!=0; }
    public boolean takeFlag(int bit) {
        if((flags&bit)!=0) return false;
        flags|=bit; setDirty(); return true;
    }
    public boolean hasLand() { return has(LAND)&&!landA.equals(BlockPos.ZERO); }
    public boolean hasPocket() { return has(POCKET)&&!pocket.equals(BlockPos.ZERO); }
    public void begin(BlockPos origin,long eventSeed,int ticks,int eventRadius,int variants) {
        active=true; center=origin.immutable(); seed=eventSeed; duration=ticks; radius=eventRadius; age=0; flags=0; variant=variants;
        landA=landB=pocket=anchor=keepsake=BlockPos.ZERO; strikes=blank(); landAt=pocketAt=tuneAt=keepsakeAt=0; setDirty();
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        tag.putBoolean("Active",active);tag.putLong("Seed",seed);tag.putInt("Age",age);tag.putInt("Duration",duration);tag.putInt("Radius",radius);
        tag.putInt("Flags",flags);tag.putInt("Variant",variant);tag.putLong("Center",center.asLong());
        long[] s=new long[STRIKES];for(int i=0;i<STRIKES;i++)s[i]=strikes[i].asLong();tag.putLongArray("Strikes",s);
        tag.putLong("LandA",landA.asLong());tag.putLong("LandB",landB.asLong());tag.putInt("LandAt",landAt);
        tag.putLong("Pocket",pocket.asLong());tag.putInt("PocketAt",pocketAt);tag.putLong("Anchor",anchor.asLong());tag.putInt("TuneAt",tuneAt);
        tag.putLong("Keepsake",keepsake.asLong());tag.putInt("KeepsakeAt",keepsakeAt);return tag;
    }
    public static ThreadData load(CompoundTag tag,HolderLookup.Provider registries) {
        ThreadData d=new ThreadData();d.active=tag.getBoolean("Active");d.seed=tag.getLong("Seed");
        d.duration=Math.max(1200,tag.getInt("Duration"));d.age=Math.max(0,Math.min(tag.getInt("Age"),d.duration));
        d.radius=Math.max(96,Math.min(1024,tag.getInt("Radius")));d.flags=tag.getInt("Flags");d.variant=tag.getInt("Variant");
        d.center=BlockPos.of(tag.getLong("Center"));
        long[] s=tag.getLongArray("Strikes");for(int i=0;i<STRIKES;i++)d.strikes[i]=i<s.length?BlockPos.of(s[i]):BlockPos.ZERO;
        d.landA=BlockPos.of(tag.getLong("LandA"));d.landB=BlockPos.of(tag.getLong("LandB"));d.landAt=tag.getInt("LandAt");
        d.pocket=BlockPos.of(tag.getLong("Pocket"));d.pocketAt=tag.getInt("PocketAt");d.anchor=BlockPos.of(tag.getLong("Anchor"));d.tuneAt=tag.getInt("TuneAt");
        d.keepsake=BlockPos.of(tag.getLong("Keepsake"));d.keepsakeAt=tag.getInt("KeepsakeAt");
        if(d.age>=d.duration)d.active=false;return d;
    }
}
