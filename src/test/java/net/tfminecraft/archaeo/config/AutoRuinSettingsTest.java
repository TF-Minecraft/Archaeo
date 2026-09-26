package net.tfminecraft.archaeo.config;

import net.tfminecraft.archaeo.model.InterestLevel;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class AutoRuinSettingsTest {
    @Test
    public void packagedDefaultsLimitGenerationAndExcludeWaterBiomes() {
        AutoRuinSettings settings = AutoRuinSettings.defaults();
        assertTrue(settings.enabled());
        assertTrue(settings.worlds().isEmpty());
        assertEquals(0.004, settings.chancePerChunk(), 0);
        assertEquals(16, settings.minChunkDistance());
        assertEquals(60, settings.maxSitesPerWorld());
        assertEquals(32, settings.excludeSpawnChunks());
        assertEquals(6, settings.maxReliefBlocks());
        assertEquals(0.35, settings.minSoilFraction(), 0);
        assertEquals(4, settings.maxPending());
        assertEquals(32, settings.maxUnloadPurgePerTick());
        assertEquals(1, settings.maxEvaluationsPerTick());
        assertTrue(settings.notifyStaff());
        assertEquals(Map.of(InterestLevel.LOW, 50, InterestLevel.MEDIUM, 35,
                InterestLevel.HIGH, 12, InterestLevel.EXCEPTIONAL, 3), settings.interestWeights());
        assertEquals(Set.of("ocean", "deep_ocean", "warm_ocean", "lukewarm_ocean",
                "deep_lukewarm_ocean", "cold_ocean", "deep_cold_ocean", "frozen_ocean",
                "deep_frozen_ocean", "deep_warm_ocean", "river", "frozen_river"),
                settings.excludedBiomes());
        assertThrows(UnsupportedOperationException.class, () -> settings.worlds().add("world"));
        assertThrows(UnsupportedOperationException.class, () -> settings.excludedBiomes().add("plains"));
        assertThrows(UnsupportedOperationException.class,
                () -> settings.interestWeights().put(InterestLevel.LOW, 1));
    }

    @Test
    public void biomeNamesNormalizeNamespacesCaseAndOuterWhitespace() {
        assertNull(AutoRuinSettings.normalizeBiomeId(null));
        assertNull(AutoRuinSettings.normalizeBiomeId(" \t\n"));
        assertNull(AutoRuinSettings.normalizeBiomeId("minecraft:"));
        assertNull(AutoRuinSettings.normalizeBiomeId(":"));
        assertEquals("ocean", AutoRuinSettings.normalizeBiomeId("  OCEAN  "));
        assertEquals("deep_ocean", AutoRuinSettings.normalizeBiomeId(" MINECRAFT:DEEP_OCEAN "));
        assertEquals("ocean", AutoRuinSettings.normalizeBiomeId("custom:ocean"));
    }

    @Test
    public void missingListsUseFallbackWhileExplicitListsReplaceIt() {
        Set<String> fallback = Set.of("river");
        assertSame(fallback, AutoRuinSettings.normalizeBiomeList(null, fallback));
        assertTrue(AutoRuinSettings.normalizeBiomeList(List.of(), fallback).isEmpty());
        Set<String> normalized = AutoRuinSettings.normalizeBiomeList(
                Arrays.asList(null, " ", "minecraft:", " OCEAN ", "minecraft:ocean", "DEEP_OCEAN"), fallback);
        assertEquals(Set.of("ocean", "deep_ocean"), normalized);
        assertThrows(UnsupportedOperationException.class, () -> normalized.add("river"));
    }
}
