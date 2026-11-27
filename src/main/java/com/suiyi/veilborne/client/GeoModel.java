package com.suiyi.veilborne.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.suiyi.veilborne.Veilborne;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/**
 * A box model authored by a tools/art script and stored as geometry/*.json (the same format as the skiff):
 * parts with pivots, rotations and cubes whose UVs point into the script-painted texture.
 */
final class GeoModel {
    final ModelPart root;
    private final Map<String,ModelPart> parts=new HashMap<>();
    private final Map<String,String> parents;
    GeoModel(ModelPart root,Map<String,String> parents){
        this.root=root;this.parents=parents;
        for(String name:parents.keySet())parts.put(name,resolve(name));
    }
    private ModelPart resolve(String name){
        Deque<String> path=new ArrayDeque<>();
        for(String n=name;n!=null&&!n.equals("root");n=parents.get(n))path.push(n);
        ModelPart part=root;
        for(String n:path)part=part.getChild(n);
        return part;
    }
    ModelPart part(String name){return parts.get(name);}
    Collection<ModelPart> all(){return parts.values();}
    void reset(){for(ModelPart p:parts.values()){p.resetPose();p.visible=true;}}
    void rotate(String name,double x,double y,double z){
        ModelPart p=parts.get(name);if(p==null)return;
        p.xRot+=(float)x;p.yRot+=(float)y;p.zRot+=(float)z;
    }
    void show(String name,boolean visible){ModelPart p=parts.get(name);if(p!=null)p.visible=visible;}

    /** Builds the layer and records the part hierarchy into {@code parents}. */
    static LayerDefinition create(String file,Map<String,String> parents){
        JsonObject json=load(file);
        MeshDefinition mesh=new MeshDefinition();
        Map<String,PartDefinition> defs=new HashMap<>();
        defs.put("root",mesh.getRoot());
        parents.clear();
        for(var element:json.getAsJsonArray("parts")){
            JsonObject p=element.getAsJsonObject();
            String name=p.get("name").getAsString(),parent=p.get("parent").getAsString();
            CubeListBuilder cubes=CubeListBuilder.create();
            for(var ce:p.getAsJsonArray("cubes")){
                JsonObject c=ce.getAsJsonObject();
                float[] o=floats(c.getAsJsonArray("origin")),s=floats(c.getAsJsonArray("size"));
                JsonArray uv=c.getAsJsonArray("uv");
                cubes.texOffs(uv.get(0).getAsInt(),uv.get(1).getAsInt()).addBox(o[0],o[1],o[2],s[0],s[1],s[2],deformation(c.get("inflate")));
            }
            float[] pv=floats(p.getAsJsonArray("pivot")),r=floats(p.getAsJsonArray("rotation"));
            defs.put(name,defs.get(parent).addOrReplaceChild(name,cubes,PartPose.offsetAndRotation(pv[0],pv[1],pv[2],r[0],r[1],r[2])));
            parents.put(name,parent);
        }
        JsonArray tex=json.getAsJsonArray("texture");
        return LayerDefinition.create(mesh,tex.get(0).getAsInt(),tex.get(1).getAsInt());
    }
    /** One number, or x, y, z where the export pushed single faces apart (tools/art/boxmodel.py, BoxModel.separate). */
    static CubeDeformation deformation(com.google.gson.JsonElement e){
        if(!e.isJsonArray())return new CubeDeformation(e.getAsFloat());
        float[] g=floats(e.getAsJsonArray());
        return new CubeDeformation(g[0],g[1],g[2]);
    }
    private static float[] floats(JsonArray a){float[] f=new float[a.size()];for(int i=0;i<f.length;i++)f[i]=a.get(i).getAsFloat();return f;}
    private static JsonObject load(String file){
        String path="assets/veilborne/geometry/"+file;
        try{
            var resource=Minecraft.getInstance().getResourceManager().getResource(Veilborne.id("geometry/"+file));
            if(resource.isPresent())try(var reader=resource.get().openAsReader()){return JsonParser.parseReader(reader).getAsJsonObject();}
            try(var in=GeoModel.class.getClassLoader().getResourceAsStream(path)){
                return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in,path),StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }catch(java.io.IOException e){throw new IllegalStateException("Unable to read "+path,e);}
    }
}
