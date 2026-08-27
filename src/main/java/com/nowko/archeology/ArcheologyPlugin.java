package com.nowko.archeology;

import com.nowko.archeology.command.ArchaeoCommand;
import com.nowko.archeology.config.CatalogRegistry;
import com.nowko.archeology.item.TrackerItem;
import com.nowko.archeology.site.SiteGenerator;
import com.nowko.archeology.site.SiteRepository;
import com.nowko.archeology.tracker.TrackerService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Spigot entry point for Archaeo: catalogs, site persistence, staff commands, and tracker.
 */
public class ArcheologyPlugin extends JavaPlugin {
    private CatalogRegistry catalogs;
    private SiteRepository sites;
    private SiteGenerator generator;
    private TrackerItem trackerItem;
    private TrackerService tracker;

    /**
     * Copies missing default YAML, loads catalogs and saved sites, and starts the tracker loop.
     */
    @Override
    public void onEnable() {
        catalogs = new CatalogRegistry(this);
        catalogs.load();
        sites = new SiteRepository(this);
        sites.loadAll();
        generator = new SiteGenerator(catalogs, sites);
        trackerItem = new TrackerItem(this, catalogs.tracker(), catalogs.items().tracker());
        tracker = new TrackerService(this, sites, trackerItem, catalogs.tracker());
        tracker.start();

        ArchaeoCommand command = new ArchaeoCommand(catalogs, generator, sites, trackerItem);
        PluginCommand pluginCommand = getCommand("archaeo");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }
        getLogger().info("Archaeo enabled. Loaded sites: " + sites.all().size());
    }

    /**
     * Stops the tracker scan loop.
     */
    @Override
    public void onDisable() {
        if (tracker != null) {
            tracker.stop();
        }
    }

    /**
     * @return loaded YAML catalogs (interest, strata, artifacts, hints)
     */
    public CatalogRegistry catalogs() {
        return catalogs;
    }

    /**
     * @return persisted excavation sites
     */
    public SiteRepository sites() {
        return sites;
    }

    /**
     * @return factory for administered ruins
     */
    public SiteGenerator generator() {
        return generator;
    }

    /**
     * @return tracker item factory
     */
    public TrackerItem trackerItem() {
        return trackerItem;
    }
}
