package org.betterx.bclib.api.v2.generator;

import org.betterx.bclib.api.v2.levelgen.biomes.BiomeAPI;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.ApiStatus;


/**
 * Helper class until FAPI integrates <a href="https://github.com/FabricMC/fabric/pull/2369">this PR</a>
 */
public class TheEndBiomesHelper {
    @ApiStatus.Internal
    private static final Map<BiomeAPI.BiomeType, Set<ResourceKey<Biome>>> END_BIOMES = new HashMap<>();

    static {
        resetToVanilla();
    }

    @ApiStatus.Internal
    public static synchronized void add(BiomeAPI.BiomeType type, ResourceKey<Biome> biome) {
        if (biome == null) return;
        END_BIOMES.computeIfAbsent(type, t -> new HashSet<>()).add(biome);
    }

    private static synchronized boolean has(BiomeAPI.BiomeType type, ResourceKey<Biome> biome) {
        if (biome == null) return false;
        Set<ResourceKey<Biome>> set = END_BIOMES.get(type);
        if (set == null) return false;
        return set.contains(biome);
    }

    @ApiStatus.Internal
    public static synchronized void resetToVanilla() {
        END_BIOMES.clear();
        add(BiomeAPI.BiomeType.END_CENTER, Biomes.THE_END);
        add(BiomeAPI.BiomeType.END_LAND, Biomes.END_HIGHLANDS);
        add(BiomeAPI.BiomeType.END_LAND, Biomes.END_MIDLANDS);
        add(BiomeAPI.BiomeType.END_BARRENS, Biomes.END_BARRENS);
        add(BiomeAPI.BiomeType.END_VOID, Biomes.SMALL_END_ISLANDS);
    }

    /**
     * Returns true if the given biome was added as a main end Biome in the end, considering the Vanilla end biomes,
     * and any biomes added to the End by mods.
     *
     * @param biome The biome to search for
     */
    public static boolean canGenerateAsMainIslandBiome(ResourceKey<Biome> biome) {
        return has(BiomeAPI.BiomeType.END_CENTER, biome);
    }

    /**
     * Returns true if the given biome was added as a small end islands Biome in the end, considering the Vanilla end biomes,
     * and any biomes added to the End by mods.
     *
     * @param biome The biome to search for
     */
    public static boolean canGenerateAsSmallIslandsBiome(ResourceKey<Biome> biome) {
        return has(BiomeAPI.BiomeType.END_VOID, biome);
    }

    /**
     * Returns true if the given biome was added as a Highland Biome in the end, considering the Vanilla end biomes,
     * and any biomes added to the End by mods.
     *
     * @param biome The biome to search for
     */
    public static boolean canGenerateAsHighlandsBiome(ResourceKey<Biome> biome) {
        return has(BiomeAPI.BiomeType.END_LAND, biome);
    }

    /**
     * Returns true if the given biome was added as midland biome in the end, considering the Vanilla end biomes,
     * and any biomes added to the End as midland biome by mods.
     *
     * @param biome The biome to search for
     */
    public static boolean canGenerateAsEndMidlands(ResourceKey<Biome> biome) {
        return has(BiomeAPI.BiomeType.END_LAND, biome);
    }

    /**
     * Returns true if the given biome was added as barrens biome in the end, considering the Vanilla end biomes,
     * and any biomes added to the End as barrens biome by mods.
     *
     * @param biome The biome to search for
     */
    public static boolean canGenerateAsEndBarrens(ResourceKey<Biome> biome) {
        return has(BiomeAPI.BiomeType.END_BARRENS, biome);
    }

    @ApiStatus.Internal
    public static void addMainIslandBiome(ResourceKey<Biome> biome, double weight) {
        add(BiomeAPI.BiomeType.END_CENTER, biome);
    }

    @ApiStatus.Internal
    public static void addHighlandsBiome(ResourceKey<Biome> biome, double weight) {
        add(BiomeAPI.BiomeType.END_LAND, biome);
    }

    @ApiStatus.Internal
    public static void addSmallIslandsBiome(ResourceKey<Biome> biome, double weight) {
        add(BiomeAPI.BiomeType.END_VOID, biome);
    }

    @ApiStatus.Internal
    public static void addMidlandsBiome(ResourceKey<Biome> highlands, ResourceKey<Biome> midlands, double weight) {
        add(BiomeAPI.BiomeType.END_LAND, midlands);
        add(BiomeAPI.BiomeType.END_LAND, highlands);
    }

    @ApiStatus.Internal
    public static void addBarrensBiome(ResourceKey<Biome> highlands, ResourceKey<Biome> barrens, double weight) {
        add(BiomeAPI.BiomeType.END_BARRENS, barrens);
        add(BiomeAPI.BiomeType.END_LAND, highlands);
    }

    public static boolean canGenerateInEnd(ResourceKey<Biome> biome) {
        return canGenerateAsHighlandsBiome(biome)
                || canGenerateAsEndBarrens(biome)
                || canGenerateAsEndMidlands(biome)
                || canGenerateAsSmallIslandsBiome(biome)
                || canGenerateAsMainIslandBiome(biome);
    }
}
