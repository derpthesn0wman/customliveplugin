package com.example.lives;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class LivesListener implements Listener {

    private final LivesPlugin plugin;

    public LivesListener(LivesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        // If they got in with 0 lives, an admin pardoned them manually - give them a fresh start.
        if (plugin.isEliminated(p.getUniqueId())) {
            plugin.revive(p.getUniqueId(), p.getName());
            p.sendMessage(LivesPlugin.PREFIX + ChatColor.YELLOW + "You were unbanned and have been given "
                    + plugin.getRevivedLives() + " life.");
        }

        plugin.recordPlayer(p);
        plugin.applyState(p);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (!plugin.isLosingLives()) return;
        plugin.handleDeath(e.getEntity());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.applyState(p);
            plugin.healToFull(p);
        });
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = e.getItem();
        if (item == null) return;
        Player p = e.getPlayer();

        if (plugin.getItems().isLifeItem(item)) {
            e.setCancelled(true);
            int lives = plugin.getLives(p.getUniqueId());
            if (lives >= LivesPlugin.MAX_LIVES) {
                p.sendMessage(LivesPlugin.PREFIX + ChatColor.RED + "You already have the maximum of "
                        + LivesPlugin.MAX_LIVES + " lives.");
                return;
            }
            item.setAmount(item.getAmount() - 1);
            plugin.setLives(p.getUniqueId(), p.getName(), lives + 1);
            p.sendMessage(LivesPlugin.PREFIX + ChatColor.GREEN + "You gained a life! You now have "
                    + (lives + 1) + " lives and " + plugin.getHearts(lives + 1) + " hearts.");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        } else if (plugin.getItems().isReviveItem(item)) {
            e.setCancelled(true);
            p.sendMessage(LivesPlugin.PREFIX + ChatColor.LIGHT_PURPLE
                    + "Hold the Revive Token and run /revive <player> to bring someone back.");
        }
    }
}
