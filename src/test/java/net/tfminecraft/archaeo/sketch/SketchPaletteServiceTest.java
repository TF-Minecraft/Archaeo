package net.tfminecraft.archaeo.sketch;

import net.tfminecraft.archaeo.config.SketchSettings;
import org.bukkit.Input;
import org.bukkit.Server;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SketchPaletteServiceTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final Server server = mock(Server.class);
    private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    private final Player player = mock(Player.class);
    private final InventoryView view = mock(InventoryView.class);
    private final Inventory inventory = mock(Inventory.class);
    private final Input input = mock(Input.class);
    private final UUID playerId = UUID.randomUUID();
    private final SketchSession session = new SketchSession(null, new SketchSheet());
    private final SketchPalette palette = new SketchPalette(session);
    private final List<Runnable> tasks = new ArrayList<>();
    private Map<UUID, SketchSession> sessions;
    private SketchService sketches;

    @Before
    @SuppressWarnings("unchecked")
    public void setup() throws Exception {
        when(plugin.getName()).thenReturn("Archaeo");
        when(plugin.namespace()).thenReturn("archaeo");
        when(plugin.getDataFolder()).thenReturn(temporaryFolder.getRoot());
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            tasks.add(call.getArgument(1));
            return null;
        });
        BossBar bar = mock(BossBar.class);
        when(bar.getPlayers()).thenReturn(List.of(player));
        when(server.createBossBar(anyString(), any(), any())).thenReturn(bar);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.getOpenInventory()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(palette);
        when(player.getCurrentInput()).thenReturn(input);
        sketches = spy(new SketchService(plugin, null, null, null, null, mock(SketchSettings.class)));
        Field field = SketchService.class.getDeclaredField("sessions");
        field.setAccessible(true);
        sessions = (Map<UUID, SketchSession>) field.get(sketches);
        sessions.put(playerId, session);
    }

    @Test
    public void selectingAPigmentIsDeferredAndKeepsTheLiveSheet() {
        session.paint();
        long revision = session.sheet().revision();
        sketches.handlePaletteClick(player, palette, 22);
        assertEquals(SketchInk.CHARCOAL, session.ink());
        verify(player, never()).closeInventory();

        tasks.removeFirst().run();

        assertEquals(SketchInk.DARK_BROWN, session.ink());
        assertSame(session, sketches.session(player));
        assertEquals(revision, session.sheet().revision());
        assertEquals(SketchInk.CHARCOAL, session.sheet().at(16, 16));
        verify(player).closeInventory();
    }

    @Test
    public void backButtonAndEscapeKeepTheCurrentInk() {
        session.selectInk(SketchInk.LIGHT_MOSS);
        sketches.handlePaletteClick(player, palette, SketchPalette.SLOT_CLOSE);
        tasks.removeFirst().run();
        verify(player).closeInventory();
        assertEquals(SketchInk.LIGHT_MOSS, session.ink());

        sketches.handlePaletteClose(player, palette);
        assertSame(session, sketches.session(player));
        assertEquals(SketchInk.LIGHT_MOSS, session.ink());
        assertFalse(session.acceptsInput(false));
    }

    @Test
    public void aQueuedSelectionCannotAffectAReplacedSessionOrClosedPalette() {
        sketches.handlePaletteClick(player, palette, 22);
        sessions.put(playerId, new SketchSession(null, new SketchSheet()));
        tasks.removeFirst().run();
        assertEquals(SketchInk.CHARCOAL, sketches.session(player).ink());
        assertFalse(sketches.isPaletteFor(player, inventory));
        verify(player, never()).closeInventory();

        sessions.put(playerId, session);
        sketches.handlePaletteClick(player, palette, 22);
        when(inventory.getHolder()).thenReturn(null);
        tasks.removeFirst().run();
        assertEquals(SketchInk.CHARCOAL, session.ink());
        verify(player, never()).closeInventory();
    }

    @Test
    public void helpMarginsAndBagSlotsDoNotScheduleASelection() {
        for (int slot : new int[]{-1, 0, SketchPalette.SLOT_HELP, 27, 36}) {
            sketches.handlePaletteClick(player, palette, slot);
        }
        assertTrue(tasks.isEmpty());
    }

    @Test
    public void openPalettePausesMovementPaintingAndJump() throws Exception {
        when(input.isRight()).thenReturn(true);
        when(input.isSneak()).thenReturn(true);
        when(input.isJump()).thenReturn(true);
        applyInput();
        assertEquals(16, session.cursorX());
        assertFalse(session.sheet().hasInk());
        verify(sketches, never()).openPalette(player);
    }

    @Test
    public void spaceOpensOnceWithoutMovingOrPaintingAndClosingRequiresFreshInput() throws Exception {
        when(inventory.getHolder()).thenReturn(null);
        doAnswer(call -> {
            session.pauseInput();
            return null;
        }).when(sketches).openPalette(player);
        when(input.isJump()).thenReturn(true);
        when(input.isSneak()).thenReturn(true);
        when(input.isRight()).thenReturn(true);
        applyInput();
        applyInput();
        assertEquals(16, session.cursorX());
        assertFalse(session.sheet().hasInk());
        verify(sketches).openPalette(player);

        when(input.isJump()).thenReturn(false);
        when(input.isSneak()).thenReturn(false);
        when(input.isRight()).thenReturn(false);
        applyInput();
        when(input.isSneak()).thenReturn(true);
        applyInput();
        assertTrue(session.sheet().hasInk());
    }

    private void applyInput() throws Exception {
        Method method = SketchService.class.getDeclaredMethod("applyInput", Player.class, SketchSession.class);
        method.setAccessible(true);
        method.invoke(sketches, player, session);
    }
}
