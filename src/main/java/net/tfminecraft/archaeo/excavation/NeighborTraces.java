package net.tfminecraft.archaeo.excavation;

import net.tfminecraft.archaeo.config.ArtifactTemplate;
import net.tfminecraft.archaeo.config.CatalogRegistry;
import net.tfminecraft.archaeo.model.BlockCell;
import net.tfminecraft.archaeo.model.BuriedFind;
import net.tfminecraft.archaeo.model.FindState;
import net.tfminecraft.archaeo.model.Site;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minesweeper-style readout: how many still-buried find cubes share a face with an opened cell.
 * Counts cubes, not artifacts, grouped by catalog material so a heavy tool can be judged.
 */
public final class NeighborTraces {
    private NeighborTraces() {
    }

    /**
     * Face neighbours that still hold a live find cube.
     *
     * @param site excavation dossier
     * @param catalogs artifact materials and display names
     * @param origin emptied (or about-to-empty) cell
     * @return material id to adjacent cube count; empty when the cut is clear
     */
    public static Map<String, Integer> count(Site site, CatalogRegistry catalogs, Block origin) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BlockFace face : PrismFill.FACES) {
            Block neighbour = origin.getRelative(face);
            BuriedFind find = site.findAt(new BlockCell(neighbour.getX(), neighbour.getY(), neighbour.getZ()))
                    .orElse(null);
            if (find == null || find.getState() == FindState.LOST || find.getState() == FindState.RECOVERED) {
                continue;
            }
            if (!PrismFill.isTerrainFill(neighbour.getType())) {
                continue;
            }
            ArtifactTemplate template = catalogs.artifact(find.getArtifactId());
            String material = template == null || template.material() == null || template.material().isBlank()
                    ? "unknown"
                    : template.material();
            counts.merge(material, 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Chat line after a cut. Silent when no traces so empty fill does not flood the log.
     *
     * @param catalogs material labels
     * @param counts {@link #count} result
     * @return English line, or {@code null} when the neighbourhood is clear
     */
    public static String chatLine(CatalogRegistry catalogs, Map<String, Integer> counts) {
        if (counts.isEmpty()) {
            return null;
        }
        return "Traces of " + joined(catalogs, counts);
    }

    /**
     * Compact HUD fragment for an opened air cell.
     *
     * @param catalogs material labels
     * @param counts {@link #count} result
     * @return {@code Ceramic: 1 · Bone: 1} or {@code clear}
     */
    public static String hudFragment(CatalogRegistry catalogs, Map<String, Integer> counts) {
        if (counts.isEmpty()) {
            return "clear";
        }
        return joined(catalogs, counts);
    }

    /**
     * @param catalogs material labels
     * @param counts material id to cube count
     * @return {@code ceramic: 1 · bone: 1}
     */
    private static String joined(CatalogRegistry catalogs, Map<String, Integer> counts) {
        List<String> parts = new ArrayList<>(counts.size());
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            parts.add(catalogs.materialDisplayName(entry.getKey()) + ": " + entry.getValue());
        }
        return String.join(" · ", parts);
    }
}
