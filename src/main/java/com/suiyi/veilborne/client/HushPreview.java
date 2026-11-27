package com.suiyi.veilborne.client;

import com.suiyi.veilborne.*;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * 无声白昼 scenes for the art harness (-PtidePreview=art -PtidePreviewScenes=hush). They play in a meadow built north of
 * the other stages: a small cottage with a door facing the meadow and a window on each side, a lantern inside and a torch
 * by the door, two trees, a pond, a path and flowers enough to see the colour go. The event seed and every site are fixed.
 * Each scene sets the weather's age exactly: the harness captures 90 ticks after its setup, so ages are set that much early.
 */
final class HushPreview {
    static final long SEED=20261127L;
    static BlockPos site(BlockPos anchor){return anchor.offset(0,0,-76);}
    static BlockPos door(BlockPos anchor){return site(anchor).offset(-8,0,5);}
    static BlockPos treeA(BlockPos anchor){return site(anchor).offset(-13,0,-9);}
    static BlockPos treeB(BlockPos anchor){return site(anchor).offset(12,0,-12);}
    static final int CAPTURE_LAG=90;

    static void scenes(List<ArtPreview.Scene> out){
        out.add(hush("hush_omen",.03,HushPreview::reset,c->aim(c,new Vec3(3,1.7,4),new Vec3(-10,3,-8))));
        out.add(hush("hush_greytide",2.2/12,HushPreview::reset,c->aim(c,new Vec3(0,24,26),new Vec3(0,0,-40))));
        out.add(hush("hush_pale",2.9/12,HushPreview::reset,c->aim(c,new Vec3(4,1.8,14),new Vec3(-6,2,2))));
        out.add(hush("hush_pale_wide",2.9/12,HushPreview::reset,c->aim(c,new Vec3(0,3,16),new Vec3(0,8,-60))));
        out.add(hush("hush_night",2.9/12,c->{reset(c);c.level.setDayTime(18000);},c->aim(c,new Vec3(4,1.8,14),new Vec3(-6,2,2))));
        out.add(hush("hush_lamp",3.1/12,HushPreview::reset,c->aim(c,new Vec3(-4,1.7,11),new Vec3(-7,1.2,7))));
        out.add(hush("hush_indoor",3.1/12,HushPreview::reset,c->aim(c,new Vec3(-8,1.62,3.2),new Vec3(-11,1.2,3.6))));
        out.add(hush("hush_dust",3.2/12,HushPreview::reset,c->aim(c,new Vec3(-10,1.6,-5),new Vec3(-13,2.6,-9))));
        out.add(hush("hush_pond",3.2/12,HushPreview::reset,c->aim(c,new Vec3(4,1.4,-2),new Vec3(9,.4,1))));
        out.add(hush("hush_rise",3.55/12,HushPreview::reset,c->skyShot(c,false,0)));
        out.add(hush("hush_lens",4.6/12,HushPreview::reset,c->skyShot(c,false,40)));
        out.add(hush("hush_ribbons",5.4/12,HushPreview::reset,c->skyShot(c,false,70)));
        out.add(hush("hush_ribbons_wide",5.4/12,HushPreview::reset,c->{skyShot(c,false,0);c.mc.options.fov().set(90);}));
        out.add(hush("hush_contact",6.72/12,HushPreview::reset,c->skyShot(c,true,20)));
        out.add(hush("hush_totality",7.35/12,HushPreview::reset,c->skyShot(c,true,20)));
        out.add(hush("hush_totality_land",7.35/12,HushPreview::reset,c->aim(c,new Vec3(4,1.8,14),new Vec3(-6,4,-2))));
        out.add(hush("hush_eclipse_night",7.35/12,c->{reset(c);c.level.setDayTime(17000);var d=HushData.get(c.level);d.moon=true;d.setDirty();},c->skyShot(c,true,20)));
        out.add(hush("hush_procession",4.3/12,c->{reset(c);procession(c,HushProcession.State.WALK,false);},c->aim(c,new Vec3(4,1.7,21),new Vec3(-2,1.2,10))));
        out.add(hush("hush_procession_close",4.3/12,c->{reset(c);procession(c,HushProcession.State.WALK,false);},HushPreview::processionClose));
        out.add(hush("hush_procession_answer",4.3/12,c->{reset(c);procession(c,HushProcession.State.WALK,false);},c->{aim(c,new Vec3(4,1.7,17),new Vec3(-2,1.6,10));atTick(c,26,l->{for(var e:l.getAllEntities())if(e instanceof HushProcession p)p.answer(Vec3.atBottomCenterOf(site(c.anchor)).add(4,0,17));});}));
        out.add(hush("hush_procession_look",7.35/12,c->{reset(c);procession(c,HushProcession.State.LOOK,false);},c->aim(c,new Vec3(4,1.7,19),new Vec3(-2,1.8,10))));
        out.add(hush("hush_procession_lantern",4.3/12,c->{reset(c);procession(c,HushProcession.State.WALK,false);lantern(c);},c->aim(c,new Vec3(3,1.9,15),new Vec3(-1,.2,10))));
        out.add(hush("hush_coat",3.3/12,c->{reset(c);coat(c,HushCoat.State.WAIT,0);},c->{aim(c,new Vec3(-5,1.8,11),new Vec3(-8,1.2,7));coatState(c,HushCoat.State.KNOCK,10);}));
        out.add(hush("hush_coat_inside",3.3/12,c->{reset(c);openDoor(c);coat(c,HushCoat.State.WAIT,0);},c->aim(c,new Vec3(-8,1.62,3),new Vec3(-8,1.3,8))));
        out.add(hush("hush_coat_bow",3.3/12,c->{reset(c);coat(c,HushCoat.State.WAIT,0);},c->{aim(c,new Vec3(-5.5,1.7,10.5),new Vec3(-8,1,7));coatState(c,HushCoat.State.BOW,18);}));
        out.add(hush("hush_coat_erase",3.3/12,c->{reset(c);coat(c,HushCoat.State.WAIT,0);},c->{aim(c,new Vec3(-5.5,1.7,10.5),new Vec3(-8,1.2,7));coatState(c,HushCoat.State.ERASE,20);}));
        out.add(hush("hush_bell",4.4/12,c->{reset(c);bell(c,false);},c->aim(c,new Vec3(-9,1.7,-4),new Vec3(-13,2.2,-8.5))));
        out.add(hush("hush_bell_ripple",4.4/12,c->{reset(c);bell(c,false);},c->aim(c,new Vec3(2,4,8),new Vec3(-10,0,-6))));
        out.add(hush("hush_island",4.4/12,c->{reset(c);bell(c,true);},c->aim(c,new Vec3(-6,2.2,-1),new Vec3(-13,1.5,-8))));
        out.add(hush("hush_line",HushTimeline.LINE+40/(double)HushTimeline.DEFAULT_DURATION,HushPreview::reset,c->lineShot(c)));
        out.add(hush("hush_afterimage",HushTimeline.LINE+(HushTimeline.LINE_HOLD+HushTimeline.LINE_FADE+50)/(double)HushTimeline.DEFAULT_DURATION,HushPreview::reset,c->lineShot(c)));
        out.add(hush("hush_feather",3.3/12,c->{reset(c);feather(c);},c->aim(c,new Vec3(0,1.62,4),new Vec3(1,2.35,.5))));
        out.add(hush("hush_flash",3.3/12,HushPreview::reset,c->{c.mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);aim(c,new Vec3(0,1.62,6),new Vec3(0,1.2,0));flash(c);}));
        out.add(hush("hush_bird",HushTimeline.BIRD+.004,c->{reset(c);feather(c);},c->aim(c,new Vec3(0,1.62,4),new Vec3(1,1.5,.5))));
        out.add(hush("hush_return",.935,HushPreview::reset,c->aim(c,new Vec3(0,2.4,10),new Vec3(0,5,-60))));
        out.add(hush("hush_return_sky",.978,HushPreview::reset,c->aim(c,new Vec3(0,2.4,10),new Vec3(0,20,-60))));
        out.add(hush("hush_candle",.5,c->{reset(c);keepsakes(c);HushService.end(c.level);},c->aim(c,new Vec3(1.5,1.6,-1),new Vec3(-.5,.4,-4))));
        out.add(hush("hush_items_hotbar",3.2/12,HushPreview::keepsakes,c->{c.mc.options.hideGui=false;aim(c,new Vec3(1.5,1.6,-1),new Vec3(-.5,.4,-4));}));
        out.add(hush("hush_blocks",3.2/12,HushPreview::keepsakes,c->{c.mc.options.hideGui=true;aim(c,new Vec3(1.5,1.6,-1),new Vec3(-.5,.4,-4));}));
        for(int i=0;i<4;i++){int spread=i;
            out.add(hush("hush_journal_"+i,4.2/12,c->{reset(c);c.player.getPersistentData().putInt(TideService.RECORD2,HushService.FIRST|HushService.ECLIPSE|HushService.BELL|HushService.HEARD);},c->journal(c,spread)));}
        for(String round:new String[]{"a","b"}){
            out.add(hush("perf_hush_pale_"+round,3.2/12,HushPreview::reset,c->aim(c,new Vec3(0,3,16),new Vec3(0,8,-60))));
            out.add(hush("perf_hush_sky_"+round,5.4/12,HushPreview::reset,c->skyShot(c,false,0)));
        }
    }
    private static ArtPreview.Scene hush(String name,double p,java.util.function.Consumer<ArtPreview.Ctx> setup,java.util.function.Consumer<ArtPreview.Ctx> camera){
        return new ArtPreview.Scene(name,(int)Math.floor(p*100),c->{setup.accept(c);var d=HushData.get(c.level);if(d.active){d.age=Math.max(0,(int)Math.round(p*d.duration)-CAPTURE_LAG);d.setDirty();}},camera,4);
    }

    // ------------------------------------------------------------------ stage
    static void build(ServerLevel l,BlockPos anchor){
        BlockPos s=site(anchor);
        for(int x=-24;x<=24;x++)for(int z=-22;z<=24;z++){
            for(int y=0;y<=16;y++)l.setBlock(s.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            l.setBlock(s.offset(x,-3,z),Blocks.DIRT.defaultBlockState(),2);
            l.setBlock(s.offset(x,-2,z),Blocks.DIRT.defaultBlockState(),2);
            boolean path=Math.abs(x-Math.round(2*Math.sin(z/5.0)))<=1&&z>-14;
            boolean pond=Math.hypot(x-9,(z-1)*1.3)<3.6;
            l.setBlock(s.offset(x,-1,z),(pond?Blocks.WATER:path?Blocks.DIRT_PATH:Blocks.GRASS_BLOCK).defaultBlockState(),2);
            if(!path&&!pond&&(x*7+z*13&7)==0){
                Block[] flowers={Blocks.POPPY,Blocks.DANDELION,Blocks.CORNFLOWER,Blocks.ALLIUM,Blocks.RED_TULIP,Blocks.OXEYE_DAISY,Blocks.SHORT_GRASS,Blocks.BLUE_ORCHID};
                l.setBlock(s.offset(x,0,z),flowers[Math.floorMod(x*3+z*5,flowers.length)].defaultBlockState(),2);
            }
        }
        tree(l,treeA(anchor));tree(l,treeB(anchor));
        // The cottage: stone footing, plank walls, a slab roof, its door facing the meadow, a window each side, a lantern inside.
        BlockPos h=site(anchor).offset(-12,0,-1);
        for(int x=0;x<=7;x++)for(int z=0;z<=6;z++)for(int y=0;y<=4;y++){
            boolean wall=x==0||x==7||z==0||z==6,corner=(x==0||x==7)&&(z==0||z==6);
            BlockPos p=h.offset(x,y,z);
            if(y==4)l.setBlock(p,Blocks.DARK_OAK_SLAB.defaultBlockState(),2);
            else if(wall)l.setBlock(p,(corner?Blocks.STRIPPED_OAK_LOG:y==0?Blocks.COBBLESTONE:Blocks.WHITE_TERRACOTTA).defaultBlockState(),2);
        }
        BlockPos d=door(anchor);
        l.setBlock(d,Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.NORTH).setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER),2);
        l.setBlock(d.above(),Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.NORTH).setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
        for(int[] w:new int[][]{{0,1,3},{0,2,3},{7,1,3},{7,2,3},{2,2,6},{5,2,6}})l.setBlock(h.offset(w[0],w[1],w[2]),Blocks.GLASS_PANE.defaultBlockState(),2);
        l.setBlock(h.offset(5,3,3),Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true),2);
        l.setBlock(h.offset(2,0,2),Blocks.RED_BED.defaultBlockState(),2);
        l.setBlock(h.offset(5,0,2),Blocks.BOOKSHELF.defaultBlockState(),2);l.setBlock(h.offset(6,0,2),Blocks.BOOKSHELF.defaultBlockState(),2);
        l.setBlock(h.offset(5,0,5),Blocks.CRAFTING_TABLE.defaultBlockState(),2);
        l.setBlock(d.offset(2,1,1),Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING,Direction.SOUTH),2);
        // A little colour to lose: a striped awning over the door and a flowering bush.
        for(int x=-1;x<=1;x++)l.setBlock(d.offset(x,2,1),(x==0?Blocks.RED_WOOL:Blocks.WHITE_WOOL).defaultBlockState(),2);
        for(int[] b:new int[][]{{3,0,-5},{4,0,-5},{3,1,-5}})l.setBlock(s.offset(b[0],b[1],b[2]),Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true),2);
        for(int y=0;y<2;y++)l.setBlock(s.offset(5,y,5),Blocks.SPRUCE_FENCE.defaultBlockState(),2);
        l.setBlock(s.offset(5,2,5),Blocks.LANTERN.defaultBlockState(),2);
    }
    private static void tree(ServerLevel l,BlockPos base){
        for(int y=0;y<6;y++)l.setBlock(base.above(y),Blocks.OAK_LOG.defaultBlockState(),2);
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)for(int y=4;y<=8;y++){
            double r=Math.sqrt(x*x+z*z+(y-6)*(y-6)*1.4);
            if(r<3.3&&!(x==0&&z==0&&y<6))l.setBlock(base.offset(x,y,z),Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true),2);
        }
    }

    // ------------------------------------------------------------------ scene setup (server thread)
    static void reset(ArtPreview.Ctx c){
        for(var e:com.google.common.collect.ImmutableList.copyOf(c.level.getAllEntities())){
            if(e instanceof HushProcession||e instanceof HushCoat)e.discard();
            else if(e instanceof net.minecraft.world.entity.item.ItemEntity i&&(i.getItem().is(Veilborne.DAYLIGHT_WAX.get())||i.getItem().is(Veilborne.HOLLOW_BELL.get())))i.discard();
        }
        c.level.getServer().setDifficulty(Difficulty.PEACEFUL,true);
        var d=HushData.get(c.level);
        if(!d.active)HushService.begin(c.level,site(c.anchor),720);
        d.seed=SEED;d.center=site(c.anchor);d.moon=false;d.variant=HushData.ALL;
        d.flags=HushData.PROCESSION|HushData.COAT|HushData.BELL|HushData.FEATHER;
        d.bell=BlockPos.ZERO;d.bellTop=BlockPos.ZERO;d.feather=BlockPos.ZERO;d.bellAt=d.foundAt=d.featherAt=0;
        d.setDirty();
        c.level.setDayTime(3000);
        closeDoor(c);
        c.player.setGameMode(GameType.SPECTATOR);
        BlockPos s=site(c.anchor);
        c.player.teleportTo(c.level,s.getX()+.5,s.getY(),s.getZ()+8.5,180,0);
    }
    private static void procession(ArtPreview.Ctx c,HushProcession.State state,boolean answer){
        var p=Veilborne.HUSH_PROCESSION.get().create(c.level);
        BlockPos s=site(c.anchor);
        // Walking east to west across the meadow in front of the camera.
        p.setRoute(s.offset(24,0,10),Math.PI,60,77);
        p.preview(26,state);
        c.level.addFreshEntity(p);
        if(answer)p.answer(Vec3.atBottomCenterOf(s).add(4,0,11));
    }
    private static void processionClose(ArtPreview.Ctx c){
        HushProcession p=null;
        if(c.mc.level!=null)for(var e:c.mc.level.entitiesForRendering())if(e instanceof HushProcession h)p=h;
        if(p==null)return;
        Vec3 at=p.member(p.renderPath(1),HushProcession.MEMBERS[1][0],0);
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        place(c,at.add(-2.6,1.7,2.4));lookAt(c,at.add(0,1.5,0));
    }
    private static void coat(ArtPreview.Ctx c,HushCoat.State state,int clock){
        var coat=Veilborne.HUSH_COAT.get().create(c.level);
        BlockPos d=door(c.anchor);
        coat.place(Vec3.atBottomCenterOf(d.south()).add(0,.02,.02),d);
        c.level.addFreshEntity(coat);
        coat.debugState(state,clock);
    }
    /** Runs on the server {@code before} ticks ahead of the capture, so a short state is caught in the middle. */
    private static void atTick(ArtPreview.Ctx c,int before,java.util.function.Consumer<ServerLevel> action){
        if(c.tick==ArtPreview.CAPTURE_AT-before){var server=c.mc.getSingleplayerServer();if(server!=null)server.execute(()->action.accept(server.overworld()));}
    }
    private static void coatState(ArtPreview.Ctx c,HushCoat.State state,int before){
        atTick(c,before,l->{for(var e:l.getAllEntities())if(e instanceof HushCoat coat)coat.debugState(state,0);});
    }
    private static void openDoor(ArtPreview.Ctx c){
        BlockPos d=door(c.anchor);
        for(BlockPos b:new BlockPos[]{d,d.above()}){var st=c.level.getBlockState(b);if(st.getBlock() instanceof DoorBlock)c.level.setBlock(b,st.setValue(DoorBlock.OPEN,true),2);}
    }
    private static void closeDoor(ArtPreview.Ctx c){
        BlockPos d=door(c.anchor);
        for(BlockPos b:new BlockPos[]{d,d.above()}){var st=c.level.getBlockState(b);if(st.getBlock() instanceof DoorBlock)c.level.setBlock(b,st.setValue(DoorBlock.OPEN,false),2);}
    }
    private static void bell(ArtPreview.Ctx c,boolean found){
        var d=HushData.get(c.level);
        BlockPos t=treeA(c.anchor);
        d.bell=t.offset(2,1,1);d.bellTop=t.offset(2,4,1);d.bellAt=Math.round(4.4f/12*d.duration)-CAPTURE_LAG-60;
        if(found){d.flags|=HushData.FOUND;d.foundAt=Math.round(4.4f/12*d.duration)-CAPTURE_LAG-400;}
        d.setDirty();
    }
    private static void feather(ArtPreview.Ctx c){
        var d=HushData.get(c.level);
        d.feather=site(c.anchor);d.featherAt=0;d.setDirty();
    }
    static void keepsakes(ArtPreview.Ctx c){
        reset(c);c.player.setGameMode(GameType.CREATIVE);
        var inv=c.player.getInventory();inv.clearContent();
        net.minecraft.world.item.Item[] kit={Veilborne.JOURNAL.get(),Veilborne.DAYLIGHT_WAX.get(),Veilborne.HOLLOW_BELL.get(),Veilborne.STOPPED_CLOCK.get(),
            Veilborne.STILL_CANDLE_ITEM.get(),Veilborne.SILENT_LANTERN_ITEM.get(),Veilborne.STILL_FEATHER_ITEM.get(),Items.LANTERN};
        for(int i=0;i<kit.length;i++)inv.setItem(i,new ItemStack(kit[i]));
        ItemStack held=new ItemStack(Veilborne.HOLLOW_BELL.get());HushItem.setRecording(held,0);inv.setItem(8,held);
        inv.selected=5;
        BlockPos s=site(c.anchor);
        c.level.setBlock(s.offset(-1,0,-4),Veilborne.STILL_CANDLE.get().defaultBlockState(),3);
        c.level.setBlock(s.offset(1,0,-4),Veilborne.SILENT_LANTERN.get().defaultBlockState(),3);
        c.level.setBlock(s.offset(0,0,-5),Veilborne.STILL_FEATHER.get().defaultBlockState(),3);
        c.level.setBlock(s.offset(-2,0,-5),Blocks.POPPY.defaultBlockState(),3);
        c.player.teleportTo(c.level,s.getX()+.5,s.getY(),s.getZ()-1,180,0);
    }
    private static void lantern(ArtPreview.Ctx c){
        c.player.setGameMode(GameType.CREATIVE);
        c.player.getInventory().clearContent();
        c.player.getInventory().setItem(0,new ItemStack(Veilborne.SILENT_LANTERN_ITEM.get()));c.player.getInventory().selected=0;
    }

    // ------------------------------------------------------------------ cameras (client)
    private static Vec3 eye(ArtPreview.Ctx c,Vec3 offset){return Vec3.atBottomCenterOf(site(c.anchor)).add(0,1.62,0).add(offset);}
    /** Toward the black sun, from the meadow: straight at it, or {@code off} degrees below it so the land is in frame. */
    private static void skyShot(ArtPreview.Ctx c,boolean close,double off){
        if(HushClient.state==null)return;
        Vec3 e=eye(c,new Vec3(0,0,6));
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);place(c,e);
        Vec3 b=HushClient.black(1);
        double pitch=Math.toDegrees(Math.asin(Math.max(-1,Math.min(1,b.y))))-off*.5;
        lookRaw(c,(float)Math.toDegrees(Math.atan2(-b.x,b.z)),(float)-pitch);
        c.mc.options.fov().set(close?45:70);
    }
    private static void lineShot(ArtPreview.Ctx c){
        if(HushClient.state==null)return;
        Vec3 e=eye(c,new Vec3(0,1,6));
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);place(c,e);
        Vec3 base=HushClient.lineBase();
        lookAt(c,new Vec3(base.x,e.y+60,base.z));
    }
    private static void flash(ArtPreview.Ctx c){
        if(c.tick==ArtPreview.CAPTURE_AT-10){
            var p=c.mc.player;
            HushClient.flashes.add(new HushClient.Flash(p.getEyePosition().add(p.getLookAngle().scale(.6)).add(0,-.25,0),HushService.FLASH_REACH,HushClient.ticks));
        }
    }
    static void journal(ArtPreview.Ctx c,int spread){
        c.mc.options.hideGui=true;
        aim(c,new Vec3(0,1.7,8),new Vec3(0,1,-20));
        if(!(c.mc.screen instanceof TideJournalScreen)){var screen=new TideJournalScreen();c.mc.setScreen(screen);screen.showChapter(4);screen.showSpread(spread);}
    }
    private static void aim(ArtPreview.Ctx c,Vec3 eye,Vec3 focus){
        if(c.mc.options.getCameraType()!=CameraType.THIRD_PERSON_BACK)c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        c.mc.options.fov().set(70);
        Vec3 base=Vec3.atBottomCenterOf(site(c.anchor));
        place(c,base.add(eye));lookAt(c,base.add(focus));
    }
    private static void place(ArtPreview.Ctx c,Vec3 eye){
        var p=c.mc.player;double y=eye.y-p.getEyeHeight();
        p.setPos(eye.x,y,eye.z);p.xo=p.xOld=eye.x;p.yo=p.yOld=y;p.zo=p.zOld=eye.z;
    }
    private static void lookAt(ArtPreview.Ctx c,Vec3 focus){
        var p=c.mc.player;Vec3 d=focus.subtract(p.getEyePosition());
        lookRaw(c,(float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));
    }
    private static void lookRaw(ArtPreview.Ctx c,float yaw,float pitch){
        var p=c.mc.player;p.setYRot(yaw);p.setXRot(pitch);p.yRotO=yaw;p.xRotO=pitch;p.setYHeadRot(yaw);p.yHeadRotO=yaw;p.yBodyRot=yaw;p.yBodyRotO=yaw;
    }
    private HushPreview(){}
}
