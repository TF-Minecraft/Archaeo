package net.tfminecraft.archaeo.site;

import net.tfminecraft.archaeo.config.*;
import net.tfminecraft.archaeo.item.ItemRef;
import net.tfminecraft.archaeo.model.*;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SiteGeneratorTest {
    private ServerMock server;
    private WorldMock world;
    private CatalogRegistry catalog;
    private SiteRepository repository;
    private SiteGenerator generator;
    private InterestSettings budget;
    private ArtifactTemplate artifact;

    @Before public void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        catalog = mock(CatalogRegistry.class);
        repository = new SiteRepository(MockBukkit.createMockPlugin());
        repository.loadAll();
        generator = new SiteGenerator(catalog, repository);
        budget = new InterestSettings(InterestLevel.LOW, "Low", 10, 0, 3, 3, 1, 1, 2, 0, 0);
        artifact = new ArtifactTemplate("pot", "Pot", 2, 4, "ceramic", "common", true, 1,
                Set.of("I"), Set.of("ceramic"), FindProfile.OBJECT, List.of(ItemRef.vanilla(Material.BRICK)), "notes");
        when(catalog.interest(any())).thenReturn(budget);
        when(catalog.strataInOrder()).thenReturn(List.of(new StratumDefinition("I", 1, "Top", 1, 3, true)));
        when(catalog.stratum("I")).thenReturn(new StratumDefinition("I", 1, "Top", 1, 3, true));
        when(catalog.pick()).thenReturn(PickSettings.defaults());
        when(catalog.materialSurvival(anyString())).thenReturn(1.0);
        when(catalog.artifacts()).thenReturn(Map.of("pot", artifact));
        when(catalog.artifact("pot")).thenReturn(artifact);
        when(catalog.hints()).thenReturn(List.of());
        when(catalog.maxShapeAttempts()).thenReturn(8);
        when(catalog.findMinCover()).thenReturn(1);
        when(catalog.useWorldSeed()).thenReturn(true);
    }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test
    public void createsPersistentHiddenSitesWithBuriedNonoverlappingConnectedFinds() {
        UUID author = UUID.randomUUID();
        Site site = generator.createManagedRuin(world.getChunkAt(0, 0), InterestLevel.LOW, "  Old pottery  ", author);
        assertEquals("Old pottery", site.getName());
        assertEquals(author, site.getCreatedBy());
        assertEquals(SiteStatus.HIDDEN, site.getStatus());
        assertEquals(SiteType.MANAGED_RUIN, site.getType());
        assertSame(site, repository.findById(site.getId()).orElseThrow());
        assertEquals(3, site.getFinds().size());
        Set<BlockCell> occupied = new HashSet<>();
        for (BuriedFind find : site.getFinds()) {
            assertEquals("pot", find.getArtifactId());
            assertEquals("BRICK", find.getItem());
            assertEquals(FindState.HIDDEN, find.getState());
            assertTrue(find.getConservation() >= 1 && find.getConservation() <= 100);
            assertTrue(find.getCells().size() >= 2 && find.getCells().size() <= 4);
            Set<BlockCell> reached = new HashSet<>();
            reached.add(find.getCells().getFirst());
            while (true) {
                int before = reached.size();
                for (BlockCell cell : find.getCells()) {
                    if (reached.stream().anyMatch(other -> adjacent(cell, other))) reached.add(cell);
                }
                if (before == reached.size()) break;
            }
            assertEquals(new HashSet<>(find.getCells()), reached);
            for (BlockCell cell : find.getCells()) {
                assertTrue(occupied.add(cell));
                assertTrue(site.isInPrism(cell.x(), cell.y(), cell.z()));
                assertFalse(world.getBlockAt(cell.x(), cell.y(), cell.z()).getType().isAir());
                assertFalse(world.getBlockAt(cell.x(), cell.y() + 1, cell.z()).getType().isAir());
            }
        }
        assertThrows(IllegalStateException.class, () -> generator.createManagedRuin(world.getChunkAt(0, 0), InterestLevel.HIGH, null, null));
    }

    @Test
    public void validatesInputsAndPreservesIdentityWhenRebuildingUntouchedRuins() {
        assertThrows(IllegalArgumentException.class, () -> generator.createManagedRuin(null, InterestLevel.LOW, null, null));
        assertThrows(IllegalArgumentException.class, () -> generator.createManagedRuin(world.getChunkAt(0, 0), null, null, null));
        assertThrows(IllegalArgumentException.class, () -> generator.regenerateInterest(null, InterestLevel.LOW));
        Site site = generator.createManagedRuin(world.getChunkAt(0, 0), InterestLevel.LOW, "", null);
        assertTrue(site.getName().startsWith("Site in "));
        UUID id = site.getId();
        int serial = site.getSerial();
        String name = site.getName();
        assertThrows(IllegalArgumentException.class, () -> generator.regenerateInterest(site, null));
        assertThrows(IllegalStateException.class, () -> generator.regenerateInterest(site, InterestLevel.LOW));
        assertSame(site, generator.regenerateInterest(site, InterestLevel.HIGH));
        assertEquals(id, site.getId());
        assertEquals(serial, site.getSerial());
        assertEquals(name, site.getName());
        assertEquals(InterestLevel.HIGH, site.getInterest());
        site.setStatus(SiteStatus.ESTABLISHED);
        assertThrows(IllegalStateException.class, () -> generator.regenerateInterest(site, InterestLevel.LOW));
        site.setStatus(SiteStatus.HIDDEN);
        site.getProspectConfirmed().add(UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> generator.regenerateInterest(site, InterestLevel.LOW));
        site.getProspectConfirmed().clear();
        site.setWorldName("missing");
        assertThrows(IllegalStateException.class, () -> generator.regenerateInterest(site, InterestLevel.LOW));
        site.setWorldName(world.getName());
        BuriedFind find = site.getFinds().getFirst();
        BlockCell cell = find.getCells().getFirst();
        world.getBlockAt(cell.x(), cell.y(), cell.z()).setType(Material.AIR);
        assertThrows(IllegalStateException.class, () -> generator.regenerateInterest(site, InterestLevel.LOW));
        find.woundDirect(cell);
        assertThrows(IllegalStateException.class, () -> generator.regenerateInterest(site, InterestLevel.LOW));
    }

    @Test
    public void missingCatalogBudgetFailsAndEmptyOrIncompatibleTerrainProducesNoFinds() {
        when(catalog.interest(InterestLevel.EXCEPTIONAL)).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> generator.createManagedRuin(world.getChunkAt(0, 0), InterestLevel.EXCEPTIONAL, null, null));
        when(catalog.strataInOrder()).thenReturn(List.of(new StratumDefinition("I", 1, "Sky", -20, -10, true)));
        assertTrue(generator.createManagedRuin(world.getChunkAt(0, 0), InterestLevel.LOW, null, null).getFinds().isEmpty());
        when(catalog.strataInOrder()).thenReturn(List.of(new StratumDefinition("I", 1, "Top", 1, 3, true)));
        Site site = siteWithBand();
        assertFalse(generator.placeFinds(site, budget, new Random(1), world).isEmpty());
        when(catalog.artifacts()).thenReturn(Map.of());
        assertTrue(generator.placeFinds(site, budget, new Random(1), world).isEmpty());
        when(catalog.artifacts()).thenReturn(Map.of("pot", artifact));
        site.getStrata().clear();
        assertTrue(generator.placeFinds(site, budget, new Random(1), world).isEmpty());
    }

    @Test
    public void strataRespectOptionalLayerChanceAndDisturbance() {
        when(catalog.strataInOrder()).thenReturn(List.of(
                new StratumDefinition("I", 1, "Top", 1, 3, true),
                new StratumDefinition("IV", 4, "Deep", 10, 14, false),
                new StratumDefinition("V", 5, "Unused", 15, 20, false)));
        Site site = new Site();
        site.setSurfaceY(64);
        generator.assignStrata(site, budget, new Random(2));
        assertTrue(site.getStrata().get("I").isPresent());
        assertFalse(site.getStrata().get("IV").isPresent());
        assertFalse(site.getStrata().get("V").isPresent());
        assertFalse(site.getStrata().get("I").isDisturbed());
        InterestSettings always = new InterestSettings(InterestLevel.HIGH, "High", 10, 0, 1, 1, 0, 0, 1, 1, 1);
        generator.assignStrata(site, always, new Random(2));
        assertTrue(site.getStrata().get("IV").isPresent());
        assertTrue(site.getStrata().get("IV").isDisturbed());
        assertEquals(50, site.getStrata().get("IV").getMinY());
        assertEquals(54, site.getStrata().get("IV").getMaxY());
    }

    @Test
    public void staffFindsValidatePlacementAndEstablishASandboxWithoutReplacingTerrain() {
        UUID director = UUID.randomUUID();
        var origin = world.getBlockAt(3, 8, 3);
        origin.setType(Material.STONE);
        assertThrows(IllegalArgumentException.class, () -> generator.spawnStaffFind(null, director, artifact, 2));
        assertThrows(IllegalArgumentException.class, () -> generator.spawnStaffFind(origin, null, artifact, 2));
        assertThrows(IllegalArgumentException.class, () -> generator.spawnStaffFind(origin, director, null, 2));
        assertThrows(IllegalArgumentException.class, () -> generator.spawnStaffFind(world.getBlockAt(3, 100, 3), director, artifact, 2));
        BuriedFind find = generator.spawnStaffFind(origin, director, artifact, 100);
        Site site = repository.findByChunk("world", 0, 0).orElseThrow();
        assertEquals("Staff sandbox", site.getName());
        assertEquals(SiteStatus.ESTABLISHED, site.getStatus());
        assertTrue(site.isDirector(director));
        assertTrue(find.getCells().contains(new BlockCell(3, 8, 3)));
        assertTrue(find.getCells().size() <= 4);
        assertEquals(Material.STONE, origin.getType());
        assertThrows(IllegalStateException.class, () -> generator.spawnStaffFind(origin, director, artifact, 2));
        var next = world.getBlockAt(12, 8, 12);
        next.setType(Material.STONE);
        assertTrue(site.getFinds().contains(generator.spawnStaffFind(next, director, artifact, 2)));
        site.getStrata().clear();
        var high = world.getBlockAt(8, 50, 8);
        high.setType(Material.STONE);
        assertEquals(1, generator.spawnStaffFind(high, director, artifact, 2).getCells().size());
        assertTrue(site.getStrata().get("I").isPresent());
    }

    @Test
    public void fieldNotesDescribeOnlyEvidenceActuallyPresentInTheDossier() {
        Site site = siteWithBand();
        when(catalog.hints()).thenReturn(List.of(
                new HintTemplate("plain", "Ordinary ground", 1, Set.of(), Set.of(), Set.of(), Set.of(), null, null, 10, null, null),
                new HintTemplate("wealth", "Rich deposits", 1, Set.of(), Set.of(), Set.of(), Set.of(), null, 20, null, null, null),
                new HintTemplate("deep", "Deep sequence", 1, Set.of(), Set.of(), Set.of(), Set.of(), null, null, null, 2, null),
                new HintTemplate("disturbed", "Disturbed ground", 1, Set.of(), Set.of(), Set.of(), Set.of(), null, null, null, null, true),
                new HintTemplate("missing", "No deep layer", 1, Set.of(), Set.of(), Set.of(), Set.of(), "IV", null, null, null, null),
                new HintTemplate("iv", "Deep layer", 1, Set.of(), Set.of(), Set.of(), Set.of("IV"), null, null, null, null, null),
                new HintTemplate("pottery", "Ceramic traces", 1, Set.of(), Set.of("ceramic", "glass"), Set.of("vessel"), Set.of("I"), null, null, null, null, null)
        ));
        InterestSettings generousNotes = new InterestSettings(InterestLevel.LOW, "Low", 10, 0, 1, 1, 0, 0, 10, 0, 0);
        assertEquals(Set.of("plain", "missing", "pottery"), new HashSet<>(generator.pickHints(site, generousNotes, Set.of("ceramic", "vessel"), new Random(4))));
        assertEquals(Set.of("plain", "missing"), new HashSet<>(generator.pickHints(site, generousNotes, Set.of("ceramic"), new Random(4))));
        assertEquals(Set.of("plain", "missing"), new HashSet<>(generator.pickHints(site, generousNotes, Set.of("vessel"), new Random(4))));
        StratumBand deep = new StratumBand(); deep.setId("IV"); deep.setPresent(true); deep.setDisturbed(true); site.getStrata().put("IV", deep);
        InterestSettings rich = new InterestSettings(InterestLevel.HIGH, "High", 30, 0, 1, 1, 0, 0, 10, 1, 1);
        List<String> notes = generator.pickHints(site, rich, Set.of("ceramic", "vessel"), new Random(4));
        assertEquals(Set.of("wealth", "deep", "disturbed", "iv", "pottery"), new HashSet<>(notes));
        assertEquals(notes.size(), new HashSet<>(notes).size());
    }

    @Test
    public void exhaustedBurialPocketStopsWithoutOverlappingOrInventingRelics() {
        Site site = siteWithBand(); StratumBand band = site.getStrata().get("I"); band.setMinY(1); band.setMaxY(1);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) world.getBlockAt(x, 1, z).setType(Material.AIR);
        world.getBlockAt(2, 1, 2).setType(Material.STONE);
        ArtifactTemplate tiny = new ArtifactTemplate("bead", "Bead", 1, 1, "ceramic", "", false, 1,
                Set.of("I"), Set.of(), FindProfile.OBJECT, List.of(), "");
        when(catalog.artifacts()).thenReturn(Map.of("bead", tiny));
        List<BuriedFind> finds = generator.placeFinds(site, budget, new Random(4), world);
        assertEquals(1, finds.size()); assertEquals("bead", finds.getFirst().getArtifactId());
        assertEquals(List.of(new BlockCell(2, 1, 2)), finds.getFirst().getCells());
        assertEquals(Material.STONE, world.getBlockAt(2, 1, 2).getType());
    }

    @Test
    public void materialDecayAndDisturbanceReduceBuriedConditionBeforeDigging() {
        Site site = siteWithBand();
        List<BuriedFind> intact = generator.placeFinds(site, budget, new Random(12), world);
        when(catalog.materialSurvival("ceramic")).thenReturn(.5);
        site.getStrata().get("I").setDisturbed(true);
        List<BuriedFind> decayed = generator.placeFinds(site, budget, new Random(12), world);
        assertEquals(intact.size(), decayed.size()); assertFalse(intact.isEmpty());
        for (int i = 0; i < intact.size(); i++) {
            assertEquals(intact.get(i).getCells(), decayed.get(i).getCells());
            assertTrue(decayed.get(i).getBuriedConservation() < intact.get(i).getBuriedConservation());
            assertFalse(decayed.get(i).isFieldDamaged());
        }
    }

    @Test
    public void pocketTooBrokenForHalfAFindGivesUpAfterConfiguredAttemptsInsteadOfPlacingCrumbs() {
        Site site = siteWithBand(); StratumBand band = site.getStrata().get("I"); band.setMinY(1); band.setMaxY(1);
        // Checkerboard of buried cells: every cell touches its neighbours only at the corners.
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) world.getBlockAt(x, 1, z).setType((x + z) % 2 == 0 ? Material.STONE : Material.AIR);
        ArtifactTemplate urn = new ArtifactTemplate("urn", "Urn", 4, 4, "ceramic", "", false, 1,
                Set.of("I"), Set.of(), FindProfile.OBJECT, List.of(), "");
        when(catalog.artifacts()).thenReturn(Map.of("urn", urn));
        assertTrue(generator.placeFinds(site, budget, new Random(5), world).isEmpty());
        ArtifactTemplate bead = new ArtifactTemplate("bead", "Bead", 1, 1, "ceramic", "", false, 1,
                Set.of("I"), Set.of(), FindProfile.OBJECT, List.of(), "");
        when(catalog.artifacts()).thenReturn(Map.of("bead", bead));
        List<BuriedFind> beads = generator.placeFinds(site, budget, new Random(5), world);
        assertEquals(3, beads.size());
        for (BuriedFind find : beads) { BlockCell cell = find.getCells().getFirst(); assertEquals(0, (cell.x() + cell.z()) % 2); }
        // generation.max-shape-attempts: 0 allows no attempt, so even a bead that fits any cell is never placed.
        when(catalog.maxShapeAttempts()).thenReturn(0);
        assertTrue(generator.placeFinds(site, budget, new Random(5), world).isEmpty());
    }

    @Test
    public void configuredLowBiasLeansBuriedConditionTowardRuinWithoutChangingTheShapes() {
        Site site = siteWithBand();
        List<BuriedFind> even = generator.placeFinds(site, budget, new Random(21), world);
        ConservationSettings base = ConservationSettings.defaults();
        PickSettings pick = PickSettings.defaults();
        when(catalog.pick()).thenReturn(new PickSettings(pick.enabled(), pick.jornadaActions(), pick.visualCues(), pick.findDust(),
                pick.findDustIntervalTicks(), pick.findDustCount(),
                new ConservationSettings(base.buriedMin(), base.buriedMax(), 3.0, base.depthPenalty(), base.disturbedPenalty(), base.grades()),
                pick.limits(), pick.neighborTraces(), pick.cues(), pick.profiles()));
        List<BuriedFind> leaned = generator.placeFinds(site, budget, new Random(21), world);
        assertEquals(even.size(), leaned.size()); assertFalse(even.isEmpty());
        for (int i = 0; i < even.size(); i++) {
            assertEquals(even.get(i).getCells(), leaned.get(i).getCells());
            assertTrue(leaned.get(i).getBuriedConservation() < even.get(i).getBuriedConservation());
            assertTrue(leaned.get(i).getBuriedConservation() >= base.buriedMin());
        }
    }

    @Test
    public void staffFindsWidenTheNearestPresentStratumAndNeverReviveAnAbsentOne() {
        Site site = new Site(); site.setId(UUID.randomUUID()); site.setSerial(repository.nextSerial()); site.setWorldName("world");
        site.setStatus(SiteStatus.HIDDEN); site.setInterest(InterestLevel.LOW); site.setCreatedAt(java.time.Instant.now());
        site.getStrata().put("I", band("I", true, 20, 24));
        site.getStrata().put("II", band("II", true, 10, 14));
        site.getStrata().put("IV", band("IV", false, 0, 0));
        repository.save(site);
        UUID director = UUID.randomUUID();
        world.getBlockAt(3, 30, 3).setType(Material.STONE);
        generator.spawnStaffFind(world.getBlockAt(3, 30, 3), director, artifact, 2);
        assertEquals(List.of(20, 34), range(site.getStrata().get("I")));
        assertEquals(List.of(10, 14), range(site.getStrata().get("II")));
        world.getBlockAt(5, 6, 5).setType(Material.STONE);
        BuriedFind low = generator.spawnStaffFind(world.getBlockAt(5, 6, 5), director, artifact, 2);
        assertEquals("II", low.getStratumId());
        assertEquals(List.of(2, 14), range(site.getStrata().get("II")));
        assertEquals(List.of(20, 34), range(site.getStrata().get("I")));
        assertFalse(site.getStrata().get("IV").isPresent());
        assertEquals(2, repository.findById(site.getId()).orElseThrow().getFinds().size());
    }

    @Test
    public void staffSandboxStillWorksWhenStrataConfigMakesEveryLayerOptional() {
        when(catalog.strataInOrder()).thenReturn(List.of(new StratumDefinition("I", 1, "Top", 1, 3, false)));
        UUID director = UUID.randomUUID();
        world.getBlockAt(3, 8, 3).setType(Material.STONE);
        BuriedFind revived = generator.spawnStaffFind(world.getBlockAt(3, 8, 3), director, artifact, 2);
        Site first = repository.findByChunk("world", 0, 0).orElseThrow();
        assertEquals("I", revived.getStratumId());
        assertTrue(first.getStrata().get("I").isPresent());
        assertEquals(List.of(4, 12), range(first.getStrata().get("I")));
        // A strata.yml that names its layers differently has no "I" at all: the sandbox adds one.
        when(catalog.strataInOrder()).thenReturn(List.of(new StratumDefinition("A", 1, "Top", 1, 3, false)));
        when(catalog.stratum("I")).thenReturn(null);
        world.getBlockAt(20, 8, 3).setType(Material.STONE);
        BuriedFind added = generator.spawnStaffFind(world.getBlockAt(20, 8, 3), director, artifact, 2);
        Site second = repository.findByChunk("world", 1, 0).orElseThrow();
        assertEquals("I", added.getStratumId());
        assertFalse(second.getStrata().get("A").isPresent());
        assertEquals(List.of(4, 12), range(second.getStrata().get("I")));
        assertTrue(added.getBuriedConservation() >= 1 && added.getBuriedConservation() <= 100);
        assertEquals(SiteStatus.ESTABLISHED, second.getStatus());
    }

    @Test
    public void staffFindBesideAnotherGrowsAwayFromItInsteadOfSharingCells() {
        UUID director = UUID.randomUUID();
        for (int x = 3; x <= 6; x++) world.getBlockAt(x, 8, 3).setType(Material.STONE);
        BuriedFind first = generator.spawnStaffFind(world.getBlockAt(3, 8, 3), director, artifact, 2);
        assertEquals(Set.of(new BlockCell(3, 8, 3), new BlockCell(4, 8, 3)), new HashSet<>(first.getCells()));
        BuriedFind second = generator.spawnStaffFind(world.getBlockAt(5, 8, 3), director, artifact, 2);
        assertEquals(Set.of(new BlockCell(5, 8, 3), new BlockCell(6, 8, 3)), new HashSet<>(second.getCells()));
    }

    @Test
    public void unnamedRuinsAreNamedFromTheBiomeThroughSpigotRegistryAwareAndFallBackWhenItCannotBeRead() {
        // Spigot's RegistryAware adds getKeyOrNull and deprecates getKey; paper-api 1.21.10 has neither change.
        Biome spigot = mock(Biome.class, withSettings().extraInterfaces(RegistryAwareBiome.class));
        when(((RegistryAwareBiome) spigot).getKeyOrNull()).thenReturn(NamespacedKey.minecraft("cherry_grove"));
        when(spigot.getKey()).thenThrow(new IllegalStateException("deprecated key read"));
        world.setBiome(8, world.getSeaLevel(), 8, spigot);
        assertEquals("Site in cherry grove", generator.createManagedRuin(world.getChunkAt(0, 0), InterestLevel.LOW, null, null).getName());
        Biome unregistered = mock(Biome.class, withSettings().extraInterfaces(RegistryAwareBiome.class));
        when(unregistered.getKey()).thenThrow(new IllegalStateException("unregistered biome"));
        world.setBiome(24, world.getSeaLevel(), 8, unregistered);
        Site unknown = generator.createManagedRuin(world.getChunkAt(1, 0), InterestLevel.LOW, " ", null);
        assertEquals("Site in unknown", unknown.getName());
        assertSame(unknown, repository.findByChunk("world", 1, 0).orElseThrow());
        assertEquals("Site in plains", generator.createManagedRuin(world.getChunkAt(2, 0), InterestLevel.LOW, null, null).getName());
        // On Paper there is no getKeyOrNull, and a keyless legacy biome throws from getKey.
        Biome legacy = mock(Biome.class);
        when(legacy.getKey()).thenThrow(new UnsupportedOperationException("Cannot get key of this biome"));
        world.setBiome(56, world.getSeaLevel(), 8, legacy);
        assertEquals("Site in unknown", generator.createManagedRuin(world.getChunkAt(3, 0), InterestLevel.LOW, null, null).getName());
    }

    @Test
    public void worldSeededLayoutsRepeatPerChunkAndUnseededLayoutsVary() {
        List<List<BlockCell>> seeded = new ArrayList<>();
        List<List<BlockCell>> unseeded = new ArrayList<>();
        for (int i = 0; i < 2; i++) seeded.add(layoutAt00());
        assertEquals(seeded.get(0), seeded.get(1));
        when(catalog.useWorldSeed()).thenReturn(false);
        for (int i = 0; i < 6; i++) unseeded.add(layoutAt00());
        assertTrue(unseeded.stream().anyMatch(layout -> !layout.equals(seeded.getFirst())));
    }

    /** Stand-in for Spigot's {@code RegistryAware} key accessor, which paper-api 1.21.10 does not have. */
    public interface RegistryAwareBiome { NamespacedKey getKeyOrNull(); }

    private List<BlockCell> layoutAt00() {
        Site site = generator.createManagedRuin(world.getChunkAt(0, 0), InterestLevel.LOW, "Seed", null);
        List<BlockCell> cells = site.getFinds().stream().flatMap(find -> find.getCells().stream()).toList();
        repository.erase(site);
        return cells;
    }

    private static StratumBand band(String id, boolean present, int min, int max) {
        StratumBand band = new StratumBand(); band.setId(id); band.setPresent(present); band.setMinY(min); band.setMaxY(max);
        return band;
    }

    private static List<Integer> range(StratumBand band) { return List.of(band.getMinY(), band.getMaxY()); }

    private Site siteWithBand() {
        Site site = new Site();
        site.setSurfaceY(4);
        generator.assignStrata(site, budget, new Random(1));
        return site;
    }

    private static boolean adjacent(BlockCell a, BlockCell b) {
        return a.y() == b.y() && Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z()) == 1;
    }
}
