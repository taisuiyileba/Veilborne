package com.suiyi.veilborne;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TideService {
    /** The whale's scales come down scattered and go out one by one: SCALE_LIFE ticks to catch them before they do. */
    public static final int SCALES=5,SCALE_LIFE=900,POOL_DAYS=3,VOYAGE_AHEAD=320,VOYAGE_SEARCH=48;
    public static final double POOL_RADIUS=40,POOL_CHANCE=.45;
    public static final String SCALE="VeilborneScale",CHART="VeilborneChart",HOME="VeilborneHome",HOME_DIM="VeilborneHomeDim";
    public static final ResourceKey<LootTable> STAR_TIDE=ResourceKey.create(Registries.LOOT_TABLE,Veilborne.id("gameplay/star_tide"));
    /** What the fisherman knows how to find at the bottom of the sea, or under the land: see data/veilborne/tags/worldgen/structure. */
    public static final TagKey<Structure> VOYAGE=TagKey.create(Registries.STRUCTURE,Veilborne.id("skiff_voyage"));
    private static final List<ItemEntity> scales=new ArrayList<>();
    public static final String RECORD="VeilborneRecords";
    public static int records(ServerPlayer p){return p.getPersistentData().getInt(RECORD);}
    /** The first record word is full; 无声白昼 and 苍翠梦潮 keep their observations in a second one, announced under record2.* keys. */
    public static final String RECORD2="VeilborneRecords2";
    public static int records2(ServerPlayer p){return p.getPersistentData().getInt(RECORD2);}
    public static void record2(ServerPlayer p,int bit){
        int old=records2(p);if((old&bit)!=0)return;
        p.getPersistentData().putInt(RECORD2,old|bit);
        p.displayClientMessage(Component.translatable("message.veilborne.record2."+bit),true);
    }
    public static void record(ServerPlayer p,int bit){
        int old=records(p);if((old&bit)!=0)return;
        p.getPersistentData().putInt(RECORD,old|bit);
        p.displayClientMessage(Component.translatable("message.veilborne.record."+bit),true);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){
        e.getEntity().getPersistentData().putInt(RECORD,e.getOriginal().getPersistentData().getInt(RECORD));
        e.getEntity().getPersistentData().putInt(RECORD2,e.getOriginal().getPersistentData().getInt(RECORD2));
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){
        if(e.getEntity() instanceof ServerPlayer p)sync(p);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){
        if(e.getEntity() instanceof ServerPlayer p)sync(p);
    }
    public static void sync(ServerPlayer p){
        var data=TideData.get(p.serverLevel());
        if(p.connection!=null&&p.connection.hasChannel(TidePacket.TYPE))PacketDistributor.sendToPlayer(p,TidePacket.from(data,records(p)));
    }
    public static void broadcast(ServerLevel level){level.players().forEach(TideService::sync);}
    public static void begin(ServerLevel level,BlockPos origin,int seconds){
        if(MoonData.get(level).active)MoonService.end(level);
        if(MirrorData.get(level).active)MirrorService.end(level);
        if(ThreadData.get(level).active)ThreadService.end(level);
        if(HushData.get(level).active)HushService.end(level);
        if(DreamData.get(level).active)DreamService.end(level);
        TideData d=TideData.get(level);
        if(d.active)end(level);
        d.begin(origin,level.random.nextLong(),seconds*20,TideConfig.RADIUS.get());
        broadcast(level);
    }
    public static void end(ServerLevel level){
        TideData d=TideData.get(level);d.active=false;d.vortex=BlockPos.ZERO;
        VeilGrade.settle(level,VeilGrade.SEA,d.seed);
        rest(level);
        for(var e:com.google.common.collect.ImmutableList.copyOf(level.getEntities().getAll()))if(e instanceof TideSkiff skiff)skiff.finish();
        broadcast(level);
    }
    /** Every veil weather is followed by the same shared quiet period. */
    public static void rest(ServerLevel level){
        TideData d=TideData.get(level);
        int min=TideConfig.MIN_DAYS.get(),max=Math.max(min,TideConfig.MAX_DAYS.get());
        d.quietTicks=(min+level.random.nextInt(max-min+1))*24000L;d.setDirty();
    }
    /** 月骸花潮 only rises after dusk; the inverted sea keeps no hours. */
    public static boolean moonHours(ServerLevel level){long t=level.getDayTime()%24000;return t>=12000&&t<=18000;}
    @SubscribeEvent public static void tick(LevelTickEvent.Post e){
        if(!(e.getLevel() instanceof ServerLevel level)||!level.dimension().equals(Level.OVERWORLD))return;
        TideData d=TideData.get(level);
        glimmer(level);
        if(level.getGameTime()%1200==0&&d.pools.removeIf(q->q.until()<level.getGameTime())){d.setDirty();VeilGrade.broadcast(level);}
        if(level.players().isEmpty())return; // No offline surprises or loss of a spectacle.
        if(!d.active){
            if(MoonData.get(level).active||MirrorData.get(level).active||ThreadData.get(level).active||HushData.get(level).active||DreamData.get(level).active)return; // One veil weather at a time; the quiet timer waits for it.
            boolean sea=TideConfig.NATURAL.get(),moon=TideConfig.MOON_NATURAL.get(),mirror=TideConfig.MIRROR_NATURAL.get(),thread=TideConfig.THREAD_NATURAL.get(),hush=TideConfig.HUSH_NATURAL.get(),dream=TideConfig.DREAM_NATURAL.get();
            if(!sea&&!moon&&!mirror&&!thread&&!hush&&!dream)return;
            if(--d.quietTicks<=0){
                var candidates=level.players().stream().filter(p->!p.isSpectator()&&p.blockPosition().getY()>level.getSeaLevel()-24&&level.canSeeSky(p.blockPosition())).toList();
                boolean night=moonHours(level);
                // Every weather that may begin right now has the same chance; the moon only rises after dusk.
                java.util.List<Integer> pool=new java.util.ArrayList<>();
                if(sea)pool.add(0);
                if(moon&&night)pool.add(1);
                if(mirror)pool.add(2);
                if(thread)pool.add(3);
                if(hush)pool.add(4);
                if(dream)pool.add(5);
                if(!candidates.isEmpty()&&!pool.isEmpty()){
                    var p=candidates.get(level.random.nextInt(candidates.size()));
                    switch(pool.get(level.random.nextInt(pool.size()))){
                        case 1 -> MoonService.begin(level,p.blockPosition(),TideConfig.MOON_DURATION.get());
                        case 2 -> MirrorService.begin(level,p.blockPosition(),TideConfig.MIRROR_DURATION.get());
                        case 3 -> ThreadService.begin(level,p.blockPosition(),TideConfig.THREAD_DURATION.get());
                        case 4 -> HushService.begin(level,p.blockPosition(),TideConfig.HUSH_DURATION.get());
                        case 5 -> DreamService.begin(level,p.blockPosition(),TideConfig.DREAM_DURATION.get());
                        default -> begin(level,p.blockPosition(),TideConfig.DURATION.get());
                    }
                } else d.quietTicks=1200;
            }
            if(level.getGameTime()%200==0)d.setDirty();
            return;
        }
        if(++d.age>=d.duration){end(level);return;}
        d.setDirty();
        double progress=d.progress();
        var witnesses=level.players().stream().filter(p->!p.isSpectator()&&d.strength(p.getX(),p.getZ())>.25).toList();
        if(d.age%20==0){
            broadcast(level);
            for(var p:witnesses){
                VeilGrade.witness(p,VeilGrade.SEA,d.seed);
                if((records(p)&1)==0){
                    record(p,1);
                    if(TideConfig.AUTO_JOURNAL.get())give(p,new ItemStack(Veilborne.JOURNAL.get()));
                }
                if(progress>.57&&progress<.78&&level.canSeeSky(p.blockPosition())){record(p,2);VeilGrade.deed(p,VeilGrade.SEA,d.seed,VeilGrade.SEA_WHALE);}
                if(p.getInventory().contains(new ItemStack(Veilborne.WHALE_SCALE.get())))record(p,4);
            }
        }
        if(witnesses.isEmpty())return;
        var observer=witnesses.get(0);
        if(progress>=.28&&progress<.85&&(d.flags&1)==0){
            BlockPos pos=findSafe(level,observer.blockPosition().offset(12,0,8),24);
            if(pos!=null&&d.takeFlag(1)){
                drop(level,pos,new ItemStack(Veilborne.BOTTLE.get()));
                level.playSound(null,pos,Veilborne.CHIME.get(),SoundSource.AMBIENT,.7f,1);
            }
        }
        if(progress>=.34&&progress<.8&&(d.flags&2)==0){
            BlockPos pos=findSafe(level,observer.blockPosition().offset(-10,0,12),24);
            if(pos!=null&&d.takeFlag(2)){
                TideSkiff skiff=Veilborne.SKIFF.get().create(level);
                if(skiff!=null){skiff.setOrigin(pos);level.addFreshEntity(skiff);}
            }
        }
        if(TideConfig.VORTEX.get()&&progress>=.43&&(d.flags&4)==0){
            BlockPos pos=findSafe(level,observer.blockPosition().offset(9,0,-9),24);
            if(pos!=null&&d.takeFlag(4)){
                d.vortex=pos;d.vortexAt=d.age;
                level.playSound(null,pos,Veilborne.VORTEX.get(),SoundSource.WEATHER,.8f,1);
                broadcast(level);
            }
        }
        if(!d.vortex.equals(BlockPos.ZERO)){
            double t=d.age-d.vortexAt;
            if(t>=100&&t<280){
                var box=new AABB(d.vortex).inflate(4,10,4);
                for(ServerPlayer p:witnesses)if(box.contains(p.position())&&vortexContains(d.vortex,p.position())&&!p.isCreative()&&level.canSeeSky(p.blockPosition())){
                    p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,240,0,false,false,true));
                    double target=d.vortex.getY()+5;
                    Vec3 motion=p.getDeltaMovement();
                    p.setDeltaMovement(motion.x,p.getY()<target?.19:.055+.018*Math.sin(d.age*.08),motion.z);
                    p.hurtMarked=true;p.fallDistance=0;
                    VeilGrade.deed(p,VeilGrade.SEA,d.seed,VeilGrade.SEA_VORTEX);
                }
            }
            if(t>=340){d.vortex=BlockPos.ZERO;broadcast(level);}
        }
        if(progress>=.72&&progress<.94&&(d.flags&8)==0){
            BlockPos pos=findSafe(level,observer.blockPosition().offset(18,0,-6),32);
            if(pos!=null&&d.takeFlag(8)){d.landing=pos;d.landingAt=d.age;broadcast(level);}
        }
        if(!d.landing.equals(BlockPos.ZERO)&&d.age-d.landingAt>=160&&d.takeFlag(16)){
            drop(level,d.landing,new ItemStack(Veilborne.CONCH.get()));
            scatter(level,d);
            pool(level,d,witnesses);
            level.playSound(null,d.landing,Veilborne.CHIME.get(),SoundSource.AMBIENT,.8f,.7f);
        }
        if(progress>.57&&progress<.71&&d.age%80==0){
            for(Animal a:level.getEntitiesOfClass(Animal.class,observer.getBoundingBox().inflate(24)).stream().limit(12).toList()){
                a.getLookControl().setLookAt(d.center.getX(),d.center.getY()+100,d.center.getZ(),12,12);
            }
            for(var fish:level.getEntitiesOfClass(net.minecraft.world.entity.animal.AbstractFish.class,observer.getBoundingBox().inflate(24)).stream().limit(8).toList()){
                int top=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,fish.getBlockX(),fish.getBlockZ());
                if(level.getFluidState(new BlockPos(fish.getBlockX(),top-1,fish.getBlockZ())).is(net.minecraft.tags.FluidTags.WATER))
                    fish.getNavigation().moveTo(fish.getX(),Math.min(top-1.2,fish.getY()+2),fish.getZ(),.35);
            }
        }
    }
    // ------------------------------------------------------------------ the scales going out
    /** Five scales, scattered round where the beam came down; each one goes out after SCALE_LIFE ticks. */
    public static void scatter(ServerLevel level,TideData d){
        Vec3 c=Vec3.atBottomCenterOf(d.landing);
        for(int k=0;k<SCALES;k++){
            double a=k*Math.PI*2/SCALES+Math.floorMod(d.seed,360L)*Math.PI/180,r=2.2+(k%2)*2.4;
            BlockPos at=findSafe(level,BlockPos.containing(c.x+Math.cos(a)*r,c.y,c.z+Math.sin(a)*r),2);
            if(at==null)at=d.landing;
            ItemEntity item=new ItemEntity(level,at.getX()+.5,at.getY()+.4,at.getZ()+.5,new ItemStack(Veilborne.WHALE_SCALE.get()));
            item.setDeltaMovement(0,.08,0);item.setGlowingTag(true);item.lifespan=SCALE_LIFE;
            item.getPersistentData().putLong(SCALE,d.seed);
            level.addFreshEntity(item);scales.add(item);
        }
    }
    /** The scales' light, thinning as each one runs out. */
    private static void glimmer(ServerLevel level){
        if(scales.isEmpty()||level.getGameTime()%6!=0)return;
        scales.removeIf(i->{
            if(!i.isAlive())return true;
            double left=1-(double)i.getAge()/SCALE_LIFE;
            if(level.random.nextDouble()<.25+left*.75)level.sendParticles(Veilborne.MOTE.get(),i.getX(),i.getY()+.3,i.getZ(),1+(int)(left*3),.12,.25,.12,.004);
            return false;
        });
    }
    @SubscribeEvent public static void scaleCaught(ItemEntityPickupEvent.Post e){
        if(!(e.getPlayer() instanceof ServerPlayer p)||!e.getItemEntity().getPersistentData().contains(SCALE))return;
        long seed=e.getItemEntity().getPersistentData().getLong(SCALE);
        TideData d=TideData.get(p.server.overworld());
        if(!d.active||d.seed!=seed)return;
        int n=VeilGrade.count(p,VeilGrade.SEA,seed,"scales");
        if(n>=3)VeilGrade.deed(p,VeilGrade.SEA,seed,VeilGrade.SEA_SCALES);
        if(n>=SCALES)VeilGrade.deed(p,VeilGrade.SEA,seed,VeilGrade.SEA_ALL_SCALES);
        p.displayClientMessage(Component.translatable("message.veilborne.scale_caught",n,SCALES),true);
    }
    @SubscribeEvent public static void scaleOut(ItemExpireEvent e){
        ItemEntity i=e.getEntity();
        if(!i.getPersistentData().contains(SCALE)||!(i.level() instanceof ServerLevel level))return;
        level.sendParticles(Veilborne.MOTE.get(),i.getX(),i.getY()+.2,i.getZ(),10,.2,.1,.2,.02);
        level.playSound(null,i.blockPosition(),Veilborne.EBB.get(),SoundSource.AMBIENT,.25f,1.6f);
    }

    // ------------------------------------------------------------------ the star-tide pool
    /** Where the scales came down the sea lingers in the water for a few days. */
    public static void pool(ServerLevel level,TideData d,List<ServerPlayer> witnesses){
        long until=level.getGameTime()+POOL_DAYS*24000L;
        d.pools.removeIf(q->q.until()<level.getGameTime()||q.at().distSqr(d.landing)<POOL_RADIUS*POOL_RADIUS);
        d.pools.add(0,new TideData.Pool(d.landing,until));
        while(d.pools.size()>TideData.POOLS)d.pools.remove(d.pools.size()-1);
        d.setDirty();
        for(ServerPlayer p:witnesses)VeilGrade.site(p,VeilGrade.SITE_POOL,d.landing,d.landing,until);
        VeilGrade.broadcast(level);
    }
    public static List<TideData.Pool> pools(ServerLevel overworld){
        long now=overworld.getGameTime();
        return TideData.get(overworld).pools.stream().filter(q->q.until()>=now).toList();
    }
    public static boolean inPool(ServerLevel level,Vec3 at){
        if(!level.dimension().equals(Level.OVERWORLD))return false;
        for(TideData.Pool q:pools(level))if(Math.hypot(at.x-q.at().getX()-.5,at.z-q.at().getZ()-.5)<=POOL_RADIUS)return true;
        return false;
    }
    /** Fishing in the star tide: now and then the line brings up something of the sea above as well. */
    @SubscribeEvent public static void fished(ItemFishedEvent e){
        var hook=e.getHookEntity();
        if(!(hook.level() instanceof ServerLevel level)||!inPool(level,hook.position())||level.random.nextFloat()>=POOL_CHANCE)return;
        Player player=e.getEntity();
        ItemStack rod=player.getMainHandItem().getItem() instanceof FishingRodItem?player.getMainHandItem():player.getOffhandItem();
        LootParams params=new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,hook.position()).withParameter(LootContextParams.TOOL,rod)
            .withParameter(LootContextParams.THIS_ENTITY,hook).withLuck(player.getLuck()).create(LootContextParamSets.FISHING);
        for(ItemStack s:level.getServer().reloadableRegistries().getLootTable(STAR_TIDE).getRandomItems(params)){
            ItemEntity item=new ItemEntity(level,hook.getX(),hook.getY(),hook.getZ(),s);
            double dx=player.getX()-hook.getX(),dy=player.getY()-hook.getY(),dz=player.getZ()-hook.getZ();
            item.setDeltaMovement(dx*.1,dy*.1+Math.sqrt(Math.sqrt(dx*dx+dy*dy+dz*dz))*.08,dz*.1);
            item.setGlowingTag(true);
            level.addFreshEntity(item);
        }
        level.sendParticles(Veilborne.MOTE.get(),hook.getX(),hook.getY()+.2,hook.getZ(),14,.35,.2,.35,.03);
        level.playSound(null,hook.blockPosition(),Veilborne.CHIME.get(),SoundSource.PLAYERS,.45f,1.5f);
    }
    /** A drift bottle with a sea chart rolled up inside it. */
    public static ItemStack chartBottle(){
        ItemStack s=new ItemStack(Veilborne.BOTTLE.get());
        CustomData.update(DataComponents.CUSTOM_DATA,s,t->t.putBoolean(CHART,true));
        s.set(DataComponents.ITEM_NAME,Component.translatable("item.veilborne.chart_bottle"));
        return s;
    }
    public static boolean charted(ItemStack s){CustomData d=s.get(DataComponents.CUSTOM_DATA);return d!=null&&d.copyTag().getBoolean(CHART);}
    /** A treasure map to the nearest buried treasure nobody has found yet, or nothing if there is none within reach. */
    public static ItemStack chart(ServerLevel level,BlockPos near){
        BlockPos at=level.findNearestMapStructure(StructureTags.ON_TREASURE_MAPS,near,50,true);
        if(at==null)return ItemStack.EMPTY;
        ItemStack map=MapItem.create(level,at.getX(),at.getZ(),(byte)1,true,true);
        MapItem.renderBiomePreviewMap(level,map);
        MapItemSavedData.addTargetDecoration(map,at,"+",MapDecorationTypes.RED_X);
        map.set(DataComponents.ITEM_NAME,Component.translatable("item.veilborne.star_chart"));
        return map;
    }

    // ------------------------------------------------------------------ the voyage
    public record Voyage(BlockPos at,boolean deep){}
    public static Vec3 ahead(Vec3 from,float yaw,double distance){double r=Math.toRadians(yaw);return from.add(-Math.sin(r)*distance,0,Math.cos(r)*distance);}
    /**
     * Where the fisherman takes you when you look one way: the nearest shipwreck, ocean ruin, ocean monument or ancient city to
     * a point some way off in that direction. Null if there is none within reach.
     */
    public static Voyage voyage(ServerLevel level,Vec3 from,float yaw){
        var set=level.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(VOYAGE);
        if(set.isEmpty())return null;
        var found=level.getChunkSource().getGenerator().findNearestMapStructure(level,set.get(),BlockPos.containing(ahead(from,yaw,VOYAGE_AHEAD)),VOYAGE_SEARCH,false);
        if(found==null)return null;
        BlockPos p=found.getFirst();
        return new Voyage(new BlockPos(p.getX()+8,0,p.getZ()+8),found.getSecond().is(BuiltinStructures.ANCIENT_CITY));
    }
    /** Sets the voyager down over where they asked to go, breathing starlit water for a while, with a shell that will take them home. */
    public static void arrive(ServerPlayer p,Voyage v,BlockPos home){
        ServerLevel level=p.serverLevel();
        int x=v.at().getX(),z=v.at().getZ();
        level.getChunk(x>>4,z>>4);
        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING,x,z);
        p.stopRiding();
        p.teleportTo(level,x+.5,y+1.5,z+.5,p.getYRot(),p.getXRot());
        p.fallDistance=0;
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,240,0,false,false,true));
        if(v.deep())p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,1200,0,false,false,true));
        else p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING,3600,0,false,false,true));
        give(p,homewardShell(level,home));
        p.displayClientMessage(Component.translatable(v.deep()?"message.veilborne.voyage_deep":"message.veilborne.voyage_sea"),false);
        level.playSound(null,p.blockPosition(),Veilborne.WHALE.get(),SoundSource.AMBIENT,.6f,1.3f);
        TideData d=TideData.get(p.server.overworld());
        if(d.active)VeilGrade.deed(p,VeilGrade.SEA,d.seed,VeilGrade.SEA_VOYAGE);
        VeilGrade.site(p,VeilGrade.SITE_VOYAGE,new BlockPos(x,y,z),home,0);
    }
    public static ItemStack homewardShell(ServerLevel level,BlockPos home){
        ItemStack s=new ItemStack(Veilborne.HOMEWARD_SHELL.get());
        CustomData.update(DataComponents.CUSTOM_DATA,s,t->{t.putLong(HOME,home.asLong());t.putString(HOME_DIM,level.dimension().location().toString());});
        return s;
    }
    public static void give(ServerPlayer p,ItemStack stack){if(!p.getInventory().add(stack))p.drop(stack,false);}
    public static boolean vortexContains(BlockPos center,Vec3 position){
        return Math.hypot(position.x-center.getX()-.5,position.z-center.getZ()-.5)<=3.5&&position.y>=center.getY()-.25&&position.y<=center.getY()+8;
    }
    public static void drop(ServerLevel level,BlockPos pos,ItemStack stack){
        ItemEntity item=new ItemEntity(level,pos.getX()+.5,pos.getY()+.4,pos.getZ()+.5,stack);
        item.setDeltaMovement(0,.08,0);item.setGlowingTag(true);item.setExtendedLifetime();level.addFreshEntity(item);
    }
    /** Only already-loaded, dry, sturdy surfaces. Never generate terrain for an encounter. */
    public static BlockPos findSafe(ServerLevel level,BlockPos near,int range){
        for(int i=0;i<48;i++){
            int x=near.getX()+(i==0?0:(i*13%(range*2+1))-range);
            int z=near.getZ()+(i==0?0:(i*19%(range*2+1))-range);
            if(!level.hasChunk(x>>4,z>>4))continue;
            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            BlockPos p=new BlockPos(x,y,z);
            if(y<=level.getMinBuildHeight()||y>=level.getMaxBuildHeight()-4)continue;
            var floor=level.getBlockState(p.below());
            if(!floor.isFaceSturdy(level,p.below(),net.minecraft.core.Direction.UP)||floor.is(Blocks.MAGMA_BLOCK)||floor.is(Blocks.CACTUS))continue;
            if(level.getFluidState(p.below()).isEmpty()&&level.getBlockState(p).isAir()&&level.getBlockState(p.above()).isAir()&&level.getBlockState(p.above(2)).isAir())return p;
        }
        return null;
    }
    static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> command(){
        return VeilService.weather("starfall",TideConfig.DURATION::get,TideService::begin,TideService::end,l->TideData.get(l).active,
            (l,p)->{var d=TideData.get(l);d.age=(int)(d.duration*p/100.0);d.setDirty();broadcast(l);},
            l->{var d=TideData.get(l);return "星海倒灌 · "+(d.active?TideTimeline.phase(d.progress())+" "+d.age/20+" / "+d.duration/20+"s · "+d.center.toShortString():"静候 · "+d.quietTicks/20+"s");});
    }
    private TideService(){}
}
