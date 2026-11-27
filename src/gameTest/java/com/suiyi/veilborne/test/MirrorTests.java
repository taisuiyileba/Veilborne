package com.suiyi.veilborne.test;

import com.suiyi.veilborne.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

/** 镜天巡礼: persistence, sync, choreography, the one-weather rule, the pilgrim's walk, the door, the prism, the gallery's ways back, the mirrors and the beasts' warning. */
@GameTestHolder(Veilborne.ID)
@PrefixGameTestTemplate(false)
public final class MirrorTests {
    @GameTest(template="empty",batch="23")
    public static void mirrorPersistsAcrossReload(GameTestHelper h){
        var d=new MirrorData();d.begin(new BlockPos(30,70,-12),9911,15600,320,MirrorData.VARIANT_FRAMES);
        d.age=8800;d.flags=MirrorData.DOOR|MirrorData.REVEALED|MirrorData.PILGRIM|MirrorData.SHARDS|MirrorData.SHARD_BURST_0;
        d.door=new BlockPos(42,71,-3);d.doorFace=2;d.doorAt=4700;d.revealedAt=5100;d.pilgrimOrigin=new BlockPos(33,70,-10);d.pilgrimYaw=1.25f;d.pilgrimAt=8400;
        d.shards=new BlockPos[]{new BlockPos(20,70,4),new BlockPos(18,69,-20)};d.shardWarn=new int[]{7000,0};d.keepsake=new BlockPos(31,70,-9);d.keepsakeAt=8700;
        var saved=d.save(new CompoundTag(),h.getLevel().registryAccess());
        var loaded=MirrorData.load(saved,h.getLevel().registryAccess());
        h.assertTrue(saved.equals(loaded.save(new CompoundTag(),h.getLevel().registryAccess())),"Mirror state changed after reload");
        h.assertTrue(loaded.revealed()&&!loaded.takeFlag(MirrorData.PILGRIM),"A spent encounter or the revealed door was lost");
        d.age=d.duration;
        h.assertTrue(!MirrorData.load(d.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess()).active,"A finished pilgrimage resumed after restart");
        h.succeed();
    }
    @GameTest(template="empty",batch="24")
    public static void mirrorPacketRoundTrip(GameTestHelper h){
        var packet=new MirrorPacket(true,-4411223344L,7011,15600,320,new BlockPos(-80,66,51),37,3,new BlockPos(-70,67,49),1,4100,4400,
            new BlockPos(-78,66,50),-2.1f,8401,new BlockPos(-60,66,60),BlockPos.ZERO,7000,0,new BlockPos(-79,66,50),14700,2048|8192,true);
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try{MirrorPacket.CODEC.encode(b,packet);h.assertTrue(packet.equals(MirrorPacket.CODEC.decode(b)),"Mirror snapshot lost authoritative state");}finally{b.release();}
        h.succeed();
    }
    @GameTest(template="empty",batch="25")
    public static void mirrorHasOmenClimaxAndClearing(GameTestHelper h){
        h.assertTrue(MirrorTimeline.phase(0)==MirrorTimeline.Phase.OMEN&&MirrorTimeline.omen(.03)>.8&&MirrorTimeline.mirror(.03)==0,"The omen is not quiet water first");
        h.assertTrue(MirrorTimeline.mirror(0)==0&&MirrorTimeline.mirror(1)==0&&MirrorTimeline.reflection(0)==0&&MirrorTimeline.panes(1)==0,"Hard cut at a weather boundary");
        h.assertTrue(MirrorTimeline.silver(.1)<MirrorTimeline.silver(.2)&&MirrorTimeline.silver(.23)>.99,"The silver does not spread down from the zenith");
        h.assertTrue(MirrorTimeline.phase(.62)==MirrorTimeline.Phase.PILGRIM&&MirrorTimeline.pilgrim(.62)>.99,"The pilgrimage is not the climax");
        h.assertTrue(MirrorTimeline.pilgrim(.5)==0&&MirrorTimeline.pilgrim(.75)==0,"The pilgrim walks outside its phase");
        h.assertTrue(MirrorTimeline.crack(.4)<.2&&MirrorTimeline.crack(.9)>.99,"The cracks do not spread in the cracking");
        h.assertTrue(MirrorTimeline.shatter(.8)==0&&MirrorTimeline.shatter(.99)>.99,"The mirror never falls away");
        h.assertTrue(MirrorTimeline.doorOpen(.4)&&!MirrorTimeline.doorOpen(.2)&&!MirrorTimeline.doorOpen(.9),"The door opens outside the reflection");
        h.assertTrue(!MirrorTimeline.galleryOpen(.95),"The gallery stays open while the mirror breaks");
        h.succeed();
    }
    @GameTest(template="empty",batch="26")
    public static void threeWeathersOneAtATime(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,22,8));
        TideService.begin(level,origin,840);
        MoonService.begin(level,origin,720);
        MirrorService.begin(level,origin,780);
        h.assertTrue(MirrorData.get(level).active&&!MoonData.get(level).active&&!TideData.get(level).active,"The mirror started on top of another weather");
        MoonService.begin(level,origin,720);
        h.assertTrue(MoonData.get(level).active&&!MirrorData.get(level).active,"The bloom started on top of the mirror");
        MirrorService.begin(level,origin,780);TideService.begin(level,origin,840);
        h.assertTrue(TideData.get(level).active&&!MirrorData.get(level).active,"The sea started on top of the mirror");
        TideService.end(level);
        h.assertTrue(TideData.get(level).quietTicks>=24000,"No quiet period after a weather");
        h.succeed();
    }
    @GameTest(template="empty",batch="27")
    public static void pilgrimStopsBesideTheWatcher(GameTestHelper h){
        double prev=-1,biggest=0;
        for(int i=0;i<=2000;i++){
            double w=i/2000.0,s=MirrorTimeline.along(w);
            h.assertTrue(s>=prev-1e-9,"The pilgrim walks backwards at "+w);
            if(prev>=0)biggest=Math.max(biggest,s-prev);
            prev=s;
        }
        h.assertTrue(biggest<1.5,"The pilgrim jumps along its path: "+biggest);
        h.assertTrue(Math.abs(MirrorTimeline.along(.5)-MirrorTimeline.PATH/2)<1e-9&&MirrorTimeline.gaze(.5)>.99,"It does not stand and look down at the midpoint");
        h.assertTrue(MirrorTimeline.along(0)==0&&Math.abs(MirrorTimeline.along(1)-MirrorTimeline.PATH)<1e-6,"The walk does not cover its path");
        Vec3 origin=new Vec3(100,64,-40);
        Vec3 stop=MirrorTimeline.pilgrimAt(origin,.7,MirrorTimeline.PATH/2);
        h.assertTrue(Math.abs(stop.distanceTo(origin)-MirrorTimeline.BESIDE)<1e-6,"It does not stop beside the watcher: "+stop.distanceTo(origin));
        h.assertTrue(stop.subtract(origin).dot(MirrorTimeline.heading(.7))<1e-6,"Its stop is not level with the watcher along its path");
        h.assertTrue(MirrorTimeline.pilgrimAt(origin,.7,0).distanceTo(origin)>180,"It starts too close to the watcher");
        h.assertTrue(MirrorTimeline.gaze(.2)==0&&MirrorTimeline.gaze(.8)==0,"It looks down while still walking");
        h.assertTrue(MirrorTimeline.turn(.5)>.99&&MirrorTimeline.turn(.2)==0&&MirrorTimeline.turn(.8)==0,"It does not turn to the watcher only while it stands");
        double a=(1-MirrorTimeline.STOP)/2;
        h.assertTrue(MirrorTimeline.turn(a)>MirrorTimeline.gaze(a),"It looks before it has turned");
        h.succeed();
    }
    @GameTest(template="empty",batch="28")
    public static void doorPrefersABuiltWall(GameTestHelper h){
        var level=h.getLevel();BlockPos o=h.absolutePos(new BlockPos(1,2,1));
        // The test enclosure has a barrier roof; lift it over this plot so the ground is under open sky.
        for(int x=-2;x<=22;x++)for(int z=-2;z<=22;z++){
            level.setBlock(o.offset(x,-1,z),Blocks.GRASS_BLOCK.defaultBlockState(),3);
            for(int y=0;y<6;y++)level.setBlock(o.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
            for(int y=6;y<40;y++)if(level.getBlockState(o.offset(x,y,z)).is(Blocks.BARRIER))level.setBlock(o.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        // A natural stone bank on one side, a stone-brick hut on the other.
        for(int x=14;x<=18;x++)for(int y=0;y<=4;y++)for(int z=0;z<=2;z++)level.setBlock(o.offset(x,y,z),Blocks.STONE.defaultBlockState(),3);
        for(int x=0;x<=4;x++)for(int y=0;y<=3;y++)for(int z=13;z<=17;z++)level.setBlock(o.offset(x,y,z),Blocks.STONE_BRICKS.defaultBlockState(),3);
        var door=MirrorService.chooseDoor(level,o.offset(8,0,8),42);
        BlockPos probe=o.offset(5,0,15);
        h.assertTrue(door!=null,"No wall was found for the extra door; beside the hut: top="+net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES.name()+" "
            +level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,probe.getX(),probe.getZ())+" stand.y="+probe.getY()
            +" loaded="+level.hasChunk(probe.getX()>>4,probe.getZ()>>4)+" wall="+level.getBlockState(probe.west())+" floor="+level.getBlockState(probe.below()));
        h.assertTrue(level.getBlockState(door.wall()).is(Blocks.STONE_BRICKS),"The door went into a natural bank instead of the hut: "+level.getBlockState(door.wall()));
        BlockPos front=door.wall().relative(Direction.from2DDataValue(door.face()));
        h.assertTrue(level.getBlockState(front).isAir()&&level.getBlockState(front.above()).isAir(),"The door opens into a wall");
        for(int x=0;x<=4;x++)for(int y=0;y<=3;y++)for(int z=13;z<=17;z++)level.setBlock(o.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        for(int x=14;x<=18;x++)for(int y=0;y<=4;y++)for(int z=0;z<=2;z++)level.setBlock(o.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(MirrorService.chooseDoor(level,o.offset(8,0,8),42)==null,"A door was set into open air");
        var free=MirrorService.freeDoor(level,o.offset(8,0,8),42);
        h.assertTrue(free!=null&&level.getBlockState(free.wall()).isAir(),"No free-standing doorway on open ground");
        h.succeed();
    }
    @GameTest(template="empty",batch="29")
    public static void prismRevealsOnlyTheDoor(GameTestHelper h){
        var level=h.getLevel();BlockPos o=h.absolutePos(new BlockPos(2,2,2));
        MirrorService.begin(level,o,780);
        var d=MirrorData.get(level);d.age=(int)(d.duration*.45);d.door=o.offset(0,0,6);d.doorFace=Direction.NORTH.get2DDataValue();d.flags|=MirrorData.DOOR;
        var p=h.makeMockServerPlayerInLevel();
        Vec3 eye=Vec3.atBottomCenterOf(o).add(0,0,0);
        Vec3 door=MirrorTimeline.doorCenter(d.door,d.doorFace,false);
        p.moveTo(eye.x,eye.y,eye.z,0,0);
        // Looking the other way reveals nothing, however long.
        p.setYRot(180);p.setXRot(0);
        for(int i=0;i<60;i++)MirrorService.observe(p,i);
        h.assertTrue(!d.revealed(),"The door showed itself to a prism aimed elsewhere");
        Vec3 to=door.subtract(p.getEyePosition());
        p.setYRot((float)Math.toDegrees(Math.atan2(-to.x,to.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(to.y,Math.hypot(to.x,to.z))));
        for(int i=0;i<MirrorService.DOOR_LOOK+2;i++)MirrorService.observe(p,i);
        h.assertTrue(d.revealed()&&(TideService.records(p)&MirrorService.DOOR)!=0,"Holding the prism on the door did not reveal it");
        MirrorService.end(level);level.getServer().getPlayerList().remove(p);
        h.succeed();
    }
    @GameTest(template="empty",batch="30",timeoutTicks=200)
    public static void galleryAlwaysLeadsBack(GameTestHelper h){
        var level=h.getLevel();BlockPos o=h.absolutePos(new BlockPos(2,2,2));
        MirrorService.begin(level,o,780);
        if(MirrorGallery.level(level.getServer())==null){
            // A world without the gallery (this test server does not load datapack dimensions): the door stays shut.
            var d0=MirrorData.get(level);d0.age=(int)(d0.duration*.45);d0.door=o.offset(0,0,4);d0.doorFace=Direction.NORTH.get2DDataValue();d0.flags|=MirrorData.DOOR|MirrorData.REVEALED;
            var p0=h.makeMockServerPlayerInLevel();
            h.assertTrue(!MirrorGallery.enter(p0,d0)&&p0.level().dimension().equals(Level.OVERWORLD)&&!p0.getPersistentData().contains(MirrorGallery.TAG),"Without the gallery the door still tried to open");
            MirrorGallery.leave(p0);
            h.assertTrue(p0.level().dimension().equals(Level.OVERWORLD),"Leaving with no gallery went somewhere");
            MirrorService.end(level);level.getServer().getPlayerList().remove(p0);
            h.succeed();return;
        }
        var d=MirrorData.get(level);d.age=(int)(d.duration*.45);d.door=o.offset(0,0,4);d.doorFace=Direction.NORTH.get2DDataValue();d.flags|=MirrorData.DOOR|MirrorData.REVEALED;
        var p=h.makeMockServerPlayerInLevel();
        p.moveTo(o.getX()+.5,o.getY(),o.getZ()+.5,0,0);
        h.assertTrue(MirrorGallery.enter(p,d),"The revealed door did not open");
        h.assertTrue(MirrorGallery.inGallery(p.level()),"Walking through the door did not lead into the gallery");
        float health=p.getHealth();
        p.hurt(p.damageSources().generic(),4);
        h.assertTrue(p.getHealth()>=health,"Something hurt a player inside the gallery");
        // The weather ending walks everyone back out, to the doorstep.
        MirrorService.end(level);
        h.assertTrue(p.level().dimension().equals(Level.OVERWORLD),"Ending the weather left someone in the gallery");
        h.assertTrue(p.position().distanceTo(Vec3.atBottomCenterOf(d.door))<2.5,"The way back did not lead to the door: "+p.position());
        h.assertTrue(!p.getPersistentData().contains(MirrorGallery.TAG),"A stale return record was left behind");
        // With no record at all, the way out is the world spawn rather than nowhere.
        MirrorService.begin(level,o,780);d=MirrorData.get(level);d.age=(int)(d.duration*.45);d.door=o.offset(0,0,4);d.doorFace=Direction.NORTH.get2DDataValue();d.flags|=MirrorData.DOOR|MirrorData.REVEALED;
        MirrorGallery.enter(p,d);
        p.getPersistentData().remove(MirrorGallery.TAG);
        MirrorGallery.leave(p);
        h.assertTrue(p.level().dimension().equals(Level.OVERWORLD),"A player without a return record stayed in the gallery");
        MirrorService.end(level);level.getServer().getPlayerList().remove(p);
        h.succeed();
    }
    @GameTest(template="empty",batch="31")
    public static void onlyThePresentMirrorIsRight(GameTestHelper h){
        var level=h.getLevel();BlockPos o=h.absolutePos(new BlockPos(4,2,4));
        MirrorService.begin(level,o,780);MirrorData.get(level).age=(int)(MirrorData.get(level).duration*.45);
        for(long seed:new long[]{1,5,99,-7,123456789}){
            var f=Veilborne.FRAMES.get().create(level);f.setup(0,seed);
            int present=0,late=0,elsewhere=0;
            for(int i=0;i<3;i++)switch(f.kind(i)){case MirrorFrames.PRESENT->present++;case MirrorFrames.LATE->late++;default->elsewhere++;}
            h.assertTrue(present==1&&late==1&&elsewhere==1,"The three mirrors are not one of each");
        }
        var f=Veilborne.FRAMES.get().create(level);f.setPos(o.getX()+.5,o.getY(),o.getZ()+.5);f.setup(0,77);level.addFreshEntity(f);
        var p=h.makeMockServerPlayerInLevel();
        int wrong=(f.present()+1)%3;
        float health=p.getHealth();
        f.observe(wrong,p);
        h.assertTrue(f.state()==MirrorFrames.State.WAITING&&p.getHealth()>=health,"A wrong choice was punished or ended the puzzle");
        f.observe(f.present(),p);
        h.assertTrue(f.state()==MirrorFrames.State.SOLVED&&(TideService.records(p)&MirrorService.FRAMES)!=0,"The present mirror was not recognised");
        for(int i=0;i<3;i++)h.assertTrue(f.normal(i).dot(f.position().subtract(f.framePos(i)).normalize())>.99,"A mirror does not face the spot between them");
        MirrorService.end(level);level.getServer().getPlayerList().remove(p);
        h.succeed();
    }
    @GameTest(template="empty",batch="32")
    public static void beastsWarnBeforeHarm(GameTestHelper h){
        // Nobody near: nothing. Someone comes close: a warning, and nothing else for the whole warning.
        h.assertTrue(MirrorService.shardStep(0,false,100)==MirrorService.Shard.NONE,"A shard woke with nobody near");
        h.assertTrue(MirrorService.shardStep(0,true,100)==MirrorService.Shard.WARN,"A shard gave no warning");
        for(int t=1;t<MirrorService.SHARD_WARN;t++)h.assertTrue(MirrorService.shardStep(100,true,100+t)==MirrorService.Shard.NONE,"A shard burst during its warning");
        // Stepping back during the warning calms it; staying bursts it.
        h.assertTrue(MirrorService.shardStep(100,false,100+MirrorService.SHARD_WARN)==MirrorService.Shard.CALM,"Stepping back did not calm the shard");
        h.assertTrue(MirrorService.shardStep(100,true,100+MirrorService.SHARD_WARN)==MirrorService.Shard.BURST,"Staying did not wake the beasts");
        var level=h.getLevel();BlockPos o=h.absolutePos(new BlockPos(3,2,3));
        MirrorService.begin(level,o,780);var d=MirrorData.get(level);
        d.shards[0]=o;d.flags|=MirrorData.SHARDS;
        MirrorService.burst(level,d,0);
        long beasts=level.getEntities(Veilborne.BEAST.get(),b->b.distanceToSqr(Vec3.atCenterOf(o))<16).size();
        h.assertTrue(d.has(MirrorData.SHARD_BURST_0)&&beasts>0||level.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL,"The burst shard released no beasts");
        MirrorService.end(level);
        h.assertTrue(level.getEntities(Veilborne.BEAST.get(),b->true).isEmpty(),"Glass beasts outlived the mirror");
        h.succeed();
    }
    @GameTest(template="empty",batch="33")
    public static void stoppingTheMirrorSendsVisitorsHome(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,22,8));
        level.setBlock(origin,Blocks.DIAMOND_BLOCK.defaultBlockState(),3);
        MirrorService.begin(level,origin,780);
        var f=Veilborne.FRAMES.get().create(level);f.setPos(Vec3.atCenterOf(origin.above()));f.setup(0,3);boolean fa=level.addFreshEntity(f);
        var t=Veilborne.TRAVELER.get().create(level);t.setPos(Vec3.atCenterOf(origin.above()));boolean ta=level.addFreshEntity(t);
        MirrorService.end(level);
        h.assertTrue(level.getBlockState(origin).is(Blocks.DIAMOND_BLOCK),"The mirror damaged a building");
        h.assertTrue(f.isRemoved()&&t.isRemoved()&&!MirrorData.get(level).active,"Stop left a visitor or an active mirror behind: frames added="+fa+" removed="+f.isRemoved()
            +", traveller added="+ta+" removed="+t.isRemoved()+", active="+MirrorData.get(level).active);
        h.assertTrue(TideData.get(level).quietTicks>=24000,"No quiet period after the mirror");
        h.succeed();
    }
    @GameTest(template="empty",batch="34")
    public static void skyShardHangsOnAWall(GameTestHelper h){
        BlockPos wall=h.absolutePos(new BlockPos(3,2,3));
        h.getLevel().setBlock(wall,Blocks.STONE.defaultBlockState(),3);
        BlockPos p=wall.north();
        h.getLevel().setBlock(p,Veilborne.SKY_SHARD.get().defaultBlockState().setValue(SkyShardBlock.FACING,Direction.NORTH),3);
        h.assertTrue(h.getLevel().getBlockState(p).getLightEmission(h.getLevel(),p)==7,"The sky shard gives no light");
        h.getLevel().setBlock(wall,Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(!h.getLevel().getBlockState(p).is(Veilborne.SKY_SHARD.get()),"The shard hangs in the air after its wall is gone");
        BlockPos floor=h.absolutePos(new BlockPos(6,2,6));
        h.getLevel().setBlock(floor,Blocks.STONE.defaultBlockState(),3);
        h.getLevel().setBlock(floor.above(),Veilborne.MIRROR_LANTERN.get().defaultBlockState(),3);
        h.assertTrue(h.getLevel().getBlockState(floor.above()).getLightEmission(h.getLevel(),floor.above())==13,"The mirror lamp gives no light");
        h.succeed();
    }
}
