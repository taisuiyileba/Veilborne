package com.suiyi.veilborne.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * 镜天巡礼 particles. Motes: silver and celadon glints, additive. Launched straight up or down with no drift they are
 * twins: one rises from the ground as its mirror image comes down, and they meet in a spark. Flakes: bits of the
 * breaking mirror, tumbling down and catching the light as they turn.
 */
public final class MirrorParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final boolean flake,twin;
    private final float base,phase,spin;
    private MirrorParticle(ClientLevel l,double x,double y,double z,double dx,double dy,double dz,SpriteSet sprites,boolean flake){
        super(l,x,y,z,dx,dy,dz);
        this.sprites=sprites;this.flake=flake;
        xd=dx;yd=dy;zd=dz;
        twin=!flake&&dx==0&&dz==0&&Math.abs(dy)>.045;
        phase=random.nextFloat()*6.283f;spin=(random.nextFloat()-.5f)*.25f;
        if(flake){
            hasPhysics=true;friction=.97f;gravity=.035f;lifetime=160+random.nextInt(80);
            quadSize=.07f+random.nextFloat()*.07f;
            float t=random.nextFloat();setColor(.9f+t*.1f,.95f+t*.05f,1f);
            roll=oRoll=random.nextFloat()*6.28f;
            setSprite(sprites.get(0,3));
        }else if(twin){
            hasPhysics=false;friction=1;gravity=0;lifetime=30;
            quadSize=.06f+random.nextFloat()*.03f;setColor(.82f,.96f,1f);
            setSprite(sprites.get(random.nextInt(4),3));
        }else{
            hasPhysics=false;friction=.985f;gravity=-.001f;lifetime=40+random.nextInt(50);
            quadSize=.04f+random.nextFloat()*.05f;
            float r=random.nextFloat();
            if(r<.14f)setColor(1f,.86f,.58f);else if(r<.55f)setColor(.72f,.95f,.96f);else setColor(.92f,.96f,1f);
            setSprite(sprites.get(random.nextInt(4),3));
        }
        base=quadSize;alpha=0;
    }
    @Override public void tick(){
        oRoll=roll;
        super.tick();
        float t=(float)age/lifetime;
        if(flake){
            xd+=Math.sin(age*.13f+phase)*.0012;zd+=Math.cos(age*.11f+phase)*.0012;
            if(yd<-.06)yd=-.06;
            roll+=spin*(onGround?0:1);
            float turn=(float)(Math.sin(age*.21f+phase)*.5+.5);
            setSprite(sprites.get(Mth.clamp((int)(turn*3.99f),0,3),3));
            // A flash as the flake turns square to the light.
            float glint=(float)Math.pow(Math.max(0,Math.sin(age*.21f+phase)),12);
            alpha=Mth.clamp(Math.min(age/8f,(1-t)*5),0,1)*(.55f+.45f*glint);
            quadSize=base*(1+.4f*glint);
            if(onGround&&age<lifetime-10)age=lifetime-10;
        }else if(twin){
            alpha=(float)Math.min(1,t*3)*.8f;
            quadSize=t>.86f?base*(1+(t-.86f)*18):base;
            if(t>.86f)alpha=(1-t)*6;
        }else{
            alpha=(float)(Math.sin(t*Math.PI)*.85);
            quadSize=base*(.8f+.2f*(float)Math.sin(age*.2f+phase));
            setSprite(sprites.get((age/5+(int)(base*400))%4,3));
        }
    }
    @Override public int getLightColor(float t){return 0xf000f0;}
    @Override public ParticleRenderType getRenderType(){return flake?ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT:TideParticle.GLOW;}
    public record MoteProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType>{
        @Override public Particle createParticle(SimpleParticleType type,ClientLevel l,double x,double y,double z,double dx,double dy,double dz){return new MirrorParticle(l,x,y,z,dx,dy,dz,sprites,false);}
    }
    public record FlakeProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType>{
        @Override public Particle createParticle(SimpleParticleType type,ClientLevel l,double x,double y,double z,double dx,double dy,double dz){return new MirrorParticle(l,x,y,z,dx,dy,dz,sprites,true);}
    }
}
