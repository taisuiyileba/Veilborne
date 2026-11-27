package com.suiyi.veilborne.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Untextured, softly shaded geometry, batched into a single pass per layer. */
final class TideMesh {
    final VertexConsumer buffer;final Matrix4f matrix;
    TideMesh(VertexConsumer b,Matrix4f m){buffer=b;matrix=m;}
    TideMesh transform(Matrix4f m){return new TideMesh(buffer,new Matrix4f(matrix).mul(m));}
    void vertex(Vec3 p,int color,double alpha){
        buffer.addVertex(matrix,(float)p.x,(float)p.y,(float)p.z).setColor((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,(float)Math.max(0,Math.min(1,alpha)));
    }
    void tri(Vec3 a,Vec3 b,Vec3 c,int color,double alpha){vertex(a,color,alpha);vertex(b,color,alpha);vertex(c,color,alpha);}
    void quad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,double alpha){tri(a,b,c,color,alpha);tri(a,c,d,color,alpha);}
    void line(Vec3 a,Vec3 b,double width,int color,double alpha){
        Vec3 n=b.subtract(a).cross(new Vec3(.3,1,.2)).normalize().scale(width);
        quad(a.add(n),b.add(n),b.subtract(n),a.subtract(n),color,alpha);
    }
    void star(Vec3 p,double radius,int color,double alpha){
        Vec3 n=p.normalize(),right=new Vec3(n.z,0,-n.x).normalize().scale(radius),up=n.cross(right).normalize().scale(radius);
        quad(p.add(up),p.add(right),p.subtract(up),p.subtract(right),color,alpha);
        line(p.subtract(up.scale(2.7)),p.add(up.scale(2.7)),radius*.08,color,alpha*.6);
    }
    void ring(Vec3 p,double radius,double width,int color,double alpha,double phase){
        int n=64;
        for(int i=0;i<n;i++){
            double a=i*Math.PI*2/n,b=(i+1)*Math.PI*2/n;
            double r=radius+Math.sin(a*5+phase)*.08;
            quad(p.add(Math.cos(a)*r,0,Math.sin(a)*r),p.add(Math.cos(b)*r,0,Math.sin(b)*r),p.add(Math.cos(b)*(r+width),0,Math.sin(b)*(r+width)),p.add(Math.cos(a)*(r+width),0,Math.sin(a)*(r+width)),color,alpha);
        }
    }
    void box(double x,double y,double z,double sx,double sy,double sz,int color,double alpha){
        Vec3 a=new Vec3(x,y,z),b=a.add(sx,0,0),c=a.add(sx,sy,0),d=a.add(0,sy,0);
        Vec3 aa=a.add(0,0,sz),bb=b.add(0,0,sz),cc=c.add(0,0,sz),dd=d.add(0,0,sz);
        quad(a,b,c,d,color,alpha);quad(bb,aa,dd,cc,shade(color,.78),alpha);
        quad(a,aa,bb,b,shade(color,.55),alpha);quad(d,c,cc,dd,shade(color,1.15),alpha);
        quad(a,d,dd,aa,shade(color,.68),alpha);quad(b,bb,cc,c,shade(color,.9),alpha);
    }
    static int shade(int c,double f){return Math.min(255,(int)((c>>16&255)*f))<<16|Math.min(255,(int)((c>>8&255)*f))<<8|Math.min(255,(int)((c&255)*f));}
}
