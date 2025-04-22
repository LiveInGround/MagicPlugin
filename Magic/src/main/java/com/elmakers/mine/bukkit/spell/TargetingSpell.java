package com.elmakers.mine.bukkit.spell;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.elmakers.mine.bukkit.api.action.CastContext;
import com.elmakers.mine.bukkit.api.entity.EntityData;
import com.elmakers.mine.bukkit.api.magic.Mage;
import com.elmakers.mine.bukkit.api.magic.MaterialSet;
import com.elmakers.mine.bukkit.api.magic.MaterialSetManager;
import com.elmakers.mine.bukkit.api.spell.TargetType;
import com.elmakers.mine.bukkit.block.DefaultMaterials;
import com.elmakers.mine.bukkit.block.MaterialAndData;
import com.elmakers.mine.bukkit.block.MaterialBrush;
import com.elmakers.mine.bukkit.magic.MagicMetaKeys;
import com.elmakers.mine.bukkit.materials.MaterialSets;
import com.elmakers.mine.bukkit.tasks.PlaySpellEffectsTask;
import com.elmakers.mine.bukkit.utility.CompatibilityLib;
import com.elmakers.mine.bukkit.utility.ConfigurationUtils;
import com.elmakers.mine.bukkit.utility.Target;
import com.elmakers.mine.bukkit.utility.Targeting;

public class TargetingSpell extends BaseSpell {
    // This differs from CompatibilityUtils.MAX_ENTITY_RANGE,
    // block targeting can theoretically go farther
    private static final int  MAX_RANGE  = 511;
    private static Set<GameMode> defaultTargetGameModes = new HashSet<>(Arrays.asList(GameMode.SURVIVAL, GameMode.ADVENTURE));
    public static Plugin HOLOGRAM_PLUGIN = null;

    private Targeting                           targeting               = new Targeting();

    private Location                            targetLocation          = null;
    protected Location                          targetLocation2          = null;
    private Location                            selectedLocation        = null;
    private Entity                                targetEntity            = null;

    private boolean                                targetNPCs                = false;
    private boolean                                targetPets                = false;
    private boolean                                targetArmorStands        = false;
    private boolean                                targetInvisible            = true;
    private boolean                                targetVanished            = false;
    private boolean                                targetUnknown            = true;
    private boolean                                targetNoDamageTicks            = true;
    private String                                 targetPermission         = null;
    private Set<GameMode>                       targetGameModes         = null;
    private boolean                             targetTamed             = true;
    private boolean                             targetMount             = false;
    private String                              targetDisplayName       = null;
    private String                              ignoreDisplayName       = null;
    protected Class<?>                          targetEntityType        = null;
    protected Set<EntityType>                   targetEntityTypes       = null;
    protected Set<EntityType>                   ignoreEntityTypes       = null;
    protected Set<PotionEffectType>             targetPotionEffectTypes = null;
    protected Set<PotionEffectType>             ignorePotionEffectTypes = null;
    protected Material                          targetContents          = null;
    protected double                             targetBreakables        = 0;
    protected boolean                           instantBlockEffects     = false;
    private double                              range                   = 0;

    private boolean                             checkProtection         = false;
    private int                                 damageResistanceProtection = 0;

    private boolean                             allowMaxRange           = false;

    private @Nonnull MaterialSet                targetThroughMaterials  = MaterialSets.empty();
    private @Nonnull MaterialSet                targetableMaterials     = MaterialSets.wildcard();
    private @Nonnull MaterialSet                reflectiveMaterials     = MaterialSets.empty();
    private boolean                             reverseTargeting        = false;
    private boolean                             originAtTarget          = false;

    protected void initializeTargeting()
    {
        targeting.reset();
        reverseTargeting = false;
        targetLocation = null;
        targetLocation2 = null;
    }

    @Override
    public String getMessage(String messageKey, String def) {
        String message = super.getMessage(messageKey, def);

        // Escape targeting parameters, but don't stomp on variables
        if (!getVariables().contains("target") && !mage.getVariables().contains("target") && message.contains("$target")) {
            String useTargetName = null;
            if (currentCast != null) {
                useTargetName = currentCast.getTargetName();
            }
            if (useTargetName == null) {
                Target target = targeting.getTarget();
                if (target != null) {
                    if (target.hasEntity() && getTargetType() != TargetType.BLOCK) {
                        useTargetName = controller.getEntityDisplayName(target.getEntity());
                    } else if (target.isValid() && getTargetType() != TargetType.OTHER_ENTITY && getTargetType() != TargetType.ANY_ENTITY) {
                        MaterialAndData material = target.getTargetedMaterial();
                        if (material != null)
                        {
                            useTargetName = material.getName(controller.getMessages());
                        }
                    }
                }
            }
            if (useTargetName == null) {
                message = message.replaceAll("\\$target(?!_)", "Nothing");
            } else {
                message = message.replaceAll("\\$target(?!_)", useTargetName);
            }
        }

        return message;
    }

    @Deprecated // Material
    public boolean isReflective(Material mat) {
        return reflectiveMaterials.testMaterial(mat);
    }

    public boolean isReflective(Block block) {
        return reflectiveMaterials.testBlock(block);
    }

    public boolean isTargetable(CastContext context, Block block) {
        if (targetBreakables > 0 && context.isBreakable(block)) {
            return true;
        }

        return isTargetable(block);
    }

    public boolean isTargetable(Block block) {
        if (!allowPassThrough(block)) {
            return true;
        }

        boolean targetThrough = targetThroughMaterials.testBlock(block);
        if (reverseTargeting) {
            return targetThrough;
        } else {
            return !targetThrough && targetableMaterials.testBlock(block);
        }
    }

    public void setReverseTargeting(boolean reverse)
    {
        reverseTargeting = reverse;
    }

    public void setTargetSpaceRequired()
    {
        targeting.setTargetSpaceRequired(true);
    }

    public void setTargetMinOffset(int offset) {
        targeting.setTargetMinOffset(offset);
    }

    public void setTarget(Location location) {
        targeting.targetBlock(getEyeLocation(), location == null ? null : location.getBlock());
    }

    public void setTargetingHeight(int offset) {
        targeting.setYOffset(offset);
    }

    public TargetType getTargetType()
    {
        return targeting.getTargetType();
    }

    public Block getPreviousBlock() {
        return targeting.getPreviousBlock();
    }

    public Block getPreviousPreviousBlock() {
        return targeting.getPreviousPreviousBlock();
    }

    public void retarget(double range, double fov, double closeRange, double closeFOV, boolean useHitbox, int yOffset, boolean targetSpaceRequired, int targetMinOffset) {
        initializeTargeting();
        this.range = range;
        targeting.setYOffset(yOffset);
        targeting.setTargetSpaceRequired(targetSpaceRequired);
        targeting.setTargetMinOffset(targetMinOffset);
        targeting.setFOV(fov);
        targeting.setCloseRange(closeFOV);
        targeting.setCloseFOV(closeRange);
        targeting.setUseHitbox(useHitbox);
        target();
    }

    public void retarget(CastContext context, double range, double fov, double closeRange, double closeFOV, boolean useHitbox) {
        initializeTargeting();
        this.range = range;
        targeting.setFOV(fov);
        targeting.setCloseRange(closeFOV);
        targeting.setCloseFOV(closeRange);
        targeting.setUseHitbox(useHitbox);
        target(context);
    }

    public void target(CastContext castContext) {
        if (!targeting.hasTarget())
        {
            getTarget(castContext);
        }
    }


    @Override
    public void target()
    {
        target(currentCast);
    }

    protected Target processBlockEffects()
    {
        Target target = targeting.getTarget();
        Target originalTarget = target;
        final Block block = target.getBlock();
        Double backfireAmount = currentCast.getReflective(block);
        if (backfireAmount != null) {
            if (random.nextDouble() < backfireAmount) {
                final Entity mageEntity = mage.getEntity();
                final Location location = getLocation();
                final Location originLocation = block.getLocation();
                Vector direction = location.getDirection();
                originLocation.setDirection(direction.multiply(-1));
                this.location = originLocation;
                backfire();
                final Collection<com.elmakers.mine.bukkit.api.effect.EffectPlayer> effects = getEffects("cast");
                if (effects.size() > 0) {
                    Bukkit.getScheduler().runTaskLater(controller.getPlugin(), new PlaySpellEffectsTask(effects, originLocation, this, mage), 5L);
                }
                target = new Target(getEyeLocation(), mageEntity);
            }
        }

        if (targetBreakables > 0 && originalTarget.isValid() && block != null && currentCast.isBreakable(block)) {
            targeting.breakBlock(currentCast, block, targetBreakables);
        }

        return target;
    }

    protected Target findTarget(CastContext context)
    {
        Location source = getEyeLocation();
        TargetType targetType = targeting.getTargetType();

        boolean isBlock = targetType == TargetType.BLOCK || targetType == TargetType.SELECT;
        if (!isBlock && targetEntity != null) {
            return targeting.overrideTarget(context, new Target(source, targetEntity));
        }
        if (targetType == TargetType.LAST_DAMAGER) {
            return targeting.overrideTarget(context, new Target(source, mage.getLastDamager()));
        }
        if (targetType == TargetType.TOP_DAMAGER) {
            return targeting.overrideTarget(context, new Target(source, mage.getTopDamager()));
        }
        if (targetType == TargetType.DAMAGE_TARGET) {
            return targeting.overrideTarget(context, new Target(source, mage.getLastDamageTarget()));
        }
        if (targetType == TargetType.BLOCK_BROKEN) {
            return targeting.overrideTarget(context, new Target(source, mage.getLastBlockBroken()));
        }

        if (targetType != TargetType.SELF && targetLocation != null) {
            return targeting.overrideTarget(context, new Target(source, targetLocation.getBlock()));
        }

        Target target = targeting.target(context, getMaxRange());
        return targeting.getResult() == Targeting.TargetingResult.MISS && !allowMaxRange ? new Target(source) : target;
    }

    protected Target getTarget()
    {
        return getTarget(currentCast);
    }

    protected Target getTarget(CastContext context)
    {
        Target target = findTarget(context);

        if (instantBlockEffects)
        {
            target = processBlockEffects();
        }
        if (originAtTarget && target.isValid()) {
            Location previous = this.location;
            if (previous == null && mage != null) {
                previous = mage.getLocation();
            }
            location = target.getLocation().clone();
            if (previous != null) {
                location.setPitch(previous.getPitch());
                location.setYaw(previous.getYaw());
            }
        }

        Entity targetEntity = target != null ? target.getEntity() : null;
        Location targetLocation = target != null ? target.getLocation() : null;
        context.setTargetLocation(targetLocation);
        context.setTargetEntity(targetEntity);

        return target;
    }

    public Target getCurrentTarget()
    {
        return targeting.getOrCreateTarget(getEyeLocation());
    }

    @Nullable
    public Block getTargetBlock() {
        return getTarget().getBlock();
    }

    public List<Target> getAllTargetEntities() {
        // This target-clearing is a bit hacky, but this is only used when we want to reset
        // targeting.
        targeting.start(currentCast.getEyeLocation());
        return targeting.getAllTargetEntities(currentCast, this.getMaxRange());
    }

    @Override
    public boolean canTarget(Entity entity) {
        return canTarget(entity, null);
    }

    public boolean canTarget(Entity entity, Class<?> targetType) {
        // Don't target dead or invalid entities
        if (!entity.isValid() || entity.isDead()) {
            return false;
        }
        if (targetEntity != null && !targetEntity.getUniqueId().equals(entity.getUniqueId())) {
            return false;
        }
        // This is mainly here to ignore pets...
        if (!targetUnknown && entity.getType() == EntityType.UNKNOWN) {
            mage.sendDebugMessage("Entity is unknown type", 30);
            return false;
        }
        if (!targetMount) {
            Entity mounted = CompatibilityLib.getDeprecatedUtils().getPassenger(entity);
            Entity mageEntity = mage.getEntity();
            if (mounted != null && mageEntity != null && mounted.equals(mageEntity)) {
                mage.sendDebugMessage("Entity skipped, can't target your own mount", 30);
                return false;
            }
        }
        if (!targetTamed && entity instanceof Tameable && ((Tameable)entity).isTamed()) {
            mage.sendDebugMessage("Entity skipped, is tamed", 30);
            return false;
        }
        if (HOLOGRAM_PLUGIN != null && CompatibilityLib.getEntityMetadataUtils().hasString(entity, "hologram", HOLOGRAM_PLUGIN)) {
            mage.sendDebugMessage("Entity skipped, is hologram entity", 30);
            return false;
        }
        if (CompatibilityLib.getEntityMetadataUtils().getBoolean(entity, MagicMetaKeys.NO_TARGET)) {
            mage.sendDebugMessage("Entity skipped, has notarget metadata", 30);
            return false;
        }
        if (!targetNPCs && controller.isStaticNPC(entity)) {
            mage.sendDebugMessage("Entity skipped, is npc", 30);
            return false;
        }
        if (!targetPets && controller.isPet(entity)) {
            mage.sendDebugMessage("Entity skipped, is pet", 30);
            return false;
        }
        if (!targetArmorStands && entity instanceof ArmorStand) {
            mage.sendDebugMessage("Entity is armor stand, so no", 30);
            return false;
        }
        if (ignoreEntityTypes != null && ignoreEntityTypes.contains(entity.getType())) {
            mage.sendDebugMessage("Entity is in ignored types list, so no", 30);
            return false;
        }
        if (targetDisplayName != null && (entity.getCustomName() == null || !entity.getCustomName().equals(targetDisplayName))) {
            mage.sendDebugMessage("Entity is not the specific display name we're looking for", 30);
            return false;
        }
        if (ignoreDisplayName != null && entity.getCustomName() != null && entity.getCustomName().equals(ignoreDisplayName)) {
            mage.sendDebugMessage("Entity has an ignored display name", 30);
            return false;
        }
        if (targetPermission != null && !controller.hasPermission(entity, targetPermission)) {
            mage.sendDebugMessage("Entity does not have specific permission required", 30);
            return false;
        }
        if (targetPotionEffectTypes != null) {
            if (!(entity instanceof LivingEntity)) {
                mage.sendDebugMessage("Entity not a living entity and looking for potion effects", 30);
                return false;
            }
            LivingEntity li = (LivingEntity)entity;
            boolean has = false;
            for (PotionEffectType type : targetPotionEffectTypes) {
                if (li.hasPotionEffect(type)) {
                    has = true;
                    break;
                }
            }
            if (!has) {
                mage.sendDebugMessage("Entity does not have the specific potion effect we're looking for", 30);
                return false;
            }
        }

        if (ignorePotionEffectTypes != null) {
            if (entity instanceof LivingEntity) {
                LivingEntity li = (LivingEntity)entity;
                for (PotionEffectType type : targetPotionEffectTypes) {
                    if (li.hasPotionEffect(type)) {
                        mage.sendDebugMessage("Entity has one of the ignored potion effects", 30);
                        return false;
                    }
                }
            }
        }

        if (damageResistanceProtection > 0 && entity instanceof LivingEntity)
        {
            LivingEntity living = (LivingEntity)entity;
            if (living.hasPotionEffect(PotionEffectType.RESISTANCE)) {
                Collection<PotionEffect> effects = living.getActivePotionEffects();
                for (PotionEffect effect : effects) {
                    if (effect.getType().equals(PotionEffectType.RESISTANCE) && effect.getAmplifier() >= damageResistanceProtection) {
                        mage.sendDebugMessage("Entity skipped due to damage resistance", 30);
                        return false;
                    }
                }
            }
        }
        if (isSuperProtected(entity)) {
            mage.sendDebugMessage("Entity is superprotected", 30);
            return false;
        }
        if (entity instanceof Player)
        {
            Player player = (Player)entity;
            if (checkProtection && player.hasPermission("magic.protected." + this.getKey())) {
                mage.sendDebugMessage("Entity has Magic.protected perm", 30);
                return false;
            }
            if (targetGameModes != null && !targetGameModes.contains(player.getGameMode())) {
                mage.sendDebugMessage("Entity has one of the ignored game modes", 30);
                return false;
            }
        }
        // Ignore invisible entities
        if (!targetInvisible && entity instanceof LivingEntity && ((LivingEntity)entity).hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            mage.sendDebugMessage("Entity skipped, is invisible", 30);
            return false;
        }
        if (!targetVanished && entity instanceof Player && controller.isVanished(entity)) {
            mage.sendDebugMessage("Entity skipped, is vanished", 30);
            return false;
        }
        // Ignore entities that are invincible due to recently being damaged
        if (!targetNoDamageTicks && entity instanceof LivingEntity && ((LivingEntity)entity).getNoDamageTicks() > 0) {
            mage.sendDebugMessage("Entity skipped, has no damage ticks", 30);
            return false;
        }

        if (targetContents != null && entity instanceof ItemFrame)
        {
            ItemFrame itemFrame = (ItemFrame)entity;
            ItemStack item = itemFrame.getItem();
            if (item == null || item.getType() != targetContents) {
                mage.sendDebugMessage("Entity missing item frame contents", 30);
                return false;
            }
        }
        if (targetType != null) {
            boolean isTargetType = targetType.isAssignableFrom(entity.getClass());
            if (!isTargetType) {
                mage.sendDebugMessage("Entity is not the right target type", 30);
            }
            return isTargetType && super.canTarget(entity);
        }
        if (targetEntityType == null && targetEntityTypes == null) {
            return super.canTarget(entity);
        }
        if (targetEntityTypes != null) {
            boolean isTargetType = targetEntityTypes.contains(entity.getType());
            if (!isTargetType) {
                mage.sendDebugMessage("Entity is not the right target type", 30);
            }
            return isTargetType && super.canTarget(entity);
        }

        boolean isTargetType = targetEntityType.isAssignableFrom(entity.getClass());
        if (!isTargetType) {
            mage.sendDebugMessage("Entity is not the right target type", 30);
        }
        return isTargetType && super.canTarget(entity);
    }

    public boolean isSuperProtected(Entity entity) {
        if (bypassAll || bypassProtection) {
            return false;
        }
        Mage mage = controller.getRegisteredMage(entity);
        if (mage != null && mage.isSuperProtected()) {
            return true;
        }
        EntityData mob = controller.getMob(entity);
        return mob != null && mob.isSuperProtected();
    }

    protected double getMaxRange()
    {
        if (allowMaxRange) return Math.min(MAX_RANGE, range);
        float multiplier = (mage == null) ? 1 : mage.getRangeMultiplier();
        return Math.min(MAX_RANGE, multiplier * range);
    }

    @Override
    public double getRange()
    {
        TargetType targetType = targeting.getTargetType();
        if (!targetType.isRanged()) return 0;
        return getMaxRange();
    }

    protected double getMaxRangeSquared()
    {
        double maxRange = getMaxRange();
        return maxRange * maxRange;
    }

    protected void setMaxRange(double range)
    {
        this.range = range;
    }

    @Deprecated
    public boolean isTransparent(Material material) {
        return targetThroughMaterials.testMaterial(material);
    }

    public boolean isTransparent(Block block) {
        return targetThroughMaterials.testBlock(block);
    }

    @Nullable
    public Block getInteractBlock() {
        Location location = getEyeLocation();
        if (location == null) return null;
        Block playerBlock = location.getBlock();
        if (isTargetable(playerBlock)) return playerBlock;
        Vector direction = location.getDirection().normalize();
        return location.add(direction).getBlock();
    }

    public Block findBlockUnder(Block block)
    {
        int depth = 0;
        if (targetThroughMaterials.testBlock(block))
        {
            while (depth < verticalSearchDistance && targetThroughMaterials.testBlock(block))
            {
                depth++;
                block = block.getRelative(BlockFace.DOWN);
            }
        }
        else
        {
            while (depth < verticalSearchDistance && !targetThroughMaterials.testBlock(block))
            {
                depth++;
                block = block.getRelative(BlockFace.UP);
            }
            block = block.getRelative(BlockFace.DOWN);
        }

        return block;
    }

    public Block findSpaceAbove(Block block)
    {
        int depth = 0;
        while (depth < verticalSearchDistance && !targetThroughMaterials.testBlock(block))
        {
            depth++;
            block = block.getRelative(BlockFace.UP);
        }
        return block;
    }

    @Override
    protected void reset()
    {
        super.reset();
        this.initializeTargeting();
    }

    @Override
    public void processTemplateParameters(ConfigurationSection parameters) {
        super.processTemplateParameters(parameters);
        range = parameters.getDouble("range", 0);
        boolean hasTargeting = parameters.contains("target");
        targeting.parseTargetType(parameters.getString("target"));

        // If a range was specified but not a target type, default to other
        if (range > 0 && !hasTargeting) {
            targeting.setTargetType(TargetType.OTHER);
        }
        TargetType targetType = targeting.getTargetType();

        // Use default range of 32 for configs that didn't specify range
        // Only when targeting is set to on
        if ((targetType != TargetType.NONE && targetType != TargetType.SELF) && !parameters.contains("range")) {
            range = 32;
        }

        // Re-process targetSelf parameter, defaults to on if targetType is "self"
        targetSelf = (targetType == TargetType.SELF);
        targetSelf = parameters.getBoolean("target_self", targetSelf);
    }

    @Override
    public void processParameters(ConfigurationSection parameters) {
        targeting.processParameters(parameters);

        // Need to do this *after* targeting.processParameters because it may override some targeting defaults.
        super.processParameters(parameters);
        allowMaxRange = parameters.getBoolean("allow_max_range", false);
        checkProtection = parameters.getBoolean("check_protection", false);
        damageResistanceProtection = parameters.getInt("damage_resistance_protection", 0);
        targetBreakables = parameters.getDouble("target_breakables", 1);
        reverseTargeting = parameters.getBoolean("reverse_targeting", false);
        instantBlockEffects = parameters.getBoolean("instant_block_effects", false);

        MaterialSetManager materials = controller.getMaterialSetManager();
        targetThroughMaterials = MaterialSets.empty();
        targetThroughMaterials = materials.getMaterialSet("transparent", targetThroughMaterials);
        targetThroughMaterials = materials.fromConfig(parameters.getString("transparent"), targetThroughMaterials);

        targetableMaterials = MaterialSets.wildcard();
        targetableMaterials = materials.fromConfig(parameters.getString("targetable"), targetableMaterials);

        reflectiveMaterials = MaterialSets.empty();
        reflectiveMaterials = materials.fromConfig(parameters.getString("reflective"), reflectiveMaterials);

        if (parameters.getBoolean("reflective_override", true)) {
            String reflectiveKey = controller.getReflectiveMaterials(mage, mage.getLocation());
            if (reflectiveKey != null) {
                reflectiveMaterials = MaterialSets.union(
                        materials.fromConfigEmpty(reflectiveKey),
                        reflectiveMaterials);
            }
        }

        targetPotionEffectTypes = parsePotionEffectTypes(parameters, "target_potion_effects");
        ignorePotionEffectTypes = parsePotionEffectTypes(parameters, "ignore_potion_effects");
        targetNPCs = parameters.getBoolean("target_npc", false);
        targetPets = parameters.getBoolean("target_pet", false);
        targetArmorStands = parameters.getBoolean("target_armor_stand", false);
        targetInvisible = parameters.getBoolean("target_invisible", true);
        targetVanished = parameters.getBoolean("target_vanished", false);
        targetUnknown = parameters.getBoolean("target_unknown", true);
        targetTamed = parameters.getBoolean("target_tamed", true);
        targetMount = parameters.getBoolean("target_mount", false);
        targetNoDamageTicks = parameters.getBoolean("target_no_damage_ticks", true);
        targetPermission = parameters.getString("target_permission");
        targetGameModes = defaultTargetGameModes;
        List<String> gameModes = ConfigurationUtils.getStringList(parameters, "target_game_modes");
        if (gameModes != null && !gameModes.isEmpty()) {
            if (gameModes.get(0).equalsIgnoreCase("all")) {
                targetGameModes = null;
            } else {
                targetGameModes = new HashSet<>();
                for (String gameMode : gameModes) {
                    try {
                        GameMode mode = GameMode.valueOf(gameMode.toUpperCase());
                        targetGameModes.add(mode);
                    } catch (Exception ex) {
                        controller.getLogger().warning(("Invalid game mode: " + gameMode));
                    }
                }
            }
        }

        if (parameters.contains("target_type")) {
            String entityTypeName = parameters.getString("target_type");
            try {
                targetEntityType = Class.forName("org.bukkit.entity." + entityTypeName);
            } catch (Throwable ex) {
                controller.getLogger().warning("Unknown entity class in target_type of " + getKey() + ": " + entityTypeName);
                targetEntityType = null;
            }
        } else if (parameters.contains("target_types")) {
            targetEntityType = null;
            targetEntityTypes = new HashSet<>();
            Collection<String> typeKeys = ConfigurationUtils.getStringList(parameters, "target_types");
            for (String typeKey : typeKeys) {
                try {
                    EntityType entityType = EntityType.valueOf(typeKey.toUpperCase());
                    targetEntityTypes.add(entityType);
                } catch (Throwable ex) {
                    controller.getLogger().warning("Unknown entity type in target_types of " + getKey() + ": " + typeKey);
                }
            }
        } else {
            targetEntityType = null;
            targetEntityTypes = null;
        }
        if (parameters.contains("ignore_types")) {
            ignoreEntityTypes = new HashSet<>();
            Collection<String> typeKeys = ConfigurationUtils.getStringList(parameters, "ignore_types");
            for (String typeKey : typeKeys) {
                try {
                    EntityType entityType = EntityType.valueOf(typeKey.toUpperCase());
                    ignoreEntityTypes.add(entityType);
                } catch (Throwable ex) {
                    controller.getLogger().warning("Unknown entity type in ignore_types of " + getKey() + ": " + typeKey);
                }
            }
        } else {
            ignoreEntityTypes = null;
        }

        targetDisplayName = parameters.getString("target_name", null);
        ignoreDisplayName = parameters.getString("ignore_name", null);
        targetContents = ConfigurationUtils.getMaterial(parameters, "target_contents", null);
        originAtTarget = parameters.getBoolean("origin_at_target", false);

        Location defaultLocation = getLocation();
        targetLocation = ConfigurationUtils.overrideLocation(controller, parameters, "t", defaultLocation, controller.canCreateWorlds());

        // For two-click construction spells
        defaultLocation = targetLocation == null ? defaultLocation : targetLocation;
        targetLocation2 = ConfigurationUtils.overrideLocation(controller, parameters, "t2", defaultLocation, controller.canCreateWorlds());

        String uuid = parameters.getString("entity", "");
        String playerName = parameters.getString("player", "");
        if (!uuid.isEmpty()) {
            Entity entity = CompatibilityLib.getCompatibilityUtils().getEntity(UUID.fromString(uuid));
            if (entity != null) {
                targetLocation = entity.getLocation();
                targetEntity = entity;
            }
        } else if (!playerName.isEmpty()) {
            Player player = CompatibilityLib.getDeprecatedUtils().getPlayer(playerName);
            if (player != null) {
                targetLocation = player.getLocation();
                targetEntity = player;
            }
        } else {
            targetEntity = null;
        }

        // Special hack that should work well in most casts.
        boolean targetUnderwater = parameters.getBoolean("target_underwater", true);
        if (targetUnderwater && isUnderwater()) {
            targetThroughMaterials = MaterialSets.union(targetThroughMaterials, DefaultMaterials.getWaterSet());
        }
    }

    @Nullable
    protected Set<PotionEffectType> parsePotionEffectTypes(ConfigurationSection parameters, String key) {
        Set<PotionEffectType> types = null;
        List<String> potionEffects = ConfigurationUtils.getStringList(parameters, key);
        if (potionEffects != null && !potionEffects.isEmpty()) {
            types = new HashSet<>();
            for (String effectType : potionEffects) {
                PotionEffectType effect = PotionEffectType.getByName(effectType);
                if (effect == null) {
                    controller.getLogger().warning("Invalid potion effect type: " + effectType + " in target/ignore parameters of " + getKey());
                } else {
                    types.add(effect);
                }
            }
        }
        return types;
    }

    @Override
    protected String getDisplayMaterialName()
    {
        Target target = targeting.getTarget();
        if (target != null && target.isValid()) {
            return MaterialBrush.getMaterialName(target.getBlock(), controller.getMessages());
        }

        return super.getDisplayMaterialName();
    }

    @Override
    protected void onBackfire() {
        targeting.setTargetType(TargetType.SELF);
    }

    @Nullable
    public Location getSelectedLocation() {
        return selectedLocation;
    }

    public void setSelectedLocation(Location location) {
        this.selectedLocation = location;
    }

    @Override
    @Nullable
    public Location getTargetLocation() {
        Target target = targeting.getTarget();
        if (target != null && target.isValid()) {
            return target.getLocation();
        }

        return null;
    }

    @Override
    @Nullable
    public Entity getTargetEntity() {
        Target target = targeting.getTarget();
        if (target != null && target.isValid()) {
            return target.getEntity();
        }

        return null;
    }

    @Nullable
    @Override
    public com.elmakers.mine.bukkit.api.block.MaterialAndData getEffectMaterial()
    {
        Target target = targeting.getTarget();
        if (target != null && target.isValid()) {
            Block block = target.getBlock();
            if (!CompatibilityLib.getCompatibilityUtils().isChunkLoaded(block)) {
                return super.getEffectMaterial();
            }
            MaterialAndData targetMaterial = new MaterialAndData(block);
            if (targetMaterial.getMaterial() == Material.AIR) {
                targetMaterial.setMaterial(DEFAULT_EFFECT_MATERIAL);
            }
            return targetMaterial;
        }
        return super.getEffectMaterial();
    }

    public Class<?> getTargetEntityType() {
        return targetEntityType;
    }
}
