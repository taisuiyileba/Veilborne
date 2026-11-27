package com.suiyi.veilborne;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Keepsakes and instruments of 苍翠梦潮. Dream seeds are planted in soil and grow into dream trees (a full seed, won from the
 * vine enclosure, into a larger one). The golden leaf is what the lost school leaves when it goes home. The drifting leaf is
 * a charm: carried, it catches you when you fall and lets you down like a leaf, a limited number of times. The deer-call
 * flute calls a whole verdant dream.
 */
public class DreamItem extends Item {
    public enum Kind { SEED,FULL_SEED,LEAF,CHARM,FLUTE }
    private final Kind kind;
    public DreamItem(Kind kind){
        super(props(kind));
        this.kind=kind;
    }
    private static Properties props(Kind k){
        Properties p=new Properties().rarity(k==Kind.FULL_SEED?Rarity.RARE:k==Kind.SEED||k==Kind.LEAF?Rarity.COMMON:Rarity.UNCOMMON);
        return k==Kind.CHARM?p.durability(DreamService.CHARM_USES):p.stacksTo(k==Kind.FLUTE?1:64);
    }
    public Kind kind(){return kind;}
    @Override public boolean isFoil(ItemStack stack){return kind==Kind.FULL_SEED;}
    @Override public InteractionResult useOn(UseOnContext c){
        if(kind!=Kind.SEED&&kind!=Kind.FULL_SEED)return InteractionResult.PASS;
        Level level=c.getLevel();BlockPos soil=c.getClickedPos();
        if(c.getClickedFace()!=Direction.UP||!DreamBlocks.soil(level.getBlockState(soil)))return InteractionResult.PASS;
        BlockPos at=soil.above();
        if(!level.getBlockState(at).canBeReplaced()||!level.getFluidState(at).isEmpty())return InteractionResult.PASS;
        if(!level.isClientSide){
            level.setBlock(at,Veilborne.DREAM_SAPLING.get().defaultBlockState().setValue(DreamBlocks.FULL,kind==Kind.FULL_SEED),3);
            level.playSound(null,at,SoundEvents.GRASS_PLACE,SoundSource.BLOCKS,1,1.2f);
            if(c.getPlayer()==null||!c.getPlayer().isCreative())c.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        if(kind!=Kind.FLUTE)return InteractionResultHolder.pass(stack);
        if(player instanceof ServerPlayer p){
            var l=p.serverLevel();
            if(!level.dimension().equals(Level.OVERWORLD)||HushItem.anyWeather(l)||!level.canSeeSky(p.blockPosition())){
                p.displayClientMessage(Component.translatable("message.veilborne.flute_blocked"),true);
                return InteractionResultHolder.fail(stack);
            }
            level.playSound(null,p.blockPosition(),Veilborne.DREAM_CALL.get(),SoundSource.PLAYERS,1,1.1f);
            DreamService.begin(l,p.blockPosition(),TideConfig.DREAM_DURATION.get());
            if(!p.isCreative())stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        lines.add(Component.translatable("tooltip.veilborne.dream_"+kind.name().toLowerCase()).withStyle(ChatFormatting.GRAY));
    }
}
