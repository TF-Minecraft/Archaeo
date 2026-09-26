package net.tfminecraft.archaeo.config;

import net.tfminecraft.archaeo.item.ItemRef;
import org.bukkit.Material;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * One entry from {@code artifacts.yml}: size range, tags, relic flag, and item(s) to give on recovery.
 *
 * @param id template key
 * @param displayName English name shown to players
 * @param sizeMin minimum connected cells
 * @param sizeMax maximum connected cells
 * @param material flavor tag
 * @param rarity optional display tier override; blank means derive from {@code weight} via {@code rarity-from-weight}
 * @param relic whether this counts toward the relic budget
 * @param weight generation weight
 * @param strata stratum ids this template may spawn in
 * @param tags matching tags for hints
 * @param profile which station questions this template uses
 * @param items vanilla / ItemsAdder / MMOItems refs; generation picks one when there are several
 * @param studyNotes English note revealed when the piece is studied at camp; may be blank
 */
public record ArtifactTemplate(
        String id,
        String displayName,
        int sizeMin,
        int sizeMax,
        String material,
        String rarity,
        boolean relic,
        int weight,
        Set<String> strata,
        Set<String> tags,
        FindProfile profile,
        List<ItemRef> items,
        String studyNotes
) {
    /**
     * Missing profile is treated as an object so older YAML still loads. An empty item pool
     * becomes brick so recovery always has a vanilla stack.
     */
    public ArtifactTemplate {
        profile = profile == null ? FindProfile.OBJECT : profile;
        items = items == null || items.isEmpty()
                ? List.of(ItemRef.vanilla(Material.BRICK))
                : List.copyOf(items);
    }

    /**
     * Clamps a requested cell count into this template's size range.
     *
     * @param requested desired size
     * @return value between {@code sizeMin} and {@code sizeMax}
     */
    public int clampSize(int requested) {
        return Math.max(sizeMin, Math.min(sizeMax, requested));
    }

    /**
     * Picks the catalog item this instance will use. One pool entry returns that token;
     * several entries pick uniformly.
     *
     * @param random site RNG; {@code null} uses the first entry
     * @return stored token ({@code GOLD_NUGGET}, {@code itemsadder:ns:id}, or {@code mmoitems:TYPE:id})
     */
    public String pickItem(Random random) {
        ItemRef ref = items.size() == 1 || random == null
                ? items.getFirst()
                : items.get(random.nextInt(items.size()));
        return ref.commandToken();
    }

    /**
     * Resolves a stored token or a catalog pool entry. Unknown names fall back to the first pool item.
     *
     * @param chosen token stored on the find, or {@code null} to use the first catalog entry
     * @return matching ref
     */
    public ItemRef resolveRef(String chosen) {
        if (chosen != null && !chosen.isBlank()) {
            String token = chosen.trim();
            for (ItemRef ref : items) {
                // Vanilla tokens are the bare material name; "minecraft:" tokens resolve via parse below.
                if (ref.commandToken().equalsIgnoreCase(token)) {
                    return ref;
                }
            }
            return ItemRef.parse(null, token).orElseGet(items::getFirst);
        }
        return items.getFirst();
    }

    /**
     * Vanilla Bukkit type for this find when the catalog entry is vanilla. Pack ids become brick
     * so callers that only need a material still have an icon.
     *
     * @param chosen token stored on the find, or {@code null} to use the first catalog entry
     * @return item material
     */
    public Material resolveItem(String chosen) {
        ItemRef ref = resolveRef(chosen);
        if (ref.kind() == ItemRef.Kind.VANILLA) {
            Material material = ref.vanillaMaterial();
            if (!material.isAir() && material.isItem()) {
                return material;
            }
        }
        return Material.BRICK;
    }
}
