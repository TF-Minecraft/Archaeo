package net.tfminecraft.archaeo.site;

import net.tfminecraft.archaeo.model.*;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.time.Instant;
import java.util.UUID;

import static org.junit.Assert.*;

public class SiteClosureTest {
    private ServerMock server;
    private WorldMock world;
    private JavaPlugin plugin;
    private SiteRepository sites;
    private PlayerMock director;
    private Site site;
    private BuriedFind find;

    @Before public void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();
        sites = new SiteRepository(plugin); sites.loadAll();
        director = server.addPlayer("Director");
        site = new Site(); site.setId(UUID.randomUUID()); site.setSerial(1); site.setName("Ford");
        site.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z")); site.setWorldName("world"); site.setInterest(InterestLevel.LOW);
        site.establish(director.getUniqueId(), 0, 0, 8, 5, 8);
        find = new BuriedFind(); find.setId(UUID.randomUUID()); find.getCells().add(new BlockCell(4, 2, 4)); site.getFinds().add(find);
        sites.save(site);
    }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test
    public void fieldAnnouncementReachesOnlyTheThreeByThreeChunksAroundTheCut() {
        PlayerMock corner = at("Corner", 31, -16), eastOnly = at("East", 20, 40), northOnly = at("North", 40, 8), onCut = at("OnCut", 8, 8);
        director.teleport(new Location(world, 500, 5, 500));
        find.setState(FindState.RECOVERED);
        assertTrue(SiteClosure.settle(sites, site));
        for (PlayerMock told : new PlayerMock[]{corner, onCut, director}) assertTrue(told.getName(), told.nextMessage().contains("Ford is exhausted"));
        assertNull(eastOnly.nextMessage()); assertNull(northOnly.nextMessage());
    }

    @Test
    public void cutInAnUnloadedWorldStillTellsItsRosterButNoBystanderAtTheSameCoordinates() {
        PlayerMock worker = server.addPlayer("Worker"), bystander = at("Bystander", 8, 8);
        worker.teleport(new Location(world, 900, 5, 900));
        site.setWorldName("archive_world"); site.getExcavators().add(worker.getUniqueId());
        find.setState(FindState.RECOVERED);
        assertTrue(SiteClosure.settle(sites, site));
        assertTrue(director.nextMessage().contains("is exhausted")); assertNull(director.nextMessage());
        assertTrue(worker.nextMessage().contains("is exhausted"));
        assertNull(bystander.nextMessage());
    }

    @Test
    public void handEditedDossierWithoutWorldOrDirectorStillSettlesAndTellsItsExcavators() throws Exception {
        PlayerMock worker = server.addPlayer("Worker");
        site.getExcavators().add(worker.getUniqueId()); find.setState(FindState.RECOVERED);
        sites.save(site);
        File file = new File(new File(plugin.getDataFolder(), "sites"), site.getId() + ".yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("world", null); yaml.set("director", null); yaml.save(file);
        sites.loadAll();
        Site loaded = sites.findById(site.getId()).orElseThrow();
        assertNull(loaded.getWorldName()); assertNull(loaded.getDirector());
        assertTrue(SiteClosure.settle(sites, loaded));
        // The director's own line went with the edit; planting the camp also enrolled them as an excavator.
        for (PlayerMock told : new PlayerMock[]{worker, director}) { assertTrue(told.nextMessage().contains("Ford is exhausted")); assertNull(told.nextMessage()); }
        sites.loadAll();
        assertEquals(SiteStatus.EXHAUSTED, sites.findById(site.getId()).orElseThrow().getStatus());
    }

    private PlayerMock at(String name, int x, int z) {
        PlayerMock player = server.addPlayer(name); player.teleport(new Location(world, x, 5, z)); return player;
    }
}
