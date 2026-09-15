package com.nowko.archeology.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Fired once a player recovers an archaeology find, after the item has
 * already been awarded to them.
 */
public class FindRecoveredEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ItemStack find;

    public FindRecoveredEvent(Player player, ItemStack find) {
        this.player = player;
        this.find = find;
    }

    public Player getPlayer() {
        return player;
    }

    public ItemStack getFind() {
        return find;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
