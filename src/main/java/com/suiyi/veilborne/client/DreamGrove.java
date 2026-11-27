package com.suiyi.veilborne.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.suiyi.veilborne.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The inverted forest's hanging trees, in the world: each a crown of leaf masses hanging from the canopy like a chandelier,
 * with its trunk and branches inside (seen once the leaves are gone), perhaps a little house hanging upside down from its
 * lowest branch with lamps in its windows, and its aerial roots: most hang free and sway, ending in mid air; the longest go
 * all the way down and grip the hilltops with rootlets spread over the ground. Later the deer's road is let down: a line of
 * roots on either side of its way. All of it is built once, relative to the event's centre, into two buffers (the road's
 * only once the road is known) and drawn with dream_grove, which grows the roots down and draws them back, thins the
 * leaves and lights the sap. It is rebuilt when land that was not loaded comes in, so roots reach the ground they meet.
 * The dream tree near you is built here too, in its own small buffer, each tick while it grows.
 */
final class DreamGrove {
    static ShaderInstance shader;
    private static VertexBuffer grove,road,tree;
    private static long builtFor=Long.MIN_VALUE;
    private static int builtAt=-9999,unresolved,roadFor=-1,treeBuiltAt=-1;
    private static double treeBuiltGrowth=-1;
    /** Where roots grip the land (relative to the centre), for the light pass; and whether each one is on the road. */
    record Grip(Vec3 at,double delay,boolean road){}
    static final List<Grip> grips=new ArrayList<>();
    /** Growing tips to glow over the sky: from, to, delay (relative to the centre), and whether each is on the road. */
    record Tip(Vec3 top,Vec3 end,double delay,boolean road,boolean reaches){}
    static final List<Tip> tips=new ArrayList<>();
    static final int BARK=0,LEAF=1,BRANCH=2,HOUSE=3,WINDOW=4,ROOTLET=5,ROAD=6,WOOD=7;

    static void invalidate(){builtFor=Long.MIN_VALUE;roadFor=-1;treeBuiltAt=-1;grips.clear();tips.clear();}

    // ------------------------------------------------------------------ building
    /** A vertex writer: kind, the root's delay, a hash and whether it hangs free go in the colour. */
    private static final class Out {
        final BufferBuilder b;int kind;double delay,key,free;
        Out(BufferBuilder b){this.b=b;}
        Out set(int kind,double delay,double key,double free){this.kind=kind;this.delay=delay;this.key=key;this.free=free;return this;}
        void v(double x,double y,double z,double u,double w,double nx,double ny,double nz){
            b.addVertex((float)x,(float)y,(float)z).setUv((float)u,(float)w).setColor((kind+.5f)/8f,(float)delay,(float)key,(float)free).setNormal((float)nx,(float)ny,(float)nz);
        }
    }
    private interface Ground { double y(double x,double z); }
    private static Ground ground(Level level,double y0,Vec3 center){
        return (x,z)->{
            int wx=Mth.floor(center.x+x),wz=Mth.floor(center.z+z);
            if(!level.hasChunk(wx>>4,wz>>4)){unresolved++;return y0-center.y;}
            return Surface.top(level,wx,wz)-center.y;
        };
    }
    /** A tube along points (parallel-transported frames), u running 0..1 along its length. */
    static void tube(Out o,List<Vec3> pts,double[] radius,int sides){
        int n=pts.size();
        if(n<2)return;
        double[] len=new double[n];
        for(int i=1;i<n;i++)len[i]=len[i-1]+pts.get(i).distanceTo(pts.get(i-1));
        double total=Math.max(1e-6,len[n-1]);
        Vec3 t0=pts.get(1).subtract(pts.get(0)).normalize();
        Vec3 ref=Math.abs(t0.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0);
        Vec3 nrm=t0.cross(ref).normalize(),bi=t0.cross(nrm).normalize();
        Vec3[][] ring=new Vec3[n][sides+1];Vec3[][] nor=new Vec3[n][sides+1];
        Vec3 prevT=t0;
        for(int i=0;i<n;i++){
            Vec3 t=(i==n-1?pts.get(i).subtract(pts.get(i-1)):pts.get(i+1).subtract(pts.get(Math.max(0,i-1)))).normalize();
            // Carry the frame along without twisting it.
            Vec3 axis=prevT.cross(t);
            if(axis.lengthSqr()>1e-10){
                double ang=Math.acos(Mth.clamp(prevT.dot(t),-1,1));
                nrm=rotate(nrm,axis.normalize(),ang);bi=t.cross(nrm).normalize();nrm=bi.cross(t).normalize();
            }
            prevT=t;
            for(int k=0;k<=sides;k++){
                double a=k*Math.PI*2/sides;
                Vec3 d=nrm.scale(Math.cos(a)).add(bi.scale(Math.sin(a)));
                ring[i][k]=pts.get(i).add(d.scale(radius[i]));nor[i][k]=d;
            }
        }
        for(int i=0;i+1<n;i++)for(int k=0;k<sides;k++){
            double u0=len[i]/total,u1=len[i+1]/total,v0=k/(double)sides,v1=(k+1)/(double)sides;
            put(o,ring[i][k],nor[i][k],u0,v0);put(o,ring[i+1][k],nor[i+1][k],u1,v0);
            put(o,ring[i+1][k+1],nor[i+1][k+1],u1,v1);put(o,ring[i][k+1],nor[i][k+1],u0,v1);
        }
    }
    private static void put(Out o,Vec3 p,Vec3 n,double u,double v){o.v(p.x,p.y,p.z,u,v,n.x,n.y,n.z);}
    static Vec3 rotate(Vec3 v,Vec3 axis,double a){
        double c=Math.cos(a),s=Math.sin(a);
        return v.scale(c).add(axis.cross(v).scale(s)).add(axis.scale(axis.dot(v)*(1-c)));
    }
    private static double wob(double x,double y,double z){return Math.sin(x*1.7+y*.9)*.5+Math.sin(y*2.3+z*1.3+1.1)*.3+Math.sin(z*3.1+x*2.7+2.3)*.2;}
    /** A lumpy mass of leaves. */
    static void lump(Out o,Vec3 c,double rx,double ry,double rz,int rings,int segs,double key){
        Vec3[][] p=new Vec3[rings+1][segs+1],n=new Vec3[rings+1][segs+1];
        for(int i=0;i<=rings;i++){
            double ph=Math.PI*i/rings-Math.PI/2;
            for(int k=0;k<=segs;k++){
                double th=Math.PI*2*k/segs;
                double dx=Math.cos(ph)*Math.cos(th),dy=Math.sin(ph),dz=Math.cos(ph)*Math.sin(th);
                double w=1+.2*wob(dx*2.2+key*7,dy*2.2+key*3,dz*2.2)+.08*wob(dx*6+key,dy*6,dz*6+key);
                if(dy<0)w*=1+.12*dy*dy;
                p[i][k]=c.add(dx*rx*w,dy*ry*w,dz*rz*w);
                n[i][k]=new Vec3(dx/rx,dy/ry,dz/rz).normalize();
            }
        }
        for(int i=0;i<rings;i++)for(int k=0;k<segs;k++){
            put(o,p[i][k],n[i][k],0,0);put(o,p[i+1][k],n[i+1][k],0,0);put(o,p[i+1][k+1],n[i+1][k+1],0,0);put(o,p[i][k+1],n[i][k+1],0,0);
        }
    }
    private static void box(Out o,Vec3 c,double hx,double hy,double hz,double yaw){
        Vec3 ax=new Vec3(Math.cos(yaw),0,Math.sin(yaw)),az=new Vec3(-Math.sin(yaw),0,Math.cos(yaw)),ay=new Vec3(0,1,0);
        Vec3[] dirs={ax,ax.scale(-1),ay,ay.scale(-1),az,az.scale(-1)};
        double[] h={hx,hx,hy,hy,hz,hz};
        for(int f=0;f<6;f++){
            Vec3 n=dirs[f],u=f<2?az:ax,v=f<2||f>=4?ay:az;
            double hu=f<2?hz:hx,hv=f<2||f>=4?hy:hz;
            Vec3 m=c.add(n.scale(h[f]));
            Vec3 a=m.subtract(u.scale(hu)).subtract(v.scale(hv)),b=m.add(u.scale(hu)).subtract(v.scale(hv)),cc=m.add(u.scale(hu)).add(v.scale(hv)),d=m.subtract(u.scale(hu)).add(v.scale(hv));
            put(o,a,n,0,0);put(o,b,n,0,0);put(o,cc,n,0,0);put(o,d,n,0,0);
        }
    }
    /** A little house hanging upside down from a branch: its floor against the wood, its roof pointing down, lamps lit. */
    private static void house(Out o,Vec3 top,double size,double yaw,double key){
        double w=2.2*size,h=1.8*size;
        Vec3 c=top.add(0,-h,0);
        o.set(HOUSE,0,key,0);
        box(o,c,w,h,w*.8,yaw);
        // The roof, inverted: a pyramid pointing at the ground, with wide eaves.
        Vec3 ax=new Vec3(Math.cos(yaw),0,Math.sin(yaw)),az=new Vec3(-Math.sin(yaw),0,Math.cos(yaw));
        Vec3 eave=c.add(0,-h,0),apex=eave.add(0,-2.6*size,0);
        Vec3[] k={eave.add(ax.scale(w*1.35)).add(az.scale(w*1.15)),eave.add(ax.scale(-w*1.35)).add(az.scale(w*1.15)),eave.add(ax.scale(-w*1.35)).add(az.scale(-w*1.15)),eave.add(ax.scale(w*1.35)).add(az.scale(-w*1.15))};
        for(int i=0;i<4;i++){Vec3 a=k[i],b=k[(i+1)%4];Vec3 n=b.subtract(a).cross(apex.subtract(a)).normalize();if(n.y>0)n=n.scale(-1);put(o,a,n,0,0);put(o,b,n,0,0);put(o,apex,n,0,0);put(o,apex,n,0,0);}
        for(int i=0;i<4;i++){Vec3 a=k[i],b=k[(i+1)%4];put(o,a,new Vec3(0,1,0),0,0);put(o,b,new Vec3(0,1,0),0,0);put(o,eave,new Vec3(0,1,0),0,0);put(o,eave,new Vec3(0,1,0),0,0);}
        // Lit windows on two sides.
        o.set(WINDOW,0,key,0);
        for(int s=-1;s<=1;s+=2)for(int i=-1;i<=1;i+=2){
            Vec3 m=c.add(az.scale(s*(w*.8+.05))).add(ax.scale(i*w*.45));
            Vec3 n=az.scale(s);
            double hw=.38*size,hh=.55*size;
            put(o,m.subtract(ax.scale(hw)).add(0,-hh,0),n,0,0);put(o,m.add(ax.scale(hw)).add(0,-hh,0),n,0,0);put(o,m.add(ax.scale(hw)).add(0,hh,0),n,0,0);put(o,m.subtract(ax.scale(hw)).add(0,hh,0),n,0,0);
        }
    }
    /** A root from where it leaves its crown to where it ends: down to the land, or hanging free in mid air. */
    private static List<Vec3> rootPath(Vec3 top,Vec3 end,double key,int n){
        List<Vec3> pts=new ArrayList<>();
        double L=top.distanceTo(end);
        Vec3 c1=top.add((Math.sin(key*11)*.12)*L,-L*.34,(Math.cos(key*13)*.12)*L),c2=end.add((Math.sin(key*7)*.06)*L,L*.3,(Math.cos(key*5)*.06)*L);
        double sa=key*37,sx=Math.cos(sa),sz=Math.sin(sa);
        for(int i=0;i<=n;i++){
            double t=i/(double)n,a=1-t;
            Vec3 p=top.scale(a*a*a).add(c1.scale(3*a*a*t)).add(c2.scale(3*a*t*t)).add(end.scale(t*t*t));
            double env=Math.sin(Math.PI*t);
            double sweep=L*(.055*Math.sin(Math.PI*t*1.6+key*9)+.025*Math.sin(Math.PI*t*3.8+key*17))*env;
            double side=L*.035*Math.sin(Math.PI*t*2.3+key*23)*env;
            double wig=Math.sin(t*L/9+key*20)*1.1*env;
            pts.add(p.add(sx*sweep-sz*side+wig,0,sz*sweep+sx*side+Math.cos(t*L/11+key*30)*1.1*env));
        }
        return pts;
    }
    /** A root made of strands twisting round its line: each strand a tube of its own, together as thick as the root. */
    static void braid(Out o,List<Vec3> pts,double[] radius,int sides,int strands,double turns,double key){
        int n=pts.size();
        if(n<2)return;
        double[] len=new double[n];
        for(int i=1;i<n;i++)len[i]=len[i-1]+pts.get(i).distanceTo(pts.get(i-1));
        double total=Math.max(1e-6,len[n-1]);
        Vec3 t0=pts.get(1).subtract(pts.get(0)).normalize();
        Vec3 ref=Math.abs(t0.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0);
        Vec3 nrm=t0.cross(ref).normalize(),bi=t0.cross(nrm).normalize(),prevT=t0;
        Vec3[] N=new Vec3[n],B=new Vec3[n];
        for(int i=0;i<n;i++){
            Vec3 t=(i==n-1?pts.get(i).subtract(pts.get(i-1)):pts.get(i+1).subtract(pts.get(Math.max(0,i-1)))).normalize();
            Vec3 axis=prevT.cross(t);
            if(axis.lengthSqr()>1e-10){double ang=Math.acos(Mth.clamp(prevT.dot(t),-1,1));nrm=rotate(nrm,axis.normalize(),ang);bi=t.cross(nrm).normalize();nrm=bi.cross(t).normalize();}
            prevT=t;N[i]=nrm;B[i]=bi;
        }
        for(int k=0;k<strands;k++){
            List<Vec3> line=new ArrayList<>();double[] r=new double[n];
            for(int i=0;i<n;i++){
                double f=len[i]/total,ph=k*Math.PI*2/strands+f*turns*Math.PI*2+key*6;
                double off=radius[i]*(strands==2?.42:.5);
                line.add(pts.get(i).add(N[i].scale(Math.cos(ph)*off)).add(B[i].scale(Math.sin(ph)*off)));
                r[i]=radius[i]*(strands==2?.62:.56)*(1-.12*k/(double)strands);
            }
            tube(o,line,r,sides);
        }
    }
    private static double[] taper(int n,double r0,double r1,boolean flare){
        double[] r=new double[n+1];
        for(int i=0;i<=n;i++){double t=i/(double)n;r[i]=r0+(r1-r0)*Math.pow(t,.7);if(flare)r[i]*=1+1.1*Math.pow(Math.max(0,t-.82)/.18,2);}
        return r;
    }
    private static void grip(Out o,Vec3 end,double width,double key,Ground g,double delay){
        o.set(ROOTLET,delay,key,0);
        int n=3+(int)(Math.abs(Math.sin(key*37))*3);
        for(int j=0;j<n;j++){
            double a=j*Math.PI*2/n+key*9,len=5+4*Math.abs(Math.sin(key*13+j));
            List<Vec3> pts=new ArrayList<>();
            for(int i=0;i<=8;i++){
                double t=i/8.0;
                double x=end.x+Math.cos(a)*len*t,z=end.z+Math.sin(a)*len*t;
                double gy=g.y(x,z);
                pts.add(new Vec3(x,Mth.lerp(t*t,end.y+4,gy-.25)+Math.sin(Math.PI*t)*.8,z));
            }
            tube(o,pts,taper(8,width*.45,.12,false),5);
        }
    }

    static void build(Minecraft mc){
        var st=DreamClient.state;
        if(st==null)return;
        long key=st.seed();
        if(builtFor==key&&(unresolved==0||DreamClient.ticks-builtAt<100))return;
        builtFor=key;builtAt=DreamClient.ticks;unresolved=0;
        grips.removeIf(gr->!gr.road());tips.removeIf(t->!t.road());
        Vec3 center=DreamClient.center();
        Ground g=ground(mc.level,center.y,center);
        int q=TideConfig.QUALITY.get();
        int count=new int[]{10,16,22}[q],rings=q==0?8:q==1?11:13,segs=q==0?14:q==1?18:24,sides=q==0?5:7,steps=q==0?22:40;
        BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        Out o=new Out(b);
        for(DreamTimeline.Tree t:DreamTimeline.trees(key,count)){
            double s=t.size(),kk=DreamTimeline.u(t.key(),9);
            double top=DreamTimeline.domeHeight(Math.hypot(t.x(),t.z()))+6,bottom=t.crown();
            // The crown: masses of leaves stacked from the canopy down, largest at the top.
            int n=5+(int)(DreamTimeline.u(t.key(),10)*3);
            List<Vec3> centres=new ArrayList<>();
            o.set(LEAF,0,kk,0);
            for(int i=0;i<n;i++){
                double f=i/(double)(n-1);
                double y=Mth.lerp(f,top-10*s,bottom+7*s);
                double r=Mth.lerp(Math.pow(f,.8),27*s,9*s);
                double jx=(DreamTimeline.u(t.key(),20+i)-.5)*12*s*(1-f*.5),jz=(DreamTimeline.u(t.key(),40+i)-.5)*12*s*(1-f*.5);
                Vec3 c=new Vec3(t.x()+jx,y,t.z()+jz);
                centres.add(c);
                lump(o,c,r*(.9+.2*DreamTimeline.u(t.key(),60+i)),r*.72,r*(.9+.2*DreamTimeline.u(t.key(),80+i)),rings,segs,kk+i);
            }
            for(int i=0;i<2;i++){
                double a=DreamTimeline.u(t.key(),100+i)*Math.PI*2,y=Mth.lerp(.35+.3*i,top,bottom);
                Vec3 c=new Vec3(t.x()+Math.cos(a)*18*s,y,t.z()+Math.sin(a)*18*s);
                lump(o,c,13*s,9*s,13*s,rings,segs,kk+10+i);
                centres.add(c);
            }
            // The trunk and branches inside it.
            o.set(BRANCH,0,kk,0);
            List<Vec3> trunk=new ArrayList<>();
            for(int i=0;i<=10;i++){double f=i/10.0;trunk.add(new Vec3(t.x()+Math.sin(f*3+kk*9)*2*s,Mth.lerp(f,top+8,bottom+4*s),t.z()+Math.cos(f*2.6+kk*7)*2*s));}
            tube(o,trunk,taper(10,3.4*s,1.1*s,false),sides);
            for(Vec3 c:centres){
                Vec3 from=trunk.get(Math.max(0,Math.min(10,(int)Math.round((top+8-c.y)/(top+8-bottom-4*s)*10))));
                List<Vec3> br=List.of(from,from.lerp(c,.5).add(0,-2,0),c);
                tube(o,br,new double[]{1.6*s,1.1*s,.5*s},Math.max(4,sides-2));
            }
            // A little house on the lowest branch, now and then.
            if(DreamTimeline.u(t.key(),120)<.6){
                Vec3 low=centres.get(n-1);
                double a=DreamTimeline.u(t.key(),121)*Math.PI*2;
                Vec3 at=low.add(Math.cos(a)*6*s,-4*s,Math.sin(a)*6*s);
                o.set(BRANCH,0,kk,0);
                tube(o,List.of(low,at.add(0,2,0),at),new double[]{1.2*s,.9*s,.6*s},4);
                house(o,at,.9+.4*s,a,kk);
            }
            // The aerial roots.
            for(DreamTimeline.Root r:DreamTimeline.roots(t)){
                Vec3 rtop=new Vec3(r.x(),r.y(),r.z());
                Vec3 end=r.reaches()?new Vec3(r.tx(),g.y(r.tx(),r.tz()),r.tz()):new Vec3(r.tx(),r.y()-r.hang(),r.tz());
                o.set(BARK,r.delay(),DreamTimeline.u(r.key(),3),r.reaches()?0:1);
                List<Vec3> pts=rootPath(rtop,end,DreamTimeline.u(r.key(),8),steps);
                double[] rad=taper(steps,r.width(),r.width()*(r.reaches()?.45:.2),r.reaches());
                if(r.width()>1.5&&q>0)braid(o,pts,rad,Math.max(5,sides-1),r.width()>2?3:2,1.5+2*DreamTimeline.u(r.key(),9),DreamTimeline.u(r.key(),10));
                else tube(o,pts,rad,sides);
                tips.add(new Tip(rtop,end,r.delay(),false,r.reaches()));
                if(r.reaches()){grip(o,end,r.width(),DreamTimeline.u(r.key(),11),g,r.delay());grips.add(new Grip(end,r.delay(),false));}
            }
        }
        grove=upload(grove,b.build());
    }
    /** The deer's road: roots let down one after another on either side of its way. */
    static void buildRoad(Minecraft mc){
        var st=DreamClient.state;
        if(st==null||!st.hasRoute()||roadFor==st.routeAt())return;
        roadFor=st.routeAt();
        grips.removeIf(Grip::road);tips.removeIf(Tip::road);
        Vec3 center=DreamClient.center();
        Ground g=ground(mc.level,center.y,center);
        int q=TideConfig.QUALITY.get(),sides=q==0?5:7,steps=q==0?22:30;
        BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        Out o=new Out(b);
        for(DreamTimeline.RoadRoot r:DreamTimeline.roadRoots(st.routeValue())){
            Vec3 foot=r.foot().subtract(center);
            foot=new Vec3(foot.x,g.y(foot.x,foot.z),foot.z);
            double top=DreamTimeline.domeHeight(Math.hypot(foot.x,foot.z))-2;
            Vec3 t=new Vec3(foot.x+Math.sin(r.along())*8,top,foot.z+Math.cos(r.along())*8);
            double key=DreamTimeline.u(st.seed()^(long)r.along(),5);
            o.set(ROAD,r.delay(),key,0);
            List<Vec3> way=rootPath(t,foot,key,steps);
            if(q>0)braid(o,way,taper(steps,3.1,1.3,true),Math.max(5,sides-1),3,2.2+key,key);
            else tube(o,way,taper(steps,3.1,1.3,true),sides);
            grips.add(new Grip(foot,r.delay(),true));
            tips.add(new Tip(t,foot,r.delay(),true,true));
        }
        road=upload(road,b.build());
    }
    private static VertexBuffer upload(VertexBuffer target,MeshData mesh){
        if(mesh==null)return target;
        if(target==null)target=new VertexBuffer(VertexBuffer.Usage.STATIC);
        target.bind();target.upload(mesh);VertexBuffer.unbind();
        return target;
    }

    // ------------------------------------------------------------------ the dream tree
    /** A slender tree of pale wood, leaning a little, its crown giving a soft green light, lamp-flowers hanging from it. */
    static void buildTree(double growth,double key){
        if(Math.abs(growth-treeBuiltGrowth)<.004&&tree!=null&&treeBuiltAt>=0)return;
        treeBuiltGrowth=growth;treeBuiltAt=DreamClient.ticks;
        var st=DreamClient.state;
        Vec3 base=Vec3.atBottomCenterOf(st.tree()).subtract(DreamClient.center());
        BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        Out o=new Out(b);
        double g=TideTimeline.smooth(0,1,growth),h=7.5*g;
        if(h<.2){tree=upload(tree,b.build());return;}
        double lean=key*Math.PI*2;
        List<Vec3> trunk=new ArrayList<>();
        for(int i=0;i<=10;i++){double f=i/10.0;trunk.add(base.add(Math.cos(lean)*Math.sin(f*1.4)*1.2*g,f*h,Math.sin(lean)*Math.sin(f*1.4)*1.2*g));}
        o.set(WOOD,0,key,0);
        tube(o,trunk,taper(10,.42*g+.05,.14*g+.03,false),7);
        Vec3 top=trunk.get(10);
        List<Vec3> ends=new ArrayList<>();
        for(int k=0;k<5;k++){
            double a=lean+k*1.2566+.3,f=.55+.08*k;
            Vec3 from=trunk.get((int)Math.round(f*10)),to=from.add(Math.cos(a)*2.6*g,1.6*g+.4*k*g,Math.sin(a)*2.6*g);
            tube(o,List.of(from,from.lerp(to,.5).add(0,.4*g,0),to),new double[]{.18*g+.02,.12*g+.02,.06*g+.02},5);
            ends.add(to);
        }
        double leaves=TideTimeline.smooth(.25,.9,growth);
        if(leaves>.01){
            o.set(LEAF,0,key,1);
            lump(o,top.add(0,.6*g,0),2.3*leaves+.2,1.7*leaves+.2,2.3*leaves+.2,12,18,key*9);
            for(int i=0;i<ends.size();i++){Vec3 e=ends.get(i);lump(o,e,1.4*leaves+.1,1*leaves+.1,1.4*leaves+.1,10,14,key*9+i);}
        }
        tree=upload(tree,b.build());
    }
    /** Where the dream tree's lamp-flowers hang (world space), for the glows. */
    static List<Vec3> treeLamps(double growth,double key){
        List<Vec3> out=new ArrayList<>();
        var st=DreamClient.state;
        if(st==null||!st.hasTree())return out;
        double g=TideTimeline.smooth(0,1,growth);
        if(g<.6)return out;
        Vec3 base=Vec3.atBottomCenterOf(st.tree());
        double lean=key*Math.PI*2;
        for(int k=0;k<5;k++){
            double a=lean+k*1.2566+.3,f=.55+.08*k,h=7.5*g;
            Vec3 from=base.add(Math.cos(lean)*Math.sin(f*1.4)*1.2*g,f*h,Math.sin(lean)*Math.sin(f*1.4)*1.2*g);
            out.add(from.add(Math.cos(a)*2.2*g,1.2*g+.4*k*g-1.4,Math.sin(a)*2.2*g));
        }
        return out;
    }

    // ------------------------------------------------------------------ drawing
    static void draw(Matrix4f view,Matrix4f proj,Vec3 camera,double p,double s,double time,float partial){
        if(shader==null)return;
        var mc=Minecraft.getInstance();
        var st=DreamClient.state;
        build(mc);
        buildRoad(mc);
        Vec3 origin=DreamClient.center().subtract(camera);
        double day=DreamClient.dayness(partial);
        float far=mc.gameRenderer.getDepthFar();
        shader.safeGetUniform("Origin").set((float)origin.x,(float)origin.y,(float)origin.z,far);
        shader.safeGetUniform("GroveLook").set((float)(time%3600),(float)day,(float)((.82+.18*TideConfig.BRIGHTNESS.get())*Math.min(1,s*1.2)),(float)(.75*s+.25));
        shader.safeGetUniform("GroveGrow").set((float)DreamTimeline.rootGrow(p),(float)DreamTimeline.rootWithdraw(p),(float)DreamTimeline.leafLoss(p),(float)DreamTimeline.gold(p));
        float angle=mc.level.getTimeOfDay(partial)*(float)Math.PI*2;
        Vec3 sun=new Vec3(-Math.sin(angle),Math.cos(angle),0);
        if(sun.y<0)sun=sun.scale(-1);
        shader.safeGetUniform("GroveSun").set((float)sun.x,(float)sun.y,(float)sun.z,(float)(DreamTimeline.fireflies(p)*s));
        shader.safeGetUniform("GroveRoad").set((float)DreamTimeline.road(p),(float)(DreamTimeline.canopy(p)*1.14-.08),0f,0f);
        float r=(float)Mth.lerp(day,.035,.62),gg=(float)Mth.lerp(day,.1,.76),bb=(float)Mth.lerp(day,.085,.62);
        shader.safeGetUniform("GroveFog").set(r,gg,bb,1f);
        // The whole grove fades in and out with the sky: nothing is left hanging when the weather goes.
        float fade=(float)(DreamTimeline.sky(p)*s);
        if(fade<.01)return;
        RenderSystem.setShaderColor(1,1,1,1);
        RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.disableBlend();RenderSystem.disableCull();
        if(TideConfig.SKY.get()){
            if(DreamTimeline.canopy(p)>.01)drawBuffer(grove,view,proj);
            if(road!=null&&DreamTimeline.road(p)>0)drawBuffer(road,view,proj);
        }
        if(st.hasTree()){
            double growth=DreamTimeline.treeGrowth(DreamClient.ageNow(partial)-st.treeAt())*(1-DreamTimeline.treeWither(p));
            buildTree(growth,DreamTimeline.u(st.seed(),77));
            if(growth>.005)drawBuffer(tree,view,proj);
        }
        RenderSystem.enableCull();
    }
    private static void drawBuffer(VertexBuffer b,Matrix4f view,Matrix4f proj){
        if(b==null)return;
        b.bind();b.drawWithShader(view,proj,shader);VertexBuffer.unbind();
    }
    private DreamGrove(){}
}
