package com.suiyi.veilborne;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sending the moments of what the weathers leave behind ({@link VeilCue}) and registering the /veilborne weather commands. */
public final class VeilService {
    @FunctionalInterface public interface Starter { void begin(ServerLevel level, BlockPos pos, int seconds); }

    public static void cue(ServerPlayer p,VeilCue cue){
        if(p.connection!=null&&p.connection.hasChannel(VeilCue.TYPE))PacketDistributor.sendToPlayer(p,cue);
    }
    public static void cue(ServerLevel level,VeilCue cue,double range){
        for(ServerPlayer p:level.players())if(p.position().distanceTo(cue.at())<range)cue(p,cue);
    }
    /** Builds one weather subcommand (start [seconds] | stop | stage <percent> | status) under /veilborne. */
    static LiteralArgumentBuilder<CommandSourceStack> weather(String name,IntSupplier duration,Starter begin,Consumer<ServerLevel> end,
            Predicate<ServerLevel> active,ObjIntConsumer<ServerLevel> stage,Function<ServerLevel,String> status){
        var sub=Commands.literal(name);
        sub.then(Commands.literal("start").executes(c->{
            begin.begin(c.getSource().getLevel(),BlockPos.containing(c.getSource().getPosition()),duration.getAsInt());return 1;
        }).then(Commands.argument("seconds",IntegerArgumentType.integer(120,3600)).executes(c->{
            begin.begin(c.getSource().getLevel(),BlockPos.containing(c.getSource().getPosition()),IntegerArgumentType.getInteger(c,"seconds"));return 1;
        })));
        sub.then(Commands.literal("stop").executes(c->{end.accept(c.getSource().getLevel());return 1;}));
        sub.then(Commands.literal("stage").then(Commands.argument("percent",IntegerArgumentType.integer(0,99)).executes(c->{
            var level=c.getSource().getLevel();
            if(!active.test(level))begin.begin(level,BlockPos.containing(c.getSource().getPosition()),duration.getAsInt());
            stage.accept(level,IntegerArgumentType.getInteger(c,"percent"));return 1;
        })));
        sub.then(Commands.literal("status").executes(c->{
            c.getSource().sendSuccess(()->Component.literal(status.apply(c.getSource().getLevel())),false);return 1;
        }));
        return sub;
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent e){
        var root=Commands.literal(Veilborne.ID).requires(s->s.hasPermission(2)&&s.getLevel().dimension().equals(Level.OVERWORLD));
        root.then(TideService.command());
        root.then(MoonService.command());
        root.then(MirrorService.command());
        root.then(ThreadService.command());
        root.then(HushService.command());
        root.then(DreamService.command());
        e.getDispatcher().register(root);
    }
    private VeilService(){}
}
