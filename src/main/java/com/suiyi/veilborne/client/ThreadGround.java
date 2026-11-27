package com.suiyi.veilborne.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.suiyi.veilborne.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * 绯线缝世 on the land, the walls and the air near you: the warned rings where the needle will come down and the band that
 * leaves them when it does, the seam it sews across the land, the hand's shadow going over, gold seams opening in rock and
 * bark and being stitched shut, threads humming under the beams indoors, crimson light at the windows, the unsewn pocket and
 * the old things floating in it, the needle walker's knot and its three loose ends, thread ends coming down, the keepsake,
 * and the ring of stillness round a tuning anchor. Also the moment's afterimages, drawn with the entities.
 */
final class ThreadGround {
    private static final ResourceLocation SIGIL=Veilborne.id("textures/environment/thread_sigil.png"),RING=Veilborne.id("textures/environment/thread_ring.png"),
        HAND=Veilborne.id("textures/environment/thread_hand.png"),KNOT=Veilborne.id("textures/environment/thread_knot.png"),
        LIGHTFALL=Veilborne.id("textures/environment/lightfall.png"),MOON_RING=Veilborne.id("textures/environment/moon_ring.png");
    private static final Matrix4f I=new Matrix4f();
    private static final int SCARLET=ThreadRenderer.SCARLET,GOLD=ThreadRenderer.GOLD;

    static void render(Vec3 camera,Quaternionf facing,double p,double s,double time,float partial){
        var st=ThreadClient.state;
        double bright=s*TideConfig.BRIGHTNESS.get();
        warnings(camera,facing,st,p,bright,time,partial);
        landSeam(camera,st,p,bright,time);
        handShadow(camera,p,s);
        if(ThreadLines.shader!=null){
            ThreadLines L=ThreadLines.begin();
            faces(L,camera,p,bright,time);
            gossamer(L,camera,p,bright,time);
            anchors(L,camera,p,bright,time);
            beams(L,camera,p,bright,time);
            walkerKnots(L,camera,time,partial);
            threadEnds(L,camera,time,partial);
            L.end(new Matrix4f(RenderSystem.getModelViewMatrix()),RenderSystem.getProjectionMatrix());
        }
        if(!sparks.isEmpty()){
            TideFx fx=TideFx.begin(ThreadRenderer.STAR,I);
            for(int i=0;i<sparks.size();i++){
                fx.billboard(sparks.get(i),facing,.16+.04*Math.sin(time*11+i),time*2+i,0xffe0a0,sparkAlpha.get(i));
                fx.billboard(sparks.get(i),facing,.5,-time+i,0xffa040,sparkAlpha.get(i)*.35);
            }
            fx.end(true);
        }
        walkerGlow(camera,facing,time,partial);
        windows(camera,p,bright,time);
        pocket(camera,facing,st,p,s,time,partial);
        keepsake(camera,facing,st,s,time,partial);
    }

    // ------------------------------------------------------------------ 落针区
    private static void warnings(Vec3 camera,Quaternionf facing,ThreadPacket st,double p,double b,double time,float partial){
        float now=ThreadClient.ageNow(partial);
        for(int i=0;i<ThreadData.STRIKES;i++){
            BlockPos g=st.strike(i);
            if(g.equals(BlockPos.ZERO))continue;
            int at=ThreadTimeline.strikeTick(i,st.duration());
            zone(camera,facing,Vec3.atBottomCenterOf(g),ThreadTimeline.STRIKE_RANGE,(now-(at-ThreadTimeline.STRIKE_WARN))/ThreadTimeline.STRIKE_WARN,now-at,b,time,p);
        }
        if(st.hasLand()){
            Vec3 a=st.footA(),bb=st.footB();
            for(int f=0;f<2;f++){
                int at=ThreadTimeline.landTick(f==1,a,bb,st.duration()),from=Math.min(ThreadTimeline.landWarnTick(st.duration()),at-ThreadTimeline.LAND_WARN_TICKS);
                zone(camera,facing,f==0?a:bb,ThreadTimeline.LAND_RANGE,(now-from)/(double)(at-from),now-at,b,time,p);
            }
        }
    }
    /** One warned ring: broad sigil lines that turn faster and redden as it builds; then the needle, a shock band, a scorch. */
    private static void zone(Vec3 camera,Quaternionf facing,Vec3 c,double range,double warn,double since,double b,double time,double p){
        if(c.distanceTo(camera)>160)return;
        Vec3 v=c.subtract(camera).add(0,.06,0);
        if(since<0&&warn>=0){
            double w=Mth.clamp(warn,0,1);
            int col=ThreadRenderer.mix(0xffc86a,0xff3a3a,w);
            double beat=.7+.3*Math.sin(time*(3+w*14));
            TideFx sig=TideFx.begin(SIGIL,I);
            sig.flat(v,range,time*(.15+w*.9),0,0,1,1,col,b*TideTimeline.smooth(0,.12,w)*beat*.95);
            sig.flat(v.add(0,.01,0),range*(1-w*.55),-time*(.3+w),0,0,1,1,0xff7a5a,b*w*.55);
            sig.end(true);
            TideFx glow=TideFx.begin(ThreadRenderer.GLOW,I);
            glow.flat(v.add(0,.02,0),range*1.25,0,0,0,1,1,0xa81c20,b*(.25+.4*w));
            glow.end(true);
            if(w>.75){
                // The needle's light is already on its way down: a column gathers over the middle.
                TideFx col2=TideFx.begin(LIGHTFALL,I);
                col2.column(v,1.2+2*(w-.75),40,14,(float)(time*1.4),0xffc070,b*(w-.75)*2.4);
                col2.end(true);
            }
        }else if(since>=0&&since<400){
            double t=since/20.0;
            TideFx ring=TideFx.begin(RING,I);
            double r=1+t*18;
            if(t<1.2)ring.flat(v,r,0,0,0,1,1,0xffb070,b*(1-t/1.2)*1.2);
            ring.end(true);
            TideFx knot=TideFx.begin(KNOT,I);
            knot.flat(v,1.6,time*.1,0,0,1,1,SCARLET,b*(1-TideTimeline.smooth(8,20,t))*(1-ThreadTimeline.fall(p))*.9+b*.3*ThreadTimeline.tug(p));
            knot.end(true);
            if(t<1){
                TideFx flash=TideFx.begin(ThreadRenderer.GLOW,I);
                flash.flat(v,range*1.6*(1-t*.5),0,0,0,1,1,0xffd090,b*(1-t)*.9);
                flash.billboard(v.add(0,2,0),facing,6*(1-t)+2,0,0xffe0b0,b*(1-t));
                flash.end(true);
            }
        }
    }

    // ------------------------------------------------------------------ the seam sewn across the land
    private static void landSeam(Vec3 camera,ThreadPacket st,double p,double b,double time){
        if(!st.hasLand()||ThreadClient.snipped()||ThreadLines.shader==null)return;
        Vec3 a=st.footA(),bb=st.footB();
        double show=TideTimeline.smooth(ThreadTimeline.strikeB(a,bb),ThreadTimeline.strikeB(a,bb)+.01,p)*(1-TideTimeline.smooth(ThreadTimeline.CUT+.02,ThreadTimeline.CUT+.06,p));
        if(show<.01)return;
        var level=Minecraft.getInstance().level;
        double tug=ThreadTimeline.tug(p),len=a.distanceTo(bb);
        ThreadLines L=ThreadLines.begin();
        List<Vec3> core=new ArrayList<>();
        Vec3 dir=bb.subtract(a).normalize(),side=new Vec3(-dir.z,0,dir.x);
        int n=(int)Math.min(160,len/1.5);
        for(int i=0;i<=n;i++){
            double t=i/(double)n;
            Vec3 at=a.lerp(bb,t);
            if(at.distanceTo(camera)>72){if(core.size()>1)L.strip(core,GOLD,b*show*(.7+tug),.0012,.03,false);core=new ArrayList<>();continue;}
            double jag=(Math.sin(t*len*.9)*.35+Math.sin(t*len*2.7+1.3)*.18)*(1-Math.abs(2*t-1)*.4);
            Vec3 on=at.add(side.scale(jag));
            int y=level.hasChunk(Mth.floor(on.x)>>4,Mth.floor(on.z)>>4)?Surface.top(level,Mth.floor(on.x),Mth.floor(on.z)):(int)on.y;
            core.add(new Vec3(on.x,y+.04,on.z).subtract(camera));
            // Stitches across it: the land's cloth drawn together.
            if(i%4==0){
                Vec3 c=new Vec3(on.x,y+.05,on.z);
                L.segment(c.add(side.scale(-.55)).add(dir.scale(-.2)).subtract(camera),c.add(side.scale(.55)).add(dir.scale(.2)).subtract(camera),SCARLET,b*show*(.9+tug),b*show*(.9+tug),.0012,.035,false);
            }
        }
        if(core.size()>1)L.strip(core,GOLD,b*show*(.7+tug),.0012,.03,false);
        L.end(new Matrix4f(RenderSystem.getModelViewMatrix()),RenderSystem.getProjectionMatrix());
    }

    // ------------------------------------------------------------------ the hand's shadow over the land
    private static void handShadow(Vec3 camera,double p,double s){
        double strength=ThreadClient.snipped()?0:ThreadRenderer.handStrength(p)*s;
        if(strength<.01)return;
        var st=ThreadClient.state;var level=Minecraft.getInstance().level;
        Vec3 goal=st.hasLand()?st.footA().lerp(st.footB(),.5):camera;
        var seam=ThreadClient.seam();
        Vec3 away=new Vec3(seam.n().x,0,seam.n().z).normalize();
        double h=Mth.clamp(ThreadTimeline.hand(p),0,1),t=TideTimeline.smooth(0,1,h);
        Vec3 palm=goal.add(away.scale(260*(1-t)));
        Vec3 fwd=away.scale(-1),across=new Vec3(-fwd.z,0,fwd.x);
        double W=190,Lh=230;
        Vec3 origin=palm.add(fwd.scale(Lh*.25));
        if(origin.distanceTo(camera)>Lh+150)return;
        RenderSystem.setShader(()->SeaSky.shader!=null?SeaSky.shader:GameRenderer.getPositionTexColorShader());
        RenderSystem.setShaderTexture(0,HAND);
        RenderSystem.enableBlend();RenderSystem.blendFunc(GlStateManager.SourceFactor.ZERO,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        BufferBuilder buf=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        int nu=26,nv=30;
        double[][] ys=new double[nu+1][nv+1];Vec3[][] at=new Vec3[nu+1][nv+1];
        for(int i=0;i<=nu;i++)for(int j=0;j<=nv;j++){
            Vec3 q=origin.add(across.scale((i/(double)nu-.5)*W)).add(fwd.scale((j/(double)nv-.5)*Lh));
            at[i][j]=q;
            ys[i][j]=level.hasChunk(Mth.floor(q.x)>>4,Mth.floor(q.z)>>4)?Surface.top(level,Mth.floor(q.x),Mth.floor(q.z))+.08:camera.y-2;
        }
        float a=(float)(.72*strength);
        for(int i=0;i<nu;i++)for(int j=0;j<nv;j++){
            int[][] k={{i,j},{i+1,j},{i+1,j+1},{i,j+1}};
            for(int[] v:k){
                Vec3 q=at[v[0]][v[1]];
                if(q.distanceTo(camera)>200)continue;
                buf.addVertex((float)(q.x-camera.x),(float)(ys[v[0]][v[1]]-camera.y),(float)(q.z-camera.z)).setUv(v[0]/(float)nu,1-v[1]/(float)nv).setColor(1f,1f,1f,a);
            }
        }
        MeshData mesh=buf.build();
        if(mesh!=null)BufferUploader.drawWithShader(mesh);
        RenderSystem.defaultBlendFunc();
    }

    // ------------------------------------------------------------------ gold seams in rock and bark
    /** Where a stitch is running along a seam in the rock this frame (camera-relative), and how bright. */
    private static final List<Vec3> sparks=new ArrayList<>();
    private static final List<Double> sparkAlpha=new ArrayList<>();
    private static void faces(ThreadLines L,Vec3 camera,double p,double b,double time){
        sparks.clear();sparkAlpha.clear();
        double k=TideTimeline.smooth(.1,.16,p)*(1-TideTimeline.smooth(.8,.86,p));
        if(k<.01)return;
        for(ThreadClient.Face f:ThreadClient.faces){
            double cyc=(time*20-f.start())/ThreadClient.FACE_LIFE;
            if(cyc<0||cyc>1)continue;
            double appear=TideTimeline.smooth(0,.2,cyc),sewn=TideTimeline.smooth(.26,.72,cyc),gone=TideTimeline.smooth(.76,1,cyc);
            Vec3 n=Vec3.atLowerCornerOf(f.out().getNormal()),across=new Vec3(-n.z,0,n.x);
            Vec3 c=Vec3.atCenterOf(f.pos()).add(n.scale(.503));
            long h=f.pos().asLong()*31+f.out().ordinal();
            List<Vec3> crack=new ArrayList<>();
            double x0=((h>>3)&7)/8.0-.45,x1=((h>>7)&7)/8.0-.45;
            int segs=5*f.length()+1;
            for(int i=0;i<=segs;i++){
                double t=i/(double)segs;
                double jag=((h*(i+3)>>(i%9*3+11))&7)/7.0-.5;
                double x=Mth.lerp(t,x0,x1)+jag*.18*(i==0||i==segs?0:1),y=.46-t*(f.length()-.08);
                crack.add(c.add(across.scale(x)).add(0,y,0).subtract(camera));
            }
            int shown=Math.max(1,(int)Math.round(appear*segs));
            double a=b*k*(1-gone);
            L.strip(crack.subList(0,shown+1),GOLD,a*(1-.4*sewn)*1.2,.0012,.02,false);
            L.strip(crack.subList(0,shown+1),0xffa040,a*(1-.4*sewn)*.35,.004,.07,false);
            // A tiny scarlet stitch zips along it, and behind the zip the seam is shut.
            int sewnTo=(int)Math.round(sewn*segs);
            if(sewn>0&&sewn<1){
                double at=sewn*segs;int i0=Math.min(segs-1,(int)at);
                sparks.add(crack.get(i0).lerp(crack.get(i0+1),at-i0).add(n.scale(.03)));sparkAlpha.add(a);
            }else if(appear<1){
                sparks.add(crack.get(shown).add(n.scale(.03)));sparkAlpha.add(a*.7);
            }
            for(int i=0;i<sewnTo&&i<segs;i++){
                Vec3 m=crack.get(i).lerp(crack.get(i+1),.5);
                Vec3 dd=crack.get(i+1).subtract(crack.get(i)).normalize().cross(n).normalize().scale(.07);
                L.segment(m.subtract(dd.scale(1.4)),m.add(dd.scale(1.4)),SCARLET,a,a,.0011,.016,false);
            }
        }
    }

    // ------------------------------------------------------------------ 引线: red gossamer in high places
    private static void gossamer(ThreadLines L,Vec3 camera,double p,double b,double time){
        double k=(ThreadTimeline.omen(p)*(1-.55*ThreadTimeline.tear(p))+.25*ThreadTimeline.tug(p))*(1-ThreadTimeline.fall(p)*.8);
        if(k<.01)return;
        Vec3 lean=new Vec3(0,0,0);
        var seam=ThreadClient.seam();
        if(seam!=null){Vec3 m=ThreadTimeline.middle(seam);lean=new Vec3(m.x-camera.x,0,m.z-camera.z).normalize().scale(.35+.8*ThreadTimeline.pull(p));}
        for(ThreadClient.Gossamer g:ThreadClient.gossamer){
            List<Vec3> pts=new ArrayList<>();
            for(int i=0;i<=12;i++){
                double t=i/12.0,ph=time*1.1+g.phase();
                Vec3 at;
                if(g.hanging())at=g.a().lerp(g.b(),t).add(lean.scale(t*t)).add(Math.sin(ph+t*2)*.08*t,0,Math.cos(ph*.8+t*2)*.08*t);
                else at=g.a().lerp(g.b(),t).add(0,-Math.sin(Math.PI*t)*(.6+.1*Math.sin(ph)),0).add(lean.scale(Math.sin(Math.PI*t)*.4));
                pts.add(at.subtract(camera));
            }
            L.strip(pts,SCARLET,Math.min(1,b*k*1.3),.0013,.014,false);
            // A bead of light caught at the loose end, turning as the thread sways.
            if(g.hanging()){sparks.add(pts.get(pts.size()-1));sparkAlpha.add(Math.min(1,b*k)*(.55+.45*Math.sin(time*2.3+g.phase())));}
        }
    }

    // ------------------------------------------------------------------ the still ring round a tuning anchor
    private static void anchors(ThreadLines L,Vec3 camera,double p,double b,double time){
        double k=ThreadTimeline.veil(p)*b;
        if(k<.01||ThreadClient.anchors.isEmpty())return;
        for(BlockPos a:ThreadClient.anchors){
            Vec3 c=Vec3.atBottomCenterOf(a);
            List<Vec3> ring=new ArrayList<>();
            var level=Minecraft.getInstance().level;
            for(int i=0;i<=48;i++){
                double ang=i/48.0*Math.PI*2+time*.05;
                double x=c.x+Math.cos(ang)*ThreadService.ANCHOR_RANGE,z=c.z+Math.sin(ang)*ThreadService.ANCHOR_RANGE;
                int y=Surface.top(level,Mth.floor(x),Mth.floor(z));
                ring.add(new Vec3(x,Math.abs(y-c.y)<4?y+.05:c.y+.05,z).subtract(camera));
            }
            L.strip(ring,GOLD,k*(.4+.2*ThreadTimeline.tug(p)*3),.0008,.02,false);
        }
    }

    // ------------------------------------------------------------------ indoors: threads humming under the beams
    private static void beams(ThreadLines L,Vec3 camera,double p,double b,double time){
        double k=ThreadTimeline.veil(p)*(.5+.5*ThreadTimeline.pull(p));
        if(k<.02||ThreadClient.beams.isEmpty())return;
        for(ThreadClient.Beam beam:ThreadClient.beams){
            List<Vec3> pts=new ArrayList<>();
            for(int i=0;i<=8;i++){
                double t=i/8.0;
                double hum=Math.sin(Math.PI*t)*Math.sin(time*(22+beam.phase()%7)+beam.phase())*(.012+.05*ThreadTimeline.tug(p));
                pts.add(beam.a().lerp(beam.b(),t).add(0,-.03+hum,0).subtract(camera));
            }
            L.strip(pts,SCARLET,b*k*(.35+.25*Math.sin(time*.7+beam.phase())),.0007,.006,false);
        }
    }

    // ------------------------------------------------------------------ indoors: crimson light at the windows
    private static void windows(Vec3 camera,double p,double b,double time){
        double veil=ThreadTimeline.veil(p)*b;
        if(veil<.01||ThreadClient.openings.isEmpty())return;
        TideFx glow=TideFx.begin(ThreadRenderer.GLOW,I);
        for(MoonClient.Opening o:ThreadClient.openings){
            Vec3 out=Vec3.atLowerCornerOf(o.out().getNormal()),across=new Vec3(-out.z,0,out.x);
            Vec3 sill=Vec3.atBottomCenterOf(o.inside()).add(out.scale(.45)).subtract(camera).add(0,.02,0);
            Vec3 in=out.scale(-2.6);
            double breathe=.8+.2*Math.sin(time*.4+o.inside().asLong()%11)+.3*ThreadTimeline.tug(p);
            glow.quad(sill.add(across.scale(.5)),sill.add(across.scale(.95)).add(in),sill.subtract(across.scale(.95)).add(in),sill.subtract(across.scale(.5)),0,0,1,1,0xc8323a,veil*.45*breathe);
        }
        glow.end(true);
    }

    // ------------------------------------------------------------------ the needle walker's knot
    private static void walkerKnots(ThreadLines L,Vec3 camera,double time,float partial){
        var mc=Minecraft.getInstance();
        for(Entity en:mc.level.entitiesForRendering()){
            if(!(en instanceof NeedleWalker w)||en.distanceToSqr(camera)>64*64)continue;
            Vec3 knot=Vec3.atBottomCenterOf(w.anchor()).add(0,.14,0);
            if(w.state()==NeedleWalker.State.KNOT||w.state()==NeedleWalker.State.ROAM){
                for(int loop=0;loop<2;loop++){
                    List<Vec3> pts=new ArrayList<>();
                    for(int i=0;i<=18;i++){double a=i/18.0*Math.PI*2;pts.add(knot.add(Math.cos(a+time*.4+loop)*.22,Math.sin(a*2+loop)*.1,Math.sin(a+time*.4+loop)*.22*(loop==0?1:.5)).subtract(camera));}
                    L.strip(pts,loop==0?SCARLET:GOLD,.9,.0012,.01,false);
                }
            }else if(w.state()==NeedleWalker.State.ALARM){
                for(int k=0;k<3;k++){
                    if((w.ends()&1<<k)==0)continue;
                    Vec3 e=w.endAt(k);
                    List<Vec3> curl=new ArrayList<>();
                    for(int i=0;i<=14;i++){double t=i/14.0,a=t*9+time*3+k;curl.add(e.add(Math.cos(a)*.25*(1-t),t*.5-.25+Math.sin(time*2+k)*.08,Math.sin(a)*.25*(1-t)).subtract(camera));}
                    L.strip(curl,SCARLET,1,.0014,.014,false);
                    List<Vec3> back=new ArrayList<>();
                    for(int i=0;i<=10;i++){double t=i/10.0;back.add(e.lerp(knot,t).add(0,-Math.sin(Math.PI*t)*.4,0).subtract(camera));}
                    L.strip(back,SCARLET,.35,.0008,.006,false);
                }
            }
        }
    }
    private static void walkerGlow(Vec3 camera,Quaternionf facing,double time,float partial){
        var mc=Minecraft.getInstance();TideFx fx=null;
        for(Entity en:mc.level.entitiesForRendering()){
            if(!(en instanceof NeedleWalker w)||en.distanceToSqr(camera)>64*64)continue;
            if(fx==null)fx=TideFx.begin(ThreadRenderer.STAR,I);
            Vec3 knot=Vec3.atBottomCenterOf(w.anchor()).add(0,.14,0).subtract(camera);
            if(w.state()==NeedleWalker.State.KNOT||w.state()==NeedleWalker.State.ROAM)fx.billboard(knot,facing,.35+.06*Math.sin(time*3),time,0xffc070,.8);
            if(w.state()==NeedleWalker.State.ALARM)for(int k=0;k<3;k++)if((w.ends()&1<<k)!=0)
                fx.billboard(w.endAt(k).subtract(camera),facing,.3+.1*Math.sin(time*8+k),time+k,0xffd090,1);
        }
        if(fx!=null)fx.end(true);
    }

    // ------------------------------------------------------------------ thread ends coming down
    private static void threadEnds(ThreadLines L,Vec3 camera,double time,float partial){
        var mc=Minecraft.getInstance();
        for(Entity en:mc.level.entitiesForRendering()){
            if(!(en instanceof ItemEntity item)||!item.getItem().is(Veilborne.SCARLET_THREAD.get())||en.distanceToSqr(camera)>48*48)continue;
            Vec3 c=item.getPosition(partial).add(0,.25,0);
            boolean falling=!item.onGround();
            List<Vec3> tail=new ArrayList<>();
            double len=falling?1.6:.5;
            for(int i=0;i<=10;i++){
                double t=i/10.0,ph=time*2.2+item.getId();
                tail.add(c.add(Math.sin(ph+t*4)*.18*t,t*len,Math.cos(ph*.8+t*3)*.18*t).subtract(camera));
            }
            L.strip(tail,SCARLET,falling?.95:.5,.0012,.012,false);
        }
    }

    // ------------------------------------------------------------------ the unsewn pocket
    private static final ItemStack[] RELICS={new ItemStack(Items.LOOM),new ItemStack(Items.BELL),new ItemStack(Items.CLOCK),new ItemStack(Items.LANTERN),new ItemStack(Items.SHEARS),
        new ItemStack(Items.GOAT_HORN),new ItemStack(Items.SPYGLASS),new ItemStack(Items.WRITABLE_BOOK),new ItemStack(Items.CANDLE),new ItemStack(Items.COMPASS),new ItemStack(Items.DECORATED_POT),new ItemStack(Items.BRUSH)};
    /** 0..1 open, and how far the anchor has gathered it (0..1), or -1 when it is not there. */
    static double[] pocketState(ThreadPacket st,double p,float partial){
        if(!st.hasPocket())return null;
        double since=(ThreadClient.ageNow(partial)-st.pocketAt())/20.0;
        double open=TideTimeline.smooth(0,3,since)*(1-TideTimeline.smooth(ThreadTimeline.POCKET_CLOSE-.01,ThreadTimeline.POCKET_CLOSE,p));
        double tune=st.has(ThreadData.TUNED)?Mth.clamp((ThreadClient.ageNow(partial)-st.tuneAt())/(double)ThreadService.TUNE_TICKS,0,1):0;
        if(st.has(ThreadData.SEWN_UP))open*=1-TideTimeline.smooth(1,1.08,tune+(ThreadClient.ageNow(partial)-st.tuneAt()-ThreadService.TUNE_TICKS)/200.0);
        return new double[]{open,tune};
    }
    private static void pocket(Vec3 camera,Quaternionf facing,ThreadPacket st,double p,double s,double time,float partial){
        double[] ps=pocketState(st,p,partial);
        if(ps==null||ps[0]<.01)return;
        Vec3 c=Vec3.atBottomCenterOf(st.pocket());
        if(c.distanceTo(camera)>96)return;
        Vec3 v=c.subtract(camera).add(0,.05,0);
        double a=ps[0]*s,tune=ps[1];
        // A still bubble of old light: its rim on the ground, a faint wall, dust that hangs without falling.
        TideFx rim=TideFx.begin(MOON_RING,I);
        rim.flat(v,ThreadService.POCKET_RANGE,time*.02,0,0,1,1,0xe6c48a,a*(.55+.3*tune));
        rim.end(true);
        TideFx wall=TideFx.begin(LIGHTFALL,I);
        wall.column(v,ThreadService.POCKET_RANGE,5.5,40,(float)(time*.03),0xd8b27a,a*.18*(1+tune));
        wall.end(true);
        TideFx dust=TideFx.begin(ThreadRenderer.STAR,I);
        for(int i=0;i<34;i++){
            double ang=i*2.399,r=Math.sqrt((i*37%100)/100.0)*ThreadService.POCKET_RANGE*.9,y=.3+(i*53%100)/100.0*3.6;
            Vec3 d=v.add(Math.cos(ang)*r,y,Math.sin(ang)*r);
            if(tune>0)d=d.lerp(Vec3.atCenterOf(st.anchor()).subtract(camera),TideTimeline.smooth(0,1,tune*1.3-(i%5)*.06));
            dust.billboard(d,facing,.05+.03*Math.sin(i),0,0xf0dcb0,a*.8*(.6+.4*Math.sin(time*.5+i)));
        }
        if(tune>0&&!st.has(ThreadData.SEWN_UP)){
            Vec3 an=Vec3.atCenterOf(st.anchor()).subtract(camera).add(0,.4,0);
            dust.billboard(an,facing,.6+.4*tune+.1*Math.sin(time*9),time,0xffd28a,a);
        }
        dust.end(true);
    }
    /** The old things floating in the pocket, drawn with the entities as sepia ghosts that turn and bob; the anchor draws them in. */
    static void entities(Vec3 camera,double p,double s,double time,float partial){
        var st=ThreadClient.state;
        moment(camera,p,s,partial);
        scrap(camera,st,s,time,partial);
        double[] ps=pocketState(st,p,partial);
        if(ps==null||ps[0]<.01)return;
        Vec3 c=Vec3.atBottomCenterOf(st.pocket());
        if(c.distanceTo(camera)>64)return;
        var mc=Minecraft.getInstance();
        var buffers=mc.renderBuffers().bufferSource();
        long seed=st.seed();
        for(int i=0;i<7;i++){
            ItemStack relic=RELICS[(int)Math.floorMod(seed+i*7L,RELICS.length)];
            double ang=i*2.399+Math.floorMod(seed,17L),r=1.2+(i*37%10)/10.0*2.4,y=1+(i*53%10)/10.0*1.8+Math.sin(time*.5+i)*.15;
            Vec3 at=c.add(Math.cos(ang)*r,y,Math.sin(ang)*r);
            double tune=ps[1];
            float scale=1;
            if(tune>0){
                double t=TideTimeline.smooth(0,1,tune*1.25-i*.04);
                at=at.lerp(Vec3.atCenterOf(st.anchor()).add(0,.4,0),t);scale=(float)(1-t);
            }
            if(scale<.02)continue;
            float alpha=(float)(ps[0]*s*(.45+.15*Math.sin(time*1.7+i*2.3))*(.8+.2*scale));
            PoseStack pose=new PoseStack();
            pose.translate(at.x-camera.x,at.y-camera.y,at.z-camera.z);
            pose.mulPose(Axis.YP.rotation((float)(time*.3+i)));
            pose.mulPose(Axis.XP.rotation((float)(Math.sin(time*.2+i)*.3)));
            pose.scale(.9f*scale,.9f*scale,.9f*scale);
            int color=0xffe2c28a;
            MultiBufferSource ghost=type->new Relic(buffers.getBuffer(RenderType.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS)),(int)(alpha*255)<<24|color&0xffffff,true);
            mc.getItemRenderer().renderStatic(relic,ItemDisplayContext.FIXED,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,pose,ghost,mc.level,i);
        }
        buffers.endBatch();
    }
    /** Keeps an item's own texture but turns it to old, warm, see-through light (sepia unless told not to), front faces only. */
    private record Relic(VertexConsumer base,int color,boolean sepia) implements VertexConsumer {
        @Override public void addVertex(float x,float y,float z,int argb,float u,float v,int overlay,int light,float nx,float ny,float nz){
            float d=(float)Math.sqrt(x*x+y*y+z*z);
            float cos=d<1e-4f?-1:(nx*x+ny*y+nz*z)/d;
            int a=cos>.05f?0:color>>>24;
            int r=(argb>>16&255)*(color>>16&255)/255,g=(argb>>8&255)*(color>>8&255)/255,b=(argb&255)*(color&255)/255;
            int grey=(r*3+g*6+b)/10;
            if(!sepia){base.addVertex(x,y,z).setColor(r,g,b,a).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xf000f0).setNormal(nx,ny,nz);return;}
            base.addVertex(x,y,z).setColor((grey+r)/2,(grey*2+g)/3,(grey+b*2)/4,a).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xf000f0).setNormal(nx,ny,nz);
        }
        @Override public VertexConsumer addVertex(float x,float y,float z){base.addVertex(x,y,z);return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){base.setColor(r*(color>>16&255)/255,g*(color>>8&255)/255,b*(color&255)/255,a*(color>>>24)/255);return this;}
        @Override public VertexConsumer setUv(float u,float v){base.setUv(u,v);return this;}
        @Override public VertexConsumer setUv1(int u,int v){base.setUv1(u,v);return this;}
        @Override public VertexConsumer setUv2(int u,int v){base.setUv2(240,240);return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){base.setNormal(x,y,z);return this;}
    }
    /** 缝合一个瞬间: while the thread is pulled, each person is followed by where they were a moment ago, twice. */
    private static void moment(Vec3 camera,double p,double s,float partial){
        double m=ThreadClient.snipped()?0:ThreadTimeline.moment(p)*s;
        if(m<.02||!TideConfig.THREAD_MOMENT.get())return;
        var mc=Minecraft.getInstance();
        var buffers=mc.renderBuffers().bufferSource();
        for(var pl:mc.level.players()){
            var q=ThreadClient.history.get(pl.getId());
            if(q==null||q.size()<24||!(pl instanceof AbstractClientPlayer who))continue;
            if(pl==mc.player&&mc.options.getCameraType().isFirstPerson())continue;
            for(int n=1;n<=3;n++){
                MoonClient.Frame f=MirrorClient.lagged(q,partial,n*7);
                var pose=new PoseStack();
                pose.translate(f.x()-camera.x,f.y()-camera.y,f.z()-camera.z);
                GhostFigure.renderFrame(who,pose,buffers,(float)(m*(.42-n*.1)),f,n==1?0xffb0a0:0xe8c890);
            }
        }
        buffers.endBatch();
    }

    // ------------------------------------------------------------------ the last scrap
    /** Where the keepsake is while it falls, camera-relative, and 0..1 how far it still has to fall. */
    private static Vec3 scrapAt(ThreadPacket st,double since,Vec3 camera){
        double fall=1-TideTimeline.smooth(0,60,since);
        double sway=Math.sin(since*.18)*2.5*fall;
        return Vec3.atBottomCenterOf(st.keepsake()).add(sway,.5+fall*fall*60,Math.cos(since*.15)*1.5*fall).subtract(camera);
    }
    /** The scrap itself: a little square of the night sky's cloth, see-through and softly lit, tumbling as a leaf does. */
    private static void scrap(Vec3 camera,ThreadPacket st,double s,double time,float partial){
        if(st.keepsake().equals(BlockPos.ZERO))return;
        double since=ThreadClient.ageNow(partial)-st.keepsakeAt();
        if(since<0||since>=60)return;
        var mc=Minecraft.getInstance();
        Vec3 at=scrapAt(st,since,camera);
        PoseStack pose=new PoseStack();
        pose.translate(at.x,at.y,at.z);
        pose.mulPose(Axis.YP.rotation((float)(since*.07)));
        pose.mulPose(Axis.XP.rotation((float)(Math.sin(since*.18)*.9)));
        pose.mulPose(Axis.ZP.rotation((float)(Math.cos(since*.13)*.5)));
        pose.scale(1.1f,1.1f,1.1f);
        var buffers=mc.renderBuffers().bufferSource();
        float alpha=(float)(s*TideTimeline.smooth(0,6,since)*.95);
        MultiBufferSource glow=type->new Relic(buffers.getBuffer(RenderType.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS)),(int)(alpha*255)<<24|0xfff0e0,false);
        mc.getItemRenderer().renderStatic(new ItemStack(Veilborne.ROBE_SCRAP_ITEM.get()),ItemDisplayContext.FIXED,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,pose,glow,mc.level,0);
        buffers.endBatch();
    }
    private static void keepsake(Vec3 camera,Quaternionf facing,ThreadPacket st,double s,double time,float partial){
        if(st.keepsake().equals(BlockPos.ZERO))return;
        double since=ThreadClient.ageNow(partial)-st.keepsakeAt();
        if(since<0||since>100)return;
        double fall=1-TideTimeline.smooth(0,60,since);
        Vec3 at=scrapAt(st,since,camera);
        double a=s*(1-TideTimeline.smooth(70,100,since));
        TideFx star=TideFx.begin(ThreadRenderer.STAR,I);
        star.billboard(at,facing,.35+fall*.5,time*.6,0xffe6b0,a*.8);
        star.billboard(at,facing,1.4+fall*1.5,-time*.3,0xe8283a,a*.3);
        star.end(true);
        TideFx trail=TideFx.begin(LIGHTFALL,I);
        trail.curtain(at.add(0,8,0),8,.25,Vec3.ZERO,(float)(time*.5),1,0xffd8a0,a*fall*.7,t->TideTimeline.smooth(0,1,t),0,time);
        trail.end(true);
    }
    private ThreadGround(){}
}
