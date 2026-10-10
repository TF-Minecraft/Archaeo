package net.tfminecraft.archaeo.command;

import net.tfminecraft.archaeo.config.ArtifactTemplate;
import net.tfminecraft.archaeo.config.CatalogRegistry;
import net.tfminecraft.archaeo.config.ConservationSettings;
import net.tfminecraft.archaeo.config.PickSettings;
import net.tfminecraft.archaeo.item.RecoveredFindItem;
import net.tfminecraft.archaeo.model.BuriedFind;
import net.tfminecraft.archaeo.model.FindState;
import net.tfminecraft.archaeo.model.Site;
import net.tfminecraft.archaeo.site.SiteGenerator;
import net.tfminecraft.archaeo.site.SiteRepository;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ArtifactCommandTest {
    private final Map<String, ArtifactTemplate> artifacts = new LinkedHashMap<>();
    private final CatalogRegistry catalogs = mock(CatalogRegistry.class);
    private final SiteRepository sites = mock(SiteRepository.class);
    private final SiteGenerator generator = mock(SiteGenerator.class);
    private final RecoveredFindItem recoveredItem = mock(RecoveredFindItem.class);
    private final ArchaeoCommand command = new ArchaeoCommand(null, catalogs, generator, sites,
            null, null, null, null, null, null, null, null, null, null, null, null, null, null, recoveredItem);
    private final List<String> messages = new ArrayList<>();

    @Before
    public void catalog() {
        when(catalogs.artifacts()).thenReturn(artifacts);
        when(catalogs.staffPermission()).thenReturn("archaeo.customstaff");
    }

    @Test
    public void completesLiveCatalogIdsIncludingCustomIds() {
        artifacts.put("custom_find", null);
        artifacts.put("coin", null);
        CommandSender staff = sender(true);
        assertEquals(List.of("artifact"), command.onTabComplete(staff, null, "archaeo", new String[]{"give", "art"}));
        assertEquals(List.of("custom_find", "coin"), command.onTabComplete(staff, null, "archaeo",
                new String[]{"give", "artifact", "C"}));
        artifacts.remove("coin");
        assertEquals(List.of("custom_find"), command.onTabComplete(staff, null, "archaeo",
                new String[]{"give", "artifact", ""}));
    }

    @Test
    public void deniesCommandsAndCompletionWithoutConfiguredPermission() {
        CommandSender player = sender(false);
        assertTrue(command.onTabComplete(player, null, "archaeo", new String[]{"give", "artifact", ""}).isEmpty());
        assertTrue(command.onCommand(player, null, "archaeo", new String[]{"give", "artifact", "coin", "#1"}));
        assertEquals(List.of("You do not have permission to use Archaeo staff commands."), messages);
    }

    @Test
    public void requiresExplicitRuinAndRejectsExtraArguments() {
        CommandSender staff = sender(true);
        command.onCommand(staff, null, "archaeo", new String[]{"give", "artifact", "coin"});
        command.onCommand(staff, null, "archaeo", new String[]{"give", "artifact", "coin", "#1", "player", "extra"});
        assertEquals(List.of("Usage: /archaeo give artifact <artifact> <#serial> [player]",
                "Usage: /archaeo give artifact <artifact> <#serial> [player]"), messages);
    }

    @Test
    public void completesRuinSerialsIncludingClosedRuins() {
        Site first = new Site();
        first.setSerial(12);
        Site second = new Site();
        second.setSerial(23);
        when(sites.all()).thenReturn(List.of(first, second));
        assertEquals(List.of("#12", "#23"), command.onTabComplete(sender(true), null, "archaeo",
                new String[]{"give", "artifact", "coin", ""}));
        assertEquals(List.of("#12"), command.onTabComplete(sender(true), null, "archaeo",
                new String[]{"give", "artifact", "coin", "1"}));
    }

    @Test
    public void savesProvenanceBeforeGivingTheItem() {
        Delivery delivery = delivery();
        command.onCommand(delivery.player(), null, "archaeo", new String[]{"give", "artifact", "coin", "#12"});
        assertEquals(List.of(delivery.find()), delivery.site().getFinds());
        assertEquals(1, delivery.site().getRecoveredCount());
        var order = inOrder(sites, delivery.inventory());
        order.verify(sites).save(delivery.site());
        order.verify(delivery.inventory()).addItem(delivery.stack());
    }

    @Test
    public void doesNotGiveAnItemOrKeepTheNewRowWhenSavingFails() {
        Delivery delivery = delivery();
        doThrow(new IllegalStateException("Disk full")).when(sites).save(delivery.site());
        command.onCommand(delivery.player(), null, "archaeo", new String[]{"give", "artifact", "coin", "#12"});
        assertTrue(delivery.site().getFinds().isEmpty());
        assertEquals(0, delivery.site().getRecoveredCount());
        verifyNoInteractions(delivery.inventory());
        verify(delivery.player()).sendMessage("Could not give the artifact: Disk full");
    }

    private Delivery delivery() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack stack = mock(ItemStack.class);
        UUID recipient = UUID.randomUUID();
        when(player.hasPermission(catalogs.staffPermission())).thenReturn(true);
        when(player.getUniqueId()).thenReturn(recipient);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.addItem(stack)).thenReturn(new java.util.HashMap<>());
        Site site = new Site();
        site.setId(UUID.randomUUID());
        site.setSerial(12);
        site.setName("Test ruin");
        ArtifactTemplate template = mock(ArtifactTemplate.class);
        when(template.displayName()).thenReturn("Coin");
        when(catalogs.artifact("coin")).thenReturn(template);
        when(sites.findBySerial(12)).thenReturn(Optional.of(site));
        BuriedFind find = new BuriedFind();
        find.setId(UUID.randomUUID());
        find.setFindNumber(1);
        find.setState(FindState.RECOVERED);
        when(generator.createStaffRecoveredFind(site, template, recipient)).thenReturn(find);
        PickSettings pick = mock(PickSettings.class);
        when(catalogs.pick()).thenReturn(pick);
        when(pick.conservation()).thenReturn(ConservationSettings.defaults());
        when(recoveredItem.create(eq(template), eq(site), eq(find), eq(recipient), anyString(), eq(false), eq(catalogs)))
                .thenReturn(stack);
        return new Delivery(player, inventory, stack, site, find);
    }

    private record Delivery(Player player, PlayerInventory inventory, ItemStack stack, Site site, BuriedFind find) {}

    private CommandSender sender(boolean allowed) {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(catalogs.staffPermission())).thenReturn(allowed);
        doAnswer(call -> { messages.add(call.getArgument(0)); return null; }).when(sender).sendMessage(anyString());
        return sender;
    }
}
