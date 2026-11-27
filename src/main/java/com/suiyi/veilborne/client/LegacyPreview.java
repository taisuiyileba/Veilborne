package com.suiyi.veilborne.client;

import com.suiyi.veilborne.*;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Art review of what the weathers leave behind (-PtidePreviewScenes=legacy): the journal's fifth spread in every chapter,
 * the voyage's sinking into the sea above and coming ashore, the cracked mirror's heart showing what is hidden, the light
 * over a tower's buried foundation, sewn lamps near and far, the star-tide pool, a grown dream tree's heart and the new
 * keepsakes in the hotbar. Staged on the sea stage by the anchor (its pond is at x 4..14, z 6..18).
 */
final class LegacyPreview {
    static final String[] CHAPTERS={"sea","moon","mirror","thread","hush","dream"};
    static final int[] STAGES={45,66,45,60,55,56};
    static void scenes(List<ArtPreview.Scene> out){
        for(int w=0;w<CHAPTERS.length;w++){int chapter=w;
            out.add(new ArtPreview.Scene("legacy_journal_"+CHAPTERS[w],STAGES[w],c->grades(c,chapter),c->journal(c,chapter),w));}
        out.add(new ArtPreview.Scene("legacy_voyage",45,LegacyPreview::quiet,c->{if(c.tick==2)VeilClient.debugVoyage(false);view(c,new Vec3(0,1.6,0),30,-20);}));
        out.add(new ArtPreview.Scene("legacy_voyage_ashore",45,LegacyPreview::quiet,c->{if(c.tick==2)VeilClient.debugVoyage(false);if(c.tick==84)VeilClient.debugVoyage(true);view(c,new Vec3(0,1.6,0),30,-20);}));
        out.add(new ArtPreview.Scene("legacy_reveal",3,LegacyPreview::cache,c->{if(c.tick==2)VeilClient.debugReveal(hidden(c.anchor),600);aimAt(c,new Vec3(-2,7,-4),new Vec3(-3,-4,7));}));
        out.add(new ArtPreview.Scene("legacy_vault",45,LegacyPreview::vault,c->{
            BlockPos base=MirrorExtras.towerBase;
            if(c.tick==4&&!base.equals(BlockPos.ZERO))VeilClient.debugBeam(Vec3.atBottomCenterOf(base));
            if(!base.equals(BlockPos.ZERO)){
                Vec3 eye=Vec3.atBottomCenterOf(base).add(13,0,-10);
                eye=new Vec3(eye.x,Math.max(base.getY()+3,Surface.top(c.mc.level,(int)Math.floor(eye.x),(int)Math.floor(eye.z))+2.5),eye.z);
                place(c,eye,Vec3.atBottomCenterOf(base).add(0,6,0));
            }
        },2));
        out.add(new ArtPreview.Scene("legacy_loom",3,LegacyPreview::lamps,c->aimAt(c,new Vec3(-2,2.4,2),new Vec3(-8,3.5,-6))));
        out.add(new ArtPreview.Scene("legacy_pool",3,LegacyPreview::pool,c->aimAt(c,new Vec3(3,2.2,3),new Vec3(9,0,12))));
        out.add(new ArtPreview.Scene("legacy_heart",2,LegacyPreview::tree,c->aimAt(c,new Vec3(-5,1.7,6),new Vec3(-10,1.2,12)),5));
        out.add(new ArtPreview.Scene("legacy_items",45,LegacyPreview::kit,c->{c.mc.options.hideGui=false;aimAt(c,new Vec3(-1.5,1.9,-2.5),new Vec3(1.5,.3,1.5));}));
    }

    // ------------------------------------------------------------------ stages
    private static void quiet(ArtPreview.Ctx c){
        for(var e:com.google.common.collect.ImmutableList.copyOf(c.level.getAllEntities()))if(e instanceof TideSkiff s)s.finish();
        c.player.setGameMode(GameType.SPECTATOR);
    }
    /** Some completeness to show: three stars last time, this one under way with a few deeds, and a remembered place. */
    private static void grades(ArtPreview.Ctx c,int w){
        quiet(c);
        CompoundTag t=new CompoundTag();
        t.putIntArray("Last",new int[]{3,2,3,2,1,3});t.putIntArray("Best",new int[]{3,3,3,2,2,3});
        t.putIntArray("LastDeeds",new int[]{0b1011111,0b11111,0b111111,0b100111,0b10011,0b111111});
        CompoundTag live=new CompoundTag();live.putInt("W",w);live.putLong("S",99);
        live.putInt("D",new int[]{0b10111,0b10011,0b100011,0b100101,0b1011,0b1101}[w]);
        t.put("Live",live);
        c.player.getPersistentData().put(VeilGrade.KEY,t);
        BlockPos a=c.anchor;long until=c.level.getGameTime()+2*24000+5000;
        VeilGrade.site(c.player,VeilGrade.SITE_VOYAGE,a.offset(-812,-18,1290),a,0);
        VeilGrade.site(c.player,VeilGrade.SITE_POOL,a.offset(6,-1,9),a.offset(6,-1,9),until);
        VeilGrade.site(c.player,VeilGrade.SITE_VAULT,a.offset(24,0,-27),a.offset(24,-7,-28),0);
        VeilGrade.site(c.player,VeilGrade.SITE_PAIR,a.offset(-19,0,12),a.offset(170,4,-91),0);
        VeilGrade.sync(c.player);
    }
    private static void journal(ArtPreview.Ctx c,int w){
        c.mc.options.hideGui=true;
        view(c,new Vec3(0,1.6,0),0,-10);
        if(!(c.mc.screen instanceof TideJournalScreen)){var screen=new TideJournalScreen();c.mc.setScreen(screen);screen.showChapter(w);screen.showSpread(4);}
    }
    /** Chests, a barrel and a spawner buried under the stage. */
    static List<BlockPos> hidden(BlockPos a){return List.of(a.offset(-6,-4,4),a.offset(-3,-6,8),a.offset(1,-5,10),a.offset(-8,-3,11),a.offset(-1,-7,3));}
    private static void cache(ArtPreview.Ctx c){
        quiet(c);
        List<BlockPos> spots=hidden(c.anchor);
        for(int i=0;i<spots.size();i++)c.level.setBlock(spots.get(i),(i==3?Blocks.SPAWNER:i==2?Blocks.BARREL:Blocks.CHEST).defaultBlockState(),3);
    }
    private static void vault(ArtPreview.Ctx c){
        c.player.setGameMode(GameType.SPECTATOR);
        MirrorData d=MirrorData.get(c.level);
        BlockPos base=MirrorVault.base(c.level,d.center,d.seed);
        if(base!=null&&!d.has(MirrorData.VAULT)){d.takeFlag(MirrorData.VAULT);d.vault=MirrorVault.build(c.level,base,d.seed);}
    }
    private static void lamps(ArtPreview.Ctx c){
        quiet(c);
        BlockPos a=c.anchor.offset(-8,0,-6),b=c.anchor.offset(-4,0,-9),far=c.anchor.offset(-11,0,-2);
        for(BlockPos p:List.of(a,b,far))c.level.setBlock(p,Veilborne.LOOM_LANTERN.get().defaultBlockState(),3);
        LoomLinks.link(c.level,a,c.level,b);
        var e=LoomLinks.lamp(c.level,far);
        if(e!=null)e.setPartner(c.anchor.offset(-420,0,-260),c.level.dimension(),true);
    }
    private static void pool(ArtPreview.Ctx c){
        quiet(c);
        var d=TideData.get(c.level);d.pools.clear();d.pools.add(new TideData.Pool(c.anchor.offset(9,-1,12),c.level.getGameTime()+72000));d.setDirty();
        VeilGrade.broadcast(c.level);
    }
    private static void tree(ArtPreview.Ctx c){
        c.player.setGameMode(GameType.SPECTATOR);
        BlockPos base=c.anchor.offset(-10,0,12);
        if(c.level.getBlockState(base).is(Veilborne.DREAM_HEART.get()))return;
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)c.level.setBlock(base.offset(x,-1,z),Blocks.GRASS_BLOCK.defaultBlockState(),3);
        DreamBlocks.grow(c.level,base,true,net.minecraft.util.RandomSource.create(17));
        for(int x=-3;x<=3;x++)for(int z=-4;z<=-2;z++){
            c.level.setBlock(base.offset(x,-1,z),Blocks.FARMLAND.defaultBlockState(),3);
            c.level.setBlock(base.offset(x,0,z),Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE,(x+z+7)%8),3);
        }
    }
    private static void kit(ArtPreview.Ctx c){
        quiet(c);
        c.player.setGameMode(GameType.CREATIVE);
        var inv=c.player.getInventory();inv.clearContent();
        ItemStack[] kit={new ItemStack(Veilborne.HOMEWARD_SHELL.get()),TideService.chartBottle(),new ItemStack(Veilborne.NEEDLE_CORE.get()),new ItemStack(Veilborne.MIRROR_HEART.get()),
            net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION,Veilborne.MOONVEIL_POTION),new ItemStack(Veilborne.SILVER_LANTERN_ITEM.get()),
            new ItemStack(Veilborne.SILENT_LANTERN_ITEM.get()),new ItemStack(Veilborne.LOOM_LANTERN_ITEM.get()),new ItemStack(Veilborne.DREAM_LEAVES_ITEM.get())};
        for(int i=0;i<kit.length;i++)inv.setItem(i,kit[i]);
        inv.selected=0;
    }

    // ------------------------------------------------------------------ cameras
    private static void view(ArtPreview.Ctx c,Vec3 offset,float yaw,float pitch){
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        Vec3 eye=Vec3.atBottomCenterOf(c.anchor).add(offset);
        var p=c.mc.player;double y=eye.y-p.getEyeHeight();
        p.setPos(eye.x,y,eye.z);p.xo=p.xOld=eye.x;p.yo=p.yOld=y;p.zo=p.zOld=eye.z;
        p.setYRot(yaw);p.setXRot(pitch);p.yRotO=yaw;p.xRotO=pitch;p.setYHeadRot(yaw);p.yHeadRotO=yaw;
    }
    private static void aimAt(ArtPreview.Ctx c,Vec3 eye,Vec3 focus){
        Vec3 base=Vec3.atBottomCenterOf(c.anchor);
        place(c,base.add(eye),base.add(focus));
    }
    private static void place(ArtPreview.Ctx c,Vec3 eye,Vec3 focus){
        c.mc.options.setCameraType(CameraType.FIRST_PERSON);
        var p=c.mc.player;double y=eye.y-p.getEyeHeight();
        p.setPos(eye.x,y,eye.z);p.xo=p.xOld=eye.x;p.yo=p.yOld=y;p.zo=p.zOld=eye.z;
        Vec3 d=focus.subtract(eye);
        float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z)),pitch=(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));
        p.setYRot(yaw);p.setXRot(pitch);p.yRotO=yaw;p.xRotO=pitch;p.setYHeadRot(yaw);p.yHeadRotO=yaw;
    }
    private LegacyPreview(){}
}
