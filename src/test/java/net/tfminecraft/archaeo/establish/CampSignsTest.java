package net.tfminecraft.archaeo.establish;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalMatchers.aryEq;

public class CampSignsTest {
    private Block block;
    @Before public void setup() {
        block = MockBukkit.mock().addSimpleWorld("world").getBlockAt(0, 5, 0);
        block.setType(Material.OAK_SIGN);
    }
    @After public void teardown() { MockBukkit.unmock(); }

    @Test public void wrapsAtWordsSplitsLongTokensAndRespectsTheFourLineSignLimit() {
        assertEquals(List.of(), CampSigns.wrap(" \t ", 15, 4));
        assertEquals(List.of("Old river camp"), CampSigns.wrap("  Old river camp  ", 15, 4));
        assertEquals(List.of("The ancient", "river", "settlement"),
                CampSigns.wrap("The ancient river settlement", 15, 4));
        assertEquals(List.of("123456789012345", "67890"), CampSigns.wrap("12345678901234567890", 15, 4));
        assertEquals(List.of("one", "two", "three", "four"), CampSigns.wrap("one two three four five", 5, 4));
    }

    @Test public void renamingUpdatesAndWaxesFrontClearsOldLinesAndPreservesBack() {
        // MockBukkit does not implement waxing; preserve its real front/back text storage.
        Sign sign = spy((Sign) block.getState());
        doNothing().when(sign).setWaxed(true);
        block = spy(block); doReturn(sign).when(block).getState();
        sign.getSide(Side.BACK).setLine(0, "Do not erase"); sign.update();
        CampSigns.write(block, "Old river camp with a very long archaeological title");
        Sign written = (Sign) block.getState(); verify(sign).setWaxed(true); verify(sign, times(2)).update();
        assertEquals("Old river camp", written.getSide(Side.FRONT).getLine(0));
        assertEquals("Do not erase", written.getSide(Side.BACK).getLine(0));
        CampSigns.write(block, "Quarry");
        assertArrayEquals(new String[]{"Quarry", "", "", ""}, ((Sign) block.getState()).getSide(Side.FRONT).getLines());
        CampSigns.write(block, null);
        assertArrayEquals(new String[]{"", "", "", ""}, ((Sign) block.getState()).getSide(Side.FRONT).getLines());
    }

    @Test public void restoringPreviewSendsRealBlockAndTextWithoutChangingTheServerSign() {
        Sign before = (Sign) block.getState(); before.getSide(Side.FRONT).setLine(0, "River camp"); before.update();
        Player viewer = mock(Player.class); CampSigns.sendTo(viewer, block);
        var order = inOrder(viewer);
        order.verify(viewer).sendBlockChange(block.getLocation(), before.getBlockData());
        order.verify(viewer).sendSignChange(eq(block.getLocation()), aryEq(before.getSide(Side.FRONT).getLines()));
        assertArrayEquals(before.getSide(Side.FRONT).getLines(), ((Sign) block.getState()).getSide(Side.FRONT).getLines());
        block.setType(Material.STONE); clearInvocations(viewer);
        CampSigns.write(block, "ignored"); CampSigns.sendTo(viewer, block);
        assertEquals(Material.STONE, block.getType()); verifyNoInteractions(viewer);
    }
}
