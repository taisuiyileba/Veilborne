package com.suiyi.veilborne.client;

import com.suiyi.veilborne.*;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;

/** Exercises a whole naturally-triggered weather, without skipping any server ticks. */
public final class LifecyclePreview {
    private static final String WORLD="Veilborne Lifecycle QA";
    private static boolean started,configured;
    private static volatile boolean seen,done;
    private static int flags,ticks,exitTicks;
    private static String capture;
    private static Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("lifecycle.log");}
    private static synchronized void report(String s){
        System.out.println(s);try{Files.writeString(file(),s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}
    }
    private static void check(String label,boolean condition){report((condition?"PASS ":"FAIL ")+label);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        var mc=Minecraft.getInstance();
        if(!started&&mc.screen!=null&&mc.getOverlay()==null){
            started=true;try{Files.writeString(file(),"Full lifecycle QA\n");}catch(Exception ex){throw new RuntimeException(ex);}
            mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.renderDistance().set(8);mc.options.simulationDistance().set(5);
            var flow=mc.createWorldOpenFlows();
            // A fresh world each run: an old one may still hold another weather that was under way when it last closed.
            Path dir=mc.gameDirectory.toPath().resolve("saves/"+WORLD);
            if(Files.exists(dir))try(var walk=Files.walk(dir)){for(Path p:walk.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(p);}catch(java.io.IOException ex){throw new RuntimeException(ex);}
            flow.createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(8301282L,true,false),WorldPresets::createNormalWorldDimensions,mc.screen);
        }
        if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
        if(!configured){
            configured=true;mc.setScreen(null);
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var level=server.overworld();var p=level.players().get(0);
                var pos=level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.blockPosition());
                p.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();
                level.setDayTime(18000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
                var d=TideData.get(level);d.active=false;d.quietTicks=1;d.setDirty();
                TideConfig.naturalOnly(0);TideConfig.DURATION.set(120);
            });
        }
        ticks++;mc.player.setYRot(0);mc.player.setXRot(-28);mc.player.yRotO=0;mc.player.xRotO=-28;
        if(ticks%5==0&&!done)mc.getSingleplayerServer().execute(()->{
            var level=mc.getSingleplayerServer().overworld();if(level.players().isEmpty())return;var p=level.players().get(0);var d=TideData.get(level);
            if(d.active&&!seen){seen=true;check("natural_weather_started",true);report("NATURAL seed="+d.seed+" duration="+d.duration);}
            if(!d.active){
                if(seen){
                    check("all_encounters_executed",(d.flags&31)==31);
                    check("observations_complete",TideService.records(p)==31);
                    check("scale_collected",p.getInventory().contains(new ItemStack(Veilborne.WHALE_SCALE.get())));
                    check("conch_collected",p.getInventory().contains(new ItemStack(Veilborne.CONCH.get())));
                    check("visitor_cleaned_up",level.getEntities().getAll().iterator().hasNext()&&java.util.stream.StreamSupport.stream(level.getEntities().getAll().spliterator(),false).noneMatch(v->v instanceof TideSkiff));
                    check("no_weather_damage",p.getHealth()>=19.9f);done=true;capture="lifecycle-complete.png";
                }return;
            }
            double progress=d.progress();
            if(d.age>=360&&(flags&1)==0){
                flags|=1;int before=d.age;level.setDayTime(level.getDayTime()+24000);
                check("day_jump_preserves_timeline",d.active&&d.age==before);
            }
            if(progress>.28&&progress<.33){
                for(var entity:level.getEntities().getAll())if(entity instanceof ItemEntity item&&item.getItem().is(Veilborne.BOTTLE.get()))p.teleportTo(item.getX(),item.getY(),item.getZ());
                if(p.getInventory().contains(new ItemStack(Veilborne.BOTTLE.get()))&&(flags&2)==0){
                    flags|=2;int slot=p.getInventory().findSlotMatchingItem(new ItemStack(Veilborne.BOTTLE.get()));
                    if(slot>=0){p.getInventory().selected=slot;Veilborne.BOTTLE.get().use(level,p,InteractionHand.MAIN_HAND);}
                    check("bottle_opened",p.getInventory().contains(new ItemStack(Veilborne.TIDE_BOTTLE.get())));
                }
            }
            if(progress>.35&&progress<.39&&(flags&4)==0){
                for(var entity:level.getEntities().getAll())if(entity instanceof TideSkiff boat){
                    flags|=4;p.teleportTo(boat.getX(),boat.getY(),boat.getZ());boat.interact(p,InteractionHand.MAIN_HAND);check("skiff_boarded",p.isPassenger());break;
                }
            }
            if(progress>.4&&(flags&8)==0){flags|=8;p.stopRiding();check("skiff_dismounted",!p.isPassenger());}
            if(!d.vortex.equals(BlockPos.ZERO)){
                int age=d.age-d.vortexAt;
                if(age>=30&&age<50&&(flags&16)==0){flags|=16;p.teleportTo(d.vortex.getX()+.5,d.vortex.getY(),d.vortex.getZ()+.5);check("warning_precedes_lift",!p.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING));}
                if(age>=170&&(flags&32)==0){flags|=32;check("vortex_lifts_player",p.getY()-d.vortex.getY()>3);check("vortex_grants_safe_descent",p.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING));}
            }
            if(progress>.62&&progress<.63)capture="lifecycle-whale.png";
            if(progress>.79&&progress<.88){
                for(var entity:level.getEntities().getAll())if(entity instanceof ItemEntity item&&(item.getItem().is(Veilborne.WHALE_SCALE.get())||item.getItem().is(Veilborne.CONCH.get())))p.teleportTo(item.getX(),item.getY(),item.getZ());
            }
            if(progress>.89&&(flags&64)==0){
                flags|=64;p.getInventory().setItem(0,new ItemStack(Veilborne.MANTLE.get()));p.getInventory().selected=0;
                Veilborne.MANTLE.get().use(level,p,InteractionHand.MAIN_HAND);
                check("mantle_works",p.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING)&&p.getCooldowns().isOnCooldown(Veilborne.MANTLE.get()));
            }
        });
        if(done&&++exitTicks==120){check("client_effects_faded",TideClient.strength()==0);mc.stop();}
        if(ticks>3400&&!done){report("FAIL lifecycle_timeout");mc.stop();}
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){
        if(capture!=null){String name=capture;capture=null;var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),c->{});}
    }
}
