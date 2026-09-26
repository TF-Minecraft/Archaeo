package net.tfminecraft.archaeo.config;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import net.tfminecraft.archaeo.model.BuriedFind;
import net.tfminecraft.archaeo.model.FindInterpretation;
import net.tfminecraft.archaeo.model.InterestLevel;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.mockbukkit.mockbukkit.MockBukkit;

public class CatalogRegistryTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private JavaPlugin plugin;
    private File folder;
    private CatalogRegistry catalog;

    @Before public void setUp() throws Exception {
        MockBukkit.mock();
        plugin = mock(JavaPlugin.class);
        folder = new File(temporary.getRoot(), "catalog");
        when(plugin.getDataFolder()).thenReturn(folder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("CatalogRegistryTest"));
        when(plugin.getConfig()).thenAnswer(call -> YamlConfiguration.loadConfiguration(new File(folder, "config.yml")));
        doAnswer(call -> {
            String resource = call.getArgument(0);
            try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(resource, stream);
                Files.copy(stream, new File(folder, resource).toPath());
            }
            return null;
        }).when(plugin).saveResource(anyString(), eq(false));
        catalog = new CatalogRegistry(plugin);
    }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test public void packagedCatalogLoadsEveryFeatureAndPreservesOperatorEditsOnReload() throws Exception {
        catalog.load();
        for (InterestLevel level : InterestLevel.values()) assertEquals(level, catalog.interest(level).level());
        assertFalse(catalog.strataInOrder().isEmpty());
        int order = Integer.MIN_VALUE;
        for (StratumDefinition stratum : catalog.strataInOrder()) {
            assertTrue(stratum.order() >= order);
            order = stratum.order();
            assertSame(stratum, catalog.stratum(stratum.id()));
        }
        assertNull(catalog.stratum("missing"));
        assertFalse(catalog.artifacts().isEmpty());
        for (ArtifactTemplate artifact : catalog.artifacts().values()) {
            assertSame(artifact, catalog.artifact(artifact.id()));
            assertEquals(artifact.profile(), catalog.profileOf(artifact.id()));
            assertNotNull(catalog.resolveRarityId(artifact));
            assertNotNull(catalog.rarityLoreLine(artifact));
        }
        assertThrows(UnsupportedOperationException.class, () -> catalog.artifacts().clear());
        assertEquals(FindProfile.OBJECT, catalog.profileOf("missing"));
        assertFalse(catalog.hints().isEmpty());
        for (HintTemplate hint : catalog.hints()) assertSame(hint, catalog.hint(hint.id()));
        for (InterpretationTemplate interpretation : catalog.interpretations())
            assertSame(interpretation, catalog.interpretation(interpretation.id()));
        for (String missing : new String[] {null, "", " ", "missing"}) {
            assertNull(catalog.hint(missing));
            assertNull(catalog.interpretation(missing));
            assertNull(catalog.interpretationType(missing));
        }
        assertNotNull(catalog.tracker()); assertNotNull(catalog.prospect());
        assertNotNull(catalog.establish()); assertNotNull(catalog.pick());
        assertNotNull(catalog.recovery()); assertNotNull(catalog.sketch());
        assertNotNull(catalog.museum()); assertNotNull(catalog.autoRuins());
        assertNotNull(catalog.toolWear()); assertNotNull(catalog.items());
        assertTrue(catalog.maxShapeAttempts() > 0); assertTrue(catalog.findMinCover() > 0);
        assertTrue(catalog.useWorldSeed());
        write("config.yml", "permissions:\n  staff: ' custom.staff '\n");
        catalog.load();
        assertEquals("custom.staff", catalog.staffPermission());
        verify(plugin, times(1)).saveResource("config.yml", false);
    }

    @Test public void missingRequiredCatalogSectionsFailWithActionablePaths() throws Exception {
        catalog.load();
        String[][] failures = {{"interest.yml", "interest-levels"}, {"strata.yml", "strata"}, {"artifacts.yml", "artifacts"}, {"hints.yml", "hints"}};
        for (String[] failure : failures) {
            File file = new File(folder, failure[0]);
            String original = Files.readString(file.toPath());
            write(failure[0], "{}");
            IllegalStateException error = assertThrows(IllegalStateException.class, catalog::load);
            assertTrue(error.getMessage(), error.getMessage().contains(failure[1]));
            write(failure[0], original);
        }
        write("interest.yml", "interest-levels:\n  low: {}\n");
        assertTrue(assertThrows(IllegalStateException.class, catalog::load).getMessage().contains("medium"));
    }

    @Test public void emptyFeatureConfigUsesSafeDefaultsAndLegacyInterestFallback() throws Exception {
        catalog.load();
        String interest = Files.readString(new File(folder, "interest.yml").toPath());
        write("config.yml", interest + "\npermissions:\n  staff: ' '\n");
        write("interest.yml", "{}");
        catalog.load();
        assertEquals("archaeo.admin", catalog.staffPermission());
        assertEquals(TrackerSettings.defaults(), catalog.tracker());
        assertEquals(ProspectSettings.defaults(), catalog.prospect());
        assertEquals(EstablishSettings.defaults(), catalog.establish());
        assertEquals(RecoverySettings.defaults(), catalog.recovery());
        assertEquals(ToolWearSettings.defaults(), catalog.toolWear());
        assertEquals(SketchSettings.defaults(), catalog.sketch());
        assertEquals(MuseumSettings.defaults(), catalog.museum());
        assertEquals(AutoRuinSettings.defaults(), catalog.autoRuins());
        write("config.yml", interest.replaceAll("(?s)generation:.*", ""));
        catalog.load();
        assertNotNull(catalog.interest(InterestLevel.EXCEPTIONAL));
    }

    @Test public void rarityBandsAreSortedAliasesNormalizeAndInvalidOverridesFallback() throws Exception {
        catalog.load();
        assertEquals("common", catalog.resolveRarityId((ArtifactTemplate) null));
        assertEquals("legendary", catalog.resolveRarityId(null, -1));
        assertEquals("epic", catalog.resolveRarityId(" ", 5));
        assertEquals("rare", catalog.resolveRarityId(null, 8));
        assertEquals("common", catalog.resolveRarityId(null, 10000));
        assertEquals("rare", catalog.resolveRarityId(" UNCOMMON ", 1));
        assertEquals("custom", catalog.resolveRarityId(" CUSTOM ", 1));
        assertEquals(catalog.rarityLoreLine("common"), catalog.rarityLoreLine((String) null));
        assertEquals(catalog.rarityLoreLine("common"), catalog.rarityLoreLine(" "));
        assertEquals(ArtifactRarity.COMMON.loreLine(), catalog.rarityLoreLine("not-a-tier"));
        write("config.yml", """
                rarity:
                  custom: {label: 'Special', color: GOLD}
                  rare: BLUE
                  epic: {label: ' ', color: invalid}
                  common: {color: ' '}
                  ' ': RED
                rarity-from-weight:
                  common: 100
                  custom: 2
                  invalid: 0
                  ' ': 3
                """);
        catalog.load();
        assertEquals("custom", catalog.resolveRarityId(null, 2));
        assertEquals("common", catalog.resolveRarityId(null, 3));
        assertTrue(catalog.rarityLoreLine("custom").contains(ChatColor.GOLD + "Special"));
        assertTrue(catalog.rarityLoreLine("rare").contains(ChatColor.BLUE.toString()));
        write("config.yml", "rarity-from-weight: {bad: -1}\n");
        catalog.load();
        assertEquals("legendary", catalog.resolveRarityId(null, 1));
        write("config.yml", "rarity-from-weight: {}\n");
        catalog.load();
        assertEquals("legendary", catalog.resolveRarityId(null, 1));
    }

    @Test public void customCatalogRowsHandleScalarGarbageItemPoolsAndMaterialFallbacks() throws Exception {
        catalog.load();
        write("strata.yml", "strata:\n  ignored: scalar\n  deep: {order: 2, depth-min: 5, depth-max: 9}\n  top: {order: 1}\n");
        write("artifacts.yml", """
                artifacts:
                  ignored: scalar
                  fallback: {weight: -5, item: [invalid]}
                  single: {item: STONE, profile: animal}
                  pool: {item: [BRICK, STONE]}
                """);
        write("hints.yml", """
                hints:
                  ignored: scalar
                  basic: {weight: -3}
                  bounded: {min-wealth: 2, max-wealth: 8, min-strata: 1, require-disturbed: false}
                """);
        write("materials.yml", """
                materials:
                  ignored: scalar
                  metal: {display-name: Iron, survival: 4}
                  organic: {survival: -1}
                  ceramic: {}
                  stone: {stains: [custom], clean-glass: BLUE_STAINED_GLASS_PANE}
                  extra: {}
                """);
        catalog.load();
        assertEquals(List.of("top", "deep"), catalog.strataInOrder().stream().map(StratumDefinition::id).toList());
        assertNull(catalog.artifact("ignored"));
        assertEquals(1, catalog.artifact("fallback").weight());
        assertEquals("BRICK", catalog.artifact("fallback").items().get(0).primary());
        assertEquals(2, catalog.artifact("pool").items().size());
        assertEquals(1, catalog.hint("basic").weight());
        assertEquals("Iron", catalog.materialDisplayName("metal"));
        assertEquals("Unknown-id", catalog.materialDisplayName("unknown-id"));
        for (String id : new String[] {null, "", " "}) {
            assertEquals("unknown", catalog.materialDisplayName(id));
            assertEquals(1, catalog.materialSurvival(id), 0);
            assertEquals("unknown", catalog.materialOf(id).id());
        }
        assertEquals(1, catalog.materialSurvival("missing"), 0);
        assertEquals(1, catalog.materialSurvival("metal"), 0);
        assertEquals(.05, catalog.materialSurvival("organic"), 0);
        assertEquals(List.of("custom"), catalog.materialOf("stone").stains());
        assertEquals(Material.BLUE_STAINED_GLASS_PANE, catalog.materialOf("stone").cleanGlass());
        write("materials.yml", "{}");
        catalog.load();
        for (String id : new String[] {"metal", "organic", "ceramic", "stone", "other"}) {
            assertNotNull(catalog.materialOf(id).cleanGlass());
            assertFalse(catalog.materialOf(id).stains().isEmpty());
        }
    }

    @Test public void stationDrawsAreStableUniqueAndIncludeAnArtifactSpecificSuggestion() throws Exception {
        catalog.load();
        write("interpretations.yml", """
                profiles:
                  object: {types: [missing, function]}
                  animal: {types: [missing]}
                  individual: {types: []}
                types:
                  ignored: scalar
                  empty: {}
                  invalid: {options: {ignored: scalar}}
                  function:
                    options:
                      suggested: {suggested-for: [pot], suggested-by: [domestic]}
                      a: {profiles: [object, invalid]}
                      b: {}
                      c: {}
                      d: {}
                      animal-only: {profiles: [animal]}
                  size:
                    options:
                      small: {}
                      large: {}
                """);
        write("artifacts.yml", "artifacts:\n  pot: {}\n  bone: {profile: animal}\n");
        catalog.load();
        assertEquals(2, catalog.interpretationTypes().size());
        assertEquals(1, catalog.interpretationTypes(null).size());
        assertEquals(catalog.interpretationTypes(), catalog.interpretationTypes(FindProfile.ANIMAL));
        assertTrue(catalog.stationOffers("missing", null, null, null).isEmpty());
        assertEquals(2, catalog.stationOffers("size", null, null, null).size());
        for (int i = 0; i < 32; i++) {
            UUID id = new UUID(i, i * 719L);
            var offers = catalog.stationOffers("function", id, "pot", Set.of("domestic"));
            assertEquals(3, offers.size());
            assertEquals(3, new HashSet<>(offers).size());
            assertTrue(offers.stream().anyMatch(option -> option.id().equals("suggested")));
            assertFalse(offers.stream().anyMatch(option -> option.id().equals("animal-only")));
            assertEquals(offers, catalog.stationOffers("function", id, "pot", Set.of("domestic")));
        }
        assertEquals(3, catalog.stationOffers("function", null, "unknown", null).size());
        assertNull(catalog.nextOpenType(null));
        BuriedFind find = new BuriedFind();
        find.setArtifactId("pot");
        assertEquals("function", catalog.nextOpenType(find).id());
        find.addInterpretation(new FindInterpretation("function", "a", UUID.randomUUID(), Instant.EPOCH));
        assertNull(catalog.nextOpenType(find));
    }

    @Test public void legacyInterpretationsAreOneQuestionAndReloadClearsOldRows() throws Exception {
        catalog.load();
        write("interpretations.yml", "interpretations:\n  invalid: scalar\n  legacy: {display-name: Old}\n");
        catalog.load();
        assertEquals(1, catalog.interpretationTypes().size());
        assertEquals("function", catalog.interpretationTypes().get(0).id());
        assertEquals("Old", catalog.interpretation("legacy").displayName());
        assertEquals(catalog.interpretationTypes(), catalog.interpretationTypes(null));
        write("interpretations.yml", "interpretations: {invalid: scalar}\n");
        catalog.load();
        assertTrue(catalog.interpretations().isEmpty());
        assertTrue(catalog.interpretationTypes().isEmpty());
        write("interpretations.yml", "{}");
        catalog.load();
        assertTrue(catalog.interpretations().isEmpty());
    }

    @Test public void featureBoundsClampInvalidOperatorValues() throws Exception {
        catalog.load();
        write("config.yml", """
                permissions: {staff: ' '}
                tracker:
                  max-range: 0
                  medium-range: 400
                  close-range: 500
                  wave-radii: [1.0, 2.0, 3.0, 4.0]
                  beep-max-ticks: 0
                  beep-min-ticks: 0
                  wave-bias-blocks: -2
                  target-switch-margin: -1
                prospect: {points-required: 0, use-ticks: 0, min-sample-distance: 0}
                establish: {max-staff: -2, max-excavations: -3}
                excavation:
                  workday-actions: -1
                  tool-wear: {pick: -1, brush: -1, unbreaking: false}
                  limits: {seconds: 0, thickness: 4, view-distance: 2}
                  conservation:
                    buried: {min: 150, max: -4, bias: -2, depth-penalty: -3, disturbed-penalty: -4}
                    grades:
                      - ignored
                      - {label: missing}
                      - {id: numeric, min-percent: 40, label: Named}
                      - {id: string, min-percent: '90'}
                      - {id: invalid, min-percent: bad}
                      - {id: absent}
                  brush: {hold-ticks: 0, max-cells-to-clean: 0, enabled: false}
                sketch: {pencil-uses: -2, lab: {dirty-count: 99}}
                museum: {displays: [LECTERN]}
                auto-ruins:
                  chance-per-chunk: '0,25'
                  min-soil-fraction: 4
                  min-chunk-distance: -2
                  max-sites-per-world: -2
                  exclude-spawn-chunks: -2
                  max-relief-blocks: -2
                  max-pending: 0
                  max-unload-purge-per-tick: 0
                  max-evaluations-per-tick: 0
                  excluded-biomes: [minecraft:ocean]
                  interest-weights: {low: -2}
                """);
        write("interest.yml", Files.readString(new File(folder, "interest.yml").toPath()) + "\ngeneration: {max-shape-attempts: 7, use-world-seed: false, find-min-cover: 0}\n");
        catalog.load();
        assertEquals(7, catalog.maxShapeAttempts()); assertEquals(1, catalog.findMinCover()); assertFalse(catalog.useWorldSeed());
        assertEquals(1, catalog.tracker().defaultMaxRange()); assertEquals(1, catalog.tracker().nearRange());
        assertEquals(1, catalog.tracker().detectMessageRange());
        assertEquals(List.of(1.0, 2.0, 3.0), catalog.tracker().waveRadii());
        assertEquals(0, catalog.tracker().waveBiasBlocks(), 0);
        assertEquals(1, catalog.prospect().pointsRequired());
        assertEquals(0, catalog.pick().jornadaActions());
        assertEquals(0, catalog.toolWear().pick()); assertEquals(0, catalog.toolWear().brush());
        assertEquals(1, catalog.pick().limits().seconds()); assertEquals(1, catalog.pick().limits().thickness(), 0);
        assertEquals(0, catalog.pick().conservation().buriedMin()); assertEquals(100, catalog.pick().conservation().buriedMax());
        assertEquals("string", catalog.pick().conservation().grades().get(0).id());
        assertEquals(1, catalog.recovery().channelTicks()); assertFalse(catalog.recovery().enabled());
        assertEquals(0, catalog.sketch().pencilUses()); assertEquals(18, catalog.sketch().lab().dirtyCount());
        assertTrue(catalog.museum().allowsVanilla(Material.LECTERN)); assertFalse(catalog.museum().allowsVanilla(Material.ARMOR_STAND));
        assertEquals(.25, catalog.autoRuins().chancePerChunk(), 0); assertEquals(1, catalog.autoRuins().minSoilFraction(), 0);
        assertEquals(Integer.valueOf(0), catalog.autoRuins().interestWeights().get(InterestLevel.LOW));
        assertEquals(1, catalog.autoRuins().maxPending());
    }

    @Test public void legacyItemsToolAliasesTempoAndLabProfilesLoadWithoutDiscardingDefaults() throws Exception {
        catalog.load();
        write("config.yml", """
                tracker: {item: ' ', wave-radii: [1.0]}
                prospect: {item: {ignored: section}}
                recovery: {item: BRUSH, channel-ticks: 9}
                pick: {enabled: false, jornada-actions: 13, visual-cues: false, find-dust: false}
                excavation:
                  cues:
                    pick-faster: [STONE, STICK, invalid, ' ', '#', '#bad key', '#unknown_tag', '#minecraft:mineable/pickaxe']
                    shovel-faster: [DIRT]
                    matched-factor: -2
                    mismatched-factor: 0
                  conservation: {grades: [ignored, {label: missing}]}
                  tools:
                    invalid: {items: [unknown]}
                    custom:
                      materials: [WOODEN_PICKAXE]
                      tempo: ' 7 '
                      dig-class: pick
                      blocks-on-time: 3
                      blocks-on-late: 1
                      late-shape: down
                      jornada-cost: 0
                      ready-window-ticks: 0
                      mining-speed: ' '
                      mining-speed-multiplier: 2.5
                      legacy-chime-ticks: -1
                    legacy: {items: [GOLDEN_PICKAXE], cue-ticks: 6, mining-speed: {ignore: section}}
                sketch:
                  lab:
                    tools:
                      ignored: scalar
                      water: {item: WATER_BUCKET}
                      air: {item: FEATHER}
                      brush: {item: BRUSH}
                      other: {item: STICK}
                    stains:
                      ignored: scalar
                      custom: {glass: BLUE_STAINED_GLASS_PANE, tool: other}
                museum: {enabled: true}
                """);
        write("items.yml", """
                discovery: {tracker: CLOCK, prospect: COMPASS}
                excavation:
                  pick: STICK
                  give: STICK
                  brush: BRUSH
                  old-list: [STONE_PICKAXE]
                  old-nested: {materials: [IRON_SHOVEL]}
                  bad: scalar
                """);
        catalog.load();
        assertEquals("CLOCK", catalog.items().tracker().primary());
        assertEquals("COMPASS", catalog.items().prospect().primary());
        assertEquals(9, catalog.recovery().channelTicks());
        assertFalse(catalog.pick().enabled());
        assertEquals(13, catalog.pick().jornadaActions());
        assertFalse(catalog.pick().visualCues());
        assertEquals(TrackerSettings.defaults().waveRadii(), catalog.tracker().waveRadii());
        assertEquals(CueSettings.defaults().matchedFactor(), catalog.pick().cues().matchedFactor(), 0);
        assertEquals(CueSettings.defaults().mismatchedFactor(), catalog.pick().cues().mismatchedFactor(), 0);
        assertTrue(catalog.pick().cues().pickFaster().contains(Material.STONE));
        assertEquals(Set.of(Material.DIRT), catalog.pick().cues().shovelFaster());
        ExcavationTool custom = tool("custom");
        assertEquals(7, custom.cueTicks()); assertEquals(DigClass.PICK, custom.digClass());
        assertEquals(3, custom.cellsOnTime()); assertEquals(3, custom.cellsOnLate());
        assertEquals(1, custom.jornadaCost()); assertEquals(1, custom.readyWindowTicks());
        assertNull(custom.miningSpeed()); assertEquals(Float.valueOf(2.5f), custom.miningSpeedMultiplier());
        assertEquals(6, tool("legacy").cueTicks());
        assertNotNull(tool("old-list")); assertNotNull(tool("old-nested"));
        assertEquals(4, catalog.sketch().lab().tools().size());
        assertEquals(1, catalog.sketch().lab().stains().size());
        assertEquals(Material.BLUE_STAINED_GLASS_PANE, catalog.sketch().lab().stains().get(0).glass());
        assertEquals(MuseumSettings.defaults(), catalog.museum());
    }

    @Test public void tempoTokensAndUnitIntervalParsingAcceptLegacyInputsAndRejectGarbage() throws Exception {
        catalog.load();
        String[] tokens = {"12", "-4", "'vanilla'", "'default'", "'auto'", "' '", "'bogus'", "'9'"};
        int[] expected = {12, 0, 0, 0, 0, 0, 0, 9};
        for (int i = 0; i < tokens.length; i++) {
            write("config.yml", "excavation:\n  tools:\n    light: {tempo: " + tokens[i] + "}\n");
            catalog.load();
            assertEquals(tokens[i], expected[i], tool("light").cueTicks());
        }
        for (String token : new String[] {"'invalid'", "' '", "[]", "{}"}) {
            write("config.yml", "auto-ruins: {chance-per-chunk: " + token + ", min-soil-fraction: " + token + "}\n");
            catalog.load();
            assertEquals(AutoRuinSettings.defaults().chancePerChunk(), catalog.autoRuins().chancePerChunk(), 0);
            assertEquals(AutoRuinSettings.defaults().minSoilFraction(), catalog.autoRuins().minSoilFraction(), 0);
        }
        write("config.yml", "auto-ruins: {chance-per-chunk: -1, min-soil-fraction: '0,5'}\nsketch: {lab: {tools: {bad: scalar}, stains: {bad: scalar}}}\n");
        catalog.load();
        assertEquals(0, catalog.autoRuins().chancePerChunk(), 0);
        assertEquals(.5, catalog.autoRuins().minSoilFraction(), 0);
        assertEquals(LabSettings.defaults(), catalog.sketch().lab());
        write("config.yml", "excavation: {cues: {}, conservation: {grades: []}}\nsketch: {pencil-uses: 3}\n");
        catalog.load();
        assertTrue(catalog.pick().cues().pickFaster().isEmpty());
        assertEquals(LabSettings.defaults(), catalog.sketch().lab());
    }

    @Test public void typedQuestionsWithoutProfilesRemainAvailableToEveryClassification() throws Exception {
        catalog.load();
        write("interpretations.yml", "types: {function: {options: {reading: {profiles: [animal]}}}}\n");
        catalog.load();
        assertEquals(catalog.interpretationTypes(), catalog.interpretationTypes(FindProfile.INDIVIDUAL));
        assertTrue(catalog.stationOffers("function", UUID.randomUUID(), null, Set.of()).isEmpty());
        write("interpretations.yml", "profiles: {object: {types: []}}\ntypes: {function: {options: {reading: {}}}}\n");
        catalog.load();
        assertEquals(catalog.interpretationTypes(), catalog.interpretationTypes(FindProfile.OBJECT));
        assertEquals(catalog.interpretationTypes(), catalog.interpretationTypes(FindProfile.INDIVIDUAL));
        assertEquals(1, catalog.stationOffers("function", null, null, null).size());
    }

    @Test public void partialOperatorSectionsKeepPackagedValuesForTheKeysTheyOmit() throws Exception {
        catalog.load();
        write("config.yml", """
                excavation:
                  conservation: {buried: {min: 50}}
                  tools:
                    light: {break-shape: {down: true}}
                auto-ruins: {enabled: false}
                rarity:
                  legendary: {label: Mythic}
                sketch:
                  lab:
                    stains:
                      grime: {display-name: '', tool: ''}
                """);
        catalog.load();
        assertEquals(50, catalog.pick().conservation().buriedMin());
        assertEquals(ConservationSettings.defaults().grades(), catalog.pick().conservation().grades());
        assertEquals(ExcavationTool.light().breakShape(), tool("light").breakShape());
        assertFalse(catalog.autoRuins().enabled());
        assertEquals(AutoRuinSettings.defaults().chancePerChunk(), catalog.autoRuins().chancePerChunk(), 0);
        assertEquals(ChatColor.GRAY + "Rarity: " + ArtifactRarity.LEGENDARY.color() + "Mythic", catalog.rarityLoreLine("legendary"));
        LabStain grime = catalog.sketch().lab().stains().get(0);
        assertEquals("grime", grime.label());
        assertTrue(grime.allowsTool("water")); assertTrue(grime.allowsTool(null));
    }

    @Test public void unwritableDataFolderIsLoggedAndTheLoadFailsOnTheMissingCatalogue() throws Exception {
        File blocker = temporary.newFile("plugins");
        folder = new File(blocker, "Archaeo");
        Logger logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger); when(plugin.getDataFolder()).thenReturn(folder);
        doNothing().when(plugin).saveResource(anyString(), eq(false));
        IllegalStateException error = assertThrows(IllegalStateException.class, catalog::load);
        assertTrue(error.getMessage(), error.getMessage().contains("interest-levels"));
        verify(logger, atLeastOnce()).warning("Could not create " + folder.getPath());
    }

    @Test public void failedReloadKeepsEveryRequiredCatalogueThatWasAlreadyLoaded() throws Exception {
        catalog.load();
        Map<String, ArtifactTemplate> artifacts = Map.copyOf(catalog.artifacts());
        int hints = catalog.hints().size(), strata = catalog.strataInOrder().size();
        // The operator edits artifacts.yml and breaks hints.yml in the same /archaeo reload.
        YamlConfiguration edited = YamlConfiguration.loadConfiguration(new File(folder, "artifacts.yml"));
        edited.set("artifacts.coin.display-name", "Recut coin");
        edited.set("artifacts.amphora.display-name", "Amphora");
        edited.save(new File(folder, "artifacts.yml"));
        write("hints.yml", "{}");
        assertThrows(IllegalStateException.class, catalog::load);
        assertNull(catalog.artifact("amphora")); assertEquals("Coin", catalog.artifact("coin").displayName());
        assertEquals(artifacts, catalog.artifacts());
        assertEquals(hints, catalog.hints().size()); assertEquals(strata, catalog.strataInOrder().size());
        for (InterestLevel level : InterestLevel.values()) assertNotNull(catalog.interest(level));
    }

    private ExcavationTool tool(String id) {
        return catalog.pick().profiles().stream().filter(tool -> tool.id().equals(id)).findFirst().orElseThrow();
    }

    private void write(String name, String contents) throws Exception {
        Files.createDirectories(folder.toPath());
        Files.writeString(new File(folder, name).toPath(), contents);
    }
}
