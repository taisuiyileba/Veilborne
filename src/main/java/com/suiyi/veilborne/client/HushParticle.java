package com.suiyi.veilborne.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * 无声白昼 particles: graphite crumbs from something being rubbed out, grey flakes that drift down turning over, settle, and
 * are gone. Alpha-blended so they read as dust rather than light.
 */
public final class HushParticle extends TextureSheetParticle {
    private final float spin;
    private HushParticle(ClientLevel l,double x,double y,double z,SpriteSet sprites){
        super(l,x,y,z,0,0,0);
        xd=(random.nextDouble()-.5)*.012;yd=-.004-random.nextDouble()*.01;zd=(random.nextDouble()-.5)*.012;
        hasPhysics=true;friction=.96f;gravity=.012f;lifetime=40+random.nextInt(40);
        quadSize=.03f+random.nextFloat()*.035f;
        float g=.16f+random.nextFloat()*.14f;setColor(g,g,g*1.03f);
        roll=oRoll=random.nextFloat()*6.28f;spin=(random.nextFloat()-.5f)*.3f;
        setSprite(sprites.get(random.nextInt(4),3));
        alpha=0;
    }
    @Override public void tick(){
        oRoll=roll;
        super.tick();
        float t=(float)age/lifetime;
        if(!onGround)roll+=spin;
        alpha=Mth.clamp(Math.min(age/5f,(1-t)*3),0,1)*.85f;
    }
    @Override public ParticleRenderType getRenderType(){return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;}
    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType>{
        @Override public Particle createParticle(SimpleParticleType type,ClientLevel l,double x,double y,double z,double dx,double dy,double dz){return new HushParticle(l,x,y,z,sprites);}
    }
}
