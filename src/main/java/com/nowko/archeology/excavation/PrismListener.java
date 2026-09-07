package com.nowko.archeology.excavation;

import com.nowko.archeology.model.Site;
import com.nowko.archeology.site.SiteRepository;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optionally locks all prism fill of an established excavation; vanilla holes
 * on claimed or unclaimed ruin prisms wound the dossier and smash exposed finds.
 */
public class PrismListener implements Listener {
    private static final long MESSAGE_COOLDOWN_MS = 3000L;

    private final SiteRepository sites;
    private final DigTools tools;
    private final Map<UUID, Long> lastWarn = new ConcurrentHashMap<>();
    private boolean protectDigSite;

    /**
     * @param sites ruin and excavation dossiers
     * @param tools excavation whitelist: those items use {@link HandPickListener} on prism fill
     * @param protectDigSite whether every present stratum band is locked against vanilla damage
     */
    public PrismListener(SiteRepository sites, DigTools tools, boolean protectDigSite) {
        this.sites = sites;
        this.tools = tools;
        this.protectDigSite = protectDigSite;
    }

    /**
     * @param protectDigSite after reload
     */
    public void setProtectDigSite(boolean protectDigSite) {
        this.protectDigSite = protectDigSite;
    }

    /**
     * @param event vanilla (or other plugin) break
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (tools.isAllowed(player.getInventory().getItemInMainHand())) {
            return;
        }
        if (!protectedFill(event.getBlock())) {
            return;
        }
        event.setCancelled(true);
        warn(player);
    }

    /**
     * Vanilla break that went through: wound the dossier, including unclaimed ruin prisms.
     *
     * @param event break that actually happened
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakWound(BlockBreakEvent event) {
        woundCell(event.getBlock(), event.getPlayer());
    }

    /**
     * Tools not on the excavation whitelist must not start mining protected prism fill.
     *
     * @param event start of vanilla damage
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent event) {
        if (!protectedFill(event.getBlock())) {
            return;
        }
        if (tools.isAllowed(event.getPlayer().getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
    }

    /**
     * @param event fire
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (protectedFill(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event fire that consumed the block
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurnWound(BlockBurnEvent event) {
        woundCell(event.getBlock(), null);
    }

    /**
     * Endermen, wither, farmland tramp, and similar.
     *
     * @param event entity changing a block
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        if (protectedFill(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event entity change that was not cancelled
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeWound(EntityChangeBlockEvent event) {
        Player player = event.getEntity() instanceof Player breaker ? breaker : null;
        woundCell(event.getBlock(), player);
    }

    /**
     * @param event block explosion
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent event) {
        stripFill(event.blockList());
    }

    /**
     * @param event block explosion after protected prism cells were removed from the list
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplodeWound(BlockExplodeEvent event) {
        woundCells(event.blockList(), null);
    }

    /**
     * @param event entity explosion
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        stripFill(event.blockList());
    }

    /**
     * @param event entity explosion after protected prism cells were removed
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplodeWound(EntityExplodeEvent event) {
        Player player = event.getEntity() instanceof Player breaker ? breaker : null;
        woundCells(event.blockList(), player);
    }

    /**
     * @param event piston push
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (anyFill(event.getBlocks()) || protectedFill(event.getBlock().getRelative(event.getDirection()))) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event piston push that was allowed
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtendWound(BlockPistonExtendEvent event) {
        woundCells(event.getBlocks(), null);
    }

    /**
     * @param event piston pull
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (anyFill(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event piston pull that was allowed
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetractWound(BlockPistonRetractEvent event) {
        woundCells(event.getBlocks(), null);
    }

    /**
     * @param block world cell
     * @return whether config locks this prism fill against vanilla damage
     */
    private boolean protectedFill(Block block) {
        return protectDigSite && DigCut.isPrismFill(sites, block);
    }

    /**
     * @param blocks piston or explode list
     * @return whether any cell is protected prism fill
     */
    private boolean anyFill(List<Block> blocks) {
        for (Block block : blocks) {
            if (protectedFill(block)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param blocks explosion list
     */
    private void stripFill(List<Block> blocks) {
        Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            if (protectedFill(iterator.next())) {
                iterator.remove();
            }
        }
    }

    /**
     * @param blocks cells vanilla was allowed to remove
     * @param player breaker, or {@code null} when the world did it
     */
    private void woundCells(List<Block> blocks, Player player) {
        Set<Site> dirty = new HashSet<>();
        boolean cued = false;
        for (Block block : blocks) {
            Site site = sites.findPrism(
                    block.getWorld().getName(),
                    block.getX(),
                    block.getY(),
                    block.getZ()).orElse(null);
            if (site == null) {
                continue;
            }
            PrismWound.Removal removal = PrismWound.onCellRemoved(
                    site, block.getX(), block.getY(), block.getZ());
            if (removal.dossierChanged()) {
                dirty.add(site);
            }
            if (removal.findSmashed() && !cued) {
                FindBreakCue.play(player, block);
                cued = true;
            }
        }
        for (Site site : dirty) {
            sites.save(site);
        }
    }

    /**
     * @param block cell that left the world as fill
     * @param player breaker, or {@code null}
     */
    private void woundCell(Block block, Player player) {
        woundCells(List.of(block), player);
    }

    /**
     * @param player breaker, or {@code null}
     */
    private void warn(Player player) {
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = lastWarn.get(player.getUniqueId());
        if (previous != null && now - previous < MESSAGE_COOLDOWN_MS) {
            return;
        }
        lastWarn.put(player.getUniqueId(), now);
        player.sendMessage("Use an excavation tool.");
    }
}
