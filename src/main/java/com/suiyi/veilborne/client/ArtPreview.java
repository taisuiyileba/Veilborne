package com.suiyi.veilborne.client;

import com.suiyi.veilborne.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * Opt-in art review harness (-PtidePreview=art). Builds a small stage in an isolated QA world and
 * frames close-ups of the visitors, the whale, the journal and the keepsakes. Never touches player saves.
 * Optional -Dveilborne.previewScenes=skiff,whale limits the run to scenes whose name starts with a prefix.
 */
public final class ArtPreview {
    private static final String DEFAULT_WORLD="Veilborne Art QA",WORLD=System.getProperty("veilborne.previewWorld",DEFAULT_WORLD);
    static final int SCENE_TICKS=110,CAPTURE_AT=92;
    /** weather: 0 the inverted sea, 1 the moon-husk bloom, 2 the mirror-sky pilgrimage, 3 the scarlet seam, 4 the silent daylight, 5 the verdant dream. */
    record Scene(String name,int stage,Consumer<Ctx> setup,Consumer<Ctx> camera,int weather){
        Scene(String name,int stage,Consumer<Ctx> setup,Consumer<Ctx> camera){this(name,stage,setup,camera,0);}
        Scene(String name,int stage,Consumer<Ctx> setup,Consumer<Ctx> camera,boolean moon){this(name,stage,setup,camera,moon?1:0);}
        boolean moon(){return weather==1;}
    }
    /** Per-scene context shared between the server setup and the client camera. */
    static final class Ctx {
        Minecraft mc;ServerLevel level;ServerPlayer player;BlockPos anchor;int tick;
        TideSkiff skiff(){
            if(mc.level==null)return null;
            for(var e:mc.level.entitiesForRendering())if(e instanceof TideSkiff s)return s;
            return null;
        }
    }
    private static final List<Scene> SCENES=new ArrayList<>();
    private static boolean loading,configured;
    private static int ticks,scene=-1;
    private static String screenshot;
    private static volatile BlockPos anchor;
    private static final Ctx CTX=new Ctx();

    static {
        for(int a:new int[]{0,70,150,215,290})
            SCENES.add(new Scene("skiff_orbit_"+a,42,ArtPreview::spawnSkiff,c->orbit(c,c.skiff(),3.4,1.1,a)));
        SCENES.add(new Scene("skiff_far",42,ArtPreview::spawnSkiff,c->orbit(c,c.skiff(),11,-.6,120)));
        // How fast it sails its loop, and that it glides: per-tick steps on the client, logged as VEILBORNE_MOTION.
        SCENES.add(new Scene("skiff_speed",42,ArtPreview::spawnSkiff,c->{orbit(c,c.skiff(),11,2,120);var s=c.skiff();if(s!=null)Steps.track(c,"skiff",s.position(),40,90);}));
        SCENES.add(new Scene("skiff_top",42,ArtPreview::spawnSkiff,c->orbit(c,c.skiff(),2.2,5.5,30)));
        SCENES.add(new Scene("skiff_ride",42,c->{spawnSkiff(c);c.level.getServer().execute(()->{
            for(var e:c.level.getAllEntities())if(e instanceof TideSkiff s){c.player.setGameMode(GameType.CREATIVE);c.player.teleportTo(s.getX(),s.getY(),s.getZ());s.interact(c.player,InteractionHand.MAIN_HAND);}
        });},c->{c.mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);look(c,null,c.mc.player.getYRot(),18);}));
        // The whale from the ground, beside it, ahead of it, beneath it and over its back.
        SCENES.add(new Scene("whale_ground",64,ArtPreview::spectate,c->lookAtWhale(c,null)));
        SCENES.add(new Scene("whale_side",66,ArtPreview::spectate,c->lookAtWhale(c,new Vec3(-6,-6,-64))));
        SCENES.add(new Scene("whale_front",66,ArtPreview::spectate,c->lookAtWhale(c,new Vec3(72,-14,-38))));
        SCENES.add(new Scene("whale_below",66,ArtPreview::spectate,c->lookAtWhale(c,new Vec3(4,-52,-10))));
        SCENES.add(new Scene("whale_wreck",66,ArtPreview::spectate,c->lookAtWhale(c,new Vec3(-10,20,-30))));
        SCENES.add(new Scene("whale_far",70,ArtPreview::spectate,c->lookAtWhale(c,new Vec3(-60,-90,-150))));
        // Sky: clouds thinning at the opening, the full sea, and a light fall.
        SCENES.add(new Scene("sky_opening",11,ArtPreview::spectate,c->fixed(c,new Vec3(0,1.6,0),0,-38)));
        SCENES.add(new Scene("sky_full",45,ArtPreview::spectate,c->fixed(c,new Vec3(0,1.6,0),20,-30)));
        SCENES.add(new Scene("sky_horizon",45,ArtPreview::spectate,c->fixed(c,new Vec3(0,1.6,0),200,-8)));
        SCENES.add(new Scene("sky_falls",45,ArtPreview::spectate,c->fixed(c,new Vec3(0,1.6,0),140,-22)));
        // Floating tide: the golden warning, then the lift.
        SCENES.add(new Scene("vortex_warning",47,c->{spectate(c);vortex(c,-60);},c->aim(c,new Vec3(-5,3.2,-5),Vec3.ZERO)));
        SCENES.add(new Scene("vortex_lift",47,c->{spectate(c);vortex(c,60);},c->aim(c,new Vec3(-6,2.4,-6),new Vec3(0,2,0))));
        // A scale falling on its beam, then landed.
        SCENES.add(new Scene("scale_falling",74,c->{spectate(c);landing(c,-30);},c->aim(c,new Vec3(10,3,-8),new Vec3(-2,14,2))));
        SCENES.add(new Scene("scale_landed",74,c->{spectate(c);landing(c,80);},c->aim(c,new Vec3(8,2.5,-6),new Vec3(-2,2,2))));
        // The journal, spread by spread, with three of five observations recorded.
        for(int i=0;i<4;i++){int spread=i;
            SCENES.add(new Scene("journal_"+i,64,c->{spectate(c);c.player.getPersistentData().putInt(TideService.RECORD,11);TideService.sync(c.player);},c->journal(c,spread)));}
        // Keepsakes: hotbar icons in the real GUI, and the bottled sea glowing on the ground.
        SCENES.add(new Scene("items_hotbar",64,ArtPreview::keepsakes,c->{c.mc.options.hideGui=false;aim(c,new Vec3(-1.5,1.9,-2.5),new Vec3(1.5,0.3,1.5));}));
        SCENES.add(new Scene("lantern_close",40,ArtPreview::keepsakes,c->{c.mc.options.hideGui=true;aim(c,new Vec3(.2,1.2,-.6),new Vec3(1.5,0.35,1.5));}));
    }
    static {
        // Frame-time probes: the same upward view with the full sea, then with the whale overhead, twice each.
        for(String round:new String[]{"a","b"}){
            SCENES.add(new Scene("perf_sea_"+round,45,ArtPreview::spectate,c->fixed(c,new Vec3(0,1.6,0),10,-35)));
            SCENES.add(new Scene("perf_whale_"+round,66,ArtPreview::spectate,c->fixed(c,new Vec3(0,1.6,0),10,-35)));
        }
        // At a two-chunk view distance the far plane is ~128 blocks; the whale must still draw whole.
        SCENES.add(new Scene("whale_short_view",66,ArtPreview::spectate,c->{c.mc.options.renderDistance().set(2);lookAtWhale(c,null);}));
        MoonPreview.scenes(SCENES);
        MirrorPreview.scenes(SCENES);
        ThreadPreview.scenes(SCENES);
        HushPreview.scenes(SCENES);
        DreamPreview.scenes(SCENES);
        LegacyPreview.scenes(SCENES);
    }
    private static void keepsakes(Ctx c){
        spectate(c);
        c.player.setGameMode(GameType.CREATIVE);
        var inv=c.player.getInventory();inv.clearContent();
        net.minecraft.world.item.Item[] kit={Veilborne.JOURNAL.get(),Veilborne.BOTTLE.get(),Veilborne.TIDE_BOTTLE.get(),Veilborne.WHALE_SCALE.get(),Veilborne.CONCH.get(),Veilborne.MANTLE.get(),Veilborne.ATTUNER.get()};
        for(int i=0;i<kit.length;i++)inv.setItem(i,new ItemStack(kit[i]));
        inv.selected=2;
        for(int[] o:new int[][]{{1,1},{2,1},{1,2}})c.level.setBlock(c.anchor.offset(o[0],0,o[1]),Veilborne.TIDE_LANTERN.get().defaultBlockState(),3);
    }
    private static void journal(Ctx c,int spread){
        c.mc.options.hideGui=true;
        fixed(c,new Vec3(0,1.6,0),0,-10);
        if(!(c.mc.screen instanceof TideJournalScreen)){var screen=new TideJournalScreen();c.mc.setScreen(screen);screen.showSpread(spread);}
    }
    private static void spectate(Ctx c){
        for(var e:c.level.getAllEntities())if(e instanceof TideSkiff s)s.finish();
        var d=TideData.get(c.level);d.vortex=BlockPos.ZERO;d.landing=BlockPos.ZERO;d.flags|=31;
        c.player.setGameMode(GameType.SPECTATOR);
    }
    private static void vortex(Ctx c,int age){var d=TideData.get(c.level);d.vortex=c.anchor;d.vortexAt=d.age-age;}
    private static void landing(Ctx c,int age){var d=TideData.get(c.level);d.landing=c.anchor.offset(-2,0,2);d.landingAt=d.age-age;}
    private static void fixed(Ctx c,Vec3 offset,float yaw,float pitch){
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        Vec3 eye=Vec3.atBottomCenterOf(c.anchor).add(offset);
        var p=c.mc.player;double y=eye.y-p.getEyeHeight();
        p.setPos(eye.x,y,eye.z);p.xo=p.xOld=eye.x;p.yo=p.yOld=y;p.zo=p.zOld=eye.z;
        look(c,null,yaw,pitch);
    }
    private static void aim(Ctx c,Vec3 eye,Vec3 focus){
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        Vec3 base=Vec3.atBottomCenterOf(c.anchor);
        place(c,base.add(eye),base.add(focus));
    }
    private static void lookAtWhale(Ctx c,Vec3 offset){
        if(TideClient.state==null)return;
        double p=TideClient.progress(0),time=TideClient.previousAge/20;
        Vec3 whale=WhaleRenderer.position(p,time);
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        Vec3 eye=offset==null?Vec3.atBottomCenterOf(c.anchor).add(0,1.6,0):whale.add(offset);
        place(c,eye,whale);
    }
    private static String filter(){return System.getProperty("veilborne.previewScenes","");}
    private static boolean wanted(Scene s){
        String f=filter();if(f.isEmpty())return true;
        for(String p:f.split(","))if(s.name().startsWith(p.trim()))return true;
        return false;
    }

    // ---------------------------------------------------------------- scene helpers
    private static void spawnSkiff(Ctx c){
        for(var e:c.level.getAllEntities())if(e instanceof TideSkiff s)s.finish();
        var skiff=Veilborne.SKIFF.get().create(c.level);
        skiff.setOrigin(c.anchor.offset(0,0,4));c.level.addFreshEntity(skiff);
        c.player.setGameMode(GameType.SPECTATOR);
    }
    private static void orbit(Ctx c,net.minecraft.world.entity.Entity target,double distance,double height,double angle){
        if(target==null)return;
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        double a=Math.toRadians(angle+target.getYRot());
        Vec3 focus=target.position().add(0,.9,0);
        Vec3 eye=focus.add(-Math.sin(a)*distance,height,Math.cos(a)*distance);
        place(c,eye,focus);
    }
    private static void place(Ctx c,Vec3 eye,Vec3 focus){
        var p=c.mc.player;
        double y=eye.y-p.getEyeHeight();
        p.setPos(eye.x,y,eye.z);p.xo=p.xOld=eye.x;p.yo=p.yOld=y;p.zo=p.zOld=eye.z;
        Vec3 d=focus.subtract(eye);
        look(c,null,(float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));
    }
    private static void look(Ctx c,Object unused,float yaw,float pitch){
        var p=c.mc.player;p.setYRot(yaw);p.setXRot(pitch);p.yRotO=yaw;p.xRotO=pitch;p.setYHeadRot(yaw);p.yHeadRotO=yaw;
    }

    // ---------------------------------------------------------------- driver
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        var mc=Minecraft.getInstance();
        // A minimized window has a zero-size framebuffer and every capture comes out 1x1: bring it back.
        long window=mc.getWindow().getWindow();
        if(org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window,org.lwjgl.glfw.GLFW.GLFW_ICONIFIED)!=0){org.lwjgl.glfw.GLFW.glfwRestoreWindow(window);report("VEILBORNE_RESTORED_WINDOW",false);}
        if(mc.level==null){
            if(!loading&&mc.screen!=null&&mc.getOverlay()==null){
                loading=true;
                var flows=mc.createWorldOpenFlows();
                Path dir=mc.gameDirectory.toPath().resolve("saves/"+WORLD);
                // The default stage is throwaway: reopening it with dimension-adding mods raises a confirmation
                // screen nobody is there to click, so it is rebuilt each run. Named worlds are left alone.
                if(WORLD.equals(DEFAULT_WORLD)&&Files.exists(dir))try(var walk=Files.walk(dir)){
                    for(Path p:walk.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(p);
                }catch(java.io.IOException ex){throw new RuntimeException(ex);}
                if(Files.exists(dir.resolve("level.dat")))flows.openWorld(WORLD,()->{});
                else flows.createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(8301282L,true,false),WorldPresets::createNormalWorldDimensions,mc.screen);
            }return;
        }
        if(mc.player==null||mc.getSingleplayerServer()==null)return;
        if(!configured){
            configured=true;mc.options.hideGui=true;mc.options.pauseOnLostFocus=false;mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.options.renderDistance().set(10);mc.options.simulationDistance().set(6);mc.options.fov().set(70);
            SCENES.removeIf(s->!wanted(s));
            report("Veilborne art capture: "+SCENES.size()+" scenes",true);
            mc.setScreen(null);
            mc.getSingleplayerServer().execute(()->{
                var l=mc.getSingleplayerServer().overworld();var p=l.players().get(0);
                BlockPos pos=l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.blockPosition()).above(6);
                for(int x=-14;x<=14;x++)for(int z=-10;z<=18;z++){
                    for(int y=0;y<=9;y++)l.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
                    l.setBlock(pos.offset(x,-2,z),Blocks.SANDSTONE.defaultBlockState(),3);
                    l.setBlock(pos.offset(x,-1,z),(x>=4&&z>=6?Blocks.WATER:(x+z&7)==0?Blocks.MOSS_BLOCK:Blocks.SMOOTH_SANDSTONE).defaultBlockState(),3);
                }
                MoonPreview.buildGarden(l,pos);
                MirrorPreview.build(l,pos);
                ThreadPreview.build(l,pos);
                HushPreview.build(l,pos);
                DreamPreview.build(l,pos);
                // Travellers and mirrors are placed by the scenes themselves, not by the weather.
                TideConfig.MIRROR_VISITORS.set(false);TideConfig.MIRROR_NATURAL.set(false);
                TideConfig.THREAD_VISITORS.set(false);TideConfig.THREAD_NATURAL.set(false);
                TideConfig.HUSH_VISITORS.set(false);TideConfig.HUSH_NATURAL.set(false);
                TideConfig.DREAM_VISITORS.set(false);TideConfig.DREAM_NATURAL.set(false);
                anchor=pos;
                p.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
                l.setDayTime(18000);l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,l.getServer());
                l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
                TideService.begin(l,pos,840);
            });
            return;
        }
        if(anchor==null)return;
        ticks++;
        CTX.mc=mc;CTX.anchor=anchor;CTX.tick=ticks%SCENE_TICKS;
        if(ticks%SCENE_TICKS==1){
            scene++;
            if(scene>=SCENES.size()){mc.stop();return;}
            Scene s=SCENES.get(scene);
            mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setScreen(null);mc.options.hideGui=true;
            if(mc.options.renderDistance().get()!=10)mc.options.renderDistance().set(10);
            frames.clear();
            mc.getSingleplayerServer().execute(()->{
                var l=mc.getSingleplayerServer().overworld();
                if(s.weather()==5){
                    var dd=DreamData.get(l);
                    if(!dd.active)DreamService.begin(l,DreamPreview.site(anchor),780);
                    dd.seed=DreamPreview.SEED;dd.age=dd.duration*s.stage()/100;dd.setDirty();
                }else if(s.weather()==4){
                    var hd=HushData.get(l);
                    if(!hd.active)HushService.begin(l,HushPreview.site(anchor),720);
                    hd.seed=HushPreview.SEED;hd.age=hd.duration*s.stage()/100;hd.setDirty();
                }else if(s.weather()==3){
                    var td=ThreadData.get(l);
                    if(!td.active)ThreadService.begin(l,ThreadPreview.site(anchor),780);
                    td.seed=ThreadPreview.SEED;td.age=td.duration*s.stage()/100;td.setDirty();
                }else if(s.weather()==2){
                    var xd=MirrorData.get(l);
                    if(!xd.active)MirrorService.begin(l,MirrorPreview.site(anchor),780);
                    xd.seed=MirrorPreview.SEED;xd.age=xd.duration*s.stage()/100;xd.setDirty();
                }else if(s.moon()){
                    var md=MoonData.get(l);
                    if(!md.active)MoonService.begin(l,MoonPreview.garden(anchor),720);
                    md.seed=MoonPreview.SEED;md.age=md.duration*s.stage()/100;md.setDirty();
                }else{
                    var d=TideData.get(l);
                    if(!d.active)TideService.begin(l,anchor,840);
                    d.age=d.duration*s.stage()/100;d.setDirty();
                }
                CTX.level=l;CTX.player=l.getServer().getPlayerList().getPlayers().get(0);CTX.player.stopRiding();
                if(s.weather()!=2&&MirrorGallery.inGallery(CTX.player.level()))MirrorGallery.leave(CTX.player);
                if(CTX.player.isUsingItem())CTX.player.stopUsingItem();
                s.setup().accept(CTX);
                TideService.broadcast(l);MoonService.broadcast(l);MirrorService.broadcast(l);ThreadService.broadcast(l);HushService.broadcast(l);DreamService.broadcast(l);
            });
        }
        if(scene>=0&&scene<SCENES.size()){
            try{SCENES.get(scene).camera().accept(CTX);}catch(RuntimeException ex){report("VEILBORNE_ART camera "+ex,false);}
            if(ticks%SCENE_TICKS==CAPTURE_AT){
                screenshot="art-"+SCENES.get(scene).name()+".png";
                if(SCENES.get(scene).name().startsWith("perf")&&!frames.isEmpty()){
                    var sorted=frames.stream().sorted().toList();
                    double mean=sorted.stream().mapToDouble(v->v).average().orElse(0);
                    report(String.format("VEILBORNE_FRAMES %s mean_ms=%.2f p95_ms=%.2f frames=%d",SCENES.get(scene).name(),mean,sorted.get((int)(sorted.size()*.95)),sorted.size()),false);
                }
            }
        }
    }
    private static final List<Double> frames=new ArrayList<>();
    private static long lastFrame;
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){
        long now=System.nanoTime();
        int t=ticks%SCENE_TICKS;
        if(lastFrame>0&&t>30&&t<CAPTURE_AT)frames.add((now-lastFrame)/1e6);
        lastFrame=now;
        if(screenshot==null)return;
        var mc=Minecraft.getInstance();String name=screenshot;screenshot=null;
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),c->report("VEILBORNE_CAPTURE "+name,false));
    }
    static void log(String text){report(text,false);}
    /**
     * Per-tick steps of something moving on the client, between two ticks of a scene: a mean speed, and how many ticks
     * it barely moved (under a quarter of the mean). Something that hops between server updates shows them as stalls.
     */
    static final class Steps {
        private static final List<Double> steps=new ArrayList<>();
        private static Vec3 last;
        static void track(Ctx c,String what,Vec3 at,int from,int to){
            if(c.tick==from){steps.clear();last=at;return;}
            if(c.tick<from||c.tick>to)return;
            steps.add(at.distanceTo(last));last=at;
            if(c.tick==to){
                double mean=steps.stream().mapToDouble(v->v).average().orElse(0);
                long stalls=steps.stream().filter(v->v<mean*.25).count();
                log(String.format("VEILBORNE_MOTION %s %s blocks_per_s=%.2f min_step=%.4f max_step=%.4f stalls=%d/%d",SCENES.get(scene).name(),what,mean*20,
                    steps.stream().mapToDouble(v->v).min().orElse(0),steps.stream().mapToDouble(v->v).max().orElse(0),stalls,steps.size()));
                log("VEILBORNE_STEPS "+String.join(" ",steps.stream().map(v->String.format("%.3f",v)).toList()));
            }
        }
    }
    private static synchronized void report(String text,boolean fresh){
        System.out.println(text);
        try{
            Path path=Minecraft.getInstance().gameDirectory.toPath().resolve("preview-art.log");
            if(fresh)Files.writeString(path,text+"\n");
            else Files.writeString(path,text+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        }catch(java.io.IOException ex){throw new RuntimeException(ex);}
    }
}
