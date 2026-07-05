package org.betterx.worlds.together.surfaceRules;

import org.betterx.bclib.BCLib;
import org.betterx.bclib.api.v2.generator.BCLBiomeSource;
import org.betterx.bclib.config.Configs;
import org.betterx.worlds.together.chunkgenerator.InjectableSurfaceRules;
import org.betterx.worlds.together.surfaceRules.compat.ExternalSurfaceRuleCompat;
import org.betterx.worlds.together.world.event.WorldBootstrap;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class SurfaceRuleUtil {
    private static List<SurfaceRules.RuleSource> getRulesForBiome(ResourceLocation biomeID) {
        Registry<AssignedSurfaceRule> registry = null;
        if (WorldBootstrap.getLastRegistryAccess() != null)
            registry = WorldBootstrap.getLastRegistryAccess()
                                     .registryOrThrow(SurfaceRuleRegistry.SURFACE_RULES_REGISTRY);

        if (registry == null) return List.of();

        return registry.stream()
                       .filter(a -> a != null && a.biomeID != null && a.biomeID.equals(
                               biomeID))
                       .map(a -> a.ruleSource)
                       .toList();

    }

    private static List<SurfaceRules.RuleSource> getRulesForBiomes(List<Biome> biomes) {
        Registry<Biome> biomeRegistry = WorldBootstrap.getLastRegistryAccess().registryOrThrow(Registries.BIOME);
        List<ResourceLocation> biomeIDs = biomes.stream()
                                                .map(b -> biomeRegistry.getKey(b))
                                                .filter(id -> id != null)
                                                .toList();

        return biomeIDs.stream()
                       .map(biomeID -> getRulesForBiome(biomeID))
                       .flatMap(List::stream)
                       .collect(Collectors.toCollection(LinkedList::new));
    }

    private static SurfaceRules.RuleSource mergeSurfaceRules(
            ResourceKey<LevelStem> dimensionKey,
            SurfaceRules.RuleSource org,
            BiomeSource source,
            List<SurfaceRules.RuleSource> additionalRules
    ) {
        if (additionalRules == null || additionalRules.isEmpty()) return null;
        final int count = additionalRules.size();
        if (org instanceof SurfaceRules.SequenceRuleSource sequenceRule) {
            List<SurfaceRules.RuleSource> existingSequence = sequenceRule.sequence();
            additionalRules = additionalRules
                    .stream()
                    .filter(r -> existingSequence.indexOf(r) < 0)
                    .collect(Collectors.toList());
            if (additionalRules.isEmpty()) return null;

            // Keep nether roof/floor guards at the beginning, then place BCL biome rules before
            // vanilla or external biome-specific surface rules. Late catch-all rules must remain last.
            if (dimensionKey.equals(LevelStem.NETHER)) {
                final List<SurfaceRules.RuleSource> combined = new ArrayList<>(existingSequence.size() + additionalRules.size());
                boolean inserted = false;
                for (SurfaceRules.RuleSource rule : existingSequence) {
                    if (!inserted
                            && rule instanceof SurfaceRules.TestRuleSource testRule
                            && testRule.ifTrue() instanceof SurfaceRules.BiomeConditionSource) {
                        combined.addAll(additionalRules);
                        inserted = true;
                    }
                    combined.add(rule);
                }
                if (!inserted) {
                    combined.addAll(additionalRules);
                }
                additionalRules = combined;
            } else {
                additionalRules.addAll(existingSequence);
            }
        } else {
            if (!additionalRules.contains(org))
                additionalRules.add(org);
        }

        if (Configs.MAIN_CONFIG.verboseLogging()) {
            BCLib.LOGGER.info("Merged " + count + " additional Surface Rules for " + source + " => " + additionalRules.size());
        }
        return new SurfaceRules.SequenceRuleSource(additionalRules);
    }

    public static void injectSurfaceRules(
            ResourceKey<LevelStem> dimensionKey,
            NoiseGeneratorSettings noiseSettings,
            BiomeSource loadedBiomeSource
    ) {
        if (((Object) noiseSettings) instanceof SurfaceRuleProvider srp) {
            SurfaceRules.RuleSource originalRules = srp.bclib_getOriginalSurfaceRules();
            List<Biome> biomesWithBCLRules = loadedBiomeSource instanceof BCLBiomeSource bclBiomeSource
                    ? bclBiomeSource.ownedPossibleBiomes().stream().map(Holder::value).toList()
                    : loadedBiomeSource.possibleBiomes().stream().map(Holder::value).toList();
            List<SurfaceRules.RuleSource> additionalRules = new ArrayList<>(getRulesForBiomes(biomesWithBCLRules));
            additionalRules.addAll(ExternalSurfaceRuleCompat.getRules(dimensionKey, loadedBiomeSource));
            srp.bclib_overwriteSurfaceRules(mergeSurfaceRules(
                    dimensionKey,
                    originalRules,
                    loadedBiomeSource,
                    additionalRules
            ));
            invalidateTerraBlenderSurfaceRuleCache(noiseSettings);
        }
    }

    private static void invalidateTerraBlenderSurfaceRuleCache(NoiseGeneratorSettings noiseSettings) {
        try {
            Field cache = noiseSettings.getClass().getDeclaredField("namespacedSurfaceRuleSource");
            cache.setAccessible(true);
            cache.set(noiseSettings, null);
        } catch (NoSuchFieldException ignored) {
        } catch (ReflectiveOperationException e) {
            BCLib.LOGGER.warning("Unable to invalidate TerraBlender surface rule cache", e);
        }
    }

    public static void injectSurfaceRulesToAllDimensions(Registry<LevelStem> dimensionRegistry) {
        for (var entry : dimensionRegistry.entrySet()) {
            ResourceKey<LevelStem> key = entry.getKey();
            LevelStem stem = entry.getValue();

            if (stem.generator() instanceof InjectableSurfaceRules<?> generator) {
                generator.injectSurfaceRules(key);
            }
        }
    }
}
