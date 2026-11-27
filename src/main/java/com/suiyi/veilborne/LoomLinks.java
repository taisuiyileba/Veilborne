package com.suiyi.veilborne;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Two sky-weaving lamps sewn together with scarlet thread are one place: use one with an empty hand and the thread draws you
 * in, and a moment later you step out beside the other. Thread one lamp and then another while holding scarlet thread to sew
 * them; the longer the thread has to be, the more it takes (one per {@link #SPAN} blocks). A needle core carried at the time
 * makes any seam cost a single thread, and is the only way to sew across to another dimension. Each lamp has one other end;
 * sewing it anew cuts the old thread.
 *
 * The scarlet seam leaves such a pair behind by itself, at the two feet of the world stitch. The far foot is usually beyond
 * where anyone has loaded the land: its lamp is set down when the ground is next loaded, or when someone first travels there.
 */
public final class LoomLinks {
    public static final int PULL_TICKS=30, SPAN=64, REACH=3;
    public static final String THREADING="VeilborneThreading";
    record Pull(BlockPos from,ResourceKey<Level> dim,int at){}
    private static final Map<UUID,Pull> pulls=new HashMap<>();

    public static int cost(BlockPos a,BlockPos b,boolean cross,boolean core){
        if(core)return 1;
        return Math.max(1,(int)Math.ceil(Math.sqrt(a.distSqr(b))/SPAN));
    }
    public static ThreadBlocks.LoomLink lamp(Level level,BlockPos pos){
        return level.getBlockState(pos).is(Veilborne.LOOM_LANTERN.get())&&level.getBlockEntity(pos) instanceof ThreadBlocks.LoomLink l?l:null;
    }
    static int threads(ServerPlayer p){
        int n=0;for(ItemStack s:p.getInventory().items)if(s.is(Veilborne.SCARLET_THREAD.get()))n+=s.getCount();
        if(p.getOffhandItem().is(Veilborne.SCARLET_THREAD.get()))n+=p.getOffhandItem().getCount();
        return n;
    }
    static boolean core(ServerPlayer p){return p.getInventory().contains(new ItemStack(Veilborne.NEEDLE_CORE.get()));}
    static void consume(ServerPlayer p,net.minecraft.world.item.Item item,int n){
        List<ItemStack> stacks=new ArrayList<>(p.getInventory().items);stacks.add(p.getOffhandItem());
        for(ItemStack s:stacks){if(n<=0)break;if(!s.is(item))continue;int k=Math.min(n,s.getCount());s.shrink(k);n-=k;}
    }

    // ------------------------------------------------------------------ sewing two lamps together
    /** Scarlet thread used on a lamp: the first one is threaded, the second is sewn to it. */
    public static ItemInteractionResult thread(ServerPlayer p,ServerLevel level,BlockPos pos){
        CompoundTag data=p.getPersistentData();
        if(!data.contains(THREADING,Tag.TAG_COMPOUND)){
            CompoundTag t=new CompoundTag();t.putLong("Pos",pos.asLong());t.putString("Dim",level.dimension().location().toString());
            data.put(THREADING,t);
            level.playSound(null,pos,Veilborne.THREAD_CHIME.get(),SoundSource.BLOCKS,.7f,1.3f);
            p.displayClientMessage(Component.translatable("message.veilborne.loom_threaded"),true);
            return ItemInteractionResult.SUCCESS;
        }
        CompoundTag t=data.getCompound(THREADING);
        BlockPos from=BlockPos.of(t.getLong("Pos"));
        ServerLevel fromLevel=p.server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(t.getString("Dim"))));
        if(fromLevel==level&&from.equals(pos)){
            data.remove(THREADING);
            p.displayClientMessage(Component.translatable("message.veilborne.loom_unthreaded"),true);
            return ItemInteractionResult.SUCCESS;
        }
        if(fromLevel==null||lamp(fromLevel,from)==null){
            data.remove(THREADING);
            p.displayClientMessage(Component.translatable("message.veilborne.loom_lost"),true);
            return ItemInteractionResult.FAIL;
        }
        boolean cross=fromLevel!=level,core=core(p);
        if(cross&&!core){p.displayClientMessage(Component.translatable("message.veilborne.loom_needs_core"),true);return ItemInteractionResult.FAIL;}
        int cost=cost(from,pos,cross,core),have=threads(p);
        if(have<cost&&!p.isCreative()){p.displayClientMessage(Component.translatable("message.veilborne.loom_short",cost,have),true);return ItemInteractionResult.FAIL;}
        if(!p.isCreative()){consume(p,Veilborne.SCARLET_THREAD.get(),cost);if(core)consume(p,Veilborne.NEEDLE_CORE.get(),1);}
        link(fromLevel,from,level,pos);
        data.remove(THREADING);
        level.playSound(null,pos,Veilborne.THREAD_KNOT.get(),SoundSource.BLOCKS,1,1.1f);
        fromLevel.playSound(null,from,Veilborne.THREAD_KNOT.get(),SoundSource.BLOCKS,1,1.1f);
        p.displayClientMessage(Component.translatable(core?"message.veilborne.loom_linked_core":"message.veilborne.loom_linked",cost),false);
        VeilGrade.site(p,VeilGrade.SITE_PAIR,from,pos,0);
        return ItemInteractionResult.SUCCESS;
    }
    /** Sews a to b, cutting whatever either was sewn to before. */
    public static void link(ServerLevel la,BlockPos a,ServerLevel lb,BlockPos b){
        cut(la,a);cut(lb,b);
        var ea=lamp(la,a);var eb=lamp(lb,b);
        if(ea!=null)ea.setPartner(b,lb.dimension(),false);
        if(eb!=null)eb.setPartner(a,la.dimension(),false);
    }
    /** Cuts this lamp's thread, and the other end's too if it still leads here. */
    public static void cut(ServerLevel level,BlockPos pos){
        var e=lamp(level,pos);
        if(e==null||!e.linked())return;
        ServerLevel other=level.getServer().getLevel(e.partnerDim());
        if(other!=null&&other.isLoaded(e.partner())){var o=lamp(other,e.partner());if(o!=null&&pos.equals(o.partner()))o.clear();}
        e.clear();
    }
    /** A lamp is broken: the far end forgets it. */
    public static void broken(ServerLevel level,BlockPos pos,ThreadBlocks.LoomLink e){
        if(e==null||!e.linked())return;
        ServerLevel other=level.getServer().getLevel(e.partnerDim());
        if(other!=null&&other.isLoaded(e.partner())){var o=lamp(other,e.partner());if(o!=null&&pos.equals(o.partner()))o.clear();}
    }

    // ------------------------------------------------------------------ being drawn along the thread
    public static InteractionResult use(ServerPlayer p,ServerLevel level,BlockPos pos){
        var e=lamp(level,pos);
        if(e==null||!e.linked()){p.displayClientMessage(Component.translatable("message.veilborne.loom_alone"),true);return InteractionResult.SUCCESS;}
        if(p.isSecondaryUseActive()){p.displayClientMessage(where(level,pos,e),true);return InteractionResult.SUCCESS;}
        if(pulls.containsKey(p.getUUID()))return InteractionResult.SUCCESS;
        pulls.put(p.getUUID(),new Pull(pos.immutable(),level.dimension(),p.server.getTickCount()));
        level.playSound(null,pos,Veilborne.THREAD_TUG.get(),SoundSource.BLOCKS,.8f,1.2f);
        VeilService.cue(level,new VeilCue(VeilCue.PULL,Vec3.atCenterOf(pos),PULL_TICKS),48);
        p.displayClientMessage(Component.translatable("message.veilborne.loom_pulling"),true);
        return InteractionResult.SUCCESS;
    }
    static Component where(ServerLevel level,BlockPos pos,ThreadBlocks.LoomLink e){
        if(!e.partnerDim().equals(level.dimension()))return Component.translatable("message.veilborne.loom_where_dim",
            Component.translatableWithFallback(e.partnerDim().location().toLanguageKey("dimension.veilborne"),e.partnerDim().location().toString()));
        BlockPos b=e.partner();
        double a=Math.toDegrees(Math.atan2(b.getX()-pos.getX(),-(b.getZ()-pos.getZ())));
        String[] dirs={"n","ne","e","se","s","sw","w","nw"};
        String dir=dirs[Math.floorMod((int)Math.round(a/45),8)];
        return Component.translatable("message.veilborne.loom_where",Component.translatable("direction.veilborne."+dir),(int)Math.round(Math.sqrt(b.distSqr(pos))));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){
        MinecraftServer server=e.getServer();
        if(server.getTickCount()%100==0)place(server.overworld());
        if(pulls.isEmpty())return;
        for(var it=pulls.entrySet().iterator();it.hasNext();){
            var entry=it.next();Pull pull=entry.getValue();
            ServerPlayer p=server.getPlayerList().getPlayer(entry.getKey());
            if(p==null||!p.level().dimension().equals(pull.dim())||p.position().distanceTo(Vec3.atBottomCenterOf(pull.from()))>REACH){
                if(p!=null)p.displayClientMessage(Component.translatable("message.veilborne.loom_let_go"),true);
                it.remove();continue;
            }
            if(server.getTickCount()-pull.at()<PULL_TICKS)continue;
            it.remove();
            travel(p,p.serverLevel(),pull.from());
        }
    }
    public static void travel(ServerPlayer p,ServerLevel level,BlockPos from){
        var e=lamp(level,from);
        if(e==null||!e.linked())return;
        ServerLevel there=p.server.getLevel(e.partnerDim());
        if(there==null){p.displayClientMessage(Component.translatable("message.veilborne.loom_broken"),true);return;}
        BlockPos to=e.partner();
        there.getChunk(to.getX()>>4,to.getZ()>>4);
        if(e.pending())to=settle(there,e,level,from);
        var o=to==null?null:lamp(there,to);
        if(o==null||!from.equals(o.partner())||!o.partnerDim().equals(level.dimension())){
            e.clear();
            p.displayClientMessage(Component.translatable("message.veilborne.loom_broken"),true);
            return;
        }
        BlockPos stand=TideService.findSafe(there,to,2);
        Vec3 at=stand!=null?Vec3.atBottomCenterOf(stand):Vec3.atBottomCenterOf(to).add(0,1,0);
        float yaw=(float)Math.toDegrees(Math.atan2(-(at.x-to.getX()-.5),at.z-to.getZ()-.5));
        level.playSound(null,from,Veilborne.THREAD_SNAP.get(),SoundSource.BLOCKS,.6f,1.4f);
        p.teleportTo(there,at.x,at.y,at.z,Float.isNaN(yaw)?p.getYRot():yaw,p.getXRot());
        p.fallDistance=0;
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,40,0,false,false,true));
        there.sendParticles(Veilborne.THREAD_FIBER.get(),at.x,at.y+1,at.z,24,.4,.8,.4,.02);
        there.playSound(null,to,Veilborne.THREAD_KNOT.get(),SoundSource.BLOCKS,.9f,1.2f);
    }

    // ------------------------------------------------------------------ the pair the seam leaves
    /** Sets a lamp down at a foot of the world stitch; returns where, or null if there is no room. */
    static BlockPos plant(ServerLevel level,BlockPos near){
        BlockPos at=TideService.findSafe(level,near,4);
        if(at==null)return null;
        level.setBlock(at,Veilborne.LOOM_LANTERN.get().defaultBlockState(),3);
        return at;
    }
    /** The seam is sewn and cut: a lamp at each foot, sewn to each other. Returns the near lamp, or null. */
    public static BlockPos leavePair(ServerLevel level,BlockPos a,BlockPos b){
        BlockPos la=plant(level,a);
        if(la==null)return null;
        if(level.hasChunk(b.getX()>>4,b.getZ()>>4)){
            BlockPos lb=plant(level,b);
            if(lb!=null){link(level,la,level,lb);return la;}
        }
        var e=lamp(level,la);
        if(e!=null)e.setPartner(b,level.dimension(),true);
        Pending.get(level).add(b,la);
        return la;
    }
    /** The far lamp of a pair, set down now that its ground is here; returns where it went, or null. */
    static BlockPos settle(ServerLevel level,ThreadBlocks.LoomLink near,ServerLevel nearLevel,BlockPos nearPos){
        BlockPos intended=near.partner();
        Pending.get(level).remove(intended);
        var already=lamp(level,intended);
        BlockPos b=already!=null?intended:plant(level,intended);
        if(b==null){near.clear();return null;}
        link(nearLevel,nearPos,level,b);
        return b;
    }
    /** Far lamps whose ground has come in since. */
    static void place(ServerLevel level){
        Pending pending=Pending.get(level);
        if(pending.list.isEmpty())return;
        for(long[] entry:List.copyOf(pending.list)){
            BlockPos b=BlockPos.of(entry[0]),a=BlockPos.of(entry[1]);
            if(!level.hasChunk(b.getX()>>4,b.getZ()>>4))continue;
            var near=level.isLoaded(a)?lamp(level,a):null;
            if(near==null||!near.pending()){
                if(level.isLoaded(a))pending.remove(b);
                continue;
            }
            settle(level,near,level,a);
        }
    }
    static final class Pending extends SavedData {
        final List<long[]> list=new ArrayList<>();
        static Pending get(ServerLevel level){return level.getDataStorage().computeIfAbsent(new Factory<>(Pending::new,Pending::load),"veilborne_loom_pending");}
        void add(BlockPos far,BlockPos near){list.add(new long[]{far.asLong(),near.asLong()});setDirty();}
        void remove(BlockPos far){if(list.removeIf(e->e[0]==far.asLong()))setDirty();}
        @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider r){
            tag.putLongArray("Far",list.stream().mapToLong(e->e[0]).toArray());tag.putLongArray("Near",list.stream().mapToLong(e->e[1]).toArray());return tag;
        }
        static Pending load(CompoundTag tag,HolderLookup.Provider r){
            Pending p=new Pending();long[] far=tag.getLongArray("Far"),near=tag.getLongArray("Near");
            for(int i=0;i<Math.min(far.length,near.length);i++)p.list.add(new long[]{far[i],near[i]});
            return p;
        }
    }
    static void clearPulls(){pulls.clear();}
    private LoomLinks(){}
}
