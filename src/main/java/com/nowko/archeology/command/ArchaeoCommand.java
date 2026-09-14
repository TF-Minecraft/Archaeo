package com.nowko.archeology.command;

import com.nowko.archeology.ArcheologyPlugin;
import com.nowko.archeology.config.ArtifactTemplate;
import com.nowko.archeology.config.CatalogRegistry;
import com.nowko.archeology.establish.CampNames;
import com.nowko.archeology.establish.EstablishService;
import com.nowko.archeology.excavation.FindDustService;
import com.nowko.archeology.excavation.HandPickService;
import com.nowko.archeology.excavation.PrismListener;
import com.nowko.archeology.excavation.RecoverService;
import com.nowko.archeology.item.BrushItem;
import com.nowko.archeology.item.EstablishItem;
import com.nowko.archeology.item.ItemMatcher;
import com.nowko.archeology.item.ProspectItem;
import com.nowko.archeology.item.TrackerItem;
import com.nowko.archeology.model.BuriedFind;
import com.nowko.archeology.model.InterestLevel;
import com.nowko.archeology.model.Site;
import com.nowko.archeology.model.SiteStatus;
import com.nowko.archeology.model.StratumBand;
import com.nowko.archeology.prospect.ProspectService;
import com.nowko.archeology.site.RuinAutoSpawner;
import com.nowko.archeology.site.SiteGenerator;
import com.nowko.archeology.site.SiteRepository;
import com.nowko.archeology.tracker.TrackerService;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import com.nowko.archeology.excavation.PrismFill;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Staff-only {@code /archaeo} commands. Players never use this; discovery is tracker and kits.
 */
public class ArchaeoCommand implements CommandExecutor, TabCompleter {
    private static final List<String> INTERESTS = List.of("low", "medium", "high", "exceptional");
    private static final List<String> ROOT = List.of(
            "give", "ruin", "workday", "find", "sketch", "reload");
    private static final List<String> RUIN_ACTIONS = List.of("create", "info", "camps", "tp");
    private static final List<String> GIVE_KINDS = List.of(
            "tracker", "prospect", "establish", "tool", "brush", "paper", "pencil");
    private static final List<String> WORKDAY_ACTIONS = List.of("reset");
    private static final List<String> FIND_ACTIONS = List.of("spawn");

    private final ArcheologyPlugin plugin;
    private final CatalogRegistry catalogs;
    private final SiteGenerator generator;
    private final SiteRepository sites;
    private final TrackerItem trackerItem;
    private final TrackerService tracker;
    private final ProspectItem prospectItem;
    private final ProspectService prospect;
    private final EstablishItem establishItem;
    private final EstablishService establish;
    private final HandPickService handPick;
    private final FindDustService findDust;
    private final PrismListener prism;
    private final BrushItem brushItem;
    private final RecoverService recover;
    private final RuinAutoSpawner autoRuins;

    /**
     * @param plugin owner used to re-bind ItemsAdder / MMOItems on reload
     * @param catalogs staff permission and YAML catalogs
     * @param generator used to persist a new managed ruin
     * @param sites lookup for {@code ruin info} and reload
     * @param trackerItem factory for {@code give tracker}
     * @param tracker live scan loop, updated on reload
     * @param prospectItem factory for {@code give prospect}
     * @param prospect sample loop, updated on reload
     * @param establishItem factory for {@code give establish}
     * @param establish camp outline loop, updated on reload
     * @param handPick excavation loop and tool whitelist, updated on reload
     * @param findDust leak on exposed find cells, updated on reload
     * @param prism prism fill lock, updated on reload
     * @param brushItem factory for {@code give brush}
     * @param recover field-brush loop, updated on reload
     * @param autoRuins trial chunk auto-spawner, updated on reload
     */
    public ArchaeoCommand(
            ArcheologyPlugin plugin,
            CatalogRegistry catalogs,
            SiteGenerator generator,
            SiteRepository sites,
            TrackerItem trackerItem,
            TrackerService tracker,
            ProspectItem prospectItem,
            ProspectService prospect,
            EstablishItem establishItem,
            EstablishService establish,
            HandPickService handPick,
            FindDustService findDust,
            PrismListener prism,
            BrushItem brushItem,
            RecoverService recover,
            RuinAutoSpawner autoRuins
    ) {
        this.plugin = plugin;
        this.catalogs = catalogs;
        this.generator = generator;
        this.sites = sites;
        this.trackerItem = trackerItem;
        this.tracker = tracker;
        this.prospectItem = prospectItem;
        this.prospect = prospect;
        this.establishItem = establishItem;
        this.establish = establish;
        this.handPick = handPick;
        this.findDust = findDust;
        this.prism = prism;
        this.brushItem = brushItem;
        this.recover = recover;
        this.autoRuins = autoRuins;
    }

    /**
     * Dispatches ruin and tracker staff subcommands. Permission node comes from {@code config.yml}.
     *
     * @param sender command issuer
     * @param command Bukkit command metadata
     * @param label alias used
     * @param args tokens after the command name
     * @return {@code true} so Bukkit does not print the plugin.yml usage line
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(catalogs.staffPermission())) {
            sender.sendMessage("You do not have permission to use Archaeo staff commands.");
            return true;
        }
        if (args.length >= 1 && "reload".equalsIgnoreCase(args[0])) {
            return handleReload(sender);
        }
        if (args.length >= 1 && "give".equalsIgnoreCase(args[0])) {
            return handleGive(sender, args);
        }
        if (args.length >= 1 && ("workday".equalsIgnoreCase(args[0]) || "pick".equalsIgnoreCase(args[0]))) {
            return handleWorkday(sender, args);
        }
        if (args.length >= 1 && "find".equalsIgnoreCase(args[0])) {
            return handleFind(sender, args);
        }
        if (args.length >= 1 && "sketch".equalsIgnoreCase(args[0])) {
            return handleSketch(sender);
        }
        if (args.length < 2 || !"ruin".equalsIgnoreCase(args[0])) {
            sendUsage(sender);
            return true;
        }
        if ("create".equalsIgnoreCase(args[1])) {
            return handleCreate(sender, args);
        }
        if ("info".equalsIgnoreCase(args[1])) {
            return handleInfo(sender, args);
        }
        if ("tp".equalsIgnoreCase(args[1])) {
            return handleRuinTp(sender, args);
        }
        if ("camps".equalsIgnoreCase(args[1])) {
            return handleCamps(sender, args);
        }
        sendUsage(sender);
        return true;
    }

    /**
     * Re-reads catalog YAML from the data folder into memory. Does not overwrite existing files.
     * Dirty site dossiers are flushed first so reload cannot throw away jornada or brush progress.
     *
     * @param sender staff issuer
     * @return {@code true} always (handled)
     */
    private boolean handleReload(CommandSender sender) {
        try {
            sites.flushDirty();
            catalogs.load();
            plugin.bindItemMatcher(ItemMatcher.detect(plugin));
            trackerItem.update(catalogs.items().tracker());
            tracker.setSettings(catalogs.tracker());
            prospectItem.update(catalogs.items().prospect());
            prospect.setSettings(catalogs.prospect());
            establishItem.update(catalogs.items().establish());
            establish.setSettings(catalogs.establish());
            handPick.setSettings(catalogs.pick());
            findDust.setSettings(catalogs.pick());
            prism.setProtectDigSite(catalogs.establish().protectDigSite());
            brushItem.update(catalogs.items().brush());
            recover.setSettings(catalogs.recovery());
            plugin.sketchSupplies().update(
                    catalogs.items().sketchPaper(),
                    catalogs.items().sketchPencil(),
                    catalogs.sketch().pencilUses());
            plugin.sketch().setSettings(catalogs.sketch());
            if (autoRuins != null) {
                autoRuins.setSettings(catalogs.autoRuins());
            }
            sites.loadAll();
            sender.sendMessage("Reloaded Archaeo config, catalogs, and sites from disk.");
        } catch (RuntimeException exception) {
            sender.sendMessage("Reload failed: " + exception.getMessage());
        }
        return true;
    }

    /**
     * Staff spawn of a configured role item: {@code /archaeo give <kind> [player]}.
     *
     * @param sender staff issuer
     * @param args {@code give <tracker|prospect|establish|tool|brush> [id] [player]}
     * @return {@code true} always (handled)
     */
    private boolean handleGive(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /archaeo give <tracker|prospect|establish|tool|brush|paper|pencil> [player]");
            sender.sendMessage("       /archaeo give tool <item> [player]");
            return true;
        }
        String kind = args[1].toLowerCase(Locale.ROOT);
        if ("pick".equals(kind)) {
            kind = "tool";
        }
        if ("tool".equals(kind)) {
            return handleGiveTool(sender, args);
        }
        Player target = resolveOnlinePlayer(sender, args, 2, "/archaeo give <kind> <player>");
        if (target == null) {
            return true;
        }
        return switch (kind) {
            case "tracker" -> giveStack(
                    sender,
                    target,
                    trackerItem.create(),
                    "an archaeological tracker",
                    "You received an archaeological tracker. Hold it to listen for hidden ruins.");
            case "prospect" -> giveStack(
                    sender,
                    target,
                    prospectItem.create(),
                    "a prospecting kit",
                    "You received a prospecting kit. Right-click ground in a suspected chunk.");
            case "establish" -> giveStack(
                    sender,
                    target,
                    establishItem.create(),
                    "an establishment kit",
                    "You received an establishment kit. Use it next to a ruin you have confirmed.");
            case "brush" -> giveStack(
                    sender,
                    target,
                    brushItem.create(),
                    "a field brush",
                    "You received a field brush. Right-click a fully exposed find to lift it.");
            case "paper" -> giveStack(
                    sender,
                    target,
                    plugin.sketchSupplies().createPaper(),
                    "a field sheet",
                    "You received a field sheet. Click it onto a field pencil to start a sketch.");
            case "pencil" -> giveStack(
                    sender,
                    target,
                    plugin.sketchSupplies().createPencil(),
                    "a field pencil",
                    "You received a field pencil. Click a field sheet onto it; the pencil wears like a tool.");
            default -> {
                sender.sendMessage("Unknown item. Use: tracker, prospect, establish, tool, brush, paper, or pencil.");
                yield true;
            }
        };
    }

    /**
     * Gives one stack from {@code excavation.tools}. A missing id uses the first listed item.
     *
     * @param sender staff issuer
     * @param args {@code give tool [item] [player]}
     * @return {@code true} always
     */
    private boolean handleGiveTool(CommandSender sender, String[] args) {
        ItemStack stack;
        int playerIndex = 2;
        Optional<ItemStack> named = args.length >= 3 ? handPick.toolForGive(args[2]) : Optional.empty();
        if (named.isPresent()) {
            stack = named.get();
            playerIndex = 3;
        } else if (args.length >= 3 && Bukkit.getPlayerExact(args[2]) == null
                && !handPick.giveToolTokens().isEmpty()) {
            sender.sendMessage("Unknown excavation tool. Tab-complete lists excavation.tools (empty hand is skipped).");
            return true;
        } else {
            stack = handPick.sampleTool();
        }
        Player target = resolveOnlinePlayer(sender, args, playerIndex, "/archaeo give tool [item] <player>");
        if (target == null) {
            return true;
        }
        String label = stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return giveStack(
                sender,
                target,
                stack,
                "an excavation tool (" + label + ")",
                "You can excavate with any pickaxe or shovel listed under excavation.tools, or an empty hand.");
    }

    /**
     * @param sender staff issuer
     * @param target inventory to fill
     * @param stack item to add
     * @param givenLabel staff confirmation noun phrase
     * @param receivedMessage line sent to {@code target} when they are not the issuer
     * @return {@code true} always
     */
    private static boolean giveStack(
            CommandSender sender,
            Player target,
            ItemStack stack,
            String givenLabel,
            String receivedMessage
    ) {
        if (stack == null || stack.getType().isAir()) {
            sender.sendMessage("Could not create that item. Check pack plugins and config.yml.");
            return true;
        }
        target.getInventory().addItem(stack);
        sender.sendMessage("Gave " + givenLabel + " to " + target.getName() + ".");
        if (target != sender) {
            target.sendMessage(receivedMessage);
        }
        return true;
    }

    /**
     * @param sender issuer
     * @param args tokens
     * @param playerIndex index of an optional player name
     * @param consoleUsage usage shown when console omits the name
     * @return online player, or {@code null} after an error message
     */
    private static Player resolveOnlinePlayer(
            CommandSender sender,
            String[] args,
            int playerIndex,
            String consoleUsage
    ) {
        if (args.length > playerIndex) {
            Player target = Bukkit.getPlayerExact(args[playerIndex]);
            if (target == null) {
                sender.sendMessage("Player not online: " + args[playerIndex]);
                return null;
            }
            return target;
        }
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage("Console must name a player: " + consoleUsage);
        return null;
    }

    /**
     * Restores today's cut budget on the excavation at a player's feet, or every established site.
     *
     * @param sender staff issuer
     * @param args {@code workday reset [player|all]} ( {@code pick reset} still accepted )
     * @return {@code true} always (handled)
     */
    private boolean handleWorkday(CommandSender sender, String[] args) {
        if (args.length < 2 || !"reset".equalsIgnoreCase(args[1])) {
            sender.sendMessage("Usage: /archaeo workday reset [player|all]");
            return true;
        }
        return handleWorkdayReset(sender, args);
    }

    /**
     * Restores Hand Pick jornada on the excavation at a player's feet, or every established site.
     *
     * @param sender staff issuer
     * @param args {@code workday reset [player|all]}
     * @return {@code true} always (handled)
     */
    private boolean handleWorkdayReset(CommandSender sender, String[] args) {
        if (catalogs.pick().unlimitedWorkday()) {
            sender.sendMessage("Work-day actions are unlimited (excavation.workday-actions: 0). Nothing to reset.");
            return true;
        }
        if (args.length >= 3 && "all".equalsIgnoreCase(args[2])) {
            int count = 0;
            for (Site site : sites.all()) {
                if (site.getStatus() != SiteStatus.ESTABLISHED) {
                    continue;
                }
                org.bukkit.World world = Bukkit.getWorld(site.getWorldName());
                if (world == null) {
                    continue;
                }
                int left = handPick.refillJornada(site, world);
                count++;
                sender.sendMessage("Reset pick jornada on #" + site.getSerial() + " to " + left + ".");
            }
            if (count == 0) {
                sender.sendMessage("No established excavations to reset.");
            }
            return true;
        }
        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage("Player not online: " + args[2]);
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage("Console must name a player or use: /archaeo workday reset all");
            return true;
        }
        Site site = establishedSiteNear(target).orElse(null);
        if (site == null) {
            sender.sendMessage(target.getName() + " is not in an established excavation (ruin chunk, prism, or camp).");
            return true;
        }
        int left = handPick.refillJornada(site, target.getWorld());
        sender.sendMessage("Reset pick jornada on #" + site.getSerial()
                + (site.getName() == null || site.getName().isBlank() ? "" : " (" + site.getName() + ")")
                + " to " + left + ".");
        if (target != sender) {
            target.sendMessage("Staff reset today's Hand Pick actions on this excavation (" + left + " left).");
        }
        return true;
    }

    /**
     * @param player whose location to search
     * @return established site at the ruin chunk, prism cell, or camp chunk
     */
    private Optional<Site> establishedSiteNear(Player player) {
        String world = player.getWorld().getName();
        int x = player.getLocation().getBlockX();
        int y = player.getLocation().getBlockY();
        int z = player.getLocation().getBlockZ();
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        return sites.findEstablishedPrism(world, x, y, z)
                .or(() -> sites.findByChunk(world, chunkX, chunkZ)
                        .filter(site -> site.getStatus() == SiteStatus.ESTABLISHED))
                .or(() -> sites.findByEstablishmentChunk(world, chunkX, chunkZ)
                        .filter(site -> site.getStatus() == SiteStatus.ESTABLISHED));
    }

    /**
     * Plants a find at the issuer's feet and opens the chunk as an excavation, skipping
     * ruin create, prospect, and camp.
     *
     * @param sender staff issuer
     * @param args {@code find spawn [artifact] [size]}
     * @return {@code true} always (handled)
     */
    private boolean handleFind(CommandSender sender, String[] args) {
        if (args.length < 2 || !"spawn".equalsIgnoreCase(args[1])) {
            sender.sendMessage("Usage: /archaeo find spawn [artifact] [size]");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Find spawn must be used in-world at the test block.");
            return true;
        }
        ArtifactTemplate template = catalogs.artifact("vessel");
        if (args.length >= 3) {
            template = catalogs.artifact(args[2].toLowerCase(Locale.ROOT));
            if (template == null) {
                sender.sendMessage("Unknown artifact. Try: " + String.join(", ", catalogs.artifacts().keySet()));
                return true;
            }
        } else if (template == null) {
            template = catalogs.artifacts().values().stream().findFirst().orElse(null);
            if (template == null) {
                sender.sendMessage("No artifact templates are loaded.");
                return true;
            }
        }
        int size = template.sizeMax();
        if (args.length >= 4) {
            try {
                size = Integer.parseInt(args[3]);
            } catch (NumberFormatException exception) {
                sender.sendMessage("Size must be a whole number.");
                return true;
            }
        }
        Block origin = staffFindOrigin(player);
        if (origin == null) {
            sender.sendMessage("Stand on a solid excavation block.");
            return true;
        }
        try {
            BuriedFind find = generator.spawnStaffFind(origin, player.getUniqueId(), template, size);
            Site site = sites.findByChunk(
                    origin.getWorld().getName(),
                    origin.getChunk().getX(),
                    origin.getChunk().getZ()).orElseThrow();
            handPick.refillJornada(site, player.getWorld());
            findDust.syncTimer();
            sender.sendMessage("Spawned " + template.displayName()
                    + " (" + find.getCells().size() + " cells) on "
                    + origin.getX() + "," + origin.getY() + "," + origin.getZ()
                    + " · site #" + site.getSerial() + " established.");
            sender.sendMessage("Lift neighbouring fill until the shape leaks on every remaining cube, then brush it.");
        } catch (IllegalStateException | IllegalArgumentException exception) {
            sender.sendMessage(exception.getMessage());
        }
        return true;
    }

    /**
     * Fill cell under the player, or the cell they occupy if that is already fill.
     *
     * @param player staff tester
     * @return origin, or {@code null} if neither cell is fill
     */
    private static Block staffFindOrigin(Player player) {
        Block feet = player.getLocation().getBlock();
        if (PrismFill.isTerrainFill(feet.getType())) {
            return feet;
        }
        Block below = feet.getRelative(BlockFace.DOWN);
        if (PrismFill.isTerrainFill(below.getType())) {
            return below;
        }
        return null;
    }

    /**
     * Registers a ruin in the player's current chunk.
     *
     * @param sender command issuer
     * @param args full argument list including {@code ruin create}
     * @return {@code true} always (handled)
     */
    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Ruin create must be used in the site chunk.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("Usage: /archaeo ruin create <low|medium|high|exceptional> [name]");
            return true;
        }
        InterestLevel interest = InterestLevel.fromInput(args[2]);
        if (interest == null) {
            sender.sendMessage("Unknown interest. Use: low, medium, high, or exceptional.");
            return true;
        }
        String name = args.length > 3
                ? Arrays.stream(args).skip(3).collect(Collectors.joining(" "))
                : null;
        try {
            Site site = generator.createManagedRuin(player.getLocation().getChunk(), interest, name, player.getUniqueId());
            sender.sendMessage("Site created: " + site.displayLabel());
            sender.sendMessage("Chunk " + site.getChunkX() + "," + site.getChunkZ()
                    + " · finds " + site.getFinds().size()
                    + " · hints " + site.getHintIds().size());
            sender.sendMessage("Terrain was not changed; remains are stored as hidden data.");
            if (site.getFinds().isEmpty()) {
                sender.sendMessage("No find fitted: this chunk has no buried ground in its strata."
                        + " Try a chunk with more soil over the layers.");
            }
        } catch (IllegalStateException | IllegalArgumentException exception) {
            sender.sendMessage(exception.getMessage());
        }
        return true;
    }

    /**
     * Prints a site dossier. With no query, uses the player's chunk; otherwise name or serial ({@code 12} or {@code #12}).
     *
     * @param sender command issuer
     * @param args full argument list including {@code ruin info}
     * @return {@code true} always (handled)
     */
    private boolean handleInfo(CommandSender sender, String[] args) {
        Optional<Site> resolved;
        if (args.length == 2) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Console must pass a site name or serial: /archaeo ruin info <name|#serial>");
                return true;
            }
            Chunk chunk = player.getLocation().getChunk();
            resolved = sites.findByChunk(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
            if (resolved.isEmpty()) {
                sender.sendMessage("No site in this chunk (" + chunk.getX() + "," + chunk.getZ() + ").");
                return true;
            }
        } else {
            String query = Arrays.stream(args).skip(2).collect(Collectors.joining(" "));
            resolved = resolveQuery(sender, query);
        }
        resolved.ifPresent(site -> sendSiteInfo(sender, site));
        return true;
    }

    /**
     * Staff teleport to the centre of a ruin chunk (surface datum). Used by auto-ruin {@code [tp]} links.
     *
     * @param sender must be a player
     * @param args {@code ruin tp <name|#serial>}
     * @return {@code true} always (handled)
     */
    private boolean handleRuinTp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Ruin tp must be used in-game.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("Usage: /archaeo ruin tp <name|#serial>");
            return true;
        }
        String query = Arrays.stream(args).skip(2).collect(Collectors.joining(" "));
        Optional<Site> resolved = resolveQuery(sender, query);
        if (resolved.isEmpty()) {
            return true;
        }
        Site site = resolved.get();
        World world = Bukkit.getWorld(site.getWorldName());
        if (world == null) {
            sender.sendMessage("World \"" + site.getWorldName() + "\" is not loaded.");
            return true;
        }
        int blockX = (site.getChunkX() << 4) + 8;
        int blockZ = (site.getChunkZ() << 4) + 8;
        world.getChunkAt(site.getChunkX(), site.getChunkZ()).load();
        int y = site.getSurfaceY() + 1;
        player.teleport(new Location(world, blockX + 0.5, y, blockZ + 0.5));
        sender.sendMessage("Teleported to " + site.displayLabel()
                + " · chunk " + site.getChunkX() + "," + site.getChunkZ()
                + " (" + site.getWorldName() + ").");
        return true;
    }

    /**
     * Lists locked camps a player still directs, and teleports staff to one when chosen.
     * One camp teleports immediately; several print a clickable list (or take {@code #serial}).
     *
     * @param sender staff issuer
     * @param args {@code ruin camps [player] [#serial]}
     * @return {@code true} always (handled)
     */
    private boolean handleCamps(CommandSender sender, String[] args) {
        OfflinePlayer director;
        Optional<Integer> serial = Optional.empty();
        if (args.length == 2) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Console must name a player: /archaeo ruin camps <player> [#serial]");
                return true;
            }
            director = player;
        } else if (args.length == 3) {
            Optional<Integer> onlySerial = parseSerial(args[2]);
            if (onlySerial.isPresent()) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Console must name a player before a serial: /archaeo ruin camps <player> #serial");
                    return true;
                }
                return teleportToCamp(sender, (Player) sender, onlySerial.get(), null);
            }
            director = CampNames.known(args[2]);
            if (director == null) {
                sender.sendMessage("Unknown player: " + args[2]);
                return true;
            }
        } else {
            director = CampNames.known(args[2]);
            if (director == null) {
                sender.sendMessage("Unknown player: " + args[2]);
                return true;
            }
            serial = parseSerial(Arrays.stream(args).skip(3).collect(Collectors.joining(" ")));
            if (serial.isEmpty()) {
                sender.sendMessage("Usage: /archaeo ruin camps [player] [#serial]");
                return true;
            }
        }
        UUID directorId = director.getUniqueId();
        if (serial.isPresent()) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only a player can teleport to a camp.");
                return true;
            }
            return teleportToCamp(sender, player, serial.get(), directorId);
        }
        List<Site> camps = sites.findDirectedCamps(directorId);
        String who = CampNames.of(sender instanceof Player player ? player : null, directorId);
        if (camps.isEmpty()) {
            sender.sendMessage(who + " does not direct any open camp.");
            return true;
        }
        if (camps.size() == 1 && sender instanceof Player player) {
            Site only = camps.getFirst();
            sendCampLine(sender, only, false);
            return teleportPlayerToCamp(sender, player, only);
        }
        sender.sendMessage(who + " directs " + camps.size() + " camp" + (camps.size() == 1 ? "" : "s") + ":");
        for (Site camp : camps) {
            sendCampLine(sender, camp, sender instanceof Player);
        }
        if (camps.size() > 1 && sender instanceof Player) {
            sender.sendMessage("Click [tp] or run /archaeo ruin camps "
                    + (args.length >= 3 ? args[2] + " " : "")
                    + "#serial");
        }
        return true;
    }

    /**
     * Teleports the issuer to a directed camp after checking the optional director filter.
     *
     * @param sender who receives errors
     * @param traveler player to move
     * @param serial site serial
     * @param expectedDirector required director, or {@code null} to accept any locked camp
     * @return {@code true} always
     */
    private boolean teleportToCamp(
            CommandSender sender,
            Player traveler,
            int serial,
            UUID expectedDirector
    ) {
        Optional<Site> resolved = sites.findBySerial(serial);
        if (resolved.isEmpty()) {
            sender.sendMessage("No site with serial #" + serial + ".");
            return true;
        }
        Site site = resolved.get();
        if (!site.isCampLocked() || site.getCampX() == null) {
            sender.sendMessage(site.displayLabel() + " has no standing camp to teleport to.");
            return true;
        }
        if (expectedDirector != null && !site.isDirector(expectedDirector)) {
            sender.sendMessage(site.displayLabel() + " is not directed by that player.");
            return true;
        }
        return teleportPlayerToCamp(sender, traveler, site);
    }

    /**
     * Moves {@code traveler} to the camp table block of {@code site}.
     *
     * @param sender who receives confirmations
     * @param traveler player to move
     * @param site locked camp
     * @return {@code true} always
     */
    private boolean teleportPlayerToCamp(CommandSender sender, Player traveler, Site site) {
        World world = Bukkit.getWorld(site.getWorldName());
        if (world == null) {
            sender.sendMessage("World not loaded: " + site.getWorldName());
            return true;
        }
        if (site.getCampX() == null || site.getCampY() == null || site.getCampZ() == null) {
            sender.sendMessage(site.displayLabel() + " has no camp coordinates.");
            return true;
        }
        Location destination = new Location(
                world,
                site.getCampX() + 0.5,
                site.getCampY() + 1.0,
                site.getCampZ() + 0.5);
        traveler.teleport(destination);
        sender.sendMessage("Teleported to " + site.displayLabel()
                + " · camp " + site.getCampX() + "," + site.getCampY() + "," + site.getCampZ()
                + " (" + site.getWorldName() + ").");
        if (traveler != sender) {
            traveler.sendMessage("Staff moved you to camp " + site.displayLabel() + ".");
        }
        return true;
    }

    /**
     * Prints one directed-camp summary, optionally with a clickable teleport token.
     *
     * @param sender who receives the line
     * @param site locked camp
     * @param clickable whether to attach a {@code [tp]} run-command
     */
    private void sendCampLine(CommandSender sender, Site site, boolean clickable) {
        String coords = site.getCampX() == null
                ? "unknown"
                : site.getCampX() + "," + site.getCampY() + "," + site.getCampZ();
        String body = site.displayLabel()
                + " · " + site.getStatus().name().toLowerCase(Locale.ROOT)
                + " · " + site.getWorldName()
                + " · camp " + coords;
        if (!clickable || !(sender instanceof Player player)) {
            sender.sendMessage("  " + body);
            return;
        }
        TextComponent prefix = new TextComponent("  " + body + " ");
        TextComponent tp = new TextComponent("[tp]");
        tp.setColor(net.md_5.bungee.api.ChatColor.AQUA);
        tp.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/archaeo ruin camps #" + site.getSerial()));
        tp.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new Text("Teleport to this camp")));
        player.spigot().sendMessage(prefix, tp);
    }

    /**
     * Resolves a staff query to one site, or sends an error and returns empty.
     *
     * @param sender who will receive ambiguity errors
     * @param query name or serial
     * @return the site, if exactly one match
     */
    private Optional<Site> resolveQuery(CommandSender sender, String query) {
        Optional<Integer> serial = parseSerial(query);
        if (serial.isPresent()) {
            Optional<Site> bySerial = sites.findBySerial(serial.get());
            if (bySerial.isEmpty()) {
                sender.sendMessage("No site with serial #" + serial.get() + ".");
            }
            return bySerial;
        }
        List<Site> matches = sites.findByName(query);
        if (matches.isEmpty()) {
            sender.sendMessage("No site named \"" + query + "\". Try /archaeo ruin info #<serial>.");
            return Optional.empty();
        }
        if (matches.size() > 1) {
            sender.sendMessage("Several sites share that name. Use a serial:");
            for (Site site : matches) {
                sender.sendMessage("  " + site.displayLabel()
                        + " · chunk " + site.getChunkX() + "," + site.getChunkZ());
            }
            return Optional.empty();
        }
        return Optional.of(matches.getFirst());
    }

    /**
     * @param raw staff query such as {@code 14} or {@code #14}
     * @return serial if the whole query is a number
     */
    private Optional<Integer> parseSerial(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("#")) {
            trimmed = trimmed.substring(1).trim();
        }
        if (trimmed.isEmpty() || !trimmed.chars().allMatch(Character::isDigit)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(trimmed));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Sends the dossier summary used by staff to verify generation without opening YAML.
     *
     * @param sender who receives the lines
     * @param site loaded site
     */
    private void sendSiteInfo(CommandSender sender, Site site) {
        sender.sendMessage("Site " + site.displayLabel());
        sender.sendMessage("Id: " + site.getId());
        sender.sendMessage("Type: " + site.getType().name()
                + " · status: " + site.getStatus().name()
                + " · interest: " + (site.getInterest() == null ? "none" : site.getInterest().yamlKey()));
        sender.sendMessage("World: " + site.getWorldName()
                + " · chunk " + site.getChunkX() + "," + site.getChunkZ()
                + " · datum Y " + site.getSurfaceY()
                + " · detection " + site.getDetectionRadius());
        sender.sendMessage("Created by: " + (site.getCreatedBy() == null ? "unknown" : site.getCreatedBy())
                + " · at " + (site.getCreatedAt() == null ? "unknown" : site.getCreatedAt()));
        sender.sendMessage("Director: " + (site.getDirector() == null ? "none" : site.getDirector())
                + " · visibility: " + site.getVisibility()
                + " · recovered: " + site.getRecoveredCount());
        if (site.hasEstablishment()) {
            sender.sendMessage("Camp chunk " + site.getEstablishmentChunkX() + "," + site.getEstablishmentChunkZ()
                    + " · table " + site.getCampX() + "," + site.getCampY() + "," + site.getCampZ());
        }
        sender.sendMessage("Strata:");
        for (StratumBand band : site.getStrata().values()) {
            if (!band.isPresent()) {
                sender.sendMessage("  " + band.getId() + " · absent");
                continue;
            }
            sender.sendMessage("  " + band.getId()
                    + " · Y " + band.getMinY() + "–" + band.getMaxY()
                    + (band.isDisturbed() ? " · disturbed" : ""));
        }
        sender.sendMessage("Hints: " + (site.getHintIds().isEmpty() ? "(none)" : String.join(", ", site.getHintIds())));
        sender.sendMessage("Prospect confirmed: " + site.getProspectConfirmed().size()
                + " · samplers: " + site.allProspectSamples().size());
        sender.sendMessage("Finds: " + site.getFinds().size());
        for (BuriedFind find : site.getFinds()) {
            sender.sendMessage("  " + find.getArtifactId()
                    + " · stratum " + find.getStratumId()
                    + " · " + find.getState().name()
                    + " · " + find.getCells().size() + " cells"
                    + " · " + find.getConservation() + "% of " + find.getBuriedConservation() + "% buried"
                    + (find.getCleanedCells().isEmpty() ? "" : " · cleaned " + find.getCleanedCells().size())
                    + (find.isDisturbedBeforeDig()
                            ? " · disturbed before the dig (" + find.getPriorCells().size() + " cells)" : "")
                    + (find.isFieldDamaged() ? " · hurt while digging" : ""));
        }
    }

    /**
     * @param sender who receives usage
     */
    private void sendUsage(CommandSender sender) {
        sender.sendMessage("Usage: /archaeo ruin create <low|medium|high|exceptional> [name]");
        sender.sendMessage("       /archaeo ruin info [name|#serial]");
        sender.sendMessage("       /archaeo ruin tp <name|#serial>");
        sender.sendMessage("       /archaeo ruin camps [player] [#serial]");
        sender.sendMessage("       /archaeo give tracker|prospect|establish|tool|brush|paper|pencil [player]");
        sender.sendMessage("       /archaeo give tool <item> [player]");
        sender.sendMessage("       /archaeo workday reset [player|all]");
        sender.sendMessage("       /archaeo find spawn [artifact] [size]");
        sender.sendMessage("       /archaeo sketch");
        sender.sendMessage("       /archaeo reload");
    }

    /**
     * Gives a new unsigned sketch, or reminds how to save / sign if one is already in hand.
     *
     * @param sender staff issuer; must be a player
     * @return {@code true} always
     */
    private boolean handleSketch(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can open the sketch prototype.");
            return true;
        }
        plugin.sketch().begin(player);
        return true;
    }

    /**
     * Completes ruin/tracker staff tokens.
     *
     * @param sender command issuer
     * @param command Bukkit command metadata
     * @param alias alias used
     * @param args tokens typed so far
     * @return matching suggestions, or empty
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(catalogs.staffPermission())) {
            return List.of();
        }
        if (args.length == 1) {
            return ROOT.stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if ("give".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                return GIVE_KINDS.stream()
                        .filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .toList();
            }
            if (args.length == 3) {
                if ("tool".equalsIgnoreCase(args[1]) || "pick".equalsIgnoreCase(args[1])) {
                    String typed = args[2].toLowerCase(Locale.ROOT);
                    return handPick.giveToolTokens().stream()
                            .filter(token -> token.toLowerCase(Locale.ROOT).startsWith(typed))
                            .toList();
                }
                return onlineNamesStartingWith(args[2]);
            }
            if (args.length == 4 && ("tool".equalsIgnoreCase(args[1]) || "pick".equalsIgnoreCase(args[1]))) {
                return onlineNamesStartingWith(args[3]);
            }
            return List.of();
        }
        if ("workday".equalsIgnoreCase(args[0]) || "pick".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                return WORKDAY_ACTIONS.stream()
                        .filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .toList();
            }
            if (args.length == 3 && "reset".equalsIgnoreCase(args[1])) {
                List<String> names = new ArrayList<>(onlineNamesStartingWith(args[2]));
                if ("all".startsWith(args[2].toLowerCase(Locale.ROOT))) {
                    names.add(0, "all");
                }
                return names;
            }
            return List.of();
        }
        if ("find".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                return FIND_ACTIONS.stream()
                        .filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .toList();
            }
            if (args.length == 3 && "spawn".equalsIgnoreCase(args[1])) {
                String typed = args[2].toLowerCase(Locale.ROOT);
                return catalogs.artifacts().keySet().stream()
                        .filter(id -> id.startsWith(typed))
                        .toList();
            }
            return List.of();
        }
        if (!"ruin".equalsIgnoreCase(args[0])) {
            return List.of();
        }
        if (args.length == 2) {
            return RUIN_ACTIONS.stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length >= 3 && "create".equalsIgnoreCase(args[1])) {
            if (args.length == 3) {
                return INTERESTS.stream()
                        .filter(value -> value.startsWith(args[2].toLowerCase(Locale.ROOT)))
                        .toList();
            }
            return List.of();
        }
        if (args.length >= 3 && "info".equalsIgnoreCase(args[1])) {
            String typed = Arrays.stream(args).skip(2).collect(Collectors.joining(" "));
            List<String> names = new ArrayList<>(sites.namesStartingWith(typed));
            if (args.length == 3) {
                String token = args[2].toLowerCase(Locale.ROOT);
                for (Site site : sites.all()) {
                    String serial = "#" + site.getSerial();
                    if (serial.startsWith(token) || String.valueOf(site.getSerial()).startsWith(token)) {
                        names.add(serial);
                    }
                }
            }
            return names;
        }
        if (args.length >= 3 && "tp".equalsIgnoreCase(args[1])) {
            String typed = Arrays.stream(args).skip(2).collect(Collectors.joining(" "));
            List<String> names = new ArrayList<>(sites.namesStartingWith(typed));
            if (args.length == 3) {
                String token = args[2].toLowerCase(Locale.ROOT);
                for (Site site : sites.all()) {
                    String serial = "#" + site.getSerial();
                    if (serial.startsWith(token) || String.valueOf(site.getSerial()).startsWith(token)) {
                        names.add(serial);
                    }
                }
            }
            return names;
        }
        if (args.length >= 3 && "camps".equalsIgnoreCase(args[1])) {
            if (args.length == 3) {
                List<String> suggestions = new ArrayList<>(onlineNamesStartingWith(args[2]));
                String token = args[2].toLowerCase(Locale.ROOT);
                for (Site site : sites.all()) {
                    if (!site.isCampLocked()) {
                        continue;
                    }
                    String serial = "#" + site.getSerial();
                    if (serial.startsWith(token) || String.valueOf(site.getSerial()).startsWith(token)) {
                        suggestions.add(serial);
                    }
                }
                return suggestions;
            }
            if (args.length == 4) {
                OfflinePlayer director = CampNames.known(args[2]);
                if (director == null) {
                    return List.of();
                }
                String token = args[3].toLowerCase(Locale.ROOT);
                List<String> serials = new ArrayList<>();
                for (Site site : sites.findDirectedCamps(director.getUniqueId())) {
                    String serial = "#" + site.getSerial();
                    if (serial.startsWith(token) || String.valueOf(site.getSerial()).startsWith(token)) {
                        serials.add(serial);
                    }
                }
                return serials;
            }
            return List.of();
        }
        return List.of();
    }

    /**
     * @param prefix player-name prefix already typed
     * @return online names that start with {@code prefix}
     */
    private List<String> onlineNamesStartingWith(String prefix) {
        String typed = prefix.toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(typed)) {
                names.add(online.getName());
            }
        }
        return names;
    }
}
