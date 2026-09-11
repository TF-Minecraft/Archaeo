package com.nowko.archeology.config;

import com.nowko.archeology.item.ItemMatcher;
import com.nowko.archeology.item.ItemRef;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Shelf;
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
        if (material == null) {
            return false;
        }
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
        if (frame == null) {
            return false;
        }
        String name = frame.getType().name();
        if ("GLOW_ITEM_FRAME".equals(name)) {
            return allowsNamed("GLOW_ITEM_FRAME");
        }
        return allowsNamed("ITEM_FRAME");
    }

    /**
     * @param entity armor stand, item display, or ItemsAdder furniture
     * @param matcher pack lookups
     * @return whether this support is listed
     */
    public boolean allowsEntity(Entity entity, ItemMatcher matcher) {
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
        if (matcher == null) {
            return false;
        }
        for (ItemRef ref : displays) {
            if (matcher.matchesEntity(entity, ref)) {
                return true;
            }
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
        if (allowsEntity(entity, matcher)) {
            return true;
        }
        if (block != null) {
            if (allowsVanilla(block.getType())) {
                return true;
            }
            if (block.getState() instanceof Shelf && allowsNamed("SHELF")) {
                return true;
            }
        }
        if (matcher == null) {
            return false;
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
        if (name == null) {
            return false;
        }
        String wanted = name.toUpperCase(Locale.ROOT);
        for (ItemRef ref : displays) {
            if (ref.kind() == ItemRef.Kind.VANILLA && wanted.equalsIgnoreCase(ref.primary())) {
                return true;
            }
        }
        return false;
    }
}
