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
        server = net.tfminecraft.archaeo.CharacterTestSupport.mock();
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
        net.tfminecraft.archaeo.CharacterTestSupport.establish(site, staff.getUniqueId(), 1, 0, 20, 4, 4);
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
        net.tfminecraft.archaeo.CharacterTestSupport.establish(camp, staff.getUniqueId(), 1, 0, 20, 4, 4);
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
        assertTrue(complete("ruin", "tp", "Old", "r").contains("ruin"));
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
        net.tfminecraft.archaeo.CharacterTestSupport.addProspectSample(site,staff.getUniqueId(),new BlockCell(1,40,1));plugin.sites().save(site);
        assertContains(run("ruin set-interest high #" + serial), "Rebuilt");
        assertEquals(net.tfminecraft.archaeo.model.InterestLevel.HIGH, site.getInterest());
        assertEquals(id, site.getId()); assertEquals("River ruin", site.getName());
        assertTrue(plugin.sites().loadAll());assertFalse(plugin.sites().ownership().held(id+".yml"));assertTrue(plugin.sites().findById(id).orElseThrow().allProspectSamples().isEmpty());
        net.tfminecraft.archaeo.CharacterTestSupport.establish(site, staff.getUniqueId(), 0, 0, 2, 4, 2);
        assertFalse(run("ruin set-interest low #" + serial).isEmpty());
        assertEquals(net.tfminecraft.archaeo.model.InterestLevel.HIGH, site.getInterest());
    }

    @Test public void reloadReportsRefusalInsteadOfClaimingSitesWereReloaded()throws Exception{
        Site site=dossier(1,"Unsaved");var file=plugin.getDataFolder().toPath().resolve("sites/"+site.getId()+".yml");java.nio.file.Files.writeString(file,java.nio.file.Files.readString(file)+"external: retained\n");site.setRecoveredCount(17);plugin.sites().touch(site);
        var blocked=plugin.getDataFolder().toPath().resolve("ownership/conflicts");java.nio.file.Files.createDirectories(blocked.getParent());java.nio.file.Files.writeString(blocked,"not a directory");
        var messages=run("reload");assertContains(messages,"site reload refused");assertFalse(messages.stream().anyMatch(line->line.contains("Reloaded Archaeo config, catalogs, and sites from disk")));assertSame(site,plugin.sites().findById(site.getId()).orElseThrow());assertEquals(17,site.getRecoveredCount());java.nio.file.Files.delete(blocked);plugin.sites().flushDirty();
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
        Site camp = dossier(1, "River camp"); net.tfminecraft.archaeo.CharacterTestSupport.establish(camp, staff.getUniqueId(), 1, 0, 20, 4, 4);
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
        Site camp = dossier(1, "River camp"); net.tfminecraft.archaeo.CharacterTestSupport.establish(camp, staff.getUniqueId(), 0, 0, 2, 4, 2);
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
        Site first = dossier(1, "River camp"); net.tfminecraft.archaeo.CharacterTestSupport.establish(first, staff.getUniqueId(), 1, 0, 20, 4, 4);
        Site second = dossier(2, "Hill camp"); net.tfminecraft.archaeo.CharacterTestSupport.establish(second, staff.getUniqueId(), 2, 0, 36, 9, 4);
        Site closed = dossier(3, "Old camp"); net.tfminecraft.archaeo.CharacterTestSupport.establish(closed, staff.getUniqueId(), 3, 0, 52, 4, 4); closed.setStatus(SiteStatus.CLOSED);
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
        Site site = dossier(1, "Survey camp"); net.tfminecraft.archaeo.CharacterTestSupport.establish(site, staff.getUniqueId(), 1, 0, 20, 4, 4);
        site.setCreatedBy(staff.getUniqueId()); site.setSurfaceY(40); site.getHintIds().addAll(List.of("pottery", "masonry"));
        StratumBand present = new StratumBand(); present.setId("I"); present.setPresent(true); present.setMinY(36); present.setMaxY(39); present.setDisturbed(true);
        StratumBand absent = new StratumBand(); absent.setId("II"); site.getStrata().put("I", present); site.getStrata().put("II", absent);
        net.tfminecraft.archaeo.CharacterTestSupport.addProspectSample(site, staff.getUniqueId(), new BlockCell(1, 40, 1)); net.tfminecraft.archaeo.CharacterTestSupport.confirmProspect(site, staff.getUniqueId());
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
        Site active = dossier(2, "Active"); net.tfminecraft.archaeo.CharacterTestSupport.establish(active, staff.getUniqueId(), 1, 0, 20, 4, 4);
        Site exhausted = dossier(3, "Exhausted"); net.tfminecraft.archaeo.CharacterTestSupport.establish(exhausted, staff.getUniqueId(), 2, 0, 36, 4, 4); exhausted.setStatus(SiteStatus.EXHAUSTED);
        Site closed = dossier(4, "Closed"); net.tfminecraft.archaeo.CharacterTestSupport.establish(closed, staff.getUniqueId(), 3, 0, 52, 4, 4); closed.setStatus(SiteStatus.CLOSED);
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
        Site active = dossier(1, "Active"); net.tfminecraft.archaeo.CharacterTestSupport.establish(active, staff.getUniqueId(), 1, 0, 20, 4, 4);
        Site hidden = dossier(2, "Hidden"); hidden.setChunkX(2);
        Site closed = dossier(3, "Closed"); closed.setChunkX(3);
        net.tfminecraft.archaeo.CharacterTestSupport.establish(closed, staff.getUniqueId(), 3, 0, 52, 4, 4); closed.setStatus(SiteStatus.CLOSED);
        Site unloaded = dossier(4, "Unloaded"); unloaded.setWorldName("unloaded");
        net.tfminecraft.archaeo.CharacterTestSupport.establish(unloaded, staff.getUniqueId(), 1, 0, 20, 4, 4);
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
        Site closed = dossier(2, "Closed camp"); closed.setChunkX(5); net.tfminecraft.archaeo.CharacterTestSupport.establish(closed, staff.getUniqueId(), 0, 1, 4, 4, 20); closed.setStatus(SiteStatus.CLOSED);
        staff.teleport(new org.bukkit.Location(staff.getWorld(), 4, 5, 20));
        assertContains(run("workday reset"), "not in an established excavation");
        Site unnamed = dossier(3, null); unnamed.setChunkX(7); net.tfminecraft.archaeo.CharacterTestSupport.establish(unnamed, staff.getUniqueId(), 7, 0, 116, 4, 4); unnamed.setJornadaPickLeft(0);
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
        Site camp = dossier(1, "Finished camp"); net.tfminecraft.archaeo.CharacterTestSupport.establish(camp, staff.getUniqueId(), 0, 0, 2, 4, 2);
        assertContains(run("ruin close"), "Closed Finished camp");
        assertEquals(SiteStatus.CLOSED, camp.getStatus()); assertTrue(staff.getInventory().contains(Material.WRITTEN_BOOK));
        Site open = dossier(2, "Open camp"); open.setChunkX(3); net.tfminecraft.archaeo.CharacterTestSupport.establish(open, staff.getUniqueId(), 3, 0, 52, 4, 4);
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
        Site lost = dossier(1, "Lost table"); lost.setStatus(SiteStatus.ESTABLISHED); net.tfminecraft.archaeo.CharacterTestSupport.setDirector(lost, staff.getUniqueId());
        var before = staff.getLocation().clone();
        List<String> single = run("ruin camps");
        assertContains(single, "Lost table · established · world · camp unknown"); assertContains(single, "has no camp coordinates");
        assertContains(run("ruin camps #1"), "has no standing camp to teleport to");
        assertEquals(before, staff.getLocation());
        Site second = dossier(2, "Second camp"); net.tfminecraft.archaeo.CharacterTestSupport.establish(second, staff.getUniqueId(), 2, 0, 36, 4, 4);
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
        Site hidden = dossier(1, "Old ruin"); Site camp = dossier(2, "Open camp"); net.tfminecraft.archaeo.CharacterTestSupport.establish(camp, staff.getUniqueId(), 1, 0, 20, 4, 4);
        assertTrue(complete("give", "pick", "IRON_").contains("IRON_PICKAXE"));
        assertEquals(List.of("Archaeologist"), complete("give", "pick", "IRON_PICKAXE", "arch"));
        for (String[] extra : List.of(new String[]{"give", "tracker", "Arch", "x"}, new String[]{"give", "tool", "IRON_PICKAXE", "Arch", "x"},
                new String[]{"workday", "nonsense", "a"}, new String[]{"workday", "reset", "all", "x"}, new String[]{"find", "nonsense", "v"},
                new String[]{"find", "spawn", "vessel", "3"}, new String[]{"ruin", "create", "low", "R"}, new String[]{"ruin", "nonsense", "x"},
                new String[]{"ruin", "camps", "Nobody", "#"}, new String[]{"ruin", "camps", "Archaeologist", "#2", "x"}))
            assertTrue(String.join(" ", extra), complete(extra).isEmpty());
        assertEquals(List.of("Archaeologist"), complete("workday", "reset", "arch"));
        assertEquals(List.of("ruin"), complete("ruin", "set-interest", "high", "Old", "r"));
        assertEquals(List.of("confirm"), complete("ruin", "close", "confirm"));
        assertEquals(List.of("Open camp"), complete("ruin", "close", "Open"));
        assertEquals(List.of("camp", "confirm"), complete("ruin", "close", "Open", "c"));
        assertEquals(List.of("#2"), complete("ruin", "close", "2"));
        assertEquals(List.of("confirm"), complete("ruin", "delete", "confirm"));
        assertEquals(List.of("#1"), complete("ruin", "delete", "1"));
        assertTrue(complete("ruin", "delete", "z").isEmpty());
        assertEquals(List.of("ruin"), complete("ruin", "delete", "Old", "r"));
        assertEquals(List.of("#1"), complete("ruin", "info", "1"));
        assertTrue(complete("ruin", "info", "z").isEmpty());
        assertEquals(List.of("ruin"), complete("ruin", "info", "Old", "r"));
        assertEquals(java.util.Set.of("#1", "#2"), new java.util.HashSet<>(complete("ruin", "tp", "#")));
        assertEquals(List.of("#2"), complete("ruin", "camps", "2"));
        assertTrue(complete("ruin", "camps", "z").isEmpty());
        assertEquals(List.of("#2"), complete("ruin", "camps", "Archaeologist", "#"));
        assertTrue(complete("ruin", "camps", "Archaeologist", "9").isEmpty());
        assertEquals(SiteStatus.HIDDEN, hidden.getStatus());
    }

    @Test public void acceptedNameCompletionsReplaceOnlyTheCurrentToken() {
        Site hidden = dossier(1, "Old ruined city");
        hidden.setSurfaceY(40);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++)
            staff.getWorld().getBlockAt(x, 40, z).setType(Material.STONE);
        Site camp = dossier(2, "Open camp");
        net.tfminecraft.archaeo.CharacterTestSupport.establish(camp, staff.getUniqueId(), 1, 0, 20, 4, 4);
        for (String action : List.of("info", "tp", "delete", "set-interest high")) {
            for (String query : List.of("Ol", "oLD r", "Old ", "Old ruined c", "Old ruined ")) {
                String input = "ruin " + action + " " + query;
                List<String> suggestions = complete(input.split(" ", -1)).stream()
                        .filter(value -> !value.equals("confirm")).toList();
                assertEquals(1, suggestions.size());
                String accepted = input.substring(0, input.lastIndexOf(' ') + 1) + suggestions.getFirst();
                assertEquals("ruin " + action + " " + (query.startsWith("oLD") ? "oLD ruined city" : "Old ruined city"), accepted);
                assertFalse(run(accepted).stream().anyMatch(message -> message.contains("No site named")));
            }
        }
        assertEquals(List.of("camp", "confirm"), complete("ruin", "close", "Open", ""));
        assertContains(run("ruin close Open " + complete("ruin", "close", "Open", "ca").getFirst()), "Closed Open camp");
        assertEquals(SiteStatus.CLOSED, camp.getStatus());
        assertTrue(complete("ruin", "set-interest", "high", "Open", "").isEmpty());
        assertEquals(List.of("confirm"), complete("ruin", "close", "Old", ""));
    }

    @Test public void optionalToolSlotIncludesExecutablePlayerShorthand() {
        assertEquals(List.of("Archaeologist"), complete("give", "tool", "aRcH"));
        assertEquals(List.of("Archaeologist"), complete("give", "pick", "aRcH"));
        assertTrue(complete("give", "tool", "").contains("IRON_PICKAXE"));
        assertTrue(complete("give", "tool", "").contains("Archaeologist"));
        assertTrue(complete("give", "tool", "absent").isEmpty());
        assertContains(run("give tool " + complete("give", "tool", "Arch").getFirst()), "Gave");
    }

    @Test public void optionalToolSlotOmitsCollidingPlayerAliasesButExplicitRecipientsStillWork() {
        PlayerMock recipient = player("bRuSh");
        player("BrushUser");
        assertEquals(List.of("bRuSh", "BrushUser"), complete("give", "tool", "br"));
        plugin.getConfig().set("excavation.tools.light.items", List.of("BRUSH"));
        plugin.saveConfig();
        assertContains(run("reload"), "Reloaded Archaeo");

        for (String kind : List.of("tool", "pick")) {
            for (String prefix : List.of("", "b", "bRu", "bRuSh")) {
                List<String> suggestions = complete("give", kind, prefix);
                assertTrue(suggestions.toString(), suggestions.contains("BRUSH"));
                assertTrue(suggestions.toString(), suggestions.contains("BrushUser"));
                assertFalse(suggestions.toString(), suggestions.contains("bRuSh"));
                assertTrue(complete("give", kind, "BRUSH", prefix).contains("bRuSh"));
            }
            staff.getInventory().clear();
            recipient.getInventory().clear();
            String explicitRecipient = complete("give", kind, "BRUSH", "bRuSh").stream()
                    .filter(name -> name.equals("bRuSh")).findFirst().orElseThrow();
            assertContains(run("give " + kind + " BRUSH " + explicitRecipient), "to bRuSh");
            assertTrue(recipient.getInventory().contains(Material.BRUSH));
            assertTrue(staff.getInventory().isEmpty());

            recipient.getInventory().clear();
            assertContains(run("give " + kind + " " + complete("give", kind, "bRuSh").getFirst()), "to Archaeologist");
            assertTrue(staff.getInventory().contains(Material.BRUSH));
            assertTrue(recipient.getInventory().isEmpty());
        }

        plugin.getConfig().set("excavation.tools.light.items", List.of("AIR"));
        plugin.saveConfig();
        assertContains(run("reload"), "Reloaded Archaeo");
        assertEquals(List.of("bRuSh", "BrushUser"), complete("give", "tool", "br"));
    }

    @Test public void optionalToolSlotKeepsCanonicalToolTokensThatAreAlsoPlayerNames() {
        PlayerMock recipient = player("IRON_PICKAXE");
        for (String kind : List.of("tool", "pick")) {
            for (String prefix : List.of("", "iRoN_", "IRON_PICKAXE")) {
                assertEquals(1L, complete("give", kind, prefix).stream()
                        .filter(token -> token.equals("IRON_PICKAXE")).count());
            }
            staff.getInventory().clear();
            recipient.getInventory().clear();
            String explicitRecipient = complete("give", kind, "IRON_PICKAXE", "iRoN_").getFirst();
            assertContains(run("give " + kind + " IRON_PICKAXE " + explicitRecipient), "to IRON_PICKAXE");
            assertTrue(recipient.getInventory().contains(Material.IRON_PICKAXE));
            assertTrue(staff.getInventory().isEmpty());
        }
    }

    @Test public void campDirectorCompletionIncludesOfflineDirectoryIdsAndSafeNames() {
        var identity = mock(net.tfminecraft.tlibs.character.CharacterIdentity.class);
        UUID multiword = UUID.randomUUID(), single = UUID.randomUUID(), duplicate = UUID.randomUUID();
        var info = new net.tfminecraft.tlibs.character.CharacterInfo(
            new net.tfminecraft.tlibs.character.CharacterRef(UUID.randomUUID(), multiword), "Old Owner",
            net.tfminecraft.tlibs.character.CharacterStatus.ALIVE, null, java.util.Set.of(), 0, null);
        var safe = new net.tfminecraft.tlibs.character.CharacterInfo(
            new net.tfminecraft.tlibs.character.CharacterRef(UUID.randomUUID(), single), "Director",
            net.tfminecraft.tlibs.character.CharacterStatus.ALIVE, null, java.util.Set.of(), 0, null);
        var repeated = new net.tfminecraft.tlibs.character.CharacterInfo(
            new net.tfminecraft.tlibs.character.CharacterRef(UUID.randomUUID(), duplicate), "Duplicate",
            net.tfminecraft.tlibs.character.CharacterStatus.ALIVE, null, java.util.Set.of(), 0, null);
        when(identity.info(multiword)).thenReturn(java.util.Optional.of(info));
        when(identity.info(single)).thenReturn(java.util.Optional.of(safe));
        when(identity.info(duplicate)).thenReturn(java.util.Optional.of(repeated));
        when(identity.findByName("Director")).thenReturn(List.of(safe));
        when(identity.findByName("Duplicate")).thenReturn(List.of(repeated, safe));
        server.getServicesManager().unregister(net.tfminecraft.tlibs.character.CharacterIdentity.class,
            server.getServicesManager().load(net.tfminecraft.tlibs.character.CharacterIdentity.class));
        server.getServicesManager().register(net.tfminecraft.tlibs.character.CharacterIdentity.class, identity,
            plugin, org.bukkit.plugin.ServicePriority.Highest);
        int serial = 1;
        for (UUID id : List.of(multiword, single, duplicate, UUID.randomUUID())) {
            Site camp = dossier(serial++, "Camp " + serial);
            net.tfminecraft.archaeo.CharacterTestSupport.establish(camp, id, serial, 0, 20, 4, 4);
        }
        dossier(20, "Hidden");
        var all = complete("ruin", "camps", "");
        assertTrue(all.containsAll(List.of(multiword.toString(), single.toString(), duplicate.toString(), "Director")));
        assertFalse(all.contains("Old Owner")); assertFalse(all.contains("Duplicate"));
        assertFalse(all.contains("#20"));
        assertEquals(List.of("Director"), complete("ruin", "camps", "dIr"));
        assertEquals(List.of(multiword.toString()), complete("ruin", "camps", multiword.toString().substring(0, 8)));
        assertContains(run("ruin camps " + multiword), "Camp");
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
        assertEquals(List.of("Archaeologist"), complete("give", "tool", ""));
        assertContains(run("give tool Missing"), "Player not online: Missing");
        staff.getInventory().clear();
        assertContains(run("give tool"), "an excavation tool (stone pickaxe)");
        assertTrue(staff.getInventory().contains(Material.STONE_PICKAXE));
    }

    @Test public void grantsArtifactsWithPersistentRuinAndFindProvenance() {
        Site site = dossier(12, "Test ruin");
        assertContains(run("give artifact coin #12"), "from ruin");
        var stack = staff.getInventory().getItem(0);
        assertNotNull(stack);
        var item = new net.tfminecraft.archaeo.item.RecoveredFindItem(plugin);
        BuriedFind find = site.getFinds().getFirst();
        assertEquals(site.getId(), item.siteIdOf(stack));
        assertEquals(find.getId(), item.findIdOf(stack));
        assertEquals(FindState.RECOVERED, find.getState());
        assertEquals(staff.getUniqueId(), find.getRecoveredBy());
        assertEquals(1, find.getFindNumber());
        assertEquals(1, site.getRecoveredCount());
        assertEquals(SiteStatus.HIDDEN, site.getStatus());
        assertTrue(find.getCells().isEmpty());
        assertTrue(stack.getItemMeta().getLore().stream().anyMatch(line -> line.contains("#Test ruin-1")));
        plugin.sites().loadAll();
        Site reloaded = plugin.sites().findById(site.getId()).orElseThrow();
        assertEquals(1, reloaded.getRecoveredCount());
        assertEquals(find.getId(), reloaded.getFinds().getFirst().getId());
        assertEquals(FindState.RECOVERED, reloaded.getFinds().getFirst().getState());
    }

    @Test public void grantsArtifactsToNamedPlayersAndCompletesTheirNames() {
        Site site = dossier(12, "Test ruin");
        PlayerMock recipient = player("Recipient");
        assertEquals(List.of("#12"), complete("give", "artifact", "coin", "1"));
        assertEquals(List.of("Recipient"), complete("give", "artifact", "coin", "#12", "rec"));
        assertContains(console("give artifact coin #12"), "Console must name a player");
        assertContains(console("give artifact coin #12 Missing"), "Player not online");
        assertTrue(site.getFinds().isEmpty());
        assertContains(console("give artifact coin #12 Recipient"), "to Recipient");
        assertFalse(recipient.getInventory().isEmpty());
        assertEquals(recipient.getUniqueId(), site.getFinds().getFirst().getRecoveredBy());
        assertTrue(recipient.nextMessage().contains("Test ruin"));
        assertTrue(staff.getInventory().isEmpty());
    }

    @Test public void resolvesMixedCaseInputAndKeepsTheConfiguredArtifactId() throws Exception {
        Site site = dossier(12, "Test ruin");
        assertContains(run("give artifact CoIn #12"), "Gave Coin");
        assertEquals("coin", site.getFinds().getFirst().getArtifactId());
        writeData("artifacts.yml", "artifacts:\n  CuStOm_Find:\n    display-name: Custom find\n    item: BRICK\n    strata: [I]\n");
        assertContains(run("reload"), "Reloaded Archaeo");
        assertEquals(List.of("CuStOm_Find"), complete("give", "artifact", "cuST"));
        assertContains(run("give artifact custom_FIND #12"), "Gave Custom find");
        Site reloaded = plugin.sites().findBySerial(12).orElseThrow();
        assertEquals("CuStOm_Find", reloaded.getFinds().getLast().getArtifactId());
        assertEquals(2, reloaded.getRecoveredCount());
        assertContains(run("give artifact unknown #12"), "Unknown artifact");
        assertEquals(2, reloaded.getFinds().size());
    }

    @Test public void deletesOnlyTheSelectedPieceAndReservesItsNumberAfterReload() throws Exception {
        Site site = dossier(12, "Museum dig");
        assertContains(run("give artifact coin #12"), "Gave Coin");
        assertContains(run("give artifact coin #12"), "Gave Coin");
        BuriedFind first = site.getFinds().getFirst();
        BuriedFind removed = site.getFinds().getLast();
        var tags = new net.tfminecraft.archaeo.item.RecoveredFindItem(plugin);
        var old = staff.getInventory().getItem(1).clone();
        PlayerMock visitor = player("Visitor");
        visitor.getEnderChest().setItem(0, old.clone());
        visitor.setItemOnCursor(old.clone());
        assertContains(run("find delete #12 2"), removed.getId().toString());
        assertEquals(2, site.getFinds().size());
        assertContains(run("find delete #12 " + removed.getId() + " confirm"), "Deleted");
        assertEquals(List.of(first), site.getFinds());
        assertEquals(1, site.getRecoveredCount());
        assertTrue(tags.isThisFind(staff.getInventory().getItem(0), first.getId()));
        assertNull(staff.getInventory().getItem(1));
        assertNull(visitor.getEnderChest().getItem(0));
        assertTrue(visitor.getItemOnCursor().getType().isAir());
        var folder = plugin.getDataFolder().toPath().resolve("sites/.deleted-finds");
        try (var files = java.nio.file.Files.list(folder)) {
            var backup = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(files.findFirst().orElseThrow().toFile());
            assertEquals(2, backup.getMapList("finds").size());
        }
        assertContains(run("reload"), "Reloaded Archaeo");
        Site copy = plugin.sites().findBySerial(12).orElseThrow();
        assertEquals(1, copy.getFinds().size());
        assertEquals(1, copy.getRecoveredCount());
        assertContains(run("give artifact coin #12"), "Gave Coin");
        assertEquals(3, copy.getFinds().getLast().getFindNumber());
    }

    @Test public void reportsStoredAndCurrentItemsWithoutChangingLegacyFinds() throws Exception {
        Site site = dossier(12, "Museum dig");
        run("give artifact coin #12");
        BuriedFind find = site.getFinds().getFirst();
        writeData("artifacts.yml", "artifacts:\n  coin:\n    display-name: Coin\n    item: [BRICK, FLOWER_POT]\n    strata: [I]\n");
        assertContains(run("reload"), "Reloaded Archaeo");
        var info = run("find info");
        assertContains(info, "Stored item: GOLD_NUGGET");
        assertContains(info, "Current item(s): BRICK, FLOWER_POT");
        assertContains(info, "outside the current pool");
        assertContains(info, find.getId().toString());
        assertContains(run("find list #12"), "GOLD_NUGGET");
        Site copy = plugin.sites().findBySerial(12).orElseThrow();
        assertEquals("GOLD_NUGGET", copy.getFinds().getFirst().getItem());
        copy.getFinds().getFirst().setItem(null);
        assertContains(run("find info #12 1"), "not recorded (legacy find)");
        assertNull(copy.getFinds().getFirst().getItem());
        copy.getFinds().getFirst().setArtifactId("retired_coin");
        assertContains(run("find info #12 1"), "absent from artifacts.yml");
    }

    @Test public void refusesUnconfirmedInvalidHeldAndBuriedFindDeletion() {
        Site site = dossier(12, "Museum dig");
        run("give artifact coin #12");
        for (String input : List.of("find delete #12 1 typo", "find delete #12 1 confirm extra",
                "find delete #12 missing confirm", "find delete #99 1 confirm")) {
            assertFalse(run(input).isEmpty());
            assertEquals(1, site.getFinds().size());
        }
        site.setOwnershipHeld(true);
        assertContains(run("find delete #12 1 confirm"), "held for ownership review");
        site.setOwnershipHeld(false);
        site.getFinds().getFirst().setState(FindState.HIDDEN);
        assertContains(run("find delete #12 1 confirm"), "Only recovered finds");
        assertEquals(1, site.getFinds().size());
        staff.setOp(false);
        staff.addAttachment(plugin, plugin.catalogs().staffPermission(), false);
        assertContains(run("find delete #12 1 confirm"), "You do not have permission");
        assertTrue(complete("find", "delete", "").isEmpty());
    }

    @Test public void backupFailureLeavesTheArchiveAndPhysicalPieceUntouched() throws Exception {
        Site site = dossier(12, "Museum dig");
        run("give artifact coin #12");
        java.nio.file.Files.writeString(plugin.getDataFolder().toPath().resolve("sites/.deleted-finds"), "blocked");
        assertContains(run("find delete #12 1 confirm"), "Could not back up the find");
        assertEquals(1, site.getFinds().size());
        assertEquals(1, site.getRecoveredCount());
        assertNotNull(staff.getInventory().getItem(0));
        assertTrue(plugin.sites().loadAll());
        assertEquals(1, plugin.sites().findBySerial(12).orElseThrow().getFinds().size());
    }

    @Test public void completesFindSerialsNumbersAndConfirmationAndAllowsConsoleInspection() {
        Site site = dossier(12, "Museum dig");
        run("give artifact coin #12");
        assertEquals(List.of("#12"), complete("find", "list", "1"));
        assertEquals(List.of("1"), complete("find", "delete", "#12", ""));
        assertEquals(List.of("confirm"), complete("find", "delete", "#12", "1", "con"));
        assertContains(console("find info #12 1"), site.getFinds().getFirst().getId().toString());
        assertContains(console("find list #12 99999999999999999"), "Page must be");
        assertContains(console("find info"), "Usage:");
        assertContains(console("find delete #12 1 confirm"), "Deleted");
        assertTrue(site.getFinds().isEmpty());
    }

    @Test public void listsNamedSitesAcrossCharactersAndStatusesForOneAccount() {
        UUID account = staff.getUniqueId(), oldCharacter = UUID.randomUUID(), newCharacter = UUID.randomUUID();
        Site closed = dossier(12, "Old valley dig"); closed.setStatus(SiteStatus.CLOSED);
        closed.getActorRefs().put(oldCharacter, new net.tfminecraft.tlibs.character.CharacterRef(account, oldCharacter));
        closed.setDirector(oldCharacter);
        Site assisted = dossier(4, "Harbour dig"); assisted.setStatus(SiteStatus.EXHAUSTED);
        assisted.getActorRefs().put(newCharacter, new net.tfminecraft.tlibs.character.CharacterRef(account, newCharacter));
        assisted.worker(newCharacter);
        Site unrelated = dossier(2, "Other player's dig");
        unrelated.getActorRefs().put(oldCharacter, new net.tfminecraft.tlibs.character.CharacterRef(account, oldCharacter));
        net.tfminecraft.archaeo.CharacterTestSupport.setDirector(unrelated, UUID.randomUUID());
        var listed = console("ruin list Archaeologist");
        assertContains(listed, "2 total"); assertContains(listed, closed.displayLabel());
        assertContains(listed, assisted.displayLabel()); assertContains(listed, "closed");
        assertFalse(listed.stream().anyMatch(line -> line.contains("Other player's dig")));
        int harbourIndex = java.util.stream.IntStream.range(0, listed.size())
                .filter(i -> listed.get(i).contains(assisted.displayLabel())).findFirst().orElseThrow();
        int valleyIndex = java.util.stream.IntStream.range(0, listed.size())
                .filter(i -> listed.get(i).contains(closed.displayLabel())).findFirst().orElseThrow();
        assertTrue(harbourIndex < valleyIndex);
        assertEquals(SiteStatus.CLOSED, closed.getStatus()); assertEquals(1, assisted.getWorkers().size());
        assertEquals(oldCharacter, closed.getDirector());
    }

    @Test public void paginatesSiteDirectoryAndCompletesPlayersWithPermissionChecks() {
        for (int serial = 1; serial <= 11; serial++) dossier(serial, "Dig " + serial);
        var first = console("ruin list"); assertContains(first, "11 total (page 1/2)");
        assertContains(first, "Next: /archaeo ruin list all 2");
        assertFalse(first.stream().anyMatch(line -> line.contains("Dig 11")));
        var second = console("ruin list all 2"); assertContains(second, "#11"); assertContains(second, "Dig 11");
        assertFalse(second.stream().anyMatch(line -> line.contains("Dig 1 ")));
        assertContains(console("ruin list all 0"), "Page must be");
        assertContains(console("ruin list Nobody"), "Unknown player");
        assertContains(console("ruin list all 1 extra"), "Usage:");
        assertEquals(List.of("Archaeologist"), complete("ruin", "list", "arch"));
        assertTrue(complete("ruin", "list", "a").contains("all"));
        staff.setOp(false); staff.addAttachment(plugin, plugin.catalogs().staffPermission(), false);
        assertContains(run("ruin list"), "You do not have permission");
        assertTrue(complete("ruin", "list", "").isEmpty());
    }

    @Test public void siteDirectorySuggestsTheFindCommandAndFindInfoShowsIdAndName() {
        Site site = dossier(12, "River museum dig");
        var before = staff.getLocation().clone();
        Player.Spigot spigot = mock(Player.Spigot.class); doReturn(spigot).when(staff).spigot();
        List<BaseComponent> choices = new ArrayList<>();
        doAnswer(call -> { choices.add(call.getArgument(0)); return null; })
                .when(spigot).sendMessage(any(BaseComponent.class));
        run("ruin list");
        assertEquals(1, choices.size()); assertTrue(choices.getFirst().toPlainText().contains(site.displayLabel()));
        assertEquals(ClickEvent.Action.SUGGEST_COMMAND, choices.getFirst().getClickEvent().getAction());
        assertEquals("/archaeo find list #12", choices.getFirst().getClickEvent().getValue());
        assertEquals(before, staff.getLocation());
        run("give artifact coin #12");
        assertContains(run("find info"), "ruin " + site.displayLabel());
        assertContains(run("find info #12 1"), "ruin " + site.displayLabel());
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
