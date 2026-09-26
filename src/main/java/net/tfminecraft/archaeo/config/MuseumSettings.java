package net.tfminecraft.archaeo.config;

import net.tfminecraft.archaeo.item.ItemMatcher;
import net.tfminecraft.archaeo.item.ItemRef;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;

import java.util.List;
import java.util.Locale;

/**
 * World supports that can hold a recovered find and open its plaque on sneak-use.
 *
 * @param displays vanilla names ({@code ITEM_FRAME}, {@code SHELF}, {@code ITEM_DISPLAY}) or ItemsAdder ids
 */
public record MuseumSettings(List<ItemRef> displays) {
    /**
     * @return packaged defaults matching {@code config.yml}
     */
    public static MuseumSettings defaults() {
        return new MuseumSettings(List.of(
                ItemRef.vanilla(Material.ITEM_FRAME),
                ItemRef.vanilla(Material.GLOW_ITEM_FRAME),
                ItemRef.vanilla(Material.ARMOR_STAND),
                ItemRef.vanilla(Material.LECTERN),
                new ItemRef(ItemRef.Kind.VANILLA, "SHELF", "")));
    }

    /**
     * @param material vanilla support type
     * @return whether that material is on the whitelist
     */
    public boolean allowsVanilla(Material material) {
        if (allowsNamed(material.name())) {
            return true;
        }
        return material.name().endsWith("_SHELF") && allowsNamed("SHELF");
    }

    /**
     * @param frame hung exhibit
     * @return whether this frame kind is listed
     */
    public boolean allowsFrame(ItemFrame frame) {
        String name = frame.getType().name();
        if ("GLOW_ITEM_FRAME".equals(name)) {
            return allowsNamed("GLOW_ITEM_FRAME");
        }
        return allowsNamed("ITEM_FRAME");
    }

    /**
     * Vanilla entity supports. ItemsAdder furniture entities are matched by {@link #allowsSupport}.
     *
     * @param entity frame, armor stand, or item display
     * @return whether this support is listed
     */
    public boolean allowsEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (entity instanceof ItemFrame frame) {
            return allowsFrame(frame);
        }
        if (entity instanceof ArmorStand) {
            return allowsNamed("ARMOR_STAND");
        }
        if (entity instanceof ItemDisplay) {
            return allowsNamed("ITEM_DISPLAY");
        }
        return false;
    }

    /**
     * @param namespacedId ItemsAdder furniture id, or {@code null}
     * @param entity furniture entity, or {@code null}
     * @param block block under the furniture, or {@code null}
     * @param matcher pack lookups
     * @return whether this support is listed
     */
    public boolean allowsSupport(
            String namespacedId,
            Entity entity,
            Block block,
            ItemMatcher matcher
    ) {
        if (allowsEntity(entity)) {
            return true;
        }
        // Every shelf block is a *_SHELF material, so allowsVanilla already covers SHELF.
        if (block != null && allowsVanilla(block.getType())) {
            return true;
        }
        for (ItemRef ref : displays) {
            if (ref.kind() != ItemRef.Kind.ITEMSADDER) {
                continue;
            }
            if (matcher.matchesNamespacedId(namespacedId, ref)
                    || matcher.matchesEntity(entity, ref)
                    || matcher.matchesPlaced(block, ref)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param name YAML token such as {@code SHELF}
     * @return whether that name is listed
     */
    private boolean allowsNamed(String name) {
        String wanted = name.toUpperCase(Locale.ROOT);
        for (ItemRef ref : displays) {
            if (ref.kind() == ItemRef.Kind.VANILLA && wanted.equalsIgnoreCase(ref.primary())) {
                return true;
            }
        }
        return false;
    }
}
