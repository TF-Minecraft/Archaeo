package net.tfminecraft.archaeo.site;

import net.tfminecraft.archaeo.config.ArtifactTemplate;
import net.tfminecraft.archaeo.config.CatalogRegistry;
import net.tfminecraft.archaeo.config.ConservationSettings;
import net.tfminecraft.archaeo.config.HintTemplate;
import net.tfminecraft.archaeo.config.InterestSettings;
import net.tfminecraft.archaeo.config.StratumDefinition;
import net.tfminecraft.archaeo.model.BlockCell;
import net.tfminecraft.archaeo.model.BuriedFind;
import net.tfminecraft.archaeo.model.FindState;
import net.tfminecraft.archaeo.model.InterestLevel;
import net.tfminecraft.archaeo.model.Site;
import net.tfminecraft.archaeo.model.SiteStatus;
import net.tfminecraft.archaeo.model.SiteType;
import net.tfminecraft.archaeo.excavation.PrismFill;
import net.tfminecraft.archaeo.excavation.PrismWound;
import net.tfminecraft.archaeo.model.StratumBand;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds administered ruins as data-only dossiers (hidden find shapes, no block edits).
 */
public class SiteGenerator {
    /** North, south, east, west on the same Y. A corner-adjacent cell is not a neighbour. */
    private static final int[][] HORIZONTAL = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};

    private final CatalogRegistry catalog;
    private final SiteRepository repository;

    /**
     * @param catalog templates and interest budgets
     * @param repository persistence for the new site
     */
    public SiteGenerator(CatalogRegistry catalog, SiteRepository repository) {
        this.catalog = catalog;
        this.repository = repository;
    }

    /**
     * Registers a hidden ruin in {@code chunk}. Terrain is not modified.
     *
     * @param chunk world chunk (16×16) that becomes the site bounds
     * @param interest wealth and detection budget
     * @param name optional display name; generated from biome if blank
     * @param createdBy staff player id, or {@code null}
     * @return persisted site
     * @throws IllegalArgumentException if chunk or interest is missing
     * @throws IllegalStateException if that chunk already has a site
     */
    public Site createManagedRuin(Chunk chunk, InterestLevel interest, String name, UUID createdBy) {
        if (chunk == null) {
            throw new IllegalArgumentException("Chunk is required");
        }
        if (interest == null) {
            throw new IllegalArgumentException("Interest level is required");
        }
        if (repository.findByChunk(chunk.getWorld().getName(), chunk.getX(), chunk.getZ()).isPresent()) {
            throw new IllegalStateException("This chunk already has a site");
        }

        chunk.load();

        Site site = new Site();
        site.setId(UUID.randomUUID());
        site.setSerial(repository.nextSerial());
        site.setType(SiteType.MANAGED_RUIN);
        site.setStatus(SiteStatus.HIDDEN);
        site.setWorldName(chunk.getWorld().getName());
        site.setChunkX(chunk.getX());
        site.setChunkZ(chunk.getZ());
        site.setCreatedBy(createdBy);
        site.setCreatedAt(Instant.now());
        site.setName(resolveName(name, chunk));
        fillLayout(site, interest, chunk);

        repository.save(site);
        return site;
    }

    /**
     * Rebuilds strata, finds, hints, and detection radius of a hidden ruin at a new interest.
     * Keeps id, serial, chunk, name, and author. Refuses established camps, confirmed prospecting,
     * and finds that the world has already wounded.
     *
     * @param site hidden ruin
     * @param interest new wealth and detection budget
     * @return the same site with a new generated layout
     * @throws IllegalArgumentException if site or interest is missing
     * @throws IllegalStateException if the rewrite is not allowed
     */
    public Site regenerateInterest(Site site, InterestLevel interest) {
        if (site == null) {
            throw new IllegalArgumentException("Site is required");
        }
        if (interest == null) {
            throw new IllegalArgumentException("Interest level is required");
        }
        if (site.getStatus() != SiteStatus.HIDDEN) {
            throw new IllegalStateException("Interest can only be changed on a hidden ruin.");
        }
        if (site.getInterest() == interest) {
            throw new IllegalStateException("This ruin is already " + interest.yamlKey() + ".");
        }
        if (!site.getProspectConfirmed().isEmpty()) {
            throw new IllegalStateException(
                    "Players have already confirmed this ruin by prospecting. Delete and create a new ruin instead.");
        }
        if (site.hasRecordedFindWounds()) {
            throw new IllegalStateException(
                    "Buried finds were already damaged. Delete and create a new ruin instead.");
        }
        World world = Bukkit.getWorld(site.getWorldName());
        if (world == null) {
            throw new IllegalStateException("World \"" + site.getWorldName() + "\" is not loaded.");
        }
        Chunk chunk = world.getChunkAt(site.getChunkX(), site.getChunkZ());
        chunk.load();
        if (PrismWound.hasMissingFindTerrain(world, site)) {
            throw new IllegalStateException(
                    "The ground over this ruin is already broken. Delete and create a new ruin instead.");
        }
        site.clearGeneratedLayout();
        fillLayout(site, interest, chunk);
        repository.save(site);
        return site;
    }

    /**
     * Writes interest, datum, strata, finds, and hints onto {@code site}. Identity fields stay.
     *
     * @param site dossier to fill
     * @param interest wealth budget
     * @param chunk ruin chunk, used for seed and buried pockets
     */
    private void fillLayout(Site site, InterestLevel interest, Chunk chunk) {
        InterestSettings settings = catalog.interest(interest);
        if (settings == null) {
            throw new IllegalStateException("No interest settings loaded for " + interest.yamlKey() + ".");
        }
        Random random = newRandom(chunk, interest);
        site.setInterest(interest);
        site.setSurfaceY(GroundDatum.medianY(chunk));
        assignStrata(site, settings, random);
        List<BuriedFind> finds = placeFinds(site, settings, random, chunk.getWorld());
        site.getFinds().addAll(finds);
        site.getHintIds().addAll(pickHints(site, settings, findTags(finds), random));
    }

    /**
     * Staff sandbox: one find grown from {@code origin}, chunk forced to an established excavation.
     * Terrain is not replaced; skipped prospecting and camp planting.
     *
     * @param origin fill block the shape starts on
     * @param director player who will excavate
     * @param template artifact from {@code artifacts.yml}
     * @param requestedSize desired cell count before clamp
     * @return the new find
     * @throws IllegalArgumentException if origin is not fill or the template is missing
     * @throws IllegalStateException if that cell already belongs to a find
     */
    public BuriedFind spawnStaffFind(Block origin, UUID director, ArtifactTemplate template, int requestedSize) {
        if (origin == null) {
            throw new IllegalArgumentException("Origin block is required");
        }
        if (director == null) {
            throw new IllegalArgumentException("Director is required");
        }
        if (template == null) {
            throw new IllegalArgumentException("Unknown artifact template");
        }
        if (!PrismFill.isTerrainFill(origin.getType())) {
            throw new IllegalArgumentException("Stand on a solid excavation block");
        }

        Chunk chunk = origin.getChunk();
        chunk.load();
        BlockCell start = new BlockCell(origin.getX(), origin.getY(), origin.getZ());
        Site site = repository.findByChunk(origin.getWorld().getName(), chunk.getX(), chunk.getZ())
                .orElseGet(() -> newStaffSite(chunk, director, origin.getY()));

        for (BuriedFind existing : site.getFinds()) {
            if (existing.getCells().contains(start)) {
                throw new IllegalStateException("A find already occupies this block");
            }
        }

        int size = template.clampSize(requestedSize);
        int pad = Math.max(4, size);
        StratumBand band = ensureBandCovers(site, origin.getY(), pad);
        Set<BlockCell> occupied = new HashSet<>();
        for (BuriedFind existing : site.getFinds()) {
            occupied.addAll(existing.getCells());
        }

        // Sandbox tool: the staff member is standing on the block they aimed at, so the cover rule
        // is not applied here. Only "is this excavation fill" is.
        Set<BlockCell> allowed = new HashSet<>(buriedPocket(origin.getWorld(), site, band, 0));
        allowed.add(start);
        LinkedCells grown = grow(start, allowed, occupied, size, new Random());
        if (grown.cells().isEmpty()) {
            throw new IllegalStateException("Could not grow a find from this block");
        }

        BuriedFind find = new BuriedFind();
        find.setId(UUID.randomUUID());
        find.setArtifactId(template.id());
        find.setItem(template.pickItem(new Random()));
        find.setStratumId(band.getId());
        find.setState(FindState.HIDDEN);
        find.getCells().addAll(grown.cells());
        find.setBuriedConservation(rollBuriedConservation(template, band.getId(), band, new Random()));
        site.getFinds().add(find);

        site.establish(
                director,
                chunk.getX(),
                chunk.getZ(),
                origin.getX(),
                origin.getY(),
                origin.getZ());
        repository.save(site);
        return find;
    }

    /**
     * Empty established sandbox in {@code chunk} with no random finds.
     *
     * @param chunk ruin chunk
     * @param director staff player
     * @param originY used as surface datum so the prism can cover the test block
     * @return persisted site
     */
    private Site newStaffSite(Chunk chunk, UUID director, int originY) {
        InterestSettings settings = catalog.interest(InterestLevel.LOW);
        Site site = new Site();
        site.setId(UUID.randomUUID());
        site.setSerial(repository.nextSerial());
        site.setType(SiteType.MANAGED_RUIN);
        site.setStatus(SiteStatus.HIDDEN);
        site.setInterest(InterestLevel.LOW);
        site.setWorldName(chunk.getWorld().getName());
        site.setChunkX(chunk.getX());
        site.setChunkZ(chunk.getZ());
        site.setCreatedBy(director);
        site.setCreatedAt(Instant.now());
        site.setName("Staff sandbox");
        site.setSurfaceY(originY);
        assignStrata(site, settings, new Random());
        repository.save(site);
        return site;
    }

    /**
     * Widens a present stratum so {@code y} plus padding sits inside the prism.
     *
     * @param site excavation
     * @param y origin block Y
     * @param pad extra blocks above and below
     * @return band that now contains {@code y}
     */
    private StratumBand ensureBandCovers(Site site, int y, int pad) {
        int min = y - pad;
        int max = y + pad;
        StratumBand covering = site.stratumAt(y);
        if (covering != null) {
            covering.setMinY(Math.min(covering.getMinY(), min));
            covering.setMaxY(Math.max(covering.getMaxY(), max));
            return covering;
        }
        StratumBand closest = null;
        int best = Integer.MAX_VALUE;
        for (StratumBand candidate : site.getStrata().values()) {
            if (!candidate.isPresent()) {
                continue;
            }
            int dist = y > candidate.getMaxY() ? y - candidate.getMaxY() : candidate.getMinY() - y;
            if (dist < best) {
                best = dist;
                closest = candidate;
            }
        }
        if (closest == null) {
            closest = site.getStrata().get("I");
            if (closest == null) {
                closest = new StratumBand();
                closest.setId("I");
                site.getStrata().put("I", closest);
            }
            closest.setPresent(true);
            closest.setMinY(min);
            closest.setMaxY(max);
            return closest;
        }
        closest.setMinY(Math.min(closest.getMinY(), min));
        closest.setMaxY(Math.max(closest.getMaxY(), max));
        return closest;
    }


    /**
     * Fills present/absent Y bands from {@code strata.yml} and interest chances.
     *
     * @param site site under construction
     * @param settings interest budget
     * @param random site RNG
     */
    void assignStrata(Site site, InterestSettings settings, Random random) {
        boolean includeIv = random.nextDouble() < settings.stratumIvChance();
        for (StratumDefinition definition : catalog.strataInOrder()) {
            boolean present = definition.alwaysPresent() || ("IV".equals(definition.id()) && includeIv);
            StratumBand band = new StratumBand();
            band.setId(definition.id());
            band.setPresent(present);
            band.setDisturbed(present && random.nextDouble() < settings.disturbedChance());
            if (present) {
                band.setMaxY(site.getSurfaceY() - definition.depthMin());
                band.setMinY(site.getSurfaceY() - definition.depthMax());
            }
            site.getStrata().put(definition.id(), band);
        }
    }

    /**
     * Places relic finds first, then fills remaining slots with weighted templates.
     *
     * <p>Shapes are drawn from a per-stratum pocket of cells that the terrain actually buries,
     * so a band is skipped when the ground gives it nowhere to hide a find.
     *
     * @param site site with strata already assigned
     * @param settings find counts
     * @param random site RNG
     * @param world ruin world, read to keep finds under ground
     * @return finds to attach to the site
     */
    List<BuriedFind> placeFinds(Site site, InterestSettings settings, Random random, World world) {
        int total = settings.minFinds() + random.nextInt(Math.max(1, settings.maxFinds() - settings.minFinds() + 1));
        int relics = settings.minRelics() + random.nextInt(Math.max(1, settings.maxRelics() - settings.minRelics() + 1));
        relics = Math.min(relics, total);

        Set<BlockCell> occupied = new HashSet<>();
        List<BuriedFind> finds = new ArrayList<>();
        Map<String, List<BlockCell>> pockets = new LinkedHashMap<>();
        List<String> present = new ArrayList<>();
        for (String stratumId : presentStrata(site)) {
            List<BlockCell> pocket = buriedPocket(world, site, site.getStrata().get(stratumId), catalog.findMinCover());
            if (pocket.isEmpty()) {
                continue;
            }
            pockets.put(stratumId, pocket);
            present.add(stratumId);
        }
        if (present.isEmpty()) {
            return finds;
        }

        for (int i = 0; i < relics; i++) {
            placeOne(site, finds, occupied, present, pockets, true, random);
        }
        while (finds.size() < total) {
            if (!placeOne(site, finds, occupied, present, pockets, false, random)) {
                break;
            }
        }
        return finds;
    }

    /**
     * Tries to add one find of the requested relic/non-relic pool.
     *
     * @param site site bounds and strata
     * @param finds list to append
     * @param occupied cells already used by other finds
     * @param present stratum ids that exist on this site and can bury a find
     * @param pockets buried cells per stratum id
     * @param relic whether to pick from relic templates
     * @param random site RNG
     * @return {@code false} if no template or shape would fit
     */
    private boolean placeOne(
            Site site,
            List<BuriedFind> finds,
            Set<BlockCell> occupied,
            List<String> present,
            Map<String, List<BlockCell>> pockets,
            boolean relic,
            Random random
    ) {
        ArtifactTemplate template = pickArtifact(present, relic, random);
        if (template == null) {
            return false;
        }
        List<String> compatible = template.strata().stream().filter(present::contains).toList();
        if (compatible.isEmpty()) {
            return false;
        }
        String stratumId = compatible.get(random.nextInt(compatible.size()));
        StratumBand band = site.getStrata().get(stratumId);
        int size = template.sizeMin() + random.nextInt(template.sizeMax() - template.sizeMin() + 1);
        List<BlockCell> shape = generateConnectedShape(pockets.get(stratumId), occupied, size, random);
        if (shape.isEmpty()) {
            return false;
        }
        occupied.addAll(shape);

        BuriedFind find = new BuriedFind();
        find.setId(UUID.randomUUID());
        find.setArtifactId(template.id());
        find.setItem(template.pickItem(random));
        find.setStratumId(stratumId);
        find.setState(FindState.HIDDEN);
        find.getCells().addAll(shape);
        find.setBuriedConservation(rollBuriedConservation(template, stratumId, band, random));
        finds.add(find);
        return true;
    }

    /**
     * How much of the piece the ground left before anyone dug. The roll is biased toward the
     * low end, then cut by depth, a disturbed band, and how badly the material rots, so an
     * untouched find is rare and a careful dig cannot invent one.
     *
     * @param template artifact row (its material decides how well it survives)
     * @param stratumId band that holds the find
     * @param band that band's disturbed flag
     * @param random site RNG
     * @return buried condition, 1–100
     */
    private int rollBuriedConservation(
            ArtifactTemplate template,
            String stratumId,
            StratumBand band,
            Random random
    ) {
        ConservationSettings settings = catalog.pick().conservation();
        int min = settings.buriedMin();
        int max = Math.max(min, settings.buriedMax());
        // Two samples averaged: middling survival is common, both a pristine piece and a
        // ruined one are rare. bias then leans the whole curve toward the low end.
        double roll = (random.nextDouble() + random.nextDouble()) / 2.0;
        double bias = Math.max(0.1, settings.bias());
        if (bias != 1.0) {
            roll = Math.pow(roll, bias);
        }
        double value = min + roll * (max - min);
        StratumDefinition definition = catalog.stratum(stratumId);
        int depthSteps = definition == null ? 0 : Math.max(0, definition.order() - 1);
        value -= (double) depthSteps * settings.depthPenalty();
        if (band != null && band.isDisturbed()) {
            value -= settings.disturbedPenalty();
        }
        value *= catalog.materialSurvival(template.material());
        return (int) Math.round(Math.max(1.0, Math.min(100.0, value)));
    }

    /**
     * Cells of one stratum band that the ground really buries: excavation fill with at least
     * {@code cover} fill blocks straight above.
     *
     * <p>The band itself is not enough of a test. Its Y range comes from the chunk's <em>median</em>
     * ground level, so on a slope, a shore, or a valley the same band runs through open air on one
     * side of the chunk and deep rock on the other. Without this filter a find could be generated
     * in the air or as the top block of the column, which is how a piece ends up lying in plain
     * sight, recoverable without digging at all.
     *
     * @param world ruin world; the chunk is already loaded by the caller
     * @param band stratum band, or {@code null}
     * @param site chunk bounds
     * @param cover fill blocks that must sit on top of a cell before it can hold a find
     * @return usable cells, empty when the terrain leaves no room in this band
     */
    private List<BlockCell> buriedPocket(World world, Site site, StratumBand band, int cover) {
        if (band == null || !band.isPresent()) {
            return List.of();
        }
        int minX = site.getChunkX() << 4;
        int minZ = site.getChunkZ() << 4;
        List<BlockCell> pocket = new ArrayList<>();
        for (int x = minX; x <= minX + 15; x++) {
            for (int z = minZ; z <= minZ + 15; z++) {
                for (int y = band.getMinY(); y <= band.getMaxY(); y++) {
                    if (isBuried(world, x, y, z, cover)) {
                        pocket.add(new BlockCell(x, y, z));
                    }
                }
            }
        }
        return pocket;
    }

    /**
     * @param world ruin world
     * @param x block X
     * @param y block Y
     * @param z block Z
     * @param cover fill blocks required above the cell
     * @return whether the cell is fill under enough fill
     */
    private static boolean isBuried(World world, int x, int y, int z, int cover) {
        if (!PrismFill.isTerrainFill(world.getBlockAt(x, y, z).getType())) {
            return false;
        }
        for (int step = 1; step <= cover; step++) {
            if (!PrismFill.isTerrainFill(world.getBlockAt(x, y + step, z).getType())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Grows a compact face-connected blob (same Y, no diagonal corners) inside a stratum's buried pocket.
     *
     * @param pocket cells this stratum may use, or {@code null}
     * @param occupied cells claimed by other finds
     * @param targetSize desired cell count
     * @param random site RNG
     * @return cells, possibly smaller than {@code targetSize} if space is tight; empty if it failed
     */
    private List<BlockCell> generateConnectedShape(
            List<BlockCell> pocket,
            Set<BlockCell> occupied,
            int targetSize,
            Random random
    ) {
        if (pocket == null || pocket.isEmpty()) {
            return List.of();
        }
        Set<BlockCell> allowed = new HashSet<>(pocket);
        for (int attempt = 0; attempt < catalog.maxShapeAttempts(); attempt++) {
            BlockCell start = randomFreeCell(pocket, occupied, random);
            if (start == null) {
                return List.of();
            }
            LinkedCells grown = grow(start, allowed, occupied, targetSize, random);
            if (grown.cells.size() >= Math.max(1, targetSize / 2)) {
                return grown.cells;
            }
        }
        return List.of();
    }

    /**
     * Expands from {@code start} on the same Y through face-adjacent cells only (north/south/east/west).
     * Corner neighbours never join. Among those faces, cells that already touch more of the shape
     * are preferred so the blob stays compact instead of walking a diagonal stair.
     *
     * @param start first cell of the shape (locks the height)
     * @param allowed cells the shape may occupy
     * @param occupied cells claimed by other finds
     * @param targetSize desired cell count
     * @param random site RNG
     * @return grown cell list
     */
    private LinkedCells grow(
            BlockCell start,
            Set<BlockCell> allowed,
            Set<BlockCell> occupied,
            int targetSize,
            Random random
    ) {
        List<BlockCell> cells = new ArrayList<>();
        Set<BlockCell> used = new HashSet<>();
        cells.add(start);
        used.add(start);

        while (cells.size() < targetSize) {
            Map<BlockCell, Integer> adjacency = new HashMap<>();
            for (BlockCell cell : cells) {
                for (int[] dir : HORIZONTAL) {
                    BlockCell candidate = new BlockCell(cell.x() + dir[0], cell.y(), cell.z() + dir[2]);
                    if (allowed.contains(candidate)
                            && !used.contains(candidate)
                            && !occupied.contains(candidate)) {
                        adjacency.merge(candidate, 1, Integer::sum);
                    }
                }
            }
            if (adjacency.isEmpty()) {
                break;
            }
            int best = 0;
            for (int count : adjacency.values()) {
                if (count > best) {
                    best = count;
                }
            }
            List<BlockCell> sticky = new ArrayList<>();
            for (Map.Entry<BlockCell, Integer> entry : adjacency.entrySet()) {
                if (entry.getValue() == best) {
                    sticky.add(entry.getKey());
                }
            }
            BlockCell next = sticky.get(random.nextInt(sticky.size()));
            cells.add(next);
            used.add(next);
        }
        return new LinkedCells(cells);
    }

    /**
     * @param pocket buried cells of one stratum
     * @param occupied cells already used
     * @param random site RNG
     * @return a random unclaimed cell of the pocket, or {@code null} if none were found quickly
     */
    private BlockCell randomFreeCell(List<BlockCell> pocket, Set<BlockCell> occupied, Random random) {
        for (int i = 0; i < 64; i++) {
            BlockCell cell = pocket.get(random.nextInt(pocket.size()));
            if (!occupied.contains(cell)) {
                return cell;
            }
        }
        return null;
    }

    /**
     * Picks hint ids whose filters match this site's wealth, strata, and find tags.
     *
     * @param site dossier with strata
     * @param settings wealth and hint count
     * @param findTags tags from placed artifacts
     * @param random site RNG
     * @return ordered hint ids stored on the site
     */
    List<String> pickHints(Site site, InterestSettings settings, Set<String> findTags, Random random) {
        List<String> present = presentStrata(site);
        boolean disturbed = site.getStrata().values().stream().anyMatch(StratumBand::isDisturbed);
        List<HintTemplate> pool = catalog.hints().stream()
                .filter(hint -> matchesHint(hint, settings, present, findTags, disturbed))
                .collect(Collectors.toCollection(ArrayList::new));
        List<String> selected = new ArrayList<>();
        int want = Math.min(settings.hintCount(), pool.size());
        for (int i = 0; i < want; i++) {
            HintTemplate pick = weightedHint(pool, random);
            if (pick == null) {
                break;
            }
            selected.add(pick.id());
            pool.remove(pick);
        }
        return selected;
    }

    /**
     * @param hint catalog hint
     * @param settings site wealth
     * @param present present stratum ids
     * @param findTags tags from placed artifacts
     * @param disturbed whether any band is disturbed
     * @return whether {@code hint} is allowed for this generated dossier
     */
    private boolean matchesHint(
            HintTemplate hint,
            InterestSettings settings,
            List<String> present,
            Set<String> findTags,
            boolean disturbed
    ) {
        if (hint.minWealth() != null && settings.baseWealth() < hint.minWealth()) {
            return false;
        }
        if (hint.maxWealth() != null && settings.baseWealth() > hint.maxWealth()) {
            return false;
        }
        if (hint.minStrata() != null && present.size() < hint.minStrata()) {
            return false;
        }
        if (Boolean.TRUE.equals(hint.requireDisturbed()) && !disturbed) {
            return false;
        }
        if (hint.requireMissingStratum() != null && present.contains(hint.requireMissingStratum())) {
            return false;
        }
        if (!hint.requireStrataAll().isEmpty() && !present.containsAll(hint.requireStrataAll())) {
            return false;
        }
        if (!hint.requireTagsAll().isEmpty() && !findTags.containsAll(hint.requireTagsAll())) {
            return false;
        }
        if (!hint.requireTagsAny().isEmpty() && hint.requireTagsAny().stream().noneMatch(findTags::contains)) {
            return false;
        }
        return true;
    }

    /**
     * Weighted pick among templates that can spawn in {@code present} strata.
     *
     * @param present stratum ids on this site
     * @param relic if {@code true}, only relic templates
     * @param random site RNG
     * @return template or {@code null} if the pool is empty
     */
    private ArtifactTemplate pickArtifact(List<String> present, boolean relic, Random random) {
        List<ArtifactTemplate> pool = catalog.artifacts().values().stream()
                .filter(template -> !relic || template.relic())
                .filter(template -> template.strata().stream().anyMatch(present::contains))
                .toList();
        if (pool.isEmpty()) {
            return null;
        }
        int total = pool.stream().mapToInt(ArtifactTemplate::weight).sum();
        int roll = random.nextInt(Math.max(1, total));
        int cursor = 0;
        for (ArtifactTemplate template : pool) {
            cursor += template.weight();
            if (roll < cursor) {
                return template;
            }
        }
        return pool.getLast();
    }

    /**
     * @param pool remaining hint templates
     * @param random site RNG
     * @return one template by weight, or {@code null} if {@code pool} is empty
     */
    private HintTemplate weightedHint(List<HintTemplate> pool, Random random) {
        if (pool.isEmpty()) {
            return null;
        }
        int total = pool.stream().mapToInt(HintTemplate::weight).sum();
        int roll = random.nextInt(Math.max(1, total));
        int cursor = 0;
        for (HintTemplate hint : pool) {
            cursor += hint.weight();
            if (roll < cursor) {
                return hint;
            }
        }
        return pool.getLast();
    }

    /**
     * @param finds placed finds
     * @return union of artifact tags for the finds already placed
     */
    private Set<String> findTags(List<BuriedFind> finds) {
        return finds.stream()
                .map(BuriedFind::getArtifactId)
                .map(catalog::artifact)
                .filter(template -> template != null)
                .flatMap(template -> template.tags().stream())
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * @param site dossier with strata
     * @return ids of strata marked present on {@code site}
     */
    private List<String> presentStrata(Site site) {
        return site.getStrata().values().stream()
                .filter(StratumBand::isPresent)
                .map(StratumBand::getId)
                .toList();
    }

    /**
     * @param requested player-supplied name
     * @param chunk used to read biome for a fallback name
     * @return player-facing name; staff commands still prefix the serial via {@code displayLabel}
     */
    private String resolveName(String requested, Chunk chunk) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        String biomeName = "unknown";
        try {
            World world = chunk.getWorld();
            Biome biome = world.getBiome(
                    (chunk.getX() << 4) + 8,
                    world.getSeaLevel(),
                    (chunk.getZ() << 4) + 8
            );
            NamespacedKey key = biomeKey(biome);
            if (key != null) {
                biomeName = key.getKey().replace('_', ' ');
            }
        } catch (Exception ignored) {
            // keep fallback
        }
        return "Site in " + biomeName;
    }

    /**
     * Reads a biome id on Paper 1.21.10 ({@code Keyed.getKey}) and on later APIs
     * ({@code getKeyOrNull}). The Maven {@code 1.21.10-R0.1-SNAPSHOT} javadoc already
     * deprecates {@code getKey} and documents {@code getKeyOrNull}; that method is not
     * on Paper 1.21.10 builds, so a direct call crashes at runtime.
     *
     * @param biome chunk biome
     * @return namespaced key, or {@code null} if the biome is unregistered
     */
    @SuppressWarnings("deprecation")
    private static NamespacedKey biomeKey(Biome biome) {
        try {
            Object value = biome.getClass().getMethod("getKeyOrNull").invoke(biome);
            if (value instanceof NamespacedKey key) {
                return key;
            }
        } catch (ReflectiveOperationException ignored) {
            // Paper 1.21.10: RegistryAware helpers are absent
        }
        return biome.getKey();
    }

    /**
     * @param chunk world chunk
     * @param interest included in the seed
     * @return RNG seeded from world+chunk+interest, or an unseeded RNG
     */
    private Random newRandom(Chunk chunk, InterestLevel interest) {
        if (!catalog.useWorldSeed()) {
            return new Random();
        }
        long seed = chunk.getWorld().getSeed()
                ^ ((long) chunk.getX() << 32)
                ^ chunk.getZ()
                ^ interest.name().hashCode();
        return new Random(seed);
    }

    /**
     * Mutable cell list used while growing a shape.
     *
     * @param cells cells claimed so far
     */
    private record LinkedCells(List<BlockCell> cells) {
    }
}
