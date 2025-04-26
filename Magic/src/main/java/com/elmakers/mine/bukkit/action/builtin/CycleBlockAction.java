package com.elmakers.mine.bukkit.action.builtin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

import com.elmakers.mine.bukkit.action.BaseSpellAction;
import com.elmakers.mine.bukkit.api.action.CastContext;
import com.elmakers.mine.bukkit.api.magic.MageController;
import com.elmakers.mine.bukkit.api.magic.MaterialSet;
import com.elmakers.mine.bukkit.api.spell.Spell;
import com.elmakers.mine.bukkit.api.spell.SpellResult;
import com.elmakers.mine.bukkit.block.DefaultMaterials;
import com.elmakers.mine.bukkit.block.MaterialAndData;

public class CycleBlockAction extends BaseSpellAction {

    private Map<MaterialAndData, MaterialAndData> materials = new HashMap<>();

    @Override
    public void initialize(Spell spell, ConfigurationSection parameters) {
        super.initialize(spell, parameters);
        @SuppressWarnings("unchecked")
        List<List<String>> allMaterials = (List<List<String>>)parameters.getList("materials");
        this.materials.clear();
        if (allMaterials != null) {
            for (List<String> list : allMaterials) {
                List<MaterialAndData> materialList = new ArrayList<>();
                for (String material : list) {
                    MaterialAndData entry = new MaterialAndData(material);
                    if (entry.isValid()) {
                        materialList.add(entry);
                    }
                }
                if (materialList.size() > 1) {
                    for (int i = 0; i < materialList.size(); i++) {
                        materials.put(materialList.get(i), materialList.get((i + 1) % materialList.size()));
                    }
                }
            }
        }

        MageController controller = spell.getController();
        List<String> materialSets = parameters.getStringList("material_sets");
        if (materialSets != null) {
            for (String materialSetKey : materialSets) {
                MaterialSet materialSet = controller.getMaterialSetManager().getMaterialSet(materialSetKey);
                if (materialSet == null) {
                    controller.getLogger().warning("Invalid material set in spell " + spell.getKey() + ": " + materialSetKey);
                    continue;
                }
                List<MaterialAndData> materialsToCycle = new ArrayList<>();
                Collection<Material> materialsInSet = materialSet.getMaterials();
                for (Material material : materialsInSet) {
                    materialsToCycle.add(new MaterialAndData(material));
                }
                if (materialsToCycle.size() > 1) {
                    for (int i = 0; i < materialsToCycle.size(); i++) {
                        materials.put(materialsToCycle.get(i), materialsToCycle.get((i + 1) % materialsToCycle.size()));
                    }
                }
            }
        }

        if (parameters.getBoolean("cycle_colors", false)) {
            Collection<Map<DyeColor, MaterialAndData>> colorBlocks = DefaultMaterials.getInstance().getAllColorBlocks();
            for (Map<DyeColor, MaterialAndData> colorMap : colorBlocks) {
                List<MaterialAndData> colorList = new ArrayList<>();
                colorList.addAll(colorMap.values());
                for (int i = 0; i < colorList.size(); i++) {
                    materials.put(colorList.get(i), colorList.get((i + 1) % colorList.size()));
                }
            }
        }
        if (parameters.getBoolean("cycle_variants", false)) {
            Collection<List<Material>> variantBlocks = DefaultMaterials.getInstance().getAllVariants();
            for (List<Material> variantList : variantBlocks) {
                for (int i = 0; i < variantList.size(); i++) {
                    materials.put(new MaterialAndData(variantList.get(i)), new MaterialAndData(variantList.get((i + 1) % variantList.size())));
                }
            }
        }

        if (this.materials.isEmpty()) {
            spell.getController().getLogger().warning("CycleBlock action missing materials list");
        }
    }

    @Override
    public SpellResult perform(CastContext context) {
        Block block = context.getTargetBlock();
        if (!context.hasBuildPermission(block)) {
            return SpellResult.INSUFFICIENT_PERMISSION;
        }

        MaterialAndData targetMaterial = new MaterialAndData(block);
        MaterialAndData newMaterial = materials.get(targetMaterial);
        if (newMaterial == null) {
            // So it seems colored blocks still set their data values even though they are using
            // separate materials in 1.13 ?
            targetMaterial.setData((short)0);
            newMaterial = materials.get(targetMaterial);
        }
        if (newMaterial == null) {
            return SpellResult.NO_TARGET;
        }

        context.registerForUndo(block);
        context.getMage().sendDebugMessage("Cycling " + block.getType() + " to " + newMaterial);
        newMaterial.modify(block);
        return SpellResult.CAST;
    }

    @Override
    public boolean requiresBuildPermission() {
        return true;
    }

    @Override
    public boolean requiresTarget() {
        return true;
    }

    @Override
    public boolean isUndoable() {
        return true;
    }
}
