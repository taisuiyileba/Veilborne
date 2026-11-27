package com.suiyi.veilborne;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.world.level.BlockGetter;

public class TideLanternBlock extends BaseEntityBlock {
    public static final MapCodec<TideLanternBlock> CODEC=simpleCodec(TideLanternBlock::new);
    public TideLanternBlock(Properties p){super(p);}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return CODEC;}
    @Override protected RenderShape getRenderShape(BlockState s){return RenderShape.MODEL;}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return box(3,0,3,13,14,13);}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new TideLanternEntity(p,s);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){
        return l.isClientSide?createTickerHelper(t,Veilborne.LANTERN_ENTITY.get(),TideLanternEntity::tick):null;
    }
}
