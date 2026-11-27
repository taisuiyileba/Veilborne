package com.suiyi.veilborne;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 镜天巡礼 on the server: timeline, the extra door and its gallery, the late traveller, the misaligned mirrors, the
 * glass beasts, the pilgrim's walk and the keepsake. Observation bits continue the shared record word:
 * 2048 first silver, 4096 the pilgrim overhead, 8192 the extra door, 16384 the gallery, 32768 the present
 * mirror, 65536 following the late traveller, and 131072 (kept secret) the pilgrim recognising you.
 */
public final class MirrorService {
    public static final int FIRST=2048, PILGRIM=4096, DOOR=8192, CORRIDOR=16384, FRAMES=32768, TRAVELER=65536, KNOWN=131072;
    public static final double SHARD_RANGE=5, SHARD_LIFT=1.1;
    public static final int SHARD_WARN=40, TRAIL=1100, DOOR_LOOK=20, FRAME_LOOK=24, KNOWN_LOOK=30;
    /** The beasts of a burst shard: the first wave, then, if all of them are beaten and someone stays, a larger second one. */
    public static final int WAVE_ONE=3, WAVE_TWO=5;
    public static final String BEAST_SHARD="VeilborneShard";
    private record Look(String key,int ticks){}
    private static final Map<UUID,Look> looking=new HashMap<>();
    private static final Map<UUID,ArrayDeque<Vec3>> trails=new HashMap<>();
    private static final Set<UUID> travelled=new HashSet<>();

    public static void sync(ServerPlayer p){
        MirrorData d=MirrorData.get(p.serverLevel());
        if(p.connection!=null&&p.connection.hasChannel(MirrorPacket.TYPE))PacketDistributor.sendToPlayer(p,MirrorPacket.from(d,TideService.records(p),p.getPersistentData().getLong(MirrorGallery.REWARD)==d.seed));
    }
    public static void broadcast(ServerLevel level){level.getServer().getPlayerList().getPlayers().forEach(MirrorService::sync);}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p);}

    public static int variants(ServerLevel level){
        if(!TideConfig.MIRROR_BEASTS.get())return MirrorData.VARIANT_FRAMES;
        int r=level.random.nextInt(4);
        return r<2?MirrorData.VARIANT_FRAMES|MirrorData.VARIANT_BEASTS:r==2?MirrorData.VARIANT_FRAMES:MirrorData.VARIANT_BEASTS;
    }
    public static void begin(ServerLevel level,BlockPos origin,int seconds){
        if(TideData.get(level).active)TideService.end(level);
        if(MoonData.get(level).active)MoonService.end(level);
        if(ThreadData.get(level).active)ThreadService.end(level);
        if(HushData.get(level).active)HushService.end(level);
        if(DreamData.get(level).active)DreamService.end(level);
        MirrorData d=MirrorData.get(level);
        if(d.active)end(level);
        d.begin(origin,level.random.nextLong(),seconds*20,TideConfig.RADIUS.get(),variants(level));
        looking.clear();trails.clear();travelled.clear();
        broadcast(level);
    }
    public static void end(ServerLevel level){
        MirrorData d=MirrorData.get(level);d.active=false;d.setDirty();
        VeilGrade.settle(level,VeilGrade.MIRROR,d.seed);
        TideService.rest(level);
        for(var e:com.google.common.collect.ImmutableList.copyOf(level.getEntities().getAll())){
            if(e instanceof MirrorTraveler t)t.discard();
            else if(e instanceof MirrorFrames f)f.discard();
            else if(e instanceof MirrorBeast b)b.crack();
        }
        for(ServerPlayer p:new ArrayList<>(level.getServer().getPlayerList().getPlayers()))if(MirrorGallery.inGallery(p.level()))MirrorGallery.leave(p);
        looking.clear();trails.clear();travelled.clear();
        broadcast(level);
    }

    @SubscribeEvent public static void tick(LevelTickEvent.Post e){
        if(!(e.getLevel() instanceof ServerLevel level)||!level.dimension().equals(Level.OVERWORLD))return;
        MirrorData d=MirrorData.get(level);
        if(!d.active)return;
        var server=level.getServer();
        if(server.getPlayerList().getPlayers().isEmpty())return;
        if(++d.age>=d.duration){end(level);return;}
        d.setDirty();
        double p=d.progress();
        for(ServerPlayer pl:new ArrayList<>(server.getPlayerList().getPlayers()))if(MirrorGallery.inGallery(pl.level()))MirrorGallery.tick(pl,d);
        var witnesses=level.players().stream().filter(pl->!pl.isSpectator()&&d.strength(pl.getX(),pl.getZ())>.25).toList();
        for(var pl:witnesses){
            var trail=trails.computeIfAbsent(pl.getUUID(),k->new ArrayDeque<>());
            trail.addLast(pl.position());while(trail.size()>TRAIL)trail.removeFirst();
        }
        if(d.age%20==0){
            broadcast(level);
            for(var pl:witnesses){
                VeilGrade.witness(pl,VeilGrade.MIRROR,d.seed);
                if((TideService.records(pl)&FIRST)==0&&MirrorTimeline.silver(p)>.05){
                    TideService.record(pl,FIRST);
                    if(TideConfig.AUTO_JOURNAL.get()&&!pl.getInventory().contains(new ItemStack(Veilborne.JOURNAL.get())))TideService.give(pl,new ItemStack(Veilborne.JOURNAL.get()));
                }
                if(d.has(MirrorData.PILGRIM)&&MirrorTimeline.pilgrim(p)>.5&&level.canSeeSky(pl.blockPosition())){
                    Vec3 at=MirrorTimeline.pilgrimAt(Vec3.atBottomCenterOf(d.pilgrimOrigin),d.pilgrimYaw,MirrorTimeline.along(MirrorTimeline.walk(p)));
                    if(Math.hypot(at.x-pl.getX(),at.z-pl.getZ())<MirrorTimeline.BESIDE+48){TideService.record(pl,PILGRIM);VeilGrade.deed(pl,VeilGrade.MIRROR,d.seed,VeilGrade.MIRROR_PILGRIM);}
                }
            }
        }
        if(witnesses.isEmpty())return;
        ServerPlayer observer=witnesses.get(0);
        // The extra door: a wall near someone, preferably one they built, that only the mirror shows with a door in it.
        if(p>=.29&&p<.6&&!d.hasDoor()&&d.age%40==0){
            Door door=chooseDoor(level,observer.blockPosition(),d.seed);
            boolean free=false;
            if(door==null&&p>=.34){door=freeDoor(level,observer.blockPosition(),d.seed);free=door!=null;}
            if(door!=null&&d.takeFlag(MirrorData.DOOR)){
                d.door=door.wall();d.doorFace=door.face();d.doorAt=d.age;
                if(free)d.takeFlag(MirrorData.FREESTANDING);
                broadcast(level);
            }
        }
        if(TideConfig.MIRROR_VISITORS.get()&&(d.variant&MirrorData.VARIANT_FRAMES)!=0&&p>=.31&&p<.62&&!d.has(MirrorData.FRAMES)&&d.age%40==0){
            MirrorFrames frames=planFrames(level,observer,d.seed);
            if(frames!=null&&d.takeFlag(MirrorData.FRAMES)){level.addFreshEntity(frames);level.playSound(null,frames.blockPosition(),Veilborne.MIRROR_CHIME.get(),SoundSource.AMBIENT,.6f,.7f);}
        }
        if(TideConfig.MIRROR_BEASTS.get()&&(d.variant&MirrorData.VARIANT_BEASTS)!=0&&p>=.34&&p<.66&&!d.has(MirrorData.SHARDS)&&d.age%40==0){
            BlockPos a=shardSpot(level,observer.blockPosition(),d.seed,null),b=a==null?null:shardSpot(level,observer.blockPosition(),d.seed+7,a);
            if(a!=null&&d.takeFlag(MirrorData.SHARDS)){d.shards[0]=a;d.shards[1]=b==null?BlockPos.ZERO:b;broadcast(level);}
        }
        shards(level,d,p);
        if(d.age%20==0)waves(level,d);
        if(d.revealed()&&d.has(MirrorData.FREESTANDING)&&MirrorTimeline.doorOpen(p)&&TideConfig.MIRROR_GALLERY.get()){
            Vec3 c=MirrorTimeline.doorCenter(d.door,d.doorFace,true);
            for(var pl:witnesses)if(Math.hypot(pl.getX()-c.x,pl.getZ()-c.z)<.55&&Math.abs(pl.getY()-d.door.getY())<1)MirrorGallery.enter(pl,d);
        }
        if(TideConfig.MIRROR_VISITORS.get()&&p>=.2&&p<.62&&d.age%40==0)for(var pl:witnesses)if(!travelled.contains(pl.getUUID()))traveller(level,pl,d);
        if(d.age%5==0)for(MirrorTraveler t:level.getEntities(Veilborne.TRAVELER.get(),x->x.state()==MirrorTraveler.State.POINT||x.state()==MirrorTraveler.State.FADE))
            for(var pl:witnesses)if(pl.position().distanceTo(t.position())<3.2){TideService.record(pl,TRAVELER);VeilGrade.deed(pl,VeilGrade.MIRROR,d.seed,VeilGrade.MIRROR_TRAVELER);}
        // The pilgrim's walk is laid out once, straight through the spot where someone is standing.
        if(p>=MirrorTimeline.PILGRIM_START&&!d.has(MirrorData.PILGRIM)){
            BlockPos under=Surface.topPos(level,observer.blockPosition());
            d.pilgrimOrigin=under;d.pilgrimYaw=(float)Math.toRadians(Math.floorMod(d.seed>>>11,360L));d.pilgrimAt=d.age;d.takeFlag(MirrorData.PILGRIM);
            broadcast(level);
        }
        if(p>=.94&&!d.has(MirrorData.KEEPSAKE)){
            BlockPos at=TideService.findSafe(level,observer.blockPosition().offset(2,0,1),5);
            if(at!=null&&d.takeFlag(MirrorData.KEEPSAKE)){d.keepsake=at;d.keepsakeAt=d.age;broadcast(level);}
        }
        if(!d.keepsake.equals(BlockPos.ZERO)&&d.age-d.keepsakeAt==50){
            TideService.drop(level,d.keepsake,new ItemStack(Veilborne.SKY_SHARD_ITEM.get()));
            level.playSound(null,d.keepsake,Veilborne.MIRROR_CHIME.get(),SoundSource.AMBIENT,.8f,1.25f);
        }
        // Animals stand still and look up at their own reflections; while the pilgrim walks, they watch it.
        if(p>.12&&p<.9&&d.age%20==0){
            Vec3 look=d.has(MirrorData.PILGRIM)&&MirrorTimeline.pilgrim(p)>.1
                ?MirrorTimeline.pilgrimAt(Vec3.atBottomCenterOf(d.pilgrimOrigin),d.pilgrimYaw,MirrorTimeline.along(MirrorTimeline.walk(p))).add(0,120,0):observer.position().add(0,200,0);
            for(Animal a:level.getEntitiesOfClass(Animal.class,observer.getBoundingBox().inflate(24)).stream().limit(14).toList()){
                if(d.age%100==0)a.getNavigation().stop();
                a.getLookControl().setLookAt(look.x,look.y,look.z,6,6);
            }
        }
    }

    // ------------------------------------------------------------------ the glass beasts' shards
    private static void shards(ServerLevel level,MirrorData d,double p){
        if(!d.has(MirrorData.SHARDS)||p>=.86)return;
        for(int i=0;i<2;i++){
            BlockPos s=d.shards[i];
            if(s.equals(BlockPos.ZERO)||d.has(i==0?MirrorData.SHARD_BURST_0:MirrorData.SHARD_BURST_1))continue;
            Vec3 c=Vec3.atBottomCenterOf(s);
            boolean near=level.players().stream().anyMatch(pl->!pl.isSpectator()&&!pl.isCreative()&&Math.hypot(pl.getX()-c.x,pl.getZ()-c.z)<SHARD_RANGE&&Math.abs(pl.getY()-c.y)<4);
            switch(shardStep(d.shardWarn[i],near,d.age)){
                case WARN -> {
                    d.shardWarn[i]=d.age;d.setDirty();
                    level.playSound(null,s.above(),Veilborne.MIRROR_WHINE.get(),SoundSource.HOSTILE,1,1);
                    broadcast(level);
                }
                case BURST -> burst(level,d,i);
                case CALM -> {d.shardWarn[i]=0;d.setDirty();broadcast(level);}
                default -> {}
            }
        }
    }
    public enum Shard { NONE, WARN, BURST, CALM }
    /**
     * One tick of a shard's temper. Someone close: it starts ringing. The warning runs {@link #SHARD_WARN} ticks;
     * if anyone is still close then, it bursts, and if everyone has stepped back it calms down and nothing happens.
     */
    public static Shard shardStep(int warnedAt,boolean near,int age){
        if(warnedAt==0)return near?Shard.WARN:Shard.NONE;
        if(age-warnedAt<SHARD_WARN)return Shard.NONE;
        return near?Shard.BURST:Shard.CALM;
    }
    /** The warning ran its course and someone is still at the shard: it breaks and its beasts come out. */
    public static void burst(ServerLevel level,MirrorData d,int i){
        d.takeFlag(i==0?MirrorData.SHARD_BURST_0:MirrorData.SHARD_BURST_1);
        BlockPos s=d.shards[i];
        level.playSound(null,s.above(),Veilborne.MIRROR_BURST.get(),SoundSource.HOSTILE,1,1);
        spawnWave(level,d,i,WAVE_ONE,1);
        broadcast(level);
    }
    private static void spawnWave(ServerLevel level,MirrorData d,int i,int count,int wave){
        BlockPos s=d.shards[i];
        for(int k=0;k<count;k++){
            MirrorBeast b=Veilborne.BEAST.get().create(level);
            if(b==null)continue;
            double a=k*Math.PI*2/count+d.seed%7,r=wave==1?.8:1.6;
            b.moveTo(s.getX()+.5+Math.cos(a)*r,s.getY()+.2,s.getZ()+.5+Math.sin(a)*r,(float)Math.toDegrees(a),0);
            b.getPersistentData().putInt(BEAST_SHARD,i);
            level.addFreshEntity(b);
        }
        d.beastWave[i]=wave;d.setDirty();
    }
    /** A beast of shard i was killed by someone. */
    public static void beastKilled(ServerLevel level,int i){
        MirrorData d=MirrorData.get(level);
        if(!d.active||i<0||i>1||d.beastWave[i]==0||d.beastWave[i]>2)return;
        d.beastKills[i]++;d.setDirty();
    }
    /**
     * The optional fight at a burst shard: beat every beast of the first wave while someone is still at the shard and the
     * glass gathers into a second, larger one; beat that too and the shard leaves its heart behind.
     */
    public static void waves(ServerLevel level,MirrorData d){
        for(int i=0;i<2;i++){
            if(d.beastWave[i]==0||d.beastWave[i]>2)continue;
            Vec3 c=Vec3.atBottomCenterOf(d.shards[i]);
            List<ServerPlayer> near=level.players().stream().filter(pl->!pl.isSpectator()&&pl.position().distanceTo(c)<12).toList();
            if(d.beastWave[i]==1&&d.beastKills[i]>=WAVE_ONE&&!near.isEmpty()&&MirrorTimeline.crack(d.progress())<.99){
                level.playSound(null,d.shards[i].above(),Veilborne.MIRROR_BURST.get(),SoundSource.HOSTILE,1.2f,.7f);
                for(ServerPlayer pl:near)pl.displayClientMessage(Component.translatable("message.veilborne.beasts_second"),true);
                spawnWave(level,d,i,WAVE_TWO,2);
            }else if(d.beastWave[i]==2&&d.beastKills[i]>=WAVE_ONE+WAVE_TWO){
                d.beastWave[i]=3;d.setDirty();
                TideService.drop(level,d.shards[i],new ItemStack(Veilborne.MIRROR_HEART.get()));
                level.playSound(null,d.shards[i].above(),Veilborne.MIRROR_CHIME.get(),SoundSource.AMBIENT,1,.6f);
                for(ServerPlayer pl:level.players())if(!pl.isSpectator()&&pl.position().distanceTo(c)<18){
                    VeilGrade.deed(pl,VeilGrade.MIRROR,d.seed,VeilGrade.MIRROR_BEASTS);
                    pl.displayClientMessage(Component.translatable("message.veilborne.beasts_cleared"),false);
                }
            }
        }
    }
    static BlockPos shardSpot(ServerLevel level,BlockPos near,long seed,BlockPos avoid){
        for(int k=0;k<16;k++){
            double a=(Math.floorMod(seed,360L)+k*97.0)*Math.PI/180,r=13+(k*5%12);
            BlockPos probe=TideService.findSafe(level,near.offset((int)(Math.cos(a)*r),0,(int)(Math.sin(a)*r)),3);
            if(probe==null||!open(level,probe)||avoid!=null&&probe.distSqr(avoid)<12*12)continue;
            if(!level.getBlockState(probe.above(3)).isAir())continue;
            return probe;
        }
        return null;
    }

    // ------------------------------------------------------------------ the late traveller
    /** Looks back through a witness's recent path for a few seconds of walking, near enough to be seen, and replays it. */
    static void traveller(ServerLevel level,ServerPlayer pl,MirrorData d){
        var trail=trails.get(pl.getUUID());
        if(trail==null||trail.size()<260||!level.canSeeSky(pl.blockPosition()))return;
        Vec3[] t=trail.toArray(new Vec3[0]);
        int length=80;
        for(int end=t.length-170;end>=length;end-=10){
            Vec3 e=t[end],s=t[end-length];
            double here=Math.hypot(e.x-pl.getX(),e.z-pl.getZ()),walked=Math.hypot(e.x-s.x,e.z-s.z);
            if(here<7||here>28||walked<5.5)continue;
            if(!level.canSeeSky(BlockPos.containing(e).above()))continue;
            List<Vec3> replay=new ArrayList<>(Arrays.asList(t).subList(end-length,end+1));
            MirrorTraveler traveler=Veilborne.TRAVELER.get().create(level);
            if(traveler==null)return;
            traveler.plan(pl.getUUID(),replay,goal(level,d,e),9);
            level.addFreshEntity(traveler);
            level.playSound(null,BlockPos.containing(s),Veilborne.MIRROR_REWIND.get(),SoundSource.AMBIENT,.6f,.9f);
            travelled.add(pl.getUUID());
            return;
        }
    }
    /** What the traveller walks on toward: the extra door, the three mirrors, a shard, or where the pilgrim will stop. */
    static BlockPos goal(ServerLevel level,MirrorData d,Vec3 from){
        List<BlockPos> options=new ArrayList<>();
        if(d.hasDoor())options.add(d.door.relative(Direction.from2DDataValue(d.doorFace)));
        for(var f:level.getEntities(Veilborne.FRAMES.get(),x->x.state()==MirrorFrames.State.WAITING))options.add(f.blockPosition());
        for(int i=0;i<2;i++)if(!d.shards[i].equals(BlockPos.ZERO))options.add(d.shards[i]);
        BlockPos best=BlockPos.ZERO;double bestD=64;
        for(BlockPos b:options){double dist=Math.hypot(b.getX()-from.x,b.getZ()-from.z);if(dist<bestD){bestD=dist;best=b;}}
        return best;
    }

    // ------------------------------------------------------------------ the extra door
    public record Door(BlockPos wall,int face){}
    /** A flat wall at least three wide and three high with open, sky-lit ground before it. Built walls win over cliffs. */
    public static Door chooseDoor(ServerLevel level,BlockPos near,long seed){
        Door best=null;double bestScore=-1e9;
        for(int dx=-24;dx<=24;dx++)for(int dz=-24;dz<=24;dz++){
            double dist=Math.hypot(dx,dz);
            if(dist<4||dist>24)continue;
            int x=near.getX()+dx,z=near.getZ()+dz;
            if(!level.hasChunk(x>>4,z>>4))continue;
            BlockPos stand=Surface.topPos(level,new BlockPos(x,near.getY(),z));
            if(Math.abs(stand.getY()-near.getY())>8||!standable(level,stand)||!open(level,stand))continue;
            for(Direction d:Direction.Plane.HORIZONTAL){
                BlockPos wall=stand.relative(d);
                if(!flatWall(level,wall,d))continue;
                BlockState face=level.getBlockState(wall);
                double score=(natural(face)?1:4)-Math.abs(dist-13)*.12+((seed^wall.asLong()*31)&255)/2550.0;
                if(score>bestScore){bestScore=score;best=new Door(wall.immutable(),d.getOpposite().get2DDataValue());}
            }
        }
        return best;
    }
    /** No wall anywhere near: the mirror shows a doorway standing on its own in the open. */
    public static Door freeDoor(ServerLevel level,BlockPos near,long seed){
        double a=Math.floorMod(seed>>>7,360L)*Math.PI/180;
        BlockPos at=TideService.findSafe(level,near.offset((int)(Math.cos(a)*11),0,(int)(Math.sin(a)*11)),6);
        if(at==null||!open(level,at))return null;
        Vec3 to=Vec3.atCenterOf(near).subtract(Vec3.atCenterOf(at));
        return new Door(at,Direction.getNearest(to.x,0,to.z).get2DDataValue());
    }
    /** Nothing overhead: the column's highest motion-blocking block is below this spot. */
    static boolean open(Level level,BlockPos p){
        return p.getY()>=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,p.getX(),p.getZ());
    }
    static boolean standable(Level level,BlockPos p){
        BlockState floor=level.getBlockState(p.below());
        return floor.isFaceSturdy(level,p.below(),Direction.UP)&&level.getBlockState(p).getCollisionShape(level,p).isEmpty()&&level.getBlockState(p.above()).getCollisionShape(level,p.above()).isEmpty()
            &&level.getFluidState(p).isEmpty()&&!floor.is(Blocks.MAGMA_BLOCK);
    }
    /** Three by three of full blocks, seen from the {@code out} side. */
    static boolean flatWall(Level level,BlockPos wall,Direction toward){
        Direction side=toward.getClockWise();
        for(int s=-1;s<=1;s++)for(int y=0;y<=2;y++){
            BlockPos b=wall.relative(side,s).above(y);
            if(!level.getBlockState(b).isSolidRender(level,b))return false;
            BlockPos front=b.relative(toward.getOpposite());
            if(s==0&&y<=1&&!level.getBlockState(front).getCollisionShape(level,front).isEmpty())return false;
        }
        return true;
    }
    static boolean natural(BlockState s){
        return s.is(BlockTags.DIRT)||s.is(BlockTags.BASE_STONE_OVERWORLD)||s.is(BlockTags.SAND)||s.is(BlockTags.TERRACOTTA)||s.is(Blocks.GRAVEL)||s.is(Blocks.SNOW_BLOCK)
            ||s.is(BlockTags.ICE)||s.is(Blocks.CLAY)||s.is(BlockTags.COAL_ORES)||s.is(BlockTags.IRON_ORES)||s.is(BlockTags.COPPER_ORES)||s.is(Blocks.SANDSTONE)||s.is(Blocks.RED_SANDSTONE);
    }
    /** Walking into the revealed door. */
    @SubscribeEvent public static void useDoor(PlayerInteractEvent.RightClickBlock e){
        if(!(e.getEntity() instanceof ServerPlayer p)||!p.level().dimension().equals(Level.OVERWORLD))return;
        MirrorData d=MirrorData.get(p.serverLevel());
        if(!d.active||!d.revealed()||!d.hasDoor())return;
        BlockPos pos=e.getPos();
        if(!(pos.equals(d.door)||pos.equals(d.door.above()))||e.getFace()==null||e.getFace().get2DDataValue()!=d.doorFace||!e.getFace().getAxis().isHorizontal())return;
        e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);
        if(!MirrorTimeline.doorOpen(d.progress())){p.displayClientMessage(Component.translatable("message.veilborne.door_closed"),true);return;}
        if(!MirrorGallery.enter(p,d))p.displayClientMessage(Component.translatable("message.veilborne.door_sealed"),true);
    }

    // ------------------------------------------------------------------ the three mirrors
    static MirrorFrames planFrames(ServerLevel level,ServerPlayer observer,long seed){
        for(int k=0;k<14;k++){
            double a=(Math.floorMod(seed>>>3,360L)+k*53.0)*Math.PI/180,r=10+(k*3%7);
            BlockPos spot=TideService.findSafe(level,observer.blockPosition().offset((int)(Math.cos(a)*r),0,(int)(Math.sin(a)*r)),2);
            if(spot==null||!open(level,spot))continue;
            Vec3 f=Vec3.atBottomCenterOf(spot),away=f.subtract(observer.position());
            double yaw=Math.atan2(away.z,away.x);
            MirrorFrames frames=Veilborne.FRAMES.get().create(level);
            if(frames==null)return null;
            frames.setPos(f.x,f.y,f.z);frames.setup(yaw,seed);
            boolean ok=true;
            for(int i=0;i<3&&ok;i++){
                Vec3 at=frames.framePos(i);
                int y=Surface.top(level,(int)Math.floor(at.x),(int)Math.floor(at.z));
                BlockPos b=BlockPos.containing(at.x,y,at.z);
                ok=Math.abs(y-spot.getY())<=1&&level.getBlockState(b).isAir()&&level.getBlockState(b.above()).isAir()&&level.getBlockState(b.above(2)).isAir();
            }
            if(ok)return frames;
        }
        return null;
    }

    // ------------------------------------------------------------------ the prism
    /** Held up and aimed: reveals the door, chooses among the three mirrors, and meets the pilgrim's gaze. */
    public static void observe(ServerPlayer p,int held){
        if(!p.level().dimension().equals(Level.OVERWORLD))return;
        MirrorData d=MirrorData.get(p.serverLevel());
        if(!d.active)return;
        double prog=d.progress();
        Vec3 eye=p.getEyePosition(),look=p.getLookAngle();
        String key=null;
        if(d.hasDoor()&&!d.revealed()&&MirrorTimeline.door(prog)>.5&&MirrorTimeline.aimed(eye,look,MirrorTimeline.doorCenter(d.door,d.doorFace,d.has(MirrorData.FREESTANDING)),1.15,22))key="door";
        MirrorFrames target=null;int frame=-1;
        if(key==null)for(MirrorFrames f:p.serverLevel().getEntities(Veilborne.FRAMES.get(),x->x.state()==MirrorFrames.State.WAITING&&x.distanceTo(p)<18)){
            for(int i=0;i<3;i++)if(MirrorTimeline.aimed(eye,look,f.glass(i),.7,18)){target=f;frame=i;key="frame"+f.getId()+":"+i;}
        }
        // The tower stands far off, so anything nearer that the prism is held on comes first.
        BlockPos tower=null;
        if(key==null&&!d.has(MirrorData.VAULT)&&MirrorVault.visible(prog)){
            tower=MirrorVault.base(p.level(),d.center,d.seed);
            if(tower!=null&&MirrorTimeline.aimed(eye,look,Vec3.atBottomCenterOf(tower).add(0,9,0),3.4,110))key="tower";
        }
        if(key==null&&d.has(MirrorData.PILGRIM)){
            double w=MirrorTimeline.walk(prog);
            Vec3 at=MirrorTimeline.pilgrimAt(Vec3.atBottomCenterOf(d.pilgrimOrigin),d.pilgrimYaw,MirrorTimeline.along(w));
            // Meeting its eye: standing where it stopped for, looking up and toward it.
            Vec3 spot=Vec3.atBottomCenterOf(d.pilgrimOrigin),to=new Vec3(at.x-p.getX(),0,at.z-p.getZ()).normalize();
            double flat=Math.hypot(look.x,look.z);
            boolean toward=flat>1e-3&&(look.x*to.x+look.z*to.z)/flat>.8&&look.y>.22&&look.y<.9;
            if(MirrorTimeline.gaze(w)>.5&&Math.hypot(spot.x-p.getX(),spot.z-p.getZ())<40&&toward)key="pilgrim";
        }
        Look was=looking.get(p.getUUID());
        int ticks=key==null?0:was!=null&&was.key().equals(key)?was.ticks()+1:1;
        looking.put(p.getUUID(),new Look(key==null?"":key,ticks));
        if(key==null)return;
        if(key.equals("door")&&ticks>=DOOR_LOOK&&d.takeFlag(MirrorData.REVEALED)){
            d.revealedAt=d.age;
            TideService.record(p,DOOR);
            VeilGrade.deed(p,VeilGrade.MIRROR,d.seed,VeilGrade.MIRROR_DOOR);
            p.serverLevel().playSound(null,d.door,Veilborne.MIRROR_DOOR.get(),SoundSource.AMBIENT,1,1.15f);
            p.displayClientMessage(Component.translatable("message.veilborne.door_revealed"),false);
            broadcast(p.serverLevel());
        }else if(key.equals("tower")&&ticks>=MirrorVault.LOOK&&d.takeFlag(MirrorData.VAULT)){
            ServerLevel level=p.serverLevel();
            BlockPos chest=MirrorVault.build(level,tower,d.seed);
            d.vault=chest==null?tower:chest;d.setDirty();
            level.playSound(null,tower,Veilborne.MIRROR_DOOR.get(),SoundSource.AMBIENT,1,.7f);
            level.sendParticles(Veilborne.MIRROR_MOTE.get(),tower.getX()+.5,tower.getY()+.4,tower.getZ()+.5,40,1.2,.3,1.2,.02);
            VeilService.cue(level,new VeilCue(VeilCue.VAULT,Vec3.atBottomCenterOf(tower),chest==null?0:1),160);
            p.displayClientMessage(Component.translatable(chest==null?"message.veilborne.vault_lost":"message.veilborne.vault_revealed"),false);
            VeilGrade.deed(p,VeilGrade.MIRROR,d.seed,VeilGrade.MIRROR_VAULT);
            if(chest!=null)for(ServerPlayer pl:level.players())if(pl==p||pl.position().distanceTo(Vec3.atCenterOf(tower))<64)VeilGrade.site(pl,VeilGrade.SITE_VAULT,tower,chest,0);
            looking.remove(p.getUUID());
            broadcast(level);
        }else if(target!=null&&ticks>=FRAME_LOOK){
            target.observe(frame,p);looking.remove(p.getUUID());
        }else if(key.equals("pilgrim")&&ticks>=KNOWN_LOOK&&(TideService.records(p)&KNOWN)==0){
            TideService.record(p,KNOWN);
            VeilGrade.deed(p,VeilGrade.MIRROR,d.seed,VeilGrade.MIRROR_KNOWN);
            p.serverLevel().playSound(null,p.blockPosition(),Veilborne.MIRROR_CHIME.get(),SoundSource.AMBIENT,.8f,.6f);
        }
    }
    public static void stopObserving(ServerPlayer p){looking.remove(p.getUUID());}

    // ------------------------------------------------------------------ commands
    static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> command(){
        return VeilService.weather("mirrorsky",TideConfig.MIRROR_DURATION::get,MirrorService::begin,MirrorService::end,l->MirrorData.get(l).active,
            (l,p)->{var d=MirrorData.get(l);d.age=(int)(d.duration*p/100.0);d.setDirty();broadcast(l);},
            l->{var d=MirrorData.get(l);return "镜天巡礼 · "+(d.active?MirrorTimeline.phase(d.progress())+" "+d.age/20+" / "+d.duration/20+"s · "+d.center.toShortString()
                +(d.hasDoor()?" · 门 "+d.door.toShortString()+(d.revealed()?" 已现":""):""):"静候");});
    }
    private MirrorService(){}
}
