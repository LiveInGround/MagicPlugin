package com.elmakers.mine.bukkit.utility.platform.modern2;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import com.elmakers.mine.bukkit.api.magic.MageController;
import com.elmakers.mine.bukkit.entity.EntityExtraData;
import com.elmakers.mine.bukkit.utility.platform.Platform;
import com.elmakers.mine.bukkit.utility.platform.modern2.entity.EntityAxolotlData;
import com.elmakers.mine.bukkit.utility.platform.modern2.entity.EntityEnderSignalData;
import com.elmakers.mine.bukkit.utility.platform.modern2.entity.EntityGoatData;

public class Modern2EntityUtils extends com.elmakers.mine.bukkit.utility.platform.modern.ModernEntityUtils  {
    public Modern2EntityUtils(final Platform platform) {
        super(platform);
    }

    @Override
    public EntityExtraData getExtraData(MageController controller, Entity entity) {
        switch (entity.getType()) {
            case GOAT:
                return new EntityGoatData(entity);
            case AXOLOTL:
                return new EntityAxolotlData(entity);
            case EYE_OF_ENDER:
                return new EntityEnderSignalData(entity);
            default:
                return super.getExtraData(controller, entity);
        }
    }

    @Override
    public EntityExtraData getExtraData(MageController controller, EntityType type, ConfigurationSection parameters) {
        switch (type) {
            case GOAT:
                return new EntityGoatData(parameters, controller);
            case AXOLOTL:
                return new EntityAxolotlData(parameters, controller);
            case EYE_OF_ENDER:
                return new EntityEnderSignalData(parameters, controller);
            default:
                return super.getExtraData(controller, type, parameters);
        }
    }
}
