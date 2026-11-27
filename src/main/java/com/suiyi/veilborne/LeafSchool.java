package com.suiyi.veilborne;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
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
 * 失路叶鱼: a small school of leaf-fish that lost the others and is stuck at a window or a door, bumping softly against it
 * from outside like moths against glass. Hold out a flower, a lantern or a flower lamp near them and they follow it, a
 * little ahead of you; bring them out under open sky, clear of walls and trees, and they gather into one golden fish that
 * turns once round you and swims back up into the canopy, leaving two golden leaves.
 */
public class LeafSchool extends Entity {
    public enum State { STUCK, FOLLOW, MERGE, ASCEND, LEAVE }
    private static final EntityDataAccessor<Integer> STATE=SynchedEntityData.defineId(LeafSchool.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> GUIDE=SynchedEntityData.defineId(LeafSchool.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FACE=SynchedEntityData.defineId(LeafSchool.class,EntityDataSerializers.INT);
    public static final int MERGE_TICKS=56, ASCEND_TICKS=150, LEAVE_TICKS=60, FISH=9;
    public static final double LURE_RANGE=7, OPEN_FROM=6;
    private Vec3 home=Vec3.ZERO;
    private int stateTicks,stateClock,lost;
    private final Glide glide=new Glide();

    public LeafSchool(EntityType<? extends LeafSchool> type,Level level){super(type,level);noPhysics=true;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){b.define(STATE,0);b.define(GUIDE,-1);b.define(FACE,2);}
    /** Hovers {@code at}, just outside a window or door whose outward side faces {@code out}. */
    public void place(Vec3 at,Direction out){home=at;entityData.set(FACE,out.get3DDataValue());moveTo(at.x,at.y,at.z,0,0);setState(State.STUCK);}
    public State state(){return State.values()[Mth.clamp(entityData.get(STATE),0,State.values().length-1)];}
    public int guide(){return entityData.get(GUIDE);}
    /** Toward the glass they keep bumping against. */
    public Vec3 into(){Direction d=Direction.from3DDataValue(entityData.get(FACE));return new Vec3(-d.getStepX(),0,-d.getStepZ());}
    private void setState(State s){entityData.set(STATE,s.ordinal());stateTicks=0;}
    public int stateClock(){return stateClock;}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(key==STATE)stateClock=0;}
    public Vec3 home(){return home;}

    @Override public void tick(){
        super.tick();
        if(level().isClientSide){glide.step(this);stateClock++;return;}
        ServerLevel level=(ServerLevel)level();
        DreamData d=DreamData.get(level);
        stateTicks++;
        State s=state();
        if(!d.active&&(s==State.STUCK||s==State.FOLLOW)){setState(State.LEAVE);s=State.LEAVE;}
        switch(s){
            case STUCK -> {
                drift(home.add(into().scale(.12*Math.sin(tickCount*.21))),.05);
                ServerPlayer g=lure(level);
                if(g!=null){entityData.set(GUIDE,g.getId());setState(State.FOLLOW);}
            }
            case FOLLOW -> {
                Entity e=level.getEntity(guide());
                if(!(e instanceof ServerPlayer p)||!p.isAlive()||!DreamService.lure(p)||p.position().distanceTo(position())>LURE_RANGE+6){
                    // Without a light to follow they drift back to the glass.
                    if(++lost>60){entityData.set(GUIDE,-1);setState(State.STUCK);lost=0;}
                    drift(home,.04);break;
                }
                lost=0;
                Vec3 ahead=p.getEyePosition().add(p.getLookAngle().multiply(1,0,1).normalize().scale(2.3)).add(0,-.25,0);
                drift(ahead,.22);
                if(stateTicks%10==0&&open(level)){
                    setState(State.MERGE);
                    DreamService.schoolHome(level,this,p);
                }
            }
            case MERGE -> {
                setDeltaMovement(getDeltaMovement().scale(.8));
                if(stateTicks==MERGE_TICKS/2)TideService.drop(level,BlockPos.containing(getX(),getY()-.5,getZ()),new ItemStack(Veilborne.GOLDEN_LEAF.get(),2));
                if(stateTicks>=MERGE_TICKS)setState(State.ASCEND);
            }
            case ASCEND -> {
                double a=stateTicks*.09;
                setPos(getX()+Math.cos(a)*.18,getY()+.06+stateTicks*.004,getZ()+Math.sin(a)*.18);
                if(stateTicks>=ASCEND_TICKS){discard();return;}
            }
            case LEAVE -> {
                setPos(getX(),getY()+.05,getZ());
                if(stateTicks>=LEAVE_TICKS){discard();return;}
            }
        }
    }
    @Override public void lerpTo(double x,double y,double z,float yaw,float pitch,int steps){glide.to(x,y,z,yaw,steps);}
    @Override public double lerpTargetX(){return glide.x(this);}
    @Override public double lerpTargetY(){return glide.y(this);}
    @Override public double lerpTargetZ(){return glide.z(this);}
    private void drift(Vec3 aim,double speed){
        Vec3 dd=aim.subtract(position());
        double l=dd.length();
        Vec3 v=getDeltaMovement().scale(.8).add(l>1e-4?dd.scale(Math.min(speed,l*.3)/l*.3):Vec3.ZERO);
        setDeltaMovement(v);setPos(getX()+v.x,getY()+v.y,getZ()+v.z);
    }
    private ServerPlayer lure(ServerLevel level){
        for(ServerPlayer p:level.players())if(!p.isSpectator()&&DreamService.lure(p)&&p.getEyePosition().distanceTo(position())<LURE_RANGE)return p;
        return null;
    }
    /** Out under open sky, well away from where it was stuck, with nothing round it but air. */
    public boolean open(Level level){
        if(position().distanceTo(home)<OPEN_FROM)return false;
        BlockPos at=BlockPos.containing(position());
        if(!level.canSeeSky(at))return false;
        for(BlockPos b:BlockPos.betweenClosed(at.offset(-2,-1,-2),at.offset(2,2,2)))if(!level.getBlockState(b).getCollisionShape(level,b).isEmpty()&&b.getY()>=at.getY())return false;
        return true;
    }
    /** 0..1 how present it is. */
    public float visibility(float partial){
        float t=stateClock+partial;
        return switch(state()){
            case LEAVE -> Mth.clamp(1-t/LEAVE_TICKS,0,1);
            case ASCEND -> Mth.clamp(1-(t-ASCEND_TICKS+40)/40f,0,1);
            default -> Mth.clamp((tickCount+partial)/40f,0,1);
        };
    }
    public void finish(){State s=state();if(s==State.STUCK||s==State.FOLLOW)setState(State.LEAVE);}
    /** For the tests and the preview. */
    public void debugState(State st){if(state()==st)entityData.set(STATE,(st.ordinal()+1)%State.values().length);setState(st);}
    public void debugHome(Vec3 at){home=at;}
    @Override public boolean isPickable(){return false;}
    @Override public boolean isPushable(){return false;}
    @Override public boolean canBeCollidedWith(){return false;}
    @Override public AABB getBoundingBoxForCulling(){return getBoundingBox().inflate(3);}
    @Override protected void readAdditionalSaveData(CompoundTag t){}
    @Override protected void addAdditionalSaveData(CompoundTag t){}
}
