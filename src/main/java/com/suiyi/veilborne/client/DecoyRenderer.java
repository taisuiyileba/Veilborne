package com.suiyi.veilborne.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.suiyi.veilborne.MoonDecoy;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** 月纱幻影: the owner's likeness in moonlight, standing where they stood. */
public final class DecoyRenderer extends EntityRenderer<MoonDecoy> {
    public DecoyRenderer(EntityRendererProvider.Context c){super(c);shadowRadius=0;GhostFigure.init(c);}
    @Override public ResourceLocation getTextureLocation(MoonDecoy e){return com.suiyi.veilborne.Veilborne.id("textures/misc/white.png");}
    @Override public void render(MoonDecoy e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        var owner=e.owner().map(e.level()::getPlayerByUUID).orElse(null);
        if(!(owner instanceof AbstractClientPlayer who))return;
        float a=e.visibility(partial);
        double time=(e.tickCount+partial)/20.0;
        pose.pushPose();
        pose.translate(0,Math.sin(time*1.7)*.03,0);
        pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
        GhostFigure.render(who,pose,buffers,a*(.88f+.1f*(float)Math.sin(time*3.1)),(float)time,0,0xdcd6ff);
        pose.popPose();
        super.render(e,yaw,partial,pose,buffers,light);
    }
}
