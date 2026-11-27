package com.suiyi.veilborne.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.suiyi.veilborne.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.lwjgl.glfw.GLFW;

/**
 * 天幕观测手记: one notebook, one chapter per veil weather, chosen by the ribbons at the top. Each chapter has
 * five spreads: cover and live phase timeline, visitors and keepsakes, recipes read from the live recipe
 * manager, the observation log (unseen entries stay as hints), and what the weather leaves in the world with the
 * places it left for you and how fully you met it (completeness: the event under way, the last one and the best). The 月骸花潮, 镜天巡礼, 绯线缝世, 无声白昼 and 苍翠梦潮
 * chapters appear
 * once that weather has been witnessed or is under way. Pages are measured before they are drawn and only scale down if
 * a long translation would overflow.
 */
public final class TideJournalScreen extends Screen {
    private static final ResourceLocation SEA_TEX=Veilborne.id("textures/gui/journal.png"),MOON_TEX=Veilborne.id("textures/gui/journal_moon.png"),MIRROR_TEX=Veilborne.id("textures/gui/journal_mirror.png"),
        THREAD_TEX=Veilborne.id("textures/gui/journal_thread.png"),HUSH_TEX=Veilborne.id("textures/gui/journal_hush.png"),
        DREAM_TEX=Veilborne.id("textures/gui/journal_dream.png");
    private static final int BW=360,BH=224,SPREADS=5,LEFT=22,RIGHT=192,COL=146,TOP=16,BOTTOM=BH-28;
    private record Palette(int title,int gold,int body,int dim,int now,int faint,int rule,int bar,int tick,int fillBar,int unseen){}
    private static final Palette SEA=new Palette(0xfff3dda6,0xffe2c07a,0xffbfd0df,0xff7f95ab,0xff8fe3e6,0xff4f6684,0xff8a6a2e,0xff1a2a45,0xff3b5578,0xff4fc6d0,0xff5d7390);
    private static final Palette MOON=new Palette(0xfff1ebff,0xffe8c687,0xffd3cce6,0xff968db5,0xffd7c9ff,0xff5f5780,0xff9c8a5a,0xff241c3d,0xff4a3f6e,0xffb9a8f0,0xff6d6590);
    private static final Palette THREAD=new Palette(0xfff8e4d8,0xffe8c07a,0xffe0c6be,0xffa88a84,0xffff9a86,0xff6e4e4c,0xffa0783c,0xff2a1216,0xff5a2a2c,0xffe0504a,0xff7a5e5a);
    /** The silent daylight's pages are pale paper in dark ink, the inverse of the others; amber marks what is now. */
    private static final Palette HUSH=new Palette(0xff2c2a28,0xff9a5a1a,0xff45423e,0xff6e6a64,0xffb8641a,0xff948e86,0xffb4a68a,0xffd6d0c4,0xffa29a8e,0xffc0802e,0xffaaa49a);
    /** The dream's pages are deep moss, written in pale leaf-green with honey for what matters. */
    private static final Palette DREAM=new Palette(0xfff0f6da,0xffe8c86a,0xffcfe0bc,0xff90ae88,0xffffe08a,0xff5f8260,0xffa08c48,0xff152a1b,0xff3c6244,0xff8ed06e,0xff6c8c6a);
    private static final Palette MIRROR=new Palette(0xfff0f6fa,0xffe6c47e,0xffc9d6de,0xff8fa0ac,0xff9fe6ee,0xff5c6c78,0xff9a8656,0xff1c2630,0xff3e5060,0xff8fd6de,0xff687884);
    private static final double[] SEA_PHASES={0,1/14.0,3/14.0,8/14.0,10/14.0,12/14.0,1};
    private static final String[] SEA_KEYS={"omen","opening","flood","whale","afterglow","ebb"};
    private static final String[] MOON_KEYS={"halo","crack","bloom","molt","farewell","close"};
    private static final String[] MIRROR_KEYS={"omen","silver","reflect","pilgrim","crack","clear"};
    private static final String[] THREAD_KEYS={"omen","tear","needle","sew","knot","fade"};
    private static final String[] HUSH_KEYS={"omen","fade","sun","eclipse","thunder","return"};
    private static final String[] DREAM_KEYS={"omen","roots","leaves","deer","seedfall","return"};
    private interface Page { int draw(int x,int y); }
    private static int lastSpread,lastChapter;
    private int spread=lastSpread,chapter=lastChapter,fade;
    private float scale=1;private int ox,oy;
    private ItemStack hovered=ItemStack.EMPTY;
    // Per-frame drawing state.
    private GuiGraphics g;private double mx,my;private int alpha=255;private float time;private boolean measuring;
    public TideJournalScreen(){
        super(Component.translatable("journal.veilborne.title"));
        if(MoonClient.active())chapter=1;
        else if(MirrorClient.active())chapter=2;
        else if(ThreadClient.active())chapter=3;
        else if(HushClient.active())chapter=4;
        else if(DreamClient.active())chapter=5;
        else if(TideClient.state!=null&&TideClient.state.active()&&TideClient.strength()>.05)chapter=0;
        if(!known(chapter))chapter=0;
        if(chapter!=lastChapter)spread=0;
        lastChapter=chapter;lastSpread=spread;
    }
    @Override public boolean isPauseScreen(){return false;}
    private static boolean moonKnown(){return (TideClient.journalRecords&MoonService.FIRST)!=0||MoonClient.active();}
    private static boolean mirrorKnown(){return (TideClient.journalRecords&MirrorService.FIRST)!=0||MirrorClient.active();}
    private static boolean threadKnown(){return (TideClient.journalRecords&ThreadService.FIRST)!=0||ThreadClient.active();}
    /** Both chapters keep their observations in the second record word; each weather's packet carries it, so take both. */
    private static int records2(){return HushClient.records|DreamClient.records;}
    private static boolean hushKnown(){return (records2()&HushService.FIRST)!=0||HushClient.active();}
    private static boolean dreamKnown(){return (records2()&DreamService.FIRST)!=0||DreamClient.active();}
    private static boolean known(int c){return c==0||c==1&&moonKnown()||c==2&&mirrorKnown()||c==3&&threadKnown()||c==4&&hushKnown()||c==5&&dreamKnown();}
    private boolean moon(){return chapter==1;}
    private boolean mirror(){return chapter==2;}
    private boolean thread(){return chapter==3;}
    private boolean hush(){return chapter==4;}
    private boolean dream(){return chapter==5;}
    private Palette pal(){return moon()?MOON:mirror()?MIRROR:thread()?THREAD:hush()?HUSH:dream()?DREAM:SEA;}
    private ResourceLocation tex(){return moon()?MOON_TEX:mirror()?MIRROR_TEX:thread()?THREAD_TEX:hush()?HUSH_TEX:dream()?DREAM_TEX:SEA_TEX;}
    /** Translation key inside the current chapter. */
    private String k(String key){return (moon()?"journal.veilborne.moon.":mirror()?"journal.veilborne.mirror.":thread()?"journal.veilborne.thread.":hush()?"journal.veilborne.hush.":dream()?"journal.veilborne.dream.":"journal.veilborne.")+key;}

    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){
        super.render(graphics,mouseX,mouseY,partial);
        g=graphics;
        scale=Math.min(1f,Math.min((width-8f)/BW,(height-8f)/BH));
        ox=Math.round((width-BW*scale)/2);oy=Math.round((height-BH*scale)/2);
        double lx=(mouseX-ox)/scale,ly=(mouseY-oy)/scale;
        hovered=ItemStack.EMPTY;
        var pose=g.pose();pose.pushPose();pose.translate(ox,oy,0);pose.scale(scale,scale,1);
        g.blit(tex(),0,0,0,0,BW,BH,512,512);
        ribbons(lx,ly);
        time=(Minecraft.getInstance().level!=null?Minecraft.getInstance().level.getGameTime():0)+partial;
        alpha=(int)(255*Math.min(1,.25+.75*(1-fade/6f)));
        switch(spread){
            case 0 -> {fit(LEFT,lx,ly,this::cover);fit(RIGHT,lx,ly,this::phases);}
            case 1 -> {fit(LEFT,lx,ly,this::visitors);fit(RIGHT,lx,ly,this::keepsakes);}
            case 2 -> {fit(LEFT,lx,ly,this::crafting);fit(RIGHT,lx,ly,this::attuner);}
            case 3 -> {fit(LEFT,lx,ly,this::records);fit(RIGHT,lx,ly,this::notes);}
            default -> {fit(LEFT,lx,ly,this::legacy);fit(RIGHT,lx,ly,this::grade);}
        }
        alpha=255;
        center(Component.literal(String.valueOf(spread*2+1)),95,BH-18,pal().faint());
        center(Component.literal(String.valueOf(spread*2+2)),265,BH-18,pal().faint());
        if(spread>0)g.blit(tex(),20,BH-22,over(lx,ly,20,BH-22,18,13)?54:36,256,18,13,512,512);
        if(spread<SPREADS-1)g.blit(tex(),BW-38,BH-22,over(lx,ly,BW-38,BH-22,18,13)?18:0,256,18,13,512,512);
        pose.popPose();
        if(!hovered.isEmpty())g.renderTooltip(font,hovered,mouseX,mouseY);
    }
    /** Chapter ribbons hanging over the top edge: teal for the sea, lilac for the moon, celadon for the mirror, scarlet for the
     *  thread, bone white for the silent daylight, leaf green for the dream. The open one hangs lower. */
    private static final int[] RIBBON_X={292,304,316,328,340,352};
    private void ribbons(double lx,double ly){
        for(int c=0;c<RIBBON_X.length;c++)if(known(c))g.blit(tex(),RIBBON_X[c],0,200+c*16+(chapter==c?8:0),256,8,chapter==c?30:22,512,512);
    }
    @Override public void tick(){if(fade>0)fade--;}
    /** Measure the page, then draw it, shrinking about its top-left corner only when it would overflow. */
    private void fit(int x,double lx,double ly,Page page){
        measuring=true;int bottom=page.draw(x,TOP);measuring=false;
        float k=bottom>BOTTOM?(float)(BOTTOM-TOP)/(bottom-TOP):1;
        var pose=g.pose();pose.pushPose();
        pose.translate(x,TOP,0);pose.scale(k,k,1);pose.translate(-x,-TOP,0);
        mx=x+(lx-x)/k;my=TOP+(ly-TOP)/k;
        page.draw(x,TOP);
        pose.popPose();
    }

    // ------------------------------------------------------------------ live state of this chapter's weather
    private boolean active(){
        if(mirror())return MirrorClient.active()&&MirrorClient.strength()>.05;
        if(thread())return ThreadClient.active()&&ThreadClient.strength()>.05;
        if(hush())return HushClient.active()&&HushClient.strength()>.05;
        if(dream())return DreamClient.active()&&DreamClient.strength()>.05;
        return moon()?MoonClient.active()&&MoonClient.strength()>.05:TideClient.state!=null&&TideClient.state.active()&&TideClient.strength()>.05;
    }
    private double liveProgress(){return moon()?MoonClient.progress(1):mirror()?MirrorClient.progress(1):thread()?ThreadClient.progress(1):hush()?HushClient.progress(1):dream()?DreamClient.progress(1):TideClient.progress(1);}
    private int phaseIndex(double p){return moon()?Math.min(5,MoonTimeline.phase(p).ordinal()):mirror()?Math.min(5,MirrorTimeline.phase(p).ordinal()):thread()?Math.min(5,ThreadTimeline.phase(p).ordinal()):hush()?Math.min(5,HushTimeline.phase(p).ordinal()):dream()?Math.min(5,DreamTimeline.phase(p).ordinal()):Math.min(5,TideTimeline.phase(p).ordinal());}
    private double[] bounds(){return moon()?MoonTimeline.BOUNDS:mirror()?MirrorTimeline.BOUNDS:thread()?ThreadTimeline.BOUNDS:hush()?HushTimeline.BOUNDS:dream()?DreamTimeline.BOUNDS:SEA_PHASES;}
    private String[] phaseKeys(){return moon()?MOON_KEYS:mirror()?MIRROR_KEYS:thread()?THREAD_KEYS:hush()?HUSH_KEYS:dream()?DREAM_KEYS:SEA_KEYS;}
    private String phaseStatus(int k){return moon()?"phase.veilborne.moon."+MOON_KEYS[k]:mirror()?"phase.veilborne.mirror."+MIRROR_KEYS[k]:thread()?"phase.veilborne.thread."+THREAD_KEYS[k]:hush()?"phase.veilborne.hush."+HUSH_KEYS[k]:dream()?"phase.veilborne.dream."+DREAM_KEYS[k]:"phase.veilborne."+SEA_KEYS[k];}

    // ------------------------------------------------------------------ pages
    private int cover(int x,int y){
        Palette c=pal();
        Component title=Component.translatable(k("title"));
        float ts=font.width(title)*2<=COL?2:1.5f;
        if(!measuring){
            var pose=g.pose();pose.pushPose();pose.translate(x+COL/2f,y+4,0);pose.scale(ts,ts,1);
            g.drawString(font,title,-font.width(title)/2,0,a(c.title()),false);pose.popPose();
        }
        y+=(int)(9*ts)+8;
        center(Component.translatable(k("subtitle")),x+COL/2,y,a(c.gold()));
        fill(x+24,y+12,x+COL-24,y+13,c.rule());
        y+=17;
        if(!measuring){
            var pose=g.pose();pose.pushPose();pose.translate(x+1,y+(float)Math.sin(time*.05)*1.5f,0);pose.scale(.9f,.9f,1);
            RenderSystem.enableBlend();g.blit(tex(),0,0,0,296,160,64,512,512);pose.popPose();
        }
        y=paragraph(Component.translatable(k("epigraph")),x,y+60,COL,c.dim(),true)+6;
        boolean active=active();
        double p=active?liveProgress():0;
        int current=active?phaseIndex(p):-1;
        text(Component.translatable("journal.veilborne.status"),x,y,c.faint());
        text(active?Component.translatable(phaseStatus(current)):Component.translatable("phase.veilborne.quiet"),x,y+11,active?c.now():c.gold());
        int iy=y+24;
        for(int k=0;k<6;k++){
            float shade=k==current?1:(active&&k<current?.62f:.38f);
            if(!measuring){RenderSystem.setShaderColor(shade,shade,shade,1);g.blit(tex(),x+k*25+3,iy,k*16,272,16,16,512,512);}
        }
        if(!measuring)RenderSystem.setShaderColor(1,1,1,1);
        int bar=iy+20;
        fill(x,bar,x+COL,bar+2,c.bar());
        for(double b:bounds())fill(x+(int)(b*COL),bar-1,x+(int)(b*COL)+1,bar+3,c.tick());
        if(active){
            int filled=(int)(p*COL);
            fill(x,bar,x+filled,bar+2,c.fillBar());
            fill(x+filled-1,bar-2,x+filled+1,bar+4,0xfff1d58a);
            return bar+6;
        }
        return paragraph(Component.translatable(k("quiet")),x,bar+6,COL,c.dim(),false);
    }
    private int phases(int x,int y){
        Palette c=pal();
        y=heading(Component.translatable(k("head.phases")),x,y+2);
        int current=active()?phaseIndex(liveProgress()):-1;
        int duration=moon()?(MoonClient.state!=null?MoonClient.state.duration():MoonTimeline.DEFAULT_DURATION)
            :mirror()?(MirrorClient.state!=null?MirrorClient.state.duration():MirrorTimeline.DEFAULT_DURATION)
            :thread()?(ThreadClient.state!=null?ThreadClient.state.duration():ThreadTimeline.DEFAULT_DURATION)
            :hush()?(HushClient.state!=null?HushClient.state.duration():HushTimeline.DEFAULT_DURATION)
            :dream()?(DreamClient.state!=null?DreamClient.state.duration():DreamTimeline.DEFAULT_DURATION)
            :(TideClient.state!=null?TideClient.state.duration():TideTimeline.DEFAULT_DURATION);
        double[] b=bounds();String[] keys=phaseKeys();
        for(int i=0;i<6;i++){
            blit(x,y,i*16,272,16,16);
            text(Component.translatable(k("phase."+keys[i])),x+20,y,i==current?c.now():c.gold());
            String stamp=clock(b[i]*duration);
            text(Component.literal(stamp),x+COL-font.width(stamp),y,c.faint());
            y=paragraph(Component.translatable(k("phase."+keys[i]+".text")),x+20,y+10,COL-20,c.body(),false)+4;
        }
        return y;
    }
    private int visitors(int x,int y){
        y=heading(Component.translatable(k("head.visitors")),x,y+2);
        if(dream()){
            y=entry(ItemStack.EMPTY,96,"deer",x,y);
            y=entry(ItemStack.EMPTY,112,"school",x,y);
            return entry(ItemStack.EMPTY,168,"wander",x,y);
        }
        if(hush()){
            y=entry(ItemStack.EMPTY,96,"procession",x,y);
            y=entry(ItemStack.EMPTY,112,"knock",x,y);
            return entry(ItemStack.EMPTY,168,"bell",x,y);
        }
        if(thread()){
            y=entry(ItemStack.EMPTY,96,"strike",x,y);
            y=entry(ItemStack.EMPTY,112,"walker",x,y);
            return entry(ItemStack.EMPTY,168,"pocket",x,y);
        }
        if(mirror()){
            y=entry(ItemStack.EMPTY,96,"door",x,y);
            y=entry(ItemStack.EMPTY,112,"traveler",x,y);
            return entry(ItemStack.EMPTY,168,"frames",x,y);
        }
        if(moon()){
            y=entry(ItemStack.EMPTY,96,"bearers",x,y);
            y=entry(ItemStack.EMPTY,112,"little_moon",x,y);
            return entry(ItemStack.EMPTY,168,"three_moons",x,y);
        }
        y=entry(new ItemStack(Veilborne.BOTTLE.get()),-1,"bottle",x,y);
        y=entry(ItemStack.EMPTY,96,"skiff",x,y);
        return entry(ItemStack.EMPTY,112,"vortex",x,y);
    }
    private int keepsakes(int x,int y){
        y=heading(Component.translatable(k("head.keepsakes")),x,y+2);
        if(dream()){
            y=entry(new ItemStack(Veilborne.DREAM_SEED.get()),-1,"seed",x,y);
            y=entry(new ItemStack(Veilborne.GOLDEN_LEAF.get()),-1,"leaf",x,y);
            return entry(new ItemStack(Veilborne.ANTLER_TWIG_ITEM.get()),-1,"twig",x,y);
        }
        if(hush()){
            y=entry(new ItemStack(Veilborne.DAYLIGHT_WAX.get()),-1,"wax",x,y);
            y=entry(new ItemStack(Veilborne.HOLLOW_BELL.get()),-1,"hollow",x,y);
            return entry(new ItemStack(Veilborne.STILL_FEATHER_ITEM.get()),-1,"feather",x,y);
        }
        if(thread()){
            y=entry(new ItemStack(Veilborne.SCARLET_THREAD.get()),-1,"thread",x,y);
            y=entry(new ItemStack(Veilborne.SEAM_SILK.get()),-1,"silk",x,y);
            return entry(new ItemStack(Veilborne.ROBE_SCRAP_ITEM.get()),-1,"scrap",x,y);
        }
        if(mirror()){
            y=entry(ItemStack.EMPTY,184,"beasts",x,y);
            y=entry(new ItemStack(Veilborne.MIRROR_SAND.get()),-1,"sand",x,y);
            return entry(new ItemStack(Veilborne.SKY_SHARD_ITEM.get()),-1,"shard",x,y);
        }
        if(moon()){
            y=entry(new ItemStack(Veilborne.MOON_DEW.get()),-1,"dew",x,y);
            y=entry(new ItemStack(Veilborne.MOON_SHARD.get()),-1,"shard",x,y);
            return entry(new ItemStack(Veilborne.SILVER_LANTERN_ITEM.get()),-1,"lantern",x,y);
        }
        y=entry(new ItemStack(Veilborne.WHALE_SCALE.get()),-1,"scale",x,y);
        y=entry(new ItemStack(Veilborne.CONCH.get()),-1,"conch",x,y);
        return entry(new ItemStack(Veilborne.MANTLE.get()),-1,"mantle",x,y);
    }
    private int crafting(int x,int y){
        y=heading(Component.translatable(k("head.crafting")),x,y+2);
        if(dream()){
            y=recipe("flower_lamp",new ItemStack(Veilborne.FLOWER_LAMP_ITEM.get()),x,y,true);
            return recipe("drifting_leaf",new ItemStack(Veilborne.DRIFTING_LEAF.get()),x,y+6,true);
        }
        if(hush()){
            y=recipe("silent_lantern",new ItemStack(Veilborne.SILENT_LANTERN_ITEM.get()),x,y,true);
            return recipe("still_candle",new ItemStack(Veilborne.STILL_CANDLE_ITEM.get()),x,y+6,true);
        }
        if(thread()){
            y=recipe("tuning_anchor",new ItemStack(Veilborne.TUNING_ANCHOR_ITEM.get()),x,y,true);
            return recipe("binding_charm",new ItemStack(Veilborne.BINDING_CHARM.get()),x,y+6,true);
        }
        if(mirror()){
            y=recipe("observation_prism",new ItemStack(Veilborne.PRISM.get()),x,y,true);
            return recipe("echo_mirror",new ItemStack(Veilborne.ECHO_MIRROR.get()),x,y+6,true);
        }
        if(moon()){
            y=recipe("moon_dial",new ItemStack(Veilborne.MOON_DIAL.get()),x,y,true);
            return recipe("moon_gauze",new ItemStack(Veilborne.MOON_GAUZE.get()),x,y+6,true);
        }
        y=recipe("field_journal",new ItemStack(Veilborne.JOURNAL.get()),x,y,true);
        return recipe("whale_mantle",new ItemStack(Veilborne.MANTLE.get()),x,y+6,true);
    }
    private int attuner(int x,int y){
        if(dream()){
            y=heading(Component.translatable("item.veilborne.deer_flute"),x,y+2);
            y=recipe("deer_flute",new ItemStack(Veilborne.DEER_FLUTE.get()),x,y,false);
            y=paragraph(Component.translatable(k("flute")),x,y+2,COL,pal().body(),false);
            return entry(new ItemStack(Veilborne.FULL_DREAM_SEED.get()),-1,"full",x,y+6);
        }
        if(hush()){
            y=heading(Component.translatable("item.veilborne.stopped_clock"),x,y+2);
            y=recipe("stopped_clock",new ItemStack(Veilborne.STOPPED_CLOCK.get()),x,y,false);
            y=paragraph(Component.translatable(k("clock")),x,y+2,COL,pal().body(),false);
            return entry(new ItemStack(net.minecraft.world.item.Items.LANTERN),-1,"raise",x,y+6);
        }
        if(thread()){
            y=heading(Component.translatable("item.veilborne.heaven_needle"),x,y+2);
            y=recipe("heaven_needle",new ItemStack(Veilborne.HEAVEN_NEEDLE.get()),x,y,false);
            y=paragraph(Component.translatable(k("needle")),x,y+2,COL,pal().body(),false);
            return entry(new ItemStack(Veilborne.LOOM_LANTERN_ITEM.get()),-1,"lantern",x,y+6);
        }
        if(mirror()){
            y=heading(Component.translatable("item.veilborne.sky_mirror"),x,y+2);
            y=recipe("sky_mirror",new ItemStack(Veilborne.SKY_MIRROR.get()),x,y,false);
            y=paragraph(Component.translatable(k("sky_mirror")),x,y+2,COL,pal().body(),false);
            return entry(new ItemStack(Veilborne.MIRROR_LANTERN_ITEM.get()),-1,"lantern",x,y+6);
        }
        if(moon()){
            y=heading(Component.translatable("item.veilborne.moonward_bell"),x,y+2);
            y=recipe("moonward_bell",new ItemStack(Veilborne.MOON_BELL_ITEM.get()),x,y,false);
            y=paragraph(Component.translatable(k("bell")),x,y+2,COL,pal().body(),false);
            return entry(new ItemStack(Veilborne.SILVER_LANTERN_ITEM.get()),-1,"lantern_craft",x,y+6);
        }
        y=heading(Component.translatable("item.veilborne.tide_attuner"),x,y+2);
        y=recipe("tide_attuner",new ItemStack(Veilborne.ATTUNER.get()),x,y,false);
        y=paragraph(Component.translatable(k("attuner")),x,y+2,COL,pal().body(),false);
        return entry(new ItemStack(Veilborne.TIDE_BOTTLE.get()),-1,"lantern",x,y+6);
    }
    private int records(int x,int y){
        Palette c=pal();
        int records=hush()||dream()?records2():TideClient.journalRecords;
        String word=hush()||dream()?"record2.":"record.",hint=hush()||dream()?"hint2.":"hint.";
        int[] bits=moon()?new int[]{MoonService.FIRST,MoonService.MOLT,MoonService.ESCORT,MoonService.CONSTELLATION,MoonService.TRUE_MOON}
            :mirror()?new int[]{MirrorService.FIRST,MirrorService.PILGRIM,MirrorService.DOOR,MirrorService.CORRIDOR,MirrorService.FRAMES,MirrorService.TRAVELER}
            :thread()?new int[]{ThreadService.FIRST,ThreadService.SEWN,ThreadService.FORESIGHT,ThreadService.THREAD_END,ThreadService.KNOT,ThreadService.POCKET}
            :hush()?new int[]{HushService.FIRST,HushService.ECLIPSE,HushService.KNOCK,HushService.BELL,HushService.PROCESSION,HushService.THUNDER}
            :dream()?new int[]{DreamService.FIRST,DreamService.DEER,DreamService.PATH,DreamService.GLANCE,DreamService.SEED,DreamService.LOST,DreamService.ENCLOSURE}:new int[]{1,2,4,8,16};
        int found=0;for(int b:bits)if((records&b)!=0)found++;
        String count=Component.translatable("journal.veilborne.count",found,bits.length).getString();
        text(Component.literal(count),x+COL-font.width(count),y+2,found==bits.length?c.now():c.faint());
        y=heading(Component.translatable(k("head.records")),x,y+2);
        for(int bit:bits){
            boolean seen=(records&bit)!=0;
            blit(x,y,seen?72:84,256,11,11);
            Component name=seen?Component.translatable("message.veilborne."+word+bit):Component.translatable("journal.veilborne.unknown");
            y=paragraph(name,x+15,y+1,COL-15,seen?c.gold():c.faint(),false);
            y=paragraph(Component.translatable("journal.veilborne."+hint+bit),x+15,y+1,COL-15,seen?c.dim():c.unseen(),false)+6;
        }
        // The pilgrim's recognition is never listed until it has happened.
        if(mirror()&&(records&MirrorService.KNOWN)!=0){
            blit(x,y,72,256,11,11);
            y=paragraph(Component.translatable("message.veilborne.record."+MirrorService.KNOWN),x+15,y+1,COL-15,c.now(),false)+4;
        }
        // The thread that holds you is never listed until it has been seen.
        if(thread()&&(records&ThreadService.STRING)!=0){
            blit(x,y,72,256,11,11);
            y=paragraph(Component.translatable("message.veilborne.record."+ThreadService.STRING),x+15,y+1,COL-15,c.now(),false)+4;
        }
        // The black sun hearing you is never listed until it has happened.
        if(hush()&&(records&HushService.HEARD)!=0){
            blit(x,y,72,256,11,11);
            y=paragraph(Component.translatable("message.veilborne.record2."+HushService.HEARD),x+15,y+1,COL-15,c.now(),false)+4;
        }
        // The seed in the deer's hoofprint is never listed until it has happened.
        if(dream()&&(records&DreamService.FOOTPRINT)!=0){
            blit(x,y,72,256,11,11);
            y=paragraph(Component.translatable("message.veilborne.record2."+DreamService.FOOTPRINT),x+15,y+1,COL-15,c.now(),false)+4;
        }
        // The empty place is never listed until it has happened.
        if(moon()&&(records&MoonService.EMPTY_PLACE)!=0){
            blit(x,y,72,256,11,11);
            y=paragraph(Component.translatable("message.veilborne.record."+MoonService.EMPTY_PLACE),x+15,y+1,COL-15,c.now(),false)+4;
        }
        return y;
    }
    private int notes(int x,int y){
        Palette c=pal();
        y=heading(Component.translatable(k("head.notes")),x,y+2);
        for(int i=0;i<3;i++){
            text(Component.literal("·"),x,y,c.gold());
            y=paragraph(Component.translatable(k("note."+i)),x+8,y,COL-8,c.body(),false)+6;
        }
        fill(x+30,y+4,x+COL-30,y+5,c.rule());
        return paragraph(Component.translatable(k("closing")),x,y+12,COL,c.dim(),true);
    }

    // ------------------------------------------------------------------ what stays, and how fully it was met
    private ItemStack[] legacyIcons(){
        return switch(chapter){
            case 1 -> new ItemStack[]{new ItemStack(Veilborne.SILVER_LANTERN_ITEM.get()),net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.POTION,Veilborne.MOONVEIL_POTION)};
            case 2 -> new ItemStack[]{new ItemStack(net.minecraft.world.item.Items.CHEST),new ItemStack(Veilborne.MIRROR_HEART.get())};
            case 3 -> new ItemStack[]{new ItemStack(Veilborne.LOOM_LANTERN_ITEM.get()),new ItemStack(Veilborne.NEEDLE_CORE.get())};
            case 4 -> new ItemStack[]{new ItemStack(Veilborne.SILENT_LANTERN_ITEM.get()),new ItemStack(Veilborne.STILL_CANDLE_ITEM.get())};
            case 5 -> new ItemStack[]{new ItemStack(Veilborne.DREAM_LOG_ITEM.get()),new ItemStack(Veilborne.DREAM_LEAVES_ITEM.get())};
            default -> new ItemStack[]{new ItemStack(Veilborne.HOMEWARD_SHELL.get()),new ItemStack(Veilborne.WHALE_SCALE.get())};
        };
    }
    private int legacy(int x,int y){
        Palette c=pal();
        y=heading(Component.translatable("journal.veilborne.legacy.head"),x,y+2);
        ItemStack[] icons=legacyIcons();
        for(int i=0;i<icons.length;i++)y=entry(icons[i],-1,"legacy."+i,x,y);
        List<Component> places=places();
        if(places.isEmpty())return y;
        text(Component.translatable("journal.veilborne.sites.head"),x,y,c.title());
        y+=11;
        for(Component line:places){text(Component.literal("·"),x,y,c.gold());y=paragraph(line,x+8,y,COL-8,c.dim(),false)+2;}
        return y;
    }
    /** The places this weather left for you, from the journal's copy. */
    private List<Component> places(){
        List<Component> out=new ArrayList<>();
        long now=Minecraft.getInstance().level==null?0:Minecraft.getInstance().level.getGameTime();
        for(VeilGrade.Site s:VeilClient.sites()){
            BlockPos a=s.a(),b=s.b();
            switch(s.kind()){
                case VeilGrade.SITE_POOL -> {if(chapter==0)out.add(Component.translatable("journal.veilborne.site.pool",a.getX(),a.getZ(),Math.max(1,(int)Math.ceil((s.until()-now)/24000.0))));}
                case VeilGrade.SITE_VOYAGE -> {if(chapter==0)out.add(Component.translatable("journal.veilborne.site.voyage",a.getX(),a.getZ()));}
                case VeilGrade.SITE_VAULT -> {if(chapter==2)out.add(Component.translatable("journal.veilborne.site.vault",b.getX(),b.getY(),b.getZ()));}
                case VeilGrade.SITE_PAIR -> {if(chapter==3)out.add(Component.translatable("journal.veilborne.site.pair",a.getX(),a.getZ(),b.getX(),b.getZ()));}
                default -> {}
            }
            if(out.size()>=4)break;
        }
        return out;
    }
    private int grade(int x,int y){
        Palette c=pal();int w=chapter;
        y=heading(Component.translatable("journal.veilborne.grade.head"),x,y+2);
        boolean[] live=new boolean[1];
        int done=VeilClient.deeds(w,live),last=VeilClient.last()[w],best=VeilClient.best()[w];
        int shown=live[0]?VeilGrade.stars(w,done):last;
        String stars=shown>0?VeilGrade.starText(shown):"☆☆☆";
        if(!measuring){
            var pose=g.pose();pose.pushPose();pose.translate(x,y,0);pose.scale(1.6f,1.6f,1);
            g.drawString(font,stars,0,0,a(shown>0?c.gold():c.faint()),false);pose.popPose();
        }
        Component label=Component.translatable(live[0]?"journal.veilborne.grade.live":last>0?"journal.veilborne.grade.last":"journal.veilborne.grade.none");
        text(label,x+46,y+1,live[0]?c.now():c.dim());
        if(best>0)text(Component.translatable("journal.veilborne.grade.best",VeilGrade.starText(best)),x+46,y+11,c.faint());
        y+=24;
        int points=VeilGrade.points(w,done);
        String scale=Component.translatable("journal.veilborne.grade.points",shown>0?points:0,VeilGrade.TWO[w],VeilGrade.THREE[w]).getString();
        text(Component.literal(scale),x,y,c.faint());
        y+=13;
        for(int i=0;i<VeilGrade.deeds(w);i++){
            int bit=1<<i;boolean got=(done&bit)!=0&&(live[0]||last>0);
            if((VeilGrade.SECRET[w]&bit)!=0&&!got)continue;
            blit(x,y,got?72:84,256,11,11);
            // What a deed is worth, when it is more than one, stands at the right edge of its line.
            String worth=VeilGrade.WORTH[w][i]>1?"+"+VeilGrade.WORTH[w][i]:"";
            int color=got?c.gold():(VeilGrade.RISK[w]&bit)!=0?c.now():c.unseen();
            if(!worth.isEmpty())text(Component.literal(worth),x+COL-font.width(worth),y+1,color);
            Component name=Component.translatable("grade.veilborne."+VeilGrade.NAMES[w]+"."+i);
            if((VeilGrade.RISK[w]&bit)!=0)name=Component.translatable("journal.veilborne.grade.risk").append(name);
            y=paragraph(name,x+15,y+1,COL-15-(worth.isEmpty()?0:font.width(worth)+4),color,false)+2;
        }
        y+=4;
        fill(x+30,y,x+COL-30,y+1,c.rule());
        y+=6;
        for(int tier=2;tier<=3;tier++){
            List<Component> names=new ArrayList<>();
            for(ItemStack s:VeilGrade.gifts(w,tier))names.add(Component.translatable("grade.veilborne.gift_item",s.getHoverName(),s.getCount()));
            net.minecraft.network.chat.MutableComponent list=Component.empty();
            for(int k=0;k<names.size();k++){if(k>0)list.append(Component.literal("、"));list.append(names.get(k));}
            y=paragraph(Component.literal(VeilGrade.starText(tier)+" ").append(list),x,y,COL,shown>=tier?c.gold():c.body(),false)+2;
        }
        return y;
    }

    // ------------------------------------------------------------------ pieces
    private int entry(ItemStack stack,int sprite,String key,int x,int y){
        if(!stack.isEmpty()){item(stack,x,y);if(over(mx,my,x,y,16,16))hovered=stack;}
        else blit(x,y,sprite,272,16,16);
        text(Component.translatable(k(key)),x+21,y+4,pal().gold());
        return paragraph(Component.translatable(k(key)+".text"),x+21,y+16,COL-21,pal().body(),false)+8;
    }
    /** A shaped recipe from the live recipe manager; tag ingredients cycle through their items. */
    private int recipe(String id,ItemStack result,int x,int y,boolean titled){
        var level=Minecraft.getInstance().level;
        List<Ingredient> grid=new ArrayList<>();int w=3,h=3;
        if(level!=null){
            var holder=level.getRecipeManager().byKey(Veilborne.id(id));
            if(holder.isPresent()&&holder.get().value() instanceof ShapedRecipe shaped){
                w=shaped.getWidth();h=shaped.getHeight();grid.addAll(shaped.getIngredients());
            }
        }
        if(titled){text(result.getHoverName(),x,y,pal().gold());y+=11;}
        for(int r=0;r<3;r++)for(int c=0;c<3;c++){
            int sx=x+c*18,sy=y+r*18;
            blit(sx,sy,128,272,18,18);
            // A narrow recipe sits in the middle of the grid, as it would be laid out by hand.
            int cc=c-(3-w)/2,rr=r-(3-h)/2,i=rr*w+cc;
            if(cc>=0&&cc<w&&rr>=0&&rr<h&&i<grid.size()){
                ItemStack[] options=grid.get(i).getItems();
                if(options.length>0){
                    ItemStack shown=options[(int)(time/30)%options.length];
                    item(shown,sx+1,sy+1);
                    if(over(mx,my,sx,sy,18,18))hovered=shown;
                }
            }
        }
        if(grid.isEmpty())text(Component.literal("?"),x+24,y+22,pal().faint());
        blit(x+60,y+21,148,272,16,11);
        blit(x+82,y+17,128,272,18,18);
        item(result,x+83,y+18);
        if(over(mx,my,x+82,y+17,18,18))hovered=result;
        return y+58;
    }
    private int heading(Component text,int x,int y){
        text(text,x,y,pal().title());
        fill(x,y+10,x+Math.min(COL,font.width(text)+12),y+11,pal().rule());
        return y+18;
    }
    private int paragraph(Component text,int x,int y,int width,int color,boolean centered){
        String raw=text.getString();
        if(!cjk(raw)){
            for(FormattedCharSequence line:font.split(text,width)){
                if(!measuring)g.drawString(font,line,x+(centered?(width-font.width(line))/2:0),y,a(color),false);
                y+=10;
            }
            return y;
        }
        for(String line:wrapCjk(raw,width)){
            if(!measuring)g.drawString(font,line,x+(centered?(width-font.width(line))/2:0),y,a(color),false);
            y+=10;
        }
        return y;
    }
    private static final String NO_LINE_START="。，、；：！？）」』》〉”’…—·%";
    private static boolean cjk(String s){for(int i=0;i<s.length();i++)if(Character.UnicodeScript.of(s.charAt(i))==Character.UnicodeScript.HAN)return true;return false;}
    /** Chinese line breaking: break between characters, keep Latin words and numbers whole, and hang
     *  closing punctuation on the line before instead of starting a line with it. */
    private List<String> wrapCjk(String s,int width){
        List<String> lines=new ArrayList<>();
        StringBuilder line=new StringBuilder();
        int i=0;
        while(i<s.length()){
            int j=i+1;
            // Latin words and numbers stay whole, and so does a count or a bonus with its sign (×2, +2).
            boolean sign=(s.charAt(i)=='×'||s.charAt(i)=='+')&&i+1<s.length()&&Character.isDigit(s.charAt(i+1));
            if(sign||Character.isLetterOrDigit(s.charAt(i))&&s.charAt(i)<128)while(j<s.length()&&Character.isLetterOrDigit(s.charAt(j))&&s.charAt(j)<128)j++;
            String token=s.substring(i,j);
            boolean hang=NO_LINE_START.indexOf(token.charAt(0))>=0;
            if(line.length()>0&&!hang&&font.width(line+token)>width){lines.add(line.toString());line.setLength(0);if(token.equals(" ")){i=j;continue;}}
            line.append(token);i=j;
        }
        if(line.length()>0)lines.add(line.toString());
        return lines;
    }
    private void text(Component t,int x,int y,int color){if(!measuring)g.drawString(font,t,x,y,a(color),false);}
    private void center(Component t,int cx,int y,int color){text(t,cx-font.width(t)/2,y,color);}
    private void fill(int x0,int y0,int x1,int y1,int color){if(!measuring)g.fill(x0,y0,x1,y1,a(color));}
    private void blit(int x,int y,int u,int v,int w,int h){if(!measuring)g.blit(tex(),x,y,u,v,w,h,512,512);}
    private void item(ItemStack stack,int x,int y){if(!measuring)g.renderItem(stack,x,y);}
    private static boolean over(double lx,double ly,int x,int y,int w,int h){return lx>=x&&lx<x+w&&ly>=y&&ly<y+h;}
    private int a(int color){return (color&0xffffff)|(Math.max(8,Math.min(255,((color>>>24)*alpha)/255))<<24);}
    private static String clock(double ticks){int s=(int)Math.round(ticks/20);return s/60+":"+String.format("%02d",s%60);}

    // ------------------------------------------------------------------ input
    private void turn(int delta){
        int next=Math.max(0,Math.min(SPREADS-1,spread+delta));
        if(next==spread)return;
        spread=lastSpread=next;fade=6;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN,1));
    }
    private void openChapter(int c){
        if(c==chapter||!known(c))return;
        chapter=lastChapter=c;spread=lastSpread=0;fade=6;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN,.8f));
    }
    @Override public boolean mouseClicked(double mouseX,double mouseY,int button){
        double lx=(mouseX-ox)/scale,ly=(mouseY-oy)/scale;
        for(int c=0;c<RIBBON_X.length;c++)if(over(lx,ly,RIBBON_X[c]-1,0,10,31)){openChapter(c);return true;}
        if(spread>0&&over(lx,ly,20,BH-22,18,13)){turn(-1);return true;}
        if(spread<SPREADS-1&&over(lx,ly,BW-38,BH-22,18,13)){turn(1);return true;}
        return super.mouseClicked(mouseX,mouseY,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(vertical!=0)turn(vertical<0?1:-1);
        return true;
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_RIGHT||key==GLFW.GLFW_KEY_PAGE_DOWN||key==GLFW.GLFW_KEY_D){turn(1);return true;}
        if(key==GLFW.GLFW_KEY_LEFT||key==GLFW.GLFW_KEY_PAGE_UP||key==GLFW.GLFW_KEY_A){turn(-1);return true;}
        if(key==GLFW.GLFW_KEY_TAB){
            for(int i=1;i<=RIBBON_X.length;i++){int c=(chapter+i)%RIBBON_X.length;if(known(c)){openChapter(c);break;}}
            return true;
        }
        return super.keyPressed(key,scan,modifiers);
    }
    /** Preview harness hooks. */
    void showSpread(int index){spread=lastSpread=Math.max(0,Math.min(SPREADS-1,index));fade=0;}
    void showChapter(int c){chapter=lastChapter=c;}
}
