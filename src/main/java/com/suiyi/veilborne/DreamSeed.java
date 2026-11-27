package com.suiyi.veilborne;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 梦种: a seed of the dream forest, a honey-gold pod with a wisp of fluff, glowing. Two kinds. The wandering seed drifts in
 * slow loops near you during the leaf tide, round the crops if there are any; touch it and the vine enclosure grows round
 * you. The falling seeds come down with the golden leaves at the end: they sink slowly and then hover a little over the
 * ground, waiting. Crouch near one, or hold out a flower, a lantern or a flower lamp, and it follows your hands; look down
 * at open soil (grass, dirt, moss, mud, farmland) and bring it low, and it lands, sprouts, and is yours. Over stone, water or
 * a roof it will not come down. Left alone, after a while it floats away.
 */
public class DreamSeed extends Entity {
    public enum Kind { WANDER, FALL }
    public enum State { DRIFT, FOLLOW, LAND, OPEN, FADE }
    private static final EntityDataAccessor<Integer> KIND=SynchedEntityData.defineId(DreamSeed.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE=SynchedEntityData.defineId(DreamSeed.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> GUIDE=SynchedEntityData.defineId(DreamSeed.class,EntityDataSerializers.INT);
    public static final int LAND_TICKS=64, OPEN_TICKS=34, FADE_TICKS=50, WAIT_TICKS=1500;
    public static final double GUIDE_RANGE=7, HOVER=1.35, LANDING=.9;
    private Vec3 home=Vec3.ZERO;
    private int stateTicks,idle,stateClock,lured;
    private double phase;
    private final Glide glide=new Glide();

    public DreamSeed(EntityType<? extends DreamSeed> type,Level level){super(type,level);noPhysics=true;phase=random.nextDouble()*Math.PI*2;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){b.define(KIND,0);b.define(STATE,0);b.define(GUIDE,-1);}
    public void place(Kind kind,Vec3 at,Vec3 home){entityData.set(KIND,kind.ordinal());this.home=home;moveTo(at.x,at.y,at.z,0,0);setState(State.DRIFT);}
    public Kind kind(){return Kind.values()[Mth.clamp(entityData.get(KIND),0,1)];}
    public State state(){return State.values()[Mth.clamp(entityData.get(STATE),0,State.values().length-1)];}
    public int guide(){return entityData.get(GUIDE);}
    private void setState(State s){entityData.set(STATE,s.ordinal());stateTicks=0;}
    public int stateClock(){return stateClock;}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(key==STATE)stateClock=0;}

    /** Where a guide's cupped hands are: a little ahead of their eyes, along where they look, and lower. */
    public static Vec3 hands(Player p){return p.getEyePosition().add(p.getLookAngle().scale(1.5)).add(0,-.35,0);}
    /** Plantable, open ground under a point: the seed may come down here. */
    public static boolean soil(Level level,BlockPos below){
        BlockState s=level.getBlockState(below);
        boolean dirt=s.is(BlockTags.DIRT)||s.is(Blocks.FARMLAND)||s.is(Blocks.MUD)||s.is(Blocks.MOSS_BLOCK);
        // Open sky by the heightmap, not by sky light: light lags behind a roof just built, and is missing in fresh chunks.
        return dirt&&level.getBlockState(below.above()).canBeReplaced()&&level.getFluidState(below.above()).isEmpty()
            &&level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,below.getX(),below.getZ())<=below.getY()+1;
    }

    @Override public void tick(){
        super.tick();
        if(level().isClientSide){glide.step(this);stateClock++;phase+=.05;return;}
        ServerLevel level=(ServerLevel)level();
        DreamData d=DreamData.get(level);
        stateTicks++;
        State s=state();
        if(!d.active&&s!=State.LAND&&s!=State.FADE&&s!=State.OPEN){setState(State.FADE);s=State.FADE;}
        double t=tickCount*.05+phase;
        switch(s){
            case DRIFT -> {
                if(kind()==Kind.WANDER){
                    // Slow loops round its home, as a fish round a stone; round the crops if it found some.
                    Vec3 aim=home.add(Math.cos(t*.35)*2.6,.25*Math.sin(t*.9),Math.sin(t*.35)*2.6);
                    drift(aim,.06);
                    if(d.progress()>=DreamTimeline.WANDER_UNTIL){setState(State.FADE);break;}
                    for(ServerPlayer p:level.players()){
                        if(p.isSpectator()||p.getBoundingBox().inflate(.35).distanceToSqr(position())>DreamTimeline.TOUCH*DreamTimeline.TOUCH)continue;
                        touched(p);break;
                    }
                }else{
                    double ground=Surface.top(level,Mth.floor(getX()),Mth.floor(getZ()));
                    // It comes down at about a block a second, then hangs at hand height to be guided.
                    Vec3 aim=new Vec3(getX()+Math.sin(t*.7)*.4,Math.max(ground+HOVER,getY()-.7),getZ()+Math.cos(t*.6)*.4);
                    drift(aim,.05);
                    ServerPlayer g=findGuide(level);
                    if(g!=null){entityData.set(GUIDE,g.getId());setState(State.FOLLOW);break;}
                    if(getY()<ground+HOVER+.3&&++idle>WAIT_TICKS)setState(State.FADE);
                }
            }
            case FOLLOW -> {
                Entity e=level.getEntity(guide());
                if(!(e instanceof ServerPlayer p)||!p.isAlive()||!guiding(p)||p.position().distanceTo(position())>GUIDE_RANGE+3){
                    if(++lured>40){entityData.set(GUIDE,-1);setState(State.DRIFT);lured=0;}
                    drift(position(),.02);break;
                }
                lured=0;
                BlockPos below=BlockPos.containing(getX(),getY()-.1,getZ());
                double ground=Surface.top(level,below.getX(),below.getZ());
                boolean soil=soil(level,BlockPos.containing(getX(),ground-1,getZ()));
                Vec3 aim=hands(p);
                // It will not come down onto anything but soil: over stone, water or a roof it stays up.
                aim=new Vec3(aim.x,Math.max(aim.y,ground+(soil?.25:HOVER)),aim.z);
                drift(aim,.2);
                if(soil&&getY()-ground<LANDING){setState(State.LAND);setDeltaMovement(Vec3.ZERO);setPos(getX(),ground+.35,getZ());}
            }
            case LAND -> {
                if(stateTicks==LAND_TICKS){
                    Entity e=level.getEntity(guide());
                    DreamService.landed(level,this,e instanceof ServerPlayer p?p:null);
                    discard();return;
                }
            }
            case OPEN -> {
                Entity e=level.getEntity(guide());
                if(e!=null)drift(e.position().add(0,1,0),.25);
                if(stateTicks>=OPEN_TICKS){discard();return;}
            }
            case FADE -> {
                setPos(getX(),getY()+.03,getZ());
                if(stateTicks>=FADE_TICKS){discard();return;}
            }
        }
    }
    @Override public void lerpTo(double x,double y,double z,float yaw,float pitch,int steps){glide.to(x,y,z,yaw,steps);}
    @Override public double lerpTargetX(){return glide.x(this);}
    @Override public double lerpTargetY(){return glide.y(this);}
    @Override public double lerpTargetZ(){return glide.z(this);}
    private void drift(Vec3 aim,double speed){
        Vec3 d=aim.subtract(position());
        double l=d.length();
        Vec3 v=getDeltaMovement().scale(.82).add(l>1e-4?d.scale(Math.min(speed,l*.25)/l*.35):Vec3.ZERO);
        setDeltaMovement(v);
        setPos(getX()+v.x,getY()+v.y,getZ()+v.z);
    }
    /** Someone crouching near it, or holding out something it will follow. */
    public static boolean guiding(Player p){return p.isCrouching()||DreamService.lure(p);}
    private ServerPlayer findGuide(ServerLevel level){
        ServerPlayer best=null;double bd=GUIDE_RANGE;
        for(ServerPlayer p:level.players()){
            if(p.isSpectator()||!guiding(p))continue;
            double dd=hands(p).distanceTo(position());
            if(dd<bd){bd=dd;best=p;}
        }
        return best;
    }
    private void touched(ServerPlayer p){
        if(DreamService.openEnclosure((ServerLevel)level(),p,this)){entityData.set(GUIDE,p.getId());setState(State.OPEN);}
    }
    @Override public InteractionResult interact(Player player,InteractionHand hand){
        if(!level().isClientSide&&player instanceof ServerPlayer p&&kind()==Kind.WANDER&&state()==State.DRIFT)touched(p);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
    /** 0..1 how present it is. */
    public float visibility(float partial){
        float t=stateClock+partial;
        return switch(state()){
            case FADE -> Mth.clamp(1-t/FADE_TICKS,0,1);
            case OPEN -> Mth.clamp(1-t/OPEN_TICKS,0,1);
            default -> Mth.clamp((tickCount+partial)/30f,0,1);
        };
    }
    public void finish(){State s=state();if(s!=State.LAND&&s!=State.FADE)setState(State.FADE);}
    /** For the tests and the preview. */
    public void debugState(State s){if(state()==s)entityData.set(STATE,(s.ordinal()+1)%State.values().length);setState(s);}
    public void debugGuide(Player p){entityData.set(GUIDE,p==null?-1:p.getId());}
    @Override public boolean isPickable(){return kind()==Kind.WANDER&&state()==State.DRIFT;}
    @Override public boolean isPushable(){return false;}
    @Override public boolean canBeCollidedWith(){return false;}
    @Override public AABB getBoundingBoxForCulling(){return getBoundingBox().inflate(2);}
    @Override protected void readAdditionalSaveData(CompoundTag t){}
    @Override protected void addAdditionalSaveData(CompoundTag t){}
}
