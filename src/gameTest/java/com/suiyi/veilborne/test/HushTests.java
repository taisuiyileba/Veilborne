package com.suiyi.veilborne.test;

import com.suiyi.veilborne.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

/** 无声白昼: persistence, sync, the order sound and colour leave and come back, the black sun covering whichever disc is up,
 *  one weather at a time, raising a light to the coat and the procession, the bell and its island, the late thunder, the
 *  hollow bell's memory, cleanup, and the blocks. */
@GameTestHolder(Veilborne.ID)
@PrefixGameTestTemplate(false)
public final class HushTests {
    @GameTest(template="empty",batch="48")
    public static void hushPersistsAcrossReload(GameTestHelper h){
        var d=new HushData();d.begin(new BlockPos(-30,71,90),88123,14400,320,HushData.VARIANT_KNOCK|HushData.VARIANT_BELL,true);
        d.age=7020;d.flags=HushData.COAT|HushData.BELL|HushData.FOUND|HushData.FEATHER;
        d.bell=new BlockPos(-8,72,101);d.bellTop=new BlockPos(-8,76,101);d.bellAt=4400;d.foundAt=6900;d.feather=new BlockPos(-29,71,88);d.featherAt=3100;
        var saved=d.save(new CompoundTag(),h.getLevel().registryAccess());
        var loaded=HushData.load(saved,h.getLevel().registryAccess());
        h.assertTrue(saved.equals(loaded.save(new CompoundTag(),h.getLevel().registryAccess())),"Silent-daylight state changed after reload");
        h.assertTrue(loaded.moon&&loaded.hasBell()&&loaded.found()&&!loaded.takeFlag(HushData.COAT),"The eclipse target, the bell or a spent encounter was lost");
        d.age=d.duration;
        h.assertTrue(!HushData.load(d.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess()).active,"A finished silent daylight resumed after restart");
        h.succeed();
    }
    @GameTest(template="empty",batch="49")
    public static void hushPacketRoundTrip(GameTestHelper h){
        var packet=new HushPacket(true,true,-771122334455L,6123,14400,320,new BlockPos(12,64,-40),HushData.BELL|HushData.FOUND,5,
            new BlockPos(30,65,-22),new BlockPos(30,69,-22),4000,6000,new BlockPos(13,64,-38),3000,HushService.FIRST|HushService.BELL);
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try{HushPacket.CODEC.encode(b,packet);h.assertTrue(packet.equals(HushPacket.CODEC.decode(b)),"Silent-daylight snapshot lost authoritative state");}finally{b.release();}
        var cue=new HushCue(HushCue.FLASH,1.5,70.25,-3.75,24);
        b=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try{HushCue.CODEC.encode(b,cue);h.assertTrue(cue.equals(HushCue.CODEC.decode(b)),"A raised light was lost on the way");}finally{b.release();}
        h.succeed();
    }
    @GameTest(template="empty",batch="50")
    public static void soundAndColourLeaveAndReturnInOrder(GameTestHelper h){
        // Going: music first, then the wind and the caves, the animals, the blocks, the footsteps last.
        h.assertTrue(HushTimeline.music(.04)<.02&&HushTimeline.ambient(.04)>.3&&HushTimeline.steps(.04)>.99,"Music does not leave first");
        h.assertTrue(HushTimeline.animals(.06)>HushTimeline.ambient(.06)&&HushTimeline.steps(.1)>HushTimeline.blocks(.1)-.2,"The layers do not leave one after another");
        h.assertTrue(HushTimeline.hush(.15)>.99&&HushTimeline.blocks(.5)>.1&&HushTimeline.blocks(.5)<.2,"The world is not silent (blocks muffled, not gone) once the weather is under way");
        // Coming back: the bird while the world is still silent, then the footsteps, and the sky's colour last.
        h.assertTrue(HushTimeline.steps(HushTimeline.BIRD)<.01,"Footsteps are back before the bird");
        h.assertTrue(HushTimeline.steps(HushTimeline.BIRD+.04)>.99&&HushTimeline.skyColour(HushTimeline.BIRD+.04)<.01,"Footsteps do not come back before the sky's colour");
        h.assertTrue(HushTimeline.music(.999)>.99&&HushTimeline.skyColour(.999)>.99&&HushTimeline.pale(0)==0&&HushTimeline.pale(1)==0,"Hard cut at a weather boundary");
        // The grey comes in from the horizon to your feet; colour goes back out from where you stand.
        double prev=1e9;
        for(int i=0;i<=200;i++){double p=i/200.0*3/12,f=HushTimeline.greyFront(p);h.assertTrue(f<=prev+1e-9,"The grey tide goes back out at "+p);prev=f;}
        h.assertTrue(HushTimeline.greyFront(0)>=HushTimeline.FAR-1&&HushTimeline.greyFront(2.8/12)<0,"The grey does not reach your feet");
        h.assertTrue(HushTimeline.colourFront(.89)<0&&HushTimeline.colourFront(.97)>HushTimeline.FAR,"Colour does not come back out to the horizon");
        h.assertTrue(HushTimeline.ribbons(5.4/12)>.99&&HushTimeline.ribbons(4.3/12)>.3&&HushTimeline.ribbons(7.4/12)==0&&HushTimeline.ribbons(9/12.0)<-.5,"The ribbons do not go up, stop, and come back down");
        // The held air: still through the weather, falling again after the bird.
        h.assertTrue(HushTimeline.stillness(0)==0&&HushTimeline.stillness(.5)>.99&&HushTimeline.stillness(.95)<.01,"The air is not held, or never let go");
        h.assertTrue(HushTimeline.gust(.1)==HushTimeline.gust(.5)&&HushTimeline.gust(.03)>HushTimeline.gust(.01),"The last wind does not stop dead");
        h.succeed();
    }
    @GameTest(template="empty",batch="51")
    public static void blackSunCoversWhicheverDiscIsUp(GameTestHelper h){
        for(long seed:new long[]{7L,991L,-42424242L,20261127L})for(double tod:new double[]{-.2,-.1,0,.12,.21})for(boolean moon:new boolean[]{false,true}){
            Vec3 white=HushTimeline.whiteDir(moon?tod+.5:tod,moon);
            Vec3 rise=HushTimeline.blackDir(seed,white,HushTimeline.RISE-.1/12);
            h.assertTrue(rise.y<.01&&rise.z<-.75,"The black sun does not rise out of the north: "+rise);
            for(int i=0;i<=20;i++){
                double p=HushTimeline.TOTAL0+.0005+(HushTimeline.TOTAL1-HushTimeline.TOTAL0-.001)*i/20;
                h.assertTrue(HushTimeline.coverage(HushTimeline.blackDir(seed,white,p),white)>.999,"The pale disc is not covered at totality ("+seed+", "+tod+", moon "+moon+")");
            }
            double last=-1;
            for(int i=0;i<=100;i++){
                double p=HushTimeline.RISE+(HushTimeline.TOTAL0-HushTimeline.RISE)*i/100;
                double c=HushTimeline.coverage(HushTimeline.blackDir(seed,white,p),white);
                h.assertTrue(c>=last-1e-6,"The cover falls back before totality");last=c;
            }
            h.assertTrue(HushTimeline.coverage(HushTimeline.blackDir(seed,white,5/12.0),white)==0,"The disc is already covered long before the eclipse");
            h.assertTrue(HushTimeline.coverage(HushTimeline.blackDir(seed,white,9.5/12),white)==0,"The black sun does not let go");
        }
        h.assertTrue(HushTimeline.phase(HushTimeline.TOTAL0+.01)==HushTimeline.Phase.ECLIPSE&&HushTimeline.totality(HushTimeline.TOTAL0+.02)>.99&&HushTimeline.totality(.5)==0,"Totality is not the climax");
        h.assertTrue(HushTimeline.ribbons(5/12.0)>.9&&HushTimeline.ribbons(HushTimeline.TOTAL0+.02)==0&&HushTimeline.ribbons(9/12.0)<0,"The colours do not flow up into it and back");
        h.succeed();
    }
    @GameTest(template="empty",batch="52")
    public static void fiveWeathersOneAtATime(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,22,8));
        ThreadService.begin(level,origin,780);HushService.begin(level,origin,720);
        h.assertTrue(HushData.get(level).active&&!ThreadData.get(level).active&&!TideData.get(level).active&&!MoonData.get(level).active&&!MirrorData.get(level).active,"The silent daylight started on top of another weather");
        TideService.begin(level,origin,840);
        h.assertTrue(TideData.get(level).active&&!HushData.get(level).active,"The sea started on top of the silent daylight");
        HushService.begin(level,origin,720);MoonService.begin(level,origin,720);
        h.assertTrue(MoonData.get(level).active&&!HushData.get(level).active,"The bloom started on top of the silent daylight");
        HushService.begin(level,origin,720);MirrorService.begin(level,origin,780);
        h.assertTrue(MirrorData.get(level).active&&!HushData.get(level).active,"The mirror started on top of the silent daylight");
        HushService.begin(level,origin,720);ThreadService.begin(level,origin,780);
        h.assertTrue(ThreadData.get(level).active&&!HushData.get(level).active,"The seam started on top of the silent daylight");
        ThreadService.end(level);
        HushService.begin(level,origin,720);HushService.end(level);
        h.assertTrue(TideData.get(level).quietTicks>=24000,"No quiet period after the silent daylight");
        h.succeed();
    }
    @GameTest(template="empty",batch="53")
    public static void aRaisedLightAnswersTheCoat(GameTestHelper h){
        var level=h.getLevel();BlockPos o=h.absolutePos(new BlockPos(3,2,6));
        HushService.begin(level,o,720);
        var d=HushData.get(level);d.age=(int)(d.duration*3.3/12);
        var p=h.makeMockServerPlayerInLevel();p.moveTo(o.getX()+.5,o.getY(),o.getZ()+.5,-90,0);
        var coat=Veilborne.HUSH_COAT.get().create(level);
        coat.place(Vec3.atBottomCenterOf(o.east(4)),o.east(5));level.addFreshEntity(coat);coat.debugState(HushCoat.State.WAIT,0);
        h.assertTrue(HushService.flash(p),"Raising a light under the silent daylight did nothing");
        h.assertTrue(coat.state()==HushCoat.State.BOW&&(TideService.records2(p)&HushService.KNOCK)!=0,"The coat did not answer the light");
        h.assertTrue(!HushService.flash(p),"A light raised again at once counted twice");
        // Another coat, behind a wall, does not see a light raised on this side of it.
        var q=h.makeMockServerPlayerInLevel();q.moveTo(o.getX()+.5,o.getY(),o.getZ()+.5,90,0);
        for(int y=0;y<3;y++)level.setBlock(o.west(2).above(y),Blocks.STONE.defaultBlockState(),3);
        var hidden=Veilborne.HUSH_COAT.get().create(level);
        hidden.place(Vec3.atBottomCenterOf(o.west(4)),o.west(5));level.addFreshEntity(hidden);hidden.debugState(HushCoat.State.WAIT,0);
        HushService.flash(q);
        h.assertTrue(hidden.state()==HushCoat.State.WAIT&&(TideService.records2(q)&HushService.KNOCK)==0,"A coat answered a light it could not see");
        HushService.end(level);
        level.getServer().getPlayerList().remove(p);level.getServer().getPlayerList().remove(q);
        h.succeed();
    }
    @GameTest(template="empty",batch="54")
    public static void aRaisedLightAnswersTheProcession(GameTestHelper h){
        var level=h.getLevel();BlockPos o=h.absolutePos(new BlockPos(6,2,6));
        HushService.begin(level,o,720);
        var d=HushData.get(level);d.age=(int)(d.duration*4.3/12);
        var proc=Veilborne.HUSH_PROCESSION.get().create(level);
        proc.setRoute(o.offset(-12,0,4),0,60,5);proc.preview(12,HushProcession.State.WALK);level.addFreshEntity(proc);
        var p=h.makeMockServerPlayerInLevel();p.moveTo(o.getX()+.5,o.getY(),o.getZ()+.5,0,0);
        h.assertTrue(HushService.flash(p)&&proc.state()==HushProcession.State.ANSWER,"The procession did not stop to answer");
        h.assertTrue((TideService.records2(p)&HushService.PROCESSION)!=0,"Answering was not recorded");
        var wax=level.getEntitiesOfClass(ItemEntity.class,new AABB(o).inflate(24),i->i.getItem().is(Veilborne.DAYLIGHT_WAX.get()));
        h.assertTrue(wax.size()==1,"The child did not leave one lump of wax: "+wax.size());
        HushService.end(level);
        h.assertTrue(proc.state()==HushProcession.State.DEPART||proc.isRemoved(),"The procession did not leave with the weather");
        level.getServer().getPlayerList().remove(p);
        h.succeed();
    }
    @GameTest(template="empty",batch="55")
    public static void theBellOpensAnIslandAndIsYours(GameTestHelper h){
        var level=h.getLevel();BlockPos o=h.absolutePos(new BlockPos(6,2,6));
        HushService.begin(level,o,720);
        var d=HushData.get(level);d.age=(int)(d.duration*4.5/12);
        // Which encounters a weather has is drawn at random; this one needs its bell.
        d.variant|=HushData.VARIANT_BELL;
        d.takeFlag(HushData.BELL);d.bell=o.east(2);d.bellTop=o.east(2).above(4);d.bellAt=d.age-10;
        var p=h.makeMockServerPlayerInLevel();p.moveTo(o.getX()+8.5,o.getY(),o.getZ()+.5,0,0);
        HushService.bell(level,d,d.progress(),p);
        h.assertTrue(!d.found(),"The bell was found from far away");
        p.moveTo(o.getX()+2.5,o.getY(),o.getZ()+.5,0,0);
        HushService.bell(level,d,d.progress(),p);
        h.assertTrue(d.found()&&(TideService.records2(p)&HushService.BELL)!=0,"Standing at the bell did not find it");
        h.assertTrue(HushTimeline.island(40)>0&&HushTimeline.island(HushTimeline.ISLAND_TICKS+1)==0,"The island does not open and close");
        d.age=d.foundAt+HushTimeline.ISLAND_TICKS;
        HushService.bell(level,d,d.progress(),p);
        var bells=level.getEntitiesOfClass(ItemEntity.class,new AABB(o).inflate(8),i->i.getItem().is(Veilborne.HOLLOW_BELL.get()));
        h.assertTrue(bells.size()==1&&d.has(HushData.BELL_DROPPED),"The bell did not come down when the island closed");
        HushService.bell(level,d,d.progress(),p);
        h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,new AABB(o).inflate(8),i->i.getItem().is(Veilborne.HOLLOW_BELL.get())).size()==1,"The bell came down twice");
        HushService.end(level);level.getServer().getPlayerList().remove(p);
        h.succeed();
    }
    @GameTest(template="empty",batch="56")
    public static void theThunderIsLateAndTheLineStill(GameTestHelper h){
        int dur=HushTimeline.DEFAULT_DURATION,line=HushTimeline.lineTick(dur),thunder=HushTimeline.thunderTick(dur);
        h.assertTrue(thunder-line>=600,"The thunder is not late enough to be strange");
        h.assertTrue(HushTimeline.line(line-1,dur)==0&&HushTimeline.line(line,dur)==1&&HushTimeline.line(line+HushTimeline.LINE_HOLD,dur)==1,"The line does not stand at once and hold still");
        h.assertTrue(HushTimeline.line(line+HushTimeline.LINE_HOLD+HushTimeline.LINE_FADE+1,dur)==0&&HushTimeline.afterimage(line+HushTimeline.LINE_HOLD+100,dur)>.5,"The line does not leave its afterimage");
        h.assertTrue(HushTimeline.phase(HushTimeline.LINE)==HushTimeline.Phase.THUNDER&&thunder<Math.round(HushTimeline.BIRD*dur),"The thunder does not come before the return");
        int ring=-1;
        for(int t=0;t<=2000;t++){int s=HushTimeline.sinceRing(1000+t,1000,77);if(s==0){h.assertTrue(ring<0||t-ring>=HushTimeline.RING_EVERY-31,"The bell rings too often");ring=t;}}
        h.assertTrue(ring>0,"The bell never rang");
        h.succeed();
    }
    @GameTest(template="empty",batch="57")
    public static void theHollowBellKeepsAWeather(GameTestHelper h){
        var level=h.getLevel();BlockPos o=h.absolutePos(new BlockPos(6,2,6));
        var p=h.makeMockServerPlayerInLevel();p.moveTo(o.getX()+.5,o.getY(),o.getZ()+.5,0,0);
        HushService.end(level);TideService.end(level);MoonService.end(level);MirrorService.end(level);ThreadService.end(level);
        h.assertTrue(HushItem.sounding(p)<0,"A weather is sounding where there is none");
        HushService.begin(level,o,720);
        h.assertTrue(HushItem.sounding(p)==4,"The silent daylight is not heard where it is");
        ThreadService.begin(level,o,780);
        h.assertTrue(HushItem.sounding(p)==3,"The seam is not heard where it is");
        var bell=new ItemStack(Veilborne.HOLLOW_BELL.get());
        h.assertTrue(HushItem.recording(bell)<0,"A new hollow bell already holds something");
        HushItem.setRecording(bell,HushItem.sounding(p));
        h.assertTrue(HushItem.recording(bell)==3&&HushItem.recording(bell.copy())==3,"The hollow bell forgot what it heard");
        ThreadService.end(level);level.getServer().getPlayerList().remove(p);
        h.succeed();
    }
    @GameTest(template="empty",batch="58")
    public static void stoppingTheHushSendsVisitorsHome(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,22,8));
        level.setBlock(origin,Blocks.DIAMOND_BLOCK.defaultBlockState(),3);
        HushService.begin(level,origin,720);
        var coat=Veilborne.HUSH_COAT.get().create(level);coat.place(Vec3.atBottomCenterOf(origin.above()),origin.east());level.addFreshEntity(coat);coat.debugState(HushCoat.State.WAIT,0);
        var proc=Veilborne.HUSH_PROCESSION.get().create(level);proc.setRoute(origin,0,40,3);proc.preview(4,HushProcession.State.WALK);level.addFreshEntity(proc);
        HushService.end(level);
        h.assertTrue(level.getBlockState(origin).is(Blocks.DIAMOND_BLOCK),"The silent daylight damaged a building");
        h.assertTrue(coat.state()==HushCoat.State.LEAVE&&proc.state()==HushProcession.State.DEPART&&!HushData.get(level).active,"Stop left a coat knocking, a procession walking or an active weather behind");
        h.assertTrue(TideData.get(level).quietTicks>=24000,"No quiet period after the silent daylight");
        h.succeed();
    }
    @GameTest(template="empty",batch="59")
    public static void hushBlocksStandAndLight(GameTestHelper h){
        var level=h.getLevel();
        BlockPos floor=h.absolutePos(new BlockPos(3,2,3));
        level.setBlock(floor,Blocks.STONE.defaultBlockState(),3);
        level.setBlock(floor.above(),Veilborne.STILL_CANDLE.get().defaultBlockState(),3);
        h.assertTrue(level.getBlockState(floor.above()).getLightEmission(level,floor.above())==5,"The still candle gives no light");
        level.setBlock(floor,Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(!level.getBlockState(floor.above()).is(Veilborne.STILL_CANDLE.get()),"The candle stands on nothing");
        BlockPos f2=h.absolutePos(new BlockPos(6,2,6));
        level.setBlock(f2,Blocks.STONE.defaultBlockState(),3);
        level.setBlock(f2.above(),Veilborne.SILENT_LANTERN.get().defaultBlockState(),3);
        h.assertTrue(level.getBlockState(f2.above()).getLightEmission(level,f2.above())==14,"The silent lantern gives no light");
        h.assertTrue(HushService.isLight(new ItemStack(Veilborne.SILENT_LANTERN_ITEM.get()))&&HushService.isLight(new ItemStack(net.minecraft.world.item.Items.TORCH))
            &&!HushService.isLight(new ItemStack(net.minecraft.world.item.Items.STONE)),"What counts as a light to raise is wrong");
        BlockPos f3=h.absolutePos(new BlockPos(9,2,3));
        level.setBlock(f3,Blocks.STONE.defaultBlockState(),3);
        level.setBlock(f3.above(),Veilborne.STILL_FEATHER.get().defaultBlockState(),3);
        level.setBlock(f3,Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(!level.getBlockState(f3.above()).is(Veilborne.STILL_FEATHER.get()),"The cloche stands on nothing");
        h.succeed();
    }
    private HushTests(){}
}
