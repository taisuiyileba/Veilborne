package com.suiyi.veilborne;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The blocks of 无声白昼 (the silent lantern is a vanilla lantern block with its own look). The still candle is made of
 * daylight wax: its white flame never moves, and near it the world is a little quieter and a little paler, as if a piece
 * of that day had stayed. The feather under glass is the one that hung in mid-fall all through the weather and landed when
 * the first bird sang; under its cloche it hangs quite still, and now and then something chirps. Nothing near a still candle
 * can be heard by the sculk, and whoever holds a silent lantern cannot be heard at all (see {@link HushSilence}).
 */
public final class HushBlocks {
    public static class Candle extends Block {
        public static final MapCodec<Candle> CODEC=simpleCodec(Candle::new);
        private static final VoxelShape SHAPE=Block.box(6,0,6,10,9,10);
        public Candle(Properties p){super(p);}
        @Override protected MapCodec<? extends Block> codec(){return CODEC;}
        @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SHAPE;}
        @Override protected boolean canSurvive(BlockState s,LevelReader l,BlockPos p){return Block.canSupportCenter(l,p.below(),Direction.UP);}
        @Override protected BlockState updateShape(BlockState s,Direction d,BlockState n,LevelAccessor l,BlockPos p,BlockPos np){
            return d==Direction.DOWN&&!canSurvive(s,l,p)?Blocks.AIR.defaultBlockState():super.updateShape(s,d,n,l,p,np);
        }
        // Near it, nothing can be heard (see HushSilence).
        @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){super.onPlace(s,l,p,old,moved);VeilWards.add(l,VeilWards.HUSH,p);}
        @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState now,boolean moved){
            if(!now.is(this))VeilWards.remove(l,VeilWards.HUSH,p);
            super.onRemove(s,l,p,now,moved);
        }
        @Override protected boolean isRandomlyTicking(BlockState s){return true;}
        @Override protected void randomTick(BlockState s,net.minecraft.server.level.ServerLevel l,BlockPos p,RandomSource r){VeilWards.add(l,VeilWards.HUSH,p);}
    }
    public static class Feather extends Block {
        public static final MapCodec<Feather> CODEC=simpleCodec(Feather::new);
        private static final VoxelShape SHAPE=Block.box(3,0,3,13,13,13);
        public Feather(Properties p){super(p);}
        @Override protected MapCodec<? extends Block> codec(){return CODEC;}
        @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SHAPE;}
        @Override protected boolean canSurvive(BlockState s,LevelReader l,BlockPos p){return Block.canSupportCenter(l,p.below(),Direction.UP);}
        @Override protected BlockState updateShape(BlockState s,Direction d,BlockState n,LevelAccessor l,BlockPos p,BlockPos np){
            return d==Direction.DOWN&&!canSurvive(s,l,p)?Blocks.AIR.defaultBlockState():super.updateShape(s,d,n,l,p,np);
        }
        @Override public void animateTick(BlockState s,Level l,BlockPos p,RandomSource r){
            if(r.nextInt(90)==0)l.playLocalSound(p.getX()+.5,p.getY()+.5,p.getZ()+.5,Veilborne.HUSH_CHIRP.get(),net.minecraft.sounds.SoundSource.BLOCKS,.22f,.9f+r.nextFloat()*.3f,false);
        }
    }
    private HushBlocks(){}
}
