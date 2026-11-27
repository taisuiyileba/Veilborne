package com.suiyi.veilborne.client;

import com.suiyi.veilborne.*;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;

/**
 * -PtidePreview=hushlife: a whole naturally triggered 无声白昼 at a short duration, run tick by tick. The player stands in
 * a meadow by a cottage with a torch in hand. The grey arrives and the journal with it; the sounds go in order and the
 * weather's own stay; the empty coat comes to the cottage door and is answered with the torch; the jay's feather hangs in
 * front of them; the procession is met and answered, and leaves its gift; the one bell is found and opens its island of
 * sound; under the covered sun they look straight at the black sun and raise the torch; they watch the white line and hear
 * the thunder come late; the bell comes down, the feather falls after the bird; colour and sound come back. Every rule is
 * checked and logged to run/hush-lifecycle.log, with screenshots at the grey, the totality, the line and the return.
 */
public final class HushLifecyclePreview {
    private static final String WORLD="Veilborne Hush Lifecycle QA";
    private static final int DURATION=240;
    private static boolean started,configured;
    private static volatile boolean seen,done;
    private static volatile int flags,clientFlags;
    private static int ticks,exitTicks,lastFlash=-100,totalAt;
    private static BlockPos home=BlockPos.ZERO;
    private static String capture;
    private static volatile String pending;
    private static int holdUntil,captureAt;
    private static final int DAY=1,GREY=2,COAT_SEEN=4,COAT_ANSWERED=8,COAT_PAID=16,FEATHER_SEEN=32,PROC_SEEN=64,PROC_ANSWERED=128,
        PROC_GIFT=256,BELL_SEEN=512,BELL_FOUND=1024,BELL_LEFT=2048,TOTALITY=4096,HEARD_DONE=8192,LINE=16384,BELL_TAKEN=32768,
        FEATHER_TAKEN=65536,GREY_SHOT=131072,TOTAL_SHOT=262144,LINE_SHOT=524288,RETURN_SHOT=1048576,AT_HOME=1<<21;
    private static final int C_GAINS=1,C_ISLAND=2,C_COVER=4;
    private static Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("hush-lifecycle.log");}
    private static synchronized void report(String s){
        System.out.println(s);try{Files.writeString(file(),s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}
    }
    private static void check(String label,boolean condition){report((condition?"PASS ":"FAIL ")+label);}
    private static boolean has(int f){return (flags&f)!=0;}
    private static void place(ServerPlayer p,Vec3 at,Vec3 lookAt){
        Vec3 dir=lookAt.subtract(at.add(0,p.getEyeHeight(),0)).normalize();
        p.connection.teleport(at.x,at.y,at.z,(float)Math.toDegrees(Math.atan2(-dir.x,dir.z)),(float)-Math.toDegrees(Math.asin(Math.max(-1,Math.min(1,dir.y)))));
    }
    private static <T extends net.minecraft.world.entity.Entity> T first(ServerLevel level,Class<T> type){
        for(var e:level.getEntities().getAll())if(type.isInstance(e)&&!e.isRemoved())return type.cast(e);
        return null;
    }
    private static long count(ServerLevel level,Class<?> type){
        long n=0;for(var e:level.getEntities().getAll())if(type.isInstance(e)&&!e.isRemoved())n++;return n;
    }
    private static ItemEntity item(ServerLevel level,Item kind,Vec3 near,double range){
        for(var e:level.getEntities().getAll())if(e instanceof ItemEntity i&&i.isAlive()&&i.getItem().is(kind)&&i.position().distanceTo(near)<range)return i;
        return null;
    }
    private static boolean recorded(ServerPlayer p,int bit){return (TideService.records2(p)&bit)!=0;}
    /** Raises the torch, as a right click would; spaced past the cooldown. */
    private static boolean raise(ServerPlayer p,int age){
        if(age-lastFlash<HushService.FLASH_COOLDOWN+2)return false;
        lastFlash=age;
        return HushService.flash(p);
    }
    /** Asks for a screenshot once the client has caught up with where the player was just put; holds the run till then. */
    private static void shot(String name,int age){pending=name;holdUntil=age+10;}
    private static Vec3 homeSpot(){return Vec3.atBottomCenterOf(home).add(0,0,4);}
    private static float gain(net.minecraft.sounds.SoundEvent sound,SoundSource source,Vec3 at){
        return HushHooks.gain(new SimpleSoundInstance(sound,source,1,1,RandomSource.create(1),at.x,at.y,at.z));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        var mc=Minecraft.getInstance();
        if(!started&&mc.screen!=null&&mc.getOverlay()==null){
            started=true;try{Files.writeString(file(),"Silent Daylight lifecycle QA\n");}catch(Exception ex){throw new RuntimeException(ex);}
            mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.renderDistance().set(10);mc.options.simulationDistance().set(8);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            Path dir=mc.gameDirectory.toPath().resolve("saves/"+WORLD);
            if(Files.exists(dir))try(var walk=Files.walk(dir)){for(Path p:walk.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(p);}catch(java.io.IOException ex){throw new RuntimeException(ex);}
            mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.SURVIVAL,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261127L,true,false),WorldPresets::createNormalWorldDimensions,mc.screen);
        }
        if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
        if(!configured){
            configured=true;mc.setScreen(null);
            mc.getSingleplayerServer().execute(HushLifecyclePreview::build);
        }
        ticks++;
        // What only the client can tell: how loud things are, and how much of the sun it sees covered.
        if(HushClient.active()){
            double p=HushClient.progress(1);
            Vec3 me=mc.player.position();
            if((clientFlags&C_GAINS)==0&&p>3.3/12&&HushClient.strength()>.95&&HushClient.islandHere()==0){
                clientFlags|=C_GAINS;
                check("music_and_wind_silenced",gain(SoundEvents.MUSIC_GAME.value(),SoundSource.MUSIC,me)<.01&&gain(SoundEvents.WEATHER_RAIN,SoundSource.WEATHER,me)<.01);
                check("animals_and_blocks_hushed",gain(SoundEvents.COW_AMBIENT,SoundSource.NEUTRAL,me)<.02&&gain(SoundEvents.STONE_BREAK,SoundSource.BLOCKS,me)<.2);
                check("own_footsteps_hushed_last",gain(SoundEvents.GRASS_STEP,SoundSource.PLAYERS,me)<.05);
                check("hostile_voice_and_weather_sounds_kept",gain(SoundEvents.ZOMBIE_AMBIENT,SoundSource.HOSTILE,me)==1&&gain(SoundEvents.PLAYER_HURT,SoundSource.VOICE,me)==1
                    &&gain(Veilborne.HUSH_BELL.get(),SoundSource.AMBIENT,me)==1);
            }
            if((clientFlags&C_ISLAND)==0&&HushClient.islandHere()>.9){
                clientFlags|=C_ISLAND;
                check("island_of_sound_gives_the_world_back",gain(SoundEvents.STONE_BREAK,SoundSource.BLOCKS,me)>.9&&gain(SoundEvents.COW_AMBIENT,SoundSource.NEUTRAL,me)>.9);
            }
            if((clientFlags&C_COVER)==0&&HushTimeline.totality(p)>.9){
                clientFlags|=C_COVER;
                check("client_sees_the_sun_covered",HushClient.covered>.85);
                report(String.format("covered %.3f at totality",HushClient.covered));
            }
        }
        if(pending!=null){if(captureAt==0)captureAt=ticks+6;else if(ticks>=captureAt){capture=pending;pending=null;captureAt=0;}}
        if(!done)mc.getSingleplayerServer().execute(HushLifecyclePreview::step);
        if(done&&++exitTicks==160){
            check("client_effects_faded",HushClient.strength()==0);
            check("sound_given_back",gain(SoundEvents.STONE_BREAK,SoundSource.BLOCKS,mc.player.position())>.99&&gain(SoundEvents.GRASS_STEP,SoundSource.PLAYERS,mc.player.position())>.99);
            check("clouds_move_again",HushClient.cloudSpeed==1);
            mc.getSingleplayerServer().execute(()->{
                var level=mc.getSingleplayerServer().overworld();
                check("visitors_gone",count(level,HushProcession.class)==0&&count(level,HushCoat.class)==0);
            });
        }
        if(done&&exitTicks==170)mc.stop();
        if(ticks>DURATION*20+2600&&!done){report("FAIL lifecycle_timeout");mc.stop();}
    }

    private static void build(){
        var server=Minecraft.getInstance().getSingleplayerServer();var level=server.overworld();var p=level.players().get(0);
        BlockPos pos=level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.blockPosition()).above(4);
        // An open meadow, a cottage whose door faces the player, and a few trees for the bell to hang from.
        for(int x=-64;x<=64;x++)for(int z=-64;z<=64;z++){
            for(int y=0;y<=18;y++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            level.setBlock(pos.offset(x,-1,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
            if(Math.floorMod(x*31+z*17,23)==0)level.setBlock(pos.offset(x,0,z),Blocks.POPPY.defaultBlockState(),2);
        }
        BlockPos h=pos.offset(-4,0,-9);
        for(int x=0;x<=7;x++)for(int z=0;z<=5;z++)for(int y=0;y<=4;y++){
            boolean wall=x==0||x==7||z==0||z==5;
            if(y==4)level.setBlock(h.offset(x,y,z),Blocks.OAK_SLAB.defaultBlockState(),2);
            else if(wall)level.setBlock(h.offset(x,y,z),Blocks.OAK_PLANKS.defaultBlockState(),2);
        }
        BlockPos door=h.offset(3,0,5);
        level.setBlock(door,Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.SOUTH).setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER),2);
        level.setBlock(door.above(),Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.SOUTH).setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
        level.setBlock(h.offset(5,2,2),Blocks.LANTERN.defaultBlockState(),2);
        for(int[] t:new int[][]{{22,0,6},{-20,0,14},{8,0,26},{-12,0,-28},{28,0,-18}}){
            BlockPos base=pos.offset(t[0],t[1],t[2]);
            for(int y=0;y<6;y++)level.setBlock(base.above(y),Blocks.OAK_LOG.defaultBlockState(),2);
            for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)for(int y=4;y<=8;y++){
                double r=Math.sqrt(x*x+z*z+(y-6)*(y-6)*1.4);
                if(r<3.3&&!(x==0&&z==0&&y<6))level.setBlock(base.offset(x,y,z),Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true),2);
            }
        }
        home=pos;
        p.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+4.5);p.getInventory().clearContent();
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.TORCH,16));
        level.setDayTime(3000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,server);
        var d=TideData.get(level);d.active=false;d.quietTicks=1;d.setDirty();
        TideConfig.naturalOnly(4);TideConfig.HUSH_DURATION.set(DURATION);TideConfig.HUSH_VISITORS.set(true);
    }

    private static void step(){
        var server=Minecraft.getInstance().getSingleplayerServer();
        var level=server.overworld();
        if(server.getPlayerList().getPlayers().isEmpty())return;
        ServerPlayer p=server.getPlayerList().getPlayers().get(0);
        var d=HushData.get(level);
        Vec3 spot=homeSpot();
        if(d.active&&!seen){
            seen=true;
            check("natural_hush_started",!TideData.get(level).active&&!MoonData.get(level).active&&!MirrorData.get(level).active&&!ThreadData.get(level).active);
            check("started_where_someone_stands",Math.hypot(d.center.getX()-home.getX(),d.center.getZ()-home.getZ())<8);
            check("covers_the_sun_by_day",!d.moon);
            d.variant=HushData.ALL;d.setDirty();
            report("NATURAL seed="+d.seed+" duration="+d.duration);
            place(p,spot,spot.add(0,1.6,-20));
        }
        if(!d.active){
            if(seen&&!done){
                int all=HushService.FIRST|HushService.ECLIPSE|HushService.KNOCK|HushService.BELL|HushService.PROCESSION|HushService.THUNDER|HushService.HEARD;
                int records=TideService.records2(p);
                check("all_seven_observations",(records&all)==all);
                if((records&all)!=all)report("records2="+Integer.toBinaryString(records)+" missing="+Integer.toBinaryString(all&~records));
                check("journal_given_with_the_grey",p.getInventory().contains(new ItemStack(Veilborne.JOURNAL.get())));
                check("daylight_wax_from_coat_and_procession",p.getInventory().countItem(Veilborne.DAYLIGHT_WAX.get())>=3);
                check("hollow_bell_kept",p.getInventory().contains(new ItemStack(Veilborne.HOLLOW_BELL.get())));
                check("still_feather_kept",p.getInventory().contains(new ItemStack(Veilborne.STILL_FEATHER_ITEM.get())));
                check("quiet_period_set",TideData.get(level).quietTicks>=24000);
                check("no_other_weather_started",!TideData.get(level).active&&!MoonData.get(level).active&&!MirrorData.get(level).active&&!ThreadData.get(level).active);
                check("cottage_untouched",level.getBlockState(home.offset(-4,1,-9)).is(Blocks.OAK_PLANKS)&&level.getBlockState(home.offset(-1,0,-4)).getBlock() instanceof DoorBlock);
                done=true;
            }
            return;
        }
        double pr=d.progress();
        int age=d.age;
        if(age<holdUntil)return;
        if(age>=300&&!has(DAY)){flags|=DAY;int before=d.age;level.setDayTime(level.getDayTime()+24000);check("day_jump_preserves_timeline",d.active&&d.age==before);}
        if(pr>=2.2/12&&!has(GREY)){
            flags|=GREY;
            check("grey_is_recorded",recorded(p,HushService.FIRST));
            check("one_weather_at_a_time",!TideData.get(level).active&&!MoonData.get(level).active&&!ThreadData.get(level).active);
        }
        if(pr>=2.9/12&&!has(GREY_SHOT)){flags|=GREY_SHOT;place(p,spot,spot.add(-3,1.4,-9));shot("hushlife-grey.png",age);return;}

        // The empty coat at the cottage door: answer it with the torch, and take what it leaves.
        HushCoat coat=first(level,HushCoat.class);
        if(coat!=null&&!has(COAT_SEEN)){
            flags|=COAT_SEEN;
            check("coat_comes_to_the_door",coat.position().distanceTo(Vec3.atBottomCenterOf(home.offset(-1,0,-3)))<1.2);
        }
        if(coat!=null&&has(COAT_SEEN)&&!has(COAT_ANSWERED)&&(coat.state()==HushCoat.State.WAIT||coat.state()==HushCoat.State.KNOCK)){
            place(p,coat.position().add(0,0,4),coat.position().add(0,1.2,0));
            if(raise(p,age)){
                flags|=COAT_ANSWERED;
                check("torch_answers_the_coat",coat.state()==HushCoat.State.BOW&&recorded(p,HushService.KNOCK));
            }
            return;
        }
        if(has(COAT_ANSWERED)&&!has(COAT_PAID)){
            ItemEntity wax=item(level,Veilborne.DAYLIGHT_WAX.get(),Vec3.atBottomCenterOf(home.offset(-1,0,-3)),4);
            if(wax!=null){flags|=COAT_PAID;p.teleportTo(wax.getX(),wax.getY(),wax.getZ());report("coat left "+wax.getItem().getCount()+" daylight wax");}
            else if(coat==null){flags|=COAT_PAID;check("coat_leaves_wax",false);}
            return;
        }

        // The jay's feather, hanging in mid-fall in front of them.
        if(!has(FEATHER_SEEN)&&d.has(HushData.FEATHER)&&!d.feather.equals(BlockPos.ZERO)){
            flags|=FEATHER_SEEN;
            check("feather_hangs_near_you_in_open_air",Vec3.atBottomCenterOf(d.feather).distanceTo(p.position())<7&&level.getBlockState(d.feather.above(2)).isAir());
        }

        // The procession: meet it where it walks and raise the torch.
        HushProcession proc=first(level,HushProcession.class);
        if(proc!=null&&!has(PROC_SEEN)){
            flags|=PROC_SEEN;
            check("procession_passes_you_by",proc.nearest(p.position())<60);
            report(String.format("procession route %.0f blocks, nearest member %.1f blocks away",proc.length(),proc.nearest(p.position())));
        }
        if(proc!=null&&has(PROC_SEEN)&&has(COAT_PAID)&&!has(PROC_ANSWERED)&&(proc.state()==HushProcession.State.WALK||proc.state()==HushProcession.State.LOOK)){
            Vec3 lead=proc.member(proc.path(),0,0);
            Vec3 at=new Vec3(lead.x+7,HushProcession.ground(level,lead.x+7,lead.z),lead.z);
            if(p.position().distanceTo(at)>1.5){place(p,at,lead.add(0,1.4,0));return;}
            if(raise(p,age)){
                flags|=PROC_ANSWERED;
                check("torch_answers_the_procession",proc.answering()&&recorded(p,HushService.PROCESSION));
            }
            return;
        }
        if(has(PROC_ANSWERED)&&!has(PROC_GIFT)){
            ItemEntity wax=item(level,Veilborne.DAYLIGHT_WAX.get(),p.position(),24);
            if(wax!=null){flags|=PROC_GIFT;p.teleportTo(wax.getX(),wax.getY(),wax.getZ());check("procession_leaves_a_gift",true);}
            else if(age-lastFlash>60){flags|=PROC_GIFT;check("procession_leaves_a_gift",false);}
            return;
        }

        // The one bell: go to it. Its island opens round it, and it rings clear.
        if(!has(BELL_SEEN)&&d.hasBell()){
            flags|=BELL_SEEN;
            double r=Math.hypot(d.bell.getX()-p.getX(),d.bell.getZ()-p.getZ());
            check("bell_hangs_some_way_off",r>12&&r<48);
            report("bell at "+d.bell.toShortString()+(d.bellTop.equals(BlockPos.ZERO)?" in plain air":" under "+level.getBlockState(d.bellTop).getBlock().getName().getString()));
        }
        if(has(BELL_SEEN)&&has(PROC_GIFT)&&!has(BELL_FOUND)){
            if(d.found()){
                flags|=BELL_FOUND;
                check("finding_the_bell_is_recorded",recorded(p,HushService.BELL));
                return;
            }
            Vec3 at=Vec3.atBottomCenterOf(d.bell).add(1,-1,0);
            place(p,at,Vec3.atBottomCenterOf(d.bell).add(0,.6,0));
            return;
        }
        if(has(BELL_FOUND)&&!has(BELL_LEFT)){
            // Stay in the island a moment so the client can hear it, then go home.
            if(age-d.foundAt<60)return;
            flags|=BELL_LEFT;
            place(p,spot,spot.add(0,1.6,-20));
            flags|=AT_HOME;
        }
        if(!has(BELL_LEFT)&&pr>HushTimeline.BELL_UNTIL-1.2/12){flags|=BELL_LEFT|BELL_FOUND;check("finding_the_bell_is_recorded",false);}

        // Totality: in the open, look straight at the black sun and raise the torch.
        if(HushTimeline.totality(pr)>.8&&!has(TOTALITY)){flags|=TOTALITY;totalAt=age;}
        if(has(TOTALITY)&&!has(HEARD_DONE)&&HushTimeline.totality(pr)>.8){
            Vec3 b=HushService.blackDir(level,d,pr);
            place(p,spot,spot.add(0,p.getEyeHeight(),0).add(b.scale(50)));
            if(age-lastFlash>6&&!has(TOTAL_SHOT)){flags|=TOTAL_SHOT;shot("hushlife-totality.png",age);}
            // The eclipse record is given on the next whole second under open sky; by now it has been.
            if(has(TOTAL_SHOT)&&age-totalAt>21&&raise(p,age)){flags|=HEARD_DONE;check("the_black_sun_hears_you",recorded(p,HushService.HEARD));check("covered_sun_is_recorded",recorded(p,HushService.ECLIPSE));}
            return;
        }

        // The white line on the horizon, and the thunder long after it.
        int line=HushTimeline.lineTick(d.duration),thunder=HushTimeline.thunderTick(d.duration);
        if(age>=line+20&&!has(LINE_SHOT)){
            flags|=LINE_SHOT;
            Vec3 base=Vec3.atBottomCenterOf(d.center).add(HushTimeline.lineDir(d.seed).scale(HushTimeline.LINE_DISTANCE));
            place(p,spot,new Vec3(base.x,spot.y+60,base.z));
            shot("hushlife-line.png",age);
            return;
        }
        if(age==thunder+1&&!has(LINE)){flags|=LINE;check("thunder_comes_late_to_those_who_saw_the_line",recorded(p,HushService.THUNDER));}

        // The bell comes down when its island closes; the feather falls after the bird. Pick both up.
        if(d.has(HushData.BELL_DROPPED)&&!has(BELL_TAKEN)&&has(LINE_SHOT)){
            ItemEntity bell=item(level,Veilborne.HOLLOW_BELL.get(),Vec3.atBottomCenterOf(d.bell),4);
            if(bell!=null){p.teleportTo(bell.getX(),bell.getY(),bell.getZ());flags|=BELL_TAKEN;check("bell_comes_down_when_the_island_closes",true);return;}
        }
        if(d.has(HushData.LANDED)&&!has(FEATHER_TAKEN)){
            ItemEntity f=item(level,Veilborne.STILL_FEATHER_ITEM.get(),Vec3.atBottomCenterOf(d.feather),3);
            check("feather_lands_after_the_bird",f!=null&&pr>=HushTimeline.BIRD);
            flags|=FEATHER_TAKEN;
            if(f!=null){p.teleportTo(f.getX(),f.getY(),f.getZ());return;}
        }
        if(pr>=.94&&!has(RETURN_SHOT)){flags|=RETURN_SHOT;place(p,spot,spot.add(-3,1.4,-9));shot("hushlife-return.png",age);}
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){
        if(capture!=null){String name=capture;capture=null;var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),c->{});}
    }
    private HushLifecyclePreview(){}
}
