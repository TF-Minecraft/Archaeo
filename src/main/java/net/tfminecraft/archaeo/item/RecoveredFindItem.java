package net.tfminecraft.archaeo.item;

import net.tfminecraft.archaeo.config.ArtifactTemplate;
import net.tfminecraft.archaeo.config.CatalogRegistry;
import net.tfminecraft.archaeo.config.InterpretationTemplate;
import net.tfminecraft.archaeo.config.InterpretationType;
import net.tfminecraft.archaeo.establish.CampNames;
import net.tfminecraft.archaeo.model.BuriedFind;
import net.tfminecraft.archaeo.model.FindInterpretation;
import net.tfminecraft.archaeo.model.FindState;
import net.tfminecraft.archaeo.model.Site;
import net.tfminecraft.archaeo.site.SiteRepository;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Builds and updates the field piece: a tag pointing at the excavation archive, plus lore for what
 * has already been revealed.
 */
public class RecoveredFindItem {
    private static final int LINE_WIDTH = 34;

    private final NamespacedKey markerKey;
    private final NamespacedKey siteIdKey;
    private final NamespacedKey siteNameKey;
    private final NamespacedKey findIdKey;
    private final NamespacedKey findNumberKey;
    private final NamespacedKey artifactIdKey;
    private final NamespacedKey stratumKey;
    private final NamespacedKey conservationKey;
    private final NamespacedKey buriedConservationKey;
    private final NamespacedKey gradeKey;
    private final NamespacedKey fieldDamagedKey;
    private final NamespacedKey priorDamageKey;
    private final NamespacedKey recoveredByKey;
    private final NamespacedKey recoveredAtKey;
    private final NamespacedKey studiedKey;
    private final NamespacedKey labCleanedKey;
    private final NamespacedKey fieldSketchKey;
    private ItemMatcher matcher = ItemMatcher.vanillaOnly();

    /**
     * @param plugin owner of the PDC keys
     */
    public RecoveredFindItem(JavaPlugin plugin) {
        this.markerKey = new NamespacedKey(plugin, "recovered_find");
        this.siteIdKey = new NamespacedKey(plugin, "site_id");
        this.siteNameKey = new NamespacedKey(plugin, "site_name");
        this.findIdKey = new NamespacedKey(plugin, "find_id");
        this.findNumberKey = new NamespacedKey(plugin, "find_number");
        this.artifactIdKey = new NamespacedKey(plugin, "artifact_id");
        this.stratumKey = new NamespacedKey(plugin, "stratum");
        this.conservationKey = new NamespacedKey(plugin, "conservation");
        this.buriedConservationKey = new NamespacedKey(plugin, "buried_conservation");
        this.gradeKey = new NamespacedKey(plugin, "conservation_grade");
        this.fieldDamagedKey = new NamespacedKey(plugin, "field_damaged");
        this.priorDamageKey = new NamespacedKey(plugin, "disturbed_before_dig");
        this.recoveredByKey = new NamespacedKey(plugin, "recovered_by");
        this.recoveredAtKey = new NamespacedKey(plugin, "recovered_at");
        this.studiedKey = new NamespacedKey(plugin, "studied");
        this.labCleanedKey = new NamespacedKey(plugin, "lab_cleaned");
        this.fieldSketchKey = new NamespacedKey(plugin, "field_sketch");
    }

    /**
     * @param matcher ItemsAdder / MMOItems lookup used when a find's {@code item} is a pack id
     */
    public void setMatcher(ItemMatcher matcher) {
        this.matcher = matcher == null ? ItemMatcher.vanillaOnly() : matcher;
    }

    /**
     * Builds the recovered field piece from the catalog item (vanilla, ItemsAdder, or MMOItems).
     *
     * @param template catalog row (material and display name)
     * @param site excavation this piece left
     * @param find dossier row being lifted
     * @param recoverer player who finished the last brush
     * @param grade condition band label for the final percentage
     * @param fieldDamaged whether the dig itself wounded the piece
     * @param catalogs rarity, notes, and interpretation labels
     * @return stack to drop; brick when the catalog item is missing
     */
    public ItemStack create(
            ArtifactTemplate template,
            Site site,
            BuriedFind find,
            UUID recoverer,
            String grade,
            boolean fieldDamaged,
            CatalogRegistry catalogs
    ) {
        ItemStack stack = baseStack(template, find);
        write(stack, template, site, find, recoverer, grade, fieldDamaged, catalogs);
        return stack;
    }

    /**
     * Pack or vanilla appearance for this find. Missing pack plugins or unknown ids become brick
     * so a piece always exists. Side-effect: old dossiers without a stored item get a stable pick.
     *
     * @param template catalog row, or {@code null}
     * @param find archive row, or {@code null}
     * @return cloneable stack, never air
     */
    public ItemStack baseStack(ArtifactTemplate template, BuriedFind find) {
        ItemRef ref = resolveRef(template, find);
        ItemStack stack = matcher.create(ref);
        if (stack == null || stack.getType().isAir()) {
            return new ItemStack(Material.BRICK, 1);
        }
        stack.setAmount(1);
        return stack;
    }

    /**
     * Catalog item stored on this find, or a stable pick from the template pool for old dossiers.
     *
     * @param template catalog row
     * @param find archive row
     * @return vanilla / ItemsAdder / MMOItems ref
     */
    private static ItemRef resolveRef(ArtifactTemplate template, BuriedFind find) {
        if (template == null) {
            return ItemRef.vanilla(Material.BRICK);
        }
        // BuriedFind stores a blank item as null, and every stored or generated find has an id.
        String chosen = find == null ? null : find.getItem();
        if (chosen == null && find != null) {
            long seed = find.getId().getMostSignificantBits() ^ find.getId().getLeastSignificantBits();
            chosen = template.pickItem(new Random(seed));
            find.setItem(chosen);
        }
        return template.resolveRef(chosen);
    }

    /**
     * Rewrites lore and PDC on every matching stack the player is carrying, so study and
     * interpretation show up without returning to the cut.
     *
     * @param player carrier
     * @param site excavation
     * @param find archive row
     * @param template catalog row
     * @param grade condition band label
     * @param catalogs rarity, notes, and interpretation labels
     */
    public void refreshCarried(
            Player player,
            Site site,
            BuriedFind find,
            ArtifactTemplate template,
            String grade,
            CatalogRegistry catalogs
    ) {
        UUID recoverer = find.getRecoveredBy() == null ? player.getUniqueId() : find.getRecoveredBy();
        boolean fieldDamaged = find.isFieldDamaged();
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (!isThisFind(stack, find.getId())) {
                continue;
            }
            write(stack, template, site, find, recoverer, grade, fieldDamaged, catalogs);
            inventory.setItem(i, stack);
        }
        ItemStack cursor = player.getOpenInventory().getCursor();
        if (isThisFind(cursor, find.getId())) {
            write(cursor, template, site, find, recoverer, grade, fieldDamaged, catalogs);
            player.getOpenInventory().setCursor(cursor);
        }
    }

    /**
     * Rewrites lore and PDC on one stack already in a cabinet slot or similar, where
     * {@link #refreshCarried} would miss it.
     *
     * @param stack recovered piece, or {@code null}
     * @param site excavation
     * @param find archive row
     * @param template catalog row
     * @param grade condition band label
     * @param catalogs rarity, notes, and interpretation labels
     */
    public void refresh(
            ItemStack stack,
            Site site,
            BuriedFind find,
            ArtifactTemplate template,
            String grade,
            CatalogRegistry catalogs
    ) {
        if (find == null || !isThisFind(stack, find.getId())) {
            return;
        }
        UUID recoverer = find.getRecoveredBy();
        write(stack, template, site, find, recoverer, grade, find.isFieldDamaged(), catalogs);
    }

    /**
     * Rewrites {@code #name-n} from the live excavation when this stack belongs to {@code site}.
     *
     * @param stack recovered piece, or {@code null}
     * @param site excavation
     * @param catalogs grades and catalog titles
     * @return whether lore was rewritten
     */
    public boolean retitle(ItemStack stack, Site site, CatalogRegistry catalogs) {
        UUID findId = findIdOf(stack);
        if (findId == null || site == null || !site.getId().equals(siteIdOf(stack))) {
            return false;
        }
        BuriedFind find = site.findById(findId).orElse(null);
        if (find == null) {
            return false;
        }
        ArtifactTemplate template = catalogs == null ? null : catalogs.artifact(find.getArtifactId());
        String grade = catalogs == null ? null : catalogs.pick().conservation().gradeLabel(find.getConservation());
        refresh(stack, site, find, template, grade, catalogs);
        return true;
    }

    /**
     * Same as {@link #retitle} when the stamped site name no longer matches the dossier.
     *
     * @param stack recovered piece, or {@code null}
     * @param sites live dossiers
     * @param catalogs grades and catalog titles
     * @return whether lore was rewritten
     */
    public boolean retitleIfStale(ItemStack stack, SiteRepository sites, CatalogRegistry catalogs) {
        UUID siteId = siteIdOf(stack);
        if (siteId == null || sites == null) {
            return false;
        }
        Site site = sites.findById(siteId).orElse(null);
        if (site == null) {
            return false;
        }
        String stored = siteNameOf(stack);
        if (stored != null && stored.equals(site.publicName())) {
            return false;
        }
        return retitle(stack, site, catalogs);
    }

    /**
     * @param stack recovered piece
     * @return excavation name stamped when lore was last written, or {@code null}
     */
    public String siteNameOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(siteNameKey, PersistentDataType.STRING);
    }

    /**
     * @param player carrier
     * @param findId archive row
     * @return whether the recovered piece is in the main hand
     */
    public boolean isInMainHand(Player player, UUID findId) {
        return player != null && isThisFind(player.getInventory().getItemInMainHand(), findId);
    }

    /**
     * @param player carrier
     * @param findId archive row
     * @return whether a matching recovered piece is in their inventory or cursor
     */
    public boolean isCarrying(Player player, UUID findId) {
        if (player == null || findId == null) {
            return false;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isThisFind(stack, findId)) {
                return true;
            }
        }
        return isThisFind(player.getOpenInventory().getCursor(), findId);
    }

    /**
     * @param stack candidate
     * @param findId archive row
     * @return whether this stack is that recovered piece
     */
    public boolean isThisFind(ItemStack stack, UUID findId) {
        if (stack == null || stack.getType().isAir() || findId == null) {
            return false;
        }
        var pdc = stack.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(markerKey, PersistentDataType.BYTE)) {
            return false;
        }
        String raw = pdc.get(findIdKey, PersistentDataType.STRING);
        return findId.toString().equals(raw);
    }

    /**
     * @param stack candidate
     * @return find id, or {@code null} if this is not a recovered Archaeo piece
     */
    public UUID findIdOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        var pdc = stack.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(markerKey, PersistentDataType.BYTE)) {
            return null;
        }
        String raw = pdc.get(findIdKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * @param stack candidate
     * @return whether this stack is a recovered Archaeo piece
     */
    public boolean isRecovered(ItemStack stack) {
        return findIdOf(stack) != null;
    }

    /**
     * @param stack recovered piece
     * @return site id, or {@code null}
     */
    public UUID siteIdOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        var pdc = stack.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(markerKey, PersistentDataType.BYTE)) {
            return null;
        }
        String raw = pdc.get(siteIdKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * @param stack recovered piece
     * @return catalog name on the tag, or {@code "recovered find"}
     */
    public String labelOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return "recovered find";
        }
        ItemMeta meta = stack.getItemMeta();
        if (!meta.hasDisplayName()) {
            return "recovered find";
        }
        String name = ChatColor.stripColor(meta.getDisplayName());
        return name.isBlank() ? "recovered find" : name;
    }

    /**
     * Stand-in for cabinet GUIs: same name and lore as the recovered piece, plus a reminder
     * that the real item stays in hand.
     *
     * @param template catalog row, or {@code null}
     * @param site excavation
     * @param find archive row
     * @param catalogs materials, grades, and readings
     * @return display copy that must not be given to the player
     */
    public ItemStack standIn(ArtifactTemplate template, Site site, BuriedFind find, CatalogRegistry catalogs) {
        String name = find.shownName(template == null ? null : template.displayName());
        String grade = catalogs == null ? null : catalogs.pick().conservation().gradeLabel(find.getConservation());
        ItemStack stack = baseStack(template, find);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + name);
        List<String> lines = lore(template, site, find, grade, find.isFieldDamaged(), catalogs);
        lines.add(ChatColor.DARK_GRAY + "The real artifact stays in your hand.");
        meta.setLore(lines);
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * @param stack stack to stamp
     * @param template catalog row
     * @param site excavation
     * @param find archive row
     * @param recoverer who lifted it
     * @param grade condition band label
     * @param fieldDamaged whether the dig wounded it
     * @param catalogs rarity, notes, and interpretation labels
     */
    private void write(
            ItemStack stack,
            ArtifactTemplate template,
            Site site,
            BuriedFind find,
            UUID recoverer,
            String grade,
            boolean fieldDamaged,
            CatalogRegistry catalogs
    ) {
        // Only ever a non-air stack: baseStack never returns air, and refreshed stacks are tagged finds.
        ItemMeta meta = stack.getItemMeta();
        String name = find.shownName(template == null ? null : template.displayName());
        meta.setDisplayName(ChatColor.WHITE + name);
        meta.setLore(lore(template, site, find, grade, fieldDamaged, catalogs));
        meta.setMaxStackSize(1);
        stack.setAmount(1);
        var pdc = meta.getPersistentDataContainer();
        pdc.set(markerKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(siteIdKey, PersistentDataType.STRING, site.getId().toString());
        pdc.set(siteNameKey, PersistentDataType.STRING, siteName(site));
        pdc.set(findIdKey, PersistentDataType.STRING, find.getId().toString());
        pdc.set(findNumberKey, PersistentDataType.INTEGER, find.getFindNumber());
        pdc.set(artifactIdKey, PersistentDataType.STRING, find.getArtifactId());
        pdc.set(stratumKey, PersistentDataType.STRING, find.getStratumId());
        pdc.set(conservationKey, PersistentDataType.INTEGER, find.getConservation());
        pdc.set(buriedConservationKey, PersistentDataType.INTEGER, find.getBuriedConservation());
        pdc.set(gradeKey, PersistentDataType.STRING, grade == null ? "" : grade);
        pdc.set(fieldDamagedKey, PersistentDataType.BYTE, fieldDamaged ? (byte) 1 : (byte) 0);
        pdc.set(priorDamageKey, PersistentDataType.BYTE, find.isDisturbedBeforeDig() ? (byte) 1 : (byte) 0);
        if (recoverer != null) {
            pdc.set(recoveredByKey, PersistentDataType.STRING, recoverer.toString());
        }
        if (find.getRecoveredAt() != null) {
            pdc.set(recoveredAtKey, PersistentDataType.STRING, find.getRecoveredAt().toString());
        }
        pdc.set(studiedKey, PersistentDataType.BYTE, find.isStudied() ? (byte) 1 : (byte) 0);
        pdc.set(labCleanedKey, PersistentDataType.BYTE, find.isLabCleaned() ? (byte) 1 : (byte) 0);
        pdc.set(fieldSketchKey, PersistentDataType.BYTE, find.hasFieldSketch() ? (byte) 1 : (byte) 0);
        stack.setItemMeta(meta);
    }

    /**
     * Lore grows with cabinet work: provenience first; material and conservation after
     * cleaning; rarity after a field sketch; signed readings last.
     *
     * @param template catalog row
     * @param site excavation
     * @param find archive row
     * @param grade condition band label
     * @param fieldDamaged whether the dig wounded it
     * @param catalogs materials, grades, and interpretation labels
     * @return lore lines
     */
    public List<String> lore(
            ArtifactTemplate template,
            Site site,
            BuriedFind find,
            String grade,
            boolean fieldDamaged,
            CatalogRegistry catalogs
    ) {
        List<String> lore = new ArrayList<>();
        String number = find.publicNumber(site);
        if (number != null) {
            lore.add(ChatColor.GOLD + number);
        }
        lore.add(ChatColor.DARK_GRAY + "Stratum " + find.getStratumId());
        if (find.isDisturbedBeforeDig()) {
            lore.add(ChatColor.GOLD + "Disturbed before the dig");
        }
        if (fieldDamaged) {
            lore.add(ChatColor.RED + "Hurt while digging");
        }
        if (conditionKnown(find)) {
            if (template != null) {
                lore.add(ChatColor.GRAY + "Material: " + ChatColor.WHITE
                        + catalogs.materialDisplayName(template.material()));
            }
            String condition = ChatColor.GRAY + "Conservation: " + ChatColor.WHITE + find.getConservation() + "%";
            if (grade != null && !grade.isBlank()) {
                condition += ChatColor.DARK_GRAY + " · " + grade;
            }
            lore.add(condition);
        }
        if (find.hasFieldSketch() && template != null) {
            lore.add(catalogs.rarityLoreLine(template));
        }
        lore.add(ChatColor.AQUA + find.catalogStatusLabel());
        String hint = nextCabinetHint(template, find, catalogs);
        if (hint != null) {
            lore.add(hint);
        }
        if (find.isStudied()) {
            // The dossier's own note outlives its catalog row; the catalog note is only a fallback.
            String notes = find.getStudyNotes();
            if ((notes == null || notes.isBlank()) && template != null) {
                notes = template.studyNotes();
            }
            if (notes != null && !notes.isBlank()) {
                wrap(lore, notes);
            }
        }
        if (catalogs != null) {
            for (FindInterpretation reading : find.getInterpretations()) {
                String author = CampNames.of(null, reading.author());
                lore.add(ChatColor.DARK_GRAY + "According to " + author + ": "
                        + ChatColor.WHITE + readingPhrase(reading, catalogs));
            }
        }
        return lore;
    }

    /**
     * @param find archive row
     * @return whether cleaning (or loss in the cut) has made material and conservation readable
     */
    public static boolean conditionKnown(BuriedFind find) {
        return find != null && (find.isLabCleaned() || find.getState() == FindState.LOST);
    }

    /**
     * Next cabinet action for a recovered piece, with {@code (n/3)} progress,
     * or a closing line when clean, drawing, and reading are all on file.
     *
     * @param template catalog row, or {@code null}
     * @param find archive row
     * @param catalogs materials for the first-step verb, and open station questions
     * @return lore line, or {@code null} when the piece is not in the field archive
     */
    public static String nextCabinetHint(ArtifactTemplate template, BuriedFind find, CatalogRegistry catalogs) {
        if (find == null || find.getState() != FindState.RECOVERED) {
            return null;
        }
        if (!find.isLabCleaned()) {
            String verb = "clean";
            if (catalogs != null) {
                String materialId = template == null ? null : template.material();
                verb = catalogs.materialOf(materialId).firstStepVerb();
            }
            return ChatColor.GRAY + "Use the cabinet with this in hand to " + verb + ". "
                    + ChatColor.DARK_GRAY + "(1/3)";
        }
        if (!find.hasFieldSketch()) {
            return ChatColor.GRAY + "Draw the piece and register the drawing at the cabinet. "
                    + ChatColor.DARK_GRAY + "(2/3)";
        }
        boolean readingOpen = catalogs == null
                ? !find.isCatalogued()
                : catalogs.nextOpenType(find) != null;
        if (readingOpen) {
            return ChatColor.GRAY + "Use the cabinet with this in hand for a reading. "
                    + ChatColor.DARK_GRAY + "(3/3)";
        }
        return ChatColor.GRAY + "The record on this piece is complete.";
    }

    /**
     * @param reading archive row
     * @param catalogs type and phrase labels
     * @return {@code Function: combat edge}, or the raw ids
     */
    public static String readingPhrase(FindInterpretation reading, CatalogRegistry catalogs) {
        if (reading == null) {
            return "";
        }
        String phrase = reading.interpretationId();
        String typeLabel = reading.typeId();
        if (catalogs != null) {
            InterpretationTemplate option = catalogs.interpretation(reading.interpretationId());
            if (option != null) {
                phrase = option.displayName();
                if (typeLabel == null || typeLabel.isBlank()) {
                    typeLabel = option.typeId();
                }
            }
            if (typeLabel != null) {
                InterpretationType type = catalogs.interpretationType(typeLabel);
                if (type != null) {
                    typeLabel = type.displayName();
                }
            }
        }
        if (typeLabel == null || typeLabel.isBlank()) {
            return phrase;
        }
        return typeLabel + ": " + phrase;
    }

    /**
     * @param site excavation
     * @return name used on the tag; several sites may share it
     */
    public static String siteName(Site site) {
        return site.publicName();
    }

    /**
     * Word-wraps a study note so the tooltip stays on-screen. Minecraft lore is one string per
     * line; a single long line would run off the right edge.
     *
     * @param lore lore being built
     * @param text one study note
     */
    private static void wrap(List<String> lore, String text) {
        StringBuilder line = new StringBuilder();
        String prefix = "";
        for (String word : text.split("\\s+")) {
            if (line.length() > 0 && line.length() + word.length() + 1 > LINE_WIDTH) {
                lore.add(ChatColor.WHITE + prefix + line);
                line.setLength(0);
                prefix = "  ";
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        // Callers only wrap non-blank text, so the last word always leaves a line to flush.
        lore.add(ChatColor.WHITE + prefix + line);
    }
}

