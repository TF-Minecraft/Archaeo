package net.tfminecraft.archaeo.ownership;

import net.tfminecraft.archaeo.model.Site;
import net.tfminecraft.tlibs.character.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import java.util.*;

/** Account UUIDs route Bukkit objects; exact current refs authorize character work. */
public final class CharacterActors {
    private static final Map<Inventory,CharacterSession> menus=new WeakHashMap<>();
    private CharacterActors() {}
    public static Optional<CharacterSession> session(Player player){return player==null?Optional.empty():Characters.currentSession(player).filter(s->current(s,player));}
    public static boolean current(CharacterSession session,Player player){return session!=null&&player!=null&&session.ref().ownerId().equals(player.getUniqueId())&&Characters.isCurrent(session);}
    public static UUID id(Player player){return session(player).map(s->s.ref().characterId()).orElse(null);}
    public static UUID id(Site site,Player player){return session(player).filter(s->!site.isOwnershipHeld()&&s.ref().equals(site.getActorRefs().get(s.ref().characterId()))).map(s->s.ref().characterId()).orElse(null);}
    public static UUID remember(Site site,Player player){if(site.isOwnershipHeld())throw new IllegalStateException("Held dossier");CharacterRef ref=session(player).orElseThrow(()->new IllegalStateException("A current character is required")).ref();remember(site,ref);return ref.characterId();}
    public static void remember(Site site,CharacterRef ref){CharacterRef previous=site.getActorRefs().putIfAbsent(ref.characterId(),ref);if(previous!=null&&!previous.equals(ref))throw new IllegalStateException("Character account conflicts");}
    public static org.bukkit.inventory.InventoryView open(Player player,Inventory inventory){menus.put(inventory,session(player).orElse(null));return player.openInventory(inventory);}
    public static boolean menuCurrent(Player player,Inventory inventory){return menus.containsKey(inventory)&&current(menus.get(inventory),player);}
    public static void endMenu(Player player,CharacterSession session){Inventory inventory=player.getOpenInventory().getTopInventory();if(session.equals(menus.get(inventory)))player.closeInventory();}
    public static Map<String,Object> ref(CharacterRef ref){Map<String,Object> result=new LinkedHashMap<>();result.put("account",ref.ownerId().toString());result.put("character",ref.characterId().toString());return result;}
    public static CharacterRef ref(Object value){var map=SiteDocuments.map(value);return new CharacterRef(UUID.fromString((String)map.get("account")),UUID.fromString((String)map.get("character")));}
    public static String name(UUID character){return Characters.info(character).map(CharacterInfo::name).orElse(character.toString().substring(0,8));}
    public static Optional<CharacterRef> invited(String name){
        try{return Characters.info(UUID.fromString(name.trim())).map(CharacterInfo::ref);}catch(IllegalArgumentException ignored){}
        List<CharacterInfo> found=Characters.findByName(name.trim());if(found.size()==1)return Optional.of(found.getFirst().ref());if(!found.isEmpty())return Optional.empty();
        for(Player player:Bukkit.getOnlinePlayers())if(player.getName().equalsIgnoreCase(name.trim()))return session(player).map(CharacterSession::ref);
        return Optional.empty();
    }
    public static boolean supports(ClassLoader loader,boolean estate){try{Class<?> api=Class.forName("net.tfminecraft.tlibs.character.Characters",false,loader);api.getMethod("legacyHeirsReady");api.getMethod("legacyHeir",UUID.class);if(estate)for(String name:new String[]{"Estates","EstateParticipant","EstateDecision","Outcome","Holdings"})Class.forName("net.tfminecraft.tlibs.character.estate."+name,false,loader);return true;}catch(ReflectiveOperationException|LinkageError failure){return false;}}
}
