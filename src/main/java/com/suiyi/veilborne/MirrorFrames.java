package com.suiyi.veilborne;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 错位路标: three standing mirrors in an arc around one spot, each truly reflecting the ground and whoever stands
 * before it. Only one reflects the present. One is late: the people in it move a beat behind. One reflects
 * somewhere else: warmer light, and someone standing behind you who is not there. Observe the present one
 * through the prism and the other two break into harmless light.
 */
public class MirrorFrames extends Entity {
    public enum State { WAITING, SOLVED, DEPART }
    public static final int PRESENT=0, LATE=1, ELSEWHERE=2;
    private static final EntityDataAccessor<Integer> STATE=SynchedEntityData.defineId(MirrorFrames.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> KINDS=SynchedEntityData.defineId(MirrorFrames.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> WRONG=SynchedEntityData.defineId(MirrorFrames.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> FACING=SynchedEntityData.defineId(MirrorFrames.class,EntityDataSerializers.FLOAT);
    public static final double RADIUS=3.4, WIDTH=1.125, HEIGHT=2.125, LIFT=.5;
    public static final double[] ANGLES={-50,0,50};
    public static final int DEPART_TICKS=40;
    private int stateTicks, clientStateTicks;

    public MirrorFrames(EntityType<? extends MirrorFrames> type,Level level){super(type,level);noPhysics=true;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){b.define(STATE,0);b.define(KINDS,0|1<<2|2<<4);b.define(WRONG,-1);b.define(FACING,0f);}
    public State state(){return State.values()[Mth.clamp(entityData.get(STATE),0,2)];}
    private void setState(State s){entityData.set(STATE,s.ordinal());stateTicks=0;}
    /** Kind of frame i: PRESENT, LATE or ELSEWHERE. */
    public int kind(int i){return entityData.get(KINDS)>>(i*2)&3;}
    public int present(){for(int i=0;i<3;i++)if(kind(i)==PRESENT)return i;return 0;}
    /** Last wrong guess as frame index, or -1. */
    public int wrong(){int w=entityData.get(WRONG);return w<0?-1:w&3;}
    public double facing(){return entityData.get(FACING);}
    public void setup(double yaw,long seed){
        entityData.set(FACING,(float)yaw);
        int[][] orders={{0,1,2},{0,2,1},{1,0,2},{1,2,0},{2,0,1},{2,1,0}};
        int[] o=orders[(int)Math.floorMod(seed>>>5,6L)];
        entityData.set(KINDS,o[0]|o[1]<<2|o[2]<<4);
        setState(State.WAITING);
    }
    /** Direction the arc opens toward, from the spot out to the middle mirror. */
    public Vec3 axis(){double a=facing();return new Vec3(Math.cos(a),0,Math.sin(a));}
    /** Base centre of frame i (on the ground). */
    public Vec3 framePos(int i){
        double a=facing()+Math.toRadians(ANGLES[i]);
        return position().add(Math.cos(a)*RADIUS,0,Math.sin(a)*RADIUS);
    }
    /** Unit normal of frame i's glass: it faces back toward the spot. */
    public Vec3 normal(int i){Vec3 d=position().subtract(framePos(i));return new Vec3(d.x,0,d.z).normalize();}
    /** Centre of frame i's glass. */
    public Vec3 glass(int i){return framePos(i).add(0,LIFT+HEIGHT/2,0).add(normal(i).scale(.06));}

    /** Observed through the prism. Right: the others break and leave mirror sand. Wrong: it says so, nothing else. */
    public void observe(int i,ServerPlayer p){
        if(state()!=State.WAITING)return;
        ServerLevel level=(ServerLevel)level();
        if(kind(i)==PRESENT){
            setState(State.SOLVED);
            TideService.record(p,MirrorService.FRAMES);
            VeilGrade.deed(p,VeilGrade.MIRROR,MirrorData.get(level).seed,VeilGrade.MIRROR_FRAMES);
            level.playSound(null,BlockPos.containing(glass(i)),Veilborne.MIRROR_CHIME.get(),SoundSource.AMBIENT,1,1);
            for(int k=0;k<3;k++)if(k!=i)level.playSound(null,BlockPos.containing(glass(k)),Veilborne.MIRROR_BURST.get(),SoundSource.AMBIENT,.7f,1.3f);
            p.displayClientMessage(Component.translatable("message.veilborne.frames_true"),false);
            TideService.drop(level,BlockPos.containing(framePos(i)).relative(net.minecraft.core.Direction.UP,0),new ItemStack(Veilborne.MIRROR_SAND.get(),3));
        }else{
            entityData.set(WRONG,i|(tickCount&0xffff)<<2);
            p.displayClientMessage(Component.translatable(kind(i)==LATE?"message.veilborne.frames_late":"message.veilborne.frames_elsewhere"),true);
            level.playSound(null,BlockPos.containing(glass(i)),Veilborne.MIRROR_WHINE.get(),SoundSource.AMBIENT,.35f,1.6f);
        }
    }
    @Override public void tick(){
        super.tick();
        if(level().isClientSide){clientStateTicks++;return;}
        stateTicks++;
        MirrorData d=MirrorData.get((ServerLevel)level());
        State st=state();
        if(st!=State.DEPART&&(!d.active||d.progress()>=.86||st==State.SOLVED&&stateTicks>400))setState(State.DEPART);
        if(state()==State.DEPART&&stateTicks>=DEPART_TICKS)discard();
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(key==STATE)clientStateTicks=0;if(key==WRONG)wrongClock=0;}
    private int wrongClock=999;
    @Override public void baseTick(){super.baseTick();if(level().isClientSide)wrongClock++;}
    /** Client: 0..1 shiver of a wrongly chosen frame. */
    public float shiver(int i,float partial){return wrong()==i?Mth.clamp(1-(wrongClock+partial)/24f,0,1):0;}
    /** Client: 0..1 how far a broken frame has come apart (the two false ones once solved). */
    public float broken(int i,float partial){return state()!=State.WAITING&&kind(i)!=PRESENT?(state()==State.SOLVED?Mth.clamp((clientStateTicks+partial)/26f,0,1):1):0;}
    /** Client: 0..1 overall presence: they rise out of the ground and sink back. */
    public float visibility(float partial){
        float appear=Mth.clamp((tickCount+partial)/30f,0,1);
        return state()==State.DEPART?appear*Mth.clamp(1-(clientStateTicks+partial)/DEPART_TICKS,0,1):appear;
    }
    @Override public boolean isPickable(){return false;}
    @Override public boolean isPushable(){return false;}
    @Override public AABB getBoundingBoxForCulling(){return getBoundingBox().inflate(6,4,6);}
    @Override public boolean shouldRenderAtSqrDistance(double d){return d<96*96;}
    @Override protected void readAdditionalSaveData(CompoundTag t){
        entityData.set(FACING,t.getFloat("Facing"));entityData.set(KINDS,t.getInt("Kinds"));
        entityData.set(STATE,Mth.clamp(t.getInt("State"),0,2));stateTicks=t.getInt("StateTicks");
    }
    @Override protected void addAdditionalSaveData(CompoundTag t){
        t.putFloat("Facing",entityData.get(FACING));t.putInt("Kinds",entityData.get(KINDS));t.putInt("State",entityData.get(STATE));t.putInt("StateTicks",stateTicks);
    }
}
