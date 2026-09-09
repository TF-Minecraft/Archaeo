package com.nowko.archeology.sketch;

import com.nowko.archeology.config.FindMaterial;
import com.nowko.archeology.config.LabSettings;
import com.nowko.archeology.config.LabStain;
import com.nowko.archeology.config.LabTool;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 27-slot lab: a glass field in the material colours, dirt scattered through it, tools on the last row.
 */
final class CabinetLabBoard implements InventoryHolder {
    private static final String KIND_DIRTY = "dirty";
    private static final String KIND_CLEAN = "clean";
    private static final String KIND_TOOL = "tool";

    private final UUID siteId;
    private final UUID findId;
    private final FindMaterial material;
    private final LabSettings lab;
    private final Location cabinet;
    private final NamespacedKey kindKey;
    private final NamespacedKey toolKey;
    private final NamespacedKey stainKey;
    private Inventory inventory;
    private int dirtyLeft;
    private boolean finished;
    private boolean warnedWrongTool;

    /**
     * @param siteId excavation
     * @param findId archive row
     * @param material lab profile
     * @param lab rack, stain catalogue, and dirt budget
     * @param cabinet block used for cues, or {@code null}
     * @param kindKey PDC kind ({@code dirty}, {@code clean}, {@code tool})
     * @param toolKey PDC rack id on a picked tool
     * @param stainKey PDC stain id on a dirty pane
     */
    CabinetLabBoard(
            UUID siteId,
            UUID findId,
            FindMaterial material,
            LabSettings lab,
            Location cabinet,
            NamespacedKey kindKey,
            NamespacedKey toolKey,
            NamespacedKey stainKey
    ) {
        this.siteId = siteId;
        this.findId = findId;
        this.material = material;
        this.lab = lab;
        this.cabinet = cabinet;
        this.kindKey = kindKey;
        this.toolKey = toolKey;
        this.stainKey = stainKey;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * @return excavation
     */
    UUID siteId() {
        return siteId;
    }

    /**
     * @return archive row
     */
    UUID findId() {
        return findId;
    }

    /**
     * @return lab profile
     */
    FindMaterial material() {
        return material;
    }

    /**
     * @return cabinet location for particles, or {@code null}
     */
    Location cabinet() {
        return cabinet;
    }

    /**
     * @return whether every dirty pane has been wiped
     */
    boolean finished() {
        return finished;
    }

    /**
     * Marks the wipe complete so closing does not treat it as an abort.
     */
    void markFinished() {
        this.finished = true;
    }

    /**
     * @return whether a wrong-tool hint was already sent
     */
    boolean warnedWrongTool() {
        return warnedWrongTool;
    }

    /**
     * Stops repeating the wrong-tool line.
     */
    void markWarnedWrongTool() {
        this.warnedWrongTool = true;
    }

    /**
     * @return dirty panes still on the field
     */
    int dirtyLeft() {
        return dirtyLeft;
    }

    /**
     * @param player worker
     */
    void open(org.bukkit.entity.Player player) {
        String title = ChatColor.DARK_AQUA + material.firstStepGerund();
        inventory = Bukkit.createInventory(this, 27, title);
        fillField();
        fillTools();
        player.openInventory(inventory);
    }

    /**
     * @param slot field or rack index
     * @return whether that cell is still dirty
     */
    boolean isDirty(int slot) {
        return KIND_DIRTY.equals(kindOf(slotItem(slot)));
    }

    /**
     * @param slot rack index
     * @return whether that cell is a rack tool
     */
    boolean isToolSlot(int slot) {
        return KIND_TOOL.equals(kindOf(slotItem(slot)));
    }

    /**
     * @param stack cursor or rack stack
     * @return whether this is a picked or rack lab tool
     */
    boolean isTool(ItemStack stack) {
        return KIND_TOOL.equals(kindOf(stack));
    }

    /**
     * @param stack cursor or rack stack
     * @return rack id, or {@code null}
     */
    String toolId(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
    }

    /**
     * @param id rack key
     * @return tool row, or {@code null}
     */
    LabTool tool(String id) {
        return lab.tool(id);
    }

    /**
     * @param slot dirty field index
     * @return stain on that pane, or {@code null}
     */
    LabStain stainOf(int slot) {
        ItemStack stack = slotItem(slot);
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        String id = stack.getItemMeta().getPersistentDataContainer().get(stainKey, PersistentDataType.STRING);
        return lab.stain(id);
    }

    /**
     * Turns a dirty field cell into the clean pane.
     *
     * @param slot field index
     * @return whether a dirty cell was cleaned
     */
    boolean wipe(int slot) {
        if (!isDirty(slot) || inventory == null) {
            return false;
        }
        inventory.setItem(slot, cleanPane());
        dirtyLeft = Math.max(0, dirtyLeft - 1);
        return true;
    }

    /**
     * @param slot rack index
     * @return a cursor copy of that tool, or empty
     */
    ItemStack copyTool(int slot) {
        ItemStack there = slotItem(slot);
        return isTool(there) ? there.clone() : new ItemStack(org.bukkit.Material.AIR);
    }

    /**
     * @param stack candidate
     * @return whether this stack was spawned by the lab window
     */
    boolean isLabStack(ItemStack stack) {
        return kindOf(stack) != null;
    }

    /**
     * Scatters stains from this material through the field.
     */
    private void fillField() {
        List<LabStain> pool = stainPool();
        int want = pool.isEmpty() ? 0 : Math.max(1, Math.min(LabSettings.FIELD_SLOTS, lab.dirtyCount()));
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < LabSettings.FIELD_SLOTS; slot++) {
            slots.add(slot);
        }
        Collections.shuffle(slots, ThreadLocalRandom.current());
        boolean[] dirty = new boolean[LabSettings.FIELD_SLOTS];
        for (int i = 0; i < want; i++) {
            dirty[slots.get(i)] = true;
        }
        dirtyLeft = want;
        for (int slot = 0; slot < LabSettings.FIELD_SLOTS; slot++) {
            if (dirty[slot]) {
                LabStain stain = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
                inventory.setItem(slot, dirtyPane(stain));
            } else {
                inventory.setItem(slot, cleanPane());
            }
        }
    }

    /**
     * @return stains this material may roll, skipping unknown ids
     */
    private List<LabStain> stainPool() {
        List<LabStain> pool = new ArrayList<>();
        List<String> ids = material.stains();
        if (ids != null) {
            for (String id : ids) {
                LabStain stain = lab.stain(id);
                if (stain != null) {
                    pool.add(stain);
                }
            }
        }
        if (pool.isEmpty() && lab.stains() != null && !lab.stains().isEmpty()) {
            pool.add(lab.stains().get(0));
        }
        return pool;
    }

    /**
     * Places rack tools on the last row, centred.
     */
    private void fillTools() {
        List<LabTool> tools = lab.tools() == null ? List.of() : lab.tools();
        int count = Math.min(9, tools.size());
        int start = LabSettings.FIELD_SLOTS + Math.max(0, (9 - count) / 2);
        for (int i = 0; i < count; i++) {
            inventory.setItem(start + i, toolStack(tools.get(i)));
        }
    }

    /**
     * @return cleaned field pane in the material colour
     */
    private ItemStack cleanPane() {
        ItemStack stack = new ItemStack(material.cleanPane());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.WHITE + "Clean");
            meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, KIND_CLEAN);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * @param stain dirt kind on this cell
     * @return dirty field pane
     */
    private ItemStack dirtyPane(LabStain stain) {
        ItemStack stack = new ItemStack(stain.pane());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + stain.label());
            LabTool needed = lab.tool(stain.toolId());
            String toolName = needed == null || needed.displayName() == null || needed.displayName().isBlank()
                    ? stain.toolId()
                    : needed.displayName();
            meta.setLore(List.of(ChatColor.GRAY + "Wipe with " + toolName + "."));
            var pdc = meta.getPersistentDataContainer();
            pdc.set(kindKey, PersistentDataType.STRING, KIND_DIRTY);
            pdc.set(stainKey, PersistentDataType.STRING, stain.id());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * @param tool rack row
     * @return labelled tool that stays on the rack
     */
    private ItemStack toolStack(LabTool tool) {
        ItemStack stack = new ItemStack(tool.item());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            String name = tool.displayName() == null || tool.displayName().isBlank()
                    ? tool.id()
                    : tool.displayName();
            meta.setDisplayName(ChatColor.WHITE + name);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Click to pick up.");
            lore.add(ChatColor.GRAY + "Click the dirt to clean it.");
            addDescription(lore, tool.description());
            meta.setLore(lore);
            var pdc = meta.getPersistentDataContainer();
            pdc.set(kindKey, PersistentDataType.STRING, KIND_TOOL);
            pdc.set(toolKey, PersistentDataType.STRING, tool.id());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * @param slot inventory index
     * @return stack, or {@code null}
     */
    private ItemStack slotItem(int slot) {
        if (inventory == null || slot < 0 || slot >= inventory.getSize()) {
            return null;
        }
        return inventory.getItem(slot);
    }

    /**
     * @param stack candidate
     * @return kind token, or {@code null}
     */
    private String kindOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(kindKey, PersistentDataType.STRING);
    }

    /**
     * Appends the configured how-to under the pick-up lines. Only these GUI copies carry it.
     *
     * @param lore lore being built
     * @param text tool description, or blank
     */
    private static void addDescription(List<String> lore, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (String paragraph : text.split("\\R")) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            wrap(lore, trimmed);
        }
    }

    /**
     * @param lore lore being built
     * @param text one paragraph
     */
    private static void wrap(List<String> lore, String text) {
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (line.length() > 0 && line.length() + word.length() + 1 > 34) {
                lore.add(ChatColor.DARK_GRAY + line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lore.add(ChatColor.DARK_GRAY + line.toString());
        }
    }
}
