package net.tfminecraft.archaeo.establish;

import net.tfminecraft.archaeo.config.*;
import net.tfminecraft.archaeo.item.RecoveredFindItem;
import net.tfminecraft.archaeo.model.*;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CampBoardsTest {
    private PlayerMock director;
    private PlayerMock worker;
    private Site site;
    private CatalogRegistry catalogs;
    private RecoveredFindItem recovered;

    @Before public void setUp() {
        MockBukkit.mock(); director = MockBukkit.getMock().addPlayer("Director"); worker = MockBukkit.getMock().addPlayer("Worker");
        catalogs = mock(CatalogRegistry.class);
        when(catalogs.pick()).thenReturn(PickSettings.defaults());
        recovered = new RecoveredFindItem(MockBukkit.createMockPlugin());
        site = new Site(); site.setId(UUID.randomUUID()); site.setName("River camp");
        site.setWorldName(director.getWorld().getName()); site.setInterest(InterestLevel.LOW);
        site.establish(director.getUniqueId(), 1, 0, 20, 4, 4);
        site.getExcavators().add(worker.getUniqueId());
    }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test
    public void mainBoardShowsAccurateProgressAndOnlyAuthorizedCampActions() {
        find(FindState.RECOVERED, "I"); find(FindState.LOST, "I"); find(FindState.HIDDEN, "II");
        StratumBand band = new StratumBand(); band.setId("I"); band.setPresent(true); site.getStrata().put("I", band);
        StratumBand absent = new StratumBand(); absent.setId("IV"); site.getStrata().put("IV", absent);
        when(catalogs.stratum("I")).thenReturn(new StratumDefinition("I", 1, "Topsoil", 1, 3, true));
        site.getHintIds().add("missing-note"); site.getHintIds().add("long-note");
        when(catalogs.hint("long-note")).thenReturn(new HintTemplate("long-note", "A very long field note that should wrap at word boundaries so the whole observation remains readable.", 1,
                Set.of(), Set.of(), Set.of(), Set.of(), null, null, null, null, null));
        CampBoard board = new CampBoard(site.getId(), true, true, catalogs); board.open(director, site);
        assertSame(board, director.getOpenInventory().getTopInventory().getHolder());
        assertEquals(site.getId(), board.siteId());
        assertEquals("Rename", name(board.getInventory(), CampBoard.SLOT_RENAME));
        assertEquals("Move camp", name(board.getInventory(), CampBoard.SLOT_MOVE));
        assertEquals("Close excavation", name(board.getInventory(), CampBoard.SLOT_CLOSE));
        String info = lore(board.getInventory(), CampBoard.SLOT_INFO);
        assertTrue(info, info.contains("1 recovered · 2 total · 1 lost"));
        assertTrue(info.contains("1/3")); assertTrue(info.contains("Topsoil")); assertFalse(info.contains("IV:"));
        assertTrue(lore(board.getInventory(), CampBoard.SLOT_INFORMATION).contains("missing-note"));
        assertTrue(lore(board.getInventory(), CampBoard.SLOT_INFORMATION).contains("readable."));
        CampBoard viewer = new CampBoard(site.getId(), false, false, catalogs); viewer.open(worker, site);
        for (int slot : new int[]{CampBoard.SLOT_RENAME, CampBoard.SLOT_MOVE, CampBoard.SLOT_CLOSE, CampBoard.SLOT_WOOL_PRIMARY}) {
            assertEquals(Material.GRAY_STAINED_GLASS_PANE, viewer.getInventory().getItem(slot).getType());
        }
        assertTrue(lore(viewer.getInventory(), CampBoard.SLOT_INFO).contains("You are"));
    }

    @Test
    public void closedArchiveReplacesPrismActionWithLocationAndReportsUnfinishedCut() {
        find(FindState.HIDDEN, "I"); site.setName("Long name ".repeat(5)); site.setStatus(SiteStatus.CLOSED);
        CampBoard board = new CampBoard(site.getId(), false, false, catalogs); board.open(worker, site);
        assertEquals(32, worker.getOpenInventory().getTitle().length());
        assertEquals("Location", name(board.getInventory(), CampBoard.SLOT_LIMITS));
        assertTrue(lore(board.getInventory(), CampBoard.SLOT_INFO).contains("not finished"));
        assertFalse(lore(board.getInventory(), CampBoard.SLOT_INFO).contains("Work day:"));
        assertTrue(lore(board.getInventory(), CampBoard.SLOT_LIMITS).contains("Coordinates:"));
        site.getFinds().clear(); site.setWorldName("missing"); board.open(worker, site);
        assertTrue(lore(board.getInventory(), CampBoard.SLOT_INFO).contains("No finds were generated"));
        assertTrue(lore(board.getInventory(), CampBoard.SLOT_LIMITS).contains("unknown"));
        site.setStatus(SiteStatus.EXHAUSTED); board.open(worker, site);
        assertTrue(lore(board.getInventory(), CampBoard.SLOT_INFO).contains("field work is over"));
    }

    @Test
    public void staffRosterDeduplicatesDirectorAndReflectsCapacityAndViewerRights() {
        site.getExcavators().add(director.getUniqueId());
        CampStaffBoard board = new CampStaffBoard(site.getId(), true, 2); board.open(director, site);
        assertEquals(director.getUniqueId(), board.playerAt(0)); assertEquals(worker.getUniqueId(), board.playerAt(1));
        assertNull(board.playerAt(-1)); assertNull(board.playerAt(2)); assertNull(board.playerAt(18));
        assertTrue(board.staffFull()); assertEquals("Staff is full", name(board.getInventory(), CampStaffBoard.SLOT_ADD));
        site.getExcavators().remove(worker.getUniqueId()); board.open(director, site);
        assertFalse(board.staffFull()); assertEquals("Add worker", name(board.getInventory(), CampStaffBoard.SLOT_ADD));
        CampStaffBoard viewer = new CampStaffBoard(site.getId(), false, 2); viewer.open(worker, site);
        assertNull(viewer.getInventory().getItem(CampStaffBoard.SLOT_ADD));
        assertEquals("Director", name(viewer.getInventory(), 0));
        assertEquals("Back", name(viewer.getInventory(), CampStaffBoard.SLOT_BACK));
    }

    @Test
    public void workerPageExposesTalliesAndPreventsDirectorDismissal() {
        var record = site.worker(worker.getUniqueId());
        record.addBlocksRemoved(4); record.noteCellBrushed(); record.noteFindRecovered(); record.noteFindDamaged();
        record.setJoinedAt(Instant.now().minusSeconds(3 * 86400)); record.setLastActiveAt(Instant.now().minusSeconds(3 * 3600));
        CampWorkerBoard board = new CampWorkerBoard(site.getId(), worker.getUniqueId(), true); board.open(director, site);
        assertTrue(lore(board.getInventory(), CampWorkerBoard.SLOT_WORK).contains("4 blocks"));
        assertTrue(lore(board.getInventory(), CampWorkerBoard.SLOT_FINDS).contains("odd chip"));
        assertTrue(lore(board.getInventory(), CampWorkerBoard.SLOT_HEAD).contains("3 days ago"));
        assertEquals(Material.IRON_DOOR, board.getInventory().getItem(CampWorkerBoard.SLOT_REMOVE).getType());
        int choices = 0;
        for (int slot = 0; slot < 27; slot++) {
            SiteRole role = CampWorkerBoard.roleAt(slot);
            if (role != null) {
                choices++; assertEquals(role.displayName(), name(board.getInventory(), slot));
                assertTrue(board.getInventory().getItem(slot).getItemMeta().getEnchantmentGlintOverride());
            }
        }
        assertEquals(SiteRole.assignable().size(), choices);
        CampWorkerBoard lead = new CampWorkerBoard(site.getId(), director.getUniqueId(), true); lead.open(director, site);
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, lead.getInventory().getItem(CampWorkerBoard.SLOT_REMOVE).getType());
        assertTrue(lore(lead.getInventory(), CampWorkerBoard.SLOT_ROLE).contains("cannot be reassigned"));
        CampWorkerBoard viewer = new CampWorkerBoard(site.getId(), worker.getUniqueId(), false); viewer.open(worker, site);
        assertTrue(lore(viewer.getInventory(), CampWorkerBoard.SLOT_ROLE).contains("Only the director"));
    }

    @Test
    public void findsRegisterIncludesSettledFindsButNotBuriedOnesAndRestrictsReportIssuing() {
        BuriedFind buried = find(FindState.HIDDEN, "I");
        BuriedFind recoveredFind = find(FindState.RECOVERED, "I"); recoveredFind.setFindNumber(1);
        BuriedFind lost = find(FindState.LOST, "II"); lost.setGivenName("Broken vessel"); lost.setFindNumber(2);
        CampFindsBoard board = new CampFindsBoard(site.getId(), true, catalogs, recovered); board.open(director, site);
        assertEquals(recoveredFind.getId(), board.findAt(0)); assertEquals(lost.getId(), board.findAt(1));
        assertNull(board.findAt(-1)); assertNull(board.findAt(2)); assertNull(board.findAt(18));
        assertEquals("Broken vessel", name(board.getInventory(), 1));
        assertTrue(lore(board.getInventory(), 0).contains("#River camp-1"));
        assertNull(board.getInventory().getItem(CampFindsBoard.SLOT_REPORT));
        site.setStatus(SiteStatus.EXHAUSTED); board.open(director, site);
        assertEquals("Issue report", name(board.getInventory(), CampFindsBoard.SLOT_REPORT));
        CampFindsBoard viewer = new CampFindsBoard(site.getId(), false, catalogs, recovered); viewer.open(worker, site);
        assertNull(viewer.getInventory().getItem(CampFindsBoard.SLOT_REPORT));
        assertEquals(FindState.HIDDEN, buried.getState());
    }

    @Test
    public void woolPaletteMapsClicksToColorsAndHighlightsCurrentRoleSelection() {
        site.setCampWoolPrimary("BLUE"); site.setCampWoolSecondary("YELLOW");
        for (CampWoolRole role : CampWoolRole.values()) {
            CampWoolPicker board = new CampWoolPicker(site.getId(), role); board.open(director, site);
            DyeColor current = role == CampWoolRole.PRIMARY ? DyeColor.BLUE : DyeColor.YELLOW;
            for (int slot = 0; slot < DyeColor.values().length; slot++) {
                DyeColor color = CampWoolPicker.colorAt(slot);
                assertEquals(Material.valueOf(color.name() + "_WOOL"), board.getInventory().getItem(slot).getType());
                assertEquals(color == current, lore(board.getInventory(), slot).contains("Current colour"));
            }
            assertEquals("Back", name(board.getInventory(), CampWoolPicker.SLOT_BACK));
        }
        assertNull(CampWoolPicker.colorAt(-1)); assertNull(CampWoolPicker.colorAt(16));
    }

    @Test
    public void finishedCutsDropTheCompletionWarningsAndUnlimitedWorkDaysSaySo() {
        find(FindState.RECOVERED, "I"); find(FindState.LOST, "I");
        var d = PickSettings.defaults();
        when(catalogs.pick()).thenReturn(new PickSettings(d.enabled(), 0, d.visualCues(), d.findDust(), d.findDustIntervalTicks(),
                d.findDustCount(), d.conservation(), d.limits(), d.neighborTraces(), d.cues(), d.profiles()));
        CampBoard board = new CampBoard(site.getId(), true, true, catalogs); board.open(director, site);
        assertTrue(lore(board.getInventory(), CampBoard.SLOT_INFO).contains("Work day: unlimited"));
        assertFalse(lore(board.getInventory(), CampBoard.SLOT_CLOSE).contains("% complete"));
        site.getFinds().clear(); board.open(director, site); // A ruin generated without finds has nothing to leave behind.
        assertFalse(lore(board.getInventory(), CampBoard.SLOT_CLOSE).contains("% complete"));
        find(FindState.RECOVERED, "I"); site.setStatus(SiteStatus.CLOSED);
        CampBoard archive = new CampBoard(site.getId(), false, false, catalogs); archive.open(worker, site);
        assertTrue(lore(archive.getInventory(), CampBoard.SLOT_INFO).contains("1/1 finds settled."));
        assertFalse(lore(archive.getInventory(), CampBoard.SLOT_INFO).contains("not finished"));
    }

    @Test
    public void dossierShowsTheConfiguredInterestNameAndOmitsInterestForUnratedRuins() {
        when(catalogs.interest(InterestLevel.LOW)).thenReturn(new InterestSettings(InterestLevel.LOW, "Modest scatter", 1, 0, 1, 2, 0, 0, 1, 0, 0));
        CampBoard board = new CampBoard(site.getId(), false, false, catalogs); board.open(worker, site);
        assertTrue(lore(board.getInventory(), CampBoard.SLOT_INFORMATION).contains("Approximate interest: Modest scatter"));
        site.setInterest(null); board.open(worker, site);
        String dossier = lore(board.getInventory(), CampBoard.SLOT_INFORMATION);
        assertFalse(dossier.contains("interest")); assertTrue(dossier.contains("No field notes"));
    }

    @Test
    public void strataWithoutCatalogNamesAndUnassignedFindsAreCountedHonestly() {
        StratumBand band = new StratumBand(); band.setId("III"); band.setPresent(true); site.getStrata().put("III", band);
        StratumBand retired = new StratumBand(); retired.setId("V"); retired.setPresent(true); site.getStrata().put("V", retired);
        when(catalogs.stratum("III")).thenReturn(new StratumDefinition("III", 3, " ", 1, 3, true));
        find(FindState.HIDDEN, "III"); find(FindState.RECOVERED, "III");
        // A hand-edited dossier without `stratum:` loads as the text "null"; another names a band this site never had.
        find(FindState.RECOVERED, "null"); find(FindState.HIDDEN, "IX");
        site.getHintIds().add("blank"); when(catalogs.hint("blank")).thenReturn(new HintTemplate("blank", " ", 1,
                Set.of(), Set.of(), Set.of(), Set.of(), null, null, null, null, null));
        CampBoard board = new CampBoard(site.getId(), false, false, catalogs); board.open(worker, site);
        String info = lore(board.getInventory(), CampBoard.SLOT_INFO);
        // One row per surveyed band, named by id when the catalogue has no name (V was removed from it).
        assertEquals(info, List.of("III: 1 recovered · 2 total · 0 lost", "V: 0 recovered · 0 total · 0 lost"),
                info.lines().filter(line -> line.contains(" recovered · ")).toList());
        assertTrue(info, info.contains("Progress: █████░░░░░ 2/4")); // Stray finds still count towards the cut.
        assertFalse(info, info.contains("null")); assertFalse(info, info.contains("IX"));
        assertEquals(List.of("Field notes:"), lore(board.getInventory(), CampBoard.SLOT_INFORMATION).lines()
                .filter(line -> line.startsWith("Field") || line.startsWith("-")).toList());
    }

    @Test
    public void closedCampLocationNamesDatapackBiomesAndFallsBackWhenTheBiomeCannotBeRead() {
        site.setStatus(SiteStatus.CLOSED);
        org.mockbukkit.mockbukkit.world.WorldMock terrace = spy(new org.mockbukkit.mockbukkit.world.WorldMock());
        terrace.setName("terrace"); MockBukkit.getMock().addWorld(terrace); site.setWorldName("terrace");
        Biome custom = mock(Biome.class); when(custom.getKey()).thenReturn(new NamespacedKey("terralith", "_moonlight__grove"));
        doReturn(custom).when(terrace).getBiome(anyInt(), anyInt(), anyInt());
        CampBoard board = new CampBoard(site.getId(), false, false, catalogs); board.open(worker, site);
        assertTrue(lore(board.getInventory(), CampBoard.SLOT_LIMITS).contains("Biome: Moonlight Grove"));
        // Spigot's RegistryAware adds getKeyOrNull (paper-api 1.21.10 has no such method); an unregistered biome returns null there and getKey throws.
        SpigotBiome unregistered = mock(SpigotBiome.class); when(unregistered.getKey()).thenThrow(new IllegalStateException("unregistered"));
        doReturn(unregistered).when(terrace).getBiome(anyInt(), anyInt(), anyInt()); board.open(worker, site);
        assertTrue(lore(board.getInventory(), CampBoard.SLOT_LIMITS).contains("Biome: unknown"));
        SpigotBiome registered = mock(SpigotBiome.class); when(registered.getKeyOrNull()).thenReturn(NamespacedKey.minecraft("cherry_grove"));
        doReturn(registered).when(terrace).getBiome(anyInt(), anyInt(), anyInt()); board.open(worker, site);
        assertTrue(lore(board.getInventory(), CampBoard.SLOT_LIMITS).contains("Biome: Cherry Grove"));
        site.setWorldName(null); board.open(worker, site); // A dossier saved without its world key.
        assertTrue(lore(board.getInventory(), CampBoard.SLOT_LIMITS).contains("Biome: unknown"));
    }

    /** Shape of {@link Biome} on Spigot, where it is {@code RegistryAware}; paper-api 1.21.10 lacks {@code getKeyOrNull}. */
    public interface SpigotBiome extends Biome { NamespacedKey getKeyOrNull(); }

    @Test
    public void oversizedRostersAndRegistersFillOnlyTheListRowsAndKeepTheActionRowClickable() {
        for (int n = 0; n < 20; n++) site.getExcavators().add(UUID.randomUUID()); // A roster filed before the cap was lowered.
        CampStaffBoard staff = new CampStaffBoard(site.getId(), true, 18); staff.open(director, site);
        assertNotNull(staff.playerAt(17)); assertNull(staff.playerAt(CampStaffBoard.SLOT_ADD));
        assertEquals("Staff is full", name(staff.getInventory(), CampStaffBoard.SLOT_ADD));
        for (int n = 1; n <= 20; n++) find(FindState.RECOVERED, "I").setFindNumber(n);
        site.setStatus(SiteStatus.EXHAUSTED);
        CampFindsBoard register = new CampFindsBoard(site.getId(), true, catalogs, recovered); register.open(director, site);
        assertNotNull(register.findAt(17)); assertNull(register.findAt(CampFindsBoard.SLOT_REPORT));
        assertEquals("Issue report", name(register.getInventory(), CampFindsBoard.SLOT_REPORT));
    }

    @Test
    public void registerRowsNameTheCatalogPieceAndColourTheirProgress() {
        ArtifactTemplate pot = new ArtifactTemplate("pot", "Clay vessel", 1, 1, "ceramic", "", false, 1,
                Set.of(), Set.of(), FindProfile.OBJECT, List.of(), "");
        when(catalogs.artifact("pot")).thenReturn(pot);
        BuriedFind fresh = find(FindState.RECOVERED, "I"); fresh.setFindNumber(1);
        BuriedFind cleaned = find(FindState.RECOVERED, "I"); cleaned.setFindNumber(2); cleaned.setLabCleaned(true);
        BuriedFind drawn = find(FindState.RECOVERED, "I"); drawn.setFindNumber(3); drawn.setFieldSketch(true);
        BuriedFind studied = find(FindState.RECOVERED, "I"); studied.setFindNumber(4); studied.setStudied(true);
        BuriedFind filed = find(FindState.RECOVERED, "I"); filed.setFindNumber(5); filed.setLabCleaned(true); filed.setFieldSketch(true);
        filed.addInterpretation(new FindInterpretation("purpose", "vessel", director.getUniqueId(), Instant.now()));
        CampFindsBoard board = new CampFindsBoard(site.getId(), false, catalogs, recovered); board.open(worker, site);
        assertEquals("Clay vessel", name(board.getInventory(), 0));
        List<ChatColor> expected = List.of(ChatColor.GRAY, ChatColor.AQUA, ChatColor.AQUA, ChatColor.AQUA, ChatColor.GOLD);
        for (int slot = 0; slot < expected.size(); slot++)
            assertTrue(slot + "", board.getInventory().getItem(slot).getItemMeta().getLore().get(2).startsWith(expected.get(slot).toString()));
    }

    @Test
    public void workerFilesReadHonestlyForLegacyStaffWithoutATallyAndForEachKindOfHands() {
        UUID legacy = UUID.randomUUID(); site.getExcavators().add(legacy); // Loaded from a dossier older than staff files.
        CampWorkerBoard file = new CampWorkerBoard(site.getId(), legacy, false); file.open(director, site);
        String head = lore(file.getInventory(), CampWorkerBoard.SLOT_HEAD);
        assertTrue(head.contains("On the staff since: —")); assertTrue(head.contains("Last worked: never"));
        assertTrue(lore(file.getInventory(), CampWorkerBoard.SLOT_WORK).contains("Has not worked the cut yet."));
        assertTrue(lore(file.getInventory(), CampWorkerBoard.SLOT_FINDS).contains("No piece has passed through their hands."));
        assertNull(site.workerRecord(legacy)); // Reading the file must not create a page in the dossier.
        List<String> verdicts = List.of("Has not hurt a single piece.", "Breaks more than they bring out.", "Has taken pieces past saving.");
        for (int i = 0; i < verdicts.size(); i++) {
            UUID hand = UUID.randomUUID(); site.getExcavators().add(hand); var tally = site.worker(hand);
            if (i == 0) tally.noteFindRecovered(); else if (i == 1) tally.noteFindDamaged(); else tally.noteFindLost();
            file = new CampWorkerBoard(site.getId(), hand, true); file.open(director, site);
            assertTrue(lore(file.getInventory(), CampWorkerBoard.SLOT_FINDS).contains(verdicts.get(i)));
        }
        var record = site.worker(worker.getUniqueId()); record.setJoinedAt(null); record.noteCellBrushed();
        file = new CampWorkerBoard(site.getId(), worker.getUniqueId(), true);
        record.setLastActiveAt(Instant.now().minusSeconds(300)); file.open(director, site);
        assertFalse(lore(file.getInventory(), CampWorkerBoard.SLOT_WORK).contains("Has not worked")); // Brushing counts as work.
        head = lore(file.getInventory(), CampWorkerBoard.SLOT_HEAD);
        assertTrue(head.contains("On the staff since: —")); assertTrue(head.contains("Last worked: 5 minutes ago"));
    }

    private BuriedFind find(FindState state, String stratum) {
        BuriedFind find = new BuriedFind(); find.setId(UUID.randomUUID()); find.setArtifactId("pot"); find.setStratumId(stratum);
        find.setState(state); site.getFinds().add(find); return find;
    }
    private static String name(Inventory inventory, int slot) { return ChatColor.stripColor(inventory.getItem(slot).getItemMeta().getDisplayName()); }
    private static String lore(Inventory inventory, int slot) { return ChatColor.stripColor(String.join("\n", inventory.getItem(slot).getItemMeta().getLore())); }
}
