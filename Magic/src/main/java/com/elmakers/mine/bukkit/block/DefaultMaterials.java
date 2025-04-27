package com.elmakers.mine.bukkit.block;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;

import com.elmakers.mine.bukkit.api.magic.MaterialSet;
import com.elmakers.mine.bukkit.api.magic.MaterialSetManager;
import com.elmakers.mine.bukkit.materials.MaterialSets;
import com.elmakers.mine.bukkit.utility.StringUtils;

public class DefaultMaterials {
    private static DefaultMaterials instance;

    private MaterialSet commandBlocks = MaterialSets.empty();
    private MaterialSet halfBlocks = MaterialSets.empty();
    private MaterialSet water = MaterialSets.empty();
    private MaterialSet lava = MaterialSets.empty();
    private MaterialSet skulls = MaterialSets.empty();
    private MaterialSet playerSkulls = MaterialSets.empty();
    private MaterialSet banners = MaterialSets.empty();
    private MaterialSet signs = MaterialSets.empty();
    private MaterialSet saplings = MaterialSets.empty();
    private MaterialSet air = MaterialSets.empty();
    private MaterialSet shulkerBoxes = MaterialSets.empty();

    private MaterialAndData playerSkullItem = null;
    private MaterialAndData playerSkullWallBlock = null;
    private MaterialAndData skeletonSkullItem = null;
    private Material groundSignBlock = null;
    private Material wallSignBlock = null;
    private Material firework = null;
    private Material fireworkStar = null;
    private Material mobSpawner = null;
    private Material filledMap = null;
    private Material netherPortal = null;
    private Material writeableBook = null;
    private MaterialAndData wallTorch = null;
    private MaterialAndData redstoneTorch = null;
    private MaterialAndData redstoneWallTorch = null;

    private Map<Material, Map<DyeColor, MaterialAndData>> materialColors = new HashMap<>();
    private Map<Material, Material> colorMap = new HashMap<>();
    private Map<Material, Material> blockItems = new HashMap<>();
    private Map<String, Biome> biomeMap = new HashMap<>();
    private Map<String, Material> migrations = new HashMap<>();

    private Map<Material, List<Material>> materialVariants = new HashMap<>();
    private Map<Material, Material> variantMap = new HashMap<>();

    private DefaultMaterials() {
    }

    public static DefaultMaterials getInstance() {
        if (instance == null) {
            instance = new DefaultMaterials();
        }
        return instance;
    }

    public void initialize(MaterialSetManager manager) {
        commandBlocks = manager.getMaterialSet("commands");
        water = manager.getMaterialSet("all_water");
        lava = manager.getMaterialSet("all_lava");
        air = manager.getMaterialSet("all_air");
        halfBlocks = manager.getMaterialSet("half");
        skulls = manager.getMaterialSet("skulls");
        playerSkulls = manager.getMaterialSet("player_skulls");
        banners = manager.getMaterialSet("banners");
        signs = manager.getMaterialSet("signs");
        saplings = manager.getMaterialSet("saplings");
        shulkerBoxes = manager.getMaterialSet("shulker_boxes");
    }

    public void addMigration(String key, Material material) {
        if (material != null) {
            migrations.put(key.toUpperCase(Locale.ROOT), material);
        }
    }

    public void loadColors(Collection<ConfigurationSection> colors) {
        for (ConfigurationSection colorSection : colors) {
            Material keyColor = null;
            Map<DyeColor, MaterialAndData> newColors = new HashMap<>();
            for (DyeColor color : DyeColor.values()) {
                String colorName = color.name().toLowerCase();
                if (colorName.equals("silver")) {
                    colorName = "light_gray";
                }
                String materialName = colorSection.getString(colorName);
                if (materialName == null || materialName.isEmpty()) break;
                MaterialAndData parsed = null;
                parsed = new MaterialAndData(materialName.toUpperCase());
                if (!parsed.isValid()) {
                    break;
                }

                newColors.put(color, parsed);
                if (keyColor == null) {
                    keyColor = parsed.getMaterial();
                }
            }
            if (newColors.size() != DyeColor.values().length) continue;

            materialColors.put(keyColor, newColors);
            for (MaterialAndData mat : newColors.values()) {
                colorMap.put(mat.getMaterial(), keyColor);
            }
        }
    }

    public void loadVariants(Collection<Object> variantLists) {
        for (Object rawList : variantLists) {
            if (!(rawList instanceof List)) continue;

            @SuppressWarnings("unchecked")
            List<String> variantList = (List<String>)rawList;

            Material baseVariant = null;
            List<Material> materialList = new ArrayList<>();
            for (String materialKey : variantList) {
                try {
                    Material material = Material.getMaterial(materialKey.toUpperCase());
                    if (baseVariant == null) {
                        baseVariant = material;
                    }

                    variantMap.put(material, baseVariant);
                    materialList.add(material);
                } catch (Exception ignore) {

                }
            }
            if (baseVariant != null) {
                materialVariants.put(baseVariant, materialList);
            }
        }
    }

    public void setGroundSignBlock(Material material) {
        this.groundSignBlock = material;
    }

    @Nullable
    public static Material getGroundSignBlock() {
        return getInstance().groundSignBlock;
    }

    public void setWallSignBlock(Material material) {
        this.wallSignBlock = material;
    }

    @Nullable
    public static Material getWallSignBlock() {
        return getInstance().wallSignBlock;
    }

    public void setFirework(Material material) {
        this.firework = material;
    }

    @Nullable
    public static Material getFirework() {
        return getInstance().firework;
    }

    @Nullable
    public static Material getFireworkStar() {
        return getInstance().fireworkStar;
    }

    public void setFireworkStar(Material material) {
        this.fireworkStar = material;
    }

    public void setMobSpawner(Material material) {
        this.mobSpawner = material;
    }

    @Nullable
    public static Material getNetherPortal() {
        return getInstance().netherPortal;
    }

    public void setNetherPortal(Material material) {
        this.netherPortal = material;
    }

    @Nullable
    public static Material getWriteableBook() {
        return getInstance().writeableBook;
    }

    public void setWriteableBook(Material material) {
        this.writeableBook = material;
    }

    @Nullable
    public static Material getMobSpawner() {
        return getInstance().mobSpawner;
    }

    public void setFilledMap(Material material) {
        this.filledMap = material;
    }

    @Nullable
    public static Material getFilledMap() {
        return getInstance().filledMap;
    }

    public void setPlayerSkullItem(MaterialAndData item) {
        playerSkullItem = item;
    }

    @Nullable
    public static MaterialAndData getPlayerSkullItem() {
        return getInstance().playerSkullItem;
    }

    public void setPlayerSkullWallBlock(MaterialAndData item) {
        playerSkullWallBlock = item;
    }

    @Nullable
    public static MaterialAndData getPlayerSkullWallBlock() {
        return getInstance().playerSkullWallBlock;
    }

    public void setSkeletonSkullItem(MaterialAndData item) {
        skeletonSkullItem = item;
    }

    @Nullable
    public static MaterialAndData getSkeletonSkullItem() {
        return getInstance().skeletonSkullItem;
    }

    public void loadBiomeMap(ConfigurationSection biomeConfig) {
        biomeMap.clear();
        if (biomeConfig == null) return;
        Set<String> biomeKeys = biomeConfig.getKeys(false);
        for (String biomeKey : biomeKeys) {
            try {
                String value = biomeConfig.getString(biomeKey);
                Biome biome = Biome.valueOf(value.toUpperCase());
                biomeMap.put(biomeKey.toUpperCase(), biome);
            } catch (Exception ignore) {
            }
        }
    }

    public void loadBlockItems(ConfigurationSection blocks) {
        Set<String> blockKeys = blocks.getKeys(false);
        for (String blockKey : blockKeys) {
            try {
                Material blockMaterial = Material.getMaterial(blockKey.toUpperCase());
                String itemKey = blocks.getString(blockKey);
                Material itemMaterial = Material.getMaterial(itemKey.toUpperCase());
                blockItems.put(blockMaterial, itemMaterial);
            } catch (Exception ignore) {

            }
        }
    }

    @Nullable
    public static Material getBaseColor(@Nullable Material material) {
        return material == null ? null : getInstance().colorMap.get(material);
    }

    @Nullable
    public Material getBaseMaterial(@Nullable Material material) {
        material = material == null ? null : colorMap.get(material);
        if (material == null && colorMap.size() > 0) {
            for (Material m : colorMap.values()) {
                material = m;
                break;
            }
        }
        return material;
    }

    public void colorize(@Nonnull MaterialAndData materialAndData, @Nonnull DyeColor color) {
        Material material = colorMap.get(materialAndData.getMaterial());
        if (material == null) {
            return;
        }
        Map<DyeColor, MaterialAndData> materialMap = materialColors.get(material);
        if (materialMap == null) {
            return;
        }
        MaterialAndData colored = materialMap.get(color);
        if (colored != null) {
            materialAndData.material = colored.material;
            materialAndData.data = colored.data;
        }
    }

    public static Collection<MaterialAndData> getColorBlocks(Material base) {
        return getInstance().materialColors.get(base).values();
    }

    @Nullable
    public static Material getBaseVariant(@Nullable Material material) {
        return material == null ? null : getInstance().variantMap.get(material);
    }

    @Nullable
    private Biome getSingleBiome(String biomeKey) {
        biomeKey = biomeKey.toUpperCase();
        Biome biome = biomeMap.get(biomeKey);
        if (biome == null) {
            try {
                biome = Biome.valueOf(biomeKey);
            } catch (Exception ignore) {

            }
        }
        return biome;
    }

    @Nullable
    public Biome getBiome(String biomeKey) {
        if (biomeKey == null) return null;
        biomeKey = biomeKey.trim();
        if (biomeKey.isEmpty()) return null;
        String[] biomeKeys = StringUtils.split(biomeKey, ",");
        for (String singleKey : biomeKeys) {
            Biome biome = getSingleBiome(singleKey);
            if (biome != null) {
                return biome;
            }
        }
        return null;
    }

    @Nullable
    public static Collection<Material> getVariants(Material base) {
        return getInstance().materialVariants.get(base);
    }

    public Collection<Map<DyeColor, MaterialAndData>> getAllColorBlocks() {
        return materialColors.values();
    }

    public Collection<List<Material>> getAllVariants() {
        return materialVariants.values();
    }

    public static boolean isCommand(Material material) {
        return getInstance().commandBlocks.testMaterial(material);
    }

    public static boolean isHalfBlock(Material material) {
        return getInstance().halfBlocks.testMaterial(material);
    }

    public static boolean isWater(Material material) {
        return getInstance().water.testMaterial(material);
    }

    public static boolean isLava(Material material) {
        return getInstance().lava.testMaterial(material);
    }

    public static boolean isPlayerSkull(MaterialAndData materialAndData) {
        return getInstance().playerSkulls.testMaterialAndData(materialAndData);
    }

    public static boolean isShulkerBox(Material material) {
        return getInstance().shulkerBoxes.testMaterial(material);
    }

    public static boolean isSkull(Material material) {
        return getInstance().skulls.testMaterial(material);
    }

    public static boolean isBanner(Material material) {
        return getInstance().banners.testMaterial(material);
    }

    public static boolean isSign(Material material) {
        return getInstance().signs.testMaterial(material);
    }

    public static boolean isSapling(Material material) {
        return getInstance().saplings.testMaterial(material);
    }

    public static boolean isMobSpawner(Material material) {
        return getInstance().mobSpawner == material;
    }

    public static boolean isFilledMap(Material material) {
        return getInstance().filledMap == material;
    }

    public static Collection<Material> getWater() {
        return getInstance().water.getMaterials();
    }

    public static MaterialSet getWaterSet() {
        return getInstance().water;
    }

    public static Collection<Material> getLava() {
        return getInstance().lava.getMaterials();
    }

    public static Material blockToItem(Material block) {
        Material item = getInstance().blockItems.get(block);
        return item == null ? block : item;
    }

    public static MaterialAndData getWallTorch() {
        return getInstance().wallTorch;
    }

    public void setWallTorch(MaterialAndData wallTorch) {
        this.wallTorch = wallTorch;
    }

    public static MaterialAndData getRedstoneTorchOn() {
        return getInstance().redstoneTorch;
    }

    public void setRedstoneTorch(MaterialAndData redstoneTorch) {
        this.redstoneTorch = redstoneTorch;
    }

    public static MaterialAndData getRedstoneWallTorchOn() {
        return getInstance().redstoneWallTorch;
    }

    public void setRedstoneWallTorch(MaterialAndData redstoneWallTorch) {
        this.redstoneWallTorch = redstoneWallTorch;
    }

    public static boolean isAir(Material material) {
        return getInstance().air.testMaterial(material);
    }

    public static boolean isBow(Material material) {
        return material == Material.BOW || material.name().equals("CROSSBOW");
    }

    @Nullable
    public static Material migrateMaterial(String key) {
        return getInstance().migrations.get(key.toUpperCase(Locale.ROOT));
    }
}
