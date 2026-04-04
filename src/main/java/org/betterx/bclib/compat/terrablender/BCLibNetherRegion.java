package org.betterx.bclib.compat.terrablender;

import org.betterx.bclib.api.v2.generator.NetherBiomesHelper;
import org.betterx.bclib.api.v2.levelgen.biomes.BCLBiome;
import org.betterx.bclib.api.v2.levelgen.biomes.BCLBiomeRegistry;
import org.betterx.bclib.api.v2.levelgen.biomes.BiomeAPI;
import org.betterx.bclib.compat.worldgen.ModBiomeWorldgenConfig;
import org.betterx.worlds.together.world.event.WorldBootstrap;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import terrablender.api.Region;
import terrablender.api.RegionType;

import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;

public class BCLibNetherRegion extends Region {
    private final int variantIndex;
    private final int variantCount;

    public BCLibNetherRegion(ResourceLocation location, int weight, int variantIndex, int variantCount) {
        super(location, RegionType.NETHER, weight);
        this.variantIndex = variantIndex;
        this.variantCount = Math.max(1, variantCount);
    }

    @Override
    public void addBiomes(
            Registry<Biome> registry,
            Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper
    ) {
        BCLBiomeRegistry.syncFromRegistry(WorldBootstrap.getLastRegistryAccess());

        final Registry<BCLBiome> bclRegistry = bcl$getBCLBiomeRegistry();
        final Map<ResourceKey<Biome>, List<Climate.ParameterPoint>> additional = bcl$collectAdditionalParameters(
                registry,
                bclRegistry
        );
        if (additional.isEmpty()) {
            return;
        }

        final List<ResourceKey<Biome>> sortedBiomeKeys = additional
                .keySet()
                .stream()
                .filter(key -> key != null && registry.containsKey(key))
                .sorted(Comparator.comparing(key -> key.location().toString()))
                .toList();
        if (sortedBiomeKeys.isEmpty()) {
            return;
        }

        final ModBiomeWorldgenConfig.ModSnapshot worldgenConfig = ModBiomeWorldgenConfig.loadBetterNether(
                sortedBiomeKeys
        );

        final Map<ResourceKey<Biome>, ResourceKey<Biome>> rootByBiome = new HashMap<>();
        for (ResourceKey<Biome> key : sortedBiomeKeys) {
            ResourceKey<Biome> root = bcl$resolveRootKey(key, bclRegistry);
            if (root == null) {
                root = key;
            }
            rootByBiome.put(key, root);
        }

        final Map<ResourceKey<Biome>, List<ResourceKey<Biome>>> membersByRoot = new LinkedHashMap<>();
        for (ResourceKey<Biome> key : sortedBiomeKeys) {
            final ResourceKey<Biome> root = rootByBiome.getOrDefault(key, key);
            membersByRoot.computeIfAbsent(root, ignored -> new ArrayList<>()).add(key);
        }

        final List<Map.Entry<ResourceKey<Biome>, List<ResourceKey<Biome>>>> sortedRoots = membersByRoot
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().location().toString()))
                .toList();

        for (Map.Entry<ResourceKey<Biome>, List<ResourceKey<Biome>>> rootEntry : sortedRoots) {
            final ResourceKey<Biome> rootKey = rootEntry.getKey();
            final List<ResourceKey<Biome>> members = rootEntry.getValue();
            final Map<Climate.ParameterPoint, List<WeightedBiome>> variantsByPoint = bcl$buildPointVariants(
                    members,
                    registry,
                    bclRegistry,
                    additional,
                    worldgenConfig
            );
            if (variantsByPoint.isEmpty()) {
                continue;
            }

            for (Map.Entry<Climate.ParameterPoint, List<WeightedBiome>> pointEntry : variantsByPoint.entrySet()) {
                final Climate.ParameterPoint point = pointEntry.getKey();
                final List<WeightedBiome> variants = pointEntry.getValue();
                if (point == null || variants == null || variants.isEmpty()) {
                    continue;
                }

                final ResourceKey<Biome> selectedBiome = bcl$pickVariant(rootKey, point, variants);
                if (selectedBiome == null || !registry.containsKey(selectedBiome)) {
                    continue;
                }

                bcl$mapExpandedPoint(mapper, point, selectedBiome, worldgenConfig);
            }
        }
    }

    private void bcl$mapExpandedPoint(
            Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper,
            Climate.ParameterPoint point,
            ResourceKey<Biome> biomeKey,
            ModBiomeWorldgenConfig.ModSnapshot worldgenConfig
    ) {
        final float sizeScale = bcl$getEffectiveSizeScale(biomeKey, worldgenConfig);
        if (sizeScale <= 0.0F) {
            return;
        }

        if (Math.abs(sizeScale - 1.0F) < 0.0001F) {
            mapper.accept(Pair.of(point, biomeKey));
            return;
        }

        mapper.accept(Pair.of(bcl$scalePoint(point, sizeScale), biomeKey));
    }

    private Map<Climate.ParameterPoint, List<WeightedBiome>> bcl$buildPointVariants(
            List<ResourceKey<Biome>> members,
            Registry<Biome> biomeRegistry,
            Registry<BCLBiome> bclRegistry,
            Map<ResourceKey<Biome>, List<Climate.ParameterPoint>> additional,
            ModBiomeWorldgenConfig.ModSnapshot worldgenConfig
    ) {
        final Map<Climate.ParameterPoint, Map<ResourceKey<Biome>, Float>> weightsByPoint = new LinkedHashMap<>();

        for (ResourceKey<Biome> member : members) {
            if (member == null || !biomeRegistry.containsKey(member)) {
                continue;
            }

            final List<Climate.ParameterPoint> points = additional.get(member);
            if (points == null || points.isEmpty()) {
                continue;
            }

            final BCLBiome biome = BCLBiomeRegistry.getBiomeOrNull(member, bclRegistry);
            final float baseWeight = biome == null ? 1.0F : biome.settings.getGenChance();
            final float weight = bcl$getEffectiveFrequencyWeight(member, baseWeight, worldgenConfig);
            if (weight <= 0.0F) {
                continue;
            }

            for (Climate.ParameterPoint point : points) {
                if (point == null) {
                    continue;
                }
                weightsByPoint
                        .computeIfAbsent(point, ignored -> new LinkedHashMap<>())
                        .put(member, weight);
            }
        }

        if (weightsByPoint.isEmpty()) {
            return Map.of();
        }

        final Map<Climate.ParameterPoint, List<WeightedBiome>> result = new LinkedHashMap<>();
        weightsByPoint.forEach((point, weights) -> {
            if (weights == null || weights.isEmpty()) {
                return;
            }

            final List<WeightedBiome> variants = weights.entrySet()
                                                        .stream()
                                                        .map(entry -> new WeightedBiome(entry.getKey(), entry.getValue()))
                                                        .toList();
            if (!variants.isEmpty()) {
                result.put(point, variants);
            }
        });

        return result.isEmpty() ? Map.of() : result;
    }

    private ResourceKey<Biome> bcl$pickVariant(
            ResourceKey<Biome> rootKey,
            Climate.ParameterPoint point,
            List<WeightedBiome> variants
    ) {
        if (variants.size() == 1) {
            return variants.get(0).biomeKey();
        }

        float totalWeight = 0.0F;
        for (WeightedBiome variant : variants) {
            totalWeight += variant.weight();
        }
        if (totalWeight <= 0.0F) {
            return variants.get(0).biomeKey();
        }

        final long seed = bcl$mix64(
                ((long) rootKey.location().hashCode() * 0x9E3779B97F4A7C15L)
                        ^ (bcl$pointHash(point) * 0x94D049BB133111EBL)
                        ^ ((long) this.variantIndex * 0xBF58476D1CE4E5B9L)
                        ^ ((long) this.variantCount * 0x632BE59BD9B4E019L)
        );
        final double unit = (double) (seed >>> 11) * 0x1.0p-53;
        final float target = (float) (unit * totalWeight);

        float sum = 0.0F;
        for (WeightedBiome variant : variants) {
            sum += variant.weight();
            if (target <= sum) {
                return variant.biomeKey();
            }
        }

        return variants.get(variants.size() - 1).biomeKey();
    }

    private static long bcl$pointHash(Climate.ParameterPoint point) {
        long hash = 1469598103934665603L;
        hash = (hash ^ point.temperature().min()) * 1099511628211L;
        hash = (hash ^ point.temperature().max()) * 1099511628211L;
        hash = (hash ^ point.humidity().min()) * 1099511628211L;
        hash = (hash ^ point.humidity().max()) * 1099511628211L;
        hash = (hash ^ point.continentalness().min()) * 1099511628211L;
        hash = (hash ^ point.continentalness().max()) * 1099511628211L;
        hash = (hash ^ point.erosion().min()) * 1099511628211L;
        hash = (hash ^ point.erosion().max()) * 1099511628211L;
        hash = (hash ^ point.depth().min()) * 1099511628211L;
        hash = (hash ^ point.depth().max()) * 1099511628211L;
        hash = (hash ^ point.weirdness().min()) * 1099511628211L;
        hash = (hash ^ point.weirdness().max()) * 1099511628211L;
        hash = (hash ^ point.offset()) * 1099511628211L;
        return hash;
    }

    private static long bcl$mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static ResourceKey<Biome> bcl$resolveRootKey(ResourceKey<Biome> biomeKey, Registry<BCLBiome> bclRegistry) {
        ResourceKey<Biome> current = biomeKey;
        for (int i = 0; i < 32; i++) {
            final BCLBiome biome = BCLBiomeRegistry.getBiomeOrNull(current, bclRegistry);
            if (biome == null || !biome.hasParentBiome()) {
                return current;
            }

            final BCLBiome parent = biome.getParentBiome();
            final ResourceKey<Biome> parentKey = parent == null ? null : parent.getBiomeKey();
            if (parentKey == null || parentKey.equals(current)) {
                return current;
            }

            current = parentKey;
        }

        return current;
    }

    private static Registry<BCLBiome> bcl$getBCLBiomeRegistry() {
        final RegistryAccess access = WorldBootstrap.getLastRegistryAccess();
        if (access != null) {
            final Registry<BCLBiome> registry = access
                    .registry(BCLBiomeRegistry.BCL_BIOMES_REGISTRY)
                    .orElse(null);
            if (registry != null) {
                return registry;
            }
        }
        return BCLBiomeRegistry.BUILTIN_BCL_BIOMES;
    }

    private static Map<ResourceKey<Biome>, List<Climate.ParameterPoint>> bcl$collectAdditionalParameters(
            Registry<Biome> biomeRegistry,
            Registry<BCLBiome> bclRegistry
    ) {
        final Map<ResourceKey<Biome>, List<Climate.ParameterPoint>> merged = new LinkedHashMap<>();

        // Registry-first: robust against helper timing/order changes.
        bcl$collectAdditionalFromBCLRegistry(merged, biomeRegistry, bclRegistry);
        if (BCLBiomeRegistry.BUILTIN_BCL_BIOMES != null && BCLBiomeRegistry.BUILTIN_BCL_BIOMES != bclRegistry) {
            bcl$collectAdditionalFromBCLRegistry(merged, biomeRegistry, BCLBiomeRegistry.BUILTIN_BCL_BIOMES);
        }

        // Compatibility merge for legacy helper registrations.
        NetherBiomesHelper.getAdditionalParameters().forEach((key, points) -> {
            if (key == null || !biomeRegistry.containsKey(key) || points == null || points.isEmpty()) {
                return;
            }
            points.forEach(point -> bcl$mergePoint(merged, key, point));
        });

        return merged.isEmpty() ? Map.of() : Map.copyOf(merged);
    }

    private static void bcl$collectAdditionalFromBCLRegistry(
            Map<ResourceKey<Biome>, List<Climate.ParameterPoint>> merged,
            Registry<Biome> biomeRegistry,
            Registry<BCLBiome> sourceRegistry
    ) {
        if (sourceRegistry == null) {
            return;
        }

        final List<Map.Entry<ResourceKey<BCLBiome>, BCLBiome>> sortedBCLBiomes = sourceRegistry
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().location().toString()))
                .toList();
        for (Map.Entry<ResourceKey<BCLBiome>, BCLBiome> entry : sortedBCLBiomes) {
            final BCLBiome bclBiome = entry.getValue();
            if (bclBiome == null || !bclBiome.getIntendedType().is(BiomeAPI.BiomeType.BCL_NETHER)) {
                continue;
            }

            final ResourceKey<Biome> biomeKey = bclBiome.getBiomeKey();
            if (biomeKey == null || !biomeRegistry.containsKey(biomeKey)) {
                continue;
            }

            bclBiome.forEachClimateParameter(point -> bcl$mergePoint(merged, biomeKey, point));
        }
    }

    private static void bcl$mergePoint(
            Map<ResourceKey<Biome>, List<Climate.ParameterPoint>> merged,
            ResourceKey<Biome> biomeKey,
            Climate.ParameterPoint point
    ) {
        if (biomeKey == null || point == null) {
            return;
        }
        final List<Climate.ParameterPoint> list = merged.computeIfAbsent(biomeKey, ignored -> new ArrayList<>());
        if (!list.contains(point)) {
            list.add(point);
        }
    }

    private static Climate.ParameterPoint bcl$scalePoint(Climate.ParameterPoint point, float scale) {
        return Climate.parameters(
                bcl$scaleParameter(point.temperature(), scale),
                bcl$scaleParameter(point.humidity(), scale),
                bcl$scaleParameter(point.continentalness(), scale),
                bcl$scaleParameter(point.erosion(), scale),
                bcl$scaleParameter(point.depth(), scale),
                bcl$scaleParameter(point.weirdness(), scale),
                Climate.unquantizeCoord(point.offset())
        );
    }

    private static Climate.Parameter bcl$scaleParameter(Climate.Parameter parameter, float scale) {
        final float min = Climate.unquantizeCoord(parameter.min());
        final float max = Climate.unquantizeCoord(parameter.max());
        final float center = (min + max) * 0.5F;
        final float halfSize = (max - min) * 0.5F * scale;
        final float outMin = Mth.clamp(center - halfSize, -2.0F, 2.0F);
        final float outMax = Mth.clamp(center + halfSize, -2.0F, 2.0F);
        return Climate.Parameter.span(outMin, outMax);
    }

    private static float bcl$getEffectiveSizeScale(
            ResourceKey<Biome> biomeKey,
            ModBiomeWorldgenConfig.ModSnapshot worldgenConfig
    ) {
        final ModBiomeWorldgenConfig.BiomeWorldgenSettings settings = worldgenConfig.settingsFor(biomeKey);
        if (!settings.enabled()) {
            return 0.0F;
        }

        return Mth.clamp(
                settings.sizeMultiplier() * worldgenConfig.globalSizeFor(biomeKey),
                0.05F,
                16.0F
        );
    }

    private static float bcl$getEffectiveFrequencyWeight(
            ResourceKey<Biome> biomeKey,
            float baseWeight,
            ModBiomeWorldgenConfig.ModSnapshot worldgenConfig
    ) {
        if (baseWeight <= 0.0F) {
            return 0.0F;
        }

        final ModBiomeWorldgenConfig.BiomeWorldgenSettings settings = worldgenConfig.settingsFor(biomeKey);
        if (!settings.enabled()) {
            return 0.0F;
        }

        return baseWeight
                * Mth.clamp(settings.frequencyMultiplier(), 0.0F, 32.0F)
                * Mth.clamp(worldgenConfig.globalFrequencyFor(biomeKey), 0.0F, 32.0F);
    }

    private record WeightedBiome(ResourceKey<Biome> biomeKey, float weight) {}
}
