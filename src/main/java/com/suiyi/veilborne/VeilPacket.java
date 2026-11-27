package com.suiyi.veilborne;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * One player's share of what the veil weathers leave: the last and best completeness of each weather and which deeds the
 * last one counted, the deeds of the event under way (liveWeather -1 when none), the places remembered for their journal,
 * and the star-tide pools still open in the Overworld (the same for everyone).
 */
public record VeilPacket(int[] last,int[] best,int[] lastDeeds,int liveWeather,int liveDeeds,List<VeilGrade.Site> sites,List<TideData.Pool> pools) implements CustomPacketPayload {
    public static final Type<VeilPacket> TYPE=new Type<>(Veilborne.id("veil"));
    public static final StreamCodec<RegistryFriendlyByteBuf,VeilPacket> CODEC=StreamCodec.of(
        (b,p)->{
            for(int[] a:new int[][]{p.last,p.best,p.lastDeeds})for(int i=0;i<VeilGrade.NAMES.length;i++)b.writeVarInt(i<a.length?a[i]:0);
            b.writeVarInt(p.liveWeather+1);b.writeVarInt(p.liveDeeds);
            b.writeVarInt(p.sites.size());for(var s:p.sites){b.writeVarInt(s.kind());b.writeBlockPos(s.a());b.writeBlockPos(s.b());b.writeLong(s.until());}
            b.writeVarInt(p.pools.size());for(var q:p.pools){b.writeBlockPos(q.at());b.writeLong(q.until());}
        },
        b->{
            int n=VeilGrade.NAMES.length;int[][] a=new int[3][n];
            for(int k=0;k<3;k++)for(int i=0;i<n;i++)a[k][i]=b.readVarInt();
            int live=b.readVarInt()-1,deeds=b.readVarInt();
            int s=Math.min(VeilGrade.SITES,b.readVarInt());List<VeilGrade.Site> sites=new ArrayList<>(s);
            for(int i=0;i<s;i++)sites.add(new VeilGrade.Site(b.readVarInt(),b.readBlockPos(),b.readBlockPos(),b.readLong()));
            int q=Math.min(TideData.POOLS,b.readVarInt());List<TideData.Pool> pools=new ArrayList<>(q);
            for(int i=0;i<q;i++)pools.add(new TideData.Pool(b.readBlockPos(),b.readLong()));
            return new VeilPacket(a[0],a[1],a[2],live,deeds,List.copyOf(sites),List.copyOf(pools));
        });
    @Override public boolean equals(Object o){
        return o instanceof VeilPacket v&&java.util.Arrays.equals(last,v.last)&&java.util.Arrays.equals(best,v.best)&&java.util.Arrays.equals(lastDeeds,v.lastDeeds)
            &&liveWeather==v.liveWeather&&liveDeeds==v.liveDeeds&&sites.equals(v.sites)&&pools.equals(v.pools);
    }
    @Override public int hashCode(){return java.util.Objects.hash(java.util.Arrays.hashCode(last),java.util.Arrays.hashCode(best),liveWeather,liveDeeds,sites,pools);}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
