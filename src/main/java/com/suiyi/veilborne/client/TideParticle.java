package com.suiyi.veilborne.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.particles.SimpleParticleType;

/** Star motes and rising droplets. Both are additive, so overlapping lights brighten instead of greying. */
public final class TideParticle extends TextureSheetParticle {
    /** Additive, depth-tested, no depth writes. TideRenderer restores the default blend after particles. */
    static final ParticleRenderType GLOW=new ParticleRenderType(){
        @Override public BufferBuilder begin(Tesselator tesselator,net.minecraft.client.renderer.texture.TextureManager textures){
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0,TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE);
            return tesselator.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.PARTICLE);
        }
        @Override public String toString(){return "VEILBORNE_GLOW";}
    };
    private final float initialSize;
    private final boolean droplet;
    private final SpriteSet sprites;
    private TideParticle(ClientLevel l,double x,double y,double z,double dx,double dy,double dz,SpriteSet sprites,boolean droplet){
        super(l,x,y,z,dx,dy,dz);
        this.sprites=sprites;this.droplet=droplet;
        xd=dx;yd=dy;zd=dz;hasPhysics=false;
        if(droplet){
            friction=.99f;gravity=-.004f;lifetime=34+random.nextInt(30);
            quadSize=.06f+random.nextFloat()*.04f;setColor(.62f,.93f,1f);
            setSprite(sprites.get(0,1));
        }else{
            friction=.995f;gravity=-.0015f;lifetime=40+random.nextInt(65);
            quadSize=.045f+random.nextFloat()*.06f;
            if(random.nextFloat()<.16f)setColor(1,.8f,.46f);else setColor(.36f,.8f,1);
            setSprite(sprites.get(random.nextInt(4),3));
        }
        initialSize=quadSize;alpha=0;
    }
    @Override public void tick(){
        super.tick();
        float t=(float)age/lifetime;
        if(droplet){
            alpha=(float)(Math.sin(Math.min(1,t*1.4)*Math.PI)*.85);
            setSprite(sprites.get(age/6%2,1));
        }else{
            alpha=(float)(Math.sin(t*Math.PI)*.85);
            quadSize=initialSize*(.8f+.2f*(float)Math.sin(age*.17));
            // Twinkle: cycle through the four sprite phases at a per-particle rate.
            setSprite(sprites.get((age/5+(int)(initialSize*400))%4,3));
        }
    }
    @Override public int getLightColor(float t){return 0xf000f0;}
    @Override public ParticleRenderType getRenderType(){return GLOW;}
    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType>{
        @Override public Particle createParticle(SimpleParticleType type,ClientLevel l,double x,double y,double z,double dx,double dy,double dz){return new TideParticle(l,x,y,z,dx,dy,dz,sprites,false);}
    }
    /** 月尘: the same soft light in the bloom's colours, silver and lilac with the odd warm spark. */
    public record MoonProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType>{
        @Override public Particle createParticle(SimpleParticleType type,ClientLevel l,double x,double y,double z,double dx,double dy,double dz){
            TideParticle p=new TideParticle(l,x,y,z,dx,dy,dz,sprites,false);
            float r=l.random.nextFloat();
            if(r<.18f)p.setColor(1f,.85f,.58f);else p.setColor(.8f+r*.15f,.76f+r*.12f,1f);
            return p;
        }
    }
    public record DropletProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType>{
        @Override public Particle createParticle(SimpleParticleType type,ClientLevel l,double x,double y,double z,double dx,double dy,double dz){return new TideParticle(l,x,y,z,dx,dy,dz,sprites,true);}
    }
}
