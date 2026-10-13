package net.tfminecraft.archaeo.ownership;

import net.tfminecraft.archaeo.CharacterTestSupport;
import net.tfminecraft.tlibs.character.*;
import org.junit.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SitePlacementsTest {
    Path root;SiteOwnership store;UUID id=UUID.randomUUID();CharacterRef owner=new CharacterRef(UUID.randomUUID(),UUID.randomUUID());Map<String,Object> before,after;
    @Before public void setup()throws Exception{CharacterTestSupport.mock();root=Files.createTempDirectory("archaeo-placement");before=new LinkedHashMap<>();before.put("id",id.toString());before.put("schema",2);before.put("actors-version",1);before.put("actor-refs",Map.of(owner.characterId().toString(),CharacterActors.ref(owner)));before.put("world","world");before.put("director",null);before.put("status","HIDDEN");SiteDocuments.write(root.resolve("sites/"+id+".yml"),SiteDocuments.text(before));store=new SiteOwnership(root);store.open();after=SiteDocuments.copy(before);after.put("director",owner.characterId().toString());after.put("status","ESTABLISHED");}
    @After public void cleanup()throws Exception{MockBukkit.unmock();try(var files=Files.walk(root)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
    Path begin()throws Exception{return SitePlacements.begin(store,id+".yml",after,new CharacterSession(owner,1),false,"world",List.of(Map.of("x",1,"y",2,"z",3,"before","minecraft:air","after","minecraft:oak_sign")),"kit: full-opaque-payload\\n");}
    @Test public void interruptedIntentAndPublishedDossierAreHeldWithoutAutomaticKitOrWorldReplay()throws Exception{
        Path path=begin();String original=store.before(id+".yml");store.open();assertTrue(store.held(id+".yml"));assertEquals(original,Files.readString(root.resolve("sites/"+id+".yml")));assertFalse((Boolean)SiteDocuments.parse(Files.readString(path)).get("done"));
        SiteDocuments.write(root.resolve("sites/"+id+".yml"),SiteDocuments.text(after));store.open();assertTrue(store.held(id+".yml"));assertEquals(SiteDocuments.text(after),Files.readString(root.resolve("sites/"+id+".yml")));assertFalse((Boolean)SiteDocuments.parse(Files.readString(path)).get("done"));
    }
    @Test public void explicitCompletionRequiresExactPublishedDossierAndRetainsEvidenceAfterRestart()throws Exception{
        Path path=begin();assertThrows(IOException.class,()->SitePlacements.complete(store,path));store.save(id,after);SitePlacements.complete(store,path);store.open();assertFalse(store.held(id+".yml"));assertTrue((Boolean)SiteDocuments.parse(Files.readString(path)).get("done"));assertEquals("kit: full-opaque-payload\\n",SiteDocuments.parse(Files.readString(path)).get("kit"));
    }
    @Test public void knownPublicationFailurePreservesSourceAndPendingEffectEvidence()throws Exception{
        Path path=begin();String original=store.before(id+".yml");try(var writes=mockStatic(SiteDocuments.class,CALLS_REAL_METHODS)){writes.when(()->SiteDocuments.write(eq(root.resolve("sites/"+id+".yml")),anyString())).thenThrow(new IOException("disk full"));assertThrows(IOException.class,()->store.save(id,after));}store.open();assertTrue(store.held(id+".yml"));assertEquals(original,Files.readString(root.resolve("sites/"+id+".yml")));assertEquals(false,SiteDocuments.parse(Files.readString(path)).get("done"));
    }
    @Test public void malformedSessionWorldAndDuplicateCoordinatesCannotMasqueradeAsCompletedPlacements()throws Exception{
        Path path=begin();var original=SiteDocuments.parse(Files.readString(path));String source=store.before(id+".yml");for(String fault:List.of("session","world","blocks","moving")){var changed=SiteDocuments.copy(original);switch(fault){case "session"->changed.put("session",((String)changed.get("session")).replace("sessionId=1]","sessionId=1.5]"));case "world"->changed.put("world","another");case "blocks"->{var rows=(List<?>)changed.get("blocks");changed.put("blocks",List.of(rows.getFirst(),rows.getFirst()));}case "moving"->{changed.put("moving",true);changed.put("before",null);}}changed.put("done",true);assertThrows(IOException.class,()->SitePlacements.validate(path,changed));assertEquals(source,Files.readString(root.resolve("sites/"+id+".yml")));}
    }
}
