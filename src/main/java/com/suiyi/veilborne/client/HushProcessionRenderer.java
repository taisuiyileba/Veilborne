package com.suiyi.veilborne.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.suiyi.veilborne.*;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 无声行列. Five figures from one model (hush_figures.json), each posed and dressed where the road puts it: the lantern in
 * front under a hood, the woman under an oiled-paper umbrella, the child with the empty birdcage, the old man bent over
 * his stick, the tall man in a top hat carrying a suitcase. They are translucent, like figures in a double exposure; their
 * mouths keep moving. Answering a raised light they stop, every face turns to it and the lantern goes up; while the black
 * sun covers the sun they stand looking up. Holding a silent lantern makes them clearer.
 */
public final class HushProcessionRenderer extends EntityRenderer<HushProcession> {
    static final ModelLayerLocation LAYER=new ModelLayerLocation(Veilborne.id("hush_figures"),"main");
    static final Map<String,String> PARENTS=new HashMap<>();
    static final ResourceLocation TEXTURE=Veilborne.id("textures/entity/hush_figures.png"),GLOW=Veilborne.id("textures/entity/hush_figures_glow.png");
    private final GeoModel model;
    public HushProcessionRenderer(EntityRendererProvider.Context c){super(c);shadowRadius=0;model=new GeoModel(c.bakeLayer(LAYER),new HashMap<>(PARENTS));}
    static LayerDefinition create(){return GeoModel.create("hush_figures.json",PARENTS);}
    @Override public ResourceLocation getTextureLocation(HushProcession e){return TEXTURE;}
    @Override public boolean shouldRender(HushProcession e,net.minecraft.client.renderer.culling.Frustum f,double x,double y,double z){return true;}

    @Override public void render(HushProcession e,float entityYaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        float alpha=e.visibility(partial);
        if(alpha<.01f)return;
        var mc=Minecraft.getInstance();
        boolean lantern=mc.player!=null&&HushService.silentLantern(mc.player);
        double s=e.renderPath(partial),time=(e.tickCount+partial)/20.0;
        Vec3 origin=e.getPosition(partial);
        var state=e.state();
        boolean walking=state==HushProcession.State.WALK;
        double answer=state==HushProcession.State.ANSWER?Math.min(1,(e.stateClock()+partial)/14.0)*(1-TideTimeline.smooth(HushProcession.ANSWER_TICKS-20,HushProcession.ANSWER_TICKS,e.stateClock()+partial)):0;
        double look=state==HushProcession.State.LOOK?Math.min(1,(e.stateClock()+partial)/30.0):0;
        Vec3 toward=Vec3.atBottomCenterOf(e.toward());
        for(int i=0;i<HushProcession.MEMBERS.length;i++){
            double[] m=HushProcession.MEMBERS[i];
            Vec3 at=e.member(s,m[0],m[1]);
            float yaw=e.yawAt(s,m[0]);
            pose.pushPose();
            pose.translate(at.x-origin.x,at.y-origin.y+.02,at.z-origin.z);
            pose.mulPose(Axis.YP.rotationDegrees(-yaw));
            if(i==2)pose.scale(.64f,.64f,.64f);
            pose.scale(-1,-1,1);
            // Heads turn toward whoever raised the light: the difference between where they face and where it is.
            double face=0;
            if(answer>0){
                Vec3 d=toward.subtract(at);
                double want=Math.toDegrees(Math.atan2(-d.x,d.z));
                face=Mth.clamp(Mth.wrapDegrees(want-yaw),-75,75)*Math.PI/180*answer;
            }
            posture(i,time,walking?s:0,answer,look,face);
            draw(pose,buffers,"traveler",alpha*(lantern?.95f:.64f),light,SHADE[i]);
            pose.popPose();
        }
        super.render(e,entityYaw,partial,pose,buffers,light);
    }
    /**
     * In this model +z is the front (tools/art/hush_models.py): a positive x turn swings an arm forward, tips a head
     * back to look up and a body back. The poses had it the other way round, every prop carried behind.
     */
    private void posture(int member,double time,double walked,double answer,double look,double face){
        model.reset();
        for(String hat:new String[]{"hood","bonnet","brim_hat","top_hat","cap"})model.show(hat,false);
        for(String prop:new String[]{"lantern","umbrella","cage","stick","suitcase","gown"})model.show(prop,false);
        // They walk without a sound: a slow, even step, arms barely moving.
        double stride=walked*(member==2?1.9:1.25)+member*1.7;
        double swing=Math.sin(stride)*(walked>0?.42:0);
        model.rotate("leg_l",swing,0,0);model.rotate("leg_r",-swing,0,0);
        model.rotate("skirt",0,0,Math.sin(stride)*.025);
        model.rotate("arm_l",-swing*.3,0,-.05);model.rotate("arm_r",swing*.3,0,.05);
        // Their mouths keep moving, each at its own pace, saying something nobody hears.
        var mouth=model.part("mouth");
        if(mouth!=null){
            double talk=Math.max(0,Math.sin(time*(5.1+member*.7)+member)*Math.sin(time*(1.3+member*.21)+member*2));
            mouth.yScale=(float)(.25+1.6*talk*(1-look*.7));
        }
        model.rotate("head",look*.95,face,0);
        switch(member){
            case 0 -> {
                model.show("hood",true);model.show("lantern",true);
                model.rotate("arm_r",.95+1.55*answer,0,.12);
                model.rotate("lantern",-.95-1.55*answer,0,-.12);
                model.rotate("lamp",Math.sin(time*1.3)*.06,0,Math.sin(time*.9)*.05);
            }
            case 1 -> {
                model.show("bonnet",true);model.show("umbrella",true);model.show("gown",true);model.show("skirt",false);
                model.rotate("arm_l",.55,.2,-.12);
                model.rotate("umbrella",-.55+.12,-.2,.12);
            }
            case 2 -> {
                model.show("cap",true);model.show("cage",true);
                model.rotate("arm_r",.3,0,.1);
                model.rotate("cage",-.3,0,-.1);
            }
            case 3 -> {
                model.show("brim_hat",true);model.show("stick",true);
                model.rotate("body",-.22,0,0);model.rotate("head",.18,0,0);
                model.rotate("arm_r",.5+Math.sin(walked*1.25+5.1)*.12,0,.08);
                model.rotate("stick",-.5,0,-.08);
            }
            default -> {
                model.show("top_hat",true);model.show("suitcase",true);
                model.rotate("arm_l",.05,0,-.12);
            }
        }
    }
    /** A slightly different shade of the same grey for each, so the file reads as five people. */
    private static final int[] SHADE={255,244,236,214,226};
    private void draw(PoseStack pose,MultiBufferSource buffers,String part,float alpha,int light,int shade){
        var p=model.part(part);if(p==null)return;
        int lit=LightTexture.pack(Math.max(LightTexture.block(light),8),LightTexture.sky(light));
        p.render(pose,buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)),lit,OverlayTexture.NO_OVERLAY,FastColor.ARGB32.color((int)(alpha*255),shade,shade,shade));
        int g=(int)(alpha*alpha*200);
        p.render(pose,buffers.getBuffer(RenderType.eyes(GLOW)),0xf000f0,OverlayTexture.NO_OVERLAY,FastColor.ARGB32.color(255,g,g,g));
    }
}
