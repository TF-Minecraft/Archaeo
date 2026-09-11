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
import com.nowko.archeology.excavation.PrismOutlineService;
import com.nowko.archeology.excavation.RecoverListener;
import com.nowko.archeology.excavation.RecoverService;
import com.nowko.archeology.item.BrushItem;
import com.nowko.archeology.item.EstablishItem;
import com.nowko.archeology.item.ItemMatcher;
import com.nowko.archeology.item.PackPluginHook;
import com.nowko.archeology.item.ProspectItem;
import com.nowko.archeology.item.RecoveredFindItem;
import com.nowko.archeology.item.RecoveredFindListener;
import com.nowko.archeology.item.SketchSupplies;
import com.nowko.archeology.item.TrackerItem;
import com.nowko.archeology.museum.MuseumListener;
import com.nowko.archeology.prospect.ProspectListener;
import com.nowko.archeology.prospect.ProspectService;
import com.nowko.archeology.site.SiteGenerator;
import com.nowko.archeology.site.SiteRepository;
import com.nowko.archeology.sketch.SketchListener;
import com.nowko.archeology.sketch.SketchService;
import com.nowko.archeology.tracker.TrackerService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Spigot entry point for Archaeo: catalogs, sites, staff commands, tracker, prospecting, camp, dig prism, and the sketch prototype.
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
    private PrismOutlineService outline;
    private PrismListener prismListener;
    private BrushItem brushItem;
    private RecoveredFindItem recoveredFindItem;
    private RecoverService recover;
    private SketchSupplies sketchSupplies;
    private SketchService sketch;
    private MuseumListener museum;

    /**
     * Copies missing default YAML, loads catalogs and saved sites, starts the dossier flusher, and starts gameplay loops.
     */
    @Override
    public void onEnable() {
        catalogs = new CatalogRegistry(this);
        catalogs.load();
        sites = new SiteRepository(this);
        sites.loadAll();
        sites.start();
        generator = new SiteGenerator(catalogs, sites);
        trackerItem = new TrackerItem(catalogs.items().tracker());
        prospectItem = new ProspectItem(catalogs.items().prospect());
        establishItem = new EstablishItem(catalogs.items().establish());
        brushItem = new BrushItem(catalogs.items().brush());
        sketchSupplies = new SketchSupplies(
                this,
                catalogs.items().sketchPaper(),
                catalogs.items().sketchPencil(),
                catalogs.sketch().pencilUses());
        digTools = new DigTools();
        tracker = new TrackerService(this, sites, trackerItem, catalogs.tracker());
        tracker.start();
        prospect = new ProspectService(this, catalogs, sites, prospectItem, catalogs.prospect());
        getServer().getPluginManager().registerEvents(new ProspectListener(prospectItem, prospect), this);
        establish = new EstablishService(this, sites, establishItem, catalogs.establish());
        establish.start();
        getServer().getPluginManager().registerEvents(new EstablishListener(establishItem, establish), this);
        handPick = new HandPickService(this, sites, catalogs, digTools, catalogs.pick());
        handPick.start();
        outline = new PrismOutlineService(this, catalogs);
        recoveredFindItem = new RecoveredFindItem(this);
        getServer().getPluginManager().registerEvents(
                new RecoveredFindListener(this, sites, catalogs, recoveredFindItem),
                this);
        getServer().getPluginManager().registerEvents(
                new CampListener(
                        this,
                        sites,
                        catalogs,
                        establishItem,
                        establish,
                        handPick,
                        outline,
                        brushItem,
                        recoveredFindItem),
                this);
        findDust = new FindDustService(this, sites, catalogs.pick());
        establish.setFindDust(findDust);
        getServer().getPluginManager().registerEvents(findDust, this);
        findDust.start();
        getServer().getPluginManager().registerEvents(new HandPickListener(handPick, sites), this);
        prismListener = new PrismListener(sites, digTools, catalogs.establish().protectDigSite());
        getServer().getPluginManager().registerEvents(prismListener, this);
        recover = new RecoverService(
                this,
                sites,
                catalogs,
                brushItem,
                recoveredFindItem,
                catalogs.recovery());
        getServer().getPluginManager().registerEvents(new RecoverListener(brushItem, recover), this);
        sketch = new SketchService(this, sketchSupplies, recoveredFindItem, sites, catalogs, catalogs.sketch());
        sketch.start();
        getServer().getPluginManager().registerEvents(new SketchListener(sketch), this);
        museum = new MuseumListener(sites, catalogs, recoveredFindItem);
        getServer().getPluginManager().registerEvents(museum, this);
        bindItemMatcher(ItemMatcher.detect(this));
        PackPluginHook.register(this);

        ArchaeoCommand command = new ArchaeoCommand(
                this,
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
     * Binds ItemsAdder / MMOItems lookups on role items and the excavation whitelist.
     *
     * @param matcher detected APIs, or vanilla-only
     */
    public void bindItemMatcher(ItemMatcher matcher) {
        ItemMatcher bound = matcher == null ? ItemMatcher.vanillaOnly() : matcher;
        if (trackerItem != null) {
            trackerItem.setMatcher(bound);
        }
        if (prospectItem != null) {
            prospectItem.setMatcher(bound);
        }
        if (establishItem != null) {
            establishItem.setMatcher(bound);
        }
        if (brushItem != null) {
            brushItem.setMatcher(bound);
        }
        if (sketchSupplies != null) {
            sketchSupplies.setMatcher(bound);
        }
        if (digTools != null) {
            digTools.setMatcher(bound);
        }
        if (sketch != null) {
            sketch.setMatcher(bound);
        }
        if (museum != null) {
            museum.setMatcher(bound);
        }
    }

    /**
     * Stops tracker, prospecting, establishment, Hand Pick HUD, prism outlines, find-particles, and the sketch prototype, then flushes dirty dossiers.
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
        if (outline != null) {
            outline.stop();
        }
        if (recover != null) {
            recover.stop();
        }
        if (sketch != null) {
            sketch.stop();
        }
        if (sites != null) {
            sites.stop();
        }
    }

    /**
     * @return staff field-sketch; the drawing lives on the {@code FILLED_MAP} item
     */
    public SketchService sketch() {
        return sketch;
    }

    /**
     * @return plaque opens from configured display furniture
     */
    public MuseumListener museum() {
        return museum;
    }

    /**
     * @return configured field sheet and pencil
     */
    public SketchSupplies sketchSupplies() {
        return sketchSupplies;
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
