package net.tfminecraft.archaeo.item;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * One whitelist entry: a Bukkit material, an ItemsAdder {@code namespace:id}, or an MMOItems type+id.
 *
 * @param kind source plugin (or vanilla)
 * @param primary Bukkit material name, ItemsAdder namespaced id, or MMOItems type
 * @param secondary MMOItems item id; unused otherwise
 */
public record ItemRef(Kind kind, String primary, String secondary) {
    /**
     * Where the stack is defined.
     */
    public enum Kind {
        /** Bukkit {@link Material}, including {@link Material#AIR} for an empty hand. */
        VANILLA,
        /** ItemsAdder {@code namespace:id}. */
        ITEMSADDER,
        /** MMOItems type plus template id. */
        MMOITEMS
    }

    /**
     * @return empty-hand vanilla ref
     */
    public static ItemRef air() {
        return new ItemRef(Kind.VANILLA, Material.AIR.name(), "");
    }

    /**
     * @param material Bukkit type
     * @return vanilla ref
     */
    public static ItemRef vanilla(Material material) {
        Material type = material == null || material.isAir() ? Material.AIR : material;
        return new ItemRef(Kind.VANILLA, type.name(), "");
    }

    /**
     * Default Hand Pick whitelist: empty hand plus each vanilla pickaxe and shovel listed by name.
     *
     * @return ordered refs matching older {@code pick.tools} lists
     */
    public static List<ItemRef> excavationDefaults() {
        List<ItemRef> refs = new ArrayList<>();
        refs.add(air());
        refs.addAll(pickaxes());
        refs.addAll(shovels());
        return List.copyOf(refs);
    }

    /**
     * @return every vanilla pickaxe
     */
    public static List<ItemRef> pickaxes() {
        return List.of(
                vanilla(Material.WOODEN_PICKAXE),
                vanilla(Material.STONE_PICKAXE),
                vanilla(Material.COPPER_PICKAXE),
                vanilla(Material.IRON_PICKAXE),
                vanilla(Material.GOLDEN_PICKAXE),
                vanilla(Material.DIAMOND_PICKAXE),
                vanilla(Material.NETHERITE_PICKAXE)
        );
    }

    /**
     * @return every vanilla shovel
     */
    public static List<ItemRef> shovels() {
        return List.of(
                vanilla(Material.WOODEN_SHOVEL),
                vanilla(Material.STONE_SHOVEL),
                vanilla(Material.COPPER_SHOVEL),
                vanilla(Material.IRON_SHOVEL),
                vanilla(Material.GOLDEN_SHOVEL),
                vanilla(Material.DIAMOND_SHOVEL),
                vanilla(Material.NETHERITE_SHOVEL)
        );
    }

    /**
     * @param plugin logger for unknown tokens, or {@code null} to stay quiet
     * @param tokens YAML lines
     * @return parsed refs; {@link #excavationDefaults()} when the list is empty
     */
    public static List<ItemRef> parseAll(JavaPlugin plugin, List<String> tokens) {
        List<ItemRef> refs = parseList(plugin, tokens);
        return refs.isEmpty() ? excavationDefaults() : refs;
    }

    /**
     * @param plugin logger for unknown tokens, or {@code null} to stay quiet
     * @param tokens YAML lines
     * @return parsed refs, possibly empty
     */
    public static List<ItemRef> parseList(JavaPlugin plugin, List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return List.of();
        }
        List<ItemRef> refs = new ArrayList<>();
        for (String token : tokens) {
            parse(plugin, token).ifPresent(refs::add);
        }
        return List.copyOf(refs);
    }

    /**
     * Parses a YAML list whose entries may be strings or one-key maps from unquoted colons.
     *
     * @param plugin logger for unknown tokens, or {@code null} to stay quiet
     * @param values {@code getList} result
     * @return parsed refs, possibly empty
     */
    public static List<ItemRef> parseYamlList(JavaPlugin plugin, List<?> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<ItemRef> refs = new ArrayList<>();
        for (Object value : values) {
            parse(plugin, yamlToken(value)).ifPresent(refs::add);
        }
        return List.copyOf(refs);
    }

    /**
     * Turns a YAML scalar or a one-key nested map back into {@code prefix:a:b}.
     *
     * @param value Bukkit {@code get} / list entry
     * @return parseable token, or {@code null}
     */
    public static String yamlToken(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text.isBlank() ? null : text.trim();
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof org.bukkit.configuration.ConfigurationSection section) {
            return yamlToken(section.getValues(false));
        }
        if (value instanceof Map<?, ?> map) {
            if (map.size() != 1) {
                return null;
            }
            Map.Entry<?, ?> entry = map.entrySet().iterator().next();
            String key = String.valueOf(entry.getKey()).trim();
            // yamlToken never hands back blank text: blank scalars become null.
            String rest = yamlToken(entry.getValue());
            if (rest == null) {
                return key.isBlank() ? null : key;
            }
            return key + ":" + rest;
        }
        return String.valueOf(value).trim();
    }

    /**
     * @param plugin logger for unknown tokens, or {@code null} to stay quiet
     * @param raw YAML token
     * @param fallback used when {@code raw} is blank or invalid
     * @return parsed ref
     */
    public static ItemRef parseOr(JavaPlugin plugin, String raw, ItemRef fallback) {
        return parse(plugin, raw).orElse(fallback);
    }

    /**
     * @param plugin logger for unknown tokens, or {@code null} to stay quiet
     * @param raw YAML token
     * @return parsed ref, or empty when the token is blank or unknown
     */
    public static Optional<ItemRef> parse(JavaPlugin plugin, String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String token = raw.trim();
        int colon = token.indexOf(':');
        if (colon > 0) {
            String prefix = token.substring(0, colon).toLowerCase(Locale.ROOT);
            String rest = token.substring(colon + 1).trim();
            if ("itemsadder".equals(prefix) || "ia".equals(prefix)) {
                return itemsAdder(plugin, token, rest);
            }
            if ("mmoitems".equals(prefix) || "mi".equals(prefix)) {
                return mmoItems(plugin, token, rest);
            }
            if ("minecraft".equals(prefix)) {
                return vanillaName(plugin, rest);
            }
            // Any other namespace is a pack id. Material.matchMaterial strips the colon, so asking it
            // first would turn an ItemsAdder "glow:stone" into vanilla GLOWSTONE.
            return itemsAdder(plugin, token, token);
        }
        String upper = token.toUpperCase(Locale.ROOT);
        if ("AIR".equals(upper) || "HAND".equals(upper) || "EMPTY".equals(upper) || "EMPTY_HAND".equals(upper)) {
            return Optional.of(air());
        }
        if ("ITEM_DISPLAY".equals(upper) || "SHELF".equals(upper)) {
            return Optional.of(new ItemRef(Kind.VANILLA, upper, ""));
        }
        return vanillaName(plugin, token);
    }

    /**
     * @return whether this is an empty-hand entry
     */
    public boolean isAir() {
        return kind == Kind.VANILLA && vanillaMaterial().isAir();
    }

    /**
     * YAML-style id for staff commands and tab complete.
     *
     * @return {@code STONE_PICKAXE}, {@code itemsadder:ns:id}, or {@code mmoitems:TYPE:id}
     */
    public String commandToken() {
        return switch (kind) {
            case VANILLA -> primary;
            case ITEMSADDER -> "itemsadder:" + primary;
            case MMOITEMS -> "mmoitems:" + primary + ":" + secondary;
        };
    }

    /**
     * @return Bukkit type when this is vanilla; {@link Material#AIR} otherwise
     */
    public Material vanillaMaterial() {
        if (kind != Kind.VANILLA) {
            return Material.AIR;
        }
        Material material = Material.matchMaterial(primary);
        return material == null ? Material.AIR : material;
    }

    /**
     * @param plugin logger
     * @param token full YAML line
     * @param rest after the prefix
     * @return ItemsAdder ref
     */
    private static Optional<ItemRef> itemsAdder(JavaPlugin plugin, String token, String rest) {
        int split = rest.indexOf(':');
        if (split <= 0 || split == rest.length() - 1) {
            warn(plugin, "Invalid ItemsAdder id (want namespace:id): " + token);
            return Optional.empty();
        }
        return Optional.of(new ItemRef(Kind.ITEMSADDER, rest.toLowerCase(Locale.ROOT), ""));
    }

    /**
     * @param plugin logger
     * @param token full YAML line
     * @param rest after the prefix
     * @return MMOItems ref
     */
    private static Optional<ItemRef> mmoItems(JavaPlugin plugin, String token, String rest) {
        int split = rest.indexOf(':');
        if (split <= 0 || split == rest.length() - 1) {
            warn(plugin, "Invalid MMOItems id (want TYPE:id): " + token);
            return Optional.empty();
        }
        String type = rest.substring(0, split).trim();
        String id = rest.substring(split + 1).trim();
        return Optional.of(new ItemRef(Kind.MMOITEMS, type.toUpperCase(Locale.ROOT), id));
    }

    /**
     * @param plugin logger
     * @param name Bukkit material token
     * @return vanilla ref
     */
    private static Optional<ItemRef> vanillaName(JavaPlugin plugin, String name) {
        Material material = Material.matchMaterial(name.trim());
        if (material == null) {
            warn(plugin, "Unknown item: " + name);
            return Optional.empty();
        }
        return Optional.of(vanilla(material));
    }

    /**
     * @param plugin logger, or {@code null} to stay quiet
     * @param message warning line
     */
    private static void warn(JavaPlugin plugin, String message) {
        if (plugin != null) {
            plugin.getLogger().warning(message);
        }
    }
}
