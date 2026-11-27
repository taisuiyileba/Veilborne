package com.suiyi.veilborne.client;

import com.suiyi.veilborne.*;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * 月骸花潮 scenes for the art harness (-PtidePreview=art -PtidePreviewScenes=moon). They play in a small moon
 * garden built beside the sea stage: grass, a winding dirt path, a grove, a hut with a door and a window, and
 * lamp posts. The event seed is fixed so the moon always hangs in the same place.
 */
final class MoonPreview {
    static final long SEED=20260926L;
    static BlockPos garden(BlockPos anchor){return anchor.offset(0,0,56);}

    static void scenes(List<ArtPreview.Scene> out){
        out.add(moon("moon_halo",3,MoonPreview::reset,c->moonShot(c,Vec3.ZERO,0,-4)));
        out.add(moon("moon_crack",17,MoonPreview::reset,c->moonShot(c,Vec3.ZERO,0,-4)));
        out.add(moon("moon_bloom_sky",42,MoonPreview::reset,c->moonShot(c,Vec3.ZERO,-22,8)));
        out.add(moon("moon_field",48,MoonPreview::reset,c->moonShot(c,new Vec3(0,0,-4),10,-24)));
        out.add(moon("moon_field_down",48,MoonPreview::standing,c->{c.mc.options.setCameraType(CameraType.FIRST_PERSON);aim(c,new Vec3(.5,1.62,-1),new Vec3(1.6,0,3.5));}));
        out.add(moon("moon_lamp",46,MoonPreview::reset,c->aim(c,new Vec3(-7,2.3,-6),new Vec3(-4,1.8,-2))));
        out.add(moon("moon_molt",64,MoonPreview::reset,c->moonShot(c,Vec3.ZERO,0,0)));
        out.add(moon("moon_three",70,MoonPreview::reset,c->threeShot(c)));
        out.add(moon("moon_shatter",71,c->{reset(c);var d=MoonData.get(c.level);d.flags|=MoonData.BROKEN;d.brokenAt=d.age-26;},MoonPreview::threeShot));
        out.add(moon("moon_farewell",81,c->{reset(c);var d=MoonData.get(c.level);d.flags&=~MoonData.LANTERN;MoonService.placeLantern(c.level,garden(c.anchor).offset(-1,0,-5),Direction.SOUTH);d.lanternAt=d.age-40;},c->aim(c,new Vec3(3,2.2,-11),new Vec3(-1,1.4,-5))));
        out.add(moon("moon_close",94,MoonPreview::reset,c->moonShot(c,new Vec3(0,0,-4),6,-14)));
        out.add(moon("moon_day",50,c->{reset(c);c.level.setDayTime(6000);},c->moonShot(c,new Vec3(0,0,-4),10,-18)));
        out.add(moon("moon_shadow",48,MoonPreview::standing,c->{c.mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);look(c,yawAwayFromMoon(c)+160,24);}));
        // Straight up, and toward the panorama's wrap opposite the moon: neither may show a seam or a pinch.
        out.add(moon("moon_zenith",40,MoonPreview::standing,c->{c.mc.options.setCameraType(CameraType.FIRST_PERSON);look(c,yawAwayFromMoon(c),-90);}));
        out.add(moon("moon_behind",40,MoonPreview::standing,c->{c.mc.options.setCameraType(CameraType.FIRST_PERSON);look(c,yawAwayFromMoon(c),-38);}));
        // Walking across the moonlight: the shadow must stay at the feet while only its stride lags.
        out.add(moon("moon_shadow_walk",48,MoonPreview::standing,c->{c.mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);look(c,yawAwayFromMoon(c)+90,22);c.mc.options.keyUp.setDown(c.tick>=62&&c.tick<=94);}));
        for(int a:new int[]{0,110,200,290}){int angle=a;
            out.add(moon("moon_procession_"+a,40,c->procession(c,34,MoonProcession.State.WALK),c->orbitProcession(c,5.2,1.6,angle)));}
        out.add(moon("moon_procession_face",40,c->procession(c,34,MoonProcession.State.WALK),c->faceProcession(c)));
        out.add(moon("moon_procession_far",40,c->procession(c,34,MoonProcession.State.WALK),c->orbitProcession(c,16,3,60)));
        out.add(moon("moon_path",40,c->{procession(c,30,MoonProcession.State.WALK);c.player.setGameMode(GameType.CREATIVE);},MoonPreview::escort));
        out.add(moon("moon_empty_place",40,c->{procession(c,32,MoonProcession.State.WALK);c.player.setGameMode(GameType.CREATIVE);c.player.getInventory().setItem(c.player.getInventory().selected,new ItemStack(Veilborne.SILVER_LANTERN_ITEM.get()));},MoonPreview::emptyPlace));
        out.add(moon("moon_scatter",40,c->procession(c,34,MoonProcession.State.WALK),MoonPreview::scatterShot));
        out.add(moon("moon_offer",66,c->procession(c,60,MoonProcession.State.OFFER),c->orbitProcession(c,8,3,40)));
        out.add(moon("moon_little",46,MoonPreview::littleMoon,c->aim(c,new Vec3(-4,2.2,-3),new Vec3(-9,2.3,-8))));
        out.add(moon("moon_constellation",46,MoonPreview::littleMoon,MoonPreview::constellation));
        out.add(moon("moon_indoor",48,MoonPreview::reset,c->aim(c,new Vec3(13.5,1.6,10.5),new Vec3(13,1,5.5))));
        out.add(moon("moon_decoy",48,c->{standing(c);var decoy=Veilborne.DECOY.get().create(c.level);decoy.setOwner(c.player.getUUID());var g=garden(c.anchor);decoy.moveTo(g.getX()+2.5,g.getY(),g.getZ()-1.5,200,0);c.level.addFreshEntity(decoy);},c->aim(c,new Vec3(-1,1.9,-4.5),new Vec3(2.5,1,-1.5))));
        out.add(moon("moon_items_hotbar",48,MoonPreview::keepsakes,c->{c.mc.options.hideGui=false;aim(c,new Vec3(-1.5,1.9,-2.5),new Vec3(1.5,.3,1.5));}));
        out.add(moon("moon_lantern_close",48,MoonPreview::keepsakes,c->{c.mc.options.hideGui=true;aim(c,new Vec3(.3,1.1,-.5),new Vec3(1.5,.45,1.5));}));
        for(int i=0;i<4;i++){int spread=i;
            out.add(moon("moon_journal_"+i,64,c->{reset(c);c.player.getPersistentData().putInt(TideService.RECORD,1|2|8|MoonService.FIRST|MoonService.MOLT|MoonService.CONSTELLATION|MoonService.EMPTY_PLACE);},c->journal(c,spread)));}
        for(String round:new String[]{"a","b"}){
            out.add(moon("perf_moon_field_"+round,48,MoonPreview::reset,c->moonShot(c,new Vec3(0,0,-4),10,-24)));
            out.add(moon("perf_moon_procession_"+round,40,c->procession(c,34,MoonProcession.State.WALK),c->moonShot(c,new Vec3(0,0,-4),10,-24)));
        }
    }
    private static ArtPreview.Scene moon(String name,int stage,java.util.function.Consumer<ArtPreview.Ctx> setup,java.util.function.Consumer<ArtPreview.Ctx> camera){
        return new ArtPreview.Scene(name,stage,setup,camera,true);
    }

    // ------------------------------------------------------------------ stage
    static void buildGarden(ServerLevel l,BlockPos anchor){
        BlockPos g=garden(anchor);
        for(int x=-24;x<=24;x++)for(int z=-24;z<=24;z++){
            for(int y=0;y<=16;y++)l.setBlock(g.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            l.setBlock(g.offset(x,-3,z),Blocks.DIRT.defaultBlockState(),2);
            l.setBlock(g.offset(x,-2,z),Blocks.DIRT.defaultBlockState(),2);
            boolean path=Math.abs(z-Math.round(2.5*Math.sin(x/6.0)))<=1;
            l.setBlock(g.offset(x,-1,z),(path?Blocks.DIRT_PATH:Blocks.GRASS_BLOCK).defaultBlockState(),2);
            if(!path&&(x*7+z*13&15)==0)l.setBlock(g.offset(x,0,z),Blocks.SHORT_GRASS.defaultBlockState(),2);
        }
        for(int[] t:new int[][]{{-9,-10},{-13,-5},{-5,-13},{-15,-12}})tree(l,g.offset(t[0],0,t[1]));
        // A hut with a doorway facing the path and a window on its east wall.
        for(int x=10;x<=16;x++)for(int z=6;z<=12;z++)for(int y=-1;y<=4;y++){
            boolean wall=x==10||x==16||z==6||z==12;
            BlockPos p=g.offset(x,y,z);
            if(y==-1||y==4)l.setBlock(p,Blocks.SPRUCE_PLANKS.defaultBlockState(),2);
            else if(wall)l.setBlock(p,Blocks.SPRUCE_PLANKS.defaultBlockState(),2);
        }
        l.setBlock(g.offset(13,0,6),Blocks.AIR.defaultBlockState(),2);l.setBlock(g.offset(13,1,6),Blocks.AIR.defaultBlockState(),2);
        l.setBlock(g.offset(16,1,9),Blocks.GLASS.defaultBlockState(),2);l.setBlock(g.offset(16,2,9),Blocks.GLASS.defaultBlockState(),2);
        l.setBlock(g.offset(13,2,11),Blocks.LANTERN.defaultBlockState(),2);
        for(int[] post:new int[][]{{-4,-3},{5,4},{-12,3}}){
            l.setBlock(g.offset(post[0],0,post[1]),Blocks.SPRUCE_FENCE.defaultBlockState(),2);
            l.setBlock(g.offset(post[0],1,post[1]),Blocks.SPRUCE_FENCE.defaultBlockState(),2);
            l.setBlock(g.offset(post[0],2,post[1]),Blocks.LANTERN.defaultBlockState(),2);
        }
    }
    private static void tree(ServerLevel l,BlockPos base){
        for(int y=0;y<6;y++)l.setBlock(base.above(y),Blocks.OAK_LOG.defaultBlockState(),2);
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)for(int y=3;y<=8;y++){
            double r=Math.sqrt(x*x+z*z+(y-5.5)*(y-5.5)*1.6);
            if(r<3.3&&!(x==0&&z==0&&y<6))l.setBlock(base.offset(x,y,z),Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT,true),2);
        }
    }

    // ------------------------------------------------------------------ scene setup (server thread)
    static void reset(ArtPreview.Ctx c){
        for(var e:c.level.getAllEntities())if(e instanceof MoonProcession p)p.finish();else if(e instanceof MoonDecoy d)d.discard();
        var d=MoonData.get(c.level);
        d.flags|=MoonData.PROCESSION|MoonData.LITTLE_MOON|MoonData.LANTERN|MoonData.REMNANT;
        d.flags&=~MoonData.BROKEN;d.brokenAt=0;d.littleMoon=BlockPos.ZERO;d.lantern=BlockPos.ZERO;
        c.level.setDayTime(18000);
        c.player.setGameMode(GameType.SPECTATOR);
        BlockPos g=garden(c.anchor);
        c.player.teleportTo(g.getX()+.5,g.getY(),g.getZ()+.5);
    }
    static void standing(ArtPreview.Ctx c){
        reset(c);c.player.setGameMode(GameType.CREATIVE);
        BlockPos g=garden(c.anchor);c.player.teleportTo(g.getX()+.5,g.getY(),g.getZ()-1.5);
    }
    static void procession(ArtPreview.Ctx c,double at,MoonProcession.State state){
        reset(c);
        BlockPos g=garden(c.anchor);
        var proc=Veilborne.PROCESSION.get().create(c.level);
        proc.setRoute(g.offset(-34,0,0),0,80,977);
        proc.preview(at,state);
        c.level.addFreshEntity(proc);
    }
    static void littleMoon(ArtPreview.Ctx c){
        reset(c);var d=MoonData.get(c.level);
        d.littleMoon=garden(c.anchor).offset(-9,0,-6);d.littleMoonAt=d.age-120;
    }
    static void keepsakes(ArtPreview.Ctx c){
        reset(c);c.player.setGameMode(GameType.CREATIVE);
        var inv=c.player.getInventory();inv.clearContent();
        net.minecraft.world.item.Item[] kit={Veilborne.JOURNAL.get(),Veilborne.MOON_DEW.get(),Veilborne.MOON_SHARD.get(),Veilborne.MOON_GAUZE.get(),Veilborne.MOON_DIAL.get(),Veilborne.MOON_BELL_ITEM.get(),Veilborne.SILVER_LANTERN_ITEM.get()};
        for(int i=0;i<kit.length;i++)inv.setItem(i,new ItemStack(kit[i]));
        inv.selected=4;
        BlockPos g=garden(c.anchor);
        c.player.teleportTo(g.getX()+.5,g.getY(),g.getZ()+.5);
        Direction[] facing={Direction.NORTH,Direction.WEST,Direction.SOUTH};
        int[][] spots={{1,1},{2,1},{1,2}};
        for(int i=0;i<3;i++)c.level.setBlock(g.offset(spots[i][0],0,spots[i][1]),Veilborne.SILVER_LANTERN.get().defaultBlockState().setValue(SilverLanternBlock.FACING,facing[i]),3);
    }

    // ------------------------------------------------------------------ cameras (client)
    private static Vec3 eye(ArtPreview.Ctx c,Vec3 offset){return Vec3.atBottomCenterOf(garden(c.anchor)).add(0,1.62,0).add(offset);}
    /** Stand in the garden and face the moon, turned by {@code yaw} degrees and tipped by {@code pitch} from it. */
    static void moonShot(ArtPreview.Ctx c,Vec3 offset,float yaw,float pitch){
        if(MoonClient.state==null)return;
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        Vec3 d=MoonClient.trueDir(0);
        float my=(float)Math.toDegrees(Math.atan2(-d.x,d.z)),mp=(float)-Math.toDegrees(Math.asin(d.y));
        place(c,eye(c,offset));look(c,my+yaw,mp-pitch);
    }
    static void threeShot(ArtPreview.Ctx c){
        if(MoonClient.state==null)return;
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        Vec3 d=MoonTimeline.base(MoonClient.state.seed());
        place(c,eye(c,Vec3.ZERO));look(c,(float)Math.toDegrees(Math.atan2(-d.x,d.z)),-24);
    }
    static float yawAwayFromMoon(ArtPreview.Ctx c){Vec3 d=MoonClient.trueDir(0);return (float)Math.toDegrees(Math.atan2(d.x,-d.z));}
    static MoonProcession procession(ArtPreview.Ctx c){
        if(c.mc.level==null)return null;
        for(var e:c.mc.level.entitiesForRendering())if(e instanceof MoonProcession p)return p;
        return null;
    }
    static void orbitProcession(ArtPreview.Ctx c,double distance,double height,double angle){
        var p=procession(c);if(p==null)return;
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        double s=p.renderPath(0);
        Vec3 focus=p.member(s,MoonProcession.LITTER-1,0).add(0,1.4,0);
        double a=Math.toRadians(angle+p.yawAt(s,MoonProcession.LITTER));
        place(c,focus.add(-Math.sin(a)*distance,height,Math.cos(a)*distance));
        lookAt(c,focus);
    }
    static void faceProcession(ArtPreview.Ctx c){
        var p=procession(c);if(p==null)return;
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        double s=p.renderPath(0);
        Vec3 guide=p.member(s,0,0),ahead=p.member(s+2.2,0,.6);
        place(c,ahead.add(0,2.05,0));lookAt(c,guide.add(0,2.1,0));
    }
    static void escort(ArtPreview.Ctx c){
        var p=procession(c);if(p==null)return;
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        double s=p.renderPath(0);
        Vec3 beside=p.member(s-1,0,-7);
        place(c,beside.add(0,1.8,0));lookAt(c,p.member(s+14,0,0).add(0,.2,0));
        if(c.player!=null&&c.tick%10==0)c.level.getServer().execute(()->{if(c.player!=null)c.player.teleportTo(beside.x,beside.y,beside.z);});
    }
    static void emptyPlace(ArtPreview.Ctx c){
        var p=procession(c);if(p==null)return;
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        double s=p.renderPath(0);
        Vec3 empty=p.member(s,MoonProcession.EMPTY,0),stand=p.member(s,MoonProcession.EMPTY-1.5,-4.2);
        place(c,stand.add(0,1.62,0));lookAt(c,empty.add(0,1.1,0));
        if(c.tick%10==0)c.level.getServer().execute(()->{if(c.player!=null)c.player.teleportTo(stand.x,stand.y,stand.z);});
    }
    static void scatterShot(ArtPreview.Ctx c){
        orbitProcession(c,7,1.8,70);
        if(c.tick==76)c.level.getServer().execute(()->{
            for(var e:c.level.getAllEntities())if(e instanceof MoonProcession p){
                Vec3 m=p.member(p.path(),MoonProcession.LITTER,0);
                c.player.setGameMode(GameType.CREATIVE);c.player.teleportTo(m.x,m.y,m.z);
            }
        });
    }
    static void constellation(ArtPreview.Ctx c){
        if(MoonClient.state==null||MoonClient.state.littleMoon().equals(BlockPos.ZERO))return;
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        Vec3 base=Vec3.atBottomCenterOf(MoonClient.state.littleMoon());
        Vec3 spot=MoonTimeline.viewpoint(MoonClient.state.seed(),base);
        double y=MoonProcession.ground(c.mc.level,spot.x,spot.z);
        place(c,new Vec3(spot.x,y+1.62,spot.z));lookAt(c,base.add(0,MoonTimeline.LITTLE_MOON_HEIGHT,0));
    }
    static void journal(ArtPreview.Ctx c,int spread){
        c.mc.options.hideGui=true;
        moonShot(c,Vec3.ZERO,0,10);
        if(!(c.mc.screen instanceof TideJournalScreen)){var screen=new TideJournalScreen();c.mc.setScreen(screen);screen.showChapter(1);screen.showSpread(spread);}
    }
    private static void aim(ArtPreview.Ctx c,Vec3 eye,Vec3 focus){
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        Vec3 base=Vec3.atBottomCenterOf(garden(c.anchor));
        place(c,base.add(eye));lookAt(c,base.add(focus));
    }
    private static void place(ArtPreview.Ctx c,Vec3 eye){
        var p=c.mc.player;double y=eye.y-p.getEyeHeight();
        p.setPos(eye.x,y,eye.z);p.xo=p.xOld=eye.x;p.yo=p.yOld=y;p.zo=p.zOld=eye.z;
    }
    private static void lookAt(ArtPreview.Ctx c,Vec3 focus){
        var p=c.mc.player;Vec3 d=focus.subtract(p.getEyePosition());
        look(c,(float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));
    }
    private static void look(ArtPreview.Ctx c,float yaw,float pitch){
        var p=c.mc.player;p.setYRot(yaw);p.setXRot(pitch);p.yRotO=yaw;p.xRotO=pitch;p.setYHeadRot(yaw);p.yHeadRotO=yaw;p.yBodyRot=yaw;p.yBodyRotO=yaw;
    }
    private MoonPreview(){}
}
