package net.tfminecraft.archaeo.site;

import net.tfminecraft.archaeo.config.AutoRuinSettings;
import net.tfminecraft.archaeo.config.CatalogRegistry;
import net.tfminecraft.archaeo.config.InterestSettings;
import net.tfminecraft.archaeo.model.BuriedFind;
import net.tfminecraft.archaeo.model.InterestLevel;
import net.tfminecraft.archaeo.model.Site;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RuinAutoSpawnerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private JavaPlugin plugin;
    private Server server;
    private World world;
    private CatalogRegistry catalog;
    private SiteRepository sites;
    private SiteGenerator generator;
    private AutoRuinEvaluationLedger ledger;
    private RuinAutoSpawner spawner;
    private Runnable drain, flush;
    private BukkitTask drainTask, flushTask;
    private MockedStatic<ChunkRuinFitness> fitness;
    private final List<Site> existing = new ArrayList<>();
    private final Map<String, InterestLevel> generated = new LinkedHashMap<>();
    private final Map<String, Chunk> chunks = new HashMap<>();
    private Policy policy;

    @Before public void setup() {
        plugin = mock(JavaPlugin.class); server = mock(Server.class); world = mock(World.class);
        catalog = mock(CatalogRegistry.class); sites = mock(SiteRepository.class); generator = mock(SiteGenerator.class);
        when(plugin.getDataFolder()).thenReturn(temporary.getRoot()); when(plugin.getLogger()).thenReturn(mock(Logger.class));
        when(plugin.getServer()).thenReturn(server); when(server.getWorld("world")).thenReturn(world);
        when(world.getName()).thenReturn("world"); when(world.getSeed()).thenReturn(123456789L);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getSpawnLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(world.getChunkAt(anyInt(), anyInt())).thenAnswer(call -> chunk(call.getArgument(0), call.getArgument(1)));
        when(sites.all()).thenAnswer(call -> List.copyOf(existing));
        when(sites.findByChunk(anyString(), anyInt(), anyInt())).thenAnswer(call -> existing.stream()
                .filter(s -> s.getWorldName().equals(call.getArgument(0)) && s.getChunkX() == (int) call.getArgument(1)
                        && s.getChunkZ() == (int) call.getArgument(2)).findFirst());
        when(catalog.useWorldSeed()).thenReturn(true); when(catalog.staffPermission()).thenReturn("archaeo.staff");
        when(generator.createManagedRuin(any(), any(), isNull(), isNull())).thenAnswer(call -> {
            Chunk chunk = call.getArgument(0); InterestLevel interest = call.getArgument(1);
            generated.put(chunk.getX() + "," + chunk.getZ(), interest);
            Site site = site("world", chunk.getX(), chunk.getZ()); site.setInterest(interest);
            site.getFinds().add(new BuriedFind()); return site;
        });
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);
        drainTask = mock(BukkitTask.class); flushTask = mock(BukkitTask.class);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L)))
                .thenAnswer(call -> { drain = call.getArgument(1); return drainTask; });
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(100L), eq(100L)))
                .thenAnswer(call -> { flush = call.getArgument(1); return flushTask; });
        fitness = mockStatic(ChunkRuinFitness.class);
        fitness.when(() -> ChunkRuinFitness.sample(any())).thenReturn(new ChunkRuinFitness.Sample(64, 0, 1));
        policy = new Policy(); ledger = new AutoRuinEvaluationLedger(plugin);
        spawner = new RuinAutoSpawner(plugin, catalog, sites, generator, ledger, policy.settings()); spawner.start();
    }

    @After public void teardown() { fitness.close(); }

    @Test public void rejectsIneligibleLoadsWithoutBurningFutureEvaluations() {
        policy.enabled = false; apply(); load(0, 0); drain.run();
        policy.enabled = true; policy.worlds = List.of("other"); apply(); load(0, 0);
        policy.worlds = List.of("WoRlD"); policy.maxSites = 1; apply();
        existing.add(site("world", 10, 10)); load(0, 0);
        assertTrue(spawner.statusLine().contains("queue=0"));
        assertFalse(ledger.isEvaluated("world", 0, 0));
        existing.clear(); load(0, 0); drain.run();
        assertTrue(ledger.isEvaluated("world", 0, 0));
        assertEquals(Set.of("0,0"), generated.keySet());
        load(0, 0); drain.run(); verify(generator).createManagedRuin(any(), any(), isNull(), isNull());
    }

    @Test public void capsPendingWorkDeduplicatesLoadsAndLetsOverflowRetry() {
        policy.pending = 2; apply(); load(0, 0); load(0, 0); load(1, 0); load(2, 0);
        assertTrue(spawner.statusLine().contains("pending=2/2 queue=2"));
        drain.run(); assertEquals(1, generated.size()); assertFalse(ledger.isEvaluated("world", 2, 0));
        load(2, 0); drain.run(); drain.run();
        assertEquals(Set.of("0,0", "1,0", "2,0"), generated.keySet());
        assertTrue(spawner.statusLine().contains("pending=0/2 queue=0"));
    }

    @Test public void unloadedAndRemovedWorldsAreDroppedWithoutMarkingAndCanRetry() {
        load(0, 0); when(world.isChunkLoaded(0, 0)).thenReturn(false); drain.run();
        assertFalse(ledger.isEvaluated("world", 0, 0)); assertTrue(generated.isEmpty());
        when(world.isChunkLoaded(0, 0)).thenReturn(true); load(0, 0);
        when(server.getWorld("world")).thenReturn(null); drain.run();
        assertFalse(ledger.isEvaluated("world", 0, 0));
        when(server.getWorld("world")).thenReturn(world); load(0, 0); drain.run();
        assertEquals(Set.of("0,0"), generated.keySet());
    }

    @Test public void unloadAndEvaluationBudgetsBoundEachTick() {
        policy.pending = 10; policy.unload = 1; policy.evaluations = 2; apply();
        for (int x = 0; x < 5; x++) { load(x, 0); when(world.isChunkLoaded(x, 0)).thenReturn(false); }
        drain.run(); assertTrue(spawner.statusLine().contains("queue=4"));
        assertEquals(0, ledger.evaluatedChunkCount());
        for (int x = 0; x < 5; x++) when(world.isChunkLoaded(x, 0)).thenReturn(true);
        drain.run(); assertEquals(2, generated.size());
        drain.run(); assertEquals(4, generated.size());
        load(0, 0); drain.run(); assertEquals(5, generated.size());
    }

    @Test public void siteCapReachedAfterEnqueueLeavesTheChunkRetryable() {
        policy.maxSites = 1; apply(); load(0, 0);
        existing.add(site("other", 3, 3)); existing.add(site("world", 4, 4)); drain.run();
        assertFalse(ledger.isEvaluated("world", 0, 0)); assertTrue(generated.isEmpty());
        existing.removeLast(); load(0, 0); drain.run(); assertEquals(Set.of("0,0"), generated.keySet());
    }

    @Test public void exclusionSquaresRespectTheirBoundaryAndWorld() {
        policy.spawn = 2; policy.spacing = 3; policy.evaluations = 10; apply();
        existing.add(site("world", 10, 10)); existing.add(site("other", 20, 20));
        load(1, 1); load(2, 2); load(10, 10); load(12, 12); load(13, 13); load(20, 20); drain.run();
        assertEquals(Set.of("2,2", "13,13", "20,20"), generated.keySet());
        assertTrue(ledger.isEvaluated("world", 1, 1)); assertTrue(ledger.isEvaluated("world", 12, 12));
    }

    @Test public void terrainRejectionsAreFinalWhileThresholdEqualityPasses() {
        policy.relief = 6; policy.soil = 0.35; policy.evaluations = 10; apply();
        fitness.when(() -> ChunkRuinFitness.isExcludedWaterBiome(chunk(0, 0), policy.biomes)).thenReturn(true);
        fitness.when(() -> ChunkRuinFitness.sample(chunk(1, 0))).thenReturn(new ChunkRuinFitness.Sample(64, 7, 1));
        fitness.when(() -> ChunkRuinFitness.sample(chunk(2, 0))).thenReturn(new ChunkRuinFitness.Sample(64, 6, 0.34));
        fitness.when(() -> ChunkRuinFitness.sample(chunk(3, 0))).thenReturn(new ChunkRuinFitness.Sample(64, 6, 0.35));
        for (int x = 0; x < 4; x++) load(x, 0);
        drain.run(); assertEquals(Set.of("3,0"), generated.keySet());
        for (int x = 0; x < 4; x++) assertTrue(ledger.isEvaluated("world", x, 0));
    }

    @Test public void emptyGenerationIsDeletedAndFailuresStillReleaseTheQueue() {
        Site empty = site("world", 0, 0);
        Chunk emptyChunk = chunk(0, 0), failingChunk = chunk(1, 0);
        doReturn(empty).when(generator).createManagedRuin(eq(emptyChunk), any(), isNull(), isNull());
        doThrow(new IllegalStateException("burial unavailable")).when(generator)
                .createManagedRuin(eq(failingChunk), any(), isNull(), isNull());
        load(0, 0); load(1, 0); drain.run(); drain.run();
        verify(sites).delete(empty);
        assertTrue(ledger.isEvaluated("world", 0, 0)); assertTrue(ledger.isEvaluated("world", 1, 0));
        assertTrue(spawner.statusLine().contains("queue=0"));
        verify(plugin.getLogger()).log(eq(java.util.logging.Level.WARNING), contains("Auto-ruin failed"), any(RuntimeException.class));
    }

    @Test public void lotteryAndInterestRemainIdenticalAfterAnExplicitReset() {
        policy.chance = 0.5; policy.pending = 64; policy.evaluations = 64;
        policy.weights = Map.of(InterestLevel.LOW, 5, InterestLevel.MEDIUM, 3, InterestLevel.HIGH, 2); apply();
        for (int x = 0; x < 64; x++) load(x, -x);
        drain.run(); Map<String, InterestLevel> first = new LinkedHashMap<>(generated);
        assertFalse(first.isEmpty()); assertTrue(first.size() < 64); assertEquals(64, ledger.evaluatedChunkCount());
        generated.clear(); spawner.clearEvaluated("retry with the same seed");
        for (int x = 0; x < 64; x++) load(x, -x);
        drain.run(); assertEquals(first, generated);
    }

    @Test public void zeroChanceSkipsGenerationAndInterestFallbacksStayValid() {
        policy.chance = 0; apply(); load(0, 0); drain.run();
        assertTrue(generated.isEmpty()); assertTrue(ledger.isEvaluated("world", 0, 0));
        policy.chance = 1; policy.weights = Map.of(InterestLevel.LOW, -2); apply(); load(1, 0); drain.run();
        assertEquals(InterestLevel.MEDIUM, generated.get("1,0"));
        when(catalog.useWorldSeed()).thenReturn(false);
        policy.weights = Map.of(InterestLevel.HIGH, 1); apply(); load(2, 0); drain.run();
        assertEquals(InterestLevel.HIGH, generated.get("2,0"));
        policy.weights = Map.of(InterestLevel.EXCEPTIONAL, 1); apply(); load(3, 0); drain.run();
        assertEquals(InterestLevel.EXCEPTIONAL, generated.get("3,0"));
    }

    @Test public void ordinaryReloadRetainsPendingWorkButChanceChangesClearItAndReseedSites() throws Exception {
        load(0, 0); apply(); drain.run(); assertTrue(ledger.isEvaluated("world", 0, 0));
        load(1, 0); existing.add(site("world", 8, 8)); policy.chance = 0.9; apply();
        assertFalse(ledger.isEvaluated("world", 0, 0)); assertTrue(ledger.isEvaluated("world", 8, 8));
        assertTrue(spawner.statusLine().contains("queue=0"));
        assertEquals("0.9", Files.readString(policyFile()).trim());
        spawner.forgetChunk("world", 8, 8); assertFalse(ledger.isEvaluated("world", 8, 8));
        spawner.resyncLedgerFromDisk(); assertTrue(ledger.isEvaluated("world", 8, 8));
    }

    @Test public void restartPreservesDecisionsUnlessPersistedChanceChanged() throws Exception {
        load(0, 0); drain.run(); flush.run(); spawner.stop();
        verify(drainTask).cancel(); verify(flushTask).cancel();
        spawner.start(); assertTrue(ledger.isEvaluated("world", 0, 0));
        Files.writeString(policyFile(), "0,25\n"); spawner.start();
        assertFalse(ledger.isEvaluated("world", 0, 0));
        load(1, 0); drain.run(); flush.run();
        for (String malformed : List.of("  ", "not-a-number")) {
            Files.writeString(policyFile(), malformed); spawner.start();
            assertTrue(ledger.isEvaluated("world", 1, 0));
        }
    }

    @Test public void disablingClearsQueuedWorkAndPolicyWriteFailureDoesNotCrashReload() throws Exception {
        load(0, 0); policy.enabled = false; apply(); drain.run();
        assertFalse(ledger.isEvaluated("world", 0, 0)); assertTrue(spawner.statusLine().contains("queue=0"));
        Files.delete(policyFile()); Files.delete(policyFile().getParent());
        Files.writeString(policyFile().getParent(), "blocked"); apply();
        verify(plugin.getLogger()).log(eq(java.util.logging.Level.WARNING), contains("Could not write"), any(java.io.IOException.class));
    }

    @Test public void staffNotificationRequiresPermissionAndIncludesTheRuinsTeleportCommand() {
        Player staff = mock(Player.class), visitor = mock(Player.class); Player.Spigot chat = mock(Player.Spigot.class);
        when(staff.hasPermission("archaeo.staff")).thenReturn(true); when(staff.spigot()).thenReturn(chat);
        doReturn(List.of(staff, visitor)).when(server).getOnlinePlayers();
        when(catalog.interest(InterestLevel.MEDIUM)).thenReturn(new InterestSettings(
                InterestLevel.MEDIUM, "Promising", 0, 0, 1, 1, 0, 0, 0, 0, 0));
        policy.notify = true; apply(); load(0, 0); drain.run();
        ArgumentCaptor<BaseComponent> prefix = ArgumentCaptor.forClass(BaseComponent.class);
        ArgumentCaptor<BaseComponent> link = ArgumentCaptor.forClass(BaseComponent.class);
        verify(chat).sendMessage(prefix.capture(), link.capture()); verify(visitor, never()).spigot();
        assertTrue(prefix.getValue().toPlainText().contains("Promising"));
        assertEquals(ClickEvent.Action.RUN_COMMAND, link.getValue().getClickEvent().getAction());
        assertEquals("/archaeo ruin tp #7", link.getValue().getClickEvent().getValue());
    }

    private void apply() { spawner.setSettings(policy.settings()); }
    private void load(int x, int z) { spawner.onChunkLoad(new ChunkLoadEvent(chunk(x, z), false)); }
    private Path policyFile() { return temporary.getRoot().toPath().resolve("auto-ruins/evaluation-policy.txt"); }
    private Chunk chunk(int x, int z) {
        return chunks.computeIfAbsent(x + "," + z, key -> {
            Chunk chunk = mock(Chunk.class); when(chunk.getX()).thenReturn(x); when(chunk.getZ()).thenReturn(z);
            when(chunk.getWorld()).thenReturn(world); return chunk;
        });
    }
    private static Site site(String world, int x, int z) {
        Site site = new Site(); site.setId(UUID.randomUUID()); site.setSerial(7); site.setWorldName(world);
        site.setChunkX(x); site.setChunkZ(z); site.setInterest(InterestLevel.MEDIUM); return site;
    }
    private static final class Policy {
        boolean enabled = true, notify;
        List<String> worlds = List.of(); Set<String> biomes = Set.of("ocean");
        double chance = 1, soil;
        int spacing, maxSites, spawn, relief = 6, pending = 64, unload = 64, evaluations = 1;
        Map<InterestLevel, Integer> weights = Map.of(InterestLevel.MEDIUM, 1);
        AutoRuinSettings settings() {
            return new AutoRuinSettings(enabled, worlds, chance, spacing, maxSites, spawn, relief, soil,
                    biomes, weights, pending, unload, evaluations, notify);
        }
    }
}
