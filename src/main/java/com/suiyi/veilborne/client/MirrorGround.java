package com.suiyi.veilborne.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.suiyi.veilborne.*;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * 镜天巡礼 on the ground and walls: still water showing the clear sky of afterwards, the extra door's light frame (and
 * the prism's hint of it), the watchtower's ghost through the prism, the pilgrim's passing shadow and the light it
 * lets down when it looks, the last shard falling, silver light at windows, the beasts' warning ring, mirror lamps
 * throwing their light one way, and the echo mirror's trail.
 */
final class MirrorGround {
    private static final ResourceLocation CLEAR=Veilborne.id("textures/environment/clear_sky.png"),DOOR=Veilborne.id("textures/environment/mirror_door.png"),
        SHADOW=Veilborne.id("textures/environment/pilgrim_shadow.png"),LIGHTFALL=Veilborne.id("textures/environment/lightfall.png"),
        FOOT=Veilborne.id("textures/environment/footprint.png"),RING=Veilborne.id("textures/environment/moon_ring.png");
    private static final Matrix4f I=new Matrix4f();

    static void render(Vec3 camera,Quaternionf facing,double p,double s,double time,float partial){
        var st=MirrorClient.state;
        double bright=s*TideConfig.BRIGHTNESS.get();
        if(TideConfig.GROUND.get())puddles(camera,p,s,time);
        door(camera,facing,st,p,s,time);
        if(MirrorClient.scoping())towerGhost(camera,s,time);
        shadow(camera,facing,p,s,time,partial);
        keepsake(camera,facing,st,s,time,partial);
        windows(camera,p,bright,time);
    }
    static void always(Vec3 camera,Quaternionf facing,double time,float partial){
        lamps(camera,facing,time);
        echoTrail(camera,time,partial);
    }

    // ------------------------------------------------------------------ 镜兆: water shows the sky of afterwards
    private static void puddles(Vec3 camera,double p,double s,double time){
        double k=MirrorTimeline.omen(p)*s;
        if(k<.01||MirrorClient.water.isEmpty())return;
        var level=Minecraft.getInstance().level;
        TideFx fx=TideFx.begin(CLEAR,I);
        for(BlockPos w:MirrorClient.water){
            float h=level.getFluidState(w).getHeight(level,w);
            double y=w.getY()+h+.004;
            double[][] c={{w.getX(),w.getZ()},{w.getX()+1,w.getZ()},{w.getX()+1,w.getZ()+1},{w.getX(),w.getZ()+1}};
            double dist=Math.hypot(w.getX()+.5-camera.x,w.getZ()+.5-camera.z);
            double a=k*.62*(1-TideTimeline.smooth(8,13,dist));
            if(a<.01)continue;
            for(double[] v:c){
                Vec3 rel=new Vec3(v[0]-camera.x,y-camera.y,v[1]-camera.z);
                Vec3 d=rel.normalize();
                // The reflected view ray, looked up in the clear sky panorama; wind ruffles it a little.
                double rx=d.x+Math.sin(time*.9+v[0]*1.7)*.012,ry=Math.abs(d.y),rz=d.z+Math.cos(time*.8+v[1]*1.9)*.012;
                float uu=(float)(Math.atan2(rz,rx)/(Math.PI*2)+.5+time*.0015),vv=(float)(Math.acos(Math.min(1,ry/Math.sqrt(rx*rx+ry*ry+rz*rz)))/(Math.PI/2));
                double fres=.45+.55*(1-ry);
                fx.vertex(rel.x,rel.y,rel.z,uu,Math.min(.995f,vv),0xffffff,a*fres);
            }
        }
        fx.end(false);
    }

    // ------------------------------------------------------------------ the extra door
    private static void door(Vec3 camera,Quaternionf facing,MirrorPacket st,double p,double s,double time){
        if(!st.hasDoor())return;
        boolean free=st.has(MirrorData.FREESTANDING);
        Vec3 n=MirrorTimeline.faceNormal(st.doorFace()),across=new Vec3(-n.z,0,n.x);
        Vec3 c=MirrorTimeline.doorCenter(st.door(),st.doorFace(),free).add(n.scale(free?0:.02)).subtract(camera);
        if(st.has(MirrorData.REVEALED)){
            double since=(MirrorClient.ageNow(0)-st.revealedAt())/20.0;
            double open=TideTimeline.smooth(0,2.5,since)*MirrorTimeline.door(p)*s;
            if(open<.01)return;
            boolean front=n.dot(c.scale(-1))>0||free;
            Vec3 r=across.scale(.55),up=new Vec3(0,1.05,0);
            // Inside the frame: a corridor of light running away into the glass.
            TideFx view=TideFx.begin(DOOR,I);
            if(front)view.quad(c.subtract(r).subtract(up),c.add(r).subtract(up),c.add(r).add(up),c.subtract(r).add(up),0,1,1,0,0xffffff,open*.95);
            if(free)view.quad(c.add(r).subtract(up),c.subtract(r).subtract(up),c.subtract(r).add(up),c.add(r).add(up),0,1,1,0,0xffffff,open*.95);
            view.end(false);
            TideFx glow=TideFx.begin(MirrorRenderer.GLOW,I);
            double breathe=.85+.15*Math.sin(time*1.3);
            Vec3 floor=c.subtract(0,1.05,0).add(n.scale(.8));
            glow.flat(floor.add(0,.02,0),1.6,0,0,0,1,1,0x8fd8e6,open*.4*breathe);
            for(int side=-1;side<=1;side+=2){
                Vec3 edge=c.add(across.scale(side*.58));
                glow.quad(edge.add(across.scale(-.12)).subtract(up.scale(1.08)),edge.add(across.scale(.12)).subtract(up.scale(1.08)),edge.add(across.scale(.12)).add(up.scale(1.08)),edge.add(across.scale(-.12)).add(up.scale(1.08)),0,.35f,1,.65f,0xffdda0,open*.5*breathe);
            }
            glow.quad(c.add(up.scale(1.02)).subtract(across.scale(.62)).add(0,-.1,0),c.add(up.scale(1.02)).add(across.scale(.62)).add(0,-.1,0),c.add(up.scale(1.02)).add(across.scale(.62)).add(0,.14,0),c.add(up.scale(1.02)).subtract(across.scale(.62)).add(0,.14,0),0,.35f,1,.65f,0xffdda0,open*.5*breathe);
            glow.end(true);
            TideFx motes=TideFx.begin(MirrorRenderer.STAR,I);
            for(int i=0;i<14;i++){
                double t=(time*.18+i*.071)%1;
                Vec3 m=c.add(across.scale(Math.sin(i*2.3+time*.4)*.5)).add(0,-1+t*2.2,0).add(n.scale(.1+t*.6));
                motes.billboard(m,facing,.06+.03*Math.sin(i+time*3),time+i,i%3==0?0xffe2b0:0xd6f6ff,open*Math.sin(t*Math.PI)*.8);
            }
            motes.end(true);
        }else if(MirrorClient.scoping()&&MirrorTimeline.door(p)>.5){
            // Through the prism: where the mirror's door would be, a faint outline trembles, brighter the closer you aim.
            var look=Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector();
            double aim=TideTimeline.smooth(Math.cos(Math.toRadians(26)),Math.cos(Math.toRadians(4)),c.normalize().dot(new Vec3(look.x,look.y,look.z)));
            double near=1-TideTimeline.smooth(14,24,c.length());
            double a=s*aim*near;
            if(a<.01)return;
            Vec3 r=across.scale(.56),up=new Vec3(0,1.06,0);
            TideFx fx=TideFx.begin(MirrorRenderer.GLOW,I);
            double jitter=Math.sin(time*23)*.012;
            for(int side=-1;side<=1;side+=2){
                Vec3 edge=c.add(across.scale(side*(.56+jitter)));
                fx.quad(edge.add(across.scale(-.05)).subtract(up),edge.add(across.scale(.05)).subtract(up),edge.add(across.scale(.05)).add(up),edge.add(across.scale(-.05)).add(up),0,.4f,1,.6f,0xbff4ff,a*.55);
            }
            fx.quad(c.add(up).subtract(r),c.add(up).add(r),c.add(up).add(r).add(0,.08,0),c.add(up).subtract(r).add(0,.08,0),0,.4f,1,.6f,0xbff4ff,a*.55);
            fx.end(true);
        }
    }
    /** Inside the gallery: the way back, a quiet frame of light on the entry wall. */
    static void exitDoor(Vec3 camera,BlockPos o,double time){
        Vec3 c=new Vec3(o.getX()+.5,o.getY()+1,o.getZ()+.02).subtract(camera);
        Vec3 r=new Vec3(.55,0,0),up=new Vec3(0,1.05,0);
        TideFx view=TideFx.begin(DOOR,I);
        view.quad(c.add(r).subtract(up),c.subtract(r).subtract(up),c.subtract(r).add(up),c.add(r).add(up),1,1,0,0,0xd8e8ff,.7);
        view.end(false);
        TideFx glow=TideFx.begin(MirrorRenderer.GLOW,I);
        glow.flat(c.subtract(0,1.03,0).add(0,0,.7),1.3,0,0,0,1,1,0x8fd8e6,.35+.08*Math.sin(time*1.3));
        glow.end(true);
    }

    // ------------------------------------------------------------------ the tower that is not there
    private static void towerGhost(Vec3 camera,double s,double time){
        if(MirrorExtras.TOWER.isEmpty())return;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE);
        BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_COLOR);
        TideMesh m=new TideMesh(b,new Matrix4f().translate((float)-camera.x,(float)-camera.y,(float)-camera.z));
        double pulse=.7+.3*Math.sin(time*2.2);
        for(BlockPos p:MirrorExtras.TOWER)m.box(p.getX()+.04,p.getY()+.04,p.getZ()+.04,.92,.92,.92,0x7fd9e8,.035*s*pulse);
        MeshData mesh=b.build();if(mesh!=null)BufferUploader.drawWithShader(mesh);
        RenderSystem.defaultBlendFunc();
    }

    // ------------------------------------------------------------------ the pilgrim's shadow and its look
    private static void shadow(Vec3 camera,Quaternionf facing,double p,double s,double time,float partial){
        MirrorClient.Pilgrim g=MirrorClient.pilgrim(partial);
        if(g==null)return;
        var level=Minecraft.getInstance().level;
        Vec3 d=MirrorTimeline.heading(g.yaw()),across=new Vec3(-d.z,0,d.x);
        double dist=Math.hypot(g.base().x-camera.x,g.base().z-camera.z);
        if(dist<120){
            double a=.58*g.visible()*s*(1-.2*g.gaze());
            RenderSystem.setShader(()->SeaSky.shader!=null?SeaSky.shader:GameRenderer.getPositionTexColorShader());
            RenderSystem.setShaderTexture(0,SHADOW);
            RenderSystem.enableBlend();RenderSystem.blendFunc(GlStateManager.SourceFactor.ZERO,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.disableCull();RenderSystem.depthMask(false);
            BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
            int nu=14,nv=20;double w=32*PilgrimModel.SCALE,l=46*PilgrimModel.SCALE;
            Vec3 origin=g.base().add(d.scale(3*PilgrimModel.SCALE));
            double[][] ys=new double[nu+1][nv+1];
            for(int i=0;i<=nu;i++)for(int j=0;j<=nv;j++){
                Vec3 at=origin.add(across.scale((i/(double)nu-.5)*w)).add(d.scale((j/(double)nv-.5)*l));
                ys[i][j]=level.hasChunk(Mth.floor(at.x)>>4,Mth.floor(at.z)>>4)?Surface.top(level,Mth.floor(at.x),Mth.floor(at.z))+.06:g.base().y;
            }
            for(int i=0;i<nu;i++)for(int j=0;j<nv;j++){
                int[][] k={{i,j},{i+1,j},{i+1,j+1},{i,j+1}};
                for(int[] v:k){
                    Vec3 at=origin.add(across.scale((v[0]/(double)nu-.5)*w)).add(d.scale((v[1]/(double)nv-.5)*l));
                    b.addVertex((float)(at.x-camera.x),(float)(ys[v[0]][v[1]]-camera.y),(float)(at.z-camera.z)).setUv(v[0]/(float)nu,1-v[1]/(float)nv).setColor(1f,1f,1f,(float)a);
                }
            }
            BufferUploader.drawWithShader(b.buildOrThrow());
            RenderSystem.defaultBlendFunc();RenderSystem.enableCull();
        }
        // It looks down: the light its mirror catches comes down the air to the one it looks at.
        if(g.gaze()>.02){
            Vec3 spot=Vec3.atBottomCenterOf(MirrorClient.state.pilgrimOrigin()).subtract(camera);
            double a=g.gaze()*s*TideConfig.BRIGHTNESS.get();
            Vec3 from=MirrorClient.reflect(MirrorRenderer.pilgrimPoint(g,PilgrimModel.mirrorCenter(g.s(),g.gaze())),camera);
            TideFx beam=TideFx.begin(MirrorRenderer.GLOW,I);
            for(int i=0;i<12;i++){
                // Faint far up, where the air is thick with it; strongest where it lands.
                double t0=i/12.0,t1=(i+1)/12.0,fade=TideTimeline.smooth(0,.5,t0)*(1-TideTimeline.smooth(.94,1,t1));
                Vec3 p0=from.lerp(spot,t0),p1=from.lerp(spot,t1);
                beam.ribbon(p0,p1,Vec3.ZERO,1.2+2.2*(1-t0),0xdff2ff,a*.16*fade);
                beam.ribbon(p0,p1,Vec3.ZERO,.35+.5*(1-t0),0xfff4dc,a*.22*fade);
            }
            beam.end(true);
            TideFx glow=TideFx.begin(MirrorRenderer.GLOW,I);
            glow.flat(spot.add(0,.05,0),4.5,0,0,0,1,1,0xc6e8f4,a*.5*(.85+.15*Math.sin(time*2)));
            glow.end(true);
            TideFx ring=TideFx.begin(RING,I);
            ring.flat(spot.add(0,.06,0),2.4,time*.2,0,0,1,1,0xffe4b0,a*.45);
            ring.end(true);
        }
    }

    // ------------------------------------------------------------------ the last shard
    private static void keepsake(Vec3 camera,Quaternionf facing,MirrorPacket st,double s,double time,float partial){
        if(st.keepsake().equals(BlockPos.ZERO))return;
        double since=MirrorClient.ageNow(partial)-st.keepsakeAt();
        if(since<0||since>90)return;
        double fall=1-TideTimeline.smooth(0,50,since);
        Vec3 at=Vec3.atBottomCenterOf(st.keepsake()).add(0,.5+fall*fall*70,0).subtract(camera);
        double a=s*(1-TideTimeline.smooth(60,90,since));
        TideFx trail=TideFx.begin(LIGHTFALL,I);
        trail.curtain(at.add(0,10,0),10,.35,Vec3.ZERO,(float)(time*.5),1,0xdff4ff,a*fall*.8,t->TideTimeline.smooth(0,1,t),0,time);
        trail.end(true);
        TideFx star=TideFx.begin(MirrorRenderer.STAR,I);
        star.billboard(at,facing,.5+fall*1.5,time*.8,0xf2fbff,a);
        star.billboard(at,facing,1.4+fall*3,-time*.3,0x9fdcea,a*.35);
        star.end(true);
    }

    // ------------------------------------------------------------------ indoors: silver light at windows
    private static void windows(Vec3 camera,double p,double bright,double time){
        double veil=MirrorTimeline.mirror(p)*(1-MirrorTimeline.shatter(p))*bright;
        if(veil<.01||MirrorClient.openings.isEmpty())return;
        TideFx glow=TideFx.begin(MirrorRenderer.GLOW,I);
        for(MoonClient.Opening o:MirrorClient.openings){
            Vec3 out=Vec3.atLowerCornerOf(o.out().getNormal()),across=new Vec3(-out.z,0,out.x);
            Vec3 sill=Vec3.atBottomCenterOf(o.inside()).add(out.scale(.45)).subtract(camera).add(0,.02,0);
            Vec3 in=out.scale(-2.4);
            double breathe=.85+.15*Math.sin(time*.5+o.inside().asLong()%11);
            glow.quad(sill.add(across.scale(.5)),sill.add(across.scale(.9)).add(in),sill.subtract(across.scale(.9)).add(in),sill.subtract(across.scale(.5)),0,0,1,1,0xb6d3e2,veil*.4*breathe);
            // A spot of reflected light that drifts on the wall beside the window, like light off water.
            double ph=o.inside().asLong()%89*.1;
            Vec3 wall=Vec3.atCenterOf(o.inside()).add(across.scale(.49)).subtract(camera).add(out.scale(-.6+Math.sin(time*.23+ph)*.3)).add(0,.4+Math.sin(time*.31+ph)*.25,0);
            glow.quad(wall.add(out.scale(-.18)).add(0,-.14,0),wall.add(out.scale(.18)).add(0,-.14,0),wall.add(out.scale(.18)).add(0,.14,0),wall.add(out.scale(-.18)).add(0,.14,0),0,0,1,1,0xdcefff,veil*.35*breathe);
        }
        glow.end(true);
    }

    // ------------------------------------------------------------------ the glass beasts' warning
    static void warning(Vec3 base,Vec3 camera,Quaternionf facing,double warning,boolean warned,double time,double s){
        Vec3 c=base.subtract(camera).add(0,.04,0);
        double pulse=warned?.55+.45*Math.sin(time*18):.25+.1*Math.sin(time*1.5);
        TideFx ring=TideFx.begin(MirrorRenderer.WARN,I);
        ring.flat(c,MirrorService.SHARD_RANGE,time*(warned?.4:.05),0,0,1,1,warned?0xf2fbff:0x9fc8d6,s*pulse*(warned?.9:.22));
        ring.end(true);
        if(!warned)return;
        TideFx glints=TideFx.begin(MirrorRenderer.STAR,I);
        for(int i=0;i<26;i++){
            double a=i*2.399+time*.1,r=1+((i*37)%10)/10.0*MirrorService.SHARD_RANGE*.9;
            double flick=Math.max(0,Math.sin(time*(9+i%5)+i*1.7));
            glints.billboard(c.add(Math.cos(a)*r,.05+.2*flick*warning,Math.sin(a)*r),facing,.08+.1*flick,time+i,i%4==0?0xffe6b0:0xe8faff,s*flick*(.4+.6*warning));
        }
        glints.end(true);
    }

    // ------------------------------------------------------------------ mirror lamps (any time)
    private static void lamps(Vec3 camera,Quaternionf facing,double time){
        if(MirrorClient.lanterns.isEmpty())return;
        var level=Minecraft.getInstance().level;
        TideFx glow=TideFx.begin(MirrorRenderer.GLOW,I);
        TideFx flare=TideFx.begin(MirrorRenderer.STAR,I);
        for(BlockPos pos:MirrorClient.lanterns){
            var st=level.getBlockState(pos);
            if(!st.is(Veilborne.MIRROR_LANTERN.get()))continue;
            Direction f=st.getValue(MirrorLanternBlock.FACING);
            Vec3 n=Vec3.atLowerCornerOf(f.getNormal()),across=new Vec3(-n.z,0,n.x);
            Vec3 lamp=Vec3.atBottomCenterOf(pos).add(0,.5,0).subtract(camera);
            double flick=.9+.1*Math.sin(time*7+pos.asLong()%13)+.05*Math.sin(time*13.7);
            // The pool it throws on the floor ahead, widening like a beam.
            for(int k=1;k<=4;k++){
                Vec3 spot=Vec3.atBottomCenterOf(pos).add(n.scale(.8+k*1.1)).subtract(camera);
                int floor=Surface.top(level,Mth.floor(spot.x+camera.x),Mth.floor(spot.z+camera.z));
                if(Math.abs(floor-pos.getY())>2)break;
                glow.flat(new Vec3(spot.x,floor-camera.y+.03,spot.z),.5+k*.32,0,0,0,1,1,0xffd9a0,.22*flick*(1-k*.18));
            }
            Vec3 to=lamp.scale(-1).normalize();
            double front=TideTimeline.smooth(.55,.95,to.dot(n));
            if(front>.01)flare.billboard(lamp.add(n.scale(.05)).add(0,.08,0),facing,.45+.35*front,time*.3,0xfff0cc,front*.8*flick);
        }
        glow.end(true);flare.end(true);
    }

    // ------------------------------------------------------------------ 回响镜
    private static void echoTrail(Vec3 camera,double time,float partial){
        double since=(MirrorClient.ticks+partial-MirrorClient.echoAt)/20.0;
        if(since<0||since>10||MirrorClient.echoTrail.isEmpty())return;
        var level=Minecraft.getInstance().level;
        List<Vec3> t=MirrorClient.echoTrail;
        double fade=TideTimeline.smooth(0,.6,since)*(1-TideTimeline.smooth(7.5,10,since));
        TideFx fx=TideFx.begin(FOOT,I);
        for(int i=1;i<t.size();i++){
            Vec3 a=t.get(i-1),b=t.get(i);
            if(a.distanceToSqr(b)<.04)continue;
            Vec3 d=b.subtract(a);double yaw=Math.atan2(d.z,d.x);
            Vec3 side=new Vec3(-d.z,0,d.x).normalize().scale((i&1)==0?.18:-.18);
            double reveal=TideTimeline.smooth(i/(double)t.size()*3,i/(double)t.size()*3+.5,since*1.2);
            double age=1-i/(double)t.size();
            double y=Surface.top(level,Mth.floor(b.x),Mth.floor(b.z));
            if(Math.abs(y-b.y)>1.5)y=b.y;
            fx.flat(new Vec3(b.x,y+.03,b.z).add(side).subtract(camera),.22,-yaw+Math.PI/2,0,0,1,1,0xd8f2ff,fade*reveal*(.35+.5*(1-age)));
        }
        fx.end(true);
    }
    /** The echo mirror's likenesses, drawn with the entities: every few seconds of the trail, you again. */
    static void echoGhosts(Vec3 camera,float partial,double time){
        double since=(MirrorClient.ticks+partial-MirrorClient.echoAt)/20.0;
        if(since<0||since>10||MirrorClient.echoTrail.size()<8)return;
        var mc=Minecraft.getInstance();
        if(!(mc.player instanceof AbstractClientPlayer me))return;
        double fade=TideTimeline.smooth(0,.6,since)*(1-TideTimeline.smooth(7.5,10,since));
        List<Vec3> t=MirrorClient.echoTrail;
        var buffers=mc.renderBuffers().bufferSource();
        for(int i=6;i<t.size();i+=18){
            Vec3 a=t.get(i-1),b=t.get(i);Vec3 d=b.subtract(a);
            float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z));
            double moving=Math.min(1,d.length()*3);
            var f=new MoonClient.Frame(b.x,b.y,b.z,yaw,yaw,0,(float)(i*.8),(float)moving,false);
            PoseStack pose=new PoseStack();pose.translate(b.x-camera.x,b.y-camera.y,b.z-camera.z);
            double reveal=TideTimeline.smooth(i/(double)t.size()*3,i/(double)t.size()*3+.5,since*1.2);
            GhostFigure.renderFrame(me,pose,buffers,(float)(fade*reveal*(.18+.3*i/t.size())),f,0xd6ecf6);
        }
        buffers.endBatch();
    }
    private MirrorGround(){}
}
