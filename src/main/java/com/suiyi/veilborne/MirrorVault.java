package com.suiyi.veilborne;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

/**
 * The watchtower that is only in the mirror stands over something that is really there: held in the prism, its shadow runs
 * on down into the ground, and a buried room of old stone is under its footprint, with a chest in it. The tower's column is
 * worked out the same way on both sides, so the room lies under the tower everyone sees. The room is only ever cut out of
 * natural ground; if something built is in the way it goes deeper, and failing that it is only a chest.
 *
 * Also here: what the cracked mirror's heart shows, the containers and spawners hidden round where it is broken.
 */
public final class MirrorVault {
    public static final int DEPTH=3, LOOK=24, TRIES=3, TOWER_DISTANCE=36;
    public static final double HEART_RANGE=32;
    public static final int HEART_TICKS=600;
    public static final ResourceKey<LootTable> LOOT=ResourceKey.create(Registries.LOOT_TABLE,Veilborne.id("chests/tower_vault"));

    /** x and z of the tower's middle column. */
    public static int[] column(BlockPos center,long seed){
        double a=Math.floorMod(seed>>>17,360L)*Math.PI/180;
        return new int[]{center.getX()+(int)(Math.cos(a)*TOWER_DISTANCE),center.getZ()+(int)(Math.sin(a)*TOWER_DISTANCE)};
    }
    /** Where the tower stands on the ground (its lowest corner, at most six below the highest), or null while the ground is not loaded. */
    public static BlockPos base(Level level,BlockPos center,long seed){
        int[] c=column(center,seed);
        if(!level.hasChunk(c[0]>>4,c[1]>>4))return null;
        int ground=Integer.MAX_VALUE,highest=Integer.MIN_VALUE;
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){int y=Surface.top(level,c[0]+x,c[1]+z);ground=Math.min(ground,y);highest=Math.max(highest,y);}
        if(highest-ground>6)ground=highest-6;
        return new BlockPos(c[0],ground,c[1]);
    }
    /** While the reflections show the tower. */
    public static boolean visible(double p){return MirrorTimeline.reflection(p)>.5&&p<.9;}
    /** Ground the room may be cut out of: rock, soil, sand, ores and the air of a cave. Never anything built or placed. */
    public static boolean natural(BlockState s){
        return s.isAir()||s.is(BlockTags.BASE_STONE_OVERWORLD)||s.is(BlockTags.DIRT)||s.is(BlockTags.SAND)||s.is(Blocks.GRAVEL)||s.is(Blocks.CLAY)||s.is(BlockTags.TERRACOTTA)
            ||s.is(Blocks.SANDSTONE)||s.is(Blocks.RED_SANDSTONE)||s.is(Blocks.CALCITE)||s.is(Blocks.DRIPSTONE_BLOCK)||s.is(Blocks.SNOW_BLOCK)||s.is(Blocks.POWDER_SNOW)
            ||s.is(BlockTags.COAL_ORES)||s.is(BlockTags.IRON_ORES)||s.is(BlockTags.COPPER_ORES)||s.is(BlockTags.GOLD_ORES)||s.is(BlockTags.REDSTONE_ORES)||s.is(BlockTags.LAPIS_ORES);
    }
    /**
     * Builds the room under {@code base} and fills its chest from the tower vault's loot table. Returns where the chest is, or
     * null if not even a chest could be put in natural ground.
     */
    public static BlockPos build(ServerLevel level,BlockPos base,long seed){
        for(int t=0;t<TRIES;t++){
            int floor=base.getY()-DEPTH-5-t*4;
            if(floor-1<=level.getMinBuildHeight())break;
            if(!clear(level,base,floor))continue;
            return room(level,base,floor,seed);
        }
        BlockPos chest=base.below(DEPTH);
        if(!natural(level.getBlockState(chest))||!level.getBlockState(chest).getFluidState().isEmpty())return null;
        return chest(level,chest,Direction.SOUTH,seed);
    }
    private static boolean clear(ServerLevel level,BlockPos base,int floor){
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=floor;y<=floor+4;y++){
            BlockState s=level.getBlockState(new BlockPos(base.getX()+x,y,base.getZ()+z));
            if(!natural(s)||!s.getFluidState().isEmpty())return false;
        }
        return true;
    }
    private static BlockPos room(ServerLevel level,BlockPos base,int floor,long seed){
        BlockState brick=Blocks.STONE_BRICKS.defaultBlockState(),mossy=Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),cracked=Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        BlockState chiselled=Blocks.CHISELED_STONE_BRICKS.defaultBlockState(),slate=Blocks.POLISHED_DEEPSLATE.defaultBlockState(),air=Blocks.AIR.defaultBlockState();
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=0;y<=4;y++){
            BlockPos p=new BlockPos(base.getX()+x,floor+y,base.getZ()+z);
            boolean shell=Math.abs(x)==2||Math.abs(z)==2||y==0||y==4;
            long h=(p.asLong()*0x9E3779B97F4A7C15L)^seed;
            BlockState s=!shell?air:y==0?slate:Math.abs(x)==2&&Math.abs(z)==2?chiselled:(h&7)==0?mossy:(h>>>3&7)==1?cracked:brick;
            level.setBlock(p,s,2);
        }
        // A pale pedestal in the middle with a cluster of amethyst on it, the way a mirror stood in the tower.
        BlockPos mid=new BlockPos(base.getX(),floor+1,base.getZ());
        level.setBlock(mid,Blocks.CALCITE.defaultBlockState(),2);
        level.setBlock(mid.above(),Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING,Direction.UP),2);
        level.setBlock(mid.offset(-1,0,1),Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,false),2);
        level.setBlock(mid.offset(1,0,1),Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,false),2);
        return chest(level,mid.offset(0,0,-1),Direction.SOUTH,seed);
    }
    private static BlockPos chest(ServerLevel level,BlockPos at,Direction facing,long seed){
        level.setBlock(at,Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,facing),2);
        if(level.getBlockEntity(at) instanceof RandomizableContainerBlockEntity c)c.setLootTable(LOOT,seed^at.asLong());
        return at;
    }

    // ------------------------------------------------------------------ the heart of the cracked mirror
    /** Chests, barrels, shulker boxes, spawners, vaults and brushable blocks within {@code range}, nearest first. */
    public static List<BlockPos> hidden(ServerLevel level,Vec3 at,double range){
        List<BlockPos> out=new ArrayList<>();
        int r=(int)Math.ceil(range/16)+1,cx=(int)Math.floor(at.x)>>4,cz=(int)Math.floor(at.z)>>4;
        for(int x=cx-r;x<=cx+r;x++)for(int z=cz-r;z<=cz+r;z++){
            LevelChunk chunk=level.getChunkSource().getChunkNow(x,z);
            if(chunk==null)continue;
            for(BlockEntity be:chunk.getBlockEntities().values()){
                boolean wanted=be instanceof ChestBlockEntity||be instanceof BarrelBlockEntity||be instanceof ShulkerBoxBlockEntity||be instanceof SpawnerBlockEntity
                    ||be instanceof TrialSpawnerBlockEntity||be instanceof net.minecraft.world.level.block.entity.vault.VaultBlockEntity||be instanceof BrushableBlockEntity||be instanceof DecoratedPotBlockEntity;
                if(wanted&&be.getBlockPos().distToCenterSqr(at)<=range*range)out.add(be.getBlockPos().immutable());
            }
        }
        out.sort(java.util.Comparator.comparingDouble(b->b.distToCenterSqr(at)));
        return out.size()>VeilCue.MAX_SPOTS?out.subList(0,VeilCue.MAX_SPOTS):out;
    }
    private MirrorVault(){}
}
