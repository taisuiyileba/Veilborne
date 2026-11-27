package com.suiyi.veilborne;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record ThreadPacket(boolean active,long seed,int age,int duration,int radius,BlockPos center,int flags,int variant,
                           List<BlockPos> strikes,BlockPos landA,BlockPos landB,int landAt,BlockPos pocket,int pocketAt,BlockPos anchor,int tuneAt,
                           BlockPos keepsake,int keepsakeAt,int records) implements CustomPacketPayload {
    public static final Type<ThreadPacket> TYPE=new Type<>(Veilborne.id("thread"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ThreadPacket> CODEC=StreamCodec.of(
        (b,p)->{b.writeBoolean(p.active);b.writeLong(p.seed);b.writeVarInt(p.age);b.writeVarInt(p.duration);b.writeVarInt(p.radius);b.writeBlockPos(p.center);
            b.writeVarInt(p.flags);b.writeVarInt(p.variant);b.writeVarInt(p.strikes.size());for(BlockPos s:p.strikes)b.writeBlockPos(s);
            b.writeBlockPos(p.landA);b.writeBlockPos(p.landB);b.writeVarInt(p.landAt);b.writeBlockPos(p.pocket);b.writeVarInt(p.pocketAt);
            b.writeBlockPos(p.anchor);b.writeVarInt(p.tuneAt);b.writeBlockPos(p.keepsake);b.writeVarInt(p.keepsakeAt);b.writeVarInt(p.records);},
        b->{boolean active=b.readBoolean();long seed=b.readLong();int age=b.readVarInt(),duration=b.readVarInt(),radius=b.readVarInt();BlockPos center=b.readBlockPos();
            int flags=b.readVarInt(),variant=b.readVarInt(),n=Math.min(8,b.readVarInt());BlockPos[] s=new BlockPos[n];for(int i=0;i<n;i++)s[i]=b.readBlockPos();
            return new ThreadPacket(active,seed,age,duration,radius,center,flags,variant,List.of(s),b.readBlockPos(),b.readBlockPos(),b.readVarInt(),b.readBlockPos(),b.readVarInt(),
                b.readBlockPos(),b.readVarInt(),b.readBlockPos(),b.readVarInt(),b.readVarInt());});
    public static ThreadPacket from(ThreadData d,int records){
        return new ThreadPacket(d.active,d.seed,d.age,d.duration,d.radius,d.center,d.flags,d.variant,List.of(d.strikes),d.landA,d.landB,d.landAt,
            d.pocket,d.pocketAt,d.anchor,d.tuneAt,d.keepsake,d.keepsakeAt,records);
    }
    public boolean has(int bit){return (flags&bit)!=0;}
    public boolean hasLand(){return has(ThreadData.LAND)&&!landA.equals(BlockPos.ZERO);}
    public boolean hasPocket(){return has(ThreadData.POCKET)&&!pocket.equals(BlockPos.ZERO);}
    public BlockPos strike(int i){return i<strikes.size()?strikes.get(i):BlockPos.ZERO;}
    public ThreadTimeline.Seam seam(){return ThreadTimeline.seam(center,seed);}
    /** The land stitch's feet, standing on the ground at their blocks. */
    public Vec3 footA(){return Vec3.atBottomCenterOf(landA);}
    public Vec3 footB(){return Vec3.atBottomCenterOf(landB);}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
