package com.suiyi.veilborne;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/** Authoritative state of one 苍翠梦潮. The quiet period between weathers lives in {@link TideData}. */
public final class DreamData extends SavedData {
    /** One-shot encounter bits. */
    public static final int TREE=1, PATH=2, ROUTE=4, GLANCE=8, GLANCE_PLAYER=16, GIFT_DROPPED=32, WANDER=64, ENCLOSURE=128, ENCLOSURE_WON=256,
        ENCLOSURE_LOST=512, LOST=1024, TWIG=2048, TWIG_DROPPED=4096;
    /** Which rotating encounters this weather draws. The dream tree, the deer, the seedfall and the twig always come. */
    public static final int VARIANT_PATH=1, VARIANT_ENCLOSURE=2, VARIANT_LOST=4, ALL=7;
    public boolean active;
    public long seed;
    public int age, duration=DreamTimeline.DEFAULT_DURATION, radius=320, flags, variant=ALL;
    public BlockPos center=BlockPos.ZERO, tree=BlockPos.ZERO, glance=BlockPos.ZERO, twig=BlockPos.ZERO, enclosure=BlockPos.ZERO, route=BlockPos.ZERO;
    public int treeAt, pathAt, routeAt, routeSide=1, glanceAt, giftAt, twigAt, enclosureAt, lightStep, lightAt, closedAt;
    public float routeYaw, enclosureExit;
    public final List<BlockPos> path=new ArrayList<>();
    public static DreamData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new Factory<>(DreamData::new,DreamData::load),"veilborne_dream");
    }
    public double progress() { return DreamTimeline.progress(age,duration); }
    public double strength(double x,double z) {
        return active ? DreamTimeline.region(Math.hypot(x-center.getX(),z-center.getZ()),radius) : 0;
    }
    public boolean has(int bit) { return (flags&bit)!=0; }
    public boolean takeFlag(int bit) {
        if((flags&bit)!=0) return false;
        flags|=bit; setDirty(); return true;
    }
    public boolean hasTree() { return has(TREE)&&!tree.equals(BlockPos.ZERO); }
    public boolean hasRoute() { return has(ROUTE)&&!route.equals(BlockPos.ZERO); }
    public DreamTimeline.Route route() { return new DreamTimeline.Route(Vec3.atBottomCenterOf(route),routeYaw,routeSide); }
    public boolean enclosureOpen() { return has(ENCLOSURE)&&!has(ENCLOSURE_WON)&&!has(ENCLOSURE_LOST); }
    public void begin(BlockPos origin,long eventSeed,int ticks,int eventRadius,int variants) {
        active=true; center=origin.immutable(); seed=eventSeed; duration=ticks; radius=eventRadius; age=0; flags=0; variant=variants;
        tree=glance=twig=enclosure=route=BlockPos.ZERO; path.clear();
        treeAt=pathAt=routeAt=glanceAt=giftAt=twigAt=enclosureAt=lightStep=lightAt=closedAt=0; routeYaw=0; routeSide=1; enclosureExit=0;
        setDirty();
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        tag.putBoolean("Active",active);tag.putLong("Seed",seed);tag.putInt("Age",age);tag.putInt("Duration",duration);tag.putInt("Radius",radius);
        tag.putInt("Flags",flags);tag.putInt("Variant",variant);tag.putLong("Center",center.asLong());
        tag.putLong("Tree",tree.asLong());tag.putInt("TreeAt",treeAt);tag.putInt("PathAt",pathAt);
        tag.put("Path",new LongArrayTag(path.stream().mapToLong(BlockPos::asLong).toArray()));
        tag.putLong("Route",route.asLong());tag.putFloat("RouteYaw",routeYaw);tag.putInt("RouteSide",routeSide);tag.putInt("RouteAt",routeAt);
        tag.putLong("Glance",glance.asLong());tag.putInt("GlanceAt",glanceAt);tag.putInt("GiftAt",giftAt);
        tag.putLong("Enclosure",enclosure.asLong());tag.putInt("EnclosureAt",enclosureAt);tag.putFloat("EnclosureExit",enclosureExit);
        tag.putInt("LightStep",lightStep);tag.putInt("LightAt",lightAt);tag.putInt("ClosedAt",closedAt);
        tag.putLong("Twig",twig.asLong());tag.putInt("TwigAt",twigAt);return tag;
    }
    public static DreamData load(CompoundTag tag,HolderLookup.Provider registries) {
        DreamData d=new DreamData();d.active=tag.getBoolean("Active");d.seed=tag.getLong("Seed");
        d.duration=Math.max(1200,tag.getInt("Duration"));d.age=Math.max(0,Math.min(tag.getInt("Age"),d.duration));
        d.radius=Math.max(96,Math.min(1024,tag.getInt("Radius")));d.flags=tag.getInt("Flags");d.variant=tag.getInt("Variant");
        d.center=BlockPos.of(tag.getLong("Center"));d.tree=BlockPos.of(tag.getLong("Tree"));d.treeAt=tag.getInt("TreeAt");d.pathAt=tag.getInt("PathAt");
        for(long l:tag.getLongArray("Path"))if(d.path.size()<DreamTimeline.PATH_POINTS)d.path.add(BlockPos.of(l));
        d.route=BlockPos.of(tag.getLong("Route"));d.routeYaw=tag.getFloat("RouteYaw");d.routeSide=tag.getInt("RouteSide")<0?-1:1;d.routeAt=tag.getInt("RouteAt");
        d.glance=BlockPos.of(tag.getLong("Glance"));d.glanceAt=tag.getInt("GlanceAt");d.giftAt=tag.getInt("GiftAt");
        d.enclosure=BlockPos.of(tag.getLong("Enclosure"));d.enclosureAt=tag.getInt("EnclosureAt");d.enclosureExit=tag.getFloat("EnclosureExit");
        d.lightStep=Math.max(0,Math.min(DreamTimeline.LIGHTS,tag.getInt("LightStep")));d.lightAt=tag.getInt("LightAt");d.closedAt=tag.getInt("ClosedAt");
        d.twig=BlockPos.of(tag.getLong("Twig"));d.twigAt=tag.getInt("TwigAt");
        if(d.age>=d.duration)d.active=false;return d;
    }
}
