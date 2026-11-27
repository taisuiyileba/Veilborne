package com.suiyi.veilborne.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * The small things of 苍翠梦潮: pollen (gold, additive, drifting), fireflies (honey, wandering, blinking slowly), dream fluff
 * (a seed of light rising out of the grass, swaying as it goes) and leaves (green, later gold, tumbling down).
 */
public final class DreamParticle extends TextureSheetParticle {
    enum Kind { POLLEN, FIREFLY, FLUFF, LEAF }
    private final Kind kind;
    private final SpriteSet sprites;
    private final float spin,base,phase;
    private DreamParticle(ClientLevel l,double x,double y,double z,double dx,double dy,double dz,SpriteSet sprites,Kind kind){
        super(l,x,y,z,dx,dy,dz);
        this.kind=kind;this.sprites=sprites;
        xd=dx;yd=dy;zd=dz;hasPhysics=false;
        phase=random.nextFloat()*6.28f;spin=(random.nextFloat()-.5f)*.22f;
        switch(kind){
            case POLLEN -> {friction=.96f;gravity=.002f;lifetime=40+random.nextInt(50);quadSize=.04f+random.nextFloat()*.05f;
                if(random.nextFloat()<.3f)setColor(.72f,1f,.62f);else setColor(1f,.82f,.42f);setSprite(sprites.get(random.nextInt(4),3));}
            case FIREFLY -> {friction=.9f;gravity=0;lifetime=90+random.nextInt(90);quadSize=.05f+random.nextFloat()*.03f;setColor(1f,.84f,.4f);setSprite(sprites.get(0,1));}
            case FLUFF -> {friction=.98f;gravity=-.0012f;lifetime=120+random.nextInt(90);quadSize=.08f+random.nextFloat()*.06f;setColor(.95f,1f,.86f);setSprite(sprites.get(random.nextInt(3),2));}
            case LEAF -> {friction=.95f;gravity=.012f;lifetime=140+random.nextInt(80);quadSize=.11f+random.nextFloat()*.07f;hasPhysics=true;
                float g=random.nextFloat();if(g<.55f)setColor(1f,.78f+.12f*g,.3f);else if(g<.8f)setColor(.94f,.55f,.26f);else setColor(.55f,.86f,.42f);
                roll=oRoll=random.nextFloat()*6.28f;setSprite(sprites.get(random.nextInt(4),3));}
        }
        base=quadSize;alpha=0;
    }
    @Override public void tick(){
        oRoll=roll;
        super.tick();
        float t=(float)age/lifetime;
        switch(kind){
            case POLLEN -> alpha=(float)Math.sin(t*Math.PI)*.9f;
            case FIREFLY -> {
                xd+=(random.nextFloat()-.5f)*.006f;yd+=(random.nextFloat()-.5f)*.004f;zd+=(random.nextFloat()-.5f)*.006f;
                // A slow blink: bright for a moment, then almost out.
                float blink=(float)Math.pow(Math.max(0,Math.sin(age*.12+phase)),3);
                alpha=Mth.clamp(Math.min(t*6,(1-t)*4),0,1)*(.15f+.85f*blink);
                quadSize=base*(.7f+.5f*blink);
            }
            case FLUFF -> {
                xd+=Math.sin(age*.08+phase)*.0012;zd+=Math.cos(age*.07+phase)*.0012;
                alpha=Mth.clamp(Math.min(t*5,(1-t)*3),0,1)*.9f;roll+=spin*.2f;
            }
            case LEAF -> {
                if(!onGround){roll+=spin;xd+=Math.sin(age*.15+phase)*.006;zd+=Math.cos(age*.13+phase)*.006;}
                alpha=Mth.clamp(Math.min(t*8,(1-t)*3),0,1);
            }
        }
    }
    @Override public int getLightColor(float t){return kind==Kind.LEAF?super.getLightColor(t):0xf000f0;}
    @Override public ParticleRenderType getRenderType(){return kind==Kind.LEAF?ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT:TideParticle.GLOW;}
    public record Pollen(SpriteSet sprites) implements ParticleProvider<SimpleParticleType>{
        @Override public Particle createParticle(SimpleParticleType type,ClientLevel l,double x,double y,double z,double dx,double dy,double dz){return new DreamParticle(l,x,y,z,dx,dy,dz,sprites,Kind.POLLEN);}
    }
    public record Firefly(SpriteSet sprites) implements ParticleProvider<SimpleParticleType>{
        @Override public Particle createParticle(SimpleParticleType type,ClientLevel l,double x,double y,double z,double dx,double dy,double dz){return new DreamParticle(l,x,y,z,dx,dy,dz,sprites,Kind.FIREFLY);}
    }
    public record Fluff(SpriteSet sprites) implements ParticleProvider<SimpleParticleType>{
        @Override public Particle createParticle(SimpleParticleType type,ClientLevel l,double x,double y,double z,double dx,double dy,double dz){return new DreamParticle(l,x,y,z,dx,dy,dz,sprites,Kind.FLUFF);}
    }
    public record Leaf(SpriteSet sprites) implements ParticleProvider<SimpleParticleType>{
        @Override public Particle createParticle(SimpleParticleType type,ClientLevel l,double x,double y,double z,double dx,double dy,double dz){return new DreamParticle(l,x,y,z,dx,dy,dz,sprites,Kind.LEAF);}
    }
}
