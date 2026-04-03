package org.betterx.bclib.compat.terrablender;

import org.betterx.bclib.api.v2.generator.NetherBiomesHelper;
import org.betterx.bclib.api.v2.levelgen.biomes.BCLBiome;
import org.betterx.bclib.api.v2.levelgen.biomes.BCLBiomeRegistry;
import org.betterx.bclib.api.v2.levelgen.biomes.BiomeAPI;
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
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;

public class BCLibNetherRegion extends Region {
    private static final float SIZE_MULTIPLIER = 0.7F;
    // Expand climate spans to make biome areas significantly larger.
    private static final float SIZE_EXPANSION_PRIMARY = 0.18F * SIZE_MULTIPLIER;
    private static final float SIZE_EXPANSION_SECONDARY = 0.28F * SIZE_MULTIPLIER;
    // Deterministic per-variant shift to avoid identical overlays across all variants.
    private static final float SECOND_LAYER_TEMP_SHIFT = 0.06F * SIZE_MULTIPLIER;
    private static final float SECOND_LAYER_HUMIDITY_SHIFT = 0.06F * SIZE_MULTIPLIER;
    private static final float SECOND_LAYER_WEIRDNESS_SHIFT = 0.04F * SIZE_MULTIPLIER;

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
            final List<Climate.ParameterPoint> basePoints = bcl$getBasePoints(members, additional);
            if (basePoints.isEmpty()) {
                continue;
            }

            final List<WeightedBiome> variants = bcl$buildVariants(
                    rootKey,
                    members,
                    registry,
                    bclRegistry
            );
            if (variants.isEmpty()) {
                continue;
            }

            for (Climate.ParameterPoint point : basePoints) {
                if (point == null) {
                    continue;
                }

                final ResourceKey<Biome> selectedBiome = bcl$pickVariant(rootKey, point, variants);
                if (selectedBiome == null || !registry.containsKey(selectedBiome)) {
                    continue;
                }

                bcl$mapExpandedPoint(mapper, point, selectedBiome);
            }
        }
    }

    private void bcl$mapExpandedPoint(
            Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper,
            Climate.ParameterPoint point,
            ResourceKey<Biome> biomeKey
    ) {
        mapper.accept(Pair.of(point, biomeKey));
        mapper.accept(Pair.of(bcl$expandPoint(point, SIZE_EXPANSION_PRIMARY), biomeKey));
        mapper.accept(Pair.of(bcl$expandPoint(point, SIZE_EXPANSION_SECONDARY), biomeKey));

        final int biomeHash = biomeKey.location().hashCode();
        final float biomePhase = (biomeHash & 1023) / 1024.0F;
        final float phase = ((float) this.variantIndex / (float) this.variantCount) + biomePhase;
        final float angle = phase * ((float) Math.PI * 2.0F);
        final float tempShift = Mth.sin(angle) * SECOND_LAYER_TEMP_SHIFT;
        final float humidityShift = Mth.cos(angle) * SECOND_LAYER_HUMIDITY_SHIFT;
        final float weirdnessShift = Mth.sin(angle + 1.5707964F) * SECOND_LAYER_WEIRDNESS_SHIFT;
        mapper.accept(
                Pair.of(
                        bcl$shiftAndExpandPoint(
                                point,
                                tempShift,
                                humidityShift,
                                weirdnessShift,
                                SIZE_EXPANSION_PRIMARY
                        ),
                        biomeKey
                )
        );
    }

    private List<WeightedBiome> bcl$buildVariants(
            ResourceKey<Biome> rootKey,
            List<ResourceKey<Biome>> members,
            Registry<Biome> biomeRegistry,
            Registry<BCLBiome> bclRegistry
    ) {
        final Map<ResourceKey<Biome>, Float> weights = new LinkedHashMap<>();
        if (rootKey != null && biomeRegistry.containsKey(rootKey)) {
            weights.put(rootKey, 1.0F);
        }

        for (ResourceKey<Biome> member : members) {
            if (member == null || member.equals(rootKey) || !biomeRegistry.containsKey(member)) {
                continue;
            }

            final BCLBiome biome = BCLBiomeRegistry.getBiomeOrNull(member, bclRegistry);
            final float weight = biome == null ? 1.0F : biome.settings.getGenChance();
            if (weight > 0.0F) {
                weights.put(member, weight);
            }
        }

        if (weights.isEmpty()) {
            return List.of();
        }

        return weights.entrySet()
                      .stream()
                      .map(entry -> new WeightedBiome(entry.getKey(), entry.getValue()))
                      .toList();
    }

    private static List<Climate.ParameterPoint> bcl$getBasePoints(
            List<ResourceKey<Biome>> members,
            Map<ResourceKey<Biome>, List<Climate.ParameterPoint>> additional
    ) {
        final LinkedHashSet<Climate.ParameterPoint> collected = new LinkedHashSet<>();
        for (ResourceKey<Biome> member : members) {
            final List<Climate.ParameterPoint> points = additional.get(member);
            if (points != null && !points.isEmpty()) {
                collected.addAll(points);
            }
        }

        return collected.isEmpty() ? List.of() : List.copyOf(collected);
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

    private static Climate.ParameterPoint bcl$expandPoint(Climate.ParameterPoint point, float expansion) {
        return Climate.parameters(
                bcl$expand(point.temperature(), expansion),
                bcl$expand(point.humidity(), expansion),
                bcl$expand(point.continentalness(), expansion),
                bcl$expand(point.erosion(), expansion),
                bcl$expand(point.depth(), expansion),
                bcl$expand(point.weirdness(), expansion),
                Climate.unquantizeCoord(point.offset())
        );
    }

    private static Climate.ParameterPoint bcl$shiftAndExpandPoint(
            Climate.ParameterPoint point,
            float temperatureShift,
            float humidityShift,
            float weirdnessShift,
            float expansion
    ) {
        return Climate.parameters(
                bcl$shiftAndExpand(point.temperature(), temperatureShift, expansion),
                bcl$shiftAndExpand(point.humidity(), humidityShift, expansion),
                bcl$expand(point.continentalness(), expansion),
                bcl$expand(point.erosion(), expansion),
                bcl$expand(point.depth(), expansion),
                bcl$shiftAndExpand(point.weirdness(), weirdnessShift, expansion),
                Climate.unquantizeCoord(point.offset())
        );
    }

    private static Climate.Parameter bcl$shiftAndExpand(Climate.Parameter parameter, float shift, float expansion) {
        final float min = Climate.unquantizeCoord(parameter.min());
        final float max = Climate.unquantizeCoord(parameter.max());
        final float center = (min + max) * 0.5F + shift;
        final float halfSize = (max - min) * 0.5F + expansion;
        final float outMin = Mth.clamp(center - halfSize, -2.0F, 2.0F);
        final float outMax = Mth.clamp(center + halfSize, -2.0F, 2.0F);
        return Climate.Parameter.span(outMin, outMax);
    }

    private static Climate.Parameter bcl$expand(Climate.Parameter parameter, float expansion) {
        return bcl$shiftAndExpand(parameter, 0.0F, expansion);
    }

    private record WeightedBiome(ResourceKey<Biome> biomeKey, float weight) {}
}
