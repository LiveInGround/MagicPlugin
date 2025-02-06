package com.elmakers.mine.bukkit.action.builtin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;

import com.elmakers.mine.bukkit.api.action.CastContext;
import com.elmakers.mine.bukkit.api.magic.CasterProperties;
import com.elmakers.mine.bukkit.api.magic.Mage;
import com.elmakers.mine.bukkit.api.magic.MageController;
import com.elmakers.mine.bukkit.api.magic.ProgressionPath;
import com.elmakers.mine.bukkit.api.spell.CastingCost;
import com.elmakers.mine.bukkit.api.spell.PrerequisiteSpell;
import com.elmakers.mine.bukkit.api.spell.SpellResult;
import com.elmakers.mine.bukkit.api.spell.SpellTemplate;
import com.elmakers.mine.bukkit.utility.ConfigurationUtils;
import com.elmakers.mine.bukkit.utility.StringUtils;

public class ShopAction extends SelectorAction {
    private boolean showNoPermission;
    private boolean showUncastable;
    private boolean checkLimits;
    private boolean showMissingRequirements;

    @Override
    public void prepare(CastContext context, ConfigurationSection parameters) {
        boolean showPath = parameters.getBoolean("show_path_spells", false);
        boolean showExtra = parameters.getBoolean("show_extra_spells", false);
        boolean showRequired = parameters.getBoolean("show_required_spells", false);
        boolean showFree = parameters.getBoolean("show_free", false);
        boolean addSellShop = parameters.getBoolean("add_sell_shop", false);
        showMissingRequirements = parameters.getBoolean("show_missing_requirements", false);
        showUncastable = parameters.getBoolean("show_uncastable", true);
        showNoPermission = parameters.getBoolean("show_no_permission", false);
        checkLimits = parameters.getBoolean("check_max_spells", showPath);

        // Don't load items as defaults
        Object itemDefaults = parameters.get("items");
        parameters.set("items", null);

        // Sell shop overrides
        if (!parameters.contains("cost_type") && (showPath || showExtra || showRequired || parameters.contains("spells"))) {
            parameters.set("cost_type", "sp");
        }

        // Apply name and lore to items by default
        if (!parameters.contains("apply_lore_to_item")) {
            parameters.set("apply_lore_to_item", true);
        }
        if (!parameters.contains("apply_name_to_item")) {
            parameters.set("apply_name_to_item", true);
        }

        // Allow adding options into this shop
        // This has to be done after adding all of the shop items, though, so we will
        // save it for later and clear it for now
        ConfigurationSection addOptions = parameters.getConfigurationSection("add_options");
        if (addOptions != null) {
            parameters.set("add_options", null);
        }

        super.prepare(context, parameters);

        // Restore items list. This is kind of messy, but so is this whole action.
        parameters.set("items", itemDefaults);
        loadItems(context, parameters, "items", false);
        loadItems(context, parameters, "spells", true);

        // Auto-populate spells
        Mage mage = context.getMage();
        CasterProperties caster = mage.getActiveProperties();
        ProgressionPath currentPath = caster.getPath();
        if (currentPath != null) {
            List<String> spellKeys = new ArrayList<>();
            if (showPath) {
                spellKeys.addAll(currentPath.getSpells());
            }
            if (showRequired) {
                spellKeys.addAll(currentPath.getRequiredSpells());
            }
            loadSpells(context, spellKeys, showFree, false);

            if (showExtra) {
                loadSpells(context, currentPath.getExtraSpells(), showFree, true);
            }
        }

        if (addSellShop) {
            boolean isSellShop = parameters.getBoolean("sell");
            int startNextLine = ((int)Math.floor(getNumSlots() / 9)) * 9;
            int buttonSlot = startNextLine + 8;
            ConfigurationSection sellShopConfig = ConfigurationUtils.newConfigurationSection();
            sellShopConfig.set("slot", buttonSlot);
            sellShopConfig.set("auto_close", true);
            sellShopConfig.set("selected", "");
            sellShopConfig.set("cast_spell", context.getSpell().getSpellKey().getKey());
            if (!isSellShop) {
                ConfigurationSection spellParameters = sellShopConfig.createSection("cast_spell_parameters");
                spellParameters.set("sell", true);
                spellParameters.set("title", getMessage("sell_title"));
                sellShopConfig.set("name", getMessage("sell_icon_name"));
                sellShopConfig.set("icon", parameters.getString("sell_icon", "yellow_wool"));
            } else {
                sellShopConfig.set("name", getMessage("buy_icon_name"));
                sellShopConfig.set("icon", parameters.getString("buy_icon", "green_wool"));
            }

            List<ConfigurationSection> buttonConfigs = new ArrayList<>();
            buttonConfigs.add(sellShopConfig);
            loadOptions(buttonConfigs);
        }

        // Add any extra options after all shop items
        if (addOptions != null) {
            Collection<ConfigurationSection> addOptionConfigs = new ArrayList<>();
            Set<String> keys = addOptions.getKeys(false);
            for (String key : keys) {
                ConfigurationSection optionConfig = addOptions.getConfigurationSection(key);
                addOptionConfigs.add(optionConfig);
            }
            loadOptions(addOptionConfigs, parameters);
        }
    }

    @Override
    public SpellResult start(CastContext context) {
        if (checkLimits) {
            Mage mage = context.getMage();
            CasterProperties caster = mage.getActiveProperties();
            int maxSpells = caster.getMaxSpells();
            if (maxSpells > 0 && caster.getSpells().size() >= maxSpells) {
                context.showMessage("max_spells", getDefaultMessage(context, "max_spells"));
                return SpellResult.NO_TARGET;
            }
        }
        return super.start(context);
    }

    protected void loadSpells(CastContext context, Collection<String> spellKeys, boolean showFree, boolean isExtra) {
        Mage mage = context.getMage();
        CasterProperties caster = mage.getActiveProperties();
        MageController controller = context.getController();
        List<SpellTemplate> spells = new ArrayList<>();
        for (String spellKey : spellKeys) {
            SpellTemplate spell = controller.getSpellTemplate(spellKey);
            if (spell == null) {
                mage.sendDebugMessage(ChatColor.GRAY + " Skipping " + spellKey + ", is invalid", 3);
                continue;
            }
            if (caster.hasSpell(spellKey) && (spell.getWorth() > 0 || showFree)) {
                mage.sendDebugMessage(ChatColor.GRAY + " Skipping " + spellKey + ", already have it", 3);
                continue;
            }
            if (spell.getWorth() <= 0 && !showFree) {
                mage.sendDebugMessage(ChatColor.GRAY + " Skipping " + spellKey + ", is free", 3);
                continue;
            }
            if (!showNoPermission && !spell.hasCastPermission(mage.getCommandSender())) {
                mage.sendDebugMessage(ChatColor.YELLOW + " Skipping " + spellKey + ", no permission", 3);
                continue;
            }
            if (!showUncastable) {
                int manaMax = caster.getManaMax();
                Collection<CastingCost> costs = spell.getCosts();
                if (costs != null) {
                    boolean skip = false;
                    for (CastingCost cost : costs) {
                        int mana = cost.getMana(mage);
                        if (mana > manaMax) {
                            mage.sendDebugMessage(ChatColor.YELLOW + " Skipping " + spellKey + ", not enough mana to cast", 3);
                            skip = true;
                            break;
                        }
                    }
                    if (skip) {
                        continue;
                    }
                }
            }
            if (!showMissingRequirements) {
                Collection<PrerequisiteSpell> missingSpells = PrerequisiteSpell.getMissingRequirements(caster, spell);
                if (!missingSpells.isEmpty()) {
                    mage.sendDebugMessage(ChatColor.YELLOW + " Skipping " + spellKey + ", missing requirements: " + StringUtils.join(missingSpells, ","), 3);
                    continue;
                }
            }

            spells.add(spell);
        }

        if (spells.size() == 0) {
            return;
        }

        mage.sendDebugMessage(ChatColor.GOLD + "Spells to buy: " + spells.size(), 2);

        Collections.sort(spells, new Comparator<SpellTemplate>() {
            @Override
            public int compare(SpellTemplate spell1, SpellTemplate spell2) {
                return Double.compare(spell1.getWorth(), spell2.getWorth());
            }
        });

        // Add padding
        List<ConfigurationSection> pathSpellConfigs = new ArrayList<>();
        if (isExtra) {
            int maxSlot = getNumSlots();
            int paddedSlot = 8 - (maxSlot + 8) % 9;
            for (int i = 0; i < paddedSlot; i++) {
                ConfigurationSection emptyConfig = ConfigurationUtils.newConfigurationSection();
                emptyConfig.set("item", "none");
                pathSpellConfigs.add(emptyConfig);
            }
        }

        String addLore = isExtra ? getMessage("extra_spell") : null;
        for (SpellTemplate spell : spells) {
            ConfigurationSection spellConfig = ConfigurationUtils.newConfigurationSection();
            spellConfig.set("item", "spell:" + spell.getKey());
            if (addLore != null) {
                spellConfig.set("description", addLore);
            }
            pathSpellConfigs.add(spellConfig);
        }
        loadOptions(pathSpellConfigs);
    }

    protected void loadItems(CastContext context, ConfigurationSection parameters, String key, boolean filterSpells) {
        if (parameters.contains(key)) {
            List<ConfigurationSection> itemConfigs = new ArrayList<>();
            if (parameters.isConfigurationSection(key)) {
                ConfigurationSection itemSection = parameters.getConfigurationSection(key);
                Set<String> itemKeys = itemSection.getKeys(false);
                for (String itemKey : itemKeys) {
                    ConfigurationSection itemConfig = ConfigurationUtils.newConfigurationSection();
                    itemConfig.set("item", itemKey);
                    itemConfig.set("cost", itemSection.get(itemKey));
                    itemConfigs.add(itemConfig);
                }
            } else {
                List<?> objects = parameters.getList(key);
                for (Object object : objects) {
                    if (object instanceof ConfigurationSection) {
                        itemConfigs.add((ConfigurationSection)object);
                    } else if (object instanceof Map) {
                         itemConfigs.add(ConfigurationUtils.toConfigurationSection(parameters, (Map<?, ?>)object));
                    } else if (object instanceof String) {
                        ConfigurationSection itemConfig = ConfigurationUtils.newConfigurationSection();
                        if (object.equals("none")) {
                            itemConfig.set("placeholder", true);
                        } else {
                            itemConfig.set("item", object);
                        }
                        itemConfigs.add(itemConfig);
                    } else {
                        context.getLogger().warning("Invalid item in shop config: " + object);
                    }
                }
            }

            if (filterSpells) {
                CasterProperties caster = context.getMage().getActiveProperties();
                Iterator<ConfigurationSection> it = itemConfigs.iterator();
                while (it.hasNext()) {
                    ConfigurationSection config = it.next();
                    String spellName = config.getString("item");
                    if (spellName != null && caster.hasSpell(spellName)) {
                        it.remove();
                    } else {
                        config.set("item", "spell:" + spellName);
                    }
                }
            }

            loadOptions(itemConfigs);
        }
    }

    @Override
    protected String getDefaultMessageSection() {
        return "shops";
    }
}
