package net.tfminecraft.archaeo.sketch;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.mockito.Mockito.*;

public class SketchPaletteListenerTest {
    private final SketchService sketches = mock(SketchService.class);
    private final SketchListener listener = new SketchListener(sketches);
    private final Player player = mock(Player.class);
    private final Inventory top = mock(Inventory.class);
    private final Inventory bag = mock(Inventory.class);
    private final InventoryView view = mock(InventoryView.class);
    private final SketchPalette palette = new SketchPalette(
            new SketchSession(null, new SketchSheet()));

    @Before
    public void setup() {
        when(top.getHolder()).thenReturn(palette);
        when(view.getTopInventory()).thenReturn(top);
        when(sketches.editing(player)).thenReturn(true);
    }

    @Test
    public void allInventoryActionsAreCancelledAndOnlyPlainSwatchClicksSelect() {
        for (ClickType click : ClickType.values()) {
            for (Inventory clicked : new Inventory[]{top, bag, null}) {
                clearInvocations(sketches);
                InventoryClickEvent event = mock(InventoryClickEvent.class);
                when(event.getView()).thenReturn(view);
                when(event.getWhoClicked()).thenReturn(player);
                when(event.getClickedInventory()).thenReturn(clicked);
                when(event.getClick()).thenReturn(click);
                when(event.getSlot()).thenReturn(10);

                listener.onPaletteClick(event);

                verify(event).setCancelled(true);
                if (clicked == top && (click == ClickType.LEFT || click == ClickType.RIGHT)) {
                    verify(sketches).handlePaletteClick(player, palette, 10);
                } else {
                    verifyNoInteractions(sketches);
                }
            }
        }
    }

    @Test
    public void draggingCannotMoveItemsIntoThePaletteOrTheBag() {
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        when(event.getView()).thenReturn(view);
        listener.onPaletteDrag(event);
        verify(event).setCancelled(true);
    }

    @Test
    public void ownPaletteMayOpenWhileEditing() {
        InventoryOpenEvent event = mock(InventoryOpenEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getInventory()).thenReturn(top);
        when(sketches.isPaletteFor(player, top)).thenReturn(true);
        listener.onInventory(event);
        verify(event, never()).setCancelled(true);
    }

    @Test
    public void escapeResumesTheExistingSessionWithoutMovingOrSavingTheMap() {
        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getInventory()).thenReturn(top);
        listener.onInventoryClose(event);
        verify(sketches).handlePaletteClose(player, palette);
        verifyNoMoreInteractions(sketches);
    }
}
