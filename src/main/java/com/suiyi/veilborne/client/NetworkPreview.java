package com.suiyi.veilborne.client;

import com.suiyi.veilborne.*;
import java.net.InetAddress;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;

/** Two real clients, using only a loopback listener and isolated QA worlds. */
public final class NetworkPreview {
    private static final boolean HOST="host".equals(System.getProperty("veilborne.networkPreview"));
    private static boolean started,configured,wasConnected,capture,ending;
    private static int ticks,joinedTicks;
    private static volatile boolean twoPlayers;
    private static Path log(){return Minecraft.getInstance().gameDirectory.toPath().resolve("network.log");}
    private static synchronized void report(String s){
        System.out.println(s);
        try{Files.writeString(log(),s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}
    }
    public static void packet(TidePacket p){
        report("SNAPSHOT "+p.active()+" "+p.seed()+" "+p.age()+" "+p.duration()+" "+p.radius()+" "+p.center().asLong()+" "+p.vortex().asLong()+" "+p.landing().asLong());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        var mc=Minecraft.getInstance();
        if(!started&&mc.screen!=null&&mc.getOverlay()==null){
            started=true;
            try{Files.writeString(log(),"Network QA "+(HOST?"host":"guest")+"\n");}catch(Exception ex){throw new RuntimeException(ex);}
            mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.renderDistance().set(6);mc.options.simulationDistance().set(5);mc.options.fov().set(75);
            if(HOST){
                var flow=mc.createWorldOpenFlows();
                if(Files.exists(mc.gameDirectory.toPath().resolve("saves/Veilborne Network QA/level.dat")))flow.openWorld("Veilborne Network QA",()->{});
                else flow.createFreshLevel("Veilborne Network QA",new LevelSettings("Veilborne Network QA",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(8301282L,true,false),WorldPresets::createNormalWorldDimensions,mc.screen);
            }else ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString("127.0.0.1:25588"),new ServerData("Veilborne loopback QA","127.0.0.1:25588",ServerData.Type.OTHER),false,null);
        }
        if(mc.level==null||mc.player==null){if(wasConnected&&!HOST){report("DISCONNECTED_CLEANLY");mc.stop();}return;}
        wasConnected=true;
        mc.player.setYRot(0);mc.player.setXRot(-32);mc.player.yRotO=0;mc.player.xRotO=-32;
        if(HOST&&!configured){
            configured=true;mc.setScreen(null);
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var l=server.overworld();var p=l.players().get(0);
                var origin=l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.blockPosition());
                p.teleportTo(origin.getX()+.5,origin.getY()+1,origin.getZ()+.5);p.setGameMode(GameType.CREATIVE);
                l.setDayTime(18000);l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
                var restored=TideData.get(l);report("RESUME active="+restored.active+" seed="+restored.seed+" age="+restored.age+" flags="+restored.flags);
                TideService.begin(l,origin,840);var d=TideData.get(l);d.age=10080;d.setDirty();TideService.broadcast(l);
                try{server.setUsesAuthentication(false);server.getConnection().startTcpServerListener(InetAddress.getByName("127.0.0.1"),25588);report("LOOPBACK_READY 25588");}catch(Exception ex){throw new RuntimeException(ex);}
            });
        }
        ticks++;
        if(HOST&&ticks%20==0)mc.getSingleplayerServer().execute(()->{
            var l=mc.getSingleplayerServer().overworld();
            if(l.players().size()>=2&&!twoPlayers){
                twoPlayers=true;var d=TideData.get(l);
                for(var p:l.players())if(p.getGameProfile().getName().equals("TideGuest")){
                    p.setGameMode(GameType.CREATIVE);p.teleportTo(d.center.getX()+3.5,d.center.getY()+1,d.center.getZ()+.5);
                }report("TWO_PLAYERS_JOINED phase="+d.progress());
            }
        });
        if(HOST&&twoPlayers){
            joinedTicks++;
            if(joinedTicks==160){capture=true;report("HOST_RENDER strength="+TideClient.strength()+" phase="+TideClient.progress(1));}
            if(joinedTicks==260)mc.getSingleplayerServer().execute(()->{var l=mc.getSingleplayerServer().overworld();var d=TideData.get(l);d.age=(int)(d.duration*.76);d.setDirty();TideService.broadcast(l);});
            if(joinedTicks==520)mc.getSingleplayerServer().execute(()->{TideService.end(mc.getSingleplayerServer().overworld());report("WEATHER_STOPPED");});
            if(joinedTicks==620){report("HOST_CLEANUP strength="+TideClient.strength());ending=true;}
            if(ending&&joinedTicks>680)mc.stop();
        }
        if(!HOST&&ticks==180){capture=true;report("GUEST_RENDER strength="+TideClient.strength()+" phase="+TideClient.progress(1));}
        if(!HOST&&TideClient.state!=null&&!TideClient.state.active()&&ticks>220&&ticks%20==0)report("GUEST_CLEANUP strength="+TideClient.strength());
        if(ticks>6000){report("QA_TIMEOUT");mc.stop();}
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){
        if(capture){capture=false;var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"network-"+(HOST?"host":"guest")+".png",mc.getMainRenderTarget(),c->report("CAPTURE "+c.getString()));}
    }
}
