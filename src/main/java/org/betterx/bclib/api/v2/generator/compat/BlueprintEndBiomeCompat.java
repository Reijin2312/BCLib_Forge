package org.betterx.bclib.api.v2.generator.compat;

import org.betterx.bclib.api.v2.generator.BCLBiomeSource;
import org.betterx.bclib.api.v2.generator.BCLibEndBiomeSource;
import org.betterx.bclib.api.v2.levelgen.biomes.BCLBiome;
import org.betterx.bclib.api.v2.levelgen.biomes.BCLBiomeRegistry;
import org.betterx.bclib.api.v2.levelgen.biomes.BiomeAPI;
import org.betterx.worlds.together.world.event.WorldBootstrap;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BlueprintEndBiomeCompat {
    private BlueprintEndBiomeCompat() {
    }

    public static Map<ResourceKey<Biome>, Collection<Holder<Biome>>> createMatchExpansions(
            BiomeSource originalSource
    ) {
        if (!(originalSource instanceof BCLibEndBiomeSource bclSource)) {
            return Map.of();
        }

        RegistryAccess access = WorldBootstrap.getLastRegistryAccess();
        if (access == null) {
            return Map.of();
        }

        bclSource.reloadBiomes();
        Registry<BCLBiome> bclBiomes = access.registry(BCLBiomeRegistry.BCL_BIOMES_REGISTRY).orElse(null);
        Map<ResourceKey<Biome>, List<Holder<Biome>>> expansions = new HashMap<>();

        for (Holder<Biome> holder : bclSource.ownedPossibleBiomes()) {
            ResourceKey<Biome> key = holder.unwrapKey().orElse(null);
            if (key == null || "minecraft".equals(key.location().getNamespace())) {
                continue;
            }

            BCLBiome bclBiome = BCLBiomeRegistry.getBiomeOrNull(key, bclBiomes);
            if (bclBiome == null) {
                continue;
            }

            BiomeAPI.BiomeType type = bclBiome.getIntendedType();
            if (type.is(BiomeAPI.BiomeType.END_LAND)) {
                add(expansions, Biomes.END_HIGHLANDS, holder);
                add(expansions, Biomes.END_MIDLANDS, holder);
            } else if (type.is(BiomeAPI.BiomeType.END_BARRENS)) {
                add(expansions, Biomes.END_BARRENS, holder);
            } else if (type.is(BiomeAPI.BiomeType.END_VOID)) {
                add(expansions, Biomes.SMALL_END_ISLANDS, holder);
            } else if (type.is(BiomeAPI.BiomeType.END_CENTER)) {
                add(expansions, Biomes.THE_END, holder);
            }
        }

        Map<ResourceKey<Biome>, Collection<Holder<Biome>>> result = new HashMap<>();
        expansions.forEach((key, holders) -> result.put(key, List.copyOf(holders)));
        return Map.copyOf(result);
    }

    private static void add(
            Map<ResourceKey<Biome>, List<Holder<Biome>>> expansions,
            ResourceKey<Biome> vanillaBiome,
            Holder<Biome> replacement
    ) {
        List<Holder<Biome>> holders = expansions.computeIfAbsent(vanillaBiome, ignored -> new ArrayList<>());
        if (!holders.contains(replacement)) {
            holders.add(replacement);
        }
    }
}
