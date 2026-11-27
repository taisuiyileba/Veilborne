package com.suiyi.veilborne.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 月瓷花瓣: a translucent shell petal that tumbles down. Its sprite cycles face, three-quarter and edge-on
 * as it turns over. Near a lamp it circles in a small spiral; near the little moon it rises instead.
 * When it settles on open ground a flower shadow opens there.
 */
public final class PetalParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float phase,spin,flutter;
    private final int tint;
    private PetalParticle(ClientLevel l,double x,double y,double z,double dx,double dy,double dz,SpriteSet sprites){
        super(l,x,y,z,dx,dy,dz);
        this.sprites=sprites;
        xd=dx;yd=dy;zd=dz;
        hasPhysics=true;friction=.96f;gravity=.05f;
        lifetime=320+random.nextInt(160);
        quadSize=.075f+random.nextFloat()*.055f;
        phase=random.nextFloat()*6.283f;spin=(random.nextFloat()-.5f)*.14f;flutter=.12f+random.nextFloat()*.1f;
        tint=random.nextInt(5);
        float[][] colors={{.96f,.95f,1f},{.9f,.88f,1f},{.86f,.82f,.98f},{1f,.97f,.93f},{.93f,.93f,.99f}};
        setColor(colors[tint][0],colors[tint][1],colors[tint][2]);
        alpha=0;roll=oRoll=random.nextFloat()*6.283f;
        setSprite(sprites.get(0,5));
    }
    @Override public void tick(){
        oRoll=roll;
        float t=age;
        Vec3 moon=MoonClient.littleMoon(0);
        boolean rising=moon!=null&&MoonClient.littleMoonAlpha(0)>.2&&Math.hypot(x-moon.x,z-moon.z)<4.2&&y<moon.y+4;
        if(rising){
            // Petals near the fallen little moon circle it and drift upward.
            double ax=x-moon.x,az=z-moon.z,r=Math.max(.3,Math.hypot(ax,az));
            xd+=(-az/r*.006-ax/r*.0015);zd+=(ax/r*.006-az/r*.0015);yd+=.0011;gravity=0;
            if(y>moon.y+3.6)alpha*=.9f;
        }else{
            gravity=.05f;
            xd+=Math.sin(t*flutter+phase)*.0018;zd+=Math.cos(t*flutter*.8+phase)*.0015;
            for(BlockPos lamp:MoonClient.lamps){
                double ax=x-lamp.getX()-.5,az=z-lamp.getZ()-.5,r=Math.hypot(ax,az);
                if(r<3.4&&y>lamp.getY()-.5&&y<lamp.getY()+5){
                    // Orbit the flame: a tangential push, a gentle pull inward, and a slower fall.
                    double near=1-r/3.4,tr=Math.max(.35,r);
                    xd+=(-az/tr*.014-ax/tr*.004)*near;zd+=(ax/tr*.014-az/tr*.004)*near;
                    yd=yd*.9+.002*near;
                    break;
                }
            }
            Vec3 wind=MoonClient.wind(0);
            xd+=wind.x*.0006;zd+=wind.z*.0006;
        }
        super.tick();
        if(yd<-.045)yd=-.045;
        roll+=spin*(onGround?0:1);
        float turn=(float)(Math.sin(t*flutter*.9+phase)*.5+.5);
        setSprite(sprites.get(Mth.clamp((int)(turn*5.99f),0,5),5));
        float life=(float)age/lifetime;
        alpha=Mth.clamp(Math.min(age/12f,(1-life)*6),0,1)*.92f;
        if(!isAlive())return;
        BlockPos at=BlockPos.containing(x,y,z);
        if(level.getFluidState(at).is(net.minecraft.tags.FluidTags.WATER)){
            // Petals float on water: the flower opens on the surface instead of sinking.
            MoonClient.landed(new Vec3(x,at.getY()+level.getFluidState(at).getHeight(level,at)+.02,z));
            remove();
        }else if(onGround){
            MoonClient.landed(new Vec3(x,y+.02,z));
            remove();
        }
    }
    @Override public int getLightColor(float t){return 0xf000f0;}
    @Override public ParticleRenderType getRenderType(){return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;}
    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType>{
        @Override public Particle createParticle(SimpleParticleType type,ClientLevel l,double x,double y,double z,double dx,double dy,double dz){return new PetalParticle(l,x,y,z,dx,dy,dz,sprites);}
    }
}
