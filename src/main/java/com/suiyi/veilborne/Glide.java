package com.suiyi.veilborne;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Client-side gliding toward where the server last put an entity it moves itself. Vanilla's {@code Entity.lerpTo}
 * jumps straight to each update, and the jump lands between ticks, so the entity stands still and then hops: it reads
 * as stutter even at an update every tick. Give it {@link #to} from {@code lerpTo} and {@link #step} once per client
 * tick, and render interpolation smooths the rest.
 */
final class Glide {
    private double x,y,z;
    private float yaw;
    private int steps;

    void to(double x,double y,double z,float yaw,int steps){this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.steps=steps;}
    void step(Entity e){
        if(steps<=0)return;
        e.setPos(e.getX()+(x-e.getX())/steps,e.getY()+(y-e.getY())/steps,e.getZ()+(z-e.getZ())/steps);
        e.setYRot(e.getYRot()+Mth.wrapDegrees(yaw-e.getYRot())/steps);
        steps--;
    }
    /** Where it is heading (vanilla asks for this when an update carries only a rotation). */
    double x(Entity e){return steps>0?x:e.getX();}
    double y(Entity e){return steps>0?y:e.getY();}
    double z(Entity e){return steps>0?z:e.getZ();}
}
