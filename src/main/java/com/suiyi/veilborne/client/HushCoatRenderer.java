package com.suiyi.veilborne.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.suiyi.veilborne.*;
import java.util.HashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * 敲门的空位: the long hooded coat with nobody in it, from the same model as the procession (the "coat" part). It hangs a
 * hand's breadth off the ground and does not move, except for its right sleeve lifting to knock three times. Answered, it
 * bows; then it is rubbed out from the hood down, graphite crumbs falling where the eraser passes (HushGround).
 */
public final class HushCoatRenderer extends EntityRenderer<HushCoat> {
    private final GeoModel model;
    public HushCoatRenderer(EntityRendererProvider.Context c){super(c);shadowRadius=0;model=new GeoModel(c.bakeLayer(HushProcessionRenderer.LAYER),new HashMap<>(HushProcessionRenderer.PARENTS));}
    @Override public ResourceLocation getTextureLocation(HushCoat e){return HushProcessionRenderer.TEXTURE;}
    @Override public void render(HushCoat e,float entityYaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        float vis=e.visibility(partial),erased=e.erased(partial);
        // Rubbed out from the hood down: the hood goes first, then the shoulders and sleeves, then the skirt.
        float hood=vis*(float)(1-TideTimeline.smooth(0,.4,erased)),upper=vis*(float)(1-TideTimeline.smooth(.25,.72,erased)),lower=vis*(float)(1-TideTimeline.smooth(.6,1,erased));
        if(lower<.01f&&upper<.01f&&hood<.01f)return;
        var mc=Minecraft.getInstance();
        boolean lantern=mc.player!=null&&HushService.silentLantern(mc.player);
        float t=e.stateClock()+partial;
        pose.pushPose();
        pose.translate(0,.09,0);
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial,e.yRotO,e.getYRot())));
        pose.scale(-1,-1,1);
        model.reset();
        var state=e.state();
        if(state==HushCoat.State.KNOCK){
            // Three knocks: the empty sleeve comes up and strikes, strikes, strikes.
            double lift=TideTimeline.smooth(0,4,t)*(1-TideTimeline.smooth(20,28,t));
            double tap=0;for(int k=0;k<3;k++){double u=t-2-k*6;if(u>=0&&u<6)tap=Math.max(tap,Math.sin(Math.PI*u/6));}
            // Toward the door it faces (+z is the coat's front, where the hood opens).
            model.rotate("sleeve_r",1.35*lift+.28*tap,0,.1*lift);
        }
        if(state==HushCoat.State.BOW||state==HushCoat.State.ERASE){
            double bow=state==HushCoat.State.BOW?TideTimeline.smooth(0,16,t)*(1-TideTimeline.smooth(30,44,t)*.4):.6;
            model.rotate("coat_upper",-.42*bow,0,0);
            model.rotate("coat_hood",-.25*bow,0,0);
        }
        ModelPart coat=model.part("coat"),top=model.part("coat_upper"),head=model.part("coat_hood");
        if(coat!=null&&top!=null&&head!=null){
            int lit=LightTexture.pack(Math.max(LightTexture.block(light),8),LightTexture.sky(light));
            float k=lantern?.96f:.8f;
            top.visible=false;piece(pose,buffers,coat,new ModelPart[]{model.root},lower,k,lit);top.visible=true;
            head.visible=false;piece(pose,buffers,top,new ModelPart[]{model.root,coat},upper,k,lit);head.visible=true;
            piece(pose,buffers,head,new ModelPart[]{model.root,coat,top},hood,k,lit);
        }
        pose.popPose();
        super.render(e,entityYaw,partial,pose,buffers,light);
    }
    /** One piece of the coat on its own, under its parents' poses, at its own alpha (with its faint glow). */
    private static void piece(PoseStack pose,MultiBufferSource buffers,ModelPart part,ModelPart[] parents,float alpha,float k,int lit){
        if(alpha<.01f)return;
        pose.pushPose();
        for(ModelPart p:parents)p.translateAndRotate(pose);
        part.render(pose,buffers.getBuffer(RenderType.entityTranslucent(HushProcessionRenderer.TEXTURE)),lit,OverlayTexture.NO_OVERLAY,FastColor.ARGB32.color((int)(alpha*k*255),255,255,255));
        int g=(int)(alpha*alpha*190);
        part.render(pose,buffers.getBuffer(RenderType.eyes(HushProcessionRenderer.GLOW)),0xf000f0,OverlayTexture.NO_OVERLAY,FastColor.ARGB32.color(255,g,g,g));
        pose.popPose();
    }
}
