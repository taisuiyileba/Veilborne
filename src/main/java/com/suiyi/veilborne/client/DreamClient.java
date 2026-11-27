package com.suiyi.veilborne.client;

import com.suiyi.veilborne.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Client half of 苍翠梦潮: interpolated state, the soundscape (a warm wooden drone, far water, short reed and plucked phrases,
 * an uncommon bird, the roots creaking as they grow, the deer's voice and its hoof-falls), the hoof rings it leaves, the
 * flower patterns that open where pollen settles, what the land's height is round you (read by the light pass), the lamps,
 * crops and windows near you, and the jade fog.
 */
public final class DreamClient {
    public static DreamPacket state;
    public static float age,previousAge,intensity;
    public static int ticks,records;
    private static Level lastLevel;
    private static Drone drone;
    private static double lastProgress=-1;
    private static int phraseAt=500,birdAt=900,dripAt=240,rootAt=0,phrase;
    private static final RandomSource random=RandomSource.create();
    /** How open the sky is above you, and how far underground you are, 0..1. */
    static float exposure=1,under;
    /** A flower pattern opening on the land: where, when, which kind (see dream_post.fsh), how wide. */
    record Bloom(Vec3 at,int born,int kind,double radius,double seed){}
    static final List<Bloom> blooms=new ArrayList<>();
    /** The deer's hoof-falls on this client's land, worked out once its road is known. */
    static List<DreamTimeline.Footfall> falls=List.of();
    private static int fallsFor=-1,fallsDone,fallsUnresolved,fallsAt;
    static final List<BlockPos> lamps=new ArrayList<>(),crops=new ArrayList<>();
    static final List<MoonClient.Opening> openings=new ArrayList<>();
    /** The golden fish gathering: where and when. */
    record Merge(Vec3 at,int born){}
    static final List<Merge> merges=new ArrayList<>();
    /** The height of the land round the camera, one texel a block, for the light pass. */
    static final int MAP=128;
    static final float[] heights=new float[MAP*MAP];
    static int mapX,mapZ,mapRow=MAP;
    static boolean mapReady;
    /** When the local player last moved (for the tree that listens). */
    static int stillSince;private static Vec3 stillAt=Vec3.ZERO;

    public static void receive(DreamPacket p){
        if(state==null||state.seed()!=p.seed()){lastProgress=-1;blooms.clear();fallsFor=-1;falls=List.of();fallsDone=0;fallsUnresolved=0;DreamGrove.invalidate();}
        state=p;records=p.records();age=previousAge=p.age();
    }
    public static void cue(DreamCue c){
        var mc=Minecraft.getInstance();
        if(mc.player==null)return;
        switch(c.kind()){
            case DreamCue.BLOOM -> {
                blooms.add(new Bloom(c.at(),ticks,3,c.value()==1?6.5:c.value()==2?8:4.2,random.nextDouble()));
                for(int i=0;i<24;i++)mc.level.addParticle(Veilborne.DREAM_POLLEN.get(),c.x()+(random.nextDouble()-.5)*1.4,c.y()+.2+random.nextDouble()*.8,c.z()+(random.nextDouble()-.5)*1.4,(random.nextDouble()-.5)*.03,.02+random.nextDouble()*.03,(random.nextDouble()-.5)*.03);
            }
            case DreamCue.CHIME -> {
                for(int i=0;i<16;i++)mc.level.addParticle(Veilborne.DREAM_FIREFLY.get(),c.x(),c.y(),c.z(),(random.nextDouble()-.5)*.12,(random.nextDouble()-.3)*.1,(random.nextDouble()-.5)*.12);
                blooms.add(new Bloom(c.at().subtract(0,1.1,0),ticks,3,2.4,random.nextDouble()));
            }
            case DreamCue.MERGE -> merges.add(new Merge(c.at(),ticks));
            case DreamCue.SPROUT -> blooms.add(new Bloom(c.at(),ticks,0,c.value()==1?6:4.5,random.nextDouble()));
            default -> {}
        }
    }
    public static double progress(float partial){return state==null?0:DreamTimeline.progress(previousAge+(age-previousAge)*partial,state.duration());}
    public static double strength(){return intensity;}
    public static boolean active(){return state!=null&&intensity>.001;}
    static float ageNow(float partial){return previousAge+(age-previousAge)*partial;}
    public static double dayness(float partial){
        var level=Minecraft.getInstance().level;
        return level==null?0:Mth.clamp((level.getSkyDarken(partial)-.2)/.8,0,1);
    }
    /** The event's centre on its ground. */
    static Vec3 center(){return Vec3.atBottomCenterOf(state.center());}
    static void clear(){
        state=null;intensity=0;lastProgress=-1;blooms.clear();merges.clear();falls=List.of();fallsFor=-1;fallsDone=0;mapReady=false;
        if(drone!=null){drone.stop2();drone=null;}
        DreamFlock.clear();DreamGrove.invalidate();
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        if(mc.level!=lastLevel){
            if(lastLevel==null||mc.level==null)clear();else{blooms.clear();merges.clear();lamps.clear();crops.clear();openings.clear();mapReady=false;DreamFlock.clear();DreamGrove.invalidate();}
            lastLevel=mc.level;
        }
        if(mc.level==null||mc.player==null){clear();return;}
        if(mc.isPaused())return;
        ticks++;previousAge=age;
        if(ticks%20==0)scanLamps(mc);
        blooms.removeIf(b->ticks-b.born()>(b.kind()==3?160:900));
        merges.removeIf(m->ticks-m.born()>LeafSchool.MERGE_TICKS+LeafSchool.ASCEND_TICKS+20);
        if(state!=null&&state.active())age=Math.min(age+1,state.duration());
        double target=0;
        if(state!=null&&state.active()&&mc.level.dimension().equals(Level.OVERWORLD))
            target=DreamTimeline.region(Math.hypot(mc.player.getX()-state.center().getX(),mc.player.getZ()-state.center().getZ()),state.radius());
        intensity+=(target-intensity)*.05f;
        if(intensity<.001){intensity=0;if(drone!=null){drone.stop2();drone=null;}DreamFlock.clear();return;}
        double p=progress(1);
        if(ticks%10==0){
            BlockPos eye=BlockPos.containing(mc.player.getEyePosition());
            float e=mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING,eye.getX(),eye.getZ())<=eye.getY()?1:.35f;
            if(mc.player.isUnderWater())e=.15f;
            exposure+=(e-exposure)*.4f;
            float u=(float)TideTimeline.smooth(4,14,Surface.top(mc.level,eye.getX(),eye.getZ())-eye.getY());
            under+=(u-under)*.3f;
        }
        Vec3 me=mc.player.position();
        if(me.distanceToSqr(stillAt)>1.4){stillAt=me;stillSince=ticks;}
        sounds(mc,p);
        hooves(mc,p);
        DreamFlock.tick(mc,p);
        heightMap(mc);
        if(ticks%30==0){scanOpenings(mc);scanCrops(mc);}
        particles(mc,p);
    }
    /** Quietly waiting near the dream tree: the thing the deer answers. */
    static boolean listening(){
        var mc=Minecraft.getInstance();
        if(state==null||!state.hasTree()||mc.player==null)return false;
        return ticks-stillSince>=DreamService.QUIET_TICKS&&mc.player.position().distanceTo(Vec3.atBottomCenterOf(state.tree()))<DreamService.QUIET_RANGE;
    }

    // ------------------------------------------------------------------ the land's height round the camera
    /** A slice of rows each tick, re-centred when the camera has moved a way. */
    private static void heightMap(Minecraft mc){
        if(!TideConfig.DREAM_DAPPLE.get()&&!TideConfig.DREAM_PATTERNS.get())return;
        int cx=Mth.floor(mc.player.getX())-MAP/2,cz=Mth.floor(mc.player.getZ())-MAP/2;
        if(Math.abs(cx-mapX)>16||Math.abs(cz-mapZ)>16){
            // Shift what is kept so the map never flashes empty, then refill it.
            float[] old=heights.clone();int dx=cx-mapX,dz=cz-mapZ;
            for(int z=0;z<MAP;z++)for(int x=0;x<MAP;x++){int ox=x+dx,oz=z+dz;heights[z*MAP+x]=ox>=0&&oz>=0&&ox<MAP&&oz<MAP?old[oz*MAP+ox]:-1e4f;}
            mapX=cx;mapZ=cz;mapRow=0;
        }
        if(mapRow>=MAP){mapRow=0;mapReady=true;}
        var level=mc.level;
        for(int k=0;k<12&&mapRow<MAP;k++,mapRow++){
            int z=mapZ+mapRow;
            for(int x=0;x<MAP;x++){
                int wx=mapX+x;
                heights[mapRow*MAP+x]=level.hasChunk(wx>>4,z>>4)?Surface.top(level,wx,z):-1e4f;
            }
        }
    }

    // ------------------------------------------------------------------ the deer's hoof-falls
    private static void hooves(Minecraft mc,double p){
        if(state==null||!state.hasRoute())return;
        // Worked out once the road is known, and again now and then while some of it lies on land not yet loaded here.
        if(fallsFor!=state.routeAt()||fallsUnresolved>0&&ticks-fallsAt>100){
            var level=mc.level;double y0=state.route().getY();
            boolean fresh=fallsFor!=state.routeAt();
            fallsUnresolved=0;
            falls=DreamTimeline.footfalls(state.routeValue(),(x,z)->{
                if(level.hasChunk(Mth.floor(x)>>4,Mth.floor(z)>>4))return Surface.top(level,Mth.floor(x),Mth.floor(z));
                fallsUnresolved++;return y0;
            });
            fallsFor=state.routeAt();fallsAt=ticks;
            if(fresh){fallsDone=0;while(fallsDone<falls.size()&&falls.get(fallsDone).p()<p-.002)fallsDone++;}
        }
        Vec3 me=mc.player.position();
        while(fallsDone<falls.size()&&falls.get(fallsDone).p()<=p){
            DreamTimeline.Footfall f=falls.get(fallsDone++);
            double d=f.at().distanceTo(me);
            if(TideConfig.SOUND.get()&&d<260)
                mc.getSoundManager().play(new SimpleSoundInstance(Veilborne.DREAM_STEP.get().getLocation(),SoundSource.AMBIENT,(float)(volume()*(.5+.5*exposure)*1.5),.9f+random.nextFloat()*.12f,RandomSource.create(),false,0,SoundInstance.Attenuation.LINEAR,f.at().x,f.at().y+2,f.at().z,false));
            if(d<60)for(int i=0;i<40;i++){
                double a=random.nextDouble()*Math.PI*2,r=random.nextDouble()*7;
                mc.level.addParticle(Veilborne.DREAM_POLLEN.get(),f.at().x+Math.cos(a)*r,f.at().y+.3,f.at().z+Math.sin(a)*r,0,.03+random.nextDouble()*.05,0);
            }
        }
    }

    // ------------------------------------------------------------------ small things in the air near you
    private static void particles(Minecraft mc,double p){
        var level=mc.level;Vec3 me=mc.player.position();
        int q=TideConfig.QUALITY.get();
        // Seeds of light rising out of the grass.
        double fluff=DreamTimeline.fluff(p)*intensity;
        int n=(int)(fluff*new int[]{2,4,7}[q]+random.nextDouble());
        for(int i=0;i<n;i++){
            double a=random.nextDouble()*Math.PI*2,r=2+random.nextDouble()*14;
            int x=Mth.floor(me.x+Math.cos(a)*r),z=Mth.floor(me.z+Math.sin(a)*r);
            int g=Surface.top(level,x,z);
            BlockState below=level.getBlockState(new BlockPos(x,g-1,z));
            if(!below.is(BlockTags.DIRT)&&!below.is(Blocks.GRASS_BLOCK)&&!(below.getBlock() instanceof LeavesBlock))continue;
            level.addParticle(Veilborne.DREAM_FLUFF.get(),x+random.nextDouble(),g+.1,z+random.nextDouble(),(random.nextDouble()-.5)*.01,.012+random.nextDouble()*.012,(random.nextDouble()-.5)*.01);
        }
        // Fireflies where it is shaded, day and night.
        double ff=DreamTimeline.fireflies(p)*intensity*(.35+.65*(1-dayness(1)));
        n=(int)(ff*new int[]{1,2,4}[q]+random.nextDouble()*.6);
        for(int i=0;i<n;i++){
            double a=random.nextDouble()*Math.PI*2,r=3+random.nextDouble()*16;
            double x=me.x+Math.cos(a)*r,z=me.z+Math.sin(a)*r;
            int g=Surface.top(level,Mth.floor(x),Mth.floor(z));
            level.addParticle(Veilborne.DREAM_FIREFLY.get(),x,g+.5+random.nextDouble()*3,z,0,0,0);
        }
        // The golden leaf-fall.
        double gf=DreamTimeline.goldFall(p)*intensity;
        n=(int)(gf*new int[]{3,6,10}[q]+random.nextDouble());
        for(int i=0;i<n;i++){
            double a=random.nextDouble()*Math.PI*2,r=random.nextDouble()*18;
            double x=me.x+Math.cos(a)*r,z=me.z+Math.sin(a)*r;
            level.addParticle(Veilborne.DREAM_LEAF.get(),x,me.y+10+random.nextDouble()*12,z,0,-.04,0);
        }
        // Falling with the drifting leaf: you come down in a swirl of leaves.
        var pl=mc.player;
        if(!pl.onGround()&&pl.hasEffect(MobEffects.SLOW_FALLING)&&pl.getInventory().contains(new net.minecraft.world.item.ItemStack(Veilborne.DRIFTING_LEAF.get()))&&ticks%2==0)
            level.addParticle(Veilborne.DREAM_LEAF.get(),pl.getX()+(random.nextDouble()-.5)*1.2,pl.getY()+random.nextDouble()*1.6,pl.getZ()+(random.nextDouble()-.5)*1.2,(random.nextDouble()-.5)*.05,-.02,(random.nextDouble()-.5)*.05);
    }

    // ------------------------------------------------------------------ sound
    private static float volume(){return (float)(TideConfig.SOUND_VOLUME.get()*intensity);}
    private static boolean crossed(double cue,double p){return lastProgress<cue&&p>=cue;}
    static void play(Minecraft mc,SoundEvent event,float volume,float pitch,Vec3 at){
        mc.getSoundManager().play(new SimpleSoundInstance(event.getLocation(),SoundSource.WEATHER,volume,pitch,RandomSource.create(),false,0,SoundInstance.Attenuation.NONE,at.x,at.y,at.z,false));
    }
    private static void sounds(Minecraft mc,double p){
        boolean on=TideConfig.SOUND.get();
        if(on&&drone==null){drone=new Drone();mc.getSoundManager().play(drone);}
        if(!on&&drone!=null){drone.stop2();drone=null;}
        Vec3 eye=mc.player.getEyePosition();
        if(on&&lastProgress>=0){
            double far=Math.floorMod(state.seed()>>>9,360L)*Math.PI/180;
            Vec3 away=eye.add(Math.cos(far)*80,30,Math.sin(far)*80);
            if(crossed(.004,p))play(mc,Veilborne.DREAM_OMEN.get(),volume()*.9f,1,away);
            if(crossed(DreamTimeline.ROOT0,p))play(mc,Veilborne.DREAM_ROOT.get(),volume()*.9f,1,eye.add(0,60,0));
            if(crossed(DreamTimeline.ARRIVED,p))play(mc,Veilborne.DREAM_BLOOM.get(),volume()*.8f,.8f,eye.add(0,8,0));
            if(crossed(DreamTimeline.ROAD,p))play(mc,Veilborne.DREAM_ROOT.get(),volume(),.8f,roadSound(eye));
            if(crossed(DreamTimeline.DEER_START+.1/13,p))play(mc,Veilborne.DREAM_CALL.get(),volume(),1,deerSound(eye,1));
            if(crossed(DreamTimeline.stopFrom()+.02*(DreamTimeline.DEER_END-DreamTimeline.DEER_START),p))play(mc,Veilborne.DREAM_CALL.get(),volume()*.8f,1.12f,deerSound(eye,1));
            if(crossed(9.55/13,p))mc.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(Veilborne.DREAM_FALL.get(),1,volume()*.8f));
            if(crossed(11.55/13,p))mc.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(Veilborne.DREAM_RETURN.get(),1,volume()*.85f));
            // The roots creak now and then while they grow.
            if(p>DreamTimeline.ROOT0&&p<DreamTimeline.ROOT1&&ticks-rootAt>260+random.nextInt(200)){rootAt=ticks;play(mc,Veilborne.DREAM_ROOT.get(),volume()*.55f,.9f+random.nextFloat()*.2f,eye.add((random.nextDouble()-.5)*120,50,(random.nextDouble()-.5)*120));}
        }
        lastProgress=p;
        if(!on)return;
        double canopy=DreamTimeline.sky(p);
        // Short reed and plucked phrases, a long while apart; never over the deer's glance.
        if(p>1.4/13&&p<12/13.0&&DreamTimeline.gaze(DreamTimeline.walk(p))<.05&&--phraseAt<=0){
            var list=Veilborne.DREAM_PHRASES;
            phrase=(phrase+1+random.nextInt(2))%list.size();
            mc.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(list.get(phrase).get(),.98f+random.nextFloat()*.04f,volume()*(.45f+.25f*exposure)*(1-under*.8f)));
            phraseAt=560+random.nextInt(640);
        }
        // An uncommon bird, somewhere in the trees.
        if(canopy>.3&&under<.5&&--birdAt<=0){
            double a=random.nextDouble()*Math.PI*2;
            Vec3 at=eye.add(Math.cos(a)*18,6+random.nextDouble()*8,Math.sin(a)*18);
            mc.getSoundManager().play(new SimpleSoundInstance(Veilborne.DREAM_BIRD.get().getLocation(),SoundSource.AMBIENT,volume()*.7f,.94f+random.nextFloat()*.12f,RandomSource.create(),false,0,SoundInstance.Attenuation.LINEAR,at.x,at.y,at.z,false));
            birdAt=700+random.nextInt(900);
        }
        // Far water dripping, from the roots perhaps.
        if(canopy>.2&&--dripAt<=0){
            double a=random.nextDouble()*Math.PI*2;
            Vec3 at=eye.add(Math.cos(a)*14,-2+random.nextDouble()*10,Math.sin(a)*14);
            mc.getSoundManager().play(new SimpleSoundInstance(Veilborne.DREAM_DRIP.get().getLocation(),SoundSource.AMBIENT,volume()*.35f,.8f+random.nextFloat()*.4f,RandomSource.create(),false,0,SoundInstance.Attenuation.LINEAR,at.x,at.y,at.z,false));
            dripAt=180+random.nextInt(360);
        }
    }
    private static Vec3 roadSound(Vec3 eye){
        if(state==null||!state.hasRoute())return eye.add(0,60,0);
        Vec3 r=state.routeValue().at(40);return eye.add(r.subtract(eye).normalize().scale(90)).add(0,40,0);
    }
    private static Vec3 deerSound(Vec3 eye,float partial){
        Vec3 head=DeerModel.headPoint(partial);
        if(head==null)return eye.add(0,40,0);
        Vec3 d=head.subtract(eye);double l=d.length();
        return l<90?head:eye.add(d.scale(90/l));
    }
    private static final class Drone extends AbstractTickableSoundInstance {
        Drone(){super(Veilborne.DREAM_DRONE.get(),SoundSource.WEATHER,RandomSource.create());looping=true;delay=0;relative=true;attenuation=Attenuation.NONE;volume=.01f;}
        @Override public void tick(){
            if(Minecraft.getInstance().level==null||!TideConfig.SOUND.get()||intensity<=.001f){stop();return;}
            double p=progress(1);
            double body=Math.max(DreamTimeline.fluff(p)*.4,DreamTimeline.sky(p));
            volume=(float)(intensity*.62*body*(.45+.55*exposure)*(1-.55*under)*TideConfig.SOUND_VOLUME.get());
            pitch=(float)(1-.06*DreamTimeline.gold(p));
        }
        void stop2(){stop();}
    }
    /** Underground the forest is only a low hum of roots, far overhead. */
    private static RootHum hum;
    private static final class RootHum extends AbstractTickableSoundInstance {
        RootHum(){super(Veilborne.DREAM_HUM.get(),SoundSource.WEATHER,RandomSource.create());looping=true;delay=0;relative=true;attenuation=Attenuation.NONE;volume=.01f;}
        @Override public void tick(){
            if(Minecraft.getInstance().level==null||!TideConfig.SOUND.get()||intensity<=.001f){stop();hum=null;return;}
            volume=(float)(intensity*under*.5*DreamTimeline.sky(progress(1))*TideConfig.SOUND_VOLUME.get());
        }
    }
    @SubscribeEvent public static void humTick(ClientTickEvent.Post e){
        var mc=Minecraft.getInstance();
        if(mc.level==null||!TideConfig.SOUND.get())return;
        if(hum==null&&intensity>.01&&under>.2){hum=new RootHum();mc.getSoundManager().play(hum);}
    }

    // ------------------------------------------------------------------ scanning
    /** Flower lamps near you, at any time: their little schools circle them. */
    private static void scanLamps(Minecraft mc){
        lamps.clear();
        BlockPos o=mc.player.blockPosition();var level=mc.level;
        BlockPos.MutableBlockPos m=new BlockPos.MutableBlockPos();
        for(int x=-16;x<=16;x++)for(int z=-16;z<=16;z++){
            if(!level.hasChunk((o.getX()+x)>>4,(o.getZ()+z)>>4))continue;
            for(int y=-6;y<=8;y++){
                m.set(o.getX()+x,o.getY()+y,o.getZ()+z);
                BlockState s=level.getBlockState(m);
                if(s.is(Veilborne.FLOWER_LAMP.get())&&s.getValue(DreamBlocks.SWARM)&&lamps.size()<10)lamps.add(m.immutable());
            }
        }
    }
    /** Crops near you, round which dream seeds swim slowly while the leaves are out. */
    private static void scanCrops(Minecraft mc){
        crops.clear();
        if(!active())return;
        BlockPos o=mc.player.blockPosition();var level=mc.level;
        for(BlockPos b:BlockPos.betweenClosed(o.offset(-10,-3,-10),o.offset(10,3,10))){
            if(level.getBlockState(b).is(BlockTags.CROPS)&&level.getBlockState(b.below()).is(Blocks.FARMLAND)&&(b.getX()*7+b.getZ()*13&3)==0){crops.add(b.immutable());if(crops.size()>=10)return;}
        }
    }
    private static boolean roofless(Level level,BlockPos p){return level.getHeight(Heightmap.Types.MOTION_BLOCKING,p.getX(),p.getZ())<=p.getY();}
    /** Window sills seen from inside, where a leaf-fish sometimes stops to rest. */
    private static void scanOpenings(Minecraft mc){
        openings.clear();
        if(!active())return;
        var level=mc.level;BlockPos o=BlockPos.containing(mc.player.getEyePosition());
        if(roofless(level,o))return;
        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)for(int y=-2;y<=1;y++){
            BlockPos pos=o.offset(x,y,z);
            if(!level.getBlockState(pos).isAir()||roofless(level,pos))continue;
            for(Direction face:Direction.Plane.HORIZONTAL){
                BlockPos n=pos.relative(face);var st=level.getBlockState(n);
                boolean window=st.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS)||st.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_PANES);
                if(window&&roofless(level,n.relative(face))){openings.add(new MoonClient.Opening(pos,face));break;}
            }
            if(openings.size()>=12)return;
        }
    }

    @SubscribeEvent public static void fog(ViewportEvent.ComputeFogColor e){
        if(state==null||intensity<.001)return;
        double p=progress((float)e.getPartialTick());
        // Far land fades into the same jade haze the canopy fades into at the horizon, so nothing stands out pale against it.
        double s=intensity*DreamTimeline.sky(p)*.92*(1-.6*DreamTimeline.veinsFade(p));
        if(s<=0)return;
        double day=dayness((float)e.getPartialTick()),gold=DreamTimeline.gold(p);
        float r=(float)Mth.lerp(day,.03,.6+.1*gold),g=(float)Mth.lerp(day,.08,.75),b=(float)Mth.lerp(day,.075,.62-.12*gold);
        e.setRed((float)(e.getRed()*(1-s)+r*s));e.setGreen((float)(e.getGreen()*(1-s)+g*s));e.setBlue((float)(e.getBlue()*(1-s)+b*s));
    }
    private DreamClient(){}
}
