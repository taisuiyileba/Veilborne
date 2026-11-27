package com.suiyi.veilborne;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 绯线缝世 on the server: timeline, the needle's three strikes on the land and their warnings, the world stitch and its two
 * feet, the pull, the loose thread ends drifting down, the needle walker, the unsewn pocket and the tuning anchor, and the
 * keepsake. Observation bits continue the shared record word: 262144 the tear, 524288 the world sewn, 1048576 reading a
 * strike's warning, 2097152 a loose thread end, 4194304 the walker's knot, 8388608 the pocket tuned, and 16777216 (kept
 * secret) looking up along the thread that holds you.
 */
public final class ThreadService {
    public static final int FIRST=262144, SEWN=524288, FORESIGHT=1048576, THREAD_END=2097152, KNOT=4194304, POCKET=8388608, STRING=16777216;
    public static final int ENDS_PER_PLAYER=8, TUNE_TICKS=320, SPIRITS=3, STRING_LOOK=30, CORE_DAMAGE=8;
    public static final double ANCHOR_RANGE=6, POCKET_RANGE=4.5, CORE_RANGE=1.6, HOLD_RANGE=4, SNIP_RANGE=8;
    public static final String DRIFT="VeilborneDrift", END="VeilborneEnd";
    private static final Map<UUID,Integer> ends=new HashMap<>(),stringLook=new HashMap<>();
    /** Who stood in each warned zone while its warning ran: strikes 0..2, then the land stitch's two feet. */
    private static final List<Set<UUID>> warned=new ArrayList<>();
    private static final List<ItemEntity> drifting=new ArrayList<>();
    static { for(int i=0;i<ThreadData.STRIKES+2;i++)warned.add(new HashSet<>()); }

    public static void sync(ServerPlayer p){
        ThreadData d=ThreadData.get(p.serverLevel());
        if(p.connection!=null&&p.connection.hasChannel(ThreadPacket.TYPE))PacketDistributor.sendToPlayer(p,ThreadPacket.from(d,TideService.records(p)));
    }
    public static void broadcast(ServerLevel level){level.getServer().getPlayerList().getPlayers().forEach(ThreadService::sync);}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p);}

    public static int variants(ServerLevel level){
        if(!TideConfig.THREAD_VISITORS.get())return 0;
        int r=level.random.nextInt(5);
        return r<3?ThreadData.VARIANT_WALKER|ThreadData.VARIANT_POCKET:r==3?ThreadData.VARIANT_WALKER:ThreadData.VARIANT_POCKET;
    }
    public static void begin(ServerLevel level,BlockPos origin,int seconds){
        if(TideData.get(level).active)TideService.end(level);
        if(MoonData.get(level).active)MoonService.end(level);
        if(MirrorData.get(level).active)MirrorService.end(level);
        if(HushData.get(level).active)HushService.end(level);
        if(DreamData.get(level).active)DreamService.end(level);
        ThreadData d=ThreadData.get(level);
        if(d.active)end(level);
        d.begin(origin,level.random.nextLong(),seconds*20,TideConfig.RADIUS.get(),variants(level));
        reset();
        broadcast(level);
    }
    private static void reset(){ends.clear();stringLook.clear();warned.forEach(Set::clear);drifting.clear();}
    public static void end(ServerLevel level){
        ThreadData d=ThreadData.get(level);d.active=false;d.setDirty();
        VeilGrade.settle(level,VeilGrade.THREAD,d.seed);
        TideService.rest(level);
        for(var e:com.google.common.collect.ImmutableList.copyOf(level.getEntities().getAll())){
            if(e instanceof NeedleWalker w)w.depart();
            else if(e instanceof ThreadSpirit s)s.unravel();
        }
        for(ItemEntity i:drifting)if(i.isAlive()){i.setNoGravity(false);i.getPersistentData().remove(DRIFT);}
        reset();
        broadcast(level);
    }

    @SubscribeEvent public static void tick(LevelTickEvent.Post e){
        if(!(e.getLevel() instanceof ServerLevel level)||!level.dimension().equals(Level.OVERWORLD))return;
        ThreadData d=ThreadData.get(level);
        if(!d.active)return;
        if(level.getServer().getPlayerList().getPlayers().isEmpty())return;
        if(++d.age>=d.duration){end(level);return;}
        d.setDirty();
        double p=d.progress();
        var witnesses=level.players().stream().filter(pl->!pl.isSpectator()&&d.strength(pl.getX(),pl.getZ())>.25).toList();
        ThreadTimeline.Seam seam=d.seam();
        if(d.age%20==0){
            broadcast(level);
            for(var pl:witnesses){
                VeilGrade.witness(pl,VeilGrade.THREAD,d.seed);
                if((TideService.records(pl)&FIRST)==0&&ThreadTimeline.tear(p)>.06){
                    TideService.record(pl,FIRST);
                    if(TideConfig.AUTO_JOURNAL.get()&&!pl.getInventory().contains(new ItemStack(Veilborne.JOURNAL.get())))TideService.give(pl,new ItemStack(Veilborne.JOURNAL.get()));
                }
                double q=ThreadTimeline.sew(p);
                if(d.hasLand()&&p>=ThreadTimeline.SEW_START&&q>=ThreadTimeline.DESCEND&&q<ThreadTimeline.THROUGH+.06&&level.canSeeSky(pl.blockPosition())
                    &&pl.position().distanceTo(Vec3.atCenterOf(d.landA))<230){TideService.record(pl,SEWN);VeilGrade.deed(pl,VeilGrade.THREAD,d.seed,VeilGrade.THREAD_SEWN);}
                if(pl.getInventory().contains(new ItemStack(Veilborne.SCARLET_THREAD.get())))TideService.record(pl,THREAD_END);
            }
        }
        drift(level,seam);
        if(witnesses.isEmpty())return;
        ServerPlayer observer=witnesses.get(0);
        strikes(level,d,seam,witnesses);
        land(level,d,observer,witnesses);
        // Loose thread ends come down through the stitching and the sewing, a few for everyone who is out under it.
        if(p>=3.3/13&&p<9.4/13&&d.age%90==0)for(var pl:witnesses){
            int n=ends.getOrDefault(pl.getUUID(),0);
            if(n<ENDS_PER_PLAYER&&level.canSeeSky(pl.blockPosition())&&threadEnd(level,pl,d.seed+n*31L))ends.put(pl.getUUID(),n+1);
        }
        // The needle walker climbs down the first strike's thread.
        if(TideConfig.THREAD_VISITORS.get()&&(d.variant&ThreadData.VARIANT_WALKER)!=0&&!d.has(ThreadData.WALKER)&&!d.strikes[0].equals(BlockPos.ZERO)
            &&d.age>=ThreadTimeline.strikeTick(0,d.duration)+60&&p<ThreadTimeline.SEW_START){
            NeedleWalker w=Veilborne.WALKER.get().create(level);
            if(w!=null&&d.takeFlag(ThreadData.WALKER)){
                Vec3 foot=Vec3.atBottomCenterOf(d.strikes[0]);
                w.plan(d.strikes[0],ThreadTimeline.strikeTop(seam,0,p).subtract(foot).normalize());
                level.addFreshEntity(w);
            }
        }
        tug(level,d,p,witnesses);
        // The hidden record: during the pull, looking straight up the thread that holds you, to where it meets the seam.
        if(ThreadTimeline.strings(p)>.5)for(var pl:witnesses){
            Vec3 to=ThreadTimeline.middle(seam).subtract(pl.getEyePosition()).normalize();
            int t=pl.getLookAngle().dot(to)>Math.cos(Math.toRadians(7))?stringLook.getOrDefault(pl.getUUID(),0)+1:0;
            stringLook.put(pl.getUUID(),t);
            if(t>=STRING_LOOK&&!VeilGrade.done(pl,VeilGrade.THREAD,d.seed,VeilGrade.THREAD_STRING)){
                VeilGrade.deed(pl,VeilGrade.THREAD,d.seed,VeilGrade.THREAD_STRING);
                if((TideService.records(pl)&STRING)==0)TideService.record(pl,STRING);
                level.playSound(null,pl.blockPosition(),Veilborne.THREAD_CHIME.get(),SoundSource.AMBIENT,.8f,.55f);
            }
        }
        pocket(level,d,p,observer);
        pair(level,d,p,witnesses);
        if(p>=.94&&!d.has(ThreadData.KEEPSAKE)){
            BlockPos at=TideService.findSafe(level,observer.blockPosition().offset(-2,0,2),5);
            if(at!=null&&d.takeFlag(ThreadData.KEEPSAKE)){d.keepsake=at;d.keepsakeAt=d.age;broadcast(level);}
        }
        if(!d.keepsake.equals(BlockPos.ZERO)&&d.age-d.keepsakeAt==60){
            TideService.drop(level,d.keepsake,new ItemStack(Veilborne.ROBE_SCRAP_ITEM.get()));
            level.playSound(null,d.keepsake,Veilborne.THREAD_CHIME.get(),SoundSource.AMBIENT,.8f,1.2f);
        }
        // Animals turn toward the needle; when the thread is pulled they stand stock still with their heads raised.
        if(p>.12&&p<.9&&d.age%20==0){
            ThreadTimeline.Pose needle=d.hasLand()&&p>=ThreadTimeline.SEW_START?ThreadTimeline.sewing(seam,Vec3.atBottomCenterOf(d.landA),Vec3.atBottomCenterOf(d.landB),p):ThreadTimeline.stitching(seam,p);
            Vec3 look=ThreadTimeline.strings(p)>.1||needle==null?ThreadTimeline.middle(seam):needle.tipPoint();
            for(Animal a:level.getEntitiesOfClass(Animal.class,observer.getBoundingBox().inflate(24)).stream().limit(14).toList()){
                if(ThreadTimeline.tug(p)>0||d.age%100==0)a.getNavigation().stop();
                a.getLookControl().setLookAt(look.x,Math.min(look.y,a.getEyeY()+60),look.z,6,6);
            }
        }
    }

    // ------------------------------------------------------------------ the needle on the land (落针区)
    private static void strikes(ServerLevel level,ThreadData d,ThreadTimeline.Seam seam,List<ServerPlayer> witnesses){
        if(!TideConfig.THREAD_STRIKES.get())return;
        for(int i=0;i<ThreadData.STRIKES;i++){
            int at=ThreadTimeline.strikeTick(i,d.duration);
            if(d.age>=at-ThreadTimeline.STRIKE_WARN&&d.age<at&&d.strikes[i].equals(BlockPos.ZERO)&&d.age%5==0){
                ServerPlayer target=witnesses.get(i%witnesses.size());
                BlockPos spot=strikeSpot(level,target,d.seed+i*977L);
                if(spot!=null){
                    d.strikes[i]=spot;d.setDirty();
                    level.playSound(null,spot,Veilborne.THREAD_WARN.get(),SoundSource.HOSTILE,1,1);
                    broadcast(level);
                }
            }
            if(d.strikes[i].equals(BlockPos.ZERO))continue;
            Vec3 c=Vec3.atBottomCenterOf(d.strikes[i]);
            if(ThreadTimeline.warning(d.age,at,ThreadTimeline.STRIKE_WARN)>=0)for(var pl:witnesses)if(ThreadTimeline.inZone(c,ThreadTimeline.STRIKE_RANGE,pl.position()))warned.get(i).add(pl.getUUID());
            if(d.age==at)strike(level,c,ThreadTimeline.STRIKE_RANGE,warned.get(i),4,d.seed);
        }
    }
    /** An open, dry, sky-lit spot a short walk from someone: close enough to be seen coming down, never on top of them. */
    static BlockPos strikeSpot(ServerLevel level,ServerPlayer near,long seed){
        for(int k=0;k<12;k++){
            double a=(Math.floorMod(seed,360L)+k*67.0)*Math.PI/180,r=11+(k*5%8);
            BlockPos probe=TideService.findSafe(level,near.blockPosition().offset((int)(Math.cos(a)*r),0,(int)(Math.sin(a)*r)),3);
            if(probe==null||!MirrorService.open(level,probe))continue;
            if(Math.hypot(probe.getX()+.5-near.getX(),probe.getZ()+.5-near.getZ())<ThreadTimeline.STRIKE_RANGE+2)continue;
            return probe;
        }
        return null;
    }
    /** A strike outside any sewing (tests): nothing is graded and no core is left. */
    public static void strike(ServerLevel level,Vec3 c,double range,Set<UUID> warnedIn,float damage){strike(level,c,range,warnedIn,damage,0);}
    /**
     * The needle comes down. Everyone still inside the warned ring is struck and thrown out of it unless something holds
     * them steady; everyone who was inside while it warned and has stepped out has read the warning. Nothing is broken.
     * Someone who stood at the very middle with a binding knot on them takes the needle's full weight, and a piece of its
     * core stays in their hands.
     */
    public static void strike(ServerLevel level,Vec3 c,double range,Set<UUID> warnedIn,float damage,long seed){
        level.playSound(null,BlockPos.containing(c),Veilborne.THREAD_STRIKE.get(),SoundSource.HOSTILE,1.2f,1);
        for(ServerPlayer pl:level.players()){
            if(pl.isSpectator())continue;
            boolean inside=ThreadTimeline.inZone(c,range,pl.position());
            if(!inside){if(warnedIn.contains(pl.getUUID())){TideService.record(pl,FORESIGHT);if(seed!=0)VeilGrade.deed(pl,VeilGrade.THREAD,seed,VeilGrade.THREAD_FORESIGHT);}continue;}
            if(pl.isCreative())continue;
            boolean core=seed!=0&&hasCharm(pl)&&Math.hypot(pl.getX()-c.x,pl.getZ()-c.z)<CORE_RANGE;
            pl.hurt(level.damageSources().magic(),core?CORE_DAMAGE:damage);
            if(core&&pl.isAlive()&&!VeilGrade.done(pl,VeilGrade.THREAD,seed,VeilGrade.THREAD_CORE)){
                VeilGrade.deed(pl,VeilGrade.THREAD,seed,VeilGrade.THREAD_CORE);
                TideService.give(pl,new ItemStack(Veilborne.NEEDLE_CORE.get()));
                level.playSound(null,pl.blockPosition(),Veilborne.THREAD_KNOT.get(),SoundSource.PLAYERS,1,.6f);
                pl.displayClientMessage(Component.translatable("message.veilborne.needle_core"),false);
            }
            if(!steady(level,pl)){
                Vec3 out=new Vec3(pl.getX()-c.x,0,pl.getZ()-c.z);
                out=out.lengthSqr()<1e-4?new Vec3(1,0,0):out.normalize();
                pl.setDeltaMovement(out.x*1.05,.42,out.z*1.05);pl.hurtMarked=true;
            }
        }
        warnedIn.clear();
    }
    /** A binding charm on you, or a tuning anchor close by, keeps the weather from moving you. */
    public static boolean steady(Level level,Player p){
        if(hasCharm(p))return true;
        BlockPos o=p.blockPosition();
        int r=(int)Math.ceil(ANCHOR_RANGE);
        for(BlockPos b:BlockPos.betweenClosed(o.offset(-r,-3,-r),o.offset(r,3,r)))
            if(level.getBlockState(b).is(Veilborne.TUNING_ANCHOR.get())&&b.distToCenterSqr(p.position())<ANCHOR_RANGE*ANCHOR_RANGE)return true;
        return false;
    }
    public static boolean hasCharm(Player p){return p.getInventory().contains(new ItemStack(Veilborne.BINDING_CHARM.get()));}

    // ------------------------------------------------------------------ the world stitch
    private static void land(ServerLevel level,ThreadData d,ServerPlayer observer,List<ServerPlayer> witnesses){
        double p=d.progress();
        if(p>=ThreadTimeline.SEW_START&&p<ThreadTimeline.pAtSew(ThreadTimeline.DESCEND)&&!d.has(ThreadData.LAND)&&d.age%10==0){
            BlockPos[] feet=landSites(level,observer,d.seed);
            if(feet!=null&&d.takeFlag(ThreadData.LAND)){
                d.landA=feet[0];d.landB=feet[1];d.landAt=d.age;
                if(TideConfig.THREAD_STRIKES.get()){level.playSound(null,d.landA,Veilborne.THREAD_WARN.get(),SoundSource.HOSTILE,1.3f,.8f);level.playSound(null,d.landB,Veilborne.THREAD_WARN.get(),SoundSource.HOSTILE,1.3f,.8f);}
                broadcast(level);
            }
        }
        if(!d.hasLand()||!TideConfig.THREAD_STRIKES.get())return;
        Vec3 a=Vec3.atBottomCenterOf(d.landA),b=Vec3.atBottomCenterOf(d.landB);
        for(int f=0;f<2;f++){
            Vec3 c=f==0?a:b;
            int at=ThreadTimeline.landTick(f==1,a,b,d.duration),warnFrom=Math.min(ThreadTimeline.landWarnTick(d.duration),at-ThreadTimeline.LAND_WARN_TICKS);
            if(d.age>=warnFrom&&d.age<at)for(var pl:witnesses)if(ThreadTimeline.inZone(c,ThreadTimeline.LAND_RANGE,pl.position()))warned.get(ThreadData.STRIKES+f).add(pl.getUUID());
            if(d.age==at)strike(level,c,ThreadTimeline.LAND_RANGE,warned.get(ThreadData.STRIKES+f),5,d.seed);
        }
    }
    /**
     * The two feet of the world stitch: the near one a short way from someone, the far one LAND_HALF*2 further on, across
     * their view rather than straight away from them, on ground about as high. Only loaded, dry, open ground.
     */
    public static BlockPos[] landSites(ServerLevel level,ServerPlayer near,long seed){
        BlockPos[] best=null;double bestScore=-1e9;
        for(int k=0;k<10;k++){
            double a=(Math.floorMod(seed>>>5,360L)+k*36.0)*Math.PI/180,r=19+(k%3)*2;
            BlockPos first=TideService.findSafe(level,near.blockPosition().offset((int)(Math.cos(a)*r),0,(int)(Math.sin(a)*r)),3);
            if(first==null||!MirrorService.open(level,first))continue;
            if(Math.hypot(first.getX()+.5-near.getX(),first.getZ()+.5-near.getZ())<ThreadTimeline.LAND_RANGE+3)continue;
            for(int turn=-1;turn<=1;turn+=2){
                double dir=a+turn*Math.toRadians(115);
                int x=(int)Math.round(first.getX()+Math.cos(dir)*ThreadTimeline.LAND_HALF*2),z=(int)Math.round(first.getZ()+Math.sin(dir)*ThreadTimeline.LAND_HALF*2);
                int y=level.hasChunk(x>>4,z>>4)?Surface.top(level,x,z):first.getY();
                if(y<=level.getMinBuildHeight()+2)y=first.getY();
                y=Math.max(first.getY()-14,Math.min(first.getY()+14,y));
                double score=-Math.abs(y-first.getY())*.3+(level.hasChunk(x>>4,z>>4)?2:0)+((seed>>>(k+turn+3))&3)*.1;
                if(score>bestScore){bestScore=score;best=new BlockPos[]{first,new BlockPos(x,y,z)};}
            }
        }
        return best;
    }
    /** The pull: a brief, gentle tug toward the land stitch, and up a little, like a string drawn from above. */
    private static void tug(ServerLevel level,ThreadData d,double p,List<ServerPlayer> witnesses){
        double t=ThreadTimeline.tug(p);
        if(t<.02||!d.hasLand()||d.has(ThreadData.SNIPPED))return;
        Vec3 a=Vec3.atBottomCenterOf(d.landA),b=Vec3.atBottomCenterOf(d.landB);
        // Whoever is on the line when it is drawn tight is holding it with it.
        if(t>.8)for(var pl:witnesses){
            Vec3 on=ThreadTimeline.nearestOnLine(a,b,pl.position());
            if(Math.hypot(on.x-pl.getX(),on.z-pl.getZ())<HOLD_RANGE&&!VeilGrade.done(pl,VeilGrade.THREAD,d.seed,VeilGrade.THREAD_HOLD)){
                VeilGrade.deed(pl,VeilGrade.THREAD,d.seed,VeilGrade.THREAD_HOLD);
                pl.displayClientMessage(Component.translatable("message.veilborne.thread_held"),true);
            }
        }
        if(!TideConfig.THREAD_TUG.get())return;
        for(var pl:witnesses){
            boolean held=d.age%10==0?refreshSteady(level,pl):steadyNow.contains(pl.getUUID());
            if(pl.isCreative()||pl.isSpectator()||pl.isPassenger()||held)continue;
            Vec3 to=ThreadTimeline.nearestOnLine(a,b,pl.position()).subtract(pl.position());
            to=new Vec3(to.x,0,to.z);
            if(to.lengthSqr()<4)continue;
            to=to.normalize();
            Vec3 v=pl.getDeltaMovement();
            double lift=pl.onGround()?.06*t:0;
            pl.setDeltaMovement(v.x+to.x*.05*t,Math.max(v.y,v.y+lift),v.z+to.z*.05*t);
            pl.fallDistance=0;pl.hurtMarked=true;
        }
    }
    private static final Set<UUID> steadyNow=new HashSet<>();
    private static boolean refreshSteady(Level level,ServerPlayer p){boolean s=steady(level,p);if(s)steadyNow.add(p.getUUID());else steadyNow.remove(p.getUUID());return s;}

    // ------------------------------------------------------------------ loose thread ends
    /** A thread end let go high overhead, drifting down toward someone. It leans toward the seam as it falls. */
    static boolean threadEnd(ServerLevel level,ServerPlayer near,long seed){
        double a=Math.floorMod(seed,360L)*Math.PI/180,r=5+Math.floorMod(seed>>>9,10L);
        BlockPos ground=TideService.findSafe(level,near.blockPosition().offset((int)(Math.cos(a)*r),0,(int)(Math.sin(a)*r)),4);
        if(ground==null||!MirrorService.open(level,ground))return false;
        ItemEntity item=new ItemEntity(level,ground.getX()+.5,ground.getY()+17+Math.floorMod(seed>>>4,6L),ground.getZ()+.5,new ItemStack(Veilborne.SCARLET_THREAD.get()));
        item.setNoGravity(true);item.setDeltaMovement(0,-.03,0);item.setExtendedLifetime();item.setPickUpDelay(10);
        item.getPersistentData().putBoolean(DRIFT,true);item.getPersistentData().putLong(END,ThreadData.get(level).seed);
        level.addFreshEntity(item);drifting.add(item);
        return true;
    }
    private static void drift(ServerLevel level,ThreadTimeline.Seam seam){
        Vec3 mid=ThreadTimeline.middle(seam);
        drifting.removeIf(i->{
            if(!i.isAlive())return true;
            if(i.onGround()||i.isInWater()||i.tickCount>1400){i.setNoGravity(false);i.getPersistentData().remove(DRIFT);return true;}
            Vec3 to=new Vec3(mid.x-i.getX(),0,mid.z-i.getZ()).normalize();
            double sway=Math.sin(i.tickCount*.07+i.getId());
            i.setDeltaMovement(to.x*.012+sway*.01,-.035,to.z*.012-sway*.006);
            return false;
        });
    }

    // ------------------------------------------------------------------ the unsewn pocket
    private static void pocket(ServerLevel level,ThreadData d,double p,ServerPlayer observer){
        if(TideConfig.THREAD_VISITORS.get()&&(d.variant&ThreadData.VARIANT_POCKET)!=0&&ThreadTimeline.pocketOpen(p)&&!d.has(ThreadData.POCKET)&&d.age%20==0){
            BlockPos near=d.hasLand()?d.landA:observer.blockPosition();
            Vec3 toward=observer.position().subtract(Vec3.atCenterOf(near));
            BlockPos probe=near.offset((int)Math.signum(toward.x)*3,0,(int)Math.signum(toward.z)*3);
            BlockPos at=TideService.findSafe(level,probe,5);
            if(at!=null&&MirrorService.open(level,at)&&d.takeFlag(ThreadData.POCKET)){d.pocket=at;d.pocketAt=d.age;broadcast(level);}
        }
        if(!d.has(ThreadData.TUNED)||d.has(ThreadData.SEWN_UP))return;
        if(!level.getBlockState(d.anchor).is(Veilborne.TUNING_ANCHOR.get())){d.flags&=~ThreadData.TUNED;d.anchor=BlockPos.ZERO;d.setDirty();broadcast(level);return;}
        if(d.age-d.tuneAt>=TUNE_TICKS&&d.takeFlag(ThreadData.SEWN_UP)){
            TideService.drop(level,d.anchor.above(),new ItemStack(Veilborne.SEAM_SILK.get(),3));
            level.playSound(null,d.anchor,Veilborne.THREAD_KNOT.get(),SoundSource.AMBIENT,1,1.1f);
            // Whoever is still near: the spirits it wakes knock people about, so the one who tuned it may have been driven off a way.
            for(ServerPlayer pl:level.players())if(pl.position().distanceTo(Vec3.atCenterOf(d.anchor))<32){TideService.record(pl,POCKET);VeilGrade.deed(pl,VeilGrade.THREAD,d.seed,VeilGrade.THREAD_POCKET);}
            broadcast(level);
        }
    }
    /** A tuning anchor set down inside the open pocket starts to gather what is floating there, and wakes its threads. */
    @SubscribeEvent public static void placed(BlockEvent.EntityPlaceEvent e){
        if(!(e.getLevel() instanceof ServerLevel level)||!level.dimension().equals(Level.OVERWORLD)||!e.getPlacedBlock().is(Veilborne.TUNING_ANCHOR.get()))return;
        ThreadData d=ThreadData.get(level);
        if(tune(level,d,e.getPos())&&e.getEntity() instanceof ServerPlayer p)p.displayClientMessage(Component.translatable("message.veilborne.anchor_tuning"),true);
    }
    public static boolean tune(ServerLevel level,ThreadData d,BlockPos at){
        if(!d.active||!d.hasPocket()||d.has(ThreadData.TUNED)||d.has(ThreadData.SEWN_UP)||!ThreadTimeline.pocketOpen(d.progress()))return false;
        Vec3 c=Vec3.atBottomCenterOf(d.pocket);
        if(Math.hypot(at.getX()+.5-c.x,at.getZ()+.5-c.z)>POCKET_RANGE||Math.abs(at.getY()-c.y)>3)return false;
        d.takeFlag(ThreadData.TUNED);d.anchor=at.immutable();d.tuneAt=d.age;d.setDirty();
        level.playSound(null,at,Veilborne.THREAD_ANCHOR.get(),SoundSource.AMBIENT,1,1);
        if(level.getDifficulty()!=Difficulty.PEACEFUL)for(int k=0;k<SPIRITS;k++){
            ThreadSpirit s=Veilborne.SPIRIT.get().create(level);
            if(s==null)continue;
            double a=k*Math.PI*2/SPIRITS+d.seed%5;
            s.moveTo(c.x+Math.cos(a)*POCKET_RANGE,c.y+1.6,c.z+Math.sin(a)*POCKET_RANGE,(float)Math.toDegrees(a),0);
            level.addFreshEntity(s);
        }
        broadcast(level);
        return true;
    }

    // ------------------------------------------------------------------ thread ends caught
    @SubscribeEvent public static void endCaught(ItemEntityPickupEvent.Post e){
        if(!(e.getPlayer() instanceof ServerPlayer p)||!e.getItemEntity().getPersistentData().contains(END))return;
        long seed=e.getItemEntity().getPersistentData().getLong(END);
        ThreadData d=ThreadData.get(p.server.overworld());
        if(d.active&&d.seed==seed&&VeilGrade.count(p,VeilGrade.THREAD,seed,"ends")>=3)VeilGrade.deed(p,VeilGrade.THREAD,seed,VeilGrade.THREAD_ENDS);
    }

    // ------------------------------------------------------------------ what the stitch leaves, or cutting it
    /** Once the thread is knotted and cut, the two feet of the world stitch keep a pair of sewn lamps, unless someone cut it first. */
    public static void pair(ServerLevel level,ThreadData d,double p,List<ServerPlayer> witnesses){
        if(p<ThreadTimeline.CUT||!d.hasLand()||d.has(ThreadData.SNIPPED)||!d.takeFlag(ThreadData.PAIRED))return;
        BlockPos near=LoomLinks.leavePair(level,d.landA,d.landB);
        if(near==null)return;
        for(ServerPlayer pl:witnesses){
            VeilGrade.site(pl,VeilGrade.SITE_PAIR,near,d.landB,0);
            if(pl.position().distanceTo(Vec3.atCenterOf(near))<96)pl.displayClientMessage(Component.translatable("message.veilborne.pair_left"),false);
        }
        level.playSound(null,near,Veilborne.THREAD_CHIME.get(),SoundSource.AMBIENT,1,.8f);
    }
    /** Between the needle coming up through the land and the hand pulling the thread tight, shears at a foot of the stitch can cut it instead. */
    public static boolean canSnip(ThreadData d,Vec3 at){
        double p=d.progress();
        if(!d.active||!d.hasLand()||d.has(ThreadData.SNIPPED)||d.has(ThreadData.PAIRED))return false;
        if(p<ThreadTimeline.pAtSew(ThreadTimeline.THROUGH)||p>=ThreadTimeline.pAtSew(ThreadTimeline.TUG0))return false;
        return Math.hypot(at.x-d.landA.getX()-.5,at.z-d.landA.getZ()-.5)<SNIP_RANGE||Math.hypot(at.x-d.landB.getX()-.5,at.z-d.landB.getZ()-.5)<SNIP_RANGE;
    }
    public static void snip(ServerLevel level,ThreadData d,ServerPlayer p){
        d.takeFlag(ThreadData.SNIPPED);
        level.playSound(null,p.blockPosition(),Veilborne.THREAD_SNAP.get(),SoundSource.PLAYERS,1.3f,.8f);
        TideService.drop(level,p.blockPosition(),new ItemStack(Veilborne.SCARLET_THREAD.get(),6));
        TideService.drop(level,p.blockPosition(),new ItemStack(Veilborne.SEAM_SILK.get()));
        VeilGrade.deed(p,VeilGrade.THREAD,d.seed,VeilGrade.THREAD_CUT);
        for(ServerPlayer pl:level.players())if(!pl.isSpectator()&&d.strength(pl.getX(),pl.getZ())>.25)pl.displayClientMessage(Component.translatable(pl==p?"message.veilborne.snipped":"message.veilborne.snipped_other"),false);
        broadcast(level);
    }
    @SubscribeEvent public static void shears(PlayerInteractEvent.RightClickItem e){
        if(!(e.getEntity() instanceof ServerPlayer p)||!(e.getItemStack().getItem() instanceof net.minecraft.world.item.ShearsItem))return;
        if(!p.level().dimension().equals(Level.OVERWORLD))return;
        ThreadData d=ThreadData.get(p.serverLevel());
        if(!canSnip(d,p.position()))return;
        snip(p.serverLevel(),d,p);
        e.setCanceled(true);e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

    // ------------------------------------------------------------------ commands
    static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> command(){
        return VeilService.weather("scarletseam",TideConfig.THREAD_DURATION::get,ThreadService::begin,ThreadService::end,l->ThreadData.get(l).active,
            (l,p)->{var d=ThreadData.get(l);d.age=(int)(d.duration*p/100.0);d.setDirty();broadcast(l);},
            l->{var d=ThreadData.get(l);return "绯线缝世 · "+(d.active?ThreadTimeline.phase(d.progress())+" "+d.age/20+" / "+d.duration/20+"s · "+d.center.toShortString()
                +(d.hasLand()?" · 缝地 "+d.landA.toShortString()+" → "+d.landB.toShortString():"")+(d.hasPocket()?" · 口袋 "+d.pocket.toShortString():""):"静候");});
    }
    private ThreadService(){}
}
