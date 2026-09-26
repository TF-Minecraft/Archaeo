package net.tfminecraft.archaeo.establish;

import net.tfminecraft.archaeo.model.*;
import net.tfminecraft.archaeo.site.SiteClosure;
import net.tfminecraft.archaeo.site.SiteRepository;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CampClosureTest {
    private ServerMock server;
    private WorldMock world;
    private PlayerMock director;
    private SiteRepository sites;
    private CampClosure closure;
    private Site site;

    @Before public void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        director = server.addPlayer("Director");
        var plugin = MockBukkit.createMockPlugin();
        sites = new SiteRepository(plugin);
        sites.loadAll();
        closure = new CampClosure(plugin, sites);
        site = new Site();
        site.setId(UUID.randomUUID()); site.setSerial(1); site.setName("River camp");
        site.setCreatedAt(java.time.Instant.parse("2026-01-01T00:00:00Z")); site.setWorldName("world"); site.setInterest(InterestLevel.LOW);
        site.establish(director.getUniqueId(), 1, 0, 20, 4, 4);
        sites.save(site);
    }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test
    public void closingCampPersistsStatusAndGivesOneLiveArchive() {
        assertFalse(closure.close(null, site));
        assertFalse(closure.close(director, null));
        assertTrue(closure.close(director, site));
        assertEquals(SiteStatus.CLOSED, site.getStatus());
        assertFalse(site.isCampLocked());
        ItemStack book = director.getInventory().getItem(0);
        assertTrue(closure.archiveBook().isArchive(book));
        assertEquals(site.getId(), closure.archiveBook().siteIdOf(book));
        assertTrue(director.nextMessage().contains("Closed River camp"));
        assertFalse(closure.close(director, site));
        assertEquals(1, book.getAmount());
        sites.loadAll();
        assertEquals(SiteStatus.CLOSED, sites.findById(site.getId()).orElseThrow().getStatus());
    }

    @Test
    public void unfinishedClosureNotifiesDirectorAndDropsOverflowInsteadOfLosingBook() {
        BuriedFind find = new BuriedFind(); find.setId(UUID.randomUUID());
        find.getCells().add(new BlockCell(0, 1, 0)); site.getFinds().add(find);
        PlayerMock staff = server.addPlayer("Staff");
        for (int slot = 0; slot < 36; slot++) staff.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
        // MockBukkit also scans equipment when adding items; use a valid fully equipped inventory.
        staff.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
        staff.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        staff.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        staff.getInventory().setBoots(new ItemStack(Material.IRON_BOOTS));
        staff.getInventory().setItemInOffHand(new ItemStack(Material.TORCH, 64));
        PlayerMock receiver = spy(staff);
        var fullStorage = spy(staff.getInventory());
        doReturn(fullStorage).when(receiver).getInventory();
        // Bukkit addItem uses storage only; MockBukkit also searches two phantom player slots.
        doAnswer(call -> {
            var overflow = new java.util.HashMap<Integer, ItemStack>();
            overflow.put(0, call.getArgument(0)); return overflow;
        }).when(fullStorage).addItem(any(ItemStack.class));
        assertTrue(closure.close(receiver, site));
        assertTrue(staff.nextMessage().contains("0% complete"));
        assertTrue(director.nextMessage().contains("was closed"));
        assertTrue(staff.getWorld().getEntities().stream().filter(Item.class::isInstance).map(Item.class::cast)
                .anyMatch(item -> closure.archiveBook().isArchive(item.getItemStack())));
    }

    @Test
    public void consoleDropsArchiveAtCampAndMissingWorldStillClosesDossier() {
        assertTrue(closure.close(server.getConsoleSender(), site));
        Item drop = world.getEntities().stream().filter(Item.class::isInstance).map(Item.class::cast).findFirst().orElseThrow();
        assertEquals(site.getId(), closure.archiveBook().siteIdOf(drop.getItemStack()));
        assertTrue(drop.getLocation().distance(new Location(world, 20.5, 5, 4.5)) < 2);
        Site missing = new Site();
        missing.setCreatedAt(java.time.Instant.parse("2026-01-01T00:00:00Z")); missing.setId(UUID.randomUUID()); missing.setWorldName("missing"); missing.setInterest(InterestLevel.LOW);
        missing.establish(UUID.randomUUID(), 1, 0, 20, 4, 4);
        assertTrue(closure.close(server.getConsoleSender(), missing));
        assertEquals(SiteStatus.CLOSED, missing.getStatus());
    }

    @Test
    public void consoleClosingALegacyCampWithoutAnOriginStillClosesItAndSaysNoBookWasDropped() {
        site.setCampX(null); site.setDirector(null); // A dossier saved before camp origins and directors were recorded.
        var console = spy(server.getConsoleSender());
        assertTrue(closure.close(console, site)); assertEquals(SiteStatus.CLOSED, site.getStatus());
        verify(console).sendMessage("No field book could be given from console.");
        assertTrue(world.getEntities().stream().noneMatch(Item.class::isInstance)); assertNull(director.nextMessage());
        sites.loadAll(); assertEquals(SiteStatus.CLOSED, sites.findById(site.getId()).orElseThrow().getStatus());
    }

    @Test
    public void lastFindExhaustsSiteOnceAndNotifiesNearbyPlayersAndRemoteRosterOnce() {
        BuriedFind find = new BuriedFind(); find.setId(UUID.randomUUID()); site.getFinds().add(find);
        PlayerMock nearby = server.addPlayer("Nearby");
        PlayerMock distant = server.addPlayer("Distant");
        PlayerMock worker = server.addPlayer("Worker");
        director.teleport(new Location(world, 4, 5, 4));
        nearby.teleport(new Location(world, 20, 5, 4));
        distant.teleport(new Location(world, 100, 5, 100));
        worker.teleport(new Location(world, 100, 5, 100));
        site.getExcavators().add(director.getUniqueId());
        site.getExcavators().add(worker.getUniqueId());
        site.getExcavators().add(UUID.randomUUID());
        assertFalse(SiteClosure.settle(sites, null));
        assertFalse(SiteClosure.settle(sites, site));
        find.setState(FindState.RECOVERED);
        assertTrue(SiteClosure.settle(sites, site));
        assertEquals(SiteStatus.EXHAUSTED, site.getStatus());
        for (PlayerMock recipient : new PlayerMock[]{director, nearby, worker}) {
            assertTrue(recipient.nextMessage().contains("is exhausted"));
            assertNull(recipient.nextMessage());
        }
        assertNull(distant.nextMessage());
        assertFalse(SiteClosure.settle(sites, site));
        assertNull(director.nextMessage());
        sites.loadAll();
        assertEquals(SiteStatus.EXHAUSTED, sites.findById(site.getId()).orElseThrow().getStatus());
    }
}
