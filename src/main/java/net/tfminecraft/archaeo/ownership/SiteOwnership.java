package net.tfminecraft.archaeo.ownership;

import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.Characters;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;

/** Complete raw inventory and fixed, checked publication of each dossier. */
public final class SiteOwnership {
    public static final int SCHEMA=2;
    private final Path root;
    private final Map<String,String> expected=new LinkedHashMap<>();
    private final Map<String,Map<String,Object>> documents=new LinkedHashMap<>();
    private final Map<String,String> holds=new LinkedHashMap<>();
    private final Map<String,String> publicationHolds=new HashMap<>();
    private final Set<String> inventoryHolds=new HashSet<>();
    private final Map<String,List<Map<String,Object>>> conflictEvidence=new LinkedHashMap<>();
    public Predicate<Map<String,Object>> estateGuard=ignored->false;
    private boolean ready;
    public static final class Conflict extends IOException { public Conflict(String message){super(message);} public Conflict(String message,Throwable cause){super(message,cause);} }
    public SiteOwnership(Path root){this.root=root;this.ready=!Files.exists(root.resolve("sites"));}
    public Path root(){return root;}
    public boolean ready(){return ready;}
    public Map<String,String> holds(){return Collections.unmodifiableMap(holds);}
    public Map<String,Map<String,Object>> documents(){return Collections.unmodifiableMap(documents);}
    public List<Map<String,Object>> evidence(String name){List<Map<String,Object>> frames=new ArrayList<>();if(documents.containsKey(name))frames.add(documents.get(name));frames.addAll(conflictEvidence.getOrDefault(name,List.of()));return frames;}
    void retainConflict(String name,List<Map<String,Object>> frames){expected.putIfAbsent(name,null);conflictEvidence.computeIfAbsent(name,ignored->new ArrayList<>()).addAll(frames);hold(name,"Unsaved runtime conflict requires offline reconciliation");inventoryHolds.add(name);}
    public static Map<String,Object> refs(Map<String,Object> document){return SiteDocuments.map(document.get("actor-refs"));}
    public static CharacterRef director(Map<String,Object> document){Object id=document.get("director");return id==null?null:CharacterActors.ref(refs(document).get(id));}
    private static void require(boolean condition,String cause)throws IOException{if(!condition)throw new Conflict(cause);}
    public List<Path> files(String folder)throws IOException{
        Path path=root.resolve(folder);if(!Files.exists(path))return List.of();try(var stream=Files.list(path)){return stream.filter(p->p.getFileName().toString().endsWith(".yml")).sorted().toList();}
    }
    public static long integral(Object value)throws IOException {
        require(value instanceof Integer||value instanceof Long,"Expected an integral ownership version/revision");
        long number=((Number)value).longValue();require(number>=0,"Negative ownership version/revision");return number;
    }
    public void open()throws IOException{
        Map<String,String> oldExpected=new LinkedHashMap<>(expected),oldHolds=new LinkedHashMap<>(holds),oldPublication=new HashMap<>(publicationHolds);
        Map<String,Map<String,Object>> oldDocuments=new LinkedHashMap<>(documents);Set<String> oldInventory=new HashSet<>(inventoryHolds);
        Map<String,List<Map<String,Object>>> oldEvidence=new LinkedHashMap<>(conflictEvidence);
        try{openInventory();}catch(IOException|RuntimeException failure){
            expected.clear();expected.putAll(oldExpected);documents.clear();documents.putAll(oldDocuments);holds.clear();holds.putAll(oldHolds);publicationHolds.clear();publicationHolds.putAll(oldPublication);inventoryHolds.clear();inventoryHolds.addAll(oldInventory);conflictEvidence.clear();conflictEvidence.putAll(oldEvidence);ready=false;
            throw failure instanceof IOException io?io:new Conflict("Invalid whole dossier inventory",failure);
        }
    }
    private void openInventory()throws IOException{
        ready=false;expected.clear();documents.clear();holds.clear();publicationHolds.clear();inventoryHolds.clear();conflictEvidence.clear();Files.createDirectories(root.resolve("sites"));
        Map<UUID,List<String>> ids=new HashMap<>();
        // Preflight the complete inventory before any source publication or replay.
        for(Path path:files("sites")) {
            String name=path.getFileName().toString(),text=Files.readString(path);expected.put(name,text);
            Path archive=root.resolve("ownership/source/"+name);if(!Files.exists(archive))SiteDocuments.write(archive,text);
            try {
                Map<String,Object> raw=SiteDocuments.parse(text);documents.put(name,raw);UUID id=UUID.fromString((String)raw.get("id"));
                ids.computeIfAbsent(id,ignored->new ArrayList<>()).add(name);
                require(name.equals(id+".yml"),"Dossier ID/filename conflict");
                long schema=integral(raw.getOrDefault("schema",1));require(schema==1||schema==SCHEMA,"Unsupported dossier schema");
                if(schema==1)actors(raw);else validate(raw);
            }catch(Exception failure){holds.put(name,failure.getMessage());}
        }
        for(List<String> copies:ids.values())if(copies.size()>1)for(String name:copies)holds.put(name,"Duplicate dossier ID");
        SitePlacements.inventory(this);SiteConflicts.inventory(this);inventoryHolds.addAll(holds.keySet());
        for(Path path:files("ownership/journals")) {
            Map<String,Object> journal=SiteDocuments.parse(Files.readString(path));String name=(String)journal.get("file");
            if(!Boolean.TRUE.equals(journal.get("done"))&&!holds.containsKey(name))try{publish(path,journal);}catch(IOException failure){holds.put(name,failure.getMessage());}
        }
        for(String name:List.copyOf(expected.keySet())) {
            if(holds.containsKey(name))continue;
            try {
                String text=Files.readString(root.resolve("sites/"+name));Map<String,Object> raw=SiteDocuments.parse(text);
                if(integral(raw.getOrDefault("schema",1))==1)raw=migrate(name,text,raw);
                validate(raw);documents.put(name,raw);expected.put(name,Files.readString(root.resolve("sites/"+name)));
            }catch(Exception failure){holds.put(name,failure.getMessage());}
        }
        ready=true;
    }
    private Map<String,Object> migrate(String name,String text,Map<String,Object> source)throws IOException{
        Path archive=root.resolve("ownership/source/"+name),plan=root.resolve("ownership/plans/"+name);
        if(!Files.exists(archive))SiteDocuments.write(archive,text);
        require(Files.readString(archive).equals(text),"Archived legacy source differs");
        Map<String,Object> mapping;
        if(Files.exists(plan)){
            Map<String,Object> prior=SiteDocuments.parse(Files.readString(plan));require(integral(prior.get("version"))==1,"Unsupported legacy plan version");require(text.equals(prior.get("source")),"Legacy plan source changed");mapping=SiteDocuments.map(prior.get("legacy-map"));
        }else{
            Set<String> actors=actors(source);
            require(actors.isEmpty()||CharacterActors.supports(SiteOwnership.class.getClassLoader(),false)&&LegacyActors.ready(),"Reviewed legacy actors unavailable");
            mapping=new LinkedHashMap<>();for(String actor:actors){UUID account=UUID.fromString(actor);mapping.put(actor,LegacyActors.heir(account).filter(ref->ref.ownerId().equals(account)).map(CharacterActors::ref).orElse(null));}
            Map<String,Object> saved=new LinkedHashMap<>();saved.put("version",1);saved.put("source",text);saved.put("legacy-map",mapping);SiteDocuments.write(plan,SiteDocuments.text(saved));
        }
        Map<String,Object> after=migrated(source,mapping);
        Map<String,Object> metadata=new LinkedHashMap<>();metadata.put("kind","migration");metadata.put("legacy-map",mapping);
        commit(name,text,after,metadata);return after;
    }
    static Set<String> actors(Map<String,Object> raw)throws IOException{
        Set<String> actors=new LinkedHashSet<>();Object director=raw.get("director");if(director!=null)actors.add(uuid(director));
        Set<String> excavators=new HashSet<>();for(Object value:list(raw.getOrDefault("excavators",List.of()))){String actor=uuid(value);require(excavators.add(actor),"Duplicate excavator slot");actors.add(actor);}
        Map<String,Object> workers=SiteDocuments.map(raw.getOrDefault("workers",Map.of()));
        for(var entry:workers.entrySet()){
            actors.add(uuid(entry.getKey()));var worker=SiteDocuments.map(entry.getValue());Object role=worker.get("role");require(role==null||Set.of("director","archaeologist","excavator").contains(role),"Unknown worker role");
            for(String counter:List.of("blocks-removed","cells-brushed","finds-recovered","finds-damaged","finds-lost"))require(!worker.containsKey(counter)||worker.get(counter) instanceof Number,"Malformed worker tally");
            for(String time:List.of("joined-at","last-active-at"))if(worker.get(time)!=null)Instant.parse((String)worker.get(time));
        }
        Map<String,Object> prospect=SiteDocuments.map(raw.getOrDefault("prospect",Map.of()));
        for(Object key:SiteDocuments.map(prospect.getOrDefault("samples",Map.of())).keySet())actors.add(uuid(key));
        for(Object value:list(prospect.getOrDefault("confirmed",List.of())))actors.add(uuid(value));return actors;
    }
    private static String uuid(Object value){return UUID.fromString((String)value).toString();}
    private static List<?> list(Object value){return (List<?>)value;}
    static Map<String,Object> migrated(Map<String,Object> source,Map<String,Object> mapping)throws IOException{
        require(integral(source.getOrDefault("schema",1))==1,"Unsupported legacy schema");
        require(!source.containsKey("actors-version")&&!source.containsKey("actor-refs"),"Legacy reserved actor fields conflict");
        require(mapping.keySet().equals(actors(source)),"Fixed mapping actor inventory conflicts");
        Map<String,Object> result=SiteDocuments.copy(source),refs=new LinkedHashMap<>();
        for(String account:actors(source)){
            require(mapping.containsKey(account)&&mapping.get(account)!=null,"No reviewed actor for "+account);
            CharacterRef ref=CharacterActors.ref(mapping.get(account));require(account.equals(ref.ownerId().toString()),"Reviewed actor account conflict");
            Object previous=refs.putIfAbsent(ref.characterId().toString(),mapping.get(account));require(previous==null||previous.equals(mapping.get(account)),"Reviewed character collision");
        }
        result.put("schema",SCHEMA);result.put("actors-version",1);result.put("actor-refs",refs);
        if(source.get("director")!=null)result.put("director",mapped(mapping,source.get("director")));
        if(source.containsKey("excavators"))result.put("excavators",list(source.get("excavators")).stream().map(value->mapped(mapping,value)).toList());
        if(source.containsKey("workers"))result.put("workers",rekey(SiteDocuments.map(source.get("workers")),mapping));
        if(source.containsKey("prospect")){
            var prospect=SiteDocuments.map(result.get("prospect"));
            if(prospect.containsKey("samples"))prospect.put("samples",rekey(SiteDocuments.map(prospect.get("samples")),mapping));
            if(prospect.containsKey("confirmed"))prospect.put("confirmed",list(prospect.get("confirmed")).stream().map(value->mapped(mapping,value)).toList());
        }
        validate(result);return result;
    }
    private static String mapped(Map<String,Object> mapping,Object account){return CharacterActors.ref(mapping.get(account)).characterId().toString();}
    private static Map<String,Object> rekey(Map<String,Object> source,Map<String,Object> mapping){Map<String,Object> result=new LinkedHashMap<>();source.forEach((key,value)->result.put(mapped(mapping,key),value));return result;}
    public static void validate(Map<String,Object> raw)throws IOException{
        require(integral(raw.getOrDefault("schema",0))==SCHEMA&&integral(raw.getOrDefault("actors-version",0))==1,"Unsupported actor dossier schema");
        Map<String,Object> refs=refs(raw);for(var entry:refs.entrySet())require(uuid(entry.getKey()).equals(CharacterActors.ref(entry.getValue()).characterId().toString()),"Actor ref/key conflict");
        for(String actor:actors(raw))require(refs.containsKey(actor),"Unresolved dossier actor "+actor);
    }
    public Optional<CharacterRef> plannedDirector(String name){
        Map<String,Object> raw=documents.get(name);if(raw==null)return Optional.empty();
        try { if(integral(raw.getOrDefault("schema",1))==SCHEMA)return Optional.ofNullable(director(raw));
            Object account=raw.get("director");Path plan=root.resolve("ownership/plans/"+name);if(account!=null&&Files.exists(plan)){Object ref=SiteDocuments.map(SiteDocuments.parse(Files.readString(plan)).get("legacy-map")).get(account);if(ref!=null)return Optional.of(CharacterActors.ref(ref));}
        }catch(Exception ignored){}return Optional.empty();
    }
    public boolean potentialOwner(String name,CharacterRef actor){
        if(conflictEvidence.containsKey(name))for(var raw:conflictEvidence.get(name))if(potentialOwner(raw,actor))return true;
        Map<String,Object> raw=documents.get(name);if(raw==null)return !conflictEvidence.containsKey(name);
        long schema;try{schema=integral(raw.getOrDefault("schema",1));}catch(IOException invalid){return true;}if(schema!=1&&schema!=SCHEMA)return true;
        if(raw.get("director")==null)return false;
        Optional<CharacterRef> owner=plannedDirector(name);if(owner.isPresent())return owner.get().equals(actor);
        if(schema==1)return raw.get("director").equals(actor.ownerId().toString());return true;
    }
    private static boolean potentialOwner(Map<String,Object> raw,CharacterRef actor){
        if(raw==null)return true;try{long schema=integral(raw.getOrDefault("schema",1));if(raw.get("director")==null)return false;if(schema==1)return actor.ownerId().toString().equals(raw.get("director"));validate(raw);return actor.equals(director(raw));}catch(Exception invalid){return true;}
    }
    public Set<String> names(){return Collections.unmodifiableSet(expected.keySet());}
    public int countOpen(CharacterRef actor){
        if(actor==null||!ready)return Integer.MAX_VALUE;int count=0;
        for(String name:expected.keySet()) {var frames=evidence(name);if(!frames.isEmpty()&&frames.stream().allMatch(raw->raw!=null&&("CLOSED".equals(raw.get("status"))||"HIDDEN".equals(raw.get("status")))))continue;if(potentialOwner(name,actor))count++;}
        return count;
    }
    public void checkInventory()throws IOException{checkInventory(Map.of());}
    public void checkInventory(Map<String,Set<String>> recoverable)throws IOException{
        Set<String> current=new HashSet<>(),changed=new TreeSet<>();List<Path> paths;
        try{paths=files("sites");}catch(IOException failure){ready=false;throw failure;}
        for(Path path:paths){
            String name=path.getFileName().toString();current.add(name);
            String actual;try{actual=Files.readString(path);}catch(IOException failure){hold(name,failure.getMessage());ready=false;throw failure;}
            if(!Objects.equals(actual,expected.get(name))&&!recoverable.getOrDefault(name,Set.of()).contains(actual)){hold(name,"Post-load dossier inventory change "+name);inventoryHolds.add(name);changed.add(name);}
        }
        for(String name:expected.keySet())if(!current.contains(name)){hold(name,"Dossier removed after load");inventoryHolds.add(name);changed.add(name);}
        require(changed.isEmpty(),"Dossier inventory changed: "+changed);
    }
    public void removed(String name){expected.remove(name);documents.remove(name);holds.remove(name);}
    public void hold(String name,String cause){holds.put(name,cause);publicationHolds.remove(name);}
    public void releaseUntouchedPlan(Map<String,Object> journal){String name=(String)journal.get("file");if(!inventoryHolds.contains(name)&&Objects.equals(publicationHolds.get(name),journal.get("id"))){holds.remove(name);publicationHolds.remove(name);}}
    public boolean held(String name){return holds.containsKey(name);}
    public void save(UUID id,Map<String,Object> desired)throws IOException{
        String name=id+".yml";require(ready&&!held(name),"Held dossier "+name);
        if(!expected.containsKey(name))checkInventory();
        try{validate(desired);}catch(RuntimeException invalid){throw new Conflict("Invalid runtime dossier "+name,invalid);}
        String current=SiteDocuments.read(root.resolve("sites/"+name));require(Objects.equals(current,expected.get(name)),"Post-load dossier edit "+name);
        SiteDocuments.write(root.resolve("sites/"+name),SiteDocuments.text(desired));expected.put(name,SiteDocuments.text(desired));documents.put(name,SiteDocuments.copy(desired));
    }
    public String before(String name){return expected.get(name);}
    public void commit(String name,String before,Map<String,Object> after,Map<String,Object> metadata)throws IOException{
        require(name.equals(UUID.fromString((String)after.get("id"))+".yml"),"Dossier output filename conflict");
        Map<String,Object> journal=SiteDocuments.copy(metadata);String id=String.format("%013d",System.currentTimeMillis())+"-"+UUID.randomUUID();
        journal.put("version",1);journal.put("id",id);journal.put("file",name);journal.put("before",before);journal.put("after",SiteDocuments.text(after));journal.put("done",false);
        Path path=root.resolve("ownership/journals/"+id+".yml");SiteDocuments.write(path,SiteDocuments.text(journal));publish(path,journal);
        expected.put(name,(String)journal.get("after"));documents.put(name,SiteDocuments.copy(after));
    }
    public void validateReceipt(Path path,Map<String,Object> journal)throws IOException{
        require(integral(journal.getOrDefault("version",0))==1&&journal.get("done") instanceof Boolean,"Unsupported ownership journal");require(journal.get("id") instanceof String id&&!id.isBlank()&&!id.contains("/")&&!id.contains("\\")&&path.getFileName().toString().equals(id+".yml"),"Journal ID/path conflict");
        String kind=(String)journal.get("kind"),name=(String)journal.get("file");require(Set.of("migration","estate").contains(kind),"Unsupported ownership journal kind");
        require(kind.equals("estate")==journal.containsKey("estate"),"Journal kind/envelope conflict");
        Map<String,Object> before=SiteDocuments.parse((String)journal.get("before")),after=SiteDocuments.parse((String)journal.get("after"));require(name.equals(UUID.fromString((String)before.get("id"))+".yml")&&Objects.equals(before.get("id"),after.get("id")),"Dossier path/identity conflict");validate(after);
        if(kind.equals("migration")){
            require(Files.readString(root.resolve("ownership/source/"+name)).equals(journal.get("before")),"Migration source snapshot conflict");
            Map<String,Object> plan=SiteDocuments.parse(Files.readString(root.resolve("ownership/plans/"+name)));require(integral(plan.get("version"))==1,"Unsupported fixed legacy plan");require(SiteDocuments.same(plan.get("legacy-map"),journal.get("legacy-map"))&&Objects.equals(plan.get("source"),journal.get("before")),"Migration fixed plan conflict");
            require(SiteDocuments.same(migrated(before,SiteDocuments.map(journal.get("legacy-map"))),after),"Migration changes unrelated dossier state");
        }else{
            validate(before);Map<String,Object> estate=SiteDocuments.map(journal.get("estate"));CharacterRef from=CharacterActors.ref(estate.get("from")),to=CharacterActors.ref(estate.get("to"));require(from.equals(director(before))&&!from.equals(to),"Estate source director conflict");
            long revision=integral(estate.get("revision"));UUID death=UUID.fromString((String)estate.get("death"));String decision=(String)estate.get("decision");require(decision.startsWith("EstateDecision[deathId="+death+", testator="+from+", ")&&decision.contains(", property=Character[ref="+to+"], ")&&decision.endsWith(", revision="+revision+"]"),"Estate full decision/header conflict");
            require(SiteDocuments.same(transfer(before,to),after),"Estate changed unrelated actors/history/dossier state");
        }
    }
    public static Map<String,Object> transfer(Map<String,Object> source,CharacterRef target)throws IOException{
        Map<String,Object> after=SiteDocuments.copy(source),refs=refs(after);Object existing=refs.get(target.characterId().toString());require(existing==null||target.equals(CharacterActors.ref(existing)),"Recipient character account conflict");
        if(existing==null)refs.put(target.characterId().toString(),CharacterActors.ref(target));after.put("director",target.characterId().toString());return after;
    }
    private void completionAllows(Map<String,Object> journal,String actual)throws IOException{
        if(!journal.containsKey("estate"))return;var header=SiteDocuments.map(journal.get("estate"));long revision=integral(header.get("revision"));UUID death=UUID.fromString((String)header.get("death"));Path path=root.resolve("ownership/completions/"+death+"/"+revision+".yml");if(!Files.exists(path))return;
        require(Boolean.TRUE.equals(journal.get("done"))&&Objects.equals(actual,journal.get("after")),"Completed revision cannot publish a pending/restored site intent");var stamp=SiteDocuments.parse(Files.readString(path));require(integral(stamp.get("version"))==1&&"estate-completion".equals(stamp.get("kind"))&&integral(stamp.get("revision"))==revision&&death.toString().equals(stamp.get("death"))&&Objects.equals(header.get("decision"),stamp.get("decision"))&&SiteDocuments.same(header.get("from"),stamp.get("from"))&&SiteDocuments.same(header.get("to"),stamp.get("to")),"Completion/receipt decision mismatch");
        Set<String> moved=new HashSet<>();for(Object value:(List<?>)stamp.get("moved")){String id=UUID.fromString((String)value).toString();require(id.equals(value)&&moved.add(id),"Invalid completion moved IDs");}var counts=SiteDocuments.map(stamp.get("counts"));require(counts.keySet().equals(Set.of("sites"))&&integral(counts.get("sites"))==moved.size()&&moved.contains(((String)journal.get("file")).replace(".yml","")),"Completion/receipt moved IDs mismatch");
    }
    public void publish(Path path,Map<String,Object> journal)throws IOException{
        try{try{validateReceipt(path,journal);}catch(Exception invalid){throw new Conflict("Invalid ownership replay",invalid);}String name=(String)journal.get("file");Path destination=root.resolve("sites/"+name);String actual=SiteDocuments.read(destination);
            require(Objects.equals(actual,journal.get("before"))||Objects.equals(actual,journal.get("after")),"Conflicting dossier publication "+name);
            if(journal.containsKey("estate")&&CharacterActors.supports(getClass().getClassLoader(),true))SiteEstates.preflightPublication(this,journal);
            completionAllows(journal,actual);
            require(!journal.containsKey("estate")||estateGuard.test(journal),"Stale/incomplete estate decision");
            actual=SiteDocuments.read(destination);require(Objects.equals(actual,journal.get("before"))||Objects.equals(actual,journal.get("after")),"Dossier changed immediately before publication");
            if(!Objects.equals(actual,journal.get("after")))SiteDocuments.write(destination,(String)journal.get("after"));
            expected.put(name,(String)journal.get("after"));documents.put(name,SiteDocuments.parse((String)journal.get("after")));
            require(!journal.containsKey("estate")||estateGuard.test(journal),"Estate guard changed before receipt");journal.put("done",true);SiteDocuments.write(path,SiteDocuments.text(journal));
            expected.put(name,(String)journal.get("after"));documents.put(name,SiteDocuments.parse((String)journal.get("after")));
            if(!holds.getOrDefault(name,"").startsWith("Unloaded dossier")){holds.remove(name);publicationHolds.remove(name);}
        }catch(Exception failure){Object file=journal.get("file");if(file instanceof String name){holds.put(name,failure.getMessage());if(!inventoryHolds.contains(name)&&journal.get("id") instanceof String id)publicationHolds.put(name,id);}else ready=false;throw failure instanceof IOException io?io:new IOException("Invalid ownership replay",failure);}
    }
}
