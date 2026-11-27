package com.suiyi.veilborne;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 无声行列: five travellers in the clothes of another age walking the land in single file: one with a lantern in front, a
 * woman under an oiled-paper umbrella, a child carrying an empty birdcage, an old man with a stick, and a tall man with a
 * suitcase. Their mouths move; nothing is heard. One entity is the whole file; members follow the leader along a
 * deterministic road that server and client both evaluate. They never climb walls or go indoors: at an obstacle they are
 * rubbed out like a pencil drawing and drawn again past it. When the black sun covers the sun they stop and look up. Raise
 * a light to them and they stop, the lantern goes up in answer, and every face turns to you. They leave no footprints,
 * unless you carry a silent lantern.
 */
public class HushProcession extends Entity {
    public enum State { WALK, ERASE, HIDDEN, REDRAW, ANSWER, LOOK, DEPART }
    private static final EntityDataAccessor<BlockPos> START=SynchedEntityData.defineId(HushProcession.class,EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Float> HEADING=SynchedEntityData.defineId(HushProcession.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LENGTH=SynchedEntityData.defineId(HushProcession.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PATH=SynchedEntityData.defineId(HushProcession.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> STATE=SynchedEntityData.defineId(HushProcession.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SEED=SynchedEntityData.defineId(HushProcession.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> TOWARD=SynchedEntityData.defineId(HushProcession.class,EntityDataSerializers.BLOCK_POS);
    public static final double SPEED=.034, STEP=1.15, DROP=3.2, LENGTH_DEFAULT=118;
    public static final int ERASE_TICKS=34, HIDDEN_TICKS=40, REDRAW_TICKS=34, ANSWER_TICKS=100, DEPART_TICKS=90;
    /** Distance behind the leader and offset across the road: the lantern, the umbrella, the child, the stick, the suitcase. */
    public static final double[][] MEMBERS={{0,0},{2.5,-.35},{3.3,.55},{5.3,-.15},{7.6,.3}};
    private double s, target;
    private int stateTicks;
    private State resume=State.WALK;
    private double clientS, clientPrevS, syncedS;
    private int syncTick, stateClock;
    private boolean clientReady;

    public HushProcession(EntityType<? extends HushProcession> type,Level level){super(type,level);noPhysics=true;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){
        b.define(START,BlockPos.ZERO);b.define(HEADING,0f);b.define(LENGTH,(float)LENGTH_DEFAULT);b.define(PATH,0f);b.define(STATE,State.REDRAW.ordinal());
        b.define(SEED,0);b.define(TOWARD,BlockPos.ZERO);
    }
    public void setRoute(BlockPos start,double heading,double length,int seed){
        entityData.set(START,start.immutable());entityData.set(HEADING,(float)heading);entityData.set(LENGTH,(float)length);entityData.set(SEED,seed);
        s=0;entityData.set(PATH,0f);setState(State.REDRAW);
        Vec3 p=member(0,0,0);setPos(p.x,p.y,p.z);
    }
    public State state(){return State.values()[Mth.clamp(entityData.get(STATE),0,State.values().length-1)];}
    public double length(){return entityData.get(LENGTH);}
    public BlockPos toward(){return entityData.get(TOWARD);}
    private void setState(State st){entityData.set(STATE,st.ordinal());stateTicks=0;}
    private void setPath(double v,boolean now){s=v;if(now||tickCount%5==0)entityData.set(PATH,(float)v);}

    // ------------------------------------------------------------------ the road
    public Vec3 road(double at){
        BlockPos o=entityData.get(START);double h=entityData.get(HEADING);int seed=entityData.get(SEED);
        double ph=(seed&1023)*.0061,ph2=(seed>>10&1023)*.0061;
        double sway=2.6*Math.sin(at/19+ph)+1.1*Math.sin(at/8.1+ph2);
        double dx=Math.cos(h),dz=Math.sin(h);
        return new Vec3(o.getX()+.5+dx*at-dz*sway,0,o.getZ()+.5+dz*at+dx*sway);
    }
    public static double ground(Level level,double x,double z){
        double fx=x-.5,fz=z-.5;int x0=Mth.floor(fx),z0=Mth.floor(fz);double tx=fx-x0,tz=fz-z0;
        double a=Surface.top(level,x0,z0),b=Surface.top(level,x0+1,z0),c=Surface.top(level,x0,z0+1),d=Surface.top(level,x0+1,z0+1);
        return Mth.lerp(tz,Mth.lerp(tx,a,b),Mth.lerp(tx,c,d));
    }
    public Vec3 member(double leader,double back,double side){
        double at=Math.max(0,leader-back);
        Vec3 p=road(at),q=road(at+.6);
        Vec3 t=q.subtract(p).normalize();
        double x=p.x-t.z*side,z=p.z+t.x*side;
        return new Vec3(x,ground(level(),x,z),z);
    }
    public float yawAt(double leader,double back){
        double at=Math.max(0,leader-back);Vec3 t=road(at+.8).subtract(road(Math.max(0,at-.8)));
        return (float)Math.toDegrees(Math.atan2(-t.x,t.z));
    }
    /** A step up, a drop, water or lava, an unloaded chunk, or a roof over the road: the file does not go on through it. */
    boolean blocked(double at){
        Vec3 here=road(s),next=road(at),ahead=road(at+1.2);
        if(!level().hasChunkAt(BlockPos.containing(next))||!level().hasChunkAt(BlockPos.containing(ahead)))return true;
        double h0=ground(level(),here.x,here.z),h1=ground(level(),next.x,next.z),h2=ground(level(),ahead.x,ahead.z);
        if(h1-h0>STEP||h2-h0>STEP*1.6||h0-h2>DROP)return true;
        BlockPos floor=BlockPos.containing(next.x,h1-1,next.z);
        var st=level().getBlockState(floor);
        return st.is(Blocks.LAVA)||st.is(Blocks.MAGMA_BLOCK)||!level().getFluidState(floor).isEmpty();
    }
    /** First even, open stretch past an obstacle, or -1. */
    double skipTarget(double from){
        for(double t=from+3;t<Math.min(length(),from+34);t+=1){
            Vec3 p=road(t);if(!level().hasChunkAt(BlockPos.containing(p)))return -1;
            double h=ground(level(),p.x,p.z);
            BlockPos floor=BlockPos.containing(p.x,h-1,p.z);
            if(!level().getFluidState(floor).isEmpty())continue;
            boolean even=true;
            for(int k=1;k<=4&&even;k++){Vec3 q=road(t+k);even=Math.abs(ground(level(),q.x,q.z)-h)<=STEP;}
            if(even)return t;
        }
        return -1;
    }
    public double path(){return s;}
    /** Figures are standing in the world, not rubbed out. */
    public boolean present(){State st=state();return st!=State.HIDDEN&&st!=State.ERASE&&st!=State.DEPART;}
    /** Someone raised a light to them: they stop, and the lantern answers. Only while they are there to see it (a file being
     *  drawn back in counts: it finishes appearing as it turns). */
    public boolean answer(Vec3 from){
        State st=state();
        if(st!=State.WALK&&st!=State.LOOK&&st!=State.REDRAW)return false;
        resume=st==State.LOOK?State.LOOK:State.WALK;
        entityData.set(TOWARD,BlockPos.containing(from));setState(State.ANSWER);
        return true;
    }
    public boolean answering(){return state()==State.ANSWER;}
    /** The nearest member to a point, as a figure standing on the road. */
    public double nearest(Vec3 at){
        double best=1e9;
        for(double[] m:MEMBERS){Vec3 p=member(s,m[0],m[1]).add(0,1,0);best=Math.min(best,p.distanceTo(at));}
        return best;
    }

    // ------------------------------------------------------------------ behaviour
    @Override public void tick(){
        super.tick();
        if(level().isClientSide){clientTick();return;}
        HushData data=HushData.get((ServerLevel)level());
        stateTicks++;
        State st=state();
        double p=data.progress();
        if((!data.active||p>=HushTimeline.PROCESSION_UNTIL)&&st!=State.DEPART&&st!=State.ANSWER){setState(State.DEPART);st=State.DEPART;}
        boolean covered=HushTimeline.totality(p)>.2;
        switch(st){
            case WALK -> {
                if(covered){setState(State.LOOK);break;}
                double next=s+SPEED;
                if(next>=length()){setPath(length(),true);setState(State.DEPART);break;}
                if(blocked(next)){double t=skipTarget(s);if(t<0){setState(State.DEPART);}else{target=t;setState(State.ERASE);}break;}
                setPath(next,false);
            }
            case LOOK -> {if(!covered&&stateTicks>20)setState(State.WALK);}
            case ERASE -> {if(stateTicks>=ERASE_TICKS){setPath(target,true);setState(State.HIDDEN);}}
            case HIDDEN -> {if(stateTicks>=HIDDEN_TICKS)setState(State.REDRAW);}
            case REDRAW -> {if(stateTicks>=REDRAW_TICKS)setState(State.WALK);}
            case ANSWER -> {if(stateTicks>=ANSWER_TICKS)setState(data.active&&p<HushTimeline.PROCESSION_UNTIL?resume:State.DEPART);}
            case DEPART -> {if(stateTicks>=DEPART_TICKS){discard();return;}}
        }
        Vec3 lead=member(s,0,0);
        setPos(lead.x,lead.y,lead.z);
        setYRot(yawAt(s,0));
    }
    /** Preview harness: place the file at a point on its road in a given state. */
    public void preview(double at,State st){setPath(at,true);setState(st);Vec3 lead=member(s,0,0);setPos(lead.x,lead.y,lead.z);}
    public void finish(){if(state()!=State.DEPART)setState(State.DEPART);}

    // ------------------------------------------------------------------ client
    private void clientTick(){
        if(!clientReady){clientReady=true;clientS=clientPrevS=syncedS=entityData.get(PATH);syncTick=tickCount;}
        stateClock++;
        clientPrevS=clientS;
        boolean moving=state()==State.WALK;
        double estimate=syncedS+(moving?SPEED*(tickCount-syncTick):0);
        if(Math.abs(estimate-clientS)>2)clientS=clientPrevS=estimate;
        else clientS+=(moving?SPEED:0)+(estimate-clientS)*.2;
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){
        super.onSyncedDataUpdated(key);
        if(key==PATH){syncedS=entityData.get(PATH);syncTick=tickCount;if(Math.abs(syncedS-clientS)>2){clientS=clientPrevS=syncedS;}}
        if(key==STATE)stateClock=0;
    }
    public double renderPath(float partial){return clientPrevS+(clientS-clientPrevS)*partial;}
    public int stateClock(){return stateClock;}
    /** 0..1: rubbed out and drawn again, and faded into the pale distance as they leave. */
    public float visibility(float partial){
        float t=stateClock+partial;
        return switch(state()){
            case ERASE -> Mth.clamp(1-t/ERASE_TICKS,0,1);
            case HIDDEN -> 0;
            case REDRAW -> Mth.clamp(t/REDRAW_TICKS,0,1);
            case DEPART -> Mth.clamp(1-t/DEPART_TICKS,0,1);
            default -> 1;
        };
    }
    /** 0..1 how far the erasing has gone down the figures (1 = gone), for the eraser's line and its crumbs. */
    public float erased(float partial){
        float t=stateClock+partial;
        return switch(state()){
            case ERASE -> Mth.clamp(t/ERASE_TICKS,0,1);
            case HIDDEN -> 1;
            case REDRAW -> Mth.clamp(1-t/REDRAW_TICKS,0,1);
            default -> 0;
        };
    }

    // ------------------------------------------------------------------ plumbing
    @Override public boolean isPickable(){return false;}
    @Override public boolean isPushable(){return false;}
    @Override public boolean canBeCollidedWith(){return false;}
    @Override public AABB getBoundingBoxForCulling(){return getBoundingBox().inflate(12,4,12);}
    @Override public boolean shouldRenderAtSqrDistance(double d){return d<140*140;}
    @Override protected void readAdditionalSaveData(CompoundTag t){}
    @Override protected void addAdditionalSaveData(CompoundTag t){}

    /** Chooses a walkable road that passes the observer across their view at a quiet distance, or returns null. */
    public static HushProcession plan(ServerLevel level,BlockPos observer,long seed){
        HushProcession best=null;double bestScore=0;
        for(int k=0;k<12;k++){
            double h=(Math.floorMod(seed,12L)+k)*Math.PI*2/12;
            double dx=Math.cos(h),dz=Math.sin(h);
            // It passes about twelve blocks from the observer, halfway along its road.
            BlockPos start=BlockPos.containing(observer.getX()-dx*LENGTH_DEFAULT*.42+(-dz)*12,observer.getY(),observer.getZ()-dz*LENGTH_DEFAULT*.42+dx*12);
            HushProcession probe=Veilborne.HUSH_PROCESSION.get().create(level);
            if(probe==null)return null;
            probe.setRoute(start,h,LENGTH_DEFAULT,(int)(seed>>>3)+k*977);
            double score=0,prev=Double.NaN;int samples=0;
            for(double at=0;at<=LENGTH_DEFAULT;at+=2,samples++){
                Vec3 p=probe.road(at);
                if(!level.hasChunkAt(BlockPos.containing(p))){score-=2;continue;}
                double y=ground(level,p.x,p.z);
                boolean ok=Math.abs(y-observer.getY())<12&&(Double.isNaN(prev)||Math.abs(y-prev)<=STEP*2);
                BlockPos floor=BlockPos.containing(p.x,y-1,p.z);
                if(!level.getFluidState(floor).isEmpty()||level.getBlockState(floor).is(Blocks.LAVA))ok=false;
                if(ok)score++;
                prev=y;
            }
            score/=samples;
            if(!level.hasChunkAt(BlockPos.containing(probe.road(0))))continue;
            if(score>bestScore){bestScore=score;best=probe;}
        }
        return bestScore>=.55?best:null;
    }
}
