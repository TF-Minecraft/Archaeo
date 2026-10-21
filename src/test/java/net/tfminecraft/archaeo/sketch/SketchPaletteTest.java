package net.tfminecraft.archaeo.sketch;

import org.junit.Test;
import org.bukkit.map.MapPalette;

import java.awt.Color;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class SketchPaletteTest {
    @Test
    @SuppressWarnings("deprecation")
    public void allSwatchesRemainDistinctAfterMinecraftMapColourMatching() {
        Set<Byte> mapColours = new HashSet<>();
        mapColours.add(MapPalette.matchColor(SketchInk.PAPER.color()));
        for (SketchInk ink : SketchInk.values()) {
            if (ink != SketchInk.PAPER) {
                assertTrue("Map colour duplicates another pigment: " + ink,
                        mapColours.add(MapPalette.matchColor(ink.color())));
            }
        }
    }

    @Test
    public void oldSavedPixelsKeepTheirOriginalColours() {
        SketchInk[] expected = {SketchInk.PAPER, SketchInk.CHARCOAL, SketchInk.RED,
                SketchInk.OCHRE, SketchInk.SLATE, SketchInk.MOSS};
        Color[] colours = {new Color(247, 236, 214), new Color(36, 32, 30),
                new Color(168, 42, 36), new Color(186, 122, 48),
                new Color(62, 92, 138), new Color(72, 108, 58)};
        SketchSheet legacy = SketchSheet.fromBytes(new byte[]{0, 1, 2, 3, 4, 5});
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], legacy.at(i, 0));
            assertEquals(colours[i], legacy.at(i, 0).color());
        }
    }

    @Test
    public void everyPigmentCanBePickedAndSavedWithoutOfferingPaperOrBagSlots() {
        EnumSet<SketchInk> offered = EnumSet.noneOf(SketchInk.class);
        SketchSheet sheet = new SketchSheet();
        for (int slot = 0; slot < 27; slot++) {
            SketchInk ink = SketchPalette.inkAt(slot);
            assertNotNull("Empty colour slot " + slot, ink);
            if (ink != null) {
                assertNotEquals(SketchInk.PAPER, ink);
                assertTrue("Duplicate pigment " + ink, offered.add(ink));
                sheet.set(slot, 0, ink);
            }
        }
        assertEquals(EnumSet.complementOf(EnumSet.of(SketchInk.PAPER)), offered);
        assertEquals(27, offered.size());
        SketchSheet restored = SketchSheet.fromBytes(sheet.toBytes());
        for (int slot = 0; slot < 27; slot++) {
            assertEquals(sheet.at(slot, 0), restored.at(slot, 0));
        }
        assertNull(SketchPalette.inkAt(-1));
        for (int slot = 27; slot < 64; slot++) {
            assertNull(SketchPalette.inkAt(slot));
        }
    }
}
