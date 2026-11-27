package com.suiyi.veilborne.test;

import com.suiyi.veilborne.*;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

/** 月骸花潮: persistence, sync, choreography, the one-weather rule, the procession's manners and the true moon. */
@GameTestHolder(Veilborne.ID)
@PrefixGameTestTemplate(false)
public final class MoonTests {
    @GameTest(template="empty",batch="13")
    public static void bloomPersistsAcrossReload(GameTestHelper h){
        var d=new MoonData();d.begin(new BlockPos(-40,80,12),5501,14400,320);
        d.age=9300;d.flags=MoonData.PROCESSION|MoonData.LITTLE_MOON|MoonData.BROKEN;d.brokenAt=9210;
        d.littleMoon=new BlockPos(-52,78,20);d.littleMoonAt=5100;d.lantern=new BlockPos(-30,79,8);d.lanternAt=9290;
        var saved=d.save(new CompoundTag(),h.getLevel().registryAccess());
        var loaded=MoonData.load(saved,h.getLevel().registryAccess());
        h.assertTrue(saved.equals(loaded.save(new CompoundTag(),h.getLevel().registryAccess())),"Bloom state changed after reload");
        h.assertTrue(loaded.broken()&&!loaded.takeFlag(MoonData.PROCESSION),"A spent encounter or the true-moon result was lost");
        d.age=d.duration;
        h.assertTrue(!MoonData.load(d.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess()).active,"A finished bloom resumed after restart");
        h.succeed();
    }
    @GameTest(template="empty",batch="14")
    public static void moonPacketRoundTrip(GameTestHelper h){
        var packet=new MoonPacket(true,-771234567L,8123,14400,320,new BlockPos(-180,90,23),new BlockPos(-163,75,42),6200,11,8050,new BlockPos(-178,82,-24),8120,512|32);
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try{MoonPacket.CODEC.encode(b,packet);h.assertTrue(packet.equals(MoonPacket.CODEC.decode(b)),"Moon snapshot lost authoritative state");}finally{b.release();}
        h.succeed();
    }
    @GameTest(template="empty",batch="15")
    public static void bloomHasOmenClimaxAndClosing(GameTestHelper h){
        h.assertTrue(MoonTimeline.phase(0)==MoonTimeline.Phase.HALO&&MoonTimeline.halo(.02)>.3,"No halo omen");
        h.assertTrue(MoonTimeline.phase(.66)==MoonTimeline.Phase.MOLT,"The molt is not the climax");
        h.assertTrue(MoonTimeline.veil(0)==0&&MoonTimeline.veil(1)==0&&MoonTimeline.moon(1)==0,"Hard cut at a weather boundary");
        h.assertTrue(MoonTimeline.shed(.7)>.8&&MoonTimeline.shed(.99)<.01,"The shell never comes away, or never closes again");
        h.assertTrue(MoonTimeline.three(.7)>.99&&MoonTimeline.three(.8)==0&&MoonTimeline.three(.5)==0,"Three moons outside the molt");
        h.assertTrue(MoonTimeline.petals(.45)>.7&&MoonTimeline.petals(.99)==0,"Petals missing or never stop");
        h.assertTrue(MoonTimeline.closing(.8)==0&&MoonTimeline.closing(.99)>.99,"Flowers do not close at the end");
        h.succeed();
    }
    @GameTest(template="empty",batch="16")
    public static void onlyOneVeilWeatherAtATime(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,22,8));
        TideService.begin(level,origin,840);
        MoonService.begin(level,origin,720);
        h.assertTrue(MoonData.get(level).active&&!TideData.get(level).active,"Bloom started on top of the sea");
        TideService.begin(level,origin,840);
        h.assertTrue(TideData.get(level).active&&!MoonData.get(level).active,"Sea started on top of the bloom");
        TideService.end(level);
        h.assertTrue(TideData.get(level).quietTicks>=24000,"No quiet period after a weather");
        h.succeed();
    }
    @GameTest(template="empty",batch="17")
    public static void trueMoonIsTheOnlyAnswer(GameTestHelper h){
        for(long seed:new long[]{1,77,-4096,902211,Long.MAX_VALUE/3}){
            int truth=MoonTimeline.trueSlot(seed);
            for(int slot=0;slot<3;slot++){
                Vec3 look=MoonTimeline.moonDir(seed,slot,1,0);
                int aimed=MoonTimeline.aimedSlot(seed,look,1,Math.toRadians(10));
                h.assertTrue(aimed==slot,"Aiming at moon "+slot+" picked "+aimed);
            }
            h.assertTrue(MoonTimeline.aimedSlot(seed,new Vec3(0,-1,0),1,Math.toRadians(10))==-1,"Looking at the ground counted as a moon");
            // The three moons must be far enough apart that one cannot be mistaken for another.
            for(int a=0;a<3;a++)for(int b=a+1;b<3;b++)
                h.assertTrue(MoonTimeline.moonDir(seed,a,1,0).dot(MoonTimeline.moonDir(seed,b,1,0))<Math.cos(Math.toRadians(22)),"Moons overlap");
            h.assertTrue(MoonTimeline.moonDir(seed,truth,1,1).distanceTo(MoonTimeline.base(seed))<1e-6,"The true moon does not return to its place");
        }
        h.succeed();
    }
    @GameTest(template="empty",batch="18")
    public static void moonDewIsLimitedPerPlayer(GameTestHelper h){
        var tag=new CompoundTag();
        for(int i=0;i<MoonService.DEW_PER_PLAYER;i++)h.assertTrue(MoonService.dewAllowed(tag,42),"Dew refused early");
        h.assertTrue(!MoonService.dewAllowed(tag,42),"Unlimited dew from one little moon");
        h.assertTrue(MoonService.dewAllowed(tag,43),"A new bloom did not refill the little moon");
        h.succeed();
    }
    @GameTest(template="empty",batch="19")
    public static void processionScattersInsteadOfColliding(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(2,22,8));
        for(int x=-4;x<=60;x++)for(int z=-5;z<=5;z++)level.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
        MoonData.get(level).begin(origin,3,14400,320);
        var proc=Veilborne.PROCESSION.get().create(level);
        proc.setRoute(origin,0,50,5);proc.preview(20,MoonProcession.State.WALK);
        var pig=EntityType.PIG.create(level);
        Vec3 far=proc.member(20,0,0).add(0,0,9);pig.setPos(far);
        h.assertTrue(!proc.intruded(List.of(pig)),"A watcher at a respectful distance scattered the line");
        Vec3 litter=proc.member(20,MoonProcession.LITTER,0);pig.setPos(litter);
        h.assertTrue(proc.intruded(List.of(pig)),"Walking into the line did not scatter it");
        MoonData.get(level).active=false;h.succeed();
    }
    @GameTest(template="empty",batch="20")
    public static void processionStopsOutsideAHouse(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(2,22,8));
        for(int x=-4;x<=40;x++)for(int z=-6;z<=6;z++)level.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
        // A closed hut across the whole road.
        for(int x=14;x<=22;x++)for(int z=-6;z<=6;z++)for(int y=0;y<=4;y++)level.setBlock(origin.offset(x,y,z),Blocks.OAK_PLANKS.defaultBlockState(),3);
        MoonData.get(level).begin(origin,3,14400,320);
        var proc=Veilborne.PROCESSION.get().create(level);
        proc.setRoute(origin,0,40,5);proc.preview(6,MoonProcession.State.WALK);
        double wall=origin.getX()+13.5,highest=0;boolean waited=false;
        for(int i=0;i<600;i++){
            proc.tick();
            if(proc.state()==MoonProcession.State.WAIT)waited=true;
            if(proc.visibilityServer())highest=Math.max(highest,proc.getY()-origin.getY());
            if(proc.visibilityServer()&&proc.getX()>wall&&proc.getX()<wall+9.5)h.fail("A bearer stood inside the hut");
        }
        h.assertTrue(waited,"The procession never paused at the wall");
        h.assertTrue(highest<2.5,"The procession climbed onto the roof: "+highest);
        MoonData.get(level).active=false;proc.finish();h.succeed();
    }
    @GameTest(template="empty",batch="21")
    public static void silverLanternLightsAndStands(GameTestHelper h){
        BlockPos floor=h.absolutePos(new BlockPos(3,2,3));
        h.getLevel().setBlock(floor,Blocks.STONE.defaultBlockState(),3);
        BlockPos p=floor.above();
        h.getLevel().setBlock(p,Veilborne.SILVER_LANTERN.get().defaultBlockState(),3);
        h.assertTrue(h.getLevel().getBlockState(p).getLightEmission(h.getLevel(),p)==11,"The silver lantern gives no light");
        h.getLevel().setBlock(floor,Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(!h.getLevel().getBlockState(p).is(Veilborne.SILVER_LANTERN.get()),"The lantern floats after its ground is removed");
        h.succeed();
    }
    @GameTest(template="empty",batch="22")
    public static void stoppingTheBloomSendsVisitorsHome(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,22,8));
        level.setBlock(origin,Blocks.DIAMOND_BLOCK.defaultBlockState(),3);
        MoonService.begin(level,origin,720);
        var proc=Veilborne.PROCESSION.get().create(level);proc.setRoute(origin.above(),0,30,1);level.addFreshEntity(proc);
        MoonService.end(level);
        h.assertTrue(level.getBlockState(origin).is(Blocks.DIAMOND_BLOCK),"The bloom damaged a building");
        h.assertTrue(proc.isRemoved()&&!MoonData.get(level).active,"Stop left a visitor or an active bloom behind");
        h.succeed();
    }
}
