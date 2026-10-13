package net.tfminecraft.archaeo.ownership;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.tfminecraft.tlibs.character.CharacterSession;

/** Stable occupancy evidence; interrupted kit/world effects require reviewed recovery. */
public final class SitePlacements {
    private SitePlacements(){}
    public static Path begin(SiteOwnership store,String name,Map<String,Object> after,CharacterSession actor,boolean moving,String world,List<Map<String,Object>> blocks,String kit)throws IOException{
        Map<String,Object> intent=new LinkedHashMap<>();String id=UUID.randomUUID().toString();intent.put("version",1);intent.put("kind","camp-placement");intent.put("id",id);intent.put("file",name);
        intent.put("actor",CharacterActors.ref(actor.ref()));intent.put("session",actor.toString());intent.put("moving",moving);intent.put("before",store.before(name));intent.put("after",SiteDocuments.text(after));
        intent.put("world",world);intent.put("blocks",blocks);intent.put("kit",kit);intent.put("done",false);Path path=store.root().resolve("ownership/placements/"+id+".yml");validate(path,intent);SiteDocuments.write(path,SiteDocuments.text(intent));return path;
    }
    public static String validate(Path path,Map<String,Object> intent)throws IOException{
        if(SiteOwnership.integral(intent.get("version"))!=1||!"camp-placement".equals(intent.get("kind"))||!path.getFileName().toString().equals(UUID.fromString((String)intent.get("id"))+".yml")||!(intent.get("done") instanceof Boolean)||!(intent.get("moving") instanceof Boolean))throw new SiteOwnership.Conflict("Invalid placement intent header");
        var after=SiteDocuments.parse((String)intent.get("after"));SiteOwnership.validate(after);String name=UUID.fromString((String)after.get("id"))+".yml";if(!name.equals(intent.get("file")))throw new SiteOwnership.Conflict("Placement dossier/path conflict");
        var actor=CharacterActors.ref(intent.get("actor"));String prefix="CharacterSession[ref="+actor+", sessionId=";String session=(String)intent.get("session");if(!actor.equals(SiteOwnership.director(after))||session==null||!session.startsWith(prefix)||!session.substring(prefix.length()).matches("-?(0|[1-9][0-9]*)\\]"))throw new SiteOwnership.Conflict("Placement actor/session conflict");try{Long.parseLong(session.substring(prefix.length(),session.length()-1));}catch(NumberFormatException invalid){throw new SiteOwnership.Conflict("Placement session overflow",invalid);}
        if(intent.get("before")!=null){var before=SiteDocuments.parse((String)intent.get("before"));SiteOwnership.validate(before);if(!Objects.equals(before.get("id"),after.get("id"))||Boolean.TRUE.equals(intent.get("moving"))&&!actor.equals(SiteOwnership.director(before)))throw new SiteOwnership.Conflict("Placement source conflict");}
        if(Boolean.TRUE.equals(intent.get("moving"))&&intent.get("before")==null)throw new SiteOwnership.Conflict("Moving placement has no source");
        if(!(intent.get("world") instanceof String)||!Objects.equals(intent.get("world"),after.get("world"))||!(intent.get("blocks") instanceof List<?> blocks)||blocks.isEmpty()||!(intent.get("kit")==null||intent.get("kit") instanceof String))throw new SiteOwnership.Conflict("Invalid placement effects evidence");
        Set<List<Object>> coordinates=new HashSet<>();
        for(Object value:blocks){var block=SiteDocuments.map(value);for(String coordinate:List.of("x","y","z"))if(!(block.get(coordinate) instanceof Integer))throw new SiteOwnership.Conflict("Invalid placement block coordinate");if(!(block.get("before") instanceof String)||!(block.get("after") instanceof String))throw new SiteOwnership.Conflict("Invalid placement block evidence");if(!coordinates.add(List.of(block.get("x"),block.get("y"),block.get("z"))))throw new SiteOwnership.Conflict("Duplicate placement block snapshot");}
        return name;
    }
    public static void inventory(SiteOwnership store)throws IOException{
        for(Path path:store.files("ownership/placements")){
            Map<String,Object> intent=SiteDocuments.parse(Files.readString(path));String name=validate(path,intent);
            if(!Boolean.TRUE.equals(intent.get("done")))store.hold(name,"Interrupted camp placement "+path.getFileName());
        }
    }
    public static void complete(SiteOwnership store,Path path)throws IOException{
        var intent=SiteDocuments.parse(Files.readString(path));String name=validate(path,intent);
        if(!Objects.equals(SiteDocuments.read(store.root().resolve("sites/"+name)),intent.get("after")))throw new SiteOwnership.Conflict("Placement dossier changed before effects receipt");
        intent.put("done",true);SiteDocuments.write(path,SiteDocuments.text(intent));
    }
}
