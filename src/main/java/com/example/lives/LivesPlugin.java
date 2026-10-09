package com.example.lives;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LivesPlugin extends JavaPlugin {

    public static final int MAX_LIVES = 3;
    public static final String PREFIX = ChatColor.GRAY + "[" + ChatColor.RED + "Lives" + ChatColor.GRAY + "] " + ChatColor.RESET;
    public static final String BAN_REASON = ChatColor.RED + "You have run out of lives!";

    private File dataFile;
    private FileConfiguration data;
    private ItemManager items;
    private Attribute maxHealthAttribute;
    private final Map<Integer, Team> teams = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();
        maxHealthAttribute = resolveMaxHealthAttribute();

        items = new ItemManager(this);
        items.registerRecipes();

        setupTeams();

        getServer().getPluginManager().registerEvents(new LivesListener(this), this);

        LivesCommand executor = new LivesCommand(this);
        getCommand("lives").setExecutor(executor);
        getCommand("lives").setTabCompleter(executor);
        getCommand("revive").setExecutor(executor);
        getCommand("revive").setTabCompleter(executor);

        for (Player p : Bukkit.getOnlinePlayers()) {
            recordPlayer(p);
            applyState(p);
        }
    }

    @Override
    public void onDisable() {
        if (items != null) items.unregisterRecipes();
        for (Team t : teams.values()) {
            try {
                t.unregister();
            } catch (IllegalStateException ignored) {
            }
        }
        saveData();
    }

    // ------------------------------------------------------------------ data

    private void loadData() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Could not save data.yml: " + e.getMessage());
        }
    }

    public ItemManager getItems() {
        return items;
    }

    // ----------------------------------------------------------- life state

    public int getLives(UUID id) {
        return data.getInt("players." + id + ".lives", MAX_LIVES);
    }

    public boolean isLosingLives() {
        return data.getBoolean("settings.lose-lives-on-death", true);
    }

    public void setLosingLives(boolean value) {
        data.set("settings.lose-lives-on-death", value);
        saveData();
    }

    public int getHearts(int lives) {
        int key = Math.max(1, Math.min(MAX_LIVES, lives));
        int def = key == 3 ? 10 : key == 2 ? 8 : 4;
        return getConfig().getInt("hearts." + key, def);
    }

    public int getRevivedLives() {
        return Math.max(1, Math.min(MAX_LIVES, getConfig().getInt("revived-lives", 1)));
    }

    /** Remembers a player's name so offline players can be targeted by name. */
    public void recordPlayer(Player p) {
        data.set("players." + p.getUniqueId() + ".name", p.getName());
        data.set("names." + p.getName().toLowerCase(), p.getUniqueId().toString());
        saveData();
    }

    /** Sets a player's lives (0-3). At 0 they are banned, going up from 0 pardons them. */
    public void setLives(UUID id, String name, int lives) {
        lives = Math.max(0, Math.min(MAX_LIVES, lives));
        int old = getLives(id);

        data.set("players." + id + ".lives", lives);
        data.set("players." + id + ".name", name);
        data.set("names." + name.toLowerCase(), id.toString());
        saveData();

        Player online = Bukkit.getPlayer(id);
        if (online != null) applyState(online);

        if (lives <= 0) {
            ban(name, online);
        } else if (old <= 0) {
            unban(name);
        }
    }

    public void revive(UUID id, String name) {
        setLives(id, name, getRevivedLives());
    }

    public boolean isEliminated(UUID id) {
        return getLives(id) <= 0;
    }

    /** Finds an online or previously-seen player by name. Returns null if never seen. */
    public OfflinePlayer findPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        String uuid = data.getString("names." + name.toLowerCase());
        if (uuid == null) return null;
        try {
            return Bukkit.getOfflinePlayer(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String storedName(UUID id, String fallback) {
        return data.getString("players." + id + ".name", fallback);
    }

    public List<String> getEliminatedNames() {
        List<String> out = new ArrayList<>();
        ConfigurationSection sec = data.getConfigurationSection("players");
        if (sec == null) return out;
        for (String key : sec.getKeys(false)) {
            if (sec.getInt(key + ".lives", MAX_LIVES) <= 0) {
                String n = sec.getString(key + ".name");
                if (n != null) out.add(n);
            }
        }
        return out;
    }

    public void handleDeath(Player p) {
        UUID id = p.getUniqueId();
        int lives = getLives(id) - 1;
        setLives(id, p.getName(), lives);

        if (lives > 0) {
            p.sendMessage(PREFIX + ChatColor.RED + "You lost a life! You now have "
                    + colorFor(lives) + lives + ChatColor.RED + " " + (lives == 1 ? "life" : "lives")
                    + " left and " + getHearts(lives) + " hearts.");
        } else {
            Bukkit.broadcastMessage(PREFIX + ChatColor.DARK_RED + p.getName() + " has lost their final life and been eliminated!");
        }
    }

    // ----------------------------------------------------------------- bans

    @SuppressWarnings({"deprecation", "rawtypes"})
    private void ban(String name, Player online) {
        BanList list = Bukkit.getBanList(BanList.Type.NAME);
        list.addBan(name, BAN_REASON, null, "Lives");
        if (online != null) {
            Bukkit.getScheduler().runTask(this, () -> online.kickPlayer(BAN_REASON));
        }
    }

    @SuppressWarnings({"deprecation", "rawtypes"})
    private void unban(String name) {
        Bukkit.getBanList(BanList.Type.NAME).pardon(name);
    }

    // -------------------------------------------------- health + name colour

    public static ChatColor colorFor(int lives) {
        switch (lives) {
            case 3:
                return ChatColor.GREEN;
            case 2:
                return ChatColor.GOLD; // closest to orange in Minecraft
            case 1:
                return ChatColor.RED;
            default:
                return ChatColor.BLACK;
        }
    }

    private void setupTeams() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        for (int i = 0; i <= MAX_LIVES; i++) {
            String name = "lives_" + i;
            Team t = sb.getTeam(name);
            if (t == null) t = sb.registerNewTeam(name);
            t.setColor(colorFor(i));
            teams.put(i, t);
        }
    }

    public void applyState(Player p) {
        int lives = getLives(p.getUniqueId());

        AttributeInstance attr = p.getAttribute(maxHealthAttribute);
        if (attr != null) {
            double max = getHearts(lives) * 2.0;
            attr.setBaseValue(max);
            if (p.getHealth() > max) p.setHealth(max);
        }

        ChatColor color = colorFor(lives);
        for (Team t : teams.values()) t.removeEntry(p.getName());
        teams.get(Math.max(0, Math.min(MAX_LIVES, lives))).addEntry(p.getName());
        p.setPlayerListName(color + p.getName());
        p.setDisplayName(color + p.getName() + ChatColor.RESET);
    }

    public void healToFull(Player p) {
        AttributeInstance attr = p.getAttribute(maxHealthAttribute);
        if (attr != null) p.setHealth(attr.getValue());
    }

    /** Attribute was renamed GENERIC_MAX_HEALTH -> MAX_HEALTH in 1.21.3, so look it up by name. */
    private Attribute resolveMaxHealthAttribute() {
        for (String n : new String[]{"MAX_HEALTH", "GENERIC_MAX_HEALTH"}) {
            try {
                return (Attribute) Attribute.class.getField(n).get(null);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        throw new IllegalStateException("Could not find the max health attribute on this server version.");
    }
}
