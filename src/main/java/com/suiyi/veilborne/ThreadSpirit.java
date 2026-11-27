package com.suiyi.veilborne;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 线灵: a snarl of scarlet thread around a bead of gold light, woken when an anchor gathers the pocket's loose time. A few
 * come, they drift after the nearest person and nip (half a heart, now and then), and once the anchor has gathered the
 * pocket into silk, or after half a minute, or when the weather ends, they come apart into plain thread. Drawn by ThreadRenderer as loops of light; the entity itself only carries the behaviour.
 */
public class ThreadSpirit extends Monster {
    public static final int LIFE=600;
    private int nip;
    public ThreadSpirit(EntityType<? extends ThreadSpirit> type,Level level){super(type,level);setNoGravity(true);xpReward=1;}
    public static AttributeSupplier.Builder attributes(){
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,4).add(Attributes.ATTACK_DAMAGE,1).add(Attributes.MOVEMENT_SPEED,.3).add(Attributes.FOLLOW_RANGE,18).add(Attributes.FLYING_SPEED,.3);
    }
    @Override protected void registerGoals(){}
    @Override public void aiStep(){
        super.aiStep();
        if(level().isClientSide)return;
        if(nip>0)nip--;
        Player target=level().getNearestPlayer(this,16);
        if(target!=null&&(target.isCreative()||target.isSpectator()))target=null;
        double t=tickCount*.11+getId();
        Vec3 wobble=new Vec3(Math.sin(t*1.3),Math.sin(t*1.9)*.6,Math.cos(t*1.1)).scale(.05);
        Vec3 want;
        if(target!=null){
            Vec3 to=target.position().add(0,1.1,0).subtract(position());
            want=to.normalize().scale(Math.min(.22,to.length()*.08)).add(wobble);
            getLookControl().setLookAt(target);
            if(to.length()<1.2&&nip==0){doHurtTarget(target);nip=36;playSound(SoundEvents.AMETHYST_BLOCK_HIT,.5f,1.8f);}
        }else want=wobble.add(0,Math.sin(t*.4)*.02,0);
        setDeltaMovement(getDeltaMovement().scale(.86).add(want.scale(.14)));
    }
    @Override public void tick(){
        super.tick();
        if(level().isClientSide)return;
        var d=ThreadData.get((ServerLevel)level());
        // Woken by the loose time in the pocket: once that is gathered into silk there is nothing left to hold them together.
        if(tickCount>LIFE||!d.active||d.has(ThreadData.SEWN_UP))unravel();
    }
    /** It lets go of itself: a puff of red and a single plain thread. */
    public void unravel(){
        if(level() instanceof ServerLevel s){
            s.sendParticles(new DustParticleOptions(new org.joml.Vector3f(.9f,.15f,.18f),1f),getX(),getY()+.3,getZ(),16,.3,.3,.3,.02);
            s.playSound(null,blockPosition(),Veilborne.THREAD_SNAP.get(),SoundSource.HOSTILE,.4f,1.6f);
        }
        discard();
    }
    @Override protected void dropCustomDeathLoot(ServerLevel level,DamageSource source,boolean recentlyHit){
        super.dropCustomDeathLoot(level,source,recentlyHit);
        spawnAtLocation(new ItemStack(Veilborne.SCARLET_THREAD.get()));
    }
    @Override public boolean causeFallDamage(float distance,float multiplier,DamageSource source){return false;}
    @Override protected void checkFallDamage(double y,boolean ground,net.minecraft.world.level.block.state.BlockState state,net.minecraft.core.BlockPos pos){}
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override protected SoundEvent getAmbientSound(){return Veilborne.THREAD_SPIRIT.get();}
    @Override protected SoundEvent getHurtSound(DamageSource source){return SoundEvents.WOOL_BREAK;}
    @Override protected SoundEvent getDeathSound(){return Veilborne.THREAD_SNAP.get();}
    @Override protected float getSoundVolume(){return .6f;}
    @Override public int getAmbientSoundInterval(){return 90;}
}
