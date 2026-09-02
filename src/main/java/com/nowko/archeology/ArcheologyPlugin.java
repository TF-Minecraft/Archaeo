package com.nowko.archeology;

import com.nowko.archeology.command.ArchaeoCommand;
import com.nowko.archeology.config.CatalogRegistry;
import com.nowko.archeology.establish.CampListener;
import com.nowko.archeology.establish.EstablishListener;
import com.nowko.archeology.establish.EstablishService;
import com.nowko.archeology.excavation.DigTools;
import com.nowko.archeology.excavation.FindDustService;
import com.nowko.archeology.excavation.HandPickListener;
import com.nowko.archeology.excavation.HandPickService;
import com.nowko.archeology.excavation.PrismListener;
import com.nowko.archeology.excavation.RecoverListener;
import com.nowko.archeology.excavation.RecoverService;
import com.nowko.archeology.item.BrushItem;
import com.nowko.archeology.item.EstablishItem;
import com.nowko.archeology.item.ProspectItem;
import com.nowko.archeology.item.RecoveredFindItem;
import com.nowko.archeology.item.TrackerItem;
import com.nowko.archeology.prospect.ProspectListener;
import com.nowko.archeology.prospect.ProspectService;
import com.nowko.archeology.site.SiteGenerator;
import com.nowko.archeology.site.SiteRepository;
import com.nowko.archeology.tracker.TrackerService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Spigot entry point for Archaeo: catalogs, sites, staff commands, tracker, prospecting, camp, and dig prism.
 */
public class ArcheologyPlugin extends JavaPlugin {
    private CatalogRegistry catalogs;
    private SiteRepository sites;
    private SiteGenerator generator;
    private TrackerItem trackerItem;
    private TrackerService tracker;
    private ProspectItem prospectItem;
    private ProspectService prospect;
    private EstablishItem establishItem;
    private EstablishService establish;
    private DigTools digTools;
    private HandPickService handPick;
    private FindDustService findDust;
    private PrismListener prismListener;
    private BrushItem brushItem;
    private RecoverService recover;

    /**
     * Copies missing default YAML, loads catalogs and saved sites, and starts gameplay loops.
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
        prospectItem = new ProspectItem(this, catalogs.prospect(), catalogs.items().prospect());
        prospect = new ProspectService(this, catalogs, sites, prospectItem, catalogs.prospect());
        getServer().getPluginManager().registerEvents(new ProspectListener(prospectItem, prospect), this);
        establishItem = new EstablishItem(this, catalogs.establish(), catalogs.items().establish());
        establish = new EstablishService(this, sites, establishItem, catalogs.establish());
        establish.start();
        getServer().getPluginManager().registerEvents(new EstablishListener(establishItem, establish), this);
        getServer().getPluginManager().registerEvents(new CampListener(this, sites, establishItem, establish), this);
        digTools = new DigTools();
        handPick = new HandPickService(this, sites, catalogs, digTools, catalogs.pick());
        handPick.start();
        findDust = new FindDustService(this, sites, catalogs.pick());
        establish.setFindDust(findDust);
        getServer().getPluginManager().registerEvents(findDust, this);
        findDust.start();
        getServer().getPluginManager().registerEvents(new HandPickListener(handPick, sites), this);
        prismListener = new PrismListener(sites, digTools, catalogs.establish().protectDigSite());
        getServer().getPluginManager().registerEvents(prismListener, this);
        brushItem = new BrushItem(catalogs.recovery(), catalogs.items().brush());
        recover = new RecoverService(
                this,
                sites,
                catalogs,
                brushItem,
                new RecoveredFindItem(this),
                catalogs.recovery());
        getServer().getPluginManager().registerEvents(new RecoverListener(brushItem, recover), this);

        ArchaeoCommand command = new ArchaeoCommand(
                catalogs,
                generator,
                sites,
                trackerItem,
                tracker,
                prospectItem,
                prospect,
                establishItem,
                establish,
                handPick,
                findDust,
                prismListener,
                brushItem,
                recover);
        PluginCommand pluginCommand = getCommand("archaeo");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }
        getLogger().info("Archaeo enabled. Loaded sites: " + sites.all().size());
    }

    /**
     * Stops tracker, prospecting, establishment, Hand Pick HUD, and find-dust tasks.
     */
    @Override
    public void onDisable() {
        if (tracker != null) {
            tracker.stop();
        }
        if (prospect != null) {
            prospect.stop();
        }
        if (establish != null) {
            establish.stop();
        }
        if (handPick != null) {
            handPick.stop();
        }
        if (findDust != null) {
            findDust.stop();
        }
        if (recover != null) {
            recover.stop();
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

    /**
     * @return prospecting-kit factory
     */
    public ProspectItem prospectItem() {
        return prospectItem;
    }

    /**
     * @return establishment-kit factory
     */
    public EstablishItem establishItem() {
        return establishItem;
    }
}
