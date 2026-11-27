package com.suiyi.veilborne;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * What the verdant dream leaves on a farm. A dream tree grown from a seed has a heart in the foot of its trunk; while its
 * crown is in leaf, the ground round it dreams: crops, saplings, stems, berries, cane and wart grow about twice as fast,
 * young animals grow up twice as fast, animals are ready to breed again twice as soon, and a pair that breeds there
 * sometimes has twins. A full seed's tree reaches further. Now and then the tree lets fall a sprig of its leaves.
 *
 * Dream leaves are what every animal will eat: fed to one that could breed, it is ready to; fed to a young one, it grows.
 * They also go into a composter almost whole.
 */
public final class DreamHeart {
    public static final int PERIOD=40, RADIUS=8, FULL_RADIUS=12, HEIGHT=3, LEAVES=8, SPRIGS=3;
    public static final double TWINS=.3, FULL_TWINS=.45;
    public static int radius(boolean full){return full?FULL_RADIUS:RADIUS;}
    /** Leaves of its own crown over the heart; a bare trunk does nothing. */
    public static int leaves(ServerLevel level,BlockPos heart){
        int n=0;
        for(BlockPos b:BlockPos.betweenClosed(heart.offset(-5,1,-5),heart.offset(5,12,5)))if(level.getBlockState(b).is(Veilborne.DREAM_LEAVES.get())&&++n>=LEAVES)return n;
        return n;
    }
    /** Anything that grows by itself, as crops and saplings do. */
    public static boolean grows(BlockState s){
        if(!s.isRandomlyTicking()||s.is(Veilborne.DREAM_SAPLING.get()))return false;
        Block b=s.getBlock();
        return s.is(BlockTags.CROPS)||s.is(BlockTags.SAPLINGS)||b instanceof StemBlock||b instanceof SweetBerryBushBlock||b instanceof CocoaBlock
            ||b instanceof SugarCaneBlock||b instanceof CactusBlock||b instanceof NetherWartBlock||b instanceof BambooStalkBlock||b instanceof CaveVinesBlock||b instanceof PitcherCropBlock;
    }
    /** One beat of the heart: {@link #PERIOD} ticks of the dream for the ground and the animals round it. */
    public static void nurture(ServerLevel level,BlockPos heart,boolean full,RandomSource r){
        if(leaves(level,heart)<LEAVES)return;
        int R=radius(full),picks=Math.max(4,(2*R+1)*(2*R+1)/36);
        // About one extra growth step per column every 70 seconds: as much again as the world's own random ticks give.
        for(int k=0;k<picks;k++){
            int x=heart.getX()+r.nextInt(2*R+1)-R,z=heart.getZ()+r.nextInt(2*R+1)-R;
            for(int y=-HEIGHT;y<=HEIGHT;y++){
                BlockPos p=new BlockPos(x,heart.getY()+y,z);
                BlockState s=level.getBlockState(p);
                if(!grows(s))continue;
                s.randomTick(level,p,r);
                if(r.nextInt(3)==0)level.sendParticles(Veilborne.DREAM_POLLEN.get(),x+.5,p.getY()+.6,z+.5,1,.2,.15,.2,.004);
                break;
            }
        }
        for(Animal a:level.getEntitiesOfClass(Animal.class,new AABB(heart).inflate(R,HEIGHT+2,R))){
            if(a.getAge()<0)a.ageUp(PERIOD/20);
            else if(a.getAge()>0)a.setAge(Math.max(0,a.getAge()-PERIOD));
        }
        if(r.nextInt(90)==0&&level.getEntitiesOfClass(ItemEntity.class,new AABB(heart).inflate(8),i->i.getItem().is(Veilborne.DREAM_LEAVES_ITEM.get())).size()<SPRIGS){
            BlockPos at=TideService.findSafe(level,heart.offset(r.nextInt(5)-2,0,r.nextInt(5)-2),3);
            if(at!=null){
                ItemEntity item=new ItemEntity(level,at.getX()+.5,at.getY()+2.5,at.getZ()+.5,new ItemStack(Veilborne.DREAM_LEAVES_ITEM.get()));
                item.setDeltaMovement(0,-.02,0);level.addFreshEntity(item);
            }
        }
    }
    /** The heart that reaches this point, if any. */
    public static BlockPos reaching(ServerLevel level,Vec3 at){
        for(BlockPos h:VeilWards.within(level,VeilWards.HEART,at,FULL_RADIUS+1)){
            BlockState s=level.getBlockState(h);
            if(!s.is(Veilborne.DREAM_HEART.get()))continue;
            int R=radius(s.getValue(DreamBlocks.FULL));
            if(Math.abs(at.x-h.getX()-.5)<=R+.5&&Math.abs(at.z-h.getZ()-.5)<=R+.5&&Math.abs(at.y-h.getY())<=HEIGHT+3)return h;
        }
        return null;
    }
    @SubscribeEvent public static void twins(BabyEntitySpawnEvent e){
        if(e.getChild()==null||!(e.getParentA() instanceof Animal a)||!(e.getParentB() instanceof Animal b)||!(a.level() instanceof ServerLevel level))return;
        BlockPos h=reaching(level,a.position());
        if(h==null||leaves(level,h)<LEAVES)return;
        double chance=level.getBlockState(h).getValue(DreamBlocks.FULL)?FULL_TWINS:TWINS;
        if(level.random.nextDouble()>=chance)return;
        AgeableMob twin=a.getBreedOffspring(level,b);
        if(twin==null)return;
        twin.setBaby(true);
        twin.moveTo(a.getX(),a.getY(),a.getZ(),a.getYRot(),0);
        level.addFreshEntityWithPassengers(twin);
        level.sendParticles(ParticleTypes.HEART,a.getX(),a.getY()+.8,a.getZ(),4,.4,.3,.4,0);
        level.sendParticles(Veilborne.DREAM_POLLEN.get(),a.getX(),a.getY()+.6,a.getZ(),12,.5,.4,.5,.01);
    }
    @SubscribeEvent public static void feed(PlayerInteractEvent.EntityInteract e){
        ItemStack stack=e.getItemStack();
        if(!stack.is(Veilborne.DREAM_LEAVES_ITEM.get())||!(e.getTarget() instanceof Animal a))return;
        if(a.level().isClientSide){e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);return;}
        boolean fed=false;
        if(a.isBaby()){a.ageUp(AgeableMob.getSpeedUpSecondsWhenFeeding(-a.getAge()),true);fed=true;}
        else if(a.getAge()==0&&a.canFallInLove()){a.setInLove(e.getEntity());fed=true;}
        if(!fed)return;
        if(!e.getEntity().getAbilities().instabuild)stack.shrink(1);
        a.level().playSound(null,a.blockPosition(),SoundEvents.AZALEA_LEAVES_BREAK,SoundSource.NEUTRAL,.8f,1.3f);
        ((ServerLevel)a.level()).sendParticles(Veilborne.DREAM_POLLEN.get(),a.getX(),a.getY()+a.getBbHeight()*.7,a.getZ(),6,.3,.2,.3,.01);
        e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);
    }
    private DreamHeart(){}
}
