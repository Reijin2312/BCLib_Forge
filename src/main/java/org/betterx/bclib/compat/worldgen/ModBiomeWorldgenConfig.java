package org.betterx.bclib.compat.worldgen;

import org.betterx.bclib.config.PathConfig;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModBiomeWorldgenConfig {
    private static final String BETTER_NETHER_NAMESPACE = "betternether";
    private static final String BETTER_END_NAMESPACE = "betterend";

    private static final float DEFAULT_MULTIPLIER = 1.0F;
    private static final float MIN_SIZE_MULTIPLIER = 0.05F;
    private static final float MAX_SIZE_MULTIPLIER = 16.0F;
    private static final float MIN_FREQUENCY_MULTIPLIER = 0.0F;
    private static final float MAX_FREQUENCY_MULTIPLIER = 32.0F;

    private static final PathConfig BETTER_NETHER_BIOMES = new PathConfig(
            BETTER_NETHER_NAMESPACE,
            "biomes_worldgen",
            false
    );
    private static final PathConfig BETTER_END_BIOMES = new PathConfig(
            BETTER_END_NAMESPACE,
            "biomes_worldgen",
            false
    );

    private ModBiomeWorldgenConfig() {
    }

    public static ModSnapshot loadBetterNether(List<ResourceKey<Biome>> biomeKeys) {
        synchronized (BETTER_NETHER_BIOMES) {
            return loadSnapshot(BETTER_NETHER_BIOMES, BETTER_NETHER_NAMESPACE, biomeKeys, "Nether");
        }
    }

    public static ModSnapshot loadBetterEnd(List<ResourceKey<Biome>> biomeKeys) {
        synchronized (BETTER_END_BIOMES) {
            return loadSnapshot(BETTER_END_BIOMES, BETTER_END_NAMESPACE, biomeKeys, "End");
        }
    }

    private static ModSnapshot loadSnapshot(
            PathConfig config,
            String targetNamespace,
            List<ResourceKey<Biome>> biomeKeys,
            String dimensionName
    ) {
        registerGlobalComments(config, dimensionName);
        final float globalSize = clampSize(config.getFloat("worldgen", "global_size_multiplier", DEFAULT_MULTIPLIER));
        final float globalFrequency = clampFrequency(
                config.getFloat("worldgen", "global_frequency_multiplier", DEFAULT_MULTIPLIER)
        );

        final Map<ResourceKey<Biome>, BiomeWorldgenSettings> settings = new LinkedHashMap<>();
        for (ResourceKey<Biome> biomeKey : biomeKeys) {
            if (biomeKey == null || !targetNamespace.equals(biomeKey.location().getNamespace())) {
                continue;
            }

            final String category = biomeKey.location().getNamespace() + "." + biomeKey.location().getPath() + ".worldgen";
            registerBiomeComments(config, category, dimensionName);

            final boolean enabled = config.getBoolean(category, "enabled", true);
            final float sizeMultiplier = clampSize(config.getFloat(category, "size_multiplier", DEFAULT_MULTIPLIER));
            final float frequencyMultiplier = clampFrequency(
                    config.getFloat(category, "frequency_multiplier", DEFAULT_MULTIPLIER)
            );
            settings.put(
                    biomeKey,
                    new BiomeWorldgenSettings(enabled, sizeMultiplier, frequencyMultiplier)
            );
        }

        config.saveChanges();
        return new ModSnapshot(
                globalSize,
                globalFrequency,
                targetNamespace,
                settings.isEmpty() ? Map.of() : Map.copyOf(settings)
        );
    }

    private static void registerGlobalComments(PathConfig config, String dimensionName) {
        config.getString(
                "worldgen",
                "_comment_00",
                "Worldgen controls for " + dimensionName + " biomes. Edit values, then restart world."
        );
        config.getString(
                "worldgen",
                "_comment_global_size_multiplier",
                "global_size_multiplier: scales biome size globally. Default: 1.0. Possible: 0.05..16.0"
        );
        config.getString(
                "worldgen",
                "_comment_global_frequency_multiplier",
                "global_frequency_multiplier: scales biome spawn frequency globally. Default: 1.0. Possible: 0.0..32.0"
        );
    }

    private static void registerBiomeComments(PathConfig config, String category, String dimensionName) {
        config.getString(
                category,
                "_comment_00",
                "Per-biome worldgen controls for " + dimensionName + "."
        );
        config.getString(
                category,
                "_comment_enabled",
                "enabled: true/false. Default: true"
        );
        config.getString(
                category,
                "_comment_size_multiplier",
                "size_multiplier: biome size multiplier. Default: 1.0. Possible: 0.05..16.0"
        );
        config.getString(
                category,
                "_comment_frequency_multiplier",
                "frequency_multiplier: biome spawn frequency multiplier. Default: 1.0. Possible: 0.0..32.0"
        );
    }

    private static float clampSize(float value) {
        if (!Float.isFinite(value)) {
            return DEFAULT_MULTIPLIER;
        }
        return Mth.clamp(value, MIN_SIZE_MULTIPLIER, MAX_SIZE_MULTIPLIER);
    }

    private static float clampFrequency(float value) {
        if (!Float.isFinite(value)) {
            return DEFAULT_MULTIPLIER;
        }
        return Mth.clamp(value, MIN_FREQUENCY_MULTIPLIER, MAX_FREQUENCY_MULTIPLIER);
    }

    public record BiomeWorldgenSettings(
            boolean enabled,
            float sizeMultiplier,
            float frequencyMultiplier
    ) {
        public static final BiomeWorldgenSettings DEFAULT = new BiomeWorldgenSettings(true, 1.0F, 1.0F);
    }

    public record ModSnapshot(
            float globalSizeMultiplier,
            float globalFrequencyMultiplier,
            String managedNamespace,
            Map<ResourceKey<Biome>, BiomeWorldgenSettings> byBiome
    ) {
        public BiomeWorldgenSettings settingsFor(ResourceKey<Biome> biomeKey) {
            return byBiome.getOrDefault(biomeKey, BiomeWorldgenSettings.DEFAULT);
        }

        public float globalSizeFor(ResourceKey<Biome> biomeKey) {
            return isManagedBiome(biomeKey) ? globalSizeMultiplier : 1.0F;
        }

        public float globalFrequencyFor(ResourceKey<Biome> biomeKey) {
            return isManagedBiome(biomeKey) ? globalFrequencyMultiplier : 1.0F;
        }

        public boolean isManagedBiome(ResourceKey<Biome> biomeKey) {
            return biomeKey != null && biomeKey.location().getNamespace().equals(managedNamespace);
        }
    }
}
