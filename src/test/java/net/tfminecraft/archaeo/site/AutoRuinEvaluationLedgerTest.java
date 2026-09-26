package net.tfminecraft.archaeo.site;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.logging.Logger;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AutoRuinEvaluationLedgerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private JavaPlugin plugin;
    private AutoRuinEvaluationLedger ledger;
    private Path root;

    @Before public void setup() {
        plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(temporary.getRoot());
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        root = temporary.getRoot().toPath().resolve("auto-ruins/evaluated");
        ledger = new AutoRuinEvaluationLedger(plugin);
    }

    @Test public void remembersRegionBoundariesNegativeCoordinatesAndWorldIsolation() throws Exception {
        ledger.load();
        assertEquals(0, ledger.loadedRegionCount());
        assertFalse(ledger.isEvaluated("world", 0, 0));
        int[][] points = {{0, 0}, {31, 31}, {32, 32}, {-1, -1}, {-32, -32}, {-33, -33},
                {Integer.MIN_VALUE, Integer.MAX_VALUE}};
        for (int[] point : points) {
            ledger.markEvaluated("world", point[0], point[1]);
            ledger.markEvaluated("world", point[0], point[1]);
            assertTrue(ledger.isEvaluated("world", point[0], point[1]));
        }
        assertFalse(ledger.isEvaluated("world", 1, 0));
        assertFalse(ledger.isEvaluated("world", 64, 64));
        assertFalse(ledger.isEvaluated("other", 0, 0));
        assertEquals(7, ledger.evaluatedChunkCount());
        assertEquals(5, ledger.loadedRegionCount());
        ledger.flush();
        assertEquals(128, Files.size(root.resolve("world/r.0.0.bin")));
        assertEquals(128, Files.size(root.resolve("world/r.1.1.bin")));
        AutoRuinEvaluationLedger restored = new AutoRuinEvaluationLedger(plugin);
        restored.load();
        assertEquals(7, restored.evaluatedChunkCount());
        for (int[] point : points) assertTrue(restored.isEvaluated("world", point[0], point[1]));
        restored.flush();
    }

    @Test public void forgettingIsIdempotentAndPersistsEmptyRegion() {
        ledger.forgetEvaluated("world", 0, 0);
        ledger.markEvaluated("world", 0, 0);
        ledger.forgetEvaluated("world", 1, 0);
        ledger.forgetEvaluated("world", 32, 0);
        ledger.forgetEvaluated("world", 0, 0);
        ledger.forgetEvaluated("world", 0, 0);
        assertFalse(ledger.isEvaluated("world", 0, 0));
        assertEquals(0, ledger.evaluatedChunkCount());
        ledger.flush();
        ledger.load();
        assertEquals(1, ledger.loadedRegionCount());
        assertEquals(0, ledger.evaluatedChunkCount());
    }

    @Test public void forgettingLoadedDecisionPersistsWithoutChangingNeighborsOrOtherWorlds() throws Exception {
        ledger.markEvaluated("world", 31, 31);
        ledger.markEvaluated("world", 30, 31);
        ledger.markEvaluated("world", 32, 31);
        ledger.markEvaluated("other", 31, 31);
        ledger.flush();
        byte[] neighboringRegion = Files.readAllBytes(root.resolve("world/r.1.0.bin"));
        byte[] otherWorld = Files.readAllBytes(root.resolve("other/r.0.0.bin"));

        AutoRuinEvaluationLedger loaded = new AutoRuinEvaluationLedger(plugin);
        loaded.load();
        assertTrue(loaded.isEvaluated("world", 31, 31));
        loaded.forgetEvaluated("world", 31, 31);
        loaded.forgetEvaluated("world", 31, 31);
        assertFalse(loaded.isEvaluated("world", 31, 31));
        assertTrue(loaded.isEvaluated("world", 30, 31));
        assertTrue(loaded.isEvaluated("world", 32, 31));
        assertTrue(loaded.isEvaluated("other", 31, 31));
        loaded.flush();

        AutoRuinEvaluationLedger restarted = new AutoRuinEvaluationLedger(plugin);
        restarted.load();
        assertFalse(restarted.isEvaluated("world", 31, 31));
        assertTrue(restarted.isEvaluated("world", 30, 31));
        assertTrue(restarted.isEvaluated("world", 32, 31));
        assertTrue(restarted.isEvaluated("other", 31, 31));
        assertEquals(128, Files.size(root.resolve("world/r.0.0.bin")));
        assertArrayEquals(neighboringRegion, Files.readAllBytes(root.resolve("world/r.1.0.bin")));
        assertArrayEquals(otherWorld, Files.readAllBytes(root.resolve("other/r.0.0.bin")));
    }

    @Test public void sanitizesWorldFolderNamesAndUsesDefaultForEmptyName() throws Exception {
        ledger.markEvaluated("a-b_c.d/ e", 0, 0);
        ledger.markEvaluated("", 0, 0);
        ledger.flush();
        assertTrue(Files.exists(root.resolve("a-b_c.d__e/r.0.0.bin")));
        assertTrue(Files.exists(root.resolve("world/r.0.0.bin")));
        ledger.load();
        assertTrue(ledger.isEvaluated("a-b_c.d/ e", 0, 0));
        assertTrue(ledger.isEvaluated("", 0, 0));
    }

    @Test public void clearAllRemovesNestedFilesAndAllowsFreshEvaluation() throws Exception {
        ledger.clearAll();
        ledger.markEvaluated("world", 0, 0);
        ledger.flush();
        Path extra = root.resolve("world/nested/deeper");
        Files.createDirectories(extra);
        Files.writeString(extra.resolve("old.txt"), "old");
        ledger.clearAll();
        assertEquals(0, ledger.loadedRegionCount());
        assertEquals(0, ledger.evaluatedChunkCount());
        assertTrue(Files.isDirectory(root));
        try (var children = Files.list(root)) { assertEquals(0, children.count()); }
        ledger.markEvaluated("world", 1, 1);
        ledger.flush();
        ledger.load();
        assertTrue(ledger.isEvaluated("world", 1, 1));
        assertFalse(ledger.isEvaluated("world", 0, 0));
    }

    @Test public void externalFolderDeletionClearsMemoryWithoutResurrectingFiles() throws Exception {
        ledger.markEvaluated("world", 0, 0);
        ledger.flush();
        ledger.markEvaluated("world", 1, 0);
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
        ledger.flush();
        assertFalse(Files.exists(root));
        assertEquals(0, ledger.evaluatedChunkCount());
        ledger.markEvaluated("world", 2, 0);
        ledger.flush();
        ledger.load();
        assertFalse(ledger.isEvaluated("world", 0, 0));
        assertTrue(ledger.isEvaluated("world", 2, 0));
    }

    @Test public void loadsValidAndBlankFilesWhileSkippingMalformedNamesAndUnreadableFiles() throws Exception {
        Path world = root.resolve("world");
        Files.createDirectories(world);
        Files.writeString(root.resolve("not-a-world.txt"), "ignored");
        Files.write(world.resolve("r.0.0.bin"), new byte[]{1});
        Files.write(world.resolve("r.1.1.bin"), new byte[0]);
        for (String name : new String[]{"other.bin", "r.2.bin", "r..2.bin", "r.bad.2.bin", "r.0.2.txt"})
            Files.write(world.resolve(name), new byte[]{1});
        Files.createDirectory(world.resolve("r.3.3.bin"));
        ledger.markEvaluated("stale", 9, 9);
        ledger.load();
        assertEquals(2, ledger.loadedRegionCount());
        assertEquals(1, ledger.evaluatedChunkCount());
        assertTrue(ledger.isEvaluated("world", 0, 0));
        assertFalse(ledger.isEvaluated("world", 32, 32));
        assertFalse(ledger.isEvaluated("stale", 9, 9));
    }

    @Test public void failedWritesPreserveInMemoryDecisions() throws Exception {
        Files.writeString(temporary.getRoot().toPath().resolve("auto-ruins"), "blocking file");
        ledger.markEvaluated("world", 0, 0);
        ledger.flush();
        assertTrue(ledger.isEvaluated("world", 0, 0));
        assertFalse(Files.exists(root));
        verify(plugin.getLogger()).log(eq(java.util.logging.Level.WARNING), contains("Could not write"),
                any(java.io.IOException.class));
    }

    @Test public void listingFailuresAreLoggedWithoutEscapingLoadOrReset() throws Exception {
        Path world = root.resolve("world"); Files.createDirectories(world);
        try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.list(root)).thenThrow(new java.io.IOException("unreadable root"));
            ledger.load(); ledger.clearAll();
        }
        assertEquals(0, ledger.evaluatedChunkCount());
        try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.list(world)).thenThrow(new java.io.IOException("unreadable world"));
            ledger.load(); ledger.clearAll();
        }
        assertEquals(0, ledger.loadedRegionCount());
        verify(plugin.getLogger()).log(eq(java.util.logging.Level.WARNING),
                eq("Could not list auto-ruin evaluation ledger"), any(java.io.IOException.class));
        verify(plugin.getLogger()).log(eq(java.util.logging.Level.WARNING),
                eq("Could not clear auto-ruin evaluation ledger"), any(java.io.IOException.class));
        verify(plugin.getLogger()).log(eq(java.util.logging.Level.WARNING),
                eq("Could not list " + world), any(java.io.IOException.class));
        verify(plugin.getLogger()).log(eq(java.util.logging.Level.WARNING),
                eq("Could not delete " + world), any(java.io.IOException.class));
    }
}
