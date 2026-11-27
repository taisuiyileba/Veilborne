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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;

/**
 * -PtidePreview=moonlife: a whole naturally triggered 月骸花潮 at the shortest legal duration, run tick by tick.
 * The player walks beside the procession, walks into it, bottles moon dew, stands in the gold ring, guesses a
 * false moon and then the true one, and collects the keepsakes; every rule is checked and logged.
 */
public final class MoonLifecyclePreview {
    private static final String WORLD="Veilborne Moon Lifecycle QA";
    private static boolean started,configured;
    private static volatile boolean seen,done;
    private static int flags,ticks,exitTicks,escortTicks,gazeTicks;
    private static String capture;
    private static Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("moon-lifecycle.log");}
    private static synchronized void report(String s){
        System.out.println(s);try{Files.writeString(file(),s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}
    }
    private static void check(String label,boolean condition){report((condition?"PASS ":"FAIL ")+label);}
    private static MoonProcession procession(net.minecraft.server.level.ServerLevel level){
        for(var e:level.getEntities().getAll())if(e instanceof MoonProcession p)return p;
        return null;
    }
    private static void lookAt(net.minecraft.server.level.ServerPlayer p,Vec3 dir){
        float yaw=(float)Math.toDegrees(Math.atan2(-dir.x,dir.z)),pitch=(float)-Math.toDegrees(Math.asin(Math.max(-1,Math.min(1,dir.normalize().y))));
        p.setYRot(yaw);p.setXRot(pitch);p.setYHeadRot(yaw);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        var mc=Minecraft.getInstance();
        if(!started&&mc.screen!=null&&mc.getOverlay()==null){
            started=true;try{Files.writeString(file(),"Moon-husk bloom lifecycle QA\n");}catch(Exception ex){throw new RuntimeException(ex);}
            mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.renderDistance().set(8);mc.options.simulationDistance().set(6);
            Path dir=mc.gameDirectory.toPath().resolve("saves/"+WORLD);
            if(Files.exists(dir))try(var walk=Files.walk(dir)){for(Path p:walk.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(p);}catch(java.io.IOException ex){throw new RuntimeException(ex);}
            mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.SURVIVAL,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(8301282L,true,false),WorldPresets::createNormalWorldDimensions,mc.screen);
        }
        if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
        if(!configured){
            configured=true;mc.setScreen(null);
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var level=server.overworld();var p=level.players().get(0);
                BlockPos pos=level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.blockPosition()).above(4);
                // A broad, flat, open meadow so every encounter has room.
                for(int x=-60;x<=60;x++)for(int z=-60;z<=60;z++){
                    for(int y=0;y<=14;y++)level.setBlock(pos.offset(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
                    level.setBlock(pos.offset(x,-1,z),net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(),2);
                }
                p.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);p.getInventory().clearContent();
                level.setDayTime(13500);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
                var d=TideData.get(level);d.active=false;d.quietTicks=1;d.setDirty();
                TideConfig.naturalOnly(1);TideConfig.MOON_DURATION.set(120);
            });
        }
        ticks++;
        if(ticks%2==0&&!done)mc.getSingleplayerServer().execute(()->{
            var level=mc.getSingleplayerServer().overworld();if(level.players().isEmpty())return;
            var p=level.players().get(0);var d=MoonData.get(level);
            if(d.active&&!seen){seen=true;check("natural_bloom_started_at_night",TideService.moonHours(level));report("NATURAL seed="+d.seed+" duration="+d.duration);}
            if(!d.active){
                if(seen&&!done){
                    int records=TideService.records(p);
                    check("all_six_observations",(records&(32|64|128|256|512|1024))==(32|64|128|256|512|1024));
                    check("dew_collected",p.getInventory().contains(new ItemStack(Veilborne.MOON_DEW.get())));
                    check("shards_collected",p.getInventory().countItem(Veilborne.MOON_SHARD.get())>=2);
                    check("lantern_left_by_the_road",!d.lantern.equals(BlockPos.ZERO)&&level.getBlockState(d.lantern).is(Veilborne.SILVER_LANTERN.get()));
                    check("visitors_cleaned_up",procession(level)==null);
                    check("no_weather_damage",p.getHealth()>=19.9f);
                    check("quiet_period_set",TideData.get(level).quietTicks>=24000);
                    done=true;
                }
                return;
            }
            double prog=d.progress();
            if(d.age>=300&&(flags&1)==0){flags|=1;int before=d.age;level.setDayTime(level.getDayTime()+24000);check("day_jump_preserves_timeline",d.active&&d.age==before);}
            MoonProcession proc=procession(level);
            if(proc!=null&&(flags&2)==0){flags|=2;check("procession_arrived",prog>=.27&&prog<.5);capture="moonlife-procession.png";}
            // Walk beside the bearers at a respectful distance for a while.
            if(proc!=null&&proc.walking()&&(flags&4)==0){
                Vec3 beside=proc.member(proc.path(),0,-6.5);
                p.teleportTo(beside.x,beside.y,beside.z);
                if((escortTicks+=2)>=340){flags|=4;check("escort_recorded",(TideService.records(p)&MoonService.ESCORT)!=0);check("escort_did_not_scatter",proc.walking());}
            }
            // Carry a lantern near the empty place.
            if(proc!=null&&(flags&4)!=0&&(flags&8)==0&&proc.visibilityServer()){
                p.getInventory().setItem(p.getInventory().selected,new ItemStack(Veilborne.SILVER_LANTERN_ITEM.get()));
                Vec3 near=proc.member(proc.path(),MoonProcession.EMPTY,-4);p.teleportTo(near.x,near.y,near.z);
                if((TideService.records(p)&MoonService.EMPTY_PLACE)!=0){flags|=8;check("empty_place_answers_the_lantern",true);p.getInventory().setItem(p.getInventory().selected,ItemStack.EMPTY);}
            }
            // Then walk straight into the line.
            if(proc!=null&&(flags&8)!=0&&(flags&16)==0){
                Vec3 into=proc.member(proc.path(),MoonProcession.LITTER,0);
                // Checked before stepping in again: once scattering, the figures are no longer standing.
                if(proc.state()==MoonProcession.State.SCATTER){flags|=16;check("walking_into_the_line_scatters_it",true);p.teleportTo(into.x+12,into.y,into.z+12);}
                else if(proc.visibilityServer())p.teleportTo(into.x,into.y,into.z);
            }
            if(proc!=null&&(flags&16)!=0&&(flags&32)==0&&proc.state()==MoonProcession.State.GATHER){flags|=32;check("procession_gathers_again",true);}
            // The fallen little moon: three bottles of dew, then the constellation.
            if(!d.littleMoon.equals(BlockPos.ZERO)&&(flags&64)==0&&(flags&16)!=0){
                flags|=64;
                Vec3 base=Vec3.atBottomCenterOf(d.littleMoon);
                p.teleportTo(base.x+1,base.y,base.z);
                p.getInventory().setItem(p.getInventory().selected,new ItemStack(Items.GLASS_BOTTLE,5));
                int got=0;for(int i=0;i<5;i++)if(MoonService.collectDew(p,InteractionHand.MAIN_HAND)&&p.getInventory().countItem(Veilborne.MOON_DEW.get())>got)got=p.getInventory().countItem(Veilborne.MOON_DEW.get());
                check("dew_limited_to_three",got==MoonService.DEW_PER_PLAYER);
            }
            if(!d.littleMoon.equals(BlockPos.ZERO)&&(flags&64)!=0&&(flags&128)==0){
                Vec3 base=Vec3.atBottomCenterOf(d.littleMoon),spot=MoonTimeline.viewpoint(d.seed,base);
                double y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,(int)Math.floor(spot.x),(int)Math.floor(spot.z));
                // The rotation must travel with the teleport: the client's own movement packets overwrite a
                // rotation that is only set on the server.
                Vec3 dir=base.add(0,MoonTimeline.LITTLE_MOON_HEIGHT,0).subtract(spot.x,y+p.getEyeHeight(),spot.z).normalize();
                p.connection.teleport(spot.x,y,spot.z,(float)Math.toDegrees(Math.atan2(-dir.x,dir.z)),(float)-Math.toDegrees(Math.asin(dir.y)));
                if((gazeTicks+=2)>=60){flags|=128;check("constellation_from_the_ring",(TideService.records(p)&MoonService.CONSTELLATION)!=0);capture="moonlife-littlemoon.png";}
            }
            // Three moons: a false guess first, then the true one.
            if(MoonTimeline.threeActive(prog)&&MoonTimeline.split(prog)>.99&&(flags&256)==0){
                flags|=256;
                p.getInventory().setItem(p.getInventory().selected,new ItemStack(Veilborne.MOON_DIAL.get()));
                int wrong=(MoonTimeline.trueSlot(d.seed)+1)%3;
                lookAt(p,MoonTimeline.moonDir(d.seed,wrong,MoonTimeline.split(prog),0));
                MoonService.calibrate(p);
                check("false_moon_guess_is_harmless",!d.broken()&&p.getHealth()>=19.9f);
                lookAt(p,MoonTimeline.moonDir(d.seed,MoonTimeline.trueSlot(d.seed),MoonTimeline.split(prog),0));
                MoonService.calibrate(p);
                check("true_moon_breaks_the_reflections",d.broken()&&(TideService.records(p)&MoonService.TRUE_MOON)!=0);
                capture="moonlife-three.png";
            }
            if(prog>.6&&prog<.74)p.teleportTo(p.getX(),level.getHeight(Heightmap.Types.MOTION_BLOCKING,p.getBlockX(),p.getBlockZ()),p.getZ());
            // Afterwards, gather what was left on the ground.
            if(prog>.8){
                for(var entity:level.getEntities().getAll())if(entity instanceof ItemEntity item&&(item.getItem().is(Veilborne.MOON_SHARD.get())))p.teleportTo(item.getX(),item.getY(),item.getZ());
            }
        });
        if(done&&++exitTicks==140){check("client_effects_faded",MoonClient.strength()==0);mc.stop();}
        if(ticks>3600&&!done){report("FAIL lifecycle_timeout");mc.stop();}
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){
        if(capture!=null){String name=capture;capture=null;var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),c->{});}
    }
}
