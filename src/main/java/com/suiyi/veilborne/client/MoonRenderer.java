package com.suiyi.veilborne.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.suiyi.veilborne.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 月骸花潮 on screen. Sky: the violet night spreading from the moon, a painted cloud layer, stars, a river of
 * petals, the porcelain moon (three of them at the climax) and plates of its shell falling away. World: moon
 * shadows. Ground: the flower tide, flowers opening where petals land, the hidden road beside the procession,
 * light at doorways, and the fallen little moon with its constellation that only lines up from one spot.
 */
public final class MoonRenderer {
    static ShaderInstance moonShader,screenShader;
    private static final ResourceLocation SURFACE=Veilborne.id("textures/environment/moon_surface.png"),SKY=Veilborne.id("textures/environment/moon_sky.png"),
        FLOWER=Veilborne.id("textures/environment/moon_flower.png"),SHARD=Veilborne.id("textures/environment/moon_shard.png"),PETAL=Veilborne.id("textures/environment/moon_petal.png"),
        GLOW=Veilborne.id("textures/environment/glow.png"),STAR=Veilborne.id("textures/environment/star.png"),RING=Veilborne.id("textures/environment/moon_ring.png"),
        LIGHTFALL=Veilborne.id("textures/environment/lightfall.png");
    static final double R=TideRenderer.DOME*.86,RADIUS=Math.tan(Math.toRadians(11.5)),EXTENT=2.25,K=3.4;
    private static final int[] FLOWER_TINT={0xe4e6ff,0xd2c8ff,0xf2ecff,0xc4ccff};

    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        var stage=e.getStage();
        float partial=e.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera=e.getCamera().getPosition();
        if(stage==RenderLevelStageEvent.Stage.AFTER_SKY)MoonShadows.begin(camera,partial);
        if(MoonClient.state==null||MoonClient.strength()<.001)return;
        double p=MoonClient.progress(partial),s=MoonClient.strength(),time=(MoonClient.ticks+partial)/20.0;
        if(stage==RenderLevelStageEvent.Stage.AFTER_SKY){
            if(!TideConfig.SKY.get())return;
            RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();RenderSystem.depthMask(false);RenderSystem.disableDepthTest();
            Matrix4f m=new Matrix4f(e.getModelViewMatrix());
            double day=MoonClient.dayness(partial);
            Vec3 moon=MoonClient.trueDir(partial);
            MoonSky.draw(m,p,time,s,moon,day);
            river(m,e.getCamera().rotation(),p,time,s,day);
            moons(m,p,time,s,day,partial);
            plates(m,e.getCamera().rotation(),p,time,s,partial);
            RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();
        }else if(stage==RenderLevelStageEvent.Stage.AFTER_ENTITIES){
            MoonShadows.players(partial);
            MoonShadows.draw();
        }else if(stage==RenderLevelStageEvent.Stage.AFTER_PARTICLES){
            RenderSystem.enableBlend();RenderSystem.disableCull();RenderSystem.depthMask(false);RenderSystem.enableDepthTest();
            Matrix4f identity=new Matrix4f();
            Quaternionf facing=e.getCamera().rotation();
            if(TideConfig.GROUND.get())field(identity,camera,p,time,s,partial);
            littleMoon(identity,camera,facing,e.getCamera().getLookVector(),p,time,s,partial);
            flurry(identity,camera,facing,p,time,s);
            lantern(identity,camera,p,time,s,partial);
            RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();
        }
    }
    static Vec3 sphere(double az,double alt,double r){return MoonTimeline.sphere(az,alt).scale(r);}

    // ------------------------------------------------------------------ moons
    private record Frame(Vec3 center,Vec3 right,Vec3 up){}
    private static Frame frame(Vec3 dir,double r){
        Vec3 right=dir.cross(new Vec3(0,1,0));
        if(right.lengthSqr()<1e-6)right=new Vec3(1,0,0);
        right=right.normalize();
        return new Frame(dir.scale(r),right,right.cross(dir).normalize());
    }
    private static void moons(Matrix4f m,double p,double time,double s,double day,float partial){
        if(moonShader==null)return;
        var st=MoonClient.state;
        double visible=MoonTimeline.moon(p)*s;
        if(visible<.002)return;
        double merge=MoonClient.merge(partial),split=p>=.63&&p<.8?MoonTimeline.split(p):0;
        int trueSlot=MoonTimeline.trueSlot(st.seed());
        double three=MoonTimeline.three(p);
        double brokenAge=st.broken()?MoonClient.previousAge+(MoonClient.age-MoonClient.previousAge)*partial-st.brokenAt():-1;
        RenderSystem.setShaderTexture(0,SURFACE);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        if(three>.002&&!(st.broken()&&brokenAge>100)){
            for(int slot=0;slot<3;slot++){
                if(slot==trueSlot)continue;
                double shatter=brokenAge<0?0:Mth.clamp(brokenAge/60,0,1);
                double a=visible*three*(brokenAge<0?1:1-TideTimeline.smooth(55,95,brokenAge));
                // A reflection copies the true moon, core and all, mirrored; it is hollow only where it breaks.
                moon(m,MoonTimeline.moonDir(st.seed(),slot,split,0),.96,a,time,
                    MoonTimeline.crack(p),MoonTimeline.shed(p),MoonTimeline.core(p)*.95,MoonTimeline.halo(p)*.6,true,shatter,day);
            }
        }
        moon(m,MoonTimeline.moonDir(st.seed(),trueSlot,split,merge),1,visible,time,
            MoonTimeline.crack(p),MoonTimeline.shed(p),MoonTimeline.core(p),MoonTimeline.halo(p),false,0,day);
        RenderSystem.defaultBlendFunc();
    }
    private static void moon(Matrix4f m,Vec3 dir,double size,double alpha,double time,double crack,double shed,double core,double halo,boolean reflection,double shatter,double day){
        if(alpha<.002)return;
        Frame f=frame(dir,R);
        double hs=R*RADIUS*EXTENT*size;
        moonShader.safeGetUniform("MoonTime").set((float)(time%1000));
        moonShader.safeGetUniform("MoonState").set((float)crack,(float)shed,(float)core,(float)halo);
        moonShader.safeGetUniform("MoonKind").set(reflection?1f:0f,(float)shatter,reflection?1f:0f,0f);
        moonShader.safeGetUniform("MoonLook").set((float)day,(float)Math.min(1,alpha),(float)EXTENT,0f);
        RenderSystem.setShader(()->moonShader);
        BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        quad(b,m,f.center(),f.right().scale(hs),f.up().scale(hs),(float)EXTENT);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }
    private static void quad(BufferBuilder b,Matrix4f m,Vec3 c,Vec3 r,Vec3 u,float e){
        vertex(b,m,c.subtract(r).subtract(u),-e,-e);vertex(b,m,c.add(r).subtract(u),e,-e);
        vertex(b,m,c.add(r).add(u),e,e);vertex(b,m,c.subtract(r).add(u),-e,e);
    }
    private static void vertex(BufferBuilder b,Matrix4f m,Vec3 p,float u,float v){b.addVertex(m,(float)p.x,(float)p.y,(float)p.z).setUv(u,v).setColor(1f,1f,1f,1f);}

    // ------------------------------------------------------------------ shell plates (mirrors moon.fsh)
    static int hash(int x,int y){
        x=x*1664525+1013904223;y=y*1664525+1013904223;
        x+=y*1664525;y+=x*1664525;
        x^=x>>>16;y^=y>>>16;
        x+=y*1664525;y+=x*1664525;
        x^=x>>>16;y^=y>>>16;
        return x^y;
    }
    static double h01(int cx,int cy,int salt){return (hash(cx+1000+salt*7919,cy+1000+salt*104729)>>>8)/16777216.0;}
    record Cell(int cx,int cy,double lon,double lat,double key,double key2){
        long id(){return ((long)cx<<32)^(cy&0xffffffffL);}
        /** Position on the disc (x right, y up) as seen from below. */
        double dx(boolean mirror){return Math.cos(lat)*Math.sin(mirror?-lon:lon);}
        double dy(){return Math.sin(lat);}
    }
    static final List<Cell> CELLS=new ArrayList<>();
    static {
        for(int cy=-6;cy<=5;cy++)for(int cx=-6;cx<=5;cx++){
            double sx=cx+.15+.7*h01(cx,cy,1),sy=cy+.15+.7*h01(cx,cy,2);
            double lon=sx/K,lat=sy/K;
            if(Math.abs(lon)>Math.PI/2*.92||Math.abs(lat)>Math.PI/2*.92)continue;
            if(Math.cos(lat)*Math.cos(lon)<.18)continue;
            CELLS.add(new Cell(cx,cy,lon,lat,.62*h01(cx,cy,3)+.38*(.5-.5*Math.sin(lat)),h01(cx,cy,4)*.8));
        }
    }
    private static final Map<Long,Integer> shedAt=new HashMap<>(),regrowAt=new HashMap<>();
    private static long platesSeed;
    private static boolean primed;
    /** Client tick: note which plates have just come away (or grown back) so they can be seen to fall. */
    static void tick(){
        var st=MoonClient.state;
        if(st==null||!MoonClient.active()){shedAt.clear();regrowAt.clear();primed=false;return;}
        if(st.seed()!=platesSeed){platesSeed=st.seed();shedAt.clear();regrowAt.clear();primed=false;}
        double shed=MoonTimeline.shed(MoonClient.progress(1));
        for(Cell c:CELLS){
            boolean gone=c.key()<shed;
            if(gone&&!shedAt.containsKey(c.id())){shedAt.put(c.id(),primed?MoonClient.ticks:-100000);regrowAt.remove(c.id());}
            else if(!gone&&shedAt.containsKey(c.id())){shedAt.remove(c.id());if(primed)regrowAt.put(c.id(),MoonClient.ticks);}
        }
        regrowAt.values().removeIf(t->MoonClient.ticks-t>60);
        primed=true;
    }
    private static void plates(Matrix4f m,Quaternionf facing,double p,double time,double s,float partial){
        var st=MoonClient.state;
        double visible=MoonTimeline.moon(p)*s;
        if(visible<.01)return;
        int trueSlot=MoonTimeline.trueSlot(st.seed());
        double split=p>=.63&&p<.8?MoonTimeline.split(p):0;
        Frame f=frame(MoonTimeline.moonDir(st.seed(),trueSlot,split,MoonClient.merge(partial)),R*.985);
        double r=R*RADIUS,now=MoonClient.ticks+partial;
        TideFx shards=TideFx.begin(SHARD,m);
        TideFx glints=TideFx.begin(PETAL,m);
        Map<Long,Cell> byId=new HashMap<>();
        for(Cell c:CELLS)byId.put(c.id(),c);
        for(var entry:shedAt.entrySet()){
            double age=(now-entry.getValue())/20;
            if(age<0||age>7)continue;
            Cell c=byId.get(entry.getKey());if(c==null)continue;
            fall(shards,glints,f,facing,r,c,false,age,visible,entry.getKey());
        }
        for(var entry:regrowAt.entrySet()){
            double age=(now-entry.getValue())/20;
            Cell c=byId.get(entry.getKey());if(c==null)continue;
            // Growing back: a flake rises into its place as the shell closes over the light.
            double t=1-Mth.clamp(age/3,0,1);
            fall(shards,glints,f,facing,r,c,false,t*2.2,visible*Math.min(1,age*2),entry.getKey());
        }
        // Small flakes keep drifting off the cracked shell while it is open.
        double flaking=MoonTimeline.crack(p)*(1-MoonTimeline.closing(p));
        if(flaking>.05)for(int i=0;i<26;i++){
            double period=8+(i%5),age=(time+i*3.7)%period;
            double a=i*2.399,rr=.55+.4*((i*37)%10)/10.0;
            Cell c=new Cell(0,0,Math.atan2(Math.cos(a)*rr,Math.sqrt(Math.max(0,1-rr*rr))),Math.asin(Math.sin(a)*rr),0,0);
            fallSmall(shards,glints,f,facing,r,c,age,visible*flaking,i);
        }
        // Broken reflections burst into porcelain.
        if(st.broken()){
            double brokenAge=MoonClient.previousAge+(MoonClient.age-MoonClient.previousAge)*partial-st.brokenAt();
            if(brokenAge<200)for(int slot=0;slot<3;slot++){
                if(slot==trueSlot)continue;
                Frame ff=frame(MoonTimeline.moonDir(st.seed(),slot,split,0),R*.985);
                for(Cell c:CELLS){
                    double age=(brokenAge-c.key2()*48)/20;
                    if(age<0||age>6)continue;
                    fall(shards,glints,ff,facing,r*.94,c,true,age*1.15,visible*MoonTimeline.three(p)+visible*.5,c.id()*31+slot);
                }
            }
        }
        shards.end(false);
        glints.end(true);
    }
    /** A plate leaves the disc, tumbles, falls ever faster and turns into petal glints on the way down. */
    private static void fall(TideFx shards,TideFx glints,Frame f,Quaternionf facing,double r,Cell c,boolean mirror,double age,double alpha,long seed){
        double rnd=((seed*2654435761L)>>>8&1023)/1023.0;
        double dx=c.dx(mirror),dy=c.dy();
        Vec3 start=f.center().add(f.right().scale(dx*r)).add(f.up().scale(dy*r));
        double outward=.06*age*r,drop=(.05*age+.045*age*age)*r*2.2;
        Vec3 pos=start.add(f.right().scale(dx*outward+(rnd-.5)*age*r*.18)).add(f.up().scale(dy*outward-drop));
        double size=r*.17*(1-.55*Mth.clamp(age/6,0,1));
        double fade=1-TideTimeline.smooth(3.2,6.2,age);
        shards.billboard(pos,facing,size,rnd*6+age*(1.2+rnd)*(rnd>.5?1:-1),0,0,1,1,0xffffff,alpha*fade*Mth.clamp(age*6,0,1));
        double petal=TideTimeline.smooth(1.8,4.2,age)*(1-TideTimeline.smooth(5.2,7,age));
        if(petal>.01)for(int k=0;k<3;k++){
            Vec3 g=pos.add(f.right().scale(Math.sin(age*2+k*2.1+rnd*6)*r*.12)).add(f.up().scale(r*(.05+.08*k)));
            glints.billboard(g,facing,r*.035,age*2+k,0,0,1,1,k==1?0xffe3b0:0xe8e2ff,alpha*petal*.8);
        }
    }
    private static void fallSmall(TideFx shards,TideFx glints,Frame f,Quaternionf facing,double r,Cell c,double age,double alpha,int i){
        double dx=c.dx(false),dy=c.dy();
        Vec3 start=f.center().add(f.right().scale(dx*r)).add(f.up().scale(dy*r));
        double drop=(.03*age+.02*age*age)*r*2;
        Vec3 pos=start.add(f.right().scale(dx*.08*age*r+Math.sin(age+i)*r*.05)).add(f.up().scale(-drop));
        double fade=Mth.clamp(age*3,0,1)*(1-TideTimeline.smooth(4,7,age));
        shards.billboard(pos,facing,r*.045,age*(i%2==0?1.4:-1.1)+i,0,0,1,1,0xffffff,alpha*fade*(1-TideTimeline.smooth(2.5,4,age)));
        glints.billboard(pos,facing,r*.03,age*2+i,0,0,1,1,i%3==0?0xffe0a8:0xe6e0ff,alpha*fade*TideTimeline.smooth(2,4,age)*.9);
    }

    // ------------------------------------------------------------------ the petal river
    private static void river(Matrix4f m,Quaternionf facing,double p,double time,double s,double day){
        double flow=MoonTimeline.petals(p)*s*TideConfig.BRIGHTNESS.get()*(1-day*.4);
        if(flow<.01)return;
        var st=MoonClient.state;
        double az0=MoonTimeline.azimuth(st.seed());
        int n=260+TideConfig.QUALITY.get()*220;
        Random rng=new Random(st.seed()^0x5EEDL);
        TideFx fx=TideFx.begin(PETAL,m);
        double sign=(st.seed()&1)==0?1:-1;
        for(int i=0;i<n;i++){
            double r0=rng.nextDouble(),r1=rng.nextDouble(),g1=rng.nextGaussian(),g2=rng.nextGaussian(),r3=rng.nextDouble();
            double t=(r0+time*(.0045+.004*r1))%1;
            double az=az0+sign*(.12+t*2.1)+g1*(.035+.17*t);
            double alt=MoonTimeline.BASE_ALT*Math.pow(1-t,1.25)+.14*Math.sin(Math.PI*t)+g2*(.02+.06*t);
            if(alt<.01)continue;
            Vec3 v=sphere(az,alt,TideRenderer.DOME*.9);
            double tw=.65+.35*Math.sin(time*(1.5+r1*2)+i);
            int color=r3<.12?0xffdca8:r3<.5?0xe8e4ff:0xc9bfff;
            fx.billboard(v,facing,TideRenderer.DOME*(.0032+.0042*r3)*(1.15-.5*t),time*(.4+r1)+i,0,0,1,1,color,flow*Math.sin(Math.PI*t)*(.3+.45*r3)*tw);
        }
        fx.end(true);
    }

    // ------------------------------------------------------------------ ground: the flower tide
    private static void field(Matrix4f matrix,Vec3 camera,double p,double time,double s,float partial){
        double bright=s*TideConfig.BRIGHTNESS.get()*(1-.45*MoonClient.dayness(partial));
        double bloom=MoonTimeline.bloom(p),closing=MoonTimeline.closing(p);
        if(bloom<.01&&MoonClient.blooms.isEmpty())return;
        TideFx glow=TideFx.begin(GLOW,matrix);
        TideFx flowers=TideFx.begin(FLOWER,matrix);
        int ticks=MoonClient.ticks;
        for(MoonClient.Patch patch:MoonClient.field){
            double open=TideTimeline.smooth(patch.threshold,patch.threshold+.08,bloom);
            if(open<.01)continue;
            double dist=Math.hypot(patch.x-camera.x,patch.z-camera.z);
            // Rings close from far to near.
            double ringAt=1-Math.floor(dist/3)*3/30;
            double closed=TideTimeline.smooth(ringAt-.05,ringAt,closing);
            Integer trod=MoonClient.trampled.get(patch.key);
            double regrow=trod==null?1:TideTimeline.smooth(60,190,ticks+partial-trod);
            double phase=(patch.key&255)*.025;
            double a=.5*open*(1-closed)*regrow*bright*(.84+.16*Math.sin(time*.7+phase));
            if(a<.004)continue;
            double bob=patch.water?Math.sin(time*.8+phase)*.03:0;
            Vec3 c=new Vec3(patch.x-camera.x,patch.y+.03+bob-camera.y,patch.z-camera.z);
            double size=patch.size*(.3+.7*open)*(1-.4*closed)*(.6+.4*regrow);
            int tint=patch.path?0xfff0d6:FLOWER_TINT[(int)(patch.key>>>40&3)];
            glow.flat(c.add(0,-.005,0),size*1.9,0,0,0,1,1,patch.water?0x6f8fd8:0x8a7fd6,a*.28);
            float u0=(patch.variant&1)*.5f,v0=(patch.variant>>1)*.5f;
            flowers.flat(c,size,patch.rot+(1-open)*1.4+time*.02,u0,v0,u0+.5f,v0+.5f,tint,a);
        }
        // Flowers opening where petals fell.
        for(MoonClient.Bloom b:MoonClient.blooms){
            double t=(ticks+partial-b.born())/20.0;
            double open=TideTimeline.smooth(0,.7,t),flash=Math.exp(-t*2.6);
            double fade=1-TideTimeline.smooth(16,21,t);
            double size=b.size()*(open+.22*Math.sin(Math.min(1,t/.7)*Math.PI));
            Vec3 c=b.pos().subtract(camera).add(0,.012,0);
            double a=(.42+.6*flash)*fade*bright*(1-closing);
            if(a<.004)continue;
            glow.flat(c,size*2.2,0,0,0,1,1,0xa497ff,a*.35+flash*.3);
            float u0=(b.variant()&1)*.5f,v0=(b.variant()>>1)*.5f;
            flowers.flat(c.add(0,.004,0),size,b.rot()+(1-open)*2,u0,v0,u0+.5f,v0+.5f,0xf4f0ff,a);
        }
        // Walk beside the bearers, not among them, and their road shows ahead of them in brighter flowers.
        if(MoonClient.pathReveal>.01)for(var e:Minecraft.getInstance().level.entitiesForRendering())if(e instanceof MoonProcession proc){
            double at=proc.renderPath(partial);
            for(double k=2;k<40&&at+k<proc.length()+2;k+=1.35){
                double side=((int)(k/1.35)%2==0?1:-1)*.85;
                Vec3 q=proc.member(at+k,0,side);
                double fadeAhead=1-TideTimeline.smooth(24,40,k);
                double a=MoonClient.pathReveal*fadeAhead*bright*(.55+.2*Math.sin(time*2-k*.4));
                Vec3 c=q.subtract(camera).add(0,.035,0);
                int v=(int)(k*7)&3;float u0=(v&1)*.5f,v0=(v>>1)*.5f;
                glow.flat(c,.9,0,0,0,1,1,0xd9b56a,a*.32);
                flowers.flat(c.add(0,.004,0),.42,k*1.7+time*.05,u0,v0,u0+.5f,v0+.5f,0xffe8b8,a);
            }
        }
        // A little flower light at doorways and windows, seen from inside: a pool of moonlight on the floor
        // with a flower shadow drifting across it, and a faint glow on the jambs.
        double veil=MoonTimeline.veil(p)*s*TideConfig.BRIGHTNESS.get();
        for(MoonClient.Opening o:MoonClient.openings){
            Vec3 out=Vec3.atLowerCornerOf(o.out().getNormal()),across=new Vec3(-out.z,0,out.x);
            Vec3 sill=Vec3.atBottomCenterOf(o.inside()).add(out.scale(.45)).subtract(camera).add(0,.02,0);
            Vec3 in=out.scale(-2.2);
            double breathe=.85+.15*Math.sin(time*.6+o.inside().asLong()%13);
            glow.quad(sill.add(across.scale(.5)),sill.add(across.scale(.9)).add(in),sill.subtract(across.scale(.9)).add(in),sill.subtract(across.scale(.5)),0,0,1,1,0xb2a8f0,veil*.42*breathe);
            glow.flat(sill.add(in.scale(.35)),1.0,0,0,0,1,1,0x8f84e0,veil*.3*breathe);
            double phase=o.inside().asLong()%97*.1;
            for(int k=0;k<2;k++){
                double slide=(time*.07+phase+k*.5)%1;
                flowers.flat(sill.add(in.scale(slide*.9)).add(across.scale(Math.sin(slide*3+k)*.3)).add(0,.006,0),.3+.08*k,phase+time*.1+k,(k&1)*.5f,0,(k&1)*.5f+.5f,.5f,0xefeaff,veil*.5*Math.sin(slide*Math.PI));
            }
            Vec3 jamb=Vec3.atCenterOf(o.inside()).add(out.scale(.49)).subtract(camera);
            glow.quad(jamb.add(across.scale(.5)).add(0,-.5,0),jamb.add(across.scale(.5)).add(0,.5,0),jamb.subtract(across.scale(.5)).add(0,.5,0),jamb.subtract(across.scale(.5)).add(0,-.5,0),0,0,1,1,0x9d92e0,veil*.12);
        }
        glow.end(true);
        flowers.end(true);
    }

    // ------------------------------------------------------------------ the fallen little moon
    private static List<Vec3> stars;
    private static long starsFor;
    /** 玉兔, sitting and facing left, drawn around the little moon so that it sits in the rabbit's body. */
    private static final double[][] RABBIT={{-.21,.44},{-.09,.48},{-.16,.25},{-.31,.18},{-.25,.07},{-.06,.21},{.15,.19},{.31,.05},
        {.35,-.15},{.42,.02},{.14,-.27},{-.1,-.25},{-.23,-.15},{-.21,-.02}};
    private static final int[][] LINES={{0,2},{1,5},{2,3},{3,4},{4,13},{13,12},{12,11},{11,10},{10,8},{8,7},{7,9},{7,6},{6,5},{5,2}};
    /** Motes placed so that from the standing spot they line up into 玉兔; anywhere else they are scattered. */
    static List<Vec3> constellation(Vec3 eye,Vec3 moon,long seed){
        Vec3 f=moon.subtract(eye).normalize(),right=f.cross(new Vec3(0,1,0)).normalize(),up=right.cross(f).normalize();
        List<Vec3> out=new ArrayList<>();
        Random rng=new Random(seed*31+7);
        for(double[] q:RABBIT){
            double d=1.6+5.2*rng.nextDouble();
            out.add(eye.add(f.add(right.scale(-q[0])).add(up.scale(q[1])).scale(d)));
        }
        return out;
    }
    private static void littleMoon(Matrix4f matrix,Vec3 camera,Quaternionf facing,Vector3f lookVector,double p,double time,double s,float partial){
        Vec3 moon=MoonClient.littleMoon(partial);
        double alpha=MoonClient.littleMoonAlpha(partial)*s;
        if(moon==null||alpha<.01)return;
        var st=MoonClient.state;
        Vec3 base=Vec3.atBottomCenterOf(st.littleMoon());
        Vec3 spot=MoonTimeline.viewpoint(st.seed(),base);
        double spotY=MoonProcession.ground(Minecraft.getInstance().level,spot.x,spot.z);
        Vec3 eye=new Vec3(spot.x,spotY+1.62,spot.z),center=Vec3.atBottomCenterOf(st.littleMoon()).add(0,MoonTimeline.LITTLE_MOON_HEIGHT,0);
        long key=st.seed()^st.littleMoon().asLong();
        if(stars==null||starsFor!=key){stars=constellation(eye,center,st.seed());starsFor=key;}
        // Light pooled on the ground under it, and a gold ring marking where to stand.
        TideFx glow=TideFx.begin(GLOW,matrix);
        glow.flat(base.subtract(camera).add(0,.03,0),3.4,0,0,0,1,1,0xb3a8ff,alpha*.34);
        glow.billboard(moon.subtract(camera),facing,2.2,0,0,0,1,1,0x9b8fe6,alpha*.22);
        glow.end(true);
        TideFx ring=TideFx.begin(RING,matrix);
        ring.flat(new Vec3(spot.x,spotY+.04,spot.z).subtract(camera),.6,time*.3,0,0,1,1,0xffd99a,alpha*(.35+.15*Math.sin(time*2)));
        ring.end(true);
        // The little moon itself: the same porcelain, cracked, with light leaking through.
        if(moonShader!=null){
            RenderSystem.setShaderTexture(0,SURFACE);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            moonShader.safeGetUniform("MoonTime").set((float)(time%1000));
            moonShader.safeGetUniform("MoonState").set((float)(.55+.15*Math.sin(time*.9)),.1f,.85f,0f);
            moonShader.safeGetUniform("MoonKind").set(0f,0f,0f,0f);
            moonShader.safeGetUniform("MoonLook").set((float)MoonClient.dayness(partial),(float)Math.min(1,alpha),1.8f,1f);
            RenderSystem.setShader(()->moonShader);
            BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
            Vector3f r=new Vector3f(1.1f,0,0).rotate(facing),u=new Vector3f(0,1.1f,0).rotate(facing);
            // Slow turn: the moon rolls a little in place.
            quad(b,matrix,moon.subtract(camera),new Vec3(r.x,r.y,r.z),new Vec3(u.x,u.y,u.z),1.8f);
            BufferUploader.drawWithShader(b.buildOrThrow());
            RenderSystem.defaultBlendFunc();
        }
        // Motes around it; from the gold ring, looking at the moon, they join into a rabbit.
        double near=1-TideTimeline.smooth(.3,.95,Math.hypot(camera.x-eye.x,camera.z-eye.z));
        Vec3 look=new Vec3(lookVector.x,lookVector.y,lookVector.z);
        double aim=TideTimeline.smooth(Math.cos(Math.toRadians(22)),Math.cos(Math.toRadians(9)),look.dot(center.subtract(camera).normalize()));
        double aligned=near*aim*alpha;
        TideFx motes=TideFx.begin(STAR,matrix);
        for(int i=0;i<stars.size();i++){
            Vec3 v=stars.get(i).subtract(camera);
            double tw=.6+.4*Math.sin(time*2.3+i*1.7);
            motes.billboard(v,facing,.07+.05*aligned+.02*Math.sin(time*3+i),time*.5+i,0,0,1,1,i%4==0?0xffe2b0:0xeae6ff,alpha*(.45+.5*aligned)*tw);
        }
        motes.end(true);
        if(aligned>.01){
            TideFx lines=TideFx.begin(GLOW,matrix);
            for(int[] l:LINES)lines.ribbon(stars.get(l[0]).subtract(camera),stars.get(l[1]).subtract(camera),Vec3.ZERO,.012,0xd8d0ff,aligned*.75);
            lines.end(true);
        }
    }

    // ------------------------------------------------------------------ distant petals and the lantern
    private static void flurry(Matrix4f matrix,Vec3 camera,Quaternionf facing,double p,double time,double s){
        double density=MoonTimeline.petals(p)*s*TideConfig.BRIGHTNESS.get();
        if(density<.02)return;
        var level=Minecraft.getInstance().level;
        int per=1+TideConfig.QUALITY.get();
        int cx=Mth.floor(camera.x/12),cz=Mth.floor(camera.z/12);
        TideFx fx=TideFx.begin(PETAL,matrix);
        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++){
            if(Math.abs(x)<=2&&Math.abs(z)<=2)continue;
            int gx=cx+x,gz=cz+z;
            int ground=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,gx*12+6,gz*12+6);
            for(int k=0;k<per;k++){
                int h=hash(gx*3+k,gz*5-k);
                double u=(h>>>8&255)/255.0,v=(h>>>16&255)/255.0,ph=(h&255)/255.0;
                double fall=(time*.9+ph*36)%36;
                Vec3 w=new Vec3(gx*12+u*12+Math.sin(time*.4+ph*9)*1.5,ground+34-fall,gz*12+v*12+Math.cos(time*.33+ph*7));
                Vec3 rel=w.subtract(camera);
                double dist=rel.length();
                double a=density*(TideTimeline.smooth(22,34,dist)*(1-TideTimeline.smooth(70,86,dist)))*Math.sin(Math.PI*fall/36)*.55;
                if(a>.01)fx.billboard(rel,facing,.28,time+ph*6,0,0,1,1,(h&7)==0?0xffe0b0:0xe2dcff,a);
            }
        }
        fx.end(true);
    }
    private static void lantern(Matrix4f matrix,Vec3 camera,double p,double time,double s,float partial){
        var st=MoonClient.state;
        if(st.lantern().equals(BlockPos.ZERO))return;
        double since=MoonClient.previousAge+(MoonClient.age-MoonClient.previousAge)*partial-st.lanternAt();
        double a=TideTimeline.smooth(0,20,since)*(1-TideTimeline.smooth(160,320,since))*s;
        if(a<.01)return;
        Vec3 v=Vec3.atBottomCenterOf(st.lantern()).subtract(camera);
        TideFx beam=TideFx.begin(LIGHTFALL,matrix);
        java.util.function.DoubleUnaryOperator profile=t->Math.pow(1-t,1.4)*TideTimeline.smooth(0,.04,t);
        beam.curtain(v.add(0,26,0),26,1.1,Vec3.ZERO,(float)(time*.2),1,0xd9d0ff,a*.5,t->profile.applyAsDouble(1-t),0,time);
        beam.end(true);
        TideFx ring=TideFx.begin(RING,matrix);
        ring.flat(v.add(0,.04,0),1.6,time*.25,0,0,1,1,0xffdca0,a*.55);
        ring.end(true);
    }
    private MoonRenderer(){}
}
