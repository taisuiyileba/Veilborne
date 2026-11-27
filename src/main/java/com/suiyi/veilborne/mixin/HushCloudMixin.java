package com.suiyi.veilborne.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.suiyi.veilborne.client.HushHooks;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The clouds stop. Vanilla moves the cloud deck by (ticks + partial tick); under the silent daylight both are replaced by a
 * clock that slows to a standstill and later runs on from where it stopped, so the clouds hang where they were and never
 * jump. The clock only differs from vanilla's once a silent daylight has held them.
 */
@Mixin(LevelRenderer.class)
public abstract class HushCloudMixin {
    @Shadow private int ticks;
    @Unique private double veilborne$cloudTime;
    @ModifyVariable(method="renderClouds",at=@At("HEAD"),argsOnly=true,ordinal=0,require=0)
    private float veilborne$holdPartial(float partial){
        if(!HushHooks.cloudsHeld()){veilborne$cloudTime=Double.NaN;return partial;}
        veilborne$cloudTime=HushHooks.cloudTime(ticks,partial);
        return (float)(veilborne$cloudTime-Math.floor(veilborne$cloudTime));
    }
    @ModifyExpressionValue(method="renderClouds",at=@At(value="FIELD",target="Lnet/minecraft/client/renderer/LevelRenderer;ticks:I"),require=0)
    private int veilborne$holdTicks(int value){
        return Double.isNaN(veilborne$cloudTime)?value:(int)Math.floor(veilborne$cloudTime);
    }
}
