package com.suiyi.veilborne;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * A moment of what the weathers leave behind, for the client to show: the skiff's voyage beginning (VOYAGE, value 1) and
 * ending (value 0), a stitched lamp's thread drawing someone in (PULL, value = ticks to go), a sound swallowed by the silent
 * daylight that something heard (LOUD), the tower's foundation found under the ground (VAULT), and the cracked mirror's
 * heart showing what is hidden nearby (REVEAL, the places in {@code spots}, value = ticks it lasts).
 */
public record VeilCue(int kind,double x,double y,double z,int value,List<BlockPos> spots) implements CustomPacketPayload {
    public static final int VOYAGE=0, PULL=1, LOUD=2, VAULT=3, REVEAL=4;
    public static final int MAX_SPOTS=96;
    public static final Type<VeilCue> TYPE=new Type<>(Veilborne.id("veil_cue"));
    public static final StreamCodec<RegistryFriendlyByteBuf,VeilCue> CODEC=StreamCodec.of(
        (b,p)->{b.writeVarInt(p.kind);b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);b.writeVarInt(p.value);
            b.writeVarInt(Math.min(MAX_SPOTS,p.spots.size()));for(int i=0;i<Math.min(MAX_SPOTS,p.spots.size());i++)b.writeBlockPos(p.spots.get(i));},
        b->{int kind=b.readVarInt();double x=b.readDouble(),y=b.readDouble(),z=b.readDouble();int value=b.readVarInt();
            int n=Math.min(MAX_SPOTS,b.readVarInt());List<BlockPos> s=new ArrayList<>(n);for(int i=0;i<n;i++)s.add(b.readBlockPos());
            return new VeilCue(kind,x,y,z,value,List.copyOf(s));});
    public VeilCue(int kind,Vec3 at,int value){this(kind,at.x,at.y,at.z,value,List.of());}
    public Vec3 at(){return new Vec3(x,y,z);}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
