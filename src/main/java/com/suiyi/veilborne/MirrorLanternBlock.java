package com.suiyi.veilborne;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 镜灯: a small oil flame in front of a round bronze mirror. The mirror throws the light one way only: a pool of
 * it lies on the ground in front of the lamp, and the face glints when you stand where the light goes.
 */
public class MirrorLanternBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<MirrorLanternBlock> CODEC=simpleCodec(MirrorLanternBlock::new);
    private static final VoxelShape SHAPE=Block.box(3.5,0,3.5,12.5,13,12.5);
    public MirrorLanternBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
    /** The light is thrown toward whoever placed it. */
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SHAPE;}
    @Override protected boolean canSurvive(BlockState s,LevelReader l,BlockPos p){return Block.canSupportCenter(l,p.below(),Direction.UP);}
    @Override protected BlockState updateShape(BlockState s,Direction d,BlockState n,LevelAccessor l,BlockPos p,BlockPos np){
        return d==Direction.DOWN&&!canSurvive(s,l,p)?net.minecraft.world.level.block.Blocks.AIR.defaultBlockState():super.updateShape(s,d,n,l,p,np);
    }
    @Override public void animateTick(BlockState s,Level l,BlockPos p,RandomSource r){
        Direction f=s.getValue(FACING);
        if(r.nextInt(4)!=0)return;
        double x=p.getX()+.5+f.getStepX()*.2,z=p.getZ()+.5+f.getStepZ()*.2;
        l.addParticle(Veilborne.MIRROR_MOTE.get(),x+(r.nextDouble()-.5)*.2,p.getY()+.55,z+(r.nextDouble()-.5)*.2,f.getStepX()*.012,.004,f.getStepZ()*.012);
    }
}
