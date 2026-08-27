package com.nowko.archeology.command;

import com.nowko.archeology.model.InterestLevel;
import com.nowko.archeology.model.Site;
import com.nowko.archeology.site.SiteGenerator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Staff command {@code /archaeo}. Currently only {@code ruin create} in the player's chunk.
 */
public class ArchaeoCommand implements CommandExecutor, TabCompleter {
    private static final List<String> INTERESTS = List.of("low", "medium", "high", "exceptional");

    private final SiteGenerator generator;

    /**
     * @param generator used to persist a new managed ruin
     */
    public ArchaeoCommand(SiteGenerator generator) {
        this.generator = generator;
    }

    /**
     * Handles {@code /archaeo ruin create <interest> [name]}. Must be a player standing in the target chunk.
     *
     * @param sender command issuer
     * @param command Bukkit command metadata
     * @param label alias used
     * @param args tokens after the command name
     * @return {@code true} so Bukkit does not print the plugin.yml usage line
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command must be used in the site chunk.");
            return true;
        }
        if (args.length < 3 || !"ruin".equalsIgnoreCase(args[0]) || !"create".equalsIgnoreCase(args[1])) {
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
     * Completes {@code ruin}, {@code create}, and interest keys.
     *
     * @param sender command issuer
     * @param command Bukkit command metadata
     * @param alias alias used
     * @param args tokens typed so far
     * @return matching suggestions, or empty
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return prefix("ruin", args[0]);
        }
        if (args.length == 2) {
            return prefix("create", args[1]);
        }
        if (args.length == 3) {
            return INTERESTS.stream()
                    .filter(value -> value.startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }

    /**
     * @param option full token to suggest
     * @param typed current argument
     * @return {@code option} if it starts with {@code typed}, otherwise empty
     */
    private List<String> prefix(String option, String typed) {
        return option.startsWith(typed.toLowerCase(Locale.ROOT)) ? List.of(option) : List.of();
    }
}
