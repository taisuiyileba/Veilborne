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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

/**
 * 无声白昼 on screen, in three passes. After the vanilla sky, the pale sky (hush_sky.fsh): a bleached, frozen daylight, a
 * paper-white sun or moon with no halo, the black sun that bends the sky round it like a lens, its black corona while it
 * covers the sun, and the white line on the horizon with the dark afterimage it leaves. After the whole level, before your
 * own hand is drawn, the colour pass (hush_post.fsh): the finished frame is copied and drawn back grey where the grey tide
 * has reached, with a little amber kept round lamps and lights, full colour inside the island of sound, in the rings the
 * bell sends out and under each footfall, and the far distance softened. Then, over the sky and the held clouds, what keeps
 * its colour up there: the world's colours drawn up into the black sun in thin ribbons, and given back (hush_aurora.fsh),
 * and the amber glints that must stay amber. The air and the ground are HushGround.
 */
public final class HushRenderer {
    static ShaderInstance skyShader,postShader,auroraShader;
    static final ResourceLocation SKY=Veilborne.id("textures/environment/hush_sky.png");
    static final int AMBER=0xffb45a,PAPER=0xf4f1ea;
    private static RenderTarget scene;
    private static int pointTexture=-1;
    private static final int POINTS=64;
    private static final FloatBuffer POINT_DATA=MemoryUtil.memAllocFloat(POINTS*8);

    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        var stage=e.getStage();
        float partial=e.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera=e.getCamera().getPosition();
        double time=(HushClient.ticks+partial)/20.0;
        Matrix4f view=new Matrix4f(e.getModelViewMatrix()),proj=new Matrix4f(e.getProjectionMatrix());
        boolean on=HushClient.active();
        if(stage==RenderLevelStageEvent.Stage.AFTER_SKY){
            if(on&&TideConfig.SKY.get())sky(view,proj,partial,time);
        }else if(stage==RenderLevelStageEvent.Stage.AFTER_ENTITIES){
            if(on)HushGround.entities(camera,HushClient.progress(partial),HushClient.strength(),time,partial);
        }else if(stage==RenderLevelStageEvent.Stage.AFTER_PARTICLES){
            Quaternionf facing=e.getCamera().rotation();
            RenderSystem.enableBlend();RenderSystem.disableCull();RenderSystem.depthMask(false);RenderSystem.enableDepthTest();
            HushGround.render(camera,facing,on?HushClient.progress(partial):0,on?HushClient.strength():0,time,partial);
            RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();
        }else if(stage==RenderLevelStageEvent.Stage.AFTER_LEVEL){
            boolean posted=post(view,proj,camera,partial,time);
            if(on||posted)afterPost(view,proj,camera,e.getCamera().rotation(),partial,time);
        }
    }

    // ------------------------------------------------------------------ the pale sky
    private static void sky(Matrix4f view,Matrix4f proj,float partial,double time){
        var st=HushClient.state;
        double p=HushClient.progress(partial),s=HushClient.strength(),day=HushClient.dayness(partial);
        double pale=HushTimeline.pale(p)*s;
        if(pale<.002||skyShader==null)return;
        Matrix4f inv=new Matrix4f(proj).mul(view).invert();
        skyShader.safeGetUniform("InvProjView").set(inv);
        skyShader.safeGetUniform("SkyLook").set((float)(time%3600),(float)day,(float)(.84+.16*TideConfig.BRIGHTNESS.get()),(float)Math.min(1,pale));
        Vec3 w=HushClient.white(partial),b=HushClient.black(partial);
        skyShader.safeGetUniform("White").set((float)w.x,(float)w.y,(float)w.z,(float)HushTimeline.WHITE_RADIUS);
        skyShader.safeGetUniform("Black").set((float)b.x,(float)b.y,(float)b.z,(float)HushTimeline.BLACK_RADIUS);
        double vis=HushTimeline.blackVisible(p)*s;
        skyShader.safeGetUniform("Eclipse").set((float)HushClient.covered,(float)(HushTimeline.corona(p)*s),(float)(.6+.4*HushTimeline.approach(p)),(float)vis);
        int age=(int)HushClient.ageNow(partial);
        double line=HushTimeline.line(age,st.duration()),after=HushTimeline.afterimage(age,st.duration());
        Vec3 base=HushClient.lineBase().subtract(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        double top=Math.atan2(HushTimeline.LINE_HEIGHT,Math.max(40,Math.hypot(base.x,base.z)));
        skyShader.safeGetUniform("Thunder").set((float)Math.atan2(base.z,base.x),(float)(line*s),(float)(after*s),(float)top);
        skyShader.safeGetUniform("Key").set((float)(Math.floorMod(st.seed(),997L)/997.0),(float)HushTimeline.totality(p),0f,0f);
        RenderSystem.setShaderTexture(0,SKY);
        RenderSystem.setShader(()->skyShader);
        RenderSystem.enableBlend();RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();RenderSystem.depthMask(false);
        quad(0);
        RenderSystem.defaultBlendFunc();RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.disableBlend();
    }
    private static void quad(float z){
        BufferBuilder q=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION);
        q.addVertex(-1,-1,z);q.addVertex(1,-1,z);q.addVertex(1,1,z);q.addVertex(-1,1,z);
        BufferUploader.drawWithShader(q.buildOrThrow());
    }

    // ------------------------------------------------------------------ the colour pass
    /** Returns whether the pass ran. It runs while the weather drains colour, and near still candles at any time. */
    private static boolean post(Matrix4f view,Matrix4f proj,Vec3 camera,float partial,double time){
        if(postShader==null||!TideConfig.HUSH_FADE.get())return false;
        boolean on=HushClient.active();
        double p=on?HushClient.progress(partial):0,s=on?HushClient.strength():0;
        double drain=HushTimeline.drain(p)*s,skyDrain=HushTimeline.drain(p)*(1-HushTimeline.skyColour(p))*s;
        boolean echo=HushClient.echoKind==4&&HushClient.ticks-HushClient.echoAt<160;
        if(drain<.002&&skyDrain<.002&&HushClient.candles.isEmpty()&&!echo)return false;
        var mc=Minecraft.getInstance();
        var main=mc.getMainRenderTarget();
        if(scene==null){scene=new TextureTarget(main.width,main.height,false,Minecraft.ON_OSX);scene.setFilterMode(GL11.GL_LINEAR);}
        else if(scene.width!=main.width||scene.height!=main.height)scene.resize(main.width,main.height,Minecraft.ON_OSX);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,scene.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0,0,main.width,main.height,0,0,scene.width,scene.height,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
        main.bindWrite(false);
        int count=points(camera,p,s,partial);
        Matrix4f inv=new Matrix4f(proj).mul(view).invert();
        postShader.safeGetUniform("InvProjView").set(inv);
        postShader.safeGetUniform("Look").set((float)drain,(float)skyDrain,(float)(time%3600),(float)count);
        double blur=TideConfig.HUSH_BLUR.get()?drain*(1-TideTimeline.smooth(.9,.96,p)):0;
        double reach=Mth.clamp((mc.options.getEffectiveRenderDistance()*16+24)/HushTimeline.FAR,.3,1);
        postShader.safeGetUniform("Front").set((float)(HushTimeline.greyFront(p)*reach),(float)(HushTimeline.colourFront(p)*reach),(float)blur,(float)HushClient.covered);
        double day=HushClient.dayness(partial);
        postShader.safeGetUniform("Tone").set(.84f,.845f,.855f,(float)(Mth.lerp(day,.72,.84)+.08*HushClient.covered));
        postShader.safeGetUniform("Texel").set(1f/main.width,1f/main.height,0f,0f);
        RenderSystem.setShaderTexture(0,scene.getColorTextureId());
        RenderSystem.setShaderTexture(1,main.getDepthTextureId());
        RenderSystem.setShaderTexture(2,pointTexture);
        RenderSystem.setShader(()->postShader);
        RenderSystem.disableBlend();RenderSystem.disableDepthTest();RenderSystem.depthMask(false);
        quad(0);
        RenderSystem.enableDepthTest();RenderSystem.depthMask(true);
        return true;
    }
    /** Everything the colour pass keeps coloured, as up to 64 points (position, radius; kind, strength, softness). */
    private static int points(Vec3 camera,double p,double s,float partial){
        var mc=Minecraft.getInstance();
        List<float[]> pts=new ArrayList<>();
        int ticks=HushClient.ticks;
        // Lamps keep a little amber round them; at totality even that goes.
        for(var l:HushClient.lamps)add(pts,l.at(),camera,l.radius(),0,l.strength(),l.radius()>10?.5:.1);
        for(Player pl:mc.level.players()){
            if(pl.isSpectator()||pl.distanceToSqr(camera)>36*36)continue;
            boolean lantern=HushService.silentLantern(pl);
            if(lantern||HushService.isLight(pl.getMainHandItem())||HushService.isLight(pl.getOffhandItem()))
                add(pts,pl.getPosition(partial).add(0,1.1,0),camera,lantern?9:4.2,0,.9,lantern?.5:.12);
        }
        for(var en:mc.level.entitiesForRendering())if(en instanceof HushProcession proc&&en.distanceToSqr(camera)<96*96){
            Vec3 lamp=HushGround.processionLantern(proc,partial);
            float vis=proc.visibility(partial);
            add(pts,lamp,camera,proc.answering()?12:5,0,vis,proc.answering()?.45:.2);
        }
        for(var f:HushClient.flashes){
            double t=(ticks-f.born()+partial)/20.0;
            add(pts,f.at(),camera,f.reach()*TideTimeline.smooth(0,.55,t)+.5,0,1-TideTimeline.smooth(.7,2.8,t),.5);
        }
        for(var k:HushClient.knocks){
            double t=ticks-k.born()+partial;
            for(int off:new int[]{0,6,12}){double tt=t-off;if(tt>=0&&tt<34)add(pts,k.at(),camera,.15+tt*.085,2,(1-tt/34)*.95,.3);}
        }
        for(var pr:HushClient.prints){
            double t=(ticks-pr.born()+partial)/20.0;
            add(pts,pr.at(),camera,.62,1,1-TideTimeline.smooth(.3,2.1,t),.45);
        }
        var st=HushClient.state;
        if(st!=null&&st.hasBell()){
            Vec3 bell=st.bellPoint();
            int age=(int)HushClient.ageNow(partial);
            if(st.found()){
                double is=HushTimeline.island(age-st.foundAt());
                if(is>0)add(pts,bell,camera,HushTimeline.ISLAND_RANGE*is,1,1,3);
            }else if(HushClient.progress(partial)<HushTimeline.BELL_UNTIL){
                // The only colour left is the bell's; each time it rings a ring of colour goes out through the grey.
                add(pts,bell,camera,1.3,1,.9,.8);
                int since=HushTimeline.sinceRing(age,st.bellAt(),st.seed());
                if(since>=0&&since<HushTimeline.RIPPLE_TICKS){double r=(since+partial)*HushTimeline.RIPPLE_SPEED;add(pts,bell,camera,r,2,(1-since/(double)HushTimeline.RIPPLE_TICKS)*.95,1.4+r*.05);}
            }
        }
        for(BlockPos c:HushClient.candles)add(pts,Vec3.atCenterOf(c),camera,4.6,3,.92,0);
        if(HushClient.echoKind==4){double t=(ticks-HushClient.echoAt+partial)/20.0;if(t<8)add(pts,HushClient.echoPos,camera,1+7*TideTimeline.smooth(0,1.2,t),3,1-TideTimeline.smooth(5,8,t),0);}
        pts.sort(Comparator.comparingDouble(a->a[0]*a[0]+a[1]*a[1]+a[2]*a[2]-a[3]*a[3]));
        int n=Math.min(POINTS,pts.size());
        POINT_DATA.clear();
        for(int row=0;row<2;row++)for(int i=0;i<POINTS;i++)for(int c=0;c<4;c++)POINT_DATA.put(i<n?pts.get(i)[row*4+c]:0);
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
    private static void add(List<float[]> pts,Vec3 at,Vec3 camera,double radius,int kind,double strength,double soft){
        if(strength<.01||radius<.05)return;
        Vec3 d=at.subtract(camera);
        if(d.lengthSqr()>(radius+120)*(radius+120))return;
        pts.add(new float[]{(float)d.x,(float)d.y,(float)d.z,(float)radius,kind,(float)Math.min(1,strength),(float)soft,0});
    }

    // ------------------------------------------------------------------ what keeps its colour: the ribbons in the sky, amber glints
    private static void afterPost(Matrix4f view,Matrix4f proj,Vec3 camera,Quaternionf facing,float partial,double time){
        var mc=Minecraft.getInstance();
        boolean on=HushClient.active();
        double p=on?HushClient.progress(partial):0,s=on?HushClient.strength():0;
        double ribbons=HushTimeline.ribbons(p)*s*HushTimeline.blackVisible(p);
        double heard=Math.max(0,1-(HushClient.ticks-HushClient.heardAt+partial)/60.0);
        double corona=HushTimeline.corona(p)*s,visible=HushTimeline.blackVisible(p)*s;
        if(on&&auroraShader!=null&&TideConfig.SKY.get()&&(Math.abs(ribbons)>.005||heard>0||visible>.002)){
            Vec3 b=HushClient.black(partial);
            var main=mc.getMainRenderTarget();
            float cloudY=mc.level.effects().getCloudHeight();
            double pixel=Math.toRadians(mc.options.fov().get())/Math.max(1,main.height);
            auroraShader.safeGetUniform("InvProjView").set(new Matrix4f(proj).mul(view).invert());
            auroraShader.safeGetUniform("Black").set((float)b.x,(float)b.y,(float)b.z,(float)HushTimeline.BLACK_RADIUS);
            auroraShader.safeGetUniform("Flow").set((float)ribbons,(float)(time%3600),(float)visible,(float)(Math.floorMod(HushClient.state.seed(),997L)/997.0));
            auroraShader.safeGetUniform("Heard").set((float)heard,(float)corona,Float.isNaN(cloudY)?1e4f:(float)(cloudY-camera.y),(float)pixel);
            RenderSystem.setShaderTexture(0,main.getDepthTextureId());
            RenderSystem.setShader(()->auroraShader);
            RenderSystem.enableBlend();RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.disableDepthTest();RenderSystem.depthMask(false);
            quad(0);
            RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();RenderSystem.enableDepthTest();RenderSystem.depthMask(true);
        }
        // Amber that must stay amber: lantern flames, raised lights, the procession's lamp. Drawn with the level's own matrices.
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(proj,VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.getModelViewStack().pushMatrix();RenderSystem.getModelViewStack().set(view);RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();RenderSystem.disableCull();RenderSystem.depthMask(false);RenderSystem.enableDepthTest();
        HushGround.glows(camera,facing,p,s,time,partial);
        RenderSystem.depthMask(true);RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();
        RenderSystem.getModelViewStack().popMatrix();RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
    }
    private HushRenderer(){}
}
