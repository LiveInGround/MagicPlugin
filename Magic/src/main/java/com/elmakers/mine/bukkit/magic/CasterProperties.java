package com.elmakers.mine.bukkit.magic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import com.elmakers.mine.bukkit.api.event.AddSpellEvent;
import com.elmakers.mine.bukkit.api.event.SpellUpgradeEvent;
import com.elmakers.mine.bukkit.api.magic.MageController;
import com.elmakers.mine.bukkit.api.magic.MagicAttribute;
import com.elmakers.mine.bukkit.api.magic.MagicPropertyType;
import com.elmakers.mine.bukkit.api.magic.Messages;
import com.elmakers.mine.bukkit.api.magic.ProgressionPath;
import com.elmakers.mine.bukkit.api.spell.Spell;
import com.elmakers.mine.bukkit.api.spell.SpellKey;
import com.elmakers.mine.bukkit.api.spell.SpellTemplate;
import com.elmakers.mine.bukkit.block.MaterialBrush;
import com.elmakers.mine.bukkit.item.MagicAttributeModifier;
import com.elmakers.mine.bukkit.utility.ColorHD;
import com.elmakers.mine.bukkit.utility.CompatibilityLib;
import com.elmakers.mine.bukkit.utility.ConfigurationUtils;
import com.elmakers.mine.bukkit.utility.CurrencyAmount;
import com.elmakers.mine.bukkit.utility.StringUtils;
import com.elmakers.mine.bukkit.utility.WordUtils;
import com.elmakers.mine.bukkit.wand.Wand;
import com.elmakers.mine.bukkit.wand.WandLevel;
import com.elmakers.mine.bukkit.wand.WandUpgradePath;

public abstract class CasterProperties extends BaseMagicConfigurable implements com.elmakers.mine.bukkit.api.magic.CasterProperties {
    // This is used for property migration, if the stored data's version property is too low
    // then we migrate.
    protected static int LEGACY_VERSION = 6;
    protected static int CURRENT_VERSION = 7;
    public static final float DEFAULT_SPELL_COLOR_MIX_WEIGHT = 0.0001f;
    private float effectColorSpellMixWeight = DEFAULT_SPELL_COLOR_MIX_WEIGHT;

    protected float effectiveManaMax = 0;
    protected float effectiveManaRegeneration = 0;
    private Map<PotionEffectType, Integer> potionEffects = new HashMap<>();
    private ColorHD effectColor = null;

    public static void setLegacyVersion() {
        CURRENT_VERSION = LEGACY_VERSION;
    }

    public CasterProperties(MagicPropertyType type, MageController controller) {
        super(type, controller);
    }

    public boolean hasOwnMana() {
        MagicPropertyType propertyType = propertyRoutes.get("mana");
        return (propertyType == null || propertyType == type);
    }

    @Override
    public int getManaRegeneration() {
        ManaController manaController = getManaController();
        if (manaController != null && isPlayer()) {
            return (int) Math.ceil(manaController.getManaRegen(getPlayer()));
        }
        return getInt("mana_regeneration", getInt("xp_regeneration"));
    }

    @Override
    public int getManaMax() {
        ManaController manaController = getManaController();
        if (manaController != null && isPlayer()) {
            return (int) Math.ceil(manaController.getMaxMana(getPlayer()));
        }
        return getInt("mana_max", getInt("xp_max"));
    }

    @Override
    public void setMana(float mana) {
        if (isCostFree()) {
            setProperty("mana", null);
        } else {
            ManaController manaController = getManaController();
            if (manaController != null && isPlayer()) {
                manaController.setMana(getPlayer(), mana);
                return;
            }
            setProperty("mana", Math.max(0, mana));
        }
    }

    @Override
    @Deprecated
    public void setManaMax(int manaMax) {
        setManaMax((float) manaMax);
    }

    @Override
    public void setManaMax(float manaMax) {
        setProperty("mana_max", Math.max(0, manaMax));
    }

    @Override
    @Deprecated
    public void setManaRegeneration(int manaRegeneration) {
        setManaRegeneration((float) manaRegeneration);
    }

    @Override
    public void setManaRegeneration(float manaRegeneration) {
        setProperty("mana_regeneration", Math.max(0, manaRegeneration));
    }

    @Override
    public float getMana() {
        ManaController manaController = getManaController();
        if (manaController != null && isPlayer()) {
            return (float) manaController.getMana(getPlayer());
        }
        return getFloat("mana", getFloat("xp"));
    }

    @Override
    public void removeMana(float amount) {
        ManaController manaController = getManaController();
        if (manaController != null && isPlayer()) {
            manaController.removeMana(getPlayer(), amount);
            return;
        }
        setMana(getMana() - amount);
    }

    public float getManaRegenerationBoost() {
        return getFloat("mana_regeneration_boost", getFloat("xp_regeneration_boost"));
    }

    public float getManaMaxBoost() {
        return getFloat("mana_max_boost", getFloat("xp_max_boost"));
    }

    @Override
    public boolean isCostFree() {
        return getFloat("cost_reduction") > 1;
    }

    @Override
    public boolean isCooldownFree() {
        return getFloat("cooldown_reduction") > 1;
    }

    @Override
    public boolean isConsumeFree() {
        return getFloat("consume_reduction") > 1;
    }

    @Override
    public int getEffectiveManaMax() {
        ManaController manaController = getManaController();
        if (manaController != null && isPlayer()) {
            return (int) Math.ceil(manaController.getMaxMana(getPlayer()));
        }
        return (int) Math.ceil(effectiveManaMax);
    }

    @Override
    public int getEffectiveManaRegeneration() {
        ManaController manaController = getManaController();
        if (manaController != null && isPlayer()) {
            return (int) Math.ceil(manaController.getManaRegen(getPlayer()));
        }
        return (int) Math.ceil(effectiveManaRegeneration);
    }

    protected long getLastManaRegeneration() {
        return getLong("mana_timestamp");
    }

    public void passiveEffectsUpdated() {
    }

    public boolean updateMaxMana(Mage mage) {
        if (!usesMana()) {
            return false;
        }

        float currentMana = effectiveManaMax;
        float currentManaRegen = effectiveManaRegeneration;
        effectiveManaMax = getManaMax();
        effectiveManaRegeneration = getManaRegeneration();
        if (mage != null && getBoolean("boostable", true)) {
            effectiveManaMax = (int) (effectiveManaMax * mage.getManaMaxMultiplier());
            effectiveManaRegeneration = (int) (effectiveManaRegeneration * mage.getManaRegenerationMultiplier());
        }

        return (currentMana != effectiveManaMax || effectiveManaRegeneration != currentManaRegen);
    }

    public boolean usesMana() {
        if (isCostFree()) return false;
        return getManaMax() > 0;
    }

    public boolean tickMana() {
        boolean updated = false;
        if (usesMana() && hasOwnMana()) {
            long now = System.currentTimeMillis();
            if (!getMage().isManaRegenerationDisabled()) {
                int effectiveManaRegeneration = getEffectiveManaRegeneration();
                long lastManaRegeneration = getLastManaRegeneration();
                if (lastManaRegeneration > 0 && effectiveManaRegeneration > 0) {
                    long delta = now - lastManaRegeneration;
                    int effectiveManaMax = getEffectiveManaMax();
                    int manaMax = getManaMax();
                    float mana = getMana();
                    if (effectiveManaMax == 0 && manaMax > 0) {
                        effectiveManaMax = manaMax;
                    }
                    setMana(Math.min(effectiveManaMax, mana + (float) effectiveManaRegeneration * (float) delta / 1000));
                    updated = true;
                }
            }
            setProperty("mana_timestamp", now);
        }

        return updated;
    }

    public void tick() {
        tickMana();
    }

    @Override
    @Deprecated
    public boolean setSpelLLevel(String spellKey, int level) {
        return setSpellLevel(spellKey, level);
    }

    @Override
    public boolean setSpellLevel(String spellKey, int level) {
        BaseMagicConfigurable storage = getStorage("spell_levels");
        if (storage != this && storage != null && storage instanceof com.elmakers.mine.bukkit.api.magic.CasterProperties) {
            return ((com.elmakers.mine.bukkit.api.magic.CasterProperties) storage).setSpellLevel(spellKey, level);
        }
        if (!hasSpell(spellKey)) {
            return false;
        }
        Map<String, Integer> spellLevels = getSpellLevels();
        Integer existingLevel = spellLevels.get(spellKey);
        boolean modified = false;
        if (existingLevel == null || level != existingLevel) {
            modified = true;
            spellLevels.put(spellKey, level);
            setProperty("spell_levels", spellLevels);
        }

        return modified;
    }

    @Override
    public boolean addSpell(String spellKey) {
        int maxSpells = getMaxSpells();
        if (maxSpells > 0 && getSpells().size() >= maxSpells) {
            return false;
        }
        return forceAddSpell(spellKey);
    }

    @Override
    public boolean forceAddSpell(String spellKey) {
        BaseMagicConfigurable storage = getStorage("spells");
        if (storage != this && storage != null) {
            return storage.addSpell(spellKey);
        }

        SpellTemplate template = controller.getSpellTemplate(spellKey);
        if (template == null) {
            // We need to remember that we have this invalid spell
            Collection<String> spells = getBaseSpells();
            boolean modified = spells.add(spellKey);
            if (modified) {
                setProperty("spells", new ArrayList<>(spells));
            }
            return modified;
        }

        // Convert to spell if aliased
        spellKey = template.getKey();

        // Make sure to apply any pending changes
        preUpdate();
        Collection<String> spells = getBaseSpells();
        SpellKey key = new SpellKey(spellKey);
        SpellTemplate currentSpell = getSpellTemplate(spellKey);
        boolean modified = spells.add(key.getBaseKey());
        if (modified) {
            setProperty("spells", new ArrayList<>(spells));
        }
        boolean levelModified = false;
        if (key.getLevel() > 1) {
            levelModified = upgradeSpellLevel(key.getBaseKey(), key.getLevel());
        }

        if (!modified && !levelModified) {
            return false;
        }

        // Special handling for spells to remove
        Collection<SpellKey> spellsToRemove = template.getSpellsToRemove();
        for (SpellKey removeKey : spellsToRemove) {
            removeSpell(removeKey.getBaseKey());
        }

        Mage mage = getMage();
        if (mage != null) {
            if (currentSpell != null) {
                String levelDescription = template.getLevelDescription();
                if (levelDescription == null || levelDescription.isEmpty()) {
                    levelDescription = template.getName();
                }
                sendLevelMessage("spell_upgraded", currentSpell.getName(), levelDescription);

                String upgradeDescription = template.getUpgradeDescription().replace("$name", currentSpell.getName());
                if (!upgradeDescription.isEmpty()) {
                    mage.sendMessage(controller.getMessages().get("spell.upgrade_description_prefix"), upgradeDescription);
                }

                if (!mage.isLoading()) {
                    SpellUpgradeEvent upgradeEvent = new SpellUpgradeEvent(mage, getWand(), currentSpell, template);
                    Bukkit.getPluginManager().callEvent(upgradeEvent);
                }
            } else {
                // This is a little hacky, but it is here to fix duplicate spell messages from the spellshop.
                if (mage.getActiveGUI() == null && !template.isQuiet())
                    sendAddMessage("spell_added", template.getName());

                if (!mage.isLoading()) {
                    AddSpellEvent addEvent = new AddSpellEvent(mage, getWand(), template);
                    Bukkit.getPluginManager().callEvent(addEvent);
                }
            }
        }
        updated();

        return true;
    }

    @Override
    public boolean addBrush(String brushKey) {
        BaseMagicConfigurable storage = getStorage("brushes");
        if (storage != this && storage != null) {
            return storage.addBrush(brushKey);
        }

        // Make sure to apply any pending changes
        preUpdate();
        Collection<String> brushes = getBrushes();
        boolean modified = brushes.add(brushKey);
        if (modified) {
            setProperty("brushes", new ArrayList<>(brushes));
        }

        Mage mage = getMage();
        if (modified && mage != null) {
            Messages messages = controller.getMessages();
            String materialName = MaterialBrush.getMaterialName(messages, brushKey);
            if (materialName == null) {
                mage.getController().getLogger().warning("Invalid material: " + brushKey);
                materialName = brushKey;
            }

            sendAddMessage("brush_added", materialName);
        }
        if (modified) {
            updated();
        }

        return modified;
    }

    public boolean removeSpell(String spellKey) {
        // Make sure to apply any pending changes
        preUpdate();
        Collection<String> spells = getBaseSpells();
        SpellKey key = new SpellKey(spellKey);
        boolean modified = spells.remove(key.getBaseKey());
        if (modified) {
            setProperty("spells", new ArrayList<>(spells));
            Map<String, Integer> spellLevels = getSpellLevels();
            if (spellLevels.remove(key.getBaseKey()) != null) {
                setProperty("spell_levels", spellLevels);
            }
            updated();
        }

        return modified;
    }

    public boolean removeBrush(String brushKey) {
        // Make sure to apply any pending changes
        preUpdate();
        Collection<String> brushes = getBrushes();
        boolean modified = brushes.remove(brushKey);
        if (modified) {
            setProperty("brushes", new ArrayList<>(brushes));
            updated();
        }

        return modified;
    }

    @Override
    public int getSpellLevel(String spellKey) {
        Map<String, Integer> spellLevels = getSpellLevels();
        Integer level = spellLevels.get(spellKey);
        return level == null ? 1 : level;
    }

    @Nullable
    @Override
    public SpellTemplate getSpellTemplate(String spellKey) {
        SpellKey key = new SpellKey(spellKey);
        Collection<String> spells = getBaseSpells();
        if (!spells.contains(key.getBaseKey())) return null;
        SpellKey baseKey = new SpellKey(key.getBaseKey(), getSpellLevel(key.getBaseKey()));
        return controller.getSpellTemplate(baseKey.getKey());
    }

    public void updateMana() {

    }

    @Override
    public boolean hasBrush(String key) {
        return !getBrushes().contains(key);
    }

    @Override
    public boolean hasSpell(String key) {
        SpellKey spellKey = new SpellKey(key);

        if (!getBaseSpells().contains(spellKey.getBaseKey())) return false;
        int level = getSpellLevel(spellKey.getBaseKey());
        return (level >= spellKey.getLevel());
    }

    public Set<String> getBaseSpells() {
        Object existingSpells = getObject("spells");
        Set<String> spells = new HashSet<>();
        if (existingSpells != null) {
            if (!(existingSpells instanceof List)) {
                controller.getLogger().warning("Spell list in " + type + " is " + existingSpells.getClass().getName() + ", expected List");
            } else {
                @SuppressWarnings("unchecked")
                List<String> existingList = (List<String>) existingSpells;
                spells.addAll(existingList);
            }
        }
        return spells;
    }

    @Override
    public Set<String> getSpells() {
        Set<String> spellSet = new HashSet<>();
        Collection<String> spells = getBaseSpells();
        Map<String, Integer> spellLevels = getSpellLevels();

        for (String key : spells) {
            Integer level = spellLevels.get(key);
            if (level != null) {
                spellSet.add(new SpellKey(key, level).getKey());
            } else {
                spellSet.add(key);
            }
        }
        return spellSet;
    }

    @Nullable
    @Override
    public Spell getSpell(String spellKey) {
        Mage mage = getMage();
        if (mage == null) {
            return null;
        }
        SpellKey key = new SpellKey(spellKey);
        spellKey = key.getBaseKey();
        Set<String> spells = getBaseSpells();
        if (!spells.contains(spellKey)) return null;
        Map<String, Integer> spellLevels = getSpellLevels();
        Integer level = spellLevels.get(spellKey);
        if (level != null) {
            spellKey = new SpellKey(spellKey, level).getKey();
        }
        return mage.getSpell(spellKey);
    }

    @Override
    public Set<String> getBrushes() {
        Object existingBrushes = getObject("brushes");
        Set<String> brushes = new HashSet<>();
        if (existingBrushes != null) {
            if (!(existingBrushes instanceof List)) {
                controller.getLogger().warning("Brush list in " + type + " is " + existingBrushes.getClass().getName() + ", expected List");
            } else {
                @SuppressWarnings("unchecked")
                List<String> existingList = (List<String>) existingBrushes;
                brushes.addAll(existingList);
            }
        }
        return brushes;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nonnull Map<String, Integer> getSpellInventory() {
        Object spellInventoryRaw = getObject("spell_inventory");
        if (spellInventoryRaw == null) return new HashMap<>();
        Map<String, Integer> spellInventory = null;

        if (spellInventoryRaw instanceof Map) {
            spellInventory = (Map<String, Integer>)spellInventoryRaw;
        } else if (spellInventoryRaw instanceof ConfigurationSection) {
            spellInventory = ConfigurationUtils.toTypedMap((ConfigurationSection)spellInventoryRaw);
        } else {
            spellInventory = new HashMap<>();
        }
        return spellInventory;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nonnull Map<String, Integer> getBrushInventory() {
        Object brushInventoryRaw = getObject("brush_inventory");
        if (brushInventoryRaw == null) return new HashMap<>();
        Map<String, Integer> brushInventory = null;

        if (brushInventoryRaw instanceof Map) {
            brushInventory = (Map<String, Integer>)brushInventoryRaw;
        } else if (brushInventoryRaw instanceof ConfigurationSection) {
            brushInventory = ConfigurationUtils.toTypedMap((ConfigurationSection)brushInventoryRaw);
        } else {
            brushInventory = new HashMap<>();
        }
        return brushInventory;
    }

    @Override
    public void updateBrushInventory(Map<String, Integer> updateBrushes) {
        Map<String, Integer> brushInventory = getBrushInventory();
        for (Map.Entry<String, Integer> brushEntry : updateBrushes.entrySet()) {
            String brushKey = brushEntry.getKey();
            Integer slot = brushEntry.getValue();
            brushInventory.put(brushKey, slot);
        }
        setProperty("brush_inventory", brushInventory);
    }

    @Override
    public void updateSpellInventory(Map<String, Integer> updateSpells) {
        Map<String, Integer> spellInventory = getSpellInventory();
        for (Map.Entry<String, Integer> spellEntry : updateSpells.entrySet()) {
            String spellKey = spellEntry.getKey();
            Integer slot = spellEntry.getValue();
            spellInventory.put(spellKey, slot);
        }
        setProperty("spell_inventory", spellInventory);
    }

    @Nullable
    @Override
    public ProgressionPath getPath() {
        String pathKey = getString("path");
        if (pathKey != null && !pathKey.isEmpty()) {
            return controller.getPath(pathKey);
        }
        return null;
    }

    @Override
    public void setPath(String path) {
        setProperty("path", path);
    }

    @Override
    public int getLevel() {
        return getInt("level", 1);
    }

    @Override
    public boolean canProgress() {
        ProgressionPath path = getPath();
        return (path != null && path.canProgress(this));
    }

    protected float stackPassiveProperty(float property, float stackProperty) {
        boolean stack = getBoolean("stack");

        // If stacking, then this value has already been added to the base value.
        // If this value is 0, then we don't need to look at it.
        if (!stack && stackProperty != 0) {
            property = Math.max(property, stackProperty);
        }
        return property;
    }

    @Override
    protected boolean upgradeInternal(String key, Object value) {
        if (key.equals("path")) {
            ProgressionPath path = getPath();
            if (path != null && path.hasPath(value.toString())) {
                return false;
            }
            setProperty(key, value);
            return true;
        }

        return super.upgradeInternal(key, value);
    }

    @Nullable
    @Override
    public Double getAttribute(String attributeKey) {
        ConfigurationSection attributes = getConfigurationSection("attributes");
        Double value = null;
        if (attributes != null) {
            if (attributes.isConfigurationSection(attributeKey)) {
                ConfigurationSection attributeConfig = attributes.getConfigurationSection(attributeKey);
                value = !attributeConfig.contains("value") ? null
                        : attributes.getDouble("value");
            } else {
                value = !attributes.contains(attributeKey)
                        ? null
                        : attributes.getDouble(attributeKey);
            }
        }
        if (value == null) {
            MagicAttribute defaultSetting = controller.getAttribute(attributeKey);
            if (defaultSetting != null) {
                value = defaultSetting.getDefault();
            }
        }
        return value;
    }

    @Override
    public void setAttribute(String attributeKey, Double attributeValue) {
        // Make sure to apply any pending changes
        preUpdate();
        ConfigurationSection attributes = getConfigurationSection("attributes");
        if (attributes == null) {
            if (attributeValue == null) return;
            attributes = ConfigurationUtils.newConfigurationSection();
        }

        if (attributes.isConfigurationSection(attributeKey)) {
            ConfigurationSection attributeConfig = attributes.getConfigurationSection(attributeKey);
            attributeConfig.set("value", attributeValue);
        } else {
            attributes.set(attributeKey, attributeValue);
        }
        setProperty("attributes", attributes);
        Mage mage = getMage();
        if (mage != null) {
            mage.attributesUpdated();
        }
        updated();
    }

    protected void cleanSlottedUpgradeConfig(ConfigurationSection upgradeConfig) {
        cleanUpgradeConfig(upgradeConfig, true);
    }

    protected void cleanUpgradeConfig(ConfigurationSection upgradeConfig) {
        cleanUpgradeConfig(upgradeConfig, false);
    }

    protected void cleanUpgradeConfig(ConfigurationSection upgradeConfig, boolean slotted) {
        upgradeConfig.set("id", null);
        upgradeConfig.set("indestructible", null);
        upgradeConfig.set("upgrade", null);
        upgradeConfig.set("icon", null);
        upgradeConfig.set("upgrade_icon", null);
        upgradeConfig.set("legacy_icon", null);
        upgradeConfig.set("legacy_upgrade_icon", null);
        upgradeConfig.set("template", null);
        upgradeConfig.set("description", null);
        upgradeConfig.set("name", null);
        upgradeConfig.set("lore", null);
        upgradeConfig.set("slot", null);
        if (slotted) {
            upgradeConfig.set("mana", null);
        }
    }

    @Override
    public boolean add(com.elmakers.mine.bukkit.api.wand.Wand wandUpgrade) {
        if (!(wandUpgrade instanceof Wand)) {
            return false;
        }
        Wand wand = (Wand) wandUpgrade;
        ConfigurationSection upgradeConfig = ConfigurationUtils.cloneConfiguration(wand.getEffectiveConfiguration());
        cleanUpgradeConfig(upgradeConfig);
        return upgrade(upgradeConfig);
    }

    @Override
    public boolean addItem(ItemStack item) {
        if (Wand.isAbsorbable(item)) {
            if (Wand.isSpell(item) && !Wand.isSkill(item)) {
                String spell = Wand.getSpell(item);
                SpellKey spellKey = new SpellKey(spell);
                Integer spellLevel = null;
                if (hasSpell(spellKey.getBaseKey())) {
                    spellLevel = getSpellLevel(spellKey.getBaseKey());
                }
                if ((spellLevel == null || spellLevel < spellKey.getLevel()) && addSpell(spell)) {
                    return true;
                }
            } else if (Wand.isBrush(item)) {
                String materialKey = Wand.getBrush(item);
                Set<String> materials = getBrushes();
                if (!materials.contains(materialKey) && addBrush(materialKey)) {
                    return true;
                }
            }
        }
        if (Wand.isUpgrade(item)) {
            Wand wand = controller.getWand(item);
            return this.add(wand);
        }
        Mage mage = getMage();
        if (mage != null) {
            CurrencyAmount currency = CompatibilityLib.getInventoryUtils().getCurrencyAmount(item);
            if (currency != null && !mage.isAtMaxCurrency(currency.getType())) {
                int amount = (int) Math.floor(mage.getEarnMultiplier(currency.getType()) * currency.getAmount() * item.getAmount());
                mage.addCurrency(currency.getType(), amount);
                return true;
            }
        }

        return false;
    }

    protected void sendLevelMessage(String messageKey, String nameParam, String level) {
        Mage mage = getMage();
        if (mage == null || nameParam == null || nameParam.isEmpty()) return;

        String message = getMessage(messageKey).replace("$name", nameParam).replace("$level", level);
        mage.sendMessage(message);
    }

    @Override
    protected void sendAddMessage(String messageKey, String nameParam) {
        Mage mage = getMage();
        if (mage == null || nameParam == null || nameParam.isEmpty()) return;
        String message = getMessage(messageKey).replace("$name", nameParam);
        mage.sendMessage(message);
    }

    @Override
    protected void sendMessage(String messageKey) {
        Mage mage = getMage();
        if (mage == null || messageKey == null || messageKey.isEmpty()) return;
        mage.sendMessage(getMessage(messageKey));
    }

    @Override
    public void sendMessageKey(String messageKey, String... parameters) {
        Mage mage = getMage();
        if (mage == null || messageKey == null || messageKey.isEmpty()) return;
        String message = getMessage(messageKey);
        if (parameters != null) {
            for (int i = 0; i < parameters.length - 1; i++) {
                message = message.replace(parameters[i], parameters[i + 1]);
            }
        }
        mage.sendMessage(message);
    }

    @Override
    protected void sendDebug(String debugMessage) {
        Mage mage = getMage();
        if (mage != null) {
            mage.sendDebugMessage(debugMessage);
        }
    }

    @Nullable
    protected Wand getWand() {
        return null;
    }

    public abstract boolean isPlayer();

    @Nullable
    public abstract Player getPlayer();

    @Nullable
    @Override
    public abstract Mage getMage();

    @Override
    public MageController getController() {
        return controller;
    }

    ;

    @SuppressWarnings("unchecked")
    protected void migrateBrushes(ConfigurationSection configuration) {
        Object brushInventoryRaw = configuration.get("brush_inventory");
        if (brushInventoryRaw != null) {
            Map<String, ? extends Object> brushInventory = null;
            Map<String, Integer> newBrushInventory = new HashMap<>();
            if (brushInventoryRaw instanceof Map) {
                brushInventory = (Map<String, ? extends Object>) brushInventoryRaw;
            } else if (brushInventoryRaw instanceof ConfigurationSection) {
                brushInventory = ConfigurationUtils.toMap((ConfigurationSection) brushInventoryRaw);
            }
            if (brushInventory != null) {
                for (Map.Entry<String, ? extends Object> brushEntry : brushInventory.entrySet()) {
                    Object slot = brushEntry.getValue();
                    if (slot != null && slot instanceof Integer) {
                        String materialKey = brushEntry.getKey();
                        materialKey = CompatibilityLib.getCompatibilityUtils().migrateMaterial(materialKey);
                        newBrushInventory.put(materialKey, (Integer) slot);
                    }
                }
                configuration.set("brush_inventory", newBrushInventory);
            }
        }

        Object brushesRaw = getObject("brushes", getObject("materials"));
        if (brushesRaw != null) {
            Collection<String> brushes = null;
            if (brushesRaw instanceof String) {
                String[] brushNames = StringUtils.split((String) brushesRaw, ',');
                brushes = Arrays.asList(brushNames);
            } else if (brushesRaw instanceof Collection) {
                brushes = (Collection<String>) brushesRaw;
            }

            if (brushes != null) {

                configuration.set("brushes", new ArrayList<>(brushes));
            }
        }
    }

    protected void migrate(int version, ConfigurationSection configuratoin) {
        // Migration: Update brushes to 1.13
        if (version <= 6) {
            migrateBrushes(configuratoin);
        }

        configuratoin.set("version", CURRENT_VERSION);
    }

    @Override
    public void load(@Nullable ConfigurationSection configuration) {
        int version = configuration.getInt("version", 0);
        if (version < CURRENT_VERSION) {
            // Migration will be handled by CasterProperties, this is just here
            // So that we save the data after to avoid re-migrating.
            migrate(version, configuration);
        }
        super.load(configuration);
    }

    @Override
    public boolean upgradesAllowed() {
        return true;
    }

    @Override
    public boolean checkAndUpgrade(boolean quiet) {
        ProgressionPath path = getPath();
        ProgressionPath nextPath = path != null ? path.getNextPath() : null;
        if (nextPath == null) {
            return true;
        }
        if (canProgress()) {
            return true;
        }
        if (!path.checkUpgradeRequirements(getWand(), quiet ? null : getMage())) {
            return false;
        }
        path.upgrade(getMage(), getWand());
        return true;
    }

    public String getName() {
        return "";
    }

    @Override
    public int randomize(int totalLevels, boolean addSpells) {
        Mage mage = getMage();
        Wand wand = (this instanceof Wand) ? (Wand) this : (mage == null ? null : mage.getActiveWand());
        ProgressionPath checkPath = getPath();
        if (checkPath == null || !(checkPath instanceof WandUpgradePath)) {
            if (mage != null && addSpells) {
                mage.sendMessage(getMessage("no_path").replace("$wand", getName()));
            }
            return 0;
        }
        WandUpgradePath path = (WandUpgradePath) checkPath;

        int minLevel = path.getMinLevel();
        if (totalLevels < minLevel) {
            if (mage != null && addSpells) {
                String levelMessage = getMessage("need_more_levels");
                levelMessage = levelMessage.replace("$levels", Integer.toString(minLevel));
                mage.sendMessage(levelMessage);
            }
            return 0;
        }

        // Just a hard-coded sanity check
        int maxLevel = path.getMaxLevel();
        totalLevels = Math.min(totalLevels, maxLevel * 50);

        int addLevels = Math.min(totalLevels, maxLevel);
        int levels = 0;
        boolean modified = true;
        while (addLevels >= minLevel && modified) {
            boolean hasUpgrade = path.hasUpgrade();
            WandLevel level = path.getLevel(addLevels);

            if (!path.canProgress(this) && (path.hasSpells() || path.hasMaterials())) {
                // Check for level up
                WandUpgradePath nextPath = path.getUpgrade();
                if (nextPath != null) {
                    if (path.checkUpgradeRequirements(this, !addSpells) && (wand != null || mage != null)) {
                        path.upgrade(wand, mage);
                    }
                    break;
                } else {
                    if (mage != null && addSpells) {
                        mage.sendMessage(getMessage("fully_enchanted").replace("$wand", getName()));
                    }
                    break;
                }
            }

            modified = level.randomize(mage, this, hasUpgrade, addSpells);
            totalLevels -= maxLevel;
            if (modified) {
                if (mage != null) {
                    path.enchanted(mage);
                }
                levels += addLevels;

                // Check for level up
                WandUpgradePath nextPath = path.getUpgrade();
                if (nextPath != null && path.checkUpgradeRequirements(this, true) && !path.canProgress(this)) {
                    path.upgrade(wand, mage);
                    path = nextPath;
                }
            } else if (path.canProgress(this)) {
                if (mage != null && levels == 0 && addSpells) {
                    String message = getMessage("require_more_levels");
                    mage.sendMessage(message);
                }
            } else if (hasUpgrade) {
                if (path.checkUpgradeRequirements(this, !addSpells)) {
                    if (wand != null || mage != null) {
                        path.upgrade(wand, mage);
                    }
                    levels += addLevels;
                }
            } else if (mage != null && addSpells) {
                mage.sendMessage(getMessage("fully_enchanted").replace("$wand", getName()));
            }
            addLevels = Math.min(totalLevels, maxLevel);
        }
        return levels;
    }

    @Nullable
    public Map<String, String> getOverrides() {
        Map<String, String> castOverrides = null;
        if (hasProperty("overrides")) {
            Object overridesGeneric = getObject("overrides");
            if (overridesGeneric != null) {
                castOverrides = new HashMap<>();
                if (overridesGeneric instanceof String) {
                    String overrides = (String) overridesGeneric;
                    if (!overrides.isEmpty()) {
                        // Support YML-List-As-String format
                        // May not really need this anymore.
                        overrides = overrides.replaceAll("[\\]\\[]", "");
                        String[] pairs = StringUtils.split(overrides, ',');
                        for (String override : pairs) {
                            parseOverride(override, castOverrides);
                        }
                    }
                } else if (overridesGeneric instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> overrideList = (List<String>) overridesGeneric;
                    for (String override : overrideList) {
                        parseOverride(override, castOverrides);
                    }
                } else if (overridesGeneric instanceof ConfigurationSection) {
                    ConfigurationSection overridesSection = (ConfigurationSection) overridesGeneric;
                    Set<String> keys = overridesSection.getKeys(true);
                    for (String key : keys) {
                        Object leaf = overridesSection.get(key);
                        if (!(leaf instanceof ConfigurationSection) && !(leaf instanceof Map)) {
                            castOverrides.put(key, leaf.toString());
                        }
                    }
                } else if (overridesGeneric instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> cast = (Map<String, String>) overridesGeneric;
                    castOverrides.putAll(cast);
                }
            }
        }
        return castOverrides;
    }

    private void parseOverride(String override, Map<String, String> castOverrides) {
        // Unescape commas
        override = override.replace("\\|", ",");
        String[] keyValue = StringUtils.split(override, " ", 2);
        if (keyValue.length > 0) {
            String value = keyValue.length > 1 ? keyValue[1] : "";
            castOverrides.put(keyValue[0], value);
        }
    }

    public void setOverrides(Map<String, String> overrides) {
        overrides = overrides != null && overrides.isEmpty() ? null : overrides;
        setProperty("overrides", overrides);
        updated();
    }

    public void removeOverride(String key) {
        Map<String, String> castOverrides = getOverrides();
        if (castOverrides != null) {
            castOverrides.remove(key);
            setOverrides(castOverrides);
        }
    }

    public void setOverride(String key, String value) {
        Map<String, String> castOverrides = getOverrides();
        if (castOverrides == null) {
            castOverrides = new HashMap<>();
        }
        if (value == null || value.length() == 0) {
            castOverrides.remove(key);
        } else {
            castOverrides.put(key, value);
        }
        setOverrides(castOverrides);
    }

    @Override
    public boolean addOverride(String key, String value) {
        Map<String, String> castOverrides = getOverrides();
        if (castOverrides == null) {
            castOverrides = new HashMap<>();
        }
        boolean modified = false;
        if (value == null || value.length() == 0) {
            modified = castOverrides.containsKey(key);
            castOverrides.remove(key);
        } else {
            String current = castOverrides.get(key);
            modified = current == null || !current.equals(value);
            castOverrides.put(key, value);
        }
        if (modified) {
            setOverrides(castOverrides);
        }

        return modified;
    }

    @Override
    public void loadProperties() {
        super.loadProperties();

        potionEffects.clear();
        if (hasProperty("potion_effects")) {
            // Do this individual for all 3 types, so we use the merged configuration section
            ConfigurationSection effectConfig = getConfigurationSection("potion_effects");
            if (effectConfig != null) {
                addPotionEffects(potionEffects, effectConfig);
            } else {
                List<String> effectList = getStringList("potion_effects");
                if (effectList != null && !effectList.isEmpty()) {
                    addPotionEffects(potionEffects, effectList);
                } else {
                    addPotionEffects(potionEffects, getString("potion_effects"));
                }
            }
        }
        if (hasProperty("effect_color")) {
            setEffectColor(getString("effect_color"));
        }
    }

    public Map<PotionEffectType, Integer> getPotionEffects() {
        return potionEffects;
    }

    @Override
    @Nullable
    public String getEffectParticleName() {
        return getString("effect_particle");
    }

    public void setEffectColor(String hexColor) {
        if (hexColor == null || hexColor.length() == 0 || hexColor.equals("none")) {
            effectColor = null;
            return;
        }
        // Annoying config conversion issue :\
        if (hexColor.contains(".")) {
            hexColor = hexColor.substring(0, hexColor.indexOf('.'));
        }
        effectColor = new ColorHD(hexColor);
        if (hexColor.equals("random")) {
            setProperty("effect_color", effectColor.toString());
        }
    }

    @Nullable
    @Override
    public Color getEffectColor() {
        return effectColor == null ? null : effectColor.getColor();
    }

    protected void onCast(SpellTemplate spell) {
        Color spellColor = spell.getColor();
        if (spellColor != null && this.effectColor != null) {
            this.effectColor = this.effectColor.mixColor(spellColor, effectColorSpellMixWeight);
            setProperty("effect_color", effectColor.toString());
            // Note that we don't save this change.
            // The hope is that the wand will get saved at some point later
            // And we don't want to trigger NBT writes every spell cast.
            // And the effect color morphing isn't all that important if a few
            // casts get lost.
        }
    }

    protected void discoverRecipes(String listKey) {
        List<String> recipes = getStringList(listKey);
        Mage mage = getMage();
        if (mage != null) {
            mage.discoverRecipes(recipes);
        }
    }

    @Override
    public void updated() {
        super.updated();
        Mage mage = getMage();
        if (mage != null) {
            mage.updatePassiveEffects();
        }
    }

    public boolean isPassive() {
        return getBoolean("passive");
    }

    public boolean pathOverridesAllowed() {
        return getBoolean("allow_path_overrides", true);
    }

    @Nullable
    public ConfigurationSection getPathConfigurationSection(String key) {
        ConfigurationSection config = getPathPropertyConfiguration(key);
        return config == null ? null : config.getConfigurationSection(key);
    }

    @Nullable
    public ConfigurationSection getPathPropertyConfiguration(String key) {
        if (key.equals("path") || key.equals("allow_path_overrides") || !pathOverridesAllowed()) return null;
        ProgressionPath path = getPath();
        if (path != null) {
            ConfigurationSection pathProperties = path.getProperties();
            if (pathProperties != null && pathProperties.contains(key)) {
                return pathProperties;
            }
        }
        return null;
    }

    @Override
    @Nonnull
    public ConfigurationSection getPropertyConfiguration(String key) {
        ConfigurationSection pathConfiguration = getPathPropertyConfiguration(key);
        if (pathConfiguration != null) {
            return pathConfiguration;
        }
        return super.getPropertyConfiguration(key);
    }

    @Override
    public boolean hasOwnProperty(String key) {
        if (!key.equals("path") && getStorage("path") == this && pathOverridesAllowed()) {
            ProgressionPath path = getPath();
            if (path != null) {
                ConfigurationSection pathProperties = path.getProperties();
                if (pathProperties != null && pathProperties.contains(key)) {
                    return true;
                }
            }
        }
        return super.hasOwnProperty(key);
    }

    @Override
    public void describe(CommandSender sender, @Nullable Set<String> ignoreProperties, @Nullable Set<String> overriddenProperties) {
        super.describe(sender, ignoreProperties, overriddenProperties);
        ProgressionPath path = getPath();
        if (path != null && pathOverridesAllowed()) {
            ConfigurationSection pathProperties = path.getProperties();
            if (pathProperties != null) {
                sender.sendMessage(ChatColor.GOLD + "Path Properties Override:");
                Set<String> keys = pathProperties.getKeys(false);
                for (String key : keys) {
                    Object value = pathProperties.get(key);
                    if (value != null && (ignoreProperties == null || !ignoreProperties.contains(key))) {
                        ChatColor propertyColor = ChatColor.YELLOW;
                        sender.sendMessage(propertyColor.toString() + key + ChatColor.GRAY + ": " + ChatColor.WHITE + describeProperty(value));
                    }
                }
            }
        }
    }

    @Override
    public int getMaxSpells() {
        return getInt("max_spells");
    }

    private ManaController getManaController() {
        String manaClass = null;
        BaseMagicProperties storage = getStorage("mana_max");
        if (storage == null) {
            storage = this;
        }
        if (storage instanceof MageClass) {
            manaClass = ((MageClass) storage).getKey();
        }
        return controller.getManaController(manaClass);
    }

    public String getLevelString(String templateName, float amount) {
        return controller.getMessages().getLevelString(getMessageKey(templateName), amount);
    }

    public String getLevelString(String templateName, float amount, float max) {
        return controller.getMessages().getLevelString(getMessageKey(templateName), amount, max);
    }

    protected String getPropertyString(String templateName, float value) {
        return getPropertyString(templateName, value, 1, false);
    }

    protected String getPropertyString(String templateName, float value, float max, boolean defaultStack) {
        String propertyTemplate = getBoolean("stack", defaultStack) ? "property_stack" : "property_value";
        if (value < 0) {
            propertyTemplate = propertyTemplate + "_negative";
        }
        return controller.getMessages().getPropertyString(getMessageKey(templateName), value, max, getMessageKey(propertyTemplate));
    }

    protected String formatPropertyString(String message, float value, float max) {
        String propertyTemplate = getBoolean("stack") ? "property_stack" : "property_value";
        if (value < 0) {
            propertyTemplate = propertyTemplate + "_negative";
        }
        return controller.getMessages().formatPropertyString(message, value, max, getMessage(propertyTemplate));
    }

    protected void addDamageTypeLore(String property, String propertyType, double amount, List<String> lore) {
        addDamageTypeLore(property, propertyType, amount, 1, lore);
    }

    protected void addDamageTypeLore(String property, String propertyType, double amount, double max, List<String> lore) {
        addDamageTypeLore(property, propertyType, amount, max, lore, null);
    }

    protected void addDamageTypeLore(String property, String propertyType, double amount, double max, List<String> lore, String unknownDefault) {
        if (amount != 0) {
            String prefix = getMessageKey("prefixes." + property);
            prefix = controller.getMessages().get(prefix, "");
            String templateKey = getMessageKey(property + "." + propertyType);
            String template;
            if (controller.getMessages().containsKey(templateKey)) {
                template = controller.getMessages().get(templateKey);
            } else {
                templateKey = getMessageKey(property + ".unknown");
                template = controller.getMessages().get(templateKey);
                String pretty = propertyType.substring(0, 1).toUpperCase() + propertyType.substring(1);
                template = template.replace("$type", pretty);
                if (unknownDefault != null && !unknownDefault.isEmpty()) {
                    // This is some special-case hackery, currently only used for enchantments
                    unknownDefault = WordUtils.capitalize(unknownDefault.toLowerCase().replace("_", " "));
                    template = template.replace("$name", unknownDefault);
                }
            }
            template = formatPropertyString(prefix + template, (float)amount, (float)max);
            ConfigurationUtils.addIfNotEmpty(template, lore);
        }
    }

    public void addPropertyLore(List<String> lore) {
        addPropertyLore(lore, false);
    }

    public void addPropertyLore(List<String> lore, boolean isSingleSpell) {
        if (usesMana() && effectiveManaMax > 0) {
            int manaMax = getManaMax();
            if (effectiveManaMax != manaMax) {
                String fullMessage = getLevelString("mana_amount_boosted", manaMax, controller.getMaxMana());
                ConfigurationUtils.addIfNotEmpty(fullMessage.replace("$mana", Integer.toString((int) Math.ceil(effectiveManaMax))), lore);
            } else {
                ConfigurationUtils.addIfNotEmpty(getLevelString("mana_amount", manaMax, controller.getMaxMana()), lore);
            }
            int manaRegeneration = getManaRegeneration();
            if (manaRegeneration > 0 && effectiveManaRegeneration > 0) {
                if (effectiveManaRegeneration != manaRegeneration) {
                    String fullMessage = getLevelString("mana_regeneration_boosted", manaRegeneration, controller.getMaxManaRegeneration());
                    ConfigurationUtils.addIfNotEmpty(fullMessage.replace("$mana", Integer.toString((int) Math.ceil(effectiveManaRegeneration))), lore);
                } else {
                    ConfigurationUtils.addIfNotEmpty(getLevelString("mana_regeneration", manaRegeneration, controller.getMaxManaRegeneration()), lore);
                }
            }
            float manaPerDamage = getFloat("mana_per_damage");
            if (manaPerDamage > 0) {
                ConfigurationUtils.addIfNotEmpty(getLevelString("mana_per_damage", manaPerDamage, controller.getMaxManaRegeneration()), lore);
            }
        }

        float blockChance = getFloat("block_chance");
        float blockReflectChance = getFloat("block_reflect_chance");
        if (blockReflectChance > 0) {
            ConfigurationUtils.addIfNotEmpty(getLevelString("reflect_chance", blockReflectChance), lore);
        } else if (blockChance != 0) {
            ConfigurationUtils.addIfNotEmpty(getLevelString("block_chance", blockChance), lore);
        }
        float manaMaxBoost = getManaMaxBoost();
        if (manaMaxBoost != 0) {
            ConfigurationUtils.addIfNotEmpty(getPropertyString("mana_boost", manaMaxBoost, 1, true), lore);
        }
        float manaRegenerationBoost = getManaRegenerationBoost();
        if (manaRegenerationBoost != 0) {
            ConfigurationUtils.addIfNotEmpty(getPropertyString("mana_regeneration_boost", manaRegenerationBoost, 1, true), lore);
        }

        float earnMultiplier = getFloat("earn_multiplier", getFloat("sp_multiplier", 1));
        if (earnMultiplier > 1) {
            String earnDescription = getPropertyString("earn_multiplier", earnMultiplier - 1);
            String earnType = getController().getMessages().get("currency.sp.name_short", "SP");
            earnDescription = earnDescription.replace("$type", earnType);
            ConfigurationUtils.addIfNotEmpty(earnDescription, lore);
        }
        addPotionEffectLore(lore);

        if (getBoolean("ignored_by_mobs")) {
            ConfigurationUtils.addIfNotEmpty(getMessage("ignored_by_mobs"), lore);
        }

        float consumeReduction = getFloat("consume_reduction");
        if (consumeReduction != 0 && !isSingleSpell) ConfigurationUtils.addIfNotEmpty(getPropertyString("consume_reduction", consumeReduction), lore);

        float costReduction = getFloat("cost_reduction");
        if (costReduction != 0 && !isSingleSpell) ConfigurationUtils.addIfNotEmpty(getPropertyString("cost_reduction", costReduction), lore);
        float cooldownReduction = getFloat("cooldown_reduction");
        if (cooldownReduction != 0 && !isSingleSpell) ConfigurationUtils.addIfNotEmpty(getPropertyString("cooldown_reduction", cooldownReduction), lore);
        float power = getFloat("power");
        if (power > 0) ConfigurationUtils.addIfNotEmpty(getLevelString("power", power), lore);
        boolean superProtected = getBoolean("protected");
        if (superProtected) {
            ConfigurationUtils.addIfNotEmpty(getMessage("super_protected"), lore);
        } else {
            Map<String, Double> protection = null;
            ConfigurationSection protectionConfig = getConfigurationSection("protection");
            if (protectionConfig != null) {
                protection = new HashMap<>();
                for (String protectionKey : protectionConfig.getKeys(false)) {
                    protection.put(protectionKey, protectionConfig.getDouble(protectionKey));
                }
            }
            if (protection != null) {
                for (Map.Entry<String, Double> entry : protection.entrySet()) {
                    String protectionType = entry.getKey();
                    double amount = entry.getValue();
                    addDamageTypeLore("protection", protectionType, amount, lore);
                }
            }
        }
        boolean superPowered = getBoolean("powered");
        if (superPowered) {
            ConfigurationUtils.addIfNotEmpty(getMessage("super_powered"), lore);
        }

        ConfigurationSection weaknessConfig = getConfigurationSection("weakness");
        if (weaknessConfig != null) {
            Set<String> keys = weaknessConfig.getKeys(false);
            for (String key : keys) {
                addDamageTypeLore("weakness", key, weaknessConfig.getDouble(key), lore);
            }
        }

        ConfigurationSection strengthConfig = getConfigurationSection("strength");
        if (strengthConfig != null) {
            Set<String> keys = strengthConfig.getKeys(false);
            for (String key : keys) {
                addDamageTypeLore("strength", key, strengthConfig.getDouble(key), lore);
            }
        }
        addAttributeLore(lore);
    }

    private void addAttributeLore(List<String> lore) {
        // TODO: This should take operation into account rather than using "stack" only
        // This should also, ideally, display lore differently for wands with attributes that are
        // "boosted" by upgrades.
        // So probably we need to group all modifiers by attribute and operation, show any that have more than one
        // modifiers as "boosted", properly stacking the numbers to display only once.
        List<MagicAttributeModifier> modifiers = getAttributes();
        if (modifiers != null) {
            // Don't bother with the lore at all if the template has been blanked out
            String template = getMessage("attributes");
            if (!template.isEmpty()) {
                for (MagicAttributeModifier modifier : modifiers) {
                    String key = modifier.getAttribute();
                    String label = controller.getMessages().get("attributes." + key + ".name", key);

                    // We  only display attributes as integers for now
                    int value = (int)modifier.getValue();
                    if (value == 0) continue;

                    float max = 1;
                    MagicAttribute attribute = controller.getAttribute(key);
                    if (attribute != null) {
                        if (attribute.isHidden()) continue;
                        Double maxValue = attribute.getMax();
                        if (maxValue != null) {
                            max = (float)(double)maxValue;
                        }
                    }

                    String templateName = "attribute." + key;
                    if (controller.getMessages().get(getMessageKey(templateName), "").isEmpty()) {
                        templateName = "attributes";
                    }
                    label = getPropertyString(templateName, value, max, true).replace("$attribute", label);
                    lore.add(label);
                }
            }
        }
    }

    protected String getAttributeLore() {
        List<String> lore = new ArrayList<>();
        addAttributeLore(lore);
        return StringUtils.join(lore, "\n");
    }

    protected void addPotionEffectLore(List<String> lore) {
        for (Map.Entry<PotionEffectType, Integer> effect : getPotionEffects().entrySet()) {
            ConfigurationUtils.addIfNotEmpty(describePotionEffect(effect.getKey(), effect.getValue()), lore);
        }
    }

    protected String getPotionEffectLore() {
        List<String> lore = new ArrayList<>();
        addPotionEffectLore(lore);
        return StringUtils.join(lore, "\n");
    }

    protected List<MagicAttributeModifier> addAttributes(List<MagicAttributeModifier> modifiers, ConfigurationSection attributesSection) {
        if (attributesSection == null) return modifiers;
        Set<String> keys = attributesSection.getKeys(false);
        if (keys.isEmpty()) return modifiers;
        if (modifiers == null) {
            modifiers = new ArrayList<>();
        }
        for (String key : keys) {
            try {
                if (attributesSection.isConfigurationSection(key)) {
                    ConfigurationSection attributeConfig = attributesSection.getConfigurationSection(key);
                    modifiers.add(new MagicAttributeModifier(key, attributeConfig, 0));
                } else {
                    modifiers.add(new MagicAttributeModifier(key, attributesSection.getDouble(key)));
                }
            } catch (Exception ex) {
                controller.getLogger().warning("Invalid attribute definition in " + key);
            }
        }
        return modifiers;
    }

    @Nullable
    public List<MagicAttributeModifier> getAttributes() {
        ConfigurationSection attributesSection = getConfigurationSection("attributes");
        return addAttributes(null, attributesSection);
    }
}
