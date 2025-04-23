package com.elmakers.mine.bukkit.utility.platform.base.entity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;

import com.elmakers.mine.bukkit.api.block.MaterialAndData;
import com.elmakers.mine.bukkit.api.magic.MageController;
import com.elmakers.mine.bukkit.entity.EntityExtraData;
import com.elmakers.mine.bukkit.entity.SpawnedEntityExtraData;
import com.elmakers.mine.bukkit.utility.ConfigUtils;
import com.elmakers.mine.bukkit.utility.platform.CompatibilityUtils;
import com.elmakers.mine.bukkit.utility.platform.PlatformInterpreter;

public class EntityFallingBlockData extends EntityExtraData {
    @Nullable
    private MaterialAndData material;
    private boolean dropItem;
    private Boolean hurtEntities;

    public EntityFallingBlockData(ConfigurationSection configuration, MageController controller) {
        String materialKey = configuration.getString("material");
        if (materialKey != null && !materialKey.isEmpty()) {
            material = controller.createMaterialAndData(materialKey);
        }
        dropItem = configuration.getBoolean("drop_items", false);
        hurtEntities = ConfigUtils.getOptionalBoolean(configuration, "hurt_entities");
    }

    public EntityFallingBlockData(Entity entity, MageController controller) {
        CompatibilityUtils compatibilityUtils = getPlatform().getCompatibilityUtils();
        if (entity instanceof FallingBlock) {
            FallingBlock fallingBlock = (FallingBlock)entity;
            Material material = compatibilityUtils.getMaterial(fallingBlock);
            String blockData = compatibilityUtils.getBlockData(fallingBlock);
            if (blockData != null) {
                this.material = controller.createMaterialAndData(material, blockData);
            } else {
                byte data = compatibilityUtils.getLegacyBlockData(fallingBlock);
                this.material = controller.createMaterialAndData(material, data);
            }
            dropItem = fallingBlock.getDropItem();
            hurtEntities = fallingBlock.canHurtEntities();
        }
    }

    @Override
    public void apply(Entity entity) {
        if (entity instanceof FallingBlock) {
            FallingBlock fallingBlock = (FallingBlock)entity;
            fallingBlock.setDropItem(dropItem);
            if (hurtEntities != null) fallingBlock.setHurtEntities(hurtEntities);
        }
    }

    @Override
    public void setMaterialAndData(@Nonnull MaterialAndData material) {
        this.material = material;
    }

    @Nullable
    @Override
    public MaterialAndData getMaterialAndData() {
        return material;
    }

    public byte getMaterialData() {
        if (material == null) {
            return 0;
        }

        Byte data = material.getBlockData();
        return data == null ? 0 : data;
    }

    @Nullable
    public Material getMaterial() {
        return material == null ? null : material.getMaterial();
    }

    @Nullable
    public String getBlockData() {
        return material == null ? null : material.getModernBlockData();
    }

    @Override
    public SpawnedEntityExtraData spawn(Location location) {
        String blockDataString = getBlockData();
        if (blockDataString != null && !blockDataString.isEmpty()) {
            BlockData blockData = PlatformInterpreter.getPlatform().getPlugin().getServer().createBlockData(blockDataString);
            Entity newEntity = location.getWorld().spawnFallingBlock(location, blockData);
            return new SpawnedEntityExtraData(newEntity, true);
        }
        return super.spawn(location);
    }
}
