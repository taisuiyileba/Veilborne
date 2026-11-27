package com.suiyi.veilborne;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * 镜兽: a small glass gecko that lives on the low cracked shards. Left alone it only skitters over the glass.
 * Walk up to its shard and it rings a warning; stay, and the shard bursts and a few of them come after you for
 * a little while before they crack apart on their own. Killed, they leave mirror sand.
 */
public class MirrorBeast extends Monster {
    public static final int LIFE=600;
    public MirrorBeast(EntityType<? extends MirrorBeast> type,Level level){super(type,level);xpReward=2;}
    public static AttributeSupplier.Builder attributes(){
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,6).add(Attributes.ATTACK_DAMAGE,2).add(Attributes.MOVEMENT_SPEED,.31).add(Attributes.FOLLOW_RANGE,18).add(Attributes.ARMOR,2);
    }
    @Override protected void registerGoals(){
        goalSelector.addGoal(0,new FloatGoal(this));
        goalSelector.addGoal(2,new MeleeAttackGoal(this,1.25,true));
        goalSelector.addGoal(5,new WaterAvoidingRandomStrollGoal(this,.9));
        goalSelector.addGoal(6,new LookAtPlayerGoal(this,Player.class,10));
        goalSelector.addGoal(7,new RandomLookAroundGoal(this));
        targetSelector.addGoal(1,new HurtByTargetGoal(this));
        targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Player.class,true));
    }
    @Override public void tick(){
        super.tick();
        if(level().isClientSide)return;
        MirrorData d=MirrorData.get((ServerLevel)level());
        if(tickCount>LIFE||!d.active)crack();
    }
    /** Time is up, or the mirror is gone: it cracks apart where it stands and leaves nothing. */
    public void crack(){
        if(level() instanceof ServerLevel s){
            s.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,Blocks.GLASS.defaultBlockState()),getX(),getY()+.3,getZ(),14,.25,.2,.25,.05);
            s.playSound(null,blockPosition(),SoundEvents.AMETHYST_CLUSTER_BREAK,SoundSource.HOSTILE,.8f,1.4f);
        }
        discard();
    }
    @Override public void die(DamageSource source){
        super.die(source);
        if(level() instanceof ServerLevel s&&getPersistentData().contains(MirrorService.BEAST_SHARD)&&source.getEntity() instanceof Player)
            MirrorService.beastKilled(s,getPersistentData().getInt(MirrorService.BEAST_SHARD));
    }
    @Override protected void dropCustomDeathLoot(ServerLevel level,DamageSource source,boolean recentlyHit){
        super.dropCustomDeathLoot(level,source,recentlyHit);
        spawnAtLocation(new ItemStack(Veilborne.MIRROR_SAND.get(),1+random.nextInt(2)));
    }
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override protected SoundEvent getAmbientSound(){return SoundEvents.AMETHYST_BLOCK_CHIME;}
    @Override protected SoundEvent getHurtSound(DamageSource source){return SoundEvents.AMETHYST_BLOCK_HIT;}
    @Override protected SoundEvent getDeathSound(){return SoundEvents.AMETHYST_CLUSTER_BREAK;}
    @Override protected float getSoundVolume(){return .5f;}
    @Override public float getVoicePitch(){return 1.5f+random.nextFloat()*.3f;}
    @Override public boolean shouldDropExperience(){return true;}
}
