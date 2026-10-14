package net.tfminecraft.archaeo.ownership;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

/** Held runtime progress is evidence for staff, never an automatic replay. */
public final class SiteConflicts {
    private SiteConflicts() {}

    public static Path capture(SiteOwnership store, UUID site, String runtime, String reason) throws IOException {
        String name=site+".yml", id=UUID.randomUUID().toString();
        Map<String,Object> record=new LinkedHashMap<>();
        record.put("version",1);record.put("kind","runtime-conflict");record.put("id",id);record.put("file",name);
        record.put("reason",reason);record.put("source",store.before(name));record.put("runtime",runtime);
        record.put("captured",SiteDocuments.text(SiteRawOverlay.patch(store.documents().get(name),SiteDocuments.parse(runtime))));
        try{record.put("current",SiteDocuments.read(store.root().resolve("sites/"+name)));record.put("current-error",null);}
        catch(IOException unreadable){record.put("current",null);record.put("current-error",unreadable.toString());}
        Path path=store.root().resolve("ownership/conflicts/"+id+".yml");
        if(Files.exists(path))throw new IOException("Conflict evidence already exists");
        SiteDocuments.write(path,SiteDocuments.text(record));store.retainConflict(name,frames(record));return path;
    }

    public static Map<String,Object> validate(Path path)throws IOException{
        Map<String,Object> saved=SiteDocuments.parse(Files.readString(path));
        try{
            if(SiteOwnership.integral(saved.get("version"))!=1||!"runtime-conflict".equals(saved.get("kind"))||!saved.keySet().equals(Set.of("version","kind","id","file","reason","source","runtime","captured","current","current-error")))throw new IOException("Unsupported conflict evidence");
            String id=UUID.fromString((String)saved.get("id")).toString(),name=(String)saved.get("file");
            if(!id.equals(saved.get("id"))||!path.getFileName().toString().equals(id+".yml")||!name.equals(UUID.fromString(name.replace(".yml",""))+".yml")||!(saved.get("reason") instanceof String reason)||reason.isBlank())throw new IOException("Conflict evidence identity mismatch");
            Map<String,Object> source=null;
            for(String field:List.of("source","runtime","captured")){
                Object text=saved.get(field);if(field.equals("source")&&text==null)continue;
                Map<String,Object> raw=SiteDocuments.parse((String)text);if(!name.equals(UUID.fromString((String)raw.get("id"))+".yml"))throw new IOException("Conflict snapshot dossier mismatch");if(field.equals("source"))source=raw;
            }
            if(!SiteDocuments.same(SiteRawOverlay.patch(source,SiteDocuments.parse((String)saved.get("runtime"))),SiteDocuments.parse((String)saved.get("captured"))))throw new IOException("Conflict captured raw overlay mismatch");
            for(String field:List.of("current","current-error"))if(saved.get(field)!=null&&!(saved.get(field) instanceof String))throw new IOException("Invalid current conflict evidence");
            if(saved.get("current-error")!=null&&saved.get("current")!=null)throw new IOException("Contradictory current conflict evidence");
            return saved;
        }catch(RuntimeException failure){throw new IOException("Malformed conflict evidence",failure);}
    }

    public static void inventory(SiteOwnership store)throws IOException{
        for(Path path:store.files("ownership/reconciled-conflicts"))validate(path);
        for(Path path:store.files("ownership/conflicts")){
            var saved=validate(path);store.retainConflict((String)saved.get("file"),frames(saved));
        }
    }
    private static List<Map<String,Object>> frames(Map<String,Object> saved){
        List<Map<String,Object>> frames=new ArrayList<>();
        for(String field:List.of("source","captured","current"))if(saved.get(field) instanceof String text){try{frames.add(SiteDocuments.parse(text));}catch(IOException unreadable){frames.add(null);}}
        if(saved.get("current-error")!=null)frames.add(null);
        return frames;
    }
}
