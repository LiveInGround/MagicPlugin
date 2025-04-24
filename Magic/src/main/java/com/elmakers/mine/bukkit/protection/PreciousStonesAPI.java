package com.elmakers.mine.bukkit.protection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Golem;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.elmakers.mine.bukkit.api.protection.BlockBreakManager;
import com.elmakers.mine.bukkit.api.protection.BlockBuildManager;
import com.elmakers.mine.bukkit.api.protection.PVPManager;
import com.elmakers.mine.bukkit.api.protection.PlayerWarp;
import com.elmakers.mine.bukkit.api.spell.SpellTemplate;
import com.elmakers.mine.bukkit.block.DefaultMaterials;

import net.sacredlabyrinth.Phaed.PreciousStones.PreciousStones;
import net.sacredlabyrinth.Phaed.PreciousStones.entries.FieldSign;
import net.sacredlabyrinth.Phaed.PreciousStones.field.Field;
import net.sacredlabyrinth.Phaed.PreciousStones.field.FieldFlag;
import net.sacredlabyrinth.Phaed.PreciousStones.managers.ForceFieldManager;

public class PreciousStonesAPI implements BlockBuildManager, BlockBreakManager, PVPManager {
    private PreciousStones preciousStones = null;

    public PreciousStonesAPI(Plugin owner, Plugin psPlugin) {
        if (psPlugin instanceof PreciousStones) {
            preciousStones = (PreciousStones) psPlugin;
        }
    }

    public boolean isEnabled() {
        return preciousStones != null;
    }

    @Override
    public boolean isPVPAllowed(Player player, Location location) {
        if (preciousStones == null || location == null)
            return true;
        List<Field> fields = PreciousStones.API().getFieldsProtectingArea(FieldFlag.PREVENT_PVP, location);
        return fields.size() == 0;
    }

    @Nullable
    public List<PlayerWarp> getFieldLocations(Player player) {
        return getFieldLocations(player == null ? null : player.getName());
    }

    @Nullable
    public List<PlayerWarp> getFieldLocations(String playerName) {
        if (preciousStones == null || playerName == null)
            return null;

        ForceFieldManager manager = preciousStones.getForceFieldManager();
        if (manager == null) return null;

        List<PlayerWarp> fieldLocations = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            Collection<Field> fields = manager.getFields(playerName, world);
            if (fields == null) continue;
            for (Field field : fields) {
                String fieldName = field.getName();
                String fieldType = field.getSettings().getTitle();
                String fieldOwner = field.getOwner();
                List<String> renters = field.getRenters();
                if (fieldName == null || fieldName.isEmpty()) {
                    fieldName = fieldType;
                }
                if (!fieldOwner.equalsIgnoreCase(playerName)) {
                    if (renters.contains(playerName.toLowerCase())) {
                        fieldName = fieldName + ChatColor.GRAY + " (Renting)";
                    } else {
                        fieldName = fieldName + ChatColor.LIGHT_PURPLE + " (" + fieldOwner + ")";
                    }
                }
                fieldLocations.add(new PlayerWarp(fieldName, field.getLocation()));
            }
        }
        return fieldLocations.isEmpty() ? null : fieldLocations;
    }

    @Nullable
    public List<PlayerWarp> getAllFieldLocations() {
        return getFieldLocations("*");
    }

    @Override
    public boolean hasBuildPermission(Player player, Block block) {
        boolean allowed = true;
        if (block != null && preciousStones != null)
        {
            Location location = block.getLocation();
            if (PreciousStones.API().isFieldProtectingArea(FieldFlag.ALL, location))
            {
                if (player == null)
                {
                    return false;
                }
                allowed = allowed && PreciousStones.API().canBreak(player, location);
                allowed = allowed && PreciousStones.API().canPlace(player, location);
                return allowed;
            }
        }
        return allowed;
    }

    @Nullable
    public Boolean getCastPermission(Player player, SpellTemplate spell, Location location) {
        Boolean overridePermission = null;
        if (location != null && preciousStones != null)
        {
            if (PreciousStones.API().isFieldProtectingArea(FieldFlag.ALL, location))
            {
                if (player == null)
                {
                    return null;
                }
                if (PreciousStones.API().canBreak(player, location) && PreciousStones.API().canPlace(player, location))
                {
                    overridePermission = true;
                }
            }
        }
        return overridePermission;
    }

    @Override
    public boolean hasBreakPermission(Player player, Block block) {
        return hasBuildPermission(player, block);
    }

    public boolean canTarget(Entity source, Entity target) {
        if (target == null)
        {
            return true;
        }

        Player player = (source instanceof Player) ? (Player)source : null;
        if (target instanceof Ageable)
        {
            Field field = preciousStones.getForceFieldManager().getEnabledSourceField(target.getLocation(), FieldFlag.PROTECT_ANIMALS);
            if (field == null) return true;
            if (player != null)
            {
                if (FieldFlag.PROTECT_ANIMALS.applies(field, player))
                {
                    return false;
                }
            }
            else
            {
                if (field.hasFlag(FieldFlag.PROTECT_ANIMALS))
                {
                    return false;
                }
            }
        }
        else if (target instanceof Villager)
        {
            Field field = preciousStones.getForceFieldManager().getEnabledSourceField(target.getLocation(), FieldFlag.PROTECT_VILLAGERS);
            if (field == null) return true;
            if (player != null)
            {
                if (FieldFlag.PROTECT_VILLAGERS.applies(field, player))
                {
                    return false;
                }
            }
            else
            {
                if (field.hasFlag(FieldFlag.PROTECT_VILLAGERS))
                {
                    return false;
                }
            }
        }
        else if (target instanceof Monster || target instanceof Golem || target instanceof WaterMob)
        {
            Field field = preciousStones.getForceFieldManager().getEnabledSourceField(target.getLocation(), FieldFlag.PROTECT_MOBS);
            if (field == null) return true;
            if (player != null)
            {
                if (FieldFlag.PROTECT_MOBS.applies(field, player))
                {
                    return false;
                }
            }
            else
            {
                if (field.hasFlag(FieldFlag.PROTECT_MOBS))
                {
                    return false;
                }
            }
        }

        return true;
    }

    public boolean createField(Location location, Player player) {
        ForceFieldManager manager = preciousStones.getForceFieldManager();
        Block targetBlock = location.getBlock();
        BlockPlaceEvent event = new BlockPlaceEvent(targetBlock,
                targetBlock.getState(),
                targetBlock.getRelative(BlockFace.DOWN),
                new ItemStack(targetBlock.getType()),
                player, true, EquipmentSlot.HAND);

        manager.add(location.getBlock(), player, event);
        return !event.isCancelled();
    }

    public boolean rentField(Location signLocation, Player player, String rent, String timePeriod, BlockFace signDirection) {
        Block signBlock = signLocation.getBlock();
        Material signMaterial = DefaultMaterials.getGroundSignBlock();
        if (signMaterial == null) {
            return false;
        }
        signBlock.setType(signMaterial);
        Sign sign = (Sign)signBlock.getState();
        sign.setLine(0, ChatColor.BLACK + "" + ChatColor.BOLD + "[Rent]");
        sign.setLine(1, rent);
        sign.setLine(2, timePeriod);
        Object data = sign.getData();
        if (data instanceof org.bukkit.material.Sign) {
            org.bukkit.material.Sign signData = (org.bukkit.material.Sign)data;
            signData.setFacingDirection(signDirection);
            sign.setData(signData);
        }
        sign.update();
        final FieldSign s = new FieldSign(signBlock, sign.getLines(), player);
        return s.isValid();
    }
}
