package com.example.lives;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class LivesCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList("check", "set", "add", "remove", "toggle", "give", "revive", "reload");
    private static final String P = LivesPlugin.PREFIX;

    private final LivesPlugin plugin;

    public LivesCommand(LivesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (c.getName().equalsIgnoreCase("revive")) return itemRevive(s, a);

        if (a.length == 0) return check(s, null);

        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "check":
                return check(s, a.length > 1 ? a[1] : null);
            case "set":
            case "add":
            case "remove":
                return modify(s, a);
            case "toggle":
                return toggle(s, a);
            case "give":
                return give(s, a);
            case "revive":
                return adminRevive(s, a);
            case "reload":
                if (!admin(s)) return true;
                plugin.reloadConfig();
                s.sendMessage(P + ChatColor.GREEN + "Config reloaded.");
                return true;
            default:
                s.sendMessage(P + ChatColor.RED + "Usage: /lives [check|set|add|remove|toggle|give|revive|reload]");
                return true;
        }
    }

    // ------------------------------------------------------------ subcommands

    private boolean check(CommandSender s, String name) {
        if (!s.hasPermission("lives.check")) {
            s.sendMessage(P + ChatColor.RED + "No permission.");
            return true;
        }
        OfflinePlayer target;
        String display;
        if (name == null) {
            if (!(s instanceof Player)) {
                s.sendMessage(P + ChatColor.RED + "Usage: /lives check <player>");
                return true;
            }
            target = (Player) s;
            display = target.getName();
        } else {
            target = plugin.findPlayer(name);
            if (target == null) {
                s.sendMessage(P + ChatColor.RED + "That player has never joined.");
                return true;
            }
            display = plugin.storedName(target.getUniqueId(), name);
        }
        int lives = plugin.getLives(target.getUniqueId());
        s.sendMessage(P + LivesPlugin.colorFor(lives) + display + ChatColor.GRAY + " has "
                + ChatColor.WHITE + lives + ChatColor.GRAY + (lives == 1 ? " life" : " lives")
                + (lives > 0 ? " (" + plugin.getHearts(lives) + " hearts)" : " (eliminated)") + ".");
        return true;
    }

    private boolean modify(CommandSender s, String[] a) {
        if (!admin(s)) return true;
        if (a.length < 3) {
            s.sendMessage(P + ChatColor.RED + "Usage: /lives " + a[0] + " <player> <amount>");
            return true;
        }
        OfflinePlayer target = plugin.findPlayer(a[1]);
        if (target == null) {
            s.sendMessage(P + ChatColor.RED + "That player has never joined.");
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(a[2]);
        } catch (NumberFormatException e) {
            s.sendMessage(P + ChatColor.RED + "'" + a[2] + "' is not a number.");
            return true;
        }

        int current = plugin.getLives(target.getUniqueId());
        int result;
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "set":
                result = amount;
                break;
            case "add":
                result = current + amount;
                break;
            default:
                result = current - amount;
                break;
        }

        String name = plugin.storedName(target.getUniqueId(), a[1]);
        plugin.setLives(target.getUniqueId(), name, result);
        int now = plugin.getLives(target.getUniqueId());
        s.sendMessage(P + ChatColor.GREEN + name + " now has " + now + (now == 1 ? " life" : " lives")
                + (now == 0 ? " and has been banned." : "."));
        return true;
    }

    private boolean toggle(CommandSender s, String[] a) {
        if (!admin(s)) return true;
        boolean value;
        if (a.length > 1) {
            String arg = a[1].toLowerCase(Locale.ROOT);
            if (arg.equals("on") || arg.equals("true")) value = true;
            else if (arg.equals("off") || arg.equals("false")) value = false;
            else {
                s.sendMessage(P + ChatColor.RED + "Usage: /lives toggle [on|off]");
                return true;
            }
        } else {
            value = !plugin.isLosingLives();
        }
        plugin.setLosingLives(value);
        Bukkit.broadcastMessage(P + (value
                ? ChatColor.RED + "Losing lives on death is now ON."
                : ChatColor.GREEN + "Losing lives on death is now OFF."));
        return true;
    }

    private boolean give(CommandSender s, String[] a) {
        if (!admin(s)) return true;
        if (a.length < 3) {
            s.sendMessage(P + ChatColor.RED + "Usage: /lives give <player> <life|revive> [amount]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(a[1]);
        if (target == null) {
            s.sendMessage(P + ChatColor.RED + "That player is not online.");
            return true;
        }
        int amount = 1;
        if (a.length > 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(a[3])));
            } catch (NumberFormatException e) {
                s.sendMessage(P + ChatColor.RED + "'" + a[3] + "' is not a number.");
                return true;
            }
        }
        ItemStack item;
        String type = a[2].toLowerCase(Locale.ROOT);
        if (type.equals("life")) item = plugin.getItems().createLifeItem(amount);
        else if (type.equals("revive")) item = plugin.getItems().createReviveItem(amount);
        else {
            s.sendMessage(P + ChatColor.RED + "Item must be 'life' or 'revive'.");
            return true;
        }
        target.getInventory().addItem(item).values()
                .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        s.sendMessage(P + ChatColor.GREEN + "Gave " + amount + "x " + type + " item to " + target.getName() + ".");
        return true;
    }

    private boolean adminRevive(CommandSender s, String[] a) {
        if (!admin(s)) return true;
        if (a.length < 2) {
            s.sendMessage(P + ChatColor.RED + "Usage: /lives revive <player>");
            return true;
        }
        return doRevive(s, a[1], null);
    }

    /** /revive <player> - requires a Revive Token in the main hand. */
    private boolean itemRevive(CommandSender s, String[] a) {
        if (!(s instanceof Player)) {
            s.sendMessage(P + ChatColor.RED + "Players only. Use /lives revive <player> from console.");
            return true;
        }
        Player p = (Player) s;
        if (!p.hasPermission("lives.revive")) {
            p.sendMessage(P + ChatColor.RED + "No permission.");
            return true;
        }
        if (a.length < 1) {
            p.sendMessage(P + ChatColor.RED + "Usage: /revive <player>");
            return true;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!plugin.getItems().isReviveItem(hand)) {
            p.sendMessage(P + ChatColor.RED + "You must hold a Revive Token in your main hand.");
            return true;
        }
        return doRevive(p, a[0], hand);
    }

    private boolean doRevive(CommandSender s, String name, ItemStack tokenToConsume) {
        OfflinePlayer target = plugin.findPlayer(name);
        if (target == null) {
            s.sendMessage(P + ChatColor.RED + "That player has never joined.");
            return true;
        }
        if (!plugin.isEliminated(target.getUniqueId())) {
            s.sendMessage(P + ChatColor.RED + "That player has not been eliminated.");
            return true;
        }
        if (tokenToConsume != null) tokenToConsume.setAmount(tokenToConsume.getAmount() - 1);

        String realName = plugin.storedName(target.getUniqueId(), name);
        plugin.revive(target.getUniqueId(), realName);
        Bukkit.broadcastMessage(P + ChatColor.LIGHT_PURPLE + realName + " has been revived by " + s.getName() + "!");
        if (s instanceof Player) {
            Player p = (Player) s;
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f);
        }
        return true;
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("lives.admin")) return true;
        s.sendMessage(P + ChatColor.RED + "You don't have permission to do that.");
        return false;
    }

    // ------------------------------------------------------------ tab complete

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();

        if (c.getName().equalsIgnoreCase("revive")) {
            if (a.length == 1) out.addAll(plugin.getEliminatedNames());
            return filter(out, a[a.length - 1]);
        }

        if (a.length == 1) {
            out.addAll(SUBS);
        } else if (a.length == 2) {
            String sub = a[0].toLowerCase(Locale.ROOT);
            if (sub.equals("toggle")) {
                out.addAll(Arrays.asList("on", "off"));
            } else if (sub.equals("revive")) {
                out.addAll(plugin.getEliminatedNames());
            } else if (!sub.equals("reload")) {
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            }
        } else if (a.length == 3) {
            String sub = a[0].toLowerCase(Locale.ROOT);
            if (sub.equals("set")) out.addAll(Arrays.asList("0", "1", "2", "3"));
            if (sub.equals("give")) out.addAll(Arrays.asList("life", "revive"));
        }
        return filter(out, a[a.length - 1]);
    }

    private List<String> filter(List<String> in, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return in.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(p)).collect(Collectors.toList());
    }
}
