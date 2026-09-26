package net.tfminecraft.archaeo.sketch;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

public class SketchAutosaveStoreTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    /** Verifies atomic replacement, revision preservation, and cleanup. */
    @Test
    public void checkpointsChangedSheetAndRemovesItAfterItemSave() throws Exception {
        SketchAutosaveStore store = new SketchAutosaveStore(temporaryFolder.getRoot().toPath());
        SketchSheet sheet = new SketchSheet();
        sheet.set(7, 12, SketchInk.OCHRE);

        store.save(42, sheet);
        SketchAutosaveStore.Snapshot first = store.load(42);
        assertArrayEquals(sheet.toBytes(), first.cells());
        org.junit.Assert.assertEquals(sheet.revision(), first.revision());

        sheet.set(7, 12, SketchInk.SLATE);
        store.save(42, sheet);
        SketchAutosaveStore.Snapshot second = store.load(42);
        assertArrayEquals(sheet.toBytes(), second.cells());
        org.junit.Assert.assertEquals(sheet.revision(), second.revision());

        store.delete(42);
        assertNull(store.load(42));
    }

    /** Verifies a short or padded file is refused rather than read as a shifted drawing. */
    @Test
    public void checkpointOfTheWrongSizeIsRejectedWithTheMapIdInTheMessage() throws Exception {
        SketchAutosaveStore store = new SketchAutosaveStore(temporaryFolder.getRoot().toPath());
        store.save(42, new SketchSheet());
        java.nio.file.Path file = temporaryFolder.getRoot().toPath().resolve("sketch-autosaves").resolve("42.bin");
        byte[] valid = java.nio.file.Files.readAllBytes(file);
        for (int length : new int[]{0, valid.length - 1, valid.length + 1}) {
            java.nio.file.Files.write(file, java.util.Arrays.copyOf(valid, length));
            java.io.IOException refused = org.junit.Assert.assertThrows(java.io.IOException.class, () -> store.load(42));
            org.junit.Assert.assertEquals("Invalid sketch autosave size for map 42", refused.getMessage());
        }
    }
}
