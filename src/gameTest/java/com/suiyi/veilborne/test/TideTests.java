package com.suiyi.veilborne.test;

import com.suiyi.veilborne.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import io.netty.buffer.Unpooled;

@GameTestHolder(Veilborne.ID)
@PrefixGameTestTemplate(false)
public final class TideTests {
    @GameTest(template="empty",batch="01")
    public static void eventPersistsAcrossReload(GameTestHelper h){
        var d=new TideData();d.begin(new BlockPos(-140,92,317),87951,16800,320);
        d.age=11012;d.flags=23;d.quietTicks=891;d.vortex=new BlockPos(-122,82,317);d.landing=new BlockPos(-131,86,300);
        var saved=d.save(new CompoundTag(),h.getLevel().registryAccess());
        var loaded=TideData.load(saved,h.getLevel().registryAccess());
        h.assertTrue(saved.equals(loaded.save(new CompoundTag(),h.getLevel().registryAccess())),"Choreography or encounters changed after reload");
        h.assertTrue(!loaded.takeFlag(1),"Consumed encounter can replay on reload");h.succeed();
    }
    @GameTest(template="empty",batch="02")
    public static void wireRoundTripPreservesWorldPositions(GameTestHelper h){
        var packet=new TidePacket(true,123456789,7801,16800,320,new BlockPos(-180,90,23),new BlockPos(-163,75,42),new BlockPos(-178,82,-24),7210,7690,19);
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try{TidePacket.CODEC.encode(b,packet);h.assertTrue(packet.equals(TidePacket.CODEC.decode(b)),"Network snapshot lost authoritative state");}finally{b.release();}
        h.succeed();
    }
    @GameTest(template="empty",batch="03")
    public static void timelineHasFullRiseClimaxAndRetreat(GameTestHelper h){
        h.assertTrue(TideTimeline.phase(0)==TideTimeline.Phase.OMEN,"Missing omen");
        h.assertTrue(TideTimeline.phase(.63)==TideTimeline.Phase.WHALE,"Whale must cross during the climax");
        h.assertTrue(TideTimeline.whale(.62)>.99&&TideTimeline.whale(.9)==0,"Whale never arrives or fails to depart");
        h.assertTrue(TideTimeline.sea(0)==0&&TideTimeline.sea(1)==0,"Hard cut at weather boundary");
        h.assertTrue(TideTimeline.sea(.4)>.99,"Full sea never develops");
        h.assertTrue(TideTimeline.region(230,320)>.99&&TideTimeline.region(320,320)==0,"Region boundary is discontinuous");
        double previous=1;
        for(int i=0;i<=100;i++){
            double value=TideTimeline.region(230+i,320);
            h.assertTrue(value<=previous&&value>=0,"Boundary intensifies while leaving the region");previous=value;
        }h.succeed();
    }
    @GameTest(template="empty",batch="04")
    public static void encounterRejectsUnloadedAndUnsafeTerrain(GameTestHelper h){
        var level=h.getLevel();BlockPos distant=new BlockPos(24000000,80,24000000);
        h.assertTrue(!level.hasChunkAt(distant),"Test precondition: distant chunk loaded");
        h.assertTrue(TideService.findSafe(level,distant,8)==null,"Encounter selected unloaded terrain");
        h.assertTrue(!level.hasChunkAt(distant),"Encounter force-loaded distant terrain");
        // GameTest encloses the template with barriers; build the open-sky fixture above that roof.
        var origin=h.absolutePos(new BlockPos(8,22,8));
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)level.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
        var found=TideService.findSafe(level,origin,2);
        h.assertTrue(found!=null&&level.getBlockState(found.below()).is(Blocks.STONE),"No safe stone landing selected: "+found+" / height="+level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,origin.getX(),origin.getZ()));h.succeed();
    }
    @GameTest(template="empty",batch="05",timeoutTicks=80)
    public static void skiffDepartureReleasesPassenger(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,22,8));
        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)level.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
        var skiff=Veilborne.SKIFF.get().create(level);skiff.setOrigin(origin);level.addFreshEntity(skiff);
        var pig=EntityType.PIG.create(level);pig.setPos(origin.getBottomCenter());level.addFreshEntity(pig);
        h.assertTrue(!pig.isPassenger(),"Skiff mounted an entity without interaction");
        h.assertTrue(pig.startRiding(skiff),"Voluntary boarding failed");
        skiff.finish();
        h.assertTrue(!pig.isPassenger()&&skiff.isRemoved(),"Passenger stranded after departure");
        h.assertTrue(level.getBlockState(pig.blockPosition().below()).is(Blocks.STONE),"Passenger returned to unsafe ground");
        pig.discard();h.succeed();
    }
    @GameTest(template="empty",batch="06")
    public static void bottledSeaHasLightAndEntity(GameTestHelper h){
        BlockPos p=h.absolutePos(new BlockPos(3,3,3));
        h.getLevel().setBlock(p,Veilborne.TIDE_LANTERN.get().defaultBlockState(),3);
        h.assertTrue(h.getLevel().getBlockEntity(p) instanceof TideLanternEntity,"Bottled sea has no animation ticker");
        h.assertTrue(h.getLevel().getBlockState(p).getLightEmission(h.getLevel(),p)==12,"Bottled sea fails to illuminate the home");h.succeed();
    }
    @GameTest(template="empty",batch="07")
    public static void finishedSnapshotCannotResurrectWeather(GameTestHelper h){
        var d=new TideData();d.begin(BlockPos.ZERO,1,16800,320);d.age=d.duration;
        var restored=TideData.load(d.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(!restored.active&&restored.strength(0,0)==0,"Completed event resumed after restart");h.succeed();
    }
    @GameTest(template="empty",batch="08")
    public static void skiffCannotSailIntoAHouse(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,22,8));
        var data=TideData.get(level);data.begin(origin,27,16800,320);
        for(int x=-2;x<=15;x++)for(int z=-2;z<=15;z++){
            level.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
            level.setBlock(origin.offset(x,5,z),Blocks.OAK_PLANKS.defaultBlockState(),3);
        }
        var skiff=Veilborne.SKIFF.get().create(level);skiff.setOrigin(origin);var before=skiff.position();
        for(int i=0;i<400;i++)skiff.tick();
        h.assertTrue(skiff.position().distanceToSqr(before)<.001,"Skiff entered a roofed building");
        skiff.finish();data.active=false;h.succeed();
    }
    /** Held under a roof while its loop runs on, then let go: it sails after its loop, it does not jump to it. */
    @GameTest(template="empty",batch="08_catchup")
    public static void skiffCatchesUpInsteadOfLeaping(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,22,8));
        var data=TideData.get(level);data.begin(origin,27,16800,320);
        for(int x=-2;x<=15;x++)for(int z=-2;z<=15;z++){
            level.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
            level.setBlock(origin.offset(x,5,z),Blocks.OAK_PLANKS.defaultBlockState(),3);
        }
        var skiff=Veilborne.SKIFF.get().create(level);skiff.setOrigin(origin);
        for(int i=0;i<200;i++)skiff.tick();
        var held=skiff.position();
        for(int x=-2;x<=15;x++)for(int z=-2;z<=15;z++)level.setBlock(origin.offset(x,5,z),Blocks.AIR.defaultBlockState(),3);
        double longest=0;var last=held;
        for(int i=0;i<40;i++){skiff.tick();longest=Math.max(longest,skiff.position().distanceTo(last));last=skiff.position();}
        h.assertTrue(last.distanceTo(held)>2,"The skiff stayed put once the roof was gone");
        h.assertTrue(longest<.31,"The skiff leapt "+longest+" blocks in one tick to catch up with its loop");
        skiff.finish();data.active=false;h.succeed();
    }
    @GameTest(template="empty",batch="09")
    public static void stoppingWeatherPreservesBuiltBlocks(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,22,8));
        level.setBlock(origin,Blocks.DIAMOND_BLOCK.defaultBlockState(),3);
        TideData.get(level).begin(origin,18,16800,320);
        var skiff=Veilborne.SKIFF.get().create(level);skiff.setOrigin(origin.above());level.addFreshEntity(skiff);
        TideService.end(level);
        h.assertTrue(level.getBlockState(origin).is(Blocks.DIAMOND_BLOCK),"Weather damaged the player's building");
        h.assertTrue(skiff.isRemoved()&&!TideData.get(level).active,"Stop left a visitor or active weather behind");h.succeed();
    }
    @GameTest(template="empty",batch="11",timeoutTicks=120)
    public static void skiffDepartureReleasesThenFades(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,22,8));
        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)level.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
        var skiff=Veilborne.SKIFF.get().create(level);skiff.setOrigin(origin);level.addFreshEntity(skiff);
        var pig=EntityType.PIG.create(level);pig.setPos(origin.getBottomCenter());level.addFreshEntity(pig);
        h.assertTrue(pig.startRiding(skiff),"Voluntary boarding failed");
        skiff.depart();
        h.assertTrue(!pig.isPassenger(),"Passenger kept aboard a departing skiff");
        h.assertTrue(skiff.isFading()&&!skiff.isRemoved(),"Skiff vanished instead of thinning into mist");
        h.assertTrue(!pig.startRiding(skiff),"A fading skiff accepted a new passenger");
        h.runAfterDelay(TideSkiff.FADE_TICKS+5,()->{
            h.assertTrue(skiff.isRemoved(),"Faded skiff was never removed");
            pig.discard();h.succeed();
        });
    }
    @GameTest(template="empty",batch="12")
    public static void skiffSeatTurnsWithHull(GameTestHelper h){
        var level=h.getLevel();var skiff=Veilborne.SKIFF.get().create(level);
        skiff.setOrigin(h.absolutePos(new BlockPos(8,22,8)));
        var pig=EntityType.PIG.create(level);
        skiff.setYRot(0);var ahead=skiff.getPassengerRidingPosition(pig).subtract(skiff.position());
        skiff.setYRot(90);var turned=skiff.getPassengerRidingPosition(pig).subtract(skiff.position());
        h.assertTrue(ahead.z>.2&&Math.abs(ahead.x)<1e-6,"Seat is not on the fore thwart: "+ahead);
        h.assertTrue(turned.x<-.2&&Math.abs(turned.z)<1e-6,"Seat stayed put while the hull turned: "+turned);
        h.succeed();
    }
    @GameTest(template="empty",batch="10")
    public static void vortexMatchesItsVisibleCircle(GameTestHelper h){
        BlockPos center=new BlockPos(30,80,-70);var p=center.getBottomCenter();
        h.assertTrue(TideService.vortexContains(center,p.add(0,0,0)),"Center of tide must lift");
        h.assertTrue(TideService.vortexContains(center,p.add(2.4,4,2.4)),"Interior of circle must lift");
        h.assertTrue(!TideService.vortexContains(center,p.add(3.6,0,0)),"Players outside the visible ring were lifted");
        h.assertTrue(!TideService.vortexContains(center,p.add(3,0,3)),"Square hitbox corners extend beyond the warning circle");
        h.assertTrue(!TideService.vortexContains(center,p.add(0,-1,0)),"Tide reached underneath the surface");h.succeed();
    }
}
