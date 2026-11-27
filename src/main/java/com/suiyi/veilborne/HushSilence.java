package com.suiyi.veilborne;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.GameEventTags;
import net.minecraft.util.SpawnUtil;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * What the silent daylight leaves in the world, and the one thing it punishes. Whoever holds a silent lantern cannot be
 * heard: nothing they do reaches a sculk sensor, a shrieker, a warden or an allay. Near a still candle nothing at all can be
 * heard. So the deep dark and its cities can be walked in.
 *
 * And the choice nobody has to make: in the weather's silence, ring a bell, strike a note block, blow a horn or start a
 * record, and something under the ground hears it. A little later a warden climbs out beside whoever made the sound. It
 * goes back down when the first bird sings; if they are still standing then, or if they beat it, they are given what it
 * brought up with it.
 */
public final class HushSilence {
    public static final double CANDLE_RANGE=6, REWARD_RANGE=96;
    public static final int DELAY=100, DIG_TICKS=110, ECHO_SHARDS=4;
    public static final String TAG="VeilborneListener", DIG="VeilborneDigAt";

    static Player source(Entity cause){
        if(cause instanceof Player p)return p;
        if(cause instanceof Projectile pr&&pr.getOwner() instanceof Player p)return p;
        return null;
    }
    @SubscribeEvent public static void heard(VanillaGameEvent e){
        if(!(e.getLevel() instanceof ServerLevel level))return;
        var ev=e.getVanillaEvent();
        Player p=source(e.getCause());
        boolean vibration=ev.is(GameEventTags.VIBRATIONS)||ev.is(GameEventTags.WARDEN_CAN_LISTEN);
        if(vibration&&p!=null&&HushService.silentLantern(p)){e.setCanceled(true);return;}
        if(vibration&&VeilWards.near(level,VeilWards.HUSH,e.getEventPosition(),CANDLE_RANGE)){e.setCanceled(true);return;}
        if(p instanceof ServerPlayer sp&&(ev.is(GameEvent.INSTRUMENT_PLAY.key())||ev.is(GameEvent.JUKEBOX_PLAY.key())))loud(sp,e.getEventPosition());
    }
    @SubscribeEvent public static void struck(PlayerInteractEvent.RightClickBlock e){
        if(!(e.getEntity() instanceof ServerPlayer p))return;
        var block=p.level().getBlockState(e.getPos()).getBlock();
        if(block instanceof BellBlock||block instanceof NoteBlock)loud(p,Vec3.atCenterOf(e.getPos()));
    }
    /** In the silence, only while the colour is gone and before the first bird. */
    public static boolean silentHours(double p){return HushTimeline.hush(p)>.9&&p<HushTimeline.BIRD;}
    /** Someone made a sound on purpose. The first one in each weather is heard. */
    public static void loud(ServerPlayer p,Vec3 at){
        ServerLevel level=p.serverLevel();
        if(!level.dimension().equals(Level.OVERWORLD))return;
        HushData d=HushData.get(level);
        if(!d.active||d.strength(p.getX(),p.getZ())<.25||!silentHours(d.progress())||d.has(HushData.LISTENER)||HushService.silentLantern(p))return;
        if(level.getDifficulty()==Difficulty.PEACEFUL){p.displayClientMessage(Component.translatable("message.veilborne.loud_peaceful"),true);return;}
        d.takeFlag(HushData.LISTENER);d.listenerAt=d.age;d.listener=p.getUUID();d.setDirty();
        level.playSound(null,BlockPos.containing(at),Veilborne.HUSH_HEART.get(),SoundSource.HOSTILE,1.4f,.7f);
        VeilService.cue(level,new VeilCue(VeilCue.LOUD,at,0),96);
        p.displayClientMessage(Component.translatable("message.veilborne.loud"),false);
    }
    /** The listener's part of each tick of the weather. */
    public static void tick(ServerLevel level,HushData d,double p){
        if(!d.has(HushData.LISTENER)||d.has(HushData.LISTENER_GONE))return;
        if(!d.has(HushData.LISTENER_UP)&&d.age-d.listenerAt>=DELAY){
            d.takeFlag(HushData.LISTENER_UP);
            ServerPlayer who=d.listener==null?null:level.getServer().getPlayerList().getPlayer(d.listener);
            if(who==null||who.level()!=level||who.isSpectator()||!who.isAlive()){d.takeFlag(HushData.LISTENER_GONE);return;}
            var warden=SpawnUtil.trySpawnMob(EntityType.WARDEN,MobSpawnType.TRIGGERED,level,who.blockPosition(),24,9,6,SpawnUtil.Strategy.ON_TOP_OF_COLLIDER);
            if(warden.isEmpty()){d.takeFlag(HushData.LISTENER_GONE);who.displayClientMessage(Component.translatable("message.veilborne.listener_none"),true);return;}
            warden.get().getPersistentData().putBoolean(TAG,true);
            warden.get().increaseAngerAt(who,100,false);
            who.displayClientMessage(Component.translatable("message.veilborne.listener_up"),true);
        }
        if(d.has(HushData.LISTENER_UP)&&p>=HushTimeline.BIRD)dismiss(level,d,true);
    }
    /** The first bird: it goes back down, and whoever called it up and is still standing is given what it brought. */
    public static void dismiss(ServerLevel level,HushData d,boolean reward){
        if(!d.has(HushData.LISTENER)||!d.takeFlag(HushData.LISTENER_GONE))return;
        for(Warden w:listeners(level)){
            w.setNoAi(true);w.setPose(Pose.DIGGING);w.getPersistentData().putLong(DIG,level.getGameTime());
            level.playSound(null,w.blockPosition(),SoundEvents.WARDEN_DIG,SoundSource.HOSTILE,5,1);
        }
        ServerPlayer who=d.listener==null?null:level.getServer().getPlayerList().getPlayer(d.listener);
        if(reward&&d.has(HushData.LISTENER_UP)&&who!=null&&who.isAlive()&&who.level()==level&&who.position().distanceTo(Vec3.atCenterOf(d.center))<d.radius+REWARD_RANGE)reward(who,d);
    }
    static void reward(ServerPlayer p,HushData d){
        if(VeilGrade.done(p,VeilGrade.HUSH,d.seed,VeilGrade.HUSH_LISTENER))return;
        VeilGrade.deed(p,VeilGrade.HUSH,d.seed,VeilGrade.HUSH_LISTENER);
        TideService.give(p,new ItemStack(Items.ECHO_SHARD,ECHO_SHARDS));
        TideService.give(p,new ItemStack(Veilborne.DAYLIGHT_WAX.get(),2));
        p.displayClientMessage(Component.translatable("message.veilborne.listener_survived"),false);
    }
    public static List<Warden> listeners(ServerLevel level){
        return level.getEntities(EntityType.WARDEN,w->w.getPersistentData().getBoolean(TAG)).stream().map(w->(Warden)w).toList();
    }
    /** Wardens that have finished climbing back down. */
    public static void sweep(ServerLevel level){
        for(Warden w:listeners(level)){
            long at=w.getPersistentData().getLong(DIG);
            if(at!=0&&level.getGameTime()-at>=DIG_TICKS)w.discard();
        }
    }
    @SubscribeEvent public static void beaten(LivingDeathEvent e){
        if(!(e.getEntity() instanceof Warden w)||!w.getPersistentData().getBoolean(TAG)||!(w.level() instanceof ServerLevel level))return;
        HushData d=HushData.get(level.getServer().overworld());
        if(!d.active)return;
        if(e.getSource().getEntity() instanceof ServerPlayer killer)reward(killer,d);
        UUID who=d.listener;
        ServerPlayer caller=who==null?null:level.getServer().getPlayerList().getPlayer(who);
        if(caller!=null&&caller.isAlive())reward(caller,d);
        d.takeFlag(HushData.LISTENER_GONE);
    }
    private HushSilence(){}
}
