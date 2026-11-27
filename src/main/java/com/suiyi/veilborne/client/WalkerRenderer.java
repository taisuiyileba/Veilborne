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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 走针者: brass body like a thimble, six needle legs, a spool of scarlet thread on its back and one gold lens. It comes down
 * its thread head first, its front and back pairs closed on the thread and the middle pair spread; on the ground it walks
 * on a tripod gait; at its knot it sits facing it and the front two points weave over it; when its knot is undone it
 * rears, scissor-blades snapping. Coming and going it resolves out of thread.
 *
 * The clasp and the reach are solved offline against leg-tip targets (.dev/art/walker_pose.py): model-space deltas for
 * hip yaw, strut and shin, per leg from the front, mirrored on the right. Clasped, the front and back points close on the
 * thread 2.5 px under the belly; reaching, the front points meet 13.5 px ahead of the body, where the knot lies.
 */
public final class WalkerRenderer extends EntityRenderer<NeedleWalker> {
    static final ModelLayerLocation LAYER=new ModelLayerLocation(Veilborne.id("needle_walker"),"main");
    private static final Map<String,String> PARENTS=new HashMap<>();
    private static final ResourceLocation TEXTURE=Veilborne.id("textures/entity/needle_walker.png"),GLOW=Veilborne.id("textures/entity/needle_walker_glow.png");
    private static final float[][] CLASP={{1.078f,.256f,-.055f},{.20f,.10f,-.10f},{.377f,.323f,-.901f}},
        REACH={{1.089f,.257f,-.084f},{.35f,.12f,-.15f},{.56f,.35f,-.75f}};
    /** Height of the thread above the model's feet while it climbs (blocks, after the .85 scale). */
    private static final float THREAD=2.5f/16*.85f;
    private final GeoModel model;
    public WalkerRenderer(EntityRendererProvider.Context c){super(c);shadowRadius=.55f;model=new GeoModel(c.bakeLayer(LAYER),new HashMap<>(PARENTS));}
    static LayerDefinition create(){return GeoModel.create("needle_walker.json",PARENTS);}
    @Override public ResourceLocation getTextureLocation(NeedleWalker e){return TEXTURE;}
    @Override public void render(NeedleWalker e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        float vis=e.visibility(partial);
        if(vis<.01f)return;
        float time=(e.tickCount+partial)/20f;
        var state=e.state();
        pose.pushPose();
        if(state==NeedleWalker.State.DESCEND){
            Vec3 down=e.up().scale(-1);
            pose.mulPose(Axis.YP.rotation((float)Math.atan2(down.x,down.z)));
            pose.mulPose(Axis.XP.rotation((float)Math.asin(Mth.clamp(-down.y,-1,1))));
            pose.translate(0,-THREAD,0);
        }else if(state==NeedleWalker.State.KNOT){
            // Facing its knot, from wherever it is drawn this frame.
            Vec3 at=e.getPosition(partial),knot=Vec3.atBottomCenterOf(e.anchor());
            double dx=knot.x-at.x,dz=knot.z-at.z;
            float face=dx*dx+dz*dz>.0025?(float)Math.toDegrees(Math.atan2(-dx,dz)):Mth.rotLerp(partial,e.yBodyRotO,e.yBodyRot);
            pose.mulPose(Axis.YP.rotationDegrees(-face));
        }else{
            float body=Mth.rotLerp(partial,e.yBodyRotO,e.yBodyRot);
            pose.mulPose(Axis.YP.rotationDegrees(-body));
        }
        if(state==NeedleWalker.State.DEPART){float t=1-vis;pose.mulPose(Axis.YP.rotation(t*t*9));pose.scale(1-t*.6f,1-t*.6f,1-t*.6f);}
        pose.scale(-.85f,-.85f,.85f);
        pose(e,state,time,partial);
        var part=model.part("body");
        if(part!=null){
            int lit=LightTexture.pack(Math.max(LightTexture.block(light),4),LightTexture.sky(light));
            int tint=e.hurtTime>0?FastColor.ARGB32.color((int)(vis*255),255,160,150):FastColor.ARGB32.color((int)(vis*255),255,255,255);
            part.render(pose,buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)),lit,OverlayTexture.NO_OVERLAY,tint);
            int g=(int)(vis*255);
            part.render(pose,buffers.getBuffer(RenderType.eyes(GLOW)),0xf000f0,OverlayTexture.NO_OVERLAY,FastColor.ARGB32.color(255,g,g,g));
        }
        pose.popPose();
        super.render(e,yaw,partial,pose,buffers,light);
    }
    private void pose(NeedleWalker e,NeedleWalker.State state,float time,float partial){
        model.reset();
        float walk=e.walkAnimation.position(partial)*1.3f,speed=Math.min(1,e.walkAnimation.speed(partial)*1.6f);
        String[] sides={"l","r"};
        for(int s=0;s<2;s++)for(int i=0;i<3;i++){
            String hip="hip_"+sides[s]+i,leg="leg_"+sides[s]+i,low=leg+"_low";
            float side=s==0?1:-1;
            switch(state){
                case DESCEND -> {
                    // Hand over hand: the gripping pairs shift a little in turn as it lets itself down.
                    float grip=i==1?0:Mth.sin(time*6+(i==0?0:(float)Math.PI)+s*(float)Math.PI)*.05f;
                    model.rotate(hip,0,side*(CLASP[i][0]+grip),0);
                    model.rotate(leg,0,0,side*(CLASP[i][1]+grip*.6f));
                    model.rotate(low,0,0,side*CLASP[i][2]);
                }
                case KNOT -> {
                    // The front points work the knot in small loops, left and right half a turn apart.
                    float t=time*5.2f+s*(float)Math.PI,wv=i==0?1:0;
                    model.rotate(hip,0,side*(REACH[i][0]+wv*.25f*Mth.sin(t)),0);
                    model.rotate(leg,0,0,side*(REACH[i][1]+wv*.3f*Mth.cos(t)+(1-wv)*.03f*Mth.sin(time*1.3f+i)));
                    model.rotate(low,0,0,side*REACH[i][2]);
                }
                default -> {
                    // Tripod: front and back of one side move with the middle of the other.
                    float ph=walk+(((i+s)&1)==0?0:(float)Math.PI);
                    float swing=Mth.sin(ph)*.38f*speed,lift=Math.max(0,Mth.cos(ph))*.3f*speed;
                    float rear=state==NeedleWalker.State.ALARM&&i==0?1:0;
                    model.rotate(leg,0,swing,side*(lift+rear*.9f));
                    model.rotate(low,0,0,side*rear*(-.5f+.25f*Mth.sin(time*14+i)));
                }
            }
        }
        float snip=state==NeedleWalker.State.ALARM?Mth.sin(time*11)*.45f:state==NeedleWalker.State.KNOT?Mth.sin(time*3)*.12f:0;
        model.rotate("blade_l",0,snip,0);model.rotate("blade_r",0,-snip,0);
        model.rotate("spool",time*(state==NeedleWalker.State.ROAM||state==NeedleWalker.State.KNOT?2.2f:.6f),0,0);
        if(state==NeedleWalker.State.ALARM)model.rotate("body",-.28f,0,0);
        float look=Mth.wrapDegrees(Mth.rotLerp(partial,e.yHeadRotO,e.yHeadRot)-Mth.rotLerp(partial,e.yBodyRotO,e.yBodyRot));
        model.rotate("head",0,-look*Mth.DEG_TO_RAD*.6f,0);
    }
}
