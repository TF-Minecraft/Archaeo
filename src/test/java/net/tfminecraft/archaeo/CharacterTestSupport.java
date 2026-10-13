package net.tfminecraft.archaeo;

import net.tfminecraft.archaeo.model.*;
import net.tfminecraft.archaeo.site.SiteRepository;
import net.tfminecraft.tlibs.character.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.mockbukkit.mockbukkit.*;
import java.util.*;

/** Existing fixtures model one character per account; isolation tests select distinct refs. */
public final class CharacterTestSupport {
    public static final Map<UUID,CharacterSession> sessions=new HashMap<>();
    public static final Map<UUID,Optional<CharacterRef>> heirs=new HashMap<>();
    public static ServerMock mock(){return install(MockBukkit.mock());}
    public static <T extends ServerMock> T mock(T server){return install(MockBukkit.mock(server));}
    private static <T extends ServerMock> T install(T server){sessions.clear();heirs.clear();var plugin=MockBukkit.createMockPlugin("TLibs");
        server.getServicesManager().register(CharacterIdentity.class,new CharacterIdentity(){
            public Optional<CharacterSession> currentSession(Player player){return Optional.ofNullable(sessions.computeIfAbsent(player.getUniqueId(),id->new CharacterSession(new CharacterRef(id,id),1)));}
            public boolean isCurrent(CharacterSession session){return session!=null&&session.equals(sessions.get(session.ref().ownerId()));}
            public Optional<CharacterInfo> info(UUID id){CharacterRef ref=sessions.values().stream().map(CharacterSession::ref).filter(r->r.characterId().equals(id)).findFirst().orElseGet(()->heirs.values().stream().flatMap(Optional::stream).filter(r->r.characterId().equals(id)).findFirst().orElse(new CharacterRef(id,id)));String name=Bukkit.getOfflinePlayer(id).getName();return Optional.of(new CharacterInfo(ref,name==null?id.toString().substring(0,8):name,CharacterStatus.ALIVE,null,Set.of(),0,null));}
            public Optional<CharacterInfo> lastPlayed(UUID account){return info(account);}
            public Optional<List<CharacterInfo>> characters(UUID account){return Optional.of(List.of(info(account).orElseThrow()));}
            public List<CharacterInfo> findByName(String name){List<CharacterInfo> found=new ArrayList<>();for(var player:Bukkit.getOfflinePlayers())if(name.equalsIgnoreCase(player.getName()))found.add(info(player.getUniqueId()).orElseThrow());return found;}
            public String displayName(Player player){return player.getName();}
            public boolean legacyHeirsReady(){return true;}
            public Optional<CharacterRef> legacyHeir(UUID account){return heirs.getOrDefault(account,Optional.of(new CharacterRef(account,account)));}
        },plugin,ServicePriority.Highest);return server;
    }
    public static UUID actor(Site site,UUID id){if(id!=null)site.getActorRefs().putIfAbsent(id,new CharacterRef(id,id));return id;}
    public static void actors(Site site){if(site==null)return;actor(site,site.getDirector());site.getExcavators().forEach(id->actor(site,id));site.getWorkers().keySet().forEach(id->actor(site,id));site.allProspectSamples().keySet().forEach(id->actor(site,id));site.getProspectConfirmed().forEach(id->actor(site,id));}
    public static void establish(Site site,UUID id,int x,int z,int bx,int by,int bz){site.establish(actor(site,id),x,z,bx,by,bz);}
    public static void setDirector(Site site,UUID id){site.setDirector(actor(site,id));}
    public static boolean grantExcavator(Site site,UUID id){return site.grantExcavator(actor(site,id));}
    public static WorkerRecord worker(Site site,UUID id){return site.worker(actor(site,id));}
    public static void confirmProspect(Site site,UUID id){site.confirmProspect(actor(site,id));}
    public static boolean addProspectSample(Site site,UUID id,BlockCell cell){return site.addProspectSample(actor(site,id),cell);}
    public static void save(SiteRepository repository,Site site){actors(site);repository.save(site);}
    public static void commit(SiteRepository repository,Site site){actors(site);repository.commit(site);}
    public static void touch(SiteRepository repository,Site site){actors(site);repository.touch(site);}
}
