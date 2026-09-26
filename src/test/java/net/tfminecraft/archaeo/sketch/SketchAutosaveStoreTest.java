package net.tfminecraft.archaeo.sketch;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

public class SketchAutosaveStoreTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void checkpointsChangedSheetAndRemovesItAfterItemSave() throws Exception {
        SketchAutosaveStore store = new SketchAutosaveStore(temporaryFolder.getRoot().toPath());
        SketchSheet sheet = new SketchSheet();
        sheet.set(7, 12, SketchInk.OCHRE);

        store.save(42, sheet);
        assertArrayEquals(sheet.toBytes(), store.load(42));

        sheet.set(7, 12, SketchInk.SLATE);
        store.save(42, sheet);
        assertArrayEquals(sheet.toBytes(), store.load(42));

        store.delete(42);
        assertNull(store.load(42));
    }
}
