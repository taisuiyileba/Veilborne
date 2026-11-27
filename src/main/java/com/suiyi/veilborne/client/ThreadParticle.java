package com.suiyi.veilborne.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * 绯线缝世 particles. Fibres: short curls of scarlet thread and cloth dust, drifting along the velocity they are given (the
 * pull toward the seam), turning slowly, alpha-blended so they read as things rather than light. Embers: gold-red sparks
 * that rise from where the needle went into the land and from the burning edges of what it touched, additive.
 */
public final class ThreadParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final boolean ember;
    private final float base,phase,spin;
    private ThreadParticle(ClientLevel l,double x,double y,double z,double dx,double dy,double dz,SpriteSet sprites,boolean ember){
        super(l,x,y,z,dx,dy,dz);
        this.sprites=sprites;this.ember=ember;
        xd=dx;yd=dy;zd=dz;
        phase=random.nextFloat()*6.283f;spin=(random.nextFloat()-.5f)*.12f;
        if(ember){
            hasPhysics=false;friction=.96f;gravity=-.004f;lifetime=24+random.nextInt(30);
            quadSize=.035f+random.nextFloat()*.05f;
            float t=random.nextFloat();setColor(1f,.45f+t*.4f,.2f+t*.25f);
            setSprite(sprites.get(random.nextInt(4),3));
        }else{
            hasPhysics=true;friction=.99f;gravity=.0006f;lifetime=90+random.nextInt(90);
            quadSize=.045f+random.nextFloat()*.05f;
            float r=random.nextFloat();
            if(r<.12f)setColor(1f,.82f,.52f);else if(r<.7f)setColor(.86f,.14f+random.nextFloat()*.08f,.16f);else setColor(.62f,.2f,.22f);
            roll=oRoll=random.nextFloat()*6.28f;
            setSprite(sprites.get(random.nextInt(4),3));
        }
        base=quadSize;alpha=0;
    }
    @Override public void tick(){
        oRoll=roll;
        super.tick();
        float t=(float)age/lifetime;
        if(ember){
            alpha=(float)Math.pow(1-t,1.3)*(.75f+.25f*(float)Math.sin(age*.9f+phase));
            quadSize=base*(1-.5f*t);
        }else{
            xd+=Math.sin(age*.09f+phase)*.0008;zd+=Math.cos(age*.08f+phase)*.0008;
            roll+=spin*(onGround?0:1);
            alpha=Mth.clamp(Math.min(age/12f,(1-t)*4),0,1)*.8f;
            if(onGround&&age<lifetime-12)age=lifetime-12;
        }
    }
    @Override public int getLightColor(float t){return ember?0xf000f0:super.getLightColor(t)|0xa0;}
    @Override public ParticleRenderType getRenderType(){return ember?TideParticle.GLOW:ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;}
    public record FiberProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType>{
        @Override public Particle createParticle(SimpleParticleType type,ClientLevel l,double x,double y,double z,double dx,double dy,double dz){return new ThreadParticle(l,x,y,z,dx,dy,dz,sprites,false);}
    }
    public record EmberProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType>{
        @Override public Particle createParticle(SimpleParticleType type,ClientLevel l,double x,double y,double z,double dx,double dy,double dz){return new ThreadParticle(l,x,y,z,dx,dy,dz,sprites,true);}
    }
}
