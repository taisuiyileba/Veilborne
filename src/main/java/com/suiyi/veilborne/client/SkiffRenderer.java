package com.suiyi.veilborne.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.suiyi.veilborne.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Ghost sampan: translucent lit hull, additive glow layer, and a luminous line fishing a star. */
public final class SkiffRenderer extends EntityRenderer<TideSkiff> {
    private static final ResourceLocation TEXTURE=Veilborne.id("textures/entity/skiff.png");
    private static final ResourceLocation GLOW=Veilborne.id("textures/entity/skiff_glow.png");
    private static final ResourceLocation STAR=Veilborne.id("textures/entity/star_bait.png");
    private static final ResourceLocation WHITE=Veilborne.id("textures/misc/white.png");
    private static final Vector3f ROD_TIP=new Vector3f(0,0,33);
    private final SkiffModel model;
    public SkiffRenderer(EntityRendererProvider.Context context){
        super(context);shadowRadius=0;
        model=new SkiffModel(context.bakeLayer(SkiffModel.LAYER));
    }
    @Override public ResourceLocation getTextureLocation(TideSkiff boat){return TEXTURE;}
    @Override public void render(TideSkiff boat,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        double time=(boat.tickCount+partial)/20.0;
        float alpha=boat.visibility(partial);
        if(alpha<=.01f)return;
        double bite=Math.pow(Math.max(0,Math.sin(time*.55-1.2)),24);
        model.animate(time,bite);
        Matrix4f toEntity=new Matrix4f(pose.last().pose()).invert();
        pose.pushPose();
        pose.translate(0,Math.sin(time*.9)*.07,0);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.ZP.rotationDegrees((float)(Math.sin(time*.7)*2.2)));
        pose.mulPose(Axis.XP.rotationDegrees((float)(Math.sin(time*.5+.8)*1.4)));
        pose.scale(-1,-1,1);
        // A spirit carries its own faint light, so the boat never dissolves into night.
        int lit=LightTexture.pack(Math.max(LightTexture.block(light),9),LightTexture.sky(light));
        model.root.render(pose,buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)),lit,OverlayTexture.NO_OVERLAY,FastColor.ARGB32.color((int)(alpha*255),255,255,255));
        int g=(int)(alpha*alpha*255);
        model.root.render(pose,buffers.getBuffer(RenderType.eyes(GLOW)),0xf000f0,OverlayTexture.NO_OVERLAY,FastColor.ARGB32.color(255,g,g,g));
        Vector3f tip=toEntity.transformPosition(model.locate(pose,ROD_TIP,"hull","fisher","rod"));
        pose.popPose();

        // The line: a thin camera-facing ribbon from the rod tip to the star it has hooked.
        Vector3f bait=new Vector3f(tip).add((float)(Math.sin(time*.6)*.12),(float)-(2.2+bite*.45),(float)(Math.cos(time*.47)*.1));
        var camera=entityRenderDispatcher.camera;
        Vector3f eye=camera.getPosition().subtract(boat.getPosition(partial)).toVector3f();
        Matrix4f m=pose.last().pose();
        VertexConsumer line=buffers.getBuffer(RenderType.eyes(WHITE));
        int lineColor=FastColor.ARGB32.color(255,(int)(55*alpha),(int)(130*alpha),(int)(140*alpha));
        Vector3f previous=tip;
        for(int i=1;i<=12;i++){
            float t=i/12f;
            Vector3f next=new Vector3f(tip).lerp(bait,t);
            next.x+=(float)(Math.sin(t*Math.PI)*.06*Math.sin(time*.9));
            ribbon(line,m,previous,next,eye,.006f,lineColor);
            previous=next;
        }
        VertexConsumer star=buffers.getBuffer(RenderType.eyes(STAR));
        Quaternionf face=camera.rotation();
        float pulse=(float)(.75+.25*Math.sin(time*3.1));
        int c=FastColor.ARGB32.color(255,(int)(255*alpha*pulse),(int)(226*alpha*pulse),(int)(160*alpha*pulse));
        billboard(star,m,bait,face,(float)(.22+.05*Math.sin(time*2.3)),(float)(time*.8),c);
        int halo=FastColor.ARGB32.color(255,(int)(60*alpha),(int)(110*alpha),(int)(120*alpha));
        billboard(star,m,bait,face,.6f,(float)(-time*.3),halo);
        super.render(boat,yaw,partial,pose,buffers,light);
    }
    private static void ribbon(VertexConsumer b,Matrix4f m,Vector3f a,Vector3f c,Vector3f eye,float width,int color){
        Vector3f dir=new Vector3f(c).sub(a),view=new Vector3f(a).sub(eye);
        Vector3f side=dir.cross(view,new Vector3f());
        if(side.lengthSquared()<1e-8)return;
        side.normalize(width);
        // Both windings: the eyes render type culls back faces.
        vertex(b,m,a.x+side.x,a.y+side.y,a.z+side.z,color,0,0);vertex(b,m,c.x+side.x,c.y+side.y,c.z+side.z,color,0,1);
        vertex(b,m,c.x-side.x,c.y-side.y,c.z-side.z,color,1,1);vertex(b,m,a.x-side.x,a.y-side.y,a.z-side.z,color,1,0);
        vertex(b,m,a.x-side.x,a.y-side.y,a.z-side.z,color,1,0);vertex(b,m,c.x-side.x,c.y-side.y,c.z-side.z,color,1,1);
        vertex(b,m,c.x+side.x,c.y+side.y,c.z+side.z,color,0,1);vertex(b,m,a.x+side.x,a.y+side.y,a.z+side.z,color,0,0);
    }
    private static void billboard(VertexConsumer b,Matrix4f m,Vector3f p,Quaternionf face,float size,float spin,int color){
        float[][] corners={{-1,-1,0,1},{-1,1,0,0},{1,1,1,0},{1,-1,1,1}};
        for(float[] k:corners){
            float cs=Mth.cos(spin),sn=Mth.sin(spin);
            Vector3f v=new Vector3f((k[0]*cs-k[1]*sn)*size,(k[0]*sn+k[1]*cs)*size,0).rotate(face).add(p);
            vertex(b,m,v.x,v.y,v.z,color,k[2],k[3]);
        }
    }
    private static void vertex(VertexConsumer b,Matrix4f m,float x,float y,float z,int color,float u,float v){
        b.addVertex(m,x,y,z).setColor(color).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xf000f0).setNormal(0,1,0);
    }
}
