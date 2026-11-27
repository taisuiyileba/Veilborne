package com.suiyi.veilborne.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.suiyi.veilborne.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * The client's share of what the weathers leave behind: the journal's copy of completeness and remembered places, the
 * star-tide pools shimmering on the water, the voyage (the world sinks away into the sea above and comes back somewhere
 * else), a stitched lamp's thread drawing someone in, the silence answering a sound, the silver light over a tower's
 * buried foundation, and what the cracked mirror's heart shows through the ground.
 */
public final class VeilClient {
    static VeilPacket state;
    static int ticks;
    private static int voyageFrom=-1,voyageTo=-1;
    private static boolean voyageStaged;
    record Pull(Vec3 at,int from,int length){}
    record Beam(Vec3 at,int from){}
    private static final List<Pull> pulls=new ArrayList<>();
    private static final List<Beam> beams=new ArrayList<>();
    private static List<BlockPos> hidden=List.of();
    private static int hiddenFrom,hiddenUntil;
    private static final RandomSource random=RandomSource.create();
    private static final Matrix4f I=new Matrix4f();
    public static final int VOYAGE_IN=50,VOYAGE_OUT=40,VOYAGE_HOLD=200,BEAM_TICKS=900;

    public static void receive(VeilPacket p){state=p;}
    public static void cue(VeilCue c){
        var mc=Minecraft.getInstance();
        switch(c.kind()){
            case VeilCue.VOYAGE -> {
                if(c.value()==1){voyageFrom=ticks;voyageTo=-1;voyageStaged=false;play(mc,Veilborne.SEA.get(),.9f,.8f,c.at());}
                else if(voyageFrom>=0){voyageTo=ticks;play(mc,Veilborne.EBB.get(),.7f,1.1f,mc.player==null?c.at():mc.player.position());}
            }
            case VeilCue.PULL -> pulls.add(new Pull(c.at(),ticks,c.value()));
            case VeilCue.LOUD -> {
                if(mc.level!=null)for(int i=0;i<40;i++){
                    double a=i*Math.PI*2/40;
                    mc.level.addParticle(ParticleTypes.SCULK_CHARGE_POP,c.x()+Math.cos(a)*1.2,c.y()+.2,c.z()+Math.sin(a)*1.2,Math.cos(a)*.12,.01,Math.sin(a)*.12);
                }
            }
            case VeilCue.VAULT -> beams.add(new Beam(c.at(),ticks));
            case VeilCue.REVEAL -> {hidden=c.spots();hiddenFrom=ticks;hiddenUntil=ticks+c.value();}
            default -> {}
        }
    }
    private static void play(Minecraft mc,net.minecraft.sounds.SoundEvent sound,float volume,float pitch,Vec3 at){
        mc.getSoundManager().play(new SimpleSoundInstance(sound.getLocation(),SoundSource.AMBIENT,volume,pitch,RandomSource.create(),false,0,SoundInstance.Attenuation.NONE,at.x,at.y,at.z,true));
    }
    public static int[] last(){return state==null?new int[VeilGrade.NAMES.length]:state.last();}
    public static int[] best(){return state==null?new int[VeilGrade.NAMES.length]:state.best();}
    public static List<VeilGrade.Site> sites(){return state==null?List.of():state.sites();}
    /** Deeds counted for this weather: those of the event under way, or else of the last one. */
    public static int deeds(int weather,boolean[] live){
        if(state==null){live[0]=false;return 0;}
        live[0]=state.liveWeather()==weather;
        return live[0]?state.liveDeeds():state.lastDeeds()[weather];
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null){pulls.clear();beams.clear();hidden=List.of();voyageFrom=-1;return;}
        if(mc.isPaused())return;
        ticks++;
        pulls.removeIf(p->ticks-p.from()>p.length()+10);
        beams.removeIf(b->ticks-b.from()>BEAM_TICKS);
        if(ticks>hiddenUntil)hidden=List.of();
        if(voyageFrom>=0&&(voyageTo>=0&&ticks-voyageTo>VOYAGE_OUT||voyageTo<0&&ticks-voyageFrom>VOYAGE_HOLD+VOYAGE_IN))voyageFrom=voyageTo=-1;
        // Stepping off on the way calls the voyage off, and the server sends no word of it: let the sea go when the boat does.
        if(voyageFrom>=0&&voyageTo<0&&!voyageStaged&&ticks-voyageFrom>5&&!(mc.player.getVehicle() instanceof TideSkiff s&&s.voyage()>0))voyageTo=ticks;
        for(Pull p:pulls){
            double t=(ticks-p.from())/(double)Math.max(1,p.length());
            if(t>1)continue;
            for(int k=0;k<3;k++){
                double a=ticks*.4+k*2.1,r=1.4*(1-t)+.2;
                mc.level.addParticle(Veilborne.THREAD_FIBER.get(),p.at().x+Math.cos(a)*r,p.at().y-.3+t*1.4+k*.2,p.at().z+Math.sin(a)*r,-Math.cos(a)*.03,.02,-Math.sin(a)*.03);
            }
        }
        pools(mc);
    }
    /** Star motes rising off open water inside a pool, around wherever you are. */
    private static void pools(Minecraft mc){
        if(state==null||state.pools().isEmpty()||!mc.level.dimension().equals(Level.OVERWORLD)||ticks%2!=0)return;
        Vec3 me=mc.player.position();
        boolean near=false;
        for(TideData.Pool q:state.pools())if(Math.hypot(me.x-q.at().getX(),me.z-q.at().getZ())<TideService.POOL_RADIUS+24)near=true;
        if(!near)return;
        for(int k=0;k<6;k++){
            int x=Mth.floor(me.x)+random.nextInt(33)-16,z=Mth.floor(me.z)+random.nextInt(33)-16;
            boolean inside=false;
            for(TideData.Pool q:state.pools())if(Math.hypot(x+.5-q.at().getX()-.5,z+.5-q.at().getZ()-.5)<=TideService.POOL_RADIUS)inside=true;
            if(!inside||!mc.level.hasChunk(x>>4,z>>4))continue;
            int top=mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,x,z);
            BlockPos w=new BlockPos(x,top-1,z);
            if(!mc.level.getFluidState(w).is(FluidTags.WATER))continue;
            mc.level.addParticle(Veilborne.MOTE.get(),x+random.nextDouble(),top+.05,z+random.nextDouble(),0,.012+random.nextDouble()*.01,0);
        }
    }

    // ------------------------------------------------------------------ the voyage
    /** 0..1: how far the sea overhead has closed over the view. */
    static double voyage(float partial){
        if(voyageFrom<0)return 0;
        double now=ticks+partial;
        double in=TideTimeline.smooth(0,VOYAGE_IN,now-voyageFrom);
        return voyageTo<0?in:in*(1-TideTimeline.smooth(0,VOYAGE_OUT,now-voyageTo));
    }
    @SubscribeEvent public static void overlay(RenderGuiEvent.Post e){
        float partial=e.getPartialTick().getGameTimeDeltaPartialTick(false);
        double v=voyage(partial);
        if(v<.005)return;
        var g=e.getGuiGraphics();
        int w=g.guiWidth(),h=g.guiHeight();
        int a=(int)(v*255)<<24;
        g.fillGradient(0,0,w,h,a|0x06122a,a|0x10284a);
        // The sea overhead seen from inside: slow motes of starlight drifting up past you.
        RandomSource r=RandomSource.create(7);
        double t=(ticks+partial)/20.0;
        for(int i=0;i<140;i++){
            double x=r.nextDouble()*w,speed=6+r.nextDouble()*18,y=((r.nextDouble()*h-t*speed)%h+h)%h;
            double tw=.55+.45*Math.sin(t*(1.5+r.nextDouble()*2)+i);
            int s=r.nextInt(5)==0?2:1;
            int alpha=(int)(v*tw*(140+r.nextInt(100)));
            int color=r.nextInt(4)==0?0xf3dda6:0xbfeaf0;
            g.fill((int)x,(int)y,(int)x+s,(int)y+s,Mth.clamp(alpha,0,255)<<24|color);
        }
        if(v>.6){
            var font=Minecraft.getInstance().font;
            var text=net.minecraft.network.chat.Component.translatable(voyageTo<0?"message.veilborne.voyage_sailing":"message.veilborne.voyage_ashore");
            int ta=(int)((v-.6)/.4*200);
            g.drawCenteredString(font,text,w/2,h/2-4,Mth.clamp(ta,4,255)<<24|0xf3dda6);
        }
    }

    // ------------------------------------------------------------------ light in the world
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        if(beams.isEmpty()&&hidden.isEmpty())return;
        float partial=e.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera=e.getCamera().getPosition();
        double time=(ticks+partial)/20.0;
        if(!beams.isEmpty()){
            TideFx fx=TideFx.begin(MirrorRenderer.GLOW,I);
            for(Beam b:beams){
                double age=ticks+partial-b.from(),k=TideTimeline.smooth(0,30,age)*(1-TideTimeline.smooth(BEAM_TICKS-120,BEAM_TICKS,age));
                Vec3 at=b.at().subtract(camera);
                fx.column(at,1.1,34,16,(float)(-time*.3),0xcfe8ee,.32*k);
                fx.column(at,.45,50,12,(float)(-time*.5),0xf3dda6,.4*k);
                fx.flat(at.add(0,.06,0),2.6,time*.2,0,0,1,1,0xcfe8ee,.5*k);
            }
            fx.end(true);
        }
        if(!hidden.isEmpty())hidden(camera,partial,time);
    }
    /** What the cracked mirror's heart shows: boxes of silver light round each hidden thing, seen through the ground. */
    private static void hidden(Vec3 camera,float partial,double time){
        double age=ticks+partial-hiddenFrom,left=hiddenUntil-ticks-partial;
        double k=TideTimeline.smooth(0,16,age)*TideTimeline.smooth(0,60,left);
        if(k<.01)return;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();
        BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_COLOR);
        TideMesh m=new TideMesh(b,new Matrix4f().translate((float)-camera.x,(float)-camera.y,(float)-camera.z));
        for(BlockPos p:hidden){
            double d=Math.sqrt(p.distToCenterSqr(camera));
            // A ripple of light runs outward from where the heart was broken.
            double wave=.7+.3*Math.sin(time*3-d*.35);
            double a=k*wave*(1-.35*Math.min(1,d/40));
            Vec3 lo=new Vec3(p.getX()-.02,p.getY()-.02,p.getZ()-.02),hi=lo.add(1.04,1.04,1.04);
            edges(m,lo,hi,.045+d*.0015,0xe6f8fa,a);
            m.box(p.getX()+.08,p.getY()+.08,p.getZ()+.08,.84,.84,.84,0x7fd9e8,a*.28);
        }
        MeshData mesh=b.build();if(mesh!=null)BufferUploader.drawWithShader(mesh);
        // A soft glow at the heart of each, so they read from across a cave.
        TideFx glow=TideFx.begin(MirrorRenderer.GLOW,I);
        var facing=Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        for(BlockPos p:hidden)glow.billboard(Vec3.atCenterOf(p).subtract(camera),facing,1.1,0,0xbfeef4,k*.55*(.75+.25*Math.sin(time*2.4+p.asLong())));
        glow.end(true);
        RenderSystem.enableDepthTest();RenderSystem.defaultBlendFunc();RenderSystem.enableCull();
    }
    private static void edges(TideMesh m,Vec3 lo,Vec3 hi,double w,int color,double a){
        double[] xs={lo.x,hi.x},ys={lo.y,hi.y},zs={lo.z,hi.z};
        for(double y:ys)for(double z:zs)m.line(new Vec3(lo.x,y,z),new Vec3(hi.x,y,z),w,color,a);
        for(double x:xs)for(double z:zs)m.line(new Vec3(x,lo.y,z),new Vec3(x,hi.y,z),w,color,a);
        for(double x:xs)for(double y:ys)m.line(new Vec3(x,y,lo.z),new Vec3(x,y,hi.z),w,color,a);
    }
    /** The keepsakes whose lasting use is not in their own tooltip: blocks placed as items. */
    @SubscribeEvent public static void tooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent e){
        var s=e.getItemStack();
        String key=s.is(Veilborne.SILVER_LANTERN_ITEM.get())?"silver_lantern":s.is(Veilborne.SILENT_LANTERN_ITEM.get())?"silent_lantern":s.is(Veilborne.STILL_CANDLE_ITEM.get())?"still_candle"
            :s.is(Veilborne.LOOM_LANTERN_ITEM.get())?"loom_lantern":s.is(Veilborne.DREAM_LEAVES_ITEM.get())?"dream_leaves":null;
        if(key!=null)e.getToolTip().add(net.minecraft.network.chat.Component.translatable("tooltip.veilborne.lasting."+key).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
    /** Preview harness hooks. */
    static void debugVoyage(boolean ending){if(ending){voyageTo=ticks-VOYAGE_OUT/3;}else{voyageFrom=ticks-VOYAGE_IN-10;voyageTo=-1;voyageStaged=true;}}
    static void debugReveal(List<BlockPos> spots,int length){hidden=List.copyOf(spots);hiddenFrom=ticks-30;hiddenUntil=ticks+length;}
    static void debugBeam(Vec3 at){beams.clear();beams.add(new Beam(at,ticks-60));}
    private VeilClient(){}
}
