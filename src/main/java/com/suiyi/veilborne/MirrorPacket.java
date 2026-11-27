package com.suiyi.veilborne;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record MirrorPacket(boolean active,long seed,int age,int duration,int radius,BlockPos center,int flags,int variant,
                           BlockPos door,int doorFace,int doorAt,int revealedAt,BlockPos pilgrimOrigin,float pilgrimYaw,int pilgrimAt,
                           BlockPos shard0,BlockPos shard1,int warn0,int warn1,BlockPos keepsake,int keepsakeAt,int records,boolean rewarded) implements CustomPacketPayload {
    public static final Type<MirrorPacket> TYPE=new Type<>(Veilborne.id("mirror"));
    public static final StreamCodec<RegistryFriendlyByteBuf,MirrorPacket> CODEC=StreamCodec.of(
        (b,p)->{b.writeBoolean(p.active);b.writeLong(p.seed);b.writeVarInt(p.age);b.writeVarInt(p.duration);b.writeVarInt(p.radius);b.writeBlockPos(p.center);
            b.writeVarInt(p.flags);b.writeVarInt(p.variant);b.writeBlockPos(p.door);b.writeVarInt(p.doorFace);b.writeVarInt(p.doorAt);b.writeVarInt(p.revealedAt);
            b.writeBlockPos(p.pilgrimOrigin);b.writeFloat(p.pilgrimYaw);b.writeVarInt(p.pilgrimAt);
            b.writeBlockPos(p.shard0);b.writeBlockPos(p.shard1);b.writeVarInt(p.warn0);b.writeVarInt(p.warn1);b.writeBlockPos(p.keepsake);b.writeVarInt(p.keepsakeAt);b.writeVarInt(p.records);b.writeBoolean(p.rewarded);},
        b->new MirrorPacket(b.readBoolean(),b.readLong(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBlockPos(),b.readVarInt(),b.readVarInt(),
            b.readBlockPos(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBlockPos(),b.readFloat(),b.readVarInt(),
            b.readBlockPos(),b.readBlockPos(),b.readVarInt(),b.readVarInt(),b.readBlockPos(),b.readVarInt(),b.readVarInt(),b.readBoolean()));
    public static MirrorPacket from(MirrorData d,int records){return from(d,records,false);}
    public static MirrorPacket from(MirrorData d,int records,boolean rewarded){
        return new MirrorPacket(d.active,d.seed,d.age,d.duration,d.radius,d.center,d.flags,d.variant,d.door,d.doorFace,d.doorAt,d.revealedAt,
            d.pilgrimOrigin,d.pilgrimYaw,d.pilgrimAt,d.shards[0],d.shards[1],d.shardWarn[0],d.shardWarn[1],d.keepsake,d.keepsakeAt,records,rewarded);
    }
    public boolean has(int bit){return (flags&bit)!=0;}
    public boolean hasDoor(){return !door.equals(BlockPos.ZERO);}
    public BlockPos shard(int i){return i==0?shard0:shard1;}
    public int warn(int i){return i==0?warn0:warn1;}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
