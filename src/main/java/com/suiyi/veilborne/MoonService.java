package com.suiyi.veilborne;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 月骸花潮 on the server: timeline, visitors, keepsakes and the rule that only one veil weather is out at a time.
 * Observation bits continue the shared record word: 32 first shard, 64 molt, 128 escort, 256 constellation,
 * 512 true moon, 1024 the empty place (kept secret until seen).
 */
public final class MoonService {
    public static final int FIRST=32, MOLT=64, ESCORT=128, CONSTELLATION=256, TRUE_MOON=512, EMPTY_PLACE=1024;
    public static final String DEW_SEED="VeilborneDewSeed", DEW_COUNT="VeilborneDewCount";
    public static final int DEW_PER_PLAYER=3;
    private static final Map<UUID,Integer> escort=new HashMap<>(), gaze=new HashMap<>();

    public static void sync(ServerPlayer p){if(p.connection!=null&&p.connection.hasChannel(MoonPacket.TYPE))PacketDistributor.sendToPlayer(p,MoonPacket.from(MoonData.get(p.serverLevel()),TideService.records(p)));}
    public static void broadcast(ServerLevel level){level.players().forEach(MoonService::sync);}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p);}

    public static void begin(ServerLevel level,BlockPos origin,int seconds){
        if(TideData.get(level).active)TideService.end(level);
        if(MirrorData.get(level).active)MirrorService.end(level);
        if(ThreadData.get(level).active)ThreadService.end(level);
        if(HushData.get(level).active)HushService.end(level);
        if(DreamData.get(level).active)DreamService.end(level);
        MoonData d=MoonData.get(level);
        if(d.active)end(level);
        d.begin(origin,level.random.nextLong(),seconds*20,TideConfig.RADIUS.get());
        escort.clear();gaze.clear();
        broadcast(level);
    }
    public static void end(ServerLevel level){
        MoonData d=MoonData.get(level);d.active=false;d.littleMoon=BlockPos.ZERO;d.setDirty();
        VeilGrade.settle(level,VeilGrade.MOON,d.seed);
        TideService.rest(level);
        for(var e:com.google.common.collect.ImmutableList.copyOf(level.getEntities().getAll()))if(e instanceof MoonProcession p)p.finish();
        escort.clear();gaze.clear();
        broadcast(level);
    }

    @SubscribeEvent public static void tick(LevelTickEvent.Post e){
        if(!(e.getLevel() instanceof ServerLevel level)||!level.dimension().equals(Level.OVERWORLD))return;
        if(level.players().isEmpty())return;
        MoonData d=MoonData.get(level);
        if(!d.active)return;
        if(++d.age>=d.duration){end(level);return;}
        d.setDirty();
        double p=d.progress();
        var witnesses=level.players().stream().filter(pl->!pl.isSpectator()&&d.strength(pl.getX(),pl.getZ())>.25).toList();
        if(d.age%20==0){
            broadcast(level);
            for(var pl:witnesses){
                VeilGrade.witness(pl,VeilGrade.MOON,d.seed);
                if((TideService.records(pl)&FIRST)==0){
                    TideService.record(pl,FIRST);
                    if(TideConfig.AUTO_JOURNAL.get()&&!pl.getInventory().contains(new ItemStack(Veilborne.JOURNAL.get())))TideService.give(pl,new ItemStack(Veilborne.JOURNAL.get()));
                }
                if(p>.6&&p<.745&&level.canSeeSky(pl.blockPosition())){TideService.record(pl,MOLT);VeilGrade.deed(pl,VeilGrade.MOON,d.seed,VeilGrade.MOON_MOLT);}
            }
        }
        if(witnesses.isEmpty())return;
        var observer=witnesses.get(0);
        if(TideConfig.MOON_VISITORS.get()&&p>=.27&&p<.5&&(d.flags&MoonData.PROCESSION)==0&&d.age%40==0){
            MoonProcession procession=MoonProcession.plan(level,observer.blockPosition(),d.seed);
            if(procession!=null&&d.takeFlag(MoonData.PROCESSION))level.addFreshEntity(procession);
        }
        if(TideConfig.MOON_VISITORS.get()&&p>=.33&&p<.8&&(d.flags&MoonData.LITTLE_MOON)==0&&d.age%40==0){
            BlockPos pos=grove(level,observer.blockPosition(),d.seed);
            if(pos!=null&&d.takeFlag(MoonData.LITTLE_MOON)){
                d.littleMoon=pos;d.littleMoonAt=d.age;
                level.playSound(null,pos,Veilborne.CHIME.get(),SoundSource.AMBIENT,.8f,1.3f);
                broadcast(level);
            }
        }
        if(!d.littleMoon.equals(BlockPos.ZERO)){
            Vec3 base=Vec3.atBottomCenterOf(d.littleMoon),center=base.add(0,MoonTimeline.LITTLE_MOON_HEIGHT,0);
            Vec3 spot=MoonTimeline.viewpoint(d.seed,base);
            for(var pl:witnesses){
                if(MoonTimeline.sightAligned(pl.getEyePosition(),pl.getLookAngle(),center,spot)&&Math.abs(pl.getY()-base.y)<2){
                    if(gaze.merge(pl.getUUID(),1,Integer::sum)>=40){TideService.record(pl,CONSTELLATION);VeilGrade.deed(pl,VeilGrade.MOON,d.seed,VeilGrade.MOON_STARS);}
                }else gaze.remove(pl.getUUID());
            }
            if(p>=.88&&d.takeFlag(MoonData.REMNANT)){
                TideService.drop(level,d.littleMoon,new ItemStack(Veilborne.MOON_SHARD.get()));
                d.littleMoon=BlockPos.ZERO;broadcast(level);
            }
        }
        if(d.age%10==0){
            for(MoonProcession proc:level.getEntities(Veilborne.PROCESSION.get(),x->true)){
                Vec3 lead=proc.member(proc.path(),0,0),litter=proc.member(proc.path(),MoonProcession.LITTER,0),empty=proc.member(proc.path(),MoonProcession.EMPTY,0);
                for(var pl:witnesses){
                    double near=Math.min(pl.position().distanceTo(lead),pl.position().distanceTo(litter));
                    if(proc.walking()&&near>3.5&&near<14){
                        if(escort.merge(pl.getUUID(),10,Integer::sum)>=300){TideService.record(pl,ESCORT);VeilGrade.deed(pl,VeilGrade.MOON,d.seed,VeilGrade.MOON_ESCORT);}
                    }
                    if(proc.visibilityServer()&&pl.position().distanceTo(empty)<10&&holdsLantern(pl)){TideService.record(pl,EMPTY_PLACE);VeilGrade.deed(pl,VeilGrade.MOON,d.seed,VeilGrade.MOON_EMPTY);}
                }
            }
        }
        if(p>=.8&&(d.flags&MoonData.LANTERN)==0)placeLantern(level,observer.blockPosition().offset(3,0,2),Direction.NORTH);
        if(p>.06&&p<.86&&d.age%20==0){
            Vec3 moon=Vec3.atCenterOf(d.center).add(MoonTimeline.trueDir(d.seed,p,MoonData.merge(p,d.age,d.brokenAt,d.broken())).scale(400));
            for(Animal a:level.getEntitiesOfClass(Animal.class,observer.getBoundingBox().inflate(24)).stream().limit(14).toList()){
                if(d.age%80==0)a.getNavigation().stop();
                a.getLookControl().setLookAt(moon.x,moon.y,moon.z,8,8);
                a.ambientSoundTime=-a.getAmbientSoundInterval()*2;
            }
        }
    }
    static boolean holdsLantern(ServerPlayer p){return p.getMainHandItem().is(Veilborne.SILVER_LANTERN_ITEM.get())||p.getOffhandItem().is(Veilborne.SILVER_LANTERN_ITEM.get());}

    /** Somewhere open under the trees if there is a grove nearby; any safe clearing otherwise. */
    public static BlockPos grove(ServerLevel level,BlockPos near,long seed){
        BlockPos best=null;int bestLeaves=-1;
        for(int i=0;i<24;i++){
            double a=(seed%360+i*137.5)*Math.PI/180,r=10+(i*7%17);
            BlockPos probe=TideService.findSafe(level,near.offset((int)(Math.cos(a)*r),0,(int)(Math.sin(a)*r)),4);
            if(probe==null||!level.canSeeSky(probe.above(2)))continue;
            int leaves=0;
            for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)for(int y=1;y<=7;y++)if(level.getBlockState(probe.offset(x,y,z)).is(BlockTags.LEAVES))leaves++;
            if(leaves>bestLeaves){bestLeaves=leaves;best=probe;}
        }
        return best!=null?best:TideService.findSafe(level,near.offset(14,0,-10),22);
    }
    /** The last bearer sets its lantern down beside the road, then the procession thins into petals. */
    public static void leaveLantern(ServerLevel level,MoonProcession proc){
        Vec3 side=proc.member(proc.path(),MoonProcession.MEMBERS[5][0],1.8);
        float yaw=proc.yawAt(proc.path(),MoonProcession.MEMBERS[5][0]);
        placeLantern(level,BlockPos.containing(side),Direction.fromYRot(yaw+90));
    }
    public static void placeLantern(ServerLevel level,BlockPos near,Direction facing){
        MoonData d=MoonData.get(level);
        if(!d.takeFlag(MoonData.LANTERN))return;
        BlockPos pos=TideService.findSafe(level,near,4);
        if(pos==null)return;
        if(TideConfig.MOON_LANTERNS.get())level.setBlock(pos,Veilborne.SILVER_LANTERN.get().defaultBlockState().setValue(SilverLanternBlock.FACING,facing.getAxis().isHorizontal()?facing:Direction.NORTH),3);
        TideService.drop(level,pos,new ItemStack(Veilborne.MOON_SHARD.get()));
        d.lantern=pos;d.lanternAt=d.age;
        level.playSound(null,pos,Veilborne.MOON_BELL.get(),SoundSource.AMBIENT,.7f,.8f);
        broadcast(level);
    }

    /** 银针月仪: aim at the moon you believe is real and use the dial. The reflections break; nothing bad happens on a wrong guess. */
    public static void calibrate(ServerPlayer p){
        ServerLevel level=p.serverLevel();MoonData d=MoonData.get(level);
        if(!d.active||!level.dimension().equals(Level.OVERWORLD)){p.displayClientMessage(Component.translatable("message.veilborne.dial_quiet"),true);return;}
        double prog=d.progress();
        if(!MoonTimeline.threeActive(prog)){p.displayClientMessage(Component.translatable(prog<.63?"message.veilborne.dial_waiting":"message.veilborne.dial_settled"),true);return;}
        if(d.broken()){p.displayClientMessage(Component.translatable("message.veilborne.dial_done"),true);return;}
        int slot=MoonTimeline.aimedSlot(d.seed,p.getLookAngle(),MoonTimeline.split(prog),Math.toRadians(10));
        if(slot<0){p.displayClientMessage(Component.translatable("message.veilborne.dial_aim"),true);return;}
        if(slot!=MoonTimeline.trueSlot(d.seed)){
            p.displayClientMessage(Component.translatable("message.veilborne.dial_false"),true);
            level.playSound(null,p.blockPosition(),Veilborne.CHIME.get(),SoundSource.PLAYERS,.4f,.6f);
            return;
        }
        d.flags|=MoonData.BROKEN;d.brokenAt=d.age;d.setDirty();
        TideService.record(p,TRUE_MOON);
        p.displayClientMessage(Component.translatable("message.veilborne.dial_true"),false);
        level.playSound(null,p.blockPosition(),Veilborne.MOON_CRACK.get(),SoundSource.WEATHER,1,1);
        // The sooner the reflections break, the more of the moon's light is left over.
        double left=moonlightLeft(prog);
        int shards=1+(int)Math.round(2*left),dew=(int)Math.round(2*left);
        TideService.drop(level,p.blockPosition(),new ItemStack(Veilborne.MOON_SHARD.get(),shards));
        if(dew>0)TideService.drop(level,p.blockPosition(),new ItemStack(Veilborne.MOON_DEW.get(),dew));
        p.displayClientMessage(Component.translatable("message.veilborne.dial_light",(int)Math.round(left*10)),true);
        VeilGrade.deed(p,VeilGrade.MOON,d.seed,VeilGrade.MOON_TRUE);
        if(left>=.5)VeilGrade.deed(p,VeilGrade.MOON,d.seed,VeilGrade.MOON_EARLY);
        broadcast(level);
    }

    /** 1 the moment the three moons part, 0 as they are about to merge again. */
    public static double moonlightLeft(double progress){return 1-TideTimeline.clamp((progress-.64)/(.75-.64));}

    // ------------------------------------------------------------------ moon dew
    @SubscribeEvent public static void useItem(PlayerInteractEvent.RightClickItem e){
        if(e.getEntity() instanceof ServerPlayer p&&e.getItemStack().is(Items.GLASS_BOTTLE)&&collectDew(p,e.getHand())){e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);}
    }
    @SubscribeEvent public static void useOnBlock(PlayerInteractEvent.RightClickBlock e){
        if(e.getEntity() instanceof ServerPlayer p&&e.getItemStack().is(Items.GLASS_BOTTLE)&&collectDew(p,e.getHand())){e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);}
    }
    public static boolean collectDew(ServerPlayer p,InteractionHand hand){
        MoonData d=MoonData.get(p.serverLevel());
        if(!d.active||d.littleMoon.equals(BlockPos.ZERO))return false;
        Vec3 center=Vec3.atBottomCenterOf(d.littleMoon).add(0,MoonTimeline.LITTLE_MOON_HEIGHT,0);
        if(p.getEyePosition().distanceTo(center)>3.8)return false;
        if(!dewAllowed(p.getPersistentData(),d.seed)){p.displayClientMessage(Component.translatable("message.veilborne.dew_empty"),true);return true;}
        if(!p.isCreative())p.getItemInHand(hand).shrink(1);
        TideService.give(p,new ItemStack(Veilborne.MOON_DEW.get()));
        VeilGrade.deed(p,VeilGrade.MOON,d.seed,VeilGrade.MOON_DEW);
        p.serverLevel().playSound(null,d.littleMoon,Veilborne.CHIME.get(),SoundSource.PLAYERS,.6f,1.6f);
        p.displayClientMessage(Component.translatable("message.veilborne.dew"),true);
        return true;
    }
    /** Each little moon yields up to three bottles to each player. */
    public static boolean dewAllowed(CompoundTag tag,long seed){
        if(tag.getLong(DEW_SEED)!=seed||!tag.contains(DEW_COUNT)){tag.putLong(DEW_SEED,seed);tag.putInt(DEW_COUNT,0);}
        int count=tag.getInt(DEW_COUNT);
        if(count>=DEW_PER_PLAYER)return false;
        tag.putInt(DEW_COUNT,count+1);return true;
    }

    // ------------------------------------------------------------------ commands
    static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> command(){
        return VeilService.weather("moonbloom",TideConfig.MOON_DURATION::get,MoonService::begin,MoonService::end,l->MoonData.get(l).active,
            (l,p)->{var d=MoonData.get(l);d.age=(int)(d.duration*p/100.0);d.setDirty();broadcast(l);},
            l->{var d=MoonData.get(l);return "月骸花潮 · "+(d.active?MoonTimeline.phase(d.progress())+" "+d.age/20+" / "+d.duration/20+"s · "+d.center.toShortString()+(d.broken()?" · 真月已辨":""):"静候");});
    }
    private MoonService(){}
}
