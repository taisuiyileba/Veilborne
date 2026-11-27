package com.suiyi.veilborne.test;

import com.suiyi.veilborne.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;

/** What the weathers leave behind (1.7.0): completeness and its gifts, the moonlit haven and moonveil, the silence, the
 *  listener, sewn lamps and the land stitch's choice, the needle core, the dream tree's heart and its leaves, the mirror
 *  tower's foundation and the two waves of beasts, the star tide's scales, pool and catch, the voyage and the homeward
 *  shell, and the new packets and saved state. */
@GameTestHolder(Veilborne.ID)
@PrefixGameTestTemplate(false)
public final class LegacyTests {
    /** A survival player the harness does not treat as creative (see ThreadTests). */
    private static ServerPlayer survivor(GameTestHelper h){
        var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(),"test-legacy"),false);
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation()){
            @Override public boolean isSpectator(){return false;}
            @Override public boolean isCreative(){return false;}
        };
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection,p,cookie);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        for(int i=0;i<61;i++)p.tick();
        return p;
    }
    private static void leave(GameTestHelper h,ServerPlayer... ps){for(var p:ps)h.getLevel().getServer().getPlayerList().remove(p);}
    /** A grass plot w blocks round o on a stone bed, clear air above: open ground over the test enclosure. */
    private static BlockPos plot(GameTestHelper h,int w){
        ServerLevel level=h.getLevel();BlockPos o=h.absolutePos(new BlockPos(8,22,8));
        for(int x=-w;x<=w;x++)for(int z=-w;z<=w;z++){
            for(int y=-12;y<-1;y++)level.setBlock(o.offset(x,y,z),Blocks.STONE.defaultBlockState(),3);
            level.setBlock(o.offset(x,-1,z),Blocks.GRASS_BLOCK.defaultBlockState(),3);
            for(int y=0;y<16;y++)level.setBlock(o.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        return o;
    }
    private static int count(ServerPlayer p,Item item){return p.getInventory().countItem(item);}
    private static List<ItemEntity> items(ServerLevel level,BlockPos at,double r,Item item){
        return level.getEntitiesOfClass(ItemEntity.class,new AABB(at).inflate(r),i->i.getItem().is(item));
    }

    // ------------------------------------------------------------------ completeness
    @GameTest(template="empty",batch="80")
    public static void completenessIsReachableAndGrows(GameTestHelper h){
        for(int w=0;w<VeilGrade.NAMES.length;w++){
            h.assertTrue(VeilGrade.stars(w,0)==1,"Being there is not one star: "+VeilGrade.NAMES[w]);
            h.assertTrue(VeilGrade.stars(w,(1<<VeilGrade.deeds(w))-1)==3,"Doing everything is not three stars: "+VeilGrade.NAMES[w]);
            h.assertTrue(VeilGrade.gifts(w,1).isEmpty()&&!VeilGrade.gifts(w,2).isEmpty()&&VeilGrade.gifts(w,3).size()>=VeilGrade.gifts(w,2).size(),"The gifts do not grow with the stars: "+VeilGrade.NAMES[w]);
            h.assertTrue(VeilGrade.TWO[w]<VeilGrade.THREE[w],"Two and three stars ask the same: "+VeilGrade.NAMES[w]);
        }
        // Three stars without the dangerous deed or a secret, with the fewest visitors each weather can draw.
        int[][] leanest={
            {VeilGrade.SEA_WHALE|VeilGrade.SEA_SKIFF|VeilGrade.SEA_VORTEX|VeilGrade.SEA_BOTTLE|VeilGrade.SEA_SCALES},
            {VeilGrade.MOON_MOLT|VeilGrade.MOON_ESCORT|VeilGrade.MOON_DEW|VeilGrade.MOON_STARS|VeilGrade.MOON_TRUE},
            {VeilGrade.MIRROR_PILGRIM|VeilGrade.MIRROR_DOOR|VeilGrade.MIRROR_GALLERY|VeilGrade.MIRROR_TRAVELER|VeilGrade.MIRROR_VAULT},
            {VeilGrade.THREAD_SEWN|VeilGrade.THREAD_FORESIGHT|VeilGrade.THREAD_ENDS|VeilGrade.THREAD_KNOT|VeilGrade.THREAD_HOLD,
             VeilGrade.THREAD_SEWN|VeilGrade.THREAD_FORESIGHT|VeilGrade.THREAD_ENDS|VeilGrade.THREAD_POCKET|VeilGrade.THREAD_CUT},
            {VeilGrade.HUSH_ECLIPSE|VeilGrade.HUSH_KNOCK|VeilGrade.HUSH_PROCESSION|VeilGrade.HUSH_THUNDER,
             VeilGrade.HUSH_ECLIPSE|VeilGrade.HUSH_KNOCK|VeilGrade.HUSH_BELL|VeilGrade.HUSH_THUNDER},
            {VeilGrade.DREAM_DEER|VeilGrade.DREAM_GLANCE|VeilGrade.DREAM_SEED|VeilGrade.DREAM_PATH,
             VeilGrade.DREAM_DEER|VeilGrade.DREAM_GLANCE|VeilGrade.DREAM_SEED|VeilGrade.DREAM_LOST}};
        for(int w=0;w<leanest.length;w++)for(int mask:leanest[w])
            h.assertTrue(VeilGrade.stars(w,mask)==3&&(mask&(VeilGrade.RISK[w]|VeilGrade.SECRET[w]))==0,"Three stars out of reach without risk: "+VeilGrade.NAMES[w]);
        h.assertTrue(VeilGrade.points(VeilGrade.THREAD,VeilGrade.THREAD_CORE)==2&&VeilGrade.points(VeilGrade.MIRROR,VeilGrade.MIRROR_BEASTS)==2&&VeilGrade.points(VeilGrade.HUSH,VeilGrade.HUSH_LISTENER)==2,"The dangerous deeds are not worth more");
        h.succeed();
    }
    @GameTest(template="empty",batch="81")
    public static void completenessSettlesOnceAndIsKept(GameTestHelper h){
        var level=h.getLevel();var p=survivor(h);
        long seed=812345L;
        VeilGrade.witness(p,VeilGrade.MOON,seed);
        for(int bit:new int[]{VeilGrade.MOON_MOLT,VeilGrade.MOON_ESCORT,VeilGrade.MOON_DEW,VeilGrade.MOON_STARS,VeilGrade.MOON_TRUE})VeilGrade.deed(p,VeilGrade.MOON,seed,bit);
        VeilGrade.deed(p,VeilGrade.MOON,seed,VeilGrade.MOON_TRUE);
        h.assertTrue(VeilGrade.done(p,VeilGrade.MOON,seed,VeilGrade.MOON_TRUE)&&!VeilGrade.done(p,VeilGrade.MOON,seed,VeilGrade.MOON_EMPTY),"Deeds were not kept for the event");
        VeilGrade.settle(level,VeilGrade.MOON,seed);
        var t=p.getPersistentData().getCompound(VeilGrade.KEY);
        h.assertTrue(!t.contains("Live")&&t.getIntArray("Last")[VeilGrade.MOON]==3&&t.getIntArray("Best")[VeilGrade.MOON]==3,"Three stars were not recorded");
        h.assertTrue(count(p,Veilborne.SILVER_LANTERN_ITEM.get())==2&&count(p,Veilborne.MOON_DEW.get())==2,"The three-star gift was not given");
        VeilGrade.settle(level,VeilGrade.MOON,seed);
        h.assertTrue(count(p,Veilborne.SILVER_LANTERN_ITEM.get())==2,"An event was graded twice");
        // A slot from an event that ended while nobody settled it is graded as soon as another begins.
        VeilGrade.witness(p,VeilGrade.SEA,seed+1);
        VeilGrade.deed(p,VeilGrade.SEA,seed+1,VeilGrade.SEA_WHALE);
        VeilGrade.witness(p,VeilGrade.MIRROR,seed+2);
        t=p.getPersistentData().getCompound(VeilGrade.KEY);
        h.assertTrue(t.getIntArray("Last")[VeilGrade.SEA]==1&&t.getIntArray("LastDeeds")[VeilGrade.SEA]==VeilGrade.SEA_WHALE&&t.getCompound("Live").getInt("W")==VeilGrade.MIRROR,"A stale slot was lost");
        // A poorer weather later does not lower the best.
        VeilGrade.settle(level,VeilGrade.MIRROR,seed+2);VeilGrade.witness(p,VeilGrade.MOON,seed+3);VeilGrade.settle(level,VeilGrade.MOON,seed+3);
        t=p.getPersistentData().getCompound(VeilGrade.KEY);
        h.assertTrue(t.getIntArray("Last")[VeilGrade.MOON]==1&&t.getIntArray("Best")[VeilGrade.MOON]==3,"The best was not kept");
        VeilGrade.site(p,VeilGrade.SITE_VAULT,new BlockPos(1,2,3),new BlockPos(1,-4,3),0);
        VeilGrade.site(p,VeilGrade.SITE_POOL,new BlockPos(9,64,9),new BlockPos(9,64,9),level.getGameTime()-1);
        h.assertTrue(VeilGrade.sites(p,level.getGameTime()).size()==1,"Places were not remembered, or a dried-up pool was");
        leave(h,p);
        h.succeed();
    }

    // ------------------------------------------------------------------ the moon
    @GameTest(template="empty",batch="82")
    public static void silverLanternsKeepMonstersFromTheDark(GameTestHelper h){
        var level=h.getLevel();BlockPos o=plot(h,3);
        level.setBlock(o,Veilborne.SILVER_LANTERN.get().defaultBlockState(),3);
        h.assertTrue(VeilWards.within(level,VeilWards.HAVEN,Vec3.atCenterOf(o),1).contains(o),"The lantern did not enter the haven list");
        Zombie near=EntityType.ZOMBIE.create(level),far=EntityType.ZOMBIE.create(level);
        near.moveTo(o.getX()+14.5,o.getY(),o.getZ()+.5);far.moveTo(o.getX()+40.5,o.getY(),o.getZ()+.5);
        Cow cow=EntityType.COW.create(level);cow.moveTo(o.getX()+5.5,o.getY(),o.getZ()+.5);
        var a=new MobSpawnEvent.PositionCheck(near,level,MobSpawnType.NATURAL,null);MoonHaven.spawn(a);
        var b=new MobSpawnEvent.PositionCheck(far,level,MobSpawnType.NATURAL,null);MoonHaven.spawn(b);
        var c=new MobSpawnEvent.PositionCheck(near,level,MobSpawnType.SPAWNER,null);MoonHaven.spawn(c);
        var d=new MobSpawnEvent.PositionCheck(cow,level,MobSpawnType.NATURAL,null);MoonHaven.spawn(d);
        h.assertTrue(a.getResult()==MobSpawnEvent.PositionCheck.Result.FAIL,"A monster came out of the dark beside a silver lantern");
        h.assertTrue(b.getResult()==MobSpawnEvent.PositionCheck.Result.DEFAULT&&c.getResult()==MobSpawnEvent.PositionCheck.Result.DEFAULT&&d.getResult()==MobSpawnEvent.PositionCheck.Result.DEFAULT,
            "The haven reached too far, or stopped a spawner or an animal");
        level.setBlock(o,Blocks.AIR.defaultBlockState(),3);
        var e=new MobSpawnEvent.PositionCheck(near,level,MobSpawnType.NATURAL,null);MoonHaven.spawn(e);
        h.assertTrue(e.getResult()==MobSpawnEvent.PositionCheck.Result.DEFAULT,"A broken lantern still kept its haven");
        h.succeed();
    }
    @GameTest(template="empty",batch="83")
    public static void moonDewBrewsMoonveil(GameTestHelper h){
        var level=h.getLevel();BlockPos o=plot(h,3);
        var brewing=level.potionBrewing();
        ItemStack awkward=PotionContents.createItemStack(Items.POTION,Potions.AWKWARD);
        ItemStack veil=brewing.mix(new ItemStack(Veilborne.MOON_DEW.get()),awkward);
        h.assertTrue(veil.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS).is(Veilborne.MOONVEIL_POTION),"Moon dew does not brew into moonveil");
        ItemStack longer=brewing.mix(new ItemStack(Items.REDSTONE),veil);
        h.assertTrue(longer.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS).is(Veilborne.LONG_MOONVEIL_POTION),"Redstone does not lengthen moonveil");
        var p=survivor(h);p.moveTo(o.getX()+.5,o.getY(),o.getZ()+.5);
        Zombie z=EntityType.ZOMBIE.create(level);z.moveTo(o.getX()+12.5,o.getY(),o.getZ()+.5);level.addFreshEntity(z);
        double seen=p.getVisibilityPercent(z);
        p.addEffect(new MobEffectInstance(Veilborne.MOONVEIL,600));
        double veiled=p.getVisibilityPercent(z);
        h.assertTrue(veiled<=seen*MoonHaven.VISIBILITY+1e-6,"Moonveil does not hide you from monsters");
        z.setTarget(p);
        Veilborne.MOONVEIL.get().applyEffectTick(p,0);
        h.assertTrue(z.getTarget()==null,"A monster far off kept after someone under moonveil");
        z.discard();leave(h,p);
        h.succeed();
    }

    // ------------------------------------------------------------------ the silence
    @GameTest(template="empty",batch="84")
    public static void silentLanternAndStillCandleCannotBeHeard(GameTestHelper h){
        var level=h.getLevel();BlockPos o=plot(h,8);
        var p=survivor(h);p.moveTo(o.getX()+20.5,o.getY(),o.getZ()+.5);
        var loud=new VanillaGameEvent(level,GameEvent.STEP,p.position(),GameEvent.Context.of(p));HushSilence.heard(loud);
        h.assertTrue(!loud.isCanceled(),"An ordinary step was swallowed");
        p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Veilborne.SILENT_LANTERN_ITEM.get()));
        var quiet=new VanillaGameEvent(level,GameEvent.STEP,p.position(),GameEvent.Context.of(p));HushSilence.heard(quiet);
        h.assertTrue(quiet.isCanceled(),"The sculk heard someone holding a silent lantern");
        var block=new VanillaGameEvent(level,GameEvent.BLOCK_PLACE,p.position(),GameEvent.Context.of(p));HushSilence.heard(block);
        h.assertTrue(block.isCanceled(),"A block placed by someone holding a silent lantern was heard");
        level.setBlock(o,Veilborne.STILL_CANDLE.get().defaultBlockState(),3);
        var nearCandle=new VanillaGameEvent(level,GameEvent.BLOCK_CHANGE,Vec3.atCenterOf(o.offset(3,0,2)),GameEvent.Context.of(Blocks.STONE.defaultBlockState()));HushSilence.heard(nearCandle);
        var farCandle=new VanillaGameEvent(level,GameEvent.BLOCK_CHANGE,Vec3.atCenterOf(o.offset(12,0,0)),GameEvent.Context.of(Blocks.STONE.defaultBlockState()));HushSilence.heard(farCandle);
        h.assertTrue(nearCandle.isCanceled()&&!farCandle.isCanceled(),"The still candle's quiet is the wrong size");
        level.setBlock(o,Blocks.AIR.defaultBlockState(),3);
        leave(h,p);
        h.succeed();
    }
    @GameTest(template="empty",batch="85",timeoutTicks=300)
    public static void aSoundInTheSilenceIsHeard(GameTestHelper h){
        var level=h.getLevel();BlockPos o=plot(h,10);
        var server=level.getServer();
        Difficulty was=level.getDifficulty();server.setDifficulty(Difficulty.NORMAL,true);
        var p=survivor(h);p.moveTo(o.getX()+.5,o.getY(),o.getZ()+.5);
        HushService.begin(level,o,720);
        HushData d=HushData.get(level);
        d.age=(int)Math.round(.5*d.duration);
        h.assertTrue(HushSilence.silentHours(d.progress()),"Half way through is not the silence");
        HushSilence.loud(p,p.position());
        h.assertTrue(d.has(HushData.LISTENER)&&p.getUUID().equals(d.listener),"A bell in the silence went unheard");
        int at=d.listenerAt;HushSilence.loud(p,p.position());
        h.assertTrue(d.listenerAt==at,"A second sound called a second listener");
        d.age=d.listenerAt+HushSilence.DELAY;
        HushSilence.tick(level,d,d.progress());
        h.assertTrue(d.has(HushData.LISTENER_UP)&&(d.has(HushData.LISTENER_GONE)||!HushSilence.listeners(level).isEmpty()),"Nothing came up after the sound");
        boolean came=!HushSilence.listeners(level).isEmpty();
        h.assertTrue(came,"No warden climbed out beside whoever made the sound");
        d.age=(int)Math.ceil(HushTimeline.BIRD*d.duration)+1;
        HushSilence.tick(level,d,d.progress());
        if(came){
            h.assertTrue(HushSilence.listeners(level).stream().allMatch(w->w.isNoAi()&&w.getPose()==net.minecraft.world.entity.Pose.DIGGING),"The listener did not go back down at the first bird");
            h.assertTrue(count(p,Items.ECHO_SHARD)==HushSilence.ECHO_SHARDS&&VeilGrade.done(p,VeilGrade.HUSH,d.seed,VeilGrade.HUSH_LISTENER),"Whoever outlasted it was given nothing");
        }
        HushService.end(level);
        HushSilence.listeners(level).forEach(net.minecraft.world.entity.Entity::discard);
        server.setDifficulty(was,true);
        leave(h,p);
        h.succeed();
    }

    // ------------------------------------------------------------------ the thread
    @GameTest(template="empty",batch="86")
    public static void sewnLampsCarryYouAlongTheThread(GameTestHelper h){
        var level=h.getLevel();BlockPos o=plot(h,20);
        BlockPos a=o.offset(-16,0,0),b=o.offset(16,0,3);
        level.setBlock(a,Veilborne.LOOM_LANTERN.get().defaultBlockState(),3);
        level.setBlock(b,Veilborne.LOOM_LANTERN.get().defaultBlockState(),3);
        var p=survivor(h);p.moveTo(a.getX()+1.5,a.getY(),a.getZ()+.5);
        h.assertTrue(LoomLinks.cost(a,b,false,false)==1&&LoomLinks.cost(BlockPos.ZERO,new BlockPos(200,0,0),false,false)==4&&LoomLinks.cost(BlockPos.ZERO,new BlockPos(900,0,0),true,true)==1,"The thread's cost is wrong");
        LoomLinks.thread(p,level,a);
        h.assertTrue(LoomLinks.thread(p,level,b)==net.minecraft.world.ItemInteractionResult.FAIL&&!LoomLinks.lamp(level,a).linked(),"Two lamps were sewn with no thread");
        p.getInventory().add(new ItemStack(Veilborne.SCARLET_THREAD.get(),3));
        LoomLinks.thread(p,level,b);
        var la=LoomLinks.lamp(level,a);var lb=LoomLinks.lamp(level,b);
        h.assertTrue(la.linked()&&b.equals(la.partner())&&lb.linked()&&a.equals(lb.partner())&&count(p,Veilborne.SCARLET_THREAD.get())==2,"The lamps were not sewn, or the thread was not used");
        LoomLinks.use(p,level,a);
        LoomLinks.travel(p,level,a);
        h.assertTrue(p.position().distanceTo(Vec3.atBottomCenterOf(b))<4,"The thread did not carry anyone to the other lamp");
        // Breaking one end cuts the thread at the other.
        level.destroyBlock(a,false);
        h.assertTrue(!LoomLinks.lamp(level,b).linked(),"The far lamp still leads to a broken one");
        // The pair the seam leaves: both feet when both are here.
        BlockPos fa=o.offset(-8,0,-10),fb=o.offset(9,0,-10);
        BlockPos near=LoomLinks.leavePair(level,fa,fb);
        var pa=LoomLinks.lamp(level,near);
        h.assertTrue(near!=null&&pa.linked()&&!pa.pending()&&LoomLinks.lamp(level,pa.partner())!=null,"The seam did not leave a sewn pair");
        level.destroyBlock(b,false);level.destroyBlock(near,false);level.destroyBlock(pa.partner(),false);
        leave(h,p);
        h.succeed();
    }
    @GameTest(template="empty",batch="87")
    public static void theLandStitchCanBeCutOrHeldAndTheCoreCaught(GameTestHelper h){
        var level=h.getLevel();BlockPos o=plot(h,12);
        var d=ThreadData.get(level);
        ThreadService.begin(level,o,780);
        d.takeFlag(ThreadData.LAND);d.landA=o.offset(-6,0,0);d.landB=o.offset(8,0,4);
        d.age=(int)Math.ceil(ThreadTimeline.pAtSew(ThreadTimeline.THROUGH+.02)*d.duration);
        var p=survivor(h);p.moveTo(d.landA.getX()+2.5,d.landA.getY(),d.landA.getZ()+.5);
        h.assertTrue(ThreadService.canSnip(d,p.position())&&!ThreadService.canSnip(d,Vec3.atCenterOf(o.offset(1,0,30))),"Shears reach the stitch from the wrong places");
        ThreadService.snip(level,d,p);
        h.assertTrue(d.has(ThreadData.SNIPPED)&&!ThreadService.canSnip(d,p.position())&&VeilGrade.done(p,VeilGrade.THREAD,d.seed,VeilGrade.THREAD_CUT),"The stitch was not cut");
        h.assertTrue(!items(level,p.blockPosition(),3,Veilborne.SCARLET_THREAD.get()).isEmpty(),"Cutting the stitch left no thread");
        d.age=(int)Math.ceil(ThreadTimeline.CUT*d.duration)+1;
        ThreadService.pair(level,d,d.progress(),List.of(p));
        h.assertTrue(!d.has(ThreadData.PAIRED),"A cut stitch still left its lamps");
        // At the middle of a warned ring with a binding knot: the needle's whole weight, and a piece of its core.
        var q=survivor(h);BlockPos s=o.offset(0,0,-8);q.moveTo(s.getX()+.5,s.getY(),s.getZ()+.5);
        q.getInventory().add(new ItemStack(Veilborne.BINDING_CHARM.get()));
        var r=survivor(h);r.moveTo(s.getX()+4.5,s.getY(),s.getZ()+.5);
        float hq=q.getHealth(),hr=r.getHealth();
        ThreadService.strike(level,Vec3.atBottomCenterOf(s),ThreadTimeline.STRIKE_RANGE,new HashSet<>(),4,d.seed);
        h.assertTrue(hq-q.getHealth()>=ThreadService.CORE_DAMAGE-.01&&count(q,Veilborne.NEEDLE_CORE.get())==1&&VeilGrade.done(q,VeilGrade.THREAD,d.seed,VeilGrade.THREAD_CORE),"The middle of the ring gave no core");
        h.assertTrue(hr-r.getHealth()<ThreadService.CORE_DAMAGE-.01&&count(r,Veilborne.NEEDLE_CORE.get())==0,"The edge of the ring was treated as its middle");
        ThreadService.end(level);
        leave(h,p,q,r);
        h.succeed();
    }

    // ------------------------------------------------------------------ the dream
    @GameTest(template="empty",batch="88")
    public static void theDreamTreesHeartQuickensTheFarm(GameTestHelper h){
        var level=h.getLevel();BlockPos o=plot(h,9);
        var random=net.minecraft.util.RandomSource.create(4);
        h.assertTrue(DreamBlocks.grow(level,o,false,random),"The dream tree did not grow");
        h.assertTrue(level.getBlockState(o).is(Veilborne.DREAM_HEART.get())&&VeilWards.within(level,VeilWards.HEART,Vec3.atCenterOf(o),1).contains(o),"The grown tree has no heart");
        h.assertTrue(DreamHeart.leaves(level,o)>=DreamHeart.LEAVES,"The crown is too thin for its heart");
        List<BlockPos> wheat=new ArrayList<>();
        for(int x=-6;x<=6;x+=3)for(int z=4;z<=7;z+=3){BlockPos c=o.offset(x,0,z);level.setBlock(c.below(),Blocks.FARMLAND.defaultBlockState(),3);level.setBlock(c,Blocks.WHEAT.defaultBlockState(),3);wheat.add(c);}
        Cow calf=EntityType.COW.create(level);calf.setAge(-24000);calf.moveTo(o.getX()+3.5,o.getY(),o.getZ()-3.5);level.addFreshEntity(calf);
        Cow cow=EntityType.COW.create(level);cow.setAge(6000);cow.moveTo(o.getX()-3.5,o.getY(),o.getZ()-3.5);level.addFreshEntity(cow);
        for(int i=0;i<300;i++)DreamHeart.nurture(level,o,false,random);
        int grown=0;for(BlockPos c:wheat)grown+=level.getBlockState(c).getValue(CropBlock.AGE);
        h.assertTrue(grown>0,"Nothing grew round the dream tree's heart");
        h.assertTrue(calf.getAge()>-24000+DreamHeart.PERIOD*200&&cow.getAge()<6000-DreamHeart.PERIOD*100,"The heart did not hurry the animals");
        // A bare trunk has nothing to give.
        BlockPos bare=o.offset(0,0,-8);
        level.setBlock(bare,Veilborne.DREAM_HEART.get().defaultBlockState(),3);
        level.setBlock(bare.offset(2,0,0).below(),Blocks.FARMLAND.defaultBlockState(),3);level.setBlock(bare.offset(2,0,0),Blocks.WHEAT.defaultBlockState(),3);
        h.assertTrue(DreamHeart.leaves(level,bare)<DreamHeart.LEAVES,"The bare heart found leaves");
        calf.discard();cow.discard();
        h.succeed();
    }
    @GameTest(template="empty",batch="89")
    public static void dreamLeavesFeedEveryAnimalAndBringTwins(GameTestHelper h){
        var level=h.getLevel();BlockPos o=plot(h,9);
        var p=h.makeMockServerPlayerInLevel();p.moveTo(o.getX()+.5,o.getY(),o.getZ()+.5,0,0);
        ItemStack leaves=new ItemStack(Veilborne.DREAM_LEAVES_ITEM.get(),4);
        p.setItemInHand(InteractionHand.MAIN_HAND,leaves);
        var frog=EntityType.FROG.create(level);frog.moveTo(o.getX()+1.5,o.getY(),o.getZ()+.5);level.addFreshEntity(frog);
        var calf=EntityType.COW.create(level);calf.setAge(-20000);calf.moveTo(o.getX()-1.5,o.getY(),o.getZ()+.5);level.addFreshEntity(calf);
        DreamHeart.feed(new PlayerInteractEvent.EntityInteract(p,InteractionHand.MAIN_HAND,frog));
        DreamHeart.feed(new PlayerInteractEvent.EntityInteract(p,InteractionHand.MAIN_HAND,calf));
        h.assertTrue(frog.isInLove()&&calf.getAge()>-20000,"Dream leaves did not feed any animal");
        var compost=Veilborne.DREAM_LEAVES_ITEM.get().builtInRegistryHolder().getData(NeoForgeDataMaps.COMPOSTABLES);
        h.assertTrue(compost!=null&&compost.chance()>=.8f,"Dream leaves do not compost");
        // Twins under a leafy tree, now and then.
        BlockPos root=o.offset(0,0,-6);
        DreamBlocks.grow(level,root,true,net.minecraft.util.RandomSource.create(9));
        var a=EntityType.COW.create(level);a.moveTo(root.getX()+2.5,root.getY(),root.getZ()+2.5);level.addFreshEntity(a);
        var b=EntityType.COW.create(level);b.moveTo(root.getX()+3.5,root.getY(),root.getZ()+2.5);level.addFreshEntity(b);
        int before=level.getEntitiesOfClass(Cow.class,new AABB(root).inflate(16)).size();
        for(int i=0;i<30;i++)DreamHeart.twins(new BabyEntitySpawnEvent(a,b,a.getBreedOffspring(level,b)));
        int after=level.getEntitiesOfClass(Cow.class,new AABB(root).inflate(16)).size();
        h.assertTrue(after>before,"No twins were ever born under the dream tree");
        level.getEntitiesOfClass(Cow.class,new AABB(o).inflate(24)).forEach(net.minecraft.world.entity.Entity::discard);frog.discard();
        level.getServer().getPlayerList().remove(p);
        h.succeed();
    }

    // ------------------------------------------------------------------ the mirror
    @GameTest(template="empty",batch="90")
    public static void theMirrorTowerStandsOverABuriedRoom(GameTestHelper h){
        var level=h.getLevel();BlockPos o=plot(h,5);
        long seed=4471;
        int[] off=MirrorVault.column(BlockPos.ZERO,seed);
        BlockPos center=o.offset(-off[0],0,-off[1]);
        BlockPos base=MirrorVault.base(level,center,seed);
        h.assertTrue(base!=null&&base.getX()==o.getX()&&base.getZ()==o.getZ()&&base.getY()==o.getY(),"The tower's base is not where the mirror shows it");
        BlockPos chest=MirrorVault.build(level,base,seed);
        h.assertTrue(chest!=null&&level.getBlockEntity(chest) instanceof ChestBlockEntity c&&c.getLootTable()!=null&&c.getLootTable().equals(MirrorVault.LOOT),"No chest of the vault's own under the tower");
        h.assertTrue(level.getBlockState(chest.above()).isAir()&&level.getBlockState(base.below()).is(Blocks.GRASS_BLOCK),"The room broke through to the surface, or is not a room");
        h.assertTrue(level.getServer().reloadableRegistries().getLootTable(MirrorVault.LOOT)!=net.minecraft.world.level.storage.loot.LootTable.EMPTY,"The tower vault's loot table is missing");
        // The heart of the cracked mirror shows hidden containers nearby.
        h.assertTrue(MirrorVault.hidden(level,Vec3.atCenterOf(o),MirrorVault.HEART_RANGE).contains(chest),"The heart does not show the chest");
        // Something built in the way: the room goes deeper rather than through it.
        BlockPos o2=o.offset(0,0,0);
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=-12;y<-1;y++)level.setBlock(o2.offset(x,y,z),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(o2.offset(0,-MirrorVault.DEPTH-3,0),Blocks.OAK_PLANKS.defaultBlockState(),3);
        BlockPos deeper=MirrorVault.build(level,base,seed+1);
        h.assertTrue(level.getBlockState(o2.offset(0,-MirrorVault.DEPTH-3,0)).is(Blocks.OAK_PLANKS)&&(deeper==null||deeper.getY()<chest.getY()),"The room was cut through something someone built");
        h.succeed();
    }
    @GameTest(template="empty",batch="91")
    public static void glassBeastsComeInTwoWaves(GameTestHelper h){
        var level=h.getLevel();BlockPos o=plot(h,8);
        MirrorService.begin(level,o,780);
        var d=MirrorData.get(level);
        d.age=(int)(.5*d.duration);d.takeFlag(MirrorData.SHARDS);d.shards[0]=o.offset(3,0,0);
        var p=survivor(h);p.moveTo(o.getX()+5.5,o.getY(),o.getZ()+.5);
        MirrorService.burst(level,d,0);
        var first=level.getEntities(Veilborne.BEAST.get(),b->b.getPersistentData().contains(MirrorService.BEAST_SHARD));
        h.assertTrue(first.size()==MirrorService.WAVE_ONE&&d.beastWave[0]==1,"The shard did not burst into its first wave");
        for(var b:first)b.hurt(level.damageSources().playerAttack(p),100);
        MirrorService.waves(level,d);
        var second=level.getEntities(Veilborne.BEAST.get(),b->b.isAlive()&&b.getPersistentData().contains(MirrorService.BEAST_SHARD));
        h.assertTrue(d.beastWave[0]==2&&second.size()==MirrorService.WAVE_TWO,"Beating the first wave with someone there brought no second");
        for(var b:second)b.hurt(level.damageSources().playerAttack(p),100);
        MirrorService.waves(level,d);
        h.assertTrue(d.beastWave[0]==3&&!items(level,d.shards[0],3,Veilborne.MIRROR_HEART.get()).isEmpty()&&VeilGrade.done(p,VeilGrade.MIRROR,d.seed,VeilGrade.MIRROR_BEASTS),"Both waves beaten left no heart");
        MirrorService.end(level);
        level.getEntitiesOfClass(ItemEntity.class,new AABB(o).inflate(12)).forEach(ItemEntity::discard);
        leave(h,p);
        h.succeed();
    }

    // ------------------------------------------------------------------ the sea
    @GameTest(template="empty",batch="92")
    public static void theWhalesScalesGoOutAndLeaveAPool(GameTestHelper h){
        var level=h.getLevel();BlockPos o=plot(h,8);
        TideService.begin(level,o,840);
        var d=TideData.get(level);d.landing=o;d.landingAt=d.age;
        var p=survivor(h);p.moveTo(o.getX()+.5,o.getY(),o.getZ()+.5);
        TideService.scatter(level,d);
        var scales=level.getEntitiesOfClass(ItemEntity.class,new AABB(o).inflate(8),i->i.getPersistentData().contains(TideService.SCALE));
        h.assertTrue(scales.size()==TideService.SCALES&&scales.stream().allMatch(i->i.lifespan==TideService.SCALE_LIFE),"The scales did not come down scattered, or never go out");
        for(int k=0;k<3;k++)TideService.scaleCaught(new ItemEntityPickupEvent.Post(p,scales.get(k),scales.get(k).getItem().copy()));
        h.assertTrue(VeilGrade.done(p,VeilGrade.SEA,d.seed,VeilGrade.SEA_SCALES)&&!VeilGrade.done(p,VeilGrade.SEA,d.seed,VeilGrade.SEA_ALL_SCALES),"Catching three scales was not counted");
        TideService.pool(level,d,List.of(p));
        h.assertTrue(TideService.inPool(level,Vec3.atCenterOf(o.offset(30,0,0)))&&!TideService.inPool(level,Vec3.atCenterOf(o.offset(60,0,0))),"The star tide pool is the wrong size");
        h.assertTrue(VeilGrade.sites(p,level.getGameTime()).stream().anyMatch(s->s.kind()==VeilGrade.SITE_POOL),"The pool was not marked in the journal");
        var saved=d.save(new CompoundTag(),level.registryAccess());
        h.assertTrue(TideData.load(saved,level.registryAccess()).pools.equals(d.pools),"The pools were lost on reload");
        LootParams params=new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(o)).withParameter(LootContextParams.TOOL,new ItemStack(Items.FISHING_ROD)).create(LootContextParamSets.FISHING);
        h.assertTrue(!level.getServer().reloadableRegistries().getLootTable(TideService.STAR_TIDE).getRandomItems(params).isEmpty(),"The star tide brings nothing up");
        h.assertTrue(TideService.charted(TideService.chartBottle())&&!TideService.charted(new ItemStack(Veilborne.BOTTLE.get())),"A chart bottle cannot be told apart");
        d.pools.clear();d.setDirty();
        TideService.end(level);
        scales.forEach(ItemEntity::discard);
        leave(h,p);
        h.succeed();
    }
    @GameTest(template="empty",batch="93",timeoutTicks=600)
    public static void theVoyageLooksAheadAndTheShellBringsYouHome(GameTestHelper h){
        var level=h.getLevel();BlockPos o=plot(h,6);
        Vec3 north=TideService.ahead(Vec3.ZERO,180,10),east=TideService.ahead(Vec3.ZERO,-90,10);
        h.assertTrue(north.z<-9.9&&Math.abs(north.x)<1e-6&&east.x>9.9,"The fisherman looks the wrong way");
        h.assertTrue(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).getTag(TideService.VOYAGE).map(t->t.size()>=4).orElse(false),"The voyage has nowhere to go");
        var p=survivor(h);
        BlockPos home=o;
        p.moveTo(o.getX()+.5,o.getY()+30,o.getZ()+.5);
        TideService.arrive(p,new TideService.Voyage(o.offset(2,0,2),false),home);
        h.assertTrue(p.hasEffect(MobEffects.WATER_BREATHING)&&count(p,Veilborne.HOMEWARD_SHELL.get())==1,"Arriving gave no breath of the sea or no way home");
        p.teleportTo(level,o.getX()+.5,o.getY()+40,o.getZ()+20.5,0,0);
        ItemStack shell=ItemStack.EMPTY;
        for(ItemStack s:p.getInventory().items)if(s.is(Veilborne.HOMEWARD_SHELL.get()))shell=s;
        p.setItemInHand(InteractionHand.MAIN_HAND,shell.copy());shell.setCount(0);
        p.getMainHandItem().use(level,p,InteractionHand.MAIN_HAND);
        h.assertTrue(p.position().distanceTo(Vec3.atBottomCenterOf(home))<8&&count(p,Veilborne.HOMEWARD_SHELL.get())==0&&p.getMainHandItem().isEmpty(),"The shell did not bring anyone home, or was not used up");
        // Held forward aboard, the skiff turns and rises into the sea above.
        TideService.begin(level,o,840);
        var skiff=Veilborne.SKIFF.get().create(level);skiff.setOrigin(o);level.addFreshEntity(skiff);
        p.moveTo(o.getX()+.5,o.getY()+1,o.getZ()+.5);p.startRiding(skiff,true);
        for(int i=0;i<=TideSkiff.VOYAGE_HOLD;i++){p.zza=1;skiff.tick();}
        h.assertTrue(skiff.voyage()>0,"Holding forward aboard did not begin a voyage");
        TideService.end(level);
        if(!skiff.isRemoved())skiff.discard();
        p.stopRiding();
        leave(h,p);
        h.succeed();
    }

    // ------------------------------------------------------------------ the wire and the saved state
    @GameTest(template="empty",batch="94")
    public static void newPacketsAndStateRoundTrip(GameTestHelper h){
        var level=h.getLevel();
        var packet=new VeilPacket(new int[]{3,2,0,1,0,3},new int[]{3,3,1,2,0,3},new int[]{127,5,0,1,0,70},VeilGrade.THREAD,VeilGrade.THREAD_HOLD|VeilGrade.THREAD_SEWN,
            List.of(new VeilGrade.Site(VeilGrade.SITE_PAIR,new BlockPos(1,64,2),new BlockPos(-300,70,900),0),new VeilGrade.Site(VeilGrade.SITE_POOL,new BlockPos(5,63,5),new BlockPos(5,63,5),900000)),
            List.of(new TideData.Pool(new BlockPos(5,63,5),900000)));
        var cue=new VeilCue(VeilCue.REVEAL,1.5,64,-3.25,600,List.of(new BlockPos(1,2,3),new BlockPos(-4,-5,-6)));
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess());
        try{
            VeilPacket.CODEC.encode(b,packet);h.assertTrue(packet.equals(VeilPacket.CODEC.decode(b)),"The journal's copy lost something on the wire");
            VeilCue.CODEC.encode(b,cue);h.assertTrue(cue.equals(VeilCue.CODEC.decode(b)),"A moment lost its places on the wire");
        }finally{b.release();}
        VeilWards.add(level,VeilWards.HAVEN,new BlockPos(90001,70,5));
        var saved=VeilWards.get(level).save(new CompoundTag(),level.registryAccess());
        h.assertTrue(VeilWards.load(saved,level.registryAccess()).save(new CompoundTag(),level.registryAccess()).equals(saved),"The wards changed on reload");
        VeilWards.remove(level,VeilWards.HAVEN,new BlockPos(90001,70,5));
        var mirror=new MirrorData();mirror.begin(new BlockPos(3,70,3),77,15600,320,MirrorData.VARIANT_BEASTS);mirror.vault=new BlockPos(40,60,-2);mirror.beastWave=new int[]{2,0};mirror.beastKills=new int[]{4,0};
        h.assertTrue(MirrorData.load(mirror.save(new CompoundTag(),level.registryAccess()),level.registryAccess()).save(new CompoundTag(),level.registryAccess()).equals(mirror.save(new CompoundTag(),level.registryAccess())),"The mirror's new state changed on reload");
        var hush=new HushData();hush.begin(new BlockPos(3,70,3),78,14400,320,HushData.ALL,false);hush.listener=UUID.randomUUID();hush.listenerAt=5000;hush.flags=HushData.LISTENER;
        var hl=HushData.load(hush.save(new CompoundTag(),level.registryAccess()),level.registryAccess());
        h.assertTrue(hush.listener.equals(hl.listener)&&hl.listenerAt==5000&&hl.has(HushData.LISTENER),"Whoever made the sound was forgotten on reload");
        BlockPos o=h.absolutePos(new BlockPos(8,22,8));
        level.setBlock(o,Veilborne.LOOM_LANTERN.get().defaultBlockState(),3);
        LoomLinks.lamp(level,o).setPartner(new BlockPos(-100,64,100),net.minecraft.world.level.Level.NETHER,true);
        var tag=LoomLinks.lamp(level,o).saveWithoutMetadata(level.registryAccess());
        level.setBlock(o,Blocks.AIR.defaultBlockState(),3);level.setBlock(o,Veilborne.LOOM_LANTERN.get().defaultBlockState(),3);
        var fresh=LoomLinks.lamp(level,o);fresh.loadWithComponents(tag,level.registryAccess());
        h.assertTrue(fresh.linked()&&fresh.pending()&&fresh.partnerDim().equals(net.minecraft.world.level.Level.NETHER)&&fresh.partner().equals(new BlockPos(-100,64,100)),"A lamp forgot the other end of its thread");
        level.setBlock(o,Blocks.AIR.defaultBlockState(),3);
        h.succeed();
    }
    @GameTest(template="empty",batch="95")
    public static void weatherCommandsUseModIdPrefix(GameTestHelper h){
        var level=h.getLevel();var dispatcher=level.getServer().getCommands().getDispatcher();
        var root=dispatcher.getRoot();var veil=root.getChild(Veilborne.ID);
        h.assertTrue(veil!=null,"Missing /veilborne root command");
        var src=level.getServer().createCommandSourceStack().withLevel(level).withPosition(Vec3.atCenterOf(plot(h,7))).withPermission(2).withSuppressedOutput();
        for(String name:List.of("starfall","moonbloom","mirrorsky","scarletseam","silentday","dreamtide")){
            h.assertTrue(root.getChild(name)==null,"Unprefixed command still registered: /"+name);
            h.assertTrue(veil.getChild(name)!=null,"Missing /veilborne subcommand: "+name);
            try{
                h.assertTrue(dispatcher.execute("veilborne "+name+" start 180",src)==1,"start failed for "+name);
                h.assertTrue(dispatcher.execute("veilborne "+name+" stage 50",src)==1,"stage failed for "+name);
                h.assertTrue(dispatcher.execute("veilborne "+name+" status",src)==1,"status failed for "+name);
                h.assertTrue(dispatcher.execute("veilborne "+name+" stop",src)==1,"stop failed for "+name);
            }catch(com.mojang.brigadier.exceptions.CommandSyntaxException ex){
                throw new AssertionError("Command execution failed for "+name,ex);
            }
        }
        h.succeed();
    }
    private LegacyTests(){}
}
