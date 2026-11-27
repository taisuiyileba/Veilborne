package com.suiyi.veilborne.client;

import com.suiyi.veilborne.*;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;

/**
 * -PtidePreview=dreamlife: a whole naturally triggered 苍翠梦潮 at a short duration, run tick by tick, played through the
 * way someone would. The player stands in a meadow by a cottage and a wheat field. The forest comes down and the journal
 * with it; they walk the petal path to the dream tree, touch the wandering seed and chase its five lights inside the vine
 * enclosure, lead the lost school away from the cottage window with a poppy, then go back to the tree and wait there
 * quietly for the deer, which stops and looks at them and lets a seed fall to them; in the seedfall they guide seeds down
 * to the soil, one of them into the deer's hoof ring; they take the antler twig. Every rule is checked and logged to
 * run/dream-lifecycle.log, with screenshots at the canopy, the deer, the glance, the seedfall and the return.
 */
public final class DreamLifecyclePreview {
    private static final String WORLD="Veilborne Dream Lifecycle QA";
    private static final int DURATION=300;
    private static boolean started,configured;
    private static volatile boolean seen,done;
    private static volatile int flags,clientFlags;
    private static int ticks,exitTicks,pathStep,lightDone=-1,escort,waitFrom=-1,seedsLanded,seedBase=-1,fullBase;
    private static BlockPos home=BlockPos.ZERO;
    private static String capture;
    private static volatile String pending;
    private static int holdUntil,captureAt;
    private static Vec3 escortTo=Vec3.ZERO;
    private static final int DAY=1,ARRIVE=2,TREE=4,PATH=8,PATH_DONE=16,WANDER=32,ENCLOSED=64,ENC_DONE=128,LOST=256,LOST_DONE=512,
        WAIT=1024,GLANCED=2048,GIFT=4096,SEED_ONE=8192,SEED_HOOF=16384,TWIG=32768,CANOPY_SHOT=65536,DEER_SHOT=131072,GLANCE_SHOT=262144,
        SEED_SHOT=524288,RETURN_SHOT=1048576,LOOT=1<<21,HOMEWARD=1<<22;
    private static final int C_CANOPY=1,C_DEER=2,C_RINGS=4,C_GROVE=8;
    private static Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("dream-lifecycle.log");}
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
    private static long count(ServerLevel level,Class<?> type){long n=0;for(var e:level.getEntities().getAll())if(type.isInstance(e)&&!e.isRemoved())n++;return n;}
    private static ItemEntity item(ServerLevel level,Item kind,Vec3 near,double range){
        for(var e:level.getEntities().getAll())if(e instanceof ItemEntity i&&i.isAlive()&&i.getItem().is(kind)&&i.position().distanceTo(near)<range)return i;
        return null;
    }
    private static boolean recorded(ServerPlayer p,int bit){return (TideService.records2(p)&bit)!=0;}
    private static void shot(String name,int age){pending=name;holdUntil=age+10;}
    private static Vec3 homeSpot(){return Vec3.atBottomCenterOf(home).add(0,0,4);}
    /** Hold something out in the off hand: the leaf-fish and the seeds follow either hand, and the inventory keeps what it has won. */
    private static int seedsBefore;
    private static void hold(ServerPlayer p,Item item){p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(item));}
    private static void pickUp(ServerPlayer p,ItemEntity it){p.teleportTo(it.getX(),it.getY(),it.getZ());}

    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        var mc=Minecraft.getInstance();
        if(!started&&mc.screen!=null&&mc.getOverlay()==null){
            started=true;try{Files.writeString(file(),"Verdant Dream lifecycle QA\n");}catch(Exception ex){throw new RuntimeException(ex);}
            mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.renderDistance().set(10);mc.options.simulationDistance().set(8);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            Path dir=mc.gameDirectory.toPath().resolve("saves/"+WORLD);
            if(Files.exists(dir))try(var walk=Files.walk(dir)){for(Path p:walk.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(p);}catch(java.io.IOException ex){throw new RuntimeException(ex);}
            mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.SURVIVAL,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261201L,true,false),WorldPresets::createNormalWorldDimensions,mc.screen);
        }
        if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
        if(!configured){configured=true;mc.setScreen(null);mc.getSingleplayerServer().execute(DreamLifecyclePreview::build);}
        ticks++;
        // What only the client can tell: the canopy over the sun, the forest built, the deer's body, the rings it leaves.
        if(DreamClient.active()){
            double p=DreamClient.progress(1);
            if((clientFlags&C_CANOPY)==0&&p>3.4/13&&DreamClient.strength()>.95){
                clientFlags|=C_CANOPY;
                check("client_canopy_covers_the_sun",DreamHooks.veil(1)>.85);
                check("client_skylight_and_fog_tinted",DreamTimeline.light(p)>.99);
            }
            if((clientFlags&C_GROVE)==0&&p>3.4/13){
                clientFlags|=C_GROVE;
                check("client_grove_built_with_roots_that_reach_the_land",!DreamGrove.tips.isEmpty()&&DreamGrove.grips.size()>=4);
                report("grove: "+DreamGrove.tips.size()+" roots, "+DreamGrove.grips.size()+" gripping the land");
            }
            if((clientFlags&C_DEER)==0&&DreamTimeline.deer(p)>.9){
                clientFlags|=C_DEER;
                DeerModel.Pose pose=DeerModel.pose(1);
                boolean feet=pose!=null;
                // Only where this client has the land: elsewhere the deer walks on the height of its road, as the model does.
                // A planted hoof stands on the land; a lifted one is somewhere above it.
                if(pose!=null)for(int leg=0;leg<4;leg++){
                    Vec3 f=pose.feet()[leg];int fx=net.minecraft.util.Mth.floor(f.x),fz=net.minecraft.util.Mth.floor(f.z);
                    if(!mc.level.hasChunk(fx>>4,fz>>4))continue;
                    double g=Surface.top(mc.level,fx,fz);
                    boolean ok=pose.swing()[leg]<0?Math.abs(f.y-g)<1.2:f.y>g-.6;
                    if(!ok){feet=false;report(String.format("hoof %d at %.1f over ground %.0f (swing %.2f)",leg,f.y,g,pose.swing()[leg]));}
                }
                check("client_deer_walks_with_its_hooves_on_the_land",feet);
            }
            if((clientFlags&C_RINGS)==0&&p>DreamTimeline.DEER_START+.5/13){
                clientFlags|=C_RINGS;
                long rings=DreamClient.falls.stream().filter(f->f.p()<=p).count();
                check("client_hoof_rings_open_where_it_steps",rings>=6);
            }
        }
        if(pending!=null){if(captureAt==0)captureAt=ticks+6;else if(ticks>=captureAt){capture=pending;pending=null;captureAt=0;}}
        if(!done)mc.getSingleplayerServer().execute(DreamLifecyclePreview::step);
        if(done&&++exitTicks==160){
            check("client_effects_faded",DreamClient.strength()==0);
            mc.getSingleplayerServer().execute(()->{
                var level=mc.getSingleplayerServer().overworld();
                check("visitors_gone",count(level,DreamSeed.class)==0&&count(level,LeafSchool.class)==0);
            });
        }
        if(done&&exitTicks==170)mc.stop();
        if(ticks>DURATION*20+2800&&!done){report("FAIL lifecycle_timeout");mc.stop();}
    }

    private static void build(){
        var server=Minecraft.getInstance().getSingleplayerServer();var level=server.overworld();var p=level.players().get(0);
        BlockPos pos=level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.blockPosition()).above(4);
        for(int x=-72;x<=72;x++)for(int z=-72;z<=72;z++){
            for(int y=0;y<=20;y++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            level.setBlock(pos.offset(x,-1,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
            if(Math.floorMod(x*31+z*17,23)==0)level.setBlock(pos.offset(x,0,z),Blocks.POPPY.defaultBlockState(),2);
        }
        // A wheat field beside the meadow, for the wandering seed to swim round.
        for(int x=6;x<=12;x++)for(int z=-4;z<=2;z++){
            boolean water=x==9&&z==-1;
            level.setBlock(pos.offset(x,-1,z),(water?Blocks.WATER:Blocks.FARMLAND).defaultBlockState(),2);
            level.setBlock(pos.offset(x,0,z),water?Blocks.AIR.defaultBlockState():Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,6),2);
        }
        // The cottage, with glass windows for the lost school to be stuck at.
        BlockPos h=pos.offset(-12,0,-4);
        for(int x=0;x<=7;x++)for(int z=0;z<=6;z++)for(int y=0;y<=4;y++){
            boolean wall=x==0||x==7||z==0||z==6;
            if(y==4)level.setBlock(h.offset(x,y,z),Blocks.OAK_SLAB.defaultBlockState(),2);
            else if(wall)level.setBlock(h.offset(x,y,z),Blocks.OAK_PLANKS.defaultBlockState(),2);
        }
        for(int[] w:new int[][]{{7,1,3},{7,2,3},{0,1,3},{0,2,3}})level.setBlock(h.offset(w[0],w[1],w[2]),Blocks.GLASS_PANE.defaultBlockState(),2);
        BlockPos door=h.offset(3,0,6);
        level.setBlock(door,Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.SOUTH).setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER),2);
        level.setBlock(door.above(),Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.SOUTH).setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
        for(int[] t:new int[][]{{-30,0,18},{30,0,-24},{-34,0,-30}}){
            BlockPos base=pos.offset(t[0],t[1],t[2]);
            for(int y=0;y<6;y++)level.setBlock(base.above(y),Blocks.OAK_LOG.defaultBlockState(),2);
            for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)for(int y=4;y<=8;y++){
                double r=Math.sqrt(x*x+z*z+(y-6)*(y-6)*1.4);
                if(r<3.3&&!(x==0&&z==0&&y<6))level.setBlock(base.offset(x,y,z),Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true),2);
            }
        }
        home=pos;
        p.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+4.5);p.getInventory().clearContent();
        level.setDayTime(5000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,server);
        var d=TideData.get(level);d.active=false;d.quietTicks=1;d.setDirty();
        TideConfig.naturalOnly(5);TideConfig.DREAM_DURATION.set(DURATION);TideConfig.DREAM_VISITORS.set(true);
    }

    private static void step(){
        var server=Minecraft.getInstance().getSingleplayerServer();
        var level=server.overworld();
        if(server.getPlayerList().getPlayers().isEmpty())return;
        ServerPlayer p=server.getPlayerList().getPlayers().get(0);
        var d=DreamData.get(level);
        Vec3 spot=homeSpot();
        if(d.active&&!seen){
            seen=true;
            check("natural_dream_started",!TideData.get(level).active&&!MoonData.get(level).active&&!MirrorData.get(level).active&&!ThreadData.get(level).active&&!HushData.get(level).active);
            check("started_where_someone_stands",Math.hypot(d.center.getX()-home.getX(),d.center.getZ()-home.getZ())<8);
            d.variant=DreamData.ALL;d.setDirty();
            report("NATURAL seed="+d.seed+" duration="+d.duration);
            place(p,spot,spot.add(0,1.6,-20));
        }
        if(!d.active){
            if(seen&&!done){
                int all=DreamService.FIRST|DreamService.DEER|DreamService.PATH|DreamService.GLANCE|DreamService.SEED|DreamService.LOST|DreamService.ENCLOSURE|DreamService.FOOTPRINT;
                int records=TideService.records2(p);
                check("all_eight_observations",(records&all)==all);
                if((records&all)!=all)report("records2="+Integer.toBinaryString(records)+" missing="+Integer.toBinaryString(all&~records));
                var inv=p.getInventory();
                check("journal_given_when_the_forest_came_down",inv.contains(new ItemStack(Veilborne.JOURNAL.get())));
                check("dream_seeds_from_the_glance_and_the_seedfall",inv.countItem(Veilborne.DREAM_SEED.get())>=2);
                check("full_seeds_from_the_enclosure_and_the_hoof_ring",inv.countItem(Veilborne.FULL_DREAM_SEED.get())>=2);
                check("golden_leaves_from_the_lost_school",inv.countItem(Veilborne.GOLDEN_LEAF.get())>=2);
                check("antler_twig_kept",inv.contains(new ItemStack(Veilborne.ANTLER_TWIG_ITEM.get())));
                check("quiet_period_set",TideData.get(level).quietTicks>=24000);
                check("no_other_weather_started",!TideData.get(level).active&&!MoonData.get(level).active&&!MirrorData.get(level).active&&!ThreadData.get(level).active&&!HushData.get(level).active);
                check("cottage_and_field_untouched",level.getBlockState(home.offset(-12,1,-4)).is(Blocks.OAK_PLANKS)&&level.getBlockState(home.offset(6,0,-4)).is(Blocks.WHEAT));
                report("inventory: "+inv.countItem(Veilborne.DREAM_SEED.get())+" dream seeds, "+inv.countItem(Veilborne.FULL_DREAM_SEED.get())+" full, "+inv.countItem(Veilborne.GOLDEN_LEAF.get())+" golden leaves");
                done=true;
            }
            return;
        }
        double pr=d.progress();
        int age=d.age;
        if(age<holdUntil)return;
        if(age>=300&&!has(DAY)){flags|=DAY;int before=d.age;level.setDayTime(level.getDayTime()+24000);check("day_jump_preserves_timeline",d.active&&d.age==before);}
        if(pr>=DreamTimeline.ARRIVED+.05/13&&!has(ARRIVE)){
            flags|=ARRIVE;
            check("forest_coming_down_is_recorded",recorded(p,DreamService.FIRST));
            check("journal_given",p.getInventory().contains(new ItemStack(Veilborne.JOURNAL.get())));
        }
        if(pr>=3.15/13&&!has(CANOPY_SHOT)){flags|=CANOPY_SHOT;place(p,spot,spot.add(10,40,-20));shot("dreamlife-canopy.png",age);return;}
        if(d.hasTree()&&!has(TREE)){
            flags|=TREE;
            double r=Math.hypot(d.tree.getX()-p.getX(),d.tree.getZ()-p.getZ());
            check("dream_tree_grows_some_way_off_on_open_ground",r>12&&r<40&&level.canSeeSky(d.tree.above()));
            report("dream tree at "+d.tree.toShortString()+", "+String.format("%.0f",r)+" blocks away");
        }

        // The petal path: walk it to the tree.
        if(d.path.size()>1&&!has(PATH)){flags|=PATH;pathStep=0;report("petal path of "+d.path.size()+" points");check("path_starts_at_your_feet",Vec3.atBottomCenterOf(d.path.get(0)).distanceTo(p.position())<3);}
        if(has(PATH)&&!has(PATH_DONE)){
            if(age%3!=0)return;
            if(pathStep<d.path.size()){BlockPos b=d.path.get(pathStep);Vec3 at=Vec3.atBottomCenterOf(b);place(p,at,Vec3.atBottomCenterOf(d.tree).add(0,3,0));pathStep+=2;return;}
            place(p,Vec3.atBottomCenterOf(d.tree).add(1.6,0,0),Vec3.atBottomCenterOf(d.tree).add(0,3,0));
            if(recorded(p,DreamService.PATH)){flags|=PATH_DONE;check("walking_the_path_to_the_tree_is_recorded",true);}
            else if(++escort>40){flags|=PATH_DONE;check("walking_the_path_to_the_tree_is_recorded",false);escort=0;}
            return;
        }
        if(!has(PATH)&&pr>DreamTimeline.PATH_UNTIL-1.2/13){flags|=PATH|PATH_DONE;check("petal_path_laid",false);}

        // The enclosure and the lost school, whichever comes first; one in hand is finished before the other is begun.
        DreamSeed wander=null;
        for(var en:level.getEntities().getAll())if(en instanceof DreamSeed s&&s.kind()==DreamSeed.Kind.WANDER&&!s.isRemoved())wander=s;
        LeafSchool school=first(level,LeafSchool.class);
        boolean enclosing=has(ENCLOSED)&&!has(ENC_DONE),escorting=has(LOST)&&!has(LOST_DONE);
        if(enclosing){
            if(d.has(DreamData.ENCLOSURE_WON)||d.has(DreamData.ENCLOSURE_LOST)){
                flags|=ENC_DONE;
                check("chasing_the_five_lights_wins_the_enclosure",d.has(DreamData.ENCLOSURE_WON)&&recorded(p,DreamService.ENCLOSURE));
                ItemEntity full=item(level,Veilborne.FULL_DREAM_SEED.get(),Vec3.atBottomCenterOf(d.enclosure),4);
                check("enclosure_leaves_a_full_seed",full!=null);
                if(full!=null)pickUp(p,full);
                return;
            }
            if(age>=d.lightAt&&d.lightStep!=lightDone){
                Vec3 l=DreamService.lightPoint(level,d.enclosure,d.seed,d.lightStep,d.enclosureExit);
                place(p,l.add(0,-.9,0),l.add(2,0,0));
                lightDone=d.lightStep;
            }
            return;
        }
        if(escorting){
            if(school==null||school.state()==LeafSchool.State.MERGE||school.state()==LeafSchool.State.ASCEND){
                ItemEntity leaf=item(level,Veilborne.GOLDEN_LEAF.get(),p.position(),24);
                if(leaf!=null){flags|=LOST_DONE;check("leading_the_school_out_is_recorded",recorded(p,DreamService.LOST));check("golden_fish_leaves_golden_leaves",leaf.getItem().getCount()>=2);pickUp(p,leaf);}
                else if(school==null){flags|=LOST_DONE;check("leading_the_school_out_is_recorded",recorded(p,DreamService.LOST));}
                return;
            }
            if(age%4!=0)return;
            // Walk backwards away from the window, looking back at them.
            Vec3 me=p.position(),to=escortTo;
            Vec3 stepV=new Vec3(to.x-me.x,0,to.z-me.z);
            if(stepV.length()>.8)stepV=stepV.normalize().scale(.8);
            Vec3 next=me.add(stepV);
            next=new Vec3(next.x,Surface.top(level,net.minecraft.util.Mth.floor(next.x),net.minecraft.util.Mth.floor(next.z)),next.z);
            place(p,next,school.position());
            if(++escort>160){flags|=LOST_DONE;check("leading_the_school_out_is_recorded",false);}
            return;
        }
        if(wander!=null&&!has(WANDER)){
            flags|=WANDER;
            BlockPos under=BlockPos.containing(wander.position());
            boolean crops=level.getBlockState(under.below()).is(Blocks.WHEAT)||level.getBlockState(under.below(2)).is(Blocks.FARMLAND)||level.getBlockState(under).is(Blocks.WHEAT);
            check("wandering_seed_comes_near_you",wander.position().distanceTo(p.position())<18);
            report("wandering seed "+(crops?"round the wheat":"in the open")+" at "+under.toShortString());
        }
        if(has(WANDER)&&!has(ENCLOSED)&&wander!=null){
            if(d.has(DreamData.ENCLOSURE)){flags|=ENCLOSED;check("touching_the_seed_opens_the_enclosure",d.enclosureOpen());return;}
            place(p,wander.position().add(0,-.9,0),wander.position().add(3,0,0));
            return;
        }
        if(has(WANDER)&&!has(ENCLOSED)&&d.has(DreamData.ENCLOSURE)){flags|=ENCLOSED;check("touching_the_seed_opens_the_enclosure",d.enclosureOpen());return;}
        // Nothing left to do out by the tree: walk back toward the cottage, where the lost school can find a window (and the
        // wandering seed, the wheat).
        if(school==null&&!has(LOST)&&!has(HOMEWARD)&&has(PATH_DONE)&&(!has(WANDER)||has(ENC_DONE))&&pr<DreamTimeline.LOST_UNTIL-1.0/13){
            flags|=HOMEWARD;
            place(p,Vec3.atBottomCenterOf(home.offset(-8,0,6)),Vec3.atBottomCenterOf(home.offset(-8,1,0)));
            report("back at the cottage to wait for the lost school");
            return;
        }
        if(school!=null&&!has(LOST)){
            flags|=LOST;
            Vec3 into=school.into();
            BlockPos at=BlockPos.containing(school.position()),glass=at.relative(Direction.getNearest(into.x,0,into.z));
            check("lost_school_is_stuck_at_a_window",level.getBlockState(glass).is(Blocks.GLASS_PANE)||level.getBlockState(glass.above()).is(Blocks.GLASS_PANE)||level.getBlockState(glass.below()).is(Blocks.GLASS_PANE));
            hold(p,Items.POPPY);
            escortTo=school.position().add(into.scale(-16));
            place(p,school.position().add(into.scale(-2.5)).add(0,-1.4,0),school.position());
            escort=0;
            return;
        }
        if(!has(WANDER)&&pr>DreamTimeline.WANDER_UNTIL){flags|=WANDER|ENCLOSED|ENC_DONE;check("wandering_seed_came",false);}
        if(!has(LOST)&&pr>DreamTimeline.LOST_UNTIL-.8/13){flags|=LOST|LOST_DONE;check("lost_school_came",false);}

        // The deer: wait by the tree, quite still, before it comes.
        if(pr>=DreamTimeline.DEER_START-.3/13&&!has(WAIT)&&d.hasTree()){
            flags|=WAIT;waitFrom=age;
            Vec3 at=Vec3.atBottomCenterOf(d.tree).add(-2.5,0,1.5);
            place(p,at,Vec3.atBottomCenterOf(d.tree).add(0,30,0));
            p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
            seedsBefore=p.getInventory().countItem(Veilborne.DREAM_SEED.get());
            check("road_of_roots_let_down_for_the_deer",d.hasRoute());
            if(d.hasRoute()){
                double beside=Math.hypot(d.route().at(DreamTimeline.PATH/2).x-d.tree.getX()-.5,d.route().at(DreamTimeline.PATH/2).z-d.tree.getZ()-.5);
                check("deer_stops_beside_the_tree",Math.abs(beside-DreamTimeline.BESIDE)<3);
            }
            return;
        }
        if(has(WAIT)&&!has(DEER_SHOT)&&DreamTimeline.deer(pr)>.9&&DreamTimeline.walk(pr)>.2){
            flags|=DEER_SHOT;
            check("deer_passing_is_recorded",recorded(p,DreamService.DEER));
            Vec3 body=d.route().at(DreamTimeline.along(DreamTimeline.walk(pr)));
            p.connection.teleport(p.getX(),p.getY(),p.getZ(),(float)Math.toDegrees(Math.atan2(-(body.x-p.getX()),body.z-p.getZ())),-22);
            shot("dreamlife-deer.png",age);
            return;
        }
        if(has(WAIT)&&!has(GLANCED)&&d.has(DreamData.GLANCE_PLAYER)){
            flags|=GLANCED;
            check("the_deer_looks_at_whoever_waits_quietly",recorded(p,DreamService.GLANCE)&&p.blockPosition().distManhattan(d.glance)<3);
        }
        if(has(GLANCED)&&!has(GLANCE_SHOT)&&pr>=DreamTimeline.glanceP()){
            flags|=GLANCE_SHOT;
            Vec3 body=d.route().at(DreamTimeline.PATH/2);
            p.connection.teleport(p.getX(),p.getY(),p.getZ(),(float)Math.toDegrees(Math.atan2(-(body.x-p.getX()),body.z-p.getZ())),-18);
            shot("dreamlife-glance.png",age);return;
        }
        if(has(WAIT)&&!has(GLANCED)&&pr>DreamTimeline.stopUntil()){flags|=GLANCED|GLANCE_SHOT;check("the_deer_looks_at_whoever_waits_quietly",false);}
        if(has(GLANCED)&&!has(GIFT)&&d.has(DreamData.GIFT_DROPPED)){
            ItemEntity gift=item(level,Veilborne.DREAM_SEED.get(),Vec3.atBottomCenterOf(d.glance),4);
            // It falls where you stand, so it may already be in your hands.
            flags|=GIFT;check("its_gift_comes_down_to_you",gift!=null||p.getInventory().countItem(Veilborne.DREAM_SEED.get())>seedsBefore);
            if(gift!=null)pickUp(p,gift);
            return;
        }

        // The seedfall: hold out a flower, look down at the soil, bring the seeds down. Take one to a hoof ring. What comes up is
        // counted by what ends up in your hands, since a seed that comes up at your feet is picked up at once.
        if(pr>=DreamTimeline.SEEDFALL_FROM&&seedsLanded<2){
            var inv=p.getInventory();
            if(seedBase<0){seedBase=inv.countItem(Veilborne.DREAM_SEED.get())+inv.countItem(Veilborne.FULL_DREAM_SEED.get());fullBase=inv.countItem(Veilborne.FULL_DREAM_SEED.get());report("seedfall begins holding "+seedBase+" seeds ("+fullBase+" full)");}
            ItemEntity got=item(level,Veilborne.DREAM_SEED.get(),p.position(),6);
            if(got==null)got=item(level,Veilborne.FULL_DREAM_SEED.get(),p.position(),8);
            if(got!=null){if(age%20==0)report("picking up "+got.getItem()+" at "+got.blockPosition().toShortString());pickUp(p,got);return;}
            int landed=inv.countItem(Veilborne.DREAM_SEED.get())+inv.countItem(Veilborne.FULL_DREAM_SEED.get())-seedBase;
            if(landed>seedsLanded){
                seedsLanded=landed;report("seed "+seedsLanded+" came up; holding "+inv.countItem(Veilborne.DREAM_SEED.get())+" + "+inv.countItem(Veilborne.FULL_DREAM_SEED.get())+" full");
                if(!has(SEED_ONE)){flags|=SEED_ONE;check("guiding_a_seed_to_the_soil_is_recorded",recorded(p,DreamService.SEED));}
                if(seedsLanded>=2)check("a_seed_landed_in_a_hoof_ring_comes_up_full",recorded(p,DreamService.FOOTPRINT)&&inv.countItem(Veilborne.FULL_DREAM_SEED.get())>fullBase);
                return;
            }
            DreamSeed seed=null;
            for(var en:level.getEntities().getAll())if(en instanceof DreamSeed ds&&ds.kind()==DreamSeed.Kind.FALL&&!ds.isRemoved()&&ds.state()!=DreamSeed.State.LAND&&ds.state()!=DreamSeed.State.FADE){seed=ds;break;}
            if(seed!=null){
                if(age%200==0)report("seed "+seed.state()+" at "+seed.blockPosition().toShortString()+", you at "+p.blockPosition().toShortString());
                if(!p.getOffhandItem().is(Items.POPPY))hold(p,Items.POPPY);
                if(seedsLanded==1&&!has(SEED_HOOF)){
                    // The second one goes to where the deer stepped by the tree.
                    DreamTimeline.Footfall best=null;double bd=1e9;
                    for(var f:DreamService.footfalls(level,d)){double dd=f.at().distanceTo(Vec3.atBottomCenterOf(d.tree));if(f.p()<pr&&dd<bd&&DreamSeed.soil(level,BlockPos.containing(f.at()).below())){bd=dd;best=f;}}
                    if(best!=null){
                        seed.debugState(DreamSeed.State.DRIFT);seed.debugGuide(null);
                        seed.teleportTo(best.at().x,best.at().y+2,best.at().z);place(p,best.at().add(1.2,0,0),best.at());
                        report("taking the second seed to a hoof print "+String.format("%.0f",bd)+" blocks from the tree");
                        flags|=SEED_HOOF;return;
                    }
                }
                Vec3 sp=seed.position();
                double g=Surface.top(level,net.minecraft.util.Mth.floor(sp.x),net.minecraft.util.Mth.floor(sp.z));
                if(sp.y-g>5)return;
                place(p,new Vec3(sp.x+1.1,g,sp.z),new Vec3(sp.x,g-2,sp.z));
                return;
            }
        }
        if(d.has(DreamData.TWIG_DROPPED)&&!has(TWIG)){
            ItemEntity twig=item(level,Veilborne.ANTLER_TWIG_ITEM.get(),Vec3.atBottomCenterOf(d.twig),4);
            flags|=TWIG;check("antler_twig_falls_near_you",twig!=null);
            if(twig!=null){pickUp(p,twig);return;}
        }
        if(pr>=.955&&!has(RETURN_SHOT)){flags|=RETURN_SHOT;place(p,spot,spot.add(-10,30,-24));shot("dreamlife-return.png",age);}
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){
        if(capture!=null){String name=capture;capture=null;var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),c->{});}
    }
    private DreamLifecyclePreview(){}
}
