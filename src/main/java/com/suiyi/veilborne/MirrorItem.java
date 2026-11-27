package com.suiyi.veilborne;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** Keepsakes and instruments of the mirror-sky pilgrimage. */
public class MirrorItem extends Item {
    public enum Kind { SAND,PRISM,ECHO,SKY_MIRROR,HEART }
    private final Kind kind;
    public MirrorItem(Kind kind){
        super(new Properties().stacksTo(kind==Kind.SAND?64:kind==Kind.HEART?16:1).rarity(kind==Kind.ECHO||kind==Kind.HEART?Rarity.RARE:kind==Kind.SAND?Rarity.COMMON:Rarity.UNCOMMON));
        this.kind=kind;
    }
    public Kind kind(){return kind;}
    public static boolean isPrism(ItemStack s){return s.getItem() instanceof MirrorItem m&&m.kind==Kind.PRISM;}
    @Override public UseAnim getUseAnimation(ItemStack stack){return kind==Kind.PRISM?UseAnim.SPYGLASS:UseAnim.NONE;}
    @Override public int getUseDuration(ItemStack stack,LivingEntity entity){return kind==Kind.PRISM?1200:0;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        switch(kind){
            case SAND -> {return InteractionResultHolder.pass(stack);}
            case PRISM -> {
                player.playSound(SoundEvents.SPYGLASS_USE,1,1.3f);
                return ItemUtils.startUsingInstantly(level,player,hand);
            }
            case ECHO -> {
                if(level.isClientSide)Veilborne.echoMirror.run();
                level.playSound(player,player.blockPosition(),Veilborne.MIRROR_REWIND.get(),SoundSource.PLAYERS,.7f,1.1f);
                player.getCooldowns().addCooldown(this,600);
                return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
            }
            case HEART -> {
                // Crushed in the hand: for half a minute the chests, spawners and buried things nearby show through the ground.
                if(player instanceof ServerPlayer p){
                    var spots=MirrorVault.hidden(p.serverLevel(),p.position(),MirrorVault.HEART_RANGE);
                    VeilService.cue(p,new VeilCue(VeilCue.REVEAL,p.getX(),p.getY(),p.getZ(),MirrorVault.HEART_TICKS,spots));
                    level.playSound(null,p.blockPosition(),Veilborne.MIRROR_SHATTER.get(),SoundSource.PLAYERS,.8f,1.3f);
                    p.displayClientMessage(Component.translatable(spots.isEmpty()?"message.veilborne.heart_none":"message.veilborne.heart_used",spots.size()),true);
                    if(!p.isCreative())stack.shrink(1);
                    p.getCooldowns().addCooldown(this,100);
                }
                return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
            }
            case SKY_MIRROR -> {
                if(player instanceof ServerPlayer p){
                    var sea=TideData.get(p.serverLevel());var moon=MoonData.get(p.serverLevel());var mirror=MirrorData.get(p.serverLevel());
                    if(!level.dimension().equals(Level.OVERWORLD)||HushItem.anyWeather(p.serverLevel())||!level.canSeeSky(p.blockPosition())){
                        p.displayClientMessage(Component.translatable("message.veilborne.sky_mirror_blocked"),true);
                        return InteractionResultHolder.fail(stack);
                    }
                    level.playSound(null,p.blockPosition(),Veilborne.MIRROR_OMEN.get(),SoundSource.PLAYERS,1,1);
                    MirrorService.begin(p.serverLevel(),p.blockPosition(),TideConfig.MIRROR_DURATION.get());
                    if(!p.isCreative())stack.shrink(1);
                }
                return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
            }
        }
        return InteractionResultHolder.pass(stack);
    }
    /** The prism is held up like a spyglass; what it is aimed at is judged on the server while it is held. */
    @Override public void onUseTick(Level level,LivingEntity entity,ItemStack stack,int remaining){
        if(kind==Kind.PRISM&&entity instanceof ServerPlayer p)MirrorService.observe(p,getUseDuration(stack,entity)-remaining);
    }
    @Override public void releaseUsing(ItemStack stack,Level level,LivingEntity entity,int remaining){
        if(kind==Kind.PRISM){entity.playSound(SoundEvents.SPYGLASS_STOP_USING,1,1.3f);if(entity instanceof ServerPlayer p)MirrorService.stopObserving(p);}
    }
    @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity entity){
        if(kind==Kind.PRISM&&entity instanceof ServerPlayer p)MirrorService.stopObserving(p);
        return stack;
    }
    @Override public boolean isFoil(ItemStack stack){return kind==Kind.HEART;}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        lines.add(Component.translatable("tooltip.veilborne.mirror_"+kind.name().toLowerCase()).withStyle(ChatFormatting.GRAY));
    }
}
