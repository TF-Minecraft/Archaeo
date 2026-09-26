package net.tfminecraft.archaeo;

import net.tfminecraft.archaeo.command.ArchaeoCommand;
import net.tfminecraft.archaeo.config.CatalogRegistry;
import net.tfminecraft.archaeo.establish.CampListener;
import net.tfminecraft.archaeo.establish.CampClosure;
import net.tfminecraft.archaeo.establish.EstablishListener;
import net.tfminecraft.archaeo.establish.EstablishService;
import net.tfminecraft.archaeo.excavation.DigTools;
import net.tfminecraft.archaeo.excavation.FindDustService;
import net.tfminecraft.archaeo.excavation.HandPickListener;
import net.tfminecraft.archaeo.excavation.HandPickService;
import net.tfminecraft.archaeo.excavation.PrismListener;
import net.tfminecraft.archaeo.excavation.PrismOutlineService;
import net.tfminecraft.archaeo.excavation.RecoverListener;
import net.tfminecraft.archaeo.excavation.RecoverService;
import net.tfminecraft.archaeo.item.BrushItem;
import net.tfminecraft.archaeo.item.EstablishItem;
import net.tfminecraft.archaeo.item.ItemMatcher;
import net.tfminecraft.archaeo.item.PackPluginHook;
import net.tfminecraft.archaeo.item.ProspectItem;
import net.tfminecraft.archaeo.item.RecoveredFindItem;
import net.tfminecraft.archaeo.item.RecoveredFindListener;
import net.tfminecraft.archaeo.item.SiteLabelRefresh;
import net.tfminecraft.archaeo.item.SketchSupplies;
import net.tfminecraft.archaeo.item.TrackerItem;
import net.tfminecraft.archaeo.museum.MuseumListener;
import net.tfminecraft.archaeo.prospect.ProspectListener;
import net.tfminecraft.archaeo.prospect.ProspectService;
import net.tfminecraft.archaeo.site.AutoRuinEvaluationLedger;
import net.tfminecraft.archaeo.site.RuinAutoSpawner;
import net.tfminecraft.archaeo.site.SiteGenerator;
import net.tfminecraft.archaeo.site.SitePurge;
import net.tfminecraft.archaeo.site.SiteRepository;
import net.tfminecraft.archaeo.sketch.SketchListener;
import net.tfminecraft.archaeo.sketch.SketchService;
import net.tfminecraft.archaeo.tracker.TrackerService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Spigot entry point for Archaeo: catalogs, sites, staff commands, tracker, prospecting, camp, dig prism, and the sketch prototype.
 */
public class ArcheologyPlugin extends JavaPlugin {
    private CatalogRegistry catalogs;
    private SiteRepository sites;
    private SiteGenerator generator;
    private AutoRuinEvaluationLedger autoRuinLedger;
    private RuinAutoSpawner autoRuins;
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
        autoRuinLedger = new AutoRuinEvaluationLedger(this);
        autoRuins = new RuinAutoSpawner(
                this, catalogs, sites, generator, autoRuinLedger, catalogs.autoRuins());
        getServer().getPluginManager().registerEvents(autoRuins, this);
        autoRuins.start();
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
        CampClosure campClosure = new CampClosure(this, sites);
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
                        recoveredFindItem,
                        campClosure),
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
        establish.setLabelRefresh(new SiteLabelRefresh(this, recoveredFindItem, catalogs, sketch));
        museum = new MuseumListener(sites, catalogs, recoveredFindItem);
        getServer().getPluginManager().registerEvents(museum, this);
        bindItemMatcher(ItemMatcher.detect(this));
        PackPluginHook.register(this);

        SitePurge sitePurge = new SitePurge(
                this,
                sites,
                autoRuins,
                establish,
                recover,
                findDust,
                recoveredFindItem,
                campClosure,
                sketch);
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
                recover,
                autoRuins,
                campClosure,
                sitePurge);
        // plugin.yml in this jar declares the command, so Bukkit always returns it.
        PluginCommand pluginCommand = getCommand("archaeo");
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);
        getLogger().info("Archaeo enabled. Loaded sites: " + sites.all().size());
    }

    /**
     * Binds ItemsAdder / MMOItems lookups on role items, recovered finds, and the excavation whitelist.
     *
     * @param matcher detected APIs, or vanilla-only
     */
    public void bindItemMatcher(ItemMatcher matcher) {
        // Callers (enable, reload, the pack-plugin hook) run only after onEnable built every item.
        ItemMatcher bound = matcher == null ? ItemMatcher.vanillaOnly() : matcher;
        trackerItem.setMatcher(bound);
        prospectItem.setMatcher(bound);
        establishItem.setMatcher(bound);
        brushItem.setMatcher(bound);
        sketchSupplies.setMatcher(bound);
        digTools.setMatcher(bound);
        recoveredFindItem.setMatcher(bound);
        sketch.setMatcher(bound);
        museum.setMatcher(bound);
    }

    /**
     * Stops tracker, prospecting, establishment, Hand Pick HUD, prism outlines, find-particles, and the sketch prototype, then flushes dirty dossiers.
     */
    @Override
    public void onDisable() {
        if (autoRuins != null) {
            autoRuins.stop();
        }
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
     * @return trial chunk auto-spawner
     */
    public RuinAutoSpawner autoRuins() {
        return autoRuins;
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
