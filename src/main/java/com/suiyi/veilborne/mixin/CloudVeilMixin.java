package com.suiyi.veilborne.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.suiyi.veilborne.TideConfig;
import com.suiyi.veilborne.MoonTimeline;
import com.suiyi.veilborne.TideTimeline;
import com.suiyi.veilborne.client.MoonClient;
import com.suiyi.veilborne.client.TideClient;
import com.suiyi.veilborne.client.MirrorHooks;
import com.suiyi.veilborne.client.ThreadHooks;
import com.suiyi.veilborne.client.HushHooks;
import com.suiyi.veilborne.client.DreamHooks;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The cloud deck thins and drifts upward as the sea opens (or the moon's night spreads), instead of hanging as black blocks over it. */
@Mixin(LevelRenderer.class)
public abstract class CloudVeilMixin {
    private static float veilborne$veil(){
        if(!TideConfig.SKY.get())return 0;
        float sea=TideClient.strength()<.001?0:(float)(TideTimeline.smooth(0,.55,TideTimeline.sea(TideClient.progress(1)))*TideClient.strength());
        float moon=!MoonClient.active()?0:(float)(TideTimeline.smooth(0,.8,MoonTimeline.moon(MoonClient.progress(1)))*MoonClient.strength());
        float mirror=(float)TideTimeline.smooth(0,.6,MirrorHooks.veil(1));
        float thread=(float)TideTimeline.smooth(0,.7,ThreadHooks.clear(1));
        float dream=DreamHooks.cloudVeil(1);
        return Math.max(Math.max(Math.max(Math.max(sea,moon),mirror),thread),dream);
    }
    @ModifyExpressionValue(method="renderClouds",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/DimensionSpecialEffects;getCloudHeight()F"),require=0)
    private float veilborne$liftCloudCurtain(float height){
        float veil=veilborne$veil();
        if(veil<.001f)return height;
        // Lifted, the deck would rise above the dream's canopy and show over it at the horizon: once the forest is mostly
        // in, the clouds are gone rather than lifted.
        if(DreamHooks.cloudVeil(1)>.6f)return Float.NaN;
        return veil>.97f?Float.NaN:height+veil*140;
    }
    @WrapOperation(method="renderClouds",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V"),require=0)
    private void veilborne$thinClouds(VertexBuffer buffer,Matrix4f modelView,Matrix4f projection,ShaderInstance shader,Operation<Void> original){
        float veil=veilborne$veil();
        // Under the silent daylight the clouds stay, held still; they only thin while the black sun is up.
        float thin=TideConfig.SKY.get()?HushHooks.cloudThin(1):0;
        // The clouds take a green underside before the forest comes out of them.
        float green=DreamHooks.cloudGreen(1);
        if(veil<.001f&&thin<.001f&&green<.001f){original.call(buffer,modelView,projection,shader);return;}
        float[] c=RenderSystem.getShaderColor();
        float r=c[0],g=c[1],b=c[2],a=c[3];
        RenderSystem.setShaderColor(r*(1-.18f*green),Math.min(1,g*(1+.08f*green)),b*(1-.3f*green),a*(1-veil)*(1-thin));
        original.call(buffer,modelView,projection,shader);
        RenderSystem.setShaderColor(r,g,b,a);
    }
}
