package com.suiyi.veilborne;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 苍翠梦潮 on the server: timeline, the dream tree and the petal path that leads to it, the wandering seed and the vine
 * enclosure it opens, the lost school at a window, the deer's road and its glance at whoever waits quietly by the tree, the
 * dream seeds of the seedfall and how they are guided to the soil, the antler twig at the end, and the drifting-leaf charm.
 * Observations go in the second record word (TideService.records2): 128 the forest coming down, 256 the deer passing,
 * 512 following the petal path to its end, 1024 the deer's glance, 2048 guiding a dream seed down, 4096 bringing the lost
 * school out, 8192 the vine enclosure, and 16384 (kept secret) a seed landed in one of the deer's hoof rings.
 */
public final class DreamService {
    public static final int FIRST=128, DEER=256, PATH=512, GLANCE=1024, SEED=2048, LOST=4096, ENCLOSURE=8192, FOOTPRINT=16384;
    public static final int CHARM_USES=40, SEEDS_EACH=3, QUIET_TICKS=100;
    public static final double QUIET_RANGE=14, PATH_REACH=3.5, DEER_SEEN=200, HOOF_REACH=7;
    private static final Map<UUID,double[]> quiet=new HashMap<>();
    private static final Map<UUID,Long> walked=new HashMap<>();
    private static final Map<UUID,Integer> seedsGiven=new HashMap<>(),lastSeed=new HashMap<>();
    private static final Set<UUID> hinted=new HashSet<>(),gliding=new HashSet<>();
    private static UUID enclosureOwner;
    private static int pathTries,fallsFor=-1;
    private static List<DreamTimeline.Footfall> falls=List.of();

    public static void sync(ServerPlayer p){
        DreamData d=DreamData.get(p.serverLevel());
        if(p.connection!=null&&p.connection.hasChannel(DreamPacket.TYPE))PacketDistributor.sendToPlayer(p,DreamPacket.from(d,TideService.records2(p)));
    }
    public static void broadcast(ServerLevel level){level.getServer().getPlayerList().getPlayers().forEach(DreamService::sync);}
    public static void cue(ServerLevel level,DreamCue cue,double range){
        for(ServerPlayer p:level.players())
            if(p.position().distanceTo(cue.at())<range&&p.connection!=null&&p.connection.hasChannel(DreamCue.TYPE))PacketDistributor.sendToPlayer(p,cue);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p);}

    public static int variants(ServerLevel level){
        if(!TideConfig.DREAM_VISITORS.get())return 0;
        int r=level.random.nextInt(5);
        return r<2?DreamData.ALL:r==2?DreamData.VARIANT_PATH|DreamData.VARIANT_ENCLOSURE:r==3?DreamData.VARIANT_PATH|DreamData.VARIANT_LOST:DreamData.VARIANT_ENCLOSURE|DreamData.VARIANT_LOST;
    }
    public static void begin(ServerLevel level,BlockPos origin,int seconds){
        if(TideData.get(level).active)TideService.end(level);
        if(MoonData.get(level).active)MoonService.end(level);
        if(MirrorData.get(level).active)MirrorService.end(level);
        if(ThreadData.get(level).active)ThreadService.end(level);
        if(HushData.get(level).active)HushService.end(level);
        DreamData d=DreamData.get(level);
        if(d.active)end(level);
        d.begin(origin,level.random.nextLong(),seconds*20,TideConfig.RADIUS.get(),variants(level));
        reset();
        broadcast(level);
    }
    private static void reset(){quiet.clear();walked.clear();seedsGiven.clear();lastSeed.clear();hinted.clear();enclosureOwner=null;pathTries=0;fallsFor=-1;falls=List.of();}
    public static void end(ServerLevel level){
        DreamData d=DreamData.get(level);d.active=false;d.setDirty();
        VeilGrade.settle(level,VeilGrade.DREAM,d.seed);
        TideService.rest(level);
        for(var e:com.google.common.collect.ImmutableList.copyOf(level.getEntities().getAll())){
            if(e instanceof DreamSeed s)s.finish();
            else if(e instanceof LeafSchool s)s.finish();
        }
        reset();
        broadcast(level);
    }

    @SubscribeEvent public static void tick(LevelTickEvent.Post e){
        if(!(e.getLevel() instanceof ServerLevel level)||!level.dimension().equals(Level.OVERWORLD))return;
        DreamData d=DreamData.get(level);
        if(!d.active)return;
        if(level.getServer().getPlayerList().getPlayers().isEmpty())return;
        if(++d.age>=d.duration){end(level);return;}
        d.setDirty();
        double p=d.progress();
        var witnesses=level.players().stream().filter(pl->!pl.isSpectator()&&d.strength(pl.getX(),pl.getZ())>.25).toList();
        if(d.age%20==0){
            broadcast(level);
            for(var pl:witnesses){
                VeilGrade.witness(pl,VeilGrade.DREAM,d.seed);
                if((TideService.records2(pl)&FIRST)==0&&p>=DreamTimeline.ARRIVED){
                    TideService.record2(pl,FIRST);
                    if(TideConfig.AUTO_JOURNAL.get()&&!pl.getInventory().contains(new ItemStack(Veilborne.JOURNAL.get())))TideService.give(pl,new ItemStack(Veilborne.JOURNAL.get()));
                }
            }
            deerSeen(level,d,p,witnesses);
        }
        if(d.age%10==0)quietTrack(d,witnesses);
        enclosure(level,d);
        gift(level,d);
        if(witnesses.isEmpty())return;
        ServerPlayer observer=witnesses.get(0);
        tree(level,d,p,observer);
        if(TideConfig.DREAM_VISITORS.get()){
            path(level,d,p,witnesses);
            wander(level,d,p,observer);
            lost(level,d,p,witnesses);
        }
        walkers(d,p,witnesses);
        route(level,d,p,observer);
        glance(level,d,p,witnesses);
        seedfall(level,d,p,witnesses);
        twig(level,d,p,observer);
        animals(level,d,p,observer);
    }

    // ------------------------------------------------------------------ keeping still
    public static void quietTrack(DreamData d,List<ServerPlayer> witnesses){
        for(ServerPlayer pl:witnesses){
            double[] q=quiet.get(pl.getUUID());
            if(q==null||pl.isSprinting()||Math.abs(pl.getX()-q[0])+Math.abs(pl.getZ()-q[2])>1.2||Math.abs(pl.getY()-q[1])>1.2)quiet.put(pl.getUUID(),new double[]{pl.getX(),pl.getY(),pl.getZ(),d.age});
        }
    }
    /** Has stood still (a step or two either way at most) for five seconds. */
    public static boolean quiet(ServerPlayer p,int age){double[] q=quiet.get(p.getUUID());return q!=null&&age-q[3]>=QUIET_TICKS;}

    // ------------------------------------------------------------------ the dream tree
    private static void tree(ServerLevel level,DreamData d,double p,ServerPlayer observer){
        if(d.has(DreamData.TREE)||p<DreamTimeline.TREE_AT||p>DreamTimeline.ROAD-.2/13||d.age%20!=0)return;
        BlockPos spot=treeSpot(level,observer.blockPosition(),d.seed);
        if(spot!=null&&d.takeFlag(DreamData.TREE)){
            d.tree=spot;d.treeAt=d.age;
            level.playSound(null,spot,Veilborne.DREAM_SPROUT.get(),SoundSource.AMBIENT,1.3f,.75f);
            broadcast(level);
        }
    }
    /** An open, dry, level patch of ground some way off, not under trees or a roof. */
    public static BlockPos treeSpot(ServerLevel level,BlockPos near,long seed){
        for(int k=0;k<72;k++){
            double a=(Math.floorMod(seed>>>13,360L)+k*53.0)*Math.PI/180,r=20+(k*7%14);
            int x=Mth.floor(near.getX()+.5+Math.cos(a)*r),z=Mth.floor(near.getZ()+.5+Math.sin(a)*r);
            if(!level.hasChunk(x>>4,z>>4))continue;
            int g=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            if(Math.abs(g-near.getY())>8||level.getHeight(Heightmap.Types.MOTION_BLOCKING,x,z)>g)continue;
            BlockPos floor=new BlockPos(x,g-1,z);
            if(!level.getFluidState(floor).isEmpty()||!level.getBlockState(floor).isFaceSturdy(level,floor,Direction.UP))continue;
            boolean ok=true;
            for(int dx=-2;dx<=2&&ok;dx++)for(int dz=-2;dz<=2&&ok;dz++){
                int h=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x+dx,z+dz);
                if(Math.abs(h-g)>1||level.getHeight(Heightmap.Types.MOTION_BLOCKING,x+dx,z+dz)>h+1)ok=false;
            }
            for(int y=0;y<9&&ok;y++)if(!level.getBlockState(new BlockPos(x,g+y,z)).canBeReplaced())ok=false;
            if(ok)return new BlockPos(x,g,z);
        }
        return null;
    }

    // ------------------------------------------------------------------ the petal path
    private static void path(ServerLevel level,DreamData d,double p,List<ServerPlayer> witnesses){
        if((d.variant&DreamData.VARIANT_PATH)==0||d.has(DreamData.PATH)||!d.hasTree()||p<DreamTimeline.PATH_FROM||p>DreamTimeline.PATH_UNTIL-1.2/13||d.age%40!=0)return;
        if(d.age-d.treeAt<160)return;
        for(ServerPlayer pl:witnesses){
            double dist=Math.hypot(pl.getX()-d.tree.getX()-.5,pl.getZ()-d.tree.getZ()-.5);
            if(dist<9||dist>64)continue;
            List<BlockPos> way=walk(level,pl.blockPosition(),d.tree);
            if(way!=null&&d.takeFlag(DreamData.PATH)){
                d.path.clear();d.path.addAll(way);d.pathAt=d.age;walked.clear();
                level.playSound(null,way.get(0),Veilborne.DREAM_BLOOM.get(),SoundSource.AMBIENT,.9f,1.1f);
                broadcast(level);return;
            }
        }
        // No way through on foot: this time there is no path, and nothing pretends there is.
        if(++pathTries>10)d.takeFlag(DreamData.PATH);
    }
    /**
     * A walkable way over the land from one place toward another, a block at a time: never up more than one block or down
     * more than two, never over water or lava, never onto leaves, never under a roof or up onto one. Where the way ahead is
     * blocked it turns aside, as far as walking along a wall; where it cannot, or where the way would wander much further than
     * the straight line, there is no path. At most {@link DreamTimeline#PATH_POINTS} points.
     */
    public static List<BlockPos> walk(Level level,BlockPos from,BlockPos to){
        List<BlockPos> out=new ArrayList<>();
        Set<Long> seen=new HashSet<>();
        Vec3 at=Vec3.atBottomCenterOf(Surface.topPos(level,from)),goal=Vec3.atBottomCenterOf(to);
        out.add(BlockPos.containing(at));seen.add(out.get(0).asLong());
        double[] turns={0,.45,-.45,.9,-.9,1.35,-1.35,1.8,-1.8,2.25,-2.25};
        double straight=Math.hypot(goal.x-at.x,goal.z-at.z);
        for(int step=0;step<150;step++){
            Vec3 flat=new Vec3(goal.x-at.x,0,goal.z-at.z);
            if(flat.length()<2.7)return thin(out);
            flat=flat.normalize();
            Vec3 next=null;
            for(double t:turns){
                Vec3 dir=new Vec3(flat.x*Math.cos(t)-flat.z*Math.sin(t),0,flat.x*Math.sin(t)+flat.z*Math.cos(t));
                Vec3 c=at.add(dir);
                int x=Mth.floor(c.x),z=Mth.floor(c.z);
                if(!level.hasChunk(x>>4,z>>4))continue;
                int top=Surface.top(level,x,z),dy=top-Mth.floor(at.y+.01);
                if(dy>1||dy<-2)continue;
                BlockPos floor=new BlockPos(x,top-1,z);
                if(seen.contains(new BlockPos(x,top,z).asLong()))continue;
                BlockState fs=level.getBlockState(floor);
                if(!level.getFluidState(floor).isEmpty()||fs.getBlock() instanceof LeavesBlock||!fs.isFaceSturdy(level,floor,Direction.UP))continue;
                if(!level.getBlockState(new BlockPos(x,top+1,z)).getCollisionShape(level,new BlockPos(x,top+1,z)).isEmpty())continue;
                if(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)>top)continue;
                next=new Vec3(c.x,top,c.z);break;
            }
            if(next==null||step>straight*2.2+12)return null;
            at=next;
            BlockPos b=BlockPos.containing(at);
            if(seen.add(b.asLong()))out.add(b);
        }
        return null;
    }
    private static List<BlockPos> thin(List<BlockPos> way){
        if(way.size()<=DreamTimeline.PATH_POINTS)return way;
        List<BlockPos> out=new ArrayList<>();
        for(int i=0;i<DreamTimeline.PATH_POINTS;i++)out.add(way.get((int)Math.round(i*(way.size()-1)/(double)(DreamTimeline.PATH_POINTS-1))));
        return out;
    }
    /** Walking the path: the points passed along it, and whether they arrive at the tree having followed most of it. */
    private static void walkers(DreamData d,double p,List<ServerPlayer> witnesses){
        if(d.path.size()<2||p>DreamTimeline.PATH_UNTIL||d.age%5!=0)return;
        int n=d.path.size();
        for(ServerPlayer pl:witnesses){
            long mask=walked.getOrDefault(pl.getUUID(),0L);
            for(int i=0;i<n;i++){
                BlockPos b=d.path.get(i);
                if(Math.hypot(pl.getX()-b.getX()-.5,pl.getZ()-b.getZ()-.5)<1.9&&Math.abs(pl.getY()-b.getY())<2.5)mask|=1L<<Math.min(63,i);
            }
            walked.put(pl.getUUID(),mask);
            if(Long.bitCount(mask)>=n/2&&d.hasTree()&&pl.position().distanceTo(Vec3.atBottomCenterOf(d.tree))<PATH_REACH+1){TideService.record2(pl,PATH);VeilGrade.deed(pl,VeilGrade.DREAM,d.seed,VeilGrade.DREAM_PATH);}
        }
    }

    // ------------------------------------------------------------------ the wandering seed and the vine enclosure
    private static void wander(ServerLevel level,DreamData d,double p,ServerPlayer observer){
        if((d.variant&DreamData.VARIANT_ENCLOSURE)==0||d.has(DreamData.WANDER)||p<DreamTimeline.WANDER_FROM||p>DreamTimeline.WANDER_UNTIL-.7/13||d.age%40!=0)return;
        Vec3 home=wanderHome(level,observer);
        if(home==null)return;
        DreamSeed s=Veilborne.DREAM_SEED_ENTITY.get().create(level);
        if(s==null||!d.takeFlag(DreamData.WANDER))return;
        s.place(DreamSeed.Kind.WANDER,home.add(0,2.5,0),home);
        level.addFreshEntity(s);
    }
    /** Round the crops, if there are some near; otherwise a little way ahead of whoever it has come to. */
    public static Vec3 wanderHome(ServerLevel level,ServerPlayer near){
        BlockPos o=near.blockPosition();Vec3 best=null;double bd=1e9;
        for(BlockPos b:BlockPos.betweenClosed(o.offset(-12,-3,-12),o.offset(12,3,12))){
            if(!level.getBlockState(b).is(net.minecraft.world.level.block.Blocks.FARMLAND)||!level.getBlockState(b.above()).is(BlockTags.CROPS))continue;
            double dd=b.distSqr(o);
            if(dd>9&&dd<bd){bd=dd;best=Vec3.atBottomCenterOf(b.above()).add(0,1.1,0);}
        }
        if(best!=null)return best;
        Vec3 look=near.getLookAngle();Vec3 flat=new Vec3(look.x,0,look.z);
        flat=flat.lengthSqr()<1e-3?new Vec3(1,0,0):flat.normalize();
        for(int k=0;k<8;k++){
            double t=(k%2==0?1:-1)*k*.4;
            Vec3 dir=new Vec3(flat.x*Math.cos(t)-flat.z*Math.sin(t),0,flat.x*Math.sin(t)+flat.z*Math.cos(t));
            Vec3 at=near.position().add(dir.scale(6.5));
            int x=Mth.floor(at.x),z=Mth.floor(at.z);
            int g=Surface.top(level,x,z);
            if(Math.abs(g-near.getY())>3||!level.getBlockState(new BlockPos(x,g+1,z)).isAir()||!level.getBlockState(new BlockPos(x,g+2,z)).isAir())continue;
            return new Vec3(x+.5,g+1.3,z+.5);
        }
        return null;
    }
    /** Someone touched the wandering seed: vines grow round where they stand, with a gap behind them. */
    public static boolean openEnclosure(ServerLevel level,ServerPlayer p,DreamSeed seed){
        DreamData d=DreamData.get(level);
        if(!d.active||(d.variant&DreamData.VARIANT_ENCLOSURE)==0||d.has(DreamData.ENCLOSURE))return false;
        d.takeFlag(DreamData.ENCLOSURE);
        d.enclosure=Surface.topPos(level,p.blockPosition());d.enclosureAt=d.age;d.lightStep=0;d.lightAt=d.age+DreamTimeline.ENCLOSE_TICKS;d.closedAt=0;
        Vec3 look=p.getLookAngle();
        d.enclosureExit=(float)Math.atan2(-look.z,-look.x);
        enclosureOwner=p.getUUID();
        level.playSound(null,d.enclosure,Veilborne.DREAM_VINE.get(),SoundSource.AMBIENT,1,1);
        p.displayClientMessage(Component.translatable("message.veilborne.enclosure"),true);
        broadcast(level);
        return true;
    }
    /** Where the k-th light floats, on the land. */
    public static Vec3 lightPoint(Level level,BlockPos center,long seed,int k,double exit){
        Vec3 s=DreamTimeline.lightSpot(Vec3.atBottomCenterOf(center),seed,k,exit);
        return new Vec3(s.x,Surface.top(level,Mth.floor(s.x),Mth.floor(s.z))+1.1,s.z);
    }
    public static void enclosure(ServerLevel level,DreamData d){
        if(!d.enclosureOpen())return;
        Vec3 c=Vec3.atBottomCenterOf(d.enclosure);
        ServerPlayer owner=enclosureOwner==null?null:level.getServer().getPlayerList().getPlayer(enclosureOwner);
        // Walking out through the vines, or away, gives it up: nothing is lost by it.
        if(owner==null||owner.isSpectator()||Math.hypot(owner.getX()-c.x,owner.getZ()-c.z)>DreamTimeline.RING+1.6){close(level,d,false);return;}
        if(d.age<d.lightAt)return;
        Vec3 light=lightPoint(level,d.enclosure,d.seed,d.lightStep,d.enclosureExit);
        for(ServerPlayer pl:level.players()){
            if(pl.isSpectator()||pl.position().add(0,.9,0).distanceTo(light)>DreamTimeline.TOUCH+.2)continue;
            cue(level,new DreamCue(DreamCue.CHIME,light,d.lightStep),48);
            level.playSound(null,light.x,light.y,light.z,Veilborne.DREAM_CHIME.get(),SoundSource.AMBIENT,.9f,(float)Math.pow(2,new int[]{0,2,4,7,9}[Math.min(4,d.lightStep)]/12.0));
            d.lightStep++;
            if(d.lightStep>=DreamTimeline.LIGHTS){close(level,d,true);return;}
            d.lightAt=d.age+22;d.setDirty();broadcast(level);return;
        }
        if(d.age>d.lightAt+DreamTimeline.LIGHT_TICKS)close(level,d,false);
    }
    private static void close(ServerLevel level,DreamData d,boolean won){
        d.takeFlag(won?DreamData.ENCLOSURE_WON:DreamData.ENCLOSURE_LOST);d.closedAt=d.age;
        Vec3 c=Vec3.atBottomCenterOf(d.enclosure);
        if(won){
            TideService.drop(level,d.enclosure,new ItemStack(Veilborne.FULL_DREAM_SEED.get()));
            level.playSound(null,d.enclosure,Veilborne.DREAM_BLOOM.get(),SoundSource.AMBIENT,1.2f,1);
            for(ServerPlayer pl:level.players())if(!pl.isSpectator()&&Math.hypot(pl.getX()-c.x,pl.getZ()-c.z)<DreamTimeline.RING+2.5){TideService.record2(pl,ENCLOSURE);VeilGrade.deed(pl,VeilGrade.DREAM,d.seed,VeilGrade.DREAM_ENCLOSURE);}
            cue(level,new DreamCue(DreamCue.BLOOM,c.add(0,.1,0),2),64);
        }else level.playSound(null,d.enclosure,Veilborne.DREAM_VINE.get(),SoundSource.AMBIENT,.7f,.7f);
        broadcast(level);
    }

    // ------------------------------------------------------------------ the lost school
    private static void lost(ServerLevel level,DreamData d,double p,List<ServerPlayer> witnesses){
        if((d.variant&DreamData.VARIANT_LOST)==0||d.has(DreamData.LOST)||p<DreamTimeline.LOST_FROM||p>DreamTimeline.LOST_UNTIL-.9/13||d.age%40!=0)return;
        for(ServerPlayer pl:witnesses){
            Object[] spot=windowSpot(level,pl);
            if(spot==null)continue;
            LeafSchool s=Veilborne.LEAF_SCHOOL.get().create(level);
            if(s==null||!d.takeFlag(DreamData.LOST))return;
            s.place((Vec3)spot[0],(Direction)spot[1]);
            level.addFreshEntity(s);return;
        }
    }
    /** The outside of a window or a door of a roofed room near someone: [where the school hovers, which way is out]. */
    public static Object[] windowSpot(ServerLevel level,ServerPlayer near){
        BlockPos o=near.blockPosition();Object[] best=null;double bestScore=-1e9;
        for(BlockPos b:BlockPos.betweenClosed(o.offset(-16,-5,-16),o.offset(16,5,16))){
            BlockState s=level.getBlockState(b);
            boolean glass=s.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_PANES)||s.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS);
            boolean door=s.getBlock() instanceof DoorBlock&&s.getValue(DoorBlock.HALF)==DoubleBlockHalf.UPPER;
            if(!glass&&!door)continue;
            for(Direction side:Direction.Plane.HORIZONTAL){
                BlockPos out=b.relative(side),in=b.relative(side.getOpposite());
                if(!level.getBlockState(out).isAir()||!level.getBlockState(out.relative(side)).isAir())continue;
                if(level.getHeight(Heightmap.Types.MOTION_BLOCKING,out.getX(),out.getZ())>out.getY()+1)continue;
                if(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,in.getX(),in.getZ())<=in.getY()+1)continue;
                double dist=Math.sqrt(b.distSqr(o));
                double score=-Math.abs(dist-9)+(door?-1:0);
                if(score>bestScore){bestScore=score;best=new Object[]{Vec3.atCenterOf(b).add(side.getStepX()*.95,door?-.3:0,side.getStepZ()*.95),side};}
            }
        }
        return best;
    }
    /** The lost school has been brought out under the open sky: it goes home. */
    public static void schoolHome(ServerLevel level,LeafSchool school,ServerPlayer guide){
        long seed=DreamData.get(level).seed;
        TideService.record2(guide,LOST);VeilGrade.deed(guide,VeilGrade.DREAM,seed,VeilGrade.DREAM_LOST);
        for(ServerPlayer pl:level.players())if(pl!=guide&&!pl.isSpectator()&&pl.distanceTo(school)<16){TideService.record2(pl,LOST);VeilGrade.deed(pl,VeilGrade.DREAM,seed,VeilGrade.DREAM_LOST);}
        level.playSound(null,school.getX(),school.getY(),school.getZ(),Veilborne.DREAM_FISH.get(),SoundSource.AMBIENT,1.1f,1);
        cue(level,new DreamCue(DreamCue.MERGE,school.position(),0),64);
    }
    /** What leaf-fish and dream seeds will follow: a flower lamp, any flower or sapling, a dream seed or golden leaf, a light. */
    public static boolean lure(Player p){return lureItem(p.getMainHandItem())||lureItem(p.getOffhandItem());}
    public static boolean lureItem(ItemStack s){
        if(s.isEmpty())return false;
        if(s.is(Veilborne.FLOWER_LAMP_ITEM.get())||s.is(Veilborne.DREAM_SEED.get())||s.is(Veilborne.FULL_DREAM_SEED.get())||s.is(Veilborne.GOLDEN_LEAF.get()))return true;
        return s.is(ItemTags.FLOWERS)||s.is(ItemTags.SAPLINGS)||HushService.isLight(s);
    }

    // ------------------------------------------------------------------ the deer
    private static void route(ServerLevel level,DreamData d,double p,ServerPlayer observer){
        if(d.has(DreamData.ROUTE)||p<DreamTimeline.ROAD)return;
        Vec3 origin=d.hasTree()?Vec3.atBottomCenterOf(d.tree):Vec3.atBottomCenterOf(Surface.topPos(level,observer.blockPosition()));
        double base=Math.floorMod(d.seed>>>21,360L)*Math.PI/180,bestScore=1e18;float bestYaw=0;int bestSide=1;
        for(int i=0;i<12;i++)for(int side=-1;side<=1;side+=2){
            double yaw=base+i*Math.PI/6;
            double score=roughness(level,new DreamTimeline.Route(origin,yaw,side),origin.y);
            if(score<bestScore){bestScore=score;bestYaw=(float)yaw;bestSide=side;}
        }
        if(!d.takeFlag(DreamData.ROUTE))return;
        d.route=BlockPos.containing(origin);d.routeYaw=bestYaw;d.routeSide=bestSide;d.routeAt=d.age;
        fallsFor=-1;
        broadcast(level);
    }
    /** How uneven and wet the way is: the deer walks over the land, so it prefers its road level and dry. */
    static double roughness(Level level,DreamTimeline.Route r,double y0){
        double score=0,prev=y0;
        for(double s=0;s<=DreamTimeline.PATH;s+=26){
            Vec3 at=r.at(s);int x=Mth.floor(at.x),z=Mth.floor(at.z);
            if(!level.hasChunk(x>>4,z>>4)){score+=8;continue;}
            int h=Surface.top(level,x,z);
            if(!level.getFluidState(new BlockPos(x,h-1,z)).isEmpty())score+=6;
            score+=Math.abs(h-prev)+Math.abs(h-y0)*.12;prev=h;
        }
        return score;
    }
    public static List<DreamTimeline.Footfall> footfalls(Level level,DreamData d){
        if(!d.hasRoute())return List.of();
        if(fallsFor!=d.routeAt){falls=DreamTimeline.footfalls(d.route(),(x,z)->Surface.top(level,Mth.floor(x),Mth.floor(z)));fallsFor=d.routeAt;}
        return falls;
    }
    private static void deerSeen(ServerLevel level,DreamData d,double p,List<ServerPlayer> witnesses){
        if(!d.hasRoute()||DreamTimeline.deer(p)<.6)return;
        Vec3 body=d.route().at(DreamTimeline.along(DreamTimeline.walk(p)));
        for(ServerPlayer pl:witnesses)if(Math.hypot(pl.getX()-body.x,pl.getZ()-body.z)<DEER_SEEN&&level.canSeeSky(pl.blockPosition())){TideService.record2(pl,DEER);VeilGrade.deed(pl,VeilGrade.DREAM,d.seed,VeilGrade.DREAM_DEER);}
    }
    /** The deer stops by the tree and lowers its head. If someone is waiting there quietly, it is them it looks at. */
    public static void glance(ServerLevel level,DreamData d,double p,List<ServerPlayer> witnesses){
        if(!d.hasRoute()||d.has(DreamData.GLANCE_PLAYER)||p<DreamTimeline.stopFrom()||p>DreamTimeline.stopUntil()-.35/13||d.age%10!=0)return;
        Vec3 focus=d.hasTree()?Vec3.atBottomCenterOf(d.tree):d.route().origin();
        for(ServerPlayer pl:witnesses){
            if(Math.hypot(pl.getX()-focus.x,pl.getZ()-focus.z)>QUIET_RANGE||!quiet(pl,d.age))continue;
            d.takeFlag(DreamData.GLANCE);d.takeFlag(DreamData.GLANCE_PLAYER);
            d.glance=pl.blockPosition();d.glanceAt=d.age;
            d.giftAt=Math.max(d.age+50,(int)Math.round(DreamTimeline.glanceP()*d.duration));
            TideService.record2(pl,GLANCE);
            VeilGrade.deed(pl,VeilGrade.DREAM,d.seed,VeilGrade.DREAM_GLANCE);
            level.playSound(null,pl.getX(),pl.getY()+2,pl.getZ(),Veilborne.DREAM_GLANCE.get(),SoundSource.AMBIENT,1.2f,1);
            broadcast(level);return;
        }
    }
    /** The seed it lets fall from its antlers comes down to whoever it looked at. */
    public static void gift(ServerLevel level,DreamData d){
        if(!d.has(DreamData.GLANCE_PLAYER)||d.has(DreamData.GIFT_DROPPED)||d.age<d.giftAt+DreamTimeline.GIFT_FALL)return;
        d.takeFlag(DreamData.GIFT_DROPPED);
        TideService.drop(level,d.glance,new ItemStack(Veilborne.DREAM_SEED.get()));
        level.playSound(null,d.glance,Veilborne.DREAM_SPROUT.get(),SoundSource.AMBIENT,.9f,1.3f);
        broadcast(level);
    }
    private static void animals(ServerLevel level,DreamData d,double p,ServerPlayer observer){
        if(!d.hasRoute()||DreamTimeline.deer(p)<.5||d.age%20!=0)return;
        Vec3 body=d.route().at(DreamTimeline.along(DreamTimeline.walk(p))).add(0,70,0);
        boolean still=DreamTimeline.gaze(DreamTimeline.walk(p))>.2;
        for(Animal a:level.getEntitiesOfClass(Animal.class,observer.getBoundingBox().inflate(24)).stream().limit(14).toList()){
            if(still||d.age%100==0)a.getNavigation().stop();
            a.getLookControl().setLookAt(body.x,body.y,body.z,6,6);
        }
    }

    // ------------------------------------------------------------------ the seedfall
    private static void seedfall(ServerLevel level,DreamData d,double p,List<ServerPlayer> witnesses){
        if(p<DreamTimeline.SEEDFALL_FROM||p>=DreamTimeline.SEEDFALL_UNTIL||d.age%20!=0)return;
        int from=(int)Math.round(DreamTimeline.SEEDFALL_FROM*d.duration),gap=(int)((DreamTimeline.SEEDFALL_UNTIL-DreamTimeline.SEEDFALL_FROM)*d.duration/(SEEDS_EACH+.4));
        for(ServerPlayer pl:witnesses){
            int given=seedsGiven.getOrDefault(pl.getUUID(),0);
            if(given>=SEEDS_EACH||d.age<from+given*gap||d.age-lastSeed.getOrDefault(pl.getUUID(),-9999)<160)continue;
            for(int k=0;k<14;k++){
                double a=level.random.nextDouble()*Math.PI*2,r=4+level.random.nextDouble()*5;
                int x=Mth.floor(pl.getX()+Math.cos(a)*r),z=Mth.floor(pl.getZ()+Math.sin(a)*r);
                if(!level.hasChunk(x>>4,z>>4))continue;
                int g=Surface.top(level,x,z);
                if(!level.canSeeSky(new BlockPos(x,g,z)))continue;
                DreamSeed s=Veilborne.DREAM_SEED_ENTITY.get().create(level);
                if(s==null)return;
                Vec3 at=new Vec3(x+.5,g+15+level.random.nextDouble()*6,z+.5);
                s.place(DreamSeed.Kind.FALL,at,at);
                level.addFreshEntity(s);
                seedsGiven.put(pl.getUUID(),given+1);lastSeed.put(pl.getUUID(),d.age);
                if(hinted.add(pl.getUUID()))pl.displayClientMessage(Component.translatable("message.veilborne.seed_hint"),true);
                break;
            }
        }
    }
    /** A dream seed came down on the soil and sprouted. In one of the deer's hoof rings it comes up full. */
    public static void landed(ServerLevel level,DreamSeed seed,ServerPlayer guide){
        DreamData d=DreamData.get(level);
        boolean hoof=inHoofRing(level,d,seed.position());
        TideService.drop(level,BlockPos.containing(seed.position()),new ItemStack(hoof?Veilborne.FULL_DREAM_SEED.get():Veilborne.DREAM_SEED.get()));
        level.playSound(null,seed.getX(),seed.getY(),seed.getZ(),Veilborne.DREAM_SPROUT.get(),SoundSource.AMBIENT,1,hoof?.8f:1.15f);
        cue(level,new DreamCue(DreamCue.BLOOM,seed.position(),hoof?1:0),64);
        if(guide!=null){
            TideService.record2(guide,SEED);VeilGrade.deed(guide,VeilGrade.DREAM,d.seed,VeilGrade.DREAM_SEED);
            if(hoof){TideService.record2(guide,FOOTPRINT);VeilGrade.deed(guide,VeilGrade.DREAM,d.seed,VeilGrade.DREAM_FOOTPRINT);}
        }
    }
    public static boolean inHoofRing(Level level,DreamData d,Vec3 at){
        if(!d.active||!d.hasRoute())return false;
        double p=d.progress();
        for(DreamTimeline.Footfall f:footfalls(level,d))if(f.p()<=p&&Math.hypot(f.at().x-at.x,f.at().z-at.z)<HOOF_REACH&&Math.abs(f.at().y-at.y)<4)return true;
        return false;
    }

    // ------------------------------------------------------------------ the antler twig
    public static void twig(ServerLevel level,DreamData d,double p,ServerPlayer observer){
        if(!d.has(DreamData.TWIG)&&p>=DreamTimeline.TWIG_AT&&d.age%20==0){
            BlockPos spot=TideService.findSafe(level,observer.blockPosition().offset(3,0,2),5);
            if(spot!=null&&d.takeFlag(DreamData.TWIG)){d.twig=spot;d.twigAt=d.age;broadcast(level);}
        }
        if(d.has(DreamData.TWIG)&&!d.has(DreamData.TWIG_DROPPED)&&d.age>=d.twigAt+DreamTimeline.TWIG_FALL){
            d.takeFlag(DreamData.TWIG_DROPPED);
            TideService.drop(level,d.twig,new ItemStack(Veilborne.ANTLER_TWIG_ITEM.get()));
            level.playSound(null,d.twig,Veilborne.DREAM_CHIME.get(),SoundSource.AMBIENT,.9f,.7f);
            broadcast(level);
        }
    }

    // ------------------------------------------------------------------ the drifting leaf
    /** Carried, the charm catches a fall once it has gone three blocks and lets you down like a leaf, one use each time. */
    @SubscribeEvent public static void glide(PlayerTickEvent.Post e){
        if(!(e.getEntity() instanceof ServerPlayer p)||p.isSpectator())return;
        if(p.onGround()||p.isInWater()||p.getAbilities().flying||p.isFallFlying()||p.isPassenger()){gliding.remove(p.getUUID());return;}
        if(!gliding.contains(p.getUUID())){
            if(p.fallDistance<3||p.getDeltaMovement().y>-.3)return;
            ItemStack charm=ItemStack.EMPTY;
            for(ItemStack s:p.getInventory().items)if(s.is(Veilborne.DRIFTING_LEAF.get())){charm=s;break;}
            if(charm.isEmpty()&&p.getOffhandItem().is(Veilborne.DRIFTING_LEAF.get()))charm=p.getOffhandItem();
            if(charm.isEmpty())return;
            gliding.add(p.getUUID());
            charm.hurtAndBreak(1,p.serverLevel(),p,item->{});
            p.serverLevel().playSound(null,p.blockPosition(),Veilborne.DREAM_RUSTLE.get(),SoundSource.PLAYERS,.6f,1.3f);
        }
        if(p.tickCount%10==0||!p.hasEffect(MobEffects.SLOW_FALLING))p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,24,0,false,false,true));
        p.fallDistance=0;
    }

    // ------------------------------------------------------------------ commands
    static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> command(){
        return VeilService.weather("dreamtide",TideConfig.DREAM_DURATION::get,DreamService::begin,DreamService::end,l->DreamData.get(l).active,
            (l,p)->{var d=DreamData.get(l);d.age=(int)(d.duration*p/100.0);d.setDirty();broadcast(l);},
            l->{var d=DreamData.get(l);return "苍翠梦潮 · "+(d.active?DreamTimeline.phase(d.progress())+" "+d.age/20+" / "+d.duration/20+"s · "+d.center.toShortString()
                +(d.hasTree()?" · 梦树 "+d.tree.toShortString():"")+(d.path.size()>1?" · 花路 "+d.path.size():"")+(d.hasRoute()?" · 鹿路":""):"静候");});
    }
    private DreamService(){}
}
