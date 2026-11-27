package com.suiyi.veilborne;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record TidePacket(boolean active,long seed,int age,int duration,int radius,BlockPos center,BlockPos vortex,BlockPos landing,int vortexAt,int landingAt,int records) implements CustomPacketPayload {
    public static final Type<TidePacket> TYPE=new Type<>(Veilborne.id("tide"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TidePacket> CODEC=StreamCodec.of(
        (b,p)->{b.writeBoolean(p.active);b.writeLong(p.seed);b.writeVarInt(p.age);b.writeVarInt(p.duration);b.writeVarInt(p.radius);b.writeBlockPos(p.center);b.writeBlockPos(p.vortex);b.writeBlockPos(p.landing);b.writeVarInt(p.vortexAt);b.writeVarInt(p.landingAt);b.writeVarInt(p.records);},
        b->new TidePacket(b.readBoolean(),b.readLong(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBlockPos(),b.readBlockPos(),b.readBlockPos(),b.readVarInt(),b.readVarInt(),b.readVarInt()));
    public static TidePacket from(TideData d,int records) {return new TidePacket(d.active,d.seed,d.age,d.duration,d.radius,d.center,d.vortex,d.landing,d.vortexAt,d.landingAt,records);}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
