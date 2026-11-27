package com.suiyi.veilborne;

import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.*;

@Mod(Veilborne.ID)
public final class Veilborne {
    public static final String ID="veilborne";
    public static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath(ID,path);}
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(ID);
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(ID);
    public static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(Registries.SOUND_EVENT,ID);
    public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLES=DeferredRegister.create(Registries.PARTICLE_TYPE,ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,ID);
    public static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,ID);
    public static final DeferredRegister<net.minecraft.world.effect.MobEffect> EFFECTS=DeferredRegister.create(Registries.MOB_EFFECT,ID);
    public static final DeferredRegister<net.minecraft.world.item.alchemy.Potion> POTIONS=DeferredRegister.create(Registries.POTION,ID);
    public static final DeferredHolder<SoundEvent,SoundEvent> SEA=SOUNDS.register("sea",()->SoundEvent.createVariableRangeEvent(id("sea")));
    public static final DeferredHolder<SoundEvent,SoundEvent> WHALE=SOUNDS.register("whale",()->SoundEvent.createVariableRangeEvent(id("whale")));
    public static final DeferredHolder<SoundEvent,SoundEvent> CHIME=SOUNDS.register("chime",()->SoundEvent.createVariableRangeEvent(id("chime")));
    public static final DeferredHolder<SoundEvent,SoundEvent> VORTEX=SOUNDS.register("vortex",()->SoundEvent.createVariableRangeEvent(id("vortex")));
    public static final DeferredHolder<SoundEvent,SoundEvent> EBB=SOUNDS.register("ebb",()->SoundEvent.createVariableRangeEvent(id("ebb")));
    public static final DeferredHolder<SoundEvent,SoundEvent> MOON_DRONE=sound("moon_drone"),MOON_OMEN=sound("moon_omen"),MOON_CRACK=sound("moon_crack"),
        MOON_MOLT=sound("moon_molt"),MOON_BELL=sound("moon_bell"),MOON_SCATTER=sound("moon_scatter"),MOON_CLOSE=sound("moon_close");
    public static final java.util.List<DeferredHolder<SoundEvent,SoundEvent>> LULLABY=java.util.stream.IntStream.range(0,6).mapToObj(i->sound("lullaby_"+i)).toList();
    public static final DeferredHolder<SoundEvent,SoundEvent> MIRROR_DRONE=sound("mirror_drone"),MIRROR_OMEN=sound("mirror_omen"),MIRROR_SILVER=sound("mirror_silver"),MIRROR_CRACK=sound("mirror_crack"),
        MIRROR_STEP=sound("mirror_step"),MIRROR_DOOR=sound("mirror_door"),MIRROR_WHINE=sound("mirror_whine"),MIRROR_BURST=sound("mirror_burst"),MIRROR_SHATTER=sound("mirror_shatter"),
        MIRROR_CHIME=sound("mirror_chime"),MIRROR_REWIND=sound("mirror_rewind");
    public static final java.util.List<DeferredHolder<SoundEvent,SoundEvent>> HYMN=java.util.stream.IntStream.range(0,5).mapToObj(i->sound("hymn_"+i)).toList();
    public static final DeferredHolder<SoundEvent,SoundEvent> THREAD_DRONE=sound("thread_drone"),THREAD_OMEN=sound("thread_omen"),THREAD_TEAR=sound("thread_tear"),THREAD_LOOM=sound("thread_loom"),
        THREAD_PIERCE=sound("thread_pierce"),THREAD_WARN=sound("thread_warn"),THREAD_STRIKE=sound("thread_strike"),THREAD_TUG=sound("thread_tug"),THREAD_SNAP=sound("thread_snap"),
        THREAD_KNOT=sound("thread_knot"),THREAD_WALKER=sound("thread_walker"),THREAD_SPIRIT=sound("thread_spirit"),THREAD_ANCHOR=sound("thread_anchor"),THREAD_CHIME=sound("thread_chime"),
        THREAD_FADE=sound("thread_fade"),THREAD_POCKET=sound("thread_pocket");
    public static final java.util.List<DeferredHolder<SoundEvent,SoundEvent>> WEAVE=java.util.stream.IntStream.range(0,5).mapToObj(i->sound("weave_"+i)).toList();
    public static final DeferredHolder<SoundEvent,SoundEvent> HUSH_DRONE=sound("hush_drone"),HUSH_OMEN=sound("hush_omen"),HUSH_FADE=sound("hush_fade"),HUSH_KNOCK=sound("hush_knock"),
        HUSH_BELL=sound("hush_bell"),HUSH_HEART=sound("hush_heart"),HUSH_TING=sound("hush_ting"),HUSH_THUNDER=sound("hush_thunder"),HUSH_BIRD=sound("hush_bird"),HUSH_CHIRP=sound("hush_chirp"),
        HUSH_RETURN=sound("hush_return"),HUSH_ERASE=sound("hush_erase"),HUSH_WHISPER=sound("hush_whisper"),HUSH_HOLLOW=sound("hush_hollow"),HUSH_ANSWER=sound("hush_answer");
    public static final java.util.List<DeferredHolder<SoundEvent,SoundEvent>> HARMONY=java.util.stream.IntStream.range(0,4).mapToObj(i->sound("harmony_"+i)).toList();
    public static final DeferredHolder<SoundEvent,SoundEvent> DREAM_DRONE=sound("dream_drone"),DREAM_OMEN=sound("dream_omen"),DREAM_ROOT=sound("dream_root"),DREAM_RUSTLE=sound("dream_rustle"),
        DREAM_BLOOM=sound("dream_bloom"),DREAM_STEP=sound("dream_step"),DREAM_CALL=sound("dream_call"),DREAM_GLANCE=sound("dream_glance"),DREAM_SPROUT=sound("dream_sprout"),
        DREAM_CHIME=sound("dream_chime"),DREAM_VINE=sound("dream_vine"),DREAM_FISH=sound("dream_fish"),DREAM_FALL=sound("dream_fall"),DREAM_RETURN=sound("dream_return"),
        DREAM_BIRD=sound("dream_bird"),DREAM_DRIP=sound("dream_drip"),DREAM_HUM=sound("dream_hum");
    /** Short reed and plucked phrases the client plays with long gaps between them. */
    public static final java.util.List<DeferredHolder<SoundEvent,SoundEvent>> DREAM_PHRASES=java.util.stream.Stream.of("dream_reed_0","dream_reed_1","dream_reed_2","dream_pluck_0","dream_pluck_1","dream_pluck_2").map(Veilborne::sound).toList();
    private static DeferredHolder<SoundEvent,SoundEvent> sound(String name){return SOUNDS.register(name,()->SoundEvent.createVariableRangeEvent(id(name)));}
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>,SimpleParticleType> MOTE=PARTICLES.register("mote",()->new SimpleParticleType(false));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>,SimpleParticleType> DROPLET=PARTICLES.register("droplet",()->new SimpleParticleType(false));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>,SimpleParticleType> PETAL=PARTICLES.register("petal",()->new SimpleParticleType(false));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>,SimpleParticleType> MOON_MOTE=PARTICLES.register("moon_mote",()->new SimpleParticleType(false));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>,SimpleParticleType> MIRROR_MOTE=PARTICLES.register("mirror_mote",()->new SimpleParticleType(false));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>,SimpleParticleType> FLAKE=PARTICLES.register("mirror_flake",()->new SimpleParticleType(false));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>,SimpleParticleType> THREAD_FIBER=PARTICLES.register("thread_fiber",()->new SimpleParticleType(false));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>,SimpleParticleType> THREAD_EMBER=PARTICLES.register("thread_ember",()->new SimpleParticleType(false));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>,SimpleParticleType> HUSH_CRUMB=PARTICLES.register("hush_crumb",()->new SimpleParticleType(false));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>,SimpleParticleType> DREAM_POLLEN=PARTICLES.register("dream_pollen",()->new SimpleParticleType(false));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>,SimpleParticleType> DREAM_FIREFLY=PARTICLES.register("dream_firefly",()->new SimpleParticleType(false));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>,SimpleParticleType> DREAM_FLUFF=PARTICLES.register("dream_fluff",()->new SimpleParticleType(false));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>,SimpleParticleType> DREAM_LEAF=PARTICLES.register("dream_leaf",()->new SimpleParticleType(false));
    public static final DeferredBlock<TideLanternBlock> TIDE_LANTERN=BLOCKS.register("tide_lantern",()->new TideLanternBlock(BlockBehaviour.Properties.of().strength(.4f).noOcclusion().lightLevel(s->12).sound(SoundType.GLASS)));
    public static final DeferredItem<BlockItem> TIDE_BOTTLE=ITEMS.registerSimpleBlockItem("tide_bottle",TIDE_LANTERN);
    public static final DeferredItem<Item> WHALE_SCALE=ITEMS.registerSimpleItem("whale_scale",new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<TideItem> CONCH=ITEMS.register("star_conch",()->new TideItem(TideItem.Kind.CONCH));
    public static final DeferredItem<TideItem> MANTLE=ITEMS.register("whale_mantle",()->new TideItem(TideItem.Kind.MANTLE));
    public static final DeferredItem<TideItem> JOURNAL=ITEMS.register("field_journal",()->new TideItem(TideItem.Kind.JOURNAL));
    public static final DeferredItem<TideItem> BOTTLE=ITEMS.register("drift_bottle",()->new TideItem(TideItem.Kind.MESSAGE));
    public static final DeferredItem<TideItem> ATTUNER=ITEMS.register("tide_attuner",()->new TideItem(TideItem.Kind.ATTUNER));
    public static final DeferredItem<TideItem> HOMEWARD_SHELL=ITEMS.register("homeward_shell",()->new TideItem(TideItem.Kind.SHELL));
    public static final DeferredHolder<EntityType<?>,EntityType<TideSkiff>> SKIFF=ENTITIES.register("tide_skiff",()->EntityType.Builder.<TideSkiff>of(TideSkiff::new,MobCategory.MISC).sized(2.4f,.9f).clientTrackingRange(12).updateInterval(2).fireImmune().build("veilborne:tide_skiff"));
    public static final DeferredBlock<SilverLanternBlock> SILVER_LANTERN=BLOCKS.register("silver_lantern",()->new SilverLanternBlock(BlockBehaviour.Properties.of().strength(.3f).noOcclusion().lightLevel(s->11).sound(SoundType.AMETHYST).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
    public static final DeferredItem<BlockItem> SILVER_LANTERN_ITEM=ITEMS.registerSimpleBlockItem("silver_lantern",SILVER_LANTERN);
    public static final DeferredItem<MoonItem> MOON_DEW=ITEMS.register("moon_dew",()->new MoonItem(MoonItem.Kind.DEW));
    public static final DeferredItem<MoonItem> MOON_SHARD=ITEMS.register("moon_shard",()->new MoonItem(MoonItem.Kind.SHARD));
    public static final DeferredItem<MoonItem> MOON_GAUZE=ITEMS.register("moon_gauze",()->new MoonItem(MoonItem.Kind.GAUZE));
    public static final DeferredItem<MoonItem> MOON_DIAL=ITEMS.register("moon_dial",()->new MoonItem(MoonItem.Kind.DIAL));
    public static final DeferredItem<MoonItem> MOON_BELL_ITEM=ITEMS.register("moonward_bell",()->new MoonItem(MoonItem.Kind.BELL));
    public static final DeferredHolder<net.minecraft.world.effect.MobEffect,MoonHaven.Moonveil> MOONVEIL=EFFECTS.register("moonveil",MoonHaven.Moonveil::new);
    public static final DeferredHolder<net.minecraft.world.item.alchemy.Potion,net.minecraft.world.item.alchemy.Potion> MOONVEIL_POTION=POTIONS.register("moonveil",
        ()->new net.minecraft.world.item.alchemy.Potion("moonveil",new net.minecraft.world.effect.MobEffectInstance(MOONVEIL,3600)));
    public static final DeferredHolder<net.minecraft.world.item.alchemy.Potion,net.minecraft.world.item.alchemy.Potion> LONG_MOONVEIL_POTION=POTIONS.register("long_moonveil",
        ()->new net.minecraft.world.item.alchemy.Potion("moonveil",new net.minecraft.world.effect.MobEffectInstance(MOONVEIL,9600)));
    public static final DeferredHolder<EntityType<?>,EntityType<MoonProcession>> PROCESSION=ENTITIES.register("moon_procession",()->EntityType.Builder.<MoonProcession>of(MoonProcession::new,MobCategory.MISC).sized(1f,2.3f).clientTrackingRange(10).updateInterval(2).fireImmune().build("veilborne:moon_procession"));
    public static final DeferredHolder<EntityType<?>,EntityType<MoonDecoy>> DECOY=ENTITIES.register("moon_decoy",()->EntityType.Builder.<MoonDecoy>of(MoonDecoy::new,MobCategory.MISC).sized(.6f,1.8f).clientTrackingRange(8).updateInterval(4).fireImmune().build("veilborne:moon_decoy"));
    public static final DeferredBlock<MirrorLanternBlock> MIRROR_LANTERN=BLOCKS.register("mirror_lantern",()->new MirrorLanternBlock(BlockBehaviour.Properties.of().strength(.4f).noOcclusion().lightLevel(s->13).sound(SoundType.LANTERN).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
    public static final DeferredItem<BlockItem> MIRROR_LANTERN_ITEM=ITEMS.registerSimpleBlockItem("mirror_lantern",MIRROR_LANTERN);
    public static final DeferredBlock<SkyShardBlock> SKY_SHARD=BLOCKS.register("sky_shard",()->new SkyShardBlock(BlockBehaviour.Properties.of().strength(.3f).noOcclusion().noCollission().lightLevel(s->7).sound(SoundType.AMETHYST_CLUSTER).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
    public static final DeferredItem<BlockItem> SKY_SHARD_ITEM=ITEMS.register("sky_shard",()->new BlockItem(SKY_SHARD.get(),new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<MirrorItem> MIRROR_SAND=ITEMS.register("mirror_sand",()->new MirrorItem(MirrorItem.Kind.SAND));
    public static final DeferredItem<MirrorItem> PRISM=ITEMS.register("observation_prism",()->new MirrorItem(MirrorItem.Kind.PRISM));
    public static final DeferredItem<MirrorItem> ECHO_MIRROR=ITEMS.register("echo_mirror",()->new MirrorItem(MirrorItem.Kind.ECHO));
    public static final DeferredItem<MirrorItem> SKY_MIRROR=ITEMS.register("sky_mirror",()->new MirrorItem(MirrorItem.Kind.SKY_MIRROR));
    public static final DeferredItem<MirrorItem> MIRROR_HEART=ITEMS.register("mirror_heart",()->new MirrorItem(MirrorItem.Kind.HEART));
    public static final DeferredHolder<EntityType<?>,EntityType<MirrorTraveler>> TRAVELER=ENTITIES.register("mirror_traveler",()->EntityType.Builder.<MirrorTraveler>of(MirrorTraveler::new,MobCategory.MISC).sized(.6f,1.8f).clientTrackingRange(8).updateInterval(1).fireImmune().noSave().build("veilborne:mirror_traveler"));
    public static final DeferredHolder<EntityType<?>,EntityType<MirrorFrames>> FRAMES=ENTITIES.register("mirror_frames",()->EntityType.Builder.<MirrorFrames>of(MirrorFrames::new,MobCategory.MISC).sized(1f,2.6f).clientTrackingRange(8).updateInterval(20).fireImmune().build("veilborne:mirror_frames"));
    public static final DeferredHolder<EntityType<?>,EntityType<MirrorBeast>> BEAST=ENTITIES.register("mirror_beast",()->EntityType.Builder.<MirrorBeast>of(MirrorBeast::new,MobCategory.MONSTER).sized(.7f,.45f).clientTrackingRange(8).build("veilborne:mirror_beast"));
    public static final DeferredBlock<ThreadBlocks.Anchor> TUNING_ANCHOR=BLOCKS.register("tuning_anchor",()->new ThreadBlocks.Anchor(BlockBehaviour.Properties.of().strength(1.2f).noOcclusion().lightLevel(s->6).sound(SoundType.CHAIN).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
    public static final DeferredItem<BlockItem> TUNING_ANCHOR_ITEM=ITEMS.register("tuning_anchor",()->new BlockItem(TUNING_ANCHOR.get(),new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredBlock<ThreadBlocks.Lantern> LOOM_LANTERN=BLOCKS.register("loom_lantern",()->new ThreadBlocks.Lantern(BlockBehaviour.Properties.of().strength(.4f).noOcclusion().lightLevel(s->13).sound(SoundType.LANTERN).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
    public static final DeferredItem<BlockItem> LOOM_LANTERN_ITEM=ITEMS.registerSimpleBlockItem("loom_lantern",LOOM_LANTERN);
    public static final DeferredBlock<ThreadBlocks.Scrap> ROBE_SCRAP=BLOCKS.register("robe_scrap",()->new ThreadBlocks.Scrap(BlockBehaviour.Properties.of().strength(.2f).noOcclusion().noCollission().lightLevel(s->5).sound(SoundType.WOOL).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
    public static final DeferredItem<BlockItem> ROBE_SCRAP_ITEM=ITEMS.register("robe_scrap",()->new BlockItem(ROBE_SCRAP.get(),new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<ThreadItem> SCARLET_THREAD=ITEMS.register("scarlet_thread",()->new ThreadItem(ThreadItem.Kind.THREAD));
    public static final DeferredItem<ThreadItem> SEAM_SILK=ITEMS.register("seam_silk",()->new ThreadItem(ThreadItem.Kind.SILK));
    public static final DeferredItem<ThreadItem> BINDING_CHARM=ITEMS.register("binding_charm",()->new ThreadItem(ThreadItem.Kind.CHARM));
    public static final DeferredItem<ThreadItem> HEAVEN_NEEDLE=ITEMS.register("heaven_needle",()->new ThreadItem(ThreadItem.Kind.NEEDLE));
    public static final DeferredItem<ThreadItem> NEEDLE_CORE=ITEMS.register("needle_core",()->new ThreadItem(ThreadItem.Kind.CORE));
    public static final DeferredHolder<EntityType<?>,EntityType<NeedleWalker>> WALKER=ENTITIES.register("needle_walker",()->EntityType.Builder.<NeedleWalker>of(NeedleWalker::new,MobCategory.MISC).sized(1.3f,.9f).clientTrackingRange(10).updateInterval(2).fireImmune().build("veilborne:needle_walker"));
    public static final DeferredHolder<EntityType<?>,EntityType<ThreadSpirit>> SPIRIT=ENTITIES.register("thread_spirit",()->EntityType.Builder.<ThreadSpirit>of(ThreadSpirit::new,MobCategory.MONSTER).sized(.6f,.6f).clientTrackingRange(8).fireImmune().noSave().build("veilborne:thread_spirit"));
    public static final DeferredBlock<HushBlocks.Candle> STILL_CANDLE=BLOCKS.register("still_candle",()->new HushBlocks.Candle(BlockBehaviour.Properties.of().strength(.2f).noOcclusion().lightLevel(s->5).sound(SoundType.CANDLE).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
    public static final DeferredItem<BlockItem> STILL_CANDLE_ITEM=ITEMS.registerSimpleBlockItem("still_candle",STILL_CANDLE);
    public static final DeferredBlock<net.minecraft.world.level.block.LanternBlock> SILENT_LANTERN=BLOCKS.register("silent_lantern",()->new net.minecraft.world.level.block.LanternBlock(BlockBehaviour.Properties.of().strength(.4f).noOcclusion().lightLevel(s->14).sound(SoundType.LANTERN).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
    public static final DeferredItem<BlockItem> SILENT_LANTERN_ITEM=ITEMS.register("silent_lantern",()->new BlockItem(SILENT_LANTERN.get(),new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredBlock<HushBlocks.Feather> STILL_FEATHER=BLOCKS.register("still_feather",()->new HushBlocks.Feather(BlockBehaviour.Properties.of().strength(.3f).noOcclusion().sound(SoundType.GLASS).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
    public static final DeferredItem<BlockItem> STILL_FEATHER_ITEM=ITEMS.register("still_feather",()->new BlockItem(STILL_FEATHER.get(),new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<HushItem> DAYLIGHT_WAX=ITEMS.register("daylight_wax",()->new HushItem(HushItem.Kind.WAX));
    public static final DeferredItem<HushItem> HOLLOW_BELL=ITEMS.register("hollow_bell",()->new HushItem(HushItem.Kind.BELL));
    public static final DeferredItem<HushItem> STOPPED_CLOCK=ITEMS.register("stopped_clock",()->new HushItem(HushItem.Kind.CLOCK));
    public static final DeferredHolder<EntityType<?>,EntityType<HushProcession>> HUSH_PROCESSION=ENTITIES.register("hush_procession",()->EntityType.Builder.<HushProcession>of(HushProcession::new,MobCategory.MISC).sized(1f,2f).clientTrackingRange(10).updateInterval(2).fireImmune().noSave().build("veilborne:hush_procession"));
    public static final DeferredHolder<EntityType<?>,EntityType<HushCoat>> HUSH_COAT=ENTITIES.register("hush_coat",()->EntityType.Builder.<HushCoat>of(HushCoat::new,MobCategory.MISC).sized(.7f,1.9f).clientTrackingRange(8).updateInterval(2).fireImmune().noSave().build("veilborne:hush_coat"));
    public static final DeferredBlock<DreamBlocks.Sapling> DREAM_SAPLING=BLOCKS.register("dream_sapling",()->new DreamBlocks.Sapling(BlockBehaviour.Properties.of().noCollission().instabreak().randomTicks().noOcclusion().lightLevel(s->3).sound(SoundType.GRASS).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
    public static final DeferredBlock<net.minecraft.world.level.block.RotatedPillarBlock> DREAM_LOG=BLOCKS.register("dream_log",()->new net.minecraft.world.level.block.RotatedPillarBlock(BlockBehaviour.Properties.of().strength(2f).sound(SoundType.CHERRY_WOOD)));
    public static final DeferredItem<BlockItem> DREAM_LOG_ITEM=ITEMS.registerSimpleBlockItem("dream_log",DREAM_LOG);
    /** The heart of a grown dream tree has no item of its own: it is only ever grown, and breaks into a log. */
    public static final DeferredBlock<DreamBlocks.Heart> DREAM_HEART=BLOCKS.register("dream_heart",()->new DreamBlocks.Heart(BlockBehaviour.Properties.of().strength(2f).sound(SoundType.CHERRY_WOOD)
        .lightLevel(s->6).randomTicks()));
    public static final DeferredBlock<DreamBlocks.Leaves> DREAM_LEAVES=BLOCKS.register("dream_leaves",()->new DreamBlocks.Leaves(BlockBehaviour.Properties.of().strength(.2f).noOcclusion().sound(SoundType.AZALEA_LEAVES)
        .isValidSpawn((s,l,p,t)->false).isSuffocating((s,l,p)->false).isViewBlocking((s,l,p)->false).lightLevel(s->s.getValue(DreamBlocks.BLOSSOM)?9:5).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
    public static final DeferredItem<BlockItem> DREAM_LEAVES_ITEM=ITEMS.registerSimpleBlockItem("dream_leaves",DREAM_LEAVES);
    public static final DeferredBlock<DreamBlocks.Lamp> FLOWER_LAMP=BLOCKS.register("flower_lamp",()->new DreamBlocks.Lamp(BlockBehaviour.Properties.of().strength(.4f).noOcclusion().lightLevel(s->13).sound(SoundType.AZALEA).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
    public static final DeferredItem<BlockItem> FLOWER_LAMP_ITEM=ITEMS.register("flower_lamp",()->new BlockItem(FLOWER_LAMP.get(),new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredBlock<DreamBlocks.Twig> ANTLER_TWIG=BLOCKS.register("antler_twig",()->new DreamBlocks.Twig(BlockBehaviour.Properties.of().strength(.3f).noOcclusion().noCollission().lightLevel(s->7).sound(SoundType.CHERRY_WOOD).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
    public static final DeferredItem<BlockItem> ANTLER_TWIG_ITEM=ITEMS.register("antler_twig",()->new BlockItem(ANTLER_TWIG.get(),new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<DreamItem> DREAM_SEED=ITEMS.register("dream_seed",()->new DreamItem(DreamItem.Kind.SEED));
    public static final DeferredItem<DreamItem> FULL_DREAM_SEED=ITEMS.register("full_dream_seed",()->new DreamItem(DreamItem.Kind.FULL_SEED));
    public static final DeferredItem<DreamItem> GOLDEN_LEAF=ITEMS.register("golden_leaf",()->new DreamItem(DreamItem.Kind.LEAF));
    public static final DeferredItem<DreamItem> DRIFTING_LEAF=ITEMS.register("drifting_leaf",()->new DreamItem(DreamItem.Kind.CHARM));
    public static final DeferredItem<DreamItem> DEER_FLUTE=ITEMS.register("deer_flute",()->new DreamItem(DreamItem.Kind.FLUTE));
    public static final DeferredHolder<EntityType<?>,EntityType<DreamSeed>> DREAM_SEED_ENTITY=ENTITIES.register("dream_seed",()->EntityType.Builder.<DreamSeed>of(DreamSeed::new,MobCategory.MISC).sized(.5f,.5f).clientTrackingRange(8).updateInterval(1).fireImmune().noSave().build("veilborne:dream_seed"));
    public static final DeferredHolder<EntityType<?>,EntityType<LeafSchool>> LEAF_SCHOOL=ENTITIES.register("leaf_school",()->EntityType.Builder.<LeafSchool>of(LeafSchool::new,MobCategory.MISC).sized(1f,1f).clientTrackingRange(8).updateInterval(2).fireImmune().noSave().build("veilborne:leaf_school"));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<TideLanternEntity>> LANTERN_ENTITY=BLOCK_ENTITIES.register("tide_lantern",()->BlockEntityType.Builder.of(TideLanternEntity::new,TIDE_LANTERN.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ThreadBlocks.LoomLink>> LOOM_LINK=BLOCK_ENTITIES.register("loom_lantern",()->BlockEntityType.Builder.of(ThreadBlocks.LoomLink::new,LOOM_LANTERN.get()).build(null));
    public static Consumer<TidePacket> clientPacket=p->{};
    public static Runnable openJournal=()->{};
    public static Consumer<TideSkiff> skiffAmbience=s->{};
    public static Consumer<MoonPacket> clientMoonPacket=p->{};
    public static Consumer<MoonProcession> processionAmbience=p->{};
    public static Consumer<MoonDecoy> decoyAmbience=d->{};
    public static Consumer<MirrorPacket> clientMirrorPacket=p->{};
    public static Consumer<MirrorTraveler> travelerAmbience=t->{};
    public static Runnable echoMirror=()->{};
    public static Consumer<ThreadPacket> clientThreadPacket=p->{};
    public static Consumer<HushPacket> clientHushPacket=p->{};
    public static Consumer<HushCue> clientHushCue=p->{};
    public static Consumer<DreamPacket> clientDreamPacket=p->{};
    public static Consumer<DreamCue> clientDreamCue=p->{};
    public static Consumer<VeilPacket> clientVeilPacket=p->{};
    public static Consumer<VeilCue> clientVeilCue=p->{};
    static {
        TABS.register("wonders",()->CreativeModeTab.builder().title(Component.translatable("itemGroup.veilborne"))
            .icon(()->new ItemStack(TIDE_BOTTLE.get())).displayItems((p,o)->{
                ITEMS.getEntries().forEach(i->o.accept(i.get()));
                for(Item kind:new Item[]{Items.POTION,Items.SPLASH_POTION,Items.LINGERING_POTION})for(var potion:java.util.List.of(MOONVEIL_POTION,LONG_MOONVEIL_POTION))
                    o.accept(net.minecraft.world.item.alchemy.PotionContents.createItemStack(kind,potion));
            }).build());
    }
    public Veilborne(IEventBus bus,ModContainer container){
        ITEMS.register(bus);BLOCKS.register(bus);SOUNDS.register(bus);PARTICLES.register(bus);
        ENTITIES.register(bus);BLOCK_ENTITIES.register(bus);TABS.register(bus);EFFECTS.register(bus);POTIONS.register(bus);
        container.registerConfig(ModConfig.Type.SERVER,TideConfig.SERVER);
        container.registerConfig(ModConfig.Type.CLIENT,TideConfig.CLIENT);
        bus.addListener(this::network);
        bus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent e)->{
            e.put(BEAST.get(),MirrorBeast.attributes().build());e.put(WALKER.get(),NeedleWalker.attributes().build());e.put(SPIRIT.get(),ThreadSpirit.attributes().build());
        });
        NeoForge.EVENT_BUS.register(TideService.class);
        NeoForge.EVENT_BUS.register(MoonService.class);
        NeoForge.EVENT_BUS.register(MirrorService.class);
        NeoForge.EVENT_BUS.register(MirrorGallery.class);
        NeoForge.EVENT_BUS.register(ThreadService.class);
        NeoForge.EVENT_BUS.register(HushService.class);
        NeoForge.EVENT_BUS.register(DreamService.class);
        NeoForge.EVENT_BUS.register(VeilService.class);
        NeoForge.EVENT_BUS.register(VeilGrade.class);
        NeoForge.EVENT_BUS.register(MoonHaven.class);
        NeoForge.EVENT_BUS.register(LoomLinks.class);
        NeoForge.EVENT_BUS.register(HushSilence.class);
        NeoForge.EVENT_BUS.register(DreamHeart.class);
    }
    private void network(RegisterPayloadHandlersEvent e){
        var r=e.registrar("7");
        r.playToClient(TidePacket.TYPE,TidePacket.CODEC,(p,c)->clientPacket.accept(p));
        r.playToClient(MoonPacket.TYPE,MoonPacket.CODEC,(p,c)->clientMoonPacket.accept(p));
        r.playToClient(MirrorPacket.TYPE,MirrorPacket.CODEC,(p,c)->clientMirrorPacket.accept(p));
        r.playToClient(ThreadPacket.TYPE,ThreadPacket.CODEC,(p,c)->clientThreadPacket.accept(p));
        r.playToClient(HushPacket.TYPE,HushPacket.CODEC,(p,c)->clientHushPacket.accept(p));
        r.playToClient(HushCue.TYPE,HushCue.CODEC,(p,c)->clientHushCue.accept(p));
        r.playToClient(DreamPacket.TYPE,DreamPacket.CODEC,(p,c)->clientDreamPacket.accept(p));
        r.playToClient(DreamCue.TYPE,DreamCue.CODEC,(p,c)->clientDreamCue.accept(p));
        r.playToClient(VeilPacket.TYPE,VeilPacket.CODEC,(p,c)->clientVeilPacket.accept(p));
        r.playToClient(VeilCue.TYPE,VeilCue.CODEC,(p,c)->clientVeilCue.accept(p));
    }
}
