package com.suiyi.veilborne;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record DreamPacket(boolean active,long seed,int age,int duration,int radius,BlockPos center,int flags,int variant,
                          BlockPos tree,int treeAt,List<BlockPos> path,int pathAt,BlockPos route,float routeYaw,int routeSide,int routeAt,
                          BlockPos glance,int glanceAt,int giftAt,BlockPos enclosure,int enclosureAt,float enclosureExit,int lightStep,int lightAt,int closedAt,
                          BlockPos twig,int twigAt,int records) implements CustomPacketPayload {
    public static final Type<DreamPacket> TYPE=new Type<>(Veilborne.id("dream"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DreamPacket> CODEC=StreamCodec.of(
        (b,p)->{b.writeBoolean(p.active);b.writeLong(p.seed);b.writeVarInt(p.age);b.writeVarInt(p.duration);b.writeVarInt(p.radius);b.writeBlockPos(p.center);
            b.writeVarInt(p.flags);b.writeVarInt(p.variant);b.writeBlockPos(p.tree);b.writeVarInt(p.treeAt);
            b.writeVarInt(p.path.size());for(BlockPos q:p.path)b.writeBlockPos(q);b.writeVarInt(p.pathAt);
            b.writeBlockPos(p.route);b.writeFloat(p.routeYaw);b.writeByte(p.routeSide);b.writeVarInt(p.routeAt);
            b.writeBlockPos(p.glance);b.writeVarInt(p.glanceAt);b.writeVarInt(p.giftAt);
            b.writeBlockPos(p.enclosure);b.writeVarInt(p.enclosureAt);b.writeFloat(p.enclosureExit);b.writeVarInt(p.lightStep);b.writeVarInt(p.lightAt);b.writeVarInt(p.closedAt);
            b.writeBlockPos(p.twig);b.writeVarInt(p.twigAt);b.writeVarInt(p.records);},
        b->{
            boolean active=b.readBoolean();long seed=b.readLong();int age=b.readVarInt(),duration=b.readVarInt(),radius=b.readVarInt();BlockPos center=b.readBlockPos();
            int flags=b.readVarInt(),variant=b.readVarInt();BlockPos tree=b.readBlockPos();int treeAt=b.readVarInt();
            int n=Math.min(DreamTimeline.PATH_POINTS,b.readVarInt());List<BlockPos> path=new ArrayList<>(n);for(int i=0;i<n;i++)path.add(b.readBlockPos());int pathAt=b.readVarInt();
            BlockPos route=b.readBlockPos();float yaw=b.readFloat();int side=b.readByte();int routeAt=b.readVarInt();
            BlockPos glance=b.readBlockPos();int glanceAt=b.readVarInt(),giftAt=b.readVarInt();
            BlockPos enclosure=b.readBlockPos();int enclosureAt=b.readVarInt();float exit=b.readFloat();int lightStep=b.readVarInt(),lightAt=b.readVarInt(),closedAt=b.readVarInt();
            BlockPos twig=b.readBlockPos();int twigAt=b.readVarInt(),records=b.readVarInt();
            return new DreamPacket(active,seed,age,duration,radius,center,flags,variant,tree,treeAt,List.copyOf(path),pathAt,route,yaw,side,routeAt,glance,glanceAt,giftAt,
                enclosure,enclosureAt,exit,lightStep,lightAt,closedAt,twig,twigAt,records);
        });
    public static DreamPacket from(DreamData d,int records){
        return new DreamPacket(d.active,d.seed,d.age,d.duration,d.radius,d.center,d.flags,d.variant,d.tree,d.treeAt,List.copyOf(d.path),d.pathAt,d.route,d.routeYaw,d.routeSide,d.routeAt,
            d.glance,d.glanceAt,d.giftAt,d.enclosure,d.enclosureAt,d.enclosureExit,d.lightStep,d.lightAt,d.closedAt,d.twig,d.twigAt,records);
    }
    public boolean has(int bit){return (flags&bit)!=0;}
    public boolean hasTree(){return has(DreamData.TREE)&&!tree.equals(BlockPos.ZERO);}
    public boolean hasRoute(){return has(DreamData.ROUTE)&&!route.equals(BlockPos.ZERO);}
    public boolean hasPath(){return has(DreamData.PATH)&&path.size()>1;}
    public DreamTimeline.Route routeValue(){return new DreamTimeline.Route(Vec3.atBottomCenterOf(route),routeYaw,routeSide);}
    public boolean enclosureOpen(){return has(DreamData.ENCLOSURE)&&!has(DreamData.ENCLOSURE_WON)&&!has(DreamData.ENCLOSURE_LOST);}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
