package net.tfminecraft.archaeo.ownership;

import net.tfminecraft.archaeo.CharacterTestSupport;
import net.tfminecraft.tlibs.character.*;
import net.tfminecraft.tlibs.character.estate.*;
import org.junit.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SiteOwnershipTest {
    Path root;
    final UUID account=UUID.randomUUID(),id=UUID.randomUUID();
    final CharacterRef from=new CharacterRef(account,UUID.randomUUID()),to=new CharacterRef(UUID.randomUUID(),UUID.randomUUID());
    @Before public void setup()throws Exception{CharacterTestSupport.mock();root=Files.createTempDirectory("archaeo-ownership");CharacterTestSupport.heirs.put(account,Optional.of(from));}
    @After public void cleanup()throws Exception{MockBukkit.unmock();try(var files=Files.walk(root)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
    Map<String,Object> raw(boolean character){Map<String,Object> raw=new LinkedHashMap<>();raw.put("id",id.toString());raw.put("schema",character?2:1);raw.put("status","ESTABLISHED");raw.put("director",character?from.characterId().toString():account.toString());raw.put("excavators",List.of(raw.get("director")));Map<String,Object> future=new LinkedHashMap<>();future.put("null",null);future.put("binary",new byte[]{0,1,127,-1});raw.put("future",future);if(character){raw.put("actors-version",1);raw.put("actor-refs",new LinkedHashMap<>(Map.of(from.characterId().toString(),CharacterActors.ref(from))));}return raw;}
    void save(String name,Map<String,Object> raw)throws Exception{SiteDocuments.write(root.resolve("sites/"+name),SiteDocuments.text(raw));}
    @Test public void completeDuplicateIdPreflightPreservesBothOriginalsBeforeAnyMigration()throws Exception{
        String first=id+".yml",second=UUID.randomUUID()+".yml";save(first,raw(false));save(second,raw(false));String a=Files.readString(root.resolve("sites/"+first)),b=Files.readString(root.resolve("sites/"+second));
        SiteOwnership store=new SiteOwnership(root);store.open();assertTrue(store.held(first));assertTrue(store.held(second));assertEquals(a,Files.readString(root.resolve("sites/"+first)));assertEquals(b,Files.readString(root.resolve("sites/"+second)));assertTrue(store.files("ownership/journals").isEmpty());assertEquals(2,store.names().size());assertEquals(2,store.countOpen(from));
    }
    @Test public void fractionalSchemasAndActorVersionsHoldOriginalBytes()throws Exception{
        for(String key:List.of("schema","actors-version")){Path folder=root.resolve(key);Map<String,Object> raw=raw(true);raw.put(key,key.equals("schema")?2.5:1.5);SiteDocuments.write(folder.resolve("sites/"+id+".yml"),SiteDocuments.text(raw));String before=Files.readString(folder.resolve("sites/"+id+".yml"));SiteOwnership store=new SiteOwnership(folder);store.open();assertTrue(store.held(id+".yml"));assertEquals(before,Files.readString(folder.resolve("sites/"+id+".yml")));assertTrue(store.files("ownership/journals").isEmpty());}
    }
    @Test public void fractionalCompletedReceiptVersionAndRevisionCannotPassByTruncation()throws Exception{
        var decision=new EstateDecision(UUID.randomUUID(),from,Optional.of(Instant.EPOCH),Instant.EPOCH,Optional.of(to),Optional.empty(),Optional.empty(),Recipient.FROZEN,Recipient.character(to),Recipient.FROZEN,Recipient.SENDER,1);
        for(String fault:List.of("version","revision")){Map<String,Object> before=raw(true),after=SiteOwnership.transfer(before,to);Map<String,Object> header=new LinkedHashMap<>();header.put("from",CharacterActors.ref(from));header.put("to",CharacterActors.ref(to));header.put("death",decision.deathId().toString());header.put("decision",fault.equals("revision")?decision.toString().replace("revision=1]","revision=1.5]"):decision.toString());header.put("revision",fault.equals("revision")?(Object)1.5:(Object)1L);
            Map<String,Object> journal=new LinkedHashMap<>();journal.put("version",fault.equals("version")?(Object)1.5:(Object)1);journal.put("kind","estate");journal.put("id","receipt");journal.put("file",id+".yml");journal.put("before",SiteDocuments.text(before));journal.put("after",SiteDocuments.text(after));journal.put("estate",header);journal.put("done",true);
            SiteOwnership store=new SiteOwnership(root);assertThrows(IOException.class,()->store.validateReceipt(root.resolve("ownership/journals/receipt.yml"),journal));
        }
    }
    @Test public void reviewedMigrationAndRepeatedRawSavesRetainNestedNullsAndBinary()throws Exception{
        Map<String,Object> original=raw(false);save(id+".yml",original);SiteOwnership store=new SiteOwnership(root);store.open();assertFalse(store.held(id+".yml"));assertEquals(from,SiteOwnership.director(store.documents().get(id+".yml")));
        Map<String,Object> changed=SiteDocuments.copy(store.documents().get(id+".yml"));changed.put("recovered-count",5);store.save(id,changed);store.open();assertEquals(5,store.documents().get(id+".yml").get("recovered-count"));assertTrue(SiteDocuments.map(store.documents().get(id+".yml").get("future")).containsKey("null"));assertArrayEquals(new byte[]{0,1,127,-1},(byte[])SiteDocuments.map(store.documents().get(id+".yml").get("future")).get("binary"));assertEquals(SiteDocuments.text(original),Files.readString(root.resolve("ownership/source/"+id+".yml")));
    }

    @Test public void immutableFixedPlanSurvivesEveryPublicationFailureCutAndChangedHeir()throws Exception{
        for(int cut=1;cut<=5;cut++){
            Path folder=root.resolve("cut"+cut);SiteDocuments.write(folder.resolve("sites/"+id+".yml"),SiteDocuments.text(raw(false)));int selected=cut;int[] count={0};
            try(var writes=mockStatic(SiteDocuments.class,CALLS_REAL_METHODS)){
                writes.when(()->SiteDocuments.write(any(Path.class),anyString())).thenAnswer(call->{call.callRealMethod();if(++count[0]==selected)throw new IOException("crash after durable file");return null;});
                try{new SiteOwnership(folder).open();}catch(IOException expected){}
            }
            boolean fixed=Files.exists(folder.resolve("ownership/plans/"+id+".yml"));
            CharacterTestSupport.heirs.put(account,Optional.of(to));SiteOwnership reboot=new SiteOwnership(folder);reboot.open();
            if(fixed){assertFalse(reboot.held(id+".yml"));assertEquals(from,SiteOwnership.director(reboot.documents().get(id+".yml")));assertEquals(1,reboot.files("ownership/journals").size());}
            else{assertTrue(reboot.held(id+".yml"));assertEquals(SiteDocuments.text(raw(false)),Files.readString(folder.resolve("sites/"+id+".yml")));}
            CharacterTestSupport.heirs.put(account,Optional.of(from));
        }
    }
    @Test public void missingHistoricalWorkerHoldsWholeRawDossierWhileOwnerlessRuinMigrates()throws Exception{
        UUID missing=UUID.randomUUID();CharacterTestSupport.heirs.put(missing,Optional.empty());var raw=raw(false);raw.put("workers",Map.of(missing.toString(),Map.of("role","excavator","blocks-removed",4)));
        save(id+".yml",raw);String before=Files.readString(root.resolve("sites/"+id+".yml"));SiteOwnership store=new SiteOwnership(root);store.open();assertTrue(store.held(id+".yml"));assertEquals(before,Files.readString(root.resolve("sites/"+id+".yml")));assertEquals(Optional.of(from),store.plannedDirector(id+".yml"));assertEquals(1,store.countOpen(from));
        var ownerless=raw(false);ownerless.put("id",UUID.randomUUID().toString());ownerless.put("director",null);ownerless.put("excavators",List.of());ownerless.put("status","HIDDEN");save(ownerless.get("id")+".yml",ownerless);store.open();assertFalse(store.held(ownerless.get("id")+".yml"));assertNull(SiteOwnership.director(store.documents().get(ownerless.get("id")+".yml")));
    }
    @Test public void nestedDuplicateKeysAndMalformedSourcesRetainTheirOriginalBytes()throws Exception{
        for(String suffix:List.of("future: {null: null, null: changed}\n","future: &loop [*loop]\n","workers: {bad: {role: director}}\n")){
            Path folder=root.resolve(UUID.randomUUID().toString());String text=SiteDocuments.text(raw(false)).replaceAll("(?s)future:.*","")+suffix;SiteDocuments.write(folder.resolve("sites/"+id+".yml"),text);SiteOwnership store=new SiteOwnership(folder);store.open();assertTrue(store.held(id+".yml"));assertEquals(text,Files.readString(folder.resolve("sites/"+id+".yml")));assertEquals(text,Files.readString(folder.resolve("ownership/source/"+id+".yml")));
        }
    }
    @Test public void hashConsistentMigrationTamperingNeverPublishesAnUnrelatedChange()throws Exception{
        save(id+".yml",raw(false));SiteOwnership store=new SiteOwnership(root);store.open();Path path=store.files("ownership/journals").getFirst();var receipt=SiteDocuments.parse(Files.readString(path));String current=Files.readString(root.resolve("sites/"+id+".yml"));
        for(String fault:List.of("director","history","path","kind","mapping")){
            var changed=SiteDocuments.copy(receipt);var after=SiteDocuments.parse((String)changed.get("after"));
            switch(fault){case "director"->after.put("director",UUID.randomUUID().toString());case "history"->after.put("future",Map.of("lost",true));case "path"->changed.put("file","../outside.yml");case "kind"->changed.put("kind","gameplay");case "mapping"->{var extra=SiteDocuments.map(changed.get("legacy-map"));extra.put(UUID.randomUUID().toString(),null);}}
            changed.put("after",SiteDocuments.text(after));changed.put("done",false);assertThrows(IOException.class,()->store.publish(path,changed));assertEquals(current,Files.readString(root.resolve("sites/"+id+".yml")));
        }
    }
    @Test public void postLoadEditBlocksOrdinarySaveInsteadOfOverwriting()throws Exception{
        save(id+".yml",raw(true));SiteOwnership store=new SiteOwnership(root);store.open();String changed=Files.readString(root.resolve("sites/"+id+".yml"))+"external: retained\n";Files.writeString(root.resolve("sites/"+id+".yml"),changed);assertThrows(IOException.class,()->store.save(id,raw(true)));assertEquals(changed,Files.readString(root.resolve("sites/"+id+".yml")));
    }

    @Test public void numericJournalIdCannotPassThroughMatchingFilenameCoercion()throws Exception{
        save(id+".yml",raw(false));SiteOwnership store=new SiteOwnership(root);store.open();var receipt=SiteDocuments.parse(Files.readString(store.files("ownership/journals").getFirst()));receipt.put("id",1);receipt.put("done",false);Path numeric=root.resolve("ownership/journals/1.yml");String current=store.before(id+".yml");assertThrows(IOException.class,()->store.publish(numeric,receipt));assertEquals(current,Files.readString(root.resolve("sites/"+id+".yml")));assertFalse(Files.exists(numeric));
    }
}
