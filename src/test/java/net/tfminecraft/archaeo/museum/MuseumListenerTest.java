package net.tfminecraft.archaeo.museum;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MuseumListenerTest {
    @Test
    public void selectsAllThreeSlotsFromEachFacing() {
        for (int slot = 0; slot < 3; slot++) {
            double horizontal = (slot + 0.5) / 3;
            assertEquals(slot, MuseumListener.shelfSlot(BlockFace.NORTH, new Vector(1 - horizontal, 0.5, 0)));
            assertEquals(slot, MuseumListener.shelfSlot(BlockFace.SOUTH, new Vector(horizontal, 0.5, 1)));
            assertEquals(slot, MuseumListener.shelfSlot(BlockFace.WEST, new Vector(0, 0.5, horizontal)));
            assertEquals(slot, MuseumListener.shelfSlot(BlockFace.EAST, new Vector(1, 0.5, 1 - horizontal)));
        }
    }

    @Test
    public void keepsEdgesInsideTheInventoryAndSplitsAtThirds() {
        double[] positions = {0, 1.0 / 3 - 0.001, 1.0 / 3, 2.0 / 3 - 0.001, 2.0 / 3, 1};
        int[] slots = {0, 0, 1, 1, 2, 2};
        for (int i = 0; i < positions.length; i++) {
            assertEquals(slots[i], MuseumListener.shelfSlot(BlockFace.SOUTH, new Vector(positions[i], 0, 1)));
        }
    }

    @Test
    public void rejectsPositionsOutsideTheBlock() {
        assertEquals(-1, MuseumListener.shelfSlot(BlockFace.SOUTH, null));
        assertEquals(-1, MuseumListener.shelfSlot(BlockFace.UP, new Vector(0.5, 0.5, 0.5)));
        for (double invalid : new double[] {-0.01, 1.01, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertEquals(-1, MuseumListener.shelfSlot(BlockFace.SOUTH, new Vector(invalid, 0.5, 0.5)));
            assertEquals(-1, MuseumListener.shelfSlot(BlockFace.SOUTH, new Vector(0.5, invalid, 0.5)));
            assertEquals(-1, MuseumListener.shelfSlot(BlockFace.SOUTH, new Vector(0.5, 0.5, invalid)));
        }
    }
}
