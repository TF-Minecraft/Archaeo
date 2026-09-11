package com.nowko.archeology.museum;

import com.nowko.archeology.config.CatalogRegistry;
import com.nowko.archeology.establish.CampFindBoard;
import com.nowko.archeology.item.ItemMatcher;
import com.nowko.archeology.item.RecoveredFindItem;
import com.nowko.archeology.model.BuriedFind;
import com.nowko.archeology.model.Site;
import com.nowko.archeology.site.SiteRepository;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Lectern;
import org.bukkit.block.Shelf;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Opens the archive plaque from a recovered Archaeo piece sitting in a configured display.
 * Empty supports and vanilla items are ignored: sneak then still means Minecraft. Normal clicks
 * stay vanilla so exhibits can be hung, rotated, and taken.
 */
public final class MuseumListener implements Listener {
    private final SiteRepository sites;
    private final CatalogRegistry catalogs;
    private final RecoveredFindItem recovered;
    private ItemMatcher matcher = ItemMatcher.vanillaOnly();

    /**
     * @param sites live excavation archive
     * @param catalogs labels on the plaque and {@code museum.displays}
     * @param recovered PDC tags that point a displayed stack at its dossier row
     */
    public MuseumListener(SiteRepository sites, CatalogRegistry catalogs, RecoveredFindItem recovered) {
        this.sites = sites;
        this.catalogs = catalogs;
        this.recovered = recovered;
    }

    /**
     * @param matcher ItemsAdder furniture lookup
     */
    public void setMatcher(ItemMatcher matcher) {
        this.matcher = matcher == null ? ItemMatcher.vanillaOnly() : matcher;
    }

    /**
     * Shelf, lectern, or listed ItemsAdder furniture: sneak-use opens the plaque instead of
     * inserting, swapping, or opening vanilla pages.
     *
     * @param event block use
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlock(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!sneaking(player)) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !catalogs.museum().allowsSupport(null, null, block, matcher)) {
            return;
        }
        ItemStack displayed = firstRecovered(displayedOnBlock(block.getState(), event.getClickedPosition()));
        if (displayed == null) {
            return;
        }
        openPlaque(player, displayed);
        denyBlockUse(event);
    }

    /**
     * Item frame, glow frame, item display, or listed ItemsAdder furniture entity.
     *
     * @param event entity use
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!sneaking(event.getPlayer())) {
            return;
        }
        Entity clicked = event.getRightClicked();
        if (!catalogs.museum().allowsSupport(null, clicked, null, matcher)) {
            return;
        }
        ItemStack displayed = firstRecovered(displayedOnEntity(clicked));
        if (displayed == null) {
            return;
        }
        openPlaque(event.getPlayer(), displayed);
        event.setCancelled(true);
    }

    /**
     * Armor stand slot under the cursor: sneak-use reads that equipment instead of swapping it.
     *
     * @param event stand manipulate
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStand(PlayerArmorStandManipulateEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!sneaking(event.getPlayer())) {
            return;
        }
        if (!catalogs.museum().allowsVanilla(Material.ARMOR_STAND)
                && !catalogs.museum().allowsEntity(event.getRightClicked(), matcher)) {
            return;
        }
        ItemStack worn = event.getArmorStandItem();
        if (!recovered.isRecovered(worn)) {
            return;
        }
        openPlaque(event.getPlayer(), worn);
        event.setCancelled(true);
    }

    /**
     * ItemsAdder furniture click (from {@code FurnitureInteractEvent}).
     *
     * @param player clicker
     * @param namespacedId furniture id, or {@code null}
     * @param entity furniture entity, or {@code null}
     * @param block block under the furniture, or {@code null}
     * @param sneaking whether sneak is held
     * @return whether the plaque opened
     */
    public boolean tryOpenFromSupport(
            Player player,
            String namespacedId,
            Entity entity,
            Block block,
            boolean sneaking
    ) {
        if (player == null || !sneaking) {
            return false;
        }
        if (!catalogs.museum().allowsSupport(namespacedId, entity, block, matcher)) {
            return false;
        }
        List<ItemStack> stacks = new ArrayList<>();
        stacks.addAll(displayedOnEntity(entity));
        if (block != null) {
            stacks.addAll(displayedOnBlock(block.getState(), null));
        }
        ItemStack displayed = firstRecovered(stacks);
        if (displayed == null) {
            return false;
        }
        openPlaque(player, displayed);
        return true;
    }

    /**
     * Resolves the stack the player is pointing at on a lectern or shelf.
     *
     * @param state lectern or shelf
     * @param click relative click, {@code 0..1} per axis, or {@code null}
     * @return displayed stacks, possibly empty
     */
    private static List<ItemStack> displayedOnBlock(BlockState state, Vector click) {
        List<ItemStack> stacks = new ArrayList<>();
        if (state instanceof Lectern lectern) {
            stacks.add(lectern.getInventory().getItem(0));
            return stacks;
        }
        if (state instanceof Shelf shelf) {
            if (click == null) {
                for (ItemStack stack : shelf.getInventory().getContents()) {
                    stacks.add(stack);
                }
                return stacks;
            }
            int slot = shelf.getSlot(click);
            if (slot >= 0) {
                stacks.add(shelf.getInventory().getItem(slot));
            }
            return stacks;
        }
        return stacks;
    }

    /**
     * @param entity frame, display, armor stand, or furniture root
     * @return stacks that entity is showing
     */
    private static List<ItemStack> displayedOnEntity(Entity entity) {
        List<ItemStack> stacks = new ArrayList<>();
        if (entity instanceof ItemFrame frame) {
            stacks.add(frame.getItem());
            return stacks;
        }
        if (entity instanceof ItemDisplay display) {
            stacks.add(display.getItemStack());
            return stacks;
        }
        if (entity instanceof ArmorStand stand) {
            EntityEquipment equipment = stand.getEquipment();
            if (equipment == null) {
                return stacks;
            }
            stacks.add(equipment.getItemInMainHand());
            stacks.add(equipment.getItemInOffHand());
            stacks.add(equipment.getHelmet());
            stacks.add(equipment.getChestplate());
            stacks.add(equipment.getLeggings());
            stacks.add(equipment.getBoots());
        }
        return stacks;
    }

    /**
     * @param stacks candidates, may contain {@code null}
     * @return first recovered find, or {@code null}
     */
    private ItemStack firstRecovered(List<ItemStack> stacks) {
        if (stacks == null) {
            return null;
        }
        for (ItemStack stack : stacks) {
            if (recovered.isRecovered(stack)) {
                return stack;
            }
        }
        return null;
    }

    /**
     * Opens the consultation plaque for a recovered Archaeo piece. Call only after
     * {@link RecoveredFindItem#isRecovered(ItemStack)} is true.
     *
     * @param player viewer
     * @param stack recovered find on the support
     */
    private void openPlaque(Player player, ItemStack stack) {
        UUID siteId = recovered.siteIdOf(stack);
        UUID findId = recovered.findIdOf(stack);
        Site site = siteId == null ? null : sites.findById(siteId).orElse(null);
        if (site == null || !site.mayConsult() || findId == null) {
            player.sendMessage("That excavation record is missing.");
            return;
        }
        BuriedFind find = site.findById(findId).orElse(null);
        if (find == null) {
            player.sendMessage("That excavation record is missing.");
            return;
        }
        new CampFindBoard(site.getId(), find.getId(), catalogs, true).open(player, site);
    }

    /**
     * Stops the lectern book screen and shelf insert/take for this click.
     *
     * @param event interact to deny
     */
    private static void denyBlockUse(PlayerInteractEvent event) {
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
    }

    /**
     * Shift key or sneak pose; entity use sometimes reports {@code isSneaking()} as false.
     *
     * @param player viewer
     * @return whether sneak is held
     */
    private static boolean sneaking(Player player) {
        return player.isSneaking() || player.getPose() == Pose.SNEAKING;
    }
}
