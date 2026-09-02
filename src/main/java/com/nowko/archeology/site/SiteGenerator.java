package com.nowko.archeology.site;

import com.nowko.archeology.config.ArtifactTemplate;
import com.nowko.archeology.config.CatalogRegistry;
import com.nowko.archeology.config.HintTemplate;
import com.nowko.archeology.config.InterestSettings;
import com.nowko.archeology.config.StratumDefinition;
import com.nowko.archeology.model.BlockCell;
import com.nowko.archeology.model.BuriedFind;
import com.nowko.archeology.model.FindState;
import com.nowko.archeology.model.InterestLevel;
import com.nowko.archeology.model.Site;
import com.nowko.archeology.model.SiteStatus;
import com.nowko.archeology.model.SiteType;
import com.nowko.archeology.excavation.PrismFill;
import com.nowko.archeology.model.StratumBand;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds administered ruins as data-only dossiers (hidden find shapes, no block edits).
 */
public class SiteGenerator {
    private static final int[][] HORIZONTAL = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final int[][] VERTICAL = {{0, 1, 0}, {0, -1, 0}};

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
        InterestSettings settings = catalog.interest(interest);
        Random random = newRandom(chunk, interest);

        Site site = new Site();
        site.setId(UUID.randomUUID());
        site.setSerial(repository.nextSerial());
        site.setType(SiteType.MANAGED_RUIN);
        site.setStatus(SiteStatus.HIDDEN);
        site.setInterest(interest);
        site.setWorldName(chunk.getWorld().getName());
        site.setChunkX(chunk.getX());
        site.setChunkZ(chunk.getZ());
        site.setCreatedBy(createdBy);
        site.setCreatedAt(Instant.now());
        site.setDetectionRadius(settings.detectionRadius());
        site.setName(resolveName(name, site.getSerial(), chunk));
        site.setSurfaceY(GroundDatum.medianY(chunk));

        assignStrata(site, settings, random);
        List<BuriedFind> finds = placeFinds(site, settings, random);
        site.getFinds().addAll(finds);
        site.getHintIds().addAll(pickHints(site, settings, findTags(finds), random));

        repository.save(site);
        return site;
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
            throw new IllegalArgumentException("Stand on excavation fill (dirt, stone, sand…)");
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

        int minX = site.getChunkX() << 4;
        int minZ = site.getChunkZ() << 4;
        LinkedCells grown = grow(
                start,
                band,
                occupied,
                size,
                minX,
                minX + 15,
                minZ,
                minZ + 15,
                new Random(),
                origin.getWorld());
        if (grown.cells().isEmpty()) {
            throw new IllegalStateException("Could not grow a find from this block");
        }

        BuriedFind find = new BuriedFind();
        find.setId(UUID.randomUUID());
        find.setArtifactId(template.id());
        find.setStratumId(band.getId());
        find.setState(FindState.HIDDEN);
        find.getCells().addAll(grown.cells());
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
        site.setDetectionRadius(settings.detectionRadius());
        site.setName("Staff sandbox #" + site.getSerial());
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
     * @param site site with strata already assigned
     * @param settings find counts
     * @param random site RNG
     * @return finds to attach to the site
     */
    List<BuriedFind> placeFinds(Site site, InterestSettings settings, Random random) {
        int total = settings.minFinds() + random.nextInt(Math.max(1, settings.maxFinds() - settings.minFinds() + 1));
        int relics = settings.minRelics() + random.nextInt(Math.max(1, settings.maxRelics() - settings.minRelics() + 1));
        relics = Math.min(relics, total);

        Set<BlockCell> occupied = new HashSet<>();
        List<BuriedFind> finds = new ArrayList<>();
        List<String> present = presentStrata(site);

        for (int i = 0; i < relics; i++) {
            placeOne(site, finds, occupied, present, true, random);
        }
        while (finds.size() < total) {
            if (!placeOne(site, finds, occupied, present, false, random)) {
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
     * @param present stratum ids that exist on this site
     * @param relic whether to pick from relic templates
     * @param random site RNG
     * @return {@code false} if no template or shape would fit
     */
    private boolean placeOne(
            Site site,
            List<BuriedFind> finds,
            Set<BlockCell> occupied,
            List<String> present,
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
        List<BlockCell> shape = generateConnectedShape(site, band, occupied, size, random);
        if (shape.isEmpty()) {
            return false;
        }
        occupied.addAll(shape);

        BuriedFind find = new BuriedFind();
        find.setId(UUID.randomUUID());
        find.setArtifactId(template.id());
        find.setStratumId(stratumId);
        find.setState(FindState.HIDDEN);
        find.getCells().addAll(shape);
        finds.add(find);
        return true;
    }

    /**
     * Grows a face-connected blob inside the chunk and stratum Y band.
     *
     * @param site chunk bounds
     * @param band Y range for this stratum
     * @param occupied cells claimed by other finds
     * @param targetSize desired cell count
     * @param random site RNG
     * @return cells, possibly smaller than {@code targetSize} if space is tight; empty if it failed
     */
    List<BlockCell> generateConnectedShape(
            Site site,
            StratumBand band,
            Set<BlockCell> occupied,
            int targetSize,
            Random random
    ) {
        int minX = site.getChunkX() << 4;
        int minZ = site.getChunkZ() << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;

        for (int attempt = 0; attempt < catalog.maxShapeAttempts(); attempt++) {
            BlockCell start = randomFreeCell(band, occupied, minX, maxX, minZ, maxZ, random);
            if (start == null) {
                return List.of();
            }
            LinkedCells grown = grow(start, band, occupied, targetSize, minX, maxX, minZ, maxZ, random, null);
            if (grown.cells.size() >= Math.max(1, targetSize / 2)) {
                return grown.cells;
            }
        }
        return List.of();
    }

    /**
     * Expands from {@code start} by picking random orthogonal neighbours.
     *
     * @param start first cell of the shape
     * @param band Y range
     * @param occupied cells claimed by other finds
     * @param targetSize desired cell count
     * @param minX chunk min X
     * @param maxX chunk max X
     * @param minZ chunk min Z
     * @param maxZ chunk max Z
     * @param random site RNG
     * @param terrainWorld if set, only grow into {@link PrismFill} cells
     * @return grown cell list
     */
    private LinkedCells grow(
            BlockCell start,
            StratumBand band,
            Set<BlockCell> occupied,
            int targetSize,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            Random random,
            World terrainWorld
    ) {
        List<BlockCell> cells = new ArrayList<>();
        Set<BlockCell> used = new HashSet<>();
        cells.add(start);
        used.add(start);

        while (cells.size() < targetSize) {
            List<BlockCell> candidates = new ArrayList<>();
            for (BlockCell cell : cells) {
                for (int[] dir : HORIZONTAL) {
                    addCandidate(candidates, used, occupied, band, minX, maxX, minZ, maxZ,
                            cell.x() + dir[0], cell.y() + dir[1], cell.z() + dir[2], terrainWorld);
                }
                if (catalog.growVertically()) {
                    for (int[] dir : VERTICAL) {
                        addCandidate(candidates, used, occupied, band, minX, maxX, minZ, maxZ,
                                cell.x() + dir[0], cell.y() + dir[1], cell.z() + dir[2], terrainWorld);
                    }
                }
            }
            if (candidates.isEmpty()) {
                break;
            }
            BlockCell next = candidates.get(random.nextInt(candidates.size()));
            cells.add(next);
            used.add(next);
        }
        return new LinkedCells(cells);
    }

    /**
     * Adds {@code (x,y,z)} to {@code candidates} when it lies in the chunk, band, and is free.
     *
     * @param candidates neighbour pool being built
     * @param used cells already in this shape
     * @param occupied cells claimed by other finds
     * @param band Y range
     * @param minX chunk min X
     * @param maxX chunk max X
     * @param minZ chunk min Z
     * @param maxZ chunk max Z
     * @param x candidate X
     * @param y candidate Y
     * @param z candidate Z
     * @param terrainWorld if set, skip cells that are not excavation fill
     */
    private void addCandidate(
            List<BlockCell> candidates,
            Set<BlockCell> used,
            Set<BlockCell> occupied,
            StratumBand band,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int x,
            int y,
            int z,
            World terrainWorld
    ) {
        if (x < minX || x > maxX || z < minZ || z > maxZ) {
            return;
        }
        if (y < band.getMinY() || y > band.getMaxY()) {
            return;
        }
        BlockCell cell = new BlockCell(x, y, z);
        if (used.contains(cell) || occupied.contains(cell)) {
            return;
        }
        if (terrainWorld != null && !PrismFill.isTerrainFill(terrainWorld.getBlockAt(x, y, z).getType())) {
            return;
        }
        candidates.add(cell);
    }

    /**
     * @param band Y range
     * @param occupied cells already used
     * @param minX chunk min X
     * @param maxX chunk max X
     * @param minZ chunk min Z
     * @param maxZ chunk max Z
     * @param random site RNG
     * @return a random unused cell in the band, or {@code null} if none were found quickly
     */
    private BlockCell randomFreeCell(
            StratumBand band,
            Set<BlockCell> occupied,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            Random random
    ) {
        for (int i = 0; i < 64; i++) {
            int x = minX + random.nextInt(16);
            int z = minZ + random.nextInt(16);
            int span = Math.max(1, band.getMaxY() - band.getMinY() + 1);
            int y = band.getMinY() + random.nextInt(span);
            BlockCell cell = new BlockCell(x, y, z);
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
     * @param serial site serial number
     * @param chunk used to read biome for a fallback name
     * @return display name
     */
    private String resolveName(String requested, int serial, Chunk chunk) {
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
        return "Site in " + biomeName + " #" + serial;
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
