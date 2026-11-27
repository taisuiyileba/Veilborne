package com.suiyi.veilborne;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * Keepsakes and instruments of 无声白昼. Daylight wax is what the empty coat leaves on the threshold. The hollow bell is the
 * one that still rang: rung inside any veil weather it keeps a little of that weather's sound, and rung anywhere afterwards
 * it gives it back; empty, it rings a moment of silence. The stopped clock calls a whole silent daylight.
 */
public class HushItem extends Item {
    public enum Kind { WAX,BELL,CLOCK }
    /** What a hollow bell can hold, in the order HushCue.ECHO numbers them; the last is its own silence. */
    public static final String[] RECORDINGS={"sea","moon","mirror","thread","hush","dream"};
    public static final String RECORDED="Recorded";
    private final Kind kind;
    public HushItem(Kind kind){
        super(new Properties().stacksTo(kind==Kind.WAX?64:1).rarity(kind==Kind.BELL?Rarity.RARE:kind==Kind.WAX?Rarity.COMMON:Rarity.UNCOMMON));
        this.kind=kind;
    }
    public Kind kind(){return kind;}
    public static int recording(ItemStack stack){
        CustomData data=stack.get(DataComponents.CUSTOM_DATA);
        if(data==null)return -1;
        String id=data.copyTag().getString(RECORDED);
        for(int i=0;i<RECORDINGS.length;i++)if(RECORDINGS[i].equals(id))return i;
        return -1;
    }
    public static void setRecording(ItemStack stack,int which){
        CompoundTag tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        tag.putString(RECORDED,RECORDINGS[which]);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
    }
    /** Which veil weather is sounding where this player stands, or -1. */
    public static int sounding(ServerPlayer p){
        ServerLevel l=p.serverLevel();
        if(!l.dimension().equals(Level.OVERWORLD))return -1;
        if(TideData.get(l).strength(p.getX(),p.getZ())>.25)return 0;
        if(MoonData.get(l).strength(p.getX(),p.getZ())>.25)return 1;
        if(MirrorData.get(l).strength(p.getX(),p.getZ())>.25)return 2;
        if(ThreadData.get(l).strength(p.getX(),p.getZ())>.25)return 3;
        if(HushData.get(l).strength(p.getX(),p.getZ())>.25)return 4;
        if(DreamData.get(l).strength(p.getX(),p.getZ())>.25)return 5;
        return -1;
    }
    public static boolean anyWeather(ServerLevel l){
        return TideData.get(l).active||MoonData.get(l).active||MirrorData.get(l).active||ThreadData.get(l).active||HushData.get(l).active||DreamData.get(l).active;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        if(kind==Kind.WAX)return InteractionResultHolder.pass(stack);
        if(player instanceof ServerPlayer p){
            var l=p.serverLevel();
            if(kind==Kind.CLOCK){
                if(!level.dimension().equals(Level.OVERWORLD)||anyWeather(l)||!level.canSeeSky(p.blockPosition())){
                    p.displayClientMessage(Component.translatable("message.veilborne.clock_blocked"),true);
                    return InteractionResultHolder.fail(stack);
                }
                level.playSound(null,p.blockPosition(),Veilborne.HUSH_OMEN.get(),SoundSource.PLAYERS,1,1);
                HushService.begin(l,p.blockPosition(),TideConfig.HUSH_DURATION.get());
                if(!p.isCreative())stack.shrink(1);
            }else{
                int now=sounding(p);
                if(now>=0&&now!=recording(stack)){
                    setRecording(stack,now);
                    p.displayClientMessage(Component.translatable("message.veilborne.bell_recorded",Component.translatable("weather.veilborne."+RECORDINGS[now])),true);
                    level.playSound(null,p.blockPosition(),Veilborne.HUSH_BELL.get(),SoundSource.PLAYERS,.6f,1.3f);
                }else{
                    int which=recording(stack);
                    HushService.cue(l,new HushCue(HushCue.ECHO,p.position().add(0,1.2,0),which<0?4:which),24);
                    p.getCooldowns().addCooldown(this,which<0||which==4?120:200);
                }
            }
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
    @Override public boolean isFoil(ItemStack stack){return kind==Kind.BELL&&recording(stack)>=0;}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        lines.add(Component.translatable("tooltip.veilborne.hush_"+kind.name().toLowerCase()).withStyle(ChatFormatting.GRAY));
        if(kind==Kind.BELL){
            int r=recording(stack);
            lines.add((r<0?Component.translatable("tooltip.veilborne.hush_bell_empty"):Component.translatable("tooltip.veilborne.hush_bell_holds",Component.translatable("weather.veilborne."+RECORDINGS[r]))).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
