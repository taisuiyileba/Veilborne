package com.suiyi.veilborne;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * A moment of 苍翠梦潮 the client should show where it happened: a dream seed coming down on the soil and sprouting (BLOOM,
 * value 1 when it landed in one of the deer's hoof rings), a light touched inside the vine enclosure (CHIME, value = which
 * one), the lost school gathering into one golden fish (MERGE), or a dream tree grown from a seed at home (SPROUT).
 */
public record DreamCue(int kind,double x,double y,double z,int value) implements CustomPacketPayload {
    public static final int BLOOM=0, CHIME=1, MERGE=2, SPROUT=3;
    public static final Type<DreamCue> TYPE=new Type<>(Veilborne.id("dream_cue"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DreamCue> CODEC=StreamCodec.of(
        (b,p)->{b.writeVarInt(p.kind);b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);b.writeVarInt(p.value);},
        b->new DreamCue(b.readVarInt(),b.readDouble(),b.readDouble(),b.readDouble(),b.readVarInt()));
    public DreamCue(int kind,Vec3 at,int value){this(kind,at.x,at.y,at.z,value);}
    public Vec3 at(){return new Vec3(x,y,z);}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
