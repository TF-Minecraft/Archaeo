package net.tfminecraft.archaeo.excavation;

import net.tfminecraft.archaeo.config.*;
import net.md_5.bungee.api.chat.BaseComponent;
import net.tfminecraft.archaeo.item.ItemRef;
import net.tfminecraft.archaeo.model.*;
import net.tfminecraft.archaeo.site.SiteRepository;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.attribute.AttributeInstanceMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class HandPickServiceTest {
    private ServerMock server;
    private SnapshotWorld world;
    private PlayerMock player;
    private SiteRepository sites;
    private CatalogRegistry catalogs;
    private HandPickService service;
    private Site site;
    private Block target;
    private ExcavationTool profile;
    /** Second whitelisted profile, served for a diamond pick when a test configures it. */
    private ExcavationTool survey;
    private final List<String> subtitles = new ArrayList<>();
    private final List<String> actionBars = new ArrayList<>();

    @Before
    public void setup() {
        server = MockBukkit.mock();
        world = new SnapshotWorld();
        world.setName("pick");
        server.addWorld(world);
        JavaPlugin plugin = MockBukkit.createMockPlugin();
        player = spy(new PlayerMock(server, "Digger"));
        server.addPlayer(player);
        // This newer player attribute is absent from MockBukkit's default player attributes.
        doReturn(new AttributeInstanceMock(Attribute.BLOCK_BREAK_SPEED, 1.0))
                .when(player).getAttribute(Attribute.BLOCK_BREAK_SPEED);
        player.teleport(new Location(world, 8, 42, 8));
        player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_PICKAXE));
        doAnswer(invocation -> target).when(player).getTargetBlockExact(6);
        doReturn(actionBarSink(actionBars)).when(player).spigot();
        doReturn(List.of()).when(player).getLastTwoTargetBlocks(null, 6);
        doAnswer(invocation -> {
            subtitles.add(invocation.getArgument(1));
            return null;
        }).when(player).sendTitle(anyString(), anyString(), anyInt(), anyInt(), anyInt());
        doNothing().when(player).spawnParticle(any(Particle.class), any(Location.class), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), any());
        doNothing().when(player).spawnParticle(any(Particle.class), any(Location.class), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble());
        site = new Site();
        site.setId(UUID.randomUUID());
        site.setStatus(SiteStatus.ESTABLISHED);
        site.setWorldName(world.getName());
        site.setDirector(player.getUniqueId());
        StratumBand band = new StratumBand();
        band.setId("I");
        band.setPresent(true);
        band.setMinY(38);
        band.setMaxY(42);
        site.getStrata().put("I", band);
        target = stone(8, 40, 8);
        stone(8, 39, 8);
        sites = mock(SiteRepository.class);
        when(sites.findEstablishedPrism(anyString(), anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            boolean inside = site.getStatus() == SiteStatus.ESTABLISHED
                    && world.getName().equals(invocation.getArgument(0))
                    && site.isInPrism(invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3));
            return inside ? Optional.of(site) : Optional.empty();
        });
        catalogs = mock(CatalogRegistry.class);
        when(catalogs.toolWear()).thenReturn(new ToolWearSettings(0, 0, false));
        profile = profile(1, 0, 20);
        DigTools tools = mock(DigTools.class);
        when(tools.match(any())).thenAnswer(invocation -> Optional.ofNullable(held(invocation.getArgument(0))));
        when(tools.isAllowed(any())).thenAnswer(invocation -> held(invocation.getArgument(0)) != null);
        service = new HandPickService(plugin, sites, catalogs, tools, settings(true, 10));
        service.start();
    }

    @After
    public void teardown() {
        if (service != null) service.stop();
        MockBukkit.unmock();
    }

    @Test
    public void invalidToolsDisabledSettingsAndNonFillNeverStartCycles() {
        service.setSettings(settings(false, 10));
        service.noteMining(player, target);
        assertFalse(service.isCycling(player));
        service.setSettings(settings(true, 10));
        player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
        service.noteMining(player, target);
        assertFalse(service.isCycling(player));
        player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_PICKAXE));
        for (Material type : List.of(Material.AIR, Material.WATER, Material.TORCH)) {
            target.setType(type);
            service.noteMining(player, target);
            assertFalse(service.isCycling(player));
        }
        service.noteMining(player, stone(16, 40, 8));
        assertFalse(service.isCycling(player));
        verify(sites, never()).save(any());
    }

    @Test
    public void permissionsAndExhaustedWorkdayRejectStartWithoutRemovingTerrain() {
        site.setDirector(UUID.randomUUID());
        service.noteMining(player, target);
        service.noteMining(player, target);
        assertFalse(service.isCycling(player));
        verify(player, times(1)).sendMessage("You are not authorised to work on this excavation.");
        site.setDirector(player.getUniqueId());
        service.ensureJornada(site, world);
        site.setJornadaPickLeft(1);
        service.noteMining(player, target);
        assertFalse(service.isCycling(player));
        assertEquals(Material.STONE, target.getType());
        assertEquals(1, site.getJornadaPickLeft());
    }

    @Test
    public void earlyReleasePreservesTerrainWorkdayAndWorkerCounters() {
        service.noteMining(player, target);
        assertTrue(service.isCycling(player));
        service.finish(player);
        service.finish(player);
        assertFalse(service.isCycling(player));
        assertEquals(Material.STONE, target.getType());
        assertEquals(10, site.getJornadaPickLeft());
        assertNull(site.workerRecord(player.getUniqueId()));
        verify(sites, never()).save(any());
    }

    @Test
    public void readyReleaseLiftsOnlyAimedCellAndSpendsOneCycleCost() {
        awaitReady();
        assertEquals(Material.STONE, target.getType());
        service.finish(player);
        assertEquals(Material.AIR, target.getType());
        assertEquals(Material.STONE, world.getBlockAt(8, 39, 8).getType());
        assertFalse(service.isCycling(player));
        assertEquals(8, site.getJornadaPickLeft());
        assertEquals(1, site.staffLog(player.getUniqueId()).getBlocksRemoved());
        verify(sites).save(site);
        service.finish(player);
        assertEquals(8, site.getJornadaPickLeft());
    }

    @Test
    public void holdingThroughNextBeatLiftsExtraCellWithoutDoubleSpending() {
        awaitReady();
        keepHoldingUntilResolved(8);
        assertEquals(Material.AIR, target.getType());
        assertEquals(Material.AIR, world.getBlockAt(8, 39, 8).getType());
        assertEquals(8, site.getJornadaPickLeft());
        assertEquals(2, site.staffLog(player.getUniqueId()).getBlocksRemoved());
        ticks(3);
        service.finish(player);
        verify(sites).save(site);
    }

    @Test
    public void missingReadyWindowTriggersLateCutBeforeNextSlowCue() {
        profile = profile(5, 0, 1);
        service.setSettings(settings(true, 10));
        awaitReady();
        keepHoldingUntilResolved(4);
        assertEquals(Material.AIR, world.getBlockAt(8, 39, 8).getType());
        assertEquals(2, site.staffLog(player.getUniqueId()).getBlocksRemoved());
        ticks(3);
        verify(player).playSound(any(Location.class), eq(Sound.ITEM_MACE_SMASH_GROUND), any(SoundCategory.class), anyFloat(), anyFloat());
    }

    @Test
    public void lateCutWithNothingElseToBreakLiftsOneCubeWithoutTheHeavySmash() {
        profile = profile(5, 0, 1);
        service.setSettings(settings(true, 10));
        world.getBlockAt(8, 39, 8).setType(Material.AIR); // Already dug out below the aimed cube.
        awaitReady();
        keepHoldingUntilResolved(4);
        ticks(3);
        assertEquals(Material.AIR, target.getType());
        assertEquals(1, site.staffLog(player.getUniqueId()).getBlocksRemoved());
        verify(player, never()).playSound(any(Location.class), eq(Sound.ITEM_MACE_SMASH_GROUND), any(SoundCategory.class), anyFloat(), anyFloat());
    }

    @Test
    public void stalePacketsReleaseReadySlowCycleWithoutInventingLateInput() {
        profile = profile(10, 0, 3);
        service.setSettings(settings(true, 10));
        awaitReady();
        ticks(17);
        assertFalse(service.isCycling(player));
        assertEquals(Material.AIR, target.getType());
        assertEquals(Material.STONE, world.getBlockAt(8, 39, 8).getType());
        assertEquals(1, site.staffLog(player.getUniqueId()).getBlocksRemoved());
    }

    @Test
    public void earlyStaleCycleStopsWithoutDigging() {
        profile = profile(10, 0, 3);
        service.setSettings(settings(true, 10));
        service.noteMining(player, target);
        ticks(17);
        assertFalse(service.isCycling(player));
        assertEquals(Material.STONE, target.getType());
        assertEquals(10, site.getJornadaPickLeft());
    }

    @Test
    public void changingTargetFinishesReadyCellThenStartsNewCycle() {
        awaitReady();
        Block previous = target;
        target = stone(9, 40, 8);
        service.noteMining(player, target);
        assertEquals(Material.AIR, previous.getType());
        assertEquals(Material.STONE, target.getType());
        assertTrue(service.isCycling(player));
        service.finish(player);
        assertEquals(Material.STONE, target.getType());
        assertEquals(8, site.getJornadaPickLeft());
    }

    @Test
    public void toolChangeAndPermissionRevocationEndCyclesSafely() {
        service.noteMining(player, target);
        player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
        ticks(1);
        assertFalse(service.isCycling(player));
        assertEquals(Material.STONE, target.getType());
        player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_PICKAXE));
        awaitReady();
        site.setDirector(UUID.randomUUID());
        ticks(1);
        assertFalse(service.isCycling(player));
        assertEquals(Material.STONE, target.getType());
        assertEquals(10, site.getJornadaPickLeft());
    }

    @Test
    public void worldChangeOrSpentBudgetBeforeReleaseCannotSpendOrDigAgain() {
        awaitReady();
        site.setJornadaPickLeft(0);
        service.finish(player);
        assertEquals(Material.STONE, target.getType());
        assertEquals(0, site.getJornadaPickLeft());
        service.refillJornada(site, world);
        subtitles.clear();
        awaitReady();
        target.setType(Material.AIR);
        service.finish(player);
        assertEquals(Material.STONE, world.getBlockAt(8, 39, 8).getType());
        assertEquals(10, site.getJornadaPickLeft());
    }

    @Test
    public void lateFindDamageCountsPiecesOnceAndArchivesLoss() {
        BuriedFind find = new BuriedFind();
        find.setId(UUID.randomUUID());
        find.getCells().addAll(List.of(new BlockCell(8, 40, 8), new BlockCell(8, 39, 8), new BlockCell(9, 39, 8)));
        site.getFinds().add(find);
        stone(9, 39, 8);
        awaitReady();
        keepHoldingUntilResolved(8);
        assertEquals(FindState.LOST, find.getState());
        assertEquals(0, find.getConservation());
        assertEquals(1, site.staffLog(player.getUniqueId()).getFindsDamaged());
        assertEquals(1, site.staffLog(player.getUniqueId()).getFindsLost());
        assertEquals(2, site.staffLog(player.getUniqueId()).getBlocksRemoved());
        assertEquals(SiteStatus.EXHAUSTED, site.getStatus());
        assertEquals(player.getUniqueId(), find.getRecoveredBy());
        assertTrue(find.getFindNumber() > 0);
        verify(player).sendMessage("Buried archaeological remains were destroyed.");
    }

    @Test
    public void workdayRefillsOnlyOnNewDayOrExplicitStaffRefill() {
        world.setFullTime(24000);
        service.ensureJornada(site, world);
        assertEquals(1, site.getJornadaWorldDay());
        assertEquals(10, site.getJornadaPickLeft());
        site.setJornadaPickLeft(3);
        service.ensureJornada(site, world);
        assertEquals(3, site.getJornadaPickLeft());
        world.setFullTime(48000);
        service.ensureJornada(site, world);
        assertEquals(10, site.getJornadaPickLeft());
        assertEquals(2, site.getJornadaWorldDay());
        site.setJornadaPickLeft(1);
        assertEquals(10, service.refillJornada(site, world));
        verify(sites, times(2)).touch(site);
        verify(sites).save(site);
    }

    @Test
    public void unlimitedWorkdayDoesNotRewriteOrConsumeStoredDailyBudget() {
        service.setSettings(settings(true, 0));
        site.setJornadaPickLeft(7);
        site.setJornadaWorldDay(12);
        assertEquals(0, service.refillJornada(site, world));
        service.ensureJornada(site, world);
        awaitReady();
        service.finish(player);
        assertEquals(Material.AIR, target.getType());
        assertEquals(7, site.getJornadaPickLeft());
        assertEquals(12, site.getJornadaWorldDay());
        verify(sites, never()).touch(any());
    }

    @Test
    public void miningLockIsIdempotentScopedToCutAndPreservesOtherModifiers() {
        var speed = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        AttributeModifier unrelated = new AttributeModifier(new NamespacedKey("other", "boost"), 0.5,
                AttributeModifier.Operation.ADD_NUMBER);
        speed.addModifier(unrelated);
        service.syncHeldTool(player);
        service.syncHeldTool(player);
        assertEquals(2, speed.getModifiers().size());
        assertTrue(speed.getModifiers().stream().anyMatch(modifier -> modifier.getKey().getKey().equals("no_vanilla_mine")));
        target = null;
        service.syncHeldTool(player);
        assertEquals(List.of(unrelated), List.copyOf(speed.getModifiers()));
        target = world.getBlockAt(8, 40, 8);
        service.syncHeldTool(player);
        service.stop();
        assertEquals(List.of(unrelated), List.copyOf(speed.getModifiers()));
    }

    @Test
    public void predictedVanillaBreakPinsRealBlockAndStartsClockWithoutBreakingIt() {
        service.suppressVanillaBreak(player, target);
        verify(player).sendBlockChange(target.getLocation(), target.getBlockData());
        assertTrue(service.isCycling(player));
        assertEquals(Material.STONE, target.getType());
        assertEquals(10, site.getJornadaPickLeft());
    }

    @Test
    public void legacyVanillaProgressAndFallbackBothReachReadyWithoutChangingTerrainEarly() {
        profile = profile(0, 0, 20);
        world.breakProgress = 1f;
        service.setSettings(settings(true, 10));
        awaitReady();
        assertEquals(Material.STONE, target.getType());
        service.finish(player);
        assertEquals(Material.AIR, target.getType());
        target.setType(Material.STONE);
        subtitles.clear();
        world.breakProgress = 0f;
        profile = profile(0, 1, 20);
        service.setSettings(settings(true, 10));
        awaitReady();
        service.finish(player);
        assertEquals(Material.AIR, target.getType());
        assertEquals(6, site.getJornadaPickLeft());
    }

    @Test
    public void zeroLegacyProgressWithoutFallbackCannotGenerateAReadyCue() {
        profile = profile(0, 0, 20);
        world.breakProgress = 0f;
        service.setSettings(settings(true, 10));
        service.noteMining(player, target);
        for (int tick = 0; tick < 12; tick++) {
            service.noteMining(player, target);
            ticks(1);
        }
        assertTrue(subtitles.isEmpty());
        service.finish(player);
        assertEquals(Material.STONE, target.getType());
        assertEquals(10, site.getJornadaPickLeft());
    }

    @Test
    public void openedCellHudCountsHiddenConnectedShapeAndThenShowsClear() {
        service.setSettings(settings(true, 10, true));
        BuriedFind nearby = nearbyPot(8, 40, 7);
        Block opened = target;
        opened.setType(Material.AIR);
        target = stone(8, 40, 9);
        doReturn(List.of(opened, target)).when(player).getLastTwoTargetBlocks(null, 6);
        ticks(1);
        assertEquals("STRATUM I  ⛏ 10  Ceramic: 1", actionBars.getLast());
        assertEquals(FindState.HIDDEN, nearby.getState());
        nearby.setState(FindState.LOST);
        ticks(1);
        assertEquals("STRATUM I  ⛏ 10  clear", actionBars.getLast());
        service.setSettings(settings(true, 10, false));
        ticks(1);
        assertEquals("STRATUM I  ⛏ 10", actionBars.getLast());
    }

    @Test
    public void wallHudUsesAirImmediatelyInFrontOnlyInsideTheExcavation() {
        service.setSettings(settings(true, 10, true));
        Block opened = world.getBlockAt(8, 40, 7);
        nearbyPot(8, 40, 6);
        doReturn(List.of(opened, target)).when(player).getLastTwoTargetBlocks(null, 6);
        ticks(1);
        assertEquals("STRATUM I  ⛏ 10  Ceramic: 1", actionBars.getLast());
        opened.setType(Material.WATER);
        ticks(1);
        assertEquals("STRATUM I  ⛏ 10", actionBars.getLast());
        target = stone(0, 40, 8);
        doReturn(List.of(world.getBlockAt(-1, 40, 8), target)).when(player).getLastTwoTargetBlocks(null, 6);
        ticks(1);
        assertEquals("STRATUM I  ⛏ 10", actionBars.getLast());
    }

    @Test
    public void activeHoldDoesNotRevealTracesUntilItsCellIsOpened() {
        service.setSettings(settings(true, 10, true));
        nearbyPot(8, 40, 7);
        doReturn(List.of(world.getBlockAt(8, 40, 9), target)).when(player).getLastTwoTargetBlocks(null, 6);
        awaitReady();
        assertEquals("STRATUM I  ⛏ 10", actionBars.getLast());
        service.finish(player);
        verify(player).sendMessage("Traces of Ceramic: 1");
        Block opened = target;
        target = stone(8, 40, 9);
        doReturn(List.of(opened, target)).when(player).getLastTwoTargetBlocks(null, 6);
        ticks(1);
        assertEquals("STRATUM I  ⛏ 8  Ceramic: 1", actionBars.getLast());
        assertEquals(Material.AIR, opened.getType());
    }

    @Test
    public void discoveredFindHudShowsConditionAndUnlimitedBudgetThenPermissionRefusal() {
        BuriedFind aimed = nearbyPot(8, 40, 8);
        aimed.setState(FindState.DISCOVERED);
        aimed.setConservation(75);
        service.setSettings(settings(true, 0, true));
        ticks(1);
        assertEquals("STRATUM I  ⛏ ∞  75%", actionBars.getLast());
        site.setDirector(UUID.randomUUID());
        ticks(1);
        assertEquals("Not authorised to work here", actionBars.getLast());
        assertEquals(Material.STONE, target.getType());
    }

    @Test
    public void competingWorkersCannotOverspendTheLastSharedWorkdayAction() {
        service.setSettings(settings(true, 2));
        world.getBlockAt(8, 39, 8).setType(Material.AIR);
        Block secondCell = stone(10, 40, 8);
        List<String> secondCues = new ArrayList<>();
        PlayerMock second = worker("Second digger", secondCell, secondCues);
        boolean firstReady = false;
        boolean secondReady = false;
        for (int tick = 0; tick < 30 && !(firstReady && secondReady); tick++) {
            service.noteMining(player, target);
            service.noteMining(second, secondCell);
            ticks(1);
            firstReady = subtitles.contains("Release");
            secondReady = secondCues.contains("Release");
        }
        assertTrue(firstReady && secondReady);
        service.finish(player);
        service.finish(second);
        boolean firstSucceeded = target.getType() == Material.AIR;
        boolean secondSucceeded = secondCell.getType() == Material.AIR;
        assertTrue("Exactly one worker may spend the last shared action", firstSucceeded ^ secondSucceeded);
        PlayerMock winner = firstSucceeded ? player : second;
        PlayerMock waiting = firstSucceeded ? second : player;
        assertEquals(Material.STONE, firstSucceeded ? secondCell.getType() : target.getType());
        assertEquals(0, site.getJornadaPickLeft());
        assertEquals(1, site.staffLog(winner.getUniqueId()).getBlocksRemoved());
        assertNull(site.workerRecord(waiting.getUniqueId()));
        verify(waiting).sendMessage("The excavation work day is over.");
        verify(sites).save(site);
        assertFalse(service.isCycling(player));
        assertFalse(service.isCycling(second));
    }

    @Test
    public void stoppingDuringEarlyHoldsUnlocksEveryWorkerAndCancelsFutureCues() {
        profile = profile(20, 0, 20);
        Block secondCell = stone(10, 40, 8);
        List<String> secondCues = new ArrayList<>();
        PlayerMock second = worker("Second digger", secondCell, secondCues);
        service.noteMining(player, target);
        service.noteMining(second, secondCell);
        ticks(1);
        assertFalse(player.getAttribute(Attribute.BLOCK_BREAK_SPEED).getModifiers().isEmpty());
        assertFalse(second.getAttribute(Attribute.BLOCK_BREAK_SPEED).getModifiers().isEmpty());
        service.stop();
        assertFalse(service.isCycling(player));
        assertFalse(service.isCycling(second));
        assertTrue(player.getAttribute(Attribute.BLOCK_BREAK_SPEED).getModifiers().isEmpty());
        assertTrue(second.getAttribute(Attribute.BLOCK_BREAK_SPEED).getModifiers().isEmpty());
        int shown = subtitles.size() + secondCues.size();
        ticks(100);
        assertEquals(shown, subtitles.size() + secondCues.size());
        assertEquals(Material.STONE, target.getType());
        assertEquals(Material.STONE, secondCell.getType());
        assertEquals(10, site.getJornadaPickLeft());
        verify(sites, never()).save(any());
    }

    @Test
    public void anotherWorkersCutEndsTheActiveHoldWithoutChargingAgain() {
        awaitReady();
        // A second worker's allowed cut removes the cell before this player's next tick.
        target.breakNaturally(new ItemStack(Material.IRON_PICKAXE));
        ticks(1);
        assertFalse(service.isCycling(player));
        assertEquals(10, site.getJornadaPickLeft());
        assertNull(site.workerRecord(player.getUniqueId()));
        assertEquals(Material.STONE, world.getBlockAt(8, 39, 8).getType());
        assertTrue(player.getAttribute(Attribute.BLOCK_BREAK_SPEED).getModifiers().isEmpty());
        service.finish(player);
        verify(sites, never()).save(any());
    }

    @Test
    public void audioOnlyCuesKeepReleaseTimingWithoutTitlesOrParticles() {
        PickSettings normal = settings(true, 10);
        service.setSettings(new PickSettings(normal.enabled(), normal.jornadaActions(), false,
                normal.findDust(), normal.findDustIntervalTicks(), normal.findDustCount(), normal.conservation(),
                normal.limits(), normal.neighborTraces(), normal.cues(), normal.profiles()));
        boolean heardReady = false;
        for (int tick = 0; tick < 30 && !heardReady; tick++) {
            service.noteMining(player, target);
            ticks(1);
            heardReady = player.getHeardSounds().stream().anyMatch(sound ->
                    sound.getSound().equals(Sound.BLOCK_NOTE_BLOCK_CHIME.getKey().getKey())
                            && sound.getPitch() == 1.45f);
        }
        assertTrue("The audio cue still tells the player when to release", heardReady);
        assertTrue(player.getHeardSounds().stream().anyMatch(sound ->
                sound.getSound().equals(Sound.BLOCK_NOTE_BLOCK_CHIME.getKey().getKey())
                        && sound.getCategory() == SoundCategory.BLOCKS && sound.getPitch() == 0.85f));
        assertTrue(subtitles.isEmpty());
        verify(player, never()).spawnParticle(any(Particle.class), any(Location.class), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), any());
        verify(player, never()).spawnParticle(any(Particle.class), any(Location.class), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble());
        assertEquals(Material.STONE, target.getType());
        service.finish(player);
        assertEquals(Material.AIR, target.getType());
        assertEquals(Material.STONE, world.getBlockAt(8, 39, 8).getType());
        assertEquals(8, site.getJornadaPickLeft());
    }

    @Test
    public void highVanillaProgressResolvesMultipleCuesInOneTickButSpendsOnlyOneCut() {
        profile = profile(0, 0, 20);
        world.breakProgress = 20f;
        service.setSettings(settings(true, 10));
        service.noteMining(player, target);
        ticks(1);
        assertTrue(subtitles.contains("Soon"));
        assertEquals(1, subtitles.stream().filter("Release"::equals).count());
        assertFalse(service.isCycling(player));
        assertEquals(Material.AIR, target.getType());
        assertEquals(Material.AIR, world.getBlockAt(8, 39, 8).getType());
        assertEquals(8, site.getJornadaPickLeft());
        assertEquals(2, site.staffLog(player.getUniqueId()).getBlocksRemoved());
        service.finish(player);
        ticks(5);
        assertEquals(8, site.getJornadaPickLeft());
        verify(sites).save(site);
    }

    @Test
    public void actualDamageListenerStartsOnlyWhitelistedToolCycles() {
        server.getPluginManager().registerEvents(new HandPickListener(service, sites), MockBukkit.createMockPlugin());
        BlockDamageEvent allowed = new BlockDamageEvent(player, target,
                player.getInventory().getItemInMainHand(), true);
        server.getPluginManager().callEvent(allowed);
        assertFalse(allowed.getInstaBreak());
        assertTrue(service.isCycling(player));
        assertFalse(player.getAttribute(Attribute.BLOCK_BREAK_SPEED).getModifiers().isEmpty());
        service.finish(player);
        player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
        BlockDamageEvent unrelated = new BlockDamageEvent(player, target,
                player.getInventory().getItemInMainHand(), true);
        server.getPluginManager().callEvent(unrelated);
        assertTrue(unrelated.getInstaBreak());
        assertFalse(service.isCycling(player));
        ticks(1);
        assertTrue(player.getAttribute(Attribute.BLOCK_BREAK_SPEED).getModifiers().isEmpty());
        assertEquals(Material.STONE, target.getType());
        assertEquals(10, site.getJornadaPickLeft());
        verify(sites, never()).save(any());
    }

    @Test
    public void releaseAfterTheReadyWindowLiftsTheLateCutEvenBeforeTheNextTickNoticesIt() {
        profile = profile(5, 0, 1);
        service.setSettings(settings(true, 10));
        awaitReady();
        for (int tick = 0; tick < 2; tick++) {
            service.noteMining(player, target);
            ticks(1);
        }
        assertTrue("Still inside the ready window after two ticks", service.isCycling(player));
        // The next packet lands past the window; the hotbar release arrives before any tick runs.
        service.noteMining(player, target);
        service.finish(player);
        assertEquals(Material.AIR, target.getType());
        assertEquals(Material.AIR, world.getBlockAt(8, 39, 8).getType());
        assertEquals(2, site.staffLog(player.getUniqueId()).getBlocksRemoved());
        assertEquals(8, site.getJornadaPickLeft());
    }

    @Test
    public void swappingToAnotherToolProfileMidHoldRestartsTheHoldWithThatProfile() {
        survey = shaped("survey", Material.DIAMOND_PICKAXE, BreakShape.DOWN, 2, 2, 1);
        service.noteMining(player, target);
        // An inventory click swaps the stack without a hotbar event; the next tick ends the hold.
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_PICKAXE));
        ticks(1);
        assertFalse(service.isCycling(player));
        assertEquals(Material.STONE, target.getType());
        player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_PICKAXE));
        service.noteMining(player, target);
        // Swap and dig packet in the same tick: the same cube restarts under the new profile.
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_PICKAXE));
        service.noteMining(player, target);
        assertTrue(service.isCycling(player));
        assertEquals(Material.STONE, target.getType());
        assertEquals(10, site.getJornadaPickLeft());
        awaitReady();
        service.finish(player);
        assertEquals("The survey profile lifts two cubes on time", Material.AIR, world.getBlockAt(8, 39, 8).getType());
        assertEquals(Material.AIR, target.getType());
        assertEquals("and costs one action", 9, site.getJornadaPickLeft());
    }

    @Test
    public void closingTheSiteMidHoldEndsItWithoutLiftingAndStopsItsHud() {
        profile = profile(20, 0, 20);
        service.setSettings(settings(true, 10));
        service.noteMining(player, target);
        ticks(10);
        assertTrue("An idle hold survives until the stale window", service.isCycling(player));
        int shown = actionBars.size();
        assertTrue(shown > 0);
        site.setStatus(SiteStatus.EXHAUSTED);
        ticks(1);
        assertEquals("A closed site no longer answers the held cube's HUD", shown, actionBars.size());
        ticks(10);
        assertFalse(service.isCycling(player));
        site.setStatus(SiteStatus.ESTABLISHED);
        service.noteMining(player, target);
        site.setStatus(SiteStatus.EXHAUSTED);
        ticks(1);
        assertFalse("An active hold on a closed site ends at once", service.isCycling(player));
        assertEquals(Material.STONE, target.getType());
        assertEquals(10, site.getJornadaPickLeft());
        verify(sites, never()).save(any());
    }

    @Test
    public void lookingAtTheSkyWithThePickShowsNoHudAndLeavesMiningUnlocked() {
        target = null;
        ticks(3);
        assertTrue(actionBars.isEmpty());
        assertTrue(player.getAttribute(Attribute.BLOCK_BREAK_SPEED).getModifiers().isEmpty());
    }

    @Test
    public void revokedPermitBeforeReleaseLiftsNothingAndSaysWhy() {
        awaitReady();
        site.setDirector(UUID.randomUUID());
        service.finish(player);
        assertEquals(Material.STONE, target.getType());
        assertEquals(10, site.getJornadaPickLeft());
        assertNull(site.workerRecord(player.getUniqueId()));
        verify(player).sendMessage("You are not authorised to work on this excavation.");
        verify(sites, never()).save(any());
    }

    @Test
    public void lateAroundLiftHitsOnlyTheAimedFindCubeAndGrazesTheRest() {
        profile = shaped("test", Material.IRON_PICKAXE, BreakShape.AROUND, 1, 3, 2);
        service.setSettings(settings(true, 10));
        BuriedFind find = new BuriedFind();
        find.setId(UUID.randomUUID());
        BlockCell aimed = new BlockCell(8, 40, 8), south = new BlockCell(8, 40, 9), east = new BlockCell(9, 40, 8);
        find.getCells().addAll(List.of(aimed, south, east, new BlockCell(9, 40, 9)));
        site.getFinds().add(find);
        stone(8, 40, 9); stone(9, 40, 8); stone(9, 40, 9);
        awaitReady();
        keepHoldingUntilResolved(8);
        assertEquals(Material.AIR, world.getBlockAt(8, 40, 9).getType());
        assertEquals(Material.AIR, world.getBlockAt(9, 40, 8).getType());
        assertEquals(Material.STONE, world.getBlockAt(9, 40, 9).getType());
        assertEquals(Set.of(aimed), find.getDirectHitCells());
        assertEquals(Set.of(south, east), find.getGrazedCells());
        assertEquals(3, site.staffLog(player.getUniqueId()).getBlocksRemoved());
        assertEquals(1, site.staffLog(player.getUniqueId()).getFindsDamaged());
    }

    @Test
    public void cutWithNoFindAroundItSendsNoTraceLine() {
        service.setSettings(settings(true, 10, true));
        awaitReady();
        service.finish(player);
        assertEquals(Material.AIR, target.getType());
        verify(player, never()).sendMessage(startsWith("Traces of"));
        verify(player, never()).sendMessage("Buried archaeological remains were destroyed.");
    }

    @Test
    public void cubeRefilledWhereAPieceWasLiftedShowsTracesInsteadOfTheOldCondition() {
        service.setSettings(settings(true, 10, true));
        BuriedFind lifted = nearbyPot(8, 40, 8);
        lifted.setState(FindState.RECOVERED);
        lifted.setConservation(80);
        nearbyPot(8, 40, 6);
        doReturn(List.of(world.getBlockAt(8, 40, 7), target)).when(player).getLastTwoTargetBlocks(null, 6);
        ticks(1);
        assertEquals("STRATUM I  ⛏ 10  Ceramic: 1", actionBars.getLast());
    }

    @Test
    public void headUnderWaterShowsNoTraceFragmentForTheWallAhead() {
        service.setSettings(settings(true, 10, true));
        nearbyPot(8, 40, 6);
        Block eye = world.getBlockAt(8, 40, 7);
        eye.setType(Material.WATER);
        // Only air is see-through for the line of sight, so it stops in the water at eye level.
        doReturn(List.of(eye)).when(player).getLastTwoTargetBlocks(null, 6);
        ticks(1);
        assertEquals("STRATUM I  ⛏ 10", actionBars.getLast());
    }

    @Test
    public void torchStandingInTheCutCountsAsAnOpenedCubeForTraces() {
        service.setSettings(settings(true, 10, true));
        nearbyPot(8, 40, 7);
        target = world.getBlockAt(8, 41, 7);
        target.setType(Material.TORCH);
        ticks(1);
        assertEquals("STRATUM I  ⛏ 10  Ceramic: 1", actionBars.getLast());
        assertFalse(service.isCycling(player));
    }

    @Test
    public void movingTheHoldToTheCubeAboveOrBesideReleasesTheReadyCube() {
        Block above = stone(8, 41, 8), beside = stone(8, 41, 9);
        for (Block[] step : new Block[][] {{target, above}, {above, beside}}) {
            target = step[0];
            subtitles.clear();
            awaitReady();
            target = step[1];
            service.noteMining(player, target);
            assertEquals(Material.AIR, step[0].getType());
            assertEquals(Material.STONE, step[1].getType());
            assertTrue(service.isCycling(player));
        }
        service.finish(player);
        assertEquals(Material.STONE, beside.getType());
        assertEquals(6, site.getJornadaPickLeft());
    }

    @Test
    public void refusalRepeatsOnlyAfterTheWarningCooldown() {
        java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong(1_000_000); service.clock = now::get;
        site.setDirector(UUID.randomUUID());
        service.noteMining(player, target);
        now.addAndGet(2999); service.noteMining(player, target);
        verify(player, times(1)).sendMessage("You are not authorised to work on this excavation.");
        now.addAndGet(1);
        service.noteMining(player, target);
        verify(player, times(2)).sendMessage("You are not authorised to work on this excavation.");
        assertFalse(service.isCycling(player));
    }

    private ExcavationTool held(ItemStack stack) {
        if (stack == null) return null;
        if (stack.getType() == Material.IRON_PICKAXE) return profile;
        return stack.getType() == Material.DIAMOND_PICKAXE ? survey : null;
    }

    private BuriedFind nearbyPot(int x, int y, int z) {
        BuriedFind nearby = new BuriedFind();
        nearby.setId(UUID.randomUUID());
        nearby.setArtifactId("pot");
        nearby.getCells().add(new BlockCell(x, y, z));
        stone(x, y, z);
        site.getFinds().add(nearby);
        when(catalogs.artifact("pot")).thenReturn(new ArtifactTemplate("pot", "Pot", 1, 1, "ceramic", null,
                false, 1, Set.of(), Set.of(), null, List.of(), null));
        when(catalogs.materialDisplayName("ceramic")).thenReturn("Ceramic");
        return nearby;
    }

    private PlayerMock worker(String name, Block aimed, List<String> cues) {
        PlayerMock worker = spy(new PlayerMock(server, name));
        server.addPlayer(worker);
        worker.teleport(new Location(world, aimed.getX(), 42, aimed.getZ()));
        worker.getInventory().setItemInMainHand(new ItemStack(Material.IRON_PICKAXE));
        doReturn(new AttributeInstanceMock(Attribute.BLOCK_BREAK_SPEED, 1.0))
                .when(worker).getAttribute(Attribute.BLOCK_BREAK_SPEED);
        doReturn(aimed).when(worker).getTargetBlockExact(6);
        doReturn(mock(Player.Spigot.class)).when(worker).spigot();
        doAnswer(invocation -> { cues.add(invocation.getArgument(1)); return null; })
                .when(worker).sendTitle(anyString(), anyString(), anyInt(), anyInt(), anyInt());
        doNothing().when(worker).spawnParticle(any(Particle.class), any(Location.class), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), any());
        doNothing().when(worker).spawnParticle(any(Particle.class), any(Location.class), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble());
        site.getExcavators().add(worker.getUniqueId());
        return worker;
    }

    private static Player.Spigot actionBarSink(List<String> messages) {
        return mock(Player.Spigot.class, invocation -> {
            if (invocation.getMethod().getName().equals("sendMessage")) {
                for (Object argument : invocation.getArguments()) {
                    if (argument instanceof BaseComponent component) messages.add(component.toPlainText());
                    if (argument instanceof BaseComponent[] components) messages.add(BaseComponent.toPlainText(components));
                }
            }
            return null;
        });
    }

    private void awaitReady() {
        service.noteMining(player, target);
        for (int tick = 0; tick < 100 && !subtitles.contains("Release"); tick++) {
            service.noteMining(player, target);
            ticks(1);
        }
        assertTrue("A complete hold must emit the ready subtitle", subtitles.contains("Release"));
        assertTrue(service.isCycling(player));
    }

    private void keepHoldingUntilResolved(int bound) {
        for (int tick = 0; tick < bound && service.isCycling(player); tick++) {
            service.noteMining(player, target);
            ticks(1);
        }
        assertFalse("A continued hold must resolve after ready", service.isCycling(player));
    }

    private void ticks(int count) {
        server.getScheduler().performTicks(count);
    }

    private Block stone(int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.STONE);
        return block;
    }

    private ExcavationTool profile(int cueTicks, int fallback, int readyWindow) {
        return new ExcavationTool("test", List.of(ItemRef.vanilla(Material.IRON_PICKAXE)), cueTicks,
                DigClass.NONE, fallback, null, null, 1, 2, BreakShape.DOWN, 2, readyWindow);
    }

    private ExcavationTool shaped(String id, Material item, BreakShape shape, int onTime, int late, int cost) {
        return new ExcavationTool(id, List.of(ItemRef.vanilla(item)), 1, DigClass.NONE, 0, null, null,
                onTime, late, shape, cost, 20);
    }

    private PickSettings settings(boolean enabled, int budget) {
        return settings(enabled, budget, false);
    }

    private PickSettings settings(boolean enabled, int budget, boolean traces) {
        return new PickSettings(enabled, budget, true, false, 6, 2,
                ConservationSettings.defaults(), LimitsSettings.defaults(), traces,
                CueSettings.defaults(), List.of(profile));
    }

    /** MockBukkit's block locations need snapshots; vanilla speed is supplied as an external input. */
    private static class SnapshotWorld extends WorldMock {
        private final Map<BlockCell, BlockMock> blocks = new HashMap<>();
        private float breakProgress;

        @Override
        public BlockMock getBlockAt(int x, int y, int z) {
            return blocks.computeIfAbsent(new BlockCell(x, y, z), key -> new BlockMock(new Location(this, x, y, z)) {
                @Override public Location getLocation() { return super.getLocation().clone(); }
                @Override public float getBreakSpeed(Player player) { return breakProgress; }
            });
        }
    }
}
