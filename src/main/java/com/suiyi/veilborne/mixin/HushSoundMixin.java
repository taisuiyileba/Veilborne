package com.suiyi.veilborne.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.suiyi.veilborne.client.HushHooks;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Under the silent daylight the world's sounds fall away layer by layer. Every sound's volume passes through here: when it
 * starts, and each tick for the looping ones (and whenever the client asks all channels to be recalculated), so a sound
 * that is already playing fades out and comes back rather than cutting. Nothing is stopped; hushed sounds still play,
 * inaudibly, so their subtitles still show and their loops are still there when the sound returns.
 */
@Mixin(SoundEngine.class)
public abstract class HushSoundMixin {
    @ModifyReturnValue(method="calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",at=@At("RETURN"),require=0)
    private float veilborne$hushTicking(float volume,SoundInstance sound){
        return volume*HushHooks.gain(sound);
    }
    @ModifyExpressionValue(method="play",at=@At(value="INVOKE",target="Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"),require=0)
    private float veilborne$hushStart(float volume,@Local(argsOnly=true) SoundInstance sound){
        return volume*HushHooks.gain(sound);
    }
}
