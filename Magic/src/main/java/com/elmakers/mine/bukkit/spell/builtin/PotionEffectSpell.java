package com.elmakers.mine.bukkit.spell.builtin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.elmakers.mine.bukkit.api.magic.Mage;
import com.elmakers.mine.bukkit.api.spell.SpellResult;
import com.elmakers.mine.bukkit.spell.UndoableSpell;
import com.elmakers.mine.bukkit.utility.CompatibilityLib;
import com.elmakers.mine.bukkit.utility.Target;

@Deprecated
public class PotionEffectSpell extends UndoableSpell
{
    @Override
    public SpellResult onCast(ConfigurationSection parameters)
    {
        Target target = getTarget();
        if (!target.hasTarget())
        {
            return SpellResult.NO_TARGET;
        }

        List<LivingEntity> targetEntities = new ArrayList<>();

        Entity targetedEntity = target.getEntity();
        if (target.hasEntity() && targetedEntity instanceof LivingEntity) {
            targetEntities.add((LivingEntity)targetedEntity);
        }

        int radius = parameters.getInt("radius", 0);
        radius = (int)(mage.getRadiusMultiplier() * radius);

        if (radius > 0) {
            Collection<Entity> entities = CompatibilityLib.getCompatibilityUtils().getNearbyEntities(target.getLocation(), radius, radius, radius);
            for (Entity entity : entities) {
                if (entity instanceof LivingEntity && entity != targetedEntity && entity != mage.getEntity()) {
                    targetEntities.add((LivingEntity)entity);
                }
            }
        }

        if (targetEntities.size() == 0) {
            return SpellResult.NO_TARGET;
        }

        int fallProtection = parameters.getInt("fall_protection", 0);

        Integer duration = null;
        if (parameters.contains("duration")) {
            duration = parameters.getInt("duration");
        }
        Collection<PotionEffect> effects = getPotionEffects(parameters, duration);
        for (LivingEntity targetEntity : targetEntities) {
            Mage targetMage = controller.isMage(targetEntity) ? controller.getMage(targetEntity) : null;

            if (targetMage != null && fallProtection > 0) {
                targetMage.enableFallProtection(fallProtection);
            }

            // Check for superprotected targets
            if (targetEntity != mage.getEntity() && isSuperProtected(targetEntity)) {
                continue;
            }
            if (targetMage != null && parameters.getBoolean("deactivate_target_mage")) {
                targetMage.deactivateAllSpells(true, false);
            }

            if (targetEntity instanceof Player && parameters.getBoolean("feed", false)) {
                Player p = (Player)targetEntity;
                p.setExhaustion(0);
                p.setFoodLevel(20);
            }
            if (parameters.getBoolean("cure", false)) {
                Set<PotionEffectType> negativeEffects = CompatibilityLib.getCompatibilityUtils().getNegativeEffects();
                Collection<PotionEffect> currentEffects = targetEntity.getActivePotionEffects();
                for (PotionEffect effect : currentEffects) {
                    if (negativeEffects.contains(effect.getType())) {
                        targetEntity.removePotionEffect(effect.getType());
                    }
                }
            }

            if (parameters.contains("heal")) {
                registerModified(targetEntity);
                double health = targetEntity.getHealth() + parameters.getDouble("heal");
                targetEntity.setHealth(Math.min(health, CompatibilityLib.getCompatibilityUtils().getMaxHealth(targetEntity)));
            } else if (parameters.contains("heal_percentage")) {
                registerModified(targetEntity);
                double health = targetEntity.getHealth() + CompatibilityLib.getCompatibilityUtils().getMaxHealth(targetEntity) * parameters.getDouble("heal_percentage");
                targetEntity.setHealth(Math.min(health, CompatibilityLib.getCompatibilityUtils().getMaxHealth(targetEntity)));
            } else if (parameters.contains("damage")) {
                registerModified(targetEntity);
                CompatibilityLib.getCompatibilityUtils().magicDamage(targetEntity, parameters.getDouble("damage") * mage.getDamageMultiplier(), mage.getEntity());
            } else {
                registerPotionEffects(targetEntity);
            }

            if (parameters.contains("fire")) {
                registerModified(targetEntity);
                targetEntity.setFireTicks(parameters.getInt("fire"));
            }

            CompatibilityLib.getCompatibilityUtils().applyPotionEffects(targetEntity, effects);

            if (parameters.contains("remove_effects")) {
                List<String> removeKeys = parameters.getStringList("remove_effects");
                for (String removeKey : removeKeys) {
                    PotionEffectType removeType = PotionEffectType.getByName(removeKey);
                    targetEntity.removePotionEffect(removeType);
                }
            }
        }
        registerForUndo();
        return SpellResult.CAST;
    }
}
