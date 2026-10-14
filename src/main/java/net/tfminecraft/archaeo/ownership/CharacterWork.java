package net.tfminecraft.archaeo.ownership;

import net.tfminecraft.tlibs.character.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.plugin.Plugin;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Immutable session tokens for account-routed work; outgoing cleanup does not require currentness. */
public final class CharacterWork implements Listener {
    private static final Map<Plugin,List<CharacterWork>> pending=new WeakHashMap<>();
    private final Map<UUID,CharacterSession> tokens=new ConcurrentHashMap<>();
    private final Consumer<Player> cleanup;
    public CharacterWork(Plugin plugin,Consumer<Player> cleanup){this.cleanup=cleanup;pending.computeIfAbsent(plugin,ignored->new ArrayList<>()).add(this);}
    public static void start(Plugin plugin){List<CharacterWork> listeners=pending.remove(plugin);if(listeners!=null)listeners.forEach(work->plugin.getServer().getPluginManager().registerEvents(work,plugin));}
    public CharacterSession capture(Player player){CharacterSession token=CharacterActors.session(player).orElseThrow(()->new IllegalStateException("A current character is required"));tokens.put(player.getUniqueId(),token);return token;}
    public CharacterSession token(Player player){return tokens.get(player.getUniqueId());}
    public boolean matches(Player player,CharacterSession token){return token!=null&&token.equals(tokens.get(player.getUniqueId()));}
    public boolean current(Player player){return CharacterActors.current(token(player),player);}
    public void forget(Player player){tokens.remove(player.getUniqueId());}
    public void cancelCaptured(Player player,CharacterSession token){if(matches(player,token)){cleanup.accept(player);tokens.remove(player.getUniqueId(),token);}}
    @EventHandler public void ended(CharacterSessionEndEvent event){if(event.getSession().ref().ownerId().equals(event.getPlayer().getUniqueId()))cancelCaptured(event.getPlayer(),event.getSession());}
}
