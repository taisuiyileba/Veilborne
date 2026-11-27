package com.suiyi.veilborne;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 敲门的空位: a long hooded coat standing outside a door with nobody in it, its hem a hand's breadth off the ground, its
 * sleeves hanging empty. Every so often it knocks, three soft knocks that come from the door and from nowhere else. It never
 * opens the door and never moves anything. Raise a light where it can see you and it bows, leaves a lump of daylight wax on
 * the threshold, and is rubbed out from the hood down. Unanswered, after a while it turns away and is gone.
 * Where there is no door near, it knocks on a tree.
 */
public class HushCoat extends Entity {
    public enum State { ARRIVE, WAIT, KNOCK, BOW, ERASE, LEAVE }
    private static final EntityDataAccessor<BlockPos> DOOR=SynchedEntityData.defineId(HushCoat.class,EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> STATE=SynchedEntityData.defineId(HushCoat.class,EntityDataSerializers.INT);
    public static final int ARRIVE_TICKS=50, KNOCK_TICKS=30, BOW_TICKS=44, ERASE_TICKS=46, LEAVE_TICKS=70, ROUNDS=7;
    /** Ticks between rounds of knocking, not quite even. */
    static final int[] GAP={60,300,340,280,360,320,380};
    public static final double REACH=9;
    private int stateTicks, rounds, stateClock;
    private boolean paid;

    public HushCoat(EntityType<? extends HushCoat> type,Level level){super(type,level);noPhysics=true;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){b.define(DOOR,BlockPos.ZERO);b.define(STATE,State.ARRIVE.ordinal());}
    /** Stands {@code at}, facing what it knocks on. */
    public void place(Vec3 at,BlockPos door){
        entityData.set(DOOR,door.immutable());
        Vec3 d=Vec3.atCenterOf(door).subtract(at);
        float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z));
        moveTo(at.x,at.y,at.z,yaw,0);setYHeadRot(yaw);
        setState(State.ARRIVE);
    }
    public BlockPos door(){return entityData.get(DOOR);}
    public State state(){return State.values()[Mth.clamp(entityData.get(STATE),0,State.values().length-1)];}
    private void setState(State s){entityData.set(STATE,s.ordinal());stateTicks=0;}
    public int rounds(){return rounds;}
    /** Where its knuckles would be: on the door, at a hand's height. */
    public Vec3 knockPoint(){
        BlockPos d=door();
        Vec3 c=Vec3.atBottomCenterOf(d).add(0,1.25,0),me=position();
        Vec3 flat=new Vec3(me.x-c.x,0,me.z-c.z);
        if(flat.lengthSqr()<1e-4)return c;
        return c.add(flat.normalize().scale(.52));
    }
    /** Someone raised a light where it could see them. */
    public boolean answer(){
        State s=state();
        if(s==State.BOW||s==State.ERASE||s==State.LEAVE||s==State.ARRIVE&&stateTicks<20)return false;
        setState(State.BOW);return true;
    }
    public boolean answered(){State s=state();return s==State.BOW||s==State.ERASE;}

    @Override public void tick(){
        super.tick();
        if(level().isClientSide){stateClock++;return;}
        HushData d=HushData.get((ServerLevel)level());
        stateTicks++;
        State s=state();
        if(!d.active&&s!=State.LEAVE&&s!=State.BOW&&s!=State.ERASE){setState(State.LEAVE);s=State.LEAVE;}
        switch(s){
            case ARRIVE -> {if(stateTicks>=ARRIVE_TICKS)setState(State.WAIT);}
            case WAIT -> {
                if(rounds>=ROUNDS||d.progress()>=HushTimeline.KNOCK_UNTIL+.02){setState(State.LEAVE);break;}
                if(stateTicks>=GAP[Math.min(rounds,GAP.length-1)]){
                    setState(State.KNOCK);rounds++;
                    Vec3 k=knockPoint();
                    level().playSound(null,k.x,k.y,k.z,Veilborne.HUSH_KNOCK.get(),SoundSource.AMBIENT,1.1f,.94f+random.nextFloat()*.1f);
                    HushService.cue((ServerLevel)level(),new HushCue(HushCue.KNOCK,k,0),48);
                }
            }
            case KNOCK -> {if(stateTicks>=KNOCK_TICKS)setState(State.WAIT);}
            case BOW -> {
                if(stateTicks==24&&!paid){
                    paid=true;
                    BlockPos at=BlockPos.containing(getX(),getY()+.2,getZ());
                    TideService.drop((ServerLevel)level(),at,new ItemStack(Veilborne.DAYLIGHT_WAX.get(),2));
                }
                if(stateTicks>=BOW_TICKS){setState(State.ERASE);level().playSound(null,getX(),getY()+1,getZ(),Veilborne.HUSH_ERASE.get(),SoundSource.AMBIENT,.8f,1);}
            }
            case ERASE -> {if(stateTicks>=ERASE_TICKS){discard();return;}}
            case LEAVE -> {if(stateTicks>=LEAVE_TICKS){discard();return;}}
        }
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(key==STATE)stateClock=0;}
    public int stateClock(){return stateClock;}
    /** 0..1 how present it is. */
    public float visibility(float partial){
        float t=stateClock+partial;
        return switch(state()){
            case ARRIVE -> Mth.clamp(t/ARRIVE_TICKS,0,1);
            case LEAVE -> Mth.clamp(1-t/LEAVE_TICKS,0,1);
            case ERASE -> 1;
            default -> 1;
        };
    }
    /** 0..1 how far the rubbing-out has come down from the hood. */
    public float erased(float partial){return state()==State.ERASE?Mth.clamp((stateClock+partial)/ERASE_TICKS,0,1):0;}
    public void finish(){State s=state();if(s!=State.LEAVE&&s!=State.ERASE&&s!=State.BOW)setState(State.LEAVE);}
    /** Preview harness. */
    /** For the tests and the preview. Setting the state it is already in still restarts its clock on the client. */
    public void debugState(State s,int clock){
        if(state()==s)entityData.set(STATE,(s.ordinal()+1)%State.values().length);
        setState(s);stateTicks=clock;
    }

    @Override public boolean isPickable(){return false;}
    @Override public boolean isPushable(){return false;}
    @Override public boolean canBeCollidedWith(){return false;}
    @Override public AABB getBoundingBoxForCulling(){return getBoundingBox().inflate(1.5);}
    @Override protected void readAdditionalSaveData(CompoundTag t){}
    @Override protected void addAdditionalSaveData(CompoundTag t){}
}
