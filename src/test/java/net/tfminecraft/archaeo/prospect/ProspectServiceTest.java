package net.tfminecraft.archaeo.prospect;

import net.tfminecraft.archaeo.config.*;
import net.tfminecraft.archaeo.item.ItemRef;
import net.tfminecraft.archaeo.item.ProspectItem;
import net.tfminecraft.archaeo.model.*;
import net.tfminecraft.archaeo.site.SiteRepository;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
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

public class ProspectServiceTest {
    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private SiteRepository sites;
    private CatalogRegistry catalogs;
    private Site site;
    private ProspectService service;

    @Before public void setUp() {
        server = MockBukkit.mock(); world = server.addSimpleWorld("world");
        player = server.addPlayer(); player.teleport(new Location(world, 4, 5, 4));
        player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_HOE));
        sites = mock(SiteRepository.class); catalogs = mock(CatalogRegistry.class);
        site = new Site(); site.setId(UUID.randomUUID()); site.setStatus(SiteStatus.HIDDEN); site.setInterest(InterestLevel.LOW);
        when(sites.findByChunk("world", 0, 0)).thenReturn(Optional.of(site));
        when(catalogs.interest(InterestLevel.LOW)).thenReturn(new InterestSettings(InterestLevel.LOW, "Low", 8, 1, 1, 1, 0, 0, 0, 0, 0));
        service = new ProspectService(MockBukkit.createMockPlugin(), catalogs, sites,
                new ProspectItem(ItemRef.vanilla(Material.IRON_HOE)), new ProspectSettings(true, 4, 2, 3));
    }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test
    public void distinctSpacedSamplesProgressToConfirmationAndSpendOneDurabilityEach() {
        String[] stages = {"Not enough traces", "Weak traces", "Possible archaeological site", "site confirmed"};
        for (int i = 0; i < 4; i++) {
            sample(1 + i * 3, 1);
            assertContains(messages(), stages[i]);
            assertEquals(i + 1, site.prospectSamples(player.getUniqueId()).size());
            assertEquals(i + 1, ((Damageable) player.getInventory().getItemInMainHand().getItemMeta()).getDamage());
            assertEquals(i == 3, site.isProspectConfirmed(player.getUniqueId()));
        }
        verify(sites, times(4)).save(site);
        service.begin(player, ground(13, 1));
        assertContains(messages(), "site confirmed");
        assertEquals(4, site.prospectSamples(player.getUniqueId()).size());
        service.begin(player, ground(14, 1));
        assertTrue(messages().isEmpty());
    }

    @Test
    public void repeatedOrTooCloseSamplesDoNotConsumeTheKitOrPersistNewProgress() {
        sample(1, 1); messages();
        service.begin(player, ground(1, 1));
        assertContains(messages(), "already sampled");
        service.begin(player, ground(2, 1));
        assertContains(messages(), "farther apart");
        assertEquals(1, site.prospectSamples(player.getUniqueId()).size());
        verify(sites).save(site);
        assertEquals(1, ((Damageable) player.getInventory().getItemInMainHand().getItemMeta()).getDamage());
    }

    @Test
    public void movingSwitchingToolsAndExplicitCancellationDoNotRecordSamples() {
        service.begin(player, ground(1, 1));
        service.begin(player, ground(4, 1));
        player.teleport(new Location(world, 10, 5, 10));
        server.getScheduler().performTicks(3);
        assertContains(messages(), "interrupted");
        assertTrue(site.prospectSamples(player.getUniqueId()).isEmpty());
        service.begin(player, ground(1, 1));
        player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
        server.getScheduler().performTicks(3);
        assertTrue(site.prospectSamples(player.getUniqueId()).isEmpty());
        player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_HOE));
        service.begin(player, ground(1, 1)); service.cancel(player);
        server.getScheduler().performTicks(3);
        verify(sites, never()).save(any());
        service.begin(player, ground(1, 1)); service.stop();
        server.getScheduler().performTicks(3);
        verify(sites, never()).save(any());
    }

    @Test
    public void disabledFeatureAndInvalidGroundNeverStartASampleAndRefusalsAreThrottled() {
        assertFalse(service.isSampleGround(null));
        assertFalse(service.isSampleGround(world.getBlockAt(0, 100, 0)));
        assertTrue(service.isSampleGround(ground(1, 1)));
        service.refuseWrongGround(null);
        service.begin(player, null);
        assertContains(messages(), "not soil");
        service.begin(player, null);
        assertTrue(messages().isEmpty());
        service.stop();
        service.setSettings(new ProspectSettings(false, 1, 1, 0));
        service.begin(player, ground(1, 1));
        server.getScheduler().performTicks(3);
        verify(sites, never()).save(any());
    }

    @Test
    public void missingAndNonHiddenSitesReportNoTracesWithoutProgress() {
        when(sites.findByChunk("world", 0, 0)).thenReturn(Optional.empty());
        service.begin(player, ground(1, 1));
        assertContains(messages(), "No archaeological traces");
        service.stop();
        when(sites.findByChunk("world", 0, 0)).thenReturn(Optional.of(site));
        site.setStatus(SiteStatus.ESTABLISHED);
        service.begin(player, ground(1, 1));
        assertContains(messages(), "No archaeological traces");
        verify(sites, never()).save(any());
    }

    @Test
    public void lastDurabilityPointBreaksKitOnlyAfterACompletedSample() {
        ItemStack kit = player.getInventory().getItemInMainHand();
        Damageable meta = (Damageable) kit.getItemMeta();
        meta.setDamage(Material.IRON_HOE.getMaxDurability() - 1); kit.setItemMeta(meta);
        service.setSettings(new ProspectSettings(true, 1, 0, 0));
        sample(1, 1);
        assertTrue(player.getInventory().getItemInMainHand().getType().isAir());
        assertTrue(site.isProspectConfirmed(player.getUniqueId()));
    }

    @Test
    public void longSamplesShowTheDiggingAnimationAndSoilSpraySeveralTimes() {
        PlayerMock digger = spy(player); service.setSettings(new ProspectSettings(true, 4, 20, 3));
        world = spy(world); Block block = spy(ground(1, 1)); doReturn(world).when(digger).getWorld();
        service.begin(digger, block); server.getScheduler().performTicks(21);
        verify(digger, times(2)).swingMainHand();
        var soil = block.getBlockData();
        verify(world, times(2)).spawnParticle(eq(org.bukkit.Particle.BLOCK), any(Location.class), eq(6), eq(0.2), eq(0.05), eq(0.2), eq(0.0), eq(soil));
        assertEquals(1, site.prospectSamples(digger.getUniqueId()).size());
    }

    @Test
    public void readingFlavourFollowsTheRuinWealthAndStaysVagueForDossiersWithoutInterest() {
        String[][] readings = {{"5", "fair amount of cultural material"}, {"1", "only modest remains"}, {"8", "unusually rich"}};
        for (String[] reading : readings) {
            site.allProspectSamples().clear();
            when(catalogs.interest(InterestLevel.LOW)).thenReturn(new InterestSettings(InterestLevel.LOW, "Low", Integer.parseInt(reading[0]), 0, 1, 1, 0, 0, 0, 0, 0));
            sample(1, 1); sample(4, 1); List<String> weak = messages();
            assertContains(weak, "Weak traces"); assertContains(weak, reading[1]);
        }
        // A dossier whose interest: key was lost or garbled loads with no interest.
        site.allProspectSamples().clear(); site.setInterest(null);
        sample(1, 1); sample(4, 1);
        assertContains(messages(), "The reading is inconclusive.");
        assertEquals(2, site.prospectSamples(player.getUniqueId()).size());
    }

    @Test
    public void kitsWithoutDurabilityAreNeverWornOrBroken() {
        // prospect.item may be any stack, such as a STICK, that has no durability bar.
        service = new ProspectService(MockBukkit.createMockPlugin(), catalogs, sites,
                new ProspectItem(ItemRef.vanilla(Material.STICK)), new ProspectSettings(true, 1, 1, 0));
        player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
        sample(1, 1);
        assertContains(messages(), "site confirmed");
        assertEquals(new ItemStack(Material.STICK), player.getInventory().getItemInMainHand());
    }

    @Test
    public void quittingWithoutASampleIsHarmlessAndTheRefusalCooldownExpires() {
        java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong(1_000_000); service.clock = now::get;
        service.cancel(player);
        assertEquals(List.of(""), messages()); // Only the cleared action bar.
        verify(sites, never()).save(any());
        String refusal = "That is not soil. Sample dirt, sand, gravel, or clay.";
        service.begin(player, null); assertEquals(List.of(refusal), messages());
        now.addAndGet(799); service.begin(player, null); assertEquals(List.of(), messages()); // Still inside the 800 ms window.
        now.addAndGet(1);
        service.begin(player, null); assertEquals(List.of(refusal), messages());
    }

    private Block ground(int x, int z) { Block block = world.getBlockAt(x, 4, z); block.setType(Material.DIRT); return block; }
    private void sample(int x, int z) { service.begin(player, ground(x, z)); server.getScheduler().performTicks(3); }
    private List<String> messages() {
        List<String> out = new ArrayList<>(); String message;
        while ((message = player.nextMessage()) != null) out.add(message);
        return out;
    }
    private static void assertContains(List<String> messages, String expected) {
        assertTrue(messages.toString(), messages.stream().anyMatch(s -> s.contains(expected)));
    }
}
