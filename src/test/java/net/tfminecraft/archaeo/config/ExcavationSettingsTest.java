package net.tfminecraft.archaeo.config;

import net.tfminecraft.archaeo.item.ItemRef;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ExcavationSettingsTest {
    @Before public void setUp() { MockBukkit.mock(); }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test
    public void cueAffinityScalesOnlyMatchingConfiguredClasses() {
        CueSettings cues = new CueSettings(Set.of(Material.STONE), Set.of(Material.DIRT), 0.7, 1.35);
        assertEquals(14, cues.intervalTicks(10, DigClass.PICK, Material.STONE));
        assertEquals(14, cues.intervalTicks(10, DigClass.SHOVEL, Material.DIRT));
        assertEquals(28, cues.intervalTicks(10, DigClass.PICK, Material.DIRT));
        assertEquals(28, cues.intervalTicks(10, DigClass.SHOVEL, Material.STONE));
        for (DigClass dig : DigClass.values()) {
            assertEquals(20, cues.intervalTicks(10, dig, Material.AIR));
            assertEquals(20, cues.intervalTicks(10, dig, null));
        }
        assertEquals(20, cues.intervalTicks(10, null, Material.STONE));
        assertEquals(2, cues.intervalTicks(-1, DigClass.NONE, Material.STONE));
        assertEquals(2, new CueSettings(Set.of(Material.STONE), Set.of(), 0, 1).intervalTicks(10, DigClass.PICK, Material.STONE));
        assertEquals(2f, cues.progressPerCue(), 0f);
        assertFalse(cues.isPickFaster(null));
        assertFalse(cues.isShovelFaster(null));
        assertTrue(CueSettings.defaults().isPickFaster(Material.STONE));
        assertTrue(CueSettings.defaults().isShovelFaster(Material.DIRT));
        assertFalse(CueSettings.defaults().isPickFaster(Material.AIR));
        assertFalse(CueSettings.defaults().isShovelFaster(Material.AIR));
        assertTrue(CueSettings.copyOf(null).isEmpty());
        assertTrue(CueSettings.copyOf(Set.of()).isEmpty());
        Set<Material> source = new HashSet<>(Set.of(Material.STONE));
        Set<Material> copy = CueSettings.copyOf(source);
        source.clear();
        assertEquals(Set.of(Material.STONE), copy);
        assertThrows(UnsupportedOperationException.class, copy::clear);
    }

    @Test
    public void packagedProfilesHaveExpectedLiftsAndInferToolClass() {
        assertEquals(ExcavationTool.hand(), ExcavationTool.packaged(null));
        assertEquals(ExcavationTool.hand(), ExcavationTool.packaged("unknown"));
        assertEquals(ExcavationTool.light(), ExcavationTool.packaged("light"));
        assertEquals(ExcavationTool.heavy(), ExcavationTool.packaged("heavy"));
        assertEquals(ExcavationTool.superHeavy(), ExcavationTool.packaged("super-heavy"));
        assertEquals(1, ExcavationTool.hand().cellsOnLate());
        assertEquals(2, ExcavationTool.light().cellsOnLate());
        assertEquals(4, ExcavationTool.heavy().cellsOnLate());
        assertEquals(8, ExcavationTool.superHeavy().cellsOnLate());
        assertFalse(ExcavationTool.hand().usesCueTicks());
        assertFalse(ExcavationTool.hand().hasChimeFallback());
        ExcavationTool timed = new ExcavationTool("timed", List.of(), 2, null, 1, null, null, 1, 2, BreakShape.DOWN, 1, 20);
        assertTrue(timed.usesCueTicks());
        assertTrue(timed.hasChimeFallback());
        assertEquals(DigClass.NONE, ExcavationTool.hand().resolveDigClass(new ItemStack(Material.IRON_PICKAXE)));
        assertEquals(DigClass.PICK, timed.resolveDigClass(new ItemStack(Material.IRON_PICKAXE)));
        assertEquals(DigClass.SHOVEL, timed.resolveDigClass(new ItemStack(Material.IRON_SHOVEL)));
        assertEquals(DigClass.NONE, timed.resolveDigClass(new ItemStack(Material.BRICK)));
        assertEquals(DigClass.NONE, timed.resolveDigClass(new ItemStack(Material.AIR)));
        PickSettings settings = PickSettings.defaults();
        assertFalse(settings.unlimitedWorkday());
        for (int actions : new int[]{0, -1}) {
            assertTrue(new PickSettings(true, actions, true, true, 1, 1,
                    settings.conservation(), settings.limits(), true, settings.cues(), settings.profiles()).unlimitedWorkday());
        }
    }

    @Test
    public void artifactPoolsDefaultCopyClampAndResolveStoredTokens() {
        ArtifactTemplate missing = artifact(null, null);
        assertEquals(FindProfile.OBJECT, missing.profile());
        assertEquals("BRICK", missing.pickItem(null));
        assertEquals("BRICK", artifact(List.of(), null).pickItem(new Random(1)));
        List<ItemRef> source = new ArrayList<>(List.of(ItemRef.vanilla(Material.GOLD_NUGGET),
                new ItemRef(ItemRef.Kind.ITEMSADDER, "pack:pot", "")));
        ArtifactTemplate template = artifact(source, FindProfile.ANIMAL);
        source.clear();
        assertEquals(FindProfile.ANIMAL, template.profile());
        assertEquals(2, template.items().size());
        assertEquals(2, template.clampSize(1));
        assertEquals(3, template.clampSize(3));
        assertEquals(4, template.clampSize(5));
        assertEquals("GOLD_NUGGET", template.pickItem(null));
        Random random = mock(Random.class);
        when(random.nextInt(2)).thenReturn(1);
        assertEquals("itemsadder:pack:pot", template.pickItem(random));
        for (String token : new String[]{null, " ", " gold_nugget ", "bad_material"}) {
            assertEquals(Material.GOLD_NUGGET, template.resolveItem(token));
        }
        assertEquals(Material.DIAMOND, template.resolveItem("minecraft:diamond"));
        assertEquals(Material.BRICK, template.resolveItem("itemsadder:pack:pot"));
        assertEquals(Material.BRICK, template.resolveItem("AIR"));
        assertEquals(Material.BRICK, template.resolveItem("WATER"));
        assertThrows(UnsupportedOperationException.class, () -> template.items().clear());
    }

    @Test
    public void labLookupIgnoresCaseAndHandlesMissingCatalogs() {
        LabSettings defaults = LabSettings.defaults();
        assertEquals(6, defaults.dirtyCount());
        assertEquals("water", defaults.tool("WATER").id());
        assertEquals("rust", defaults.stain("RUST").id());
        for (String id : new String[]{null, " ", "unknown"}) {
            assertNull(defaults.tool(id));
            assertNull(defaults.stain(id));
        }
        assertNull(new LabSettings(1, null, null).tool("water"));
        assertNull(new LabSettings(1, null, null).stain("rust"));
    }

    private static ArtifactTemplate artifact(List<ItemRef> items, FindProfile profile) {
        return new ArtifactTemplate("pot", "Pot", 2, 4, "ceramic", "common", false, 10,
                Set.of("I"), Set.of("vessel"), profile, items, "notes");
    }
}
