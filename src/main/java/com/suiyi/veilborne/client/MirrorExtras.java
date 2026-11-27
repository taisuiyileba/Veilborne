package com.suiyi.veilborne.client;

import com.suiyi.veilborne.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * What the mirror shows that the world does not have: a door in a wall (or a doorway standing alone in the open)
 * and a slender watchtower on a nearby rise. Built from ordinary block states and meshed into the reflection only.
 */
final class MirrorExtras {
    /** Real-world positions of the tower's blocks, for the prism's outline. */
    static final List<BlockPos> TOWER=new ArrayList<>();
    static boolean towerPlaced;
    static BlockPos towerBase=BlockPos.ZERO;

    static void door(MirrorPacket st,Map<Long,BlockState> out,Map<Long,Integer> kinds){
        if(!st.hasDoor())return;
        Direction f=Direction.from2DDataValue(st.doorFace()),side=f.getClockWise();
        BlockPos d=st.door();
        BlockState frame=Blocks.WAXED_CUT_COPPER.defaultBlockState(),trim=Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
        BlockState door=Blocks.WAXED_OXIDIZED_COPPER_DOOR.defaultBlockState().setValue(DoorBlock.FACING,f).setValue(DoorBlock.HINGE,DoorHingeSide.LEFT);
        put(out,kinds,d,door.setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER),MirrorWorld.EXTRA_LIGHT);
        put(out,kinds,d.above(),door.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),MirrorWorld.EXTRA_LIGHT);
        for(int s=-1;s<=1;s+=2)for(int y=0;y<=2;y++)put(out,kinds,d.relative(side,s).above(y),y==2?trim:frame,MirrorWorld.EXTRA);
        put(out,kinds,d.above(2),Blocks.LANTERN.defaultBlockState(),MirrorWorld.EXTRA_LIGHT);
        if(st.has(MirrorData.FREESTANDING)){
            // A doorway on its own: two posts, a lintel and a step, nothing to either side.
            put(out,kinds,d.above(2),frame,MirrorWorld.EXTRA);
            put(out,kinds,d.above(3),Blocks.LANTERN.defaultBlockState(),MirrorWorld.EXTRA_LIGHT);
            for(int s=-1;s<=1;s++)put(out,kinds,d.relative(side,s).below(),Blocks.POLISHED_DIORITE.defaultBlockState(),MirrorWorld.EXTRA);
        }
    }
    /** A watchtower that is not really there, on the ground some way off, with lit windows. */
    static void tower(MirrorPacket st,Level level,Map<Long,BlockState> out,Map<Long,Integer> kinds){
        TOWER.clear();towerPlaced=false;
        if(level==null)return;
        // The same column the server digs the tower's foundation under (MirrorVault).
        int[] col=MirrorVault.column(st.center(),st.seed());
        int bx=col[0],bz=col[1];
        if(level.getChunkSource().getChunk(bx>>4,bz>>4,false)==null)return;
        int ground=Integer.MAX_VALUE,highest=Integer.MIN_VALUE;
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){int y=Surface.top(level,bx+x,bz+z);ground=Math.min(ground,y);highest=Math.max(highest,y);}
        if(highest-ground>6)ground=highest-6;
        towerBase=new BlockPos(bx,ground,bz);
        BlockState brick=Blocks.STONE_BRICKS.defaultBlockState(),mossy=Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),cracked=Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        BlockState chisel=Blocks.CHISELED_STONE_BRICKS.defaultBlockState(),glow=Blocks.YELLOW_STAINED_GLASS.defaultBlockState(),slate=Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        int height=19;
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){
            int top=Surface.top(level,bx+x,bz+z);
            for(int y=Math.min(top,ground)-1;y<ground;y++)put(out,kinds,new BlockPos(bx+x,y,bz+z),brick,MirrorWorld.EXTRA);
        }
        for(int y=0;y<height;y++){
            int r=y<4?2:y<15?1:2;
            for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++){
                boolean shell=Math.abs(x)==r||Math.abs(z)==r;
                if(!shell&&y%5!=0)continue;
                if(r==2&&Math.abs(x)==2&&Math.abs(z)==2&&y>=15)continue;
                BlockPos p=new BlockPos(bx+x,ground+y,bz+z);
                boolean window=shell&&(y==7||y==8||y==12)&&(x==0||z==0)&&r==1;
                BlockState s=window?glow:y==3||y==14?chisel:((x*7+y*13+z*5)&7)==0?mossy:((x*3+y*5+z*11)&15)==1?cracked:brick;
                if(y>=15)s=slate;
                put(out,kinds,p,s,window?MirrorWorld.EXTRA_LIGHT:MirrorWorld.EXTRA);
            }
        }
        // A pointed roof of deepslate tile stairs and a lantern on the spire.
        BlockState stair=Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState();
        for(int ring=0;ring<3;ring++){
            int r=2-ring,y=ground+height+ring;
            for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++){
                if(Math.abs(x)!=r&&Math.abs(z)!=r)continue;
                Direction facing=Math.abs(x)>=Math.abs(z)?(x>0?Direction.WEST:Direction.EAST):(z>0?Direction.NORTH:Direction.SOUTH);
                BlockState s=r==0?Blocks.DEEPSLATE_TILES.defaultBlockState():stair.setValue(StairBlock.FACING,facing).setValue(StairBlock.HALF,Half.BOTTOM).setValue(StairBlock.SHAPE,StairsShape.STRAIGHT);
                put(out,kinds,new BlockPos(bx+x,y,bz+z),s,MirrorWorld.EXTRA);
            }
        }
        put(out,kinds,new BlockPos(bx,ground+height+3,bz),Blocks.CHAIN.defaultBlockState(),MirrorWorld.EXTRA);
        put(out,kinds,new BlockPos(bx,ground+height+4,bz),Blocks.LANTERN.defaultBlockState(),MirrorWorld.EXTRA_LIGHT);
        for(int y=1;y<height;y+=6)put(out,kinds,new BlockPos(bx,ground+y,bz),Blocks.LANTERN.defaultBlockState(),MirrorWorld.EXTRA_LIGHT);
        towerPlaced=true;
    }
    private static void put(Map<Long,BlockState> out,Map<Long,Integer> kinds,BlockPos p,BlockState s,int kind){
        out.put(p.asLong(),s);kinds.put(p.asLong(),kind);
        if(p.getY()>=towerBase.getY()-8&&Math.abs(p.getX()-towerBase.getX())<=3&&Math.abs(p.getZ()-towerBase.getZ())<=3&&!towerBase.equals(BlockPos.ZERO))TOWER.add(p.immutable());
    }
    private MirrorExtras(){}
}
