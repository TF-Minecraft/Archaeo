package com.nowko.archeology.site;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Remembers which chunks Archaeo has already considered for auto-spawn.
 * That is independent of Minecraft's {@code isNewChunk}: an old explored map can still be
 * evaluated the first time each chunk loads after the plugin is installed, and never again.
 *
 * <p>Storage is one 32×32-chunk region bitset (128 bytes) under
 * {@code plugins/Archaeo/auto-ruins/evaluated/<world>/r.<rx>.<rz>.bin}.
 */
public class AutoRuinEvaluationLedger {
    private static final int REGION = 32;
    private static final int BITS = REGION * REGION;
    private static final int BYTES = BITS / 8;

    private final JavaPlugin plugin;
    private final Path root;
    /** world name → region key → evaluated chunks in that region */
    private final Map<String, Map<Long, BitSet>> worlds = new ConcurrentHashMap<>();
    private final Map<String, Map<Long, Boolean>> dirty = new ConcurrentHashMap<>();

    /**
     * @param plugin data-folder owner and logger
     */
    public AutoRuinEvaluationLedger(JavaPlugin plugin) {
        this.plugin = plugin;
        this.root = plugin.getDataFolder().toPath().resolve("auto-ruins").resolve("evaluated");
    }

    /**
     * Loads every region file already on disk into memory.
     */
    public void load() {
        worlds.clear();
        dirty.clear();
        if (!Files.isDirectory(root)) {
            return;
        }
        try (var worldsStream = Files.list(root)) {
            worldsStream.filter(Files::isDirectory).forEach(this::loadWorldFolder);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not list auto-ruin evaluation ledger", exception);
        }
    }

    /**
     * Writes every dirty region bitset. Called on disable and by the flush timer.
     */
    public void flush() {
        for (Map.Entry<String, Map<Long, Boolean>> worldEntry : dirty.entrySet()) {
            String worldName = worldEntry.getKey();
            Map<Long, BitSet> regions = worlds.get(worldName);
            if (regions == null) {
                continue;
            }
            for (Long regionKey : worldEntry.getValue().keySet()) {
                BitSet bits = regions.get(regionKey);
                if (bits != null) {
                    writeRegion(worldName, regionKey, bits);
                }
            }
            worldEntry.getValue().clear();
        }
    }

    /**
     * @param worldName world id
     * @param chunkX chunk X
     * @param chunkZ chunk Z
     * @return whether this chunk was already decided (spawn or skip)
     */
    public boolean isEvaluated(String worldName, int chunkX, int chunkZ) {
        BitSet bits = region(sanitize(worldName), chunkX, chunkZ, false);
        return bits != null && bits.get(bitIndex(chunkX, chunkZ));
    }

    /**
     * Records that Archaeo finished considering this chunk.
     *
     * @param worldName world id
     * @param chunkX chunk X
     * @param chunkZ chunk Z
     */
    public void markEvaluated(String worldName, int chunkX, int chunkZ) {
        String key = sanitize(worldName);
        BitSet bits = region(key, chunkX, chunkZ, true);
        int bit = bitIndex(chunkX, chunkZ);
        if (bits.get(bit)) {
            return;
        }
        bits.set(bit);
        dirty
                .computeIfAbsent(key, ignored -> new ConcurrentHashMap<>())
                .put(regionKey(chunkX, chunkZ), Boolean.TRUE);
    }

    /**
     * @param folder {@code evaluated/<world>}
     */
    private void loadWorldFolder(Path folder) {
        String worldName = folder.getFileName().toString();
        try (var files = Files.list(folder)) {
            files.filter(path -> path.getFileName().toString().endsWith(".bin")).forEach(path -> {
                String name = path.getFileName().toString();
                // r.<rx>.<rz>.bin
                if (!name.startsWith("r.") || !name.endsWith(".bin")) {
                    return;
                }
                String body = name.substring(2, name.length() - 4);
                int split = body.lastIndexOf('.');
                if (split <= 0) {
                    return;
                }
                try {
                    int rx = Integer.parseInt(body.substring(0, split));
                    int rz = Integer.parseInt(body.substring(split + 1));
                    byte[] raw = Files.readAllBytes(path);
                    BitSet bits = fromBytes(raw);
                    worlds
                            .computeIfAbsent(worldName, ignored -> new ConcurrentHashMap<>())
                            .put(packRegion(rx, rz), bits);
                } catch (IOException | NumberFormatException exception) {
                    plugin.getLogger().log(Level.WARNING, "Could not read " + path, exception);
                }
            });
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not list " + folder, exception);
        }
    }

    /**
     * @param worldName world id
     * @param regionKey packed region coords
     * @param bits evaluated mask
     */
    private void writeRegion(String worldName, long regionKey, BitSet bits) {
        int rx = (int) (regionKey >> 32);
        int rz = (int) regionKey;
        Path folder = root.resolve(sanitize(worldName));
        try {
            Files.createDirectories(folder);
            Path file = folder.resolve("r." + rx + "." + rz + ".bin");
            Files.write(file, toBytes(bits));
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not write auto-ruin ledger for " + worldName + " region " + rx + "," + rz,
                    exception);
        }
    }

    /**
     * @param worldName world id
     * @param chunkX chunk X
     * @param chunkZ chunk Z
     * @param create whether to allocate a bitset when missing
     * @return region bitset, or {@code null} when missing and {@code create} is false
     */
    private BitSet region(String worldName, int chunkX, int chunkZ, boolean create) {
        Map<Long, BitSet> regions = worlds.get(worldName);
        if (regions == null) {
            if (!create) {
                return null;
            }
            regions = worlds.computeIfAbsent(worldName, ignored -> new ConcurrentHashMap<>());
        }
        long key = regionKey(chunkX, chunkZ);
        BitSet bits = regions.get(key);
        if (bits == null && create) {
            bits = new BitSet(BITS);
            regions.put(key, bits);
        }
        return bits;
    }

    /**
     * @param chunkX chunk X
     * @param chunkZ chunk Z
     * @return packed region key
     */
    private static long regionKey(int chunkX, int chunkZ) {
        return packRegion(Math.floorDiv(chunkX, REGION), Math.floorDiv(chunkZ, REGION));
    }

    /**
     * @param rx region X
     * @param rz region Z
     * @return packed key
     */
    private static long packRegion(int rx, int rz) {
        return (((long) rx) << 32) | (rz & 0xffffffffL);
    }

    /**
     * @param chunkX chunk X
     * @param chunkZ chunk Z
     * @return bit inside the 32×32 region mask
     */
    private static int bitIndex(int chunkX, int chunkZ) {
        int lx = Math.floorMod(chunkX, REGION);
        int lz = Math.floorMod(chunkZ, REGION);
        return lz * REGION + lx;
    }

    /**
     * @param bits region mask
     * @return fixed-length byte array for disk
     */
    private static byte[] toBytes(BitSet bits) {
        byte[] raw = bits.toByteArray();
        if (raw.length == BYTES) {
            return raw;
        }
        byte[] fixed = new byte[BYTES];
        System.arraycopy(raw, 0, fixed, 0, Math.min(raw.length, BYTES));
        return fixed;
    }

    /**
     * @param raw region file bytes
     * @return bitset (empty when the file was blank)
     */
    private static BitSet fromBytes(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return new BitSet(BITS);
        }
        return BitSet.valueOf(raw);
    }

    /**
     * @param worldName raw Bukkit world name
     * @return filesystem-safe folder name
     */
    private static String sanitize(String worldName) {
        StringBuilder builder = new StringBuilder(worldName.length());
        for (int i = 0; i < worldName.length(); i++) {
            char character = worldName.charAt(i);
            if (Character.isLetterOrDigit(character) || character == '-' || character == '_' || character == '.') {
                builder.append(character);
            } else {
                builder.append('_');
            }
        }
        return builder.isEmpty() ? "world" : builder.toString();
    }
}
