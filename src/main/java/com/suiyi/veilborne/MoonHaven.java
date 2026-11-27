package com.suiyi.veilborne;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * What the moon-husk bloom leaves for the nights after it. A silver flower lantern holds a little of that moonlight: no
 * monster comes out of the dark within {@link #RADIUS} blocks of one (spawners, eggs and summons are not the dark, and still
 * work). Moon dew brews into a potion of moonveil: under it, monsters only notice you from a quarter of their usual reach,
 * and one that is already after you loses you once you are {@link #SHAKE_OFF} blocks away.
 */
public final class MoonHaven {
    public static final double RADIUS=24,SHAKE_OFF=10,VISIBILITY=.25;
    @SubscribeEvent public static void spawn(MobSpawnEvent.PositionCheck e){
        MobSpawnType type=e.getSpawnType();
        if(type!=MobSpawnType.NATURAL&&type!=MobSpawnType.CHUNK_GENERATION)return;
        if(e.getEntity().getType().getCategory()!=MobCategory.MONSTER)return;
        if(e.getLevel().getLevel() instanceof ServerLevel level&&sheltered(level,e.getX(),e.getY(),e.getZ()))e.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
    }
    public static boolean sheltered(ServerLevel level,double x,double y,double z){
        return VeilWards.near(level,VeilWards.HAVEN,new net.minecraft.world.phys.Vec3(x,y,z),RADIUS);
    }
    @SubscribeEvent public static void visibility(LivingEvent.LivingVisibilityEvent e){
        if(e.getLookingEntity() instanceof Enemy&&e.getEntity().hasEffect(Veilborne.MOONVEIL))e.modifyVisibility(VISIBILITY);
    }
    @SubscribeEvent public static void brewing(RegisterBrewingRecipesEvent e){
        e.getBuilder().addMix(Potions.AWKWARD,Veilborne.MOON_DEW.get(),Veilborne.MOONVEIL_POTION);
        e.getBuilder().addMix(Veilborne.MOONVEIL_POTION,Items.REDSTONE,Veilborne.LONG_MOONVEIL_POTION);
    }
    /** 月隐: the moonlight you drank keeps you in its shadow. */
    public static class Moonveil extends MobEffect {
        public Moonveil(){super(MobEffectCategory.BENEFICIAL,0xb9a8f0);}
        @Override public boolean shouldApplyEffectTickThisTick(int duration,int amplifier){return duration%20==0;}
        @Override public boolean applyEffectTick(LivingEntity entity,int amplifier){
            if(entity.level() instanceof ServerLevel level)
                for(Monster m:level.getEntitiesOfClass(Monster.class,entity.getBoundingBox().inflate(32)))
                    if(m.getTarget()==entity&&m.distanceTo(entity)>SHAKE_OFF){m.setTarget(null);m.getNavigation().stop();}
            return true;
        }
    }
    private MoonHaven(){}
}
