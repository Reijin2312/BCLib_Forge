package org.betterx.bclib.api.v2.generator;

import org.betterx.bclib.BCLib;
import org.betterx.bclib.api.v2.generator.config.BCLNetherBiomeSourceConfig;
import org.betterx.bclib.api.v2.generator.config.MapBuilderFunction;
import org.betterx.bclib.api.v2.generator.map.MapStack;
import org.betterx.bclib.api.v2.levelgen.biomes.BCLBiomeRegistry;
import org.betterx.bclib.api.v2.levelgen.biomes.BiomeAPI;
import org.betterx.bclib.interfaces.BiomeMap;
import org.betterx.worlds.together.biomesource.BiomeSourceWithConfig;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.Map;

public class BCLibNetherBiomeSource extends BCLBiomeSource implements BiomeSourceWithConfig<BCLibNetherBiomeSource, BCLNetherBiomeSourceConfig> {
    public static final Codec<BCLibNetherBiomeSource> CODEC = RecordCodecBuilder
            .create(instance -> instance
                    .group(
                            Codec
                                    .LONG
                                    .fieldOf("seed")
                                    .stable()
                                    .forGetter(source -> {
                                        return source.currentSeed;
                                    }),
                            BCLNetherBiomeSourceConfig
                                    .CODEC
                                    .fieldOf("config")
                                    .orElse(BCLNetherBiomeSourceConfig.DEFAULT)
                                    .forGetter(o -> o.config)
                    )
                    .apply(instance, instance.stable(BCLibNetherBiomeSource::new))
            );
    private BiomeMap biomeMap;
    private BiomePicker biomePicker;
    private BCLNetherBiomeSourceConfig config;

    public BCLibNetherBiomeSource(
            BCLNetherBiomeSourceConfig config
    ) {
        this(0, config, false);
    }

    private BCLibNetherBiomeSource(
            long seed,
            BCLNetherBiomeSourceConfig config
    ) {
        this(seed, config, true);
    }


    private BCLibNetherBiomeSource(
            long seed,
            BCLNetherBiomeSourceConfig config,
            boolean initMaps
    ) {
        super(seed);
        this.config = config;
        rebuildBiomes(false);
        if (initMaps) {
            initMap(seed);
        }
    }

    @Override
    protected BiomeAPI.BiomeType defaultBiomeType() {
        return BiomeAPI.BiomeType.NETHER;
    }

    @Override
    protected Map<BiomeAPI.BiomeType, BiomePicker> createFreshPickerMap() {
        // BiomeAPI's vanilla wrappers are registered from static field initializers. Merely using
        // BiomeAPI.BiomeType does not initialize the outer BiomeAPI class, so a Nether source can
        // otherwise build its picker before those wrappers exist. In that case only modded biomes
        // are owned by this source; the vanilla MultiNoise source cannot be used as a replacement
        // because it returns a vanilla biome at every coordinate and would hide BetterNether.
        // Touch the five wrappers explicitly before populateBiomePickers scans the registry.
        ensureVanillaNetherBiomesRegistered();
        this.biomePicker = new BiomePicker();
        return Map.of(defaultBiomeType(), this.biomePicker);
    }

    private static void ensureVanillaNetherBiomesRegistered() {
        BCLBiomeRegistry.registerBuiltinIfUnknown(BiomeAPI.NETHER_WASTES_BIOME);
        BCLBiomeRegistry.registerBuiltinIfUnknown(BiomeAPI.SOUL_SAND_VALLEY_BIOME);
        BCLBiomeRegistry.registerBuiltinIfUnknown(BiomeAPI.CRIMSON_FOREST_BIOME);
        BCLBiomeRegistry.registerBuiltinIfUnknown(BiomeAPI.WARPED_FOREST_BIOME);
        BCLBiomeRegistry.registerBuiltinIfUnknown(BiomeAPI.BASALT_DELTAS_BIOME);
    }

    public static void register() {
        Registry.register(BuiltInRegistries.BIOME_SOURCE, BCLib.makeID("nether_biome_source"), CODEC);
    }
    
    @Override
    public Holder<Biome> getNoiseBiome(int biomeX, int biomeY, int biomeZ, Climate.Sampler var4) {
        if (!wasBound()) reloadBiomes(false);

        if (biomeMap == null) {
            Holder<Biome> fallback = this.ownedPossibleBiomes().stream()
                                        .findFirst()
                                        .orElseGet(() -> this.possibleBiomes().stream().findFirst().get());
            return applyExternalBiomeSource(fallback, biomeX, biomeY, biomeZ, var4);
        }

        if ((biomeX & 63) == 0 && (biomeZ & 63) == 0) {
            biomeMap.clearCache();
        }
        BiomePicker.ActualBiome bb = biomeMap.getBiome(biomeX << 2, biomeY << 2, biomeZ << 2);
        return applyExternalBiomeSource(bb.biome, biomeX, biomeY, biomeZ, var4);
    }

    @Override
    protected Codec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected void onInitMap(long seed) {
        MapBuilderFunction mapConstructor = config.mapVersion.mapBuilder;
        if (maxHeight > config.biomeSizeVertical * 1.5 && config.useVerticalBiomes) {
            this.biomeMap = new MapStack(
                    seed,
                    config.biomeSize,
                    biomePicker,
                    config.biomeSizeVertical,
                    maxHeight,
                    mapConstructor
            );
        } else {
            this.biomeMap = mapConstructor.create(
                    seed,
                    config.biomeSize,
                    biomePicker
            );
        }
    }

    @Override
    protected void onHeightChange(int newHeight) {
        initMap(currentSeed);
    }

    @Override
    public String toShortString() {
        return "BCLib - Nether BiomeSource (" + Integer.toHexString(hashCode()) + ")";
    }

    @Override
    public String toString() {
        return "\n" + toShortString() +
                "\n    biomes     = " + possibleBiomes().size() +
                "\n    namespaces = " + getNamespaces() +
                "\n    seed       = " + currentSeed +
                "\n    height     = " + maxHeight +
                "\n    config     = " + config;
    }

    @Override
    public BCLNetherBiomeSourceConfig getTogetherConfig() {
        return config;
    }

    @Override
    public void setTogetherConfig(BCLNetherBiomeSourceConfig newConfig) {
        this.config = newConfig;
        initMap(currentSeed);
    }
}
