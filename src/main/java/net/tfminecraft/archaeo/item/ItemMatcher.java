package net.tfminecraft.archaeo.item;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Optional ItemsAdder / MMOItems lookups via reflection so those jars stay off the compile classpath.
 */
public final class ItemMatcher {
    private final JavaPlugin plugin;
    private final Method iaByStack;
    private final Method iaNamespacedId;
    private final Method iaGetInstance;
    private final Method iaGetItemStack;
    private final Method iaFurnitureByBlock;
    private final Method iaFurnitureByEntity;
    private final Method iaFurnitureId;
    private final Method iaBlockByPlaced;
    private final Method iaBlockId;
    private final Object miPlugin;
    private final Method miTypeName;
    private final Method miId;
    private final Method miGetItem;

    /**
     * @param plugin Archaeo, or {@code null} for a vanilla-only matcher
     * @param iaByStack ItemsAdder {@code CustomStack.byItemStack}
     * @param iaNamespacedId ItemsAdder {@code getNamespacedID}
     * @param iaGetInstance ItemsAdder {@code CustomStack.getInstance}
     * @param iaGetItemStack ItemsAdder {@code CustomStack.getItemStack}
     * @param iaFurnitureByBlock ItemsAdder {@code CustomFurniture.byAlreadySpawned(Block)}
     * @param iaFurnitureByEntity ItemsAdder {@code CustomFurniture.byAlreadySpawned(Entity)}
     * @param iaFurnitureId ItemsAdder furniture {@code getNamespacedID}
     * @param iaBlockByPlaced ItemsAdder {@code CustomBlock.byAlreadyPlaced}
     * @param iaBlockId ItemsAdder custom-block {@code getNamespacedID}
     * @param miPlugin MMOItems plugin singleton
     * @param miTypeName MMOItems {@code getTypeName}
     * @param miId MMOItems {@code getID}
     * @param miGetItem MMOItems {@code getItem(Type, String)}
     */
    private ItemMatcher(
            JavaPlugin plugin,
            Method iaByStack,
            Method iaNamespacedId,
            Method iaGetInstance,
            Method iaGetItemStack,
            Method iaFurnitureByBlock,
            Method iaFurnitureByEntity,
            Method iaFurnitureId,
            Method iaBlockByPlaced,
            Method iaBlockId,
            Object miPlugin,
            Method miTypeName,
            Method miId,
            Method miGetItem
    ) {
        this.plugin = plugin;
        this.iaByStack = iaByStack;
        this.iaNamespacedId = iaNamespacedId;
        this.iaGetInstance = iaGetInstance;
        this.iaGetItemStack = iaGetItemStack;
        this.iaFurnitureByBlock = iaFurnitureByBlock;
        this.iaFurnitureByEntity = iaFurnitureByEntity;
        this.iaFurnitureId = iaFurnitureId;
        this.iaBlockByPlaced = iaBlockByPlaced;
        this.iaBlockId = iaBlockId;
        this.miPlugin = miPlugin;
        this.miTypeName = miTypeName;
        this.miId = miId;
        this.miGetItem = miGetItem;
    }

    /**
     * @return matcher with no pack plugins bound
     */
    public static ItemMatcher vanillaOnly() {
        return new ItemMatcher(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    /**
     * Binds APIs only when those plugins are enabled.
     *
     * @param plugin Archaeo instance
     * @return matcher; vanilla-only when neither pack plugin is present
     */
    public static ItemMatcher detect(JavaPlugin plugin) {
        Method iaByStack = null;
        Method iaNamespacedId = null;
        Method iaGetInstance = null;
        Method iaGetItemStack = null;
        Method iaFurnitureByBlock = null;
        Method iaFurnitureByEntity = null;
        Method iaFurnitureId = null;
        Method iaBlockByPlaced = null;
        Method iaBlockId = null;
        Object miPlugin = null;
        Method miTypeName = null;
        Method miId = null;
        Method miGetItem = null;
        if (pluginEnabled(plugin, "ItemsAdder")) {
            try {
                Class<?> customStack = Class.forName("dev.lone.itemsadder.api.CustomStack");
                iaByStack = customStack.getMethod("byItemStack", ItemStack.class);
                iaNamespacedId = customStack.getMethod("getNamespacedID");
                iaGetInstance = customStack.getMethod("getInstance", String.class);
                iaGetItemStack = customStack.getMethod("getItemStack");
            } catch (ClassNotFoundException | NoSuchMethodException exception) {
                plugin.getLogger().log(Level.WARNING, "ItemsAdder is enabled but its API could not be bound.", exception);
            }
            try {
                Class<?> furniture = Class.forName("dev.lone.itemsadder.api.CustomFurniture");
                iaFurnitureByBlock = furniture.getMethod("byAlreadySpawned", Block.class);
                iaFurnitureByEntity = furniture.getMethod("byAlreadySpawned", Entity.class);
                iaFurnitureId = furniture.getMethod("getNamespacedID");
            } catch (ClassNotFoundException | NoSuchMethodException exception) {
                plugin.getLogger().warning("ItemsAdder furniture API is missing; placed cabinets cannot match pack furniture.");
            }
            try {
                Class<?> customBlock = Class.forName("dev.lone.itemsadder.api.CustomBlock");
                iaBlockByPlaced = customBlock.getMethod("byAlreadyPlaced", Block.class);
                iaBlockId = customBlock.getMethod("getNamespacedID");
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                // Custom blocks are optional.
            }
        }
        if (pluginEnabled(plugin, "MMOItems")) {
            try {
                Class<?> mmoClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
                miPlugin = mmoClass.getField("plugin").get(null);
                miTypeName = mmoClass.getMethod("getTypeName", ItemStack.class);
                miId = mmoClass.getMethod("getID", ItemStack.class);
                Class<?> mmoType = Class.forName("net.Indyuce.mmoitems.api.Type");
                miGetItem = mmoClass.getMethod("getItem", mmoType, String.class);
            } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException | IllegalAccessException exception) {
                plugin.getLogger().log(Level.WARNING, "MMOItems is enabled but its API could not be bound.", exception);
            }
        }
        return new ItemMatcher(
                plugin,
                iaByStack,
                iaNamespacedId,
                iaGetInstance,
                iaGetItemStack,
                iaFurnitureByBlock,
                iaFurnitureByEntity,
                iaFurnitureId,
                iaBlockByPlaced,
                iaBlockId,
                miPlugin,
                miTypeName,
                miId,
                miGetItem);
    }

    /**
     * @param stack main-hand stack, or {@code null}
     * @param ref whitelist entry
     * @return whether this stack is that entry
     */
    public boolean matches(ItemStack stack, ItemRef ref) {
        return switch (ref.kind()) {
            case VANILLA -> matchesVanilla(stack, ref);
            case ITEMSADDER -> matchesItemsAdder(stack, ref);
            case MMOITEMS -> matchesMmoItems(stack, ref);
        };
    }

    /**
     * @param block clicked world block, or {@code null}
     * @param ref configured cabinet
     * @return whether this block is that vanilla type or ItemsAdder furniture/block
     */
    public boolean matchesPlaced(Block block, ItemRef ref) {
        if (block == null || ref == null) {
            return false;
        }
        return switch (ref.kind()) {
            case VANILLA -> block.getType() == ref.vanillaMaterial();
            case ITEMSADDER -> idEquals(itemsAdderPlacedId(block), ref.primary());
            case MMOITEMS -> false;
        };
    }

    /**
     * @param entity clicked furniture entity, or {@code null}
     * @param ref configured cabinet
     * @return whether this entity is that ItemsAdder furniture
     */
    public boolean matchesEntity(Entity entity, ItemRef ref) {
        if (entity == null || ref == null || ref.kind() != ItemRef.Kind.ITEMSADDER) {
            return false;
        }
        return idEquals(itemsAdderEntityId(entity), ref.primary());
    }

    /**
     * @param namespacedId ItemsAdder {@code namespace:id}, or {@code null}
     * @param ref configured cabinet
     * @return whether the id is that furniture
     */
    public boolean matchesNamespacedId(String namespacedId, ItemRef ref) {
        return ref != null && ref.kind() == ItemRef.Kind.ITEMSADDER && idEquals(namespacedId, ref.primary());
    }

    /**
     * Builds a stack for staff give. Pack plugins must be installed for non-vanilla refs.
     *
     * @param ref configured item
     * @return cloneable stack, never {@code null} (air when the pack item is missing)
     */
    public ItemStack create(ItemRef ref) {
        return switch (ref.kind()) {
            case VANILLA -> new ItemStack(ref.vanillaMaterial());
            case ITEMSADDER -> createItemsAdder(ref);
            case MMOITEMS -> createMmoItems(ref);
        };
    }

    /**
     * @param stack live stack
     * @return whether ItemsAdder or MMOItems claims this stack
     */
    public boolean isCustom(ItemStack stack) {
        return itemsAdderId(stack) != null || mmoType(stack) != null;
    }

    /**
     * @param stack live stack
     * @param ref vanilla material
     * @return whether the Bukkit type matches and the stack is not a pack item
     */
    private boolean matchesVanilla(ItemStack stack, ItemRef ref) {
        Material wanted = ref.vanillaMaterial();
        Material type = stack == null || stack.getType().isAir()
                ? Material.AIR
                : stack.getType();
        if (type != wanted) {
            return false;
        }
        if (wanted == Material.AIR) {
            return true;
        }
        return !isCustom(stack);
    }

    /**
     * @param stack live stack
     * @param ref ItemsAdder id
     * @return whether namespaced ids match
     */
    private boolean matchesItemsAdder(ItemStack stack, ItemRef ref) {
        String id = itemsAdderId(stack);
        return id != null && id.equalsIgnoreCase(ref.primary());
    }

    /**
     * @param stack live stack
     * @param ref MMOItems type+id
     * @return whether type and id match
     */
    private boolean matchesMmoItems(ItemStack stack, ItemRef ref) {
        String type = mmoType(stack);
        String id = mmoId(stack);
        if (type == null || id == null) {
            return false;
        }
        return type.equalsIgnoreCase(ref.primary()) && id.equalsIgnoreCase(ref.secondary());
    }

    /**
     * @param ref ItemsAdder namespaced id
     * @return pack stack or air
     */
    private ItemStack createItemsAdder(ItemRef ref) {
        // detect binds getItemStack after getInstance, so one check covers both.
        if (iaGetItemStack == null) {
            warn("ItemsAdder is not loaded; cannot create " + ref.primary());
            return new ItemStack(Material.AIR);
        }
        try {
            Object custom = iaGetInstance.invoke(null, ref.primary());
            if (custom == null) {
                warn("Unknown ItemsAdder item: " + ref.primary());
                return new ItemStack(Material.AIR);
            }
            Object stack = iaGetItemStack.invoke(custom);
            if (stack instanceof ItemStack item && !item.getType().isAir()) {
                return item.clone();
            }
        } catch (ReflectiveOperationException exception) {
            warn("Could not create ItemsAdder item " + ref.primary() + ": " + exception.getMessage());
        }
        return new ItemStack(Material.AIR);
    }

    /**
     * @param ref MMOItems type and id
     * @return pack stack or air
     */
    private ItemStack createMmoItems(ItemRef ref) {
        // detect binds getItem after reading the singleton, and MMOItems sets that in its constructor,
        // long before it reports enabled, so a bound getItem always has its plugin.
        if (miGetItem == null) {
            warn("MMOItems is not loaded; cannot create " + ref.primary() + ":" + ref.secondary());
            return new ItemStack(Material.AIR);
        }
        try {
            Object types = miPlugin.getClass().getMethod("getTypes").invoke(miPlugin);
            Object type = types.getClass().getMethod("get", String.class).invoke(types, ref.primary());
            if (type == null) {
                warn("Unknown MMOItems type: " + ref.primary());
                return new ItemStack(Material.AIR);
            }
            Object stack = miGetItem.invoke(miPlugin, type, ref.secondary());
            if (stack instanceof ItemStack item && !item.getType().isAir()) {
                return item.clone();
            }
            warn("Unknown MMOItems item: " + ref.primary() + ":" + ref.secondary());
        } catch (ReflectiveOperationException exception) {
            warn("Could not create MMOItems item " + ref.primary() + ":" + ref.secondary() + ": " + exception.getMessage());
        }
        return new ItemStack(Material.AIR);
    }

    /**
     * @param stack live stack
     * @return {@code namespace:id}, or {@code null}
     */
    private String itemsAdderId(ItemStack stack) {
        // detect binds getNamespacedID after byItemStack, so one check covers both.
        if (iaNamespacedId == null || stack == null) {
            return null;
        }
        try {
            Object custom = iaByStack.invoke(null, stack);
            if (custom == null) {
                return null;
            }
            Object id = iaNamespacedId.invoke(custom);
            return id == null ? null : String.valueOf(id).toLowerCase(Locale.ROOT);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    /**
     * @param block placed block
     * @return ItemsAdder furniture or custom-block id, or {@code null}
     */
    private String itemsAdderPlacedId(Block block) {
        String furniture = invokeNamespaced(iaFurnitureByBlock, iaFurnitureId, block);
        if (furniture != null) {
            return furniture;
        }
        return invokeNamespaced(iaBlockByPlaced, iaBlockId, block);
    }

    /**
     * @param entity furniture entity
     * @return namespaced id, or {@code null}
     */
    private String itemsAdderEntityId(Entity entity) {
        return invokeNamespaced(iaFurnitureByEntity, iaFurnitureId, entity);
    }

    /**
     * @param lookup static {@code byAlreadySpawned} / {@code byAlreadyPlaced}
     * @param idMethod {@code getNamespacedID}
     * @param argument block or entity
     * @return id, or {@code null}
     */
    private String invokeNamespaced(Method lookup, Method idMethod, Object argument) {
        // detect binds each id method after its lookup; matchesPlaced/matchesEntity reject null arguments.
        if (idMethod == null) {
            return null;
        }
        try {
            Object custom = lookup.invoke(null, argument);
            if (custom == null) {
                return null;
            }
            Object id = idMethod.invoke(custom);
            return id == null ? null : String.valueOf(id).toLowerCase(Locale.ROOT);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    /**
     * @param left live id
     * @param right configured ItemsAdder id, never {@code null}
     * @return case-insensitive match
     */
    private static boolean idEquals(String left, String right) {
        return left != null && left.equalsIgnoreCase(right);
    }

    /**
     * @param stack live stack
     * @return MMOItems type name, or {@code null}
     */
    private String mmoType(ItemStack stack) {
        if (miTypeName == null || stack == null) {
            return null;
        }
        try {
            Object type = miTypeName.invoke(null, stack);
            return type == null ? null : String.valueOf(type);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    /**
     * @param stack live stack
     * @return MMOItems template id, or {@code null}
     */
    private String mmoId(ItemStack stack) {
        if (miId == null || stack == null) {
            return null;
        }
        try {
            Object id = miId.invoke(null, stack);
            return id == null ? null : String.valueOf(id);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    /**
     * @param message warning line
     */
    private void warn(String message) {
        if (plugin != null) {
            plugin.getLogger().warning(message);
        }
    }

    /**
     * @param plugin Archaeo
     * @param name Bukkit plugin name
     * @return whether that plugin is enabled
     */
    private static boolean pluginEnabled(JavaPlugin plugin, String name) {
        Plugin other = plugin.getServer().getPluginManager().getPlugin(name);
        return other != null && other.isEnabled();
    }
}
