package com.suiyi.veilborne;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The blocks of 苍翠梦潮. A dream seed planted in soil becomes a sapling of pale wood that grows, in its own time or with
 * bone meal, into a small dream tree: a slender, leaning trunk of pale bark with a crown of leaves that give a soft green
 * light, some of them in honey-coloured flower. A full dream seed grows a larger one with two branches and hanging fronds.
 * Neither spreads: the leaves never fall or drop saplings, and nothing grows anywhere it was not planted. The flower lamp is
 * a lotus bud of gold with a lamp inside; use it to let its little school of leaf-fish out to circle it, or call them in.
 * The antler twig is a piece of the deer's antler, hung on a wall, with a tiny tree still growing on it. The foot of a grown
 * tree's trunk is its heart: while the crown is in leaf, things round it grow faster (see {@link DreamHeart}).
 */
public final class DreamBlocks {
    public static final BooleanProperty FULL=BooleanProperty.create("full"),BLOSSOM=BooleanProperty.create("blossom"),SWARM=BooleanProperty.create("swarm");
    public static final IntegerProperty STAGE=BlockStateProperties.AGE_3;

    /** Where a dream seed takes: soil with room above. */
    public static boolean soil(BlockState s){return s.is(BlockTags.DIRT)||s.is(Blocks.FARMLAND)||s.is(Blocks.MUD)||s.is(Blocks.MOSS_BLOCK);}

    public static class Sapling extends Block implements BonemealableBlock {
        public static final MapCodec<Sapling> CODEC=simpleCodec(Sapling::new);
        private static final VoxelShape[] SHAPES={Block.box(6,0,6,10,5,10),Block.box(5,0,5,11,8,11),Block.box(4,0,4,12,11,12),Block.box(3,0,3,13,14,13)};
        public Sapling(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(STAGE,0).setValue(FULL,false));}
        @Override protected MapCodec<? extends Block> codec(){return CODEC;}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(STAGE,FULL);}
        @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SHAPES[s.getValue(STAGE)];}
        @Override protected VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return net.minecraft.world.phys.shapes.Shapes.empty();}
        @Override protected boolean canSurvive(BlockState s,LevelReader l,BlockPos p){return soil(l.getBlockState(p.below()));}
        @Override protected BlockState updateShape(BlockState s,Direction d,BlockState n,LevelAccessor l,BlockPos p,BlockPos np){
            return d==Direction.DOWN&&!canSurvive(s,l,p)?Blocks.AIR.defaultBlockState():super.updateShape(s,d,n,l,p,np);
        }
        @Override protected boolean isRandomlyTicking(BlockState s){return true;}
        @Override protected void randomTick(BlockState s,ServerLevel l,BlockPos p,RandomSource r){
            if(l.getMaxLocalRawBrightness(p.above())>=8&&r.nextInt(6)==0)advance(l,p,s,r);
        }
        private void advance(ServerLevel l,BlockPos p,BlockState s,RandomSource r){
            int stage=s.getValue(STAGE);
            if(stage<3){l.setBlock(p,s.setValue(STAGE,stage+1),3);return;}
            if(grow(l,p,s.getValue(FULL),r)){
                l.playSound(null,p,Veilborne.DREAM_SPROUT.get(),SoundSource.BLOCKS,1,1);
                DreamService.cue(l,new DreamCue(DreamCue.SPROUT,net.minecraft.world.phys.Vec3.atBottomCenterOf(p),s.getValue(FULL)?1:0),48);
            }
        }
        @Override public boolean isValidBonemealTarget(LevelReader l,BlockPos p,BlockState s){return true;}
        @Override public boolean isBonemealSuccess(Level l,RandomSource r,BlockPos p,BlockState s){return r.nextFloat()<.6f;}
        @Override public void performBonemeal(ServerLevel l,RandomSource r,BlockPos p,BlockState s){advance(l,p,s,r);}
        @Override public void animateTick(BlockState s,Level l,BlockPos p,RandomSource r){
            if(r.nextInt(5)==0)l.addParticle(Veilborne.DREAM_POLLEN.get(),p.getX()+.5+(r.nextDouble()-.5)*.5,p.getY()+.3+s.getValue(STAGE)*.2,p.getZ()+.5+(r.nextDouble()-.5)*.5,0,.01,0);
        }
    }

    /**
     * Grows a dream tree from the sapling at {@code base}. Returns false (and leaves the sapling) if there is no room for
     * its trunk; leaves only go where there is air or a plant, so it never pushes into a wall or overwrites a block.
     */
    public static boolean grow(LevelAccessor l,BlockPos base,boolean full,RandomSource r){
        int height=full?8+r.nextInt(2):5+r.nextInt(2);
        Direction lean=Direction.Plane.HORIZONTAL.getRandomDirection(r);
        int leanAt=full?4:3;
        BlockPos top=base;
        for(int y=0;y<height;y++){
            BlockPos at=base.above(y).relative(lean,y>=leanAt?1:0);
            if(y>0&&!free(l,at))return false;
            top=at;
        }
        BlockState log=Veilborne.DREAM_LOG.get().defaultBlockState();
        BlockState heart=Veilborne.DREAM_HEART.get().defaultBlockState().setValue(FULL,full);
        for(int y=0;y<height;y++){
            BlockPos at=base.above(y).relative(lean,y>=leanAt?1:0);
            l.setBlock(at,y==0?heart:log,3);
            // The lean is a step: fill the corner under it so the trunk reads as one piece.
            if(y==leanAt)put(l,base.above(y-1).relative(lean),log);
        }
        crown(l,top.above(),full?3.4:2.6,r);
        if(full){
            for(Direction d:new Direction[]{lean.getClockWise(),lean.getCounterClockWise()}){
                BlockPos b=top.below(3);
                for(int i=1;i<=2;i++){b=b.relative(d).above(i==2?1:0);put(l,b,log.setValue(RotatedPillarBlock.AXIS,d.getAxis()));}
                crown(l,b.above(),2.1,r);
            }
            // Fronds hanging from the crown's edge.
            for(int i=0;i<7;i++){
                double a=r.nextDouble()*Math.PI*2;
                BlockPos f=top.offset((int)Math.round(Math.cos(a)*3),-1,(int)Math.round(Math.sin(a)*3));
                for(int k=0;k<1+r.nextInt(3);k++){if(!put(l,f.below(k),leaves(r)))break;}
            }
        }
        return true;
    }
    private static void crown(LevelAccessor l,BlockPos c,double radius,RandomSource r){
        int R=(int)Math.ceil(radius);
        for(int x=-R;x<=R;x++)for(int y=-R+1;y<=R-1;y++)for(int z=-R;z<=R;z++){
            double d=Math.sqrt(x*x+(y+.5)*(y+.5)*1.6+z*z);
            if(d>radius+r.nextDouble()*.35-.2)continue;
            put(l,c.offset(x,y,z),leaves(r));
        }
    }
    private static BlockState leaves(RandomSource r){
        return Veilborne.DREAM_LEAVES.get().defaultBlockState().setValue(LeavesBlock.PERSISTENT,true).setValue(BLOSSOM,r.nextFloat()<.16f);
    }
    private static boolean free(LevelAccessor l,BlockPos p){BlockState s=l.getBlockState(p);return s.isAir()||s.canBeReplaced()||s.is(BlockTags.LEAVES)&&!s.is(Veilborne.DREAM_LEAVES.get());}
    private static boolean put(LevelAccessor l,BlockPos p,BlockState s){
        BlockState was=l.getBlockState(p);
        if(!(was.isAir()||was.canBeReplaced()))return false;
        l.setBlock(p,s,3);return true;
    }

    /** 梦树心: the foot of a grown dream tree's trunk, a knot of amber heartwood that beats every two seconds. */
    public static class Heart extends Block {
        public static final MapCodec<Heart> CODEC=simpleCodec(Heart::new);
        public Heart(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FULL,false));}
        @Override protected MapCodec<? extends Block> codec(){return CODEC;}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FULL);}
        @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){
            super.onPlace(s,l,p,old,moved);
            VeilWards.add(l,VeilWards.HEART,p);
            if(!l.isClientSide)l.scheduleTick(p,this,DreamHeart.PERIOD);
        }
        @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState now,boolean moved){
            if(!now.is(this))VeilWards.remove(l,VeilWards.HEART,p);
            super.onRemove(s,l,p,now,moved);
        }
        @Override protected void tick(BlockState s,ServerLevel l,BlockPos p,RandomSource r){
            DreamHeart.nurture(l,p,s.getValue(FULL),r);
            l.scheduleTick(p,this,DreamHeart.PERIOD);
        }
        @Override protected boolean isRandomlyTicking(BlockState s){return true;}
        @Override protected void randomTick(BlockState s,ServerLevel l,BlockPos p,RandomSource r){
            VeilWards.add(l,VeilWards.HEART,p);
            if(!l.getBlockTicks().hasScheduledTick(p,this))l.scheduleTick(p,this,DreamHeart.PERIOD);
        }
        @Override public void animateTick(BlockState s,Level l,BlockPos p,RandomSource r){
            if(r.nextInt(4)!=0)return;
            Direction d=Direction.Plane.HORIZONTAL.getRandomDirection(r);
            l.addParticle(Veilborne.DREAM_POLLEN.get(),p.getX()+.5+d.getStepX()*.55,p.getY()+.35+r.nextDouble()*.3,p.getZ()+.5+d.getStepZ()*.55,d.getStepX()*.01,.012,d.getStepZ()*.01);
        }
    }

    /** Leaves of the dream tree: they never decay (so they never drop anything but themselves), and some are in flower. */
    public static class Leaves extends LeavesBlock {
        public static final MapCodec<Leaves> CODEC=simpleCodec(Leaves::new);
        public Leaves(Properties p){super(p);registerDefaultState(defaultBlockState().setValue(BLOSSOM,false).setValue(PERSISTENT,true));}
        @Override public MapCodec<? extends LeavesBlock> codec(){return CODEC;}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){super.createBlockStateDefinition(b);b.add(BLOSSOM);}
        @Override protected boolean isRandomlyTicking(BlockState s){return false;}
        @Override protected void randomTick(BlockState s,ServerLevel l,BlockPos p,RandomSource r){}
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){BlockState s=super.getStateForPlacement(c);return s==null?null:s.setValue(PERSISTENT,true);}
        @Override public void animateTick(BlockState s,Level l,BlockPos p,RandomSource r){
            if(r.nextInt(s.getValue(BLOSSOM)?14:40)!=0)return;
            if(!l.getBlockState(p.below()).isAir())return;
            l.addParticle(s.getValue(BLOSSOM)?Veilborne.DREAM_POLLEN.get():Veilborne.DREAM_FIREFLY.get(),p.getX()+r.nextDouble(),p.getY()-.05,p.getZ()+r.nextDouble(),0,-.01,0);
        }
    }

    /** 花灯: a lotus bud of gold leaf with a lamp inside. Use it to let its little school out to circle it, or call them in. */
    public static class Lamp extends LanternBlock {
        public static final MapCodec<Lamp> CODEC=simpleCodec(Lamp::new);
        public Lamp(Properties p){super(p);registerDefaultState(defaultBlockState().setValue(SWARM,true));}
        @Override public MapCodec<LanternBlock> codec(){return (MapCodec<LanternBlock>)(MapCodec<?>)CODEC;}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){super.createBlockStateDefinition(b);b.add(SWARM);}
        @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){
            l.setBlock(p,s.cycle(SWARM),3);
            l.playSound(player,p,Veilborne.DREAM_CHIME.get(),SoundSource.BLOCKS,.5f,s.getValue(SWARM)?.8f:1.25f);
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
    }

    /** 鹿角枝: a twig of the deer's antler, hung on a wall, with a tiny tree growing on it. Fireflies come to it at night. */
    public static class Twig extends HorizontalDirectionalBlock {
        public static final MapCodec<Twig> CODEC=simpleCodec(Twig::new);
        private static final VoxelShape NORTH=Block.box(2,1,13,14,15,16),SOUTH=Block.box(2,1,0,14,15,3),WEST=Block.box(13,1,2,16,15,14),EAST=Block.box(0,1,2,3,15,14);
        public Twig(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
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
        @Override protected boolean canSurvive(BlockState s,LevelReader l,BlockPos p){
            Direction f=s.getValue(FACING);BlockPos wall=p.relative(f.getOpposite());
            return l.getBlockState(wall).isFaceSturdy(l,wall,f);
        }
        @Override protected BlockState updateShape(BlockState s,Direction d,BlockState n,LevelAccessor l,BlockPos p,BlockPos np){
            return d==s.getValue(FACING).getOpposite()&&!canSurvive(s,l,p)?Blocks.AIR.defaultBlockState():super.updateShape(s,d,n,l,p,np);
        }
        @Override public void animateTick(BlockState s,Level l,BlockPos p,RandomSource r){
            if(r.nextInt(7)!=0)return;
            Direction f=s.getValue(FACING);
            l.addParticle(Veilborne.DREAM_FIREFLY.get(),p.getX()+.5+f.getStepX()*.3+(r.nextDouble()-.5)*.8,p.getY()+.4+r.nextDouble()*.7,
                p.getZ()+.5+f.getStepZ()*.3+(r.nextDouble()-.5)*.8,0,.004,0);
        }
    }
    private DreamBlocks(){}
}
