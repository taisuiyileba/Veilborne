package com.suiyi.veilborne.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.suiyi.veilborne.Veilborne;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import org.joml.Vector3f;

/**
 * The shoreless skiff and its fisherman, authored as box geometry by tools/art/skiff.py.
 * Parts and texture layout come from geometry/skiff.json so the model and its texture stay in step.
 */
final class SkiffModel {
    static final ModelLayerLocation LAYER=new ModelLayerLocation(Veilborne.id("tide_skiff"),"main");
    private static final String PATH="assets/veilborne/geometry/skiff.json";
    private static final Map<String,String> PARENTS=new LinkedHashMap<>();
    final ModelPart root;
    private final Map<String,ModelPart> parts=new HashMap<>();

    SkiffModel(ModelPart root){
        this.root=root;
        for(String name:PARENTS.keySet())parts.put(name,resolve(name));
    }
    private ModelPart resolve(String name){
        Deque<String> path=new ArrayDeque<>();
        for(String n=name;n!=null&&!n.equals("root");n=PARENTS.get(n))path.push(n);
        ModelPart part=root;
        for(String n:path)part=part.getChild(n);
        return part;
    }
    ModelPart part(String name){return parts.get(name);}

    /** Gentle idle motion: the lantern swings, the rod dips, the fisherman breathes. */
    void animate(double time,double bite){
        for(ModelPart p:parts.values())p.resetPose();
        nudge("lantern",Math.sin(time*1.3)*.09,0,Math.sin(time*.9+.6)*.11);
        nudge("pole",Math.sin(time*.8)*.012,0,0);
        nudge("rod",Math.sin(time*.7)*.03+bite*.09,Math.sin(time*.45)*.02,0);
        nudge("arm_r",Math.sin(time*.7)*.03+bite*.07,0,0);
        nudge("arm_l",Math.sin(time*.7+.3)*.025+bite*.05,0,0);
        nudge("hat",Math.sin(time*.5)*.02,0,Math.sin(time*.37)*.015);
        nudge("oar",Math.sin(time*.6)*.05,Math.sin(time*.6+1)*.04,0);
        ModelPart fisher=parts.get("fisher");
        if(fisher!=null)fisher.y+=(float)(Math.sin(time*.9)*.18);
    }
    private void nudge(String name,double x,double y,double z){
        ModelPart p=parts.get(name);if(p==null)return;
        p.xRot+=(float)x;p.yRot+=(float)y;p.zRot+=(float)z;
    }

    /** World-relative position of a point given in a part's local pixels, under the current pose. */
    Vector3f locate(PoseStack pose,Vector3f local,String... chain){
        pose.pushPose();
        root.translateAndRotate(pose);
        for(String n:chain)parts.get(n).translateAndRotate(pose);
        Vector3f out=pose.last().pose().transformPosition(local.x/16f,local.y/16f,local.z/16f,new Vector3f());
        pose.popPose();
        return out;
    }

    static LayerDefinition create(){
        JsonObject json=load();
        MeshDefinition mesh=new MeshDefinition();
        Map<String,PartDefinition> defs=new HashMap<>();
        defs.put("root",mesh.getRoot());
        PARENTS.clear();
        for(var element:json.getAsJsonArray("parts")){
            JsonObject p=element.getAsJsonObject();
            String name=p.get("name").getAsString(),parent=p.get("parent").getAsString();
            CubeListBuilder cubes=CubeListBuilder.create();
            for(var ce:p.getAsJsonArray("cubes")){
                JsonObject c=ce.getAsJsonObject();
                float[] o=floats(c.getAsJsonArray("origin")),s=floats(c.getAsJsonArray("size"));
                JsonArray uv=c.getAsJsonArray("uv");
                cubes.texOffs(uv.get(0).getAsInt(),uv.get(1).getAsInt())
                    .addBox(o[0],o[1],o[2],s[0],s[1],s[2],GeoModel.deformation(c.get("inflate")));
            }
            float[] pv=floats(p.getAsJsonArray("pivot")),r=floats(p.getAsJsonArray("rotation"));
            defs.put(name,defs.get(parent).addOrReplaceChild(name,cubes,PartPose.offsetAndRotation(pv[0],pv[1],pv[2],r[0],r[1],r[2])));
            PARENTS.put(name,parent);
        }
        JsonArray tex=json.getAsJsonArray("texture");
        return LayerDefinition.create(mesh,tex.get(0).getAsInt(),tex.get(1).getAsInt());
    }
    private static float[] floats(JsonArray a){
        float[] f=new float[a.size()];for(int i=0;i<f.length;i++)f[i]=a.get(i).getAsFloat();return f;
    }
    private static JsonObject load(){
        try{
            var resource=Minecraft.getInstance().getResourceManager().getResource(Veilborne.id("geometry/skiff.json"));
            if(resource.isPresent())try(var reader=resource.get().openAsReader()){return JsonParser.parseReader(reader).getAsJsonObject();}
            try(var in=SkiffModel.class.getClassLoader().getResourceAsStream(PATH)){
                return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in,PATH),StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }catch(java.io.IOException e){throw new IllegalStateException("Unable to read "+PATH,e);}
    }
}
