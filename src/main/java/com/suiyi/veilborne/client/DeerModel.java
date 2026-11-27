package com.suiyi.veilborne.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.suiyi.veilborne.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 梦鹿: a stag the height of a hill, nearly transparent, walking the road of roots. Built every frame in world space: a
 * lofted body (deep chest, tucked flank, rounded rump, the withers higher than the hips), a long neck, a head with honey
 * eyes that blink, ears that turn, and four legs whose hooves are planted on the land in a walk's lateral sequence and held
 * there while the body passes over them; each leg is solved from hoof to hip (the knee folds as it lifts, the hind leg's
 * hock bends back), so it steps over the hills rather than through them. On its antlers grows a woodland: small trees on
 * every tine, moss trailing from them, lamps, and a little house with a lit window.
 *
 * When it stops by the dream tree it turns a little toward it and lowers its head, down out of the sky, until its face is
 * a stone's throw from whoever it looks at, one eye toward them; late in the look it blinks once, slowly.
 */
final class DeerModel {
    static ShaderInstance shader;
    static final int GLASS=0,ANTLER=1,LEAF=2,TRUNK=3,EYE=4,HOOF=5,WARM=6,MOSS=7;
    private static VertexBuffer buffer;
    interface Out { void v(double x,double y,double z,double u,double w,double nx,double ny,double nz,int kind,float shade); }

    /** The deer this frame, in world space. */
    record Pose(Vec3 base,Vec3 h,Vec3 r,double gait,double turn,double gaze,Vec3 target,Vec3[] feet,double[] swing,
                Vec3 hips,Vec3 shoulders,double hindY,double foreY,Vec3 neck,Vec3 occiput,Vec3 ha,Vec3 hu,double vis,double time){}
    static final double LIFT=6.5;
    private static final Vec3 UP=new Vec3(0,1,0);

    static Vec3 target(DreamPacket st){
        if(st.has(DreamData.GLANCE_PLAYER))return Vec3.atBottomCenterOf(st.glance()).add(0,1.6,0);
        if(st.hasTree())return Vec3.atBottomCenterOf(st.tree()).add(0,5,0);
        return st.routeValue().origin().add(0,2,0);
    }
    private static double ground(double x,double z,double y0){
        var level=Minecraft.getInstance().level;
        int bx=Mth.floor(x),bz=Mth.floor(z);
        return level!=null&&level.hasChunk(bx>>4,bz>>4)?Surface.top(level,bx,bz):y0;
    }
    private static Vec3 planted(DreamTimeline.Route r,int leg,int k){
        Vec3 a=DreamTimeline.plant(r,leg,k);
        return new Vec3(a.x,ground(a.x,a.z,r.origin().y),a.z);
    }
    static Pose pose(float partial){
        var st=DreamClient.state;
        if(st==null||!st.hasRoute())return null;
        double p=DreamClient.progress(partial);
        double vis=DreamTimeline.deer(p)*DreamClient.strength();
        if(vis<=.002)return null;
        double time=(DreamClient.ticks+partial)/20.0;
        double w=DreamTimeline.walk(p),s=DreamTimeline.along(w),gait=DreamTimeline.gait(s);
        double turn=DreamTimeline.turn(w),gaze=DreamTimeline.gaze(w);
        DreamTimeline.Route route=st.routeValue();
        Vec3[] feet=new Vec3[4];double[] sw=new double[4];
        for(int leg=0;leg<4;leg++){
            int k=DreamTimeline.step(leg,gait);double u=DreamTimeline.swing(leg,gait);
            Vec3 a=planted(route,leg,k);
            if(u<0){feet[leg]=a;sw[leg]=-1;continue;}
            Vec3 b=planted(route,leg,k+1);
            double e=u*u*(3-2*u);
            Vec3 f=a.lerp(b,e).add(0,Math.sin(Math.PI*u)*LIFT+Math.max(0,b.y-a.y)*Math.sin(Math.PI*u)*.4,0);
            // Standing, a lifted hoof comes down where it would have landed.
            feet[leg]=f.lerp(b,turn);sw[leg]=u*(1-turn);
        }
        Vec3 base=route.at(s);
        Vec3 h=route.tangent(s);
        Vec3 target=target(st);
        double yaw=Math.atan2(h.z,h.x);
        Vec3 toT=target.subtract(base);
        double want=Math.atan2(toT.z,toT.x),diff=Mth.wrapDegrees(Math.toDegrees(want-yaw));
        yaw+=Math.toRadians(Mth.clamp(diff,-30,30))*.7*turn;
        h=new Vec3(Math.cos(yaw),0,Math.sin(yaw));
        Vec3 r=new Vec3(-h.z,0,h.x);
        double hindY=(feet[0].y+feet[2].y)/2,foreY=(feet[1].y+feet[3].y)/2;
        double bob=.9*Math.sin(gait*4*Math.PI)*(1-turn);
        Vec3 hips=new Vec3(base.x-h.x*19,Math.min(hindY,foreY+6)+40+bob,base.z-h.z*19);
        Vec3 shoulders=new Vec3(base.x+h.x*15,Math.min(foreY,hindY+6)+41.5+bob*.8,base.z+h.z*15);
        Vec3 neck=spine(hips,shoulders,h,17).add(0,5.5,0);
        // The head: held high and forward as it walks, nodding with the stride; lowered toward whoever it looks at.
        double nod=.06*Math.sin(gait*4*Math.PI+1)*(1-turn);
        Vec3 walkDir=h.scale(.5).add(0,.866,0).normalize();
        Vec3 flatT=new Vec3(target.x-neck.x,0,target.z-neck.z);
        Vec3 flatN=flatT.lengthSqr()<1e-4?h:flatT.normalize();
        walkDir=walkDir.lerp(flatN.scale(.45).add(0,.89,0).normalize(),turn*.6).normalize();
        Vec3 oWalk=neck.add(walkDir.scale(26)).add(0,-nod*10,0);
        Vec3 awayFlat=flatN.scale(-1);
        Vec3 oGaze=target.add(awayFlat.scale(25)).add(0,11,0);
        Vec3 d=oGaze.subtract(neck);
        if(d.length()>52)oGaze=neck.add(d.normalize().scale(52));
        double ground=ground(oGaze.x,oGaze.z,target.y);
        if(oGaze.y<ground+9)oGaze=new Vec3(oGaze.x,ground+9,oGaze.z);
        double g=gaze*gaze*(3-2*gaze);
        Vec3 occiput=oWalk.lerp(oGaze,g);
        Vec3 haWalk=h.scale(.88).add(0,-.47-nod,0).normalize();
        // Its muzzle points a little past the one it looks at, so the near eye is turned to them.
        Vec3 aim=target.subtract(occiput),side=aim.cross(UP);
        side=side.lengthSqr()<1e-6?r:side.normalize();
        if(side.dot(h)>0)side=side.scale(-1);
        Vec3 haGaze=target.add(side.scale(7)).subtract(occiput).normalize();
        Vec3 ha=haWalk.lerp(haGaze,g).normalize();
        Vec3 hu=UP.subtract(ha.scale(UP.dot(ha))).normalize();
        return new Pose(base,h,r,gait,turn,gaze,target,feet,sw,hips,shoulders,hindY,foreY,neck,occiput,ha,hu,vis,time);
    }
    /** A point of the spine, {@code x} blocks along the body from its middle (hips at -19, shoulders at +15). */
    static Vec3 spine(Vec3 hips,Vec3 shoulders,Vec3 h,double x){
        return hips.lerp(shoulders,(x+19)/34);
    }
    static Vec3 headPoint(float partial){Pose p=pose(partial);return p==null?null:p.occiput;}
    /** Where the light in its chest is. */
    static Vec3 heart(Pose p){return spine(p.hips,p.shoulders,p.h,9).add(0,-7,0);}

    // ------------------------------------------------------------------ geometry
    private static final double[] TX={-31,-28,-23,-16,-9,-2,5,12,18,23,26.5};
    private static final double[] TUP={.6,4.2,6.2,6.6,6.0,6.0,6.6,7.6,8.2,6.2,1.6};
    private static final double[] TDN={.8,6.0,9.6,11.2,11.4,12.4,13.6,14.2,13.6,11.2,3.6};
    private static final double[] TW={.8,4.6,7.1,8.0,7.8,8.2,8.6,8.6,8.0,6.4,2.4};
    private static final double[] TDY={-3,-1.6,0,.4,-.4,-.7,0,1.4,2.8,1.6,-1.4};
    static double interp(double[] a,double x){
        int n=TX.length;if(x<=TX[0])return a[0];if(x>=TX[n-1])return a[n-1];
        int i=0;while(x>TX[i+1])i++;
        double t=(x-TX[i])/(TX[i+1]-TX[i]);
        double p0=a[Math.max(0,i-1)],p1=a[i],p2=a[i+1],p3=a[Math.min(n-1,i+2)];
        return .5*(2*p1+(-p0+p2)*t+(2*p0-5*p1+4*p2-p3)*t*t+(-p0+3*p1-3*p2+p3)*t*t*t);
    }
    private static void quad(Out o,Vec3 a,Vec3 b,Vec3 c,Vec3 d,Vec3 na,Vec3 nb,Vec3 nc,Vec3 nd,double ua,double va,double ub,double vb,int kind,float shade){
        o.v(a.x,a.y,a.z,ua,va,na.x,na.y,na.z,kind,shade);o.v(b.x,b.y,b.z,ub,va,nb.x,nb.y,nb.z,kind,shade);
        o.v(c.x,c.y,c.z,ub,vb,nc.x,nc.y,nc.z,kind,shade);o.v(d.x,d.y,d.z,ua,vb,nd.x,nd.y,nd.z,kind,shade);
    }
    private static void torso(Out o,Pose p,int rings,int sides){
        Vec3[][] pt=new Vec3[rings+1][sides+1],nm=new Vec3[rings+1][sides+1];
        for(int i=0;i<=rings;i++){
            double x=Mth.lerp(i/(double)rings,TX[0],TX[TX.length-1]);
            Vec3 c=spine(p.hips,p.shoulders,p.h,x).add(0,interp(TDY,x),0);
            double up=interp(TUP,x),dn=interp(TDN,x),w=interp(TW,x);
            for(int k=0;k<=sides;k++){
                double a=k*Math.PI*2/sides,cs=Math.cos(a),sn=Math.sin(a);
                double e=cs>=0?.8:.9;
                double y=Math.signum(cs)*Math.pow(Math.abs(cs),e)*(cs>=0?up:dn),z=Math.signum(sn)*Math.pow(Math.abs(sn),.85)*w;
                pt[i][k]=c.add(0,y,0).add(p.r.scale(z));
            }
        }
        for(int i=0;i<=rings;i++)for(int k=0;k<=sides;k++){
            Vec3 a=pt[Math.min(rings,i+1)][k].subtract(pt[Math.max(0,i-1)][k]),b=pt[i][(k+1)%sides].subtract(pt[i][(k+sides-1)%sides]);
            Vec3 n=b.cross(a);
            nm[i][k]=n.lengthSqr()<1e-9?p.h.scale(i==0?-1:1):n.normalize();
        }
        for(int i=0;i<rings;i++)for(int k=0;k<sides;k++)
            quad(o,pt[i][k],pt[i+1][k],pt[i+1][k+1],pt[i][k+1],nm[i][k],nm[i+1][k],nm[i+1][k+1],nm[i][k+1],i/(double)rings*.5,k/(double)sides,(i+1)/(double)rings*.5,(k+1)/(double)sides,GLASS,1);
    }
    /** A tube through points with per-point radii; frames carried along without twist; u from u0 to u1. */
    static void tube(Out o,List<Vec3> pts,double[] radius,int sides,int kind,double u0,double u1,float shade){
        int n=pts.size();
        if(n<2)return;
        Vec3 t0=pts.get(1).subtract(pts.get(0)).normalize();
        Vec3 ref=Math.abs(t0.y)>.9?new Vec3(1,0,0):UP;
        Vec3 nr=t0.cross(ref).normalize(),bi;
        Vec3 prevT=t0;
        Vec3[][] ring=new Vec3[n][sides+1],nor=new Vec3[n][sides+1];
        for(int i=0;i<n;i++){
            Vec3 t=(i==n-1?pts.get(i).subtract(pts.get(i-1)):pts.get(i+1).subtract(pts.get(Math.max(0,i-1)))).normalize();
            Vec3 axis=prevT.cross(t);
            if(axis.lengthSqr()>1e-10)nr=DreamGrove.rotate(nr,axis.normalize(),Math.acos(Mth.clamp(prevT.dot(t),-1,1)));
            bi=t.cross(nr).normalize();nr=bi.cross(t).normalize();prevT=t;
            for(int k=0;k<=sides;k++){double a=k*Math.PI*2/sides;Vec3 d=nr.scale(Math.cos(a)).add(bi.scale(Math.sin(a)));ring[i][k]=pts.get(i).add(d.scale(radius[i]));nor[i][k]=d;}
        }
        for(int i=0;i+1<n;i++)for(int k=0;k<sides;k++)
            quad(o,ring[i][k],ring[i+1][k],ring[i+1][k+1],ring[i][k+1],nor[i][k],nor[i+1][k],nor[i+1][k+1],nor[i][k+1],
                Mth.lerp(i/(double)(n-1),u0,u1),k/(double)sides,Mth.lerp((i+1)/(double)(n-1),u0,u1),(k+1)/(double)sides,kind,shade);
    }
    static void blob(Out o,Vec3 c,double rx,double ry,double rz,int kind,int rings,int segs,double key,float shade){
        for(int i=0;i<rings;i++)for(int k=0;k<segs;k++){
            Vec3[] q=new Vec3[4],n=new Vec3[4];
            int[][] ij={{i,k},{i+1,k},{i+1,k+1},{i,k+1}};
            for(int m=0;m<4;m++){
                double ph=Math.PI*ij[m][0]/rings-Math.PI/2,th=Math.PI*2*ij[m][1]/segs;
                double dx=Math.cos(ph)*Math.cos(th),dy=Math.sin(ph),dz=Math.cos(ph)*Math.sin(th);
                double wb=1+.16*Math.sin(dx*4+key*9)*Math.sin(dz*3.7+dy*2+key*5);
                q[m]=c.add(dx*rx*wb,dy*ry*wb,dz*rz*wb);n[m]=new Vec3(dx/rx,dy/ry,dz/rz).normalize();
            }
            quad(o,q[0],q[1],q[2],q[3],n[0],n[1],n[2],n[3],k/(double)segs,i/(double)rings,(k+1)/(double)segs,(i+1)/(double)rings,kind,shade);
        }
    }
    /** Two-bone reach: where the middle joint goes, bending toward {@code pole}. */
    static Vec3 joint(Vec3 a,Vec3 t,double l1,double l2,Vec3 pole){
        Vec3 d=t.subtract(a);double dist=Math.max(1e-4,d.length());
        double dd=Mth.clamp(dist,Math.abs(l1-l2)+.05,l1+l2-.05);
        Vec3 dir=d.scale(1/dist);
        double cos=(l1*l1+dd*dd-l2*l2)/(2*l1*dd),sin=Math.sqrt(Math.max(0,1-cos*cos));
        Vec3 perp=pole.subtract(dir.scale(pole.dot(dir)));
        perp=perp.lengthSqr()<1e-8?UP:perp.normalize();
        return a.add(dir.scale(l1*cos)).add(perp.scale(l1*sin));
    }
    /** Catmull-Rom through the joints, so a leg bends round its knee instead of kinking there; radii follow smoothly. */
    private static List<Vec3> spline(List<Vec3> pts,double[] rad,int per,List<Double> radOut){
        List<Vec3> out=new ArrayList<>();
        int n=pts.size();
        for(int i=0;i+1<n;i++){
            Vec3 p0=pts.get(Math.max(0,i-1)),p1=pts.get(i),p2=pts.get(i+1),p3=pts.get(Math.min(n-1,i+2));
            double r0=rad[Math.max(0,i-1)],r1=rad[i],r2=rad[i+1],r3=rad[Math.min(n-1,i+2)];
            for(int k=0;k<per;k++){
                double t=k/(double)per,t2=t*t,t3=t2*t;
                double a=-.5*t3+t2-.5*t,b=1.5*t3-2.5*t2+1,c=-1.5*t3+2*t2+.5*t,d=.5*t3-.5*t2;
                out.add(p0.scale(a).add(p1.scale(b)).add(p2.scale(c)).add(p3.scale(d)));
                radOut.add(Math.max(.2,r0*a+r1*b+r2*c+r3*d));
            }
        }
        out.add(pts.get(n-1));radOut.add(rad[n-1]);
        return out;
    }
    private static void legs(Out o,Pose p,int sides){
        for(int leg=0;leg<4;leg++){
            boolean fore=leg%2==1;double side=leg<2?-1:1;
            Vec3 F=p.feet[leg];double u=p.swing[leg];
            double fold=fore?(u<0?0:1.25*Math.pow(Math.sin(Math.PI*u),.8)):(u<0?-.12:-.12+.8*Math.sin(Math.PI*u));
            double low=fore?12.5:14;
            Vec3 knee=F.add(0,Math.cos(fold)*low,0).add(p.h.scale(Math.sin(fold)*low));
            Vec3 root=fore?spine(p.hips,p.shoulders,p.h,16).add(0,-7,0).add(p.r.scale(side*5.2)):spine(p.hips,p.shoulders,p.h,-19).add(0,-5,0).add(p.r.scale(side*6));
            double l1=fore?13:15,l2=fore?15:17;
            // The foreleg's elbow sits behind its line, the hind leg's stifle ahead of it.
            Vec3 mid=joint(root,knee,l1,l2,p.h.scale(fore?-1:1));
            Vec3 kneeR=mid.add(knee.subtract(mid).normalize().scale(l2));
            Vec3 lowDir=F.subtract(knee).normalize();
            Vec3 fet=kneeR.add(lowDir.scale(low-3.2)),hoof=kneeR.add(lowDir.scale(low));
            List<Vec3> pts=List.of(root.add(0,4,0),root,root.lerp(mid,.5),mid,mid.lerp(kneeR,.5),kneeR,kneeR.lerp(fet,.5),fet,hoof);
            double[] rad=fore?new double[]{4.5,5.2,4.0,3.1,2.3,2.1,1.35,1.55,1.4}:new double[]{5.8,6.6,5.4,4.1,2.6,2.2,1.45,1.6,1.5};
            List<Double> rl=new ArrayList<>();
            List<Vec3> smooth=spline(pts,rad,4,rl);
            double[] rs=new double[rl.size()];for(int i=0;i<rs.length;i++)rs[i]=rl.get(i);
            tube(o,smooth,rs,sides,GLASS,.86,.99,1);
            // The hoof: a little gold, the only part of it that touches anything.
            tube(o,List.of(hoof.add(lowDir.scale(-1.2)),hoof,hoof.add(0,-.35,0)),new double[]{1.5,1.8,1.7},sides,HOOF,0,1,1);
        }
    }
    private static void neckAndHead(Out o,Pose p,int sides,double time){
        // The neck, from the chest up to the back of the skull.
        Vec3 n0=p.neck,o1=p.occiput;
        Vec3 c1=n0.add(p.h.scale(5)).add(0,7,0),c2=o1.subtract(p.ha.scale(9)).add(p.hu.scale(-3));
        List<Vec3> pts=new ArrayList<>();double[] rad=new double[15];
        for(int i=0;i<=14;i++){
            double t=i/14.0,a=1-t;
            pts.add(n0.scale(a*a*a).add(c1.scale(3*a*a*t)).add(c2.scale(3*a*t*t)).add(o1.scale(t*t*t)));
            rad[i]=Mth.lerp(Math.pow(t,.7),7.6,4.3)+(t<.2?(.2-t)*9:0);
        }
        tube(o,pts,rad,sides,GLASS,.5,.7,1);
        // The head, lofted along its axis from the back of the skull to the tip of the muzzle.
        // A broad, round skull between the eyes, narrowing into a long slender muzzle.
        double[] T={0,.12,.3,.5,.7,.85,.95,1},UPp={3.4,4.4,4.2,3.0,2.2,1.9,1.7,.8},DN={3.6,4.8,5.0,4.0,3.1,2.6,2.2,.9},WD={3.2,4.3,4.4,3.0,2.1,1.8,1.6,.8};
        Vec3 hr=p.ha.cross(p.hu).normalize();
        int rings=16;double len=18.5;
        Vec3[][] ring=new Vec3[rings+1][sides+1],nor=new Vec3[rings+1][sides+1];
        for(int i=0;i<=rings;i++){
            double t=i/(double)rings,up=lin(T,UPp,t),dn=lin(T,DN,t),w=lin(T,WD,t);
            Vec3 c=o1.add(p.ha.scale(t*len)).add(p.hu.scale(-t*1.2));
            for(int k=0;k<=sides;k++){
                double a=k*Math.PI*2/sides,cs=Math.cos(a),sn=Math.sin(a);
                ring[i][k]=c.add(p.hu.scale(cs*(cs>=0?up:dn))).add(hr.scale(sn*w));
                nor[i][k]=p.hu.scale(cs/(cs>=0?up:dn)).add(hr.scale(sn/w)).normalize();
            }
        }
        for(int i=0;i<rings;i++)for(int k=0;k<sides;k++)
            quad(o,ring[i][k],ring[i+1][k],ring[i+1][k+1],ring[i][k+1],nor[i][k],nor[i+1][k],nor[i+1][k+1],nor[i][k+1],.7+i/(double)rings*.15,k/(double)sides,.7+(i+1)/(double)rings*.15,(k+1)/(double)sides,GLASS,1);
        // The nose, closing the muzzle: a dark, damp pad, the one dark thing about it.
        Vec3 nose=o1.add(p.ha.scale(len-.55)).add(p.hu.scale(-1.0));
        blob(o,nose,.95,.75,.95,TRUNK,6,10,0,1);
        // Eyes: honey, a slit of a pupil across; they blink now and then, and once, slowly, while it looks at you.
        float lid=blink(time,p.gaze);
        for(int s=-1;s<=1;s+=2){
            Vec3 ec=o1.add(p.ha.scale(.3*len)).add(p.hu.scale(1.5)).add(hr.scale(s*3.7));
            Vec3 out=hr.scale(s);
            for(int i=0;i<6;i++)for(int k=0;k<10;k++){
                Vec3[] q=new Vec3[4],nn=new Vec3[4];double[] uu=new double[4],vv=new double[4];
                int[][] ij={{i,k},{i+1,k},{i+1,k+1},{i,k+1}};
                for(int m=0;m<4;m++){
                    double ph=Math.PI*ij[m][0]/6-Math.PI/2,th=Math.PI*2*ij[m][1]/10;
                    Vec3 d=p.hu.scale(Math.sin(ph)).add(out.scale(Math.cos(ph)*Math.cos(th))).add(p.ha.scale(Math.cos(ph)*Math.sin(th)));
                    q[m]=ec.add(d.scale(1.25));nn[m]=d;uu[m]=Math.max(0,d.dot(out));vv[m]=ij[m][0]/6.0;
                }
                o.v(q[0].x,q[0].y,q[0].z,uu[0],vv[0],nn[0].x,nn[0].y,nn[0].z,EYE,lid);o.v(q[1].x,q[1].y,q[1].z,uu[1],vv[1],nn[1].x,nn[1].y,nn[1].z,EYE,lid);
                o.v(q[2].x,q[2].y,q[2].z,uu[2],vv[2],nn[2].x,nn[2].y,nn[2].z,EYE,lid);o.v(q[3].x,q[3].y,q[3].z,uu[3],vv[3],nn[3].x,nn[3].y,nn[3].z,EYE,lid);
            }
            // Ears: turned out to the sides as it walks, and forward, toward you, while it looks.
            double flick=Math.max(0,Math.sin(time*.9+s*1.7))>.97?Math.sin(time*18)*.25:0;
            Vec3 base=o1.add(p.ha.scale(.06*len)).add(p.hu.scale(3.2)).add(hr.scale(s*2.6));
            Vec3 dir=hr.scale(s*.8).add(p.hu.scale(.5)).add(p.ha.scale(-.35+.7*p.gaze+flick)).normalize();
            List<Vec3> ear=new ArrayList<>();double[] er=new double[6];
            for(int i=0;i<6;i++){double t=i/5.0;ear.add(base.add(dir.scale(t*7.5)));er[i]=Math.max(.15,2.1*Math.pow(Math.sin(Math.PI*Math.min(1,t*.95+.05)),.7));}
            tube(o,ear,er,6,GLASS,.85,.86,1);
        }
    }
    static float blink(double time,double gaze){
        double t=time%7.3;
        double quick=Math.exp(-Math.pow((t-.15)/.06,2));
        double slow=gaze>.95?Math.exp(-Math.pow(((time*.13)%1-.5)/.05,2)):0;
        return (float)(1-.9*Math.max(quick,slow));
    }
    private static double lin(double[] x,double[] y,double t){
        for(int i=0;i+1<x.length;i++)if(t<=x[i+1]){double f=(t-x[i])/(x[i+1]-x[i]);return y[i]+(y[i+1]-y[i])*f;}
        return y[y.length-1];
    }

    // ------------------------------------------------------------------ the antlers and the woodland on them
    private static final double[][] BEAM={{0,0,0},{-2.5,7,4},{-6,17,10},{-6.5,28,16},{-3,38,20.5},{2,46,19.5},{6.5,52,15}};
    /** Tines: where along the beam (0..1), direction (forward, up, out), length. */
    private static final double[][] TINES={{.05,1,.45,.25,13},{.2,1,.7,.15,11},{.38,.8,.9,.2,12},{.52,-.2,.6,1,10},{.78,.3,1,.3,10},{.9,-.4,1,.6,9}};
    static Vec3 beam(Vec3 base,Vec3 a,Vec3 b,Vec3 c,double t){
        double f=t*(BEAM.length-1);int i=Math.min(BEAM.length-2,(int)Math.floor(f));double u=f-i;
        double[] p0=BEAM[Math.max(0,i-1)],p1=BEAM[i],p2=BEAM[i+1],p3=BEAM[Math.min(BEAM.length-1,i+2)];
        double[] q=new double[3];
        for(int k=0;k<3;k++)q[k]=.5*(2*p1[k]+(-p0[k]+p2[k])*u+(2*p0[k]-5*p1[k]+4*p2[k]-p3[k])*u*u+(-p0[k]+3*p1[k]-3*p2[k]+p3[k])*u*u*u);
        return base.add(a.scale(q[0])).add(b.scale(q[1])).add(c.scale(q[2]));
    }
    record Lamp(Vec3 at,double size){}
    static final List<Lamp> lamps=new ArrayList<>();
    private static void antlers(Out o,Pose p,int sides,double time,int quality){
        lamps.clear();
        Vec3 hr=p.ha.cross(p.hu).normalize();
        for(int s=-1;s<=1;s+=2){
            Vec3 c=hr.scale(s);
            Vec3 base=p.occiput.add(p.ha.scale(3.2)).add(p.hu.scale(3.4)).add(c.scale(2));
            List<Vec3> pts=new ArrayList<>();int n=20;double[] rad=new double[n+1];
            for(int i=0;i<=n;i++){double t=i/(double)n;pts.add(beam(base,p.ha,p.hu,c,t));rad[i]=Mth.lerp(Math.pow(t,.8),2.7,.7);}
            tube(o,pts,rad,sides,ANTLER,0,1,1);
            List<Vec3> perches=new ArrayList<>();
            for(double[] tn:TINES){
                Vec3 from=beam(base,p.ha,p.hu,c,tn[0]);
                Vec3 dir=p.ha.scale(tn[1]).add(p.hu.scale(tn[2])).add(c.scale(tn[3])).normalize();
                List<Vec3> tp=new ArrayList<>();double[] tr=new double[7];
                for(int i=0;i<=6;i++){double t=i/6.0;tp.add(from.add(dir.scale(t*tn[4])).add(UP.scale(t*t*tn[4]*.25)));tr[i]=Mth.lerp(t,Mth.lerp(tn[0],2.2,.9),.3);}
                tube(o,tp,tr,Math.max(5,sides-2),ANTLER,0,1,1);
                perches.add(tp.get(6));perches.add(tp.get(3));
            }
            perches.add(pts.get(n));perches.add(pts.get(n*3/4));perches.add(pts.get(n/2));
            // The woodland: little trees on the tines and along the beam, upright however the head is held.
            int trees=Math.min(perches.size(),quality==0?6:quality==1?9:11);
            for(int k=0;k<trees;k++){
                Vec3 at=perches.get(k*perches.size()/trees);
                double key=k*1.7+s*3.1,hgt=4.5+4*Math.abs(Math.sin(key*2.3));
                Vec3 top=at.add(Math.sin(key)*.9,hgt,Math.cos(key)*.9);
                tube(o,List.of(at.add(0,-.6,0),at.lerp(top,.5).add(Math.cos(key*3)*.4,0,Math.sin(key*3)*.4),top),new double[]{.62,.45,.22},6,TRUNK,0,1,1);
                if((k%3)!=1){
                    double w=2.6+1.2*Math.abs(Math.sin(key*1.9));
                    blob(o,top.add(0,.5,0),w,w*.8,w,LEAF,8,12,key,1);
                    for(int j=0;j<3;j++){double a=key*5+j*2.1;blob(o,top.add(Math.cos(a)*w*.6,-w*.35+j*.3,Math.sin(a)*w*.6),w*.62,w*.5,w*.62,LEAF,7,10,key+j+1,1);}
                }else{for(int j=0;j<4;j++)blob(o,top.add(0,-2.4+j*1.5,0),2.6-j*.55,1.15,2.6-j*.55,LEAF,6,12,key+j,1);}
            }
            // Moss trailing from the tines, swaying; a few small lamps hung in it.
            for(int k=0;k<9;k++){
                Vec3 at=perches.get((k*5+s+7)%perches.size());
                double len=5+6*Math.abs(Math.sin(k*1.3+s)),ph=time*.6+k+s;
                List<Vec3> strand=new ArrayList<>();double[] rr=new double[7];
                for(int i=0;i<=6;i++){double t=i/6.0;strand.add(at.add(Math.sin(ph+t*2)*t*1.4,-len*t,Math.cos(ph*.8+t*2)*t*1.4));rr[i]=Mth.lerp(t,.45,.12);}
                tube(o,strand,rr,4,MOSS,0,1,1);
                if(k%3==0){Vec3 l=strand.get(6).add(0,-.6,0);blob(o,l,.55,.7,.55,WARM,4,6,0,1);lamps.add(new Lamp(l,.8));}
            }
            // A little house with a lit window, set in the crotch of one antler.
            if(s<0){
                Vec3 at=beam(base,p.ha,p.hu,c,.62).add(0,.5,0);
                Vec3 f=new Vec3(p.ha.x,0,p.ha.z).normalize(),rt=new Vec3(-f.z,0,f.x);
                hut(o,at,f,rt);
                lamps.add(new Lamp(at.add(0,1.4,0).add(f.scale(1.3)),1.2));
            }
        }
    }
    private static void hut(Out o,Vec3 at,Vec3 f,Vec3 r){
        double w=1.5,h=1.6;
        Vec3[] c={at.add(f.scale(w)).add(r.scale(w)),at.add(f.scale(-w)).add(r.scale(w)),at.add(f.scale(-w)).add(r.scale(-w)),at.add(f.scale(w)).add(r.scale(-w))};
        for(int i=0;i<4;i++){
            Vec3 a=c[i],b=c[(i+1)%4],n=b.subtract(a).cross(UP).normalize().scale(-1);
            quad(o,a,b,b.add(0,h,0),a.add(0,h,0),n,n,n,n,0,0,1,1,TRUNK,1);
        }
        Vec3 apex=at.add(0,h+1.9,0);
        for(int i=0;i<4;i++){
            Vec3 a=c[i].add(0,h,0).add(c[i].subtract(at).scale(.35)),b=c[(i+1)%4].add(0,h,0).add(c[(i+1)%4].subtract(at).scale(.35));
            Vec3 n=b.subtract(a).cross(apex.subtract(a)).normalize();
            if(n.y<0)n=n.scale(-1);
            quad(o,a,b,apex,apex,n,n,n,n,0,0,1,1,LEAF,1);
        }
        // Well clear of the wall: the deer is seen from a few hundred blocks, where two cm of depth no longer separate.
        Vec3 m=at.add(f.scale(w+.12)).add(0,.8,0);
        quad(o,m.add(r.scale(-.45)).add(0,-.5,0),m.add(r.scale(.45)).add(0,-.5,0),m.add(r.scale(.45)).add(0,.4,0),m.add(r.scale(-.45)).add(0,.4,0),f,f,f,f,0,0,1,1,WARM,1);
    }
    private static void tail(Out o,Pose p,double time){
        Vec3 root=spine(p.hips,p.shoulders,p.h,-30).add(0,2,0);
        double flick=Math.sin(time*1.3)*.2;
        Vec3 tip=root.add(p.h.scale(-3.5)).add(0,-8,0).add(p.r.scale(flick*3));
        tube(o,List.of(root,root.lerp(tip,.5).add(p.h.scale(-.8)),tip),new double[]{2.3,1.9,.5},8,GLASS,.49,.5,1);
    }
    static void build(Out o,Pose p,int quality){
        int sides=quality==0?16:24,rings=quality==0?22:32;
        torso(o,p,rings,sides);
        tail(o,p,p.time);
        legs(o,p,quality==0?10:14);
        neckAndHead(o,p,quality==0?12:18,p.time);
        antlers(o,p,quality==0?6:8,p.time,quality);
    }

    // ------------------------------------------------------------------ drawing
    static void render(Matrix4f view,Matrix4f proj,Vec3 camera,float partial){
        Pose p=pose(partial);
        if(p==null||shader==null)return;
        var mc=Minecraft.getInstance();
        // Far off, the whole animal is brought inside the far plane at the same apparent size.
        double far=mc.gameRenderer.getDepthFar()*.85,dist=p.base.distanceTo(camera)+60;
        double k=Math.min(1,far/dist);
        BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        build((x,y,z,u,v,nx,ny,nz,kind,shade)->b.addVertex((float)((x-camera.x)*k),(float)((y-camera.y)*k),(float)((z-camera.z)*k)).setUv((float)u,(float)v)
            .setColor(shade,shade,shade,(kind+.5f)/8f).setNormal((float)nx,(float)ny,(float)nz),p,TideConfig.QUALITY.get());
        MeshData mesh=b.build();
        if(mesh==null)return;
        if(buffer==null)buffer=new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        buffer.bind();buffer.upload(mesh);VertexBuffer.unbind();
        double day=DreamClient.dayness(partial),distance=dist/k;
        shader.safeGetUniform("DeerLook").set((float)(p.time%3600),(float)day,(float)(.85+.15*TideConfig.BRIGHTNESS.get()),(float)Math.min(1,p.vis));
        float haze=(float)Math.min(.5,Math.max(0,(distance-90)/600));
        shader.safeGetUniform("DeerFog").set((float)Mth.lerp(day,.035,.62),(float)Mth.lerp(day,.1,.76),(float)Mth.lerp(day,.085,.62),haze);
        float angle=mc.level.getTimeOfDay(partial)*(float)Math.PI*2;
        Vec3 sun=new Vec3(-Math.sin(angle),Math.cos(angle),0);
        if(sun.y<0)sun=sun.scale(-1);
        shader.safeGetUniform("DeerSun").set((float)sun.x,(float)sun.y,(float)sun.z,(float)(1-day));
        RenderSystem.disableCull();RenderSystem.enableDepthTest();RenderSystem.depthFunc(515);
        halo(0,0);
        // The body never shows its own far side: depth first, then colour over only the nearest surface.
        RenderSystem.colorMask(false,false,false,false);RenderSystem.depthMask(true);RenderSystem.disableBlend();
        draw(view,proj);
        RenderSystem.colorMask(true,true,true,true);RenderSystem.depthMask(false);
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        draw(view,proj);
        // A soft rim of light about it, wider from far away: how you see it against the sky at all.
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ONE);
        halo((float)(Math.max(.3,Math.min(2.6,distance*.012))*k),(float)(.3*TideConfig.BRIGHTNESS.get()));
        draw(view,proj);
        halo(0,0);
        RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();RenderSystem.depthMask(true);RenderSystem.enableCull();
        lastPose=p;lastScale=k;lastCamera=camera;
    }
    static Pose lastPose;static double lastScale=1;static Vec3 lastCamera=Vec3.ZERO;
    private static void halo(float width,float strength){shader.safeGetUniform("DeerHalo").set(width,strength,0f,0f);}
    private static void draw(Matrix4f view,Matrix4f proj){buffer.bind();buffer.drawWithShader(view,proj,shader);VertexBuffer.unbind();}
    private DeerModel(){}
}
