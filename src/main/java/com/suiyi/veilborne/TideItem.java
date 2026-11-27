package com.suiyi.veilborne;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.ChatFormatting;

public class TideItem extends Item {
    public enum Kind { CONCH,MANTLE,JOURNAL,MESSAGE,ATTUNER,SHELL }
    private final Kind kind;
    public TideItem(Kind kind){super(new Properties().stacksTo(kind==Kind.MESSAGE?16:1).rarity(kind==Kind.MANTLE||kind==Kind.SHELL?Rarity.RARE:Rarity.UNCOMMON));this.kind=kind;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        if(kind==Kind.JOURNAL){if(level.isClientSide)Veilborne.openJournal.run();return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);}
        if(player instanceof ServerPlayer p){
            switch(kind){
                case CONCH -> {
                    level.playSound(null,p.blockPosition(),Veilborne.WHALE.get(),SoundSource.PLAYERS,.65f,1.2f);
                    p.getCooldowns().addCooldown(this,160);
                    var d=TideData.get(p.serverLevel());p.displayClientMessage(Component.translatable(d.active?"message.veilborne.conch_active":"message.veilborne.conch_quiet"),true);
                }
                case MANTLE -> {p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,600,0));p.getCooldowns().addCooldown(this,1200);}
                case MESSAGE -> {
                    ItemStack chart=TideService.charted(stack)?TideService.chart(p.serverLevel(),p.blockPosition()):ItemStack.EMPTY;
                    p.displayClientMessage(Component.translatable(chart.isEmpty()?"message.veilborne.bottle":"message.veilborne.bottle_chart"),false);
                    if(!p.isCreative())stack.shrink(1);
                    TideService.give(p,chart.isEmpty()?new ItemStack(Veilborne.TIDE_BOTTLE.get()):chart);TideService.record(p,8);
                    var d=TideData.get(p.serverLevel());
                    if(d.active&&d.strength(p.getX(),p.getZ())>.25)VeilGrade.deed(p,VeilGrade.SEA,d.seed,VeilGrade.SEA_BOTTLE);
                }
                case SHELL -> {
                    CustomData data=stack.get(DataComponents.CUSTOM_DATA);
                    CompoundTag tag=data==null?new CompoundTag():data.copyTag();
                    if(!tag.contains(TideService.HOME)){p.displayClientMessage(Component.translatable("message.veilborne.shell_empty"),true);return InteractionResultHolder.fail(stack);}
                    if(!level.dimension().location().toString().equals(tag.getString(TideService.HOME_DIM))){p.displayClientMessage(Component.translatable("message.veilborne.shell_far"),true);return InteractionResultHolder.fail(stack);}
                    BlockPos home=BlockPos.of(tag.getLong(TideService.HOME));
                    p.serverLevel().getChunk(home.getX()>>4,home.getZ()>>4);
                    BlockPos safe=TideService.findSafe(p.serverLevel(),home,6);
                    if(safe==null)safe=home;
                    level.playSound(null,p.blockPosition(),Veilborne.EBB.get(),SoundSource.PLAYERS,.8f,1.2f);
                    p.teleportTo(p.serverLevel(),safe.getX()+.5,safe.getY(),safe.getZ()+.5,p.getYRot(),p.getXRot());
                    p.fallDistance=0;
                    level.playSound(null,safe,Veilborne.CHIME.get(),SoundSource.PLAYERS,.7f,.9f);
                    p.displayClientMessage(Component.translatable("message.veilborne.shell_home"),true);
                    if(!p.isCreative())stack.shrink(1);
                }
                case ATTUNER -> {
                    var d=TideData.get(p.serverLevel());
                    if(!level.dimension().equals(Level.OVERWORLD)||HushItem.anyWeather(p.serverLevel())||!level.canSeeSky(p.blockPosition())){
                        p.displayClientMessage(Component.translatable("message.veilborne.attuner_blocked"),true);
                        return InteractionResultHolder.fail(stack);
                    }
                    TideService.begin(p.serverLevel(),p.blockPosition(),TideConfig.DURATION.get());
                    if(!p.isCreative())stack.shrink(1);
                }
                default -> {}
            }
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        lines.add(Component.translatable("tooltip.veilborne."+kind.name().toLowerCase()).withStyle(ChatFormatting.GRAY));
        if(kind==Kind.MESSAGE&&TideService.charted(stack))lines.add(Component.translatable("tooltip.veilborne.message_chart").withStyle(ChatFormatting.DARK_AQUA));
        if(kind==Kind.SHELL){
            CustomData data=stack.get(DataComponents.CUSTOM_DATA);
            if(data!=null&&data.copyTag().contains(TideService.HOME)){
                BlockPos home=BlockPos.of(data.copyTag().getLong(TideService.HOME));
                lines.add(Component.translatable("tooltip.veilborne.shell_home",home.getX(),home.getY(),home.getZ()).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }
    @Override public boolean isFoil(ItemStack stack){return kind==Kind.MESSAGE&&TideService.charted(stack);}
}
