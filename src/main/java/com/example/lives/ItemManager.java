package com.example.lives;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

public class ItemManager {

    private final LivesPlugin plugin;
    private final NamespacedKey typeKey;
    private final NamespacedKey lifeRecipeKey;
    private final NamespacedKey reviveRecipeKey;

    public ItemManager(LivesPlugin plugin) {
        this.plugin = plugin;
        this.typeKey = new NamespacedKey(plugin, "item_type");
        this.lifeRecipeKey = new NamespacedKey(plugin, "life_item");
        this.reviveRecipeKey = new NamespacedKey(plugin, "revive_token");
    }

    public ItemStack createLifeItem(int amount) {
        return build(Material.HEART_OF_THE_SEA, "life", ChatColor.GREEN + "" + ChatColor.BOLD + "Extra Life", amount,
                ChatColor.GRAY + "Right-click to gain one life.",
                ChatColor.DARK_GRAY + "(Cannot exceed 3 lives)");
    }

    public ItemStack createReviveItem(int amount) {
        return build(Material.NETHER_STAR, "revive", ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Revive Token", amount,
                ChatColor.GRAY + "Hold this and run " + ChatColor.WHITE + "/revive <player>",
                ChatColor.GRAY + "to bring back a banned player.");
    }

    private ItemStack build(Material mat, String type, String name, int amount, String... lore) {
        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type);

        // Enchant glint (looked up by key so it works across versions)
        Enchantment glint = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
        if (glint != null) {
            meta.addEnchant(glint, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private String typeOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
    }

    public boolean isLifeItem(ItemStack item) {
        return "life".equals(typeOf(item));
    }

    public boolean isReviveItem(ItemStack item) {
        return "revive".equals(typeOf(item));
    }

    public void registerRecipes() {
        if (!plugin.getConfig().getBoolean("recipes.enabled", true)) return;

        // Extra Life:  D A D / A N A / D A D
        ShapedRecipe life = new ShapedRecipe(lifeRecipeKey, createLifeItem(1));
        life.shape("DAD", "ANA", "DAD");
        life.setIngredient('D', Material.DIAMOND_BLOCK);
        life.setIngredient('A', Material.GOLDEN_APPLE);
        life.setIngredient('N', Material.NETHER_STAR);
        Bukkit.addRecipe(life);

        // Revive Token:  N D N / D S D / N D N
        ShapedRecipe revive = new ShapedRecipe(reviveRecipeKey, createReviveItem(1));
        revive.shape("NDN", "DSD", "NDN");
        revive.setIngredient('N', Material.NETHERITE_INGOT);
        revive.setIngredient('D', Material.DIAMOND_BLOCK);
        revive.setIngredient('S', Material.NETHER_STAR);
        Bukkit.addRecipe(revive);
    }

    public void unregisterRecipes() {
        Bukkit.removeRecipe(lifeRecipeKey);
        Bukkit.removeRecipe(reviveRecipeKey);
    }
}
