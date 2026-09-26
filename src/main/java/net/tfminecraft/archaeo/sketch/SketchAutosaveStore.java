package net.tfminecraft.archaeo.sketch;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Durable recovery copies for open sheets, without changing the map held by the player. */
final class SketchAutosaveStore {
    /** Autosaved cells and the sheet revision they contain. */
    record Snapshot(long revision, byte[] cells) {
    }

    private final Path directory;

    /**
     * @param pluginDataFolder plugin data directory
     */
    SketchAutosaveStore(Path pluginDataFolder) {
        this.directory = pluginDataFolder.resolve("sketch-autosaves");
    }

    /**
     * Loads the recovery copy for one map.
     *
     * @param mapId Bukkit map ID
     * @return saved revision and cells, or {@code null} when no checkpoint exists
     * @throws IOException if the checkpoint cannot be read or is malformed
     */
    Snapshot load(int mapId) throws IOException {
        Path file = fileFor(mapId);
        if (!Files.exists(file)) {
            return null;
        }
        byte[] data = Files.readAllBytes(file);
        if (data.length != Long.BYTES + SketchSheet.SIZE * SketchSheet.SIZE) {
            throw new IOException("Invalid sketch autosave size for map " + mapId);
        }
        ByteBuffer buffer = ByteBuffer.wrap(data);
        long revision = buffer.getLong();
        byte[] cells = new byte[SketchSheet.SIZE * SketchSheet.SIZE];
        buffer.get(cells);
        return new Snapshot(revision, cells);
    }

    /**
     * Atomically replaces the recovery copy for one map.
     *
     * @param mapId Bukkit map ID
     * @param sheet current drawing
     * @throws IOException if the checkpoint cannot be written
     */
    void save(int mapId, SketchSheet sheet) throws IOException {
        Files.createDirectories(directory);
        Path destination = fileFor(mapId);
        Path temporary = Files.createTempFile(directory, mapId + "-", ".tmp");
        try {
            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES + SketchSheet.SIZE * SketchSheet.SIZE);
            buffer.putLong(sheet.revision());
            buffer.put(sheet.toBytes());
            Files.write(temporary, buffer.array());
            try {
                Files.move(temporary, destination,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Removes the recovery copy after the item has been saved.
     *
     * @param mapId Bukkit map ID
     * @throws IOException if the checkpoint cannot be removed
     */
    void delete(int mapId) throws IOException {
        Files.deleteIfExists(fileFor(mapId));
    }

    /**
     * @param mapId Bukkit map ID
     * @return path for that map's checkpoint
     */
    private Path fileFor(int mapId) {
        return directory.resolve(mapId + ".bin");
    }
}
