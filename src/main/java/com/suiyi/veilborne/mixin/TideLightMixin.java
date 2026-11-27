package com.suiyi.veilborne.mixin;

import com.suiyi.veilborne.TideConfig;
import com.suiyi.veilborne.MoonTimeline;
import com.suiyi.veilborne.TideTimeline;
import com.suiyi.veilborne.client.MoonClient;
import com.suiyi.veilborne.client.TideClient;
import com.suiyi.veilborne.client.MirrorHooks;
import com.suiyi.veilborne.MirrorTimeline;
import com.suiyi.veilborne.ThreadTimeline;
import com.suiyi.veilborne.client.ThreadHooks;
import com.suiyi.veilborne.client.HushHooks;
import com.suiyi.veilborne.HushTimeline;
import com.suiyi.veilborne.DreamTimeline;
import com.suiyi.veilborne.client.DreamHooks;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LightTexture.class)
public abstract class TideLightMixin {
    @ModifyArgs(method="updateLightTexture",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/platform/NativeImage;setPixelRGBA(III)V"),require=0)
    private void veilborne$coolSkylight(Args args){
        if(!TideConfig.LIGHT.get())return;
        if(TideClient.strength()>=.001){
            int block=args.get(0),sky=args.get(1),color=args.get(2);
            double blend=TideClient.strength()*TideTimeline.sea(TideClient.progress(1))*.88*(sky/15.0)*Math.pow(1-block/15.0,2);
            int r=(int)((color&255)*(1-.55*blend));
            int g=(int)((color>>8&255)*(1-.27*blend));
            int b=Math.min(255,(int)((color>>16&255)*(1-.10*blend)+12*blend));
            args.set(2,0xff000000|b<<16|g<<8|r);
        }
        if(MoonClient.active()){
            int block=args.get(0),sky=args.get(1),color=args.get(2);
            double p=MoonClient.progress(1);
            double w=MoonClient.strength()*MoonTimeline.veil(p)*(sky/15.0)*Math.pow(1-block/15.0,2);
            // Daylight dims toward a moonlit dusk; the bare core brightens the night a little at the molt.
            double dim=1-.42*MoonClient.dayness(1)*w,lift=.1*MoonTimeline.core(p)*w*(1-MoonClient.dayness(1));
            int r=(int)Math.min(255,(color&255)*dim*(1-.2*w)+255*lift*.8);
            int g=(int)Math.min(255,(color>>8&255)*dim*(1-.24*w)+255*lift*.75);
            int b=(int)Math.min(255,(color>>16&255)*dim*(1-.04*w)+10*w+255*lift);
            args.set(2,0xff000000|b<<16|g<<8|r);
        }
        if(MirrorHooks.active()){
            int block=args.get(0),sky=args.get(1),color=args.get(2);
            double p=MirrorHooks.progress();
            double w=MirrorHooks.strength()*MirrorTimeline.mirror(p)*(1-.7*MirrorTimeline.shatter(p))*(sky/15.0)*Math.pow(1-block/15.0,2);
            // The mirror gives back a pale, cool light: nights are a little brighter and daylight a little colder.
            double day=MirrorHooks.dayness(),lift=.15*w*(1-day);
            int r=(int)Math.min(255,(color&255)*(1-.13*w)+200*lift);
            int g=(int)Math.min(255,(color>>8&255)*(1-.05*w)+214*lift);
            int b=(int)Math.min(255,(color>>16&255)*(1+.03*w)+228*lift+6*w);
            args.set(2,0xff000000|Math.min(255,b)<<16|g<<8|r);
        }
        if(ThreadHooks.active()){
            int block=args.get(0),sky=args.get(1),color=args.get(2);
            double p=ThreadHooks.progress();
            double w=ThreadHooks.strength()*ThreadTimeline.veil(p)*(sky/15.0)*Math.pow(1-block/15.0,2);
            // Light through red cloth: the day goes dim and rust-coloured; the night takes a little of the wound's glow.
            double day=ThreadHooks.dayness(),dim=1-.38*day*w,glow=(.06+.08*ThreadTimeline.tug(p))*w*(1-day);
            int r=(int)Math.min(255,(color&255)*dim*(1+.02*w)+255*glow);
            int g=(int)Math.min(255,(color>>8&255)*dim*(1-.34*w)+60*glow);
            int b=(int)Math.min(255,(color>>16&255)*dim*(1-.42*w)+50*glow);
            args.set(2,0xff000000|b<<16|g<<8|r);
        }
        if(DreamHooks.active()){
            int block=args.get(0),sky=args.get(1),color=args.get(2);
            double p=DreamHooks.progress(),day=DreamHooks.dayness();
            double w=DreamHooks.strength()*DreamTimeline.light(p)*(sky/15.0)*Math.pow(1-block/15.0,2)*(1-.6*DreamTimeline.veinsFade(p));
            // Under the canopy the daylight comes down green and mottled; at night it is a little brighter and jade.
            double dim=1-.3*day*w,lift=(.1+.05*DreamTimeline.gold(p))*w*(1-day);
            int r=(int)Math.min(255,(color&255)*dim*(1-.42*w)+160*lift*.7);
            int g=(int)Math.min(255,(color>>8&255)*dim*(1-.06*w)+230*lift*.8);
            int b=(int)Math.min(255,(color>>16&255)*dim*(1-.3*w)+180*lift*.75);
            args.set(2,0xff000000|b<<16|g<<8|r);
        }
        if(HushHooks.active()){
            int block=args.get(0),sky=args.get(1),color=args.get(2);
            double p=HushHooks.progress(),s=sky/15.0;
            // A daylight with no source: shadows go shallow, night becomes the same pale day, and while the black sun covers
            // the sun the light goes flat and bright instead of dark. Lamps keep their own warmth.
            double w=HushHooks.strength()*HushTimeline.pale(p)*Math.pow(s,.35)*Math.pow(1-block/15.0,1.5);
            double cover=HushHooks.covered();
            double level=((.44+.46*s)*(1-cover)+(.8+.16*s)*cover)*255;
            int r=(int)Math.min(255,Mth.lerp(w,color&255,level)),g=(int)Math.min(255,Mth.lerp(w,color>>8&255,level)),b=(int)Math.min(255,Mth.lerp(w,color>>16&255,level*1.01));
            args.set(2,0xff000000|b<<16|g<<8|r);
        }
    }
}
