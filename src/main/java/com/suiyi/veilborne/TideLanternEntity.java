package com.suiyi.veilborne;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TideLanternEntity extends BlockEntity {
    public TideLanternEntity(BlockPos p,BlockState s){super(Veilborne.LANTERN_ENTITY.get(),p,s);}
    public static void tick(Level l,BlockPos p,BlockState s,TideLanternEntity e){
        if(l.getGameTime()%5!=0)return;
        double a=l.random.nextDouble()*Math.PI*2,r=l.random.nextDouble()*.24;
        l.addParticle(Veilborne.MOTE.get(),p.getX()+.5+Math.cos(a)*r,p.getY()+.25,p.getZ()+.5+Math.sin(a)*r,0,.008,0);
    }
}
