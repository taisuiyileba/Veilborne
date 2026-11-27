package com.suiyi.veilborne;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** Keepsakes and instruments of 绯线缝世. */
public class ThreadItem extends Item {
    public enum Kind { THREAD,SILK,CHARM,NEEDLE,CORE }
    private final Kind kind;
    public ThreadItem(Kind kind){
        super(new Properties().stacksTo(kind==Kind.THREAD||kind==Kind.SILK?64:kind==Kind.CORE?16:1).rarity(kind==Kind.SILK||kind==Kind.CORE?Rarity.RARE:kind==Kind.THREAD?Rarity.COMMON:Rarity.UNCOMMON));
        this.kind=kind;
    }
    public Kind kind(){return kind;}
    @Override public boolean isFoil(ItemStack stack){return kind==Kind.SILK||kind==Kind.CORE;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        if(kind!=Kind.NEEDLE)return InteractionResultHolder.pass(stack);
        if(player instanceof ServerPlayer p){
            var l=p.serverLevel();
            if(!level.dimension().equals(Level.OVERWORLD)||HushItem.anyWeather(l)||!level.canSeeSky(p.blockPosition())){
                p.displayClientMessage(Component.translatable("message.veilborne.needle_blocked"),true);
                return InteractionResultHolder.fail(stack);
            }
            level.playSound(null,p.blockPosition(),Veilborne.THREAD_OMEN.get(),SoundSource.PLAYERS,1,1);
            ThreadService.begin(l,p.blockPosition(),TideConfig.THREAD_DURATION.get());
            if(!p.isCreative())stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        lines.add(Component.translatable("tooltip.veilborne.thread_"+kind.name().toLowerCase()).withStyle(ChatFormatting.GRAY));
    }
}
