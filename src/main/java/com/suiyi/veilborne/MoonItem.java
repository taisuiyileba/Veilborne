package com.suiyi.veilborne;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** Keepsakes and instruments of the moon-husk bloom. */
public class MoonItem extends Item {
    public enum Kind { DEW,SHARD,GAUZE,DIAL,BELL }
    private final Kind kind;
    public MoonItem(Kind kind){
        super(new Properties().stacksTo(kind==Kind.SHARD?64:kind==Kind.DEW?16:1).rarity(kind==Kind.GAUZE?Rarity.RARE:kind==Kind.SHARD?Rarity.COMMON:Rarity.UNCOMMON));
        this.kind=kind;
    }
    public Kind kind(){return kind;}
    @Override public UseAnim getUseAnimation(ItemStack stack){return kind==Kind.DEW?UseAnim.DRINK:UseAnim.NONE;}
    @Override public int getUseDuration(ItemStack stack,LivingEntity entity){return kind==Kind.DEW?32:0;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        switch(kind){
            case DEW -> {return ItemUtils.startUsingInstantly(level,player,hand);}
            case SHARD -> {return InteractionResultHolder.pass(stack);}
            default -> {}
        }
        if(player instanceof ServerPlayer p){
            switch(kind){
                case GAUZE -> {
                    MoonDecoy decoy=Veilborne.DECOY.get().create(level);
                    if(decoy!=null){
                        decoy.setOwner(p.getUUID());
                        decoy.moveTo(p.getX(),p.getY(),p.getZ(),p.getYRot(),0);
                        level.addFreshEntity(decoy);
                    }
                    p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,100,0,false,false,true));
                    level.playSound(null,p.blockPosition(),Veilborne.MOON_SCATTER.get(),SoundSource.PLAYERS,.6f,1.25f);
                    p.getCooldowns().addCooldown(this,600);
                }
                case DIAL -> {MoonService.calibrate(p);p.getCooldowns().addCooldown(this,20);}
                case BELL -> {
                    var sea=TideData.get(p.serverLevel());var moon=MoonData.get(p.serverLevel());var mirror=MirrorData.get(p.serverLevel());
                    if(!level.dimension().equals(Level.OVERWORLD)||HushItem.anyWeather(p.serverLevel())||!level.canSeeSky(p.blockPosition())){
                        p.displayClientMessage(Component.translatable("message.veilborne.bell_blocked"),true);
                        return InteractionResultHolder.fail(stack);
                    }
                    level.playSound(null,p.blockPosition(),Veilborne.MOON_BELL.get(),SoundSource.PLAYERS,1,1);
                    MoonService.begin(p.serverLevel(),p.blockPosition(),TideConfig.MOON_DURATION.get());
                    if(!p.isCreative())stack.shrink(1);
                }
                default -> {}
            }
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
    @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity entity){
        if(kind!=Kind.DEW)return stack;
        if(!level.isClientSide){
            entity.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,1200,0));
            entity.removeEffect(MobEffects.DARKNESS);entity.removeEffect(MobEffects.BLINDNESS);
            level.playSound(null,entity.blockPosition(),Veilborne.CHIME.get(),SoundSource.PLAYERS,.4f,1.4f);
        }
        if(entity instanceof Player p&&p.getAbilities().instabuild)return stack;
        stack.shrink(1);
        if(stack.isEmpty())return new ItemStack(Items.GLASS_BOTTLE);
        if(entity instanceof Player p&&!p.getInventory().add(new ItemStack(Items.GLASS_BOTTLE)))p.drop(new ItemStack(Items.GLASS_BOTTLE),false);
        return stack;
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        lines.add(Component.translatable("tooltip.veilborne.moon_"+kind.name().toLowerCase()).withStyle(ChatFormatting.GRAY));
    }
}
