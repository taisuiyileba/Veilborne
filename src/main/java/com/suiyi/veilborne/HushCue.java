package com.suiyi.veilborne;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * A moment of 无声白昼 the client should show where it happened: someone raising a light (FLASH, value = reach), a knock on a
 * door (KNOCK, value = the side it came from), a hollow bell played back (ECHO, value = the weather it holds), or the black
 * sun answering (HEARD).
 */
public record HushCue(int kind,double x,double y,double z,int value) implements CustomPacketPayload {
    public static final int FLASH=0, KNOCK=1, ECHO=2, HEARD=3;
    public static final Type<HushCue> TYPE=new Type<>(Veilborne.id("hush_cue"));
    public static final StreamCodec<RegistryFriendlyByteBuf,HushCue> CODEC=StreamCodec.of(
        (b,p)->{b.writeVarInt(p.kind);b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);b.writeVarInt(p.value);},
        b->new HushCue(b.readVarInt(),b.readDouble(),b.readDouble(),b.readDouble(),b.readVarInt()));
    public HushCue(int kind,Vec3 at,int value){this(kind,at.x,at.y,at.z,value);}
    public Vec3 at(){return new Vec3(x,y,z);}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
