package net.tfminecraft.archaeo.command;

import net.tfminecraft.archaeo.ArcheologyPlugin;
import net.tfminecraft.archaeo.model.Site;
import net.tfminecraft.archaeo.model.StratumBand;
import net.tfminecraft.archaeo.model.BuriedFind;
import net.tfminecraft.archaeo.model.FindState;
import net.tfminecraft.archaeo.model.BlockCell;
import org.bukkit.entity.Player;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.tfminecraft.archaeo.model.SiteStatus;
import org.bukkit.Material;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ArchaeoCommandTest {
    private ServerMock server;
    private ArcheologyPlugin plugin;
    private PlayerMock staff;
    private ArchaeoCommand commands;

    @Before public void setUp() {
        server = MockBukkit.mock();
        var world = new org.mockbukkit.mockbukkit.world.WorldMock() {
            @Override public org.bukkit.Chunk[] getLoadedChunks() {
                // MockBukkit has no tile-entity lookup; this command fixture contains no block inventories.
                return java.util.Arrays.stream(super.getLoadedChunks()).map(chunk -> {
                    org.bukkit.Chunk snapshot = spy(chunk);
                    doReturn(new org.bukkit.block.BlockState[0]).when(snapshot).getTileEntities();
                    return snapshot;
                }).toArray(org.bukkit.Chunk[]::new);
            }
        };
        world.setName("world");
        server.addWorld(world);
        plugin = MockBukkit.load(ArcheologyPlugin.class);
        staff = player("Archaeologist");
        staff.setOp(true);
        staff.addAttachment(plugin, plugin.catalogs().staffPermission(), true);
        commands = (ArchaeoCommand) plugin.getCommand("archaeo").getExecutor();
    }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test
    public void deniesNonStaffBeforeDispatchAndCompletion() {
        staff.setOp(false);
        staff.addAttachment(plugin, plugin.catalogs().staffPermission(), false);
        assertContains(run("give tracker"), "You do not have permission");
        assertTrue(staff.getInventory().isEmpty());
        assertTrue(complete("give", "").isEmpty());
    }

    @Test
    public void givesConfiguredRoleItemsAndNamedToolsToSelfOrOnlinePlayers() {
        for (String role : List.of("tracker", "prospect", "establish", "brush", "paper", "pencil", "tool", "pick")) {
            staff.getInventory().clear();
            assertContains(run("give " + role), "Gave ");
            assertFalse(staff.getInventory().isEmpty());
        }
        staff.getInventory().clear();
        assertContains(run("give tool IRON_PICKAXE"), "iron pickaxe");
        assertTrue(staff.getInventory().contains(Material.IRON_PICKAXE));
        PlayerMock recipient = player("Recipient");
        assertContains(run("give tracker Recipient"), "to Recipient");
        assertFalse(recipient.getInventory().isEmpty());
        assertTrue(recipient.nextMessage().contains("received an archaeological tracker"));
        assertContains(run("give brush Missing"), "Player not online");
        assertContains(run("give tool nonsense"), "Unknown excavation tool");
        assertContains(run("give nonsense"), "Unknown item");
        assertContains(run("give"), "Usage:");
    }

    @Test
    public void handlesIncompleteCommandsAndInvalidFindArgumentsWithoutMutatingSites() {
        for (String input : List.of("", "nonsense", "ruin", "ruin nonsense", "workday", "pick nonsense", "find", "find nonsense",
                "ruin create", "ruin set-interest", "ruin auto", "ruin auto nonsense", "ruin tp")) {
            assertFalse(input, run(input).isEmpty());
        }
        assertContains(run("find spawn nonexistent"), "Unknown artifact");
        String artifact = plugin.catalogs().artifacts().keySet().iterator().next();
        assertContains(run("find spawn " + artifact + " many"), "Size must be a whole number");
        assertContains(run("ruin create nonsense"), "Unknown interest");
        assertContains(run("ruin set-interest nonsense"), "Unknown interest");
        assertTrue(plugin.sites().all().isEmpty());
    }

    @Test
    public void queriesMissingSitesAndRequiresConfirmationBeforeDeletion() {
        assertContains(run("ruin info"), "No site in this chunk");
        assertContains(run("ruin info #123"), "No site with serial");
        assertContains(run("ruin info Lost city"), "No site named");
        assertContains(run("ruin info #999999999999999999999"), "No site named");
        assertContains(run("ruin close"), "No site in this chunk");
        assertContains(run("ruin delete"), "No site in this chunk");
        Site site = dossier(1, "Old camp");
        assertContains(run("ruin info #1"), "Old camp");
        assertContains(run("ruin info Old camp"), "Old camp");
        assertContains(run("ruin close #1"), "no standing camp");
        assertContains(run("ruin delete #1"), "Add confirm");
        assertTrue(plugin.sites().findBySerial(1).isPresent());
        assertContains(run("ruin delete #1 confirm"), "Deleted");
        assertFalse(plugin.sites().findById(site.getId()).isPresent());
        dossier(2, "Duplicate"); dossier(3, "Duplicate");
        assertContains(run("ruin info Duplicate"), "Several sites share that name");
    }

    @Test
    public void statisticsIncludeSavedWorldsAndTeleportUsesStoredDatum() {
        Site site = dossier(1, "Old camp");
        site.setSurfaceY(40);
        assertContains(run("ruin stats"), "Ruins: 1 (all worlds)");
        assertContains(run("ruin stats world"), "Ruins: 1 (world)");
        assertContains(run("ruin stats missing"), "Unknown world");
        assertContains(run("ruin tp #1"), "Teleported to");
        assertEquals(8.5, staff.getLocation().getX(), 0);
        assertEquals(41, staff.getLocation().getY(), 0);
        assertEquals(8.5, staff.getLocation().getZ(), 0);
        site.setWorldName("unloaded");
        assertContains(run("ruin stats unloaded"), "Ruins: 1 (unloaded)");
        assertContains(run("ruin tp #1"), "is not loaded");
        assertContains(run("ruin tp #999"), "No site with serial");
    }

    @Test
    public void workdayAndAutoCommandsReportTheirStateAndReloadKeepsDossiers() {
        assertContains(run("workday reset"), "not in an established excavation");
        assertContains(run("workday reset Missing"), "Player not online");
        assertContains(run("workday reset all"), "No established excavations");
        Site site = dossier(1, "Old camp");
        site.establish(staff.getUniqueId(), 1, 0, 20, 4, 4);
        site.setJornadaPickLeft(0);
        assertContains(run("workday reset"), "Reset pick jornada");
        assertEquals(plugin.catalogs().pick().jornadaActions(), site.getJornadaPickLeft());
        assertContains(run("pick reset all"), "Reset pick jornada");
        assertFalse(run("ruin auto status").isEmpty());
        assertContains(run("ruin auto reset"), "Cleared auto-ruin evaluation ledger");
        plugin.sites().commit(site);
        assertContains(run("reload"), "Reloaded Archaeo");
        assertEquals("Old camp", plugin.sites().findBySerial(1).orElseThrow().getName());
    }

    @Test
    public void completionUsesPermissionsPlayersWorldsAndEligibleSiteStates() {
        Site hidden = dossier(1, "Old ruin");
        Site camp = dossier(2, "Open camp");
        camp.establish(staff.getUniqueId(), 1, 0, 20, 4, 4);
        assertTrue(complete("g").contains("give"));
        assertTrue(complete("give", "tr").contains("tracker"));
        assertTrue(complete("give", "tracker", "arch").contains("Archaeologist"));
        assertTrue(complete("give", "tool", "IRON_").contains("IRON_PICKAXE"));
        assertTrue(complete("give", "tool", "IRON_PICKAXE", "arch").contains("Archaeologist"));
        assertEquals(List.of("reset"), complete("workday", "r"));
        assertTrue(complete("pick", "reset", "a").contains("all"));
        assertEquals(List.of("spawn"), complete("find", "s"));
        assertEquals(plugin.catalogs().artifacts().size(), complete("find", "spawn", "").size());
        assertTrue(complete("ruin", "").contains("create"));
        assertEquals(List.of("low"), complete("ruin", "create", "l"));
        assertEquals(List.of("high"), complete("ruin", "set-interest", "h"));
        assertEquals(List.of("world"), complete("ruin", "stats", "w"));
        assertTrue(complete("ruin", "auto", "").contains("reset"));
        assertTrue(complete("ruin", "set-interest", "high", "O").contains(hidden.getName()));
        assertFalse(complete("ruin", "set-interest", "high", "O").contains(camp.getName()));
        assertTrue(complete("ruin", "close", "").contains(camp.getName()));
        assertFalse(complete("ruin", "close", "").contains(hidden.getName()));
        assertTrue(complete("ruin", "delete", "con").contains("confirm"));
        assertTrue(complete("ruin", "info", "#").containsAll(List.of("#1", "#2")));
        assertTrue(complete("ruin", "tp", "Old", "r").contains("Old ruin"));
        assertTrue(complete("ruin", "camps", "#").contains("#2"));
        assertTrue(complete("ruin", "camps", "Archaeologist", "2").contains("#2"));
        assertTrue(complete("ruin", "delete", "#1", "confirm").isEmpty());
        assertTrue(complete("ruin", "close", "#2", "confirm").isEmpty());
        assertTrue(complete("unknown", "").isEmpty());
    }

    @Test
    public void createsAndRebuildsHiddenRuinsButPreservesEstablishedExcavations() {
        // Give all configured strata room below the surface in MockBukkit's shallow world.
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 4; y <= 40; y++) staff.getWorld().getBlockAt(x, y, z).setType(Material.STONE);
            }
        }
        assertContains(run("ruin create low River ruin"), "Site created:");
        Site site = plugin.sites().findByChunk("world", 0, 0).orElseThrow();
        assertEquals("River ruin", site.getName());
        UUID id = site.getId(); int serial = site.getSerial();
        assertContains(run("ruin create low Duplicate"), "already");
        assertEquals(1, plugin.sites().all().size());
        assertContains(run("ruin set-interest high #" + serial), "Rebuilt");
        assertEquals(net.tfminecraft.archaeo.model.InterestLevel.HIGH, site.getInterest());
        assertEquals(id, site.getId()); assertEquals("River ruin", site.getName());
        site.establish(staff.getUniqueId(), 0, 0, 2, 4, 2);
        assertFalse(run("ruin set-interest low #" + serial).isEmpty());
        assertEquals(net.tfminecraft.archaeo.model.InterestLevel.HIGH, site.getInterest());
    }

    @Test
    public void staffFindCommandCreatesAnExcavationAtSolidFeetAndRejectsAir() {
        staff.teleport(new org.bukkit.Location(staff.getWorld(), 5, 8, 5));
        assertContains(run("find spawn"), "Stand on a solid");
        assertTrue(plugin.sites().all().isEmpty());
        staff.getWorld().getBlockAt(5, 8, 5).setType(Material.STONE);
        assertContains(run("find spawn"), "Spawned");
        Site site = plugin.sites().findByChunk("world", 0, 0).orElseThrow();
        assertEquals(SiteStatus.ESTABLISHED, site.getStatus()); assertEquals(staff.getUniqueId(), site.getDirector());
        assertEquals(1, site.getFinds().size()); assertFalse(site.getFinds().getFirst().getCells().isEmpty());
        assertTrue(site.getJornadaPickLeft() > 0);
    }

    @Test
    public void campLookupTeleportsSingleResultsAndChecksDirectorFilter() {
        assertContains(run("ruin camps"), "does not direct any open camp");
        assertContains(run("ruin camps Nobody"), "Unknown player");
        Site camp = dossier(1, "River camp"); camp.establish(staff.getUniqueId(), 1, 0, 20, 4, 4);
        assertContains(run("ruin camps"), "Teleported to");
        assertEquals(20.5, staff.getLocation().getX(), 0); assertEquals(5, staff.getLocation().getY(), 0);
        PlayerMock guest = player("Guest");
        assertContains(run("ruin camps Guest #1"), "not directed by that player");
        assertContains(run("ruin camps #999"), "No site with serial");
        assertContains(run("ruin camps Archaeologist invalid"), "Usage:");
        camp.setWorldName("unloaded"); assertContains(run("ruin camps #1"), "World not loaded");
        camp.setWorldName("world"); camp.setStatus(SiteStatus.CLOSED);
        assertContains(run("ruin camps #1"), "no standing camp");
        assertEquals(guest.getWorld(), staff.getWorld());
    }

    @Test
    public void closingUnfinishedCampRequiresConfirmationAndIssuesArchive() {
        Site camp = dossier(1, "River camp"); camp.establish(staff.getUniqueId(), 0, 0, 2, 4, 2);
        var find = new net.tfminecraft.archaeo.model.BuriedFind(); find.setId(UUID.randomUUID());
        find.setArtifactId("vessel"); find.setStratumId("I"); camp.getFinds().add(find);
        assertContains(run("ruin close #1"), "Add confirm"); assertEquals(SiteStatus.ESTABLISHED, camp.getStatus());
        assertFalse(run("ruin close #1 confirm").isEmpty());
        assertEquals(SiteStatus.CLOSED, camp.getStatus()); assertTrue(staff.getInventory().contains(Material.WRITTEN_BOOK));
        assertEquals(SiteStatus.CLOSED, plugin.sites().findById(camp.getId()).orElseThrow().getStatus());
    }

    @Test
    public void consoleCommandsExplainWorldRequirementsAndCanGiveToNamedPlayers() {
        var console = server.getConsoleSender();
        for (String input : List.of("ruin create low", "find spawn", "ruin info", "ruin camps", "ruin camps #1", "sketch")) {
            while (console.nextMessage() != null) { }
            assertTrue(commands.onCommand(console, plugin.getCommand("archaeo"), "archaeo", input.split(" ")));
            assertNotNull(input, console.nextMessage());
        }
        staff.getInventory().clear();
        commands.onCommand(console, plugin.getCommand("archaeo"), "archaeo", new String[]{"give", "tracker", staff.getName()});
        assertTrue(plugin.trackerItem().isTracker(staff.getInventory().getItem(0)));
    }

    @Test public void multipleDirectedCampsOfferClickableChoicesWithoutTeleportingUntilSelected() {
        Site first = dossier(1, "River camp"); first.establish(staff.getUniqueId(), 1, 0, 20, 4, 4);
        Site second = dossier(2, "Hill camp"); second.establish(staff.getUniqueId(), 2, 0, 36, 9, 4);
        Site closed = dossier(3, "Old camp"); closed.establish(staff.getUniqueId(), 3, 0, 52, 4, 4); closed.setStatus(SiteStatus.CLOSED);
        Player.Spigot spigot = mock(Player.Spigot.class); doReturn(spigot).when(staff).spigot();
        List<BaseComponent[]> choices = new ArrayList<>();
        doAnswer(call -> { choices.add((BaseComponent[]) call.getRawArguments()[0]); return null; })
                .when(spigot).sendMessage(any(BaseComponent[].class));
        var before = staff.getLocation().clone();
        List<String> output = run("ruin camps Archaeologist");
        assertContains(output, "directs 2 camps"); assertContains(output, "Click [tp]"); assertEquals(before, staff.getLocation());
        assertEquals(2, choices.size());
        for (BaseComponent[] choice : choices) {
            assertEquals(2, choice.length); assertTrue(choice[0].toPlainText().contains("camp"));
            assertEquals("[tp]", choice[1].toPlainText()); assertEquals(ClickEvent.Action.RUN_COMMAND, choice[1].getClickEvent().getAction());
            assertNotNull(choice[1].getHoverEvent());
        }
        assertEquals(List.of("/archaeo ruin camps #1", "/archaeo ruin camps #2"), choices.stream().map(parts -> parts[1].getClickEvent().getValue()).sorted().toList());
        assertContains(run("ruin camps Archaeologist #2"), "Teleported to");
        assertEquals(36.5, staff.getLocation().getX(), 0); assertEquals(10, staff.getLocation().getY(), 0);
        assertEquals(2, choices.size());
    }

    @Test public void detailedDossierReportsStrataSamplingAndDistinctSourcesOfFindDamage() {
        Site site = dossier(1, "Survey camp"); site.establish(staff.getUniqueId(), 1, 0, 20, 4, 4);
        site.setCreatedBy(staff.getUniqueId()); site.setSurfaceY(40); site.getHintIds().addAll(List.of("pottery", "masonry"));
        StratumBand present = new StratumBand(); present.setId("I"); present.setPresent(true); present.setMinY(36); present.setMaxY(39); present.setDisturbed(true);
        StratumBand absent = new StratumBand(); absent.setId("II"); site.getStrata().put("I", present); site.getStrata().put("II", absent);
        site.addProspectSample(staff.getUniqueId(), new BlockCell(1, 40, 1)); site.confirmProspect(staff.getUniqueId());
        BuriedFind find = new BuriedFind(); find.setId(UUID.randomUUID()); find.setArtifactId("pot"); find.setStratumId("I"); find.setState(FindState.PARTIAL);
        find.setBuriedConservation(90); find.setConservation(90);
        find.getCells().addAll(List.of(new BlockCell(1, 38, 1), new BlockCell(2, 38, 1), new BlockCell(3, 38, 1), new BlockCell(4, 38, 1)));
        find.woundBeforeDig(find.getCells().get(0)); find.woundFromAbove(find.getCells().get(1)); find.markCleaned(find.getCells().get(2)); site.getFinds().add(find);
        List<String> info = run("ruin info #1");
        assertContains(info, "Created by: " + staff.getUniqueId()); assertContains(info, "Camp chunk 1,0 · table 20,4,4");
        assertContains(info, "I · Y 36–39 · disturbed"); assertContains(info, "II · absent");
        assertContains(info, "Hints: pottery, masonry"); assertContains(info, "Prospect confirmed: 1 · samplers: 1");
        assertContains(info, "pot · stratum I · PARTIAL · 4 cells"); assertContains(info, "of 90% buried · cleaned 1");
        assertContains(info, "disturbed before the dig (1 cells) · hurt while digging");
        assertEquals(1, site.getFinds().size()); assertEquals(1, find.getCleanedCells().size());
    }

    @Test public void censusSeparatesEveryLifecycleStateAndConsoleCanInspectDirectedCamps() {
        dossier(1, "Hidden");
        Site active = dossier(2, "Active"); active.establish(staff.getUniqueId(), 1, 0, 20, 4, 4);
        Site exhausted = dossier(3, "Exhausted"); exhausted.establish(staff.getUniqueId(), 2, 0, 36, 4, 4); exhausted.setStatus(SiteStatus.EXHAUSTED);
        Site closed = dossier(4, "Closed"); closed.establish(staff.getUniqueId(), 3, 0, 52, 4, 4); closed.setStatus(SiteStatus.CLOSED);
        List<String> stats = run("ruin stats");
        for (String expected : List.of("Ruins: 4", "Hidden: 1", "Excavations: 3", "Active: 1", "Exhausted: 1", "Closed: 1")) assertContains(stats, expected);
        List<String> camps = console("ruin camps Archaeologist");
        assertContains(camps, "directs 2 camps"); assertContains(camps, "camp 20,4,4"); assertContains(camps, "camp 36,4,4");
        assertFalse(camps.stream().anyMatch(line -> line.contains("Closed") || line.contains("[tp]")));
        assertContains(console("ruin camps Archaeologist #2"), "Only a player can teleport");
        assertContains(console("ruin info #2"), "Active");
        exhausted.setStatus(SiteStatus.CLOSED);
        assertContains(console("ruin camps Archaeologist"), "directs 1 camp:");
    }

    @Test public void consoleMustNameTargetsForLocationDependentCommandsAndGivesToolsToNamedPlayers() {
        for (String input : List.of("ruin close", "ruin delete confirm", "ruin set-interest high"))
            assertContains(console(input), "Console must pass a site name or serial");
        assertContains(console("give tracker"), "Console must name a player"); assertContains(console("workday reset"), "Console must name a player or use:");
        assertContains(console("ruin tp #1"), "must be used in-game");
        assertContains(console("give tool IRON_PICKAXE Archaeologist"), "Gave"); assertTrue(staff.getInventory().contains(Material.IRON_PICKAXE));
        assertContains(run("give tool Archaeologist"), "Gave");
        assertContains(run("ruin set-interest high #999"), "No site with serial");
    }

    @Test public void workdayResetNotifiesTheNamedWorkerAndPreservesUnavailableExcavations() {
        Site active = dossier(1, "Active"); active.establish(staff.getUniqueId(), 1, 0, 20, 4, 4);
        Site hidden = dossier(2, "Hidden"); hidden.setChunkX(2);
        Site closed = dossier(3, "Closed"); closed.setChunkX(3);
        closed.establish(staff.getUniqueId(), 3, 0, 52, 4, 4); closed.setStatus(SiteStatus.CLOSED);
        Site unloaded = dossier(4, "Unloaded"); unloaded.setWorldName("unloaded");
        unloaded.establish(staff.getUniqueId(), 1, 0, 20, 4, 4);
        for (Site site : List.of(active, hidden, closed, unloaded)) {
            site.setJornadaPickLeft(0); plugin.sites().save(site);
        }
        PlayerMock worker = player("Worker"); worker.teleport(new org.bukkit.Location(staff.getWorld(), 20, 5, 4));
        assertContains(console("workday reset Worker"), "Reset pick jornada on #1");
        assertTrue(worker.nextMessage().contains("Staff reset today's Hand Pick actions"));
        assertEquals(plugin.catalogs().pick().jornadaActions(), active.getJornadaPickLeft());
        active.setJornadaPickLeft(0);
        List<String> reset = run("workday reset all");
        assertEquals(1, reset.size()); assertContains(reset, "on #1");
        assertEquals(plugin.catalogs().pick().jornadaActions(), active.getJornadaPickLeft());
        for (Site preserved : List.of(hidden, closed, unloaded)) assertEquals(0, preserved.getJornadaPickLeft());
    }

    @Test public void unlimitedWorkdayExplainsWhyNoResetIsNeeded() {
        plugin.getConfig().set("excavation.workday-actions", 0); plugin.saveConfig();
        assertContains(run("reload"), "Reloaded Archaeo");
        assertContains(run("workday reset all"), "Work-day actions are unlimited");
        assertTrue(plugin.sites().all().isEmpty());
    }

    @Test public void staffFindUsesGroundUnderfootAndReportsOverlapWithoutReplacingTheFind() {
        staff.teleport(new org.bukkit.Location(staff.getWorld(), 5, 9, 5));
        staff.getWorld().getBlockAt(5, 8, 5).setType(Material.STONE);
        assertContains(run("find spawn vessel 1"), "Spawned");
        Site site = plugin.sites().findByChunk("world", 0, 0).orElseThrow();
        BuriedFind original = site.getFinds().getFirst();
        assertEquals(List.of(new BlockCell(5, 8, 5)), original.getCells());
        assertContains(run("find spawn vessel 1"), "A find already occupies this block");
        assertEquals(List.of(original), site.getFinds());
        assertEquals(Material.STONE, staff.getWorld().getBlockAt(5, 8, 5).getType());
    }

    @Test public void sketchCommandStartsOneEditableSheetAndExplainsHowToSaveIt() {
        // MockBukkit lacks these two rendering flags; retain real map IDs, metadata and sessions.
        try (var maps = mockStatic(org.bukkit.Bukkit.class, CALLS_REAL_METHODS)) {
            maps.when(() -> org.bukkit.Bukkit.createMap(staff.getWorld())).thenAnswer(call -> {
                var view = spy(server.createMap(staff.getWorld()));
                doNothing().when(view).setTrackingPosition(anyBoolean());
                doNothing().when(view).setUnlimitedTracking(anyBoolean());
                return view;
            });
            run("sketch");
            assertTrue(plugin.sketch().editing(staff));
            var sheet = staff.getInventory().getItemInMainHand();
            assertTrue(plugin.sketch().isSketchMap(sheet));
            assertContains(run("sketch"), "Switch the map away to save");
            assertEquals(sheet, staff.getInventory().getItemInMainHand());
            assertEquals(1, java.util.Arrays.stream(staff.getInventory().getContents())
                    .filter(plugin.sketch()::isSketchMap).count());
        }
    }

    @Test public void failedReloadReportsTheBrokenCatalogueAndKeepsCreatingRuinsAtEveryInterest() throws Exception {
        // Regression: the interest map used to be cleared before a missing level threw, so a failed
        // reload left later ruin creation without its budget.
        java.io.File file = new java.io.File(plugin.getDataFolder(), "interest.yml");
        var interest = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        interest.set("interest-levels.high", null); interest.save(file);
        assertContains(run("reload"), "Reload failed: Missing interest-levels.high");
        assertNotNull(plugin.catalogs().interest(net.tfminecraft.archaeo.model.InterestLevel.HIGH));
        stoneColumn();
        assertContains(run("ruin create high Kept budget"), "Site created:");
        assertEquals(net.tfminecraft.archaeo.model.InterestLevel.HIGH, plugin.sites().findByChunk("world", 0, 0).orElseThrow().getInterest());
    }

    @Test public void roleItemFromAMissingPackPluginIsReportedInsteadOfGivingAir() {
        plugin.getConfig().set("tracker.item", "itemsadder:field:tracker"); plugin.saveConfig();
        assertContains(run("reload"), "Reloaded Archaeo");
        staff.getInventory().clear();
        assertContains(run("give tracker"), "Could not create that item");
        assertTrue(staff.getInventory().isEmpty());
        assertContains(run("give tool IRON_PICKAXE Missing"), "Player not online: Missing");
        assertTrue(staff.getInventory().isEmpty());
        assertContains(run("nonsense extra"), "Usage: /archaeo ruin create");
    }

    @Test public void workdayResetNamesOnlyNamedSitesAndIgnoresUnopenedGroundUnderfoot() {
        Site hidden = dossier(1, "Hidden"); hidden.setChunkX(0);
        assertContains(run("workday reset"), "not in an established excavation");
        Site closed = dossier(2, "Closed camp"); closed.setChunkX(5); closed.establish(staff.getUniqueId(), 0, 1, 4, 4, 20); closed.setStatus(SiteStatus.CLOSED);
        staff.teleport(new org.bukkit.Location(staff.getWorld(), 4, 5, 20));
        assertContains(run("workday reset"), "not in an established excavation");
        Site unnamed = dossier(3, null); unnamed.setChunkX(7); unnamed.establish(staff.getUniqueId(), 7, 0, 116, 4, 4); unnamed.setJornadaPickLeft(0);
        staff.teleport(new org.bukkit.Location(staff.getWorld(), 116, 5, 4));
        assertEquals(List.of("Reset pick jornada on #3 to " + plugin.catalogs().pick().jornadaActions() + "."), run("workday reset"));
        unnamed.setName(" ");
        assertEquals(List.of("Reset pick jornada on #3 to " + plugin.catalogs().pick().jornadaActions() + "."), run("workday reset"));
        assertEquals(SiteStatus.CLOSED, closed.getStatus());
    }

    @Test public void staffFindFallsBackToTheFirstTemplateAndExplainsAnEmptyArtifactCatalogue() throws Exception {
        writeData("artifacts.yml", "artifacts:\n  urn: {display-name: Urn, size-min: 1, size-max: 2}\n");
        assertContains(run("reload"), "Reloaded Archaeo");
        staff.teleport(new org.bukkit.Location(staff.getWorld(), 5, 8, 5)); staff.getWorld().getBlockAt(5, 8, 5).setType(Material.STONE);
        assertContains(run("find spawn"), "Spawned Urn");
        assertEquals("urn", plugin.sites().findByChunk("world", 0, 0).orElseThrow().getFinds().getFirst().getArtifactId());
        writeData("artifacts.yml", "artifacts: {}\n");
        assertContains(run("reload"), "Reloaded Archaeo");
        staff.teleport(new org.bukkit.Location(staff.getWorld(), 21, 8, 5)); staff.getWorld().getBlockAt(21, 8, 5).setType(Material.STONE);
        assertContains(run("find spawn"), "No artifact templates are loaded.");
        assertTrue(plugin.sites().findByChunk("world", 1, 0).isEmpty());
    }

    @Test public void unnamedRuinsAndRebuildsInBareChunksExplainThatNoFindFitted() {
        // A stone lid over open air: the strata lie within the world but hold no buried ground.
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) staff.getWorld().getBlockAt(x, 40, z).setType(Material.STONE);
        List<String> created = run("ruin create exceptional");
        assertContains(created, "Site created: #1"); assertContains(created, "No find fitted");
        Site site = plugin.sites().findByChunk("world", 0, 0).orElseThrow();
        assertTrue(site.getFinds().isEmpty()); assertFalse(site.getName().isBlank()); assertEquals(net.tfminecraft.archaeo.model.InterestLevel.EXCEPTIONAL, site.getInterest());
        List<String> rebuilt = run("ruin set-interest low");
        assertContains(rebuilt, "Rebuilt #1"); assertContains(rebuilt, "No find fitted");
        assertEquals(net.tfminecraft.archaeo.model.InterestLevel.LOW, site.getInterest());
        List<String> info = run("ruin info");
        assertContains(info, "Site #1"); assertContains(info, "interest: low");
    }

    @Test public void closeAndDeleteActOnTheSiteUnderfootAndRejectUnknownSerials() {
        assertContains(run("ruin close #9"), "No site with serial #9");
        assertContains(run("ruin delete #9 confirm"), "No site with serial #9");
        Site camp = dossier(1, "Finished camp"); camp.establish(staff.getUniqueId(), 0, 0, 2, 4, 2);
        assertContains(run("ruin close"), "Closed Finished camp");
        assertEquals(SiteStatus.CLOSED, camp.getStatus()); assertTrue(staff.getInventory().contains(Material.WRITTEN_BOOK));
        Site open = dossier(2, "Open camp"); open.setChunkX(3); open.establish(staff.getUniqueId(), 3, 0, 52, 4, 4);
        var find = new BuriedFind(); find.setId(UUID.randomUUID()); find.setArtifactId("vessel"); find.setStratumId("I"); open.getFinds().add(find);
        staff.teleport(new org.bukkit.Location(staff.getWorld(), 52, 5, 4));
        assertContains(run("ruin close confirm"), "Closed Open camp");
        assertEquals(SiteStatus.CLOSED, open.getStatus());
        // Paper keeps the crafting view open after a board closes; MockBukkit leaves no top inventory.
        staff.openInventory(server.createInventory(null, 9));
        assertContains(run("ruin delete confirm"), "Deleted #2");
        assertTrue(plugin.sites().findById(open.getId()).isEmpty()); assertTrue(plugin.sites().findById(camp.getId()).isPresent());
    }

    @Test public void campListingHandlesUnknownDirectorsAndDossiersThatLostTheirCampTable() {
        assertContains(run("ruin camps Nobody #1"), "Unknown player: Nobody");
        // A dossier whose establishment block lost camp-x still loads as a locked camp.
        Site lost = dossier(1, "Lost table"); lost.setStatus(SiteStatus.ESTABLISHED); lost.setDirector(staff.getUniqueId());
        var before = staff.getLocation().clone();
        List<String> single = run("ruin camps");
        assertContains(single, "Lost table · established · world · camp unknown"); assertContains(single, "has no camp coordinates");
        assertContains(run("ruin camps #1"), "has no standing camp to teleport to");
        assertEquals(before, staff.getLocation());
        Site second = dossier(2, "Second camp"); second.establish(staff.getUniqueId(), 2, 0, 36, 4, 4);
        Player.Spigot spigot = mock(Player.Spigot.class); doReturn(spigot).when(staff).spigot();
        List<String> several = run("ruin camps");
        assertContains(several, "Archaeologist directs 2 camps:"); assertContains(several, "Click [tp] or run /archaeo ruin camps #serial");
        verify(spigot, times(2)).sendMessage(any(BaseComponent[].class));
        assertEquals(before, staff.getLocation());
    }

    @Test public void dossierReportsMissingInterestCreationTimeAndUndamagedEvidence() {
        Site site = dossier(1, "Old dossier"); site.setInterest(null); site.setCreatedAt(null);
        StratumBand band = new StratumBand(); band.setId("I"); band.setPresent(true); band.setMinY(30); band.setMaxY(34); site.getStrata().put("I", band);
        BuriedFind find = new BuriedFind(); find.setId(UUID.randomUUID()); find.setArtifactId("pot"); find.setStratumId("I"); find.setState(FindState.HIDDEN);
        find.setBuriedConservation(80); find.setConservation(80); find.getCells().add(new BlockCell(1, 31, 1)); site.getFinds().add(find);
        List<String> info = run("ruin info #1");
        assertContains(info, "interest: none"); assertContains(info, "Created by: unknown · at unknown");
        assertTrue(info.contains("  I · Y 30–34"));
        assertTrue(info.contains("  pot · stratum I · HIDDEN · 1 cells · 80% of 80% buried"));
        assertContains(run("ruin info #"), "No site named \"#\"");
    }

    @Test public void completionCoversAliasesMultiWordNamesSerialDigitsAndExtraTokens() {
        Site hidden = dossier(1, "Old ruin"); Site camp = dossier(2, "Open camp"); camp.establish(staff.getUniqueId(), 1, 0, 20, 4, 4);
        assertTrue(complete("give", "pick", "IRON_").contains("IRON_PICKAXE"));
        assertEquals(List.of("Archaeologist"), complete("give", "pick", "IRON_PICKAXE", "arch"));
        for (String[] extra : List.of(new String[]{"give", "tracker", "Arch", "x"}, new String[]{"give", "tool", "IRON_PICKAXE", "Arch", "x"},
                new String[]{"workday", "nonsense", "a"}, new String[]{"workday", "reset", "all", "x"}, new String[]{"find", "nonsense", "v"},
                new String[]{"find", "spawn", "vessel", "3"}, new String[]{"ruin", "create", "low", "R"}, new String[]{"ruin", "nonsense", "x"},
                new String[]{"ruin", "camps", "Nobody", "#"}, new String[]{"ruin", "camps", "Archaeologist", "#2", "x"}))
            assertTrue(String.join(" ", extra), complete(extra).isEmpty());
        assertEquals(List.of("Archaeologist"), complete("workday", "reset", "arch"));
        assertEquals(List.of("Old ruin"), complete("ruin", "set-interest", "high", "Old", "r"));
        assertEquals(List.of("confirm"), complete("ruin", "close", "confirm"));
        assertEquals(List.of("Open camp"), complete("ruin", "close", "Open"));
        assertEquals(List.of("Open camp", "confirm"), complete("ruin", "close", "Open", "c"));
        assertEquals(List.of("#2"), complete("ruin", "close", "2"));
        assertEquals(List.of("confirm"), complete("ruin", "delete", "confirm"));
        assertEquals(List.of("#1"), complete("ruin", "delete", "1"));
        assertTrue(complete("ruin", "delete", "z").isEmpty());
        assertEquals(List.of("Old ruin"), complete("ruin", "delete", "Old", "r"));
        assertEquals(List.of("#1"), complete("ruin", "info", "1"));
        assertTrue(complete("ruin", "info", "z").isEmpty());
        assertEquals(List.of("Old ruin"), complete("ruin", "info", "Old", "r"));
        assertEquals(java.util.Set.of("#1", "#2"), new java.util.HashSet<>(complete("ruin", "tp", "#")));
        assertEquals(List.of("#2"), complete("ruin", "camps", "2"));
        assertTrue(complete("ruin", "camps", "z").isEmpty());
        assertEquals(List.of("#2"), complete("ruin", "camps", "Archaeologist", "#"));
        assertTrue(complete("ruin", "camps", "Archaeologist", "9").isEmpty());
        assertEquals(SiteStatus.HIDDEN, hidden.getStatus());
    }

    @Test public void worldCompletionOffersUnloadedDossierWorldsAndSkipsDossiersWithoutAWorld() {
        dossier(1, "Archived").setWorldName("archive");
        dossier(2, "Worldless").setWorldName(null); // a dossier file without its world: key
        assertEquals(List.of("archive"), complete("ruin", "stats", "a"));
        assertEquals(List.of("archive", "world"), complete("ruin", "stats", ""));
        assertTrue(complete("ruin", "stats", "x").isEmpty());
    }

    @Test public void handOnlyExcavationGivesTheFallbackPickAndStillNamesAnOfflineRecipient() {
        for (String profile : List.of("hand", "light", "heavy", "super-heavy")) plugin.getConfig().set("excavation.tools." + profile + ".items", List.of("AIR"));
        plugin.saveConfig();
        assertContains(run("reload"), "Reloaded Archaeo");
        assertTrue(complete("give", "tool", "").isEmpty());
        assertContains(run("give tool Missing"), "Player not online: Missing");
        staff.getInventory().clear();
        assertContains(run("give tool"), "an excavation tool (stone pickaxe)");
        assertTrue(staff.getInventory().contains(Material.STONE_PICKAXE));
    }

    private void stoneColumn() {
        // Give all configured strata room below the surface in MockBukkit's shallow world.
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 4; y <= 40; y++) staff.getWorld().getBlockAt(x, y, z).setType(Material.STONE);
    }

    private void writeData(String name, String contents) throws Exception {
        java.nio.file.Files.writeString(new java.io.File(plugin.getDataFolder(), name).toPath(), contents);
    }

    private List<String> console(String input) {
        var sender = server.getConsoleSender(); while (sender.nextMessage() != null) { }
        assertTrue(commands.onCommand(sender, plugin.getCommand("archaeo"), "archaeo", input.split(" ")));
        List<String> result = new ArrayList<>(); String line; while ((line = sender.nextMessage()) != null) result.add(line); return result;
    }

    private PlayerMock player(String name) {
        PlayerMock player = spy(new PlayerMock(server, name));
        doReturn(null).when(player).getTargetBlockExact(anyInt());
        server.addPlayer(player);
        player.openInventory(server.createInventory(null, 9));
        return player;
    }

    private Site dossier(int serial, String name) {
        Site site = new Site();
        site.setId(UUID.randomUUID()); site.setSerial(serial); site.setName(name);
        site.setCreatedAt(java.time.Instant.parse("2026-01-01T00:00:00Z")); site.setWorldName("world"); site.setStatus(SiteStatus.HIDDEN);
        site.setInterest(net.tfminecraft.archaeo.model.InterestLevel.LOW);
        plugin.sites().save(site);
        return site;
    }

    private List<String> run(String input) {
        while (staff.nextMessage() != null) { }
        assertTrue(commands.onCommand(staff, plugin.getCommand("archaeo"), "archaeo",
                input.isEmpty() ? new String[0] : input.split(" ")));
        List<String> result = new ArrayList<>();
        String message;
        while ((message = staff.nextMessage()) != null) result.add(message);
        return result;
    }

    private List<String> complete(String... args) {
        return commands.onTabComplete(staff, plugin.getCommand("archaeo"), "archaeo", args);
    }

    private static void assertContains(List<String> messages, String expected) {
        assertTrue("Expected '" + expected + "' in " + messages,
                messages.stream().anyMatch(message -> message.contains(expected)));
    }
}
