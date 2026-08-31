package com.nowko.archeology.command;

import com.nowko.archeology.config.CatalogRegistry;
import com.nowko.archeology.establish.EstablishService;
import com.nowko.archeology.excavation.HandPickService;
import com.nowko.archeology.item.EstablishItem;
import com.nowko.archeology.item.HandPickItem;
import com.nowko.archeology.item.ProspectItem;
import com.nowko.archeology.item.TrackerItem;
import com.nowko.archeology.model.BuriedFind;
import com.nowko.archeology.model.InterestLevel;
import com.nowko.archeology.model.Site;
import com.nowko.archeology.model.SiteStatus;
import com.nowko.archeology.model.StratumBand;
import com.nowko.archeology.prospect.ProspectService;
import com.nowko.archeology.site.SiteGenerator;
import com.nowko.archeology.site.SiteRepository;
import com.nowko.archeology.tracker.TrackerService;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Staff-only {@code /archaeo} commands. Players never use this; discovery is tracker and kits.
 */
public class ArchaeoCommand implements CommandExecutor, TabCompleter {
    private static final List<String> INTERESTS = List.of("low", "medium", "high", "exceptional");
    private static final List<String> ROOT = List.of("ruin", "tracker", "prospect", "establish", "pick", "reload");
    private static final List<String> RUIN_ACTIONS = List.of("create", "info");
    private static final List<String> GIVE_ACTIONS = List.of("give");
    private static final List<String> PICK_ACTIONS = List.of("give", "reset");

    private final CatalogRegistry catalogs;
    private final SiteGenerator generator;
    private final SiteRepository sites;
    private final TrackerItem trackerItem;
    private final TrackerService tracker;
    private final ProspectItem prospectItem;
    private final ProspectService prospect;
    private final EstablishItem establishItem;
    private final EstablishService establish;
    private final HandPickItem handPickItem;
    private final HandPickService handPick;

    /**
     * @param catalogs staff permission and YAML catalogs
     * @param generator used to persist a new managed ruin
     * @param sites lookup for {@code ruin info} and reload
     * @param trackerItem factory for {@code tracker give}
     * @param tracker live scan loop, updated on reload
     * @param prospectItem factory for {@code prospect give}
     * @param prospect sample loop, updated on reload
     * @param establishItem factory for {@code establish give}
     * @param establish camp outline loop, updated on reload
     * @param handPickItem factory for {@code pick give}
     * @param handPick strike-cycle loop, updated on reload
     */
    public ArchaeoCommand(
            CatalogRegistry catalogs,
            SiteGenerator generator,
            SiteRepository sites,
            TrackerItem trackerItem,
            TrackerService tracker,
            ProspectItem prospectItem,
            ProspectService prospect,
            EstablishItem establishItem,
            EstablishService establish,
            HandPickItem handPickItem,
            HandPickService handPick
    ) {
        this.catalogs = catalogs;
        this.generator = generator;
        this.sites = sites;
        this.trackerItem = trackerItem;
        this.tracker = tracker;
        this.prospectItem = prospectItem;
        this.prospect = prospect;
        this.establishItem = establishItem;
        this.establish = establish;
        this.handPickItem = handPickItem;
        this.handPick = handPick;
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
        if (args.length >= 1 && "tracker".equalsIgnoreCase(args[0])) {
            return handleTracker(sender, args);
        }
        if (args.length >= 1 && "prospect".equalsIgnoreCase(args[0])) {
            return handleProspect(sender, args);
        }
        if (args.length >= 1 && "establish".equalsIgnoreCase(args[0])) {
            return handleEstablish(sender, args);
        }
        if (args.length >= 1 && "pick".equalsIgnoreCase(args[0])) {
            return handlePick(sender, args);
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
        sendUsage(sender);
        return true;
    }

    /**
     * Re-reads catalog YAML from the data folder into memory. Does not overwrite existing files.
     *
     * @param sender staff issuer
     * @return {@code true} always (handled)
     */
    private boolean handleReload(CommandSender sender) {
        try {
            catalogs.load();
            trackerItem.update(catalogs.tracker(), catalogs.items().tracker());
            tracker.setSettings(catalogs.tracker());
            prospectItem.update(catalogs.prospect(), catalogs.items().prospect());
            prospect.setSettings(catalogs.prospect());
            establishItem.update(catalogs.establish(), catalogs.items().establish());
            establish.setSettings(catalogs.establish());
            handPickItem.update(catalogs.pick(), catalogs.items().pick());
            handPick.setSettings(catalogs.pick());
            sites.loadAll();
            sender.sendMessage("Reloaded Archaeo config, catalogs, and sites from disk.");
        } catch (RuntimeException exception) {
            sender.sendMessage("Reload failed: " + exception.getMessage());
        }
        return true;
    }

    /**
     * Gives a tracker item. Players scan by holding it; they do not run this command.
     *
     * @param sender staff issuer
     * @param args {@code tracker give [player]}
     * @return {@code true} always (handled)
     */
    private boolean handleTracker(CommandSender sender, String[] args) {
        if (args.length < 2 || !"give".equalsIgnoreCase(args[1])) {
            sender.sendMessage("Usage: /archaeo tracker give [player]");
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
            sender.sendMessage("Console must name a player: /archaeo tracker give <player>");
            return true;
        }
        target.getInventory().addItem(trackerItem.create());
        sender.sendMessage("Gave an archaeological tracker to " + target.getName() + ".");
        if (target != sender) {
            target.sendMessage("You received an archaeological tracker. Hold it to listen for hidden ruins.");
        }
        return true;
    }

    /**
     * Gives a prospecting kit. Players sample by right-clicking ground; they do not run this command.
     *
     * @param sender staff issuer
     * @param args {@code prospect give [player]}
     * @return {@code true} always (handled)
     */
    private boolean handleProspect(CommandSender sender, String[] args) {
        if (args.length < 2 || !"give".equalsIgnoreCase(args[1])) {
            sender.sendMessage("Usage: /archaeo prospect give [player]");
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
            sender.sendMessage("Console must name a player: /archaeo prospect give <player>");
            return true;
        }
        target.getInventory().addItem(prospectItem.create());
        sender.sendMessage("Gave a prospecting kit to " + target.getName() + ".");
        if (target != sender) {
            target.sendMessage("You received a prospecting kit. Right-click ground in a suspected chunk.");
        }
        return true;
    }

    /**
     * Gives an establishment kit. Players plant a camp in a neighbor chunk; they do not run this command.
     *
     * @param sender staff issuer
     * @param args {@code establish give [player]}
     * @return {@code true} always (handled)
     */
    private boolean handleEstablish(CommandSender sender, String[] args) {
        if (args.length < 2 || !"give".equalsIgnoreCase(args[1])) {
            sender.sendMessage("Usage: /archaeo establish give [player]");
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
            sender.sendMessage("Console must name a player: /archaeo establish give <player>");
            return true;
        }
        target.getInventory().addItem(establishItem.create());
        sender.sendMessage("Gave an establishment kit to " + target.getName() + ".");
        if (target != sender) {
            target.sendMessage("You received an establishment kit. Use it next to a ruin you have confirmed.");
        }
        return true;
    }

    /**
     * Gives a Hand Pick or refills today's pick budget on an established excavation.
     *
     * @param sender staff issuer
     * @param args {@code pick give [player]} or {@code pick reset [player|all]}
     * @return {@code true} always (handled)
     */
    private boolean handlePick(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /archaeo pick give [player]");
            sender.sendMessage("       /archaeo pick reset [player|all]");
            return true;
        }
        if ("reset".equalsIgnoreCase(args[1])) {
            return handlePickReset(sender, args);
        }
        if (!"give".equalsIgnoreCase(args[1])) {
            sender.sendMessage("Usage: /archaeo pick give [player]");
            sender.sendMessage("       /archaeo pick reset [player|all]");
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
            sender.sendMessage("Console must name a player: /archaeo pick give <player>");
            return true;
        }
        target.getInventory().addItem(handPickItem.create());
        sender.sendMessage("Gave a Hand Pick to " + target.getName() + ".");
        if (target != sender) {
            target.sendMessage("You received a Hand Pick. Hold left-click; soft chimes, then release on the ready chime.");
        }
        return true;
    }

    /**
     * Restores Hand Pick jornada on the excavation at a player's feet, or every established site.
     *
     * @param sender staff issuer
     * @param args {@code pick reset [player|all]}
     * @return {@code true} always (handled)
     */
    private boolean handlePickReset(CommandSender sender, String[] args) {
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
            sender.sendMessage("Console must name a player or use: /archaeo pick reset all");
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
                    + " · " + find.getConservation() + "%"
                    + (find.isDamaged() ? " · damaged" : ""));
        }
    }

    /**
     * @param sender who receives usage
     */
    private void sendUsage(CommandSender sender) {
        sender.sendMessage("Usage: /archaeo ruin create <low|medium|high|exceptional> [name]");
        sender.sendMessage("       /archaeo ruin info [name|#serial]");
        sender.sendMessage("       /archaeo tracker give [player]");
        sender.sendMessage("       /archaeo prospect give [player]");
        sender.sendMessage("       /archaeo establish give [player]");
        sender.sendMessage("       /archaeo pick give [player]");
        sender.sendMessage("       /archaeo pick reset [player|all]");
        sender.sendMessage("       /archaeo reload");
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
        if ("tracker".equalsIgnoreCase(args[0])
                || "prospect".equalsIgnoreCase(args[0])
                || "establish".equalsIgnoreCase(args[0])
                || "pick".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                List<String> actions = "pick".equalsIgnoreCase(args[0]) ? PICK_ACTIONS : GIVE_ACTIONS;
                return actions.stream()
                        .filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .toList();
            }
            if (args.length == 3 && "give".equalsIgnoreCase(args[1])) {
                return onlineNamesStartingWith(args[2]);
            }
            if (args.length == 3 && "pick".equalsIgnoreCase(args[0]) && "reset".equalsIgnoreCase(args[1])) {
                List<String> names = new ArrayList<>(onlineNamesStartingWith(args[2]));
                if ("all".startsWith(args[2].toLowerCase(Locale.ROOT))) {
                    names.add(0, "all");
                }
                return names;
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
