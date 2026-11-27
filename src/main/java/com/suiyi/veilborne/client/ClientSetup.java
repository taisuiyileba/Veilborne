package com.suiyi.veilborne.client;

import com.suiyi.veilborne.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(modid=Veilborne.ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent public static void setup(FMLClientSetupEvent e){e.enqueueWork(()->{
        Veilborne.clientPacket=TideClient::receive;
        Veilborne.openJournal=()->Minecraft.getInstance().setScreen(new TideJournalScreen());
        NeoForge.EVENT_BUS.register(TideClient.class);
        NeoForge.EVENT_BUS.register(TideRenderer.class);
        Veilborne.skiffAmbience=TideClient::skiffAmbience;
        Veilborne.clientMoonPacket=MoonClient::receive;
        NeoForge.EVENT_BUS.register(MoonClient.class);
        NeoForge.EVENT_BUS.register(MoonRenderer.class);
        Veilborne.processionAmbience=MoonAmbience::procession;
        Veilborne.decoyAmbience=MoonAmbience::decoy;
        Veilborne.clientMirrorPacket=MirrorClient::receive;
        NeoForge.EVENT_BUS.register(MirrorClient.class);
        NeoForge.EVENT_BUS.register(MirrorRenderer.class);
        NeoForge.EVENT_BUS.register(MirrorOverlay.class);
        Veilborne.travelerAmbience=MirrorOverlay::traveler;
        Veilborne.echoMirror=MirrorClient::showEcho;
        Veilborne.clientThreadPacket=ThreadClient::receive;
        NeoForge.EVENT_BUS.register(ThreadClient.class);
        NeoForge.EVENT_BUS.register(ThreadRenderer.class);
        Veilborne.clientHushPacket=HushClient::receive;
        Veilborne.clientHushCue=HushClient::cue;
        NeoForge.EVENT_BUS.register(HushClient.class);
        NeoForge.EVENT_BUS.register(HushRenderer.class);
        Veilborne.clientDreamPacket=DreamClient::receive;
        Veilborne.clientDreamCue=DreamClient::cue;
        NeoForge.EVENT_BUS.register(DreamClient.class);
        NeoForge.EVENT_BUS.register(DreamRenderer.class);
        Veilborne.clientVeilPacket=VeilClient::receive;
        Veilborne.clientVeilCue=VeilClient::cue;
        NeoForge.EVENT_BUS.register(VeilClient.class);
        // 空响铃: it looks different once it holds a weather's sound.
        net.minecraft.client.renderer.item.ItemProperties.register(Veilborne.HOLLOW_BELL.get(),Veilborne.id("recorded"),(stack,level,entity,seed)->HushItem.recording(stack)>=0?1:0);
        // 银针月仪: the needle points at the true moon relative to where its holder is facing.
        net.minecraft.client.renderer.item.ItemProperties.register(Veilborne.MOON_DIAL.get(),Veilborne.id("needle"),(stack,level,entity,seed)->MoonAmbience.needle(entity));
        if(Boolean.getBoolean("veilborne.preview")){
            String mode=System.getProperty("veilborne.previewMode");
            NeoForge.EVENT_BUS.register("lifecycle".equals(mode)?LifecyclePreview.class:"moonlife".equals(mode)?MoonLifecyclePreview.class:"mirrorlife".equals(mode)?MirrorLifecyclePreview.class
                :"threadlife".equals(mode)?ThreadLifecyclePreview.class:"hushlife".equals(mode)?HushLifecyclePreview.class:"dreamlife".equals(mode)?DreamLifecyclePreview.class:"art".equals(mode)?ArtPreview.class:PreviewCapture.class);
        }
        if(System.getProperty("veilborne.networkPreview")!=null){
            Veilborne.clientPacket=p->{TideClient.receive(p);NetworkPreview.packet(p);};
            NeoForge.EVENT_BUS.register(NetworkPreview.class);
        }
    });}
    @SubscribeEvent public static void particles(RegisterParticleProvidersEvent e){
        e.registerSpriteSet(Veilborne.MOTE.get(),TideParticle.Provider::new);
        e.registerSpriteSet(Veilborne.DROPLET.get(),TideParticle.DropletProvider::new);
        e.registerSpriteSet(Veilborne.PETAL.get(),PetalParticle.Provider::new);
        e.registerSpriteSet(Veilborne.MOON_MOTE.get(),TideParticle.MoonProvider::new);
        e.registerSpriteSet(Veilborne.MIRROR_MOTE.get(),MirrorParticle.MoteProvider::new);
        e.registerSpriteSet(Veilborne.FLAKE.get(),MirrorParticle.FlakeProvider::new);
        e.registerSpriteSet(Veilborne.THREAD_FIBER.get(),ThreadParticle.FiberProvider::new);
        e.registerSpriteSet(Veilborne.THREAD_EMBER.get(),ThreadParticle.EmberProvider::new);
        e.registerSpriteSet(Veilborne.HUSH_CRUMB.get(),HushParticle.Provider::new);
        e.registerSpriteSet(Veilborne.DREAM_POLLEN.get(),DreamParticle.Pollen::new);
        e.registerSpriteSet(Veilborne.DREAM_FIREFLY.get(),DreamParticle.Firefly::new);
        e.registerSpriteSet(Veilborne.DREAM_FLUFF.get(),DreamParticle.Fluff::new);
        e.registerSpriteSet(Veilborne.DREAM_LEAF.get(),DreamParticle.Leaf::new);
    }
    @SubscribeEvent public static void shaders(RegisterShadersEvent e) throws java.io.IOException {
        e.registerShader(new net.minecraft.client.renderer.ShaderInstance(e.getResourceProvider(),Veilborne.id("sea"),com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR),shader->SeaSky.shader=shader);
        e.registerShader(new net.minecraft.client.renderer.ShaderInstance(e.getResourceProvider(),Veilborne.id("whale"),com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL),shader->WhaleRenderer.shader=shader);
        e.registerShader(new net.minecraft.client.renderer.ShaderInstance(e.getResourceProvider(),Veilborne.id("moon"),com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR),shader->MoonRenderer.moonShader=shader);
        e.registerShader(new net.minecraft.client.renderer.ShaderInstance(e.getResourceProvider(),Veilborne.id("screen"),com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION),shader->MoonRenderer.screenShader=shader);
        e.registerShader(new net.minecraft.client.renderer.ShaderInstance(e.getResourceProvider(),Veilborne.id("mirror_world"),com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL),shader->MirrorWorld.shader=shader);
        e.registerShader(new net.minecraft.client.renderer.ShaderInstance(e.getResourceProvider(),Veilborne.id("mirror_clip"),com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL),shader->MirrorWorld.clipShader=shader);
        e.registerShader(new net.minecraft.client.renderer.ShaderInstance(e.getResourceProvider(),Veilborne.id("mirror_sky"),com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION),shader->MirrorRenderer.skyShader=shader);
        e.registerShader(new net.minecraft.client.renderer.ShaderInstance(e.getResourceProvider(),Veilborne.id("mirror_pane"),com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR),shader->MirrorRenderer.paneShader=shader);
        e.registerShader(new net.minecraft.client.renderer.ShaderInstance(e.getResourceProvider(),Veilborne.id("mirror_ghost"),com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR),shader->MirrorRenderer.ghostShader=shader);
        e.registerShader(new net.minecraft.client.renderer.ShaderInstance(e.getResourceProvider(),Veilborne.id("thread_sky"),com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION),shader->ThreadRenderer.skyShader=shader);
        e.registerShader(new net.minecraft.client.renderer.ShaderInstance(e.getResourceProvider(),Veilborne.id("thread_needle"),com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL),shader->ThreadNeedle.shader=shader);
        e.registerShader(new net.minecraft.client.renderer.ShaderInstance(e.getResourceProvider(),Veilborne.id("thread_line"),com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR),shader->ThreadLines.shader=shader);
        e.registerShader(new net.minecraft.client.renderer.ShaderInstance(e.getResourceProvider(),Veilborne.id("hush_sky"),com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION),shader->HushRenderer.skyShader=shader);
        e.registerShader(new net.minecraft.client.renderer.ShaderInstance(e.getResourceProvider(),Veilborne.id("hush_post"),com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION),shader->HushRenderer.postShader=shader);
        e.registerShader(new net.minecraft.client.renderer.ShaderInstance(e.getResourceProvider(),Veilborne.id("hush_aurora"),com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION),shader->HushRenderer.auroraShader=shader);
        e.registerShader(new net.minecraft.client.renderer.ShaderInstance(e.getResourceProvider(),Veilborne.id("dream_sky"),com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION),shader->DreamRenderer.skyShader=shader);
        e.registerShader(new net.minecraft.client.renderer.ShaderInstance(e.getResourceProvider(),Veilborne.id("dream_post"),com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION),shader->DreamRenderer.postShader=shader);
        e.registerShader(new net.minecraft.client.renderer.ShaderInstance(e.getResourceProvider(),Veilborne.id("dream_grove"),com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL),shader->DreamGrove.shader=shader);
        e.registerShader(new net.minecraft.client.renderer.ShaderInstance(e.getResourceProvider(),Veilborne.id("dream_deer"),com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL),shader->DeerModel.shader=shader);
    }
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e){
        e.registerLayerDefinition(SkiffModel.LAYER,SkiffModel::create);
        e.registerLayerDefinition(ProcessionRenderer.LAYER,ProcessionRenderer::create);
        e.registerLayerDefinition(FramesRenderer.LAYER,FramesRenderer::create);
        e.registerLayerDefinition(BeastRenderer.LAYER,BeastRenderer::create);
        e.registerLayerDefinition(WalkerRenderer.LAYER,WalkerRenderer::create);
        e.registerLayerDefinition(HushProcessionRenderer.LAYER,HushProcessionRenderer::create);
    }
    @SubscribeEvent public static void entities(EntityRenderersEvent.RegisterRenderers e){
        e.registerEntityRenderer(Veilborne.SKIFF.get(),SkiffRenderer::new);
        e.registerEntityRenderer(Veilborne.PROCESSION.get(),ProcessionRenderer::new);
        e.registerEntityRenderer(Veilborne.DECOY.get(),DecoyRenderer::new);
        e.registerEntityRenderer(Veilborne.TRAVELER.get(),TravelerRenderer::new);
        e.registerEntityRenderer(Veilborne.FRAMES.get(),FramesRenderer::new);
        e.registerEntityRenderer(Veilborne.BEAST.get(),BeastRenderer::new);
        e.registerEntityRenderer(Veilborne.WALKER.get(),WalkerRenderer::new);
        e.registerEntityRenderer(Veilborne.SPIRIT.get(),net.minecraft.client.renderer.entity.NoopRenderer::new);
        e.registerEntityRenderer(Veilborne.HUSH_PROCESSION.get(),HushProcessionRenderer::new);
        e.registerEntityRenderer(Veilborne.HUSH_COAT.get(),HushCoatRenderer::new);
        e.registerEntityRenderer(Veilborne.DREAM_SEED_ENTITY.get(),net.minecraft.client.renderer.entity.NoopRenderer::new);
        e.registerEntityRenderer(Veilborne.LEAF_SCHOOL.get(),net.minecraft.client.renderer.entity.NoopRenderer::new);
    }
    @SubscribeEvent public static void dimensions(RegisterDimensionSpecialEffectsEvent e){e.register(Veilborne.id("mirror_gallery"),new GallerySky());}
}
