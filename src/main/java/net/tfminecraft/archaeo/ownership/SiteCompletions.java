package net.tfminecraft.archaeo.ownership;

import net.tfminecraft.tlibs.character.estate.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Revision acknowledgements bind cumulative receipts and retained ownership. */
final class SiteCompletions {
    private SiteCompletions(){}
    static Map<String,Object> current(SiteOwnership store,EstateDecision decision,Map<Long,Set<String>> receipts,Map<Long,String> decisions)throws IOException{
        Path directory=store.root().resolve("ownership/completions/"+decision.deathId());if(!Files.exists(directory))return null;
        Map<String,Object> current=null;
        try(var paths=Files.list(directory)){
            for(Path path:paths.filter(p->p.getFileName().toString().endsWith(".yml")).sorted().toList()){
                Map<String,Object> stamp=SiteDocuments.parse(Files.readString(path));validate(path,stamp,decision,receipts,decisions);
                long revision=SiteOwnership.integral(stamp.get("revision"));if(revision>decision.revision())throw new SiteOwnership.Conflict("Future site completion revision");
                if(revision==decision.revision()){
                    if(!decision.toString().equals(stamp.get("decision"))||!decision.property().toString().equals(stamp.get("property"))||!decision.property().character().map(CharacterActors::ref).equals(Optional.ofNullable(stamp.get("to"))))throw new SiteOwnership.Conflict("Same-revision site completion decision changed");current=stamp;
                }
            }
        }
        return current;
    }
    static void validate(Path path,Map<String,Object> stamp,EstateDecision decision,Map<Long,Set<String>> receipts,Map<Long,String> decisions)throws IOException{
        long revision=SiteOwnership.integral(stamp.get("revision"));if(SiteOwnership.integral(stamp.get("version"))!=1||!"estate-completion".equals(stamp.get("kind"))||!path.getFileName().toString().equals(revision+".yml")||!path.getParent().getFileName().toString().equals(stamp.get("death"))||!decision.deathId().toString().equals(stamp.get("death"))||!CharacterActors.ref(decision.testator()).equals(stamp.get("from")))throw new SiteOwnership.Conflict("Invalid site completion header");
        String captured=(String)stamp.get("decision"),property=(String)stamp.get("property");if(captured==null||property==null||!captured.startsWith("EstateDecision[deathId="+decision.deathId()+", testator="+decision.testator()+", ")||!captured.contains(", property="+property+", guildLocks=")||!captured.endsWith(", revision="+revision+"]"))throw new SiteOwnership.Conflict("Invalid site completion decision binding");
        if(decisions.containsKey(revision)&&!captured.equals(decisions.get(revision)))throw new SiteOwnership.Conflict("Completion differs from its revision receipts");
        Object target=stamp.get("to");if(property.startsWith("Character[")){if(target==null||!property.equals("Character[ref="+CharacterActors.ref(target)+"]"))throw new SiteOwnership.Conflict("Completion recipient conflict");}else if(target!=null||!Set.of("Frozen[]","Waiting[]","Sender[]").contains(property))throw new SiteOwnership.Conflict("Unsupported noncharacter completion recipient");
        Set<String> ids=new TreeSet<>();for(Object value:(List<?>)stamp.get("moved")){String id=UUID.fromString((String)value).toString();if(!id.equals(value)||!ids.add(id))throw new SiteOwnership.Conflict("Invalid completion moved IDs");}
        var counts=SiteDocuments.map(stamp.get("counts"));if(!counts.keySet().equals(Set.of("sites"))||SiteOwnership.integral(counts.get("sites"))!=ids.size())throw new SiteOwnership.Conflict("Completion moved count conflict");
        Set<String> expected=new TreeSet<>();receipts.forEach((at,moved)->{if(at<=revision)expected.addAll(moved);});if(!ids.equals(expected))throw new SiteOwnership.Conflict("Completion/cumulative receipt IDs conflict");
        for(var entry:SiteDocuments.map(stamp.get("retained")).entrySet()){validateRetainedName(entry.getKey());if(entry.getValue()!=null&&!decision.testator().equals(CharacterActors.ref(entry.getValue())))throw new SiteOwnership.Conflict("Retained owner conflict");}
    }
    private static void validateRetainedName(String name)throws IOException{
        try{if(!name.endsWith(".yml")||!name.equals(UUID.fromString(name.substring(0,name.length()-4))+".yml"))throw new IllegalArgumentException();}catch(IllegalArgumentException invalid){throw new SiteOwnership.Conflict("Invalid retained dossier UUID filename",invalid);}
    }
    static Map<String,Object> retained(SiteOwnership store,EstateDecision decision)throws IOException{
        Map<String,Object> retained=new TreeMap<>();for(String name:store.names())if(store.potentialOwner(name,decision.testator())){validateRetainedName(name);retained.put(name,store.plannedDirector(name).map(CharacterActors::ref).orElse(null));}return retained;
    }
    static void unchangedRetention(SiteOwnership store,EstateDecision decision,Map<String,Object> stamp)throws IOException{
        if(!SiteDocuments.same(stamp.get("retained"),retained(store,decision)))throw new SiteOwnership.Conflict("Completed revision has new/remaining/resolved site ownership; a new revision is required");
        if(decision.property().kind()==Recipient.Kind.CHARACTER&&!SiteDocuments.map(stamp.get("retained")).isEmpty())throw new SiteOwnership.Conflict("Completed character assignment still retains testator sites");
    }
    static void write(SiteOwnership store,EstateDecision decision,Set<String> moved)throws IOException{
        if(decision.property().kind()==Recipient.Kind.ACCOUNT)throw new SiteOwnership.Conflict("Ledger account cannot direct a site");
        if(!Estates.guardHolds(decision))throw new IOException("Estate guard changed before completion");Map<String,Object> stamp=new LinkedHashMap<>();stamp.put("version",1);stamp.put("kind","estate-completion");stamp.put("death",decision.deathId().toString());stamp.put("revision",decision.revision());stamp.put("decision",decision.toString());stamp.put("from",CharacterActors.ref(decision.testator()));stamp.put("to",decision.property().character().map(CharacterActors::ref).orElse(null));stamp.put("property",decision.property().toString());stamp.put("counts",Map.of("sites",moved.size()));stamp.put("moved",List.copyOf(moved));stamp.put("retained",retained(store,decision));Path path=store.root().resolve("ownership/completions/"+decision.deathId()+"/"+decision.revision()+".yml");
        if(Files.exists(path))throw new SiteOwnership.Conflict("Completion already exists");SiteDocuments.write(path,SiteDocuments.text(stamp));if(!Estates.guardHolds(decision))throw new IOException("Estate changed before completion acknowledgement");
    }
}
