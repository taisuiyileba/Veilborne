package com.suiyi.veilborne.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.suiyi.veilborne.*;
import java.nio.FloatBuffer;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

/**
 * 苍翠梦潮 on screen. After the vanilla sky: the canopy (dream_sky.fsh). Once the solid world and its creatures are drawn,
 * before water, particles and clouds: the light pass (dream_post.fsh), leaf-light moving over whatever is out under the
 * forest and flower patterns opening on the land; then, in the world with depth, the hanging trees, their roots and the
 * road of roots (DreamGrove), and the dream tree. After the particles: the deer (DeerModel), then everything small and
 * lit that goes with it (DreamGround) and the leaf-fish (DreamFlock).
 */
public final class DreamRenderer {
    static ShaderInstance skyShader,postShader;
    static final ResourceLocation SKY=Veilborne.id("textures/environment/dream_sky.png"),DAPPLE=Veilborne.id("textures/environment/dream_dapple.png");
    private static RenderTarget scene;
    private static int pointTexture=-1,heightTexture=-1,heightVersion=-1;
    private static final int POINTS=64;
    private static final FloatBuffer POINT_DATA=MemoryUtil.memAllocFloat(POINTS*8),HEIGHT_DATA=MemoryUtil.memAllocFloat(DreamClient.MAP*DreamClient.MAP);

    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        var stage=e.getStage();
        float partial=e.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera=e.getCamera().getPosition();
        double time=(DreamClient.ticks+partial)/20.0;
        Matrix4f view=new Matrix4f(e.getModelViewMatrix()),proj=new Matrix4f(e.getProjectionMatrix());
        boolean on=DreamClient.active();
        double p=on?DreamClient.progress(partial):0,s=on?DreamClient.strength():0;
        if(stage==RenderLevelStageEvent.Stage.AFTER_SKY){
            if(on&&TideConfig.SKY.get())sky(view,proj,camera,p,s,time,partial);
        }else if(stage==RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES){
            if(!on)return;
            post(view,proj,camera,p,s,time,partial);
            DreamGrove.draw(view,proj,camera,p,s,time,partial);
        }else if(stage==RenderLevelStageEvent.Stage.AFTER_PARTICLES){
            if(on&&TideConfig.SKY.get())DeerModel.render(view,proj,camera,partial);
            else DeerModel.lastPose=null;
            Quaternionf facing=e.getCamera().rotation();
            RenderSystem.enableBlend();RenderSystem.disableCull();RenderSystem.depthMask(false);RenderSystem.enableDepthTest();
            DreamGround.render(camera,facing,p,s,time,partial);
            DreamFlock.render(camera,facing,p,s,time,partial);
            RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();
        }
    }
    private static void quad(){
        BufferBuilder q=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION);
        q.addVertex(-1,-1,0);q.addVertex(1,-1,0);q.addVertex(1,1,0);q.addVertex(-1,1,0);
        BufferUploader.drawWithShader(q.buildOrThrow());
    }
    static Vec3 sun(float partial){
        var level=Minecraft.getInstance().level;
        float angle=level.getTimeOfDay(partial)*(float)Math.PI*2;
        return new Vec3(-Math.sin(angle),Math.cos(angle),0);
    }

    // ------------------------------------------------------------------ the canopy
    private static void sky(Matrix4f view,Matrix4f proj,Vec3 camera,double p,double s,double time,float partial){
        double presence=DreamTimeline.sky(p)*s;
        if(presence<.002||skyShader==null)return;
        var mc=Minecraft.getInstance();
        var st=DreamClient.state;
        double day=DreamClient.dayness(partial);
        Vec3 c=DreamClient.center(),dome=c.add(0,DreamTimeline.APEX-DreamTimeline.DOME_R,0).subtract(camera),eye=c.add(0,1.6,0).subtract(camera);
        skyShader.safeGetUniform("InvProjView").set(new Matrix4f(proj).mul(view).invert());
        skyShader.safeGetUniform("SkyLook").set((float)(time%3600),(float)day,(float)(.84+.16*TideConfig.BRIGHTNESS.get()),(float)presence);
        skyShader.safeGetUniform("Dome").set((float)dome.x,(float)dome.y,(float)dome.z,(float)DreamTimeline.DOME_R);
        skyShader.safeGetUniform("Eye").set((float)eye.x,(float)eye.y,(float)eye.z,(float)(Math.floorMod(st.seed(),997L)/997.0));
        skyShader.safeGetUniform("Grow").set((float)DreamTimeline.canopy(p),(float)DreamTimeline.leafLoss(p),(float)DreamTimeline.veinsFade(p),(float)(DreamTimeline.fireflies(p)*s));
        Vec3 sun=sun(partial);
        skyShader.safeGetUniform("SunDir").set((float)sun.x,(float)sun.y,(float)sun.z,1-mc.level.getRainLevel(partial));
        skyShader.safeGetUniform("Tint").set((float)DreamTimeline.gold(p),(float)(1-day),1f,DreamHooks.veil(partial));
        RenderSystem.setShaderTexture(0,SKY);
        RenderSystem.setShader(()->skyShader);
        RenderSystem.enableBlend();RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();RenderSystem.depthMask(false);
        quad();
        RenderSystem.defaultBlendFunc();RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------ light on the land
    private static void post(Matrix4f view,Matrix4f proj,Vec3 camera,double p,double s,double time,float partial){
        if(postShader==null)return;
        double dapple=TideConfig.DREAM_DAPPLE.get()?DreamTimeline.dapple(p)*s:0;
        int count=TideConfig.DREAM_PATTERNS.get()?points(camera,p,s,partial):0;
        if(dapple<.003&&count==0)return;
        if(!DreamClient.mapReady&&count==0)return;
        var mc=Minecraft.getInstance();
        var main=mc.getMainRenderTarget();
        if(scene==null){scene=new TextureTarget(main.width,main.height,false,Minecraft.ON_OSX);scene.setFilterMode(GL11.GL_NEAREST);}
        else if(scene.width!=main.width||scene.height!=main.height)scene.resize(main.width,main.height,Minecraft.ON_OSX);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,scene.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0,0,main.width,main.height,0,0,scene.width,scene.height,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
        main.bindWrite(false);
        heights();
        double day=DreamClient.dayness(partial);
        Vec3 sun=sun(partial);
        if(sun.y<0)sun=sun.scale(-1);
        Vec3 c=DreamClient.center();
        postShader.safeGetUniform("InvProjView").set(new Matrix4f(proj).mul(view).invert());
        postShader.safeGetUniform("Look").set((float)(dapple*(DreamClient.mapReady?1:0)),(float)(time%3600),(float)day,(float)count);
        postShader.safeGetUniform("Sun").set((float)sun.x,(float)sun.y,(float)sun.z,(float)(c.y+DreamTimeline.domeHeight(Math.hypot(camera.x-c.x,camera.z-c.z))-40-camera.y));
        postShader.safeGetUniform("Height").set((float)(DreamClient.mapX-camera.x),(float)(DreamClient.mapZ-camera.z),(float)DreamClient.MAP,(float)camera.y);
        postShader.safeGetUniform("Tone").set((float)DreamTimeline.gold(p),(float)(.85*TideConfig.BRIGHTNESS.get()+.15),(float)(1-day),0f);
        RenderSystem.setShaderTexture(0,scene.getColorTextureId());
        RenderSystem.setShaderTexture(1,main.getDepthTextureId());
        RenderSystem.setShaderTexture(2,pointTexture);
        RenderSystem.setShaderTexture(3,heightTexture);
        RenderSystem.setShaderTexture(4,DAPPLE);
        RenderSystem.setShader(()->postShader);
        RenderSystem.disableBlend();RenderSystem.disableDepthTest();RenderSystem.depthMask(false);
        quad();
        RenderSystem.enableDepthTest();RenderSystem.depthMask(true);
    }
    private static void heights(){
        if(heightTexture<0){
            heightTexture=GlStateManager._genTexture();
            GlStateManager._bindTexture(heightTexture);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL30.GL_CLAMP_TO_EDGE);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL30.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_R32F,DreamClient.MAP,DreamClient.MAP,0,GL11.GL_RED,GL11.GL_FLOAT,(FloatBuffer)null);
        }
        if(heightVersion==DreamClient.ticks)return;
        heightVersion=DreamClient.ticks;
        HEIGHT_DATA.clear();HEIGHT_DATA.put(DreamClient.heights);HEIGHT_DATA.flip();
        GlStateManager._bindTexture(heightTexture);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH,0);GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS,0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS,0);GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT,4);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,DreamClient.MAP,DreamClient.MAP,GL11.GL_RED,GL11.GL_FLOAT,HEIGHT_DATA);
        GlStateManager._bindTexture(0);
    }
    /** Every flower pattern on the land near you, as up to 64 points (centre, radius; kind, strength, age or gap, seed). */
    private static int points(Vec3 camera,double p,double s,float partial){
        var st=DreamClient.state;
        List<float[]> pts=new ArrayList<>();
        int ticks=DreamClient.ticks;
        double fade=DreamTimeline.light(p)*s;
        for(var b:DreamClient.blooms){
            double age=(ticks-b.born()+partial)/20.0;
            if(age<0)continue;
            double str=b.kind()==3?1-TideTimeline.smooth(4,8,age):TideTimeline.smooth(0,.4,age)*(1-TideTimeline.smooth(22,44,age));
            add(pts,b.at(),camera,b.radius(),b.kind(),str*Math.max(fade,.5),age,b.seed());
        }
        // The deer's hoof rings: bright for most of a minute, then flower shadows till the end.
        if(st.hasRoute())for(var f:DreamClient.falls){
            if(f.p()>p)break;
            double sec=(p-f.p())*st.duration()/20;
            add(pts,f.at(),camera,8.5,1,DreamTimeline.hoofRing(sec)*fade,sec,f.leg()*.25+.1);
        }
        if(st.hasTree()){
            double growth=DreamTimeline.treeGrowth(DreamClient.ageNow(partial)-st.treeAt())*(1-DreamTimeline.treeWither(p));
            add(pts,Vec3.atBottomCenterOf(st.tree()),camera,5.5*growth+.5,4,growth*fade*.8,0,.37);
        }
        if(st.has(DreamData.ENCLOSURE)){
            double now=DreamClient.ageNow(partial);
            double v=DreamTimeline.vines(now-st.enclosureAt(),st.enclosureOpen()?-1:now-st.closedAt());
            add(pts,Vec3.atBottomCenterOf(st.enclosure()),camera,DreamTimeline.RING,2,v,st.enclosureExit(),.6);
        }
        Vec3 c0=DreamClient.center();
        double grow=DreamTimeline.rootGrow(p),road=DreamTimeline.road(p),back=DreamTimeline.rootWithdraw(p);
        for(var g:DreamGrove.grips){
            double reach=TideTimeline.smooth(g.delay(),g.delay()+.45,g.road()?road:grow)*(1-TideTimeline.smooth(g.delay()*.35,g.delay()*.35+.62,back));
            if(reach<.98)continue;
            add(pts,c0.add(g.at()),camera,g.road()?11:8,5,fade*.9,0,g.delay());
        }
        pts.sort(Comparator.comparingDouble(a->a[0]*a[0]+a[1]*a[1]+a[2]*a[2]-a[3]*a[3]));
        int n=Math.min(POINTS,pts.size());
        POINT_DATA.clear();
        for(int row=0;row<2;row++)for(int i=0;i<POINTS;i++)for(int k=0;k<4;k++)POINT_DATA.put(i<n?pts.get(i)[row*4+k]:0);
        POINT_DATA.flip();
        if(pointTexture<0){
            pointTexture=GlStateManager._genTexture();
            GlStateManager._bindTexture(pointTexture);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL30.GL_CLAMP_TO_EDGE);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL30.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,POINTS,2,0,GL11.GL_RGBA,GL11.GL_FLOAT,(FloatBuffer)null);
        }
        GlStateManager._bindTexture(pointTexture);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH,0);GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS,0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS,0);GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT,4);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,POINTS,2,GL11.GL_RGBA,GL11.GL_FLOAT,POINT_DATA);
        GlStateManager._bindTexture(0);
        return n;
    }
    private static void add(List<float[]> pts,Vec3 at,Vec3 camera,double radius,int kind,double strength,double age,double seed){
        if(strength<.01||radius<.1)return;
        Vec3 d=at.subtract(camera);
        if(d.lengthSqr()>(radius+130)*(radius+130))return;
        pts.add(new float[]{(float)d.x,(float)d.y,(float)d.z,(float)radius,kind,(float)Math.min(1,strength),(float)age,(float)seed});
    }
    private DreamRenderer(){}
}
