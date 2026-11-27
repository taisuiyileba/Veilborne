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
 * 苍翠梦潮 scenes for the art harness (-PtidePreview=art -PtidePreviewScenes=dream). They play in a meadow built south-west
 * of the other stages: a cottage with glass windows and a door, a field of wheat, a pond, two oaks and flowers. The event
 * seed, the dream tree, the petal path and the deer's road are fixed, so each scene sees the same forest. Each scene sets
 * the weather's age exactly: the harness captures 90 ticks after its setup, so ages are set that much early.
 */
final class DreamPreview {
    static final long SEED=20261201L;
    static BlockPos site(BlockPos anchor){return anchor.offset(-70,0,74);}
    static BlockPos tree(BlockPos anchor){return site(anchor).offset(9,0,-12);}
    static BlockPos door(BlockPos anchor){return site(anchor).offset(-9,0,5);}
    static final int CAPTURE_LAG=90;
    /** The deer's road: along x, 46 blocks south of the tree, so it passes across the view from the meadow. */
    static final float ROUTE_YAW=0;

    static void scenes(List<ArtPreview.Scene> out){
        out.add(dream("dream_omen",.035,DreamPreview::reset,c->aim(c,new Vec3(3,1.7,6),new Vec3(-8,.6,-6))));
        out.add(dream("dream_clouds",.95/13,DreamPreview::reset,c->look(c,new Vec3(0,1.7,6),30,-42)));
        out.add(dream("dream_canopy_grow",1.75/13,DreamPreview::reset,c->look(c,new Vec3(0,1.7,6),20,-48)));
        out.add(dream("dream_roots_grow",2.35/13,DreamPreview::reset,c->look(c,new Vec3(0,1.7,6),60,-22)));
        out.add(dream("dream_roots_down",2.9/13,DreamPreview::reset,c->look(c,new Vec3(0,1.7,6),160,-16)));
        out.add(dream("dream_canopy_full",3.3/13,DreamPreview::reset,c->look(c,new Vec3(0,1.7,6),10,-72)));
        out.add(dream("dream_canopy_wide",3.3/13,DreamPreview::reset,c->{look(c,new Vec3(0,1.7,6),200,-14);c.mc.options.fov().set(90);}));
        out.add(dream("dream_night",3.3/13,c->{reset(c);c.level.setDayTime(18000);},c->look(c,new Vec3(0,1.7,6),200,-20)));
        out.add(dream("dream_dapple",3.4/13,DreamPreview::reset,c->aim(c,new Vec3(-4,1.7,12),new Vec3(-10,1.5,6))));
        out.add(dream("dream_indoor",3.4/13,DreamPreview::reset,c->aim(c,new Vec3(-9,1.62,2.6),new Vec3(-12.4,1.2,2.6))));
        out.add(dream("dream_schools",4.5/13,DreamPreview::reset,c->{aim(c,new Vec3(2,1.7,8),new Vec3(2,6,-8));school(c,new Vec3(2,6,-6));}));
        out.add(dream("dream_schools_below",4.5/13,DreamPreview::reset,c->{look(c,new Vec3(0,1.7,6),0,-60);school(c,new Vec3(0,9,4));}));
        out.add(dream("dream_patterns",4.5/13,DreamPreview::reset,c->{aim(c,new Vec3(0,6,14),new Vec3(0,0,2));patterns(c);}));
        out.add(dream("dream_patterns_night",4.5/13,c->{reset(c);c.level.setDayTime(18000);},c->{aim(c,new Vec3(0,6,14),new Vec3(0,0,2));patterns(c);}));
        out.add(dream("dream_tree",5/13.0,c->{reset(c);tree(c,-2000);},c->aim(c,new Vec3(3,1.7,-1),new Vec3(9,4,-12))));
        out.add(dream("dream_tree_night",5/13.0,c->{reset(c);tree(c,-2000);c.level.setDayTime(18000);},c->aim(c,new Vec3(3,1.7,-1),new Vec3(9,4,-12))));
        out.add(dream("dream_tree_listen",5/13.0,c->{reset(c);tree(c,-2000);},c->{c.mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);aim(c,new Vec3(6,1.62,-6),new Vec3(9,2,-12));}));
        out.add(dream("dream_path",5/13.0,c->{reset(c);tree(c,-2000);path(c);},c->aim(c,new Vec3(-2,2.6,8),new Vec3(6,0,-8))));
        out.add(dream("dream_wander",5/13.0,c->{reset(c);wander(c);},c->aim(c,new Vec3(10,1.7,6),new Vec3(12,1.4,12))));
        out.add(dream("dream_enclosure",5/13.0,c->{reset(c);enclosure(c,false);},c->{c.mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);aim(c,new Vec3(0,1.62,4),new Vec3(0,1,-2));}));
        out.add(dream("dream_enclosure_won",5/13.0,c->{reset(c);enclosure(c,true);},c->aim(c,new Vec3(0,5,12),new Vec3(0,0,0))));
        out.add(dream("dream_lost",5/13.0,c->{reset(c);lost(c,LeafSchool.State.STUCK);},c->aim(c,new Vec3(-9.2,1.62,2.5),new Vec3(-4.6,2.4,2.5))));
        out.add(dream("dream_lost_merge",5/13.0,c->{reset(c);lost(c,LeafSchool.State.STUCK);},c->{aim(c,new Vec3(-2,1.7,6),new Vec3(-6,2.5,2.6));atTick(c,18,l->{for(var e:l.getAllEntities())if(e instanceof LeafSchool s)s.debugState(LeafSchool.State.MERGE);});}));
        out.add(dream("dream_road",6.85/13,c->{reset(c);tree(c,-2000);route(c);},c->look(c,new Vec3(0,1.7,6),150,-12)));
        out.add(dream("dream_deer_far",7.25/13,c->{reset(c);tree(c,-2000);route(c);},DreamPreview::deerAhead));
        out.add(dream("dream_deer_walk",7.8/13,c->{reset(c);tree(c,-2000);route(c);},c->deerSide(c,70,4,false)));
        out.add(dream("dream_deer_close",7.95/13,c->{reset(c);tree(c,-2000);route(c);},c->deerSide(c,34,2,false)));
        out.add(dream("dream_deer_legs",7.95/13,c->{reset(c);tree(c,-2000);route(c);},c->deerSide(c,30,1,true)));
        out.add(dream("dream_deer_night",7.8/13,c->{reset(c);tree(c,-2000);route(c);c.level.setDayTime(18000);},c->deerSide(c,70,4,false)));
        out.add(dream("dream_deer_turn",DreamTimeline.stopFrom()+.004,c->{reset(c);tree(c,-2000);route(c);glance(c);},DreamPreview::fromTree));
        out.add(dream("dream_glance",DreamTimeline.glanceP()+.004,c->{reset(c);tree(c,-2000);route(c);glance(c);},DreamPreview::fromTree));
        out.add(dream("dream_glance_wide",DreamTimeline.glanceP()+.004,c->{reset(c);tree(c,-2000);route(c);glance(c);},c->{fromTree(c);c.mc.options.fov().set(95);}));
        out.add(dream("dream_glance_night",DreamTimeline.glanceP()+.004,c->{reset(c);tree(c,-2000);route(c);glance(c);c.level.setDayTime(18000);},DreamPreview::fromTree));
        out.add(dream("dream_glance_side",DreamTimeline.glanceP()+.004,c->{reset(c);tree(c,-2000);route(c);glance(c);},c->{c.mc.options.setCameraType(CameraType.FIRST_PERSON);Vec3 t=Vec3.atBottomCenterOf(tree(c.anchor));place(c,t.add(-18,6,26));lookAt(c,t.add(0,14,24));}));
        out.add(dream("dream_antlers",7.8/13,c->{reset(c);tree(c,-2000);route(c);},DreamPreview::antlers));
        out.add(dream("dream_hoof_rings",8.9/13,c->{reset(c);tree(c,-2000);route(c);},c->{c.mc.options.setCameraType(CameraType.FIRST_PERSON);Vec3 t=Vec3.atBottomCenterOf(tree(c.anchor));place(c,t.add(0,40,20));lookAt(c,t.add(0,0,46));}));
        out.add(dream("dream_gold",10.1/13,c->{reset(c);tree(c,-2000);},c->look(c,new Vec3(0,1.7,6),200,-30)));
        out.add(dream("dream_seedfall",10.3/13,c->{reset(c);seed(c,DreamSeed.State.DRIFT);},c->aim(c,new Vec3(0,1.62,4),new Vec3(1,2.2,.5))));
        out.add(dream("dream_seed_follow",10.3/13,c->{reset(c);seed(c,DreamSeed.State.FOLLOW);},c->{c.mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);aim(c,new Vec3(0,1.62,4),new Vec3(0,1.2,1));}));
        // The seed following a sweeping gaze: it should glide, not hop between server updates (VEILBORNE_MOTION).
        out.add(dream("dream_seed_motion",10.3/13,c->{reset(c);seed(c,DreamSeed.State.FOLLOW);},c->{
            // A level gaze keeps the cupped hands above landing height, so it follows instead of coming down to sprout.
            look(c,new Vec3(0,1.62,4),180+(float)Math.sin(c.tick*.06)*35,0);
            for(var e:c.mc.level.entitiesForRendering())if(e instanceof DreamSeed s){ArtPreview.Steps.track(c,"seed",s.position(),30,90);break;}
        }));
        out.add(dream("dream_seed_land",10.3/13,c->{reset(c);seed(c,DreamSeed.State.LAND);},c->aim(c,new Vec3(0,1.62,3.5),new Vec3(1,.4,.5))));
        out.add(dream("dream_veins",11.45/13,DreamPreview::reset,c->look(c,new Vec3(0,1.7,6),30,-55)));
        out.add(dream("dream_return",12.35/13,DreamPreview::reset,c->look(c,new Vec3(0,1.7,6),160,-20)));
        out.add(dream("dream_twig",DreamTimeline.TWIG_AT+60/(double)DreamTimeline.DEFAULT_DURATION,c->{reset(c);twig(c);},c->aim(c,new Vec3(0,1.7,6),new Vec3(3,8,2))));
        out.add(dream("dream_items_hotbar",3.2/13,DreamPreview::keepsakes,c->{c.mc.options.hideGui=false;aim(c,new Vec3(1.5,1.6,-1),new Vec3(-.5,.6,-4));}));
        out.add(dream("dream_blocks",3.2/13,DreamPreview::keepsakes,c->{c.mc.options.hideGui=true;aim(c,new Vec3(1.5,1.6,-1),new Vec3(-.5,.8,-5));}));
        out.add(dream("dream_grown_tree",.5,c->{keepsakes(c);DreamService.end(c.level);},c->{c.mc.options.hideGui=true;aim(c,new Vec3(-3,2.5,1),new Vec3(-9,4,-6));}));
        for(int i=0;i<4;i++){int spread=i;
            out.add(dream("dream_journal_"+i,4.2/13,c->{reset(c);c.player.getPersistentData().putInt(TideService.RECORD2,DreamService.FIRST|DreamService.DEER|DreamService.SEED|DreamService.FOOTPRINT);},c->journal(c,spread)));}
        for(String round:new String[]{"a","b"}){
            out.add(dream("perf_dream_canopy_"+round,3.4/13,DreamPreview::reset,c->look(c,new Vec3(0,1.7,6),200,-14)));
            out.add(dream("perf_dream_deer_"+round,7.8/13,c->{reset(c);tree(c,-2000);route(c);},c->deerSide(c,70,4,false)));
        }
    }
    private static ArtPreview.Scene dream(String name,double p,java.util.function.Consumer<ArtPreview.Ctx> setup,java.util.function.Consumer<ArtPreview.Ctx> camera){
        return new ArtPreview.Scene(name,(int)Math.floor(p*100),c->{setup.accept(c);var d=DreamData.get(c.level);if(d.active){d.age=Math.max(0,(int)Math.round(p*d.duration)-CAPTURE_LAG);d.setDirty();}},camera,5);
    }

    // ------------------------------------------------------------------ stage
    static void build(ServerLevel l,BlockPos anchor){
        BlockPos s=site(anchor);
        for(int x=-26;x<=26;x++)for(int z=-24;z<=26;z++){
            for(int y=0;y<=16;y++)l.setBlock(s.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            l.setBlock(s.offset(x,-3,z),Blocks.DIRT.defaultBlockState(),2);
            l.setBlock(s.offset(x,-2,z),Blocks.DIRT.defaultBlockState(),2);
            boolean pond=Math.hypot(x+14,(z+12)*1.2)<4;
            boolean field=x>=8&&x<=16&&z>=8&&z<=16;
            l.setBlock(s.offset(x,-1,z),(pond?Blocks.WATER:field?Blocks.FARMLAND:Blocks.GRASS_BLOCK).defaultBlockState(),2);
            if(field&&(x+z)%4!=0)l.setBlock(s.offset(x,0,z),Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,5+Math.floorMod(x*3+z,3)),2);
            else if(!field&&!pond&&(x*7+z*13&7)==0){
                Block[] flowers={Blocks.POPPY,Blocks.DANDELION,Blocks.CORNFLOWER,Blocks.ALLIUM,Blocks.OXEYE_DAISY,Blocks.SHORT_GRASS,Blocks.AZURE_BLUET,Blocks.SHORT_GRASS};
                l.setBlock(s.offset(x,0,z),flowers[Math.floorMod(x*3+z*5,flowers.length)].defaultBlockState(),2);
            }
        }
        for(int z=8;z<=16;z+=4)l.setBlock(s.offset(12,-1,z),Blocks.WATER.defaultBlockState(),2);
        oak(l,s.offset(-18,0,14));oak(l,s.offset(20,0,-18));
        // The cottage: windows on every side, its door facing the meadow.
        BlockPos h=s.offset(-13,0,-1);
        for(int x=0;x<=7;x++)for(int z=0;z<=6;z++)for(int y=0;y<=4;y++){
            boolean wall=x==0||x==7||z==0||z==6,corner=(x==0||x==7)&&(z==0||z==6);
            BlockPos p=h.offset(x,y,z);
            if(y==4)l.setBlock(p,Blocks.SPRUCE_SLAB.defaultBlockState(),2);
            else if(wall)l.setBlock(p,(corner?Blocks.STRIPPED_SPRUCE_LOG:y==0?Blocks.MOSSY_COBBLESTONE:Blocks.BIRCH_PLANKS).defaultBlockState(),2);
        }
        BlockPos d=door(anchor);
        l.setBlock(d,Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.NORTH).setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER),2);
        l.setBlock(d.above(),Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.NORTH).setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
        for(int[] w:new int[][]{{0,1,3},{0,2,3},{7,1,3},{7,2,3},{2,2,0},{5,2,0},{2,2,6},{5,2,6}})l.setBlock(h.offset(w[0],w[1],w[2]),Blocks.GLASS_PANE.defaultBlockState(),2);
        l.setBlock(h.offset(4,3,3),Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true),2);
        l.setBlock(h.offset(2,0,2),Blocks.GREEN_BED.defaultBlockState(),2);
        l.setBlock(h.offset(5,0,5),Blocks.CRAFTING_TABLE.defaultBlockState(),2);
        l.setBlock(h.offset(6,0,1),Blocks.BOOKSHELF.defaultBlockState(),2);
    }
    private static void oak(ServerLevel l,BlockPos base){
        for(int y=0;y<6;y++)l.setBlock(base.above(y),Blocks.OAK_LOG.defaultBlockState(),2);
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)for(int y=4;y<=8;y++){
            double r=Math.sqrt(x*x+z*z+(y-6)*(y-6)*1.4);
            if(r<3.3&&!(x==0&&z==0&&y<6))l.setBlock(base.offset(x,y,z),Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true),2);
        }
    }

    // ------------------------------------------------------------------ scene setup (server thread)
    static void reset(ArtPreview.Ctx c){
        for(var e:com.google.common.collect.ImmutableList.copyOf(c.level.getAllEntities())){
            if(e instanceof DreamSeed||e instanceof LeafSchool)e.discard();
            else if(e instanceof net.minecraft.world.entity.item.ItemEntity i&&(i.getItem().is(Veilborne.DREAM_SEED.get())||i.getItem().is(Veilborne.FULL_DREAM_SEED.get())||i.getItem().is(Veilborne.ANTLER_TWIG_ITEM.get())||i.getItem().is(Veilborne.GOLDEN_LEAF.get())))i.discard();
        }
        c.level.getServer().setDifficulty(Difficulty.PEACEFUL,true);
        var d=DreamData.get(c.level);
        if(!d.active)DreamService.begin(c.level,site(c.anchor),780);
        d.seed=SEED;d.center=site(c.anchor);d.variant=DreamData.ALL;
        d.flags=DreamData.TREE|DreamData.PATH|DreamData.ROUTE|DreamData.WANDER|DreamData.ENCLOSURE|DreamData.ENCLOSURE_LOST|DreamData.LOST|DreamData.TWIG|DreamData.TWIG_DROPPED|DreamData.GLANCE|DreamData.GIFT_DROPPED;
        d.tree=BlockPos.ZERO;d.route=BlockPos.ZERO;d.glance=BlockPos.ZERO;d.enclosure=BlockPos.ZERO;d.twig=BlockPos.ZERO;d.path.clear();
        d.setDirty();
        c.level.setDayTime(3000);
        c.player.setGameMode(GameType.SPECTATOR);
        BlockPos s=site(c.anchor);
        c.player.teleportTo(c.level,s.getX()+.5,s.getY(),s.getZ()+6.5,180,0);
    }
    private static void tree(ArtPreview.Ctx c,int ago){var d=DreamData.get(c.level);d.tree=tree(c.anchor);d.treeAt=ago;d.setDirty();}
    private static void path(ArtPreview.Ctx c){
        var d=DreamData.get(c.level);
        var way=DreamService.walk(c.level,site(c.anchor).offset(-3,0,7),tree(c.anchor));
        d.path.clear();if(way!=null)d.path.addAll(way);d.pathAt=-2000;d.setDirty();
    }
    private static void route(ArtPreview.Ctx c){
        var d=DreamData.get(c.level);
        d.route=tree(c.anchor);d.routeYaw=ROUTE_YAW;d.routeSide=1;d.routeAt=-100;d.setDirty();
    }
    /** The deer looks at whoever is standing where the camera will be. */
    private static void glance(ArtPreview.Ctx c){
        var d=DreamData.get(c.level);
        d.flags|=DreamData.GLANCE_PLAYER;d.glance=tree(c.anchor).offset(-3,0,6);d.glanceAt=0;d.giftAt=(int)Math.round(DreamTimeline.glanceP()*d.duration)-40;d.flags&=~DreamData.GIFT_DROPPED;d.setDirty();
    }
    private static void wander(ArtPreview.Ctx c){
        var s=Veilborne.DREAM_SEED_ENTITY.get().create(c.level);
        Vec3 home=Vec3.atBottomCenterOf(site(c.anchor).offset(12,0,12)).add(0,1.1,0);
        s.place(DreamSeed.Kind.WANDER,home,home);c.level.addFreshEntity(s);
    }
    private static void seed(ArtPreview.Ctx c,DreamSeed.State state){
        var s=Veilborne.DREAM_SEED_ENTITY.get().create(c.level);
        Vec3 at=Vec3.atBottomCenterOf(site(c.anchor)).add(1,state==DreamSeed.State.LAND?.35:2.2,.5);
        s.place(DreamSeed.Kind.FALL,at,at);c.level.addFreshEntity(s);
        if(state==DreamSeed.State.FOLLOW){c.player.setGameMode(GameType.CREATIVE);c.player.getInventory().clearContent();c.player.getInventory().setItem(0,new ItemStack(Items.POPPY));c.player.getInventory().selected=0;s.debugGuide(c.player);}
        if(state!=DreamSeed.State.DRIFT)s.debugState(state);
    }
    private static void enclosure(ArtPreview.Ctx c,boolean won){
        var d=DreamData.get(c.level);
        BlockPos at=site(c.anchor);
        c.player.teleportTo(c.level,at.getX()+.5,at.getY(),at.getZ()+.5,180,0);
        // Opened the way the game opens it, so the server knows whose it is and keeps it open (a spectator it would let go).
        c.player.setGameMode(GameType.CREATIVE);
        d.flags&=~(DreamData.ENCLOSURE|DreamData.ENCLOSURE_LOST|DreamData.ENCLOSURE_WON);d.variant|=DreamData.VARIANT_ENCLOSURE;
        DreamService.openEnclosure(c.level,c.player,null);
        d.enclosure=at;d.enclosureAt=-400;d.enclosureExit=(float)(Math.PI/2);d.lightStep=won?DreamTimeline.LIGHTS:2;d.lightAt=(int)Math.round(5/13.0*d.duration)-CAPTURE_LAG-20;
        if(won){d.flags|=DreamData.ENCLOSURE_WON;d.closedAt=(int)Math.round(5/13.0*d.duration)-CAPTURE_LAG-10;}
        d.setDirty();
    }
    private static void lost(ArtPreview.Ctx c,LeafSchool.State state){
        var s=Veilborne.LEAF_SCHOOL.get().create(c.level);
        BlockPos h=site(c.anchor).offset(-13,0,-1);
        s.place(Vec3.atCenterOf(h.offset(7,2,3)).add(.95,0,0),Direction.EAST);
        c.level.addFreshEntity(s);
        if(state!=LeafSchool.State.STUCK)s.debugState(state);
    }
    private static void twig(ArtPreview.Ctx c){
        var d=DreamData.get(c.level);
        d.flags&=~DreamData.TWIG_DROPPED;d.twig=site(c.anchor).offset(3,0,2);d.twigAt=(int)Math.round(DreamTimeline.TWIG_AT*d.duration);d.setDirty();
    }
    static void keepsakes(ArtPreview.Ctx c){
        reset(c);c.player.setGameMode(GameType.CREATIVE);
        var inv=c.player.getInventory();inv.clearContent();
        net.minecraft.world.item.Item[] kit={Veilborne.JOURNAL.get(),Veilborne.DREAM_SEED.get(),Veilborne.FULL_DREAM_SEED.get(),Veilborne.GOLDEN_LEAF.get(),
            Veilborne.DRIFTING_LEAF.get(),Veilborne.DEER_FLUTE.get(),Veilborne.FLOWER_LAMP_ITEM.get(),Veilborne.ANTLER_TWIG_ITEM.get(),Veilborne.DREAM_LEAVES_ITEM.get()};
        for(int i=0;i<kit.length;i++)inv.setItem(i,new ItemStack(kit[i]));
        inv.selected=6;
        BlockPos s=site(c.anchor);
        c.level.setBlock(s.offset(-1,0,-4),Veilborne.FLOWER_LAMP.get().defaultBlockState(),3);
        c.level.setBlock(s.offset(1,0,-4),Veilborne.DREAM_SAPLING.get().defaultBlockState().setValue(DreamBlocks.STAGE,2),3);
        c.level.setBlock(s.offset(2,0,-5),Veilborne.DREAM_SAPLING.get().defaultBlockState().setValue(DreamBlocks.STAGE,0).setValue(DreamBlocks.FULL,true),3);
        for(int y=0;y<3;y++)c.level.setBlock(s.offset(0,y,-7),Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),3);
        c.level.setBlock(s.offset(0,1,-6),Veilborne.ANTLER_TWIG.get().defaultBlockState().setValue(DreamBlocks.Twig.FACING,Direction.SOUTH),3);
        // A dream tree grown from a full seed, beside the cottage.
        BlockPos g=s.offset(-9,0,-6);
        if(!c.level.getBlockState(g).is(Veilborne.DREAM_LOG.get()))DreamBlocks.grow(c.level,g,true,net.minecraft.util.RandomSource.create(7));
        c.player.teleportTo(c.level,s.getX()+.5,s.getY(),s.getZ()-1,180,0);
    }

    // ------------------------------------------------------------------ cameras (client)
    private static void school(ArtPreview.Ctx c,Vec3 off){
        // Held where it was put, swimming in place, so it is still in frame when the shot is taken.
        if(c.tick!=4&&!DreamFlock.schools.isEmpty()){
            DreamFlock.School held=DreamFlock.schools.get(0);
            held.center=Vec3.atBottomCenterOf(site(c.anchor)).add(off);held.vel=Vec3.ZERO;held.state=DreamFlock.CRUISE;
            return;
        }
        DreamFlock.schools.clear();
        DreamFlock.School s=new DreamFlock.School();
        s.center=Vec3.atBottomCenterOf(site(c.anchor)).add(off);s.vel=Vec3.ZERO;s.heading=.4;s.seed=3.3;s.born=DreamClient.ticks-100;s.stateAt=DreamClient.ticks;s.climb=off.y;
        var random=net.minecraft.util.RandomSource.create(5);
        for(int i=0;i<24;i++){
            Vec3 slot=new Vec3((random.nextDouble()-.5)*6,(random.nextDouble()-.5)*2.2,(random.nextDouble()-.5)*4.2);
            DreamFlock.Fish f=new DreamFlock.Fish(s.center.add(slot),slot);
            f.phase=random.nextDouble()*6.28;f.size=.8+random.nextDouble()*.55;f.variant=random.nextInt(3);f.tint=i%3==0?DreamFlock.JADE:DreamFlock.GREEN;
            s.fish.add(f);
        }
        DreamFlock.schools.add(s);
    }
    private static void patterns(ArtPreview.Ctx c){
        if(c.tick!=30)return;
        Vec3 o=Vec3.atBottomCenterOf(site(c.anchor));
        DreamClient.blooms.add(new DreamClient.Bloom(o.add(-2,0,2),DreamClient.ticks-20,0,5.5,.3));
        DreamClient.blooms.add(new DreamClient.Bloom(o.add(5,0,-1),DreamClient.ticks-40,0,3.2,.7));
        DreamClient.blooms.add(new DreamClient.Bloom(o.add(-7,0,-3),DreamClient.ticks-10,0,2.4,.1));
        DreamClient.blooms.add(new DreamClient.Bloom(o.add(3,0,6),DreamClient.ticks-50,1,8.5,.5));
    }
    private static void deerAhead(ArtPreview.Ctx c){
        DeerModel.Pose p=DeerModel.pose(1);
        if(p==null)return;
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        Vec3 t=Vec3.atBottomCenterOf(tree(c.anchor));
        place(c,t.add(0,1.7,4));
        lookAt(c,p.base().add(0,60,0));
    }
    private static void deerSide(ArtPreview.Ctx c,double dist,double height,boolean low){
        DeerModel.Pose p=DeerModel.pose(1);
        if(p==null)return;
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        Vec3 out=p.r().scale(-1);
        Vec3 eye=p.base().add(out.scale(dist)).add(p.h().scale(dist*.3));
        // Never below the ground the deer walks on: past the edge of the flat stage the land falls away.
        double top=Surface.top(c.mc.level,net.minecraft.util.Mth.floor(eye.x),net.minecraft.util.Mth.floor(eye.z));
        eye=new Vec3(eye.x,Math.max(top,p.base().y)+height,eye.z);
        place(c,eye);
        lookAt(c,p.base().add(0,low?10:42,0));
        c.mc.options.fov().set(low?70:80);
    }
    private static void fromTree(ArtPreview.Ctx c){
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        Vec3 at=Vec3.atBottomCenterOf(tree(c.anchor).offset(-3,0,6));
        place(c,at.add(0,1.62,0));
        DeerModel.Pose p=DeerModel.pose(1);
        lookAt(c,p==null?at.add(0,20,40):p.occiput().add(p.ha().scale(6)));
        c.mc.options.fov().set(80);
    }
    private static void antlers(ArtPreview.Ctx c){
        DeerModel.Pose p=DeerModel.pose(1);
        if(p==null)return;
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        Vec3 crown=p.occiput().add(p.hu().scale(30));
        place(c,crown.add(p.r().scale(-38)).add(p.h().scale(22)).add(0,6,0));
        lookAt(c,crown);
    }
    private static void atTick(ArtPreview.Ctx c,int before,java.util.function.Consumer<ServerLevel> action){
        if(c.tick==ArtPreview.CAPTURE_AT-before){var server=c.mc.getSingleplayerServer();if(server!=null)server.execute(()->action.accept(server.overworld()));}
    }
    static void journal(ArtPreview.Ctx c,int spread){
        c.mc.options.hideGui=true;
        aim(c,new Vec3(0,1.7,8),new Vec3(0,1,-20));
        if(!(c.mc.screen instanceof TideJournalScreen)){var screen=new TideJournalScreen();c.mc.setScreen(screen);screen.showChapter(5);screen.showSpread(spread);}
    }
    private static void look(ArtPreview.Ctx c,Vec3 eye,float yaw,float pitch){
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);c.mc.options.fov().set(70);
        place(c,Vec3.atBottomCenterOf(site(c.anchor)).add(eye));lookRaw(c,yaw,pitch);
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
    private DreamPreview(){}
}
