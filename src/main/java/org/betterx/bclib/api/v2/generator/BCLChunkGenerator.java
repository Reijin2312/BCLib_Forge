package org.betterx.bclib.api.v2.generator;

import org.betterx.bclib.BCLib;
import org.betterx.bclib.api.v2.generator.compat.TerraBlenderNoiseSettingsCompat;
import org.betterx.bclib.api.v2.levelgen.LevelGenUtil;
import org.betterx.bclib.config.Configs;
import org.betterx.bclib.interfaces.NoiseGeneratorSettingsProvider;
import org.betterx.bclib.mixin.common.ChunkGeneratorAccessor;
import org.betterx.worlds.together.WorldsTogether;
import org.betterx.worlds.together.biomesource.BlueprintBiomeSourceCompat;
import org.betterx.worlds.together.biomesource.MergeableBiomeSource;
import org.betterx.worlds.together.biomesource.ReloadableBiomeSource;
import org.betterx.worlds.together.chunkgenerator.EnforceableChunkGenerator;
import org.betterx.worlds.together.chunkgenerator.InjectableSurfaceRules;
import org.betterx.worlds.together.chunkgenerator.RestorableBiomeSource;
import org.betterx.worlds.together.world.BiomeSourceWithNoiseRelatedSettings;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.types.templates.TypeTemplate;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.data.worldgen.SurfaceRuleData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import com.google.common.base.Suppliers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class BCLChunkGenerator extends NoiseBasedChunkGenerator implements RestorableBiomeSource<BCLChunkGenerator>, InjectableSurfaceRules<BCLChunkGenerator>, EnforceableChunkGenerator<BCLChunkGenerator> {

    public static final Codec<BCLChunkGenerator> CODEC = RecordCodecBuilder
            .create((RecordCodecBuilder.Instance<BCLChunkGenerator> builderInstance) -> {

                RecordCodecBuilder<BCLChunkGenerator, BiomeSource> biomeSourceCodec = BiomeSource.CODEC
                        .fieldOf("biome_source")
                        .forGetter((BCLChunkGenerator generator) -> generator.biomeSource);

                RecordCodecBuilder<BCLChunkGenerator, Holder<NoiseGeneratorSettings>> settingsCodec = NoiseGeneratorSettings.CODEC
                        .fieldOf("settings")
                        .forGetter((BCLChunkGenerator generator) -> generator.generatorSettings());


                return builderInstance.group(biomeSourceCodec, settingsCodec)
                                      .apply(builderInstance, builderInstance.stable(BCLChunkGenerator::new));
            });
    protected static final NoiseSettings NETHER_NOISE_SETTINGS_AMPLIFIED = NoiseSettings.create(0, 256, 1, 4);
    public static final ResourceKey<NoiseGeneratorSettings> AMPLIFIED_NETHER = ResourceKey.create(
            Registries.NOISE_SETTINGS,
            new ResourceLocation("bclib", "amplified_nether")
    );

    public final BiomeSource initialBiomeSource;
    private ResourceKey<LevelStem> knownDimensionKey;

    public BCLChunkGenerator(
            BiomeSource biomeSource,
            Holder<NoiseGeneratorSettings> holder
    ) {
        super(biomeSource, holder);
        initialBiomeSource = biomeSource;
        knownDimensionKey = null;
        if (biomeSource instanceof BCLBiomeSource bclBiomeSource) {
            bclBiomeSource.setExternalBiomeSourcePreparedCallback(this::refreshExternalGenerationState);
        }
        if (biomeSource instanceof BiomeSourceWithNoiseRelatedSettings bcl && holder.isBound()) {
            bcl.onLoadGeneratorSettings(holder.value());
        }

        if (WorldsTogether.RUNS_TERRABLENDER) {
            TerraBlenderNoiseSettingsCompat.applyRegionType(holder, null, biomeSource);
            BCLib.LOGGER.info("Make sure features are loaded from terrablender:" + biomeSource.getClass().getName());

            //terrablender is invalidating the feature initialization
            //we redo it at this point, otherwise we will get blank biomes
            rebuildFeaturesPerStep(biomeSource);
        }
    }

    private void rebuildFeaturesPerStep(BiomeSource biomeSource) {
        if (this instanceof ChunkGeneratorAccessor acc) {
            Function<Holder<Biome>, BiomeGenerationSettings> function = (Holder<Biome> hh) -> hh.value()
                                                                                                .getGenerationSettings();

            acc.bcl_setFeaturesPerStep(Suppliers.memoize(() -> FeatureSorter.buildFeaturesPerStep(
                    List.copyOf(biomeSource.possibleBiomes()),
                    (hh) -> function.apply(hh).features(),
                    true
            )));
            logCompat(
                    "Rebuilt featuresPerStep for {} with {} possible biomes: {}",
                    describeObject(this),
                    biomeSource.possibleBiomes().size(),
                    biomeSource instanceof BCLBiomeSource bclBiomeSource
                            ? bclBiomeSource.getNamespaces()
                            : biomeSource.possibleBiomes().size()
            );
        }
    }

    private void refreshExternalGenerationState() {
        if (knownDimensionKey != null && BlueprintBiomeSourceCompat.wraps(getBiomeSource(), initialBiomeSource)) {
            BiomeSource refreshedSource = BlueprintBiomeSourceCompat.refreshWrappedSource(
                    getBiomeSource(),
                    knownDimensionKey
            );
            if (refreshedSource != getBiomeSource() && this instanceof ChunkGeneratorAccessor acc) {
                acc.bcl_setBiomeSource(refreshedSource);
                logCompat(
                        "Refreshed Blueprint modded biome source for {}: wrapper={}, biomes={}",
                        knownDimensionKey.location(),
                        describeObject(refreshedSource),
                        refreshedSource.possibleBiomes().size()
                );
            }
        }
        rebuildFeaturesPerStep(getBiomeSource());
        if (knownDimensionKey != null) {
            injectSurfaceRules(knownDimensionKey);
        }
    }

    /**
     * Other Mods like TerraBlender might inject new BiomeSources. We undo that change after the world setup did run.
     *
     * @param dimensionKey The Dimension where this ChunkGenerator is used from
     */
    @Override
    public void restoreInitialBiomeSource(ResourceKey<LevelStem> dimensionKey) {
        if (BlueprintBiomeSourceCompat.wraps(getBiomeSource(), initialBiomeSource)) {
            logCompat(
                    "Keeping Blueprint modded biome source for {}: wrapper={}, original={}",
                    dimensionKey.location(),
                    describeObject(getBiomeSource()),
                    describeObject(initialBiomeSource)
            );
            rebuildFeaturesPerStep(getBiomeSource());
            return;
        }

        if (initialBiomeSource != getBiomeSource()) {
            if (this instanceof ChunkGeneratorAccessor acc) {
                if (initialBiomeSource instanceof MergeableBiomeSource bs) {
                    acc.bcl_setBiomeSource(bs.mergeWithBiomeSource(getBiomeSource()));
                } else if (initialBiomeSource instanceof ReloadableBiomeSource bs) {
                    bs.reloadBiomes();
                }

                rebuildFeaturesPerStep(getBiomeSource());
            }
        }
    }


    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public String toString() {
        return "BCLib - Chunk Generator (" + Integer.toHexString(hashCode()) + ")";
    }

    // This method is injected by Terrablender.
    // We make sure terrablender does not rewrite the feature-set for our ChunkGenerator by overwriting the
    // Mixin-Method with an empty implementation
    public void appendFeaturesPerStep() {
    }

    @Override
    public void injectSurfaceRules(ResourceKey<LevelStem> dimensionKey) {
        this.knownDimensionKey = dimensionKey;
        if (WorldsTogether.RUNS_TERRABLENDER) {
            TerraBlenderNoiseSettingsCompat.applyRegionType(generatorSettings(), dimensionKey, this.getBiomeSource());
        }

        InjectableSurfaceRules.super.injectSurfaceRules(dimensionKey);
    }

    public void mergeExternalBiomeSource(
            RegistryAccess access,
            ResourceKey<LevelStem> dimensionKey,
            Holder<DimensionType> dimensionType,
            ChunkGenerator externalChunkGenerator
    ) {
        this.knownDimensionKey = dimensionKey;
        if (externalChunkGenerator == null || externalChunkGenerator == this) {
            logCompat(
                    "Skipping external merge for {}: externalGenerator={}",
                    dimensionKey.location(),
                    describeObject(externalChunkGenerator)
            );
            return;
        }

        final BiomeSource externalBiomeSource = externalChunkGenerator.getBiomeSource();
        if (BlueprintBiomeSourceCompat.wraps(externalBiomeSource, getBiomeSource())) {
            logCompat(
                    "Skipping Blueprint wrapper merge for {}: externalBiomeSource={}, currentBiomeSource={}",
                    dimensionKey.location(),
                    describeObject(externalBiomeSource),
                    describeObject(getBiomeSource())
            );
            rebuildFeaturesPerStep(getBiomeSource());
            return;
        }

        if (externalBiomeSource == null || externalBiomeSource == getBiomeSource()) {
            logCompat(
                    "Skipping external merge for {}: externalBiomeSource={}, currentBiomeSource={}",
                    dimensionKey.location(),
                    describeObject(externalBiomeSource),
                    describeObject(getBiomeSource())
            );
            return;
        }

        logCompat(
                "Merging external biome source for {}: targetGenerator={}, targetBiomeSource={}, externalGenerator={}, externalBiomeSource={}",
                dimensionKey.location(),
                describeObject(this),
                describeObject(getBiomeSource()),
                describeObject(externalChunkGenerator),
                describeObject(externalBiomeSource)
        );
        if (getBiomeSource() instanceof MergeableBiomeSource mbs) {
            final BiomeSource bs = mbs.mergeWithBiomeSource(externalBiomeSource);
            if (mbs instanceof BCLBiomeSource bclBiomeSource) {
                bclBiomeSource.setExternalBiomeSourceContext(
                        access,
                        dimensionType,
                        dimensionKey,
                        externalChunkGenerator
                );
            }

            if (bs != getBiomeSource() && this instanceof ChunkGeneratorAccessor acc) {
                acc.bcl_setBiomeSource(bs);
            }
            rebuildFeaturesPerStep(getBiomeSource());
        }
    }

    @Override
    public Registry<LevelStem> enforceGeneratorInWorldGenSettings(
            RegistryAccess access,
            ResourceKey<LevelStem> dimensionKey,
            ResourceKey<DimensionType> dimensionTypeKey,
            ChunkGenerator loadedChunkGenerator,
            Registry<LevelStem> dimensionRegistry
    ) {
        this.knownDimensionKey = dimensionKey;
        BCLib.LOGGER.info("Enforcing Correct Generator for " + dimensionKey.location().toString() + ".");
        logCompat(
                "Enforce start for {}: referenceGenerator={}, referenceBiomeSource={}, loadedGenerator={}, loadedBiomeSource={}",
                dimensionKey.location(),
                describeObject(this),
                describeObject(getBiomeSource()),
                describeObject(loadedChunkGenerator),
                describeObject(loadedChunkGenerator.getBiomeSource())
        );

        ChunkGenerator referenceGenerator = this;
        final BiomeSource bs;
        if (referenceGenerator.getBiomeSource() instanceof MergeableBiomeSource mbs) {
            final BiomeSource loadedBiomeSource = loadedChunkGenerator.getBiomeSource();
            final boolean loadedBlueprintWrapsReference = BlueprintBiomeSourceCompat.wraps(
                    loadedBiomeSource,
                    referenceGenerator.getBiomeSource()
            );
            bs = loadedBlueprintWrapsReference
                    ? referenceGenerator.getBiomeSource()
                    : mbs.mergeWithBiomeSource(loadedBiomeSource);
            if (loadedBlueprintWrapsReference) {
                logCompat(
                        "Enforce keeping Blueprint wrapper outside BCL source for {}: loadedBiomeSource={}, referenceBiomeSource={}",
                        dimensionKey.location(),
                        describeObject(loadedBiomeSource),
                        describeObject(referenceGenerator.getBiomeSource())
                );
            } else if (mbs instanceof BCLBiomeSource bclBiomeSource && !(loadedBiomeSource instanceof BCLBiomeSource)) {
                logCompat(
                        "Enforce applying loaded external context for {}: loadedGenerator={}, loadedBiomeSource={}",
                        dimensionKey.location(),
                        describeObject(loadedChunkGenerator),
                        describeObject(loadedBiomeSource)
                );
                Holder<DimensionType> dimensionType = access
                        .registryOrThrow(Registries.DIMENSION_TYPE)
                        .getHolderOrThrow(dimensionTypeKey);
                bclBiomeSource.setExternalBiomeSourceContext(
                        access,
                        dimensionType,
                        dimensionKey,
                        loadedChunkGenerator
                );
            } else if (loadedBiomeSource instanceof BCLBiomeSource) {
                logCompat(
                        "Enforce preserving existing external context for {} because loaded biome source is already BCL: loadedBiomeSource={}",
                        dimensionKey.location(),
                        describeObject(loadedBiomeSource)
                );
            }
        } else {
            bs = referenceGenerator.getBiomeSource();
        }

        if (loadedChunkGenerator instanceof NoiseGeneratorSettingsProvider noiseProvider) {
            referenceGenerator = new BCLChunkGenerator(
                    bs,
                    noiseProvider.bclib_getNoiseGeneratorSettingHolders()
            );
            ((BCLChunkGenerator) referenceGenerator).knownDimensionKey = dimensionKey;
            logCompat(
                    "Enforce created BCL generator with loaded noise settings for {}: newGenerator={}, biomeSource={}",
                    dimensionKey.location(),
                    describeObject(referenceGenerator),
                    describeObject(referenceGenerator.getBiomeSource())
            );
        } else if (bs != referenceGenerator.getBiomeSource()) {
            referenceGenerator = new BCLChunkGenerator(
                    bs,
                    generatorSettings()
            );
            ((BCLChunkGenerator) referenceGenerator).knownDimensionKey = dimensionKey;
            logCompat(
                    "Enforce created BCL generator with reference noise settings for {}: newGenerator={}, biomeSource={}",
                    dimensionKey.location(),
                    describeObject(referenceGenerator),
                    describeObject(referenceGenerator.getBiomeSource())
            );
        }

        return LevelGenUtil.replaceGenerator(
                dimensionKey,
                dimensionTypeKey,
                access,
                dimensionRegistry,
                referenceGenerator
        );
    }

    private static void logCompat(String message, Object... args) {
        if (Configs.MAIN_CONFIG.verboseLogging()) {
            BCLib.LOGGER.info("[ChunkGeneratorCompat] " + message, args);
        }
    }

    private static String describeObject(Object object) {
        if (object == null) {
            return "null";
        }
        return object.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(object));
    }


    public static NoiseGeneratorSettings amplifiedNether(BootstapContext<NoiseGeneratorSettings> bootstapContext) {
        HolderGetter<DensityFunction> densityGetter = bootstapContext.lookup(Registries.DENSITY_FUNCTION);
        return new NoiseGeneratorSettings(
                NETHER_NOISE_SETTINGS_AMPLIFIED,
                Blocks.NETHERRACK.defaultBlockState(),
                Blocks.LAVA.defaultBlockState(),
                bclibNoNewCaves(
                        densityGetter,
                        bootstapContext.lookup(Registries.NOISE),
                        bclibSlideNetherLike(densityGetter, 0, 256)
                ),
                SurfaceRuleData.nether(),
                List.of(),
                32,
                false,
                false,
                false,
                true
        );
    }

    public static Map<String, Supplier<TypeTemplate>> addGeneratorDSL(Map<String, Supplier<TypeTemplate>> map) {
        if (map.containsKey("minecraft:flat")) {
            Map<String, Supplier<TypeTemplate>> nMap = new HashMap<>(map);
            nMap.put("bclib:betterx", DSL::remainder);
            return nMap;
        }
        return map;
    }

    private static NoiseRouter bclibNoNewCaves(
            HolderGetter<DensityFunction> densityGetter,
            HolderGetter<NormalNoise.NoiseParameters> noiseGetter,
            DensityFunction densityFunction
    ) {
        return NoiseRouterData.noNewCaves(densityGetter, noiseGetter, densityFunction);
    }

    private static DensityFunction bclibSlideNetherLike(
            HolderGetter<DensityFunction> densityGetter,
            int minY,
            int maxY
    ) {
        return NoiseRouterData.slideNetherLike(densityGetter, minY, maxY);
    }

}
