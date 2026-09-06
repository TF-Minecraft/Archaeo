package com.nowko.archeology.excavation;

import com.nowko.archeology.config.ExcavationTool;
import com.nowko.archeology.config.PickSettings;
import com.nowko.archeology.item.ItemMatcher;
import com.nowko.archeology.item.ItemRef;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * Named excavation profiles and the stacks that may run the cut clock on prism fill.
 * {@link Material#AIR} means an empty hand. Outside the dig site these items stay vanilla.
 */
public class DigTools {
    private List<ExcavationTool> profiles = PickSettings.defaults().profiles();
    private ItemMatcher matcher = ItemMatcher.vanillaOnly();

    /**
     * @param matcher ItemsAdder / MMOItems lookup; vanilla-only when those plugins are missing
     */
    public void setMatcher(ItemMatcher matcher) {
        this.matcher = matcher == null ? ItemMatcher.vanillaOnly() : matcher;
    }

    /**
     * @param profiles named tools from {@code excavation.tools}
     */
    public void setProfiles(List<ExcavationTool> profiles) {
        this.profiles = profiles == null || profiles.isEmpty()
                ? PickSettings.defaults().profiles()
                : List.copyOf(profiles);
    }

    /**
     * First matching profile wins (YAML order).
     *
     * @param stack main-hand stack, or {@code null}
     * @return profile for this stack
     */
    public Optional<ExcavationTool> match(ItemStack stack) {
        for (ExcavationTool profile : profiles) {
            for (ItemRef ref : profile.materials()) {
                if (matcher.matches(stack, ref)) {
                    return Optional.of(profile);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * @param ref configured id
     * @return pack or vanilla stack (air when missing)
     */
    public ItemStack create(ItemRef ref) {
        return matcher.create(ref);
    }

    /**
     * @param stack main-hand stack, or {@code null}
     * @return whether this stack may excavate on the dig site
     */
    public boolean isAllowed(ItemStack stack) {
        return match(stack).isPresent();
    }

    /**
     * @return a whitelist stack for {@code /archaeo give tool}
     */
    public ItemStack sampleStack() {
        for (ExcavationTool profile : profiles) {
            for (ItemRef ref : profile.materials()) {
                if (ref.isAir()) {
                    continue;
                }
                ItemStack stack = matcher.create(ref);
                if (!stack.getType().isAir()) {
                    return stack;
                }
            }
        }
        return new ItemStack(Material.STONE_PICKAXE);
    }

    /**
     * Configured excavation item ids, skipping empty-hand entries. Order follows YAML.
     *
     * @return tokens for {@code /archaeo give tool}
     */
    public List<String> giveTokens() {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (ExcavationTool profile : profiles) {
            for (ItemRef ref : profile.materials()) {
                if (ref.isAir()) {
                    continue;
                }
                tokens.add(ref.commandToken());
            }
        }
        return List.copyOf(tokens);
    }

    /**
     * @param token staff argument, case-insensitive
     * @return stack for that whitelist id, or empty when unknown
     */
    public Optional<ItemStack> createByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String want = token.trim();
        for (ExcavationTool profile : profiles) {
            for (ItemRef ref : profile.materials()) {
                if (ref.isAir()) {
                    continue;
                }
                if (ref.commandToken().equalsIgnoreCase(want)) {
                    ItemStack stack = matcher.create(ref);
                    if (!stack.getType().isAir()) {
                        return Optional.of(stack);
                    }
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Clears an older Archaeo mine-lock on a whitelist tool so {@code getBreakSpeed} can be sampled.
     *
     * @param stack main-hand stack, or {@code null}
     */
    public void restoreVanillaSpeedIfSealed(ItemStack stack) {
        if (!isAllowed(stack) || stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !needsSpeedRepair(meta)) {
            return;
        }
        Collection<AttributeModifier> modifiers = meta.getAttributeModifiers(Attribute.BLOCK_BREAK_SPEED);
        if (modifiers != null) {
            for (AttributeModifier modifier : new ArrayList<>(modifiers)) {
                meta.removeAttributeModifier(Attribute.BLOCK_BREAK_SPEED, modifier);
            }
        }
        ItemMeta vanilla = new ItemStack(stack.getType()).getItemMeta();
        if (vanilla != null) {
            meta.setTool(vanilla.getTool());
        }
        stack.setItemMeta(meta);
    }

    /**
     * @param meta item meta
     * @return whether an older zero-speed lock is still on this stack
     */
    private static boolean needsSpeedRepair(ItemMeta meta) {
        Collection<AttributeModifier> modifiers = meta.getAttributeModifiers(Attribute.BLOCK_BREAK_SPEED);
        if (modifiers != null && !modifiers.isEmpty()) {
            return true;
        }
        return meta.getTool().getDefaultMiningSpeed() <= 0f;
    }
}
