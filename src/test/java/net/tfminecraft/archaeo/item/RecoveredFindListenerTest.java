package net.tfminecraft.archaeo.item;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.archaeo.config.*;
import net.tfminecraft.archaeo.model.*;
import net.tfminecraft.archaeo.site.SiteRepository;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.*;
import org.mockito.ArgumentCaptor;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

public class RecoveredFindListenerTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private PlayerMock player;
    private RecoveredFindItem recovered;
    private RecoveredFindListener listener;
    private SiteRepository sites;
    private CatalogRegistry catalogs;
    private ArtifactTemplate template;
    private Site site;
    private BuriedFind find;

    @Before public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin(); player = server.addPlayer();
        recovered = new RecoveredFindItem(plugin);
        sites = mock(SiteRepository.class); catalogs = mock(CatalogRegistry.class);
        when(catalogs.pick()).thenReturn(PickSettings.defaults());
        when(catalogs.materialOf("ceramic")).thenReturn(new FindMaterial("ceramic", "Ceramic", 1, Material.WHITE_STAINED_GLASS_PANE, List.of("soil")));
        template = new ArtifactTemplate("pot", "Ancient pot", 1, 1, "ceramic", null, false, 1, Set.of(), Set.of(), FindProfile.OBJECT, List.of(ItemRef.vanilla(Material.BRICK)), "");
        when(catalogs.artifact("pot")).thenReturn(template);
        site = new Site(); site.setId(UUID.randomUUID()); site.setName("Old camp");
        find = new BuriedFind(); find.setId(UUID.randomUUID()); find.setArtifactId("pot");
        find.setState(FindState.RECOVERED); find.setStratumId("I"); find.setFindNumber(1);
        site.getFinds().add(find);
        when(sites.findById(site.getId())).thenReturn(Optional.of(site));
        listener = new RecoveredFindListener(plugin, sites, catalogs, recovered);
    }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test public void anvilPreviewPreservesArchiveTagWhenVanillaOutputLosesIt() {
        ItemStack left = create();
        PrepareAnvilEvent event = preview(left, new ItemStack(Material.BRICK), " Family urn ");
        listener.onPrepareAnvil(event);
        ArgumentCaptor<ItemStack> output = ArgumentCaptor.forClass(ItemStack.class);
        verify(event).setResult(output.capture());
        ItemStack stamped = output.getValue();
        assertTrue(recovered.isThisFind(stamped, find.getId()));
        assertEquals(site.getId(), recovered.siteIdOf(stamped));
        assertEquals("Family urn", recovered.labelOf(stamped));
        assertEquals("Ancient pot", recovered.labelOf(left));
        assertNotSame(left, stamped);
        assertEquals(left.getItemMeta().getLore(), stamped.getItemMeta().getLore());
        assertNull(find.getGivenName());
        verify(sites, never()).save(any());
    }

    @Test public void anvilPreviewLeavesExistingTagsAndNonFindRecipesAlone() {
        ItemStack tagged = create();
        PrepareAnvilEvent alreadyTagged = preview(tagged, tagged.clone(), "New name");
        listener.onPrepareAnvil(alreadyTagged);
        verify(alreadyTagged, never()).setResult(any());
        for (ItemStack result : new ItemStack[] {null, new ItemStack(Material.AIR)}) {
            PrepareAnvilEvent absent = preview(tagged, result, "Name");
            listener.onPrepareAnvil(absent);
            verify(absent, never()).setResult(any());
        }
        PrepareAnvilEvent ordinary = preview(new ItemStack(Material.BRICK), new ItemStack(Material.BRICK), "Ordinary");
        listener.onPrepareAnvil(ordinary);
        verify(ordinary, never()).setResult(any());
        PrepareAnvilEvent noName = preview(tagged, new ItemStack(Material.BRICK), null);
        listener.onPrepareAnvil(noName);
        ArgumentCaptor<ItemStack> output = ArgumentCaptor.forClass(ItemStack.class);
        verify(noName).setResult(output.capture());
        assertEquals("Ancient pot", recovered.labelOf(output.getValue()));
    }

    @Test public void takingRenamePersistsDossierThenRefreshesCarriedPieceOnNextTick() {
        ItemStack stack = create(); player.getInventory().setItem(0, stack);
        listener.onAnvilTake(take(stack, " Family urn "));
        assertEquals("Family urn", find.getGivenName());
        verify(sites).save(site);
        assertEquals("Ancient pot", recovered.labelOf(player.getInventory().getItem(0)));
        server.getScheduler().performOneTick();
        assertEquals("Family urn", recovered.labelOf(player.getInventory().getItem(0)));
        assertEquals(find.getId(), recovered.findIdOf(player.getInventory().getItem(0)));
        listener.onAnvilTake(take(player.getInventory().getItem(0), "Family urn"));
        verify(sites, times(1)).save(site);
    }

    @Test public void catalogNameClearsCustomNameAndBlankAnvilTextUsesResultName() {
        find.setGivenName("Family urn");
        ItemStack stack = create(); player.getInventory().setItem(0, stack);
        listener.onAnvilTake(take(stack, "ANCIENT POT"));
        assertNull(find.getGivenName());
        server.getScheduler().performOneTick();
        assertEquals("Ancient pot", recovered.labelOf(player.getInventory().getItem(0)));
        ItemMeta meta = stack.getItemMeta(); meta.setDisplayName(ChatColor.GOLD + "River vessel"); stack.setItemMeta(meta);
        listener.onAnvilTake(take(stack, " "));
        assertEquals("River vessel", find.getGivenName());
        verify(sites, times(2)).save(site);
    }

    @Test public void unknownArchiveRowsAndOtherInventorySlotsDoNotPersistNames() {
        ItemStack stack = create();
        InventoryClickEvent inputSlot = take(stack, "Name"); when(inputSlot.getRawSlot()).thenReturn(0);
        listener.onAnvilTake(inputSlot);
        listener.onAnvilTake(take(new ItemStack(Material.BRICK), "Name"));
        InventoryClickEvent otherView = mock(InventoryClickEvent.class);
        when(otherView.getRawSlot()).thenReturn(2); when(otherView.getWhoClicked()).thenReturn(player);
        when(otherView.getView()).thenReturn(player.getOpenInventory());
        listener.onAnvilTake(otherView);
        when(sites.findById(site.getId())).thenReturn(Optional.empty());
        listener.onAnvilTake(take(stack, "Name"));
        when(sites.findById(site.getId())).thenReturn(Optional.of(site)); site.getFinds().clear();
        listener.onAnvilTake(take(stack, "Name"));
        assertNull(find.getGivenName()); verify(sites, never()).save(any());
        site.getFinds().add(find);
        ItemMeta meta = stack.getItemMeta(); meta.getPersistentDataContainer().remove(new NamespacedKey(plugin, "site_id")); stack.setItemMeta(meta);
        listener.onAnvilTake(take(stack, "Name"));
        verify(sites, never()).save(any());
    }

    @Test public void leavingBeforeScheduledRefreshStillPersistsTheRename() {
        ItemStack stack = create(); player.getInventory().setItem(0, stack);
        listener.onAnvilTake(take(stack, "Travel urn"));
        player.disconnect();
        server.getScheduler().performOneTick();
        assertEquals("Travel urn", find.getGivenName());
        verify(sites).save(site);
        assertEquals("Ancient pot", recovered.labelOf(player.getInventory().getItem(0)));
    }

    @Test public void joiningAndOpeningStorageRefreshesStaleSiteNamesInBagsAndChests() {
        ItemStack old = create();
        player.getInventory().setItem(0, old.clone()); player.getEnderChest().setItem(3, old.clone());
        site.setName("River camp");
        PlayerJoinEvent join = mock(PlayerJoinEvent.class); when(join.getPlayer()).thenReturn(player);
        listener.onJoin(join);
        assertEquals("River camp", recovered.siteNameOf(player.getInventory().getItem(0)));
        assertEquals("River camp", recovered.siteNameOf(player.getEnderChest().getItem(3)));
        Inventory chest = server.createInventory(null, 9);
        chest.setItem(1, old.clone()); chest.setItem(2, new ItemStack(Material.STONE, 7));
        player.getInventory().setItem(5, old.clone());
        InventoryOpenEvent open = mock(InventoryOpenEvent.class);
        when(open.getInventory()).thenReturn(chest); when(open.getPlayer()).thenReturn(player);
        listener.onOpen(open);
        assertEquals("River camp", recovered.siteNameOf(chest.getItem(1)));
        assertEquals("River camp", recovered.siteNameOf(player.getInventory().getItem(5)));
        assertEquals(7, chest.getItem(2).getAmount());
        verify(sites, never()).save(any());
    }

    @Test public void pickupRetitlesOnlyStaleFindsAndUpdatesTheDroppedStack() {
        ItemStack stack = create();
        Item dropped = mock(Item.class); when(dropped.getItemStack()).thenReturn(stack);
        EntityPickupItemEvent pickup = mock(EntityPickupItemEvent.class); when(pickup.getItem()).thenReturn(dropped);
        listener.onPickup(pickup);
        verify(dropped, never()).setItemStack(any());
        site.setName("River camp");
        listener.onPickup(pickup);
        verify(dropped).setItemStack(stack);
        assertEquals("River camp", recovered.siteNameOf(stack));
        listener.onPickup(pickup);
        verify(dropped, times(1)).setItemStack(stack);
    }

    @Test public void renameSurvivesACatalogRowRemovedByReload() {
        find.setGivenName("Family urn");
        ItemStack stack = create(); player.getInventory().setItem(0, stack);
        when(catalogs.artifact("pot")).thenReturn(null);
        // CatalogRegistry.materialOf never returns null: an unknown or missing id gets a placeholder material.
        when(catalogs.materialOf(null)).thenReturn(new FindMaterial("unknown", "Unknown", 1, Material.WHITE_STAINED_GLASS_PANE, List.of()));
        listener.onAnvilTake(take(stack, "Ancient pot"));
        assertEquals("Ancient pot", find.getGivenName());
        verify(sites).save(site);
        server.getScheduler().performOneTick();
        assertEquals("Ancient pot", recovered.labelOf(player.getInventory().getItem(0)));
        assertEquals(find.getId(), recovered.findIdOf(player.getInventory().getItem(0)));
    }

    @Test public void takingWithoutAnyRenameTextKeepsTheShownName() {
        ItemStack stack = create(); player.getInventory().setItem(0, stack);
        ItemMeta meta = stack.getItemMeta(); meta.setDisplayName(ChatColor.GOLD + "River vessel"); stack.setItemMeta(meta);
        listener.onAnvilTake(take(stack, null));
        assertEquals("River vessel", find.getGivenName());
    }

    private ItemStack create() { return recovered.create(template, site, find, player.getUniqueId(), "Good", false, catalogs); }
    private PrepareAnvilEvent preview(ItemStack left, ItemStack result, String name) {
        PrepareAnvilEvent event = mock(PrepareAnvilEvent.class);
        AnvilInventory inventory = mock(AnvilInventory.class); AnvilView view = mock(AnvilView.class);
        when(event.getInventory()).thenReturn(inventory); when(inventory.getItem(0)).thenReturn(left);
        when(event.getResult()).thenReturn(result); when(event.getView()).thenReturn(view); when(view.getRenameText()).thenReturn(name);
        return event;
    }
    private InventoryClickEvent take(ItemStack result, String name) {
        InventoryClickEvent event = mock(InventoryClickEvent.class); AnvilView view = mock(AnvilView.class);
        when(event.getRawSlot()).thenReturn(2); when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view); when(view.getRenameText()).thenReturn(name); when(event.getCurrentItem()).thenReturn(result);
        return event;
    }
}
