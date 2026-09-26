package net.tfminecraft.archaeo.tracker;

import net.tfminecraft.archaeo.config.TrackerSettings;
import net.tfminecraft.archaeo.item.ItemRef;
import net.tfminecraft.archaeo.item.TrackerItem;
import net.tfminecraft.archaeo.model.Site;
import net.tfminecraft.archaeo.model.SiteStatus;
import net.tfminecraft.archaeo.site.SiteRepository;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TrackerServiceTest {
    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private TrackerService tracker;
    private final List<Site> sites = new ArrayList<>();
    private final List<Location> particles = new ArrayList<>();

    @Before public void setUp() {
        server = MockBukkit.mock(); world = spy(new WorldMock()); world.setName("world"); server.addWorld(world); player = server.addPlayer();
        doAnswer(call -> {
            particles.add(new Location(world, call.getArgument(1), call.getArgument(2), call.getArgument(3)));
            return null;
        }).when(world).spawnParticle(eq(Particle.ENCHANTED_HIT), anyDouble(), anyDouble(), anyDouble(), eq(1), eq(0.0), eq(0.0), eq(0.0), eq(0.0));
        SiteRepository repository = mock(SiteRepository.class);
        when(repository.all()).thenAnswer(invocation -> sites);
        when(repository.findById(any())).thenAnswer(invocation -> sites.stream().filter(s -> s.getId().equals(invocation.getArgument(0))).findFirst());
        tracker = new TrackerService(MockBukkit.createMockPlugin(), repository,
                new TrackerItem(ItemRef.vanilla(Material.COMPASS)), settings(true, false));
        player.teleport(new Location(world, 8.5, 5, -8, 0, 0));
    }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test
    public void scansOnlyHiddenSameWorldSitesInRangeAndInFrontRegardlessOfPitch() {
        Site target = site(0, 0);
        assertSame(target, tracker.strongestAhead(player).site());
        var position = player.getLocation(); position.setPitch(90); player.teleport(position);
        assertSame(target, tracker.strongestAhead(player).site());
        position.setYaw(180); player.teleport(position);
        assertNull(tracker.strongestAhead(player));
        player.teleport(new Location(world, 8, 100, 8, 180, 90));
        assertTrue(tracker.strongestAhead(player).onChunk());
        target.setStatus(SiteStatus.ESTABLISHED); assertNull(tracker.strongestAhead(player));
        target.setStatus(SiteStatus.HIDDEN); target.setWorldName("other"); assertNull(tracker.strongestAhead(player));
        target.setWorldName("world"); target.setChunkZ(50); assertNull(tracker.strongestAhead(player));
    }

    @Test
    public void choosesBestAlignmentThenDistanceAndReleasesGoneTargets() {
        Site far = site(0, 4); Site near = site(0, 1); Site sideways = site(4, 1);
        assertSame(near, tracker.strongestAhead(player).site());
        player.getInventory().setItemInMainHand(new ItemStack(Material.COMPASS));
        tracker.pulse();
        assertSame(near, tracker.lockedTarget(player).site());
        near.setStatus(SiteStatus.EXHAUSTED);
        assertSame(far, tracker.lockedTarget(player).site());
        sites.remove(far);
        assertSame(sideways, tracker.lockedTarget(player).site());
        sites.clear(); assertNull(tracker.lockedTarget(player));
    }

    @Test
    public void proximityAndCadenceGrowStrongerTowardTheRuin() {
        Site target = site(0, 0);
        TrackerService.ScanHit on = new TrackerService.ScanHit(target, 0, 128, 1, true);
        TrackerService.ScanHit close = new TrackerService.ScanHit(target, 8, 128, 1, false);
        TrackerService.ScanHit medium = new TrackerService.ScanHit(target, 24, 128, 1, false);
        TrackerService.ScanHit far = new TrackerService.ScanHit(target, 128, 128, 1, false);
        assertEquals(4, tracker.proximityBands(on)); assertEquals(3, tracker.proximityBands(close));
        assertEquals(2, tracker.proximityBands(medium)); assertEquals(1, tracker.proximityBands(far));
        assertTrue(tracker.beepInterval(on) < tracker.beepInterval(close));
        assertTrue(tracker.beepInterval(close) < tracker.beepInterval(medium));
        assertTrue(tracker.beepInterval(medium) < tracker.beepInterval(far));
        assertEquals(70, tracker.beepInterval(far));
        assertEquals(5, tracker.beepInterval(close));
        assertTrue(new TrackerService.ScanHit(target, 8, 128, 0.5, false).sensedDistance() > close.sensedDistance());
    }

    @Test
    public void targetLockResistsMarginalDistanceChangesButSwitchesToClearlyCloserEvidence() {
        Site current = site(0, 2);
        player.getInventory().setItemInMainHand(new ItemStack(Material.COMPASS)); tracker.pulse();
        Site marginal = site(0, 1);
        assertSame(marginal, tracker.strongestAhead(player).site());
        assertSame(current, tracker.lockedTarget(player).site());
        Site close = site(0, 0);
        assertSame(close, tracker.lockedTarget(player).site());
        tracker.pulse(); assertSame(close, tracker.lockedTarget(player).site());
    }

    @Test
    public void deliberateTurnChangesTheLockToTheRuinUnderTheCrosshair() {
        Site straight = site(0, 1); Site diagonal = site(2, 1);
        player.getInventory().setItemInMainHand(new ItemStack(Material.COMPASS)); tracker.pulse();
        assertSame(straight, tracker.lockedTarget(player).site());
        Location turned = player.getLocation(); turned.setYaw(-45); player.teleport(turned);
        assertSame(diagonal, tracker.lockedTarget(player).site());
    }

    @Test
    public void heldTrackersNotifyOncePerCooldownAndDroppingThemStopsScanning() {
        site(0, 0);
        player.teleport(new Location(world, 8, 5, 8));
        tracker.pulse(); assertNull(player.nextMessage());
        player.getInventory().setItemInOffHand(new ItemStack(Material.COMPASS));
        tracker.pulse();
        assertEquals("Archaeological signal detected.", player.nextMessage());
        assertTrue(player.nextMessage().startsWith("Prospect the area"));
        tracker.pulse(); assertNull(player.nextMessage());
        player.getInventory().clear(); tracker.pulse(); assertNull(player.nextMessage());
        tracker.setSettings(settings(false, false));
        player.getInventory().setItemInMainHand(new ItemStack(Material.COMPASS));
        tracker.pulse(); assertNull(player.nextMessage());
        tracker.stop(); tracker.setSettings(settings(true, false));
        tracker.start(); server.getScheduler().performTicks(22);
        assertEquals("Archaeological signal detected.", player.nextMessage()); player.nextMessage();
        tracker.stop(); server.getScheduler().performTicks(30); assertNull(player.nextMessage());
    }

    @Test
    public void onSitePulseDrawsThreeConcentricRingsCenteredOnTheScanner() {
        site(0, 0); tracker.setSettings(settings(true, true));
        player.teleport(new Location(world, 8, 5, 8));
        player.getInventory().setItemInMainHand(new ItemStack(Material.COMPASS));
        tracker.pulse(); server.getScheduler().performTicks(4);
        assertFalse(particles.isEmpty());
        java.util.Set<Integer> radiusHundredths = new java.util.HashSet<>();
        for (Location particle : particles) {
            assertEquals(5.12, particle.getY(), .0001);
            radiusHundredths.add((int) Math.round(Math.hypot(particle.getX() - 8, particle.getZ() - 8) * 100));
        }
        assertEquals(java.util.Set.of(120, 128, 240, 248, 360, 368), radiusHundredths);
        assertTrue(particles.stream().anyMatch(p -> p.getZ() < 8));
        assertTrue(particles.stream().anyMatch(p -> p.getZ() > 8));
        assertEquals(new Location(world, 8, 5, 8), player.getLocation());
    }

    @Test
    public void offSitePulsePointsAheadAndDoesNotDrawBehindTheScanner() {
        site(0, 0); tracker.setSettings(settings(true, true));
        player.getInventory().setItemInMainHand(new ItemStack(Material.COMPASS));
        Location before = player.getLocation(); tracker.pulse(); server.getScheduler().performTicks(10);
        assertFalse(particles.isEmpty());
        double centerZ = before.getZ() + 1.2;
        assertTrue(particles.stream().allMatch(p -> p.getZ() > centerZ));
        assertTrue(particles.stream().anyMatch(p -> p.getX() < before.getX()));
        assertTrue(particles.stream().anyMatch(p -> p.getX() > before.getX()));
        assertEquals(before, player.getLocation());
    }

    @Test
    public void lookingAwayReleasesTheLockSoTurningBackPicksTheStrongestRuin() {
        Site locked = site(0, 2);
        player.getInventory().setItemInMainHand(new ItemStack(Material.COMPASS)); tracker.pulse();
        Site marginal = site(0, 1);
        assertSame(locked, tracker.lockedTarget(player).site());
        Location away = player.getLocation(); away.setYaw(180); player.teleport(away); tracker.pulse();
        away.setYaw(0); player.teleport(away);
        assertSame(marginal, tracker.lockedTarget(player).site());
    }

    @Test
    public void somewhatBetterAimAtACloserRuinDoesNotStealTheLock() {
        Site locked = site(-5, 3);
        player.getInventory().setItemInMainHand(new ItemStack(Material.COMPASS)); tracker.pulse();
        Site closer = site(1, 0);
        assertSame(closer, tracker.strongestAhead(player).site());
        assertSame(locked, tracker.lockedTarget(player).site());
    }

    @Test
    public void equallyAimedRuinsPreferTheNearerWhateverTheDossierOrder() {
        Site near = site(0, 1); site(0, 4);
        assertSame(near, tracker.strongestAhead(player).site());
    }

    @Test
    public void closeConeChimesThreeRisingNotesAboveTheMediumBand() {
        site(0, 0); player.getInventory().setItemInMainHand(new ItemStack(Material.COMPASS));
        player.teleport(new Location(world, 8.5, 5, -4, 0, 0));
        tracker.pulse(); server.getScheduler().performTicks(10);
        assertEquals(List.of(1.55f, 1.61f, 1.67f), chimes());
        assertEquals("Archaeological signal detected.", player.nextMessage());
        clearInvocations(world); tracker.stop();
        player.teleport(new Location(world, 8.5, 5, -20, 0, 0));
        tracker.pulse(); server.getScheduler().performTicks(10);
        assertEquals(List.of(1.12f, 1.18f), chimes());
    }

    @Test
    public void farSignalDrawsOneArcThatWidensAsTheScannerClosesIn() {
        site(0, 0); tracker.setSettings(settings(true, true));
        player.getInventory().setItemInMainHand(new ItemStack(Material.COMPASS));
        player.teleport(new Location(world, 8.5, 5, -100, 0, 0));
        double distant = ringRadius();
        tracker.stop();
        player.teleport(new Location(world, 8.5, 5, -60, 0, 0));
        double nearer = ringRadius();
        assertTrue(distant + " < " + nearer, distant < nearer);
        assertTrue(distant >= 1.2 && nearer <= 2.4);
    }

    @Test
    public void closeButObliqueScannersHearTheRuinWithoutTheProspectingHint() {
        site(0, 0); player.getInventory().setItemInMainHand(new ItemStack(Material.COMPASS));
        player.teleport(new Location(world, 8.5, 5, -4, 60, 0));
        tracker.pulse(); server.getScheduler().performTicks(10);
        assertEquals(List.of(0.72f), chimes());
        assertNull(player.nextMessage());
    }

    @Test
    public void onChunkPulsesKeepChimingButTheHintRepeatsOnlyAfterItsCooldown() {
        site(0, 0); player.getInventory().setItemInMainHand(new ItemStack(Material.COMPASS));
        player.teleport(new Location(world, 8, 5, 8));
        for (int pulse = 0; pulse < 30; pulse++) { tracker.pulse(); server.getScheduler().performTicks(2); }
        assertTrue(chimes().stream().filter(pitch -> pitch == 1.85f).count() > 1);
        assertEquals("Archaeological signal detected.", player.nextMessage()); player.nextMessage();
        assertNull(player.nextMessage());
    }

    private List<Float> chimes() {
        var pitches = org.mockito.ArgumentCaptor.forClass(Float.class);
        verify(world, atLeast(0)).playSound(any(Location.class), eq(org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME), eq(org.bukkit.SoundCategory.PLAYERS), anyFloat(), pitches.capture());
        return pitches.getAllValues().stream().map(pitch -> Math.round(pitch * 100) / 100f).toList();
    }

    private double ringRadius() {
        particles.clear(); Location feet = player.getLocation();
        tracker.pulse(); server.getScheduler().performTicks(10);
        assertFalse(particles.isEmpty());
        double centerX = feet.getX(), centerZ = feet.getZ() + 1.2;
        java.util.TreeSet<Long> radii = new java.util.TreeSet<>();
        for (Location particle : particles) radii.add(Math.round(Math.hypot(particle.getX() - centerX, particle.getZ() - centerZ) * 100));
        assertEquals("one ring and its thickening stroke", 2, radii.size());
        return radii.first() / 100.0;
    }

    private Site site(int chunkX, int chunkZ) {
        Site site = new Site(); site.setId(UUID.randomUUID()); site.setWorldName("world");
        site.setChunkX(chunkX); site.setChunkZ(chunkZ); site.setStatus(SiteStatus.HIDDEN); sites.add(site); return site;
    }
    private static TrackerSettings settings(boolean enabled, boolean particles) {
        return new TrackerSettings(enabled, 128, 32, 16, particles, 70, 5, 200, List.of(1.2, 2.4, 3.6), 3, 10, Particle.ENCHANTED_HIT, 1.2, 16);
    }
}
