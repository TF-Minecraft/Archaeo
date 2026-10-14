package net.tfminecraft.archaeo.ownership;

import net.tfminecraft.archaeo.model.Site;
import net.tfminecraft.archaeo.site.SiteRepository;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.estate.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Optional estate types load only after the capability probe. */
public final class SiteEstates implements EstateParticipant {
    private final SiteRepository repository;
    public SiteEstates(SiteRepository repository){this.repository=repository;}
    @Override public String id(){return "archaeo-sites";}
    public static boolean guard(Map<String,Object> journal){
        try{Map<String,Object> saved=SiteDocuments.map(journal.get("estate"));long revision=SiteOwnership.integral(saved.get("revision"));
            return Estates.decision(UUID.fromString((String)saved.get("death"))).filter(d->d.revision()==revision&&d.toString().equals(saved.get("decision"))&&CharacterActors.ref(d.testator()).equals(saved.get("from"))&&d.property().character().map(CharacterActors::ref).equals(Optional.ofNullable(saved.get("to")))).filter(Estates::guardHolds).isPresent();
        }catch(Exception failure){return false;}
    }
    @Override public Holdings describe(UUID character){
        SiteOwnership store=repository.ownership();List<String> names=new ArrayList<>();
        for(String name:store.names())if(store.plannedDirector(name).map(ref->ref.characterId().equals(character)).orElse(false)||(store.held(name)&&net.tfminecraft.tlibs.character.Characters.info(character).map(info->store.potentialOwner(name,info.ref())).orElse(false)))names.add(name+(store.held(name)?" (held: "+store.holds().get(name)+")":""));
        return new Holdings(Map.of("sites",names.size()),names);
    }
    private record Journal(Path path,Map<String,Object> value){}
    private record Inspection(List<Journal> journals,Map<Long,Set<String>> receipts,Map<String,Object> completion){}
    private static Inspection inspect(SiteOwnership store,EstateDecision decision)throws IOException{
        try{
            List<Journal> journals=new ArrayList<>();Map<Long,Set<String>> receipts=new TreeMap<>();Set<String> completedIds=new HashSet<>();Map<String,Set<String>> recoverable=new HashMap<>();Map<Long,String> decisions=new HashMap<>();
            for(Path path:store.files("ownership/journals")){
                var journal=SiteDocuments.parse(Files.readString(path));if(!journal.containsKey("estate")&&!"estate".equals(journal.get("kind")))continue;var header=SiteDocuments.map(journal.get("estate"));UUID death=UUID.fromString((String)header.get("death"));if(!decision.deathId().equals(death))continue;store.validateReceipt(path,journal);if(!decision.testator().equals(CharacterActors.ref(header.get("from"))))throw new SiteOwnership.Conflict("Site receipt testator conflict");
                long revision=SiteOwnership.integral(header.get("revision"));bindDecision(decisions,revision,(String)header.get("decision"));if(revision==decision.revision()&&(!decision.toString().equals(header.get("decision"))||!decision.property().character().map(CharacterActors::ref).equals(Optional.ofNullable(header.get("to")))))throw new SiteOwnership.Conflict("Same-revision site decision conflict");journals.add(new Journal(path,journal));String name=(String)journal.get("file");String id=name.replace(".yml","");
                if(Boolean.TRUE.equals(journal.get("done"))){if(!completedIds.add(id))throw new SiteOwnership.Conflict("Duplicate completed source receipt "+id);receipts.computeIfAbsent(revision,ignored->new TreeSet<>()).add(id);}
                else{
                    String actual=SiteDocuments.read(store.root().resolve("sites/"+name));boolean same=guard(journal);
                    if(!same&&(revision>=decision.revision()||!Objects.equals(actual,journal.get("before"))))throw new SiteOwnership.Conflict("Stale published/conflicting site intent "+name);
                    if(same&&!Objects.equals(actual,journal.get("before"))&&!Objects.equals(actual,journal.get("after")))throw new SiteOwnership.Conflict("Conflicting pending site intent "+name);
                    recoverable.computeIfAbsent(name,ignored->new HashSet<>()).add((String)journal.get("before"));if(same)recoverable.get(name).add((String)journal.get("after"));
                }
            }
            for(Path path:store.files("ownership/superseded")){
                var journal=SiteDocuments.parse(Files.readString(path));if(!"estate".equals(journal.get("kind"))||!Boolean.FALSE.equals(journal.get("done")))throw new SiteOwnership.Conflict("Invalid superseded estate evidence");var header=SiteDocuments.map(journal.get("estate"));if(!decision.deathId().toString().equals(header.get("death")))continue;store.validateReceipt(path,journal);if(!decision.testator().equals(CharacterActors.ref(header.get("from"))))throw new SiteOwnership.Conflict("Superseded receipt testator conflict");bindDecision(decisions,SiteOwnership.integral(header.get("revision")),(String)header.get("decision"));
            }
            Map<String,Object> completion=SiteCompletions.current(store,decision,receipts,decisions);
            if(completion!=null){SiteCompletions.unchangedRetention(store,decision,completion);for(Journal journal:journals)if(!Boolean.TRUE.equals(journal.value().get("done"))&&SiteOwnership.integral(SiteDocuments.map(journal.value().get("estate")).get("revision"))==decision.revision())throw new SiteOwnership.Conflict("Completed revision has an incomplete site intent");}
            for(String id:completedIds){String actual=SiteDocuments.read(store.root().resolve("sites/"+id+".yml"));if(actual!=null){var raw=SiteDocuments.parse(actual);SiteOwnership.validate(raw);if(decision.testator().equals(SiteOwnership.director(raw)))throw new SiteOwnership.Conflict("Completed source restored to testator "+id);}}
            store.checkInventory(recoverable);return new Inspection(journals,receipts,completion);
        }catch(Exception invalid){throw invalid instanceof IOException io?io:new SiteOwnership.Conflict("Invalid site estate inventory",invalid);}
    }
    private static void bindDecision(Map<Long,String> decisions,long revision,String decision)throws IOException{String previous=decisions.putIfAbsent(revision,decision);if(previous!=null&&!previous.equals(decision))throw new SiteOwnership.Conflict("Site receipt full decisions disagree at revision "+revision);}
    public static void preflightPublication(SiteOwnership store,Map<String,Object> journal)throws IOException{
        UUID death=UUID.fromString((String)SiteDocuments.map(journal.get("estate")).get("death"));EstateDecision current=Estates.decision(death).orElseThrow(()->new IOException("Estate decision unavailable"));inspect(store,current);
    }
    @Override public Outcome settle(EstateDecision decision){
        SiteOwnership store=repository.ownership();if(!Estates.guardHolds(decision))return Outcome.retry("Estate guard changed");
        try{
            Inspection inspection=inspect(store,decision);Set<String> moved=new TreeSet<>();Map<UUID,CharacterRef> repairs=new HashMap<>();Map<Long,Set<String>> receipts=new TreeMap<>();
            for(Journal entry:inspection.journals()){
                Path path=entry.path();Map<String,Object> journal=entry.value();if(!journal.containsKey("estate")&&!"estate".equals(journal.get("kind")))continue;
                Map<String,Object> previous=SiteDocuments.map(journal.get("estate"));if(previous==null)return Outcome.blocked("Malformed estate receipt "+path.getFileName());
                if(!decision.deathId().toString().equals(previous.get("death")))continue;
                try{store.validateReceipt(path,journal);}catch(Exception invalid){return Outcome.blocked("Invalid completed/pending site receipt: "+invalid.getMessage());}
                if(!CharacterActors.ref(decision.testator()).equals(previous.get("from")))return Outcome.blocked("Site receipt testator conflict");
                if(SiteOwnership.integral(previous.get("revision"))==decision.revision()&&(!decision.toString().equals(previous.get("decision"))||!decision.property().character().map(CharacterActors::ref).equals(Optional.ofNullable(previous.get("to")))))return Outcome.blocked("Same-revision site decision conflict");
                if(!Boolean.TRUE.equals(journal.get("done"))){if(!guard(journal)){if(SiteOwnership.integral(previous.get("revision"))>=decision.revision())return Outcome.blocked("Stale incomplete site transfer");supersede(store,path,journal,decision);store.releaseUntouchedPlan(journal);repository.releaseUntouchedPlan(UUID.fromString(((String)journal.get("file")).replace(".yml","")));continue;}store.publish(path,journal);repository.directorCommitted(UUID.fromString(((String)journal.get("file")).replace(".yml","")),CharacterActors.ref(previous.get("to")));}
                String name=(String)journal.get("file");var loaded=repository.findById(UUID.fromString(name.replace(".yml",""))).orElse(null);
                if(Boolean.TRUE.equals(journal.get("done"))&&loaded!=null&&loaded.isOwnershipHeld()&&decision.testator().characterId().equals(loaded.getDirector())&&store.plannedDirector(name).equals(Optional.of(CharacterActors.ref(previous.get("to")))))repairs.put(loaded.getId(),CharacterActors.ref(previous.get("to")));
                String siteId=name.replace(".yml","");if(!moved.add(siteId))return Outcome.blocked("Duplicate completed source receipt "+siteId);receipts.computeIfAbsent(SiteOwnership.integral(previous.get("revision")),ignored->new TreeSet<>()).add(siteId);
            }
            if(!store.ready())return Outcome.blocked("Dossier inventory unavailable");
            store.checkInventory();
            for(String name:store.names())if(moved.contains(name.replace(".yml",""))&&store.plannedDirector(name).map(ref->ref.equals(decision.testator())).orElse(false))return Outcome.blocked("Completed site restored to testator "+name);
            repairs.forEach(repository::directorCommitted);
            var complete=inspection.completion();if(complete!=null){SiteCompletions.unchangedRetention(store,decision,complete);return done(moved);}
            if(decision.property().kind()!=Recipient.Kind.CHARACTER){SiteCompletions.write(store,decision,moved);return done(moved);}
            CharacterRef target=decision.property().character().orElseThrow();if(target.equals(decision.testator()))return Outcome.blocked("Character property recipient equals testator");
            List<String> sources=new ArrayList<>();
            for(String name:store.names())if(store.potentialOwner(name,decision.testator())){
                Map<String,Object> raw=store.documents().get(name);if(raw==null)return Outcome.blocked("Unreadable ownership dossier "+name);
                try{SiteOwnership.validate(raw);if(!decision.testator().equals(SiteOwnership.director(raw)))continue;SiteOwnership.transfer(raw,target);}catch(Exception invalid){return Outcome.blocked("Held site ownership "+name+": "+invalid.getMessage());}
                if(store.held(name)&&!store.holds().get(name).startsWith("Unloaded dossier"))return Outcome.blocked("Held site "+name+": "+store.holds().get(name));sources.add(name);
            }
            for(String name:sources){
                UUID id=UUID.fromString(name.replace(".yml",""));Site site=repository.findById(id).orElse(null);if(site!=null)repository.commit(site);
                if(!Estates.guardHolds(decision))return Outcome.retry("Estate changed after dossier flush");
                Map<String,Object> before=store.documents().get(name);if(!decision.testator().equals(SiteOwnership.director(before)))return Outcome.blocked("Site owner changed "+name);
                Map<String,Object> header=new LinkedHashMap<>();header.put("death",decision.deathId().toString());header.put("revision",decision.revision());header.put("decision",decision.toString());header.put("from",CharacterActors.ref(decision.testator()));header.put("to",CharacterActors.ref(target));
                Map<String,Object> metadata=new LinkedHashMap<>();metadata.put("kind","estate");metadata.put("estate",header);
                try{store.commit(name,store.before(name),SiteOwnership.transfer(before,target),metadata);repository.directorCommitted(id,target);}catch(IOException failure){if(site!=null)site.setOwnershipHeld(true);throw failure;}
                moved.add(id.toString());
            }
            store.checkInventory();SiteCompletions.write(store,decision,moved);return done(moved);
        }catch(Exception failure){repository.syncOwnershipHolds();return failure instanceof SiteOwnership.Conflict?Outcome.blocked(failure.getMessage()):failure instanceof IOException||failure.getCause() instanceof IOException?Outcome.retry("Site I/O: "+failure.getMessage()):Outcome.blocked("Invalid site metadata: "+failure.getMessage());}
    }
    private static void supersede(SiteOwnership store,Path path,Map<String,Object> journal,EstateDecision current)throws IOException{
        String name=(String)journal.get("file");Path source=store.root().resolve("sites/"+name);if(!Objects.equals(SiteDocuments.read(source),journal.get("before")))throw new SiteOwnership.Conflict("Stale site intent has published/conflicting effects "+name);
        if(!Estates.guardHolds(current))throw new IOException("Estate changed before superseding untouched plan");String original=Files.readString(path);if(!SiteDocuments.same(SiteDocuments.parse(original),journal))throw new SiteOwnership.Conflict("Site intent changed before supersession");Path archive=store.root().resolve("ownership/superseded/"+path.getFileName());Files.createDirectories(archive.getParent());for(Path parent=archive.getParent();parent!=null;parent=parent.getParent())SiteDocuments.forceDirectory(parent);
        if(Files.exists(archive)){if(!Files.readString(archive).equals(original))throw new SiteOwnership.Conflict("Superseded intent archive collision");}else Files.createLink(archive,path);SiteDocuments.forceDirectory(archive.getParent());
        if(!Objects.equals(SiteDocuments.read(source),journal.get("before"))||!Estates.guardHolds(current)||!Files.readString(path).equals(original))throw new SiteOwnership.Conflict("Untouched plan changed during supersession");Files.delete(path);SiteDocuments.forceDirectory(path.getParent());
    }
    private static Outcome done(Set<String> moved){return Outcome.done(Map.of("sites",moved.size()),List.copyOf(moved));}
    public static void start(Plugin plugin,SiteRepository repository){SiteEstates participant=new SiteEstates(repository);Bukkit.getServicesManager().register(EstateParticipant.class,participant,plugin,ServicePriority.Normal);Bukkit.getScheduler().runTaskTimer(plugin,()->{for(EstateDecision decision:Estates.decided())try{Estates.report(decision.deathId(),participant.id(),decision.revision(),participant.settle(decision));}catch(Exception failure){plugin.getLogger().warning("Site estate "+decision.deathId()+" revision "+decision.revision()+": "+failure.getMessage());}},3L,20L*60*60);}
}
