package org.betterx.bclib.api.v2.generator;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import org.jetbrains.annotations.ApiStatus;

public final class NetherBiomesHelper {
    private static final Set<ResourceKey<Biome>> VANILLA_NETHER = new HashSet<>(
            MultiNoiseBiomeSourceParameterList.Preset.NETHER.usedBiomes().toList()
    );
    private static final Set<ResourceKey<Biome>> ADDED_BIOMES = new HashSet<>();
    private static final Map<ResourceKey<Biome>, List<Climate.ParameterPoint>> ADDED_PARAMETERS = new HashMap<>();

    private NetherBiomesHelper() {
    }

    @ApiStatus.Internal
    public static synchronized void addNetherBiome(ResourceKey<Biome> biome, Climate.ParameterPoint parameters) {
        if (biome == null || parameters == null) {
            return;
        }
        if (VANILLA_NETHER.contains(biome)) {
            return;
        }
        ADDED_BIOMES.add(biome);
        List<Climate.ParameterPoint> list = ADDED_PARAMETERS.computeIfAbsent(biome, key -> new ArrayList<>());
        if (!list.contains(parameters)) {
            list.add(parameters);
        }
    }

    public static boolean canGenerateInNether(ResourceKey<Biome> biome) {
        return biome != null && (VANILLA_NETHER.contains(biome) || ADDED_BIOMES.contains(biome));
    }

    @ApiStatus.Internal
    public static synchronized Map<ResourceKey<Biome>, List<Climate.ParameterPoint>> getAdditionalParameters() {
        final Map<ResourceKey<Biome>, List<Climate.ParameterPoint>> copy = new HashMap<>();
        ADDED_PARAMETERS.forEach((biome, points) -> copy.put(biome, List.copyOf(points)));
        return Map.copyOf(copy);
    }

    @ApiStatus.Internal
    public static synchronized void clearCustomBiomes() {
        ADDED_BIOMES.clear();
        ADDED_PARAMETERS.clear();
    }
}
