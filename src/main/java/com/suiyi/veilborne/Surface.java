package com.suiyi.veilborne;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The ground under the leaves, on either side. The server keeps a MOTION_BLOCKING_NO_LEAVES heightmap, but
 * clients are only ever sent MOTION_BLOCKING and WORLD_SURFACE: their NO_LEAVES map starts empty and is only
 * patched by later block changes, so it reads as the bottom of the world in freshly received chunks. On the
 * client the same height is derived from MOTION_BLOCKING by stepping down through leaves.
 */
public final class Surface {
    /** First free y above the top block that blocks motion or holds fluid, ignoring leaves. */
    public static int top(Level level,int x,int z){
        if(!level.isClientSide)return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING,x,z);
        BlockPos.MutableBlockPos p=new BlockPos.MutableBlockPos(x,y-1,z);
        int min=level.getMinBuildHeight();
        while(p.getY()>min){
            BlockState s=level.getBlockState(p);
            boolean ground=(s.blocksMotion()||!s.getFluidState().isEmpty())&&!(s.getBlock() instanceof LeavesBlock);
            if(ground)break;
            p.move(0,-1,0);
        }
        return p.getY()+1;
    }
    public static BlockPos topPos(Level level,BlockPos at){return new BlockPos(at.getX(),top(level,at.getX(),at.getZ()),at.getZ());}
    private Surface(){}
}
