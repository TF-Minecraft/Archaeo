package net.tfminecraft.archaeo.sketch;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Durable recovery copies for open sheets, without changing the map held by the player. */
final class SketchAutosaveStore {
    record Snapshot(long revision, byte[] cells) {
    }

    private final Path directory;

    SketchAutosaveStore(Path pluginDataFolder) {
        this.directory = pluginDataFolder.resolve("sketch-autosaves");
    }

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

    void delete(int mapId) throws IOException {
        Files.deleteIfExists(fileFor(mapId));
    }

    private Path fileFor(int mapId) {
        return directory.resolve(mapId + ".bin");
    }
}
