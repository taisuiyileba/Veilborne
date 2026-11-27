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
 * 银花灯: a drooping silver flower hung from a thin stake, breaks by hand, drops itself. It keeps a little of the bloom's
 * moonlight: no monster comes out of the dark near one (see {@link MoonHaven}).
 */
public class SilverLanternBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<SilverLanternBlock> CODEC=simpleCodec(SilverLanternBlock::new);
    private static final VoxelShape SHAPE=Block.box(4,0,4,12,15,12);
    public SilverLanternBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SHAPE;}
    @Override protected boolean canSurvive(BlockState s,LevelReader l,BlockPos p){return Block.canSupportCenter(l,p.below(),Direction.UP);}
    @Override protected BlockState updateShape(BlockState s,Direction d,BlockState n,LevelAccessor l,BlockPos p,BlockPos np){
        return d==Direction.DOWN&&!canSurvive(s,l,p)?net.minecraft.world.level.block.Blocks.AIR.defaultBlockState():super.updateShape(s,d,n,l,p,np);
    }
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){super.onPlace(s,l,p,old,moved);VeilWards.add(l,VeilWards.HAVEN,p);}
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState now,boolean moved){
        if(!now.is(this))VeilWards.remove(l,VeilWards.HAVEN,p);
        super.onRemove(s,l,p,now,moved);
    }
    @Override protected boolean isRandomlyTicking(BlockState s){return true;}
    @Override protected void randomTick(BlockState s,net.minecraft.server.level.ServerLevel l,BlockPos p,RandomSource r){VeilWards.add(l,VeilWards.HAVEN,p);}
    @Override public void animateTick(BlockState s,Level l,BlockPos p,RandomSource r){
        // The hanging bloom sits over the stake's hook, a little toward the facing side.
        Direction f=s.getValue(FACING);
        double x=p.getX()+.5+f.getStepX()*.12,z=p.getZ()+.5+f.getStepZ()*.12;
        if(r.nextInt(3)==0)l.addParticle(Veilborne.MOON_MOTE.get(),x+(r.nextDouble()-.5)*.3,p.getY()+.55,z+(r.nextDouble()-.5)*.3,0,-.004,0);
        if(r.nextInt(14)==0)l.addParticle(Veilborne.PETAL.get(),x,p.getY()+.5,z,(r.nextDouble()-.5)*.01,-.01,(r.nextDouble()-.5)*.01);
    }
}
