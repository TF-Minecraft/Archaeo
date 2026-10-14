package net.tfminecraft.archaeo.ownership;

import net.tfminecraft.archaeo.CharacterTestSupport;
import net.tfminecraft.archaeo.model.*;
import net.tfminecraft.tlibs.character.*;
import org.bukkit.plugin.ServicePriority;
import org.junit.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CharacterBoundaryTest {
    ServerMock server;PlayerMock player;CharacterRef a,b;CharacterSession first,second;
    @Before public void setup(){server=CharacterTestSupport.mock();player=server.addPlayer();a=new CharacterRef(player.getUniqueId(),UUID.randomUUID());b=new CharacterRef(player.getUniqueId(),UUID.randomUUID());first=new CharacterSession(a,1);second=new CharacterSession(b,2);CharacterTestSupport.sessions.put(player.getUniqueId(),first);}
    @After public void cleanup(){MockBukkit.unmock();}
    @Test public void accountRoutingDoesNotGrantAnotherCharacterDirectorWorkerOrProspectAuthority(){
        Site site=new Site();site.setStatus(SiteStatus.ESTABLISHED);site.getActorRefs().put(a.characterId(),a);site.getActorRefs().put(b.characterId(),b);site.setDirector(a.characterId());site.grantExcavator(a.characterId());site.confirmProspect(a.characterId());assertEquals(a.characterId(),CharacterActors.id(site,player));assertTrue(site.isDirector(CharacterActors.id(site,player)));CharacterTestSupport.sessions.put(player.getUniqueId(),second);assertEquals(b.characterId(),CharacterActors.id(site,player));assertFalse(site.isDirector(CharacterActors.id(site,player)));assertFalse(site.mayWork(CharacterActors.id(site,player)));assertFalse(site.isProspectConfirmed(CharacterActors.id(site,player)));site.setOwnershipHeld(true);assertNull(CharacterActors.id(site,player));
    }
    @Test public void staleAndWrongAccountProviderSessionsCannotAuthorizeMenusOrRememberActors(){
        var previous=server.getServicesManager().load(CharacterIdentity.class);server.getServicesManager().unregister(CharacterIdentity.class,previous);var identity=mock(CharacterIdentity.class);server.getServicesManager().register(CharacterIdentity.class,identity,MockBukkit.createMockPlugin(),ServicePriority.Highest);when(identity.currentSession(player)).thenReturn(Optional.of(first));when(identity.isCurrent(first)).thenReturn(false);assertTrue(CharacterActors.session(player).isEmpty());var inventory=server.createInventory(null,9);CharacterActors.open(player,inventory);assertFalse(CharacterActors.menuCurrent(player,inventory));assertThrows(IllegalStateException.class,()->CharacterActors.remember(new Site(),player));var wrong=new CharacterSession(new CharacterRef(UUID.randomUUID(),a.characterId()),3);when(identity.currentSession(player)).thenReturn(Optional.of(wrong));when(identity.isCurrent(wrong)).thenReturn(true);assertNull(CharacterActors.id(player));assertFalse(CharacterActors.current(wrong,player));
    }
    @Test public void outgoingAlreadyNoncurrentCleanupRunsOnceAndLateEndCannotCancelNewCharacterWork(){
        var plugin=MockBukkit.createMockPlugin();AtomicInteger cleaned=new AtomicInteger();var work=new CharacterWork(plugin,p->cleaned.incrementAndGet());CharacterWork.start(plugin);work.capture(player);CharacterActors.open(player,server.createInventory(null,9));CharacterTestSupport.sessions.put(player.getUniqueId(),second);server.getPluginManager().callEvent(new CharacterSessionEndEvent(player,first,CharacterSessionEndEvent.Reason.SWITCH));assertEquals(1,cleaned.get());work.capture(player);server.getPluginManager().callEvent(new CharacterSessionEndEvent(player,first,CharacterSessionEndEvent.Reason.QUIT));assertTrue(work.current(player));assertEquals(1,cleaned.get());CharacterActors.endMenu(player,first);assertNull(player.getOpenInventory().getTopInventory());
    }
    @Test public void capturedMenuRejectsSwitchButPreservesANewerOpenMenu(){
        var old=server.createInventory(null,9);CharacterActors.open(player,old);assertTrue(CharacterActors.menuCurrent(player,old));CharacterTestSupport.sessions.put(player.getUniqueId(),second);assertFalse(CharacterActors.menuCurrent(player,old));var next=server.createInventory(null,9);CharacterActors.open(player,next);CharacterActors.endMenu(player,first);assertSame(next,player.getOpenInventory().getTopInventory());assertTrue(CharacterActors.menuCurrent(player,next));
    }
    @Test public void historicalDirectorRoleDoesNotCreateASecondDisplayedDirector(){
        Site site=new Site();site.setDirector(a.characterId());site.grantExcavator(a.characterId());site.grantExcavator(b.characterId());site.worker(a.characterId()).setRole(SiteRole.DIRECTOR);site.worker(b.characterId()).setBlocksRemoved(17);site.setDirector(b.characterId());assertEquals(SiteRole.defaultRole(),site.roleOf(a.characterId()));assertEquals(SiteRole.DIRECTOR,site.roleOf(b.characterId()));assertEquals(SiteRole.DIRECTOR,site.workerRecord(a.characterId()).getRole());assertEquals(17,site.workerRecord(b.characterId()).getBlocksRemoved());
    }
}
