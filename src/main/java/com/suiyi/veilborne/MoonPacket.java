package com.suiyi.veilborne;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record MoonPacket(boolean active,long seed,int age,int duration,int radius,BlockPos center,BlockPos littleMoon,int littleMoonAt,int flags,int brokenAt,BlockPos lantern,int lanternAt,int records) implements CustomPacketPayload {
    public static final Type<MoonPacket> TYPE=new Type<>(Veilborne.id("moon"));
    public static final StreamCodec<RegistryFriendlyByteBuf,MoonPacket> CODEC=StreamCodec.of(
        (b,p)->{b.writeBoolean(p.active);b.writeLong(p.seed);b.writeVarInt(p.age);b.writeVarInt(p.duration);b.writeVarInt(p.radius);b.writeBlockPos(p.center);
            b.writeBlockPos(p.littleMoon);b.writeVarInt(p.littleMoonAt);b.writeVarInt(p.flags);b.writeVarInt(p.brokenAt);b.writeBlockPos(p.lantern);b.writeVarInt(p.lanternAt);b.writeVarInt(p.records);},
        b->new MoonPacket(b.readBoolean(),b.readLong(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBlockPos(),b.readBlockPos(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBlockPos(),b.readVarInt(),b.readVarInt()));
    public static MoonPacket from(MoonData d,int records){return new MoonPacket(d.active,d.seed,d.age,d.duration,d.radius,d.center,d.littleMoon,d.littleMoonAt,d.flags,d.brokenAt,d.lantern,d.lanternAt,records);}
    public boolean broken(){return (flags&MoonData.BROKEN)!=0;}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
