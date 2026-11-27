package com.suiyi.veilborne.mixin;

import com.suiyi.veilborne.client.MirrorHooks;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A block changed on the client: the mirror's copy of that chunk is rebuilt later, so the reflection runs late. */
@Mixin(LevelRenderer.class)
public abstract class MirrorBlockMixin {
    @Inject(method="blockChanged",at=@At("HEAD"),require=0)
    private void veilborne$lateReflection(BlockGetter level,BlockPos pos,BlockState old,BlockState now,int flags,CallbackInfo ci){
        if(old!=now)MirrorHooks.changed(pos);
    }
}
