package com.suiyi.veilborne.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.suiyi.veilborne.MirrorTimeline;

/**
 * 巡礼者: the colossus that walks through the inverted land. Pilgrim's clothes at the scale of a hill: a broad conical
 * hat with curtains of glass beads hiding the face, layered pale robes with gold and celadon hems, wide hanging
 * sleeves, a ringed monk's staff with a paper lantern in the right hand and a round bronze mirror in the left.
 *
 * Built in its own space (feet at the origin, facing +x, right hand toward +z, about 84 blocks tall) and seen only
 * reflected, standing on the mirror image of the real ground. Walking is a slow glide: the hem swings forward over
 * each hidden step, the body rises between footfalls, the beads and staff rings sway. Standing over the watcher it
 * tips its face up (which, reflected, is down toward them) and lifts the mirror.
 * Lighting is baked into vertex colours; the shared mirror shader adds grain, grade and haze.
 */
final class PilgrimModel {
    interface Out { void vertex(double x,double y,double z,int rgb,int kind); }
    private static final double[][] ROBE={
        // y, half depth (front-back), half width, forward offset
        {.4,9.6,11.2,0},{2.5,9.2,10.8,0},{7,8.2,9.8,0},{14,7,8.5,.2},{21,6,7.5,.4},{27,5.2,6.8,.6},{30,4.9,6.5,.7},
        {34,5.3,7.0,.8},{40,5.8,7.7,.9},{45,5.8,8.3,.9},{49,4.9,8.1,.8},{51.2,3.2,5.4,.7},{52.6,2.3,2.8,.6}};
    private static final int SIDES=40;
    /** Built about 84 tall; drawn at {@link MirrorTimeline#HEIGHT}. */
    static final double SCALE=MirrorTimeline.HEIGHT/84.0;
    // 青金与浅灰: a lapis robe under a sheer pale grey one, gold at every edge, celadon at the hem.
    static final int ROBE_LIGHT=0x3a5896,ROBE_SHADE=0x223868,VEIL=0xc3cad4,HEM_GOLD=0xe2b95e,HEM_CELADON=0x86c9c2,SASH=0xc9a24e,
        HAT=0xc9ae78,HAT_UNDER=0x4a3d2c,BEAD=0xa8eef6,STAFF=0x5d4a30,RING=0xe6c46a,LANTERN=0xffd08a,MIRROR_BACK=0x8c6b3a,MIRROR_FACE=0xeafaff,SKIN=0xe8dccd,VOID=0x0d1016;

    /** Pose: {@code s} blocks walked, {@code gaze} 0..1 standing and looking up, {@code time} seconds. */
    static void build(Out out,double s,double gaze,double time,int quality){
        double phase=MirrorTimeline.stepPhase(s);
        double swing=Math.sin(Math.PI*phase);          // + right leg forward, - left leg forward
        double lift=Math.abs(swing);
        double walking=1-gaze;
        double bob=.9*lift*walking;
        double lean=(.06*walking-.12*gaze);            // radians about z: forward is positive
        int sides=quality==0?28:SIDES;
        robe(out,swing*walking,bob,lean,time,sides);
        sleeves(out,swing*walking,bob,lean,gaze,time);
        head(out,bob,lean,gaze,time,sides,phase,walking,quality);
        staff(out,swing*walking,bob,lean,gaze,time,phase);
        mirror(out,bob,lean,gaze,time);
    }

    // ------------------------------------------------------------------ helpers
    /** Rotates a point about the z axis (forward lean) around height {@code pivot}. */
    private static double[] tilt(double x,double y,double z,double lean,double pivot){
        double c=Math.cos(lean),sn=Math.sin(lean),dy=y-pivot;
        return new double[]{x*c+dy*sn,pivot-x*sn+dy*c,z};
    }
    private static final double[] L=norm(.35,.85,.4);
    private static double[] norm(double x,double y,double z){double l=Math.sqrt(x*x+y*y+z*z);return new double[]{x/l,y/l,z/l};}
    /** Diffuse from above and ahead, with a silver rim where the surface turns away from the watcher overhead. */
    static int lit(int rgb,double nx,double ny,double nz,double ambient){
        double d=Math.max(0,nx*L[0]+ny*L[1]+nz*L[2]);
        double k=ambient+(1-ambient)*d;
        double rim=Math.pow(1-Math.abs(ny),3)*.3;
        int r=(int)Math.min(255,(rgb>>16&255)*k+200*rim),g=(int)Math.min(255,(rgb>>8&255)*k+225*rim),b=(int)Math.min(255,(rgb&255)*k+240*rim);
        return r<<16|g<<8|b;
    }
    private static int mix(int a,int b,double t){
        return (int)((a>>16&255)*(1-t)+(b>>16&255)*t)<<16|(int)((a>>8&255)*(1-t)+(b>>8&255)*t)<<8|(int)((a&255)*(1-t)+(b&255)*t);
    }
    private static void quad(Out o,double[] a,double[] b,double[] c,double[] d,int ca,int cb,int cc,int cd,int kind){
        o.vertex(a[0],a[1],a[2],ca,kind);o.vertex(b[0],b[1],b[2],cb,kind);o.vertex(c[0],c[1],c[2],cc,kind);o.vertex(d[0],d[1],d[2],cd,kind);
    }

    // ------------------------------------------------------------------ robe
    private static void robe(Out out,double swing,double bob,double lean,double time,int sides){
        int rings=ROBE.length;
        double[][][] p=new double[rings][sides+1][];int[][] c=new int[rings][sides+1];
        for(int i=0;i<rings;i++){
            double y=ROBE[i][0],rx=ROBE[i][1],rz=ROBE[i][2],fx=ROBE[i][3];
            double low=1-Math.min(1,y/30);
            for(int k=0;k<=sides;k++){
                double th=k*Math.PI*2/sides,cs=Math.cos(th),sn=Math.sin(th);
                double fold=1+.075*Math.sin(th*9+y*.12)*low+.03*Math.sin(th*23-y*.3)*low;
                double hem=low*low*(.35*Math.sin(th*5+time*.6)+swing*2.2*Math.max(0,cs)*(sn*swing>0?1:.4));
                double x=cs*rx*fold+fx+hem*Math.max(0,cs),z=sn*rz*fold;
                double[] v=tilt(x,y+bob,z,lean,1);
                p[i][k]=v;
                boolean gold=y<2.6,celadon=y>=2.6&&y<7.5&&i==2,sash=y>=29&&y<=31;
                int base=gold?HEM_GOLD:celadon?HEM_CELADON:sash?SASH:mix(ROBE_LIGHT,ROBE_SHADE,.5+.5*Math.sin(th*9+y*.12)*low);
                c[i][k]=lit(base,cs/rx,.12,sn/rz,.5);
            }
        }
        for(int i=0;i+1<rings;i++){
            // The gold hem glows faintly, so at night the figure is drawn in its edges.
            int kind=ROBE[i+1][0]<=2.6?MirrorWorld.PLAIN_LIGHT:MirrorWorld.PLAIN;
            for(int k=0;k<sides;k++)quad(out,p[i][k],p[i][k+1],p[i+1][k+1],p[i+1][k],c[i][k],c[i][k+1],c[i+1][k+1],c[i+1][k],kind);
        }
        // A second, sheer outer layer from the shoulders to the knee, open at the front.
        for(int i=0;i<5;i++){
            double y0=46-i*7,y1=y0-7;
            for(int k=0;k<sides;k++){
                double t0=k*Math.PI*2/sides,t1=(k+1)*Math.PI*2/sides;
                if(Math.cos(t0)>.72)continue;
                double[][] q=new double[4][];
                double[] ys={y0,y0,y1,y1},ts={t0,t1,t1,t0};
                for(int n=0;n<4;n++){
                    double r=1.12+.03*i,yy=ys[n];
                    double rx=interp(yy,1)*r,rz=interp(yy,2)*r,sway=Math.sin(time*.5+ts[n]*3)*.25*(46-yy)/35;
                    q[n]=tilt(Math.cos(ts[n])*rx+sway,yy+bob,Math.sin(ts[n])*rz,lean,1);
                }
                // Its front edges are gold.
                boolean edge=Math.cos(t0)>.62||Math.cos(t1)>.62;
                int col=lit(edge?HEM_GOLD:VEIL,Math.cos(t0),.1,Math.sin(t0),.55);
                quad(out,q[0],q[1],q[2],q[3],col,col,col,col,MirrorWorld.PLAIN);
            }
        }
    }
    private static double interp(double y,int col){
        for(int i=0;i+1<ROBE.length;i++)if(y<=ROBE[i+1][0]){double t=(y-ROBE[i][0])/(ROBE[i+1][0]-ROBE[i][0]);return ROBE[i][col]+(ROBE[i+1][col]-ROBE[i][col])*Math.max(0,Math.min(1,t));}
        return ROBE[ROBE.length-1][col];
    }

    // ------------------------------------------------------------------ sleeves and hands
    private static void sleeves(Out out,double swing,double bob,double lean,double gaze,double time){
        for(int side=-1;side<=1;side+=2){
            boolean right=side>0;
            double arm=right?-swing*.08:swing*.08;
            // Shoulder, elbow and wrist. The right hand holds the staff; the left lifts the mirror when it looks up.
            double lift=right?0:gaze;
            double[] sh={.8,49,side*7.4};
            double[] el={1.5+arm*6+lift*4,38+lift*8,side*(8.6-lift*2)};
            double[] wr={right?5.6:4.2+arm*8+lift*8,right?29:29+lift*31,side*(right?8.8:7.2-lift*4)};
            double[][] path={sh,el,wr};
            double[] radius={2.3,2.9,4.4};
            int seg=12;
            double[][] prev=null;int[] prevC=null;
            for(int j=0;j<=2*4;j++){
                double t=j/8.0;int a=Math.min(1,(int)(t*2));double u=t*2-a;
                double[] c=lerp(path[a],path[a+1],u);
                double r=radius[a]+(radius[a+1]-radius[a])*u;
                double[] dir=sub(path[a+1],path[a]);double dl=len(dir);dir=new double[]{dir[0]/dl,dir[1]/dl,dir[2]/dl};
                double[] e1=normalize(cross(dir,new double[]{0,0,1}));double[] e2=cross(dir,e1);
                double[][] ring=new double[seg+1][];int[] col=new int[seg+1];
                for(int k=0;k<=seg;k++){
                    double th=k*Math.PI*2/seg,cs=Math.cos(th),sn=Math.sin(th);
                    double droop=u*(a==1?1:0)*Math.max(0,-e1[1]*cs-e2[1]*sn)*1.8;
                    double[] v={c[0]+(e1[0]*cs+e2[0]*sn)*r,c[1]+(e1[1]*cs+e2[1]*sn)*r-droop,c[2]+(e1[2]*cs+e2[2]*sn)*r};
                    ring[k]=tilt(v[0],v[1]+bob,v[2],lean,1);
                    boolean cuff=j>=7;
                    col[k]=lit(cuff?HEM_GOLD:VEIL,e1[0]*cs+e2[0]*sn,e1[1]*cs+e2[1]*sn,e1[2]*cs+e2[2]*sn,.52);
                }
                int kind=j>=8?MirrorWorld.PLAIN_LIGHT:MirrorWorld.PLAIN;
                if(prev!=null)for(int k=0;k<seg;k++)quad(out,prev[k],prev[k+1],ring[k+1],ring[k],prevC[k],prevC[k+1],col[k+1],col[k],kind);
                prev=ring;prevC=col;
            }
            sphere(out,tilt(wr[0]+.6,wr[1]-1.2+bob,wr[2],lean,1),1.3,SKIN,MirrorWorld.PLAIN,8);
        }
    }

    // ------------------------------------------------------------------ head, hat and bead curtains
    private static void head(Out out,double bob,double lean,double gaze,double time,int sides,double phase,double walking,int quality){
        double nod=.05*Math.sin(Math.PI*phase*2)*walking;
        double up=gaze*.6;                        // tip the face up: reflected, down toward the watcher beside it
        double[] neck={.6,52.6+bob,0};
        java.util.function.Function<double[],double[]> headT=v->{
            double c=Math.cos(up+nod),s=Math.sin(up+nod);
            double x=v[0],y=v[1]-52.6,z=v[2];
            // Tipping back rotates the head about the neck: forward (+x) goes up.
            double[] r={x*c-y*s,x*s+y*c,z};
            return tilt(neck[0]+r[0],neck[1]+r[1],r[2],lean,1);
        };
        // The face: nothing but shadow under the brim.
        ellipsoid(out,headT,new double[]{.8,55.5,0},new double[]{3.2,3.8,3.0},VOID,MirrorWorld.PLAIN,10);
        // 斗笠: a broad woven cone, pale gold on top and dark beneath.
        double brim=13.2,apex=67.5,rim=58.4;
        for(int k=0;k<sides;k++){
            double t0=k*Math.PI*2/sides,t1=(k+1)*Math.PI*2/sides;
            double[] a=headT.apply(new double[]{.8,apex,0});
            double w0=Math.sin(t0*6+time*.3)*.25,w1=Math.sin(t1*6+time*.3)*.25;
            double[] b=headT.apply(new double[]{.8+Math.cos(t0)*brim,rim+w0,Math.sin(t0)*brim});
            double[] c=headT.apply(new double[]{.8+Math.cos(t1)*brim,rim+w1,Math.sin(t1)*brim});
            double weave=.92+.08*((k&1)==0?1:-1);
            int top=lit(mix(HAT,0xa98f5d,((k/3)&1)*.4),Math.cos(t0)*.45,.9,Math.sin(t0)*.45,.55);
            int topScaled=scale(top,weave);
            out.vertex(a[0],a[1],a[2],topScaled,MirrorWorld.PLAIN);out.vertex(a[0],a[1],a[2],topScaled,MirrorWorld.PLAIN);
            out.vertex(c[0],c[1],c[2],topScaled,MirrorWorld.PLAIN);out.vertex(b[0],b[1],b[2],topScaled,MirrorWorld.PLAIN);
            double[] d=headT.apply(new double[]{.8,apex-1.4,0});
            out.vertex(d[0],d[1],d[2],HAT_UNDER,MirrorWorld.PLAIN);out.vertex(d[0],d[1],d[2],HAT_UNDER,MirrorWorld.PLAIN);
            out.vertex(b[0],b[1]-.2,b[2],HAT_UNDER,MirrorWorld.PLAIN);out.vertex(c[0],c[1]-.2,c[2],HAT_UNDER,MirrorWorld.PLAIN);
        }
        // Curtains of glass beads from the brim, swinging with the stride. They glow faintly.
        int strings=quality==0?20:30,beads=quality==0?5:7;
        for(int k=0;k<strings;k++){
            double th=k*Math.PI*2/strings+.05;
            double sway=Math.sin(time*.9+k*.7)*.35+Math.sin(Math.PI*phase)*walking*.6;
            for(int n=0;n<beads;n++){
                double drop=1.4+n*1.35;
                double r=brim*.93;
                double[] v=headT.apply(new double[]{.8+Math.cos(th)*r+sway*drop*.08,rim-drop,Math.sin(th)*r});
                bead(out,v,.42,BEAD);
            }
        }
    }
    private static void bead(Out out,double[] c,double r,int rgb){
        double[][] v={{c[0]+r,c[1],c[2]},{c[0],c[1]+r*1.3,c[2]},{c[0]-r,c[1],c[2]},{c[0],c[1]-r*1.3,c[2]},{c[0],c[1],c[2]+r},{c[0],c[1],c[2]-r}};
        int[][] f={{0,1,4},{1,2,4},{2,3,4},{3,0,4},{1,0,5},{2,1,5},{3,2,5},{0,3,5}};
        for(int[] t:f){
            int col=scale(rgb,t[2]==4?1:.8);
            out.vertex(v[t[0]][0],v[t[0]][1],v[t[0]][2],col,MirrorWorld.PLAIN_LIGHT);out.vertex(v[t[1]][0],v[t[1]][1],v[t[1]][2],col,MirrorWorld.PLAIN_LIGHT);
            out.vertex(v[t[2]][0],v[t[2]][1],v[t[2]][2],col,MirrorWorld.PLAIN_LIGHT);out.vertex(v[t[2]][0],v[t[2]][1],v[t[2]][2],col,MirrorWorld.PLAIN_LIGHT);
        }
    }

    // ------------------------------------------------------------------ the ringed staff and its lantern
    private static void staff(Out out,double swing,double bob,double lean,double gaze,double time,double phase){
        double a=swing*.1-gaze*.05;                 // swings a little forward and back with the stride
        double[] foot={5.6+Math.sin(a)*29,0,9.2},top={5.6-Math.sin(a)*52,82,9.2};
        cylinder(out,tilt(foot[0],foot[1]+bob*.3,foot[2],lean,1),tilt(top[0],top[1]+bob,top[2],lean,1),.5,STAFF,8);
        double[] head=tilt(top[0],top[1]+bob,top[2],lean,1);
        // The ring head: a tall loop with six loose rings that jingle at each step.
        torus(out,new double[]{head[0],head[1]+3.2,head[2]},3.2,.35,0,RING,MirrorWorld.PLAIN,18);
        double jingle=Math.exp(-5*(phase-Math.floor(phase)))*.4;
        for(int i=0;i<6;i++){
            double ang=(i-2.5)*.42+Math.sin(time*2+i)*.05+jingle*Math.sin(i*2.1+time*8);
            double[] c={head[0]+Math.sin(ang)*3.2,head[1]+3.2-Math.cos(ang)*3.2-1.1,head[2]};
            torus(out,c,1.05,.18,1,RING,MirrorWorld.PLAIN,10);
        }
        // A paper lantern hung from a short crook below the rings.
        double[] crook={head[0]+.2,head[1]-6,head[2]+2.2};
        cylinder(out,new double[]{head[0],head[1]-6,head[2]},crook,.25,STAFF,5);
        double swingL=Math.sin(time*.8)*.3+Math.sin(Math.PI*phase)*.2;
        double[] lan={crook[0]+swingL,crook[1]-3.2,crook[2]};
        cylinder(out,crook,new double[]{lan[0],lan[1]+2,lan[2]},.12,STAFF,4);
        ellipsoid(out,v->v,lan,new double[]{1.6,2.3,1.6},LANTERN,MirrorWorld.PLAIN_LIGHT,10);
    }
    /** Where the lantern hangs, in the pilgrim's own space (for the glow drawn over the mirror). */
    static double[] lantern(double s,double gaze,double time){
        double phase=MirrorTimeline.stepPhase(s),walking=1-gaze,swing=Math.sin(Math.PI*phase)*walking,bob=.9*Math.abs(Math.sin(Math.PI*phase))*walking;
        double lean=.06*walking-.12*gaze,a=swing*.1-gaze*.05;
        double[] head=tilt(5.6-Math.sin(a)*52,82+bob,9.2,lean,1);
        return new double[]{head[0]+.2+Math.sin(time*.8)*.3,head[1]-9.2,head[2]+2.2};
    }

    // ------------------------------------------------------------------ the bronze mirror
    private static void mirror(Out out,double bob,double lean,double gaze,double time){
        double[] c=mirrorCenter(bob,lean,gaze);
        // Carried face-down at the waist; held out before the face when it stops, turned to the one it looks at.
        double tip=-Math.PI/2+gaze*(Math.PI/2+.6);
        double[] n={Math.cos(tip),Math.sin(tip),-.2*(1-gaze)};n=normalize(n);
        double[] e1=normalize(cross(n,new double[]{0,0,1}));if(len(e1)<.1)e1=new double[]{1,0,0};double[] e2=cross(n,e1);
        int seg=22;double r=3.6;
        for(int k=0;k<seg;k++){
            double t0=k*Math.PI*2/seg,t1=(k+1)*Math.PI*2/seg;
            double[] a={c[0]+(e1[0]*Math.cos(t0)+e2[0]*Math.sin(t0))*r,c[1]+(e1[1]*Math.cos(t0)+e2[1]*Math.sin(t0))*r,c[2]+(e1[2]*Math.cos(t0)+e2[2]*Math.sin(t0))*r};
            double[] b={c[0]+(e1[0]*Math.cos(t1)+e2[0]*Math.sin(t1))*r,c[1]+(e1[1]*Math.cos(t1)+e2[1]*Math.sin(t1))*r,c[2]+(e1[2]*Math.cos(t1)+e2[2]*Math.sin(t1))*r};
            double[] fc={c[0]+n[0]*.2,c[1]+n[1]*.2,c[2]+n[2]*.2},bc={c[0]-n[0]*.3,c[1]-n[1]*.3,c[2]-n[2]*.3};
            out.vertex(fc[0],fc[1],fc[2],MIRROR_FACE,MirrorWorld.PLAIN_LIGHT);out.vertex(fc[0],fc[1],fc[2],MIRROR_FACE,MirrorWorld.PLAIN_LIGHT);
            out.vertex(b[0]+n[0]*.2,b[1]+n[1]*.2,b[2]+n[2]*.2,scale(MIRROR_FACE,.85),MirrorWorld.PLAIN_LIGHT);out.vertex(a[0]+n[0]*.2,a[1]+n[1]*.2,a[2]+n[2]*.2,scale(MIRROR_FACE,.85),MirrorWorld.PLAIN_LIGHT);
            int back=lit(MIRROR_BACK,-n[0],-n[1],-n[2],.5);
            out.vertex(bc[0],bc[1],bc[2],back,MirrorWorld.PLAIN);out.vertex(bc[0],bc[1],bc[2],back,MirrorWorld.PLAIN);
            out.vertex(a[0],a[1],a[2],back,MirrorWorld.PLAIN);out.vertex(b[0],b[1],b[2],back,MirrorWorld.PLAIN);
            quad(out,a,b,new double[]{b[0]+n[0]*.2,b[1]+n[1]*.2,b[2]+n[2]*.2},new double[]{a[0]+n[0]*.2,a[1]+n[1]*.2,a[2]+n[2]*.2},RING,RING,RING,RING,MirrorWorld.PLAIN);
        }
    }
    static double[] mirrorCenter(double bob,double lean,double gaze){
        return tilt(5.2+gaze*9.5,27.5+gaze*34+bob,-6.6+gaze*3.2,lean,1);
    }
    static double[] mirrorCenter(double s,double gaze){
        double phase=MirrorTimeline.stepPhase(s),walking=1-gaze;
        return mirrorCenter(.9*Math.abs(Math.sin(Math.PI*phase))*walking,.06*walking-.12*gaze,gaze);
    }

    // ------------------------------------------------------------------ primitives
    private static void sphere(Out out,double[] c,double r,int rgb,int kind,int seg){ellipsoid(out,v->v,c,new double[]{r,r,r},rgb,kind,seg);}
    private static void ellipsoid(Out out,java.util.function.Function<double[],double[]> t,double[] c,double[] r,int rgb,int kind,int seg){
        for(int i=0;i<seg/2;i++)for(int k=0;k<seg;k++){
            double[][] q=new double[4][];int[] col=new int[4];
            int[][] ij={{i,k},{i,k+1},{i+1,k+1},{i+1,k}};
            for(int n=0;n<4;n++){
                double ph=Math.PI*ij[n][0]/(seg/2.0)-Math.PI/2,th=Math.PI*2*ij[n][1]/seg;
                double nx=Math.cos(ph)*Math.cos(th),ny=Math.sin(ph),nz=Math.cos(ph)*Math.sin(th);
                q[n]=t.apply(new double[]{c[0]+nx*r[0],c[1]+ny*r[1],c[2]+nz*r[2]});
                col[n]=kind==MirrorWorld.PLAIN_LIGHT?scale(rgb,.8+.2*ny):lit(rgb,nx,ny,nz,.45);
            }
            quad(out,q[0],q[1],q[2],q[3],col[0],col[1],col[2],col[3],kind);
        }
    }
    private static void cylinder(Out out,double[] a,double[] b,double r,int rgb,int seg){
        double[] d=normalize(sub(b,a));double[] e1=normalize(cross(d,Math.abs(d[1])>.9?new double[]{1,0,0}:new double[]{0,1,0}));double[] e2=cross(d,e1);
        for(int k=0;k<seg;k++){
            double t0=k*Math.PI*2/seg,t1=(k+1)*Math.PI*2/seg;
            double[] o0={(e1[0]*Math.cos(t0)+e2[0]*Math.sin(t0))*r,(e1[1]*Math.cos(t0)+e2[1]*Math.sin(t0))*r,(e1[2]*Math.cos(t0)+e2[2]*Math.sin(t0))*r};
            double[] o1={(e1[0]*Math.cos(t1)+e2[0]*Math.sin(t1))*r,(e1[1]*Math.cos(t1)+e2[1]*Math.sin(t1))*r,(e1[2]*Math.cos(t1)+e2[2]*Math.sin(t1))*r};
            int c0=lit(rgb,o0[0]/r,o0[1]/r,o0[2]/r,.45),c1=lit(rgb,o1[0]/r,o1[1]/r,o1[2]/r,.45);
            quad(out,add(a,o0),add(a,o1),add(b,o1),add(b,o0),c0,c1,c1,c0,MirrorWorld.PLAIN);
        }
    }
    /** A ring in the plane spanned by x/y ({@code plane} 0) or turned side-on ({@code plane} 1). */
    private static void torus(Out out,double[] c,double R,double r,int plane,int rgb,int kind,int seg){
        int tube=5;
        for(int i=0;i<seg;i++)for(int j=0;j<tube;j++){
            double[][] q=new double[4][];int[] col=new int[4];
            int[][] ij={{i,j},{i+1,j},{i+1,j+1},{i,j+1}};
            for(int n=0;n<4;n++){
                double u=Math.PI*2*ij[n][0]/seg,v=Math.PI*2*ij[n][1]/tube;
                double rr=R+r*Math.cos(v);
                double x=Math.cos(u)*rr,y=Math.sin(u)*rr,z=r*Math.sin(v);
                double nx=Math.cos(u)*Math.cos(v),ny=Math.sin(u)*Math.cos(v),nz=Math.sin(v);
                if(plane==1){double t=x;x=z;z=t;t=nx;nx=nz;nz=t;}
                q[n]=new double[]{c[0]+x,c[1]+y,c[2]+z};col[n]=lit(rgb,nx,ny,nz,.5);
            }
            quad(out,q[0],q[1],q[2],q[3],col[0],col[1],col[2],col[3],kind);
        }
    }
    private static int scale(int c,double f){return (int)Math.min(255,(c>>16&255)*f)<<16|(int)Math.min(255,(c>>8&255)*f)<<8|(int)Math.min(255,(c&255)*f);}
    private static double[] lerp(double[] a,double[] b,double t){return new double[]{a[0]+(b[0]-a[0])*t,a[1]+(b[1]-a[1])*t,a[2]+(b[2]-a[2])*t};}
    private static double[] sub(double[] a,double[] b){return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    private static double[] add(double[] a,double[] b){return new double[]{a[0]+b[0],a[1]+b[1],a[2]+b[2]};}
    private static double len(double[] a){return Math.sqrt(a[0]*a[0]+a[1]*a[1]+a[2]*a[2]);}
    private static double[] normalize(double[] a){double l=len(a);return l<1e-9?new double[]{0,1,0}:new double[]{a[0]/l,a[1]/l,a[2]/l};}
    private static double[] cross(double[] a,double[] b){return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    /** Writes the model into a buffer for the mirror shader: untextured, lit in its colours. */
    static void write(BufferBuilder b,double s,double gaze,double time,int quality){
        build((x,y,z,rgb,kind)->b.addVertex((float)x,(float)y,(float)z).setUv(0,0).setColor((rgb>>16&255)/255f,(rgb>>8&255)/255f,(rgb&255)/255f,1f).setNormal(0,1,kind/127f),s,gaze,time,quality);
    }
    private PilgrimModel(){}
}
