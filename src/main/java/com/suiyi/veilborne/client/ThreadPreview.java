package com.suiyi.veilborne.client;

import com.suiyi.veilborne.*;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;

/**
 * 绯线缝世 scenes for the art harness (-PtidePreview=art -PtidePreviewScenes=thread). They play around a meadow built west of
 * the other stages: a rock outcrop and two trees for the gold seams, a small timber hut with a window for the inside, a
 * path, and open ground where the needle comes down. The event seed and every site are fixed, so the seam, the stitches
 * and the land stitch are always in the same place. Each scene sets the weather's age exactly: the harness captures 90
 * ticks after its setup, so ages are set that much early.
 */
final class ThreadPreview {
    static final long SEED=20261126L;
    static BlockPos site(BlockPos anchor){return anchor.offset(-72,0,0);}
    /** Strike sites and the land stitch's feet, relative to the meadow. */
    static BlockPos strike(BlockPos anchor,int i){return site(anchor).offset(new int[]{9,-10,4}[i],0,new int[]{6,5,-11}[i]);}
    static BlockPos footA(BlockPos anchor){return site(anchor).offset(14,0,12);}
    static BlockPos footB(ServerLevel l,BlockPos anchor){
        BlockPos b=site(anchor).offset(14+(int)(ThreadTimeline.LAND_HALF*2*Math.cos(Math.toRadians(-35))),0,12+(int)(ThreadTimeline.LAND_HALF*2*Math.sin(Math.toRadians(-35))));
        int y=l.hasChunk(b.getX()>>4,b.getZ()>>4)?Surface.top(l,b.getX(),b.getZ()):site(anchor).getY();
        return new BlockPos(b.getX(),Math.max(site(anchor).getY()-12,Math.min(site(anchor).getY()+12,y)),b.getZ());
    }
    static final int CAPTURE_LAG=90;

    static void scenes(List<ArtPreview.Scene> out){
        double span=ThreadTimeline.span();
        out.add(thread("thread_omen",at(.035),ThreadPreview::reset,c->aim(c,new Vec3(-4,1.8,-6),new Vec3(-13,4,-9))));
        out.add(thread("thread_hairline",at(.055),ThreadPreview::reset,ThreadPreview::seamShot));
        out.add(thread("thread_tear",at(1.9/13),ThreadPreview::reset,ThreadPreview::seamShot));
        out.add(thread("thread_wound",at(2.9/13),ThreadPreview::reset,ThreadPreview::seamShot));
        out.add(thread("thread_wound_wide",at(2.9/13),ThreadPreview::reset,c->look(c,Vec3.ZERO,yawTo(c,0),-35)));
        out.add(thread("thread_needle_in",at(ThreadTimeline.STITCH_START+.2*span),ThreadPreview::reset,c->needleShot(c)));
        out.add(thread("thread_needle_turn",at(ThreadTimeline.STITCH_START+3.3*span),ThreadPreview::reset,c->needleShot(c)));
        out.add(thread("thread_needle_across",at(ThreadTimeline.STITCH_START+6.55*span),ThreadPreview::reset,c->needleShot(c)));
        out.add(thread("thread_sutures",at(ThreadTimeline.STITCH_START+9.9*span),ThreadPreview::reset,ThreadPreview::seamShot));
        out.add(thread("thread_day",at(ThreadTimeline.STITCH_START+5.4*span),c->{reset(c);c.level.setDayTime(6000);},ThreadPreview::seamShot));
        out.add(thread("thread_horizon",at(ThreadTimeline.STITCH_START+4.05*span),ThreadPreview::reset,c->look(c,Vec3.ZERO,yawTo(c,1)+60,-4)));
        int k2=ThreadTimeline.STRIKE_STITCH[0];
        double sp=ThreadTimeline.strikeP(0);
        out.add(thread("thread_strike_warn",sp-100/(double)ThreadTimeline.DEFAULT_DURATION,ThreadPreview::reset,c->aim(c,new Vec3(3,2.6,-4),new Vec3(9,0,6))));
        out.add(thread("thread_strike_fall",sp-12/(double)ThreadTimeline.DEFAULT_DURATION,ThreadPreview::reset,c->aim(c,new Vec3(0,1.8,-6),new Vec3(9,8,6))));
        out.add(thread("thread_strike_after",sp+30/(double)ThreadTimeline.DEFAULT_DURATION,ThreadPreview::reset,c->aim(c,new Vec3(0,1.8,-4),new Vec3(9,6,6))));
        out.add(thread("thread_strike_thread",sp+400/(double)ThreadTimeline.DEFAULT_DURATION,ThreadPreview::reset,c->aim(c,new Vec3(4,1.8,-2),new Vec3(9,40,6))));
        out.add(thread("thread_walker_descend",sp+(60+70)/(double)ThreadTimeline.DEFAULT_DURATION,c->{reset(c);walker(c,NeedleWalker.State.DESCEND);},ThreadPreview::walkerShot));
        out.add(thread("thread_walker_knot",sp+800/(double)ThreadTimeline.DEFAULT_DURATION,c->{reset(c);walker(c,NeedleWalker.State.KNOT);},c->aim(c,new Vec3(6,2,2.5),new Vec3(9,.3,6))));
        out.add(thread("thread_walker_knot_close",sp+800/(double)ThreadTimeline.DEFAULT_DURATION,c->{reset(c);walker(c,NeedleWalker.State.KNOT);},c->walkerShot(c,2.6,1.6,-.6)));
        out.add(thread("thread_walker_alarm",sp+800/(double)ThreadTimeline.DEFAULT_DURATION,c->{reset(c);walker(c,NeedleWalker.State.ALARM);},c->aim(c,new Vec3(4.5,2.2,1),new Vec3(9,.6,6))));
        out.add(thread("thread_seams",at(.3),ThreadPreview::reset,c->aim(c,new Vec3(-7,1.7,-4),new Vec3(-12,1.6,-8))));
        out.add(thread("thread_indoor",at(.38),ThreadPreview::reset,c->aim(c,new Vec3(-4,1.62,9.5),new Vec3(-1.5,2.4,11))));
        out.add(thread("thread_ends",at(.45),c->{reset(c);ends(c);},c->aim(c,new Vec3(0,1.8,-5),new Vec3(1,5,1))));
        out.add(thread("thread_descend",sew(.075),ThreadPreview::reset,c->look(c,new Vec3(-4,0,-4),yawTo(c,2),-38)));
        out.add(thread("thread_arrive",sew(.118),ThreadPreview::reset,c->archShot(c,-24,6)));
        out.add(thread("thread_arch",sew(.2),ThreadPreview::reset,c->archShot(c,-30,4)));
        out.add(thread("thread_needle_close",sew(.14),ThreadPreview::reset,c->aim(c,new Vec3(3,1.8,1),new Vec3(14,16,12))));
        out.add(thread("thread_arch_foot",sew(.25),ThreadPreview::reset,c->aim(c,new Vec3(4,1.8,2),new Vec3(14,14,12))));
        out.add(thread("thread_rise",sew(.35),ThreadPreview::reset,c->archShot(c,-30,12)));
        out.add(thread("thread_hand",sew(.47),ThreadPreview::reset,c->look(c,Vec3.ZERO,yawTo(c,0),-50)));
        out.add(thread("thread_hand_land",sew(.49),ThreadPreview::reset,c->aim(c,new Vec3(-20,30,-20),new Vec3(20,0,20))));
        out.add(thread("thread_tug",sew(.54),c->{reset(c);animals(c);},c->{c.mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);aim(c,new Vec3(0,1.8,0),new Vec3(4,1,6));}));
        out.add(thread("thread_moment",sew(.56),c->{reset(c);animals(c);},c->aim(c,new Vec3(-2,1.8,-3),new Vec3(4,1.5,6))));
        out.add(thread("thread_loop",sew(.72),ThreadPreview::reset,c->aim(c,new Vec3(4,1.8,0),new Vec3(14,30,12))));
        out.add(thread("thread_land_seam",sew(.72),ThreadPreview::reset,c->aim(c,new Vec3(12,6,4),new Vec3(30,0,4))));
        out.add(thread("thread_knot",ThreadTimeline.KNOT+.003,ThreadPreview::reset,ThreadPreview::middleShot));
        out.add(thread("thread_pocket",ThreadTimeline.POCKET_OPEN+.02,c->{reset(c);pocket(c,false);},c->aim(c,new Vec3(10,2.4,5),new Vec3(15,1.2,11))));
        out.add(thread("thread_pocket_tune",ThreadTimeline.POCKET_OPEN+.03,c->{reset(c);pocket(c,true);},c->aim(c,new Vec3(10,2.6,5),new Vec3(15,1,11))));
        out.add(thread("thread_band",at(.965),ThreadPreview::reset,ThreadPreview::seamShot));
        out.add(thread("thread_band_day",at(.935),c->{reset(c);c.level.setDayTime(5500);},ThreadPreview::seamShot));
        out.add(thread("thread_keepsake",at(.944),c->{reset(c);var d=ThreadData.get(c.level);d.keepsake=site(c.anchor).offset(1,0,-3);d.keepsakeAt=(int)Math.round(.944*d.duration)-40;d.flags|=ThreadData.KEEPSAKE;},c->aim(c,new Vec3(-3,2.2,-8),new Vec3(1.5,4,-3))));
        out.add(thread("thread_lamps",at(.4),c->{reset(c);lamps(c);},c->aim(c,new Vec3(-3,2.5,-9),new Vec3(1,1,-2))));
        out.add(thread("thread_items_hotbar",at(.4),ThreadPreview::keepsakes,c->{c.mc.options.hideGui=false;aim(c,new Vec3(-1.5,1.9,-4.5),new Vec3(1.5,.3,-1));}));
        out.add(thread("thread_blocks",at(.4),ThreadPreview::keepsakes,c->{c.mc.options.hideGui=true;aim(c,new Vec3(-1.2,1.7,-4.2),new Vec3(1.5,.8,-1));}));
        for(int i=0;i<4;i++){int spread=i;
            out.add(thread("thread_journal_"+i,at(.45),c->{reset(c);c.player.getPersistentData().putInt(TideService.RECORD,1|MoonService.FIRST|MirrorService.FIRST|ThreadService.FIRST|ThreadService.SEWN|ThreadService.FORESIGHT|ThreadService.STRING);},c->journal(c,spread)));}
        for(String round:new String[]{"a","b"}){
            out.add(thread("perf_thread_sky_"+round,at(ThreadTimeline.STITCH_START+4.3*span),ThreadPreview::reset,ThreadPreview::seamShot));
            out.add(thread("perf_thread_sew_"+round,sew(.54),c->{reset(c);animals(c);},c->aim(c,new Vec3(4,1.8,0),new Vec3(14,20,12))));
        }
    }
    /** Weather progress p expressed as the harness's stage percent; the exact age is set again in reset. */
    private static double at(double p){return p;}
    private static double sew(double q){return ThreadTimeline.pAtSew(q);}
    private static ArtPreview.Scene thread(String name,double p,java.util.function.Consumer<ArtPreview.Ctx> setup,java.util.function.Consumer<ArtPreview.Ctx> camera){
        return new ArtPreview.Scene(name,(int)Math.floor(p*100),c->{setup.accept(c);var d=ThreadData.get(c.level);d.age=Math.max(0,(int)Math.round(p*d.duration)-CAPTURE_LAG);d.setDirty();},camera,3);
    }

    // ------------------------------------------------------------------ stage
    static void build(ServerLevel l,BlockPos anchor){
        BlockPos s=site(anchor);
        for(int x=-22;x<=22;x++)for(int z=-20;z<=22;z++){
            for(int y=0;y<=16;y++)l.setBlock(s.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            l.setBlock(s.offset(x,-3,z),Blocks.DIRT.defaultBlockState(),2);
            l.setBlock(s.offset(x,-2,z),Blocks.DIRT.defaultBlockState(),2);
            boolean path=Math.abs(z-Math.round(1.5*Math.sin(x/6.0)))<=1&&x>-6;
            l.setBlock(s.offset(x,-1,z),(path?Blocks.DIRT_PATH:Blocks.GRASS_BLOCK).defaultBlockState(),2);
            if(!path&&(x*7+z*13&15)==0)l.setBlock(s.offset(x,0,z),((x+z)&1)==0?Blocks.SHORT_GRASS.defaultBlockState():Blocks.POPPY.defaultBlockState(),2);
        }
        // A rock outcrop to the north-west, its faces bare for the gold seams.
        for(int x=-16;x<=-9;x++)for(int z=-13;z<=-7;z++){
            int h=(int)(4.5-Math.hypot(x+12.5,(z+10)*1.2)*.9+((x*3+z*5)&1));
            for(int y=0;y<h;y++)l.setBlock(s.offset(x,y,z),(y+x+z)%4==0?Blocks.ANDESITE.defaultBlockState():Blocks.STONE.defaultBlockState(),2);
        }
        for(int[] t:new int[][]{{-14,2},{-6,-14},{17,-8}})tree(l,s.offset(t[0],0,t[1]));
        // A timber hut: log posts, plank walls, open beams under a slab roof, a window east and a door west.
        for(int x=-7;x<=-1;x++)for(int z=8;z<=13;z++)for(int y=0;y<=4;y++){
            boolean wall=x==-7||x==-1||z==8||z==13,post=(x==-7||x==-1)&&(z==8||z==13);
            BlockPos p=s.offset(x,y,z);
            if(y==4)l.setBlock(p,Blocks.SPRUCE_SLAB.defaultBlockState(),2);
            else if(y==3&&!wall&&(z==10||z==11))l.setBlock(p,Blocks.STRIPPED_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,Direction.Axis.X),2);
            else if(wall)l.setBlock(p,(post?Blocks.OAK_LOG:Blocks.OAK_PLANKS).defaultBlockState(),2);
        }
        l.setBlock(s.offset(-1,1,10),Blocks.GLASS_PANE.defaultBlockState(),2);l.setBlock(s.offset(-1,2,10),Blocks.GLASS_PANE.defaultBlockState(),2);
        l.setBlock(s.offset(-1,1,11),Blocks.GLASS_PANE.defaultBlockState(),2);l.setBlock(s.offset(-1,2,11),Blocks.GLASS_PANE.defaultBlockState(),2);
        l.setBlock(s.offset(-4,0,8),Blocks.AIR.defaultBlockState(),2);l.setBlock(s.offset(-4,1,8),Blocks.AIR.defaultBlockState(),2);
        l.setBlock(s.offset(-3,2,12),Blocks.LANTERN.defaultBlockState(),2);
        // A fence post with a lamp by the path, and a well of cobblestone.
        for(int y=0;y<3;y++)l.setBlock(s.offset(3,y,-2),Blocks.SPRUCE_FENCE.defaultBlockState(),2);
        l.setBlock(s.offset(3,3,-2),Blocks.LANTERN.defaultBlockState(),2);
        for(int x=-3;x<=-1;x++)for(int z=-4;z<=-2;z++)if(x!=-2||z!=-3)l.setBlock(s.offset(x,0,z),Blocks.COBBLESTONE_WALL.defaultBlockState(),2);
    }
    private static void tree(ServerLevel l,BlockPos base){
        for(int y=0;y<7;y++)l.setBlock(base.above(y),Blocks.DARK_OAK_LOG.defaultBlockState(),2);
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)for(int y=4;y<=8;y++){
            double r=Math.sqrt(x*x+z*z+(y-6)*(y-6)*1.3);
            if(r<3.4&&!(x==0&&z==0&&y<7))l.setBlock(base.offset(x,y,z),Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true),2);
        }
    }

    // ------------------------------------------------------------------ scene setup (server thread)
    static void reset(ArtPreview.Ctx c){
        for(var e:c.level.getAllEntities()){
            if(e instanceof NeedleWalker||e instanceof ThreadSpirit)e.discard();
            else if(e instanceof net.minecraft.world.entity.item.ItemEntity i&&i.getItem().is(Veilborne.SCARLET_THREAD.get()))i.discard();
        }
        c.level.getServer().setDifficulty(Difficulty.PEACEFUL,true);
        var d=ThreadData.get(c.level);
        d.seed=SEED;d.center=site(c.anchor);
        d.flags=ThreadData.LAND|ThreadData.WALKER|ThreadData.KEEPSAKE;
        for(int i=0;i<ThreadData.STRIKES;i++)d.strikes[i]=strike(c.anchor,i);
        d.landA=footA(c.anchor);d.landB=footB(c.level,c.anchor);d.landAt=0;
        d.pocket=BlockPos.ZERO;d.anchor=BlockPos.ZERO;d.keepsake=BlockPos.ZERO;d.tuneAt=d.pocketAt=0;
        d.setDirty();
        c.level.setDayTime(18000);
        c.player.setGameMode(GameType.SPECTATOR);
        BlockPos s=site(c.anchor);
        c.player.teleportTo(c.level,s.getX()+.5,s.getY(),s.getZ()+.5,180,0);
    }
    /** Close on the walker wherever it is: it is 34 blocks up its thread while it climbs, and on the ground at its knot. */
    private static void walkerShot(ArtPreview.Ctx c){walkerShot(c,4.5,.4,1);}
    private static void walkerShot(ArtPreview.Ctx c,double dist,double rise,double side){
        NeedleWalker w=null;
        if(c.mc.level!=null)for(var e:c.mc.level.entitiesForRendering())if(e instanceof NeedleWalker n)w=n;
        if(w==null)return;
        Vec3 at=w.getPosition(1),d=new Vec3(side,0,-1).normalize();
        c.mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        place(c,at.add(d.x*dist,rise,d.z*dist));
        lookAt(c,at.add(0,.3,0));
    }
    private static void walker(ArtPreview.Ctx c,NeedleWalker.State state){
        var d=ThreadData.get(c.level);
        var w=Veilborne.WALKER.get().create(c.level);
        BlockPos foot=strike(c.anchor,0);
        w.plan(foot,ThreadTimeline.strikeTop(d.seam(),0,d.progress()).subtract(Vec3.atBottomCenterOf(foot)).normalize());
        c.level.addFreshEntity(w);
        if(state!=NeedleWalker.State.DESCEND){w.moveTo(foot.getX()+.5,foot.getY(),foot.getZ()+.5,200,0);w.debugState(state);}
    }
    private static void ends(ArtPreview.Ctx c){
        BlockPos s=site(c.anchor);
        for(int i=0;i<4;i++){
            var item=new net.minecraft.world.entity.item.ItemEntity(c.level,s.getX()+.5+i*1.3-1,s.getY()+3+i*1.4,s.getZ()+1.5+(i%2),new ItemStack(Veilborne.SCARLET_THREAD.get()));
            item.setNoGravity(true);item.setDeltaMovement(0,0,0);item.setNeverPickUp();c.level.addFreshEntity(item);
        }
    }
    private static void animals(ArtPreview.Ctx c){
        BlockPos s=site(c.anchor);
        for(int i=0;i<3;i++){
            var sheep=net.minecraft.world.entity.EntityType.SHEEP.create(c.level);
            if(sheep==null)continue;
            sheep.moveTo(s.getX()+3+i*1.6,s.getY(),s.getZ()+4+i,120+i*30,0);sheep.setNoAi(true);c.level.addFreshEntity(sheep);
        }
        c.player.setGameMode(GameType.CREATIVE);
    }
    private static void pocket(ArtPreview.Ctx c,boolean tuned){
        var d=ThreadData.get(c.level);
        BlockPos p=footA(c.anchor).offset(-2,0,-2);
        d.flags|=ThreadData.POCKET;d.pocket=p;d.pocketAt=d.age-200;
        if(tuned){
            BlockPos a=p.offset(1,0,0);
            c.level.setBlock(a,Veilborne.TUNING_ANCHOR.get().defaultBlockState(),3);
            d.flags|=ThreadData.TUNED;d.anchor=a;d.tuneAt=d.age-60;
        }
        d.setDirty();
    }
    private static void lamps(ArtPreview.Ctx c){
        BlockPos s=site(c.anchor);
        for(int[] o:new int[][]{{-2,-3},{2,-1},{5,-4},{0,2}})c.level.setBlock(s.offset(o[0],0,o[1]),Veilborne.LOOM_LANTERN.get().defaultBlockState(),3);
        c.level.setBlock(s.offset(-4,0,-1),Veilborne.TUNING_ANCHOR.get().defaultBlockState(),3);
    }
    static void keepsakes(ArtPreview.Ctx c){
        reset(c);c.player.setGameMode(GameType.CREATIVE);
        var inv=c.player.getInventory();inv.clearContent();
        net.minecraft.world.item.Item[] kit={Veilborne.JOURNAL.get(),Veilborne.SCARLET_THREAD.get(),Veilborne.SEAM_SILK.get(),Veilborne.BINDING_CHARM.get(),Veilborne.HEAVEN_NEEDLE.get(),
            Veilborne.TUNING_ANCHOR_ITEM.get(),Veilborne.LOOM_LANTERN_ITEM.get(),Veilborne.ROBE_SCRAP_ITEM.get()};
        for(int i=0;i<kit.length;i++)inv.setItem(i,new ItemStack(kit[i]));
        inv.selected=2;
        BlockPos s=site(c.anchor);
        c.level.setBlock(s.offset(1,0,1),Veilborne.LOOM_LANTERN.get().defaultBlockState(),3);
        c.level.setBlock(s.offset(3,0,0),Veilborne.LOOM_LANTERN.get().defaultBlockState(),3);
        c.level.setBlock(s.offset(-1,0,0),Veilborne.TUNING_ANCHOR.get().defaultBlockState(),3);
        for(int y=0;y<3;y++)c.level.setBlock(s.offset(2,y,2),Blocks.STONE_BRICKS.defaultBlockState(),3);
        c.level.setBlock(s.offset(2,1,1),Veilborne.ROBE_SCRAP.get().defaultBlockState().setValue(ThreadBlocks.Scrap.FACING,Direction.NORTH),3);
        c.player.teleportTo(c.level,s.getX()+.5,s.getY(),s.getZ()-4,180,0);
    }

    // ------------------------------------------------------------------ cameras (client)
    private static Vec3 eye(ArtPreview.Ctx c,Vec3 offset){return Vec3.atBottomCenterOf(site(c.anchor)).add(0,1.62,0).add(offset);}
    /** Yaw toward: 0 the seam's middle, 1 the current stitch, 2 the land stitch. */
    private static float yawTo(ArtPreview.Ctx c,int what){
        Vec3 e=eye(c,Vec3.ZERO),t;
        var st=ThreadClient.state;
        if(st==null)return 180;
        var seam=st.seam();
        if(what==2&&st.hasLand())t=st.footA().lerp(st.footB(),.3);
        else if(what==1){var n=ThreadClient.needle(0);t=n==null?ThreadTimeline.middle(seam):n.tipPoint();}
        else t=ThreadTimeline.middle(seam);
        Vec3 d=t.subtract(e);
        return (float)Math.toDegrees(Math.atan2(-d.x,d.z));
    }
    static void seamShot(ArtPreview.Ctx c){
        var st=ThreadClient.state;
        if(st==null){look(c,Vec3.ZERO,180,-60);return;}
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        Vec3 e=eye(c,Vec3.ZERO);place(c,e);
        lookAt(c,ThreadTimeline.middle(st.seam()).lerp(e,.25));
    }
    static void middleShot(ArtPreview.Ctx c){
        var st=ThreadClient.state;
        if(st==null)return;
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        place(c,eye(c,Vec3.ZERO));lookAt(c,ThreadTimeline.middle(st.seam()));
        c.mc.options.fov().set(40);
    }
    static void needleShot(ArtPreview.Ctx c){
        var n=ThreadClient.needle(0);
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        Vec3 e=eye(c,Vec3.ZERO);place(c,e);
        if(n==null){seamShot(c);return;}
        lookAt(c,n.at(n.tip()-Math.PI/2));
    }
    /** The land stitch from beside it: standing off the line between the feet, looking across at the arch. */
    static void archShot(ArtPreview.Ctx c,double back,double up){
        var st=ThreadClient.state;
        if(st==null||!st.hasLand())return;
        Vec3 a=st.footA(),b=st.footB(),mid=a.lerp(b,.5),dir=b.subtract(a).normalize(),side=new Vec3(-dir.z,0,dir.x);
        Vec3 e=mid.add(side.scale(back*4.5)).add(0,up+1.6,0);
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);place(c,e);lookAt(c,mid.add(0,50,0));
    }
    static void journal(ArtPreview.Ctx c,int spread){
        c.mc.options.hideGui=true;
        look(c,Vec3.ZERO,180,-40);
        if(!(c.mc.screen instanceof TideJournalScreen)){var screen=new TideJournalScreen();c.mc.setScreen(screen);screen.showChapter(3);screen.showSpread(spread);}
    }
    static void look(ArtPreview.Ctx c,Vec3 offset,float yaw,float pitch){
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        place(c,eye(c,offset));lookRaw(c,yaw,pitch);
    }
    private static void aim(ArtPreview.Ctx c,Vec3 eye,Vec3 focus){
        if(c.mc.options.getCameraType()!=CameraType.THIRD_PERSON_BACK)c.mc.options.setCameraType(CameraType.FIRST_PERSON);
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
    private ThreadPreview(){}
}
