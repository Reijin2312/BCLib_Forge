package org.betterx.bclib.mixin.common;

import org.betterx.bclib.api.v2.generator.BiomePicker;
import org.betterx.bclib.api.v2.generator.TheEndBiomesHelper;
import org.betterx.bclib.api.v2.generator.config.BCLEndBiomeSourceConfig;
import org.betterx.bclib.api.v2.generator.map.hex.HexBiomeMap;
import org.betterx.bclib.api.v2.levelgen.biomes.BCLBiome;
import org.betterx.bclib.api.v2.levelgen.biomes.BCLBiomeRegistry;
import org.betterx.bclib.api.v2.levelgen.biomes.BiomeAPI;
import org.betterx.bclib.compat.worldgen.ModBiomeWorldgenConfig;
import org.betterx.bclib.config.Configs;
import org.betterx.bclib.interfaces.BiomeMap;
import org.betterx.worlds.together.world.BiomeSourceWithSeed;
import org.betterx.worlds.together.world.event.WorldBootstrap;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Mixin(TheEndBiomeSource.class)
public abstract class TheEndBiomeSourceMixin implements BiomeSourceWithSeed {
    private static final int INNER_VOID_RADIUS_SQUARED = BCLEndBiomeSourceConfig.MINECRAFT_20.innerVoidRadiusSquared;
    private static final int BASE_CENTER_BIOME_SIZE = BCLEndBiomeSourceConfig.MINECRAFT_20.centerBiomesSize;
    private static final int BASE_VOID_BIOME_SIZE = BCLEndBiomeSourceConfig.MINECRAFT_20.voidBiomesSize;
    private static final int BASE_LAND_BIOME_SIZE = BCLEndBiomeSourceConfig.MINECRAFT_20.landBiomesSize;
    private static final int BASE_BARRENS_BIOME_SIZE = BCLEndBiomeSourceConfig.MINECRAFT_20.barrensBiomesSize;
    private static final boolean WITH_VOID_BIOMES = BCLEndBiomeSourceConfig.MINECRAFT_20.withVoidBiomes;

    @Shadow
    @Final
    private Holder<Biome> end;

    @Shadow
    @Final
    private Holder<Biome> highlands;

    @Shadow
    @Final
    private Holder<Biome> midlands;

    @Shadow
    @Final
    private Holder<Biome> islands;

    @Shadow
    @Final
    private Holder<Biome> barrens;

    @Unique
    private RegistryAccess bcl$cachedAccess;
    @Unique
    private long bcl$seed;
    @Unique
    private long bcl$preparedSeed = Long.MIN_VALUE;
    @Unique
    private int bcl$preparedSyncEpoch = Integer.MIN_VALUE;

    @Unique
    private BiomeMap bcl$landMap;
    @Unique
    private BiomeMap bcl$voidMap;
    @Unique
    private BiomeMap bcl$centerMap;
    @Unique
    private Map<ResourceKey<Biome>, BiomeMap> bcl$midlandsByHighlands = Map.of();
    @Unique
    private Map<ResourceKey<Biome>, BiomeMap> bcl$barrensByHighlands = Map.of();
    @Unique
    private Map<ResourceKey<Biome>, ResourceKey<Biome>> bcl$endRootByBiome = Map.of();
    @Unique
    private Set<Holder<Biome>> bcl$additionalPossibleBiomes = Set.of();

    @Override
    public void setSeed(long seed) {
        if (this.bcl$seed != seed) {
            this.bcl$seed = seed;
            this.bcl$preparedSeed = Long.MIN_VALUE;
        }
    }

    @Inject(method = "collectPossibleBiomes", at = @At("RETURN"), cancellable = true)
    private void bcl$collectPossibleBiomes(CallbackInfoReturnable<Stream<Holder<Biome>>> cir) {
        bcl$refreshMaps();
        if (bcl$additionalPossibleBiomes.isEmpty()) {
            return;
        }

        final Set<Holder<Biome>> result = new LinkedHashSet<>(cir.getReturnValue().toList());
        result.addAll(bcl$additionalPossibleBiomes);
        cir.setReturnValue(result.stream());
    }

    @Inject(method = "getNoiseBiome", at = @At("RETURN"), cancellable = true)
    private void bcl$addEndBiomesAdditively(
            int quartX,
            int quartY,
            int quartZ,
            Climate.Sampler sampler,
            CallbackInfoReturnable<Holder<Biome>> cir
    ) {
        bcl$refreshMaps();

        final Holder<Biome> original = cir.getReturnValue();
        if (original == null) {
            return;
        }

        bcl$clearMapCaches(quartX, quartZ);

        final int blockX = QuartPos.toBlock(quartX);
        final int blockY = QuartPos.toBlock(quartY);
        final int blockZ = QuartPos.toBlock(quartZ);
        final Holder<Biome> vanillaBucket = bcl$resolveVanillaBucket(blockX, blockY, blockZ, sampler);
        final Holder<Biome> selectedHighlands = bcl$pickFromMap(this.bcl$landMap, this.highlands, blockX, blockY, blockZ);

        if (vanillaBucket.equals(this.end)) {
            cir.setReturnValue(bcl$pickFromMap(this.bcl$centerMap, this.end, blockX, blockY, blockZ));
        } else if (vanillaBucket.equals(this.islands)) {
            final Holder<Biome> selectedVoid = bcl$pickFromMap(this.bcl$voidMap, this.islands, blockX, blockY, blockZ);
            final ResourceKey<Biome> selectedVoidKey = selectedVoid.unwrapKey().orElse(null);
            final boolean isCustomVoidBiome = selectedVoidKey != null && !selectedVoidKey.equals(Biomes.SMALL_END_ISLANDS);

            if (isCustomVoidBiome && bcl$isNearLandBucket(blockX, blockY, blockZ, sampler)) {
                // Keep island rims tied to the selected highlands root to avoid chunk-like void cuts on shorelines.
                cir.setReturnValue(
                        bcl$pickDependentBiome(
                                selectedHighlands,
                                this.bcl$barrensByHighlands,
                                selectedHighlands,
                                blockX,
                                blockY,
                                blockZ
                        )
                );
            } else {
                cir.setReturnValue(selectedVoid);
            }
        } else if (vanillaBucket.equals(this.barrens)) {
            cir.setReturnValue(
                    bcl$pickDependentBiome(
                            selectedHighlands,
                            this.bcl$barrensByHighlands,
                            selectedHighlands,
                            blockX,
                            blockY,
                            blockZ
                    )
            );
        } else if (vanillaBucket.equals(this.midlands)) {
            cir.setReturnValue(
                    bcl$pickDependentBiome(
                            selectedHighlands,
                            this.bcl$midlandsByHighlands,
                            selectedHighlands,
                            blockX,
                            blockY,
                            blockZ
                    )
            );
        } else {
            // Highlands stay the root source; midlands/barrens are resolved from that root.
            cir.setReturnValue(selectedHighlands);
        }
    }

    @Unique
    private Holder<Biome> bcl$resolveVanillaBucket(
            int blockX,
            int blockY,
            int blockZ,
            Climate.Sampler sampler
    ) {
        final long distanceSquared = (long) blockX * (long) blockX + (long) blockZ * (long) blockZ;
        if (distanceSquared <= INNER_VOID_RADIUS_SQUARED) {
            return this.end;
        }

        final int sampleX = (SectionPos.blockToSectionCoord(blockX) * 2 + 1) * 8;
        final int sampleZ = (SectionPos.blockToSectionCoord(blockZ) * 2 + 1) * 8;
        final double erosion = sampler.erosion().compute(new DensityFunction.SinglePointContext(sampleX, blockY, sampleZ));
        if (erosion > 0.25D) {
            return this.highlands;
        }
        if (erosion >= -0.0625D) {
            return this.midlands;
        }
        if (erosion < -0.21875D) {
            return this.islands;
        }
        return WITH_VOID_BIOMES ? this.barrens : this.highlands;
    }

    @Unique
    private boolean bcl$isNearLandBucket(int blockX, int blockY, int blockZ, Climate.Sampler sampler) {
        // 32 blocks catches shoreline transitions without suppressing deep-void custom biomes.
        final int step = 32;
        return !bcl$resolveVanillaBucket(blockX + step, blockY, blockZ, sampler).equals(this.islands)
                || !bcl$resolveVanillaBucket(blockX - step, blockY, blockZ, sampler).equals(this.islands)
                || !bcl$resolveVanillaBucket(blockX, blockY, blockZ + step, sampler).equals(this.islands)
                || !bcl$resolveVanillaBucket(blockX, blockY, blockZ - step, sampler).equals(this.islands);
    }

    @Unique
    private void bcl$refreshMaps() {
        final RegistryAccess access = WorldBootstrap.getLastRegistryAccess();
        final int syncEpoch = BCLBiomeRegistry.getSyncEpoch();
        final boolean accessChanged = this.bcl$cachedAccess != access;
        final boolean seedChanged = this.bcl$preparedSeed != this.bcl$seed;
        final boolean syncChanged = this.bcl$preparedSyncEpoch != syncEpoch;
        if (!accessChanged && !seedChanged && !syncChanged) {
            return;
        }

        this.bcl$cachedAccess = access;
        if (access == null) {
            bcl$resetToVanillaMaps();
            this.bcl$preparedSeed = this.bcl$seed;
            this.bcl$preparedSyncEpoch = syncEpoch;
            return;
        }

        final Registry<LevelStem> stemRegistry = access.registry(Registries.LEVEL_STEM).orElse(null);
        if (stemRegistry == null) {
            bcl$resetToVanillaMaps();
            this.bcl$preparedSeed = this.bcl$seed;
            this.bcl$preparedSyncEpoch = syncEpoch;
            return;
        }

        final LevelStem endStem = stemRegistry.get(LevelStem.END);
        if (endStem == null) {
            bcl$resetToVanillaMaps();
            this.bcl$preparedSeed = this.bcl$seed;
            this.bcl$preparedSyncEpoch = syncEpoch;
            return;
        }

        final Registry<Biome> biomeRegistry = access.registryOrThrow(Registries.BIOME);
        final Registry<BCLBiome> bclRegistry =
                access.registry(BCLBiomeRegistry.BCL_BIOMES_REGISTRY).orElse(BCLBiomeRegistry.BUILTIN_BCL_BIOMES);

        final EndPickers pickers = bcl$buildPickers(biomeRegistry, bclRegistry);
        final ModBiomeWorldgenConfig.ModSnapshot worldgenConfig = pickers.worldgenConfig();
        final int landBiomeSize = bcl$getScaledBiomeSize(BASE_LAND_BIOME_SIZE, worldgenConfig.globalSizeMultiplier());
        final int voidBiomeSize = bcl$getScaledBiomeSize(BASE_VOID_BIOME_SIZE, worldgenConfig.globalSizeMultiplier());
        final int centerBiomeSize = bcl$getScaledBiomeSize(
                BASE_CENTER_BIOME_SIZE,
                worldgenConfig.globalSizeMultiplier()
        );
        final int barrensBiomeSize = bcl$getScaledBiomeSize(
                BASE_BARRENS_BIOME_SIZE,
                worldgenConfig.globalSizeMultiplier()
        );

        this.bcl$landMap = new HexBiomeMap(this.bcl$seed, landBiomeSize, pickers.landPicker());
        this.bcl$voidMap = new HexBiomeMap(this.bcl$seed, voidBiomeSize, pickers.voidPicker());
        this.bcl$centerMap = new HexBiomeMap(this.bcl$seed, centerBiomeSize, pickers.centerPicker());
        this.bcl$midlandsByHighlands = bcl$buildDependentMaps(
                pickers.midlandsByHighlands(),
                landBiomeSize,
                0L
        );
        this.bcl$barrensByHighlands = bcl$buildDependentMaps(
                pickers.barrensByHighlands(),
                barrensBiomeSize,
                0L
        );
        this.bcl$endRootByBiome = pickers.endRootByBiome();

        final Set<Holder<Biome>> extras = new LinkedHashSet<>(pickers.endBiomes());
        extras.remove(this.end);
        extras.remove(this.highlands);
        extras.remove(this.midlands);
        extras.remove(this.barrens);
        extras.remove(this.islands);
        this.bcl$additionalPossibleBiomes = Set.copyOf(extras);

        this.bcl$preparedSeed = this.bcl$seed;
        this.bcl$preparedSyncEpoch = syncEpoch;
    }

    @Unique
    private EndPickers bcl$buildPickers(Registry<Biome> biomeRegistry, Registry<BCLBiome> bclRegistry) {
        BiomePicker landPicker = new BiomePicker(biomeRegistry);
        BiomePicker voidPicker = new BiomePicker(biomeRegistry);
        BiomePicker centerPicker = new BiomePicker(biomeRegistry);
        BiomePicker defaultMidlandsPicker = new BiomePicker(biomeRegistry);
        BiomePicker defaultBarrensPicker = new BiomePicker(biomeRegistry);
        Map<ResourceKey<Biome>, BiomePicker> midlandsByHighlands = new LinkedHashMap<>();
        Map<ResourceKey<Biome>, BiomePicker> barrensByHighlands = new LinkedHashMap<>();
        Map<ResourceKey<Biome>, ResourceKey<Biome>> endRootByBiome = new LinkedHashMap<>();

        final Set<Holder<Biome>> endBiomes = new LinkedHashSet<>();
        final Map<BiomeAPI.BiomeType, List<String>> includeMap = Configs.BIOMES_CONFIG.getBiomeIncludeMap();
        final List<String> excludeList = Configs.BIOMES_CONFIG.getExcludeMatching(BiomeAPI.BiomeType.END_LAND);

        final List<Map.Entry<ResourceKey<Biome>, Biome>> sortedBiomes = biomeRegistry
                .entrySet()
                .stream()
                .sorted((a, b) -> a.getKey().location().toString().compareTo(b.getKey().location().toString()))
                .toList();
        final ModBiomeWorldgenConfig.ModSnapshot worldgenConfig = ModBiomeWorldgenConfig.loadBetterEnd(
                sortedBiomes.stream().map(Map.Entry::getKey).toList()
        );

        for (Map.Entry<ResourceKey<Biome>, Biome> entry : sortedBiomes) {
            final ResourceKey<Biome> biomeKey = entry.getKey();
            final String biomeID = biomeKey.location().toString();
            if (excludeList.contains(biomeID)) {
                continue;
            }

            BCLBiome bclBiome = BCLBiomeRegistry.getBiomeOrNull(biomeKey, bclRegistry);
            BiomeAPI.BiomeType type = bclBiome != null ? bclBiome.getIntendedType() : bcl$typeForUnknownBiome(biomeKey);
            type = bcl$applyIncludeOverrides(includeMap, biomeKey, type);
            if (!type.is(BiomeAPI.BiomeType.END)) {
                continue;
            }

            final ModBiomeWorldgenConfig.BiomeWorldgenSettings worldgenSettings = worldgenConfig.settingsFor(biomeKey);
            if (!worldgenSettings.enabled()) {
                continue;
            }

            final float worldgenMultiplier = Mth.clamp(
                    worldgenSettings.sizeMultiplier() * worldgenSettings.frequencyMultiplier()
                            * worldgenConfig.globalFrequencyFor(biomeKey),
                    0.0F,
                    32.0F
            );
            if (worldgenMultiplier <= 0.0F) {
                continue;
            }

            endBiomes.add(biomeRegistry.getHolderOrThrow(biomeKey));
            final ResourceKey<Biome> rootKey;
            if (biomeKey.equals(net.minecraft.world.level.biome.Biomes.END_MIDLANDS)
                    || biomeKey.equals(net.minecraft.world.level.biome.Biomes.END_BARRENS)) {
                rootKey = net.minecraft.world.level.biome.Biomes.END_HIGHLANDS;
            } else {
                rootKey = bcl$resolveEndRootKey(biomeKey, bclRegistry);
            }
            endRootByBiome.put(biomeKey, rootKey);

            if (bclBiome == null) {
                bclBiome = new BCLBiome(biomeKey.location(), type);
            }

            if (biomeKey.equals(net.minecraft.world.level.biome.Biomes.END_MIDLANDS)) {
                bcl$addBiomeToPicker(
                        bcl$getOrCreateDependentPicker(midlandsByHighlands, rootKey, biomeRegistry),
                        bclBiome,
                        worldgenMultiplier
                );
                bcl$addBiomeToPicker(defaultMidlandsPicker, bclBiome, worldgenMultiplier);
                continue;
            }
            if (biomeKey.equals(net.minecraft.world.level.biome.Biomes.END_BARRENS)) {
                bcl$addBiomeToPicker(
                        bcl$getOrCreateDependentPicker(barrensByHighlands, rootKey, biomeRegistry),
                        bclBiome,
                        worldgenMultiplier
                );
                bcl$addBiomeToPicker(defaultBarrensPicker, bclBiome, worldgenMultiplier);
                continue;
            }

            if (bclBiome.hasParentBiome()) {
                final BCLBiome parent = bclBiome.getParentBiome();
                final ResourceKey<Biome> parentKey = parent != null ? parent.getBiomeKey() : null;
                if (parentKey == null) {
                    continue;
                }

                if (type.is(BiomeAPI.BiomeType.END_LAND)) {
                    bcl$addBiomeToPicker(
                            bcl$getOrCreateDependentPicker(midlandsByHighlands, rootKey, biomeRegistry),
                            bclBiome,
                            worldgenMultiplier
                    );
                    bcl$addBiomeToPicker(defaultMidlandsPicker, bclBiome, worldgenMultiplier);
                } else if (type.is(BiomeAPI.BiomeType.END_BARRENS)) {
                    bcl$addBiomeToPicker(
                            bcl$getOrCreateDependentPicker(barrensByHighlands, rootKey, biomeRegistry),
                            bclBiome,
                            worldgenMultiplier
                    );
                    bcl$addBiomeToPicker(defaultBarrensPicker, bclBiome, worldgenMultiplier);
                }
                continue;
            }

            if (type.is(BiomeAPI.BiomeType.END_CENTER)) {
                bcl$addBiomeToPicker(centerPicker, bclBiome, worldgenMultiplier);
            } else if (type.is(BiomeAPI.BiomeType.END_VOID)) {
                bcl$addBiomeToPicker(voidPicker, bclBiome, worldgenMultiplier);
            } else if (type.is(BiomeAPI.BiomeType.END_BARRENS)) {
                bcl$addBiomeToPicker(defaultBarrensPicker, bclBiome, worldgenMultiplier);
            } else if (type.is(BiomeAPI.BiomeType.END_LAND)) {
                if (!biomeKey.equals(net.minecraft.world.level.biome.Biomes.END_MIDLANDS)) {
                    bcl$addBiomeToPicker(landPicker, bclBiome, worldgenMultiplier);
                }
            }
        }

        if (landPicker.isEmpty()) {
            landPicker.addBiome(BiomeAPI.END_HIGHLANDS);
        }
        if (voidPicker.isEmpty()) {
            voidPicker.addBiome(BiomeAPI.SMALL_END_ISLANDS);
            if (voidPicker.isEmpty()) {
                voidPicker = landPicker;
            }
        }
        if (centerPicker.isEmpty()) {
            centerPicker.addBiome(BiomeAPI.THE_END);
            if (centerPicker.isEmpty()) {
                centerPicker = landPicker;
            }
        }
        if (defaultMidlandsPicker.isEmpty()) {
            defaultMidlandsPicker.addBiome(BiomeAPI.END_MIDLANDS);
            if (defaultMidlandsPicker.isEmpty()) {
                defaultMidlandsPicker = landPicker;
            }
        }
        if (defaultBarrensPicker.isEmpty()) {
            defaultBarrensPicker.addBiome(BiomeAPI.END_BARRENS);
            if (defaultBarrensPicker.isEmpty()) {
                defaultBarrensPicker = defaultMidlandsPicker;
            }
        }

        landPicker.rebuild();
        voidPicker.rebuild();
        centerPicker.rebuild();
        defaultMidlandsPicker.rebuild();
        defaultBarrensPicker.rebuild();
        bcl$rebuildDependentPickers(midlandsByHighlands);
        bcl$rebuildDependentPickers(barrensByHighlands);

        return new EndPickers(
                landPicker,
                voidPicker,
                centerPicker,
                defaultMidlandsPicker,
                defaultBarrensPicker,
                Map.copyOf(midlandsByHighlands),
                Map.copyOf(barrensByHighlands),
                Map.copyOf(endRootByBiome),
                Set.copyOf(endBiomes),
                worldgenConfig
        );
    }

    @Unique
    private static BiomeAPI.BiomeType bcl$typeForUnknownBiome(ResourceKey<Biome> biomeKey) {
        if (TheEndBiomesHelper.canGenerateAsMainIslandBiome(biomeKey)) {
            return BiomeAPI.BiomeType.END_CENTER;
        } else if (TheEndBiomesHelper.canGenerateAsHighlandsBiome(biomeKey)) {
            if (!WITH_VOID_BIOMES) {
                return BiomeAPI.BiomeType.END_VOID;
            }
            return BiomeAPI.BiomeType.END_LAND;
        } else if (TheEndBiomesHelper.canGenerateAsEndBarrens(biomeKey)) {
            return BiomeAPI.BiomeType.END_BARRENS;
        } else if (TheEndBiomesHelper.canGenerateAsSmallIslandsBiome(biomeKey)) {
            return BiomeAPI.BiomeType.END_VOID;
        } else if (TheEndBiomesHelper.canGenerateAsEndMidlands(biomeKey)) {
            return BiomeAPI.BiomeType.END_LAND;
        }
        return BiomeAPI.BiomeType.NONE;
    }

    @Unique
    private static BiomeAPI.BiomeType bcl$applyIncludeOverrides(
            Map<BiomeAPI.BiomeType, List<String>> includeMap,
            ResourceKey<Biome> biomeKey,
            BiomeAPI.BiomeType defaultType
    ) {
        for (Map.Entry<BiomeAPI.BiomeType, List<String>> includeList : includeMap.entrySet()) {
            if (includeList.getValue().contains(biomeKey.location().toString())) {
                return includeList.getKey();
            }
        }
        return defaultType;
    }

    @Unique
    private static void bcl$addBiomeToPicker(BiomePicker picker, BCLBiome biome, float multiplier) {
        picker.setGenerationWeightMultiplier(biome, multiplier);
        picker.addBiome(biome);
    }

    @Unique
    private static int bcl$getScaledBiomeSize(int baseBiomeSize, float multiplier) {
        return Mth.clamp(Math.round(baseBiomeSize * Mth.clamp(multiplier, 0.05F, 16.0F)), 1, 4096);
    }

    @Unique
    private static BiomePicker bcl$getOrCreateDependentPicker(
            Map<ResourceKey<Biome>, BiomePicker> map,
            ResourceKey<Biome> key,
            Registry<Biome> biomeRegistry
    ) {
        return map.computeIfAbsent(key, ignored -> new BiomePicker(biomeRegistry));
    }

    @Unique
    private static void bcl$rebuildDependentPickers(Map<ResourceKey<Biome>, BiomePicker> pickers) {
        pickers.values().forEach(BiomePicker::rebuild);
    }

    @Unique
    private static ResourceKey<Biome> bcl$resolveEndRootKey(
            ResourceKey<Biome> biomeKey,
            Registry<BCLBiome> bclRegistry
    ) {
        if (biomeKey == null) {
            return null;
        }

        ResourceKey<Biome> current = biomeKey;
        for (int i = 0; i < 32; i++) {
            final BCLBiome currentBiome = BCLBiomeRegistry.getBiomeOrNull(current, bclRegistry);
            if (currentBiome == null || !currentBiome.hasParentBiome()) {
                return current;
            }

            final BCLBiome parent = currentBiome.getParentBiome();
            final ResourceKey<Biome> parentKey = parent != null ? parent.getBiomeKey() : null;
            if (parentKey == null || parentKey.equals(current)) {
                return current;
            }

            current = parentKey;
        }

        return current;
    }

    @Unique
    private Map<ResourceKey<Biome>, BiomeMap> bcl$buildDependentMaps(
            Map<ResourceKey<Biome>, BiomePicker> pickers,
            int biomeSize,
            long salt
    ) {
        if (pickers.isEmpty()) {
            return Map.of();
        }

        final Map<ResourceKey<Biome>, BiomeMap> maps = new LinkedHashMap<>();
        for (Map.Entry<ResourceKey<Biome>, BiomePicker> entry : pickers.entrySet()) {
            final ResourceKey<Biome> key = entry.getKey();
            final BiomeMap map = bcl$buildDependentMap(entry.getValue(), biomeSize, salt);
            if (map != null && key != null) {
                maps.put(key, map);
            }
        }

        return maps.isEmpty() ? Map.of() : Map.copyOf(maps);
    }

    @Unique
    private BiomeMap bcl$buildDependentMap(BiomePicker picker, int biomeSize, long salt) {
        if (picker == null || picker.isEmpty()) {
            return null;
        }

        return new HexBiomeMap(this.bcl$seed ^ salt, biomeSize, picker);
    }

    @Unique
    private Holder<Biome> bcl$pickDependentBiome(
            Holder<Biome> highlandsBiome,
            Map<ResourceKey<Biome>, BiomeMap> byHighlands,
            Holder<Biome> fallbackBiome,
            int blockX,
            int blockY,
            int blockZ
    ) {
        final ResourceKey<Biome> selectedKey = highlandsBiome.unwrapKey().orElse(null);
        BiomeMap map = selectedKey == null ? null : byHighlands.get(selectedKey);
        if (map == null && selectedKey != null) {
            final ResourceKey<Biome> rootKey = this.bcl$endRootByBiome.getOrDefault(selectedKey, selectedKey);
            map = byHighlands.get(rootKey);
        }

        return bcl$pickFromMap(map, fallbackBiome, blockX, blockY, blockZ);
    }

    @Unique
    private void bcl$clearMapCaches(int quartX, int quartZ) {
        if ((quartX & 63) != 0 && (quartZ & 63) != 0) {
            return;
        }

        if (this.bcl$landMap != null) {
            this.bcl$landMap.clearCache();
        }
        if (this.bcl$voidMap != null) {
            this.bcl$voidMap.clearCache();
        }
        if (this.bcl$centerMap != null) {
            this.bcl$centerMap.clearCache();
        }
        bcl$clearDependentMapCaches(this.bcl$midlandsByHighlands);
        bcl$clearDependentMapCaches(this.bcl$barrensByHighlands);
    }

    @Unique
    private static void bcl$clearDependentMapCaches(Map<ResourceKey<Biome>, BiomeMap> maps) {
        for (BiomeMap map : maps.values()) {
            if (map != null) {
                map.clearCache();
            }
        }
    }

    @Unique
    private Holder<Biome> bcl$pickFromMap(
            BiomeMap map,
            Holder<Biome> fallback,
            int blockX,
            int blockY,
            int blockZ
    ) {
        if (map == null) {
            return fallback;
        }

        BiomePicker.ActualBiome result = map.getBiome(blockX, blockY, blockZ);
        return result == null || result.biome == null ? fallback : result.biome;
    }

    @Unique
    private void bcl$resetToVanillaMaps() {
        this.bcl$landMap = null;
        this.bcl$voidMap = null;
        this.bcl$centerMap = null;
        this.bcl$midlandsByHighlands = Map.of();
        this.bcl$barrensByHighlands = Map.of();
        this.bcl$endRootByBiome = Map.of();
        this.bcl$additionalPossibleBiomes = Set.of();
    }

    @Unique
    private record EndPickers(
            BiomePicker landPicker,
            BiomePicker voidPicker,
            BiomePicker centerPicker,
            BiomePicker defaultMidlandsPicker,
            BiomePicker defaultBarrensPicker,
            Map<ResourceKey<Biome>, BiomePicker> midlandsByHighlands,
            Map<ResourceKey<Biome>, BiomePicker> barrensByHighlands,
            Map<ResourceKey<Biome>, ResourceKey<Biome>> endRootByBiome,
            Set<Holder<Biome>> endBiomes,
            ModBiomeWorldgenConfig.ModSnapshot worldgenConfig
    ) {}
}
