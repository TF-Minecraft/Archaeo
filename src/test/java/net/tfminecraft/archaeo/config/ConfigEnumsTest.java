package net.tfminecraft.archaeo.config;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

public class ConfigEnumsTest {
    private JavaPlugin plugin;
    @Before public void setUp() {
        MockBukkit.mock();
        plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ConfigEnumsTest"));
    }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test public void materialsAcceptNamespacedTrimmedTokensAndRejectAirOrUnknown() {
        assertEquals(Material.STONE, ConfigEnums.material(plugin, " minecraft:stone ", Material.BRICK, "material"));
        for (String token : new String[] {null, " ", "missing_material", "AIR", "CAVE_AIR", "VOID_AIR"}) {
            assertEquals(Material.BRICK, ConfigEnums.material(plugin, token, Material.BRICK, "material"));
        }
    }
    @Test public void particlesAreCaseInsensitiveAndFallbackForUnknownNames() {
        assertEquals(Particle.FLAME, ConfigEnums.particle(plugin, " flame ", Particle.CLOUD, "particle"));
        for (String token : new String[] {null, " ", "missing_particle"}) {
            assertEquals(Particle.CLOUD, ConfigEnums.particle(plugin, token, Particle.CLOUD, "particle"));
        }
    }
    @Test public void soundsAcceptEnumDottedAndNamespacedForms() {
        for (String token : new String[] {" ITEM_BUCKET_EMPTY ", "item.bucket.empty", "MINECRAFT:ITEM.BUCKET.EMPTY"}) {
            assertEquals(Sound.ITEM_BUCKET_EMPTY, ConfigEnums.sound(plugin, token, Sound.BLOCK_WOOL_HIT, "sound"));
        }
        for (String token : new String[] {null, " ", "not.a.sound", "bad key!"}) {
            assertEquals(Sound.BLOCK_WOOL_HIT, ConfigEnums.sound(plugin, token, Sound.BLOCK_WOOL_HIT, "sound"));
        }
        assertNull(ConfigEnums.sound(plugin, "missing", null, "sound"));
    }
}
