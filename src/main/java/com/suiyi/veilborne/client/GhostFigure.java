package com.suiyi.veilborne.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.suiyi.veilborne.Veilborne;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * A moonlit likeness of a player built only from game models: the player model, the armour models for
 * whatever they wear, and the item in their hand. Every surface becomes the same pale, translucent light,
 * denser toward the silhouette's edges, so the figure reads as an outline of the person and their equipment
 * rather than a copy of their skin.
 */
final class GhostFigure {
    private static final ResourceLocation WHITE=Veilborne.id("textures/misc/white.png");
    private static PlayerModel<AbstractClientPlayer> wide,slim;
    private static HumanoidModel<AbstractClientPlayer> inner,outer;

    static void init(EntityRendererProvider.Context c){
        if(wide!=null)return;
        wide=new PlayerModel<>(c.bakeLayer(ModelLayers.PLAYER),false);
        slim=new PlayerModel<>(c.bakeLayer(ModelLayers.PLAYER_SLIM),true);
        inner=new HumanoidModel<>(c.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        outer=new HumanoidModel<>(c.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
    }
    static PlayerModel<AbstractClientPlayer> model(AbstractClientPlayer who){
        if(wide==null)return null;
        return who.getSkin().model()==PlayerSkin.Model.SLIM?slim:wide;
    }

    /**
     * Draws the likeness at the pose origin (feet), facing +z of the pose. {@code wave} 0..1 raises the right
     * arm and waves it; {@code alpha} scales the light.
     */
    static void render(AbstractClientPlayer who,PoseStack pose,MultiBufferSource buffers,float alpha,float time,float wave,int rgb){
        var model=model(who);
        if(model==null||alpha<.01f)return;
        int color=FastColor.ARGB32.color((int)(255*Math.min(1,alpha)),rgb>>16&255,rgb>>8&255,rgb&255);
        // Alpha-blended rather than additive, so the model's inner and outer layers never stack up to white.
        MultiBufferSource ghost=type->new Glow(buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE)),color);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(-1,-1,1);pose.scale(.9375f,.9375f,.9375f);pose.translate(0,-1.501,0);
        // Only the base boxes: the skin's overlay layers would double every surface.
        model.setAllVisible(true);model.hat.visible=model.jacket.visible=model.leftSleeve.visible=model.rightSleeve.visible=model.leftPants.visible=model.rightPants.visible=false;
        model.crouching=false;model.riding=false;model.young=false;model.attackTime=0;
        model.leftArmPose=model.rightArmPose=HumanoidModel.ArmPose.EMPTY;
        model.setupAnim(who,0,0,time*20,0,0);
        // Breathing sway, and a slow wave with the right hand.
        model.body.xRot+=(float)(Math.sin(time*1.3)*.02);
        if(wave>0){
            model.rightArm.xRot=(float)(-2.7*wave);
            model.rightArm.zRot=(float)((.25+.35*Math.sin(time*6.5))*wave);
            model.rightSleeve.copyFrom(model.rightArm);
        }
        VertexConsumer glow=ghost.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        model.renderToBuffer(pose,glow,0xf000f0,OverlayTexture.NO_OVERLAY,color);
        armour(who,model,pose,glow,color);
        ItemStack held=who.getMainHandItem();
        if(!held.isEmpty()){
            pose.pushPose();
            model.translateToHand(HumanoidArm.RIGHT,pose);
            pose.mulPose(Axis.XP.rotationDegrees(-90));pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.translate(1/16f,.125f,-.625f);
            Minecraft.getInstance().getItemRenderer().renderStatic(held,ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,0xf000f0,OverlayTexture.NO_OVERLAY,pose,ghost,who.level(),0);
            pose.popPose();
        }
        pose.popPose();
    }
    /**
     * The likeness in a recorded pose (afterimages, the late traveller, reflections): feet at the pose origin, turned
     * by the frame's body yaw. {@code wave} raises the right arm; {@code point} lifts it straight ahead.
     */
    static void renderFrame(AbstractClientPlayer who,PoseStack pose,MultiBufferSource buffers,float alpha,MoonClient.Frame f,int rgb){
        renderFrame(who,pose,buffers,alpha,f,rgb,0,0,true);
    }
    static void renderFrame(AbstractClientPlayer who,PoseStack pose,MultiBufferSource buffers,float alpha,MoonClient.Frame f,int rgb,float wave,float point,boolean gear){
        if(model(who)==null||alpha<.01f)return;
        int color=FastColor.ARGB32.color((int)(255*Math.min(1,alpha)),rgb>>16&255,rgb>>8&255,rgb&255);
        VertexConsumer glow=new Glow(buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE)),color);
        posed(who,pose,glow,color,f,wave,point,gear,who.tickCount);
    }
    /** Poses the model from a frame and sends every surface (and optionally armour) to {@code out}. */
    static void posed(AbstractClientPlayer who,PoseStack pose,VertexConsumer out,int color,MoonClient.Frame f,float wave,float point,boolean gear,float age){
        var model=model(who);
        if(model==null)return;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180-f.body()));
        pose.scale(-1,-1,1);pose.scale(.9375f,.9375f,.9375f);pose.translate(0,-1.501,0);
        model.setAllVisible(true);model.hat.visible=model.jacket.visible=model.leftSleeve.visible=model.rightSleeve.visible=model.leftPants.visible=model.rightPants.visible=false;
        model.crouching=f.crouch();model.riding=false;model.young=false;model.attackTime=0;
        model.leftArmPose=model.rightArmPose=HumanoidModel.ArmPose.EMPTY;
        model.setupAnim(who,f.limb(),f.speed(),age,net.minecraft.util.Mth.wrapDegrees(f.head()-f.body()),f.pitch());
        if(wave>0){model.rightArm.xRot=(float)(-2.7*wave);model.rightArm.zRot=(float)((.25+.35*Math.sin(age*.33))*wave);}
        if(point>0){model.rightArm.xRot=model.rightArm.xRot*(1-point)-1.45f*point;model.rightArm.yRot=model.rightArm.yRot*(1-point)-.1f*point;model.rightArm.zRot*=1-point;}
        model.renderToBuffer(pose,out,0xf000f0,OverlayTexture.NO_OVERLAY,color);
        if(gear)armour(who,model,pose,out,color);
        pose.popPose();
    }
    private static void armour(AbstractClientPlayer who,PlayerModel<AbstractClientPlayer> body,PoseStack pose,VertexConsumer glow,int color){
        for(EquipmentSlot slot:new EquipmentSlot[]{EquipmentSlot.FEET,EquipmentSlot.LEGS,EquipmentSlot.CHEST,EquipmentSlot.HEAD}){
            if(who.getItemBySlot(slot).isEmpty())continue;
            HumanoidModel<AbstractClientPlayer> m=slot==EquipmentSlot.LEGS?inner:outer;
            body.copyPropertiesTo(m);
            m.setAllVisible(false);
            switch(slot){
                case HEAD -> {m.head.visible=true;m.hat.visible=true;}
                case CHEST -> {m.body.visible=true;m.rightArm.visible=true;m.leftArm.visible=true;}
                case LEGS -> {m.body.visible=true;m.rightLeg.visible=true;m.leftLeg.visible=true;}
                case FEET -> {m.rightLeg.visible=true;m.leftLeg.visible=true;}
                default -> {}
            }
            m.renderToBuffer(pose,glow,0xf000f0,OverlayTexture.NO_OVERLAY,color);
        }
    }

    /**
     * Forwards geometry only, with every surface the same light: brighter where it turns away from the eye,
     * so the figure reads as an outline of moonlight rather than a flat shape.
     */
    private record Glow(VertexConsumer base,int color) implements VertexConsumer {
        @Override public void addVertex(float x,float y,float z,int argb,float u,float v,int overlay,int light,float nx,float ny,float nz){
            // Positions are relative to the camera, so the dot with the normal tells front from back. Back faces
            // are dropped: with every face translucent, the far side would double the light through the body.
            float d=(float)Math.sqrt(x*x+y*y+z*z);
            float cos=d<1e-4f?-1:(nx*x+ny*y+nz*z)/d;
            float rim=(1+Math.min(cos,0))*(1+Math.min(cos,0));
            int a=cos>.04f?0:(int)((color>>>24)*(.22f+.64f*rim));
            base.addVertex(x,y,z).setColor(color>>16&255,color>>8&255,color&255,a).setUv(.5f,.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xf000f0).setNormal(0,1,0);
        }
        @Override public VertexConsumer addVertex(float x,float y,float z){
            base.addVertex(x,y,z).setColor(color).setUv(.5f,.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xf000f0).setNormal(0,1,0);
            return this;
        }
        @Override public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        @Override public VertexConsumer setUv(float u,float v){return this;}
        @Override public VertexConsumer setUv1(int u,int v){return this;}
        @Override public VertexConsumer setUv2(int u,int v){return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
    private GhostFigure(){}
}
