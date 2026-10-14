package net.tfminecraft.archaeo.site;

import net.tfminecraft.archaeo.model.*;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SiteRepositoryTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private JavaPlugin plugin;
    private SiteRepository repository;
    private static final Instant NOW = Instant.parse("2026-02-03T04:05:06Z");

    @Before public void setup() {
        plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(temporary.getRoot());
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        repository = new SiteRepository(plugin);
    }

    @Test public void roundTripsFullDossierWithoutLosingWorkersFindsOrCampState() throws Exception {
        Site site = site(7, "world", 2, -3, SiteStatus.ESTABLISHED);
        UUID owner = UUID.randomUUID(), workerId = UUID.randomUUID();
        site.setName("River camp");
        site.setCreatedBy(owner);
        net.tfminecraft.archaeo.CharacterTestSupport.setDirector(site, owner);
        site.setVisibility("public");
        site.setSurfaceY(72);
        site.setRecoveredCount(2);
        site.getHintIds().add("pottery");
        site.getExcavators().add(net.tfminecraft.archaeo.CharacterTestSupport.actor(site, workerId));
        site.getFactions().add("historians");
        site.relocateCamp(3, -3, 48, 73, -48);
        site.setCampSignX(49); site.setCampSignY(74); site.setCampSignZ(-47);
        site.getCampBlocks().add(new BlockCell(48, 73, -48));
        site.setCampWoolPrimary("BLUE"); site.setCampWoolSecondary("WHITE"); site.setCampFacing("NORTH");
        site.setJornadaWorldDay(42); site.setJornadaPickLeft(9);
        site.getFillDamage().put(new BlockCell(33, 60, -40), 4);
        net.tfminecraft.archaeo.CharacterTestSupport.confirmProspect(site, owner);
        net.tfminecraft.archaeo.CharacterTestSupport.addProspectSample(site, owner, new BlockCell(32, 70, -48));
        site.allProspectSamples().put(workerId, List.of());
        StratumBand band = band("roman", 60, 70);
        band.setDisturbed(true);
        site.getStrata().put("roman", band);
        WorkerRecord worker = net.tfminecraft.archaeo.CharacterTestSupport.worker(site, workerId);
        worker.setRole(SiteRole.values()[0]);
        worker.setBlocksRemoved(11); worker.setCellsBrushed(12); worker.setFindsRecovered(13);
        worker.setFindsDamaged(14); worker.setFindsLost(15);
        worker.setJoinedAt(NOW); worker.setLastActiveAt(NOW.plusSeconds(1));
        net.tfminecraft.archaeo.CharacterTestSupport.worker(site, owner).setJoinedAt(null);
        BuriedFind find = find();
        find.setItem("itemsadder:archaeo:coin"); find.setGivenName("River coin");
        for (int x = 0; x < 10; x++) find.getCells().add(new BlockCell(x, 65, 0));
        find.setBuriedConservation(90);
        find.woundBeforeDig(find.getCells().get(0));
        find.woundFromAbove(find.getCells().get(1));
        find.woundDirect(find.getCells().get(2));
        find.markCleaned(find.getCells().get(3));
        find.setBrushRemaining(find.getCells().get(4), 12);
        find.setState(FindState.RECOVERED); find.setFindNumber(4);
        find.setRecoveredBy(owner); find.setRecoveredAt(NOW);
        find.setStudied(true); find.setLabCleaned(true); find.setFieldSketch(true);
        find.setStudyNotes("Hammered silver");
        find.addInterpretation(new FindInterpretation("purpose", "coin", workerId, NOW));
        find.addInterpretation(new FindInterpretation(null, "legacy", null, null));
        find.addInterpretation(new FindInterpretation(" ", "unknown", null, null));
        site.getFinds().add(find);
        BuriedFind blank = find(); blank.setStudyNotes(" "); site.getFinds().add(blank);
        site.getFinds().add(find());
        net.tfminecraft.archaeo.CharacterTestSupport.save(repository, site);
        YamlConfiguration before = YamlConfiguration.loadConfiguration(file(site));
        assertEquals(SiteRepository.SCHEMA, before.getInt("schema"));
        SiteRepository restored = new SiteRepository(plugin); restored.loadAll();
        Site copy = restored.findById(site.getId()).orElseThrow();
        assertEquals(8, restored.nextSerial());
        assertEquals(site.getId(), copy.getId()); assertEquals("River camp", copy.getName());
        assertEquals(owner, copy.getCreatedBy()); assertEquals(NOW, copy.getCreatedAt());
        assertEquals(owner, copy.getDirector()); assertEquals("public", copy.getVisibility());
        assertEquals(72, copy.getSurfaceY()); assertEquals(2, copy.getRecoveredCount());
        assertEquals(site.getHintIds(), copy.getHintIds()); assertEquals(site.getExcavators(), copy.getExcavators());
        assertEquals(site.getFactions(), copy.getFactions()); assertEquals(site.getCampBlocks(), copy.getCampBlocks());
        assertEquals(Integer.valueOf(49), copy.getCampSignX()); assertEquals(Integer.valueOf(74), copy.getCampSignY());
        assertEquals(Integer.valueOf(-47), copy.getCampSignZ()); assertEquals("BLUE", copy.getCampWoolPrimary());
        assertEquals("WHITE", copy.getCampWoolSecondary()); assertEquals("NORTH", copy.getCampFacing());
        assertEquals(42, copy.getJornadaWorldDay()); assertEquals(9, copy.getJornadaPickLeft());
        assertEquals(site.getFillDamage(), copy.getFillDamage()); assertTrue(copy.isProspectConfirmed(owner));
        assertEquals(site.prospectSamples(owner), copy.prospectSamples(owner));
        assertTrue(copy.getStrata().get("roman").isDisturbed());
        WorkerRecord workerCopy = copy.getWorkers().get(workerId);
        assertEquals(worker.getRole(), workerCopy.getRole()); assertEquals(11, workerCopy.getBlocksRemoved());
        assertEquals(12, workerCopy.getCellsBrushed()); assertEquals(13, workerCopy.getFindsRecovered());
        assertEquals(14, workerCopy.getFindsDamaged()); assertEquals(15, workerCopy.getFindsLost());
        assertEquals(NOW, workerCopy.getJoinedAt()); assertEquals(NOW.plusSeconds(1), workerCopy.getLastActiveAt());
        BuriedFind piece = copy.getFinds().getFirst();
        assertEquals(find.getId(), piece.getId()); assertEquals("coin", piece.getArtifactId());
        assertEquals("roman", piece.getStratumId()); assertEquals(find.getItem(), piece.getItem());
        assertEquals("River coin", piece.getGivenName()); assertEquals(50, piece.getConservation());
        assertEquals(90, piece.getBuriedConservation()); assertEquals(FindState.RECOVERED, piece.getState());
        assertEquals(find.getCells(), piece.getCells()); assertEquals(find.getPriorCells(), piece.getPriorCells());
        assertEquals(find.getGrazedCells(), piece.getGrazedCells());
        assertEquals(find.getDirectHitCells(), piece.getDirectHitCells());
        assertEquals(find.getCleanedCells(), piece.getCleanedCells());
        assertEquals(find.getBrushRemaining(), piece.getBrushRemaining());
        assertEquals(4, piece.getFindNumber()); assertEquals(owner, piece.getRecoveredBy());
        assertEquals(NOW, piece.getRecoveredAt()); assertTrue(piece.isStudied());
        assertTrue(piece.isLabCleaned()); assertTrue(piece.hasFieldSketch());
        assertEquals("Hammered silver", piece.getStudyNotes());
        assertEquals(3, piece.getInterpretations().size());
        assertEquals(workerId, piece.getInterpretations().getFirst().author());
        assertEquals(NOW, piece.getInterpretations().getFirst().recordedAt());
        net.tfminecraft.archaeo.CharacterTestSupport.commit(restored, copy);
        assertEquals(before.saveToString(), YamlConfiguration.loadConfiguration(file(site)).saveToString());
    }

    @Test public void queriesSeparateWorldsBoundsNamesLifecycleAndDirectors() {
        UUID owner = UUID.randomUUID();
        Site hidden = site(1, "world", 0, 0, SiteStatus.HIDDEN);
        Site established = site(2, "world", 1, 0, SiteStatus.ESTABLISHED);
        Site exhausted = site(3, "other", 2, 0, SiteStatus.EXHAUSTED);
        Site closed = site(4, "world", 3, 0, SiteStatus.CLOSED);
        hidden.setName(null); established.setName("Alpha"); exhausted.setName("Alpha"); closed.setName(" ");
        for (Site s : List.of(hidden, established, exhausted, closed)) {
            net.tfminecraft.archaeo.CharacterTestSupport.setDirector(s, owner); s.getStrata().put("roman", band("roman", 50, 60)); net.tfminecraft.archaeo.CharacterTestSupport.touch(repository, s);
        }
        established.relocateCamp(8, 9, 128, 70, 144);
        exhausted.relocateCamp(10, 11, 160, 70, 176);
        established.getCampBlocks().add(new BlockCell(128, 70, 144));
        assertEquals(Optional.of(hidden), repository.findByChunk("world", 0, 0));
        assertTrue(repository.findByChunk("wrong", 0, 0).isEmpty());
        assertTrue(repository.findByChunk("world", 0, 1).isEmpty());
        assertEquals(Optional.of(established), repository.findByEstablishmentChunk("world", 8, 9));
        assertTrue(repository.findByEstablishmentChunk("other", 8, 9).isEmpty());
        assertTrue(repository.findByEstablishmentChunk("world", 8, 10).isEmpty());
        assertTrue(repository.findByEstablishmentChunk("world", 9, 9).isEmpty());
        assertTrue(repository.chunkOccupied("world", 0, 0));
        assertTrue(repository.chunkOccupied("world", 8, 9));
        assertFalse(repository.chunkOccupied("world", 99, 99));
        assertEquals(Optional.of(established), repository.findLockedCampBlock("world", 128, 70, 144));
        assertTrue(repository.findLockedCampBlock("other", 128, 70, 144).isEmpty());
        assertTrue(repository.findLockedCampBlock("world", 128, 71, 144).isEmpty());
        assertEquals(Optional.of(hidden), repository.findPrism("world", 1, 55, 1));
        assertEquals(Optional.of(established), repository.findEstablishedPrism("world", 17, 55, 1));
        assertTrue(repository.findEstablishedPrism("world", 1, 55, 1).isEmpty());
        assertTrue(repository.findPrism("world", 1, 80, 1).isEmpty());
        assertTrue(repository.findPrism("other", 33, 55, 1).isEmpty());
        assertTrue(repository.findPrism("world", 49, 55, 1).isEmpty());
        assertEquals(Optional.of(exhausted), repository.findBySerial(3));
        assertTrue(repository.findBySerial(9).isEmpty());
        assertTrue(repository.findById(UUID.randomUUID()).isEmpty());
        assertTrue(repository.findByName(null).isEmpty()); assertTrue(repository.findByName(" ").isEmpty());
        assertEquals(Set.of(established, exhausted), new HashSet<>(repository.findByName(" ALPHA ")));
        assertTrue(repository.findByName("beta").isEmpty());
        assertEquals(List.of("Alpha"), repository.namesStartingWith(null));
        assertEquals(List.of("Alpha"), repository.namesStartingWith("aL"));
        assertTrue(repository.namesStartingWith("z").isEmpty());
        assertEquals(new SiteCensus(4, 1, 1, 1, 1), repository.census());
        assertEquals(new SiteCensus(3, 1, 1, 0, 1), repository.census("WORLD"));
        assertEquals(List.of(established, exhausted), repository.findDirectedCamps(owner));
        assertEquals(2, repository.countDirectedCamps(owner));
        assertTrue(repository.findDirectedCamps(null).isEmpty());
        assertTrue(repository.findDirectedCamps(UUID.randomUUID()).isEmpty());
        assertEquals(4, repository.all().size());
        assertThrows(UnsupportedOperationException.class, () -> repository.all().clear());
    }

    @Test public void flushTimerStartsOnceStopsAndWritesPendingChanges() {
        Server server = mock(Server.class); BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        when(plugin.getServer()).thenReturn(server); when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(20L), eq(20L))).thenReturn(task);
        repository.start(); repository.start();
        ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskTimer(eq(plugin), tick.capture(), eq(20L), eq(20L));
        Site site = site(1, "world", 0, 0, SiteStatus.HIDDEN);
        net.tfminecraft.archaeo.CharacterTestSupport.touch(repository, site); assertFalse(file(site).exists()); tick.getValue().run(); assertTrue(file(site).exists());
        site.setName("Changed"); net.tfminecraft.archaeo.CharacterTestSupport.touch(repository, site); repository.stop(); repository.stop();
        verify(task).cancel(); assertEquals("Changed", YamlConfiguration.loadConfiguration(file(site)).getString("name"));
        repository.start(); verify(scheduler, times(2)).runTaskTimer(eq(plugin), any(Runnable.class), eq(20L), eq(20L));
        repository.stop();
    }

    @Test public void deletionAndErasureClearDirtyEntriesAndPreserveSerialIndex() {
        repository.loadAll();
        assertEquals(1, repository.nextSerial()); assertEquals(2, repository.nextSerial());
        Site site = site(2, "world", 0, 0, SiteStatus.HIDDEN); net.tfminecraft.archaeo.CharacterTestSupport.commit(repository, site);
        net.tfminecraft.archaeo.CharacterTestSupport.touch(repository, site); repository.delete(site);
        assertTrue(repository.all().isEmpty()); assertFalse(file(site).exists());
        File trashed = new File(new File(file(site).getParentFile(), ".trash"), file(site).getName());
        assertTrue(trashed.exists()); repository.flushDirty(); assertFalse(file(site).exists());
        repository.loadAll(); assertEquals(3, repository.nextSerial());
        net.tfminecraft.archaeo.CharacterTestSupport.commit(repository, site); repository.erase(site);
        assertFalse(file(site).exists()); assertFalse(trashed.exists());
        repository.delete(site); repository.erase(site);
        repository.delete(null); repository.delete(new Site()); repository.erase(null); repository.erase(new Site());
        net.tfminecraft.archaeo.CharacterTestSupport.touch(repository, null); net.tfminecraft.archaeo.CharacterTestSupport.touch(repository, new Site()); repository.flushDirty();
        assertThrows(IllegalArgumentException.class, () -> net.tfminecraft.archaeo.CharacterTestSupport.commit(repository, null));
        assertThrows(IllegalArgumentException.class, () -> net.tfminecraft.archaeo.CharacterTestSupport.commit(repository, new Site()));
    }

    @Test public void failedCommitRemainsDirtyAndSucceedsWhenStorageRecovers() throws Exception {
        Path sites = temporary.getRoot().toPath().resolve("sites"); Files.writeString(sites, "blocked");
        Site site = site(1, "world", 0, 0, SiteStatus.HIDDEN);
        assertThrows(IllegalStateException.class, () -> net.tfminecraft.archaeo.CharacterTestSupport.commit(repository, site));
        repository.flushDirty(); assertSame(site, repository.findById(site.getId()).orElseThrow());
        repository.loadAll(); assertSame(site, repository.findById(site.getId()).orElseThrow());
        Files.delete(sites); repository.flushDirty(); assertTrue(file(site).exists());
        assertFalse(new File(file(site).getPath() + ".tmp").exists());
    }

    @Test public void loadSkipsInvalidDossiersAndTemporaryFilesAndUsesLegacyDefaults() throws Exception {
        repository.loadAll();
        Files.writeString(temporary.getRoot().toPath().resolve("sites/bad.yml"), "id: broken\n");
        Files.writeString(temporary.getRoot().toPath().resolve("sites/pending.yml.tmp"), "id: ignored\n");
        UUID id = UUID.randomUUID(); YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", id.toString()); yaml.set("world", "world"); yaml.set("schema", 99);
        yaml.set("workers.bad-uuid.role", "excavator"); yaml.set("workers.scalar", "ignored");
        UUID worker = UUID.randomUUID(); yaml.set("workers." + worker + ".joined-at", "invalid");
        yaml.set("workers." + worker + ".last-active-at", " ");
        yaml.set("strata.scalar", "ignored");
        yaml.set("establishment.chunk-x", 4); yaml.set("establishment.chunk-z", 5);
        yaml.set("establishment.blocks", List.of("bad", "1,2,3")); yaml.set("establishment.wool", "GREEN");
        yaml.set("fill-damage", List.of("bad", "1:2", "1,2,3:4"));
        yaml.set("prospect.samples." + worker, List.of("bad", "4,5,6"));
        yaml.save(new File(temporary.getRoot(), "sites/legacy.yml"));
        String before=Files.readString(new File(temporary.getRoot(), "sites/legacy.yml").toPath());
        repository.loadAll(); assertTrue(repository.all().isEmpty());assertTrue(repository.ownership().held("legacy.yml"));
        assertEquals(before,Files.readString(new File(temporary.getRoot(), "sites/legacy.yml").toPath()));
        assertTrue(repository.ownership().names().contains("legacy.yml"));

    }

    @Test public void readsLegacyAndCorruptFindFieldsWithoutLosingTheDossier() throws Exception {
        repository.loadAll();
        UUID id = UUID.randomUUID(); YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", id.toString()); yaml.set("world", "world");
        List<Object> rows = new ArrayList<>(); rows.add("ignored");
        Map<String, Object> first = row();
        first.put("item", "null"); first.put("given-name", "§ "); first.put("conservation", "bad");
        first.put("find-number", "bad"); first.put("recovered-by", "bad"); first.put("recovered-at", "bad");
        first.put("studied", "true"); first.put("lab-cleaned", "false"); first.put("field-sketch", true);
        first.put("cells", List.of("invalid", " 1, 2, 3 "));
        first.put("brush-remaining", List.of("invalid", "1:2", "1,2,3:0", "1,2,3:-2", "x,2,3:5", "1,2,3:bad", "1,2,3:7"));
        first.put("interpretations", List.of("ignored", Map.of("type", "no-id"),
                Map.of("id", "coin", "author", "bad", "at", "bad"), Map.of("id", "legacy")));
        rows.add(first);
        Map<String, Object> blank = row(); blank.put("item", " "); rows.add(blank);
        Map<String, Object> low = row(); low.put("conservation", -1); rows.add(low);
        Map<String, Object> high = row(); high.put("conservation", 101); rows.add(high);
        for (String damage : List.of("grazed-cells", "direct-hit-cells", "prior-cells")) {
            Map<String, Object> hurt = row(); hurt.put("cells", List.of("1,2,3", "2,2,3", "3,2,3", "4,2,3"));
            hurt.put(damage, List.of("1,2,3")); rows.add(hurt);
        }
        yaml.set("finds", rows); yaml.save(new File(temporary.getRoot(), "sites/legacy.yml"));
        String before=Files.readString(new File(temporary.getRoot(), "sites/legacy.yml").toPath());
        repository.loadAll();assertTrue(repository.findById(id).isEmpty());assertTrue(repository.ownership().held("legacy.yml"));
        assertEquals(before,Files.readString(new File(temporary.getRoot(), "sites/legacy.yml").toPath()));

    }


    @Test public void campsWithoutSignsAndLegacyWoolRemainReadable() throws Exception {
        Site site = site(1, "world", 0, 0, SiteStatus.ESTABLISHED);
        site.relocateCamp(1, 1, 16, 60, 16);
        net.tfminecraft.archaeo.CharacterTestSupport.commit(repository, site);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file(site));
        yaml.set("establishment.wool-secondary", null);
        yaml.save(file(site));
        repository.loadAll();
        Site copy = repository.findById(site.getId()).orElseThrow();
        assertNull(copy.getCampFacing()); assertNull(copy.getCampSignX());
        assertEquals("RED", copy.getCampWoolSecondary());
    }

    @Test public void uncreatableDataFolderPreservesLiveStateAndRejectsCommits() throws Exception {
        File blocked = temporary.newFile("blocked");
        when(plugin.getDataFolder()).thenReturn(blocked);
        SiteRepository failed = new SiteRepository(plugin);
        failed.loadAll(); assertTrue(failed.all().isEmpty());
        Site site = site(1, "world", 0, 0, SiteStatus.HIDDEN); failed.touch(site);
        failed.loadAll(); assertSame(site, failed.findById(site.getId()).orElseThrow());
        assertThrows(IllegalStateException.class, () -> failed.commit(site));
        failed.flushDirty(); assertSame(site, failed.findById(site.getId()).orElseThrow());
    }

    @Test public void emptyRepositorySurvivesAnUnlistableSitesPath() throws Exception {
        Files.writeString(temporary.getRoot().toPath().resolve("sites"), "file not directory");
        repository.loadAll(); assertTrue(repository.all().isEmpty()); assertEquals(1, repository.nextSerial());
    }

    @Test public void atomicMoveFallbackStillCommitsTheDossier() throws Exception {
        Site site = site(1, "world", 0, 0, SiteStatus.HIDDEN);
        Path target = file(site).toPath(); Path source = Path.of(target + ".tmp");
        try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.move(source, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING))
                    .thenThrow(new java.nio.file.AtomicMoveNotSupportedException(source.toString(), target.toString(), "test filesystem"));
            net.tfminecraft.archaeo.CharacterTestSupport.commit(repository, site);
        }
        assertEquals(site.getId().toString(), YamlConfiguration.loadConfiguration(file(site)).getString("id"));
        assertFalse(Files.exists(source));
    }

    @Test public void indexFailureKeepsDossierAndRetriesAfterTheObstacleIsRemoved() throws Exception {
        Path blocked = temporary.getRoot().toPath().resolve("sites-index.yml.tmp");
        Files.createDirectory(blocked); Path child = blocked.resolve("child"); Files.writeString(child, "blocked");
        repository.nextSerial(); Site site = site(1, "world", 0, 0, SiteStatus.HIDDEN);
        net.tfminecraft.archaeo.CharacterTestSupport.commit(repository, site); assertTrue(file(site).exists());
        assertFalse(new File(temporary.getRoot(), "sites-index.yml").exists());
        Files.delete(child); Files.delete(blocked); net.tfminecraft.archaeo.CharacterTestSupport.commit(repository, site);
        assertEquals(2, YamlConfiguration.loadConfiguration(new File(temporary.getRoot(), "sites-index.yml")).getInt("next-serial"));
    }

    @Test public void trashMoveFailureRetainsDossierAndEraseRefusesNonemptyDirectories() throws Exception {
        Site site = site(1, "world", 0, 0, SiteStatus.HIDDEN); net.tfminecraft.archaeo.CharacterTestSupport.commit(repository, site);
        Path trash = file(site).toPath().getParent().resolve(".trash"); Files.writeString(trash, "not a directory");
        assertThrows(IllegalStateException.class,()->repository.delete(site)); assertTrue(file(site).exists()); assertFalse(repository.all().isEmpty());
        Files.delete(trash); Files.createDirectories(trash);
        Files.delete(file(site).toPath()); Files.createDirectory(file(site).toPath()); Files.writeString(file(site).toPath().resolve("child"), "blocked");
        Path copy = trash.resolve(file(site).getName()); Files.createDirectory(copy); Files.writeString(copy.resolve("child"), "blocked");
        assertThrows(IllegalStateException.class,()->repository.erase(site)); assertTrue(file(site).exists()); assertTrue(Files.exists(copy));
    }

    // Must run as a non-root user (as GitHub Actions does): root ignores the folder's write bit.
    @Test public void readOnlySitesFolderKeepsOldFilesRetriesWritesAndStillDropsDeletedDossiersFromTheSession() throws Exception {
        Site kept = site(1, "world", 0, 0, SiteStatus.HIDDEN), trashed = site(2, "world", 1, 0, SiteStatus.HIDDEN), dropped = site(3, "world", 2, 0, SiteStatus.HIDDEN);
        kept.setName("Before"); for (Site site : List.of(kept, trashed, dropped)) net.tfminecraft.archaeo.CharacterTestSupport.commit(repository, site);
        File folder = file(kept).getParentFile();
        try {
            assertTrue(folder.setWritable(false));
            kept.setName("After");
            assertThrows(IllegalStateException.class, () -> net.tfminecraft.archaeo.CharacterTestSupport.commit(repository, kept));
            assertEquals("Before", YamlConfiguration.loadConfiguration(file(kept)).getString("name"));
            repository.flushDirty();
            verify(plugin.getLogger()).log(eq(java.util.logging.Level.WARNING), eq("Could not flush site " + kept.getId()), any(IllegalStateException.class));
            assertThrows(IllegalStateException.class,()->repository.delete(dropped));
            assertTrue(repository.findById(dropped.getId()).isPresent());assertTrue(file(dropped).exists());
            assertTrue(folder.setWritable(true)); assertTrue(new File(folder, ".trash").mkdir()); assertTrue(folder.setWritable(false));
            assertThrows(IllegalStateException.class,()->repository.delete(trashed));
            assertTrue(repository.findById(trashed.getId()).isPresent()); assertTrue(file(trashed).exists());
        } finally {
            folder.setWritable(true);
        }
        repository.flushDirty();
        assertEquals("After", YamlConfiguration.loadConfiguration(file(kept)).getString("name"));
    }

    @Test public void unreachableTrashRetainsDossierAcrossRestart() throws Exception {
        Site site = site(1, "world", 0, 0, SiteStatus.HIDDEN); net.tfminecraft.archaeo.CharacterTestSupport.commit(repository, site);
        // e.g. .trash linked to a backup disk that is not mounted
        Path trash = file(site).toPath().getParent().resolve(".trash");
        Files.createSymbolicLink(trash, temporary.getRoot().toPath().resolve("unmounted/trash"));
        assertThrows(IllegalStateException.class,()->repository.delete(site));assertTrue(file(site).exists());
        repository.loadAll();assertTrue(repository.findById(site.getId()).isPresent());
    }

    @Test public void failedReplaceRemovesItsTempFileAndLeavesTheSiteDirtyForTheNextFlush() throws Exception {
        Site site = site(1, "world", 0, 0, SiteStatus.HIDDEN); net.tfminecraft.archaeo.CharacterTestSupport.commit(repository, site);
        Files.delete(file(site).toPath()); Files.createDirectory(file(site).toPath()); Files.writeString(file(site).toPath().resolve("child"), "squatter");
        site.setName("Renamed");
        assertThrows(IllegalStateException.class, () -> net.tfminecraft.archaeo.CharacterTestSupport.commit(repository, site));
        assertFalse(new File(file(site).getPath() + ".tmp").exists());
        Files.delete(file(site).toPath().resolve("child")); Files.delete(file(site).toPath());
        // Restoring the exact checked source permits an ordinary I/O retry; disappearance conflicts.
        repository.flushDirty();assertTrue(site.isOwnershipHeld());assertFalse(file(site).exists());
    }

    private File file(Site site) { return new File(temporary.getRoot(), "sites/" + site.getId() + ".yml"); }
    private static Site site(int serial, String world, int x, int z, SiteStatus status) {
        Site site = new Site(); site.setId(UUID.randomUUID()); site.setSerial(serial); site.setWorldName(world);
        site.setChunkX(x); site.setChunkZ(z); site.setStatus(status); site.setInterest(InterestLevel.MEDIUM);
        site.setCreatedAt(NOW); return site;
    }
    private static StratumBand band(String id, int min, int max) {
        StratumBand band = new StratumBand(); band.setId(id); band.setPresent(true); band.setMinY(min); band.setMaxY(max);
        return band;
    }
    private static BuriedFind find() {
        BuriedFind find = new BuriedFind(); find.setId(UUID.randomUUID()); find.setArtifactId("coin");
        find.setStratumId("roman"); return find;
    }
    private static Map<String, Object> row() {
        Map<String, Object> row = new LinkedHashMap<>(); row.put("id", UUID.randomUUID().toString());
        row.put("artifact-id", "coin"); row.put("stratum", "roman"); return row;
    }

    @Test public void ordinaryModelSavesPreserveUnknownRootWorkerFindInterpretationNullAndBinaryAfterRestart()throws Exception{
        Site original=site(1,"world",0,0,SiteStatus.ESTABLISHED);UUID owner=UUID.randomUUID();net.tfminecraft.archaeo.CharacterTestSupport.setDirector(original,owner);net.tfminecraft.archaeo.CharacterTestSupport.worker(original,owner).setBlocksRemoved(3);BuriedFind find=find();find.addInterpretation(new FindInterpretation("purpose","coin",owner,NOW));original.getFinds().add(find);net.tfminecraft.archaeo.CharacterTestSupport.save(repository,original);
        var raw=net.tfminecraft.archaeo.ownership.SiteDocuments.parse(Files.readString(file(original).toPath()));Map<String,Object> future=new LinkedHashMap<>();future.put("null",null);future.put("binary",new byte[]{0,1,-1});raw.put("future",future);var workers=net.tfminecraft.archaeo.ownership.SiteDocuments.map(raw.get("workers"));net.tfminecraft.archaeo.ownership.SiteDocuments.map(workers.get(owner.toString())).put("future",future);var finds=(List<?>)raw.get("finds");var row=net.tfminecraft.archaeo.ownership.SiteDocuments.map(finds.getFirst());row.put("future",future);var interpretation=net.tfminecraft.archaeo.ownership.SiteDocuments.map(((List<?>)row.get("interpretations")).getFirst());interpretation.put("future",future);net.tfminecraft.archaeo.ownership.SiteDocuments.write(file(original).toPath(),net.tfminecraft.archaeo.ownership.SiteDocuments.text(raw));
        for(int round=0;round<2;round++){
            repository.loadAll();Site live=repository.findById(original.getId()).orElseThrow();live.setName("Changed "+round);live.worker(owner).setBlocksRemoved(4+round);repository.save(live);var saved=net.tfminecraft.archaeo.ownership.SiteDocuments.parse(Files.readString(file(original).toPath()));assertTrue(net.tfminecraft.archaeo.ownership.SiteDocuments.same(future,saved.get("future")));assertTrue(net.tfminecraft.archaeo.ownership.SiteDocuments.same(future,net.tfminecraft.archaeo.ownership.SiteDocuments.map(net.tfminecraft.archaeo.ownership.SiteDocuments.map(saved.get("workers")).get(owner.toString())).get("future")));var savedFind=net.tfminecraft.archaeo.ownership.SiteDocuments.map(((List<?>)saved.get("finds")).getFirst());assertTrue(net.tfminecraft.archaeo.ownership.SiteDocuments.same(future,savedFind.get("future")));assertTrue(net.tfminecraft.archaeo.ownership.SiteDocuments.same(future,net.tfminecraft.archaeo.ownership.SiteDocuments.map(((List<?>)savedFind.get("interpretations")).getFirst()).get("future")));
        }
    }

    @Test public void deletionAndErasureHoldExternallyChangedOrMissingDossiersWithoutRemovingTheInventory()throws Exception{
        List<Site> originals=new ArrayList<>();for(int index=0;index<3;index++){Site fresh=site(index+1,"world",index,0,SiteStatus.HIDDEN);repository.save(fresh);originals.add(fresh);}
        for(boolean erase:List.of(false,true)){
            Site site=originals.get(erase?1:0);String original=Files.readString(file(site).toPath());String changed=original+"external: retained\n";Files.writeString(file(site).toPath(),changed);assertThrows(IllegalStateException.class,()->{if(erase)repository.erase(site);else repository.delete(site);});assertEquals(changed,Files.readString(file(site).toPath()));assertTrue(repository.ownership().names().contains(site.getId()+".yml"));assertTrue(site.isOwnershipHeld());assertTrue(repository.findById(site.getId()).isPresent());
        }
        Site missing=originals.get(2);Files.delete(file(missing).toPath());assertThrows(IllegalStateException.class,()->repository.delete(missing));assertTrue(repository.ownership().held(missing.getId()+".yml"));assertTrue(repository.ownership().names().contains(missing.getId()+".yml"));assertTrue(repository.findById(missing.getId()).isPresent());
    }
}
