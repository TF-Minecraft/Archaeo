package com.nowko.archeology.config;

import com.nowko.archeology.item.ItemRef;
import org.bukkit.Material;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Role-item ids from {@code config.yml} feature sections (vanilla, ItemsAdder, or MMOItems).
 *
 * @param tracker {@code tracker.item}
 * @param prospect {@code prospect.item}
 * @param establish {@code establish.item}
 * @param brush {@code excavation.brush.item}
 * @param sketchPaper {@code sketch.paper}
 * @param sketchPencil {@code sketch.pencil}
 * @param excavationProfiles {@code excavation.tools.<id>.items}
 */
public record ItemMaterials(
        ItemRef tracker,
        ItemRef prospect,
        ItemRef establish,
        ItemRef brush,
        ItemRef sketchPaper,
        ItemRef sketchPencil,
        Map<String, List<ItemRef>> excavationProfiles
) {
    /**
     * @return packaged defaults matching {@code config.yml}
     */
    public static ItemMaterials defaults() {
        Map<String, List<ItemRef>> profiles = new LinkedHashMap<>();
        profiles.put("hand", ExcavationTool.hand().materials());
        profiles.put("light", ExcavationTool.light().materials());
        profiles.put("heavy", ExcavationTool.heavy().materials());
        return new ItemMaterials(
                ItemRef.vanilla(Material.RECOVERY_COMPASS),
                ItemRef.vanilla(Material.STONE_HOE),
                ItemRef.vanilla(Material.STICK),
                ItemRef.vanilla(Material.BRUSH),
                ItemRef.vanilla(Material.PAPER),
                ItemRef.vanilla(Material.FEATHER),
                Collections.unmodifiableMap(profiles)
        );
    }

    /**
     * @param profileId YAML key such as {@code hand}
     * @return whitelist for that profile, or empty if unknown
     */
    public List<ItemRef> profileMaterials(String profileId) {
        List<ItemRef> list = excavationProfiles.get(profileId);
        return list == null ? List.of() : list;
    }
}
