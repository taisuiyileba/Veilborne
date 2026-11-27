package com.suiyi.veilborne.client;

import org.joml.Matrix4d;
import org.joml.Vector3d;

/**
 * The celestial humpback as a smooth lofted mesh in whale space: nose toward +x, back toward +y,
 * about 100 units from rostrum to fluke root. Proportions follow a humpback: broad flat rostrum,
 * deep pleated throat, laterally compressed tail stock, flippers a third of the body long with a
 * scalloped leading edge, and serrated flukes. It swims with vertical strokes as whales do.
 * UVs address the atlas painted by tools/art/whale.py.
 */
final class WhaleModel {
    interface Emitter { void vertex(double x,double y,double z,double u,double v,double nx,double ny,double nz,int argb); }

    static final double LENGTH=100,U0=-.03;
    // Profile from the nose cap (u<0) to the fluke root (u=1): half-heights above/below the axis, half-width, axis height.
    private static final double[] U ={-.03,-.022,-.012,0,.02,.05,.1,.15,.2,.25,.3,.35,.4,.45,.5,.55,.6,.65,.7,.75,.8,.85,.9,.95,1};
    private static final double[] HT={.1,1.0,1.7,2.3,3.2,4.4,5.7,6.7,7.5,8.2,8.8,9.2,9.5,9.6,9.5,9.2,8.7,8.0,7.1,6.1,5.0,4.0,3.1,2.4,1.8};
    private static final double[] HB={.1,1.4,2.4,3.2,4.3,5.8,7.7,9.2,10.3,11.0,11.4,11.6,11.5,11.1,10.4,9.4,8.2,7.0,5.9,4.9,4.0,3.3,2.7,2.2,1.7};
    private static final double[] W ={.1,1.8,3.0,3.9,5.0,6.4,7.9,8.9,9.6,10.1,10.5,10.7,10.7,10.4,9.9,9.1,8.1,7.0,5.8,4.6,3.5,2.6,1.9,1.4,1.1};
    private static final double[] C ={-1.2,-1.2,-1.2,-1.2,-1.1,-1.0,-.9,-.7,-.5,-.3,-.1,0,0,0,0,0,.1,.3,.5,.7,.9,1.0,1.0,.9,.8};
    private static final int RINGS=76,SIDES=44,SPAN=18,CHORD=9;
    private static final double[][][] P=new double[RINGS+1][SIDES+1][3],N=new double[RINGS+1][SIDES+1][3];
    private static final double[] RU=new double[RINGS+1],COS=new double[SIDES+1],SIN=new double[SIDES+1];
    private static final double[][][] FP=new double[SPAN+1][CHORD+1][3],FN=new double[SPAN+1][CHORD+1][3];
    private static final int WHITE=0xffffffff;
    static {
        for(int i=0;i<=RINGS;i++){double t=(double)i/RINGS;RU[i]=U0+(1-U0)*(.7*t+.3*(.5-.5*Math.cos(Math.PI*t)));}
    }

    static double smooth(double a,double b,double x){double t=Math.max(0,Math.min(1,(x-a)/(b-a)));return t*t*(3-2*t);}
    static double interp(double[] a,double u){
        int n=U.length;if(u<=U[0])return a[0];if(u>=U[n-1])return a[n-1];
        int i=0;while(u>U[i+1])i++;
        double t=(u-U[i])/(U[i+1]-U[i]);
        double p0=a[Math.max(0,i-1)],p1=a[i],p2=a[i+1],p3=a[Math.min(n-1,i+2)];
        return .5*(2*p1+(-p0+p2)*t+(2*p0-5*p1+4*p2-p3)*t*t+(-p0+3*p1-3*p2+p3)*t*t*t);
    }
    /** Vertical swimming wave: grows toward the tail, with a slight counter-bob of the head. */
    static double bend(double u,double time){
        double k=Math.max(0,(u-U0)/(1-U0));
        return 4.2*Math.pow(k,2.3)*Math.sin(time*.55-u*2.6)+.5*Math.pow(1-k,3)*Math.sin(time*.55+1.2);
    }
    static double slope(double u,double time){return (bend(u+.01,time)-bend(u-.01,time))/(.02*LENGTH);}
    private static double nTop(double u){double n=2.7+(2.1-2.7)*smooth(.1,.35,u);return n+(1.6-n)*smooth(.75,.97,u);}
    private static double nBot(double u){double n=2.3+(2.0-2.3)*smooth(.2,.5,u);return n+(1.65-n)*smooth(.75,.97,u);}
    static double x(double u){return LENGTH*(.5-u);}
    static void surface(double u,double theta,double time,double[] out){
        double c=Math.cos(theta),s=Math.sin(theta);
        boolean top=c>=0;
        double e=2/(top?nTop(u):nBot(u));
        double y=interp(C,u)+Math.signum(c)*Math.pow(Math.abs(c),e)*interp(top?HT:HB,u)+bend(u,time);
        if(top)y+=.8*Math.exp(-Math.pow((u-.64)/.035,2))*Math.pow(c,8);   // the hump under the dorsal fin
        out[0]=x(u);out[1]=y;out[2]=Math.signum(s)*Math.pow(Math.abs(s),e)*interp(W,u);
    }
    /** Body half-width at a given height, for draping chains along the flank. */
    static double halfWidthAt(double u,double y,double time){
        double axis=interp(C,u)+bend(u,time),dy=y-axis;
        double h=dy>=0?interp(HT,u):interp(HB,u),n=dy>=0?nTop(u):nBot(u);
        double r=Math.min(1,Math.abs(dy)/h);
        return interp(W,u)*Math.pow(Math.max(0,1-Math.pow(r,n)),1/n);
    }

    static void body(Emitter e,double time){body(e,time,1);}
    /** step 2 halves the ring and side count for the light quality setting. */
    static void body(Emitter e,double time,int step){
        // Profile values depend only on the ring, the superellipse terms only on the side: evaluate each once.
        for(int j=0;j<=SIDES;j++){double t=j*Math.PI*2/SIDES;COS[j]=Math.cos(t);SIN[j]=Math.sin(t);}
        for(int i=0;i<=RINGS;i++){
            double u=RU[i],axis=interp(C,u)+bend(u,time),ht=interp(HT,u),hb=interp(HB,u),w=interp(W,u),x=x(u);
            double et=2/nTop(u),eb=2/nBot(u),hump=.8*Math.exp(-Math.pow((u-.64)/.035,2));
            for(int j=0;j<=SIDES;j++){
                double c=COS[j],s=SIN[j],ex=c>=0?et:eb;
                double y=axis+Math.signum(c)*Math.pow(Math.abs(c),ex)*(c>=0?ht:hb);
                if(c>=0)y+=hump*Math.pow(c,8);
                double[] out=P[i][j];out[0]=x;out[1]=y;out[2]=Math.signum(s)*Math.pow(Math.abs(s),ex)*w;
            }
        }
        for(int i=0;i<=RINGS;i++)for(int j=0;j<=SIDES;j++){
            double[] a=P[Math.min(RINGS,i+1)][j],b=P[Math.max(0,i-1)][j];
            double[] c=P[i][(j+1)%SIDES],d=P[i][(j+SIDES-1)%SIDES];
            normal(b,a,d,c,N[i][j]);
            if(i==0){N[i][j][0]=1;N[i][j][1]=0;N[i][j][2]=0;}
        }
        for(int i=0;i<RINGS;i+=step){
            int i1=Math.min(RINGS,i+step);
            double u0=(RU[i]-U0)/(1-U0)*.75,u1=(RU[i1]-U0)/(1-U0)*.75;
            for(int j=0;j<SIDES;j+=step){
                int j1=Math.min(SIDES,j+step);
                double v0=(double)j/SIDES,v1=(double)j1/SIDES;
                put(e,P[i][j],N[i][j],u0,v0);put(e,P[i1][j],N[i1][j],u1,v0);
                put(e,P[i1][j1],N[i1][j1],u1,v1);put(e,P[i][j1],N[i][j1],u0,v1);
            }
        }
        dorsalFin(e,time);
        for(int side:new int[]{1,-1})flipper(e,side,time);
        for(int side:new int[]{1,-1})fluke(e,side,time);
    }
    /** n = normalize((a-b) x (c-d)): along-body tangent crossed with around-body tangent points outward. */
    private static void normal(double[] b,double[] a,double[] d,double[] c,double[] out){
        double ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2];     // toward the nose
        double vx=c[0]-d[0],vy=c[1]-d[1],vz=c[2]-d[2];     // around
        double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
        double l=Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(l<1e-9){out[0]=1;out[1]=0;out[2]=0;return;}
        out[0]=-nx/l;out[1]=-ny/l;out[2]=-nz/l;
    }
    private static void put(Emitter e,double[] p,double[] n,double u,double v){e.vertex(p[0],p[1],p[2],u,v,n[0],n[1],n[2],WHITE);}

    private static void dorsalFin(Emitter e,double time){
        double[] a=new double[3],b=new double[3];
        surface(.615,0,time,a);surface(.69,0,time,b);
        double tipU=.705;double[] t=new double[3];surface(tipU,0,time,t);t[1]+=3.1;t[0]-=1.2;
        for(int side:new int[]{1,-1}){
            double z=.45*side;
            double u=(.66-U0)/(1-U0)*.75;
            e.vertex(a[0],a[1]-.3,z,u-.01,.004,0,.2,side,WHITE);e.vertex(b[0],b[1]-.3,z,u+.01,.004,0,.2,side,WHITE);
            e.vertex(t[0],t[1],z*.2,u+.012,.001,0,.3,side,WHITE);e.vertex(t[0],t[1],z*.2,u+.012,.001,0,.3,side,WHITE);
        }
    }

    /** Long pectoral flipper: span out, back and down from the lower flank; scalloped leading edge. */
    private static void flipper(Emitter e,int side,double time){
        double[] root=new double[3];
        surface(.30,side>0?Math.PI*.64:Math.PI*1.36,time,root);
        Vector3d s=new Vector3d(-.55,-.42,side).normalize();
        Vector3d c=new Vector3d(1,-.1,0);c.sub(new Vector3d(s).mul(c.dot(s))).normalize();
        Vector3d t=new Vector3d(s).cross(c).normalize();if(t.y<0)t.negate();
        double flap=.2*Math.sin(time*.45+side*.5)+.05;
        s.rotateAxis(flap*side,c.x,c.y,c.z);t.rotateAxis(flap*side,c.x,c.y,c.z);
        for(int top=0;top<2;top++){
            double sign=top==0?1:-1;
            for(int a=0;a<=SPAN;a++){
                double sp=(double)a/SPAN,ch=7.0*Math.pow(1-sp,.55)+.9;
                double le=2.8+.45*Math.abs(Math.sin(sp*Math.PI*8))*smooth(.1,.2,sp);
                double th=(1.6*(1-sp)+.3)*.5,droop=-3.0*sp*sp;
                for(int b=0;b<=CHORD;b++){
                    double cp=(double)b/CHORD,along=le-cp*ch,thick=th*Math.pow(Math.sin(Math.PI*cp),.7)*sign;
                    double[] q=FP[a][b];
                    q[0]=root[0]+s.x*sp*30+c.x*along+t.x*thick;
                    q[1]=root[1]+s.y*sp*30+c.y*along+t.y*thick+droop;
                    q[2]=root[2]+s.z*sp*30+c.z*along+t.z*thick;
                }
            }
            surfaceGrid(e,sign*t.x,sign*t.y,sign*t.z,.75,.25,top==0?0:.25,.25);
        }
    }

    /** Flukes: swept, notched, serrated; they pitch with the stroke and flex at the tips. */
    private static void fluke(Emitter e,int side,double time){
        double[] root=new double[3];surface(1,0,time,root);
        double axis=interp(C,1)+bend(1,time);
        double pitch=Math.atan(slope(1,time)*1.6)+.12*Math.sin(time*.55-3.3);
        double cs=Math.cos(pitch),sn=Math.sin(pitch);
        for(int top=0;top<2;top++){
            double sign=top==0?1:-1;
            for(int a=0;a<=SPAN;a++){
                double sp=(double)a/SPAN;
                double le=-sp*sp*4-sp*5.5+1.2,ch=8.6*Math.pow(1-sp,.7)+.6;
                double te=le-ch+3.2*Math.exp(-Math.pow(sp/.12,2))+.35*Math.sin(sp*Math.PI*12)*smooth(.2,.3,sp);
                double th=(1.0*(1-sp)+.25)*.5,flex=1.3*sp*sp*Math.sin(time*.55-3.6);
                for(int b=0;b<=CHORD;b++){
                    double cp=(double)b/CHORD,lx=le+(te-le)*cp,ly=th*Math.pow(Math.sin(Math.PI*cp),.7)*sign+flex;
                    double[] q=FP[a][b];
                    q[0]=root[0]+lx*cs-ly*sn;q[1]=axis+lx*sn+ly*cs;q[2]=side*sp*17;
                }
            }
            surfaceGrid(e,-sn*sign,cs*sign,0,.75,.25,top==0?.5:.75,.25);
        }
    }
    /** Emits the FP grid with normals oriented toward (ox,oy,oz). Atlas: u0+du*span, v0+dv*chord. */
    private static void surfaceGrid(Emitter e,double ox,double oy,double oz,double u0,double du,double v0,double dv){
        for(int a=0;a<=SPAN;a++)for(int b=0;b<=CHORD;b++){
            double[] p1=FP[Math.min(SPAN,a+1)][b],p0=FP[Math.max(0,a-1)][b],q1=FP[a][Math.min(CHORD,b+1)],q0=FP[a][Math.max(0,b-1)];
            double ux=p1[0]-p0[0],uy=p1[1]-p0[1],uz=p1[2]-p0[2],vx=q1[0]-q0[0],vy=q1[1]-q0[1],vz=q1[2]-q0[2];
            double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx,l=Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(l<1e-9){nx=ox;ny=oy;nz=oz;l=Math.max(1e-9,Math.sqrt(ox*ox+oy*oy+oz*oz));}
            if(nx*ox+ny*oy+nz*oz<0)l=-l;
            FN[a][b][0]=nx/l;FN[a][b][1]=ny/l;FN[a][b][2]=nz/l;
        }
        for(int a=0;a<SPAN;a++)for(int b=0;b<CHORD;b++){
            double s0=u0+du*a/SPAN,s1=u0+du*(a+1)/SPAN,c0=v0+dv*b/CHORD*.999,c1=v0+dv*(b+1)/CHORD*.999;
            put(e,FP[a][b],FN[a][b],s0,c0);put(e,FP[a+1][b],FN[a+1][b],s1,c0);
            put(e,FP[a+1][b+1],FN[a+1][b+1],s1,c1);put(e,FP[a][b+1],FN[a][b+1],s0,c1);
        }
    }

    // ------------------------------------------------------------------ the wreck on its back
    private record Region(double u0,double v0,double u1,double v1,double tile){}
    private static final Region PLANK=new Region(0,0,.5,.5,5),SAIL=new Region(.5,0,1,.5,0),SPAR=new Region(0,.5,.5,.93,6),
        ROPE=new Region(0,.94,.5,1,24),PLAIN=new Region(.52,.52,.73,.73,0),LANTERN=new Region(.77,.52,.98,.73,0),BEAD=new Region(.77,.77,.98,.98,0);

    /** An old sloop grounded on the whale's back, masts askew, trailing star-strung kelp down both flanks. */
    static void wreck(Emitter e,double time){
        double u=.52;
        double back=interp(C,u)+interp(HT,u)+bend(u,time);
        double tilt=Math.atan(slope(u,time));
        Matrix4d m=new Matrix4d().translate(x(u),back-1.1,0).rotateZ(tilt-.06).rotateX(.21).rotateY(.13).scale(1.3);
        hull(e,m);
        box(e,m,-13,-.5,-.35,15.8,.3,.35,SPAR,WHITE);                                 // keel
        for(int r=0;r<4;r++)rib(e,m,-3.6+r*1.5);                                     // ribs showing through the breach
        box(e,m,-12.8,2.4,-3.2,-7.2,3.0,3.2,PLANK,WHITE);                             // stern deck
        box(e,m,-12,3.0,-2.4,-8,5.4,2.4,PLANK,0xffc9d6d2);                            // cabin
        for(int w=0;w<3;w++)box(e,m,-11.6+w*1.25,3.9,2.42,-11.0+w*1.25,4.6,2.5,LANTERN,WHITE);
        Matrix4d sprit=new Matrix4d(m).translate(12.5,2.2,0).rotateZ(.35);
        box(e,sprit,0,-.25,-.25,7,.25,.25,SPAR,WHITE);
        // Main mast leaning back, crow's nest, yard and a torn sail that still catches the current.
        Matrix4d mast=new Matrix4d(m).translate(1.5,0,0).rotateZ(.16).rotateX(-.1);
        box(e,mast,-.4,0,-.4,.4,17,.4,SPAR,WHITE);
        box(e,mast,-1.3,12.5,-1.3,1.3,13.4,1.3,SPAR,WHITE);
        box(e,mast,-.25,11.6,-6,.25,12.1,6,SPAR,WHITE);
        box(e,mast,-.35,16.2,-.35,.35,16.9,.35,LANTERN,WHITE);
        sail(e,mast,time);
        Matrix4d fore=new Matrix4d(m).translate(8,0,0).rotateZ(-.35).rotateX(.25);
        box(e,fore,-.35,0,-.35,.35,6.5,.35,SPAR,WHITE);
        box(e,fore,-.3,6.2,-.3,.3,7.4,.3,SPAR,0xff9fb0ad);
        // Rigging from the masthead to bow and stern.
        Vector3d head=mast.transformPosition(new Vector3d(0,16.5,0));
        rope(e,head,m.transformPosition(new Vector3d(17,2.8,0)),.07);
        rope(e,head,m.transformPosition(new Vector3d(-12.5,3.2,0)),.07);
        rope(e,mast.transformPosition(new Vector3d(0,11.8,5.8)),m.transformPosition(new Vector3d(1.5,3,4)),.05);
        rope(e,mast.transformPosition(new Vector3d(0,11.8,-5.8)),m.transformPosition(new Vector3d(1.5,3,-4)),.05);
        // Stern lanterns.
        for(int side:new int[]{1,-1})box(e,m,-13.2,3.4,side*2.6-.35,-12.5,4.3,side*2.6+.35,LANTERN,WHITE);
        // Kelp chains strung with star beads drape down the flanks, swaying in the sky-current.
        for(int k=0;k<7;k++){
            int side=k%2==0?1:-1;double ku=.45+k*.022;
            Vector3d start=m.transformPosition(new Vector3d(-9+k*3.1,1.8,side*4.2));
            kelp(e,start,ku,side,time,k);
        }
    }
    private static final int HULL_X=22,HULL_A=14;
    private static double hullBeam(double x){
        double t=(x+13)/28.5;                                   // 0 stern .. 1 bow
        return .3+4.3*Math.pow(Math.sin(Math.PI*Math.min(1,t*.62+.38)),.8)*(t<.1?.85+t*1.5:1);
    }
    private static double hullSheer(double x){return 3.2+.0012*(x+13)*(x+13)+(x>10?(x-10)*.25:0);}
    private static boolean breached(double x,double a){return x>-4.3&&x<1.6&&a>Math.PI*.62;}
    /** A lofted, planked hull: U-shaped sections from the flat transom to a pointed stem, one flank stove in. */
    private static void hull(Emitter e,Matrix4d m){
        double[][][] g=new double[HULL_X+1][HULL_A+1][3];
        for(int i=0;i<=HULL_X;i++){
            double x=-13+28.5*i/HULL_X,hb=hullBeam(x),sh=hullSheer(x);
            for(int j=0;j<=HULL_A;j++){
                double a=Math.PI*j/HULL_A;
                Vector3d p=m.transformPosition(new Vector3d(x,sh*(1-Math.pow(Math.sin(a),.55)),hb*Math.cos(a)));
                g[i][j][0]=p.x;g[i][j][1]=p.y;g[i][j][2]=p.z;
            }
        }
        for(int i=0;i<HULL_X;i++)for(int j=0;j<HULL_A;j++){
            double x=-13+28.5*(i+.5)/HULL_X,a=Math.PI*(j+.5)/HULL_A;
            if(breached(x,a))continue;
            double[] p0=g[i][j],p1=g[i+1][j],p2=g[i+1][j+1],p3=g[i][j+1];
            double ux=p1[0]-p0[0],uy=p1[1]-p0[1],uz=p1[2]-p0[2],vx=p3[0]-p0[0],vy=p3[1]-p0[1],vz=p3[2]-p0[2];
            Vector3d n=new Vector3d(uy*vz-uz*vy,uz*vx-ux*vz,ux*vy-uy*vx);
            if(n.lengthSquared()<1e-12)continue;
            n.normalize();
            double s0=(double)i/HULL_X,s1=(double)(i+1)/HULL_X,t0=(double)j/HULL_A,t1=(double)(j+1)/HULL_A;
            quad(e,new Vector3d(p0[0],p0[1],p0[2]),new Vector3d(p1[0],p1[1],p1[2]),new Vector3d(p2[0],p2[1],p2[2]),new Vector3d(p3[0],p3[1],p3[2]),n,PLANK,WHITE,s0,t0,s1,t1);
        }
        // Flat transom closing the stern, and gunwale rails along both sheers.
        double hb=hullBeam(-13),sh=hullSheer(-13);
        box(e,m,-13.3,0,-hb,-12.9,sh+.2,hb,PLANK,0xffd0dad6);
        for(int side:new int[]{1,-1})for(int i=0;i<HULL_X;i++){
            double x0=-13+28.5*i/HULL_X,x1=-13+28.5*(i+1)/HULL_X;
            if(side<0&&breached((x0+x1)/2,Math.PI))continue;
            Vector3d a=m.transformPosition(new Vector3d(x0,hullSheer(x0)+.15,side*hullBeam(x0)));
            Vector3d b=m.transformPosition(new Vector3d(x1,hullSheer(x1)+.15,side*hullBeam(x1)));
            ribbon(e,a,b,.22,SPAR,0xff9fb0ab);
        }
    }
    private static void rib(Emitter e,Matrix4d m,double x){
        double hb=hullBeam(x),sh=hullSheer(x);
        Vector3d prev=null;
        for(int j=0;j<=6;j++){
            double a=Math.PI*.5+Math.PI*.5*j/6;
            Vector3d p=m.transformPosition(new Vector3d(x,sh*(1-Math.pow(Math.sin(a),.55))+(j==6?.6:0),hb*Math.cos(a)*1.02));
            if(prev!=null)ribbon(e,prev,p,.2,SPAR,0xffb8c4c0);
            prev=p;
        }
    }
    private static void kelp(Emitter e,Vector3d start,double u,int side,double time,int seed){
        Vector3d prev=new Vector3d(start);
        int segments=16+seed%3*3;
        for(int j=1;j<=segments;j++){
            double y=start.y-j*1.05;
            double sway=Math.sin(time*.6+j*.45+seed)*(.35+j*.07);
            double z=side*(Math.max(Math.abs(prev.z),halfWidthAt(u,y,time)+.45)+Math.abs(sway)*.2);
            Vector3d next=new Vector3d(prev.x+sway*.3+.12,y,z);
            ribbon(e,prev,next,.17,PLAIN,0xff1f4744);
            if(j%3==0){double r=.26;box(e,null,next.x-r,next.y-r,next.z-r,next.x+r,next.y+r,next.z+r,BEAD,j%6==0?0xffffd9a0:0xffc8f6ff);}
            prev=next;
        }
    }
    private static void sail(Emitter e,Matrix4d mast,double time){
        int nz=6,ny=5;
        double[][][] g=new double[nz+1][ny+1][3];
        for(int i=0;i<=nz;i++)for(int j=0;j<=ny;j++){
            double zz=-5.4+10.8*i/nz,yy=11.6-7.5*j/ny;
            double billow=1.4*Math.sin(Math.PI*i/nz)*(j/(double)ny)+.35*Math.sin(time*1.1+i*.8+j*.6)*(j/(double)ny);
            Vector3d p=mast.transformPosition(new Vector3d(billow,yy,zz));
            g[i][j][0]=p.x;g[i][j][1]=p.y;g[i][j][2]=p.z;
        }
        Vector3d n=mast.transformDirection(new Vector3d(1,0,0)).normalize();
        double[] nn={n.x,n.y,n.z};
        for(int i=0;i<nz;i++)for(int j=0;j<ny;j++){
            double u0=lerp(SAIL.u0,SAIL.u1,(double)i/nz),u1=lerp(SAIL.u0,SAIL.u1,(double)(i+1)/nz);
            double v0=lerp(SAIL.v0,SAIL.v1,(double)j/ny),v1=lerp(SAIL.v0,SAIL.v1,(double)(j+1)/ny);
            put(e,g[i][j],nn,u0,v0);put(e,g[i+1][j],nn,u1,v0);put(e,g[i+1][j+1],nn,u1,v1);put(e,g[i][j+1],nn,u0,v1);
        }
    }
    private static double lerp(double a,double b,double t){return a+(b-a)*t;}
    private static double frac(double v){return v-Math.floor(v);}
    private static void rope(Emitter e,Vector3d a,Vector3d b,double w){ribbon(e,a,b,w,ROPE,0xffc0bb9a);}
    /** Two crossed quads along a segment, so thin strands read from any side. */
    private static void ribbon(Emitter e,Vector3d a,Vector3d b,double w,Region r,int color){
        Vector3d d=new Vector3d(b).sub(a);double len=d.length();if(len<1e-6)return;d.div(len);
        Vector3d s1=new Vector3d(d).cross(0,1,0);if(s1.lengthSquared()<1e-6)s1.set(1,0,0);s1.normalize(w);
        Vector3d s2=new Vector3d(d).cross(s1).normalize(w);
        for(Vector3d s:new Vector3d[]{s1,s2}){
            Vector3d n=new Vector3d(s).cross(d).normalize();
            quad(e,new Vector3d(a).add(s),new Vector3d(b).add(s),new Vector3d(b).sub(s),new Vector3d(a).sub(s),n,r,color,0,0,1,Math.max(1,len/Math.max(1,r.tile)));
        }
    }
    private static void box(Emitter e,Matrix4d m,double x0,double y0,double z0,double x1,double y1,double z1,Region r,int color){
        Vector3d[] c=new Vector3d[8];
        for(int i=0;i<8;i++){
            Vector3d v=new Vector3d((i&1)==0?x0:x1,(i&2)==0?y0:y1,(i&4)==0?z0:z1);
            c[i]=m==null?v:m.transformPosition(v);
        }
        double sx=x1-x0,sy=y1-y0,sz=z1-z0;
        int[][] faces={{0,4,6,2},{1,3,7,5},{0,1,5,4},{2,6,7,3},{0,2,3,1},{4,5,7,6}};
        double[][] dims={{sz,sy},{sz,sy},{sx,sz},{sx,sz},{sx,sy},{sx,sy}};
        double[][] normals={{-1,0,0},{1,0,0},{0,-1,0},{0,1,0},{0,0,-1},{0,0,1}};
        for(int f=0;f<6;f++){
            Vector3d n=new Vector3d(normals[f][0],normals[f][1],normals[f][2]);
            if(m!=null)m.transformDirection(n);
            n.normalize();
            double tu=r.tile>0?Math.min(1,dims[f][0]/r.tile):1,tv=r.tile>0?Math.min(1,dims[f][1]/r.tile):1;
            // Offset each face into the tile by its position so neighbouring planks don't repeat.
            double su=r.tile>0?(1-tu)*frac(Math.abs(x0*.37+z0*.61+f*.13)):0,sv=r.tile>0?(1-tv)*frac(Math.abs(y0*.53+x0*.29)):0;
            quad(e,c[faces[f][0]],c[faces[f][1]],c[faces[f][2]],c[faces[f][3]],n,r,color,su,sv,su+tu,sv+tv);
        }
    }
    private static void quad(Emitter e,Vector3d a,Vector3d b,Vector3d c,Vector3d d,Vector3d n,Region r,int color,double s0,double t0,double s1,double t1){
        double u0=lerp(r.u0,r.u1,s0),u1=lerp(r.u0,r.u1,Math.min(1,s1)),v0=lerp(r.v0,r.v1,t0),v1=lerp(r.v0,r.v1,Math.min(1,t1));
        e.vertex(a.x,a.y,a.z,u0,v0,n.x,n.y,n.z,color);e.vertex(b.x,b.y,b.z,u1,v0,n.x,n.y,n.z,color);
        e.vertex(c.x,c.y,c.z,u1,v1,n.x,n.y,n.z,color);e.vertex(d.x,d.y,d.z,u0,v1,n.x,n.y,n.z,color);
    }
    private WhaleModel(){}
}
