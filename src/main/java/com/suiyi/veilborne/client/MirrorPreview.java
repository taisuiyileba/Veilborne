package com.suiyi.veilborne.client;

import com.suiyi.veilborne.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;

/**
 * 镜天巡礼 scenes for the art harness (-PtidePreview=art -PtidePreviewScenes=mirror). They play around a small
 * village plot built beside the other stages: a cottage whose south wall gets the mirror's extra door, a pond, a
 * lamp post and a path, with the natural land left standing around it so the sky has something real to reflect.
 * The event seed is fixed, so the tower, the cracks and the facets are always in the same place.
 */
final class MirrorPreview {
    static final long SEED=20261003L;
    static BlockPos site(BlockPos anchor){return anchor.offset(64,0,0);}
    static BlockPos doorWall(BlockPos anchor){return site(anchor).offset(9,0,2);}

    static void scenes(List<ArtPreview.Scene> out){
        out.add(mirror("mirror_omen",2,MirrorPreview::reset,c->aim(c,new Vec3(-2.5,2.4,6.5),new Vec3(-5.5,-.2,6.5))));
        out.add(mirror("mirror_silver",12,MirrorPreview::reset,c->look(c,Vec3.ZERO,180,-58)));
        out.add(mirror("mirror_reflect",40,MirrorPreview::reset,c->look(c,Vec3.ZERO,180,-76)));
        out.add(mirror("mirror_house",42,MirrorPreview::reset,MirrorPreview::houseShot));
        out.add(mirror("mirror_tower",42,MirrorPreview::reset,MirrorPreview::towerShot));
        out.add(mirror("mirror_horizon",42,MirrorPreview::reset,c->look(c,Vec3.ZERO,-90,-16)));
        out.add(mirror("mirror_day",42,c->{reset(c);c.level.setDayTime(6000);},c->look(c,Vec3.ZERO,160,-62)));
        out.add(mirror("mirror_panes",35,MirrorPreview::reset,MirrorPreview::paneShot));
        out.add(mirror("mirror_crack",82,MirrorPreview::reset,c->look(c,Vec3.ZERO,200,-58)));
        out.add(mirror("mirror_shatter",93,MirrorPreview::reset,c->look(c,Vec3.ZERO,200,-48)));
        out.add(mirror("mirror_pilgrim_walk",58,MirrorPreview::reset,c->pilgrimShot(c,Vec3.ZERO,false)));
        out.add(mirror("mirror_pilgrim_near",61,MirrorPreview::reset,c->pilgrimShot(c,Vec3.ZERO,false)));
        out.add(mirror("mirror_pilgrim_gaze",64,MirrorPreview::reset,c->pilgrimShot(c,Vec3.ZERO,false)));
        out.add(mirror("mirror_pilgrim_day",64,c->{reset(c);c.level.setDayTime(5000);},c->pilgrimShot(c,Vec3.ZERO,false)));
        out.add(mirror("mirror_pilgrim_shadow",59,MirrorPreview::reset,c->pilgrimShot(c,new Vec3(0,14,0),true)));
        out.add(mirror("mirror_door_hint",45,c->{reset(c);prism(c);},c->{scope(c);aim(c,new Vec3(9.5,1.62,8),new Vec3(9.5,1,3));}));
        out.add(mirror("mirror_door",50,c->{reset(c);reveal(c,120);},c->aim(c,new Vec3(10.5,1.8,6.5),new Vec3(9.5,.9,3))));
        out.add(mirror("mirror_door_far",50,c->{reset(c);reveal(c,120);},c->aim(c,new Vec3(4,3,14),new Vec3(9.5,1.2,3))));
        out.add(mirror("mirror_frames",45,c->{reset(c);frames(c);},c->framesShot(c,1,0)));
        out.add(mirror("mirror_frames_wide",45,c->{reset(c);frames(c);},MirrorPreview::framesWide));
        out.add(mirror("mirror_frames_left",45,c->{reset(c);frames(c);},c->framesShot(c,0,0)));
        out.add(mirror("mirror_frames_right",45,c->{reset(c);frames(c);},c->framesShot(c,2,0)));
        out.add(mirror("mirror_frames_solved",45,c->{reset(c);frames(c).observe(framesEntity(c).present(),c.player);},MirrorPreview::framesWide));
        out.add(mirror("mirror_traveler",30,MirrorPreview::traveler,c->aim(c,new Vec3(-3,1.9,-8),new Vec3(2,1,-2))));
        out.add(mirror("mirror_traveler_point",30,c->traveler(c,24),c->{aim(c,new Vec3(-3,1.9,-8),new Vec3(2,1,-2));}));
        out.add(mirror("mirror_shard",45,c->{reset(c);shard(c,0);},c->aim(c,new Vec3(3,2.2,5),new Vec3(8.5,2.4,11.5))));
        out.add(mirror("mirror_shard_warn",45,c->{reset(c);shard(c,24);},c->aim(c,new Vec3(3,3.2,4),new Vec3(8.5,.8,11.5))));
        out.add(mirror("mirror_beasts",45,c->{reset(c);shard(c,0);c.level.getServer().setDifficulty(Difficulty.EASY,true);MirrorService.burst(c.level,MirrorData.get(c.level),0);},c->aim(c,new Vec3(4,2.8,7),new Vec3(8.5,.4,11.5))));
        out.add(mirror("mirror_gallery",45,MirrorPreview::gallery,c->galleryShot(c,new Vec3(.5,1.62,1.5),new Vec3(.5,1.4,24))));
        out.add(mirror("mirror_gallery_wall",45,MirrorPreview::gallery,c->galleryShot(c,new Vec3(1.8,1.62,9),new Vec3(-2,1.2,12))));
        out.add(mirror("mirror_gallery_pedestal",45,MirrorPreview::gallery,c->galleryShot(c,new Vec3(.5,1.62,19.5),new Vec3(-1.5,1.2,23.5))));
        // Walking down the gallery past the wall, each a fresh entry: the reflection must be lit in every chunk.
        for(String w:new String[]{"a","b","c"}){double lean=w.equals("b")?70:50;
            out.add(mirror("mirror_gallery_walk_"+w,45,MirrorPreview::gallery,c->galleryWalk(c,lean)));}
        // A glass beast walking up to the camera: head first.
        out.add(mirror("mirror_beast_walk",45,MirrorPreview::beastWalk,MirrorPreview::beastShot));
        out.add(mirror("mirror_indoor",42,MirrorPreview::reset,c->aim(c,new Vec3(8,1.62,-2),new Vec3(12,1.5,-2.5))));
        out.add(mirror("mirror_afterimage",40,c->{standing(c);BlockPos s=site(c.anchor);
            // A clear run across the lawn: nothing to stop the sprint.
            for(int x=-19;x<=19;x++)for(int z=-13;z<=-10;z++)for(int y=0;y<=8;y++)c.level.setBlock(s.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            c.player.teleportTo(c.level,s.getX()+3.5,s.getY(),s.getZ()-11.5,-90,0);c.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED,400,2,false,false));},c->{c.mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);lookRaw(c,-90,20);c.mc.options.keyLeft.setDown(c.tick>=50&&c.tick<=94);}));
        out.add(mirror("mirror_echo",40,c->standing(c),MirrorPreview::echoShot));
        out.add(mirror("mirror_lamps",40,c->{reset(c);lamps(c);},c->aim(c,new Vec3(-4,1.9,-6),new Vec3(-1,.6,-1.5))));
        out.add(mirror("mirror_items_hotbar",40,MirrorPreview::keepsakes,c->{c.mc.options.hideGui=false;aim(c,new Vec3(-1.5,1.9,-4.5),new Vec3(1.5,.3,-1));}));
        out.add(mirror("mirror_shard_block",40,MirrorPreview::keepsakes,c->{c.mc.options.hideGui=true;aim(c,new Vec3(9.5,1.6,4.3),new Vec3(9.5,1.5,3));}));
        out.add(mirror("mirror_keepsake",95,c->{reset(c);var d=MirrorData.get(c.level);d.flags&=~MirrorData.KEEPSAKE;d.keepsake=site(c.anchor).offset(1,0,-3);d.keepsakeAt=d.age-30;d.flags|=MirrorData.KEEPSAKE;},c->aim(c,new Vec3(-3,2.2,-8),new Vec3(1.5,4,-3))));
        for(int i=0;i<4;i++){int spread=i;
            out.add(mirror("mirror_journal_"+i,45,c->{reset(c);c.player.getPersistentData().putInt(TideService.RECORD,1|MoonService.FIRST|MirrorService.FIRST|MirrorService.PILGRIM|MirrorService.DOOR|MirrorService.FRAMES|MirrorService.KNOWN);},c->journal(c,spread)));}
        for(String round:new String[]{"a","b"}){
            out.add(mirror("perf_mirror_sky_"+round,42,MirrorPreview::reset,c->look(c,Vec3.ZERO,180,-50)));
            out.add(mirror("perf_mirror_pilgrim_"+round,59,MirrorPreview::reset,c->look(c,Vec3.ZERO,180,-50)));
        }
    }
    private static ArtPreview.Scene mirror(String name,int stage,java.util.function.Consumer<ArtPreview.Ctx> setup,java.util.function.Consumer<ArtPreview.Ctx> camera){
        return new ArtPreview.Scene(name,stage,setup,camera,2);
    }

    // ------------------------------------------------------------------ stage
    static void build(ServerLevel l,BlockPos anchor){
        BlockPos s=site(anchor);
        for(int x=-20;x<=20;x++)for(int z=-18;z<=20;z++){
            for(int y=0;y<=14;y++)l.setBlock(s.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            l.setBlock(s.offset(x,-3,z),Blocks.DIRT.defaultBlockState(),2);
            l.setBlock(s.offset(x,-2,z),Blocks.DIRT.defaultBlockState(),2);
            boolean path=Math.abs(x-Math.round(1.5*Math.sin(z/5.0)))<=1&&z<2;
            l.setBlock(s.offset(x,-1,z),(path?Blocks.DIRT_PATH:Blocks.GRASS_BLOCK).defaultBlockState(),2);
            if(!path&&(x*5+z*11&15)==0)l.setBlock(s.offset(x,0,z),Blocks.SHORT_GRASS.defaultBlockState(),2);
        }
        // A pond to the west.
        for(int x=-9;x<=-3;x++)for(int z=3;z<=10;z++)if(Math.hypot(x+6,(z-6.5)*.8)<3.6){
            l.setBlock(s.offset(x,-1,z),Blocks.WATER.defaultBlockState(),2);l.setBlock(s.offset(x,-2,z),Blocks.WATER.defaultBlockState(),2);l.setBlock(s.offset(x,0,z),Blocks.AIR.defaultBlockState(),2);
        }
        // A cottage: stone footing, plank walls, a real door on the west and windows east and north.
        for(int x=6;x<=12;x++)for(int z=-4;z<=2;z++)for(int y=-1;y<=3;y++){
            boolean wall=x==6||x==12||z==-4||z==2;
            BlockPos p=s.offset(x,y,z);
            if(y==-1)l.setBlock(p,Blocks.COBBLESTONE.defaultBlockState(),2);
            else if(wall)l.setBlock(p,(y==0?Blocks.STONE_BRICKS:(x==6||x==12)&&(z==-4||z==2)?Blocks.STRIPPED_SPRUCE_LOG:Blocks.SPRUCE_PLANKS).defaultBlockState(),2);
        }
        for(int x=5;x<=13;x++)for(int step=0;step<=3;step++){
            l.setBlock(s.offset(x,4+step,-5+step),Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.SOUTH).setValue(StairBlock.HALF,Half.BOTTOM),2);
            l.setBlock(s.offset(x,4+step,3-step),Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.NORTH).setValue(StairBlock.HALF,Half.BOTTOM),2);
        }
        for(int x=5;x<=13;x++)l.setBlock(s.offset(x,7,-1),Blocks.DEEPSLATE_TILES.defaultBlockState(),2);
        for(int z=-3;z<=1;z++)for(int y=4;y<=6;y++){
            if(y-4<=Math.min(z+4,2-z)-1){l.setBlock(s.offset(6,y,z),Blocks.SPRUCE_PLANKS.defaultBlockState(),2);l.setBlock(s.offset(12,y,z),Blocks.SPRUCE_PLANKS.defaultBlockState(),2);}
        }
        BlockState door=Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.WEST);
        l.setBlock(s.offset(6,0,-1),door.setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER),2);
        l.setBlock(s.offset(6,1,-1),door.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
        l.setBlock(s.offset(12,1,-1),Blocks.GLASS_PANE.defaultBlockState(),2);l.setBlock(s.offset(12,2,-1),Blocks.GLASS_PANE.defaultBlockState(),2);
        l.setBlock(s.offset(9,1,-4),Blocks.GLASS_PANE.defaultBlockState(),2);l.setBlock(s.offset(9,2,-4),Blocks.GLASS_PANE.defaultBlockState(),2);
        l.setBlock(s.offset(9,2,1),Blocks.LANTERN.defaultBlockState(),2);
        l.setBlock(s.offset(-2,0,0),Blocks.SPRUCE_FENCE.defaultBlockState(),2);l.setBlock(s.offset(-2,1,0),Blocks.SPRUCE_FENCE.defaultBlockState(),2);l.setBlock(s.offset(-2,2,0),Blocks.LANTERN.defaultBlockState(),2);
        for(int[] t:new int[][]{{-14,-12},{-16,-4},{15,-12},{16,8},{-15,14}})tree(l,s.offset(t[0],0,t[1]));
    }
    private static void tree(ServerLevel l,BlockPos base){
        for(int y=0;y<6;y++)l.setBlock(base.above(y),Blocks.BIRCH_LOG.defaultBlockState(),2);
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=3;y<=7;y++){
            double r=Math.sqrt(x*x+z*z+(y-5)*(y-5)*1.4);
            if(r<2.9&&!(x==0&&z==0&&y<6))l.setBlock(base.offset(x,y,z),Blocks.BIRCH_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true),2);
        }
    }

    // ------------------------------------------------------------------ scene setup (server thread)
    static void reset(ArtPreview.Ctx c){
        if(MirrorGallery.inGallery(c.player.level()))MirrorGallery.leave(c.player);
        for(var e:c.level.getAllEntities()){
            if(e instanceof MirrorFrames||e instanceof MirrorTraveler)e.discard();
            else if(e instanceof MirrorBeast b)b.discard();
        }
        c.level.getServer().setDifficulty(Difficulty.PEACEFUL,true);
        var d=MirrorData.get(c.level);
        d.flags=MirrorData.DOOR|MirrorData.PILGRIM|MirrorData.FRAMES|MirrorData.SHARDS|MirrorData.KEEPSAKE;
        d.door=doorWall(c.anchor);d.doorFace=Direction.SOUTH.get2DDataValue();d.doorAt=0;d.revealedAt=0;
        d.pilgrimOrigin=site(c.anchor);d.pilgrimYaw=(float)Math.toRadians(-20);d.pilgrimAt=0;
        d.shards=new BlockPos[]{BlockPos.ZERO,BlockPos.ZERO};d.shardWarn=new int[2];
        d.keepsake=BlockPos.ZERO;d.setDirty();
        c.level.setDayTime(18000);
        c.player.setGameMode(GameType.SPECTATOR);
        BlockPos s=site(c.anchor);
        c.player.teleportTo(c.level,s.getX()+.5,s.getY(),s.getZ()+.5,180,0);
    }
    static void standing(ArtPreview.Ctx c){
        reset(c);c.player.setGameMode(GameType.CREATIVE);
        BlockPos s=site(c.anchor);c.player.teleportTo(c.level,s.getX()+.5,s.getY(),s.getZ()-6.5,90,0);
    }
    private static void prism(ArtPreview.Ctx c){
        c.player.setGameMode(GameType.CREATIVE);
        c.player.getInventory().setItem(c.player.getInventory().selected,new ItemStack(Veilborne.PRISM.get()));
        BlockPos s=site(c.anchor);c.player.teleportTo(c.level,s.getX()+10,s.getY(),s.getZ()+8,180,0);
    }
    private static void reveal(ArtPreview.Ctx c,int ago){var d=MirrorData.get(c.level);d.flags|=MirrorData.REVEALED;d.revealedAt=d.age-ago;}
    private static MirrorFrames frames(ArtPreview.Ctx c){
        BlockPos s=site(c.anchor).offset(-6,0,-9);
        var f=Veilborne.FRAMES.get().create(c.level);
        f.setPos(s.getX()+.5,s.getY(),s.getZ()+.5);f.setup(Math.toRadians(-90),SEED);
        c.level.addFreshEntity(f);
        return f;
    }
    private static MirrorFrames framesEntity(ArtPreview.Ctx c){
        for(var e:c.level.getAllEntities())if(e instanceof MirrorFrames f)return f;
        return null;
    }
    static void traveler(ArtPreview.Ctx c){traveler(c,80);}
    /** A replay of {@code frames} ticks; short enough and it has walked on and turned to point by the capture. */
    static void traveler(ArtPreview.Ctx c,int frames){
        reset(c);
        BlockPos s=site(c.anchor);
        List<Vec3> path=new ArrayList<>();
        for(int i=0;i<frames;i++){double t=i/(frames-1.0);path.add(new Vec3(s.getX()-4+t*7,s.getY(),s.getZ()-5+Math.sin(t*3)*1.2));}
        var t=Veilborne.TRAVELER.get().create(c.level);
        t.plan(c.player.getUUID(),path,doorWall(c.anchor).south(),7);
        c.level.addFreshEntity(t);
    }
    private static void shard(ArtPreview.Ctx c,int warnedFor){
        var d=MirrorData.get(c.level);
        d.shards[0]=site(c.anchor).offset(8,0,11);d.shards[1]=BlockPos.ZERO;
        d.shardWarn[0]=warnedFor>0?d.age-warnedFor:0;d.flags|=MirrorData.SHARDS;d.flags&=~(MirrorData.SHARD_BURST_0|MirrorData.SHARD_BURST_1);d.setDirty();
    }
    static void gallery(ArtPreview.Ctx c){
        reset(c);
        c.player.setGameMode(GameType.CREATIVE);
        var d=MirrorData.get(c.level);d.flags|=MirrorData.REVEALED;
        MirrorGallery.enter(c.player,d);
    }
    private static void lamps(ArtPreview.Ctx c){
        BlockPos s=site(c.anchor);
        c.level.setBlock(s.offset(-1,0,-2),Veilborne.MIRROR_LANTERN.get().defaultBlockState().setValue(MirrorLanternBlock.FACING,Direction.NORTH),3);
        c.level.setBlock(s.offset(1,0,-1),Veilborne.MIRROR_LANTERN.get().defaultBlockState().setValue(MirrorLanternBlock.FACING,Direction.WEST),3);
    }
    static void keepsakes(ArtPreview.Ctx c){
        reset(c);c.player.setGameMode(GameType.CREATIVE);
        var inv=c.player.getInventory();inv.clearContent();
        net.minecraft.world.item.Item[] kit={Veilborne.JOURNAL.get(),Veilborne.PRISM.get(),Veilborne.ECHO_MIRROR.get(),Veilborne.MIRROR_SAND.get(),Veilborne.SKY_MIRROR.get(),Veilborne.MIRROR_LANTERN_ITEM.get(),Veilborne.SKY_SHARD_ITEM.get()};
        for(int i=0;i<kit.length;i++)inv.setItem(i,new ItemStack(kit[i]));
        inv.selected=1;
        BlockPos s=site(c.anchor);
        c.level.setBlock(s.offset(9,1,3),Veilborne.SKY_SHARD.get().defaultBlockState().setValue(SkyShardBlock.FACING,Direction.SOUTH),3);
        c.level.setBlock(s.offset(1,0,1),Veilborne.MIRROR_LANTERN.get().defaultBlockState().setValue(MirrorLanternBlock.FACING,Direction.NORTH),3);
        c.player.teleportTo(c.level,s.getX()+.5,s.getY(),s.getZ()-4,180,0);
    }

    // ------------------------------------------------------------------ cameras (client)
    private static Vec3 eye(ArtPreview.Ctx c,Vec3 offset){return Vec3.atBottomCenterOf(site(c.anchor)).add(0,1.62,0).add(offset);}
    static void look(ArtPreview.Ctx c,Vec3 offset,float yaw,float pitch){
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        place(c,eye(c,offset));lookRaw(c,yaw,pitch);
    }
    /** Look up at where the sky shows a real-space point. */
    private static void lookAtReflection(ArtPreview.Ctx c,Vec3 eye,Vec3 world){
        if(Double.isNaN(MirrorClient.mirrorH))return;
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        place(c,eye);
        lookAt(c,new Vec3(world.x,2*MirrorClient.mirrorH-world.y,world.z));
    }
    static void houseShot(ArtPreview.Ctx c){
        Vec3 door=Vec3.atCenterOf(doorWall(c.anchor));
        lookAtReflection(c,door.add(-3,1.2,26),door.add(0,2,0));
    }
    static void towerShot(ArtPreview.Ctx c){
        if(MirrorExtras.TOWER.isEmpty()){look(c,Vec3.ZERO,180,-60);return;}
        BlockPos b=MirrorExtras.towerBase;
        Vec3 site=Vec3.atBottomCenterOf(site(c.anchor));
        Vec3 dir=new Vec3(b.getX()-site.x,0,b.getZ()-site.z).normalize();
        lookAtReflection(c,Vec3.atBottomCenterOf(b).subtract(dir.scale(30)).add(0,1.6,0).with(net.minecraft.core.Direction.Axis.Y,site.y+1.62),Vec3.atBottomCenterOf(b).add(0,12,0));
    }
    /** Faces the nearest shard of mirror turning in the air. */
    static void paneShot(ArtPreview.Ctx c){
        Vec3 eye=eye(c,Vec3.ZERO);
        var panes=MirrorRenderer.panesNear(eye,MirrorClient.progress(0),1,MirrorClient.ticks/20.0);
        if(panes.isEmpty()){look(c,new Vec3(0,0,-6),0,-24);return;}
        MirrorRenderer.Pane best=panes.get(panes.size()-1);
        Vec3 to=best.center().subtract(eye).normalize();
        place(c,eye.subtract(to.scale(4)));lookAt(c,best.center());
    }
    static void pilgrimShot(ArtPreview.Ctx c,Vec3 offset,boolean down){
        var g=MirrorClient.pilgrim(0);
        if(g==null){look(c,offset,180,-60);return;}
        if(down){
            Vec3 eye=g.base().add(-24,20,-10);
            c.mc.options.setCameraType(CameraType.FIRST_PERSON);place(c,eye);lookAt(c,g.base());return;
        }
        double[] head=PilgrimModel.mirrorCenter(g.s(),g.gaze());
        Vec3 at=MirrorRenderer.pilgrimPoint(g,new double[]{head[0],44,head[2]});
        lookAtReflection(c,eye(c,offset),at);
    }
    private static void scope(ArtPreview.Ctx c){
        var p=c.mc.player;
        if(p!=null&&!p.isUsingItem()&&MirrorItem.isPrism(p.getMainHandItem()))p.startUsingItem(InteractionHand.MAIN_HAND);
    }
    static void framesShot(ArtPreview.Ctx c,int i,float unused){
        var f=clientFrames(c);if(f==null)return;
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        Vec3 spot=f.position().add(0,1.62,0);
        place(c,spot.add(f.normal(i).scale(-.6)));lookAt(c,f.glass(i).add(0,.1,0));
    }
    static void framesWide(ArtPreview.Ctx c){
        var f=clientFrames(c);if(f==null)return;
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        Vec3 back=f.position().subtract(f.axis().scale(3.2)).add(0,2.1,0);
        place(c,back);lookAt(c,f.position().add(f.axis().scale(2.4)).add(0,1.2,0));
    }
    private static MirrorFrames clientFrames(ArtPreview.Ctx c){
        if(c.mc.level==null)return null;
        for(var e:c.mc.level.entitiesForRendering())if(e instanceof MirrorFrames f)return f;
        return null;
    }
    static void galleryShot(ArtPreview.Ctx c,Vec3 eye,Vec3 focus){
        if(c.mc.level==null||!MirrorGallery.inGallery(c.mc.level))return;
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        BlockPos o=MirrorGallery.originAt(c.mc.player.getX());
        Vec3 base=Vec3.atLowerCornerOf(o);
        place(c,base.add(eye));lookAt(c,base.add(focus));
    }
    private static int unlitAtStart;
    static void galleryWalk(ArtPreview.Ctx c,double lean){
        if(c.tick==1)unlitAtStart=MirrorWorld.unlit;
        if(c.mc.level==null||!MirrorGallery.inGallery(c.mc.level))return;
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        Vec3 base=Vec3.atLowerCornerOf(MirrorGallery.originAt(c.mc.player.getX()));
        double z=1.5+Math.max(0,c.tick-20)*.2;
        place(c,base.add(1.6,1.62,z));lookRaw(c,(float)lean,4);
        if(c.tick==ArtPreview.CAPTURE_AT-1)ArtPreview.log(String.format("VEILBORNE_GALLERY walk lean=%.0f z=%.1f deferred_unlit=%d meshes=%d",lean,z,MirrorWorld.unlit-unlitAtStart,MirrorWorld.meshCount()));
    }
    static void beastWalk(ArtPreview.Ctx c){
        reset(c);c.player.setGameMode(GameType.CREATIVE);
        c.level.getServer().setDifficulty(Difficulty.EASY,true);
        BlockPos s=site(c.anchor);
        var b=Veilborne.BEAST.get().create(c.level);
        b.moveTo(s.getX()+.5,s.getY(),s.getZ()-15.5,90,0);
        c.level.addFreshEntity(b);
    }
    /** Up the path toward the camera and past it; asked again now and then, since its own wandering may cut in. */
    private static void walkBeast(ServerLevel l,BlockPos s){
        for(var e:l.getAllEntities())if(e instanceof MirrorBeast b)b.getNavigation().moveTo(s.getX()+.5,s.getY(),s.getZ()+6.5,1.0);
    }
    private static double beastFacing;private static int beastMoving;
    static void beastShot(ArtPreview.Ctx c){
        // Where it is walking to, a little to one side: a head-first beast walks into the lens.
        aim(c,new Vec3(1.2,1.3,-2.8),new Vec3(0,.2,-8));
        if(c.tick==1){beastFacing=0;beastMoving=0;}
        // Set off late, so it is still on its way at the capture.
        if(c.tick%10==0&&c.tick>=50){var server=c.mc.getSingleplayerServer();BlockPos s=site(c.anchor);if(server!=null)server.execute(()->walkBeast(server.overworld(),s));}
        for(var e:c.mc.level.entitiesForRendering())if(e instanceof MirrorBeast b){
            // Where it goes against where its body faces, which is where the renderer now points its head.
            Vec3 v=new Vec3(b.getX()-b.xo,0,b.getZ()-b.zo);
            if(c.tick>=20&&c.tick<=88&&v.length()>.02){double r=Math.toRadians(b.yBodyRot);beastFacing+=v.normalize().dot(new Vec3(-Math.sin(r),0,Math.cos(r)));beastMoving++;}
            if(c.tick==ArtPreview.CAPTURE_AT-1)ArtPreview.log(String.format("VEILBORNE_BEAST moving_ticks=%d mean_cos_heading_vs_body=%.3f",beastMoving,beastMoving==0?0:beastFacing/beastMoving));
            break;
        }
    }
    static void echoShot(ArtPreview.Ctx c){
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        if(c.tick==20){
            MirrorClient.trail.clear();
            BlockPos s=site(c.anchor);
            for(int i=0;i<110;i++){double t=i/109.0;MirrorClient.trail.add(new Vec3(s.getX()-6+t*14,s.getY(),s.getZ()-3+Math.sin(t*4)*3));}
            MirrorClient.showEcho();
        }
        aim(c,new Vec3(-8,3.4,-10),new Vec3(1,0,-3));
    }
    static void journal(ArtPreview.Ctx c,int spread){
        c.mc.options.hideGui=true;
        look(c,Vec3.ZERO,180,-40);
        if(!(c.mc.screen instanceof TideJournalScreen)){var screen=new TideJournalScreen();c.mc.setScreen(screen);screen.showChapter(2);screen.showSpread(spread);}
    }
    private static void aim(ArtPreview.Ctx c,Vec3 eye,Vec3 focus){
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
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
    private MirrorPreview(){}
}
