package com.nowko.archeology.item;

import com.nowko.archeology.config.ArtifactTemplate;
import com.nowko.archeology.model.BuriedFind;
import com.nowko.archeology.model.Site;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds the dropped field piece: catalog item plus provenance and conservation in PDC.
 */
public class RecoveredFindItem {
    private final NamespacedKey markerKey;
    private final NamespacedKey siteIdKey;
    private final NamespacedKey siteNameKey;
    private final NamespacedKey findIdKey;
    private final NamespacedKey artifactIdKey;
    private final NamespacedKey stratumKey;
    private final NamespacedKey conservationKey;
    private final NamespacedKey buriedConservationKey;
    private final NamespacedKey gradeKey;
    private final NamespacedKey fieldDamagedKey;
    private final NamespacedKey recoveredByKey;
    private final NamespacedKey recoveredAtKey;

    /**
     * @param plugin owner of the PDC keys
     */
    public RecoveredFindItem(JavaPlugin plugin) {
        this.markerKey = new NamespacedKey(plugin, "recovered_find");
        this.siteIdKey = new NamespacedKey(plugin, "site_id");
        this.siteNameKey = new NamespacedKey(plugin, "site_name");
        this.findIdKey = new NamespacedKey(plugin, "find_id");
        this.artifactIdKey = new NamespacedKey(plugin, "artifact_id");
        this.stratumKey = new NamespacedKey(plugin, "stratum");
        this.conservationKey = new NamespacedKey(plugin, "conservation");
        this.buriedConservationKey = new NamespacedKey(plugin, "buried_conservation");
        this.gradeKey = new NamespacedKey(plugin, "conservation_grade");
        this.fieldDamagedKey = new NamespacedKey(plugin, "field_damaged");
        this.recoveredByKey = new NamespacedKey(plugin, "recovered_by");
        this.recoveredAtKey = new NamespacedKey(plugin, "recovered_at");
    }

    /**
     * @param template catalog row (material and display name)
     * @param site excavation this piece left
     * @param find dossier row being lifted
     * @param recoverer player who finished the last brush
     * @param grade condition band label for the final percentage
     * @param fieldDamaged whether the dig itself wounded the piece
     * @return stack to drop, or {@code null} if the template item is unknown
     */
    public ItemStack create(
            ArtifactTemplate template,
            Site site,
            BuriedFind find,
            UUID recoverer,
            String grade,
            boolean fieldDamaged
    ) {
        Material material = Material.matchMaterial(template.item());
        if (material == null || material.isAir()) {
            material = Material.BRICK;
        }
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        String name = template.displayName();
        meta.setDisplayName(ChatColor.WHITE + name);
        List<String> lore = new ArrayList<>();
        String siteName = site.getName() == null || site.getName().isBlank()
                ? ("Excavation #" + site.getSerial())
                : site.getName();
        lore.add(ChatColor.GRAY + siteName);
        lore.add(ChatColor.DARK_GRAY + "Stratum " + find.getStratumId());
        String condition = ChatColor.DARK_GRAY + "Conservation " + find.getConservation() + "%";
        if (grade != null && !grade.isBlank()) {
            condition += ChatColor.DARK_GRAY + " · " + grade;
        }
        lore.add(condition);
        if (fieldDamaged) {
            lore.add(ChatColor.RED + "Hurt while digging");
        }
        meta.setLore(lore);
        var pdc = meta.getPersistentDataContainer();
        pdc.set(markerKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(siteIdKey, PersistentDataType.STRING, site.getId().toString());
        pdc.set(siteNameKey, PersistentDataType.STRING, siteName);
        pdc.set(findIdKey, PersistentDataType.STRING, find.getId().toString());
        pdc.set(artifactIdKey, PersistentDataType.STRING, find.getArtifactId());
        pdc.set(stratumKey, PersistentDataType.STRING, find.getStratumId());
        pdc.set(conservationKey, PersistentDataType.INTEGER, find.getConservation());
        pdc.set(buriedConservationKey, PersistentDataType.INTEGER, find.getBuriedConservation());
        pdc.set(gradeKey, PersistentDataType.STRING, grade == null ? "" : grade);
        pdc.set(fieldDamagedKey, PersistentDataType.BYTE, fieldDamaged ? (byte) 1 : (byte) 0);
        pdc.set(recoveredByKey, PersistentDataType.STRING, recoverer.toString());
        pdc.set(recoveredAtKey, PersistentDataType.STRING, Instant.now().toString());
        stack.setItemMeta(meta);
        return stack;
    }
}
