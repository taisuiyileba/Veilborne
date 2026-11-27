package com.suiyi.veilborne.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.suiyi.veilborne.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Screen-space touches: the observation prism held to the eye (a faceted rim with a rainbow edge, and a closer view),
 * the flash of stepping through the mirror door, and the little life of the late traveller and the echo mirror.
 */
public final class MirrorOverlay {
    private static final ResourceLocation SCOPE=Veilborne.id("textures/misc/prism_scope.png");
    private static float scope;
    @SubscribeEvent public static void gui(RenderGuiEvent.Post e){
        var mc=Minecraft.getInstance();
        var g=e.getGuiGraphics();
        int w=g.guiWidth(),h=g.guiHeight();
        float target=MirrorClient.scoping()?1:0;
        scope+=(target-scope)*.25f;
        if(scope>.01f){
            RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1,1,1,Math.min(1,scope));
            int size=Math.max(w,h);
            g.blit(SCOPE,(w-size)/2,(h-size)/2,size,size,0,0,512,512,512,512);
            RenderSystem.setShaderColor(1,1,1,1);
        }
        if(MirrorClient.flash>.01f){
            float f=MirrorClient.flash;
            int a=(int)(Mth.clamp(f*1.3f,0,1)*230);
            g.fill(0,0,w,h,a<<24|0xe8f3fb);
        }
    }
    @SubscribeEvent public static void fov(ComputeFovModifierEvent e){
        if(MirrorClient.scoping())e.setNewFovModifier(e.getNewFovModifier()*Mth.lerp(scope,1f,.62f));
    }
    /** Motes at the traveller's feet while it walks, and a thread of them toward what it points at. */
    static void traveler(MirrorTraveler t){
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.isPaused())return;
        var r=mc.level.random;
        float a=t.visibility(0);
        if(r.nextFloat()<.35f*a)mc.level.addParticle(Veilborne.MIRROR_MOTE.get(),t.getX()+(r.nextDouble()-.5)*.5,t.getY()+.05,t.getZ()+(r.nextDouble()-.5)*.5,(r.nextDouble()-.5)*.004,.008,(r.nextDouble()-.5)*.004);
        if(t.state()==MirrorTraveler.State.POINT&&!t.target().equals(net.minecraft.core.BlockPos.ZERO)&&t.clientTicks()%3==0){
            var to=net.minecraft.world.phys.Vec3.atCenterOf(t.target()).subtract(t.position());
            double d=Math.min(8,to.length());var n=to.normalize();
            double s=r.nextDouble()*d;
            mc.level.addParticle(Veilborne.MIRROR_MOTE.get(),t.getX()+n.x*s,t.getY()+1.3+n.y*s*.2,t.getZ()+n.z*s,n.x*.02,.004,n.z*.02);
        }
    }
    private MirrorOverlay(){}
}
