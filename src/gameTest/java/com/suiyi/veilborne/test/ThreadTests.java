package com.suiyi.veilborne.test;

import com.suiyi.veilborne.*;
import io.netty.buffer.Unpooled;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

/** 绯线缝世: persistence, sync, choreography, the needle meeting its own holes, the world stitch, the strikes and their
 *  warning, what holds you steady, the pocket and its anchor, the walker's knot, cleanup, and the three blocks. */
@GameTestHolder(Veilborne.ID)
@PrefixGameTestTemplate(false)
public final class ThreadTests {
    /** GameTestHelper's mock player always reports creative mode, and strikes rightly spare creative players: this one is a survivor. */
    private static net.minecraft.server.level.ServerPlayer survivor(GameTestHelper h){
        var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(),"test-survivor"),false);
        var p=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation()){
            @Override public boolean isSpectator(){return false;}
            @Override public boolean isCreative(){return false;}
        };
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection,p,cookie);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        // A player who has only just arrived cannot be hurt for three seconds; mock players are never ticked, so pass it here.
        for(int i=0;i<61;i++)p.tick();
        return p;
    }
    @GameTest(template="empty",batch="35")
    public static void seamPersistsAcrossReload(GameTestHelper h){
        var d=new ThreadData();d.begin(new BlockPos(40,70,-20),5511,15600,320,ThreadData.VARIANT_WALKER|ThreadData.VARIANT_POCKET);
        d.age=9100;d.flags=ThreadData.WALKER|ThreadData.LAND|ThreadData.POCKET|ThreadData.TUNED;
        d.strikes=new BlockPos[]{new BlockPos(44,70,-9),new BlockPos(31,71,-14),BlockPos.ZERO};
        d.landA=new BlockPos(52,70,-4);d.landB=new BlockPos(210,76,-120);d.landAt=8400;d.pocket=new BlockPos(50,70,-6);d.pocketAt=9000;
        d.anchor=new BlockPos(51,70,-6);d.tuneAt=9050;d.keepsake=new BlockPos(41,70,-19);d.keepsakeAt=9090;
        var saved=d.save(new CompoundTag(),h.getLevel().registryAccess());
        var loaded=ThreadData.load(saved,h.getLevel().registryAccess());
        h.assertTrue(saved.equals(loaded.save(new CompoundTag(),h.getLevel().registryAccess())),"Seam state changed after reload");
        h.assertTrue(loaded.hasLand()&&loaded.hasPocket()&&!loaded.takeFlag(ThreadData.WALKER),"The land stitch, the pocket or a spent encounter was lost");
        d.age=d.duration;
        h.assertTrue(!ThreadData.load(d.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess()).active,"A finished sewing resumed after restart");
        h.succeed();
    }
    @GameTest(template="empty",batch="36")
    public static void seamPacketRoundTrip(GameTestHelper h){
        var packet=new ThreadPacket(true,-9912340011L,8123,15600,320,new BlockPos(-80,66,51),ThreadData.LAND|ThreadData.POCKET,3,
            List.of(new BlockPos(-70,66,57),new BlockPos(-90,67,48),BlockPos.ZERO),new BlockPos(-66,66,63),new BlockPos(90,70,-50),8100,
            new BlockPos(-68,66,61),9000,BlockPos.ZERO,0,BlockPos.ZERO,0,ThreadService.FIRST|ThreadService.SEWN);
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try{ThreadPacket.CODEC.encode(b,packet);h.assertTrue(packet.equals(ThreadPacket.CODEC.decode(b)),"Seam snapshot lost authoritative state");}finally{b.release();}
        h.succeed();
    }
    @GameTest(template="empty",batch="37")
    public static void seamHasOmenClimaxAndFade(GameTestHelper h){
        h.assertTrue(ThreadTimeline.phase(0)==ThreadTimeline.Phase.OMEN&&ThreadTimeline.omen(.04)>.8&&ThreadTimeline.veil(.03)==0,"The omen is not threads first");
        h.assertTrue(ThreadTimeline.veil(0)==0&&ThreadTimeline.veil(1)==0&&ThreadTimeline.needle(0)==0&&ThreadTimeline.needle(1)==0,"Hard cut at a weather boundary");
        h.assertTrue(ThreadTimeline.tear(1.3/13)<ThreadTimeline.tear(2.2/13)&&ThreadTimeline.tear(3/13.0)>.99,"The tear does not run");
        h.assertTrue(ThreadTimeline.needle(ThreadTimeline.STITCH_START+.01)>.9,"No needle while it stitches");
        h.assertTrue(ThreadTimeline.phase(ThreadTimeline.pAtSew(.5))==ThreadTimeline.Phase.SEW&&ThreadTimeline.tug(ThreadTimeline.pAtSew(ThreadTimeline.TUG0+.012))>.9,"The pull is not the climax");
        h.assertTrue(ThreadTimeline.tug(ThreadTimeline.pAtSew(.3))==0&&ThreadTimeline.tug(ThreadTimeline.pAtSew(.9))==0,"The pull happens outside its moment");
        h.assertTrue(ThreadTimeline.strings(.3)==0&&ThreadTimeline.moment(.3)==0,"Strings or the moment outside the sewing");
        h.assertTrue(ThreadTimeline.fall(ThreadTimeline.CUT-.001)==0&&ThreadTimeline.fall(ThreadTimeline.CUT+.02)>.99,"The cut threads do not fall");
        h.assertTrue(ThreadTimeline.starband(.5)==0&&ThreadTimeline.starband(.98)>.99,"The seam never becomes stars");
        h.assertTrue(ThreadTimeline.pocketOpen(ThreadTimeline.POCKET_OPEN+.01)&&!ThreadTimeline.pocketOpen(.5)&&!ThreadTimeline.pocketOpen(.99),"The pocket is open at the wrong time");
        h.succeed();
    }
    @GameTest(template="empty",batch="38")
    public static void fourWeathersOneAtATime(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,22,8));
        TideService.begin(level,origin,840);MoonService.begin(level,origin,720);MirrorService.begin(level,origin,780);
        ThreadService.begin(level,origin,780);
        h.assertTrue(ThreadData.get(level).active&&!MirrorData.get(level).active&&!MoonData.get(level).active&&!TideData.get(level).active,"The seam started on top of another weather");
        MirrorService.begin(level,origin,780);
        h.assertTrue(MirrorData.get(level).active&&!ThreadData.get(level).active,"The mirror started on top of the seam");
        ThreadService.begin(level,origin,780);TideService.begin(level,origin,840);
        h.assertTrue(TideData.get(level).active&&!ThreadData.get(level).active,"The sea started on top of the seam");
        ThreadService.begin(level,origin,780);MoonService.begin(level,origin,720);
        h.assertTrue(MoonData.get(level).active&&!ThreadData.get(level).active,"The bloom started on top of the seam");
        MoonService.end(level);
        ThreadService.begin(level,origin,780);ThreadService.end(level);
        h.assertTrue(TideData.get(level).quietTicks>=24000,"No quiet period after the seam");
        h.succeed();
    }
    @GameTest(template="empty",batch="39")
    public static void stitchesCloseTheWound(GameTestHelper h){
        double prev=-100;
        for(int i=0;i<=4000;i++){
            double p=ThreadTimeline.STITCH_START+i/4000.0*(ThreadTimeline.SEW_START-ThreadTimeline.STITCH_START);
            double f=ThreadTimeline.closeFront(p);
            h.assertTrue(f>=prev-1e-9,"The wound opens again at "+p);
            prev=f;
        }
        for(int k=0;k<ThreadTimeline.STITCHES;k++){
            double a=ThreadTimeline.stitchAlong(k);
            double before=ThreadTimeline.STITCH_START+(k+.1)*ThreadTimeline.span(),after=ThreadTimeline.STITCH_START+(k+.99)*ThreadTimeline.span()+ThreadTimeline.span()*.5;
            h.assertTrue(ThreadTimeline.halfWidth(a,before)>ThreadTimeline.openWidth(a)*.95,"Stitch "+k+" is closed before the needle reaches it");
            h.assertTrue(ThreadTimeline.halfWidth(a,after)<ThreadTimeline.LINE*1.05,"Stitch "+k+" is still open after it was pulled tight");
            h.assertTrue(ThreadTimeline.chord(k,before,true)>ThreadTimeline.openWidth(a)*ThreadTimeline.DOME_RADIUS+ThreadTimeline.MARGIN*.9,"Stitch "+k+" goes in inside the wound, not through the cloth");
        }
        h.assertTrue(ThreadTimeline.halfWidth(0,ThreadTimeline.SEW_START)<=ThreadTimeline.LINE*1.001,"The middle is not sewn by the time the world is");
        h.succeed();
    }
    @GameTest(template="empty",batch="40")
    public static void needleGoesInThroughItsOwnHoles(GameTestHelper h){
        var seam=ThreadTimeline.seam(new BlockPos(100,64,-40),424242);
        for(int k=0;k<ThreadTimeline.STITCHES;k++){
            double pin=ThreadTimeline.STITCH_START+(k+1e-6)*ThreadTimeline.span();
            var pose=ThreadTimeline.stitching(seam,pin);
            Vec3 hole=ThreadTimeline.hole(seam,k,pin,true);
            h.assertTrue(pose!=null&&pose.tipPoint().distanceTo(hole)<3.5,"Stitch "+k+": the point is "+(pose==null?"missing":pose.tipPoint().distanceTo(hole)+" blocks")+" from its entry hole");
            double pout=ThreadTimeline.STITCH_START+(k+ThreadTimeline.exitAt(k))*ThreadTimeline.span();
            pose=ThreadTimeline.stitching(seam,pout);
            Vec3 exit=ThreadTimeline.hole(seam,k,pout,false);
            h.assertTrue(pose.tipPoint().distanceTo(exit)<3.5,"Stitch "+k+": the point comes out "+pose.tipPoint().distanceTo(exit)+" blocks from its exit hole");
            // In between, the point is in front of the cloth, on the land's side.
            double mid=ThreadTimeline.STITCH_START+(k+ThreadTimeline.exitAt(k)*.5)*ThreadTimeline.span();
            h.assertTrue(ThreadTimeline.stitching(seam,mid).tipPoint().distanceTo(seam.center())<ThreadTimeline.DOME_RADIUS,"Stitch "+k+": the point crosses the wound behind the cloth");
        }
        h.succeed();
    }
    @GameTest(template="empty",batch="41")
    public static void worldStitchRollsThroughTheEarth(GameTestHelper h){
        var seam=ThreadTimeline.seam(new BlockPos(0,64,0),777);
        Vec3 a=new Vec3(20,64,5),b=a.add(ThreadTimeline.LAND_HALF*2*.8,6,ThreadTimeline.LAND_HALF*2*-.6);
        var in=ThreadTimeline.sewing(seam,a,b,ThreadTimeline.strikeA()+1e-7);
        h.assertTrue(in.tipPoint().distanceTo(a)<1.5,"The point does not go in at the first foot: "+in.tipPoint().distanceTo(a));
        var out=ThreadTimeline.sewing(seam,a,b,ThreadTimeline.strikeB(a,b));
        h.assertTrue(out.tipPoint().distanceTo(b)<1.5,"The point does not come out at the far foot: "+out.tipPoint().distanceTo(b));
        double mid=(ThreadTimeline.strikeA()+ThreadTimeline.strikeB(a,b))/2;
        h.assertTrue(ThreadTimeline.sewing(seam,a,b,mid).tipPoint().y<Math.min(a.y,b.y)-10,"Between the feet the point is not under the land");
        var done=ThreadTimeline.sewing(seam,a,b,ThreadTimeline.pAtSew(ThreadTimeline.THROUGH)-1e-7);
        h.assertTrue(done.eye().distanceTo(b)<2,"The eye does not follow out at the far foot: "+done.eye().distanceTo(b));
        // It starts and ends in the sky.
        h.assertTrue(ThreadTimeline.sewing(seam,a,b,ThreadTimeline.SEW_START).tipPoint().y>a.y+300,"The needle does not start from the seam");
        h.assertTrue(ThreadTimeline.sewing(seam,a,b,ThreadTimeline.pAtSew(ThreadTimeline.ASCEND+.05)).tipPoint().y>a.y+300,"The needle does not go back up");
        h.succeed();
    }
    @GameTest(template="empty",batch="42")
    public static void strikesWarnBeforeHarm(GameTestHelper h){
        int at=ThreadTimeline.strikeTick(0,ThreadTimeline.DEFAULT_DURATION);
        h.assertTrue(ThreadTimeline.warning(at-ThreadTimeline.STRIKE_WARN-1,at,ThreadTimeline.STRIKE_WARN)<0,"A warning before its time");
        h.assertTrue(ThreadTimeline.warning(at-ThreadTimeline.STRIKE_WARN,at,ThreadTimeline.STRIKE_WARN)==0&&ThreadTimeline.warning(at-1,at,ThreadTimeline.STRIKE_WARN)>.99,"The warning does not build to the strike");
        h.assertTrue(ThreadTimeline.STRIKE_WARN>=100&&ThreadTimeline.LAND_WARN_TICKS>=100,"Too little warning to step out");
        var level=h.getLevel();BlockPos o=h.absolutePos(new BlockPos(6,2,6));
        var stay=survivor(h);var left=survivor(h);
        Vec3 c=Vec3.atBottomCenterOf(o);
        stay.moveTo(c.x+1,c.y,c.z,0,0);left.moveTo(c.x+ThreadTimeline.STRIKE_RANGE+3,c.y,c.z,0,0);
        Set<UUID> warned=new HashSet<>(Set.of(stay.getUUID(),left.getUUID()));
        float hs=stay.getHealth(),hl=left.getHealth();
        ThreadService.strike(level,c,ThreadTimeline.STRIKE_RANGE,warned,4);
        h.assertTrue(stay.getHealth()<hs,"Staying in the ring did not hurt");
        h.assertTrue(left.getHealth()>=hl&&(TideService.records(left)&ThreadService.FORESIGHT)!=0,"Stepping out was hurt or not recorded as reading the warning");
        h.assertTrue((TideService.records(stay)&ThreadService.FORESIGHT)==0,"Staying was recorded as reading the warning");
        level.getServer().getPlayerList().remove(stay);level.getServer().getPlayerList().remove(left);
        h.succeed();
    }
    @GameTest(template="empty",batch="43")
    public static void anchorAndCharmHoldYouSteady(GameTestHelper h){
        var level=h.getLevel();BlockPos o=h.absolutePos(new BlockPos(6,2,6));
        var p=h.makeMockServerPlayerInLevel();
        p.moveTo(o.getX()+.5,o.getY(),o.getZ()+.5,0,0);
        p.getInventory().clearContent();
        h.assertTrue(!ThreadService.steady(level,p),"Held steady with nothing to hold");
        p.getInventory().add(new ItemStack(Veilborne.BINDING_CHARM.get()));
        h.assertTrue(ThreadService.steady(level,p),"The binding knot does not hold its bearer");
        p.getInventory().clearContent();
        level.setBlock(o.offset(0,-1,0).east(3),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(o.east(3),Veilborne.TUNING_ANCHOR.get().defaultBlockState(),3);
        h.assertTrue(ThreadService.steady(level,p),"A tuning anchor three blocks away does not hold");
        p.moveTo(o.getX()+.5-ThreadService.ANCHOR_RANGE-2,o.getY(),o.getZ()+.5,0,0);
        h.assertTrue(!ThreadService.steady(level,p),"A tuning anchor holds from too far away");
        level.getServer().getPlayerList().remove(p);
        h.succeed();
    }
    @GameTest(template="empty",batch="44")
    public static void pocketNeedsItsAnchorInside(GameTestHelper h){
        var level=h.getLevel();BlockPos o=h.absolutePos(new BlockPos(6,2,6));
        ThreadService.begin(level,o,780);
        var d=ThreadData.get(level);
        h.assertTrue(!ThreadService.tune(level,d,o),"An anchor tuned a pocket that is not there");
        d.flags|=ThreadData.POCKET;d.pocket=o;d.pocketAt=0;
        d.age=(int)(d.duration*(ThreadTimeline.POCKET_OPEN-.05));
        h.assertTrue(!ThreadService.tune(level,d,o),"An anchor tuned the pocket before it opened");
        d.age=(int)(d.duration*(ThreadTimeline.POCKET_OPEN+.02));
        h.assertTrue(!ThreadService.tune(level,d,o.east(8)),"An anchor outside the pocket tuned it");
        h.assertTrue(ThreadService.tune(level,d,o.east(2))&&d.has(ThreadData.TUNED)&&d.anchor.equals(o.east(2)),"An anchor inside the pocket did not tune it");
        h.assertTrue(!ThreadService.tune(level,d,o.west(1)),"The pocket was tuned twice");
        long spirits=level.getEntities(Veilborne.SPIRIT.get(),s->true).size();
        h.assertTrue(spirits<=ThreadService.SPIRITS,"More thread spirits woke than the wave allows: "+spirits);
        ThreadService.end(level);
        h.assertTrue(level.getEntities(Veilborne.SPIRIT.get(),s->true).isEmpty(),"Thread spirits outlived the weather");
        h.succeed();
    }
    @GameTest(template="empty",batch="45",timeoutTicks=120)
    public static void walkerKnotComesUndoneOnce(GameTestHelper h){
        var level=h.getLevel();BlockPos o=h.absolutePos(new BlockPos(6,2,6));
        ThreadService.begin(level,o,780);ThreadData.get(level).age=(int)(ThreadData.get(level).duration*.4);
        var w=Veilborne.WALKER.get().create(level);
        w.plan(o,new Vec3(0,1,0));w.debugState(NeedleWalker.State.KNOT);w.moveTo(o.getX()+.5,o.getY(),o.getZ()+.5);level.addFreshEntity(w);
        var p=h.makeMockServerPlayerInLevel();
        h.assertTrue(w.startAlarm(p)&&w.state()==NeedleWalker.State.ALARM&&w.ends()==7,"Touching the knot did not undo it into three ends");
        h.assertTrue(!w.startAlarm(p),"The knot came undone twice");
        for(int k=0;k<3;k++){
            Vec3 e=w.endAt(k);
            h.assertTrue(e.distanceTo(Vec3.atBottomCenterOf(o))<NeedleWalker.END_RANGE+1.5,"A loose end flew too far");
        }
        int before=p.getInventory().countItem(Veilborne.SCARLET_THREAD.get());
        Vec3 e0=w.endAt(0);p.moveTo(e0.x,e0.y-.8,e0.z);
        h.runAfterDelay(3,()->{
            h.assertTrue(p.getInventory().countItem(Veilborne.SCARLET_THREAD.get())>before&&(w.ends()&1)==0,"Catching a loose end gave nothing");
            ThreadService.end(level);
            h.assertTrue(w.state()==NeedleWalker.State.DEPART,"The walker did not leave with the weather");
            level.getServer().getPlayerList().remove(p);
            h.succeed();
        });
    }
    @GameTest(template="empty",batch="46")
    public static void stoppingTheSeamSendsVisitorsHome(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,22,8));
        level.setBlock(origin,Blocks.DIAMOND_BLOCK.defaultBlockState(),3);
        ThreadService.begin(level,origin,780);
        var s=Veilborne.SPIRIT.get().create(level);s.setPos(Vec3.atCenterOf(origin.above(2)));level.addFreshEntity(s);
        var w=Veilborne.WALKER.get().create(level);w.plan(origin.above(),new Vec3(0,1,0));level.addFreshEntity(w);
        ThreadService.end(level);
        h.assertTrue(level.getBlockState(origin).is(Blocks.DIAMOND_BLOCK),"The seam damaged a building");
        h.assertTrue(s.isRemoved()&&w.state()==NeedleWalker.State.DEPART&&!ThreadData.get(level).active,"Stop left a spirit, a walker or an active seam behind");
        h.assertTrue(TideData.get(level).quietTicks>=24000,"No quiet period after the seam");
        h.succeed();
    }
    @GameTest(template="empty",batch="47")
    public static void seamBlocksStandAndLight(GameTestHelper h){
        var level=h.getLevel();
        BlockPos floor=h.absolutePos(new BlockPos(3,2,3));
        level.setBlock(floor,Blocks.STONE.defaultBlockState(),3);
        level.setBlock(floor.above(),Veilborne.TUNING_ANCHOR.get().defaultBlockState(),3);
        h.assertTrue(level.getBlockState(floor.above()).getLightEmission(level,floor.above())==6,"The tuning anchor gives no light");
        level.setBlock(floor,Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(!level.getBlockState(floor.above()).is(Veilborne.TUNING_ANCHOR.get()),"The anchor stands on nothing");
        BlockPos f2=h.absolutePos(new BlockPos(6,2,6));
        level.setBlock(f2,Blocks.STONE.defaultBlockState(),3);
        level.setBlock(f2.above(),Veilborne.LOOM_LANTERN.get().defaultBlockState(),3);
        h.assertTrue(level.getBlockState(f2.above()).getLightEmission(level,f2.above())==13,"The sky-weaving lamp gives no light");
        BlockPos wall=h.absolutePos(new BlockPos(9,2,3));
        level.setBlock(wall,Blocks.STONE.defaultBlockState(),3);
        level.setBlock(wall.north(),Veilborne.ROBE_SCRAP.get().defaultBlockState().setValue(ThreadBlocks.Scrap.FACING,Direction.NORTH),3);
        h.assertTrue(level.getBlockState(wall.north()).getLightEmission(level,wall.north())==5,"The robe scrap gives no light");
        level.setBlock(wall,Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(!level.getBlockState(wall.north()).is(Veilborne.ROBE_SCRAP.get()),"The scrap hangs in the air after its wall is gone");
        h.succeed();
    }
    private ThreadTests(){}
}
