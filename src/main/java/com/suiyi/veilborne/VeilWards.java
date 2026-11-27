package com.suiyi.veilborne;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Where, in one dimension, the lasting keepsakes stand that act on the world around them: silver flower lanterns (no monster
 * comes out of the dark near one), still candles (the world near one cannot be heard) and the hearts of dream trees (things
 * grow faster near one). The blocks enter themselves when placed and leave when removed; a random tick also re-enters them,
 * so a keepsake placed before this list existed finds its way in on its own. There are only ever a few, so asking what is
 * near a point is a walk over the list.
 */
public final class VeilWards extends SavedData {
    public static final int HAVEN=0, HUSH=1, HEART=2;
    private static final String[] KEYS={"Haven","Hush","Heart"};
    private final LongOpenHashSet[] sets={new LongOpenHashSet(),new LongOpenHashSet(),new LongOpenHashSet()};
    public static VeilWards get(ServerLevel level){
        return level.getDataStorage().computeIfAbsent(new Factory<>(VeilWards::new,VeilWards::load),"veilborne_wards");
    }
    public static void add(Level level,int kind,BlockPos pos){
        if(level instanceof ServerLevel s){VeilWards w=get(s);if(w.sets[kind].add(pos.asLong()))w.setDirty();}
    }
    public static void remove(Level level,int kind,BlockPos pos){
        if(level instanceof ServerLevel s){VeilWards w=get(s);if(w.sets[kind].remove(pos.asLong()))w.setDirty();}
    }
    public static boolean near(ServerLevel level,int kind,Vec3 at,double range){
        VeilWards w=get(level);
        if(w.sets[kind].isEmpty())return false;
        double r2=range*range;
        for(long l:w.sets[kind])if(BlockPos.of(l).distToCenterSqr(at)<=r2)return true;
        return false;
    }
    public static List<BlockPos> within(ServerLevel level,int kind,Vec3 at,double range){
        VeilWards w=get(level);List<BlockPos> out=new ArrayList<>();
        double r2=range*range;
        for(long l:w.sets[kind])if(BlockPos.of(l).distToCenterSqr(at)<=r2)out.add(BlockPos.of(l));
        return out;
    }
    public static int size(ServerLevel level,int kind){return get(level).sets[kind].size();}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        // Sorted, so the same places always save the same way whatever order the set happens to hold them in.
        for(int k=0;k<KEYS.length;k++){long[] a=sets[k].toLongArray();java.util.Arrays.sort(a);tag.putLongArray(KEYS[k],a);}
        return tag;
    }
    public static VeilWards load(CompoundTag tag,HolderLookup.Provider registries){
        VeilWards w=new VeilWards();
        for(int k=0;k<KEYS.length;k++)for(long l:tag.getLongArray(KEYS[k]))w.sets[k].add(l);
        return w;
    }
}
