package com.suiyi.veilborne;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record HushPacket(boolean active,boolean moon,long seed,int age,int duration,int radius,BlockPos center,int flags,int variant,
                         BlockPos bell,BlockPos bellTop,int bellAt,int foundAt,BlockPos feather,int featherAt,int records) implements CustomPacketPayload {
    public static final Type<HushPacket> TYPE=new Type<>(Veilborne.id("hush"));
    public static final StreamCodec<RegistryFriendlyByteBuf,HushPacket> CODEC=StreamCodec.of(
        (b,p)->{b.writeBoolean(p.active);b.writeBoolean(p.moon);b.writeLong(p.seed);b.writeVarInt(p.age);b.writeVarInt(p.duration);b.writeVarInt(p.radius);
            b.writeBlockPos(p.center);b.writeVarInt(p.flags);b.writeVarInt(p.variant);b.writeBlockPos(p.bell);b.writeBlockPos(p.bellTop);b.writeVarInt(p.bellAt);
            b.writeVarInt(p.foundAt);b.writeBlockPos(p.feather);b.writeVarInt(p.featherAt);b.writeVarInt(p.records);},
        b->new HushPacket(b.readBoolean(),b.readBoolean(),b.readLong(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBlockPos(),b.readVarInt(),b.readVarInt(),
            b.readBlockPos(),b.readBlockPos(),b.readVarInt(),b.readVarInt(),b.readBlockPos(),b.readVarInt(),b.readVarInt()));
    public static HushPacket from(HushData d,int records){
        return new HushPacket(d.active,d.moon,d.seed,d.age,d.duration,d.radius,d.center,d.flags,d.variant,d.bell,d.bellTop,d.bellAt,d.foundAt,d.feather,d.featherAt,records);
    }
    public boolean has(int bit){return (flags&bit)!=0;}
    public boolean hasBell(){return has(HushData.BELL)&&!bell.equals(BlockPos.ZERO);}
    public boolean found(){return has(HushData.FOUND);}
    public boolean hasFeather(){return has(HushData.FEATHER)&&!feather.equals(BlockPos.ZERO);}
    /** Where the bell hangs: from the underside of what holds it, or in plain air when nothing does. */
    public Vec3 bellPoint(){return Vec3.atBottomCenterOf(bell).add(0,.62,0);}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
