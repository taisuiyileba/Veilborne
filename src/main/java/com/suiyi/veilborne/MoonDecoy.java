package com.suiyi.veilborne;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** 月纱幻影: a moonlit likeness of its owner that draws hostile attention for a few seconds, then thins away. */
public class MoonDecoy extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER=SynchedEntityData.defineId(MoonDecoy.class,EntityDataSerializers.OPTIONAL_UUID);
    public static final int LIFE=160,FADE=24;
    private int life;
    public MoonDecoy(EntityType<? extends MoonDecoy> type,Level level){super(type,level);noPhysics=true;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){b.define(OWNER,Optional.empty());}
    public void setOwner(UUID id){entityData.set(OWNER,Optional.ofNullable(id));}
    public Optional<UUID> owner(){return entityData.get(OWNER);}
    public float visibility(float partial){
        float t=tickCount+partial;
        return Mth.clamp(Math.min(t/12f,(LIFE-t)/FADE),0,1);
    }
    @Override public void tick(){
        super.tick();
        if(level().isClientSide){Veilborne.decoyAmbience.accept(this);return;}
        if(++life>=LIFE){discard();return;}
        if(life%5!=0)return;
        var owner=owner().map(level()::getPlayerByUUID).orElse(null);
        // Monsters that were after the owner lose the trail and come to inspect the likeness instead.
        for(Monster m:level().getEntitiesOfClass(Monster.class,new AABB(blockPosition()).inflate(18))){
            if(owner!=null&&m.getTarget()==owner){m.setTarget(null);}
            if(m.getTarget()==null&&m.distanceToSqr(this)>2.5)m.getNavigation().moveTo(getX(),getY(),getZ(),1.0);
            m.getLookControl().setLookAt(this,30,30);
        }
    }
    @Override public boolean isPickable(){return false;}
    @Override public boolean isPushable(){return false;}
    @Override protected void readAdditionalSaveData(CompoundTag t){life=t.getInt("Life");if(t.hasUUID("Owner"))setOwner(t.getUUID("Owner"));}
    @Override protected void addAdditionalSaveData(CompoundTag t){t.putInt("Life",life);owner().ifPresent(id->t.putUUID("Owner",id));}
}
