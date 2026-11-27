package com.suiyi.veilborne;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Authoritative state of one 无声白昼. The quiet period between weathers lives in {@link TideData}. */
public final class HushData extends SavedData {
    /** One-shot encounter bits. */
    public static final int PROCESSION=1, COAT=2, BELL=4, FOUND=8, FEATHER=16, LANDED=32, BELL_DROPPED=64, THUNDER=128,
        LISTENER=256, LISTENER_UP=512, LISTENER_GONE=1024;
    /** Which rotating encounters this weather draws. The black sun, the late thunder and the feather always come. */
    public static final int VARIANT_PROCESSION=1, VARIANT_KNOCK=2, VARIANT_BELL=4, ALL=7;
    public boolean active;
    /** The pale disc the black sun covers is the moon: it is the one that will be up at totality. */
    public boolean moon;
    public long seed;
    public int age, duration=HushTimeline.DEFAULT_DURATION, radius=320, flags, variant=ALL;
    public int bellAt, foundAt, featherAt, listenerAt;
    /** Who made a sound in the silence, if anyone did (see {@link HushSilence}). */
    public java.util.UUID listener;
    public BlockPos center=BlockPos.ZERO, bell=BlockPos.ZERO, bellTop=BlockPos.ZERO, feather=BlockPos.ZERO;
    public static HushData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new Factory<>(HushData::new,HushData::load),"veilborne_hush");
    }
    public double progress() { return HushTimeline.progress(age,duration); }
    public double strength(double x,double z) {
        return active ? HushTimeline.region(Math.hypot(x-center.getX(),z-center.getZ()),radius) : 0;
    }
    public boolean has(int bit) { return (flags&bit)!=0; }
    public boolean takeFlag(int bit) {
        if((flags&bit)!=0) return false;
        flags|=bit; setDirty(); return true;
    }
    public boolean hasBell() { return has(BELL)&&!bell.equals(BlockPos.ZERO); }
    public boolean found() { return has(FOUND); }
    public void begin(BlockPos origin,long eventSeed,int ticks,int eventRadius,int variants,boolean moonTarget) {
        active=true; center=origin.immutable(); seed=eventSeed; duration=ticks; radius=eventRadius; age=0; flags=0; variant=variants; moon=moonTarget;
        bell=bellTop=feather=BlockPos.ZERO; bellAt=foundAt=featherAt=listenerAt=0; listener=null; setDirty();
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        tag.putBoolean("Active",active);tag.putBoolean("Moon",moon);tag.putLong("Seed",seed);tag.putInt("Age",age);tag.putInt("Duration",duration);
        tag.putInt("Radius",radius);tag.putInt("Flags",flags);tag.putInt("Variant",variant);tag.putLong("Center",center.asLong());
        tag.putLong("Bell",bell.asLong());tag.putLong("BellTop",bellTop.asLong());tag.putInt("BellAt",bellAt);tag.putInt("FoundAt",foundAt);
        tag.putLong("Feather",feather.asLong());tag.putInt("FeatherAt",featherAt);
        tag.putInt("ListenerAt",listenerAt);if(listener!=null)tag.putUUID("Listener",listener);return tag;
    }
    public static HushData load(CompoundTag tag,HolderLookup.Provider registries) {
        HushData d=new HushData();d.active=tag.getBoolean("Active");d.moon=tag.getBoolean("Moon");d.seed=tag.getLong("Seed");
        d.duration=Math.max(1200,tag.getInt("Duration"));d.age=Math.max(0,Math.min(tag.getInt("Age"),d.duration));
        d.radius=Math.max(96,Math.min(1024,tag.getInt("Radius")));d.flags=tag.getInt("Flags");d.variant=tag.getInt("Variant");
        d.center=BlockPos.of(tag.getLong("Center"));d.bell=BlockPos.of(tag.getLong("Bell"));d.bellTop=BlockPos.of(tag.getLong("BellTop"));
        d.bellAt=tag.getInt("BellAt");d.foundAt=tag.getInt("FoundAt");d.feather=BlockPos.of(tag.getLong("Feather"));d.featherAt=tag.getInt("FeatherAt");
        d.listenerAt=tag.getInt("ListenerAt");d.listener=tag.hasUUID("Listener")?tag.getUUID("Listener"):null;
        if(d.age>=d.duration)d.active=false;return d;
    }
}
