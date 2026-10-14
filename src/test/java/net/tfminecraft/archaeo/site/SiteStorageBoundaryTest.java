package net.tfminecraft.archaeo.site;

import net.tfminecraft.archaeo.CharacterTestSupport;
import net.tfminecraft.archaeo.model.*;
import net.tfminecraft.archaeo.ownership.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SiteStorageBoundaryTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    JavaPlugin plugin;SiteRepository repository;Path root;
    @Before public void setup(){CharacterTestSupport.mock();root=temp.getRoot().toPath();plugin=spy(MockBukkit.createMockPlugin());doReturn(root.toFile()).when(plugin).getDataFolder();repository=new SiteRepository(plugin);assertTrue(repository.loadAll());}
    @After public void cleanup(){MockBukkit.unmock();}
    Site site(int serial){Site site=new Site();site.setId(UUID.randomUUID());site.setSerial(serial);site.setWorldName("world");site.setChunkX(serial);site.setStatus(SiteStatus.HIDDEN);site.setInterest(InterestLevel.LOW);site.setCreatedAt(Instant.EPOCH);repository.save(site);return site;}
    Path file(Site site){return root.resolve("sites/"+site.getId()+".yml");}
    List<Path> snapshots()throws Exception{return repository.ownership().files("ownership/conflicts");}

    @Test public void localSourceConflictPreservesDirtyProgressWithoutHoldingHealthyNeighbor()throws Exception{
        Site a=site(1),b=site(2);String before=Files.readString(file(a));
        var raw=SiteDocuments.parse(before);var extension=new LinkedHashMap<String,Object>();extension.put("null",null);extension.put("binary",new byte[]{0,-1});raw.put("future",extension);SiteDocuments.write(file(a),SiteDocuments.text(raw));assertTrue(repository.loadAll());a=repository.findById(a.getId()).orElseThrow();
        String loaded=Files.readString(file(a)),changed=loaded+"external: retained\n";Files.writeString(file(a),changed);
        a.setRecoveredCount(13);repository.touch(a);b.setName("Healthy progress");repository.touch(b);
        assertThrows(SiteOwnership.Conflict.class,()->repository.ownership().checkInventory());repository.syncOwnershipHolds();assertTrue(a.isOwnershipHeld());assertFalse(b.isOwnershipHeld());assertTrue(repository.ownership().ready());
        repository.flushDirty();assertEquals(changed,Files.readString(file(a)));assertEquals("Healthy progress",SiteDocuments.parse(Files.readString(file(b))).get("name"));assertEquals(1,snapshots().size());
        var evidence=SiteDocuments.parse(Files.readString(snapshots().getFirst()));assertEquals(loaded,evidence.get("source"));assertEquals(changed,evidence.get("current"));var captured=SiteDocuments.parse((String)evidence.get("captured"));assertEquals(13,captured.get("recovered-count"));assertTrue(SiteDocuments.same(extension,captured.get("future")));
        assertTrue(repository.loadAll());assertSame(b,repository.findById(b.getId()).orElseThrow());assertTrue(repository.findById(a.getId()).isEmpty());assertTrue(repository.ownership().held(a.getId()+".yml"));assertEquals(changed,Files.readString(file(a)));repository.stop();assertEquals(1,snapshots().size());
    }

    @Test public void failedConflictSnapshotRetainsLiveDirtyStateAndRefusesReloadUntilEvidenceIsDurable()throws Exception{
        Site a=site(1);String changed=Files.readString(file(a))+"external: retained\n";Files.writeString(file(a),changed);a.setRecoveredCount(19);repository.touch(a);
        Path blocked=root.resolve("ownership/conflicts");Files.createDirectories(blocked.getParent());Files.writeString(blocked,"not a directory");
        assertFalse(repository.loadAll());assertSame(a,repository.findById(a.getId()).orElseThrow());assertEquals(19,a.getRecoveredCount());assertEquals(changed,Files.readString(file(a)));repository.stop();assertEquals(changed,Files.readString(file(a)));
        Files.delete(blocked);assertTrue(repository.loadAll());assertEquals(1,snapshots().size());assertEquals(19,SiteDocuments.parse((String)SiteDocuments.parse(Files.readString(snapshots().getFirst())).get("captured")).get("recovered-count"));
    }

    @Test public void unreadableWholeInventoryKeepsOldEvidenceAndSchedulesAnotherInventoryAttempt()throws Exception{
        Site a=site(1);String original=repository.ownership().before(a.getId()+".yml");Path broken=root.resolve("ownership/journals/bad.yml");Files.createDirectories(broken.getParent());Files.writeString(broken,"invalid: [\n");
        assertFalse(repository.loadAll());assertSame(a,repository.findById(a.getId()).orElseThrow());assertEquals(original,repository.ownership().before(a.getId()+".yml"));assertFalse(repository.ownership().ready());assertTrue(repository.needsOwnershipReload());assertTrue(repository.chunkOccupied("world",99,99));
        assertThrows(IllegalStateException.class,()->repository.save(a));Files.delete(broken);assertTrue(repository.loadAll());assertFalse(repository.findById(a.getId()).orElseThrow().isOwnershipHeld());
    }

    @Test public void staleInstanceCannotRestoreAnOldDirectorOrWedgeTheHealthyDirtyQueue()throws Exception{
        Site stale=site(1);UUID old=UUID.randomUUID(),heir=UUID.randomUUID();CharacterTestSupport.setDirector(stale,old);repository.save(stale);
        var changed=SiteDocuments.parse(Files.readString(file(stale)));changed.put("director",heir.toString());SiteDocuments.map(changed.get("actor-refs")).put(heir.toString(),CharacterActors.ref(new net.tfminecraft.tlibs.character.CharacterRef(heir,heir)));SiteDocuments.write(file(stale),SiteDocuments.text(changed));assertTrue(repository.loadAll());Site current=repository.findById(stale.getId()).orElseThrow();assertNotSame(stale,current);
        stale.setRecoveredCount(21);String disk=Files.readString(file(stale));assertThrows(IllegalStateException.class,()->repository.save(stale));assertSame(current,repository.findById(stale.getId()).orElseThrow());assertEquals(heir,current.getDirector());assertEquals(disk,Files.readString(file(stale)));assertEquals(1,snapshots().size());assertTrue(current.isOwnershipHeld());assertTrue(repository.loadAll());assertTrue(repository.findById(stale.getId()).isEmpty());assertTrue(repository.ownership().held(stale.getId()+".yml"));
    }

    @Test public void invalidRuntimeActorRefBecomesCheckedHoldWithPreservedProgress()throws Exception{
        Site a=site(1);UUID actor=UUID.randomUUID();CharacterTestSupport.setDirector(a,actor);repository.save(a);String before=Files.readString(file(a));a.getActorRefs().clear();a.setRecoveredCount(7);
        var failure=assertThrows(IllegalStateException.class,()->repository.save(a));assertTrue(failure.getCause() instanceof SiteOwnership.Conflict);assertTrue(a.isOwnershipHeld());repository.flushDirty();assertEquals(before,Files.readString(file(a)));assertEquals(1,snapshots().size());assertTrue(repository.loadAll());
    }

    @Test public void releasedRuntimeMissingDossierLoadsPromptlyAndAllRawDocumentsReserveTheirChunks()throws Exception{
        Site site=site(4);SiteRepository other=new SiteRepository(plugin);other.ownership().open();assertTrue(other.findById(site.getId()).isEmpty());assertTrue(other.chunkOccupied("world",4,0));other.releaseUntouchedPlan(site.getId());assertTrue(other.findById(site.getId()).isPresent());assertTrue(other.chunkOccupied("world",4,0));
        Files.delete(file(site));assertThrows(IOException.class,()->other.ownership().checkInventory());assertTrue(other.chunkOccupied("world",4,0));assertTrue(other.ownership().ready());
    }

    @Test public void failedDossierDeleteRetainsTheRuntimeCacheAndOwnershipInventory()throws Exception{
        Site site=site(1);String before=Files.readString(file(site));
        try(var reads=mockStatic(SiteDocuments.class,CALLS_REAL_METHODS)){
            reads.when(()->SiteDocuments.read(file(site))).thenReturn(before);Files.delete(file(site));Files.createDirectory(file(site));Files.writeString(file(site).resolve("child"),"blocked");assertThrows(IllegalStateException.class,()->repository.erase(site));
        }
        assertSame(site,repository.findById(site.getId()).orElseThrow());assertTrue(repository.ownership().names().contains(site.getId()+".yml"));assertTrue(Files.exists(file(site).resolve("child")));
    }

    @Test public void removedKnownContainersRetainUnknownSiblingsButNonemptyRecognizedValuesStillHold()throws Exception{
        var unknown=new LinkedHashMap<String,Object>();unknown.put("null",null);unknown.put("binary",new byte[]{0,-1});
        var raw=new LinkedHashMap<String,Object>();raw.put("prospect",new LinkedHashMap<>(Map.of("samples",Map.of(UUID.randomUUID().toString(),List.of("1,2,3")),"confirmed",List.of(),"future",unknown)));raw.put("strata",Map.of("removed",Map.of("present",true,"min-y",1,"max-y",3,"future",unknown)));
        var patched=SiteRawOverlay.patch(raw,Map.of());SiteRawOverlay.validateReadable(patched,Map.of());assertTrue(SiteDocuments.same(unknown,SiteDocuments.map(patched.get("prospect")).get("future")));assertTrue(SiteDocuments.same(unknown,SiteDocuments.map(SiteDocuments.map(patched.get("strata")).get("removed")).get("future")));
        assertThrows(IOException.class,()->SiteRawOverlay.validateReadable(raw,Map.of()));assertThrows(IOException.class,()->SiteRawOverlay.validateReadable(Map.of("prospect",Map.of("samples","bad")),Map.of()));
    }

    @Test public void conflictHoldSurvivesRestartUntilReviewedArchiveAndRetainsMissingRawOwnershipAndOccupancy()throws Exception{
        Site site=site(1);UUID actor=UUID.randomUUID();CharacterTestSupport.setDirector(site,actor);site.setStatus(SiteStatus.ESTABLISHED);site.establish(actor,5,6,81,70,97);repository.save(site);site.setRecoveredCount(13);repository.touch(site);String changed=Files.readString(file(site))+"external: retained\n";Files.writeString(file(site),changed);repository.flushDirty();Path snapshot=snapshots().getFirst();String proof=Files.readString(snapshot);
        SiteRepository restarted=new SiteRepository(plugin);assertTrue(restarted.loadAll());assertTrue(restarted.findById(site.getId()).isEmpty());assertTrue(restarted.ownership().held(site.getId()+".yml"));assertTrue(restarted.chunkOccupied("world",5,6));assertEquals(1,restarted.countDirectedCamps(new net.tfminecraft.tlibs.character.CharacterRef(actor,actor)));
        assertEquals(List.of("Ruin site "+site.getId().toString().substring(0,8)+" (held: Unsaved runtime conflict requires offline reconciliation)"),new SiteEstates(restarted).describe(actor).labels());
        Files.delete(file(site));assertTrue(restarted.loadAll());assertTrue(restarted.ownership().names().contains(site.getId()+".yml"));assertTrue(restarted.chunkOccupied("world",5,6));assertEquals(1,restarted.countDirectedCamps(new net.tfminecraft.tlibs.character.CharacterRef(actor,actor)));assertEquals(0,restarted.countDirectedCamps(new net.tfminecraft.tlibs.character.CharacterRef(UUID.randomUUID(),UUID.randomUUID())));
        // A reviewed offline reconciliation writes the retained care, then archives proof unchanged.
        var reconciled=SiteDocuments.parse(changed);reconciled.put("recovered-count",13);Files.writeString(file(site),SiteDocuments.text(reconciled));Path archive=root.resolve("ownership/reconciled-conflicts/"+snapshot.getFileName());Files.createDirectories(archive.getParent());Files.move(snapshot,archive);assertEquals(proof,Files.readString(archive));assertTrue(restarted.loadAll());assertFalse(restarted.ownership().held(site.getId()+".yml"));assertEquals(13,restarted.findById(site.getId()).orElseThrow().getRecoveredCount());restarted.save(restarted.findById(site.getId()).orElseThrow());assertTrue(restarted.loadAll());assertFalse(restarted.ownership().held(site.getId()+".yml"));assertEquals("retained",SiteDocuments.parse(Files.readString(file(site))).get("external"));assertEquals(proof,Files.readString(archive));
    }

    @Test public void corruptConflictEvidenceNeverEnablesPendingOwnershipPublication()throws Exception{
        Site site=site(1);repository.touch(site);Files.writeString(file(site),Files.readString(file(site))+"external: retained\n");repository.flushDirty();Path snapshot=snapshots().getFirst();String current=Files.readString(file(site));var saved=SiteDocuments.parse(Files.readString(snapshot));var captured=SiteDocuments.parse((String)saved.get("captured"));captured.put("recovered-count",99);saved.put("captured",SiteDocuments.text(captured));SiteDocuments.write(snapshot,SiteDocuments.text(saved));assertFalse(repository.loadAll());assertFalse(repository.ownership().ready());assertEquals(current,Files.readString(file(site)));assertTrue(repository.ownership().files("ownership/journals").isEmpty());
    }

    @Test public void incompleteCurrentConflictMetadataKeepsConservativeCapWithoutUncheckedStatusFailure()throws Exception{
        Site site=site(1);UUID actor=UUID.randomUUID();CharacterTestSupport.setDirector(site,actor);site.setStatus(SiteStatus.ESTABLISHED);repository.save(site);var current=SiteDocuments.parse(Files.readString(file(site)));current.remove("status");Files.writeString(file(site),SiteDocuments.text(current));site.setRecoveredCount(3);repository.touch(site);repository.flushDirty();assertTrue(repository.loadAll());assertEquals(1,repository.countDirectedCamps(new net.tfminecraft.tlibs.character.CharacterRef(actor,actor)));assertTrue(repository.ownership().held(site.getId()+".yml"));
    }

    private Site unpublished(int serial){Site site=new Site();site.setId(UUID.randomUUID());site.setSerial(serial);site.setWorldName("world");site.setChunkX(serial);site.setStatus(SiteStatus.HIDDEN);site.setInterest(InterestLevel.LOW);site.setCreatedAt(Instant.EPOCH);return site;}

    @Test public void refusedFirstCreationHasNoPhantomAndLaterCreationsSurviveReloadAndRestart()throws Exception{
        Site a=site(1);String edited=Files.readString(file(a))+"external: retained\n";Files.writeString(file(a),edited);Site refused=unpublished(2);
        assertThrows(IllegalStateException.class,()->repository.save(refused));repository.flushDirty();assertFalse(Files.exists(file(refused)));assertTrue(repository.findById(refused.getId()).isEmpty());assertFalse(repository.ownership().names().contains(refused.getId()+".yml"));assertFalse(repository.ownership().held(refused.getId()+".yml"));assertTrue(snapshots().isEmpty());assertTrue(a.isOwnershipHeld());assertEquals(edited,Files.readString(file(a)));
        assertTrue(repository.loadAll());assertFalse(repository.ownership().held(a.getId()+".yml"));Site next=unpublished(3);repository.save(next);SiteRepository restarted=new SiteRepository(plugin);assertTrue(restarted.loadAll());Site last=unpublished(4);restarted.save(last);assertTrue(Files.exists(file(next)));assertTrue(Files.exists(file(last)));assertTrue(snapshots().isEmpty());assertTrue(restarted.ownership().holds().isEmpty());
    }

    @Test public void runtimeUnreadableFileHoldsOnlyItAndNeverCapturesHealthyDirtyNeighbour()throws Exception{
        Site a=site(1),b=site(2);String original=Files.readString(file(a));byte[] unreadable=(original+"extension: caf\u00e9\n").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);Files.write(file(a),unreadable);b.setRecoveredCount(17);repository.touch(b);Site refused=unpublished(3);
        assertThrows(IllegalStateException.class,()->repository.save(refused));assertTrue(repository.ownership().ready());assertTrue(a.isOwnershipHeld());assertFalse(b.isOwnershipHeld());repository.flushDirty();assertEquals(17,SiteDocuments.parse(Files.readString(file(b))).get("recovered-count"));assertArrayEquals(unreadable,Files.readAllBytes(file(a)));assertTrue(snapshots().isEmpty());assertFalse(repository.ownership().held(refused.getId()+".yml"));
        Files.writeString(file(a),original);assertTrue(repository.loadAll());SiteRepository restarted=new SiteRepository(plugin);assertTrue(restarted.loadAll());assertFalse(restarted.ownership().held(a.getId()+".yml"));assertFalse(restarted.ownership().held(b.getId()+".yml"));assertEquals(17,restarted.findById(b.getId()).orElseThrow().getRecoveredCount());assertTrue(snapshots().isEmpty());
    }

    @Test public void knownMissingConflictAllowsOtherCreationButLaterReappearanceIsStillUnexpected()throws Exception{
        Site missing=site(1);UUID actor=UUID.randomUUID();CharacterTestSupport.setDirector(missing,actor);missing.setStatus(SiteStatus.ESTABLISHED);repository.save(missing);missing.setRecoveredCount(13);repository.touch(missing);Files.delete(file(missing));repository.flushDirty();assertEquals(1,snapshots().size());repository.ownership().checkInventory();
        SiteRepository restarted=new SiteRepository(plugin);assertTrue(restarted.loadAll());restarted.ownership().checkInventory();Site other=unpublished(3);restarted.save(other);assertTrue(restarted.ownership().held(missing.getId()+".yml"));assertTrue(restarted.chunkOccupied("world",1,0));assertFalse(restarted.chunkOccupied("world",9,0));assertEquals(1,restarted.countDirectedCamps(new net.tfminecraft.tlibs.character.CharacterRef(actor,actor)));
        Files.writeString(file(missing),(String)SiteDocuments.parse(Files.readString(snapshots().getFirst())).get("captured"));assertThrows(SiteOwnership.Conflict.class,()->restarted.ownership().checkInventory());assertTrue(restarted.ownership().held(missing.getId()+".yml"));
    }

    @Test public void dirtyOwnUnreadableFileCapturesOnceAndPreservesProgressThroughReloadStopAndRestart()throws Exception{
        Site seed=site(1);var source=SiteDocuments.parse(Files.readString(file(seed)));var future=new LinkedHashMap<String,Object>();future.put("null",null);future.put("binary",new byte[]{0,-1});source.put("future",future);Files.writeString(file(seed),SiteDocuments.text(source));assertTrue(repository.loadAll());Site a=repository.findById(seed.getId()).orElseThrow();String original=Files.readString(file(a));
        byte[] latin=(original+"extension: caf\u00e9\n").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);Files.write(file(a),latin);a.setRecoveredCount(21);repository.touch(a);repository.flushDirty();repository.flushDirty();
        assertTrue(a.isOwnershipHeld());assertTrue(repository.ownership().held(a.getId()+".yml"));assertEquals(1,snapshots().size());Path snapshot=snapshots().getFirst();String proof=Files.readString(snapshot);var evidence=SiteConflicts.validate(snapshot);assertEquals(original,evidence.get("source"));assertNull(evidence.get("current"));assertTrue(((String)evidence.get("current-error")).contains("MalformedInputException"));var captured=SiteDocuments.parse((String)evidence.get("captured"));assertEquals(21,captured.get("recovered-count"));assertTrue(SiteDocuments.same(future,captured.get("future")));
        assertFalse(repository.loadAll());assertEquals(21,a.getRecoveredCount());repository.stop();repository.stop();assertEquals(1,snapshots().size());assertEquals(proof,Files.readString(snapshot));assertArrayEquals(latin,Files.readAllBytes(file(a)));
        Files.writeString(file(a),original);SiteRepository restarted=new SiteRepository(plugin);assertTrue(restarted.loadAll());assertTrue(restarted.findById(a.getId()).isEmpty());assertTrue(restarted.ownership().held(a.getId()+".yml"));assertTrue(restarted.chunkOccupied("world",1,0));assertEquals(proof,Files.readString(snapshot));
        // Explicit offline reconciliation retains progress and unknown data, then archives unchanged proof.
        var reviewed=SiteDocuments.parse(original);reviewed.put("recovered-count",21);Files.writeString(file(a),SiteDocuments.text(reviewed));Path archive=root.resolve("ownership/reconciled-conflicts/"+snapshot.getFileName());Files.createDirectories(archive.getParent());Files.move(snapshot,archive);assertEquals(proof,Files.readString(archive));assertTrue(restarted.loadAll());assertEquals(21,restarted.findById(a.getId()).orElseThrow().getRecoveredCount());assertFalse(restarted.ownership().held(a.getId()+".yml"));
    }

    @Test public void deletingAfterCaptureAllowsOtherCreationForPresentAndUnreadableFramesButReappearanceStillConflicts()throws Exception{
        for(boolean unreadable:List.of(false,true)){
            Site a=site(unreadable?11:1);String original=Files.readString(file(a));a.setRecoveredCount(13);repository.touch(a);
            if(unreadable)Files.write(file(a),(original+"extension: caf\u00e9\n").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));else Files.writeString(file(a),original+"external: retained\n");repository.flushDirty();Path snapshot=snapshots().stream().filter(path->{try{return (a.getId()+".yml").equals(SiteConflicts.validate(path).get("file"));}catch(IOException failure){throw new AssertionError(failure);}}).findFirst().orElseThrow();String proof=Files.readString(snapshot);repository.stop();Files.delete(file(a));
            repository=new SiteRepository(plugin);assertTrue(repository.loadAll());repository.ownership().checkInventory();repository.save(unpublished(unreadable?12:2));repository=new SiteRepository(plugin);assertTrue(repository.loadAll());repository.save(unpublished(unreadable?13:3));assertTrue(repository.ownership().held(a.getId()+".yml"));assertTrue(repository.chunkOccupied("world",a.getChunkX(),0));assertEquals(proof,Files.readString(snapshot));
            Files.writeString(file(a),original);assertThrows(SiteOwnership.Conflict.class,()->repository.ownership().checkInventory());assertEquals(proof,Files.readString(snapshot));Files.delete(file(a));assertTrue(repository.loadAll());
        }
    }

    @Test public void fullReloadEscalatesLocalUnreadabilityOnlyAfterSavingHealthyDirtyProgress()throws Exception{
        Site a=site(1),b=site(2);String original=Files.readString(file(a));Files.write(file(a),(original+"extension: caf\u00e9\n").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));b.setRecoveredCount(17);repository.touch(b);assertThrows(IllegalStateException.class,()->repository.save(unpublished(3)));assertTrue(a.isOwnershipHeld());assertFalse(b.isOwnershipHeld());assertTrue(repository.ownership().ready());
        assertFalse(repository.loadAll());assertFalse(repository.ownership().ready());assertTrue(b.isOwnershipHeld());assertEquals(17,SiteDocuments.parse(Files.readString(file(b))).get("recovered-count"));assertTrue(snapshots().isEmpty());assertTrue(repository.chunkOccupied("world",99,99));Files.writeString(file(a),original);assertTrue(repository.loadAll());assertFalse(repository.findById(b.getId()).orElseThrow().isOwnershipHeld());assertEquals(17,repository.findById(b.getId()).orElseThrow().getRecoveredCount());
    }
}
