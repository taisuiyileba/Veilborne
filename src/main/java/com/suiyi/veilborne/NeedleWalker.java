package com.suiyi.veilborne;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 走针者: a six-legged brass thing, all needles for legs, with a spool of scarlet thread on its back. It climbs down the thread
 * a strike left between the land and the sky, gathers loose thread ends from the ground, and settles at the foot of its
 * thread to work a knot. Left alone it never bothers anyone. Touch the knot and it comes undone into three loose ends;
 * the walker turns on you and tries to gather them back. Catch all three first and the knot's heart is yours.
 */
public class NeedleWalker extends PathfinderMob {
    public enum State { DESCEND, ROAM, KNOT, ALARM, DEPART }
    public static final int DESCEND_TICKS=140, ROAM_TICKS=420, ALARM_TICKS=260, DEPART_TICKS=40;
    public static final double CLIMB=34, END_RANGE=3.6, CATCH=1.25;
    /** How far in front of its body the front points meet (blocks): it sits this far behind its knot, facing it. */
    public static final double KNOT_REACH=.72;
    private static final EntityDataAccessor<Integer> STATE=SynchedEntityData.defineId(NeedleWalker.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> ANCHOR=SynchedEntityData.defineId(NeedleWalker.class,EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Float> UP_X=SynchedEntityData.defineId(NeedleWalker.class,EntityDataSerializers.FLOAT),
        UP_Y=SynchedEntityData.defineId(NeedleWalker.class,EntityDataSerializers.FLOAT),UP_Z=SynchedEntityData.defineId(NeedleWalker.class,EntityDataSerializers.FLOAT);
    /** Which of the three loose ends are still out (bits), during the encounter. */
    private static final EntityDataAccessor<Integer> ENDS=SynchedEntityData.defineId(NeedleWalker.class,EntityDataSerializers.INT);
    private int stateTicks,clientTicks,attackCooldown;
    private boolean used;
    private UUID toucher;

    public NeedleWalker(EntityType<? extends NeedleWalker> type,Level level){super(type,level);xpReward=4;setPersistenceRequired();}
    public static AttributeSupplier.Builder attributes(){
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,30).add(Attributes.ARMOR,8).add(Attributes.MOVEMENT_SPEED,.24)
            .add(Attributes.ATTACK_DAMAGE,3).add(Attributes.FOLLOW_RANGE,20).add(Attributes.KNOCKBACK_RESISTANCE,1).add(Attributes.STEP_HEIGHT,1.1);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(STATE,0);b.define(ANCHOR,BlockPos.ZERO);b.define(UP_X,0f);b.define(UP_Y,1f);b.define(UP_Z,0f);b.define(ENDS,0);}
    public State state(){return State.values()[Mth.clamp(entityData.get(STATE),0,4)];}
    private void setState(State s){entityData.set(STATE,s.ordinal());stateTicks=0;}
    public BlockPos anchor(){return entityData.get(ANCHOR);}
    /** Unit direction up its thread, from the anchor on the ground toward the sky. */
    public Vec3 up(){return new Vec3(entityData.get(UP_X),entityData.get(UP_Y),entityData.get(UP_Z));}
    public int ends(){return entityData.get(ENDS);}
    public int stateTicks(){return level().isClientSide?clientTicks:stateTicks;}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(key==STATE)clientTicks=0;}
    public void plan(BlockPos anchor,Vec3 up){
        entityData.set(ANCHOR,anchor.immutable());entityData.set(UP_X,(float)up.x);entityData.set(UP_Y,(float)up.y);entityData.set(UP_Z,(float)up.z);
        Vec3 start=Vec3.atBottomCenterOf(anchor).add(up.scale(CLIMB));
        moveTo(start.x,start.y,start.z,0,0);
        setState(State.DESCEND);
    }
    /** Where loose end k lies while the knot is undone: three points around the anchor, a little above the ground. */
    public Vec3 endAt(int k){
        BlockPos a=anchor();double base=Math.floorMod(a.asLong(),360L)*Math.PI/180+k*Math.PI*2/3;
        return Vec3.atBottomCenterOf(a).add(Math.cos(base)*END_RANGE,.9,Math.sin(base)*END_RANGE);
    }
    /** 0..1 along its descent (client uses this to place it on the thread). */
    public Vec3 descentPoint(double t){return Vec3.atBottomCenterOf(anchor()).add(up().scale(CLIMB*(1-t)));}

    @Override public void tick(){
        noPhysics=state()==State.DESCEND;
        setNoGravity(state()==State.DESCEND);
        super.tick();
        if(level().isClientSide){clientTicks++;return;}
        stateTicks++;if(attackCooldown>0)attackCooldown--;
        ThreadData d=ThreadData.get((ServerLevel)level());
        if(state()!=State.DEPART&&(!d.active||d.progress()>=ThreadTimeline.CUT))depart();
        switch(state()){
            case DESCEND -> {
                follow(d);
                double t=Math.min(1,stateTicks/(double)DESCEND_TICKS);
                Vec3 at=descentPoint(t*t*(3-2*t));
                setPos(at.x,at.y,at.z);setDeltaMovement(Vec3.ZERO);
                if(stateTicks%8==0)playSound(Veilborne.THREAD_WALKER.get(),.5f,1.1f+random.nextFloat()*.2f);
                if(t>=1)setState(State.ROAM);
            }
            case ROAM -> roam();
            case KNOT -> {
                settle();
                if(stateTicks%60==0)playSound(Veilborne.THREAD_WALKER.get(),.35f,.9f);
            }
            case ALARM -> alarm((ServerLevel)level());
            case DEPART -> {if(stateTicks>=DEPART_TICKS)discard();}
        }
    }
    /**
     * Its thread's top end is a needle hole in the cloth, and the hole slides as that stitch is pulled tight; keep climbing
     * down the thread as it is now, not as it was when the walker set out.
     */
    private void follow(ThreadData d){
        if(!d.active)return;
        for(int i=0;i<ThreadData.STRIKES;i++)if(d.strikes[i].equals(anchor())){
            Vec3 up=ThreadTimeline.strikeTop(d.seam(),i,d.progress()).subtract(Vec3.atBottomCenterOf(anchor())).normalize();
            entityData.set(UP_X,(float)up.x);entityData.set(UP_Y,(float)up.y);entityData.set(UP_Z,(float)up.z);
            return;
        }
    }
    /** At its knot: it sits with the knot between its front points, facing it; walks back first if it was drawn away. */
    private void settle(){
        Vec3 home=Vec3.atBottomCenterOf(anchor());
        Vec3 to=new Vec3(home.x-getX(),0,home.z-getZ());
        double flat=to.length();
        Vec3 dir=flat>.05?to.scale(1/flat):Vec3.directionFromRotation(0,getYRot());
        Vec3 spot=home.subtract(dir.scale(KNOT_REACH));
        double off=Math.hypot(spot.x-getX(),spot.z-getZ());
        if(off>1.5){if(stateTicks%20==1)getNavigation().moveTo(spot.x,spot.y,spot.z,.8);return;}
        getNavigation().stop();
        if(off>.02){double k=Math.min(1,.06/off);setPos(getX()+(spot.x-getX())*k,getY(),getZ()+(spot.z-getZ())*k);}
        float yaw=(float)Math.toDegrees(Math.atan2(-dir.x,dir.z));
        setYRot(yaw);yBodyRot=yaw;yHeadRot=yaw;
    }
    private void roam(){
        Vec3 home=Vec3.atBottomCenterOf(anchor());
        if(stateTicks>ROAM_TICKS){
            if(position().distanceTo(home)<1.6){setState(State.KNOT);return;}
            if(stateTicks%20==0)getNavigation().moveTo(home.x,home.y,home.z,.9);
            return;
        }
        if(stateTicks%30!=0)return;
        ItemEntity best=null;double bestD=14;
        for(ItemEntity i:level().getEntitiesOfClass(ItemEntity.class,getBoundingBox().inflate(14),x->x.getItem().is(Veilborne.SCARLET_THREAD.get())&&x.onGround()&&x.getAge()>200)){
            double dd=i.distanceTo(this);if(dd<bestD){bestD=dd;best=i;}
        }
        if(best==null){if(position().distanceTo(home)>6)getNavigation().moveTo(home.x,home.y,home.z,.8);return;}
        if(bestD<1.5){
            // Wound onto its spool.
            ((ServerLevel)level()).sendParticles(new DustParticleOptions(new org.joml.Vector3f(.86f,.12f,.16f),.8f),best.getX(),best.getY()+.2,best.getZ(),8,.15,.1,.15,0);
            playSound(Veilborne.THREAD_CHIME.get(),.4f,1.5f);
            best.discard();
        }else getNavigation().moveTo(best.getX(),best.getY(),best.getZ(),.85);
    }
    /** The knot is touched: it comes undone and the walker turns on whoever did it. */
    public boolean startAlarm(Player p){
        if(state()!=State.KNOT||used||level().isClientSide)return false;
        used=true;toucher=p.getUUID();
        entityData.set(ENDS,7);setState(State.ALARM);
        playSound(Veilborne.THREAD_SNAP.get(),1,1.3f);
        p.displayClientMessage(Component.translatable("message.veilborne.knot_undone"),true);
        return true;
    }
    private void alarm(ServerLevel level){
        int left=ends();
        for(int k=0;k<3;k++){
            if((left&1<<k)==0)continue;
            Vec3 e=endAt(k);
            for(ServerPlayer pl:level.players())if(!pl.isSpectator()&&pl.position().add(0,.8,0).distanceTo(e)<CATCH){
                left&=~(1<<k);entityData.set(ENDS,left);
                TideService.give(pl,new ItemStack(Veilborne.SCARLET_THREAD.get()));
                level.playSound(null,BlockPos.containing(e),Veilborne.THREAD_CHIME.get(),SoundSource.NEUTRAL,.8f,1.1f+k*.15f);
                if(left==0){
                    TideService.give(pl,new ItemStack(Veilborne.SEAM_SILK.get()));
                    TideService.record(pl,ThreadService.KNOT);
                    VeilGrade.deed(pl,VeilGrade.THREAD,ThreadData.get(level).seed,VeilGrade.THREAD_KNOT);
                    pl.displayClientMessage(Component.translatable("message.veilborne.knot_caught"),false);
                }
                break;
            }
        }
        Player target=toucher==null?null:level.getPlayerByUUID(toucher);
        boolean lost=toucher!=null&&(target==null||target.isCreative()||target.isSpectator()||target.distanceTo(this)>24);
        if(left==0||stateTicks>ALARM_TICKS||lost){
            entityData.set(ENDS,0);setState(State.KNOT);getNavigation().stop();setTarget(null);
            Vec3 home=Vec3.atBottomCenterOf(anchor());getNavigation().moveTo(home.x,home.y,home.z,.9);
            return;
        }
        if(target==null)return;
        // It goes after the loose end nearest the one who undid it, stabbing at them on the way.
        getNavigation().moveTo(target,1.15);
        getLookControl().setLookAt(target,30,30);
        if(distanceTo(target)<1.9&&attackCooldown==0){doHurtTarget(target);attackCooldown=22;playSound(Veilborne.THREAD_WALKER.get(),.8f,.7f);}
    }
    /** Preview harness: stand it in a state directly (an alarm with all three ends out). */
    public void debugState(State s){
        setState(s);noPhysics=false;setNoGravity(false);
        if(s==State.ALARM){used=true;entityData.set(ENDS,7);}
    }
    /** The thread it came by has been cut: it winds itself up and goes. */
    public void depart(){if(state()!=State.DEPART){setState(State.DEPART);getNavigation().stop();}}
    @Override public InteractionResult mobInteract(Player p,InteractionHand hand){
        if(state()==State.KNOT&&!used){
            if(!level().isClientSide)startAlarm(p);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(p,hand);
    }
    @Override public boolean hurt(DamageSource source,float amount){
        if(state()==State.DESCEND||state()==State.DEPART)return false;
        if(source.getEntity() instanceof Player p&&state()==State.KNOT&&!used)startAlarm(p);
        return super.hurt(source,amount);
    }
    /** 0..1 how much of it is there: it resolves out of thread coming down, and unwinds into thread going. */
    public float visibility(float partial){
        float t=clientTicks+partial;
        return switch(state()){case DESCEND->Mth.clamp(t/30f,0,1);case DEPART->Mth.clamp(1-t/DEPART_TICKS,0,1);default->1;};
    }
    @Override protected void dropCustomDeathLoot(ServerLevel level,DamageSource source,boolean recentlyHit){
        super.dropCustomDeathLoot(level,source,recentlyHit);
        spawnAtLocation(new ItemStack(Veilborne.SCARLET_THREAD.get(),2));
    }
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override public boolean causeFallDamage(float distance,float multiplier,DamageSource source){return false;}
    @Override public boolean isPushable(){return state()!=State.DESCEND;}
    @Override protected SoundEvent getAmbientSound(){return null;}
    @Override protected SoundEvent getHurtSound(DamageSource source){return SoundEvents.CHAIN_HIT;}
    @Override protected SoundEvent getDeathSound(){return SoundEvents.CHAIN_BREAK;}
    @Override protected void playStepSound(BlockPos pos,net.minecraft.world.level.block.state.BlockState state){playSound(Veilborne.THREAD_WALKER.get(),.25f,1.3f+random.nextFloat()*.3f);}
    @Override public void addAdditionalSaveData(CompoundTag t){
        super.addAdditionalSaveData(t);
        t.putInt("WalkState",entityData.get(STATE));t.putInt("WalkTicks",stateTicks);t.putLong("Anchor",anchor().asLong());
        t.putFloat("UpX",entityData.get(UP_X));t.putFloat("UpY",entityData.get(UP_Y));t.putFloat("UpZ",entityData.get(UP_Z));t.putBoolean("Used",used);
    }
    @Override public void readAdditionalSaveData(CompoundTag t){
        super.readAdditionalSaveData(t);
        entityData.set(ANCHOR,BlockPos.of(t.getLong("Anchor")));
        entityData.set(UP_X,t.getFloat("UpX"));entityData.set(UP_Y,t.contains("UpY")?t.getFloat("UpY"):1f);entityData.set(UP_Z,t.getFloat("UpZ"));
        used=t.getBoolean("Used");
        // An encounter interrupted by a reload settles back into its knot.
        int s=Mth.clamp(t.getInt("WalkState"),0,4);
        entityData.set(STATE,s==State.ALARM.ordinal()?State.KNOT.ordinal():s);stateTicks=t.getInt("WalkTicks");
    }
}
