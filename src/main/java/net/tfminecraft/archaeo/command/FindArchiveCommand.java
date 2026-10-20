package net.tfminecraft.archaeo.command;

import net.tfminecraft.archaeo.config.ArtifactTemplate;
import net.tfminecraft.archaeo.config.CatalogRegistry;
import net.tfminecraft.archaeo.item.RecoveredFindItem;
import net.tfminecraft.archaeo.model.BuriedFind;
import net.tfminecraft.archaeo.model.FindState;
import net.tfminecraft.archaeo.model.Site;
import net.tfminecraft.archaeo.site.SitePurge;
import net.tfminecraft.archaeo.site.SiteRepository;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/** Staff inspection and removal of individual recovered pieces, including retired catalog items. */
final class FindArchiveCommand {
    private static final int PAGE_SIZE = 8;
    private final SiteRepository sites;
    private final CatalogRegistry catalogs;
    private final RecoveredFindItem recovered;
    private final SitePurge purge;

    FindArchiveCommand(SiteRepository sites, CatalogRegistry catalogs, RecoveredFindItem recovered, SitePurge purge) {
        this.sites = sites;
        this.catalogs = catalogs;
        this.recovered = recovered;
        this.purge = purge;
    }

    boolean execute(CommandSender sender, String[] args) {
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> list(sender, args);
            case "info" -> info(sender, args);
            case "delete" -> delete(sender, args);
            default -> usage(sender);
        }
        return true;
    }

    private void list(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 4) {
            sender.sendMessage("Usage: /archaeo find list <#serial> [page]");
            return;
        }
        Site site = site(sender, args[2]);
        if (site == null) return;
        List<BuriedFind> finds = site.getFinds().stream()
                .filter(find -> find.getState() == FindState.RECOVERED).toList();
        int pages = Math.max(1, (finds.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = args.length == 4 ? positiveInt(args[3]) : 1;
        if (page < 1 || page > pages) {
            sender.sendMessage("Page must be between 1 and " + pages + ".");
            return;
        }
        sender.sendMessage("Recovered finds in " + site.displayLabel() + " (page " + page + "/" + pages + "):");
        finds.stream().skip((long) (page - 1) * PAGE_SIZE).limit(PAGE_SIZE).forEach(find -> {
            sender.sendMessage("  " + find.getFindNumber() + " · " + find.getArtifactId()
                    + " · " + find.publicNumber(site));
            mapping(sender, find);
        });
        if (finds.isEmpty()) sender.sendMessage("No recovered finds in this ruin.");
        sender.sendMessage("Inspect: /archaeo find info #" + site.getSerial() + " <number|uuid>");
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length == 2 && sender instanceof Player player) {
            ItemStack held = player.getInventory().getItemInMainHand();
            UUID siteId = recovered.siteIdOf(held);
            UUID findId = recovered.findIdOf(held);
            Site site = siteId == null ? null : sites.findById(siteId).orElse(null);
            BuriedFind find = site == null ? null : site.findById(findId).orElse(null);
            if (find == null) {
                sender.sendMessage("Hold a recovered Archaeo piece with an existing excavation record.");
                return;
            }
            describe(sender, site, find);
            sender.sendMessage("Held material: " + held.getType().name());
            return;
        }
        if (args.length != 4) {
            sender.sendMessage("Usage: /archaeo find info [<#serial> <number|uuid>] (no arguments: main hand)");
            return;
        }
        Site site = site(sender, args[2]);
        if (site == null) return;
        BuriedFind find = find(sender, site, args[3]);
        if (find != null) describe(sender, site, find);
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length < 4 || args.length > 5 || (args.length == 5 && !"confirm".equalsIgnoreCase(args[4]))) {
            sender.sendMessage("Usage: /archaeo find delete <#serial> <number|uuid> confirm");
            return;
        }
        Site site = site(sender, args[2]);
        if (site == null) return;
        BuriedFind find = find(sender, site, args[3]);
        if (find == null) return;
        if (find.getState() != FindState.RECOVERED) {
            sender.sendMessage("Only recovered finds can be deleted; buried and lost finds are unchanged.");
            return;
        }
        if (site.isOwnershipHeld()) {
            sender.sendMessage("This excavation record is held for ownership review and cannot be changed.");
            return;
        }
        if (args.length == 4) {
            describe(sender, site, find);
            sender.sendMessage("Delete this piece and its archive row: /archaeo find delete #"
                    + site.getSerial() + " " + find.getId() + " confirm");
            return;
        }
        String number = find.publicNumber(site);
        Path backup;
        try {
            backup = purge.eraseFind(site, find);
        } catch (RuntimeException failure) {
            sender.sendMessage("Find deletion failed: " + failure.getMessage());
            sender.sendMessage("Check /archaeo find info #" + site.getSerial() + " " + find.getId()
                    + " before retrying; physical cleanup may be incomplete.");
            return;
        }
        sender.sendMessage("Deleted " + number + " (" + find.getId() + ") from " + site.displayLabel() + ".");
        sender.sendMessage("Dossier backup: " + backup);
        sender.sendMessage("Matching items removed from online players and loaded locations."
                + " Offline inventories, unloaded chunks, and previously printed books are not edited.");
    }

    private void describe(CommandSender sender, Site site, BuriedFind find) {
        sender.sendMessage("Find " + find.publicNumber(site) + " · ruin " + site.displayLabel());
        sender.sendMessage("UUID: " + find.getId());
        sender.sendMessage("Artifact: " + find.getArtifactId() + " · state: " + find.getState()
                + " · conservation: " + find.getConservation() + "%");
        mapping(sender, find);
        sender.sendMessage("Recovered by: " + find.getRecoveredBy() + " · at: " + find.getRecoveredAt());
    }

    private void mapping(CommandSender sender, BuriedFind find) {
        ArtifactTemplate template = catalogs.artifact(find.getArtifactId());
        String stored = find.getItem();
        sender.sendMessage("  Stored item: " + (stored == null || stored.isBlank() ? "not recorded (legacy find)" : stored));
        if (template == null) {
            sender.sendMessage("  Current item: artifact ID is absent from artifacts.yml; choose a replacement manually.");
            return;
        }
        sender.sendMessage("  Current item(s): " + template.items().stream()
                .map(ref -> ref.commandToken()).collect(Collectors.joining(", ")));
        if (stored != null && !stored.isBlank() && !template.offers(stored)) {
            sender.sendMessage("  Stored item is outside the current pool; giving this artifact uses the current pool.");
        }
    }

    private Site site(CommandSender sender, String token) {
        int serial = positiveInt(token.startsWith("#") ? token.substring(1) : token);
        Site site = serial < 1 ? null : sites.findBySerial(serial).orElse(null);
        if (site == null) sender.sendMessage("Unknown ruin serial. Use #12, for example.");
        return site;
    }

    private BuriedFind find(CommandSender sender, Site site, String token) {
        BuriedFind found = null;
        int number = positiveInt(token);
        if (number > 0) {
            List<BuriedFind> matches = site.getFinds().stream().filter(find -> find.getFindNumber() == number).toList();
            if (matches.size() > 1) {
                sender.sendMessage("Several finds share that inventory number; use the full UUID.");
                return null;
            }
            if (matches.size() == 1) found = matches.getFirst();
        } else {
            try {
                found = site.findById(UUID.fromString(token)).orElse(null);
            } catch (IllegalArgumentException ignored) {
                // Require a full UUID or an inventory number, never an artifact type shared by many pieces.
            }
        }
        if (found == null) sender.sendMessage("No such find in this ruin. Use /archaeo find list #" + site.getSerial() + ".");
        return found;
    }

    List<String> complete(String[] args) {
        String action = args[1].toLowerCase(Locale.ROOT);
        if (!List.of("list", "info", "delete").contains(action)) return List.of();
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 3) {
            return sites.all().stream().map(site -> "#" + site.getSerial())
                    .filter(serial -> serial.startsWith(typed) || serial.substring(1).startsWith(typed)).sorted().toList();
        }
        int serial = positiveInt(args[2].startsWith("#") ? args[2].substring(1) : args[2]);
        Site site = sites.findBySerial(serial).orElse(null);
        if (site == null) return List.of();
        if (args.length == 4 && !"list".equals(action)) {
            return site.getFinds().stream()
                    .filter(find -> !"delete".equals(action) || find.getState() == FindState.RECOVERED)
                    .map(find -> find.getFindNumber() > 0 ? String.valueOf(find.getFindNumber()) : find.getId().toString())
                    .filter(number -> number.toLowerCase(Locale.ROOT).startsWith(typed)).distinct().toList();
        }
        return args.length == 5 && "delete".equals(action) && "confirm".startsWith(typed) ? List.of("confirm") : List.of();
    }

    private static int positiveInt(String token) {
        try {
            int number = Integer.parseInt(token);
            return number > 0 ? number : -1;
        } catch (NumberFormatException failure) {
            return -1;
        }
    }

    static void usage(CommandSender sender) {
        sender.sendMessage("Usage: /archaeo find spawn [artifact] [size]");
        sender.sendMessage("       /archaeo find list <#serial> [page]");
        sender.sendMessage("       /archaeo find info [<#serial> <number|uuid>]");
        sender.sendMessage("       /archaeo find delete <#serial> <number|uuid> confirm");
    }
}
