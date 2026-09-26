package net.tfminecraft.archaeo.sketch;

import static org.junit.Assert.*;

import java.util.*;
import net.tfminecraft.archaeo.config.*;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

public class CabinetLabBoardTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private PlayerMock player;
    @Before public void setUp() { server = MockBukkit.mock(); plugin = MockBukkit.createMockPlugin(); player = server.addPlayer(); }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test public void fieldContainsExactlyTheConfiguredDirtAndOnlyMaterialCompatibleStains() {
        for (int count : new int[]{1, 6, 18}) {
            CabinetLabBoard board = board(new LabSettings(count, LabSettings.defaults().tools(), LabSettings.defaults().stains()), List.of("rust"));
            board.open(player);
            assertSame(board, player.getOpenInventory().getTopInventory().getHolder());
            assertEquals(count, board.dirtyLeft());
            int dirty = 0;
            for (int slot = 0; slot < LabSettings.FIELD_SLOTS; slot++) {
                ItemStack pane = board.getInventory().getItem(slot);
                assertTrue(board.isLabStack(pane));
                assertFalse(board.isTool(pane));
                if (board.isDirty(slot)) {
                    dirty++;
                    assertEquals("rust", board.stainOf(slot).id());
                    assertEquals(Material.RED_STAINED_GLASS_PANE, pane.getType());
                    assertTrue(ChatColor.stripColor(pane.getItemMeta().getLore().get(0)).contains("Brush"));
                } else {
                    assertEquals(Material.GRAY_STAINED_GLASS_PANE, pane.getType());
                    assertEquals("Clean", ChatColor.stripColor(pane.getItemMeta().getDisplayName()));
                }
            }
            assertEquals(count, dirty);
        }
    }

    @Test public void wipingIsIdempotentAndNeverConsumesOrCleansRackTools() {
        CabinetLabBoard board = board(LabSettings.defaults(), List.of("rust")); board.open(player);
        int before = board.dirtyLeft(); int wiped = 0;
        for (int slot = 0; slot < 27; slot++) {
            boolean wasDirty = board.isDirty(slot);
            assertEquals(wasDirty, board.wipe(slot));
            if (wasDirty) {
                wiped++;
                assertEquals(Material.GRAY_STAINED_GLASS_PANE, board.getInventory().getItem(slot).getType());
                assertNull(board.stainOf(slot));
            }
            assertFalse(board.wipe(slot));
            assertEquals(before - wiped, board.dirtyLeft());
        }
        assertEquals(0, board.dirtyLeft());
        assertTrue(board.isToolSlot(21)); assertTrue(board.isToolSlot(22)); assertTrue(board.isToolSlot(23));
        assertFalse(board.wipe(-1)); assertFalse(board.wipe(27));
    }

    @Test public void rackCopiesAreIndependentAndPlainInventoryItemsAreNeverLabTools() {
        CabinetLabBoard board = board(LabSettings.defaults(), List.of("rust")); board.open(player);
        assertFalse(board.isToolSlot(20)); assertTrue(board.isToolSlot(21)); assertFalse(board.isToolSlot(24));
        ItemStack brush = board.copyTool(22);
        assertTrue(board.isTool(brush)); assertEquals("brush", board.toolId(brush));
        assertEquals(LabSettings.defaults().tool("brush"), board.tool(board.toolId(brush)));
        brush.setAmount(12);
        assertEquals(1, board.getInventory().getItem(22).getAmount());
        for (ItemStack ordinary : new ItemStack[]{null, new ItemStack(Material.AIR), new ItemStack(Material.BRUSH)}) {
            assertFalse(board.isTool(ordinary)); assertFalse(board.isLabStack(ordinary)); assertNull(board.toolId(ordinary));
        }
        assertTrue(board.copyTool(0).getType().isAir());
        assertTrue(board.copyTool(30).getType().isAir());
    }

    @Test public void absentMaterialStainsUseFirstConfiguredStain() {
        CabinetLabBoard fallback = board(LabSettings.defaults(), List.of("removed-stain")); fallback.open(player);
        for (int slot = 0; slot < LabSettings.FIELD_SLOTS; slot++) {
            if (fallback.isDirty(slot)) assertEquals("limescale", fallback.stainOf(slot).id());
        }
    }

    @Test public void customRackCapsAtNineAndShowsReadableWrappedDescriptions() {
        List<LabTool> tools = new ArrayList<>();
        for (int i = 0; i < 12; i++) tools.add(new LabTool("tool" + i, Material.STICK, i == 0 ? " " : "Tool " + i,
                i == 0 ? "A long description explaining how this tool should remove deposits without harming the artifact.\n\nUse gently." : "", null));
        LabStain stain = new LabStain("dust", "Dust", Material.BROWN_STAINED_GLASS_PANE, "tool0");
        CabinetLabBoard board = board(new LabSettings(18, tools, List.of(stain)), List.of("dust")); board.open(player);
        for (int slot = 18; slot < 27; slot++) {
            assertTrue(board.isToolSlot(slot)); assertEquals("tool" + (slot - 18), board.toolId(board.getInventory().getItem(slot)));
        }
        ItemStack first = board.copyTool(18);
        assertEquals("tool0", ChatColor.stripColor(first.getItemMeta().getDisplayName()));
        List<String> lore = first.getItemMeta().getLore().stream().map(ChatColor::stripColor).toList();
        assertTrue(lore.contains("Use gently.")); assertTrue(lore.size() > 4);
        for (String line : lore.subList(2, lore.size())) assertTrue(line, line.length() <= 34);
        assertEquals("Wipe with tool0.", ChatColor.stripColor(board.getInventory().getItem(0).getItemMeta().getLore().get(0)));
    }

    @Test public void missingRackToolStillNamesTheRequiredToolInStainInstructions() {
        LabStain stain = new LabStain("dust", "Dust", Material.BROWN_STAINED_GLASS_PANE, "missing-tool");
        CabinetLabBoard board = board(new LabSettings(18, LabSettings.defaults().tools(), List.of(stain)), List.of("dust")); board.open(player);
        assertEquals("Wipe with missing-tool.", ChatColor.stripColor(board.getInventory().getItem(0).getItemMeta().getLore().get(0)));
    }

    private CabinetLabBoard board(LabSettings lab, List<String> stains) {
        FindMaterial material = new FindMaterial("metal", "Metal", 1, Material.GRAY_STAINED_GLASS_PANE, stains);
        return new CabinetLabBoard(UUID.randomUUID(), UUID.randomUUID(), material, lab, player.getLocation(),
                new NamespacedKey(plugin, "lab_kind"), new NamespacedKey(plugin, "lab_tool"), new NamespacedKey(plugin, "lab_stain"));
    }
}
