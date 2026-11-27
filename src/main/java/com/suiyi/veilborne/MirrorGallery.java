package com.suiyi.veilborne;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * 镜廊: the short corridor behind the extra door, in a small void dimension of its own so the Overworld is never
 * touched. One long wall is a mirror. The reflection in it is late by a beat, and it holds one thing the real
 * corridor does not: something on the far pedestal. Stand on the gold tile before the pedestal and wait for your
 * reflection to catch up; it takes the thing down and it is yours.
 *
 * Every way out leads back to where you stepped in: the door you came through, the weather ending, a time limit,
 * falling, logging back in after the weather, or no valid record at all (then the world spawn). Nothing can be
 * broken or placed here and nothing hurts you.
 */
public final class MirrorGallery {
    public static final ResourceKey<Level> GALLERY=ResourceKey.create(Registries.DIMENSION,Veilborne.id("mirror_gallery"));
    public static final String TAG="VeilborneGallery", REWARD="VeilborneGalleryReward";
    /** Hall layout, relative to the slot origin: interior x -2..2, floor y -1, ceiling y 4, entry wall z -1, end wall z 26. */
    public static final int LENGTH=26, SPACING=512, Y=64, TIME_LIMIT=4000, WARN_AT=3400;
    /** The mirror is the east face of the west wall. */
    public static final double MIRROR_X=-2.0;
    public static final Vec3 PLATE=new Vec3(.5,0,21.5), PEDESTAL=new Vec3(.5,1.3,23.5);
    public static final int PLATE_TICKS=28;
    private static long builtFor=Long.MIN_VALUE;

    public static boolean inGallery(Level level){return level.dimension().equals(GALLERY);}
    public static ServerLevel level(MinecraftServer server){return server.getLevel(GALLERY);}
    public static int slot(long seed){return 1+(int)Math.floorMod(seed,48L);}
    public static BlockPos origin(long seed){return new BlockPos(slot(seed)*SPACING,Y,0);}
    /** Slot origin of the hall a position is in (clients use this to find the mirror). */
    public static BlockPos originAt(double x){return new BlockPos((int)Math.round(x/SPACING)*SPACING,Y,0);}

    // ------------------------------------------------------------------ building
    public static void build(ServerLevel g,BlockPos o){
        for(int cx=(o.getX()-8)>>4;cx<=(o.getX()+8)>>4;cx++)for(int cz=-1;cz<=2;cz++)g.getChunk(cx,cz);
        BlockState air=Blocks.AIR.defaultBlockState();
        for(int x=-6;x<=6;x++)for(int y=-4;y<=7;y++)for(int z=-4;z<=LENGTH+3;z++)set(g,o,x,y,z,air);
        // A dark hall lit only by its lanterns. Pale quartz and calcite, deepslate, a little gold: 青金与浅灰.
        // Every fifth step a rib of quartz crosses floor, walls and vault, with a lantern hung beneath it.
        for(int x=-3;x<=3;x++)for(int z=-1;z<=LENGTH;z++){
            boolean rib=z%5==2;
            set(g,o,x,-2,z,Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            BlockState floor=x==0?(rib?Blocks.CHISELED_QUARTZ_BLOCK:Blocks.SMOOTH_QUARTZ).defaultBlockState()
                :rib?Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                :Math.abs(x)==1?Blocks.DEEPSLATE_TILES.defaultBlockState():Blocks.POLISHED_DEEPSLATE.defaultBlockState();
            set(g,o,x,-1,z,floor);
            set(g,o,x,4,z,rib?Blocks.QUARTZ_BRICKS.defaultBlockState():Math.abs(x)>=2?Blocks.DEEPSLATE_BRICKS.defaultBlockState():Blocks.DEEPSLATE_TILES.defaultBlockState());
            set(g,o,x,5,z,Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        }
        set(g,o,0,-1,21,Blocks.GOLD_BLOCK.defaultBlockState());
        for(int z=-1;z<=LENGTH;z++)for(int y=0;y<=3;y++){
            // West: the mirror's dark backing. East: pale calcite between quartz pillars, windows onto the silver void.
            set(g,o,-3,y,z,Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            boolean pillar=z%5==2,window=(z%5==0||z%5==4)&&(y==1||y==2)&&z>0&&z<LENGTH-1;
            BlockState east=pillar?(y==3?Blocks.CHISELED_QUARTZ_BLOCK:Blocks.QUARTZ_PILLAR).defaultBlockState()
                :window?Blocks.GLASS.defaultBlockState():y==0?Blocks.POLISHED_DEEPSLATE.defaultBlockState():Blocks.CALCITE.defaultBlockState();
            set(g,o,3,y,z,east);
        }
        for(int x=-2;x<=2;x++)for(int y=0;y<=3;y++){
            set(g,o,x,y,-1,y==0?Blocks.POLISHED_DEEPSLATE.defaultBlockState():Blocks.CALCITE.defaultBlockState());
            set(g,o,x,y,LENGTH,y>=1&&y<=2&&Math.abs(x)<=1?Blocks.GLASS.defaultBlockState():Blocks.CALCITE.defaultBlockState());
        }
        for(int z=2;z<LENGTH;z+=5)set(g,o,0,3,z,Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true));
        // Between the windows, mirror lamps turned toward the glass, so the hall is lit in the mirror too.
        for(int z=4;z<LENGTH-1;z+=5)set(g,o,2,0,z,Veilborne.MIRROR_LANTERN.get().defaultBlockState().setValue(MirrorLanternBlock.FACING,Direction.WEST));
        // The pedestal: a quartz column with a copper cap, bare on this side of the glass.
        set(g,o,0,0,23,Blocks.QUARTZ_PILLAR.defaultBlockState());
        set(g,o,0,1,23,Blocks.WAXED_CUT_COPPER_SLAB.defaultBlockState());
        builtFor=o.asLong();
    }
    private static void set(ServerLevel g,BlockPos o,int x,int y,int z,BlockState s){g.setBlock(o.offset(x,y,z),s,2|16);}
    private static boolean intact(ServerLevel g,BlockPos o){return g.getBlockState(o.offset(0,1,23)).is(Blocks.WAXED_CUT_COPPER_SLAB)&&g.getBlockState(o.offset(-3,1,10)).is(Blocks.POLISHED_BLACKSTONE);}

    // ------------------------------------------------------------------ in and out
    /** Steps a player through the revealed door. Returns false if the gallery is unavailable. */
    public static boolean enter(ServerPlayer p,MirrorData d){
        ServerLevel g=level(p.server);
        if(g==null||!TideConfig.MIRROR_GALLERY.get())return false;
        BlockPos o=origin(d.seed);
        if(builtFor!=o.asLong()||!intact(g,o))build(g,o);
        CompoundTag tag=new CompoundTag();
        Vec3 n=MirrorTimeline.faceNormal(d.doorFace);
        // Come back out standing just in front of the door, facing away from the wall.
        Vec3 back=d.has(MirrorData.FREESTANDING)?Vec3.atBottomCenterOf(d.door).add(n.scale(1.4)):Vec3.atBottomCenterOf(d.door).add(n.scale(1.05));
        tag.putString("Dim",p.level().dimension().location().toString());
        tag.putDouble("X",back.x);tag.putDouble("Y",back.y);tag.putDouble("Z",back.z);
        tag.putFloat("Yaw",(float)Math.toDegrees(Math.atan2(-n.x,n.z)));tag.putFloat("Pitch",0);
        tag.putLong("Seed",d.seed);tag.putLong("At",p.server.overworld().getGameTime());
        p.getPersistentData().put(TAG,tag);
        p.teleportTo(g,o.getX()+.5,o.getY(),o.getZ()+1.5,0,0);
        g.playSound(null,o.offset(0,1,1),Veilborne.MIRROR_DOOR.get(),SoundSource.AMBIENT,.9f,1);
        p.displayClientMessage(Component.translatable("message.veilborne.gallery_enter"),true);
        return true;
    }
    /** Back to where the player came in, or somewhere safe if that record is missing or the spot is now blocked. */
    public static void leave(ServerPlayer p){
        CompoundTag tag=p.getPersistentData().getCompound(TAG);
        p.getPersistentData().remove(TAG);
        MinecraftServer server=p.server;
        ServerLevel home=null;
        if(tag.contains("Dim")){
            ResourceLocation dim=ResourceLocation.tryParse(tag.getString("Dim"));
            if(dim!=null)home=server.getLevel(ResourceKey.create(Registries.DIMENSION,dim));
        }
        if(home==null||inGallery(home)){
            home=server.overworld();
            BlockPos spawn=home.getSharedSpawnPos();BlockPos safe=TideService.findSafe(home,spawn,8);
            BlockPos at=safe!=null?safe:home.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,spawn);
            p.teleportTo(home,at.getX()+.5,at.getY(),at.getZ()+.5,p.getYRot(),0);
        }else{
            BlockPos at=BlockPos.containing(tag.getDouble("X"),tag.getDouble("Y"),tag.getDouble("Z"));
            boolean clear=home.getBlockState(at).getCollisionShape(home,at).isEmpty()&&home.getBlockState(at.above()).getCollisionShape(home,at.above()).isEmpty();
            if(!clear){BlockPos safe=TideService.findSafe(home,at,6);if(safe!=null)at=safe;}
            double x=clear?tag.getDouble("X"):at.getX()+.5,y=clear?tag.getDouble("Y"):at.getY(),z=clear?tag.getDouble("Z"):at.getZ()+.5;
            p.teleportTo(home,x,y,z,tag.getFloat("Yaw"),tag.getFloat("Pitch"));
        }
        p.fallDistance=0;
        p.level().playSound(null,p.blockPosition(),Veilborne.MIRROR_DOOR.get(),SoundSource.PLAYERS,.7f,.8f);
    }
    /** Server tick for someone inside: the pedestal, the time limit, the weather, a fall into the void. */
    public static void tick(ServerPlayer p,MirrorData d){
        CompoundTag tag=p.getPersistentData().getCompound(TAG);
        long now=p.server.overworld().getGameTime();
        boolean valid=tag.contains("Seed")&&d.active&&tag.getLong("Seed")==d.seed&&MirrorTimeline.galleryOpen(d.progress());
        long inside=now-tag.getLong("At");
        if(!valid||inside>TIME_LIMIT||p.getY()<Y-24){
            if(valid&&inside>TIME_LIMIT)p.displayClientMessage(Component.translatable("message.veilborne.gallery_time"),false);
            else if(tag.contains("Seed"))p.displayClientMessage(Component.translatable("message.veilborne.gallery_closing"),false);
            leave(p);return;
        }
        if(inside==WARN_AT)p.displayClientMessage(Component.translatable("message.veilborne.gallery_warn"),true);
        BlockPos o=originAt(p.getX());
        Vec3 plate=Vec3.atLowerCornerOf(o).add(PLATE);
        CompoundTag data=p.getPersistentData();
        if(Math.hypot(p.getX()-plate.x,p.getZ()-plate.z)<.9&&Math.abs(p.getY()-plate.y)<1.2&&data.getLong(REWARD)!=d.seed){
            int t=data.getInt(REWARD+"Ticks")+1;data.putInt(REWARD+"Ticks",t);
            if(t>=PLATE_TICKS){
                data.putLong(REWARD,d.seed);data.remove(REWARD+"Ticks");
                TideService.give(p,new ItemStack(Veilborne.ECHO_MIRROR.get()));
                TideService.give(p,new ItemStack(Veilborne.MIRROR_SAND.get(),2));
                TideService.record(p,MirrorService.CORRIDOR);
                VeilGrade.deed(p,VeilGrade.MIRROR,d.seed,VeilGrade.MIRROR_GALLERY);
                p.serverLevel().playSound(null,BlockPos.containing(Vec3.atLowerCornerOf(o).add(PEDESTAL)),Veilborne.MIRROR_CHIME.get(),SoundSource.AMBIENT,1,.8f);
                p.displayClientMessage(Component.translatable("message.veilborne.gallery_reward"),false);
                MirrorService.sync(p);
            }
        }else data.remove(REWARD+"Ticks");
    }
    /** The wall blocks that act as the way back, on the inside face of the entry wall. */
    public static boolean isExit(BlockPos pos,BlockPos origin){
        return pos.getX()==origin.getX()&&pos.getZ()==origin.getZ()-1&&(pos.getY()==origin.getY()||pos.getY()==origin.getY()+1);
    }

    // ------------------------------------------------------------------ safety rules
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){
        if(e.getEntity() instanceof ServerPlayer p&&inGallery(p.level())){
            MirrorData d=MirrorData.get(p.serverLevel());
            CompoundTag tag=p.getPersistentData().getCompound(TAG);
            long inside=p.server.overworld().getGameTime()-tag.getLong("At");
            if(!tag.contains("Seed")||!d.active||tag.getLong("Seed")!=d.seed||!MirrorTimeline.galleryOpen(d.progress())||inside>TIME_LIMIT)leave(p);
        }
    }
    @SubscribeEvent public static void changed(PlayerEvent.PlayerChangedDimensionEvent e){
        if(e.getFrom().equals(GALLERY))e.getEntity().getPersistentData().remove(TAG);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){
        if(!inGallery(e.getEntity().level()))e.getEntity().getPersistentData().remove(TAG);
        else if(e.getEntity() instanceof ServerPlayer p)leave(p);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){
        var old=e.getOriginal().getPersistentData();
        if(old.contains(REWARD))e.getEntity().getPersistentData().putLong(REWARD,old.getLong(REWARD));
    }
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent e){
        if(e.getLevel() instanceof Level l&&inGallery(l)&&!e.getPlayer().isCreative())e.setCanceled(true);
    }
    @SubscribeEvent public static void placeBlock(BlockEvent.EntityPlaceEvent e){
        if(e.getLevel() instanceof Level l&&inGallery(l)&&!(e.getEntity() instanceof net.minecraft.world.entity.player.Player pl&&pl.isCreative()))e.setCanceled(true);
    }
    @SubscribeEvent public static void hurt(LivingIncomingDamageEvent e){
        if(e.getEntity() instanceof ServerPlayer&&inGallery(e.getEntity().level())&&!e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))e.setCanceled(true);
    }
    @SubscribeEvent public static void use(PlayerInteractEvent.RightClickBlock e){
        if(!(e.getEntity() instanceof ServerPlayer p)||!inGallery(p.level()))return;
        if(isExit(e.getPos(),originAt(e.getPos().getX()))&&e.getFace()==Direction.SOUTH){leave(p);e.setCanceled(true);}
    }
    private MirrorGallery(){}
}
