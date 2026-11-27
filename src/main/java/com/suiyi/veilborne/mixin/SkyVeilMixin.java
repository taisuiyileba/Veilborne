package com.suiyi.veilborne.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.suiyi.veilborne.MoonTimeline;
import com.suiyi.veilborne.TideConfig;
import com.suiyi.veilborne.client.MoonClient;
import com.suiyi.veilborne.client.MirrorHooks;
import com.suiyi.veilborne.client.ThreadHooks;
import com.suiyi.veilborne.client.HushHooks;
import com.suiyi.veilborne.client.DreamHooks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * While the moon-husk bloom hangs its own moon, the ordinary sun, moon and stars step aside. Vanilla fades
 * all three by the rain level inside renderSky, so only that one reading is raised; rain itself is untouched.
 */
@Mixin(LevelRenderer.class)
public abstract class SkyVeilMixin {
    @WrapOperation(method="renderSky",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"),require=0)
    private float veilborne$hideCelestials(ClientLevel level,float partial,Operation<Float> original){
        float rain=original.call(level,partial);
        if(!TideConfig.SKY.get())return rain;
        float hide=MoonClient.active()?(float)(MoonTimeline.moon(MoonClient.progress(partial))*MoonClient.strength()):0;
        // Under the mirror the ordinary sky is covered until the facets fall out and let it back.
        hide=Math.max(hide,MirrorHooks.veil(partial));
        // Behind the cloth the real sun and moon only show dimly, and the sky shader draws that.
        hide=Math.max(hide,Math.min(1,ThreadHooks.clear(partial)*1.6f));
        // The silent daylight draws its own pale sun (or moon) without a halo; the real ones give way to it.
        hide=Math.max(hide,Math.min(1,HushHooks.veil(partial)*1.8f));
        // The dream forest covers the sun and moon with its canopy; the sky shader draws them again in the gaps.
        hide=Math.max(hide,Math.min(1,DreamHooks.veil(partial)*1.4f));
        return Math.max(rain,Math.min(1,hide*1.2f));
    }
}
