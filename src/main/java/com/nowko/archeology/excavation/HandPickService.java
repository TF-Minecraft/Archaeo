package com.nowko.archeology.excavation;

import com.nowko.archeology.config.CatalogRegistry;
import com.nowko.archeology.config.PickSettings;
import com.nowko.archeology.config.StratumDefinition;
import com.nowko.archeology.item.HandPickItem;
import com.nowko.archeology.model.Site;
import com.nowko.archeology.model.StratumBand;
import com.nowko.archeology.site.SiteRepository;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hand Pick: the world block never changes. While left-click is held on prism fill,
 * vanilla destroy progress is accumulated with {@link VanillaBreakClock}. Each time it
 * would have reached a break ({@code 1.0}), a cling plays and the clock restarts.
 */
public class HandPickService {
    private static final long WARN_MS = 3000L;
    /** Mining packets stop shortly after release; this delay treats the button as up. */
    private static final int STALE_TICKS = 16;
    /** Progress is counted only while packets still arrive; must be less than {@link #STALE_TICKS}. */
    private static final int ACTIVE_HOLD_TICKS = 8;

    private final JavaPlugin plugin;
    private final SiteRepository sites;
    private final CatalogRegistry catalogs;
    private final HandPickItem item;
    private final NamespacedKey noVanillaMineKey;
    private PickSettings settings;
    private BukkitTask task;
    private int gameTick;
    private final Map<UUID, Cycle> cycles = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastWarn = new ConcurrentHashMap<>();

    /**
     * @param plugin scheduler
     * @param sites excavation dossiers
     * @param catalogs strata labels for the HUD
     * @param item Hand Pick recognition
     * @param settings copy and jornada (jornada is not spent in this slice)
     */
    public HandPickService(
            JavaPlugin plugin,
            SiteRepository sites,
            CatalogRegistry catalogs,
            HandPickItem item,
            PickSettings settings
    ) {
        this.plugin = plugin;
        this.sites = sites;
        this.catalogs = catalogs;
        this.item = item;
        this.noVanillaMineKey = new NamespacedKey(plugin, "hand_pick_no_vanilla_mine");
        this.settings = settings;
    }

    /**
     * @param settings after reload
     */
    public void setSettings(PickSettings settings) {
        this.settings = settings;
    }

    /**
     * Starts the HUD and break-clock tick.
     */
    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /**
     * Ends open cycles, unlocks mining speed, and stops the tick.
     */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID id : new ArrayList<>(cycles.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                finish(player);
            } else {
                cycles.remove(id);
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            setVanillaMineLocked(player, false);
        }
    }

    /**
     * Starts or keeps a hold on prism fill. The live {@link Block} is not modified.
     *
     * @param player holder
     * @param block cell in the excavation prism
     */
    public void noteMining(Player player, Block block) {
        if (!settings.enabled()) {
            return;
        }
        if (!isExcavationFill(block)) {
            return;
        }
        Cycle existing = cycles.get(player.getUniqueId());
        if (existing != null && !existing.sameCell(block)) {
            finish(player);
            existing = null;
        }
        syncHeldTool(player);
        if (existing == null) {
            cycles.put(player.getUniqueId(), new Cycle(block.getX(), block.getY(), block.getZ(), gameTick));
            return;
        }
        existing.lastActiveTick = gameTick;
    }

    /**
     * Locks or unlocks client mining from the stack that is (or will be) in the main hand.
     * Must run when the slot changes, not when {@code BlockDamageEvent} arrives.
     *
     * @param player holder
     */
    public void syncHeldTool(Player player) {
        syncHeldTool(player, player.getInventory().getItemInMainHand());
    }

    /**
     * @param player holder
     * @param held stack that is or will be in the main hand
     */
    public void syncHeldTool(Player player, ItemStack held) {
        boolean holding = item.isPick(held);
        if (holding) {
            item.sealVanillaMining(held);
        }
        setVanillaMineLocked(player, holding);
    }

    /**
     * Re-sends the real {@link BlockData} if the client predicted a vanilla finish.
     *
     * @param player miner
     * @param block cell the client tried to finish
     */
    public void suppressVanillaBreak(Player player, Block block) {
        pinBlock(player, block);
        syncHeldTool(player);
        if (isExcavationFill(block)) {
            noteMining(player, block);
        }
    }

    /**
     * Drops the hold without changing the world (release, swap, quit).
     *
     * @param player holder
     */
    public void finish(Player player) {
        cycles.remove(player.getUniqueId());
    }

    /**
     * Action-bar hint when the pick is used off the excavation prism.
     *
     * @param player holder
     */
    public void warnOffCut(Player player) {
        warn(player, "This tool is only for the open cut of an excavation.");
    }

    /**
     * @param player holder
     * @return whether a cycle is open
     */
    public boolean isCycling(Player player) {
        return cycles.containsKey(player.getUniqueId());
    }

    /**
     * Refills today's Hand Pick budget on an established excavation.
     *
     * @param site established site
     * @param world used for the current Minecraft day id
     * @return actions after refill
     */
    public int refillJornada(Site site, World world) {
        ensureJornada(site, world);
        site.setJornadaPickLeft(settings.jornadaActions());
        sites.save(site);
        return site.getJornadaPickLeft();
    }

    /**
     * Restores pick actions when the world day rolls over.
     *
     * @param site excavation
     * @param world site world
     */
    void ensureJornada(Site site, World world) {
        long day = world.getFullTime() / 24000L;
        if (site.getJornadaWorldDay() != day) {
            site.setJornadaWorldDay(day);
            site.setJornadaPickLeft(settings.jornadaActions());
        }
    }

    /**
     * Counts vanilla-equivalent break cycles while the button is down.
     */
    private void tick() {
        gameTick++;
        for (Map.Entry<UUID, Cycle> entry : new ArrayList<>(cycles.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            Cycle cycle = entry.getValue();
            if (player == null || !player.isOnline() || !item.isPick(player.getInventory().getItemInMainHand())) {
                if (player != null) {
                    finish(player);
                } else {
                    cycles.remove(entry.getKey());
                }
                continue;
            }
            if (gameTick - cycle.lastActiveTick > STALE_TICKS) {
                finish(player);
                continue;
            }
            Block block = player.getWorld().getBlockAt(cycle.x, cycle.y, cycle.z);
            if (gameTick - cycle.lastActiveTick > ACTIVE_HOLD_TICKS) {
                continue;
            }
            if (!isExcavationFill(block)) {
                finish(player);
                continue;
            }
            float step = VanillaBreakClock.tickProgress(player, block, noVanillaMineKey);
            if (step <= 0f) {
                continue;
            }
            cycle.progress += step;
            while (cycle.progress >= 1.0f) {
                cycle.progress -= 1.0f;
                playCling(player, block);
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack held = player.getInventory().getItemInMainHand();
            boolean holding = item.isPick(held);
            syncHeldTool(player, held);
            if (holding) {
                showHud(player);
            }
        }
    }

    /**
     * @param block world cell
     * @return whether the Hand Pick clock runs here (excavation prism fill)
     */
    private boolean isExcavationFill(Block block) {
        if (!PrismFill.isTerrainFill(block.getType())) {
            return false;
        }
        return sites.findEstablishedPrism(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ()).isPresent();
    }

    /**
     * Client-only restore of the live block so a predicted break cannot flash air.
     * Uses {@link Block#getBlockData()} so orientation and extra states stay exact.
     *
     * @param player viewer
     * @param block live cell
     */
    private void pinBlock(Player player, Block block) {
        BlockData data = block.getBlockData();
        player.sendBlockChange(block.getLocation(), data);
    }

    /**
     * One vanilla-break-that-did-not-happen.
     *
     * @param player miner
     * @param block unchanged cell
     */
    private void playCling(Player player, Block block) {
        block.getWorld().playSound(
                block.getLocation(),
                Sound.BLOCK_NOTE_BLOCK_CHIME,
                SoundCategory.BLOCKS,
                0.55f,
                0.95f);
        if (!settings.visualCues()) {
            return;
        }
        Location at = block.getLocation().add(0.5, 1.05, 0.5);
        player.spawnParticle(
                Particle.DUST,
                at,
                10,
                0.18,
                0.08,
                0.18,
                0,
                new Particle.DustOptions(Color.fromRGB(160, 210, 255), 1.15f));
    }

    /**
     * Multiplies client mining speed to zero while the Hand Pick is in the main hand.
     *
     * @param player holder
     * @param locked whether vanilla destroy progress must stay at zero on the client
     */
    private void setVanillaMineLocked(Player player, boolean locked) {
        AttributeInstance speed = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (speed == null) {
            return;
        }
        AttributeModifier found = null;
        for (AttributeModifier modifier : speed.getModifiers()) {
            if (noVanillaMineKey.equals(modifier.getKey())) {
                found = modifier;
                break;
            }
        }
        if (locked && found == null) {
            speed.addModifier(new AttributeModifier(
                    noVanillaMineKey,
                    -1.0,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                    EquipmentSlotGroup.ANY));
            return;
        }
        if (!locked && found != null) {
            speed.removeModifier(found);
        }
    }

    /**
     * @param player viewer
     */
    private void showHud(Player player) {
        Cycle cycle = cycles.get(player.getUniqueId());
        Block target = player.getTargetBlockExact(6);
        if (target == null && cycle == null) {
            return;
        }
        if (cycle != null) {
            target = player.getWorld().getBlockAt(cycle.x, cycle.y, cycle.z);
        }
        Site site = sites.findEstablishedPrism(
                target.getWorld().getName(),
                target.getX(),
                target.getY(),
                target.getZ()).orElse(null);
        if (site == null) {
            return;
        }
        ensureJornada(site, player.getWorld());
        StratumBand band = site.stratumAt(target.getY());
        String layer = band == null ? "—" : band.getId();
        StratumDefinition definition = band == null ? null : catalogs.stratum(band.getId());
        String antiquity = definition == null ? "" : " · " + definition.antiquity();
        String text = "STRATUM " + layer + antiquity + "  ⛏ " + site.getJornadaPickLeft();
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
    }

    /**
     * @param player viewer
     * @param message English line
     */
    private void warn(Player player, String message) {
        long now = System.currentTimeMillis();
        Long previous = lastWarn.get(player.getUniqueId());
        if (previous != null && now - previous < WARN_MS) {
            return;
        }
        lastWarn.put(player.getUniqueId(), now);
        player.sendMessage(message);
    }

    /**
     * One player's current left-click hold on a cell.
     */
    private static final class Cycle {
        private final int x;
        private final int y;
        private final int z;
        private int lastActiveTick;
        private float progress;

        /**
         * @param x block X
         * @param y block Y
         * @param z block Z
         * @param tick current service tick
         */
        private Cycle(int x, int y, int z, int tick) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.lastActiveTick = tick;
        }

        /**
         * @param block world cell
         * @return whether this cycle is still on that cell
         */
        private boolean sameCell(Block block) {
            return block.getX() == x && block.getY() == y && block.getZ() == z;
        }
    }
}
