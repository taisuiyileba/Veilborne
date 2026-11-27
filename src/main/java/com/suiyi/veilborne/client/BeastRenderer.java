package com.suiyi.veilborne.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.suiyi.veilborne.*;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 镜兽: a glass gecko. Awake, it scuttles with a sprawling gait and flashes when struck. Asleep, a few of them
 * crawl over their low shard (drawn by {@link #crawlers}); during the shard's warning they freeze and turn to you.
 */
public final class BeastRenderer extends EntityRenderer<MirrorBeast> {
    static final ModelLayerLocation LAYER=new ModelLayerLocation(Veilborne.id("mirror_beast"),"main");
    private static final Map<String,String> PARENTS=new HashMap<>();
    static final ResourceLocation TEXTURE=Veilborne.id("textures/entity/mirror_beast.png"),GLOW=Veilborne.id("textures/entity/mirror_beast_glow.png");
    static GeoModel model;
    public BeastRenderer(EntityRendererProvider.Context c){super(c);shadowRadius=.25f;model=new GeoModel(c.bakeLayer(LAYER),new HashMap<>(PARENTS));}
    static LayerDefinition create(){return GeoModel.create("mirror_beast.json",PARENTS);}
    @Override public ResourceLocation getTextureLocation(MirrorBeast e){return TEXTURE;}
    @Override public void render(MirrorBeast e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        float body=Mth.rotLerp(partial,e.yBodyRotO,e.yBodyRot);
        float walk=e.walkAnimation.position(partial),speed=Math.min(1,e.walkAnimation.speed(partial));
        pose.pushPose();
        // The model's head is +z (tools/art/mirror_models.py), not vanilla's -z: turned 180 more, it ran tail first.
        pose.mulPose(Axis.YP.rotationDegrees(-body));
        if(e.deathTime>0){float d=Math.min(1,(e.deathTime+partial-1)/20f*1.6f);pose.mulPose(Axis.ZP.rotationDegrees(d*90));}
        pose.scale(-1,-1,1);
        pose.translate(0,-.19,0);
        float headYaw=Mth.wrapDegrees(Mth.rotLerp(partial,e.yHeadRotO,e.yHeadRot)-body);
        pose(walk,speed,(e.tickCount+partial)/20f,headYaw*Mth.DEG_TO_RAD,0);
        draw(pose,buffers,1,e.hurtTime>0);
        pose.popPose();
        super.render(e,yaw,partial,pose,buffers,light);
    }
    private static void pose(float walk,float speed,float time,float headYaw,float freeze){
        model.reset();
        float s=walk*1.4f;
        float stride=(1-freeze)*speed;
        model.rotate("leg_fl",0,Mth.sin(s)*.6f*stride,Mth.cos(s)*.3f*stride);
        model.rotate("leg_br",0,Mth.sin(s)*.6f*stride,-Mth.cos(s)*.3f*stride);
        model.rotate("leg_fr",0,-Mth.sin(s)*.6f*stride,-Mth.cos(s)*.3f*stride);
        model.rotate("leg_bl",0,-Mth.sin(s)*.6f*stride,Mth.cos(s)*.3f*stride);
        model.rotate("body",0,Mth.sin(s)*.12f*stride,0);
        model.rotate("tail",0,-Mth.sin(s)*.35f*stride+Mth.sin(time*1.7f)*.12f*(1-freeze),0);
        model.rotate("tail_tip",0,-Mth.sin(s-1)*.4f*stride+Mth.sin(time*1.7f-1)*.18f*(1-freeze),0);
        model.rotate("head",0,headYaw,0);
    }
    private static void draw(PoseStack pose,MultiBufferSource buffers,float alpha,boolean hurt){
        var part=model.part("body");
        if(part==null)return;
        int tint=hurt?FastColor.ARGB32.color((int)(alpha*255),255,150,150):FastColor.ARGB32.color((int)(alpha*255),255,255,255);
        part.render(pose,buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)),0xf000f0,OverlayTexture.NO_OVERLAY,tint);
        int g=(int)(alpha*255);
        part.render(pose,buffers.getBuffer(RenderType.eyes(GLOW)),0xf000f0,OverlayTexture.NO_OVERLAY,FastColor.ARGB32.color(255,g,g,g));
    }
    /** Sleeping geckos crawling over a shard centred at {@code center}, turned by {@code yaw}. */
    static void crawlers(Vec3 center,double yaw,Vec3 camera,double time,double warning,double alpha,float partial){
        if(model==null)return;
        var mc=Minecraft.getInstance();
        var buffers=mc.renderBuffers().bufferSource();
        Vec3 across=new Vec3(Math.cos(yaw),0,Math.sin(yaw)),normal=new Vec3(-Math.sin(yaw),0,Math.cos(yaw));
        double freeze=Math.min(1,warning*3);
        for(int k=0;k<3;k++){
            double t=time*.16+k*2.1,slow=1-freeze*.9;
            double u=Math.sin(t*slow+k)*.7,v=Math.sin(t*.7*slow+k*1.7)*1.2;
            double side=k==1?-1:1;
            Vec3 at=center.add(across.scale(u)).add(0,v,0).add(normal.scale(side*.07));
            // Head first along its path. Laid on the pane as below, heading 0 points the head straight down the glass and
            // the model's own x runs along across*side, so the heading is the path's direction measured that way.
            double du=Math.cos(t*slow+k)*.7,dv=Math.cos(t*.7*slow+k*1.7)*.84;
            double heading=Math.atan2(side*du,-dv);
            PoseStack pose=new PoseStack();
            pose.translate(at.x-camera.x,at.y-camera.y,at.z-camera.z);
            // Belly to the glass: the model's back faces out of the pane, its head wherever it is crawling.
            float face=(float)Math.toDegrees(Math.atan2(normal.x*side,normal.z*side));
            pose.mulPose(Axis.YP.rotationDegrees(face));
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.mulPose(Axis.YP.rotationDegrees((float)(Math.toDegrees(heading)*(1-freeze))));
            pose.scale(-.8f,-.8f,.8f);
            pose((float)(time*3+k),(float)(1-freeze),(float)time+k,0,(float)freeze);
            draw(pose,buffers,(float)alpha,false);
        }
        buffers.endBatch();
    }
}
