package com.suiyi.veilborne;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 无声白昼 on the server: timeline, the three rotating visitors (the silent procession, the empty coat that knocks, the one
 * bell that still rings and the island of sound it opens once found), the feather held in mid-fall, the late thunder, and
 * the one thing a player can do in a world without sound: raise a light. Observations use their own record word
 * (TideService.records2): 1 the world gone silent, 2 the black sun covering the sun, 4 answering the knock, 8 finding the
 * only sound, 16 the procession's lantern raised to you, 32 waiting out the late thunder, and 64 (kept secret) the black
 * sun hearing you.
 */
public final class HushService {
    public static final int FIRST=1, ECLIPSE=2, KNOCK=4, BELL=8, PROCESSION=16, THUNDER=32, HEARD=64;
    public static final int FLASH_COOLDOWN=24, FLASH_REACH=12, LANTERN_REACH=24;
    public static final double PROCESSION_REACH=18, FIND_RANGE=2.8, HEARD_ANGLE=Math.toRadians(8);
    private static final Map<UUID,Integer> lastFlash=new HashMap<>();
    private static final Set<UUID> sawLine=new HashSet<>(),gifted=new HashSet<>();

    public static void sync(ServerPlayer p){
        HushData d=HushData.get(p.serverLevel());
        if(p.connection!=null&&p.connection.hasChannel(HushPacket.TYPE))PacketDistributor.sendToPlayer(p,HushPacket.from(d,TideService.records2(p)));
    }
    public static void broadcast(ServerLevel level){level.getServer().getPlayerList().getPlayers().forEach(HushService::sync);}
    /** Tells everyone within {@code range} blocks of a moment to show it. */
    public static void cue(ServerLevel level,HushCue cue,double range){cue(level,cue,range,null);}
    public static void cue(ServerLevel level,HushCue cue,double range,ServerPlayer except){
        for(ServerPlayer p:level.players())
            if(p!=except&&p.position().distanceTo(cue.at())<range&&p.connection!=null&&p.connection.hasChannel(HushCue.TYPE))PacketDistributor.sendToPlayer(p,cue);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p);}

    public static int variants(ServerLevel level){
        if(!TideConfig.HUSH_VISITORS.get())return 0;
        int r=level.random.nextInt(5);
        return r<2?HushData.ALL:r==2?HushData.VARIANT_PROCESSION|HushData.VARIANT_KNOCK:r==3?HushData.VARIANT_KNOCK|HushData.VARIANT_BELL:HushData.VARIANT_PROCESSION|HushData.VARIANT_BELL;
    }
    /** The black sun covers whichever of the sun and the moon will be up when it gets there. */
    public static boolean moonAtTotality(ServerLevel level,int ticks){
        boolean cycle=level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        long at=level.getDayTime()+(cycle?Math.round(HushTimeline.TOTAL0*ticks):0);
        double tod=level.dimensionType().timeOfDay(at);
        return HushTimeline.sunDir(tod).y<0;
    }
    public static void begin(ServerLevel level,BlockPos origin,int seconds){
        if(TideData.get(level).active)TideService.end(level);
        if(MoonData.get(level).active)MoonService.end(level);
        if(MirrorData.get(level).active)MirrorService.end(level);
        if(ThreadData.get(level).active)ThreadService.end(level);
        if(DreamData.get(level).active)DreamService.end(level);
        HushData d=HushData.get(level);
        if(d.active)end(level);
        d.begin(origin,level.random.nextLong(),seconds*20,TideConfig.RADIUS.get(),variants(level),moonAtTotality(level,seconds*20));
        reset();
        broadcast(level);
    }
    private static void reset(){lastFlash.clear();sawLine.clear();gifted.clear();}
    public static void end(ServerLevel level){
        HushData d=HushData.get(level);
        HushSilence.dismiss(level,d,false);
        d.active=false;d.setDirty();
        VeilGrade.settle(level,VeilGrade.HUSH,d.seed);
        TideService.rest(level);
        for(var e:com.google.common.collect.ImmutableList.copyOf(level.getEntities().getAll())){
            if(e instanceof HushProcession p)p.finish();
            else if(e instanceof HushCoat c)c.finish();
        }
        reset();
        broadcast(level);
    }

    @SubscribeEvent public static void tick(LevelTickEvent.Post e){
        if(!(e.getLevel() instanceof ServerLevel level)||!level.dimension().equals(Level.OVERWORLD))return;
        HushData d=HushData.get(level);
        if(level.getGameTime()%10==0)HushSilence.sweep(level);
        if(!d.active)return;
        if(level.getServer().getPlayerList().getPlayers().isEmpty())return;
        if(++d.age>=d.duration){end(level);return;}
        d.setDirty();
        double p=d.progress();
        var witnesses=level.players().stream().filter(pl->!pl.isSpectator()&&d.strength(pl.getX(),pl.getZ())>.25).toList();
        if(d.age%20==0){
            broadcast(level);
            for(var pl:witnesses){
                VeilGrade.witness(pl,VeilGrade.HUSH,d.seed);
                if((TideService.records2(pl)&FIRST)==0&&HushTimeline.greyFront(p)<160&&HushTimeline.hush(p)>.9){
                    TideService.record2(pl,FIRST);
                    if(TideConfig.AUTO_JOURNAL.get()&&!pl.getInventory().contains(new ItemStack(Veilborne.JOURNAL.get())))TideService.give(pl,new ItemStack(Veilborne.JOURNAL.get()));
                }
                if(HushTimeline.totality(p)>.5&&level.canSeeSky(pl.blockPosition())){TideService.record2(pl,ECLIPSE);VeilGrade.deed(pl,VeilGrade.HUSH,d.seed,VeilGrade.HUSH_ECLIPSE);}
            }
        }
        thunder(level,d,witnesses);
        HushSilence.tick(level,d,p);
        if(witnesses.isEmpty())return;
        ServerPlayer observer=witnesses.get(0);
        if(TideConfig.HUSH_VISITORS.get()){
            procession(level,d,p,observer);
            coat(level,d,p,witnesses);
            bell(level,d,p,observer);
        }
        feather(level,d,p,observer);
        // Animals turn toward the black sun; while it covers the sun they stand stock still, heads raised.
        if(p>HushTimeline.RISE&&p<HushTimeline.SET&&d.age%20==0){
            Vec3 look=blackDir(level,d,p).scale(80);
            boolean still=HushTimeline.totality(p)>.1;
            for(Animal a:level.getEntitiesOfClass(Animal.class,observer.getBoundingBox().inflate(24)).stream().limit(14).toList()){
                if(still||d.age%100==0)a.getNavigation().stop();
                a.getLookControl().setLookAt(a.getX()+look.x,a.getEyeY()+Math.max(4,look.y),a.getZ()+look.z,6,6);
            }
        }
    }
    public static Vec3 blackDir(Level level,HushData d,double p){
        return HushTimeline.blackDir(d.seed,HushTimeline.whiteDir(level.getTimeOfDay(1),d.moon),p);
    }

    // ------------------------------------------------------------------ the silent procession
    private static void procession(ServerLevel level,HushData d,double p,ServerPlayer observer){
        if((d.variant&HushData.VARIANT_PROCESSION)==0||d.has(HushData.PROCESSION)||p<HushTimeline.PROCESSION_FROM||p>HushTimeline.PROCESSION_UNTIL-.9/12||d.age%40!=0)return;
        HushProcession proc=HushProcession.plan(level,observer.blockPosition(),d.seed);
        if(proc!=null&&d.takeFlag(HushData.PROCESSION))level.addFreshEntity(proc);
    }

    // ------------------------------------------------------------------ the knock
    private static void coat(ServerLevel level,HushData d,double p,List<ServerPlayer> witnesses){
        if((d.variant&HushData.VARIANT_KNOCK)==0||d.has(HushData.COAT)||p<HushTimeline.KNOCK_FROM||p>HushTimeline.KNOCK_UNTIL-.7/12||d.age%40!=0)return;
        // A door first; only once the chance of a door has had a while does it knock on a tree instead.
        boolean trees=p>HushTimeline.KNOCK_FROM+1.1/12;
        for(ServerPlayer pl:witnesses){
            Vec3[] spot=doorSpot(level,pl);
            if(spot==null&&trees)spot=treeSpot(level,pl);
            if(spot==null)continue;
            HushCoat c=Veilborne.HUSH_COAT.get().create(level);
            if(c==null||!d.takeFlag(HushData.COAT))return;
            c.place(spot[0],BlockPos.containing(spot[1]));
            level.addFreshEntity(c);
            return;
        }
    }
    /** The outside of a door near someone: [where the coat stands, the door's lower block]. Doors into roofed rooms first. */
    public static Vec3[] doorSpot(ServerLevel level,ServerPlayer near){
        BlockPos o=near.blockPosition();Vec3[] best=null;double bestScore=-1e9;
        for(BlockPos b:BlockPos.betweenClosed(o.offset(-14,-4,-14),o.offset(14,4,14))){
            BlockState s=level.getBlockState(b);
            if(!(s.getBlock() instanceof DoorBlock)||s.getValue(DoorBlock.HALF)!=DoubleBlockHalf.LOWER)continue;
            Direction f=s.getValue(DoorBlock.FACING);
            for(Direction side:new Direction[]{f,f.getOpposite()}){
                BlockPos out=b.relative(side),in=b.relative(side.getOpposite());
                if(!level.getBlockState(out).isAir()||!level.getBlockState(out.above()).isAir()||!level.getBlockState(out.below()).isFaceSturdy(level,out.below(),Direction.UP))continue;
                if(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,out.getX(),out.getZ())>out.getY()+2)continue;
                boolean roofed=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,in.getX(),in.getZ())>in.getY()+1;
                double score=(roofed?10:0)-b.distManhattan(o)*.2;
                if(score>bestScore){bestScore=score;best=new Vec3[]{Vec3.atBottomCenterOf(out).add(side.getStepX()*.02,.02,side.getStepZ()*.02),Vec3.atBottomCenterOf(b)};}
            }
        }
        return best;
    }
    /** A tree trunk near someone, on its open side: [where the coat stands, the trunk block it knocks on]. */
    public static Vec3[] treeSpot(ServerLevel level,ServerPlayer near){
        BlockPos o=near.blockPosition();
        for(int i=0;i<200;i++){
            int x=o.getX()+near.getRandom().nextInt(25)-12,z=o.getZ()+near.getRandom().nextInt(25)-12;
            if(!level.hasChunk(x>>4,z>>4))continue;
            int g=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            BlockPos trunk=new BlockPos(x,g-1,z);
            // Walk down to the bottom of the trunk from where it stops blocking.
            while(level.getBlockState(trunk.below()).is(BlockTags.LOGS))trunk=trunk.below();
            if(!level.getBlockState(trunk).is(BlockTags.LOGS)||!level.getBlockState(trunk.above()).is(BlockTags.LOGS))continue;
            for(Direction d:Direction.Plane.HORIZONTAL){
                BlockPos out=trunk.relative(d);
                if(!level.getBlockState(out).isAir()||!level.getBlockState(out.above()).isAir()||!level.getBlockState(out.below()).isFaceSturdy(level,out.below(),Direction.UP))continue;
                if(out.distManhattan(o)<4)continue;
                return new Vec3[]{Vec3.atBottomCenterOf(out).add(d.getStepX()*.2,.02,d.getStepZ()*.2),Vec3.atBottomCenterOf(trunk)};
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ the only sound
    public static void bell(ServerLevel level,HushData d,double p,ServerPlayer observer){
        if((d.variant&HushData.VARIANT_BELL)==0)return;
        if(!d.has(HushData.BELL)&&p>=HushTimeline.BELL_FROM&&p<HushTimeline.BELL_UNTIL-1/12.0&&d.age%20==0){
            BlockPos[] spot=bellSpot(level,observer,d.seed);
            if(spot!=null&&d.takeFlag(HushData.BELL)){d.bell=spot[0];d.bellTop=spot[1];d.bellAt=d.age;broadcast(level);}
        }
        if(!d.hasBell())return;
        Vec3 at=Vec3.atBottomCenterOf(d.bell).add(0,.62,0);
        if(!d.found()){
            if(p>=HushTimeline.BELL_UNTIL)return;
            if(HushTimeline.sinceRing(d.age,d.bellAt,d.seed)==0)level.playSound(null,at.x,at.y,at.z,Veilborne.HUSH_BELL.get(),SoundSource.AMBIENT,1.4f,1);
            for(ServerPlayer pl:level.players()){
                if(pl.isSpectator()||pl.position().add(0,1,0).distanceTo(at)>FIND_RANGE)continue;
                d.takeFlag(HushData.FOUND);d.foundAt=d.age;d.setDirty();
                level.playSound(null,at.x,at.y,at.z,Veilborne.HUSH_BELL.get(),SoundSource.AMBIENT,1.6f,1.12f);
                for(ServerPlayer w:level.players())if(!w.isSpectator()&&w.position().distanceTo(at)<HushTimeline.ISLAND_RANGE+2){TideService.record2(w,BELL);VeilGrade.deed(w,VeilGrade.HUSH,d.seed,VeilGrade.HUSH_BELL);}
                broadcast(level);
                break;
            }
            return;
        }
        int since=d.age-d.foundAt;
        // While the island is open the bell rings clear every so often; when it closes the bell comes down, and is yours.
        if(since>0&&since<HushTimeline.ISLAND_TICKS-120&&since%120==0)level.playSound(null,at.x,at.y,at.z,Veilborne.HUSH_BELL.get(),SoundSource.AMBIENT,1.1f,1.12f);
        if(since>=HushTimeline.ISLAND_TICKS&&d.takeFlag(HushData.BELL_DROPPED)){
            TideService.drop(level,BlockPos.containing(at.x,at.y-.5,at.z),new ItemStack(Veilborne.HOLLOW_BELL.get()));
            broadcast(level);
        }
    }
    /** Hanging under a tree or a rock edge some way off, or, where nothing overhangs, in plain air: [bell, what it hangs from]. */
    public static BlockPos[] bellSpot(ServerLevel level,ServerPlayer near,long seed){
        BlockPos o=near.blockPosition();BlockPos[] floating=null;
        for(int k=0;k<48;k++){
            double a=(Math.floorMod(seed>>>11,360L)+k*47.0)*Math.PI/180,r=19+(k*7%18);
            int x=(int)Math.floor(o.getX()+Math.cos(a)*r),z=(int)Math.floor(o.getZ()+Math.sin(a)*r);
            if(!level.hasChunk(x>>4,z>>4))continue;
            int g=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            if(Math.abs(g-o.getY())>10)continue;
            BlockPos floor=new BlockPos(x,g-1,z);
            if(!level.getFluidState(floor).isEmpty()||!level.getBlockState(floor).isFaceSturdy(level,floor,Direction.UP))continue;
            if(!level.getBlockState(new BlockPos(x,g,z)).isAir()||!level.getBlockState(new BlockPos(x,g+1,z)).isAir())continue;
            for(int y=g+3;y<=g+7;y++){
                BlockPos up=new BlockPos(x,y,z);
                BlockState s=level.getBlockState(up);
                if(s.isAir())continue;
                boolean leaves=s.getBlock() instanceof LeavesBlock,rock=s.isFaceSturdy(level,up,Direction.DOWN);
                boolean clear=true;
                for(int yy=g;yy<y;yy++)if(!level.getBlockState(new BlockPos(x,yy,z)).isAir())clear=false;
                if(clear&&(leaves||rock))return new BlockPos[]{new BlockPos(x,g+1,z),up};
                break;
            }
            if(floating==null)floating=new BlockPos[]{new BlockPos(x,g+1,z),BlockPos.ZERO};
        }
        return floating;
    }

    // ------------------------------------------------------------------ the feather held in mid-fall
    private static void feather(ServerLevel level,HushData d,double p,ServerPlayer observer){
        if(!d.has(HushData.FEATHER)&&p>=HushTimeline.FEATHER_AT&&p<HushTimeline.BIRD-.05&&d.age%20==0){
            Vec3 look=observer.getLookAngle();
            Vec3 flat=new Vec3(look.x,0,look.z);
            flat=flat.lengthSqr()<1e-3?new Vec3(1,0,0):flat.normalize();
            for(int k=0;k<6;k++){
                double turn=(k%2==0?1:-1)*k*.45;
                Vec3 dir=new Vec3(flat.x*Math.cos(turn)-flat.z*Math.sin(turn),0,flat.x*Math.sin(turn)+flat.z*Math.cos(turn));
                BlockPos ground=TideService.findSafe(level,BlockPos.containing(observer.position().add(dir.scale(3.4))),1);
                if(ground==null)continue;
                BlockPos air=ground.above(2);
                if(!level.getBlockState(air).isAir()||!level.getBlockState(air.above()).isAir())continue;
                if(d.takeFlag(HushData.FEATHER)){d.feather=ground;d.featherAt=d.age;broadcast(level);}
                return;
            }
        }
        // After the bird it falls again, turning, and lands at last.
        if(d.has(HushData.FEATHER)&&!d.feather.equals(BlockPos.ZERO)&&p>=HushTimeline.BIRD&&d.age>=Math.round(HushTimeline.BIRD*d.duration)+FEATHER_FALL&&d.takeFlag(HushData.LANDED)){
            TideService.drop(level,d.feather,new ItemStack(Veilborne.STILL_FEATHER_ITEM.get()));
            level.playSound(null,d.feather,Veilborne.HUSH_CHIRP.get(),SoundSource.AMBIENT,.7f,1.1f);
            broadcast(level);
        }
    }
    /** Ticks the feather takes to come down once it is let go. */
    public static final int FEATHER_FALL=120;
    /** Its height above the ground while it hangs. */
    public static final double FEATHER_HEIGHT=2.35;

    // ------------------------------------------------------------------ the late thunder
    private static void thunder(ServerLevel level,HushData d,List<ServerPlayer> witnesses){
        int line=HushTimeline.lineTick(d.duration),thunder=HushTimeline.thunderTick(d.duration);
        if(d.age>=line&&d.age<line+HushTimeline.LINE_HOLD)for(ServerPlayer pl:witnesses)if(level.canSeeSky(pl.blockPosition()))sawLine.add(pl.getUUID());
        if(d.age==thunder){
            d.takeFlag(HushData.THUNDER);
            for(ServerPlayer pl:witnesses)if(sawLine.contains(pl.getUUID())){TideService.record2(pl,THUNDER);VeilGrade.deed(pl,VeilGrade.HUSH,d.seed,VeilGrade.HUSH_THUNDER);}
        }
    }

    // ------------------------------------------------------------------ raising a light
    /** Anything that is itself a light, or places one: torches, lanterns, glowstone, sea lanterns, and the silent lantern. */
    public static boolean isLight(ItemStack s){
        if(s.isEmpty())return false;
        if(s.getItem() instanceof BlockItem b)return b.getBlock().defaultBlockState().getLightEmission()>=10;
        return false;
    }
    public static boolean silentLantern(Player p){return p.getMainHandItem().is(Veilborne.SILENT_LANTERN_ITEM.get())||p.getOffhandItem().is(Veilborne.SILENT_LANTERN_ITEM.get());}
    @SubscribeEvent public static void use(PlayerInteractEvent.RightClickItem e){
        if(!(e.getEntity() instanceof ServerPlayer p)||!isLight(e.getItemStack()))return;
        if(flash(p))e.getEntity().swing(e.getHand(),true);
    }
    /** Someone raised a light. Returns whether it counted (the weather is here and they have not just done so). */
    public static boolean flash(ServerPlayer p){
        ServerLevel level=p.serverLevel();
        HushData d=HushData.get(level);
        if(!level.dimension().equals(Level.OVERWORLD)||!d.active||d.strength(p.getX(),p.getZ())<.25||HushTimeline.hush(d.progress())<.5)return false;
        int now=level.getServer().getTickCount();
        Integer last=lastFlash.get(p.getUUID());
        if(last!=null&&now-last<FLASH_COOLDOWN)return false;
        lastFlash.put(p.getUUID(),now);
        int reach=silentLantern(p)?LANTERN_REACH:FLASH_REACH;
        Vec3 hand=p.getEyePosition().add(p.getLookAngle().scale(.6)).add(0,-.25,0);
        cue(level,new HushCue(HushCue.FLASH,hand,reach),96,p);
        double pr=d.progress();
        for(var en:com.google.common.collect.ImmutableList.copyOf(level.getEntities().getAll())){
            if(en instanceof HushCoat c&&c.position().add(0,1.2,0).distanceTo(p.getEyePosition())<HushCoat.REACH&&sees(level,p,c.position().add(0,1.2,0))&&c.answer()){
                TideService.record2(p,KNOCK);
                VeilGrade.deed(p,VeilGrade.HUSH,d.seed,VeilGrade.HUSH_KNOCK);
                level.playSound(null,c.getX(),c.getY()+1.4,c.getZ(),Veilborne.HUSH_ANSWER.get(),SoundSource.AMBIENT,.7f,.85f);
            }else if(en instanceof HushProcession proc&&proc.present()&&proc.nearest(p.getEyePosition())<Math.max(PROCESSION_REACH,reach)&&proc.answer(p.position())){
                TideService.record2(p,PROCESSION);
                VeilGrade.deed(p,VeilGrade.HUSH,d.seed,VeilGrade.HUSH_PROCESSION);
                level.playSound(null,proc.getX(),proc.getY()+1.6,proc.getZ(),Veilborne.HUSH_ANSWER.get(),SoundSource.AMBIENT,.8f,1);
                if(gifted.add(p.getUUID())){
                    double[] m=HushProcession.MEMBERS[2];
                    TideService.drop(level,BlockPos.containing(proc.member(proc.path(),m[0],m[1])),new ItemStack(Veilborne.DAYLIGHT_WAX.get()));
                }
            }
        }
        // The black sun, while it covers the sun: look straight at it and raise a light, and it hears you.
        if(HushTimeline.totality(pr)>.5&&level.canSeeSky(p.blockPosition())){
            Vec3 b=blackDir(level,d,pr);
            if(Math.acos(Math.max(-1,Math.min(1,p.getLookAngle().dot(b))))<HEARD_ANGLE&&!VeilGrade.done(p,VeilGrade.HUSH,d.seed,VeilGrade.HUSH_HEARD)){
                VeilGrade.deed(p,VeilGrade.HUSH,d.seed,VeilGrade.HUSH_HEARD);
                TideService.record2(p,HEARD);
                if(p.connection!=null&&p.connection.hasChannel(HushCue.TYPE))PacketDistributor.sendToPlayer(p,new HushCue(HushCue.HEARD,p.getEyePosition().add(b.scale(120)),0));
            }
        }
        return true;
    }
    /** Nothing but air and glass between someone's eyes and a point. */
    static boolean sees(Level level,Player p,Vec3 at){
        var hit=level.clip(new ClipContext(p.getEyePosition(),at,ClipContext.Block.VISUAL,ClipContext.Fluid.NONE,p));
        return hit.getType()==HitResult.Type.MISS||hit.getLocation().distanceTo(at)<.6;
    }

    // ------------------------------------------------------------------ commands
    static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> command(){
        return VeilService.weather("silentday",TideConfig.HUSH_DURATION::get,HushService::begin,HushService::end,l->HushData.get(l).active,
            (l,p)->{var d=HushData.get(l);d.age=(int)(d.duration*p/100.0);d.setDirty();broadcast(l);},
            l->{var d=HushData.get(l);return "无声白昼 · "+(d.active?HushTimeline.phase(d.progress())+" "+d.age/20+" / "+d.duration/20+"s · "+d.center.toShortString()
                +" · "+(d.moon?"蚀月":"蚀日")+(d.hasBell()?" · 铃 "+d.bell.toShortString()+(d.found()?"（已找到）":""):""):"静候");});
    }
    private HushService(){}
}
