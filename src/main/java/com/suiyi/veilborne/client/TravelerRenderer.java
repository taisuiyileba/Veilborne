package com.suiyi.veilborne.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.suiyi.veilborne.MirrorTraveler;
import com.suiyi.veilborne.Veilborne;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 迟到的旅人: the likeness of whoever walked here, in pale mirror light, striding along the path they took a while
 * ago. If that player is not in view, the local player's body stands in (without their gear): it is still you,
 * in a way. At the end it turns and points.
 */
public final class TravelerRenderer extends EntityRenderer<MirrorTraveler> {
    public TravelerRenderer(EntityRendererProvider.Context c){super(c);shadowRadius=0;GhostFigure.init(c);}
    @Override public ResourceLocation getTextureLocation(MirrorTraveler e){return Veilborne.id("textures/misc/white.png");}
    @Override public void render(MirrorTraveler e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        var level=Minecraft.getInstance().level;
        var owner=e.owner().map(level::getPlayerByUUID).orElse(null);
        boolean own=owner instanceof AbstractClientPlayer;
        AbstractClientPlayer who=own?(AbstractClientPlayer)owner:Minecraft.getInstance().player;
        if(who==null)return;
        float a=e.visibility(partial);
        if(a<.01f)return;
        float body=e.getViewYRot(partial);
        double time=(e.clientTicks()+partial)/20.0;
        var f=new MoonClient.Frame(e.getX(),e.getY(),e.getZ(),body,body,4,e.stride(partial),e.speed(),false);
        float flicker=.88f+.12f*(float)Math.sin(time*5.3)*(float)Math.sin(time*2.1);
        GhostFigure.renderFrame(who,pose,buffers,a*.62f*flicker,f,0xd2e2ec,0,e.pointing(partial),own);
        super.render(e,yaw,partial,pose,buffers,light);
    }
}
