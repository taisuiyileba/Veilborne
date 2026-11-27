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
import net.minecraft.world.phys.Vec3;

/**
 * 送月人. Each figure is drawn where the road puts it: a guide with a bell staff, four bearers under the
 * litter's poles with the moon-shell fragment, an empty place, and a rear bearer carrying the silver lantern.
 * Robes glide and sway rather than stride. Veils are translucent; faces are blank porcelain with one gold seam.
 */
public final class ProcessionRenderer extends EntityRenderer<MoonProcession> {
    static final ModelLayerLocation LAYER=new ModelLayerLocation(Veilborne.id("moon_procession"),"main");
    private static final Map<String,String> PARENTS=new HashMap<>();
    private static final ResourceLocation TEXTURE=Veilborne.id("textures/entity/procession.png");
    private static final ResourceLocation GLOW=Veilborne.id("textures/entity/procession_glow.png");
    private final GeoModel model;
    private static final int GHOST=150;
    private int ghostTicks,emptyCooldown,lastTick=-1;
    public ProcessionRenderer(EntityRendererProvider.Context c){
        super(c);shadowRadius=0;
        model=new GeoModel(c.bakeLayer(LAYER),new HashMap<>(PARENTS));
        GhostFigure.init(c);
    }
    static LayerDefinition create(){return GeoModel.create("procession.json",PARENTS);}
    @Override public ResourceLocation getTextureLocation(MoonProcession e){return TEXTURE;}
    @Override public boolean shouldRender(MoonProcession e,net.minecraft.client.renderer.culling.Frustum f,double x,double y,double z){return true;}

    @Override public void render(MoonProcession e,float entityYaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        float alpha=e.visibility(partial);
        double s=e.renderPath(partial),time=(e.tickCount+partial)/20.0;
        Vec3 origin=e.getPosition(partial);
        var state=e.state();
        double offer=state==MoonProcession.State.OFFER?Math.min(1,(e.stateClock()+partial)/300.0):0;
        boolean shadows=MoonShadows.casting();
        for(int i=0;i<MoonProcession.MEMBERS.length;i++){
            double[] m=MoonProcession.MEMBERS[i];
            Vec3 at=e.member(s,m[0],m[1]);
            float yaw=e.yawAt(s,m[0]);
            double bob=Math.sin(time*2.1+i*1.3)*.035;
            pose.pushPose();
            pose.translate(at.x-origin.x,at.y-origin.y+.06+bob,at.z-origin.z);
            pose.mulPose(Axis.YP.rotationDegrees(-yaw));
            pose.scale(-1,-1,1);
            posture(i,time,offer);
            if(shadows&&alpha>.3f){
                MoonShadows.anchor(at);
                model.part("figure").render(pose,MoonShadows.COLLECT,0xf000f0,OverlayTexture.NO_OVERLAY);
            }
            if(alpha>.01f)draw(pose,buffers,"figure",alpha,light);
            pose.popPose();
        }
        // The litter rests on the four bearers' shoulders and tilts with the ground between them.
        Vec3 front=e.member(s,MoonProcession.MEMBERS[1][0],0),back=e.member(s,MoonProcession.MEMBERS[3][0],0),mid=e.member(s,MoonProcession.LITTER,0);
        float yaw=e.yawAt(s,MoonProcession.LITTER);
        double pitch=Math.atan2(front.y-back.y,front.subtract(back).horizontalDistance());
        pose.pushPose();
        pose.translate(mid.x-origin.x,(front.y+back.y)/2-origin.y+.06+Math.sin(time*2.1)*.03,mid.z-origin.z);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.XP.rotationDegrees((float)Math.toDegrees(-pitch)));
        pose.scale(-1,-1,1);
        model.reset();
        model.rotate("litter",0,0,Math.sin(time*1.4)*.02);
        // Offering: the shell lifts from the cushion toward the moon, turning slowly, and fades upward.
        var shell=model.part("shell");
        if(shell!=null){shell.y-=(float)(offer*offer*40);shell.yRot+=(float)(offer*time*.4);}
        if(shadows&&alpha>.3f){MoonShadows.anchor(mid);model.part("litter").render(pose,MoonShadows.COLLECT,0xf000f0,OverlayTexture.NO_OVERLAY);}
        if(alpha>.01f)draw(pose,buffers,"litter",alpha,light);
        pose.popPose();
        // The empty place: nobody walks there, yet its shadow does. Carry a silver lantern close and someone waves.
        emptyPlace(e,s,time,partial,origin,pose,buffers,alpha);
        super.render(e,entityYaw,partial,pose,buffers,light);
    }
    /**
     * In this model +z is the front (tools/art/bearer.py), so a positive x turn swings an arm forward and tips a part's
     * top backward; the poses used to have it the other way round, every hand reaching behind.
     */
    private void posture(int member,double time,double offer){
        model.reset();
        double sway=Math.sin(time*1.7+member*.9);
        model.rotate("skirt",sway*.035,0,Math.sin(time*1.1+member)*.03);
        model.rotate("veil",sway*.05,0,0);
        model.rotate("head",Math.sin(time*.6+member*2)*.04,Math.sin(time*.35+member)*.12,0);
        boolean guide=member==0,rear=member==5,bearer=!guide&&!rear;
        model.show("staff",guide);
        model.show("lantern_pole",rear);
        if(guide){
            // The bell staff held out in front, leaning a little ahead.
            double hold=.55+Math.sin(time*1.7)*.03;
            model.rotate("arm_r",hold,0,.08);
            model.rotate("arm_l",.1+sway*.04,0,-.06);
            model.rotate("staff",-hold-.08,0,-.08);
        }else if(rear){
            // The lantern pole held out in front, its top leaning ahead and the lantern hanging straight from the crook.
            model.rotate("arm_r",.6,0,.1);
            model.rotate("arm_l",.08+sway*.04,0,-.06);
            model.rotate("lantern_pole",-.75,0,-.1);
            model.rotate("lantern",.15+Math.sin(time*1.6)*.12,0,Math.sin(time*1.1)*.1);
        }else{
            // Hands on the poles at their sides: the front pair reach back to them, the rear pair forward, toward the
            // litter between them. In the offering they lift it a little.
            double lift=offer*.35;
            boolean left=MoonProcession.MEMBERS[member][1]<0,front=MoonProcession.MEMBERS[member][0]<MoonProcession.LITTER;
            double reach=front?-.42-lift:.42+lift;
            model.rotate("arm_l",reach+sway*.015,0,left?-.04:.02);
            model.rotate("arm_r",reach-sway*.015,0,left?-.02:.04);
            model.rotate("body",-.05,0,0);
        }
    }
    private void draw(PoseStack pose,MultiBufferSource buffers,String part,float alpha,int light){
        var p=model.part(part);if(p==null)return;
        int lit=LightTexture.pack(Math.max(LightTexture.block(light),10),LightTexture.sky(light));
        p.render(pose,buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)),lit,OverlayTexture.NO_OVERLAY,FastColor.ARGB32.color((int)(alpha*255),255,255,255));
        int g=(int)(alpha*alpha*255);
        p.render(pose,buffers.getBuffer(RenderType.eyes(GLOW)),0xf000f0,OverlayTexture.NO_OVERLAY,FastColor.ARGB32.color(255,g,g,g));
    }
    private void emptyPlace(MoonProcession e,double s,double time,float partial,Vec3 origin,PoseStack pose,MultiBufferSource buffers,float alpha){
        Vec3 at=e.member(s,MoonProcession.EMPTY,0);
        float yaw=e.yawAt(s,MoonProcession.EMPTY);
        if(MoonShadows.casting()&&alpha>.3f){
            pose.pushPose();
            pose.translate(at.x-origin.x,at.y-origin.y+.06,at.z-origin.z);
            pose.mulPose(Axis.YP.rotationDegrees(-yaw));pose.scale(-1,-1,1);
            posture(2,time+1.3,0);model.show("staff",false);
            MoonShadows.anchor(at);
            model.part("figure").render(pose,MoonShadows.COLLECT,0xf000f0,OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        var me=Minecraft.getInstance().player;
        if(me==null)return;
        if(e.tickCount!=lastTick){
            lastTick=e.tickCount;
            boolean lantern=me.getMainHandItem().is(Veilborne.SILVER_LANTERN_ITEM.get())||me.getOffhandItem().is(Veilborne.SILVER_LANTERN_ITEM.get());
            boolean near=me.position().distanceTo(at)<10&&e.visibilityServer();
            if(ghostTicks>0)ghostTicks++;
            else if(emptyCooldown>0)emptyCooldown--;
            else if(lantern&&near)ghostTicks=1;
            if(ghostTicks>GHOST){ghostTicks=0;emptyCooldown=320;}
        }
        if(ghostTicks<=0)return;
        float t=ghostTicks+partial;
        float a=Math.min(1,Math.min(t/20f,(GHOST-t)/24f))*alpha;
        float wave=Math.min(1,Math.max(0,Math.min((t-18)/10f,(GHOST-20-t)/10f)));
        // It turns to whoever carries the lantern, then lifts a hand.
        Vec3 to=me.position().subtract(at);
        float face=(float)Math.toDegrees(Math.atan2(-to.x,to.z));
        pose.pushPose();
        pose.translate(at.x-origin.x,at.y-origin.y+.02,at.z-origin.z);
        pose.mulPose(Axis.YP.rotationDegrees(-face));
        GhostFigure.render(me,pose,buffers,a*.95f,(float)time,wave,0xd8d2ff);
        pose.popPose();
    }
}
