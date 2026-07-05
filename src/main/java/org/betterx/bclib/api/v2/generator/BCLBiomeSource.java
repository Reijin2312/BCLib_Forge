package org.betterx.bclib.api.v2.generator;

import org.betterx.bclib.BCLib;
import org.betterx.bclib.api.v2.levelgen.biomes.BCLBiome;
import org.betterx.bclib.api.v2.levelgen.biomes.BCLBiomeRegistry;
import org.betterx.bclib.api.v2.levelgen.biomes.BiomeAPI;
import org.betterx.bclib.api.v2.generator.compat.ExternalBiomeSourceAdapters;
import org.betterx.bclib.api.v2.generator.compat.ExternalBiomeSourceContext;
import org.betterx.bclib.config.Configs;
import org.betterx.worlds.together.biomesource.BiomeSourceHelper;
import org.betterx.worlds.together.biomesource.MergeableBiomeSource;
import org.betterx.worlds.together.biomesource.ReloadableBiomeSource;
import org.betterx.worlds.together.world.BiomeSourceWithNoiseRelatedSettings;
import org.betterx.worlds.together.world.BiomeSourceWithSeed;
import org.betterx.worlds.together.world.event.WorldBootstrap;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

import java.util.*;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public abstract class BCLBiomeSource extends BiomeSource implements BiomeSourceWithSeed, MergeableBiomeSource<BCLBiomeSource>, BiomeSourceWithNoiseRelatedSettings, ReloadableBiomeSource {
    @FunctionalInterface
    public interface PickerAdder {
        boolean add(BCLBiome bclBiome, BiomeAPI.BiomeType type, BiomePicker picker);
    }

    /**
     * @deprecated Unknown biomes are no longer auto-imported into BCL biome pickers. Use explicit biome data or
     * {@code force_include} config entries for intentional BCL-managed placement.
     */
    @Deprecated
    @FunctionalInterface
    public interface CustomTypeFinder {
        BiomeAPI.BiomeType find(ResourceKey<Biome> biomeKey, BiomeAPI.BiomeType defaultType);
    }

    protected long currentSeed;
    protected int maxHeight;
    private boolean didCreatePickers;
    private Set<Holder<Biome>> ownedPossibleBiomes;
    private Set<Holder<Biome>> externalPossibleBiomes;
    private Set<ResourceKey<Biome>> ownedPossibleBiomeKeys;
    private Set<Holder<Biome>> dynamicPossibleBiomes;
    private BiomeSource externalBiomeSource;
    private RegistryAccess externalRegistryAccess;
    private Holder<DimensionType> externalDimensionType;
    private ResourceKey<LevelStem> externalDimensionKey;
    private ChunkGenerator externalChunkGenerator;
    private long externalBiomeSourcePreparedSeed;
    private boolean hasExplicitWorldSeed;
    private Runnable externalBiomeSourcePreparedCallback;

    protected BCLBiomeSource(long seed) {
        super();
        this.ownedPossibleBiomes = Set.of();
        this.externalPossibleBiomes = Set.of();
        this.ownedPossibleBiomeKeys = Set.of();
        this.dynamicPossibleBiomes = Set.of();
        this.externalBiomeSource = null;
        this.externalRegistryAccess = null;
        this.externalDimensionType = null;
        this.externalDimensionKey = null;
        this.externalChunkGenerator = null;
        this.externalBiomeSourcePreparedSeed = Long.MIN_VALUE;
        this.hasExplicitWorldSeed = false;
        this.externalBiomeSourcePreparedCallback = null;
        this.currentSeed = seed;
        this.didCreatePickers = false;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        reloadBiomes();
        return dynamicPossibleBiomes.stream();
    }

    @Override
    public Set<Holder<Biome>> possibleBiomes() {
        return dynamicPossibleBiomes;
    }

    public Set<Holder<Biome>> ownedPossibleBiomes() {
        return ownedPossibleBiomes;
    }


    protected boolean wasBound() {
        return didCreatePickers;
    }

    final public void setSeed(long seed) {
        this.hasExplicitWorldSeed = true;
        if (seed != currentSeed) {
            BCLib.LOGGER.debug(this + "\n    --> new seed = " + seed);
            this.currentSeed = seed;
            initMap(seed);
        }
        prepareExternalBiomeSource();
    }

    /**
     * Set world height
     *
     * @param maxHeight height of the World.
     */
    final public void setMaxHeight(int maxHeight) {
        if (this.maxHeight != maxHeight) {
            BCLib.LOGGER.debug(this + "\n    --> new height = " + maxHeight);
            this.maxHeight = maxHeight;
            onHeightChange(maxHeight);
        }
    }

    protected final void initMap(long seed) {
        BCLib.LOGGER.debug(this + "\n    --> Map Update");
        onInitMap(seed);
    }

    protected abstract void onInitMap(long newSeed);
    protected abstract void onHeightChange(int newHeight);


    @NotNull
    protected String getNamespaces() {
        return BiomeSourceHelper.getNamespaces(possibleBiomes());
    }

    protected boolean addToPicker(BCLBiome bclBiome, BiomeAPI.BiomeType type, BiomePicker picker) {
        picker.addBiome(bclBiome);
        return true;
    }

    /**
     * @deprecated Unknown biomes are no longer auto-imported into BCL biome pickers. The method is retained only
     * for source compatibility with older subclasses.
     */
    @Deprecated
    protected BiomeAPI.BiomeType typeForUnknownBiome(ResourceKey<Biome> biomeKey, BiomeAPI.BiomeType defaultType) {
        return defaultType;
    }

    protected final Holder<Biome> applyExternalBiomeSource(
            Holder<Biome> biome,
            int biomeX,
            int biomeY,
            int biomeZ,
            Climate.Sampler sampler
    ) {
        if (externalBiomeSource == null || externalBiomeSource == this) {
            return biome;
        }

        try {
            Holder<Biome> externalBiome = externalBiomeSource.getNoiseBiome(biomeX, biomeY, biomeZ, sampler);
            if (isRealExternalBiome(externalBiome)) {
                return externalBiome;
            }
        } catch (Throwable ignored) {
            // External biome sources are optional compatibility inputs. Keep BCL placement if one fails.
        }

        return biome;
    }

    protected boolean isRealExternalBiome(Holder<Biome> biomeHolder) {
        ResourceKey<Biome> key = biomeHolder == null ? null : biomeHolder.unwrapKey().orElse(null);
        if (key == null || ownedPossibleBiomeKeys.contains(key)) {
            return false;
        }

        ResourceLocation id = key.location();
        String namespace = id.getNamespace();
        String path = id.getPath();

        if ("minecraft".equals(namespace)
                || "bclib".equals(namespace)
                || "betternether".equals(namespace)
                || "betterend".equals(namespace)
                || "worlds_together".equals(namespace)) {
            return false;
        }

        if ("terrablender".equals(namespace) && "deferred_placeholder".equals(path)) {
            return false;
        }

        return !path.contains("placeholder")
                && !path.contains("deferred")
                && !path.contains("internal")
                && !path.contains("technical");
    }

    protected static Set<Holder<Biome>> populateBiomePickers(
            Map<BiomeAPI.BiomeType, BiomePicker> acceptedBiomeTypes,
            BiomeAPI.BiomeType exclusionListType,
            PickerAdder pickerAdder
    ) {
        final RegistryAccess access = WorldBootstrap.getLastRegistryAccess();
        if (access == null) {
            if (Configs.MAIN_CONFIG.verboseLogging() && !BCLib.isDatagen()) {
                BCLib.LOGGER.info("Unable to build Biome List yet");
            }
            return null;
        }

        final Set<Holder<Biome>> allBiomes = new HashSet<>();
        final Map<BiomeAPI.BiomeType, List<String>> includeMap = Configs.BIOMES_CONFIG.getBiomeIncludeMap();
        final List<String> excludeList = Configs.BIOMES_CONFIG.getExcludeMatching(exclusionListType);
        final Registry<Biome> biomes = access.registryOrThrow(Registries.BIOME);
        final Registry<BCLBiome> bclBiomes = access.registryOrThrow(BCLBiomeRegistry.BCL_BIOMES_REGISTRY);

        final List<Map.Entry<ResourceKey<Biome>, Biome>> sortedList = biomes
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(a -> a.getKey().location().toString()))
                .toList();

        for (Map.Entry<ResourceKey<Biome>, Biome> biomeEntry : sortedList) {
            if (excludeList.contains(biomeEntry.getKey().location())) continue;

            BiomeAPI.BiomeType type = BiomeAPI.BiomeType.NONE;
            BiomeAPI.BiomeType explicitlyIncludedType = getExplicitIncludedBiomeType(includeMap, biomeEntry.getKey());
            boolean foundBCLBiome = false;
            if (BCLBiomeRegistry.hasBiome(biomeEntry.getKey(), bclBiomes)) {
                foundBCLBiome = true;
                type = BCLBiomeRegistry.getBiome(biomeEntry.getKey(), bclBiomes).getIntendedType();
                if (explicitlyIncludedType != null) {
                    type = explicitlyIncludedType;
                }
            } else if (explicitlyIncludedType != null) {
                type = explicitlyIncludedType;
            } else {
                type = BiomeAPI.BiomeType.NONE;
            }

            for (Map.Entry<BiomeAPI.BiomeType, BiomePicker> pickerEntry : acceptedBiomeTypes.entrySet()) {
                if (type.is(pickerEntry.getKey())) {
                    BCLBiome bclBiome;
                    if (foundBCLBiome) {
                        bclBiome = BCLBiomeRegistry.getBiome(biomeEntry.getKey(), bclBiomes);
                    } else if (explicitlyIncludedType != null) {
                        bclBiome = new BCLBiome(biomeEntry.getKey().location(), type);
                        BCLBiomeRegistry.register(bclBiome);
                        foundBCLBiome = true;
                    } else {
                        continue;
                    }

                    if (!bclBiome.isPickable()) {
                        continue;
                    }

                    boolean isPossible;
                    if (!bclBiome.hasParentBiome()) {
                        isPossible = pickerAdder.add(bclBiome, pickerEntry.getKey(), pickerEntry.getValue());
                    } else {
                        isPossible = true;
                    }

                    if (isPossible) {
                        allBiomes.add(biomes.getHolderOrThrow(biomeEntry.getKey()));
                    }
                }
            }
        }


        return allBiomes;
    }

    protected abstract BiomeAPI.BiomeType defaultBiomeType();
    protected abstract Map<BiomeAPI.BiomeType, BiomePicker> createFreshPickerMap();

    public abstract String toShortString();

    protected void onFinishBiomeRebuild(Map<BiomeAPI.BiomeType, BiomePicker> pickerMap) {
        for (var picker : pickerMap.values()) {
            picker.rebuild();
        }
    }

    protected final void rebuildBiomes(boolean force) {
        if (!force && didCreatePickers) return;

        if (Configs.MAIN_CONFIG.verboseLogging()) {
            BCLib.LOGGER.info("Updating Pickers for " + this.toShortString());
        }

        Map<BiomeAPI.BiomeType, BiomePicker> pickerMap = createFreshPickerMap();
        this.ownedPossibleBiomes = populateBiomePickers(
                pickerMap,
                defaultBiomeType(),
                this::addToPicker
        );
        if (this.ownedPossibleBiomes == null) {
            this.ownedPossibleBiomes = Set.of();
        } else {
            this.didCreatePickers = true;
        }
        updateOwnedPossibleBiomeKeys();
        updateCombinedPossibleBiomes();

        onFinishBiomeRebuild(pickerMap);
    }

    @Override
    public BCLBiomeSource mergeWithBiomeSource(BiomeSource inputBiomeSource) {
        setExternalBiomeSource(inputBiomeSource);
        updateCombinedPossibleBiomes();
        return this;
    }

    private void setExternalBiomeSource(BiomeSource inputBiomeSource) {
        if (inputBiomeSource == null || inputBiomeSource == this || inputBiomeSource instanceof BCLBiomeSource) {
            logCompat(
                    "Ignoring external biome source for {}: source={}",
                    toShortString(),
                    describeObject(inputBiomeSource)
            );
            return;
        }

        this.externalBiomeSource = inputBiomeSource;
        this.externalBiomeSourcePreparedSeed = Long.MIN_VALUE;
        logCompat(
                "Assigned external biome source for {}: source={}",
                toShortString(),
                describeObject(inputBiomeSource)
        );
        prepareExternalBiomeSource();
        refreshExternalPossibleBiomes();
    }

    public void setExternalBiomeSourceContext(
            RegistryAccess registryAccess,
            Holder<DimensionType> dimensionType,
            ResourceKey<LevelStem> dimensionKey,
            ChunkGenerator chunkGenerator
    ) {
        this.externalRegistryAccess = registryAccess;
        this.externalDimensionType = dimensionType;
        this.externalDimensionKey = dimensionKey;
        this.externalChunkGenerator = chunkGenerator;
        logCompat(
                "Updated external context for {}: dimension={}, dimensionType={}, generator={}, generatorBiomeSource={}, seedReady={}",
                toShortString(),
                describeKey(dimensionKey),
                describeHolderKey(dimensionType),
                describeObject(chunkGenerator),
                chunkGenerator == null ? "null" : describeObject(chunkGenerator.getBiomeSource()),
                hasExplicitWorldSeed
        );
        prepareExternalBiomeSource();
        refreshExternalPossibleBiomes();
    }

    void setExternalBiomeSourcePreparedCallback(Runnable callback) {
        this.externalBiomeSourcePreparedCallback = callback;
    }

    private void prepareExternalBiomeSource() {
        if (externalBiomeSource == null
                || externalRegistryAccess == null
                || externalDimensionType == null
                || externalDimensionKey == null
                || externalChunkGenerator == null
                || !hasExplicitWorldSeed
                || externalBiomeSourcePreparedSeed == currentSeed) {
            logCompat(
                    "Skipping external preparation for {}: source={}, registryAccess={}, dimensionType={}, dimension={}, generator={}, hasSeed={}, preparedSeed={}, currentSeed={}",
                    toShortString(),
                    describeObject(externalBiomeSource),
                    externalRegistryAccess != null,
                    describeHolderKey(externalDimensionType),
                    describeKey(externalDimensionKey),
                    describeObject(externalChunkGenerator),
                    hasExplicitWorldSeed,
                    externalBiomeSourcePreparedSeed,
                    currentSeed
            );
            return;
        }

        logCompat(
                "Preparing external biome source for {}: source={}, generator={}, generatorBiomeSource={}, dimension={}, seed={}",
                toShortString(),
                describeObject(externalBiomeSource),
                describeObject(externalChunkGenerator),
                describeObject(externalChunkGenerator.getBiomeSource()),
                describeKey(externalDimensionKey),
                currentSeed
        );

        final ExternalBiomeSourceContext context = new ExternalBiomeSourceContext(
                externalBiomeSource,
                externalRegistryAccess,
                externalDimensionType,
                externalDimensionKey,
                externalChunkGenerator,
                currentSeed,
                toShortString()
        );
        ExternalBiomeSourceAdapters.prepare(context);
        this.externalBiomeSource = context.biomeSource();
        externalBiomeSourcePreparedSeed = currentSeed;
        refreshExternalPossibleBiomes();
        if (externalBiomeSourcePreparedCallback != null) {
            externalBiomeSourcePreparedCallback.run();
        }
        if (Configs.MAIN_CONFIG.verboseLogging() && !externalPossibleBiomes.isEmpty()) {
            BCLib.LOGGER.info(
                    "Prepared external biome source for {}: {} ({})",
                    toShortString(),
                    BiomeSourceHelper.getNamespaces(externalPossibleBiomes),
                    ExternalBiomeSourceContext.summarizeBiomeKeys(externalPossibleBiomes, 24)
            );
        }
    }

    private void refreshExternalPossibleBiomes() {
        if (externalBiomeSource == null) {
            this.externalPossibleBiomes = Set.of();
            updateCombinedPossibleBiomes();
            return;
        }

        try {
            this.externalPossibleBiomes = Set.copyOf(externalBiomeSource.possibleBiomes());
        } catch (RuntimeException e) {
            BCLib.LOGGER.warning("Unable to read external possible biomes for {}: {}", toShortString(), e.toString());
            this.externalPossibleBiomes = Set.of();
        }
        updateCombinedPossibleBiomes();
    }

    private void updateOwnedPossibleBiomeKeys() {
        Set<ResourceKey<Biome>> keys = new HashSet<>();
        for (Holder<Biome> biomeHolder : ownedPossibleBiomes) {
            biomeHolder.unwrapKey().ifPresent(keys::add);
        }
        this.ownedPossibleBiomeKeys = Set.copyOf(keys);
    }

    private void updateCombinedPossibleBiomes() {
        if (externalPossibleBiomes.isEmpty()) {
            this.dynamicPossibleBiomes = ownedPossibleBiomes;
            logCompat(
                    "Combined possible biomes for {}: owned={}, external=0, combined={}",
                    toShortString(),
                    ownedPossibleBiomes.size(),
                    dynamicPossibleBiomes.size()
            );
            return;
        }

        Set<Holder<Biome>> combined = new HashSet<>(ownedPossibleBiomes);
        combined.addAll(externalPossibleBiomes);
        this.dynamicPossibleBiomes = Set.copyOf(combined);
        logCompat(
                "Combined possible biomes for {}: owned={}, external={}, combined={}, externalNamespaces={}",
                toShortString(),
                ownedPossibleBiomes.size(),
                externalPossibleBiomes.size(),
                dynamicPossibleBiomes.size(),
                BiomeSourceHelper.getNamespaces(externalPossibleBiomes)
        );
    }

    private void logCompat(String message, Object... args) {
        if (Configs.MAIN_CONFIG.verboseLogging()) {
            BCLib.LOGGER.info("[BiomeSourceCompat] " + message, args);
        }
    }

    private static String describeObject(Object object) {
        if (object == null) {
            return "null";
        }
        return object.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(object));
    }

    private static String describeKey(ResourceKey<?> key) {
        return key == null ? "null" : key.location().toString();
    }

    private static String describeHolderKey(Holder<?> holder) {
        return holder == null
                ? "null"
                : holder.unwrapKey().map(BCLBiomeSource::describeKey).orElse("unbound:" + describeObject(holder.value()));
    }

    private static String summarizeBiomeKeys(Collection<Holder<Biome>> biomes, int limit) {
        List<String> ids = new ArrayList<>();
        for (Holder<Biome> biome : biomes) {
            if (ids.size() >= limit) {
                ids.add("...+" + (biomes.size() - limit));
                break;
            }
            ids.add(biome.unwrapKey().map(BCLBiomeSource::describeKey).orElse("unbound"));
        }
        return String.join(", ", ids);
    }

    private static BiomeAPI.BiomeType getExplicitIncludedBiomeType(
            Map<BiomeAPI.BiomeType, List<String>> includeMap,
            ResourceKey<Biome> biomeKey
    ) {
        for (Map.Entry<BiomeAPI.BiomeType, List<String>> includeList : includeMap.entrySet()) {
            if (includeList.getValue().contains(biomeKey.location().toString())) {
                return includeList.getKey();
            }
        }

        return null;
    }

    public void onLoadGeneratorSettings(NoiseGeneratorSettings generator) {
        this.setMaxHeight(generator.noiseSettings().height());
    }

    protected void reloadBiomes(boolean force) {
        rebuildBiomes(force);
        this.initMap(currentSeed);
    }

    @Override
    public void reloadBiomes() {
        reloadBiomes(true);
    }
}
