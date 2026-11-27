package com.suiyi.veilborne.client;

import com.suiyi.veilborne.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.minecraft.world.level.block.Blocks;

/** Explicitly opt-in development harness. Never loads or alters the user's worlds. */
public final class PreviewCapture {
    private static boolean loading,configured;
    private static int ticks,scene=-1;
    private static String screenshot;
    private static final int[] STAGES={8,30,62,74,91,99};
    private static final boolean DETAIL="detail".equals(System.getProperty("veilborne.previewMode"));
    private static final String WORLD=System.getProperty("veilborne.previewWorld",DETAIL?"Veilborne Detail QA":"Veilborne Preview");
    private static volatile BlockPos anchor;
    private static long lastFrame;
    private static final java.util.List<Double> frameTimes=new java.util.ArrayList<>();
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        var mc=Minecraft.getInstance();
        if(mc.level==null){
            if(!loading&&mc.screen!=null&&mc.getOverlay()==null){
                loading=true;
                var flows=mc.createWorldOpenFlows();
                java.nio.file.Path path=mc.gameDirectory.toPath().resolve("saves/"+WORLD+"/level.dat");
                if(java.nio.file.Files.exists(path))flows.openWorld(WORLD,()->{});
                else flows.createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(8301282L,true,false),WorldPresets::createNormalWorldDimensions,mc.screen);
            }return;
        }
        if(mc.player==null||mc.getSingleplayerServer()==null)return;
        if(!configured){
            configured=true;mc.options.hideGui=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(DETAIL?8:12);mc.options.simulationDistance().set(5);mc.options.fov().set(75);
            try{java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("preview-"+(DETAIL?"detail":"sky")+".log"),"Veilborne runtime capture\n");}catch(java.io.IOException ex){throw new RuntimeException(ex);}
            mc.options.getCameraType();mc.setScreen(null);
            mc.getSingleplayerServer().execute(()->{
                var l=mc.getSingleplayerServer().overworld();
                var p=l.players().get(0);BlockPos pos=l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.blockPosition());
                if(DETAIL){
                    pos=pos.above(10);anchor=pos;
                    for(int x=-18;x<=22;x++)for(int z=-12;z<=22;z++){
                        for(int y=0;y<=7;y++)l.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
                        l.setBlock(pos.offset(x,-2,z),Blocks.SANDSTONE.defaultBlockState(),3);
                        l.setBlock(pos.offset(x,-1,z),(x>=7&&z>=2?Blocks.WATER:Blocks.SMOOTH_SANDSTONE).defaultBlockState(),3);
                    }
                    for(int x=-5;x<=5;x++)for(int z=8;z<=16;z++){
                        l.setBlock(pos.offset(x,-1,z),Blocks.SPRUCE_PLANKS.defaultBlockState(),3);
                        l.setBlock(pos.offset(x,5,z),Blocks.SPRUCE_PLANKS.defaultBlockState(),3);
                        for(int y=0;y<5;y++)if(x==-5||x==5||z==8||z==16)
                            l.setBlock(pos.offset(x,y,z),(y>=1&&y<=3&&Math.abs(x)<=3&&z==8?Blocks.GLASS:Blocks.SPRUCE_PLANKS).defaultBlockState(),3);
                    }
                    l.setBlock(pos.offset(0,0,12),Blocks.SPRUCE_PLANKS.defaultBlockState(),3);
                    l.setBlock(pos.offset(0,1,12),Veilborne.TIDE_LANTERN.get().defaultBlockState(),3);
                    l.setBlock(pos.offset(2,0,14),Blocks.LANTERN.defaultBlockState(),3);
                }
                anchor=pos;p.teleportTo(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);p.setYRot(0);p.setXRot(-32);
                p.setGameMode(GameType.CREATIVE);l.setDayTime(18000);l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,l.getServer());
                TideService.begin(l,pos,840);
            });
        }
        float yaw=DETAIL?(scene==2?180:scene==3?90:0):0;
        float pitch=DETAIL?(scene==2?-15:scene==3?12:scene==5?50:8):-32;
        mc.player.setYRot(yaw);mc.player.setXRot(pitch);mc.player.yRotO=yaw;mc.player.xRotO=pitch;
        ticks++;
        if(ticks%180==1){
            scene++;
            if(scene>=(DETAIL?8:STAGES.length)){mc.stop();return;}
            frameTimes.clear();
            int stage=DETAIL?(scene==5?45:62):STAGES[scene];int sceneNumber=scene;
            if(DETAIL){if(scene==4)mc.setScreen(new TideJournalScreen());else mc.setScreen(null);}
            mc.getSingleplayerServer().execute(()->{
                var l=mc.getSingleplayerServer().overworld();var d=TideData.get(l);d.age=d.duration*stage/100;d.setDirty();TideService.broadcast(l);
                if(DETAIL&&anchor!=null){
                    var p=l.players().get(0);p.stopRiding();p.setGameMode(GameType.CREATIVE);
                    double x=anchor.getX()+.5,y=anchor.getY(),z=anchor.getZ()+.5;
                    if(sceneNumber==0){p.teleportTo(x,y+1,z-7);}
                    if(sceneNumber==1){
                        var skiff=Veilborne.SKIFF.get().create(l);skiff.setOrigin(anchor.offset(0,0,5));l.addFreshEntity(skiff);
                        p.teleportTo(x+1,y+1,z);skiff.interact(p,net.minecraft.world.InteractionHand.MAIN_HAND);
                        report("VEILBORNE_CHECK boarding="+p.isPassenger());
                    }
                    if(sceneNumber==2)p.teleportTo(x,y+1,z+12);
                    if(sceneNumber==3)p.teleportTo(x+3,y+1,z+12);
                    if(sceneNumber==4)p.teleportTo(x+3,y+1,z+12);
                    if(sceneNumber==5){
                        p.teleportTo(x,y,z-3);p.setGameMode(GameType.SURVIVAL);d.vortex=anchor.offset(0,0,-3);d.vortexAt=d.age;d.flags|=4;
                    }
                    if(sceneNumber==6){d.radius=96;p.teleportTo(x+112,y+10,z);}
                    if(sceneNumber==7){TideService.end(l);p.teleportTo(x,y+1,z-7);}
                    TideService.broadcast(l);
                }
            });
        }
        if(ticks%180==150){
            screenshot=DETAIL?String.format("detail-%02d.png",scene):String.format("starfall-%02d.png",STAGES[scene]);
            if(DETAIL&&scene==5)report("VEILBORNE_CHECK vortexLift="+(mc.player.getY()-anchor.getY())+" slowFall="+mc.player.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING));
            if(!frameTimes.isEmpty()){
                var sorted=frameTimes.stream().sorted().toList();double sum=sorted.stream().mapToDouble(v->v).sum();
                report("VEILBORNE_FRAMES scene="+scene+" mean_ms="+sum/sorted.size()+" p95_ms="+sorted.get((int)(sorted.size()*.95))+" frames="+sorted.size()+" strength="+TideClient.strength());
            }
        }
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){
        long now=System.nanoTime();if(configured&&ticks%180>35&&lastFrame>0)frameTimes.add((now-lastFrame)/1_000_000.0);lastFrame=now;
        if(screenshot!=null){
            var mc=Minecraft.getInstance();String name=screenshot;screenshot=null;
            Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),c->report("VEILBORNE_CAPTURE "+name+" "+c.getString()));
        }
    }
    private static synchronized void report(String text){
        System.out.println(text);
        try{java.nio.file.Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("preview-"+(DETAIL?"detail":"sky")+".log"),text+"\n",java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);}catch(java.io.IOException ex){throw new RuntimeException(ex);}
    }
}
