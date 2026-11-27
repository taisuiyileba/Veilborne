package com.suiyi.veilborne;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 完整度: how fully someone met one veil weather. Each weather counts a handful of deeds for the event under way; when it
 * ends, everyone who witnessed it is given one to three stars and a small gift that grows with them, and the result is kept
 * for the journal (the last and the best, per weather). Deeds live on the player, keyed by the event's seed, so a restart or
 * a logout in the middle loses nothing: a slot left over from an event that is no longer under way is settled the next time
 * its owner is seen. Also kept here, for the journal: the places a weather left behind for someone (a star-tide pool, a
 * tower's buried foundation, a pair of stitched lamps).
 */
public final class VeilGrade {
    public static final int SEA=0,MOON=1,MIRROR=2,THREAD=3,HUSH=4,DREAM=5;
    public static final String[] NAMES={"sea","moon","mirror","thread","hush","dream"};
    public static final int SEA_WHALE=1,SEA_SKIFF=2,SEA_VORTEX=4,SEA_BOTTLE=8,SEA_SCALES=16,SEA_ALL_SCALES=32,SEA_VOYAGE=64;
    public static final int MOON_MOLT=1,MOON_ESCORT=2,MOON_DEW=4,MOON_STARS=8,MOON_TRUE=16,MOON_EARLY=32,MOON_EMPTY=64;
    public static final int MIRROR_PILGRIM=1,MIRROR_DOOR=2,MIRROR_GALLERY=4,MIRROR_FRAMES=8,MIRROR_TRAVELER=16,MIRROR_VAULT=32,MIRROR_BEASTS=64,MIRROR_KNOWN=128;
    public static final int THREAD_SEWN=1,THREAD_FORESIGHT=2,THREAD_ENDS=4,THREAD_KNOT=8,THREAD_POCKET=16,THREAD_HOLD=32,THREAD_CUT=64,THREAD_CORE=128,THREAD_STRING=256;
    public static final int HUSH_ECLIPSE=1,HUSH_KNOCK=2,HUSH_PROCESSION=4,HUSH_BELL=8,HUSH_THUNDER=16,HUSH_LISTENER=32,HUSH_HEARD=64;
    public static final int DREAM_DEER=1,DREAM_PATH=2,DREAM_GLANCE=4,DREAM_SEED=8,DREAM_LOST=16,DREAM_ENCLOSURE=32,DREAM_FOOTPRINT=64;
    /** Every deed of each weather, in the journal's order (bit i is deed i), and what it is worth. */
    public static final int[][] WORTH={
        {1,1,1,1,2,1,1},
        {1,1,1,1,1,1,1},
        {1,1,1,1,1,1,2,1},
        {1,1,1,1,1,1,1,2,1},
        {1,1,1,1,1,2,1},
        {1,1,2,1,1,1,1}};
    /** Deeds never listed until they have happened, like the secret observations. */
    public static final int[] SECRET={0,MOON_EMPTY,MIRROR_KNOWN,THREAD_STRING,HUSH_HEARD,DREAM_FOOTPRINT};
    /** The dangerous ones nobody has to do. */
    public static final int[] RISK={0,0,MIRROR_BEASTS,THREAD_CORE,HUSH_LISTENER,0};
    /**
     * Points for two and for three stars. Three is always within reach without the dangerous deed or a secret, whichever
     * of the rotating visitors came (the silent daylight draws only two of its three in most weathers, hence its four).
     */
    public static final int[] TWO={3,3,3,3,3,3},THREE={6,5,5,5,4,5};
    public static final String KEY="VeilborneGrade";
    public static final int SITE_POOL=0,SITE_VAULT=1,SITE_PAIR=2,SITE_VOYAGE=3,SITES=8;
    public record Site(int kind,BlockPos a,BlockPos b,long until){}

    public static int deeds(int weather){return WORTH[weather].length;}
    public static int points(int weather,int done){
        int s=0;for(int i=0;i<WORTH[weather].length;i++)if((done&1<<i)!=0)s+=WORTH[weather][i];
        return s;
    }
    /** One star for having been there, two and three for having met it. */
    public static int stars(int weather,int done){
        int s=points(weather,done);
        return s>=THREE[weather]?3:s>=TWO[weather]?2:1;
    }
    /** What the weather leaves at two and three stars. */
    public static List<ItemStack> gifts(int weather,int stars){
        if(stars<2)return List.of();
        boolean full=stars>=3;
        return switch(weather){
            case SEA -> full?List.of(new ItemStack(Veilborne.WHALE_SCALE.get(),2),TideService.chartBottle()):List.of(new ItemStack(Veilborne.WHALE_SCALE.get()));
            case MOON -> full?List.of(new ItemStack(Veilborne.SILVER_LANTERN_ITEM.get(),2),new ItemStack(Veilborne.MOON_DEW.get(),2)):List.of(new ItemStack(Veilborne.SILVER_LANTERN_ITEM.get()));
            case MIRROR -> full?List.of(new ItemStack(Veilborne.MIRROR_SAND.get(),6),new ItemStack(Veilborne.SKY_MIRROR.get())):List.of(new ItemStack(Veilborne.MIRROR_SAND.get(),4));
            case THREAD -> full?List.of(new ItemStack(Veilborne.SCARLET_THREAD.get(),4),new ItemStack(Veilborne.LOOM_LANTERN_ITEM.get())):List.of(new ItemStack(Veilborne.SCARLET_THREAD.get(),3));
            case HUSH -> full?List.of(new ItemStack(Veilborne.DAYLIGHT_WAX.get(),2),new ItemStack(Veilborne.SILENT_LANTERN_ITEM.get())):List.of(new ItemStack(Veilborne.DAYLIGHT_WAX.get(),2));
            default -> full?List.of(new ItemStack(Veilborne.FULL_DREAM_SEED.get()),new ItemStack(Veilborne.DREAM_LEAVES_ITEM.get(),8)):List.of(new ItemStack(Veilborne.DREAM_SEED.get()),new ItemStack(Veilborne.DREAM_LEAVES_ITEM.get(),4));
        };
    }

    // ------------------------------------------------------------------ the player's slot
    static CompoundTag tag(ServerPlayer p){
        CompoundTag root=p.getPersistentData();
        if(!root.contains(KEY,Tag.TAG_COMPOUND))root.put(KEY,new CompoundTag());
        return root.getCompound(KEY);
    }
    /** The slot for the event this player is meeting now, opened (and a stale one settled) if need be. */
    private static CompoundTag live(ServerPlayer p,int weather,long seed){
        CompoundTag t=tag(p);
        if(t.contains("Live",Tag.TAG_COMPOUND)){
            CompoundTag live=t.getCompound("Live");
            if(live.getInt("W")==weather&&live.getLong("S")==seed)return live;
            settle(p);
        }
        CompoundTag live=new CompoundTag();live.putInt("W",weather);live.putLong("S",seed);live.putInt("D",0);
        t.put("Live",live);
        sync(p);
        return live;
    }
    /** Someone is out in this weather: it will be graded for them when it ends. */
    public static void witness(ServerPlayer p,int weather,long seed){live(p,weather,seed);}
    public static void deed(ServerPlayer p,int weather,long seed,int bit){
        CompoundTag live=live(p,weather,seed);
        int was=live.getInt("D");
        if((was&bit)!=0)return;
        live.putInt("D",was|bit);
        sync(p);
    }
    public static boolean done(ServerPlayer p,int weather,long seed,int bit){return (live(p,weather,seed).getInt("D")&bit)!=0;}
    /** A running count for this event (scales caught, thread ends caught), returned after adding one. */
    public static int count(ServerPlayer p,int weather,long seed,String key){
        CompoundTag c=live(p,weather,seed).getCompound("C");
        int n=c.getInt(key)+1;c.putInt(key,n);
        live(p,weather,seed).put("C",c);
        return n;
    }
    /** The event has ended: grade everyone who met it and is here. The rest are graded when they next log in. */
    public static void settle(ServerLevel level,int weather,long seed){
        for(ServerPlayer p:List.copyOf(level.getServer().getPlayerList().getPlayers())){
            CompoundTag t=tag(p);
            if(!t.contains("Live",Tag.TAG_COMPOUND))continue;
            CompoundTag live=t.getCompound("Live");
            if(live.getInt("W")==weather&&live.getLong("S")==seed)settle(p);
        }
    }
    static void settle(ServerPlayer p){
        CompoundTag t=tag(p);
        if(!t.contains("Live",Tag.TAG_COMPOUND))return;
        CompoundTag live=t.getCompound("Live");t.remove("Live");
        int weather=live.getInt("W"),done=live.getInt("D");
        if(weather<0||weather>=NAMES.length)return;
        int stars=stars(weather,done);
        int[] last=array(t,"Last"),best=array(t,"Best"),lastDeeds=array(t,"LastDeeds");
        last[weather]=stars;best[weather]=Math.max(best[weather],stars);lastDeeds[weather]=done;
        t.putIntArray("Last",last);t.putIntArray("Best",best);t.putIntArray("LastDeeds",lastDeeds);
        List<ItemStack> gifts=gifts(weather,stars);
        MutableComponent head=Component.translatable("grade.veilborne.settle",Component.translatable("weather.veilborne."+NAMES[weather]),Component.literal(starText(stars)).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.WHITE);
        p.displayClientMessage(head,false);
        List<Component> names=new ArrayList<>();
        for(int i=0;i<deeds(weather);i++)if((done&1<<i)!=0)names.add(Component.translatable("grade.veilborne."+NAMES[weather]+"."+i));
        if(!names.isEmpty())p.displayClientMessage(Component.translatable("grade.veilborne.deeds",join(names)).withStyle(ChatFormatting.GRAY),false);
        if(!gifts.isEmpty()){
            List<Component> list=new ArrayList<>();
            for(ItemStack g:gifts){list.add(Component.translatable("grade.veilborne.gift_item",g.getHoverName(),g.getCount()));TideService.give(p,g.copy());}
            p.displayClientMessage(Component.translatable("grade.veilborne.gift",join(list)).withStyle(ChatFormatting.YELLOW),false);
        }else p.displayClientMessage(Component.translatable("grade.veilborne.more").withStyle(ChatFormatting.DARK_GRAY),false);
        p.serverLevel().playSound(null,p.blockPosition(),Veilborne.CHIME.get(),SoundSource.PLAYERS,.5f,stars>=3?1.25f:stars==2?1f:.8f);
        sync(p);
    }
    public static String starText(int stars){return "★".repeat(stars)+"☆".repeat(Math.max(0,3-stars));}
    private static Component join(List<Component> parts){
        MutableComponent out=Component.empty();
        for(int i=0;i<parts.size();i++){if(i>0)out.append(Component.literal(" · "));out.append(parts.get(i));}
        return out;
    }
    private static int[] array(CompoundTag t,String key){int[] a=t.getIntArray(key);int[] out=new int[NAMES.length];System.arraycopy(a,0,out,0,Math.min(a.length,out.length));return out;}
    /** Whether that weather, with that seed, is still under way. */
    static boolean underWay(ServerLevel overworld,int weather,long seed){
        return switch(weather){
            case SEA -> TideData.get(overworld).active&&TideData.get(overworld).seed==seed;
            case MOON -> MoonData.get(overworld).active&&MoonData.get(overworld).seed==seed;
            case MIRROR -> MirrorData.get(overworld).active&&MirrorData.get(overworld).seed==seed;
            case THREAD -> ThreadData.get(overworld).active&&ThreadData.get(overworld).seed==seed;
            case HUSH -> HushData.get(overworld).active&&HushData.get(overworld).seed==seed;
            default -> DreamData.get(overworld).active&&DreamData.get(overworld).seed==seed;
        };
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){
        if(!(e.getEntity() instanceof ServerPlayer p))return;
        CompoundTag t=tag(p);
        if(t.contains("Live",Tag.TAG_COMPOUND)){
            CompoundTag live=t.getCompound("Live");
            if(!underWay(p.server.overworld(),live.getInt("W"),live.getLong("S")))settle(p);
        }
        sync(p);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){
        CompoundTag old=e.getOriginal().getPersistentData();
        if(old.contains(KEY,Tag.TAG_COMPOUND))e.getEntity().getPersistentData().put(KEY,old.getCompound(KEY).copy());
    }

    // ------------------------------------------------------------------ places left behind
    /** Remembers a place for this player's journal: newest first, one entry per place, at most {@link #SITES}. */
    public static void site(ServerPlayer p,int kind,BlockPos a,BlockPos b,long until){
        List<Site> list=new ArrayList<>(sites(p,p.serverLevel().getGameTime()));
        list.removeIf(s->s.kind()==kind&&s.a().equals(a));
        list.add(0,new Site(kind,a.immutable(),b.immutable(),until));
        while(list.size()>SITES)list.remove(list.size()-1);
        ListTag out=new ListTag();
        for(Site s:list){CompoundTag c=new CompoundTag();c.putInt("K",s.kind());c.putLong("A",s.a().asLong());c.putLong("B",s.b().asLong());c.putLong("U",s.until());out.add(c);}
        tag(p).put("Sites",out);
        sync(p);
    }
    /** Remembered places that have not run out (an until of 0 never does). */
    public static List<Site> sites(ServerPlayer p,long now){
        List<Site> out=new ArrayList<>();
        ListTag list=tag(p).getList("Sites",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++){
            CompoundTag c=list.getCompound(i);
            long until=c.getLong("U");
            if(until!=0&&until<now)continue;
            out.add(new Site(c.getInt("K"),BlockPos.of(c.getLong("A")),BlockPos.of(c.getLong("B")),until));
        }
        return out;
    }

    // ------------------------------------------------------------------ the client's copy
    public static VeilPacket packet(ServerPlayer p){
        CompoundTag t=tag(p);
        int weather=-1,done=0;
        if(t.contains("Live",Tag.TAG_COMPOUND)){CompoundTag live=t.getCompound("Live");weather=live.getInt("W");done=live.getInt("D");}
        return new VeilPacket(array(t,"Last"),array(t,"Best"),array(t,"LastDeeds"),weather,done,sites(p,p.serverLevel().getGameTime()),
            TideService.pools(p.server.overworld()));
    }
    public static void sync(ServerPlayer p){
        if(p.connection!=null&&p.connection.hasChannel(VeilPacket.TYPE))PacketDistributor.sendToPlayer(p,packet(p));
    }
    public static void broadcast(ServerLevel level){level.getServer().getPlayerList().getPlayers().forEach(VeilGrade::sync);}
    private VeilGrade(){}
}
