package org.betterx.worlds.together.levelgen;

import org.betterx.bclib.BCLib;
import org.betterx.bclib.api.v2.generator.BCLChunkGenerator;
import org.betterx.bclib.api.v2.levelgen.biomes.BCLBiomeRegistry;
import org.betterx.bclib.api.v2.levelgen.biomes.BiomeAPI;
import org.betterx.bclib.config.Configs;
import org.betterx.worlds.together.biomesource.BiomeSourceWithConfig;
import org.betterx.worlds.together.biomesource.ReloadableBiomeSource;
import org.betterx.worlds.together.chunkgenerator.EnforceableChunkGenerator;
import org.betterx.worlds.together.worldPreset.TogetherWorldPreset;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

import java.util.Map;

class BiomeRepairHelper {
    private Map<ResourceKey<LevelStem>, ChunkGenerator> vanillaDimensions = null;

    public Registry<LevelStem> repairBiomeSourceInAllDimensions(
            RegistryAccess registryAccess,
            Registry<LevelStem> dimensionRegistry
    ) {
        Map<ResourceKey<LevelStem>, ChunkGenerator> dimensions = TogetherWorldPreset.loadWorldDimensions();
        for (var entry : dimensionRegistry.entrySet()) {
            boolean didRepair = false;
            ResourceKey<LevelStem> key = entry.getKey();
            LevelStem loadedStem = entry.getValue();

            ChunkGenerator referenceGenerator = dimensions.get(key);
            if (referenceGenerator instanceof EnforceableChunkGenerator enforcer) {
                final ChunkGenerator loadedChunkGenerator = loadedStem.generator();
                final ChunkGenerator externalChunkGenerator = getExternalBaseGenerator(registryAccess, key);
                logCompat(
                        "Repair dimension {}: referenceGenerator={}, referenceBiomeSource={}, loadedGenerator={}, loadedBiomeSource={}, externalGenerator={}, externalBiomeSource={}",
                        key.location(),
                        describeObject(referenceGenerator),
                        describeObject(referenceGenerator.getBiomeSource()),
                        describeObject(loadedChunkGenerator),
                        describeObject(loadedChunkGenerator.getBiomeSource()),
                        describeObject(externalChunkGenerator),
                        externalChunkGenerator == null ? "null" : describeObject(externalChunkGenerator.getBiomeSource())
                );
                attachExternalBiomeSource(
                        registryAccess,
                        key,
                        loadedStem,
                        referenceGenerator,
                        externalChunkGenerator
                );
                attachExternalBiomeSource(
                        registryAccess,
                        key,
                        loadedStem,
                        loadedChunkGenerator,
                        externalChunkGenerator
                );

                // we ensure that all biomes with a dimensional Tag are properly added to the correct biome source
                // using the correct type
                processBiomeTagsForDimension(key);

                // if the loaded ChunkGenerator is not the one we expect from vanilla, we will load the vanilla
                // ones and mark all modded biomes with the respective dimension
                registerAllBiomesFromVanillaDimension(key);

                // now compare the reference world settings (the ones that were created when the world was
                // started) with the settings that were loaded by the game.
                // If those do not match, we will create a new ChunkGenerator / BiomeSources with appropriate
                // settings
                if (enforcer.togetherShouldRepair(loadedChunkGenerator)) {
                    dimensionRegistry = enforcer.enforceGeneratorInWorldGenSettings(
                            registryAccess,
                            key,
                            loadedStem.type().unwrapKey().orElseThrow(),
                            loadedChunkGenerator,
                            dimensionRegistry
                    );
                    didRepair = true;
                } else if (loadedChunkGenerator.getBiomeSource() instanceof BiomeSourceWithConfig lodedSource) {
                    if (referenceGenerator.getBiomeSource() instanceof BiomeSourceWithConfig refSource) {
                        if (!refSource.getTogetherConfig().sameConfig(lodedSource.getTogetherConfig())) {
                            lodedSource.setTogetherConfig(refSource.getTogetherConfig());
                        }
                    }
                }
            }


            if (!didRepair) {
                if (loadedStem.generator().getBiomeSource() instanceof ReloadableBiomeSource reload) {
                    reload.reloadBiomes();
                }
            }

        }
        return dimensionRegistry;
    }

    private ChunkGenerator getExternalBaseGenerator(RegistryAccess registryAccess, ResourceKey<LevelStem> key) {
        if (LevelStem.NETHER.equals(key)) {
            Holder<NoiseGeneratorSettings> settings = registryAccess
                    .registryOrThrow(Registries.NOISE_SETTINGS)
                    .getHolderOrThrow(NoiseGeneratorSettings.NETHER);
            Holder<MultiNoiseBiomeSourceParameterList> parameters = registryAccess
                    .registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                    .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER);
            ChunkGenerator generator = new NoiseBasedChunkGenerator(
                    MultiNoiseBiomeSource.createFromPreset(parameters),
                    settings
            );
            logCompat(
                    "Created external vanilla Nether generator: generator={}, biomeSource={}, settings={}",
                    describeObject(generator),
                    describeObject(generator.getBiomeSource()),
                    settings.unwrapKey().map(ResourceKey::location).orElse(null)
            );
            return generator;
        }

        logCompat("No external base generator for {}", key.location());
        return null;
    }

    private void attachExternalBiomeSource(
            RegistryAccess registryAccess,
            ResourceKey<LevelStem> key,
            LevelStem loadedStem,
            ChunkGenerator targetGenerator,
            ChunkGenerator externalChunkGenerator
    ) {
        if (externalChunkGenerator == null || targetGenerator == externalChunkGenerator) {
            logCompat(
                    "Skipping attach for {}: targetGenerator={}, externalGenerator={}",
                    key.location(),
                    describeObject(targetGenerator),
                    describeObject(externalChunkGenerator)
            );
            return;
        }

        if (targetGenerator instanceof BCLChunkGenerator bclGenerator) {
            logCompat(
                    "Attaching external biome source for {}: targetGenerator={}, targetBiomeSource={}, externalGenerator={}, externalBiomeSource={}",
                    key.location(),
                    describeObject(targetGenerator),
                    describeObject(targetGenerator.getBiomeSource()),
                    describeObject(externalChunkGenerator),
                    describeObject(externalChunkGenerator.getBiomeSource())
            );
            bclGenerator.mergeExternalBiomeSource(
                    registryAccess,
                    key,
                    loadedStem.type(),
                    externalChunkGenerator
            );
        } else {
            logCompat(
                    "Target generator is not BCL, not attaching external source for {}: targetGenerator={}",
                    key.location(),
                    describeObject(targetGenerator)
            );
        }
    }

    private static void logCompat(String message, Object... args) {
        if (Configs.MAIN_CONFIG.verboseLogging()) {
            BCLib.LOGGER.info("[BiomeRepairCompat] " + message, args);
        }
    }

    private static String describeObject(Object object) {
        if (object == null) {
            return "null";
        }
        return object.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(object));
    }

    private void processBiomeTagsForDimension(ResourceKey<LevelStem> key) {
        // External biome engines (TerraBlender, Biolith and mods built on top of them)
        // own their placement. Importing tagged/region biomes into BCLBiomeRegistry makes
        // BCL pick them with BCL weights and can also pull technical or cave biomes into
        // Nether/End maps. Keep BCL registration limited to actual BCL biome entries.
    }

    private void registerAllBiomesFromVanillaDimension(
            ResourceKey<LevelStem> key
    ) {
        BiomeAPI.BiomeType type = BiomeAPI.BiomeType.getMainBiomeTypeForDimension(key);

        if (type != null) {
            if (vanillaDimensions == null) {
                vanillaDimensions = TogetherWorldPreset.getDimensionsMap(net.minecraft.world.level.levelgen.presets.WorldPresets.NORMAL);
            }

            final ChunkGenerator vanillaDim = vanillaDimensions.getOrDefault(key, null);
            if (vanillaDim != null && vanillaDim.getBiomeSource() != null) {
                for (Holder<Biome> biomeHolder : vanillaDim.getBiomeSource().possibleBiomes()) {
                    BCLBiomeRegistry.registerIfUnknown(biomeHolder, type);
                }
            }
        }
    }
}
