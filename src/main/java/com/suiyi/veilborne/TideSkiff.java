package com.suiyi.veilborne;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A voluntary, short-lived ride. The fisherman is rendered as part of the boat. Held forward for a moment while aboard, the
 * boat turns the way its passenger looks and rises into the sea above: the voyage sets them down over the nearest thing the
 * sea has lost that way (see {@link TideService#voyage}), with a shell that brings them back to where they boarded.
 */
public class TideSkiff extends Entity {
    private static final EntityDataAccessor<Integer> FADE=SynchedEntityData.defineId(TideSkiff.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VOYAGE=SynchedEntityData.defineId(TideSkiff.class,EntityDataSerializers.INT);
    public static final int LIFE=2400,FADE_TICKS=40,VOYAGE_HOLD=30,VOYAGE_RISE=70;
    /** Blocks per tick at most, catching up with its loop; the loop itself is under a tenth of that. */
    private static final double MAX_STEP=.3;
    private int forward,voyage;
    private float heading;
    private BlockPos origin=BlockPos.ZERO;
    private int life;
    private int lerpSteps;
    private double lx,ly,lz;
    private float lyaw;
    public TideSkiff(EntityType<? extends TideSkiff> type,Level level){super(type,level);noPhysics=true;}
    public void setOrigin(BlockPos pos){origin=pos.immutable();setPos(pos.getX()+.5,pos.getY()+1.5,pos.getZ()+.5);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){b.define(FADE,0);b.define(VOYAGE,0);}
    /** Ticks into the voyage, 0 when there is none. */
    public int voyage(){return entityData.get(VOYAGE);}
    @Override protected void readAdditionalSaveData(CompoundTag t){origin=BlockPos.of(t.getLong("Origin"));life=t.getInt("Life");}
    @Override protected void addAdditionalSaveData(CompoundTag t){t.putLong("Origin",origin.asLong());t.putInt("Life",life);}
    @Override public boolean isPickable(){return !isFading();}
    @Override public boolean canBeCollidedWith(){return false;}
    @Override protected boolean canAddPassenger(Entity e){return getPassengers().isEmpty()&&!isFading();}
    public boolean isFading(){return entityData.get(FADE)>0;}
    /** 0..1 opacity: mist gathers into a boat on arrival and thins back to mist on departure. */
    public float visibility(float partial){
        int fade=entityData.get(FADE);
        if(fade>0)return Mth.clamp((fade-partial)/FADE_TICKS,0,1);
        return Mth.clamp((tickCount+partial)/FADE_TICKS,0,1);
    }
    @Override public AABB getBoundingBoxForCulling(){return getBoundingBox().inflate(2.5,3.5,2.5);}
    @Override public InteractionResult interact(Player p,InteractionHand hand){
        if(!level().isClientSide&&!p.isSecondaryUseActive()&&!isFading()){
            if(p.startRiding(this)){
                p.displayClientMessage(Component.translatable("message.veilborne.skiff"),true);
                if(p instanceof ServerPlayer sp){
                    TideService.record(sp,16);
                    TideData d=TideData.get(sp.serverLevel());
                    if(d.active)VeilGrade.deed(sp,VeilGrade.SEA,d.seed,VeilGrade.SEA_SKIFF);
                }
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
    /** The fore thwart, turned with the hull. */
    @Override public Vec3 getPassengerRidingPosition(Entity passenger){
        float yaw=getYRot()*Mth.DEG_TO_RAD;
        return position().add(-Mth.sin(yaw)*.26,.3,Mth.cos(yaw)*.26);
    }
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger){
        if(level() instanceof ServerLevel l){BlockPos safe=TideService.findSafe(l,blockPosition(),24);if(safe==null)safe=TideService.findSafe(l,origin,12);if(safe!=null)return safe.getBottomCenter();}
        passenger.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING,240,0));
        return position().add(0,1,0);
    }
    @Override public void lerpTo(double x,double y,double z,float yaw,float pitch,int steps){lx=x;ly=y;lz=z;lyaw=yaw;lerpSteps=steps;}
    @Override public void tick(){
        super.tick();
        if(level().isClientSide){
            if(lerpSteps>0){
                setPos(getX()+(lx-getX())/lerpSteps,getY()+(ly-getY())/lerpSteps,getZ()+(lz-getZ())/lerpSteps);
                setYRot(getYRot()+Mth.wrapDegrees(lyaw-getYRot())/lerpSteps);
                lerpSteps--;
            }
            Veilborne.skiffAmbience.accept(this);
            return;
        }
        int fade=entityData.get(FADE);
        if(fade>0){
            if(fade==1){discard();return;}
            entityData.set(FADE,fade-1);return;
        }
        if(voyage>0){sail();return;}
        if(getFirstPassenger() instanceof ServerPlayer p){
            forward=p.zza>.5f?forward+1:0;
            if(forward>=VOYAGE_HOLD)embark(p);
        }else forward=0;
        life++;
        if(life>=LIFE||!TideData.get((ServerLevel)level()).active){depart();return;}
        // Round its loop in about 48 seconds: up to nearly two blocks a second, still slower than a walk, so it can be caught.
        double angle=life*.0065;
        Vec3 target=origin.getBottomCenter().add(Math.sin(angle)*14,1.7+Math.sin(angle*1.8)*.35,(1-Math.cos(angle))*9);
        // Toward its place on the loop, never faster than it can sail: held up by something, it catches up afterwards
        // instead of leaping there in one tick, past whatever held it. Each step is judged where it lands.
        Vec3 step=target.subtract(position());
        if(step.length()>MAX_STEP)step=step.normalize().scale(MAX_STEP);
        Vec3 next=position().add(step);
        int ground=level().hasChunkAt(BlockPos.containing(next))?level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,Mth.floor(next.x),Mth.floor(next.z)):Integer.MAX_VALUE;
        // Rise gently over low obstacles instead of stalling; never climb onto roofs.
        if(ground>next.y&&ground<=origin.getY()+4)next=new Vec3(next.x,ground+1.2,next.z);
        step=next.subtract(position());
        if(ground<=next.y&&level().noCollision(getBoundingBox().move(step).inflate(.25,1,.25))){
            setPos(next);
            if(step.horizontalDistanceSqr()>1e-6)setYRot(Mth.rotLerp(.2f,getYRot(),(float)Math.toDegrees(Math.atan2(-step.x,step.z))));
        }
    }
    /** The passenger has held forward long enough: the old man turns the bow the way they look. */
    private void embark(ServerPlayer p){
        voyage=1;heading=p.getYRot();entityData.set(VOYAGE,1);forward=0;
        p.displayClientMessage(Component.translatable("message.veilborne.voyage_begin"),true);
        level().playSound(null,blockPosition(),Veilborne.VORTEX.get(),net.minecraft.sounds.SoundSource.AMBIENT,.8f,.8f);
        VeilService.cue(p,new VeilCue(VeilCue.VOYAGE,position(),1));
    }
    /** Up into the sea overhead, then across it. Stepping off on the way calls it off. */
    private void sail(){
        if(!(getFirstPassenger() instanceof ServerPlayer p)){
            voyage=0;entityData.set(VOYAGE,0);
            return;
        }
        voyage++;entityData.set(VOYAGE,voyage);
        double r=Math.toRadians(heading);
        setPos(getX()-Math.sin(r)*.14,getY()+.07,getZ()+Math.cos(r)*.14);
        setYRot(Mth.rotLerp(.15f,getYRot(),heading));
        if(voyage<VOYAGE_RISE)return;
        TideService.Voyage v=TideService.voyage((ServerLevel)level(),p.position(),heading);
        if(v==null){
            p.displayClientMessage(Component.translatable("message.veilborne.voyage_none"),false);
            VeilService.cue(p,new VeilCue(VeilCue.VOYAGE,position(),0));
            voyage=0;entityData.set(VOYAGE,0);
            return;
        }
        BlockPos home=TideService.findSafe((ServerLevel)level(),origin,12);
        TideService.arrive(p,v,home==null?origin:home);
        VeilService.cue(p,new VeilCue(VeilCue.VOYAGE,p.position(),0));
        discard();
    }
    /** Natural departure: let passengers down safely, then thin into mist. */
    public void depart(){
        release();
        if(!isRemoved()&&!isFading())entityData.set(FADE,FADE_TICKS);
    }
    /** Immediate removal, used when the weather is stopped. */
    public void finish(){release();discard();}
    private void release(){
        for(Entity passenger:java.util.List.copyOf(getPassengers())){
            Vec3 safe=passenger instanceof LivingEntity l?getDismountLocationForPassenger(l):origin.getBottomCenter();
            passenger.stopRiding();passenger.teleportTo(safe.x,safe.y,safe.z);
        }
    }
}
