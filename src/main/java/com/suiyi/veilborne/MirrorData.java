package com.suiyi.veilborne;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Authoritative state of one 镜天巡礼. The quiet period between weathers lives in {@link TideData}. */
public final class MirrorData extends SavedData {
    /** One-shot encounter bits. */
    public static final int DOOR=1, REVEALED=2, PILGRIM=4, FRAMES=8, SHARDS=16, KEEPSAKE=32, SHARD_BURST_0=64, SHARD_BURST_1=128, FREESTANDING=256, VAULT=512;
    /** Which of the rotating encounters this weather draws: the door and the late traveller always come. */
    public static final int VARIANT_FRAMES=1, VARIANT_BEASTS=2;
    public boolean active;
    public long seed;
    public int age, duration=MirrorTimeline.DEFAULT_DURATION, radius=320, flags, variant=VARIANT_FRAMES|VARIANT_BEASTS;
    public int doorFace, doorAt, revealedAt, pilgrimAt, keepsakeAt;
    public float pilgrimYaw;
    public int[] shardWarn=new int[2];
    public BlockPos center=BlockPos.ZERO, door=BlockPos.ZERO, pilgrimOrigin=BlockPos.ZERO, keepsake=BlockPos.ZERO;
    public BlockPos[] shards={BlockPos.ZERO,BlockPos.ZERO};
    /** The tower's buried room, once the prism has found it (its chest). */
    public BlockPos vault=BlockPos.ZERO;
    /** For each shard that burst: which wave of beasts is out (0 none, 1, 2, 3 all beaten), and how many have been killed. */
    public int[] beastWave=new int[2], beastKills=new int[2];
    public static MirrorData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new Factory<>(MirrorData::new,MirrorData::load),"veilborne_mirror");
    }
    public double progress() { return MirrorTimeline.progress(age,duration); }
    public double strength(double x,double z) {
        return active ? MirrorTimeline.region(Math.hypot(x-center.getX(),z-center.getZ()),radius) : 0;
    }
    public boolean has(int bit) { return (flags&bit)!=0; }
    public boolean takeFlag(int bit) {
        if((flags&bit)!=0) return false;
        flags|=bit; setDirty(); return true;
    }
    public boolean hasDoor() { return !door.equals(BlockPos.ZERO); }
    public boolean revealed() { return has(REVEALED); }
    public void begin(BlockPos origin,long eventSeed,int ticks,int eventRadius,int variants) {
        active=true; center=origin.immutable(); seed=eventSeed; duration=ticks; radius=eventRadius; age=0; flags=0; variant=variants;
        door=BlockPos.ZERO; pilgrimOrigin=BlockPos.ZERO; keepsake=BlockPos.ZERO; shards=new BlockPos[]{BlockPos.ZERO,BlockPos.ZERO}; shardWarn=new int[2]; vault=BlockPos.ZERO; beastWave=new int[2]; beastKills=new int[2];
        doorFace=doorAt=revealedAt=pilgrimAt=keepsakeAt=0; pilgrimYaw=0; setDirty();
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        tag.putBoolean("Active",active);tag.putLong("Seed",seed);tag.putInt("Age",age);tag.putInt("Duration",duration);tag.putInt("Radius",radius);
        tag.putInt("Flags",flags);tag.putInt("Variant",variant);tag.putLong("Center",center.asLong());
        tag.putLong("Door",door.asLong());tag.putInt("DoorFace",doorFace);tag.putInt("DoorAt",doorAt);tag.putInt("RevealedAt",revealedAt);
        tag.putLong("PilgrimOrigin",pilgrimOrigin.asLong());tag.putFloat("PilgrimYaw",pilgrimYaw);tag.putInt("PilgrimAt",pilgrimAt);
        tag.putLong("Shard0",shards[0].asLong());tag.putLong("Shard1",shards[1].asLong());tag.putIntArray("ShardWarn",shardWarn);
        tag.putLong("Keepsake",keepsake.asLong());tag.putInt("KeepsakeAt",keepsakeAt);
        tag.putLong("Vault",vault.asLong());tag.putIntArray("BeastWave",beastWave);tag.putIntArray("BeastKills",beastKills);return tag;
    }
    public static MirrorData load(CompoundTag tag,HolderLookup.Provider registries) {
        MirrorData d=new MirrorData();d.active=tag.getBoolean("Active");d.seed=tag.getLong("Seed");
        d.duration=Math.max(1200,tag.getInt("Duration"));d.age=Math.max(0,Math.min(tag.getInt("Age"),d.duration));
        d.radius=Math.max(96,Math.min(1024,tag.getInt("Radius")));d.flags=tag.getInt("Flags");d.variant=tag.getInt("Variant");
        d.center=BlockPos.of(tag.getLong("Center"));d.door=BlockPos.of(tag.getLong("Door"));d.doorFace=tag.getInt("DoorFace")&3;
        d.doorAt=tag.getInt("DoorAt");d.revealedAt=tag.getInt("RevealedAt");
        d.pilgrimOrigin=BlockPos.of(tag.getLong("PilgrimOrigin"));d.pilgrimYaw=tag.getFloat("PilgrimYaw");d.pilgrimAt=tag.getInt("PilgrimAt");
        d.shards=new BlockPos[]{BlockPos.of(tag.getLong("Shard0")),BlockPos.of(tag.getLong("Shard1"))};
        int[] warn=tag.getIntArray("ShardWarn");d.shardWarn=warn.length==2?warn:new int[2];
        d.keepsake=BlockPos.of(tag.getLong("Keepsake"));d.keepsakeAt=tag.getInt("KeepsakeAt");
        d.vault=BlockPos.of(tag.getLong("Vault"));
        int[] wave=tag.getIntArray("BeastWave"),kills=tag.getIntArray("BeastKills");d.beastWave=wave.length==2?wave:new int[2];d.beastKills=kills.length==2?kills:new int[2];
        if(d.age>=d.duration)d.active=false;return d;
    }
}
