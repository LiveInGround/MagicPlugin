package com.elmakers.mine.bukkit.utility.platform;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.block.Skull;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.elmakers.mine.bukkit.utility.CurrencyAmount;

public interface InventoryUtils {
    CurrencyAmount getCurrencyAmount(ItemStack item);

    boolean saveTagsToItem(ConfigurationSection tags, ItemStack item);

    boolean saveTagsToItem(Map<String, Object> tags, ItemStack item);

    boolean configureSkillItem(ItemStack skillItem, String skillClass, boolean quickCast, ConfigurationSection skillConfig);

    boolean saveTagsToNBT(ConfigurationSection tags, Object node);

    boolean saveTagsToNBT(ConfigurationSection tags, Object node, Set<String> tagNames);

    boolean saveTagsToNBT(Map<String, Object> tags, Object node, Set<String> tagNames);

    boolean addTagsToNBT(Map<String, Object> tags, Object node);

    Object wrapInTag(Object value)
            throws IllegalAccessException, InvocationTargetException, InstantiationException;

    Set<String> getTagKeys(Object tag);

    Object getMetaObject(Object tag, String key);

    Object getTagValue(Object tag) throws IllegalAccessException, InvocationTargetException;

    ItemStack setSkullURL(ItemStack itemStack, String url);

    ItemStack setSkullURL(ItemStack itemStack, URL url, UUID id);

    ItemStack setSkullURL(ItemStack itemStack, URL url, UUID id, String name);

    ItemStack setSkullURLAndName(ItemStack itemStack, URL url, String ownerName, UUID id);

    String getSkullURL(ItemStack skull);

    boolean isSkull(ItemStack item);

    Object getSkullProfile(ItemMeta itemMeta);

    Object getSkullProfile(Skull state);

    boolean setSkullProfile(Skull state, Object data);

    boolean setSkullProfile(ItemMeta itemMeta, Object data);

    void wrapText(String text, Collection<String> list);

    void wrapText(String text, String prefix, Collection<String> list);

    void wrapText(String text, int maxLength, Collection<String> list);

    void wrapText(String text, String prefix, int maxLength, Collection<String> list);

    boolean hasItem(Inventory inventory, String itemName);

    ItemStack getItem(Inventory inventory, String itemName);

    void makeKeep(ItemStack itemStack);

    boolean isKeep(ItemStack itemStack);

    void applyAttributes(ItemStack item, ConfigurationSection attributeConfig, String slot);

    void applyEnchantments(ItemStack item, ConfigurationSection enchantConfig);

    boolean addEnchantments(ItemStack item, ConfigurationSection enchantConfig);

    String describeProperty(Object property);

    String describeProperty(Object property, int maxLength);

    @SuppressWarnings("EqualsReference")
    boolean isSameInstance(ItemStack one, ItemStack two);

    int getMapId(ItemStack mapItem);

    void setMapId(ItemStack mapItem, int id);

    ItemStack createMap(Material material, int id);

    void convertIntegers(Map<String, Object> m);
}
