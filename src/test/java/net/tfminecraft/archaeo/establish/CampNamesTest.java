package net.tfminecraft.archaeo.establish;

import net.tfminecraft.archaeo.model.Site;
import org.bukkit.OfflinePlayer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CampNamesTest {
    private ServerMock server;

    @Before public void setUp() { server = MockBukkit.mock(); }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test public void dossiersWithoutADirectorListOnlyTheirExcavatorsOnce() {
        Site site = new Site(); UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        site.getExcavators().addAll(List.of(first, second, first)); // Hand-edited YAML may repeat an entry.
        assertEquals(List.of(first, second), CampNames.roster(site));
    }

    @Test public void unknownAccountsShowAShortIdAndNamelessProfilesAreNeverMatchedByName() {
        UUID stranger = UUID.randomUUID(); OfflinePlayer nameless = nameless(stranger);
        assertEquals(stranger.toString().substring(0, 8), CampNames.of(null, stranger));
        when(nameless.getName()).thenReturn("Wanderer"); assertEquals("Wanderer", CampNames.of(null, stranger)); // Cached from an earlier visit.
        when(nameless.getName()).thenReturn(null);
        server.addPlayer("Keeper").disconnect();
        assertEquals("Keeper", CampNames.known(" keeper ").getName());
        assertNull(CampNames.known("Nobody"));
    }

    /** Paper returns a null name for accounts it never cached; MockBukkit invents one, so register the profile. */
    private OfflinePlayer nameless(UUID id) {
        OfflinePlayer profile = mock(OfflinePlayer.class); when(profile.getUniqueId()).thenReturn(id);
        server.getPlayerList().addOfflinePlayer(profile); return profile;
    }
}
