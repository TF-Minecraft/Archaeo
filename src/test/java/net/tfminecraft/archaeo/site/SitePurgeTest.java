package net.tfminecraft.archaeo.site;

import net.tfminecraft.archaeo.establish.CampClosure;
import net.tfminecraft.archaeo.item.RecoveredFindItem;
import net.tfminecraft.archaeo.model.BuriedFind;
import net.tfminecraft.archaeo.model.Site;
import org.bukkit.Chunk;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SitePurgeTest {
    @Test public void removesOnlyTheExactSiteAndFindFromLoadedInventoriesAndDisplaysAfterSaving() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.namespace()).thenReturn("archaeo");
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        SiteRepository sites = mock(SiteRepository.class);
        RecoveredFindItem tags = mock(RecoveredFindItem.class);
        Site site = new Site(); site.setId(UUID.randomUUID());
        BuriedFind find = new BuriedFind(); find.setId(UUID.randomUUID());
        Path backup = Path.of("backup.yml");
        when(sites.deleteRecoveredFind(site, find)).thenReturn(backup);

        ItemStack matching = mock(ItemStack.class), sibling = mock(ItemStack.class), otherSite = mock(ItemStack.class);
        when(tags.siteIdOf(matching)).thenReturn(site.getId()); when(tags.findIdOf(matching)).thenReturn(find.getId());
        when(tags.siteIdOf(sibling)).thenReturn(site.getId()); when(tags.findIdOf(sibling)).thenReturn(UUID.randomUUID());
        when(tags.siteIdOf(otherSite)).thenReturn(UUID.randomUUID()); when(tags.findIdOf(otherSite)).thenReturn(find.getId());
        Player player = mock(Player.class);
        PlayerInventory bag = mock(PlayerInventory.class);
        Inventory ender = mock(Inventory.class), top = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        when(player.getInventory()).thenReturn(bag); when(player.getEnderChest()).thenReturn(ender);
        when(player.getOpenInventory()).thenReturn(view); when(view.getTopInventory()).thenReturn(top);
        when(bag.getContents()).thenReturn(new ItemStack[]{matching, sibling, otherSite});
        when(ender.getContents()).thenReturn(new ItemStack[]{matching});
        when(player.getItemOnCursor()).thenReturn(matching);
        doReturn(List.of(player)).when(server).getOnlinePlayers();
        World world = mock(World.class); when(server.getWorlds()).thenReturn(List.of(world));
        ItemFrame frame = mock(ItemFrame.class); when(frame.getItem()).thenReturn(matching);
        ItemDisplay display = mock(ItemDisplay.class); when(display.getItemStack()).thenReturn(sibling);
        Item drop = mock(Item.class); when(drop.getItemStack()).thenReturn(matching);
        when(world.getEntitiesByClass(ItemFrame.class)).thenReturn(List.of(frame));
        when(world.getEntitiesByClass(ItemDisplay.class)).thenReturn(List.of(display));
        when(world.getEntitiesByClass(Item.class)).thenReturn(List.of(drop));
        Chunk chunk = mock(Chunk.class);
        when(world.getLoadedChunks()).thenReturn(new Chunk[]{chunk});
        when(chunk.getTileEntities()).thenReturn(new BlockState[0]);

        SitePurge purge = new SitePurge(plugin, sites, null, null, null, null, tags, mock(CampClosure.class), null);
        assertEquals(backup, purge.eraseFind(site, find));
        var order = inOrder(sites, bag);
        order.verify(sites).deleteRecoveredFind(site, find); order.verify(bag).setItem(0, null);
        verify(bag, never()).setItem(eq(1), any()); verify(bag, never()).setItem(eq(2), any());
        verify(ender).setItem(0, null); verify(player).setItemOnCursor(null);
        verify(frame).setItem(null); verify(drop).remove(); verify(display, never()).setItemStack(any());
        verify(sites, never()).erase(any());
    }

    @Test public void doesNotTouchPhysicalItemsWhenArchiveDeletionFails() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.namespace()).thenReturn("archaeo");
        SiteRepository sites = mock(SiteRepository.class);
        Site site = new Site(); BuriedFind find = new BuriedFind();
        when(sites.deleteRecoveredFind(site, find)).thenThrow(new IllegalStateException("Disk full"));
        SitePurge purge = new SitePurge(plugin, sites, null, null, null, null,
                mock(RecoveredFindItem.class), mock(CampClosure.class), null);
        clearInvocations(plugin);
        assertThrows(IllegalStateException.class, () -> purge.eraseFind(site, find));
        verifyNoInteractions(plugin);
    }
}
