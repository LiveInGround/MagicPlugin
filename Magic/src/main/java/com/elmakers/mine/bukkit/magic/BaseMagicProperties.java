package com.elmakers.mine.bukkit.magic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.NumberConversions;
import org.bukkit.util.Vector;

import com.elmakers.mine.bukkit.api.magic.MageController;
import com.elmakers.mine.bukkit.api.magic.MagicProperties;
import com.elmakers.mine.bukkit.utility.CompatibilityConstants;
import com.elmakers.mine.bukkit.utility.CompatibilityLib;
import com.elmakers.mine.bukkit.utility.ConfigurationUtils;
import com.elmakers.mine.bukkit.utility.StringUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

public class BaseMagicProperties implements MagicProperties {

    protected final @Nonnull MagicController controller;
    protected @Nonnull ConfigurationSection configuration = ConfigurationUtils.newConfigurationSection();

    // TODO: Separate these out into wand vs class properties
    public static final ImmutableSet<String> PROPERTY_KEYS = ImmutableSet.of(
            "path", "template",
            "mana", "mana_regeneration", "mana_max", "mana_max_boost",
            "mana_regeneration_boost",
            "mana_per_damage", "reset_mana_on_activate",
            "consume_reduction", "cost_reduction", "cooldown_reduction",
            "hotbar_count", "max_spells",
            "icon", "overrides",
            "protection", "potion_effects",
            "brushes", "brush_inventory", "spells", "spell_inventory", "spell_levels",
            "powered", "protected",
            "earn_multiplier",
            "attributes", "tags", "ignored_by_mobs", "allow_container_copy", "ignore_particles",
            "reflect_chance", "reflect_fov", "allow_path_overrides",
            "block_fov", "block_chance", "block_reflect_chance", "block_mage_cooldown", "block_cooldown"
    );

    public static final ImmutableSet<String> HIDDEN_PROPERTY_KEYS = ImmutableSet.of(
            "owner", "owner_id", "version", "item_attributes", "item_attribute_slot",
            "mana_timestamp", "storage", "hotbar", "template"
    );

    protected BaseMagicProperties(@Nonnull MageController controller) {
       this(controller, null);
    }

    protected BaseMagicProperties(@Nonnull MageController controller, ConfigurationSection configuration) {
        // Don't really like this, but Wand is very dependent on MagicController
        Preconditions.checkArgument(controller instanceof MagicController);
        this.controller = (MagicController)controller;

        // TODO: does this need to be a clone?
        if (configuration != null) {
            this.configuration = ConfigurationUtils.cloneConfiguration(configuration);
        }
    }

    public void load(@Nullable ConfigurationSection configuration) {
        this.configuration = ConfigurationUtils.cloneConfiguration(configuration);
    }

    public boolean hasOwnProperty(String key) {
        return configuration.contains(key);
    }

    @Override
    public boolean hasProperty(String key) {
        return hasOwnProperty(key);
    }

    @Nonnull
    public ConfigurationSection getPropertyConfiguration(String key) {
        return configuration;
    }

    @Override
    @Nullable
    public Object getProperty(String key) {
        return getPropertyConfiguration(key).get(key);
    }

    @Override
    @Nullable
    public <T> T getProperty(String key, Class<T> type) {
        Object value = getProperty(key);
        if (value == null || !type.isInstance(value)) {
            return null;
        }

        return type.cast(value);
    }

    @Override
    @Nonnull
    public <T> T getProperty(String key, T defaultValue) {
        Preconditions.checkNotNull(key, "key");
        Preconditions.checkNotNull(defaultValue, "defaultValue");

        @SuppressWarnings("unchecked")
        Class<? extends T> clazz = (Class<? extends T>) defaultValue.getClass();

        Object value = getProperty(key);
        if (value != null && clazz.isInstance(value)) {
            return clazz.cast(value);
        }
        if (value != null && value instanceof Number && defaultValue instanceof Number) {
            if (defaultValue instanceof Double) {
                return clazz.cast(NumberConversions.toDouble(value));
            } else if (defaultValue instanceof Integer) {
                return clazz.cast(NumberConversions.toInt(value));
            } else if (defaultValue instanceof Byte) {
                return clazz.cast(NumberConversions.toByte(value));
            } else if (defaultValue instanceof Float) {
                return clazz.cast(NumberConversions.toFloat(value));
            } else if (defaultValue instanceof Long) {
                return clazz.cast(NumberConversions.toLong(value));
            } else if (defaultValue instanceof Short) {
                return clazz.cast(NumberConversions.toShort(value));
            }
        }

        return defaultValue;
    }

    @Nullable
    public Object getObject(String key, Object defaultValue) {
        Object value = getProperty(key);
        return value == null ? defaultValue : value;
    }

    @Nullable
    public Object getObject(String key) {
        return getProperty(key);
    }

    @Override
    public double getDouble(String key, double defaultValue) {
        return getPropertyConfiguration(key).getDouble(key, defaultValue);
    }

    @Override
    public double getDouble(String key) {
        return getDouble(key, 0.0);
    }

    @Override
    public float getFloat(String key, float defaultValue) {
        return (float)getPropertyConfiguration(key).getDouble(key, defaultValue);
    }

    @Override
    public float getFloat(String key) {
        return getFloat(key, 0.0f);
    }

    @Override
    public int getInt(String key, int defaultValue) {
        return getPropertyConfiguration(key).getInt(key, defaultValue);
    }

    @Override
    public int getInt(String key) {
        return getInt(key, 0);
    }

    @Override
    public long getLong(String key, long defaultValue) {
        return getPropertyConfiguration(key).getLong(key, defaultValue);
    }

    @Override
    public long getLong(String key) {
        return getLong(key, 0L);
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        return getPropertyConfiguration(key).getBoolean(key, defaultValue);
    }

    @Override
    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    public boolean isBoolean(String key) {
        return getPropertyConfiguration(key).isBoolean(key);
    }

    @Override
    public String getString(String key, String defaultValue) {
        Object value = getProperty(key);
        return value == null ? defaultValue : value.toString();
    }

    @Override
    public String getString(String key) {
        return getString(key, null);
    }

    @Override
    @Nullable
    public List<String> getStringList(String key) {
        Object value = getProperty(key);
        return ConfigurationUtils.getStringList(value);
    }

    @Override
    @Nullable
    public List<ConfigurationSection> getSectionList(String key) {
        return ConfigurationUtils.getNodeList(getPropertyConfiguration(key), key);
    }

    public @Nullable String getIconDisabledKey() {
        return getIconKey("icon_disabled");
    }

    @Nullable
    public String getIconKey() {
        return getIconKey("icon");
    }

    @Nullable
    public String getIconKey(String iconKey) {
        if (controller.isLegacyIconsEnabled()) {
            return getString("legacy_" + iconKey, getString(iconKey));
        }
        if (controller.isVanillaIconsEnabled()) {
            return getString("vanilla_" + iconKey, getString(iconKey));
        }
        return getString(iconKey);
    }

    public boolean hasIconKey() {
        return hasIconKey("icon");
    }

    public boolean hasIconKey(String iconKey) {
        if (controller.isLegacyIconsEnabled()) {
            return hasProperty("legacy_" + iconKey) || hasProperty(iconKey);
        }
        if (controller.isVanillaIconsEnabled()) {
            return hasProperty("vanilla_" + iconKey) || hasProperty(iconKey);
        }
        return hasProperty(iconKey);
    }

    @Nullable
    public ConfigurationSection getConfigurationSection(String key) {
        Object value = getProperty(key);
        return value == null || !(value instanceof ConfigurationSection) ? null : (ConfigurationSection)value;
    }

    @Nullable
    public Vector getVector(String key, Vector def) {
        String stringData = getString(key, null);
        if (stringData == null) {
            return def;
        }

        return ConfigurationUtils.toVector(stringData);
    }

    @Nullable
    public Vector getVector(String key) {
        return getVector(key, null);
    }

    @Nonnull
    public ConfigurationSection getConfiguration() {
        return configuration;
    }

    @Nullable
    protected static String getPotionEffectString(Map<PotionEffectType, Integer> potionEffects) {
        if (potionEffects.size() == 0) return null;
        Collection<String> effectStrings = new ArrayList<>();
        for (Map.Entry<PotionEffectType, Integer> entry : potionEffects.entrySet()) {
            String effectString = entry.getKey().getName();
            if (entry.getValue() > 0) {
                effectString += ":" + entry.getValue();
            }
            effectStrings.add(effectString);
        }
        return StringUtils.join(effectStrings, ",");
    }

    protected String describePotionEffect(PotionEffectType effect, int level) {
        String effectName = effect.getName();
        String effectFirst = effectName.substring(0, 1);
        effectName = effectName.substring(1).toLowerCase().replace("_", " ");
        effectName = effectFirst + effectName;
        effectName = controller.getMessages().get("potion_effects." + effect.getName().toLowerCase(), effectName);
        return controller.getMessages().getLevelString("wand.potion_effect", level + 1, 5).replace("$effect", effectName);
    }

    protected void sendDebug(String debugMessage) {
        // Does nothing unless overridden
    }

    protected void sendMessage(String messageKey) {
        // Does nothing unless overridden
    }

    protected void sendAddMessage(String messageKey, String nameParam) {
        String message = getMessage(messageKey).replace("$name", nameParam);
        sendMessage(message);
    }

    protected String getMessage(String messageKey) {
        return getMessage(messageKey, "");
    }

    public String getMessage(String messageKey, String defaultValue) {
        return parameterizeMessage(controller.getMessages().get(getMessageKey(messageKey), defaultValue));
    }

    public String getMessageKey(String messageKey) {
        return messageKey;
    }

    protected String parameterizeMessage(String message) {
        return message;
    }

    public static String describeProperty(Object property) {
        return CompatibilityLib.getInventoryUtils().describeProperty(property, CompatibilityConstants.MAX_PROPERTY_DISPLAY_LENGTH);
    }

    public void describe(CommandSender sender, @Nullable Set<String> ignoreProperties, @Nullable Set<String> overriddenProperties) {
        ConfigurationSection itemConfig = getConfiguration();
        Set<String> keys = itemConfig.getKeys(false);
        for (String key : keys) {
            Object value = itemConfig.get(key);
            if (value != null && (ignoreProperties == null || !ignoreProperties.contains(key))) {
                ChatColor propertyColor = ChatColor.GRAY;
                if (overriddenProperties == null || !overriddenProperties.contains(key)) {
                    propertyColor = getAllPropertyKeys().contains(key) ? ChatColor.DARK_AQUA : ChatColor.DARK_GREEN;
                }

                sender.sendMessage(propertyColor.toString() + key + ChatColor.GRAY + ": " + ChatColor.WHITE + describeProperty(value));
            }
        }
    }

    @Override
    public void describe(CommandSender sender, @Nullable Set<String> ignoreProperties) {
        describe(sender, ignoreProperties, null);
    }

    @Override
    public void describe(CommandSender sender) {
        describe(sender, null);
    }

    /**
     * This is used in some very specific cases where properties coming from a config file should not
     * really be part of the config, and are more meta config.
     */
    protected void clearProperty(String key) {
        configuration.set(key, null);
    }

    @Override
    public boolean isEmpty() {
        return configuration.getKeys(false).isEmpty();
    }

    protected Set<String> getAllPropertyKeys() {
        return PROPERTY_KEYS;
    }
}
