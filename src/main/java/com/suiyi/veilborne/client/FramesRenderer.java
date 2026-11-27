package com.suiyi.veilborne.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.suiyi.veilborne.*;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.phys.Vec3;

/**
 * 错位路标: three aged bronze frames standing in an arc, each facing the spot in the middle. They rise out of the
 * ground, the wrong ones shiver when chosen, and once the present one is found the other two come apart and sink.
 * The glass and what it reflects are drawn by MirrorGlass.
 */
public final class FramesRenderer extends EntityRenderer<MirrorFrames> {
    static final ModelLayerLocation LAYER=new ModelLayerLocation(Veilborne.id("mirror_frame"),"main");
    private static final Map<String,String> PARENTS=new HashMap<>();
    private static final ResourceLocation TEXTURE=Veilborne.id("textures/entity/mirror_frame.png"),GLOW=Veilborne.id("textures/entity/mirror_frame_glow.png");
    private final GeoModel model;
    public FramesRenderer(EntityRendererProvider.Context c){super(c);shadowRadius=0;model=new GeoModel(c.bakeLayer(LAYER),new HashMap<>(PARENTS));}
    static LayerDefinition create(){return GeoModel.create("mirror_frame.json",PARENTS);}
    @Override public ResourceLocation getTextureLocation(MirrorFrames e){return TEXTURE;}
    @Override public boolean shouldRender(MirrorFrames e,net.minecraft.client.renderer.culling.Frustum f,double x,double y,double z){return true;}
    @Override public void render(MirrorFrames e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        float vis=e.visibility(partial);
        double time=(e.tickCount+partial)/20.0;
        Vec3 origin=e.getPosition(partial);
        for(int i=0;i<3;i++){
            float broken=e.broken(i,partial);
            if(broken>=.999f)continue;
            Vec3 at=e.framePos(i),n=e.normal(i);
            float shiver=e.shiver(i,partial);
            float face=(float)Math.toDegrees(Math.atan2(n.x,n.z));
            pose.pushPose();
            pose.translate(at.x-origin.x,at.y-origin.y+(vis-1)*MirrorFrames.HEIGHT-broken*.6,at.z-origin.z);
            pose.mulPose(Axis.YP.rotationDegrees(face+(float)Math.sin(time*50)*2.5f*shiver));
            pose.mulPose(Axis.ZP.rotationDegrees(broken*9*(i==0?1:-1)));
            pose.scale(-1,-1,1);
            model.reset();
            var part=model.part("frame");
            float alpha=vis*(1-broken);
            int lit=LightTexture.pack(Math.max(LightTexture.block(light),5),LightTexture.sky(light));
            part.render(pose,buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)),lit,OverlayTexture.NO_OVERLAY,FastColor.ARGB32.color((int)(alpha*255),255,255,255));
            int g=(int)(alpha*alpha*255);
            part.render(pose,buffers.getBuffer(RenderType.eyes(GLOW)),0xf000f0,OverlayTexture.NO_OVERLAY,FastColor.ARGB32.color(255,g,g,g));
            pose.popPose();
        }
        super.render(e,yaw,partial,pose,buffers,light);
    }
}
