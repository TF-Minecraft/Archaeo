package net.tfminecraft.archaeo.excavation;

import net.tfminecraft.archaeo.config.CatalogRegistry;
import net.tfminecraft.archaeo.config.LimitsSettings;
import net.tfminecraft.archaeo.config.PickSettings;
import net.tfminecraft.archaeo.model.Site;
import net.tfminecraft.archaeo.model.StratumBand;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.entity.BlockDisplayMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PrismOutlineServiceTest {
    private ServerMock server;
    private WorldMock world;
    private JavaPlugin plugin;
    private CatalogRegistry catalogs;
    private PrismOutlineService service;
    private PlayerMock viewer;
    private PlayerMock other;
    private Site site;

    @Before
    public void setup() {
        server = MockBukkit.mock();
        world = new DisplayWorld(server);
        world.setName("outline");
        server.addWorld(world);
        plugin = MockBukkit.createMockPlugin();
        viewer = spy(new PlayerMock(server, "Viewer"));
        other = spy(new PlayerMock(server, "Other"));
        server.addPlayer(viewer);
        server.addPlayer(other);
        viewer.teleport(new Location(world, 0, 30, 0));
        other.teleport(new Location(world, 0, 30, 0));
        catalogs = mock(CatalogRegistry.class);
        configure(new LimitsSettings(1, 0.08, 128));
        service = new PrismOutlineService(plugin, catalogs);
        site = new Site();
        site.setWorldName(world.getName());
        site.setChunkX(-2);
        site.setChunkZ(3);
        band("deep", 10, 13, true);
        band("absent", 100, 120, false);
        band("shallow", 14, 20, true);
    }

    @After
    public void teardown() {
        if (service != null) service.stop();
        MockBukkit.unmock();
    }

    @Test
    public void outlinesChunkEdgesAndSortedPresentStratumSeamsForOneViewer() {
        service.show(viewer, site);
        List<BlockDisplay> bars = bars();
        assertEquals(16, bars.size());
        int xEdges = 0, yEdges = 0, zEdges = 0, seams = 0;
        Set<Double> ringHeights = new java.util.HashSet<>();
        for (BlockDisplay display : bars) {
            Location at = display.getLocation();
            assertTrue(at.getX() == -32 || at.getX() == -16);
            assertTrue(at.getZ() == 48 || at.getZ() == 64);
            Vector3f scale = display.getTransformation().getScale();
            Vector3f translation = display.getTransformation().getTranslation();
            if (scale.x > 1) {
                xEdges++;
                assertEquals(16, scale.x, 0.00001);
                assertEquals(0.08, scale.y, 0.00001);
                assertEquals(0.08, scale.z, 0.00001);
                assertEquals(0, translation.x, 0);
                assertEquals(-0.04, translation.y, 0.00001);
                ringHeights.add(at.getY());
            } else if (scale.y > 1) {
                yEdges++;
                assertEquals(11, scale.y, 0.00001);
                assertEquals(10, at.getY(), 0);
                assertEquals(0.08, scale.x, 0.00001);
                assertEquals(0.08, scale.z, 0.00001);
                assertEquals(13, display.getDisplayHeight(), 0);
            } else {
                zEdges++;
                assertEquals(16, scale.z, 0.00001);
                assertEquals(0.08, scale.x, 0.00001);
                assertEquals(0.08, scale.y, 0.00001);
                ringHeights.add(at.getY());
            }
            boolean seam = display.getBlock().getMaterial() == Material.LIGHT_BLUE_CONCRETE;
            if (seam) {
                seams++;
                assertEquals(14, at.getY(), 0);
                assertEquals(Color.fromRGB(170, 215, 255), display.getGlowColorOverride());
            } else {
                assertEquals(Material.ORANGE_CONCRETE, display.getBlock().getMaterial());
                assertEquals(Color.fromRGB(255, 205, 120), display.getGlowColorOverride());
            }
            assertFalse(display.isVisibleByDefault());
            assertFalse(display.isPersistent());
            assertTrue(display.isGlowing());
            assertEquals(Display.Billboard.FIXED, display.getBillboard());
            assertEquals(15, display.getBrightness().getBlockLight());
            assertEquals(15, display.getBrightness().getSkyLight());
            assertEquals(2, display.getViewRange(), 0);
            verify(viewer).showEntity(plugin, display);
            verify(other, never()).showEntity(plugin, display);
        }
        assertEquals(6, xEdges);
        assertEquals(4, yEdges);
        assertEquals(6, zEdges);
        assertEquals(4, seams);
        assertEquals(Set.of(21.0, 14.0, 10.0), ringHeights);
    }

    @Test
    public void staticBarsRemainUntilDurationExpiresThenAllAreRemoved() {
        service.show(viewer, site);
        List<BlockDisplay> created = bars();
        ticks(19);
        assertEquals(created.stream().map(Entity::getUniqueId).collect(Collectors.toSet()),
                bars().stream().map(Entity::getUniqueId).collect(Collectors.toSet()));
        for (BlockDisplay bar : created) assertFalse(bar.isDead());
        ticks(1);
        for (BlockDisplay bar : created) assertTrue(bar.isDead());
        assertTrue(bars().isEmpty());
    }

    @Test
    public void replacingOutlineGivesNewBarsTheirFullDuration() {
        service.show(viewer, site);
        List<BlockDisplay> old = bars();
        ticks(10);
        service.show(viewer, site);
        List<BlockDisplay> replacement = bars();
        assertEquals(16, replacement.size());
        for (BlockDisplay bar : old) assertTrue(bar.isDead());
        ticks(10);
        for (BlockDisplay bar : replacement) assertFalse("Old expiry must not remove replacement", bar.isDead());
        ticks(9);
        assertEquals(16, bars().size());
        ticks(1);
        for (BlockDisplay bar : replacement) assertTrue(bar.isDead());
    }

    @Test
    public void viewerCancellationAndShutdownKeepOtherViewersIndependent() {
        service.show(viewer, site);
        List<BlockDisplay> first = bars();
        service.show(other, site);
        List<BlockDisplay> second = bars().stream().filter(bar -> !first.contains(bar)).toList();
        assertEquals(16, second.size());
        service.cancel(viewer);
        for (BlockDisplay bar : first) assertTrue(bar.isDead());
        for (BlockDisplay bar : second) assertFalse(bar.isDead());
        service.stop();
        for (BlockDisplay bar : second) assertTrue(bar.isDead());
        ticks(25);
        assertTrue(bars().isEmpty());
    }

    @Test
    public void invalidShowRequestsPreserveExistingOutlineAndExplainWhy() {
        service.show(viewer, site);
        List<BlockDisplay> current = bars();
        site.setWorldName("other-world");
        service.show(viewer, site);
        verify(viewer).sendMessage("That excavation is in another world.");
        site.setWorldName(world.getName());
        site.getStrata().clear();
        service.show(viewer, site);
        verify(viewer).sendMessage("This site has no excavated strata to outline.");
        assertEquals(current.size(), bars().size());
        for (BlockDisplay bar : current) assertFalse(bar.isDead());
    }

    @Test
    public void liveSettingsControlNextOutlineThicknessRangeAndDuration() {
        configure(new LimitsSettings(2, 0.2, 64));
        service.show(viewer, site);
        for (BlockDisplay bar : bars()) {
            Vector3f scale = bar.getTransformation().getScale();
            assertEquals(0.2, Math.min(scale.x, Math.min(scale.y, scale.z)), 0.00001);
            assertEquals(1, bar.getViewRange(), 0);
        }
        ticks(39);
        assertEquals(16, bars().size());
        ticks(1);
        assertTrue(bars().isEmpty());
    }

    @Test
    public void invertedStratumFromAMalformedDossierDrawsItsRingsWithoutUprights() {
        site.getStrata().clear();
        band("inverted", 50, 40, true);
        service.show(viewer, site);
        List<BlockDisplay> bars = bars();
        assertEquals(8, bars.size());
        for (BlockDisplay bar : bars) {
            assertTrue("No bar runs upwards", bar.getTransformation().getScale().y < 1);
        }
        assertEquals(Set.of(41.0, 50.0), bars.stream().map(bar -> bar.getLocation().getY()).collect(Collectors.toSet()));
        verify(viewer).sendMessage("Excavation limits shown for 1 seconds.");
    }

    private void configure(LimitsSettings limits) {
        PickSettings defaults = PickSettings.defaults();
        when(catalogs.pick()).thenReturn(new PickSettings(true, 8, true, true, 6, 2,
                defaults.conservation(), limits, true, defaults.cues(), defaults.profiles()));
    }

    private void band(String id, int min, int max, boolean present) {
        StratumBand band = new StratumBand();
        band.setId(id);
        band.setMinY(min);
        band.setMaxY(max);
        band.setPresent(present);
        site.getStrata().put(id, band);
    }

    private List<BlockDisplay> bars() { return world.getEntitiesByClass(BlockDisplay.class).stream().toList(); }
    private void ticks(int count) { server.getScheduler().performTicks(count); }
    /** MockBukkit has display geometry but does not yet store default visibility or billboard mode. */
    private static class DisplayWorld extends WorldMock {
        private final ServerMock server;
        DisplayWorld(ServerMock server) { this.server = server; }
        @Override
        public <T extends Entity> T spawn(Location location, Class<T> type, Consumer<? super T> configure) {
            if (type != BlockDisplay.class) return super.spawn(location, type, configure);
            BlockDisplayMock display = new BlockDisplayMock(server, UUID.randomUUID()) {
                private boolean visible = true;
                private Display.Billboard billboard = Display.Billboard.FIXED;
                @Override public void setVisibleByDefault(boolean value) { visible = value; }
                @Override public boolean isVisibleByDefault() { return visible; }
                @Override public void setBillboard(Display.Billboard value) { billboard = value; }
                @Override public Display.Billboard getBillboard() { return billboard; }
            };
            display.setLocation(location);
            T entity = type.cast(display);
            configure.accept(entity);
            server.registerEntity(display);
            return entity;
        }
    }

}
