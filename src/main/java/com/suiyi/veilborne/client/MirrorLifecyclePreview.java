package com.suiyi.veilborne.client;

import com.suiyi.veilborne.*;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
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
 * -PtidePreview=mirrorlife: a whole naturally triggered 镜天巡礼 at a short duration, run tick by tick. The player
 * walks a little early on (so a late traveller has something to replay), reveals the extra door with the prism,
 * walks the gallery to its pedestal and back, picks a wrong mirror and then the right one, meets the traveller,
 * steps up to a shard and back (it must only warn), stays at another (it must burst), meets the pilgrim's eye,
 * is walked out of the gallery before the mirror breaks, and catches the last shard. Every rule is checked and
 * logged to run/mirror-lifecycle.log.
 */
public final class MirrorLifecyclePreview {
    private static final String WORLD="Veilborne Mirror Lifecycle QA";
    private static final int DURATION=150;
    private static boolean started,configured;
    private static volatile boolean seen,done;
    private static int flags,ticks,exitTicks,waitTicks,warnAt;
    private static BlockPos home=BlockPos.ZERO;
    private static String capture;
    // Steps, in the order they may happen.
    private static final int DAY=1,WALKED=2,DOOR_SEEN=4,DOOR=8,GALLERY=16,FRAMES=32,TRAVELER_SEEN=64,TRAVELER=128,
        SHARD_WARNED=256,SHARD_CALM=512,SHARD_BURST=1024,PILGRIM=2048,KNOWN=4096,GALLERY_LATE=8192,GALLERY_OUT=16384,KEEPSAKE=32768,
        REFLECT_SHOT=65536,CRACK_SHOT=131072;
    private static Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("mirror-lifecycle.log");}
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
    /** Puts something in hand without losing what was there: that goes back into the inventory. */
    private static void hold(ServerPlayer p,ItemStack s){
        var inv=p.getInventory();ItemStack old=inv.getSelected();
        inv.setItem(inv.selected,s);
        if(!old.isEmpty()&&!old.is(Veilborne.PRISM.get()))inv.add(old);
    }
    private static <T extends net.minecraft.world.entity.Entity> T first(ServerLevel level,Class<T> type){
        for(var e:level.getEntities().getAll())if(type.isInstance(e)&&!e.isRemoved())return type.cast(e);
        return null;
    }
    private static long count(ServerLevel level,Class<?> type){
        long n=0;for(var e:level.getEntities().getAll())if(type.isInstance(e)&&!e.isRemoved())n++;return n;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        var mc=Minecraft.getInstance();
        if(!started&&mc.screen!=null&&mc.getOverlay()==null){
            started=true;try{Files.writeString(file(),"Mirror-sky pilgrimage lifecycle QA\n");}catch(Exception ex){throw new RuntimeException(ex);}
            mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.renderDistance().set(8);mc.options.simulationDistance().set(6);
            Path dir=mc.gameDirectory.toPath().resolve("saves/"+WORLD);
            if(Files.exists(dir))try(var walk=Files.walk(dir)){for(Path p:walk.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(p);}catch(java.io.IOException ex){throw new RuntimeException(ex);}
            mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.SURVIVAL,false,Difficulty.EASY,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261003L,true,false),WorldPresets::createNormalWorldDimensions,mc.screen);
        }
        if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
        if(!configured){
            configured=true;mc.setScreen(null);
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var level=server.overworld();var p=level.players().get(0);
                BlockPos pos=level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.blockPosition()).above(4);
                // A broad open meadow, and one small stone house for the mirror to add a door to.
                for(int x=-60;x<=60;x++)for(int z=-60;z<=60;z++){
                    for(int y=0;y<=18;y++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
                    level.setBlock(pos.offset(x,-1,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
                }
                for(int x=9;x<=13;x++)for(int y=0;y<=3;y++)for(int z=3;z<=7;z++)level.setBlock(pos.offset(x,y,z),Blocks.STONE_BRICKS.defaultBlockState(),2);
                home=pos;
                p.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);p.getInventory().clearContent();
                level.setDayTime(6000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
                var d=TideData.get(level);d.active=false;d.quietTicks=1;d.setDirty();
                TideConfig.naturalOnly(2);TideConfig.MIRROR_DURATION.set(DURATION);
                TideConfig.MIRROR_VISITORS.set(true);TideConfig.MIRROR_BEASTS.set(true);TideConfig.MIRROR_GALLERY.set(true);
            });
        }
        ticks++;
        if(ticks%2==0&&!done)mc.getSingleplayerServer().execute(MirrorLifecyclePreview::step);
        if(done&&++exitTicks==140){check("client_effects_faded",MirrorClient.strength()==0);mc.stop();}
        if(ticks>DURATION*20+1800&&!done){report("FAIL lifecycle_timeout");mc.stop();}
    }

    private static void step(){
        var server=Minecraft.getInstance().getSingleplayerServer();
        var level=server.overworld();
        if(server.getPlayerList().getPlayers().isEmpty())return;
        ServerPlayer p=server.getPlayerList().getPlayers().get(0);
        var d=MirrorData.get(level);
        Vec3 c=Vec3.atBottomCenterOf(home);
        if(d.active&&!seen){
            seen=true;
            check("natural_mirror_started",!TideData.get(level).active&&!MoonData.get(level).active);
            check("started_where_someone_stands",Math.hypot(d.center.getX()-home.getX(),d.center.getZ()-home.getZ())<4);
            // Both variants, so every encounter can be checked in one run.
            d.variant=MirrorData.VARIANT_FRAMES|MirrorData.VARIANT_BEASTS;d.setDirty();
            report("NATURAL seed="+d.seed+" duration="+d.duration);
        }
        if(!d.active){
            if(seen&&!done){
                int records=TideService.records(p);
                int all=MirrorService.FIRST|MirrorService.PILGRIM|MirrorService.DOOR|MirrorService.CORRIDOR|MirrorService.FRAMES|MirrorService.TRAVELER|MirrorService.KNOWN;
                check("all_seven_observations",(records&all)==all);
                if((records&all)!=all)report("records="+Integer.toBinaryString(records));
                check("echo_mirror_from_the_gallery",p.getInventory().contains(new ItemStack(Veilborne.ECHO_MIRROR.get())));
                check("mirror_sand_collected",p.getInventory().countItem(Veilborne.MIRROR_SAND.get())>=2);
                check("last_shard_caught",p.getInventory().contains(new ItemStack(Veilborne.SKY_SHARD_ITEM.get())));
                check("visitors_cleaned_up",count(level,MirrorTraveler.class)==0&&count(level,MirrorFrames.class)==0);
                check("beasts_gone_with_the_mirror",count(level,MirrorBeast.class)==0);
                check("nobody_left_in_the_gallery",!MirrorGallery.inGallery(p.level())&&!p.getPersistentData().contains(MirrorGallery.TAG));
                check("house_untouched",level.getBlockState(home.offset(11,1,5)).is(Blocks.STONE_BRICKS));
                check("no_weather_damage",p.getHealth()>=19.9f);
                check("quiet_period_set",TideData.get(level).quietTicks>=24000);
                done=true;
            }
            return;
        }
        double prog=d.progress();
        if(p.isDeadOrDying()){report("FAIL player_died");return;}
        // The rules below are checked in the order a visit would find them.
        if(d.age>=300&&!has(DAY)){flags|=DAY;int before=d.age;level.setDayTime(level.getDayTime()+24000);check("day_jump_preserves_timeline",d.active&&d.age==before);}
        // Early on, walk a line away from home and come back: the traveller will replay it.
        if(d.age>=100&&d.age<330&&!has(WALKED)){
            double t=(d.age-100)/230.0;
            place(p,c.add(-26*Math.sin(Math.PI*t),0,0),c.add(-40,1.6,0));
            if(d.age>=328){flags|=WALKED;place(p,c,c.add(0,1.6,10));}
        }
        if(!has(WALKED))return;
        if(prog>.44&&prog<.5&&!has(REFLECT_SHOT)){flags|=REFLECT_SHOT;place(p,c,c.add(0,30,20));capture="mirrorlife-reflect.png";return;}

        // The extra door: it is not there, until the prism is held on it long enough.
        if(d.hasDoor()&&MirrorTimeline.doorOpen(prog)&&MirrorTimeline.door(prog)>.5&&!d.revealed()&&!has(DOOR)){
            Vec3 door=MirrorTimeline.doorCenter(d.door,d.doorFace,d.has(MirrorData.FREESTANDING));
            if(!has(DOOR_SEEN)){
                flags|=DOOR_SEEN;
                check("door_went_into_the_house",level.getBlockState(d.door).is(Blocks.STONE_BRICKS)||d.has(MirrorData.FREESTANDING));
                hold(p,new ItemStack(Veilborne.PRISM.get()));
                place(p,c,c.add(-20,1.6,0));
                for(int i=0;i<MirrorService.DOOR_LOOK+4;i++)MirrorService.observe(p,i);
                check("prism_aimed_away_reveals_nothing",!d.revealed());
                MirrorService.stopObserving(p);
                waitTicks=0;
            }
            Vec3 stand=door.add(MirrorTimeline.faceNormal(d.doorFace).scale(6)).subtract(0,1,0);
            place(p,new Vec3(stand.x,d.door.getY(),stand.z),door);
            MirrorService.observe(p,waitTicks++);
            if(d.revealed()){flags|=DOOR;check("prism_reveals_the_door",(TideService.records(p)&MirrorService.DOOR)!=0);}
            else if(waitTicks>MirrorService.DOOR_LOOK+10){flags|=DOOR;check("prism_reveals_the_door",false);}
            return;
        }
        // Through it into the gallery, onto the gold plate before the pedestal, and back out.
        if(d.revealed()&&!has(GALLERY)&&MirrorTimeline.doorOpen(prog)){
            if(!MirrorGallery.inGallery(p.level())){
                hold(p,ItemStack.EMPTY);
                boolean in=MirrorGallery.enter(p,d);
                check("door_opens_into_the_gallery",in&&MirrorGallery.inGallery(p.level()));
                if(!in){flags|=GALLERY;return;}
                float health=p.getHealth();
                p.hurt(p.damageSources().generic(),6);
                check("nothing_hurts_in_the_gallery",p.getHealth()>=health);
                waitTicks=0;capture="mirrorlife-gallery.png";
                return;
            }
            BlockPos o=MirrorGallery.origin(d.seed);
            Vec3 plate=Vec3.atLowerCornerOf(o).add(MirrorGallery.PLATE);
            p.teleportTo((ServerLevel)p.level(),plate.x,plate.y,plate.z,180,0);
            if(p.getInventory().contains(new ItemStack(Veilborne.ECHO_MIRROR.get()))||(waitTicks+=2)>MirrorGallery.PLATE_TICKS*3){
                check("pedestal_gives_the_echo_mirror",p.getInventory().contains(new ItemStack(Veilborne.ECHO_MIRROR.get()))&&(TideService.records(p)&MirrorService.CORRIDOR)!=0);
                MirrorGallery.leave(p);
                check("gallery_leads_back_to_the_door",p.level().dimension().equals(Level.OVERWORLD)&&p.position().distanceTo(Vec3.atBottomCenterOf(d.door))<3);
                flags|=GALLERY;
                place(p,c,c.add(0,1.6,10));
            }
            return;
        }
        // Three mirrors: a wrong one first (harmless), then the one that shows the present.
        MirrorFrames frames=first(level,MirrorFrames.class);
        if(frames!=null&&frames.state()==MirrorFrames.State.WAITING&&!has(FRAMES)){
            flags|=FRAMES;
            hold(p,new ItemStack(Veilborne.PRISM.get()));
            int wrong=(frames.present()+1)%3;
            float health=p.getHealth();
            place(p,frames.position(),frames.glass(wrong));
            for(int i=0;i<=MirrorService.FRAME_LOOK+1;i++)MirrorService.observe(p,i);
            check("wrong_mirror_is_harmless",frames.state()==MirrorFrames.State.WAITING&&p.getHealth()>=health);
            MirrorService.stopObserving(p);
            place(p,frames.position(),frames.glass(frames.present()));
            for(int i=0;i<=MirrorService.FRAME_LOOK+1;i++)MirrorService.observe(p,i);
            check("present_mirror_is_recognised",frames.state()!=MirrorFrames.State.WAITING&&(TideService.records(p)&MirrorService.FRAMES)!=0);
            MirrorService.stopObserving(p);
            hold(p,ItemStack.EMPTY);
            return;
        }
        // Mirror sand left by the three mirrors: walk over it.
        if(has(FRAMES)&&count(level,MirrorFrames.class)==0||has(FRAMES)&&frames!=null&&frames.state()!=MirrorFrames.State.WAITING){
            for(var entity:level.getEntities().getAll())if(entity instanceof ItemEntity item&&item.getItem().is(Veilborne.MIRROR_SAND.get())&&item.distanceTo(p)<24)p.teleportTo(item.getX(),item.getY(),item.getZ());
        }
        // The late traveller: it walks my old path, a little further, and points. Going to it is noted.
        MirrorTraveler traveler=first(level,MirrorTraveler.class);
        if(traveler!=null&&!has(TRAVELER_SEEN)){
            flags|=TRAVELER_SEEN;
            double dist=traveler.position().distanceTo(p.position());
            check("traveller_appears_at_a_distance",dist>=6&&dist<=30);
            check("traveller_walks_an_old_path",Math.abs(traveler.getZ()-c.z)<3&&traveler.getX()<c.x-3);
        }
        if(traveler!=null&&has(TRAVELER_SEEN)&&!has(TRAVELER)&&(traveler.state()==MirrorTraveler.State.POINT||traveler.state()==MirrorTraveler.State.FADE)){
            place(p,traveler.position().add(1,0,0),traveler.position().add(0,1.6,0));
            if((TideService.records(p)&MirrorService.TRAVELER)!=0){flags|=TRAVELER;check("meeting_the_traveller_is_noted",true);place(p,c,c.add(0,1.6,10));}
            return;
        }
        // The glass beasts' shards: step up to one and back off during its warning, and nothing happens.
        if(d.has(MirrorData.SHARDS)&&!d.shards[0].equals(BlockPos.ZERO)&&!has(SHARD_CALM)&&prog<.6){
            Vec3 s=Vec3.atBottomCenterOf(d.shards[0]);
            if(!has(SHARD_WARNED)){
                place(p,s.add(3,0,0),s.add(0,1,0));
                if(d.shardWarn[0]>0){flags|=SHARD_WARNED;warnAt=d.age;check("shard_warns_before_harm",count(level,MirrorBeast.class)==0);place(p,c,c.add(0,1.6,10));}
            }else if(d.age-warnAt>MirrorService.SHARD_WARN+6){
                flags|=SHARD_CALM;
                check("stepping_back_calms_the_shard",d.shardWarn[0]==0&&!d.has(MirrorData.SHARD_BURST_0)&&count(level,MirrorBeast.class)==0);
            }
            return;
        }
        // Stay at the other one: it bursts, and the beasts can be broken like glass.
        if(has(SHARD_CALM)&&!has(SHARD_BURST)&&prog<.6){
            int i=!d.shards[1].equals(BlockPos.ZERO)?1:0;
            Vec3 s=Vec3.atBottomCenterOf(d.shards[i]);
            place(p,s.add(2,0,0),s.add(0,1,0));
            if(d.has(i==0?MirrorData.SHARD_BURST_0:MirrorData.SHARD_BURST_1)){
                flags|=SHARD_BURST;
                check("staying_wakes_the_beasts",count(level,MirrorBeast.class)>0);
                // A copy: cracking one while walking the live list can skip the next, and a missed beast kills the player later.
                for(var entity:com.google.common.collect.ImmutableList.copyOf(level.getEntities().getAll()))if(entity instanceof MirrorBeast b)b.crack();
                p.heal(20);
                place(p,c,c.add(0,1.6,10));
            }
            return;
        }
        // The pilgrim: it walks by, stops beside the one it came for, and turns to look. Meet its eye with the prism.
        if(d.has(MirrorData.PILGRIM)&&MirrorTimeline.pilgrim(prog)>.5){
            if(!has(PILGRIM)){flags|=PILGRIM;check("pilgrim_walks_from_where_someone_stood",Vec3.atBottomCenterOf(d.pilgrimOrigin).distanceTo(new Vec3(p.getX(),d.pilgrimOrigin.getY(),p.getZ()))<40);}
            double w=MirrorTimeline.walk(prog);
            if(MirrorTimeline.gaze(w)>.6&&!has(KNOWN)){
                flags|=KNOWN;
                Vec3 origin=Vec3.atBottomCenterOf(d.pilgrimOrigin);
                Vec3 at=MirrorTimeline.pilgrimAt(origin,d.pilgrimYaw,MirrorTimeline.along(w));
                check("pilgrim_stops_beside_the_watcher",Math.abs(at.distanceTo(origin)-MirrorTimeline.BESIDE)<2);
                check("pilgrim_was_witnessed",(TideService.records(p)&MirrorService.PILGRIM)!=0);
                hold(p,new ItemStack(Veilborne.PRISM.get()));
                Vec3 to=new Vec3(at.x-origin.x,0,at.z-origin.z).normalize();
                place(p,origin,origin.add(0,1.6,0).add(to.scale(10)).add(0,6.5,0));
                for(int i=0;i<=MirrorService.KNOWN_LOOK+2;i++)MirrorService.observe(p,i);
                check("meeting_its_eye_is_known",(TideService.records(p)&MirrorService.KNOWN)!=0);
                MirrorService.stopObserving(p);hold(p,ItemStack.EMPTY);
                capture="mirrorlife-pilgrim.png";
            }
            return;
        }
        if(prog>.8&&prog<.84&&!has(CRACK_SHOT)){flags|=CRACK_SHOT;place(p,c,c.add(0,30,20));capture="mirrorlife-crack.png";return;}
        // Go back into the gallery just before it closes, and stay: it must walk me out before the mirror breaks.
        if(prog>=.86&&MirrorTimeline.doorOpen(prog)&&!has(GALLERY_LATE)&&d.revealed()){
            flags|=GALLERY_LATE;
            check("gallery_open_late",MirrorGallery.enter(p,d));
            return;
        }
        if(has(GALLERY_LATE)&&!has(GALLERY_OUT)&&prog>=.9){
            if(!MirrorGallery.inGallery(p.level())){flags|=GALLERY_OUT;check("gallery_empties_before_the_mirror_breaks",p.level().dimension().equals(Level.OVERWORLD));place(p,c,c.add(0,1.6,10));}
            else if(prog>.91){flags|=GALLERY_OUT;check("gallery_empties_before_the_mirror_breaks",false);MirrorGallery.leave(p);}
            return;
        }
        // The last shard falls near someone: catch it.
        if(!d.keepsake.equals(BlockPos.ZERO)&&!has(KEEPSAKE)){
            for(var entity:level.getEntities().getAll())if(entity instanceof ItemEntity item&&item.getItem().is(Veilborne.SKY_SHARD_ITEM.get())){
                p.teleportTo(item.getX(),item.getY(),item.getZ());flags|=KEEPSAKE;
            }
        }
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){
        if(capture!=null){String name=capture;capture=null;var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),c->{});}
    }
}
