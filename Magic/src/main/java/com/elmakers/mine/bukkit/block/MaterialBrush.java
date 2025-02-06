package com.elmakers.mine.bukkit.block;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.util.Vector;

import com.elmakers.mine.bukkit.api.block.BrushMode;
import com.elmakers.mine.bukkit.api.block.Schematic;
import com.elmakers.mine.bukkit.api.data.BrushData;
import com.elmakers.mine.bukkit.api.magic.Mage;
import com.elmakers.mine.bukkit.api.magic.MageController;
import com.elmakers.mine.bukkit.api.magic.Messages;
import com.elmakers.mine.bukkit.entity.EntityData;
import com.elmakers.mine.bukkit.maps.BufferedMapCanvas;
import com.elmakers.mine.bukkit.utility.CompatibilityLib;
import com.elmakers.mine.bukkit.utility.ConfigurationUtils;
import com.elmakers.mine.bukkit.utility.StringUtils;

public class MaterialBrush extends MaterialAndData implements com.elmakers.mine.bukkit.api.block.MaterialBrush {

    public static final String ERASE_MATERIAL_KEY = "erase";
    public static final String COPY_MATERIAL_KEY = "copy";
    public static final String CLONE_MATERIAL_KEY = "clone";
    public static final String REPLICATE_MATERIAL_KEY = "replicate";
    public static final String MAP_MATERIAL_KEY = "map";
    public static final String SCHEMATIC_MATERIAL_KEY = "schematic";
    public static final int DEFAULT_MAP_SIZE = 16;

    // This does not include schematics
    public static final String[] SPECIAL_MATERIAL_KEYS = {ERASE_MATERIAL_KEY, COPY_MATERIAL_KEY,
        CLONE_MATERIAL_KEY, REPLICATE_MATERIAL_KEY, MAP_MATERIAL_KEY};

    public static MaterialAndData EraseMaterial = new MaterialAndData(Material.PAPER);
    public static MaterialAndData CopyMaterial = new MaterialAndData(Material.PAPER);
    public static MaterialAndData CloneMaterial = new MaterialAndData(Material.PAPER);
    public static MaterialAndData ReplicateMaterial = new MaterialAndData(Material.PAPER);
    public static MaterialAndData MapMaterial = new MaterialAndData(Material.MAP);
    public static MaterialAndData SchematicMaterial = new MaterialAndData(Material.PAPER);
    public static MaterialAndData DefaultBrushMaterial = new MaterialAndData(Material.PAPER);

    public static String EraseCustomIcon;
    public static String CopyCustomIcon;
    public static String CloneCustomIcon;
    public static String ReplicateCustomIcon;
    public static String MapCustomIcon;
    public static String SchematicCustomIcon;
    public static String DefaultBrushCustomIcon;

    public static boolean EraseEnabled = true;
    public static boolean CopyEnabled = true;
    public static boolean CloneEnabled = true;
    public static boolean ReplicateEnabled = true;
    public static boolean MapEnabled = true;
    public static boolean SchematicEnabled = true;

    public static final Material DEFAULT_MATERIAL = Material.DIRT;
    private static final Map<MaterialAndData, MaterialAndData> replacements = new HashMap<>();

    private BrushMode mode = BrushMode.MATERIAL;
    private Location cloneSource = null;
    private Location cloneTarget = null;
    private Location materialTarget = null;
    private Vector targetOffset = null;
    private String targetWorldName = null;
    private final Mage mage;
    private final MageController controller;
    private int mapId = -1;
    private BufferedMapCanvas mapCanvas = null;
    private Schematic schematic;
    private String schematicName = "";
    private boolean fillWithAir = true;
    private Vector orientVector = null;
    private Map<String, String> commandMap;
    private MaterialBrush parent;

    // For the MAP brush
    private Material mapMaterialBase = null;
    private Material mapMaterialDefault = null;
    private double scale = 1;

    private MaterialBrush(final Mage mage) {
        this.mage = mage;
        this.controller = mage != null ? mage.getController() : null;
    }

    public MaterialBrush(final Mage mage, final Material material, final byte data) {
        super(material, data);
        this.mage = mage;
        this.controller = mage != null ? mage.getController() : null;
    }

    public MaterialBrush(final Mage mage, final Location location, final String materialKey) {
        super(DEFAULT_MATERIAL, (byte)0);
        this.mage = mage;
        this.controller = mage != null ? mage.getController() : null;
        update(materialKey);
        activate(location, materialKey);
    }

    public MaterialBrush(final Mage mage, final Block block) {
        super(block);
        this.mage = mage;
        this.controller = mage != null ? mage.getController() : null;
    }

    public MaterialBrush(final String materialKey) {
        this(null, materialKey);
    }

    public MaterialBrush(final MageController controller, final String materialKey) {
        super(DEFAULT_MATERIAL, (byte)0);
        this.mage = null;
        this.controller = controller;
        update(materialKey);
    }

    @Override
    public MaterialBrush getCopy() {
        MaterialBrush newBrush = new MaterialBrush(mage);
        newBrush.parent = this;
        copyTo(newBrush);
        return newBrush;
    }

    public void copyTo(MaterialBrush other) {
        super.copyTo(other);
        other.mode = mode;
        other.cloneSource = cloneSource;
        other.cloneTarget = cloneTarget;
        other.materialTarget = materialTarget;
        other.targetOffset = targetOffset;
        other.targetWorldName = targetWorldName;
        other.mapId = mapId;
        other.mapCanvas = mapCanvas;
        other.schematic = schematic;
        other.schematicName = schematicName;
        other.fillWithAir = fillWithAir;
        other.orientVector = orientVector;
        other.commandMap = commandMap;
        other.mapMaterialBase = mapMaterialBase;
        other.scale = scale;
    }

    @Override
    public String getKey() {
        String materialKey = null;
        if (mode == BrushMode.ERASE) {
            materialKey = ERASE_MATERIAL_KEY;
        } else if (mode == BrushMode.COPY) {
            materialKey = COPY_MATERIAL_KEY;
        } else if (mode == BrushMode.CLONE) {
            materialKey = CLONE_MATERIAL_KEY;
        } else if (mode == BrushMode.MAP) {
            materialKey = MAP_MATERIAL_KEY;
            int mapSize = (int)(128 / scale);
            if (mapSize != DEFAULT_MAP_SIZE)
            {
                materialKey = materialKey + ":" + mapSize;
            }
        } else if (mode == BrushMode.REPLICATE) {
            materialKey = REPLICATE_MATERIAL_KEY;
        } else if (mode == BrushMode.SCHEMATIC) {
            // This would be kinda broken.. might want to revisit all this.
            // This method is only called by addMaterial at this point,
            // which should only be called with real materials anyway.
            materialKey = SCHEMATIC_MATERIAL_KEY + ":" + schematicName;
        } else {
            materialKey = super.getKey();
        }

        return materialKey;
    }

    public static boolean isSpecialMaterialKey(String materialKey) {
        if (materialKey == null || materialKey.length() == 0) return false;
        materialKey = splitMaterialKey(materialKey)[0];
        return COPY_MATERIAL_KEY.equals(materialKey)
                || ERASE_MATERIAL_KEY.equals(materialKey)
                || REPLICATE_MATERIAL_KEY.equals(materialKey)
                || CLONE_MATERIAL_KEY.equals(materialKey)
                || MAP_MATERIAL_KEY.equals(materialKey)
                || SCHEMATIC_MATERIAL_KEY.equals(materialKey)
                || materialKey.equalsIgnoreCase("light");
    }

    public static boolean isSchematic(String materialKey) {
        if (materialKey == null || materialKey.length() == 0) return false;
        materialKey = splitMaterialKey(materialKey)[0];
        return SCHEMATIC_MATERIAL_KEY.equals(materialKey);
    }

    public static String getMaterialName(Messages messages, String materialKey) {
        MaterialBrush brush = new MaterialBrush(materialKey);
        return brush.getName(messages);
    }

    @Override
    public String getName() {
        Messages messages = mage != null ? mage.getController().getMessages() : null;
        return getName(messages);
    }

    @Nonnull
    @Override
    public String getName(@Nullable Messages messages) {
        String brushKey;
        switch (mode) {
        case ERASE:
            brushKey = ERASE_MATERIAL_KEY;
            if (messages != null) {
                brushKey = messages.get("wand.erase_material_name");
            }
            break;
        case CLONE:
            brushKey = CLONE_MATERIAL_KEY;
            if (messages != null) {
                brushKey = messages.get("wand.clone_material_name");
            }
            break;
        case REPLICATE:
            brushKey = REPLICATE_MATERIAL_KEY;
            if (messages != null) {
                brushKey = messages.get("wand.replicate_material_name");
            }
            break;
        case COPY:
            brushKey = COPY_MATERIAL_KEY;
            if (messages != null) {
                brushKey = messages.get("wand.copy_material_name");
            }
            break;
        case MAP:
            brushKey = MAP_MATERIAL_KEY;
            int mapSize = (int)(128 / scale);
            if (mapSize != DEFAULT_MAP_SIZE)
            {
                if (messages != null) {
                    brushKey = messages.get("wand.map_material_name_scaled");
                    brushKey = brushKey.replace("$size", Integer.toString(mapSize));
                } else {
                    brushKey = brushKey + " " + mapSize + "x" + mapSize;
                }
            } else if (messages != null) {
                brushKey = messages.get("wand.map_material_name");
            }
            if (mapId > 0) {
                brushKey = brushKey.replace("$id", Integer.toString(mapId));
            } else {
                brushKey = brushKey.replace("$id", "");
            }
            break;
        case SCHEMATIC:
            brushKey = schematicName;
            brushKey = brushKey.toLowerCase().replace('_', ' ');
            break;
        default:
            brushKey = super.getName(messages);
        }

        return brushKey;
    }

    @Nullable
    public static MaterialBrush parseMaterialKey(String materialKey) {
        return parseMaterialKey(materialKey, false);
    }

    @Nullable
    public static MaterialBrush parseMaterialKey(String materialKey, boolean allowItems) {
        if (materialKey == null || materialKey.length() == 0) return null;
        MaterialBrush brush = new MaterialBrush(materialKey);
        return brush.isValid(allowItems) ? brush : null;
    }

    public static boolean isValidMaterial(String materialKey, boolean allowItems) {
        MaterialBrush brush = new MaterialBrush(materialKey);
        return brush.isValid(allowItems);
    }

    public boolean isValid(boolean allowItems) {
        if (!isValid()) return false;
        if (mode != BrushMode.MATERIAL) return true;
        return allowItems || material.isBlock();
    }

    @Override
    public boolean isTargetValid() {
        return isTargetValid;
    }

    @Override
    public void setMaterial(Material material, Short data) {
        if (material != null && (mage == null || !mage.isRestricted(material, data))) {
            super.setMaterial(material, data);
            isValid = true;
        } else {
            isValid = false;
        }
        isTargetValid = true;
        fillWithAir = true;
        mode = BrushMode.MATERIAL;
    }

    public void enableCloning() {
        if (!CloneEnabled) {
            isValid = false;
            return;
        }
        if (this.mode != BrushMode.CLONE) {
            fillWithAir = this.mode == BrushMode.ERASE;
            this.mode = BrushMode.CLONE;
        }
    }

    public void enableErase() {
        if (!EraseEnabled) {
            isValid = false;
            return;
        }
        if (this.mode != BrushMode.ERASE) {
            this.setMaterial(Material.AIR);
            this.mode = BrushMode.ERASE;
            fillWithAir = true;
        }
    }

    public void enableMap(int size, boolean solid) {
        if (!MapEnabled) {
            isValid = false;
            return;
        }
        fillWithAir = false;
        if (size <= 0) {
            size = DEFAULT_MAP_SIZE;
        }
        this.scale = (float)128 / size;
        this.mode = BrushMode.MAP;
        this.mapMaterialDefault = solid ? material : null;
        this.mapMaterialBase = DefaultMaterials.getInstance().getBaseMaterial(material);
        if (this.mapId == -1 && mage != null) {
            this.mapId = mage.getLastHeldMapId();
        }
    }

    public void enableSchematic(String name) {
        if (!SchematicEnabled) {
            isValid = false;
            return;
        }
        if (this.mode != BrushMode.SCHEMATIC) {
            fillWithAir = this.mode == BrushMode.ERASE;
            this.mode = BrushMode.SCHEMATIC;
        }
        this.schematicName = name;
        schematic = null;
    }

    public void clearSchematic() {
        schematic = null;
    }

    public void enableReplication() {
        if (!ReplicateEnabled) {
            isValid = false;
            return;
        }
        if (this.mode != BrushMode.REPLICATE) {
            fillWithAir = this.mode == BrushMode.ERASE;
            this.mode = BrushMode.REPLICATE;
        }
    }

    public void setMapId(int mapId) {
        this.mapCanvas = null;
        this.mapId = mapId;
    }

    public int getMapId() {
        return this.mapId;
    }

    public void setCloneLocation(Location cloneFrom) {
        cloneSource = cloneFrom;
        materialTarget = cloneFrom;
        cloneTarget = null;
    }

    public void clearCloneLocation() {
        cloneSource = null;
        materialTarget = null;
    }

    public void clearCloneTarget() {
        cloneTarget = null;
        targetOffset = null;
        targetWorldName = null;
    }

    public void setTargetOffset(Vector offset, String worldName) {
        targetOffset = offset.clone();
        targetWorldName = worldName;
    }

    public boolean hasCloneTarget() {
        return cloneSource != null && cloneTarget != null;
    }

    public void enableCopying() {
        mode = BrushMode.COPY;
    }

    @Override
    public boolean isReady() {
        if ((mode == BrushMode.CLONE || mode == BrushMode.REPLICATE) && materialTarget != null) {
            Block block = materialTarget.getBlock();
            return CompatibilityLib.getCompatibilityUtils().isChunkLoaded(block);
        } else if (mode == BrushMode.SCHEMATIC) {
            return checkSchematic();
        }

        return true;
    }

    @Nullable
    public Location toTargetLocation(World targetWorld, Location target) {
        if (cloneSource == null || cloneTarget == null) return null;
        Location translated = cloneSource.clone();
        translated.subtract(cloneTarget.toVector());
        translated.add(target.toVector());
        if (targetWorld != null) {
            translated.setWorld(targetWorld);
        }
        return translated;
    }

    @Nullable
    public Location toTargetLocation(Location target) {
        return toTargetLocation(null, target);
    }

    @Nullable
    public Location fromTargetLocation(World targetWorld, Location target, boolean isBlock) {
        if (cloneSource == null || cloneTarget == null) return null;
        Location translated = target.clone();
        Vector delta = cloneTarget.toVector().subtract(cloneSource.toVector());
        if (isBlock) {
            delta.setX((int)delta.getX());
            delta.setY((int)delta.getY());
            delta.setZ((int)delta.getZ());
        }
        translated.add(delta);
        translated.setWorld(targetWorld);
        return translated;
    }

    @Override
    public void update(String activeMaterial) {
        // First check for brush mods
        String[] pieces = StringUtils.split(activeMaterial, '&');
        if (pieces != null && pieces.length > 0)
        {
            activeMaterial = pieces[0];
            if (pieces.length > 1)
            {
                update(pieces[1]);
            }
        }

        pieces = splitMaterialKey(activeMaterial);
        isValid = true;
        isTargetValid = true;
        if (activeMaterial.equals(COPY_MATERIAL_KEY)) {
            enableCopying();
        } else if (activeMaterial.equals(CLONE_MATERIAL_KEY)) {
            enableCloning();
        } else if (activeMaterial.equals(REPLICATE_MATERIAL_KEY)) {
            enableReplication();
        } else if (pieces[0].equals(MAP_MATERIAL_KEY)) {
            int size = DEFAULT_MAP_SIZE;
            boolean solid = false;
            if (pieces.length > 1) {
                String[] dataPieces = StringUtils.split(pieces[1], ":", 2);
                if (dataPieces.length > 0) {
                    String[] sizePieces = StringUtils.split(dataPieces[0], ",");
                    for (String sizePiece : sizePieces) {
                        if (sizePiece.equalsIgnoreCase("solid")) {
                            solid = true;
                        } else {
                            try {
                                size = Integer.parseInt(sizePiece);
                            } catch (Exception ex) {
                                Bukkit.getLogger().info("Error in map brush definition, first part is not an integer: " + activeMaterial);
                            }
                        }
                    }

                    if (dataPieces.length > 1) {
                        String mapKey = dataPieces[1];
                        if (controller != null && mapKey.startsWith("http")) {
                            mapId = controller.getMaps().getURLMapId(Bukkit.getWorlds().get(0).getName(), mapKey);
                        } else {
                            try {
                                mapId = Integer.parseInt(mapKey);
                            } catch (Exception ex) {
                                Bukkit.getLogger().info("Error in map brush definition, second part is not an integer or a URL: " + activeMaterial);
                            }
                        }
                    }
                }
            }
            enableMap(size, solid);
        } else if (activeMaterial.equals(ERASE_MATERIAL_KEY)) {
            enableErase();
        } else if (pieces.length > 1 && pieces[0].equals(SCHEMATIC_MATERIAL_KEY)) {
            enableSchematic(pieces[1]);
        } else {
            mode = BrushMode.MATERIAL;
            super.update(activeMaterial);
        }
    }

    @Override
    public boolean update(final Mage fromMage, final Location target) {
        // Chain up to parent
        if (parent != null) {
            parent.update(fromMage, target);
        }

        if (mode == BrushMode.CLONE || mode == BrushMode.REPLICATE) {
            if (cloneSource == null) {
                isTargetValid = false;
                return true;
            }
            if (cloneTarget == null) cloneTarget = target;
            materialTarget = toTargetLocation(target);
            if (materialTarget.getY() < 0 || materialTarget.getWorld() == null || materialTarget.getY() > materialTarget.getWorld().getMaxHeight()) {
                isTargetValid = false;
            } else {
                Block block = materialTarget.getBlock();
                if (!CompatibilityLib.getCompatibilityUtils().isChunkLoaded(block)) return false;

                updateFromBlock(block, fromMage.getRestrictedMaterialSet());
                isTargetValid = fillWithAir || material != Material.AIR;
            }
        }

        if (mode == BrushMode.SCHEMATIC) {
            if (!checkSchematic()) {
                return true;
            }
            if (cloneTarget == null) {
                isTargetValid = false;
                return true;
            }
            Vector diff = target.toVector().subtract(cloneTarget.toVector());
            com.elmakers.mine.bukkit.api.block.MaterialAndData newMaterial = schematic.getBlock(diff);
            if (newMaterial == null) {
                isTargetValid = false;
            } else {
                updateFrom(newMaterial);
                isTargetValid = fillWithAir || newMaterial.getMaterial() != Material.AIR;

                // Check for command overrides
                if (commandMap != null && DefaultMaterials.isCommand(material)) {
                    String commandKey = getCommandLine();
                    if (commandKey != null && commandKey.length() > 0 && commandMap.containsKey(commandKey)) {
                        setCommandLine(commandMap.get(commandKey));
                    }
                }
            }
        }

        if (mode == BrushMode.MAP && mapId >= 0) {
            if (mapCanvas == null) {
                try {
                    MapView mapView = CompatibilityLib.getDeprecatedUtils().getMap(mapId);
                    if (mapView != null) {
                        Player player = fromMage != null ? fromMage.getPlayer() : null;
                        List<MapRenderer> renderers = mapView.getRenderers();
                        if (renderers.size() > 0) {
                            mapCanvas = new BufferedMapCanvas();
                            MapRenderer renderer = renderers.get(0);
                            // This is mainly here as a hack for my own urlmaps that do their own caching
                            // Bukkit *seems* to want to do caching at the MapView level, but looking at the code-
                            // they cache but never use the cache?
                            // Anyway render gets called constantly so I'm not re-rendering on each render... but then
                            // how to force a render to a canvas? So we re-initialize.
                            renderer.initialize(mapView);
                            renderer.render(mapView, mapCanvas, player);
                        }
                    }
                } catch (Exception ex) {
                    controller.getLogger().log(Level.WARNING, "Error reading map id " + mapId, ex);
                }
            }
            isTargetValid = false;
            if (mapCanvas != null && cloneTarget != null) {
                Vector diff = target.toVector().subtract(cloneTarget.toVector());

                // TODO : Different orientations, centering, scaling, etc
                // We default to 1/8 scaling for now to make the portraits work well.
                DyeColor mapColor = DyeColor.WHITE;
                if (orientVector.getBlockY() > orientVector.getBlockZ() || orientVector.getBlockY() > orientVector.getBlockX()) {
                    if (orientVector.getBlockX() > orientVector.getBlockZ()) {
                        mapColor = mapCanvas.getDyeColor(
                                Math.abs((int)(diff.getBlockX() * scale + BufferedMapCanvas.CANVAS_WIDTH / 2) % BufferedMapCanvas.CANVAS_WIDTH),
                                Math.abs((int)(-diff.getBlockY() * scale + BufferedMapCanvas.CANVAS_HEIGHT / 2) % BufferedMapCanvas.CANVAS_HEIGHT));
                    } else {
                        mapColor = mapCanvas.getDyeColor(
                                Math.abs((int)(diff.getBlockZ() * scale + BufferedMapCanvas.CANVAS_WIDTH / 2) % BufferedMapCanvas.CANVAS_WIDTH),
                                Math.abs((int)(-diff.getBlockY() * scale + BufferedMapCanvas.CANVAS_HEIGHT / 2) % BufferedMapCanvas.CANVAS_HEIGHT));
                    }
                } else {
                    mapColor = mapCanvas.getDyeColor(
                        Math.abs((int)(diff.getBlockX() * scale + BufferedMapCanvas.CANVAS_WIDTH / 2) % BufferedMapCanvas.CANVAS_WIDTH),
                        Math.abs((int)(diff.getBlockZ() * scale + BufferedMapCanvas.CANVAS_HEIGHT / 2) % BufferedMapCanvas.CANVAS_HEIGHT));
                }
                if (mapColor != null) {
                    this.material = mapMaterialBase;
                    DefaultMaterials.getInstance().colorize(this, mapColor);
                    isTargetValid = this.material != null;
                } else if (mapMaterialDefault != null) {
                    this.material = mapMaterialDefault;
                    isTargetValid = true;
                }
            }
        }

        return true;
    }

    protected boolean checkSchematic() {
        if (schematic == null && mage != null) {
            if (schematicName.length() == 0) {
                isValid = false;
                return false;
            }

            schematic = mage.getController().loadSchematic(schematicName);
            if (schematic == null) {
                schematicName = "";
                isValid = false;
                return false;
            }
        }

        return schematic != null && schematic.isLoaded();
    }

    @Override
    public void prepare() {
        if (materialTarget != null) {
            CompatibilityLib.getCompatibilityUtils().checkChunk(materialTarget);
        }
    }

    public void load(BrushData data)
    {
        cloneSource = data.getCloneLocation();
        cloneTarget = data.getCloneTarget();
        materialTarget = data.getMaterialTarget();
        schematicName = data.getSchematicName();
        mapId = data.getMapId();
        material = data.getMaterial();
        this.data = data.getMaterialData();
        scale = data.getScale();
        fillWithAir = data.isFillWithAir();
    }

    public void save(BrushData data)
    {
        data.setCloneLocation(cloneSource);
        data.setCloneTarget(cloneTarget);
        data.setMaterialTarget(materialTarget);
        data.setSchematicName(schematicName);
        data.setMapId(mapId);
        data.setMaterial(material);
        data.setMaterialData(this.data);
        data.setScale(scale);
        data.setFillWithAir(fillWithAir);
    }

    @Override
    public boolean hasEntities()
    {
        return mode == BrushMode.CLONE || mode == BrushMode.REPLICATE || mode == BrushMode.SCHEMATIC;
    }

    @Nullable
    @Override
    public Collection<Entity> getTargetEntities() {
        if (cloneTarget == null || mage == null) return null;

        if (mode == BrushMode.CLONE || mode == BrushMode.REPLICATE || mode == BrushMode.SCHEMATIC)
        {
            List<Entity> targetData = new ArrayList<>();
            World targetWorld = cloneTarget.getWorld();
            List<Entity> targetEntities = targetWorld.getEntities();
            for (Entity entity : targetEntities) {
                // Schematics currently only deal with Hanging entities
                if (mode == BrushMode.SCHEMATIC && !(entity instanceof Hanging)) continue;

                // Note that we ignore players and NPCs
                if (!(entity instanceof Player) && !mage.getController().isNPC(entity)) {
                    targetData.add(entity);
                }
            }

            return targetData;
        }

        return null;
    }

    protected void addEntities(Collection<Entity> source, Collection<com.elmakers.mine.bukkit.api.entity.EntityData> destination) {
        for (Entity entity : source) {
            if (!(entity instanceof Player || entity instanceof Item || controller.isNPC(entity))) {
                EntityData entityData = new EntityData(controller, entity);
                Location translated = fromTargetLocation(cloneTarget.getWorld(), entityData.getLocation(), entity instanceof Hanging);
                entityData.setLocation(translated);
                destination.add(entityData);
            }
        }
    }

    @Override
    @Nullable
    public Collection<com.elmakers.mine.bukkit.api.entity.EntityData> getEntities(Collection<Chunk> chunks) {
        if (cloneTarget == null) return null;

        if ((mode == BrushMode.CLONE || mode == BrushMode.REPLICATE) && cloneSource != null) {
            World sourceWorld = cloneSource.getWorld();
            List<com.elmakers.mine.bukkit.api.entity.EntityData> copyEntities = new ArrayList<>();
            if (chunks == null) {
                addEntities(sourceWorld.getEntities(), copyEntities);
            } else {
                for (Chunk chunk : chunks) {
                    Location chunkLocation = chunk.getBlock(0, 0, 0).getLocation();
                    chunkLocation = toTargetLocation(sourceWorld, chunkLocation);
                    Chunk sourceChunk = chunkLocation.getChunk();
                    // This has to be loaded synchronously or else we have to re-work this code quite a bit.
                    sourceChunk.load();
                    addEntities(Arrays.asList(sourceChunk.getEntities()), copyEntities);
                }
            }
            return copyEntities;
        }
        else if (mode == BrushMode.SCHEMATIC) {
            if (schematic != null) {
                return schematic.getEntities(cloneTarget);
            }
        }

        return null;
    }

    @Nullable
    @Override
    public Collection<com.elmakers.mine.bukkit.api.entity.EntityData> getEntities() {
        return getEntities(null);
    }

    @Override
    public void activate(final Location location, final String material) {
        String materialKey = splitMaterialKey(material)[0];
        if ((materialKey.equals(CLONE_MATERIAL_KEY) || materialKey.equals(REPLICATE_MATERIAL_KEY)) && location != null) {
            Location cloneFrom = location.clone();
            cloneFrom.setY(cloneFrom.getY() - 1);
            setCloneLocation(cloneFrom);
        } else if (materialKey.equals(MAP_MATERIAL_KEY) || materialKey.equals(SCHEMATIC_MATERIAL_KEY)) {
            clearCloneTarget();
        }
    }

    @Override
    public void setTarget(Location target) {
        setTarget(target, target);
    }

    @Override
    public void setTarget(Location target, Location center) {
        if (target == null || center == null || mage == null) return;
        if (parent != null) {
            parent.setTarget(target, center);
        }
        orientVector = target.toVector().subtract(center.toVector());
        orientVector.setX(Math.abs(orientVector.getX()));
        orientVector.setY(Math.abs(orientVector.getY()));
        orientVector.setZ(Math.abs(orientVector.getZ()));

        if (mode == BrushMode.REPLICATE || mode == BrushMode.CLONE || mode == BrushMode.MAP || mode == BrushMode.SCHEMATIC) {
            if (cloneTarget == null
                    || mode == BrushMode.CLONE
                    || !center.getWorld().getName().equals(cloneTarget.getWorld().getName())) {
                cloneTarget = center.clone();
                if (targetOffset != null) {
                    cloneTarget = cloneTarget.add(targetOffset);
                }
            } else if (mode == BrushMode.SCHEMATIC) {
                checkSchematic();
                boolean recenter = true;

                if (schematic != null && schematic.isLoaded()) {
                    Vector diff = target.toVector().subtract(cloneTarget.toVector());
                    recenter = !schematic.contains(diff);
                }

                if (recenter) {
                    cloneTarget = center.clone();
                    if (targetOffset != null) {
                        cloneTarget = cloneTarget.add(targetOffset);
                    }
                }
            }

            cloneTarget.setX(cloneTarget.getBlockX());
            cloneTarget.setY(cloneTarget.getBlockY());
            cloneTarget.setZ(cloneTarget.getBlockZ());

            if (cloneSource == null) {
                cloneSource = cloneTarget.clone();
                if (targetWorldName != null && targetWorldName.length() > 0) {
                    World sourceWorld = cloneSource.getWorld();
                    cloneSource.setWorld(ConfigurationUtils.overrideWorld(controller, targetWorldName, sourceWorld, mage.getController().canCreateWorlds()));
                }
            }
            if (materialTarget == null) {
                materialTarget = cloneTarget;
            }
        }
        if (mode == BrushMode.COPY) {
            Block block = target.getBlock();
            if (targetOffset != null) {
                Location targetLocation = block.getLocation();
                targetLocation = targetLocation.add(targetOffset);
                block = targetLocation.getBlock();
            }
            updateFromBlock(block, mage.getRestrictedMaterialSet());
        }
    }

    @Override
    public Vector getSize() {
        if (mode != BrushMode.SCHEMATIC) {
            return new Vector(0, 0, 0);
        }

        if (!checkSchematic()) {
            return new Vector(0, 0, 0);
        }

        return schematic.getSize();
    }

    @Override
    public BrushMode getMode()
    {
        return mode;
    }

    @Override
    public boolean isEraseModifierActive()
    {
        return fillWithAir;
    }

    @Override
    public boolean isErase()
    {
        return mode == BrushMode.ERASE || (mode == BrushMode.MATERIAL && material == Material.AIR);
    }

    @Nullable
    public ItemStack getItem(MageController controller, boolean isItem) {
        Messages messages = controller.getMessages();
        boolean urlIcons = mage == null ? controller.isUrlIconsEnabled() : mage.isUrlIconsEnabled();
        Short data = this.getData();
        MaterialAndData icon = data == null ? new MaterialAndData(this.getMaterial()) : new MaterialAndData(this.getMaterial(), data);
        String extraLore = null;
        String customName = getName(messages);
        ItemStack itemStack = null;

        if (mode == BrushMode.ERASE) {
            icon = MaterialBrush.EraseMaterial;
            if (EraseCustomIcon != null && !EraseCustomIcon.isEmpty() && urlIcons) {
                itemStack = controller.getURLSkull(EraseCustomIcon);
            }
            extraLore = messages.get("wand.erase_material_description");
        } else if (mode == BrushMode.COPY) {
            icon = MaterialBrush.CopyMaterial;
            if (CopyCustomIcon != null && !CopyCustomIcon.isEmpty() && urlIcons) {
                itemStack = controller.getURLSkull(CopyCustomIcon);
            }
            extraLore = messages.get("wand.copy_material_description");
        } else if (mode == BrushMode.CLONE) {
            icon = MaterialBrush.CloneMaterial;
            if (CloneCustomIcon != null && !CloneCustomIcon.isEmpty() && urlIcons) {
                itemStack = controller.getURLSkull(CloneCustomIcon);
            }
            extraLore = messages.get("wand.clone_material_description");
        } else if (mode == BrushMode.REPLICATE) {
            icon = MaterialBrush.ReplicateMaterial;
            if (ReplicateCustomIcon != null && !ReplicateCustomIcon.isEmpty() && urlIcons) {
                itemStack = controller.getURLSkull(ReplicateCustomIcon);
            }
            extraLore = messages.get("wand.replicate_material_description");
        } else if (mode == BrushMode.MAP) {
            icon = MaterialBrush.MapMaterial;
            if (MapCustomIcon != null && !MapCustomIcon.isEmpty() && urlIcons) {
                itemStack = controller.getURLSkull(MapCustomIcon);
            }
            extraLore = messages.get("wand.map_material_description");
        } else if (mode == BrushMode.SCHEMATIC) {
            icon = MaterialBrush.SchematicMaterial;
            if (SchematicCustomIcon != null && !SchematicCustomIcon.isEmpty() && urlIcons) {
                itemStack = controller.getURLSkull(SchematicCustomIcon);
            }
            extraLore = messages.get("wand.schematic_material_description").replace("$schematic", schematicName);
        } else {
            MaterialAndData replacementMaterial = replacements.get(icon);
            if (replacementMaterial != null) {
                icon = replacementMaterial;
            }
            extraLore = messages.get("wand.building_material_description").replace("$material", customName);
        }

        if (itemStack == null) {
            itemStack = icon.getItemStack(1);
            itemStack = CompatibilityLib.getItemUtils().makeReal(itemStack);
            if (itemStack == null) {
                if (DefaultBrushCustomIcon != null && !DefaultBrushCustomIcon.isEmpty() && urlIcons) {
                    itemStack = controller.getURLSkull(DefaultBrushCustomIcon);
                }
                if (itemStack == null) {
                    itemStack = DefaultBrushMaterial.getItemStack(1);
                    itemStack = CompatibilityLib.getItemUtils().makeReal(itemStack);
                    if (itemStack == null) {
                        return itemStack;
                    }
                }
            }
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) return null;
        List<String> lore = new ArrayList<>();
        if (extraLore != null) {
            lore.add(ChatColor.LIGHT_PURPLE + extraLore);
        }
        if (blockData != null && !blockData.isEmpty()) {
            ConfigurationUtils.addIfNotEmpty(messages.get("brush.block_data_description").replace("$data", blockData), lore);
        }
        if (isItem) {
            ConfigurationUtils.addIfNotEmpty(messages.get("wand.brush_item_description"), lore);
        }
        meta.setLore(lore);
        meta.setDisplayName(customName);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public static void configureReplacements(ConfigurationSection replacementConfig) {
        replacements.clear();
        if (replacementConfig == null) return;
        Set<String> keys = replacementConfig.getKeys(false);
        for (String key : keys) {
            MaterialAndData toMaterial = ConfigurationUtils.getMaterialAndData(replacementConfig, key);
            MaterialAndData fromMaterial = ConfigurationUtils.toMaterialAndData(key);
            replacements.put(fromMaterial, toMaterial);
        }
    }

    public void colorize(DyeColor color) {
         DefaultMaterials.getInstance().colorize(this, color);
    }

    @Override
    public String toString() {
        return mode + ": " + super.toString();
    }

    public void addCommandMapping(String key, String command) {
        if (commandMap == null) {
            commandMap = new HashMap<>();
        }

        commandMap.put(key,  command);
    }

    @Override
    protected boolean allowContainers() {
        return mage == null ? super.allowContainers() : mage.allowContainerCopy();
    }
}
