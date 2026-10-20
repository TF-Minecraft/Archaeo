package net.tfminecraft.archaeo.command;

import net.tfminecraft.archaeo.establish.CampNames;
import net.tfminecraft.archaeo.model.Site;
import net.tfminecraft.archaeo.site.SiteRepository;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Read-only directory of every excavation, optionally filtered by a player's character records. */
final class RuinListCommand {
    private static final int PAGE_SIZE = 10;
    private final SiteRepository sites;

    RuinListCommand(SiteRepository sites) {
        this.sites = sites;
    }

    boolean execute(CommandSender sender, String[] args) {
        if (args.length > 4) {
            sender.sendMessage("Usage: /archaeo ruin list [player|all] [page]");
            return true;
        }
        OfflinePlayer player = null;
        if (args.length >= 3 && !"all".equalsIgnoreCase(args[2])) {
            player = CampNames.known(args[2]);
            if (player == null) {
                sender.sendMessage("Unknown player: " + args[2]);
                return true;
            }
        }
        UUID account = player == null ? null : player.getUniqueId();
        List<Site> matches = sites.all().stream()
                .filter(site -> account == null || participated(site, account))
                .sorted(Comparator.comparingInt(Site::getSerial)).toList();
        int pages = Math.max(1, (matches.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page;
        try {
            page = args.length == 4 ? Integer.parseInt(args[3]) : 1;
        } catch (NumberFormatException invalid) {
            page = 0;
        }
        if (page < 1 || page > pages) {
            sender.sendMessage("Page must be between 1 and " + pages + ".");
            return true;
        }
        String who = player == null ? "All players" : player.getName();
        sender.sendMessage("Sites: " + who + " · " + matches.size() + " total (page " + page + "/" + pages + "):");
        matches.stream().skip((long) (page - 1) * PAGE_SIZE).limit(PAGE_SIZE).forEach(site -> line(sender, site));
        if (matches.isEmpty()) sender.sendMessage("No matching sites.");
        sender.sendMessage("Use /archaeo find list <#serial> to inspect a site's recovered pieces.");
        if (page < pages) sender.sendMessage("Next: /archaeo ruin list "
                + (args.length >= 3 ? args[2] : "all") + " " + (page + 1));
        return true;
    }

    /** Accounts select all their recorded characters; character UUIDs never double as account IDs. */
    private static boolean participated(Site site, UUID account) {
        return site.getActorRefs().entrySet().stream()
                .filter(entry -> account.equals(entry.getValue().ownerId()))
                .anyMatch(entry -> {
                    UUID character = entry.getKey();
                    return character.equals(site.getDirector()) || site.getExcavators().contains(character)
                            || site.getWorkers().containsKey(character)
                            || site.getFinds().stream().anyMatch(find -> character.equals(find.getRecoveredBy()));
                });
    }

    private static void line(CommandSender sender, Site site) {
        String body = "  " + site.displayLabel() + " · " + site.getStatus().name().toLowerCase(Locale.ROOT)
                + " · director: " + CampNames.of(null, site.getDirector());
        if (sender instanceof Player player) {
            TextComponent text = new TextComponent(body);
            text.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                    "/archaeo find list #" + site.getSerial()));
            text.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new Text("Inspect recovered pieces in " + site.displayLabel())));
            player.spigot().sendMessage(text);
        } else {
            sender.sendMessage(body);
        }
    }

    List<String> complete(String[] args) {
        if (args.length != 3) return List.of();
        String typed = args[2].toLowerCase(Locale.ROOT);
        var names = Arrays.stream(Bukkit.getOfflinePlayers()).map(OfflinePlayer::getName)
                .filter(name -> name != null && name.toLowerCase(Locale.ROOT).startsWith(typed));
        return java.util.stream.Stream.concat("all".startsWith(typed) ? java.util.stream.Stream.of("all")
                : java.util.stream.Stream.empty(), names).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
