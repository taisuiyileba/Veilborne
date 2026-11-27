package com.suiyi.veilborne.test;

import com.suiyi.veilborne.*;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.*;

/** 苍翠梦潮: persistence, sync, the order the forest comes and goes, the deer's road and where its hooves come down, one
 *  weather at a time, the petal path over the land, the glance and its gift, seeds that only come down on soil (and the
 *  hoofprint secret), the lost school led home, the vine enclosure won and given up, cleanup, the charm, and the blocks. */
@GameTestHolder(Veilborne.ID)
@PrefixGameTestTemplate(false)
public final class DreamTests {
    /** Open ground above the test enclosure's barrier roof: a grass plot `w` by `w` round o, clear air above it. */
    private static BlockPos plot(GameTestHelper h,int w){
        ServerLevel level=h.getLevel();BlockPos o=h.absolutePos(new BlockPos(8,22,8));
        for(int x=-w;x<=w;x++)for(int z=-w;z<=w;z++){
            level.setBlock(o.offset(x,-1,z),Blocks.GRASS_BLOCK.defaultBlockState(),3);
            for(int y=0;y<14;y++)level.setBlock(o.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        return o;
    }
    private static List<ItemEntity> items(ServerLevel level,BlockPos at,double r,Item item){
        return level.getEntitiesOfClass(ItemEntity.class,new AABB(at).inflate(r),i->i.getItem().is(item));
    }
    private static int count(ServerLevel level,BlockPos at,double r,Item item){
        return items(level,at,r,item).stream().mapToInt(i->i.getItem().getCount()).sum();
    }
    private static void clear(ServerLevel level,BlockPos at,double r){level.getEntitiesOfClass(ItemEntity.class,new AABB(at).inflate(r)).forEach(ItemEntity::discard);}
    private static DreamData full(){
        var d=new DreamData();d.begin(new BlockPos(-30,71,90),88123,15600,320,DreamData.VARIANT_PATH|DreamData.VARIANT_LOST);
        d.age=7020;d.flags=DreamData.TREE|DreamData.PATH|DreamData.ROUTE|DreamData.GLANCE|DreamData.GLANCE_PLAYER|DreamData.ENCLOSURE|DreamData.ENCLOSURE_WON;
        d.tree=new BlockPos(-8,72,101);d.treeAt=3900;d.path.add(new BlockPos(-20,71,95));d.path.add(new BlockPos(-14,72,99));d.pathAt=4400;
        d.route=new BlockPos(-8,72,101);d.routeYaw=1.25f;d.routeSide=-1;d.routeAt=5800;d.glance=new BlockPos(-9,72,100);d.glanceAt=6500;d.giftAt=6600;
        d.enclosure=new BlockPos(-40,70,80);d.enclosureAt=5000;d.enclosureExit=2.5f;d.lightStep=5;d.lightAt=5400;d.closedAt=5500;d.twig=new BlockPos(1,2,3);d.twigAt=7000;
        return d;
    }

    @GameTest(template="empty",batch="60")
    public static void dreamPersistsAcrossReload(GameTestHelper h){
        var d=full();
        var saved=d.save(new CompoundTag(),h.getLevel().registryAccess());
        var loaded=DreamData.load(saved,h.getLevel().registryAccess());
        h.assertTrue(saved.equals(loaded.save(new CompoundTag(),h.getLevel().registryAccess())),"Verdant-dream state changed after reload");
        h.assertTrue(loaded.hasTree()&&loaded.hasRoute()&&loaded.path.size()==2&&loaded.route().side()==-1&&!loaded.takeFlag(DreamData.GLANCE),"The tree, the path, the deer's road or a spent glance was lost");
        h.assertTrue(!loaded.enclosureOpen()&&loaded.has(DreamData.ENCLOSURE_WON),"A finished enclosure reopened after restart");
        d.age=d.duration;
        h.assertTrue(!DreamData.load(d.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess()).active,"A finished verdant dream resumed after restart");
        h.succeed();
    }
    @GameTest(template="empty",batch="61")
    public static void dreamPacketRoundTrip(GameTestHelper h){
        var packet=DreamPacket.from(full(),DreamService.FIRST|DreamService.DEER|DreamService.FOOTPRINT);
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try{DreamPacket.CODEC.encode(b,packet);h.assertTrue(packet.equals(DreamPacket.CODEC.decode(b)),"Verdant-dream snapshot lost authoritative state");}finally{b.release();}
        var cue=new DreamCue(DreamCue.BLOOM,1.5,70.25,-3.75,2);
        b=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try{DreamCue.CODEC.encode(b,cue);h.assertTrue(cue.equals(DreamCue.CODEC.decode(b)),"A bloom was lost on the way");}finally{b.release();}
        h.succeed();
    }
    @GameTest(template="empty",batch="62")
    public static void theForestComesAndGoesInOrder(GameTestHelper h){
        double[] b=DreamTimeline.BOUNDS;
        for(int i=1;i<b.length;i++)h.assertTrue(b[i]>b[i-1],"Phase bounds do not increase at "+i);
        h.assertTrue(DreamTimeline.phase(.5/13)==DreamTimeline.Phase.OMEN&&DreamTimeline.phase(2/13.0)==DreamTimeline.Phase.ROOTS&&DreamTimeline.phase(5/13.0)==DreamTimeline.Phase.LEAVES
            &&DreamTimeline.phase(8/13.0)==DreamTimeline.Phase.DEER&&DreamTimeline.phase(10/13.0)==DreamTimeline.Phase.SEEDFALL&&DreamTimeline.phase(12/13.0)==DreamTimeline.Phase.RETURN,"The phases are out of order");
        // Nothing cuts in or out hard at either end.
        for(var f:List.<java.util.function.DoubleUnaryOperator>of(DreamTimeline::fluff,DreamTimeline::cloudGreen,DreamTimeline::cloudVeil,DreamTimeline::sky,DreamTimeline::fireflies,
                DreamTimeline::light,DreamTimeline::schools,DreamTimeline::goldFall,DreamTimeline::deer,DreamTimeline::dapple))
            h.assertTrue(f.applyAsDouble(0)<1e-9&&f.applyAsDouble(1)<1e-9,"A curve does not start and end at nothing");
        // Clouds soften first, the forest has come down by the time it counts as seen, its roots grow before they withdraw.
        h.assertTrue(DreamTimeline.fluff(.5/13)>.5&&DreamTimeline.canopy(.5/13)==0,"The forest comes before the omen");
        h.assertTrue(DreamTimeline.sky(DreamTimeline.ARRIVED)>.99&&DreamTimeline.canopy(DreamTimeline.ARRIVED)>.95,"The forest has not come down when it is recorded");
        h.assertTrue(DreamTimeline.rootGrow(DreamTimeline.ROOT0)==0&&DreamTimeline.rootGrow(DreamTimeline.ROOT1)==1&&DreamTimeline.rootWithdraw(DreamTimeline.ROOT1)==0&&DreamTimeline.rootWithdraw(12.6/13)==1,"The roots do not grow and then withdraw");
        double prev=-1;
        for(int i=0;i<=200;i++){double c=DreamTimeline.canopy(i/200.0);h.assertTrue(c>=prev-1e-9,"The canopy draws back while it is growing");prev=c;}
        // Gold before the leaves fall, the leaves gone before the return, the veins last.
        h.assertTrue(DreamTimeline.gold(10/13.0)>DreamTimeline.leafLoss(10/13.0)&&DreamTimeline.leafLoss(9.5/13)==0&&DreamTimeline.leafLoss(11.4/13)==1,"The leaves do not turn gold and then fall");
        h.assertTrue(DreamTimeline.veinsFade(11.5/13)==0&&DreamTimeline.veinsFade(12.8/13)==1,"The veins do not stay until the end");
        // The encounters in their windows (and no integer division has turned a window inside out).
        h.assertTrue(DreamTimeline.ROOT0>0&&DreamTimeline.TREE_AT>DreamTimeline.ARRIVED&&DreamTimeline.PATH_FROM>DreamTimeline.TREE_AT&&DreamTimeline.PATH_UNTIL>DreamTimeline.PATH_FROM
            &&DreamTimeline.WANDER_UNTIL>DreamTimeline.WANDER_FROM&&DreamTimeline.LOST_UNTIL>DreamTimeline.LOST_FROM&&DreamTimeline.ROAD<DreamTimeline.DEER_START
            &&DreamTimeline.SEEDFALL_FROM>DreamTimeline.DEER_END&&DreamTimeline.SEEDFALL_UNTIL>DreamTimeline.SEEDFALL_FROM&&DreamTimeline.TWIG_AT>DreamTimeline.SEEDFALL_UNTIL
            &&DreamTimeline.WITHER>DreamTimeline.TWIG_AT&&DreamTimeline.WITHER+.5/13<1,"An encounter window is out of order");
        h.assertTrue(DreamTimeline.stopFrom()<DreamTimeline.glanceP()&&DreamTimeline.glanceP()<DreamTimeline.stopUntil()&&DreamTimeline.stopUntil()<DreamTimeline.DEER_END,"The glance is not inside the deer's stop");
        // The deer walks its road without going back, and stands still while it stops.
        prev=-1;
        for(int i=0;i<=400;i++){double s=DreamTimeline.along(i/400.0);h.assertTrue(s>=prev-1e-9,"The deer walks backwards at "+i);prev=s;}
        h.assertTrue(Math.abs(DreamTimeline.along(0))<1e-6&&Math.abs(DreamTimeline.along(1)-DreamTimeline.PATH)<1,"The deer does not walk its whole road");
        double a=(1-DreamTimeline.STOP)/2;
        h.assertTrue(DreamTimeline.along(a+.01)==DreamTimeline.along(a+DreamTimeline.STOP-.01)&&DreamTimeline.gaze(a+.1)>.99&&DreamTimeline.gaze(.1)==0,"The deer does not stop and look");
        // Hoof rings open, glow, and settle to a faint shadow that stays.
        h.assertTrue(DreamTimeline.hoofRing(0)==0&&DreamTimeline.hoofRing(10)>.99&&DreamTimeline.hoofRing(120)>.2&&DreamTimeline.hoofRing(120)<.35,"The hoof rings do not settle to a lasting shadow");
        h.succeed();
    }
    @GameTest(template="empty",batch="63")
    public static void theDeerWalksItsRoadOnItsFeet(GameTestHelper h){
        var r=new DreamTimeline.Route(new Vec3(100.5,64,-50.5),.7,1);
        Vec3 mid=r.at(DreamTimeline.PATH/2);
        h.assertTrue(Math.abs(Math.hypot(mid.x-100.5,mid.z+50.5)-DreamTimeline.BESIDE)<.01,"The deer does not stop beside the tree");
        h.assertTrue(new Vec3(100.5-mid.x,0,-50.5-mid.z).dot(r.inward(DreamTimeline.PATH/2))>0,"Inward does not point at the tree");
        for(DreamTimeline.Ground g:List.<DreamTimeline.Ground>of((x,z)->64,(x,z)->64+10*Math.sin(x/40)+4*Math.cos(z/23))){
            var falls=DreamTimeline.footfalls(r,g);
            int[] perLeg=new int[4];double last=-1;
            for(var f:falls){
                h.assertTrue(Math.abs(f.at().y-g.y(f.at().x,f.at().z))<1e-9,"A hoof comes down off the ground");
                h.assertTrue(f.p()>=last&&f.p()>=DreamTimeline.DEER_START&&f.p()<=DreamTimeline.DEER_END,"Footfalls are out of time");
                h.assertTrue(!(f.p()>DreamTimeline.stopFrom()+1e-4&&f.p()<DreamTimeline.stopUntil()-1e-4),"A hoof comes down while the deer stands still");
                last=f.p();perLeg[f.leg()]++;
            }
            for(int leg=0;leg<4;leg++)h.assertTrue(Math.abs(perLeg[leg]-DreamTimeline.PATH/DreamTimeline.STRIDE)<=1.5,"Leg "+leg+" takes the wrong number of steps: "+perLeg[leg]);
        }
        for(int leg=0;leg<4;leg++)for(int k=0;k<6;k++){
            double stride=DreamTimeline.plant(r,leg,k).distanceTo(DreamTimeline.plant(r,leg,k+1));
            h.assertTrue(stride>DreamTimeline.STRIDE*.8&&stride<DreamTimeline.STRIDE*1.2,"A stride is the wrong length: "+stride);
        }
        // Left feet on the left of its road, right feet on the right.
        double s=DreamTimeline.plantAlong(0,3);Vec3 t=r.tangent(s),side=new Vec3(-t.z,0,t.x);
        h.assertTrue(DreamTimeline.plant(r,0,3).subtract(r.at(s)).dot(side)*DreamTimeline.plant(r,2,3).subtract(r.at(DreamTimeline.plantAlong(2,3))).dot(side)<0,"Both hind feet come down on one side");
        h.succeed();
    }
    @GameTest(template="empty",batch="64")
    public static void sixWeathersOneAtATime(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,22,8));
        HushService.begin(level,origin,720);DreamService.begin(level,origin,780);
        h.assertTrue(DreamData.get(level).active&&!HushData.get(level).active&&!ThreadData.get(level).active&&!TideData.get(level).active&&!MoonData.get(level).active&&!MirrorData.get(level).active,"The verdant dream started on top of another weather");
        var p=h.makeMockServerPlayerInLevel();p.moveTo(origin.getX()+.5,origin.getY(),origin.getZ()+.5,0,0);
        h.assertTrue(HushItem.sounding(p)==5,"The verdant dream is not heard where it is");
        TideService.begin(level,origin,840);
        h.assertTrue(TideData.get(level).active&&!DreamData.get(level).active,"The sea started on top of the verdant dream");
        DreamService.begin(level,origin,780);MoonService.begin(level,origin,720);
        h.assertTrue(MoonData.get(level).active&&!DreamData.get(level).active,"The bloom started on top of the verdant dream");
        DreamService.begin(level,origin,780);MirrorService.begin(level,origin,780);
        h.assertTrue(MirrorData.get(level).active&&!DreamData.get(level).active,"The mirror started on top of the verdant dream");
        DreamService.begin(level,origin,780);ThreadService.begin(level,origin,780);
        h.assertTrue(ThreadData.get(level).active&&!DreamData.get(level).active,"The seam started on top of the verdant dream");
        DreamService.begin(level,origin,780);HushService.begin(level,origin,720);
        h.assertTrue(HushData.get(level).active&&!DreamData.get(level).active,"The silent daylight started on top of the verdant dream");
        HushService.end(level);
        DreamService.begin(level,origin,780);DreamService.end(level);
        h.assertTrue(TideData.get(level).quietTicks>=24000,"No quiet period after the verdant dream");
        level.getServer().getPlayerList().remove(p);
        h.succeed();
    }
    @GameTest(template="empty",batch="65")
    public static void thePetalPathKeepsToTheLand(GameTestHelper h){
        var level=h.getLevel();BlockPos o=plot(h,12);
        BlockPos from=o.west(10),to=o.east(10);
        var way=DreamService.walk(level,from,to);
        h.assertTrue(way!=null&&way.size()>=6,"No path over open, level ground");
        h.assertTrue(way.get(0).distSqr(from)<3&&Math.hypot(way.get(way.size()-1).getX()-to.getX(),way.get(way.size()-1).getZ()-to.getZ())<4,"The path does not run from you to the tree");
        // A wall across the middle: it goes round, never over.
        for(int z=-2;z<=2;z++)for(int y=0;y<3;y++)level.setBlock(o.offset(0,y,z),Blocks.STONE.defaultBlockState(),3);
        way=DreamService.walk(level,from,to);
        h.assertTrue(way!=null,"The path gave up at a wall it could walk round");
        for(int i=0;i<way.size();i++){
            h.assertTrue(way.get(i).getY()==o.getY(),"The path climbed the wall at "+way.get(i));
            if(i>0)h.assertTrue(Math.abs(way.get(i).getX()-way.get(i-1).getX())<=1&&Math.abs(way.get(i).getZ()-way.get(i-1).getZ())<=1,"The path jumps");
        }
        // A cliff right across: no path, and nothing pretends there is one.
        for(int x=4;x<=12;x++)for(int z=-12;z<=12;z++)for(int y=0;y<4;y++)level.setBlock(o.offset(x,y,z),Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(DreamService.walk(level,from,to.above(4))==null,"A path was laid up a cliff");
        // Nor over water.
        for(int x=4;x<=12;x++)for(int z=-12;z<=12;z++){for(int y=0;y<4;y++)level.setBlock(o.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);level.setBlock(o.offset(x,-1,z),Blocks.WATER.defaultBlockState(),3);}
        h.assertTrue(DreamService.walk(level,from,to)==null,"A path was laid over water");
        h.assertTrue(DreamTimeline.PATH_POINTS<=64,"The path does not fit the walker's record of it");
        h.succeed();
    }
    @GameTest(template="empty",batch="66")
    public static void theDeerLooksAtWhoeverWaitsQuietly(GameTestHelper h){
        var level=h.getLevel();BlockPos o=plot(h,20);
        DreamService.begin(level,o,780);
        var d=DreamData.get(level);
        d.takeFlag(DreamData.TREE);d.tree=o;d.takeFlag(DreamData.ROUTE);d.route=o;d.routeYaw=0;d.routeSide=1;
        d.age=((int)Math.round(DreamTimeline.stopFrom()*d.duration)/10+3)*10;
        var still=h.makeMockServerPlayerInLevel();still.moveTo(o.getX()+3.5,o.getY(),o.getZ()+.5,0,0);
        var fidget=h.makeMockServerPlayerInLevel();fidget.moveTo(o.getX()-3.5,o.getY(),o.getZ()+.5,0,0);
        var far=h.makeMockServerPlayerInLevel();far.moveTo(o.getX()+.5,o.getY(),o.getZ()+19.5,0,0);
        var all=List.of(fidget,far,still);
        DreamService.quietTrack(d,all);
        d.age+=DreamService.QUIET_TICKS;fidget.moveTo(fidget.getX()-3,fidget.getY(),fidget.getZ(),0,0);
        DreamService.quietTrack(d,all);
        h.assertTrue(DreamService.quiet(still,d.age)&&!DreamService.quiet(fidget,d.age)&&DreamService.quiet(far,d.age),"Keeping still is not noticed");
        DreamService.glance(level,d,d.progress(),List.of(far));
        h.assertTrue(!d.has(DreamData.GLANCE_PLAYER),"The deer looked at someone far from the tree");
        DreamService.glance(level,d,d.progress(),all);
        h.assertTrue(d.has(DreamData.GLANCE_PLAYER)&&d.glance.equals(still.blockPosition())&&(TideService.records2(still)&DreamService.GLANCE)!=0,"The deer did not look at the one waiting quietly");
        h.assertTrue((TideService.records2(fidget)&DreamService.GLANCE)==0&&(TideService.records2(far)&DreamService.GLANCE)==0,"The glance was given to the wrong person");
        int glanceAt=d.glanceAt;d.age+=10;
        DreamService.glance(level,d,d.progress(),all);
        h.assertTrue(d.glanceAt==glanceAt,"The deer looked twice");
        // The seed it lets fall comes down where they stood, once.
        d.age=d.giftAt+DreamTimeline.GIFT_FALL-1;DreamService.gift(level,d);
        h.assertTrue(count(level,d.glance,3,Veilborne.DREAM_SEED.get())==0,"The gift fell early");
        d.age++;DreamService.gift(level,d);DreamService.gift(level,d);
        h.assertTrue(count(level,d.glance,3,Veilborne.DREAM_SEED.get())==1,"The gift did not fall once, at the glance");
        DreamService.end(level);clear(level,o,24);
        for(var pl:all)level.getServer().getPlayerList().remove(pl);
        h.succeed();
    }
    @GameTest(template="empty",batch="67",timeoutTicks=200)
    public static void seedsComeDownOnlyOnSoil(GameTestHelper h){
        var level=h.getLevel();BlockPos o=plot(h,14);
        level.setBlock(o.offset(4,-1,0),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(o.offset(-4,-1,0),Blocks.WATER.defaultBlockState(),3);
        level.setBlock(o.offset(0,-1,5),Blocks.MOSS_BLOCK.defaultBlockState(),3);
        level.setBlock(o.offset(-5,3,-5),Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(DreamSeed.soil(level,o.below())&&DreamSeed.soil(level,o.offset(0,-1,5)),"Grass or moss under open sky is not soil");
        h.assertTrue(!DreamSeed.soil(level,o.offset(4,-1,0))&&!DreamSeed.soil(level,o.offset(-4,-1,0)),"Stone or water counts as soil");
        // The hoofprint secret: a seed come down in one of the deer's rings comes up full. The road is laid past the plot, so
        // the tree it stops beside (46 blocks in from its way) is well off and its footfalls pass right by.
        DreamService.begin(level,o,780);
        var d=DreamData.get(level);
        d.takeFlag(DreamData.ROUTE);d.route=o.offset(0,0,(int)DreamTimeline.BESIDE);d.routeYaw=(float)Math.PI;d.routeSide=1;d.routeAt=1;
        var falls=DreamService.footfalls(level,d);
        h.assertTrue(falls.size()>20,"The deer has no footfalls");
        Vec3 c0=Vec3.atBottomCenterOf(o);
        var f=falls.get(0);
        for(var ff:falls)if(Math.hypot(ff.at().x-c0.x,ff.at().z-c0.z)<Math.hypot(f.at().x-c0.x,f.at().z-c0.z))f=ff;
        h.assertTrue(Math.hypot(f.at().x-c0.x,f.at().z-c0.z)<8,"The deer's road does not pass the plot: "+f.at());
        var guide=h.makeMockServerPlayerInLevel();guide.moveTo(o.getX()+.5,o.getY(),o.getZ()+.5,0,0);
        var other=h.makeMockServerPlayerInLevel();other.moveTo(o.getX()+.5,o.getY(),o.getZ()+.5,0,0);
        // Before the deer has walked there, a seed on the same soil comes up ordinary.
        d.age=(int)Math.round(DreamTimeline.DEER_START*d.duration)-100;
        h.assertTrue(!DreamService.inHoofRing(level,d,f.at()),"A hoof ring is there before the hoof");
        var plain=Veilborne.DREAM_SEED_ENTITY.get().create(level);plain.place(DreamSeed.Kind.FALL,f.at(),f.at());
        DreamService.landed(level,plain,other);
        BlockPos at=BlockPos.containing(f.at());
        h.assertTrue(count(level,at,2,Veilborne.DREAM_SEED.get())==1&&count(level,at,2,Veilborne.FULL_DREAM_SEED.get())==0,"A seed on ordinary soil did not leave an ordinary seed");
        h.assertTrue((TideService.records2(other)&DreamService.SEED)!=0&&(TideService.records2(other)&DreamService.FOOTPRINT)==0,"Guiding it down was not recorded, or the hoofprint was recorded before the hoof");
        clear(level,at,4);
        // After the hoof, a seed brought down beside the print comes up full.
        d.age=(int)Math.round(f.p()*d.duration)+80;
        h.assertTrue(DreamService.inHoofRing(level,d,f.at().add(2,0,1))&&!DreamService.inHoofRing(level,d,f.at().add(40,0,40)),"The hoof rings are in the wrong place");
        Vec3 beside=f.at().add(2,0,1);
        var hoof=Veilborne.DREAM_SEED_ENTITY.get().create(level);hoof.place(DreamSeed.Kind.FALL,beside,beside);
        DreamService.landed(level,hoof,guide);
        h.assertTrue(count(level,BlockPos.containing(beside),3,Veilborne.FULL_DREAM_SEED.get())==1,"A seed in a hoof ring did not come up full");
        h.assertTrue((TideService.records2(guide)&(DreamService.SEED|DreamService.FOOTPRINT))==(DreamService.SEED|DreamService.FOOTPRINT),"Guiding it down, or the hoofprint, was not recorded");
        DreamService.end(level);clear(level,o,64);
        level.getServer().getPlayerList().remove(guide);level.getServer().getPlayerList().remove(other);
        // Under a roof, not soil either (light settles a moment after the roof goes on).
        for(int x=-7;x<=-3;x++)for(int z=-7;z<=-3;z++)level.setBlock(o.offset(x,3,z),Blocks.STONE.defaultBlockState(),3);
        h.runAfterDelay(20,()->{
            h.assertTrue(!DreamSeed.soil(level,o.offset(-5,-1,-5)),"Grass under a roof counts as open soil");
            h.succeed();
        });
    }
    @GameTest(template="empty",batch="68")
    public static void theLostSchoolFollowsAFlowerHome(GameTestHelper h){
        var level=h.getLevel();BlockPos o=plot(h,16);
        // A little roofed hut with a window on its south side.
        BlockPos hut=o.offset(-3,0,-8);
        for(int x=0;x<5;x++)for(int z=0;z<5;z++)for(int y=0;y<4;y++){
            boolean wall=x==0||x==4||z==0||z==4||y==3;
            level.setBlock(hut.offset(x,y,z),wall?Blocks.STONE_BRICKS.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
        }
        level.setBlock(hut.offset(2,1,4),Blocks.GLASS_PANE.defaultBlockState(),3);
        var p=h.makeMockServerPlayerInLevel();p.moveTo(hut.getX()+2.5,hut.getY(),hut.getZ()+9.5,180,0);
        Object[] spot=DreamService.windowSpot(level,p);
        h.assertTrue(spot!=null&&spot[1]==Direction.SOUTH,"The school was not put outside the window: "+(spot==null?null:spot[1]));
        h.assertTrue(DreamService.lureItem(new ItemStack(Items.POPPY))&&DreamService.lureItem(new ItemStack(Items.OAK_SAPLING))&&DreamService.lureItem(new ItemStack(Items.TORCH))
            &&DreamService.lureItem(new ItemStack(Veilborne.FLOWER_LAMP_ITEM.get()))&&!DreamService.lureItem(new ItemStack(Items.STONE)),"What the leaf-fish follow is wrong");
        DreamService.begin(level,o,780);
        var school=Veilborne.LEAF_SCHOOL.get().create(level);school.place((Vec3)spot[0],(Direction)spot[1]);level.addFreshEntity(school);
        // Empty-handed nearby, nothing: they stay at the glass.
        p.moveTo(school.getX(),school.getY()-1.4,school.getZ()+2.5,180,0);
        for(int i=0;i<20;i++)school.tick();
        h.assertTrue(school.state()==LeafSchool.State.STUCK,"The school followed someone holding nothing");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.POPPY));
        school.tick();
        h.assertTrue(school.state()==LeafSchool.State.FOLLOW&&school.guide()==p.getId(),"The school did not follow a flower");
        // Lead them out into the open, walking backwards, a little at a time.
        for(int i=0;i<400&&school.state()==LeafSchool.State.FOLLOW;i++){
            if(i%4==0&&p.getZ()<o.getZ()+12)p.moveTo(p.getX(),p.getY(),p.getZ()+.3,0,0);
            school.tick();
        }
        h.assertTrue(school.state()==LeafSchool.State.MERGE,"The school never found the open sky: "+school.state()+" at "+school.position());
        h.assertTrue((TideService.records2(p)&DreamService.LOST)!=0,"Leading them out was not recorded");
        for(int i=0;i<LeafSchool.MERGE_TICKS;i++)school.tick();
        h.assertTrue(count(level,school.blockPosition(),6,Veilborne.GOLDEN_LEAF.get())==2&&school.state()==LeafSchool.State.ASCEND,"The great fish did not leave two golden leaves and go up");
        DreamService.end(level);clear(level,o,32);school.discard();
        level.getServer().getPlayerList().remove(p);
        h.succeed();
    }
    @GameTest(template="empty",batch="69")
    public static void theVineEnclosureIsWonOrLetGo(GameTestHelper h){
        var level=h.getLevel();BlockPos o=plot(h,14);
        var p=h.makeMockServerPlayerInLevel();
        // Won: every light caught in time.
        DreamService.begin(level,o,780);var d=DreamData.get(level);d.variant=DreamData.ALL;
        p.moveTo(o.getX()+.5,o.getY(),o.getZ()+.5,0,0);
        var seed=Veilborne.DREAM_SEED_ENTITY.get().create(level);seed.place(DreamSeed.Kind.WANDER,Vec3.atBottomCenterOf(o).add(1,1,0),Vec3.atBottomCenterOf(o));
        h.assertTrue(DreamService.openEnclosure(level,p,seed)&&d.enclosureOpen(),"Touching the seed did not open the enclosure");
        h.assertTrue(!DreamService.openEnclosure(level,p,seed),"A second enclosure opened in one dream");
        Vec3 c=Vec3.atBottomCenterOf(d.enclosure);
        for(int k=0;k<DreamTimeline.LIGHTS;k++){
            d.age=d.lightAt;
            Vec3 l=DreamService.lightPoint(level,d.enclosure,d.seed,d.lightStep,d.enclosureExit);
            h.assertTrue(Math.hypot(l.x-c.x,l.z-c.z)<DreamTimeline.RING,"Light "+k+" is outside the ring");
            p.moveTo(l.x,l.y-.9,l.z,0,0);
            DreamService.enclosure(level,d);
        }
        h.assertTrue(d.has(DreamData.ENCLOSURE_WON)&&!d.enclosureOpen(),"Catching all five lights did not win the enclosure");
        h.assertTrue(count(level,d.enclosure,3,Veilborne.FULL_DREAM_SEED.get())==1&&(TideService.records2(p)&DreamService.ENCLOSURE)!=0,"Winning left no full seed or no record");
        DreamService.end(level);clear(level,o,24);
        // Well away, so the next enclosures' lights never come up under the one who won this one.
        p.moveTo(o.getX()+60.5,o.getY(),o.getZ()+.5,0,0);
        // Let go: walking out through the gap gives it up; nothing is lost and nothing is given.
        var q=h.makeMockServerPlayerInLevel();
        DreamService.begin(level,o,780);d=DreamData.get(level);d.variant=DreamData.ALL;
        q.moveTo(o.getX()+.5,o.getY(),o.getZ()+.5,0,0);
        h.assertTrue(DreamService.openEnclosure(level,q,seed),"The enclosure did not open again in a new dream");
        d.age=d.lightAt;q.moveTo(o.getX()+.5+DreamTimeline.RING+2.5,o.getY(),o.getZ()+.5,0,0);
        DreamService.enclosure(level,d);
        h.assertTrue(d.has(DreamData.ENCLOSURE_LOST)&&count(level,d.enclosure,4,Veilborne.FULL_DREAM_SEED.get())==0&&(TideService.records2(q)&DreamService.ENCLOSURE)==0,"Walking out of the ring did not simply let it go");
        DreamService.end(level);
        // Too slow: the vines close.
        DreamService.begin(level,o,780);d=DreamData.get(level);d.variant=DreamData.ALL;
        q.moveTo(o.getX()+.5,o.getY(),o.getZ()+.5,0,0);
        DreamService.openEnclosure(level,q,seed);
        d.age=d.lightAt+DreamTimeline.LIGHT_TICKS+1;DreamService.enclosure(level,d);
        h.assertTrue(d.has(DreamData.ENCLOSURE_LOST),"A light that was never caught did not close the enclosure");
        DreamService.end(level);clear(level,o,24);
        level.getServer().getPlayerList().remove(p);level.getServer().getPlayerList().remove(q);
        h.succeed();
    }
    @GameTest(template="empty",batch="70")
    public static void stoppingTheDreamSendsItsVisitorsHome(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,22,8));
        level.setBlock(origin,Blocks.DIAMOND_BLOCK.defaultBlockState(),3);
        DreamService.begin(level,origin,780);
        var fall=Veilborne.DREAM_SEED_ENTITY.get().create(level);fall.place(DreamSeed.Kind.FALL,Vec3.atCenterOf(origin.above(4)),Vec3.atCenterOf(origin.above(4)));level.addFreshEntity(fall);
        var wander=Veilborne.DREAM_SEED_ENTITY.get().create(level);wander.place(DreamSeed.Kind.WANDER,Vec3.atCenterOf(origin.above(3)),Vec3.atCenterOf(origin.above(3)));level.addFreshEntity(wander);
        var school=Veilborne.LEAF_SCHOOL.get().create(level);school.place(Vec3.atCenterOf(origin.above(2)),Direction.NORTH);level.addFreshEntity(school);
        DreamService.end(level);
        h.assertTrue(level.getBlockState(origin).is(Blocks.DIAMOND_BLOCK),"The verdant dream damaged a building");
        h.assertTrue(fall.state()==DreamSeed.State.FADE&&wander.state()==DreamSeed.State.FADE&&school.state()==LeafSchool.State.LEAVE&&!DreamData.get(level).active,"Stop left a seed drifting, a school stuck or an active weather behind");
        h.assertTrue(TideData.get(level).quietTicks>=24000,"No quiet period after the verdant dream");
        fall.discard();wander.discard();school.discard();
        h.succeed();
    }
    @GameTest(template="empty",batch="71")
    public static void theCharmLetsYouDownLikeALeaf(GameTestHelper h){
        var level=h.getLevel();BlockPos o=plot(h,4);
        var p=h.makeMockServerPlayerInLevel();p.moveTo(o.getX()+.5,o.getY()+12,o.getZ()+.5,0,0);
        p.getInventory().add(new ItemStack(Veilborne.DRIFTING_LEAF.get()));
        p.setOnGround(false);p.fallDistance=1;p.setDeltaMovement(0,-.6,0);
        DreamService.glide(new PlayerTickEvent.Post(p));
        h.assertTrue(!p.hasEffect(MobEffects.SLOW_FALLING),"The charm caught a step, not a fall");
        p.fallDistance=4;
        DreamService.glide(new PlayerTickEvent.Post(p));
        h.assertTrue(p.hasEffect(MobEffects.SLOW_FALLING)&&p.fallDistance==0,"The charm did not catch the fall");
        var q=h.makeMockServerPlayerInLevel();q.moveTo(o.getX()+2.5,o.getY()+12,o.getZ()+.5,0,0);
        q.setOnGround(false);q.fallDistance=4;q.setDeltaMovement(0,-.6,0);
        DreamService.glide(new PlayerTickEvent.Post(q));
        h.assertTrue(!q.hasEffect(MobEffects.SLOW_FALLING),"A fall was caught without a charm");
        h.assertTrue(new ItemStack(Veilborne.DRIFTING_LEAF.get()).getMaxDamage()==DreamService.CHARM_USES,"The charm does not wear");
        level.getServer().getPlayerList().remove(p);level.getServer().getPlayerList().remove(q);
        h.succeed();
    }
    @GameTest(template="empty",batch="72")
    public static void dreamBlocksGrowAndStay(GameTestHelper h){
        var level=h.getLevel();BlockPos o=plot(h,12);
        // A seed planted on the soil becomes a sapling; on stone it does not.
        var p=h.makeMockServerPlayerInLevel();p.moveTo(o.getX()+.5,o.getY(),o.getZ()+3.5,180,0);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Veilborne.DREAM_SEED.get()));
        BlockPos a=o.west(5);
        Veilborne.DREAM_SEED.get().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(a.below()).add(0,.5,0),Direction.UP,a.below(),false)));
        h.assertTrue(level.getBlockState(a).is(Veilborne.DREAM_SAPLING.get())&&!level.getBlockState(a).getValue(DreamBlocks.FULL),"Planting a dream seed did not leave a sapling");
        level.setBlock(o.offset(5,-1,-5),Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(!Veilborne.DREAM_SAPLING.get().defaultBlockState().canSurvive(level,o.offset(5,0,-5)),"A sapling stands on stone");
        // It grows into a tree without overwriting anything, and the tree never spreads.
        level.setBlock(a.offset(2,4,0),Blocks.DIAMOND_BLOCK.defaultBlockState(),3);
        for(int i=0;i<8&&level.getBlockState(a).is(Veilborne.DREAM_SAPLING.get());i++){
            BlockState s=level.getBlockState(a);
            ((DreamBlocks.Sapling)s.getBlock()).performBonemeal(level,level.random,a,s);
        }
        h.assertTrue(level.getBlockState(a).is(Veilborne.DREAM_HEART.get())&&level.getBlockState(a.above()).is(Veilborne.DREAM_LOG.get()),"The sapling did not grow into a tree with a heart at its foot");
        h.assertTrue(level.getBlockState(a.offset(2,4,0)).is(Blocks.DIAMOND_BLOCK),"The tree overwrote a block");
        int logs=0,leaves=0;
        for(BlockPos b:BlockPos.betweenClosed(a.offset(-5,0,-5),a.offset(5,12,5))){
            BlockState s=level.getBlockState(b);
            if(s.is(Veilborne.DREAM_LOG.get())||s.is(Veilborne.DREAM_HEART.get()))logs++;
            if(s.is(Veilborne.DREAM_LEAVES.get())){leaves++;h.assertTrue(!s.isRandomlyTicking()&&s.getLightEmission(level,b)>=5,"Dream leaves decay or give no light");}
        }
        h.assertTrue(logs>=5&&logs<=9&&leaves>=12,"The tree is the wrong shape: "+logs+" logs, "+leaves+" leaves");
        // A full seed grows a taller one.
        BlockPos f=o.east(5);
        level.setBlock(f,Veilborne.DREAM_SAPLING.get().defaultBlockState().setValue(DreamBlocks.FULL,true),3);
        for(int i=0;i<8&&level.getBlockState(f).is(Veilborne.DREAM_SAPLING.get());i++){
            BlockState s=level.getBlockState(f);
            ((DreamBlocks.Sapling)s.getBlock()).performBonemeal(level,level.random,f,s);
        }
        int tall=0;
        for(BlockPos b:BlockPos.betweenClosed(f.offset(-2,0,-2),f.offset(2,12,2)))if(level.getBlockState(b).is(Veilborne.DREAM_LOG.get()))tall=Math.max(tall,b.getY()-f.getY()+1);
        h.assertTrue(tall>=8,"A full seed's tree is not taller: "+tall);
        // The flower lamp lights and lets its school in and out; the antler twig hangs only on a wall.
        BlockPos l=o.offset(0,0,-6);
        level.setBlock(l,Veilborne.FLOWER_LAMP.get().defaultBlockState(),3);
        h.assertTrue(level.getBlockState(l).getLightEmission(level,l)==13,"The flower lamp gives no light");
        boolean before=level.getBlockState(l).getValue(DreamBlocks.SWARM);
        level.getBlockState(l).useWithoutItem(level,p,new BlockHitResult(Vec3.atCenterOf(l),Direction.UP,l,false));
        h.assertTrue(level.getBlockState(l).getValue(DreamBlocks.SWARM)!=before,"Using the flower lamp did not call its school");
        BlockPos t=o.offset(-8,1,-8);
        level.setBlock(t.south(),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(t,Veilborne.ANTLER_TWIG.get().defaultBlockState().setValue(DreamBlocks.Twig.FACING,Direction.NORTH),3);
        h.assertTrue(level.getBlockState(t).getLightEmission(level,t)==7,"The antler twig gives no light");
        level.setBlock(t.south(),Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(!level.getBlockState(t).is(Veilborne.ANTLER_TWIG.get()),"The antler twig hangs on nothing");
        level.getServer().getPlayerList().remove(p);
        h.succeed();
    }
    private DreamTests(){}
}
