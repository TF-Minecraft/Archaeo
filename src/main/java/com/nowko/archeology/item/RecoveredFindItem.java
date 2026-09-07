package com.nowko.archeology.item;

import com.nowko.archeology.config.ArtifactTemplate;
import com.nowko.archeology.config.CatalogRegistry;
import com.nowko.archeology.config.InterpretationTemplate;
import com.nowko.archeology.establish.CampNames;
import com.nowko.archeology.model.BuriedFind;
import com.nowko.archeology.model.FindInterpretation;
import com.nowko.archeology.model.Site;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds and updates the field piece: a tag pointing at the excavation archive, plus lore for what
 * has already been revealed.
 */
public class RecoveredFindItem {
    private static final int LINE_WIDTH = 34;

    private final NamespacedKey markerKey;
    private final NamespacedKey siteIdKey;
    private final NamespacedKey siteNameKey;
    private final NamespacedKey findIdKey;
    private final NamespacedKey findNumberKey;
    private final NamespacedKey artifactIdKey;
    private final NamespacedKey stratumKey;
    private final NamespacedKey conservationKey;
    private final NamespacedKey buriedConservationKey;
    private final NamespacedKey gradeKey;
    private final NamespacedKey fieldDamagedKey;
    private final NamespacedKey priorDamageKey;
    private final NamespacedKey recoveredByKey;
    private final NamespacedKey recoveredAtKey;
    private final NamespacedKey studiedKey;

    /**
     * @param plugin owner of the PDC keys
     */
    public RecoveredFindItem(JavaPlugin plugin) {
        this.markerKey = new NamespacedKey(plugin, "recovered_find");
        this.siteIdKey = new NamespacedKey(plugin, "site_id");
        this.siteNameKey = new NamespacedKey(plugin, "site_name");
        this.findIdKey = new NamespacedKey(plugin, "find_id");
        this.findNumberKey = new NamespacedKey(plugin, "find_number");
        this.artifactIdKey = new NamespacedKey(plugin, "artifact_id");
        this.stratumKey = new NamespacedKey(plugin, "stratum");
        this.conservationKey = new NamespacedKey(plugin, "conservation");
        this.buriedConservationKey = new NamespacedKey(plugin, "buried_conservation");
        this.gradeKey = new NamespacedKey(plugin, "conservation_grade");
        this.fieldDamagedKey = new NamespacedKey(plugin, "field_damaged");
        this.priorDamageKey = new NamespacedKey(plugin, "disturbed_before_dig");
        this.recoveredByKey = new NamespacedKey(plugin, "recovered_by");
        this.recoveredAtKey = new NamespacedKey(plugin, "recovered_at");
        this.studiedKey = new NamespacedKey(plugin, "studied");
    }

    /**
     * @param template catalog row (material and display name)
     * @param site excavation this piece left
     * @param find dossier row being lifted
     * @param recoverer player who finished the last brush
     * @param grade condition band label for the final percentage
     * @param fieldDamaged whether the dig itself wounded the piece
     * @param catalogs rarity, notes, and interpretation labels
     * @return stack to drop, or {@code null} if the template item is unknown
     */
    public ItemStack create(
            ArtifactTemplate template,
            Site site,
            BuriedFind find,
            UUID recoverer,
            String grade,
            boolean fieldDamaged,
            CatalogRegistry catalogs
    ) {
        Material material = Material.matchMaterial(template.item());
        if (material == null || material.isAir()) {
            material = Material.BRICK;
        }
        ItemStack stack = new ItemStack(material);
        write(stack, template, site, find, recoverer, grade, fieldDamaged, catalogs);
        return stack;
    }

    /**
     * Rewrites lore and PDC on every matching stack the player is carrying, so study and
     * interpretation show up without returning to the cut.
     *
     * @param player carrier
     * @param site excavation
     * @param find archive row
     * @param template catalog row
     * @param grade condition band label
     * @param catalogs rarity, notes, and interpretation labels
     */
    public void refreshCarried(
            Player player,
            Site site,
            BuriedFind find,
            ArtifactTemplate template,
            String grade,
            CatalogRegistry catalogs
    ) {
        if (player == null || find == null || find.getId() == null) {
            return;
        }
        UUID recoverer = find.getRecoveredBy() == null ? player.getUniqueId() : find.getRecoveredBy();
        boolean fieldDamaged = find.isFieldDamaged();
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (!isThisFind(stack, find.getId())) {
                continue;
            }
            write(stack, template, site, find, recoverer, grade, fieldDamaged, catalogs);
            inventory.setItem(i, stack);
        }
        ItemStack cursor = player.getOpenInventory().getCursor();
        if (isThisFind(cursor, find.getId())) {
            write(cursor, template, site, find, recoverer, grade, fieldDamaged, catalogs);
            player.getOpenInventory().setCursor(cursor);
        }
    }

    /**
     * @param player carrier
     * @param findId archive row
     * @return whether a matching recovered piece is in their inventory or cursor
     */
    public boolean isCarrying(Player player, UUID findId) {
        if (player == null || findId == null) {
            return false;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isThisFind(stack, findId)) {
                return true;
            }
        }
        return isThisFind(player.getOpenInventory().getCursor(), findId);
    }

    /**
     * @param stack candidate
     * @param findId archive row
     * @return whether this stack is that recovered piece
     */
    public boolean isThisFind(ItemStack stack, UUID findId) {
        if (stack == null || stack.getType().isAir() || findId == null) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        var pdc = meta.getPersistentDataContainer();
        if (!pdc.has(markerKey, PersistentDataType.BYTE)) {
            return false;
        }
        String raw = pdc.get(findIdKey, PersistentDataType.STRING);
        return findId.toString().equals(raw);
    }

    /**
     * @param stack candidate
     * @return find id, or {@code null} if this is not a recovered Archaeo piece
     */
    public UUID findIdOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        var pdc = meta.getPersistentDataContainer();
        if (!pdc.has(markerKey, PersistentDataType.BYTE)) {
            return null;
        }
        String raw = pdc.get(findIdKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * @param stack stack to stamp
     * @param template catalog row
     * @param site excavation
     * @param find archive row
     * @param recoverer who lifted it
     * @param grade condition band label
     * @param fieldDamaged whether the dig wounded it
     * @param catalogs rarity, notes, and interpretation labels
     */
    private void write(
            ItemStack stack,
            ArtifactTemplate template,
            Site site,
            BuriedFind find,
            UUID recoverer,
            String grade,
            boolean fieldDamaged,
            CatalogRegistry catalogs
    ) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        String name = template.displayName();
        meta.setDisplayName(ChatColor.WHITE + name);
        meta.setLore(lore(template, site, find, grade, fieldDamaged, catalogs));
        var pdc = meta.getPersistentDataContainer();
        pdc.set(markerKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(siteIdKey, PersistentDataType.STRING, site.getId().toString());
        pdc.set(siteNameKey, PersistentDataType.STRING, siteName(site));
        pdc.set(findIdKey, PersistentDataType.STRING, find.getId().toString());
        pdc.set(findNumberKey, PersistentDataType.INTEGER, find.getFindNumber());
        pdc.set(artifactIdKey, PersistentDataType.STRING, find.getArtifactId());
        pdc.set(stratumKey, PersistentDataType.STRING, find.getStratumId() == null ? "" : find.getStratumId());
        pdc.set(conservationKey, PersistentDataType.INTEGER, find.getConservation());
        pdc.set(buriedConservationKey, PersistentDataType.INTEGER, find.getBuriedConservation());
        pdc.set(gradeKey, PersistentDataType.STRING, grade == null ? "" : grade);
        pdc.set(fieldDamagedKey, PersistentDataType.BYTE, fieldDamaged ? (byte) 1 : (byte) 0);
        pdc.set(priorDamageKey, PersistentDataType.BYTE, find.isDisturbedBeforeDig() ? (byte) 1 : (byte) 0);
        if (recoverer != null) {
            pdc.set(recoveredByKey, PersistentDataType.STRING, recoverer.toString());
        }
        if (find.getRecoveredAt() != null) {
            pdc.set(recoveredAtKey, PersistentDataType.STRING, find.getRecoveredAt().toString());
        }
        pdc.set(studiedKey, PersistentDataType.BYTE, find.isStudied() ? (byte) 1 : (byte) 0);
        stack.setItemMeta(meta);
    }

    /**
     * @param template catalog row
     * @param site excavation
     * @param find archive row
     * @param grade condition band label
     * @param fieldDamaged whether the dig wounded it
     * @param catalogs rarity, notes, and interpretation labels
     * @return lore lines
     */
    public List<String> lore(
            ArtifactTemplate template,
            Site site,
            BuriedFind find,
            String grade,
            boolean fieldDamaged,
            CatalogRegistry catalogs
    ) {
        List<String> lore = new ArrayList<>();
        String number = find.publicNumber(site.getSerial());
        if (number != null) {
            lore.add(ChatColor.GOLD + number);
        }
        lore.add(ChatColor.GRAY + siteName(site));
        lore.add(ChatColor.DARK_GRAY + "Stratum " + find.getStratumId());
        String condition = ChatColor.DARK_GRAY + "Conservation " + find.getConservation() + "%";
        if (grade != null && !grade.isBlank()) {
            condition += ChatColor.DARK_GRAY + " · " + grade;
        }
        lore.add(condition);
        if (find.isDisturbedBeforeDig()) {
            lore.add(ChatColor.GOLD + "Disturbed before the dig");
        }
        if (fieldDamaged) {
            lore.add(ChatColor.RED + "Hurt while digging");
        }
        lore.add(ChatColor.AQUA + find.catalogStatusLabel());
        if (find.isStudied() && template != null) {
            if (template.rarity() != null && !template.rarity().isBlank()) {
                lore.add(ChatColor.GRAY + "Rarity: " + ChatColor.WHITE + template.rarity());
            }
            String notes = find.getStudyNotes();
            if (notes == null || notes.isBlank()) {
                notes = template.studyNotes();
            }
            if (notes != null && !notes.isBlank()) {
                wrap(lore, notes);
            }
        }
        if (catalogs != null) {
            for (FindInterpretation reading : find.getInterpretations()) {
                InterpretationTemplate interpretation = catalogs.interpretation(reading.interpretationId());
                String label = interpretation == null ? reading.interpretationId() : interpretation.displayName();
                String author = CampNames.of(null, reading.author());
                lore.add(ChatColor.DARK_GRAY + "According to " + author + ": " + ChatColor.WHITE + label
                        + ChatColor.DARK_GRAY + " (" + reading.confidence().displayName() + ")");
            }
        }
        return lore;
    }

    /**
     * @param site excavation
     * @return name used on the tag
     */
    public static String siteName(Site site) {
        if (site.getName() == null || site.getName().isBlank()) {
            return "Excavation #" + site.getSerial();
        }
        return site.getName();
    }

    /**
     * @param lore lore being built
     * @param text one study note
     */
    private static void wrap(List<String> lore, String text) {
        StringBuilder line = new StringBuilder();
        String prefix = "";
        for (String word : text.split("\\s+")) {
            if (line.length() > 0 && line.length() + word.length() + 1 > LINE_WIDTH) {
                lore.add(ChatColor.WHITE + prefix + line);
                line.setLength(0);
                prefix = "  ";
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lore.add(ChatColor.WHITE + prefix + line);
        }
    }
}

