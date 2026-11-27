package com.suiyi.veilborne;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 迟到的旅人: a pale likeness of a player walking a few seconds of the path that player took a while ago, where they
 * took it. At the end of the real path it goes on a few steps further, toward something worth finding, turns and
 * points, then fades. It copies only walking: no attacks, no containers, nothing dropped.
 */
public class MirrorTraveler extends Entity {
    public enum State { REPLAY, EXTRA, POINT, FADE }
    private static final EntityDataAccessor<Optional<UUID>> OWNER=SynchedEntityData.defineId(MirrorTraveler.class,EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> STATE=SynchedEntityData.defineId(MirrorTraveler.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> TARGET=SynchedEntityData.defineId(MirrorTraveler.class,EntityDataSerializers.BLOCK_POS);
    public static final int POINT_TICKS=70, FADE_TICKS=30, APPEAR_TICKS=16;
    public static final double WALK=.16;
    /** Server: the remembered path, one sample per tick, then the extra steps. */
    private final List<Vec3> path=new ArrayList<>();
    private int replayLength, index, stateTicks;
    // Client: distance walked, for the stride.
    private float stride, prevStride, speed;
    private int clientTicks;
    private final Glide glide=new Glide();

    public MirrorTraveler(EntityType<? extends MirrorTraveler> type,Level level){super(type,level);noPhysics=true;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){b.define(OWNER,Optional.empty());b.define(STATE,0);b.define(TARGET,BlockPos.ZERO);}
    public Optional<UUID> owner(){return entityData.get(OWNER);}
    public State state(){return State.values()[Mth.clamp(entityData.get(STATE),0,3)];}
    public BlockPos target(){return entityData.get(TARGET);}
    private void setState(State s){entityData.set(STATE,s.ordinal());stateTicks=0;}

    /** Sets up the walk: {@code replay} is the remembered stretch, then up to {@code extra} blocks on toward {@code target}. */
    public void plan(UUID who,List<Vec3> replay,BlockPos goal,double extra){
        entityData.set(OWNER,Optional.of(who));entityData.set(TARGET,goal.immutable());
        path.clear();path.addAll(replay);replayLength=path.size();
        Vec3 end=path.get(path.size()-1);
        Vec3 dir=goal.equals(BlockPos.ZERO)?end.subtract(path.get(Math.max(0,path.size()-10))):Vec3.atBottomCenterOf(goal).subtract(end);
        dir=new Vec3(dir.x,0,dir.z);
        if(dir.lengthSqr()<1e-4)dir=new Vec3(1,0,0);
        dir=dir.normalize();
        double reach=goal.equals(BlockPos.ZERO)?extra:Math.min(extra,Math.max(0,Math.hypot(goal.getX()+.5-end.x,goal.getZ()+.5-end.z)-2.5));
        Vec3 at=end;
        for(double s=WALK;s<=reach;s+=WALK){
            Vec3 next=end.add(dir.scale(s));
            double y=Surface.top(level(),Mth.floor(next.x),Mth.floor(next.z));
            if(Math.abs(y-at.y)>1.3)break;
            at=new Vec3(next.x,Mth.lerp(.35,at.y,y),next.z);
            path.add(at);
        }
        index=0;setState(State.REPLAY);
        Vec3 first=path.get(0);setPos(first.x,first.y,first.z);
    }
    public Vec3 end(){return path.isEmpty()?position():path.get(path.size()-1);}

    @Override public void tick(){
        super.tick();
        if(level().isClientSide){clientTick();return;}
        stateTicks++;
        MirrorData d=MirrorData.get((net.minecraft.server.level.ServerLevel)level());
        if(path.isEmpty()||!d.active&&state()!=State.FADE){if(state()!=State.FADE)setState(State.FADE);}
        switch(state()){
            case REPLAY,EXTRA -> {
                if(index>=replayLength&&state()==State.REPLAY)setState(State.EXTRA);
                if(index>=path.size()-1){setState(State.POINT);break;}
                index++;
                Vec3 p=path.get(index),q=path.get(Math.max(0,index-1));
                setPos(p.x,p.y,p.z);
                if(p.distanceToSqr(q)>1e-5)setYRot((float)Math.toDegrees(Math.atan2(-(p.x-q.x),p.z-q.z)));
            }
            case POINT -> {
                BlockPos t=target();
                if(!t.equals(BlockPos.ZERO)){Vec3 to=Vec3.atCenterOf(t).subtract(position());setYRot((float)Math.toDegrees(Math.atan2(-to.x,to.z)));}
                if(stateTicks>=POINT_TICKS)setState(State.FADE);
            }
            case FADE -> {if(stateTicks>=FADE_TICKS)discard();}
        }
    }
    @Override public void lerpTo(double x,double y,double z,float yaw,float pitch,int steps){glide.to(x,y,z,yaw,steps);}
    @Override public double lerpTargetX(){return glide.x(this);}
    @Override public double lerpTargetY(){return glide.y(this);}
    @Override public double lerpTargetZ(){return glide.z(this);}
    private void clientTick(){
        glide.step(this);
        clientTicks++;
        prevStride=stride;
        double moved=Math.hypot(getX()-xo,getZ()-zo);
        stride+=(float)moved*4.2f;
        speed+=(Math.min(1,(float)moved*6.5f)-speed)*.35f;
        Veilborne.travelerAmbience.accept(this);
    }
    public float stride(float partial){return Mth.lerp(partial,prevStride,stride);}
    public float speed(){return speed;}
    public int clientTicks(){return clientTicks;}
    /** 0..1 how solid the likeness looks: it thickens out of nothing and thins away again. */
    public float visibility(float partial){
        float appear=Mth.clamp((clientTicks+partial)/APPEAR_TICKS,0,1);
        return state()==State.FADE?appear*Mth.clamp(1-(stateTicksClient+partial)/FADE_TICKS,0,1):appear;
    }
    private int stateTicksClient;
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(key==STATE)stateTicksClient=0;}
    @Override public void baseTick(){super.baseTick();if(level().isClientSide)stateTicksClient++;}
    /** 0..1 raising the arm to point. */
    public float pointing(float partial){return state()==State.POINT?Mth.clamp((stateTicksClient+partial)/14f,0,1):state()==State.FADE?Mth.clamp(1-(stateTicksClient+partial)/10f,0,1):0;}

    @Override public boolean isPickable(){return false;}
    @Override public boolean isPushable(){return false;}
    @Override public boolean shouldBeSaved(){return false;}
    @Override public AABB getBoundingBoxForCulling(){return getBoundingBox().inflate(2);}
    @Override protected void readAdditionalSaveData(CompoundTag t){}
    @Override protected void addAdditionalSaveData(CompoundTag t){}
}
