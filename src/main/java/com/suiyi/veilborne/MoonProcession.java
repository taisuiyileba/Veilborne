package com.suiyi.veilborne;

import java.util.List;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 送月人: faceless, veiled bearers carrying a piece of the old moon shell along a meandering road.
 * One entity is the whole procession; members walk at fixed distances behind the leader along the same
 * deterministic route, which server and client evaluate identically. They never climb walls or roofs:
 * at a building they stop outside, then scatter into petals and gather again past it. Anyone who walks
 * into the line scatters them the same way. At the road's end they offer the shell up to the moon and
 * the last bearer leaves a lantern by the roadside.
 */
public class MoonProcession extends Entity {
    public enum State { WALK, WAIT, SCATTER, HIDDEN, GATHER, ARRIVED, OFFER, DEPART }
    private static final EntityDataAccessor<BlockPos> START=SynchedEntityData.defineId(MoonProcession.class,EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Float> HEADING=SynchedEntityData.defineId(MoonProcession.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LENGTH=SynchedEntityData.defineId(MoonProcession.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PATH=SynchedEntityData.defineId(MoonProcession.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> STATE=SynchedEntityData.defineId(MoonProcession.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SEED=SynchedEntityData.defineId(MoonProcession.class,EntityDataSerializers.INT);
    public static final double SPEED=.032, INTRUDE=2.1, WALL=2.2, DROP=5;
    public static final int SCATTER_TICKS=30, HIDDEN_TICKS=50, GATHER_TICKS=30, DEPART_TICKS=60;
    /** Distance behind the leader and offset across the road: guide, four litter bearers, the rear lantern bearer. */
    public static final double[][] MEMBERS={{0,0},{3.4,-.74},{3.4,.74},{6.2,-.74},{6.2,.74},{11.6,0}};
    public static final double LITTER=4.8, EMPTY=9.0;
    private double s, target;
    private int stateTicks;
    // Client-side smoothing.
    private double clientS, clientPrevS, syncedS;
    private int syncTick, stateClock;
    private boolean clientReady;

    public MoonProcession(EntityType<? extends MoonProcession> type,Level level){super(type,level);noPhysics=true;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){
        b.define(START,BlockPos.ZERO);b.define(HEADING,0f);b.define(LENGTH,120f);b.define(PATH,0f);b.define(STATE,State.GATHER.ordinal());b.define(SEED,0);
    }
    public void setRoute(BlockPos start,double heading,double length,int seed){
        entityData.set(START,start.immutable());entityData.set(HEADING,(float)heading);entityData.set(LENGTH,(float)length);entityData.set(SEED,seed);
        s=0;entityData.set(PATH,0f);setState(State.GATHER);
        Vec3 p=member(0,0,0);setPos(p.x,p.y,p.z);
    }
    public State state(){return State.values()[Mth.clamp(entityData.get(STATE),0,State.values().length-1)];}
    public double length(){return entityData.get(LENGTH);}
    private void setState(State st){entityData.set(STATE,st.ordinal());stateTicks=0;}
    private void setPath(double v,boolean now){s=v;if(now||tickCount%5==0)entityData.set(PATH,(float)v);}

    // ------------------------------------------------------------------ the road
    public Vec3 road(double at){
        BlockPos o=entityData.get(START);double h=entityData.get(HEADING);int seed=entityData.get(SEED);
        double ph=(seed&1023)*.0061,ph2=(seed>>10&1023)*.0061;
        double sway=3.0*Math.sin(at/17+ph)+1.4*Math.sin(at/7.3+ph2);
        double dx=Math.cos(h),dz=Math.sin(h);
        return new Vec3(o.getX()+.5+dx*at-dz*sway,0,o.getZ()+.5+dz*at+dx*sway);
    }
    /** Ground under a point, interpolated between columns so the procession glides over steps instead of hopping. */
    public static double ground(Level level,double x,double z){
        double fx=x-.5,fz=z-.5;int x0=Mth.floor(fx),z0=Mth.floor(fz);double tx=fx-x0,tz=fz-z0;
        double a=column(level,x0,z0),b=column(level,x0+1,z0),c=column(level,x0,z0+1),d=column(level,x0+1,z0+1);
        return Mth.lerp(tz,Mth.lerp(tx,a,b),Mth.lerp(tx,c,d));
    }
    private static double column(Level level,int x,int z){return Surface.top(level,x,z);}
    /** A member's position: {@code back} blocks behind the leader along the road, {@code side} across it. */
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
    private boolean blocked(double at){
        Vec3 here=road(s),next=road(at);
        if(!level().hasChunkAt(BlockPos.containing(next))||!level().hasChunkAt(BlockPos.containing(road(at+1.5))))return true;
        double h0=ground(level(),here.x,here.z),h1=ground(level(),next.x,next.z);
        Vec3 ahead=road(at+1.5);double h2=ground(level(),ahead.x,ahead.z);
        if(h2-h0>WALL||h1-h0>WALL||h0-h2>DROP)return true;
        BlockPos floor=BlockPos.containing(next.x,h1-1,next.z);
        return level().getBlockState(floor).is(Blocks.LAVA)||level().getBlockState(floor).is(Blocks.MAGMA_BLOCK);
    }
    /** First smooth, open stretch past an obstacle, or -1. */
    private double skipTarget(double from){
        Vec3 here=road(s);double h0=ground(level(),here.x,here.z);
        for(double t=from+4;t<Math.min(length(),from+40);t+=1){
            Vec3 p=road(t);if(!level().hasChunkAt(BlockPos.containing(p)))return -1;
            double h=ground(level(),p.x,p.z);
            if(Math.abs(h-h0)>WALL*2)continue;
            boolean smooth=true;
            for(int k=1;k<=3&&smooth;k++){Vec3 q=road(t+k);smooth=Math.abs(ground(level(),q.x,q.z)-h)<=WALL;}
            if(smooth)return t;
        }
        return -1;
    }
    /** Anyone stepping into the line scatters it. Players only in play; tests may pass any entities. */
    public boolean intruded(List<? extends Entity> watchers){
        State st=state();
        if(st==State.SCATTER||st==State.HIDDEN||st==State.GATHER||st==State.DEPART)return false;
        for(Entity e:watchers){
            if(e.isSpectator())continue;
            for(double[] m:MEMBERS){
                Vec3 p=member(s,m[0],m[1]);
                if(Math.hypot(e.getX()-p.x,e.getZ()-p.z)<INTRUDE&&Math.abs(e.getY()-p.y)<3)return true;
            }
            Vec3 litter=member(s,LITTER,0);
            if(Math.hypot(e.getX()-litter.x,e.getZ()-litter.z)<INTRUDE&&Math.abs(e.getY()-litter.y)<3)return true;
        }
        return false;
    }
    private void scatter(double to){
        target=Math.max(s,Math.min(length(),to));setState(State.SCATTER);
        level().playSound(null,getX(),getY()+1,getZ(),Veilborne.MOON_SCATTER.get(),net.minecraft.sounds.SoundSource.AMBIENT,.8f,1);
    }
    public double path(){return s;}
    public boolean walking(){State st=state();return st==State.WALK||st==State.WAIT;}
    /** Figures are standing in the world (not dissolved into petals). */
    public boolean visibilityServer(){State st=state();return st==State.WALK||st==State.WAIT||st==State.ARRIVED||st==State.OFFER;}

    // ------------------------------------------------------------------ behaviour
    @Override public void tick(){
        super.tick();
        if(level().isClientSide){clientTick();return;}
        MoonData data=MoonData.get((ServerLevel)level());
        stateTicks++;
        State st=state();
        if(!data.active&&st!=State.DEPART){setState(State.DEPART);st=State.DEPART;}
        double p=data.progress();
        if(p>=.79&&st!=State.DEPART&&st!=State.OFFER){
            MoonService.leaveLantern((ServerLevel)level(),this);setState(State.DEPART);st=State.DEPART;
        }
        List<? extends Entity> watchers=level().players();
        switch(st){
            case WALK -> {
                if(intruded(watchers)){scatter(s+14);break;}
                double next=s+SPEED;
                if(next>=length()){setPath(length(),true);setState(State.ARRIVED);break;}
                if(blocked(next)){setState(State.WAIT);break;}
                setPath(next,false);
            }
            case WAIT -> {
                if(intruded(watchers)){double t=skipTarget(s);scatter(t<0?s:t);break;}
                if(!blocked(s+SPEED)){setState(State.WALK);break;}
                if(stateTicks>100){double t=skipTarget(s);if(t<0)setState(State.ARRIVED);else scatter(t);}
            }
            case SCATTER -> {if(stateTicks>=SCATTER_TICKS){setPath(target,true);setState(State.HIDDEN);}}
            case HIDDEN -> {if(stateTicks>=HIDDEN_TICKS)setState(State.GATHER);}
            case GATHER -> {if(stateTicks>=GATHER_TICKS)setState(s>=length()-.01?State.ARRIVED:State.WALK);}
            case ARRIVED -> {
                if(intruded(watchers))scatter(s);
                else if(p>=.6)setState(State.OFFER);
            }
            case OFFER -> {
                if(intruded(watchers)){scatter(s);break;}
                if(p>=.79){MoonService.leaveLantern((ServerLevel)level(),this);setState(State.DEPART);}
            }
            case DEPART -> {if(stateTicks>=DEPART_TICKS){discard();return;}}
        }
        Vec3 lead=member(s,0,0);
        setPos(lead.x,lead.y,lead.z);
        setYRot(yawAt(s,0));
    }
    /** Preview harness: place the procession at a point on its road in a given state. */
    public void preview(double at,State st){setPath(at,true);setState(st);Vec3 lead=member(s,0,0);setPos(lead.x,lead.y,lead.z);}
    /** Immediate removal when the weather is stopped. */
    public void finish(){discard();}

    // ------------------------------------------------------------------ client
    private void clientTick(){
        if(!clientReady){clientReady=true;clientS=clientPrevS=syncedS=entityData.get(PATH);syncTick=tickCount;}
        stateClock++;
        clientPrevS=clientS;
        boolean moving=state()==State.WALK;
        double estimate=syncedS+(moving?SPEED*(tickCount-syncTick):0);
        if(Math.abs(estimate-clientS)>2)clientS=clientPrevS=estimate;
        else clientS+=(moving?SPEED:0)+(estimate-clientS)*.2;
        Veilborne.processionAmbience.accept(this);
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){
        super.onSyncedDataUpdated(key);
        if(key==PATH){syncedS=entityData.get(PATH);syncTick=tickCount;if(Math.abs(syncedS-clientS)>2){clientS=clientPrevS=syncedS;}}
        if(key==STATE)stateClock=0;
    }
    public double renderPath(float partial){return clientPrevS+(clientS-clientPrevS)*partial;}
    public int stateClock(){return stateClock;}
    /** 0..1 visibility of the figures: they gather from petals and scatter back into them. */
    public float visibility(float partial){
        float t=stateClock+partial;
        return switch(state()){
            case SCATTER -> Mth.clamp(1-t/SCATTER_TICKS,0,1);
            case HIDDEN -> 0;
            case GATHER -> Mth.clamp(t/GATHER_TICKS,0,1);
            case DEPART -> Mth.clamp(1-t/DEPART_TICKS,0,1);
            default -> 1;
        };
    }

    // ------------------------------------------------------------------ plumbing
    @Override public boolean isPickable(){return false;}
    @Override public boolean isPushable(){return false;}
    @Override public boolean canBeCollidedWith(){return false;}
    @Override public AABB getBoundingBoxForCulling(){return getBoundingBox().inflate(15,5,15);}
    @Override public boolean shouldRenderAtSqrDistance(double d){return d<160*160;}
    @Override protected void readAdditionalSaveData(CompoundTag t){
        entityData.set(START,BlockPos.of(t.getLong("Start")));entityData.set(HEADING,t.getFloat("Heading"));entityData.set(LENGTH,t.getFloat("Length"));
        entityData.set(SEED,t.getInt("RouteSeed"));s=t.getDouble("Path");target=t.getDouble("Target");entityData.set(PATH,(float)s);
        entityData.set(STATE,Mth.clamp(t.getInt("State"),0,State.values().length-1));stateTicks=t.getInt("StateTicks");
    }
    @Override protected void addAdditionalSaveData(CompoundTag t){
        t.putLong("Start",entityData.get(START).asLong());t.putFloat("Heading",entityData.get(HEADING));t.putFloat("Length",entityData.get(LENGTH));
        t.putInt("RouteSeed",entityData.get(SEED));t.putDouble("Path",s);t.putDouble("Target",target);t.putInt("State",entityData.get(STATE));t.putInt("StateTicks",stateTicks);
    }
    /** Chooses a walkable road that passes the observer at a respectful distance, or returns null. */
    public static MoonProcession plan(ServerLevel level,BlockPos observer,long seed){
        MoonProcession best=null;double bestScore=0;
        for(int k=0;k<12;k++){
            double h=(seed%12+k)*Math.PI*2/12;
            double dx=Math.cos(h),dz=Math.sin(h);
            BlockPos start=BlockPos.containing(observer.getX()-dx*34+(-dz)*8,observer.getY(),observer.getZ()-dz*34+dx*8);
            MoonProcession probe=Veilborne.PROCESSION.get().create(level);
            if(probe==null)return null;
            probe.setRoute(start,h,112,(int)(seed>>>3)+k*977);
            double score=0,prev=Double.NaN;int samples=0;
            for(double at=0;at<=112;at+=2,samples++){
                Vec3 p=probe.road(at);
                if(!level.hasChunkAt(BlockPos.containing(p))){score-=2;continue;}
                double y=ground(level,p.x,p.z);
                boolean ok=Math.abs(y-observer.getY())<14&&(Double.isNaN(prev)||Math.abs(y-prev)<=WALL);
                BlockPos floor=BlockPos.containing(p.x,y-1,p.z);
                if(level.getBlockState(floor).is(Blocks.LAVA))ok=false;
                if(ok)score++;
                prev=y;
            }
            score/=samples;
            Vec3 first=probe.road(0);
            if(!level.hasChunkAt(BlockPos.containing(first)))continue;
            if(score>bestScore){bestScore=score;best=probe;}
        }
        return bestScore>=.6?best:null;
    }
}
