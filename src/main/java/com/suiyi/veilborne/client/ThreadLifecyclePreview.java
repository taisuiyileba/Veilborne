package com.suiyi.veilborne.client;

import com.suiyi.veilborne.*;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;

/**
 * -PtidePreview=threadlife: a whole naturally triggered 绯线缝世 at a short duration, run tick by tick. The player watches
 * the tear, steps into the first warned ring and out again before the needle comes down (it must not touch them), stays in
 * the second (struck and thrown) and the third with a binding charm (struck, not thrown), catches a thread end, undoes the
 * needle walker's knot and catches its three loose ends, lets the pull draw them toward the land seam, looks up the thread
 * that holds them, tries a tuning anchor outside the unsewn pocket (nothing) and inside (it gathers it into silk while the
 * thread spirits wake), and catches the keepsake. Every rule is checked and logged to run/thread-lifecycle.log.
 */
public final class ThreadLifecyclePreview {
    private static final String WORLD="Veilborne Thread Lifecycle QA";
    private static final int DURATION=180;
    private static boolean started,configured;
    private static volatile boolean seen,done;
    private static int flags,ticks,exitTicks,warnAt;
    private static double tugFrom=-1;
    private static float healthBefore;
    private static BlockPos home=BlockPos.ZERO;
    private static String capture;
    // Steps, in the order they may happen.
    private static final int DAY=1,TORN=2,OUT_SET=4,OUT_DONE=8,IN_SET=16,IN_DONE=32,STEADY_SET=64,STEADY_DONE=128,END_CAUGHT=256,
        KNOT_TOUCHED=512,KNOT_DONE=1024,LAND=2048,TUG=4096,STRING=8192,POCKET=16384,OUTSIDE=32768,TUNED=65536,SILK=131072,
        KEEPSAKE=262144,TEAR_SHOT=524288,NEEDLE_SHOT=1048576,SEW_SHOT=2097152,BAND_SHOT=4194304,WALKER=8388608,
        OUT_LEFT=1<<24,IN_ARMED=1<<25,STEADY_ARMED=1<<26;
    private static ItemEntity endItem;
    private static Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("thread-lifecycle.log");}
    private static synchronized void report(String s){
        System.out.println(s);try{Files.writeString(file(),s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}
    }
    private static void check(String label,boolean condition){report((condition?"PASS ":"FAIL ")+label);}
    private static boolean has(int f){return (flags&f)!=0;}
    /** Moves and turns the player together: a rotation only set on the server is overwritten by the client. */
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
    private static boolean recorded(ServerPlayer p,int bit){return (TideService.records(p)&bit)!=0;}
    private static void setCharm(ServerPlayer p,boolean on){
        var inv=p.getInventory();
        if(on){if(!ThreadService.hasCharm(p))inv.add(new ItemStack(Veilborne.BINDING_CHARM.get()));}
        else for(int i=0;i<inv.getContainerSize();i++)if(inv.getItem(i).is(Veilborne.BINDING_CHARM.get()))inv.setItem(i,ItemStack.EMPTY);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        var mc=Minecraft.getInstance();
        if(!started&&mc.screen!=null&&mc.getOverlay()==null){
            started=true;try{Files.writeString(file(),"Scarlet Seam lifecycle QA\n");}catch(Exception ex){throw new RuntimeException(ex);}
            mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.renderDistance().set(10);mc.options.simulationDistance().set(8);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            Path dir=mc.gameDirectory.toPath().resolve("saves/"+WORLD);
            if(Files.exists(dir))try(var walk=Files.walk(dir)){for(Path p:walk.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(p);}catch(java.io.IOException ex){throw new RuntimeException(ex);}
            mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.SURVIVAL,false,Difficulty.EASY,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261126L,true,false),WorldPresets::createNormalWorldDimensions,mc.screen);
        }
        if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
        if(!configured){
            configured=true;mc.setScreen(null);
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var level=server.overworld();var p=level.players().get(0);
                BlockPos pos=level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.blockPosition()).above(4);
                // A broad open meadow, and one small stone house the needle must leave alone.
                for(int x=-64;x<=64;x++)for(int z=-64;z<=64;z++){
                    for(int y=0;y<=18;y++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
                    level.setBlock(pos.offset(x,-1,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
                }
                for(int x=9;x<=13;x++)for(int y=0;y<=3;y++)for(int z=3;z<=7;z++)level.setBlock(pos.offset(x,y,z),Blocks.STONE_BRICKS.defaultBlockState(),2);
                home=pos;
                p.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);p.getInventory().clearContent();
                level.setDayTime(18000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
                var d=TideData.get(level);d.active=false;d.quietTicks=1;d.setDirty();
                TideConfig.naturalOnly(3);TideConfig.THREAD_DURATION.set(DURATION);
                TideConfig.THREAD_VISITORS.set(true);TideConfig.THREAD_STRIKES.set(true);TideConfig.THREAD_TUG.set(true);
            });
        }
        ticks++;
        if(!done)mc.getSingleplayerServer().execute(ThreadLifecyclePreview::step);
        if(done&&++exitTicks==140){
            check("client_effects_faded",ThreadClient.strength()==0);
            mc.getSingleplayerServer().execute(()->{
                var level=mc.getSingleplayerServer().overworld();
                check("walker_and_spirits_gone",count(level,NeedleWalker.class)==0&&count(level,ThreadSpirit.class)==0);
                boolean drifting=false;
                for(var en:level.getEntities().getAll())if(en instanceof ItemEntity i&&(i.isNoGravity()||i.getPersistentData().contains(ThreadService.DRIFT)))drifting=true;
                check("drifting_thread_ends_let_go",!drifting);
            });
        }
        if(done&&exitTicks==150)mc.stop();
        if(ticks>DURATION*20+2400&&!done){report("FAIL lifecycle_timeout");mc.stop();}
    }

    private static void step(){
        var server=Minecraft.getInstance().getSingleplayerServer();
        var level=server.overworld();
        if(server.getPlayerList().getPlayers().isEmpty())return;
        ServerPlayer p=server.getPlayerList().getPlayers().get(0);
        var d=ThreadData.get(level);
        Vec3 c=Vec3.atBottomCenterOf(home);
        if(d.active&&!seen){
            seen=true;
            check("natural_seam_started",!TideData.get(level).active&&!MoonData.get(level).active&&!MirrorData.get(level).active);
            check("started_where_someone_stands",Math.hypot(d.center.getX()-home.getX(),d.center.getZ()-home.getZ())<4);
            // Both variants, so every encounter can be checked in one run.
            d.variant=ThreadData.VARIANT_WALKER|ThreadData.VARIANT_POCKET;d.setDirty();
            report("NATURAL seed="+d.seed+" duration="+d.duration);
            place(p,c,c.add(0,40,30));
        }
        if(!d.active){
            if(seen&&!done){
                int all=ThreadService.FIRST|ThreadService.SEWN|ThreadService.FORESIGHT|ThreadService.THREAD_END|ThreadService.KNOT|ThreadService.POCKET|ThreadService.STRING;
                int records=TideService.records(p);
                check("all_seven_observations",(records&all)==all);
                if((records&all)!=all)report("records="+Integer.toBinaryString(records)+" missing="+Integer.toBinaryString(all&~records));
                check("journal_given_on_the_tear",p.getInventory().contains(new ItemStack(Veilborne.JOURNAL.get())));
                check("thread_ends_kept",p.getInventory().countItem(Veilborne.SCARLET_THREAD.get())>=4);
                check("seam_silk_from_knot_and_pocket",p.getInventory().countItem(Veilborne.SEAM_SILK.get())>=4);
                check("keepsake_caught",p.getInventory().contains(new ItemStack(Veilborne.ROBE_SCRAP_ITEM.get())));
                check("walker_departs_with_the_seam",first(level,NeedleWalker.class)==null||first(level,NeedleWalker.class).state()==NeedleWalker.State.DEPART);
                check("house_untouched",level.getBlockState(home.offset(11,1,5)).is(Blocks.STONE_BRICKS)&&level.getBlockState(home.offset(9,3,3)).is(Blocks.STONE_BRICKS));
                check("quiet_period_set",TideData.get(level).quietTicks>=24000);
                check("no_other_weather_started",!TideData.get(level).active&&!MoonData.get(level).active&&!MirrorData.get(level).active);
                done=true;
            }
            return;
        }
        if(p.isDeadOrDying()){report("FAIL player_died");done=true;return;}
        double prog=d.progress();
        var seam=d.seam();
        if(d.age>=300&&!has(DAY)){flags|=DAY;int before=d.age;level.setDayTime(level.getDayTime()+24000);check("day_jump_preserves_timeline",d.active&&d.age==before);}
        if(prog>=.2&&!has(TEAR_SHOT)){flags|=TEAR_SHOT;place(p,c,ThreadTimeline.middle(seam));capture="threadlife-tear.png";return;}
        if(prog>=3.3/13&&!has(TORN)){
            flags|=TORN;
            check("tear_is_recorded",recorded(p,ThreadService.FIRST));
            check("one_weather_at_a_time",!TideData.get(level).active&&!MoonData.get(level).active&&!MirrorData.get(level).active);
        }
        // Strike 0: step into the warned ring, then out before it comes down.
        int at0=ThreadTimeline.strikeTick(0,d.duration),at1=ThreadTimeline.strikeTick(1,d.duration),at2=ThreadTimeline.strikeTick(2,d.duration);
        if(!d.strikes[0].equals(BlockPos.ZERO)&&!has(OUT_SET)){
            flags|=OUT_SET;
            Vec3 s=Vec3.atBottomCenterOf(d.strikes[0]);
            check("strike_warned_near_you_not_on_you",s.distanceTo(p.position())>ThreadTimeline.STRIKE_RANGE&&s.distanceTo(p.position())<28);
            check("strike_spot_open_sky",level.canSeeSky(d.strikes[0].above()));
            place(p,s.add(1,0,1),s.add(0,1,0));
            healthBefore=p.getHealth();
        }
        if(has(OUT_SET)&&!has(OUT_LEFT)&&d.age>=at0-30){flags|=OUT_LEFT;Vec3 s=Vec3.atBottomCenterOf(d.strikes[0]);place(p,s.add(ThreadTimeline.STRIKE_RANGE+5,0,0),s.add(0,6,0));}
        if(has(OUT_LEFT)&&!has(OUT_DONE)&&d.age>=at0+2){
            flags|=OUT_DONE;
            check("stepping_out_in_time_is_safe",p.getHealth()>=healthBefore);
            check("reading_the_warning_is_noted",recorded(p,ThreadService.FORESIGHT));
        }
        // Strike 1: stay in it. Struck and thrown out; nothing else broken.
        if(!d.strikes[1].equals(BlockPos.ZERO)&&has(OUT_DONE)&&!has(IN_SET)&&d.age<at1-20){flags|=IN_SET;setCharm(p,false);Vec3 s=Vec3.atBottomCenterOf(d.strikes[1]);place(p,s.add(.5,0,.5),s.add(0,1,0));}
        if(has(IN_SET)&&!has(IN_ARMED)&&d.age>=at1-4){flags|=IN_ARMED;healthBefore=p.getHealth();Vec3 s=Vec3.atBottomCenterOf(d.strikes[1]);place(p,s.add(.5,0,.5),s.add(0,1,0));}
        if(has(IN_ARMED)&&!has(IN_DONE)&&d.age>=at1+3){
            flags|=IN_DONE;
            check("staying_in_the_ring_is_struck",p.getHealth()<healthBefore);
            check("struck_and_thrown_out",p.getDeltaMovement().y>.2||Vec3.atBottomCenterOf(d.strikes[1]).distanceTo(p.position())>1.2);
            p.heal(20);
        }
        // Strike 2: stay in it with a binding charm. Struck, but held steady.
        if(!d.strikes[2].equals(BlockPos.ZERO)&&has(IN_DONE)&&!has(STEADY_SET)&&d.age<at2-20){flags|=STEADY_SET;setCharm(p,true);Vec3 s=Vec3.atBottomCenterOf(d.strikes[2]);place(p,s.add(.5,0,.5),s.add(0,1,0));}
        if(has(STEADY_SET)&&!has(STEADY_ARMED)&&d.age>=at2-4){flags|=STEADY_ARMED;healthBefore=p.getHealth();Vec3 s=Vec3.atBottomCenterOf(d.strikes[2]);place(p,s.add(.5,0,.5),s.add(0,1,0));}
        if(has(STEADY_ARMED)&&!has(STEADY_DONE)&&d.age>=at2+3){
            flags|=STEADY_DONE;
            check("charm_struck_but_not_thrown",p.getHealth()<healthBefore&&p.getDeltaMovement().y<.2);
            p.heal(20);place(p,c,c.add(0,40,30));
        }
        if(prog>.44&&!has(NEEDLE_SHOT)&&(has(STEADY_DONE)||d.age>at2+40)){flags|=NEEDLE_SHOT;place(p,c,ThreadTimeline.middle(seam));capture="threadlife-needle.png";return;}
        // A thread end drifting down: rise to meet it (falling slowly after) and catch it.
        if(has(STEADY_DONE)&&!has(END_CAUGHT)){
            if(endItem!=null&&!endItem.isAlive()){
                flags|=END_CAUGHT;endItem=null;
                check("thread_end_caught",p.getInventory().contains(new ItemStack(Veilborne.SCARLET_THREAD.get())));
                place(p,c,c.add(0,40,30));
            }else if(endItem==null){
                for(var en:level.getEntities().getAll())if(en instanceof ItemEntity item&&item.isAlive()&&item.getItem().is(Veilborne.SCARLET_THREAD.get())&&item.getPersistentData().contains(ThreadService.DRIFT)&&(endItem==null||item.getY()<endItem.getY()))endItem=(ItemEntity)en;
                if(endItem!=null)check("thread_end_drifts_down_slowly",endItem.isNoGravity()&&endItem.getDeltaMovement().y>-.06&&endItem.getDeltaMovement().y<0);
            }
            if(endItem!=null){p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,120,0,false,false));p.teleportTo(endItem.getX(),endItem.getY()-1,endItem.getZ());return;}
            if(prog>6.6/13&&!has(END_CAUGHT)){flags|=END_CAUGHT;check("thread_end_caught",false);}
        }
        // The needle walker: it comes down its thread and settles to its knot. Touch it, then catch the three loose ends.
        NeedleWalker walker=first(level,NeedleWalker.class);
        if(walker!=null&&!has(WALKER)){
            flags|=WALKER;
            check("walker_comes_down_the_first_strike",Vec3.atBottomCenterOf(walker.anchor()).distanceTo(Vec3.atBottomCenterOf(d.strikes[0]))<1);
        }
        if(walker!=null&&walker.state()==NeedleWalker.State.KNOT&&!has(KNOT_TOUCHED)&&has(END_CAUGHT)){
            flags|=KNOT_TOUCHED;
            check("walker_settles_at_its_thread",walker.position().distanceTo(Vec3.atBottomCenterOf(walker.anchor()))<2.2);
            place(p,walker.position().add(1.5,0,0),walker.position().add(0,.4,0));
            check("touching_the_knot_undoes_it",walker.startAlarm(p)&&walker.state()==NeedleWalker.State.ALARM&&walker.ends()==7);
            return;
        }
        if(walker!=null&&has(KNOT_TOUCHED)&&!has(KNOT_DONE)){
            p.heal(20);
            if(walker.state()==NeedleWalker.State.ALARM&&walker.ends()!=0){
                for(int k=0;k<3;k++)if((walker.ends()&1<<k)!=0){Vec3 e=walker.endAt(k);p.teleportTo(e.x,e.y-.8,e.z);break;}
                return;
            }
            flags|=KNOT_DONE;
            check("three_ends_caught",recorded(p,ThreadService.KNOT)&&p.getInventory().countItem(Veilborne.SEAM_SILK.get())>=1);
            check("undone_only_once",!walker.startAlarm(p));
            place(p,c,c.add(0,40,30));
        }
        // The world stitch: two feet on open ground, one near and one far, warned; the pull draws people toward the line.
        if(d.hasLand()&&!has(LAND)){
            flags|=LAND;
            double near=Math.hypot(d.landA.getX()+.5-p.getX(),d.landA.getZ()+.5-p.getZ());
            check("land_stitch_near_but_not_on_you",near>ThreadTimeline.LAND_RANGE&&near<40);
            check("far_foot_across_the_land",Math.hypot(d.landB.getX()-d.landA.getX(),d.landB.getZ()-d.landA.getZ())>150);
            setCharm(p,false);
            place(p,c,Vec3.atBottomCenterOf(d.landA).add(0,6,0));
        }
        double q=ThreadTimeline.sew(prog);
        if(has(LAND)&&!has(SEW_SHOT)&&prog>=ThreadTimeline.SEW_START&&q>=ThreadTimeline.DESCEND+.03){flags|=SEW_SHOT;place(p,c,Vec3.atBottomCenterOf(d.landA).add(0,20,0));capture="threadlife-sew.png";return;}
        if(has(LAND)&&!has(TUG)&&prog>=ThreadTimeline.SEW_START){
            Vec3 a=Vec3.atBottomCenterOf(d.landA),b=Vec3.atBottomCenterOf(d.landB);
            if(tugFrom<0&&q>=ThreadTimeline.TUG0){
                check("world_sewn_recorded",recorded(p,ThreadService.SEWN));
                tugFrom=ThreadTimeline.nearestOnLine(a,b,p.position()).distanceTo(p.position());
            }
            if(tugFrom>=0&&q>=(ThreadTimeline.TUG0+ThreadTimeline.TUG1)/2){
                flags|=TUG;
                double now=ThreadTimeline.nearestOnLine(a,b,p.position()).distanceTo(p.position());
                check("pull_draws_you_toward_the_land_seam",now<tugFrom-.4);
                report("tug moved "+String.format("%.2f",tugFrom-now)+" blocks");
            }
            if(!has(TUG))return;
        }
        // The hidden record: look straight up the thread that holds you while the strings show.
        if(has(TUG)&&!has(STRING)){
            if(ThreadTimeline.strings(prog)>.5){place(p,p.position(),ThreadTimeline.middle(seam));if(recorded(p,ThreadService.STRING)){flags|=STRING;check("looking_up_the_string",true);}}
            else if(q>ThreadTimeline.TUG1+.1){flags|=STRING;check("looking_up_the_string",false);}
            return;
        }
        // The unsewn pocket: an anchor outside it does nothing; inside, it gathers the pocket into silk and wakes the spirits.
        if(d.hasPocket()&&!has(POCKET)){
            flags|=POCKET;
            check("pocket_opens_after_the_cut",prog>=ThreadTimeline.POCKET_OPEN&&level.canSeeSky(d.pocket.above()));
        }
        if(has(POCKET)&&!has(OUTSIDE)){
            flags|=OUTSIDE;
            BlockPos out=TideService.findSafe(level,d.pocket.offset(9,0,0),2);
            if(out!=null){level.setBlock(out,Veilborne.TUNING_ANCHOR.get().defaultBlockState(),3);check("anchor_outside_the_pocket_does_nothing",!ThreadService.tune(level,d,out)&&!d.has(ThreadData.TUNED));level.removeBlock(out,false);}
            else check("anchor_outside_the_pocket_does_nothing",!ThreadService.tune(level,d,d.pocket.offset(9,0,0)));
        }
        if(has(OUTSIDE)&&!has(TUNED)){
            flags|=TUNED;
            BlockPos in=d.pocket.offset(1,0,0);
            level.setBlock(in,Veilborne.TUNING_ANCHOR.get().defaultBlockState(),3);
            check("anchor_inside_tunes_the_pocket",ThreadService.tune(level,d,in)&&d.has(ThreadData.TUNED));
            check("thread_spirits_wake",count(level,ThreadSpirit.class)==ThreadService.SPIRITS);
            place(p,Vec3.atBottomCenterOf(d.pocket).add(-3,0,0),Vec3.atBottomCenterOf(d.pocket).add(0,1,0));
            return;
        }
        if(has(TUNED)&&!has(SILK)){
            p.heal(20);
            if(d.age-d.tuneAt==ThreadService.TUNE_TICKS)report(String.format("pocket closed with the tuner %.1f blocks from the anchor (the spirits push people about)",p.position().distanceTo(Vec3.atCenterOf(d.anchor))));
            if(d.has(ThreadData.SEWN_UP)){
                for(var en:level.getEntities().getAll())if(en instanceof ItemEntity item&&item.getItem().is(Veilborne.SEAM_SILK.get())&&item.distanceTo(p)<24)p.teleportTo(item.getX(),item.getY(),item.getZ());
                if(recorded(p,ThreadService.POCKET)&&p.getInventory().countItem(Veilborne.SEAM_SILK.get())>=4){flags|=SILK;check("pocket_gathered_into_silk",true);check("spirits_unravel_once_it_is_gathered",count(level,ThreadSpirit.class)==0);place(p,c,c.add(0,40,30));}
            }
            if(prog>=ThreadTimeline.POCKET_CLOSE&&!has(SILK)){flags|=SILK;check("pocket_gathered_into_silk",false);}
            return;
        }
        if(prog>=.962&&!has(BAND_SHOT)){flags|=BAND_SHOT;place(p,p.position(),ThreadTimeline.middle(seam));capture="threadlife-band.png";return;}
        // The last scrap of the sky falls near someone: catch it.
        if(!d.keepsake.equals(BlockPos.ZERO)&&!has(KEEPSAKE)){
            for(var en:level.getEntities().getAll())if(en instanceof ItemEntity item&&item.getItem().is(Veilborne.ROBE_SCRAP_ITEM.get())){
                p.teleportTo(item.getX(),item.getY(),item.getZ());flags|=KEEPSAKE;
            }
        }
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){
        if(capture!=null){String name=capture;capture=null;var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),c->{});}
    }
    private ThreadLifecyclePreview(){}
}
