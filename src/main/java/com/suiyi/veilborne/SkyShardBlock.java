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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 天镜残片: the last piece of the mirror sky, hung on a wall. It still shows the inverted land drifting past,
 * long after the sky it came from has gone. Decorative, faintly lit, and it drops itself.
 */
public class SkyShardBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<SkyShardBlock> CODEC=simpleCodec(SkyShardBlock::new);
    private static final VoxelShape NORTH=Block.box(1,1,15,15,15,16),SOUTH=Block.box(1,1,0,15,15,1),WEST=Block.box(15,1,1,16,15,15),EAST=Block.box(0,1,1,1,15,15);
    public SkyShardBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){
        Direction face=c.getClickedFace();
        if(!face.getAxis().isHorizontal())face=c.getHorizontalDirection().getOpposite();
        BlockState s=defaultBlockState().setValue(FACING,face);
        return canSurvive(s,c.getLevel(),c.getClickedPos())?s:null;
    }
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){
        return switch(s.getValue(FACING)){case SOUTH->SOUTH;case WEST->WEST;case EAST->EAST;default->NORTH;};
    }
    /** Hung on the face of the block behind it. */
    @Override protected boolean canSurvive(BlockState s,LevelReader l,BlockPos p){
        Direction f=s.getValue(FACING);BlockPos wall=p.relative(f.getOpposite());
        return l.getBlockState(wall).isFaceSturdy(l,wall,f);
    }
    @Override protected BlockState updateShape(BlockState s,Direction d,BlockState n,LevelAccessor l,BlockPos p,BlockPos np){
        return d==s.getValue(FACING).getOpposite()&&!canSurvive(s,l,p)?Blocks.AIR.defaultBlockState():super.updateShape(s,d,n,l,p,np);
    }
    @Override public void animateTick(BlockState s,Level l,BlockPos p,RandomSource r){
        if(r.nextInt(9)!=0)return;
        Direction f=s.getValue(FACING);
        l.addParticle(Veilborne.MIRROR_MOTE.get(),p.getX()+.5+f.getStepX()*.42+(r.nextDouble()-.5)*.6*Math.abs(f.getStepZ()),p.getY()+.2+r.nextDouble()*.6,
            p.getZ()+.5+f.getStepZ()*.42+(r.nextDouble()-.5)*.6*Math.abs(f.getStepX()),f.getStepX()*.004,.01,f.getStepZ()*.004);
    }
}
