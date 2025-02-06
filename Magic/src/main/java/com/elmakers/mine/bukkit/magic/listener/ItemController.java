package com.elmakers.mine.bukkit.magic.listener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nullable;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.elmakers.mine.bukkit.api.magic.MageController;
import com.elmakers.mine.bukkit.configuration.MagicConfiguration;
import com.elmakers.mine.bukkit.item.ItemData;
import com.elmakers.mine.bukkit.utility.CompatibilityLib;
import com.elmakers.mine.bukkit.utility.ConfigurationUtils;
import com.elmakers.mine.bukkit.utility.StringUtils;

public class ItemController {
    private MageController controller;
    private final Set<String> itemKeys = new HashSet<>();
    private final Map<String, ItemData> items = new HashMap<>();
    private final Map<ItemStack, ItemData> itemsByStack = new HashMap<>();
    private final Map<Material, Map<Integer, ItemData>> replaceOnEquip = new HashMap<>();

    public ItemController(MageController controller) {
        this.controller = controller;
    }

    public void load(ConfigurationSection configuration) {
        // Need to clear cached items to prevent
        itemKeys.clear();
        items.clear();
        itemsByStack.clear();
        replaceOnEquip.clear();

        Set<String> itemKeys = configuration.getKeys(false);
        for (String itemKey : itemKeys) {
            ConfigurationSection itemConfig = configuration.getConfigurationSection(itemKey);
            if (itemConfig != null) {
                if (ConfigurationUtils.isEnabled(itemConfig)) {
                    itemConfig = MagicConfiguration.getKeyed(controller, itemConfig, "item", itemKey);
                    loadItem(itemKey, itemConfig);
                }
            } else {
                String itemString = configuration.getString(itemKey);
                if (itemString != null && !itemString.isEmpty()) {
                    loadItem(itemKey, itemString);
                } else {
                    controller.getLogger().warning("Improperly formatted item: " + itemKey);
                }
            }
        }
    }

    public void loadItem(String itemKey, String material) {
        ItemData magicItem = new ItemData(itemKey, material, controller);
        itemKeys.add(itemKey);
        items.put(itemKey, magicItem);
    }

    public void loadItem(String itemKey, ConfigurationSection configuration) {
        try {
            ItemData magicItem = new ItemData(itemKey, configuration, controller);
            itemKeys.add(itemKey);
            items.put(itemKey, magicItem);
            if (magicItem.isReplaceOnEquip()) {
                Material type = magicItem.getType();
                Map<Integer, ItemData> mapped = replaceOnEquip.get(type);
                if (mapped == null) {
                    mapped = new HashMap<>();
                    replaceOnEquip.put(type, mapped);
                }
                mapped.put(magicItem.getCustomModelData(), magicItem);
            }
        } catch (Throwable ex) {
            controller.getLogger().log(Level.WARNING, "An error occurred while processing the item: " + itemKey, ex);
        }
    }

    public void finalizeItems() {
        for (ItemData magicItem : items.values()) {
            magicItem.checkKey();
            try {
                ItemStack itemStack = magicItem.getOrCreateItemStack();
                itemsByStack.put(itemStack, magicItem);
            } catch (Exception ex) {
                controller.getLogger().log(Level.WARNING, "Invalid item in config: " + magicItem.getKey() + " (" + ex.getMessage() + ")");
            }
        }
    }

    public int getCount() {
        return items.size();
    }

    public Set<String> getKeys() {
        return itemKeys;
    }

    @Nullable
    public ItemData get(String key) {
        ItemData item = items.get(key);
        if (item == null) {
            String[] pieces = StringUtils.split(key, ':');
            if (pieces.length > 1) {
                ItemData baseItem = items.get(pieces[0]);
                if (baseItem != null && baseItem.getMaxDurability() > 0) {
                    try {
                        short damage = Short.parseShort(pieces[1]);
                        item = baseItem.createVariant(key, damage);
                        if (item != null) {
                            items.put(key, item);
                        }
                    } catch (Exception ex) {
                        return null;
                    }
                }
            }
        }
        return item;
    }

    @Nullable
    public ItemData get(ItemStack item) {
        return itemsByStack.get(item);
    }

    @Nullable
    public ItemData getOrCreate(String key) {
        ItemData data = get(key);
        if (data == null) {
            data = new ItemData(key, controller);
        }
        return data;
    }

    public void remove(String key) {
        ItemData existing = items.get(key);
        if (existing != null) {
            itemsByStack.remove(existing.getItemStack(1));
        }
        items.remove(key);
        itemKeys.remove(key);
    }

    public void updateOnEquip(ItemStack itemStack) {
        if (CompatibilityLib.getItemUtils().isEmpty(itemStack)) return;
        Map<Integer, ItemData> mapped = replaceOnEquip.get(itemStack.getType());
        if (mapped == null) return;
        int customData = CompatibilityLib.getItemUtils().getCustomModelData(itemStack);
        ItemData replacement = mapped.get(customData);
        if (replacement != null) {
            ItemMeta meta = replacement.getItemMeta();
            itemStack.setItemMeta(meta);
        }
    }
}
