package com.suiyi.veilborne;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
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
import org.joml.Vector3f;

/** The three blocks of 绯线缝世: the tuning anchor, the sky-weaving lamp and the scrap of the heavenly robe. */
public final class ThreadBlocks {
    private static final DustParticleOptions SCARLET=new DustParticleOptions(new Vector3f(.88f,.16f,.2f),.6f);

    /**
     * 调律锚: an iron stake with a brass tuning fork for a head and scarlet thread wound down it. Within a few blocks of it
     * the weather cannot move anyone. Set down inside an unsewn pocket, it gathers what floats there into seam silk.
     */
    public static class Anchor extends Block {
        public static final MapCodec<Anchor> CODEC=simpleCodec(Anchor::new);
        private static final VoxelShape SHAPE=Block.box(5,0,5,11,15,11);
        public Anchor(Properties p){super(p);}
        @Override protected MapCodec<? extends Block> codec(){return CODEC;}
        @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SHAPE;}
        @Override protected boolean canSurvive(BlockState s,LevelReader l,BlockPos p){return Block.canSupportCenter(l,p.below(),Direction.UP);}
        @Override protected BlockState updateShape(BlockState s,Direction d,BlockState n,LevelAccessor l,BlockPos p,BlockPos np){
            return d==Direction.DOWN&&!canSurvive(s,l,p)?Blocks.AIR.defaultBlockState():super.updateShape(s,d,n,l,p,np);
        }
        @Override public void animateTick(BlockState s,Level l,BlockPos p,RandomSource r){
            if(r.nextInt(5)!=0)return;
            double a=r.nextDouble()*Math.PI*2;
            l.addParticle(SCARLET,p.getX()+.5+Math.cos(a)*.18,p.getY()+.2+r.nextDouble()*.6,p.getZ()+.5+Math.sin(a)*.18,0,.01,0);
        }
    }

    /**
     * 织天灯: a brass cage around a glowing cocoon of scarlet thread. Lamps within reach of each other are joined by a slack,
     * glowing thread (drawn by the client), so a few of them string light across a room or a garden. Two lamps sewn together
     * with scarlet thread, however far apart, are one place: see {@link LoomLinks}.
     */
    public static class Lantern extends Block implements EntityBlock {
        public static final MapCodec<Lantern> CODEC=simpleCodec(Lantern::new);
        @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new LoomLink(p,s);}
        @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){
            if(!stack.is(Veilborne.SCARLET_THREAD.get()))return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if(l instanceof ServerLevel level&&player instanceof ServerPlayer sp)return LoomLinks.thread(sp,level,p);
            return ItemInteractionResult.SUCCESS;
        }
        @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){
            if(!player.getMainHandItem().isEmpty())return InteractionResult.PASS;
            if(l instanceof ServerLevel level&&player instanceof ServerPlayer sp)return LoomLinks.use(sp,level,p);
            return InteractionResult.SUCCESS;
        }
        @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState now,boolean moved){
            if(!now.is(this)&&l instanceof ServerLevel level&&l.getBlockEntity(p) instanceof LoomLink e)LoomLinks.broken(level,p,e);
            super.onRemove(s,l,p,now,moved);
        }
        private static final VoxelShape SHAPE=Block.box(4,0,4,12,14,12);
        public Lantern(Properties p){super(p);}
        @Override protected MapCodec<? extends Block> codec(){return CODEC;}
        @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SHAPE;}
        @Override protected boolean canSurvive(BlockState s,LevelReader l,BlockPos p){return Block.canSupportCenter(l,p.below(),Direction.UP)||Block.canSupportCenter(l,p.above(),Direction.DOWN);}
        @Override protected BlockState updateShape(BlockState s,Direction d,BlockState n,LevelAccessor l,BlockPos p,BlockPos np){
            return d.getAxis().isVertical()&&!canSurvive(s,l,p)?Blocks.AIR.defaultBlockState():super.updateShape(s,d,n,l,p,np);
        }
        @Override public void animateTick(BlockState s,Level l,BlockPos p,RandomSource r){
            if(r.nextInt(6)!=0)return;
            l.addParticle(Veilborne.THREAD_FIBER.get(),p.getX()+.5+(r.nextDouble()-.5)*.3,p.getY()+.55,p.getZ()+.5+(r.nextDouble()-.5)*.3,(r.nextDouble()-.5)*.004,.006,(r.nextDouble()-.5)*.004);
        }
    }

    /** The other end of a sky-weaving lamp's thread, if it has been sewn to one (pending: set down when its ground is next loaded). */
    public static class LoomLink extends BlockEntity {
        private BlockPos partner;
        private ResourceKey<Level> dim=Level.OVERWORLD;
        private boolean pending;
        public LoomLink(BlockPos p,BlockState s){super(Veilborne.LOOM_LINK.get(),p,s);}
        public boolean linked(){return partner!=null;}
        public BlockPos partner(){return partner;}
        public ResourceKey<Level> partnerDim(){return dim;}
        public boolean pending(){return pending;}
        public void setPartner(BlockPos p,ResourceKey<Level> d,boolean waiting){partner=p.immutable();dim=d;pending=waiting;changed();}
        public void clear(){partner=null;pending=false;changed();}
        private void changed(){setChanged();if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
        @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider r){
            super.saveAdditional(tag,r);
            if(partner!=null){tag.putLong("Partner",partner.asLong());tag.putString("Dim",dim.location().toString());tag.putBoolean("Pending",pending);}
        }
        @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider r){
            super.loadAdditional(tag,r);
            if(tag.contains("Partner")){
                partner=BlockPos.of(tag.getLong("Partner"));pending=tag.getBoolean("Pending");
                ResourceLocation id=ResourceLocation.tryParse(tag.getString("Dim"));
                dim=id==null?Level.OVERWORLD:ResourceKey.create(Registries.DIMENSION,id);
            }else{partner=null;pending=false;}
        }
        @Override public CompoundTag getUpdateTag(HolderLookup.Provider r){return saveWithoutMetadata(r);}
        @Override public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    }

    /** 天衣残片: a scrap of the sky's cloth, hung on a wall: night blue, a few stars, and a gold seam stitched across it. */
    public static class Scrap extends HorizontalDirectionalBlock {
        public static final MapCodec<Scrap> CODEC=simpleCodec(Scrap::new);
        private static final VoxelShape NORTH=Block.box(1,1,15,15,15,16),SOUTH=Block.box(1,1,0,15,15,1),WEST=Block.box(15,1,1,16,15,15),EAST=Block.box(0,1,1,1,15,15);
        public Scrap(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
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
            if(r.nextInt(12)!=0)return;
            Direction f=s.getValue(FACING);
            l.addParticle(Veilborne.MOTE.get(),p.getX()+.5+f.getStepX()*.44+(r.nextDouble()-.5)*.6*Math.abs(f.getStepZ()),p.getY()+.2+r.nextDouble()*.6,
                p.getZ()+.5+f.getStepZ()*.44+(r.nextDouble()-.5)*.6*Math.abs(f.getStepX()),f.getStepX()*.003,.006,f.getStepZ()*.003);
        }
    }
    private ThreadBlocks(){}
}
