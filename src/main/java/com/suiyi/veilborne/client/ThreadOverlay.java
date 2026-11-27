package com.suiyi.veilborne.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.suiyi.veilborne.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * 缝合一个瞬间, on the screen: in the seconds before the pull the finished frame is copied, at half size, every few ticks into a
 * small ring; when the thread is pulled tight the older copies are laid back over the present, each a little warmer and a
 * little displaced, like layers of film slipping in a gate, and then they slide apart and fade. Nothing in the world moves.
 */
final class ThreadOverlay {
    private static final int LAYERS=3,EVERY=12;
    private static final RenderTarget[] ring=new RenderTarget[LAYERS];
    private static final int[] stamp=new int[LAYERS];
    private static int next,lastCapture=-100;

    static void level(float partial){
        if(!ThreadClient.active()||!TideConfig.THREAD_MOMENT.get()||ThreadClient.snipped()){return;}
        double p=ThreadClient.progress(partial),q=ThreadTimeline.sew(p);
        if(p<ThreadTimeline.SEW_START||q<ThreadTimeline.MOMENT0-.035||q>ThreadTimeline.MOMENT1)return;
        var mc=Minecraft.getInstance();
        double m=ThreadTimeline.moment(p)*ThreadClient.strength()*(.55+.45*TideConfig.BRIGHTNESS.get());
        // Keep a few frames from just before the pull, and keep refreshing only until it lands.
        if(q<ThreadTimeline.MOMENT0+.004&&ThreadClient.ticks-lastCapture>=EVERY)capture(mc);
        if(m<.01)return;
        var main=mc.getMainRenderTarget();
        main.bindWrite(false);
        RenderSystem.disableDepthTest();RenderSystem.depthMask(false);
        RenderSystem.enableBlend();RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        double drift=(q-ThreadTimeline.MOMENT0)/(ThreadTimeline.MOMENT1-ThreadTimeline.MOMENT0);
        for(int k=0;k<LAYERS;k++){
            int i=Math.floorMod(next-1-k,LAYERS);
            if(ring[i]==null||stamp[i]==0)continue;
            float a=(float)(m*(.34-.08*k));
            float dx=(float)((k+1)*.006*drift*(k%2==0?1:-1)),dy=(float)((k+1)*.004*drift);
            float r=1f,g=(float)(.78-.06*k),b=(float)(.66-.1*k);
            RenderSystem.setShaderTexture(0,ring[i].getColorTextureId());
            BufferBuilder buf=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
            quad(buf,dx,dy,r,g,b,a);
            var ortho=new org.joml.Matrix4f().setOrtho(0,1,0,1,-1,1);
            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(ortho,VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.getModelViewStack().pushMatrix();RenderSystem.getModelViewStack().identity();RenderSystem.applyModelViewMatrix();
            BufferUploader.drawWithShader(buf.buildOrThrow());
            RenderSystem.getModelViewStack().popMatrix();RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
        RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();RenderSystem.enableDepthTest();RenderSystem.depthMask(true);
    }
    private static void quad(BufferBuilder b,float dx,float dy,float r,float g,float bl,float a){
        // A frame line crossing slowly, where one layer's gate slips against the next.
        b.addVertex(dx,dy,0).setUv(0,0).setColor(r,g,bl,a);
        b.addVertex(1+dx,dy,0).setUv(1,0).setColor(r,g,bl,a);
        b.addVertex(1+dx,1+dy,0).setUv(1,1).setColor(r,g,bl,a);
        b.addVertex(dx,1+dy,0).setUv(0,1).setColor(r,g,bl,a);
    }
    private static void capture(Minecraft mc){
        var main=mc.getMainRenderTarget();
        int w=Math.max(1,main.width/2),h=Math.max(1,main.height/2);
        RenderTarget t=ring[next];
        if(t==null){t=new TextureTarget(w,h,false,Minecraft.ON_OSX);t.setFilterMode(GL11.GL_LINEAR);ring[next]=t;}
        else if(t.width!=w||t.height!=h)t.resize(w,h,Minecraft.ON_OSX);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,t.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0,0,main.width,main.height,0,0,w,h,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_LINEAR);
        main.bindWrite(false);
        stamp[next]=ThreadClient.ticks;next=(next+1)%LAYERS;lastCapture=ThreadClient.ticks;
    }
    private ThreadOverlay(){}
}
