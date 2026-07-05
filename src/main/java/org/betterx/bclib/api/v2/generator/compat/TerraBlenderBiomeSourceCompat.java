package org.betterx.bclib.api.v2.generator.compat;

import org.betterx.bclib.BCLib;
import org.betterx.worlds.together.biomesource.BiomeSourceHelper;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class TerraBlenderBiomeSourceCompat {
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void prepare(ExternalBiomeSourceContext context, List<Holder<Biome>> additionalDeferredBiomes) {
        try {
            final Class<?> levelUtils = Class.forName("terrablender.util.LevelUtils");
            final Method shouldApply = levelUtils.getMethod("shouldApplyToChunkGenerator", ChunkGenerator.class);
            final boolean canApply = Boolean.TRUE.equals(shouldApply.invoke(null, context.chunkGenerator()));
            context.log(
                    "TerraBlender check for {}: canApply={}, externalGenerator={}, externalGeneratorBiomeSource={}",
                    context.ownerName(),
                    canApply,
                    ExternalBiomeSourceContext.describeObject(context.chunkGenerator()),
                    context.chunkGenerator() == null
                            ? "null"
                            : ExternalBiomeSourceContext.describeObject(context.chunkGenerator().getBiomeSource())
            );
            if (!canApply) {
                return;
            }

            final String regionTypeName = regionTypeName(context.dimensionKey());
            context.log(
                    "TerraBlender region type for {}: dimension={}, regionType={}",
                    context.ownerName(),
                    ExternalBiomeSourceContext.describeKey(context.dimensionKey()),
                    regionTypeName
            );
            if (regionTypeName == null) {
                return;
            }

            final Class<?> regionTypeClass = Class.forName("terrablender.api.RegionType");
            final Enum regionType = Enum.valueOf((Class<Enum>) regionTypeClass.asSubclass(Enum.class), regionTypeName);

            applyRegionTypeToSettings(context, regionTypeClass, regionType);

            final Object parameterList = getParameterList(context);
            final Class<?> extendedParameterListClass = Class.forName("terrablender.worldgen.IExtendedParameterList");
            final boolean extendedParameterList = extendedParameterListClass.isInstance(parameterList);
            context.log(
                    "TerraBlender parameter list for {}: parameterList={}, isExtended={}",
                    context.ownerName(),
                    ExternalBiomeSourceContext.describeObject(parameterList),
                    extendedParameterList
            );
            if (!extendedParameterList) {
                return;
            }

            final Method initializeForTerraBlender = extendedParameterListClass.getMethod(
                    "initializeForTerraBlender",
                    RegistryAccess.class,
                    regionTypeClass,
                    long.class
            );
            initializeForTerraBlender.invoke(parameterList, context.registryAccess(), regionType, context.seed());
            context.log("TerraBlender initialized parameter list for {} with seed {}", context.ownerName(), context.seed());

            final List<Holder<Biome>> terraBlenderRegionBiomes = getMissingRegionBiomes(context, regionTypeClass, regionType);
            final List<Holder<Biome>> regionBiomes = combineDeferredBiomes(additionalDeferredBiomes, terraBlenderRegionBiomes);
            context.log(
                    "TerraBlender collected deferred biomes for {}: count={}, biomes={}",
                    context.ownerName(),
                    regionBiomes.size(),
                    ExternalBiomeSourceContext.summarizeBiomeKeys(regionBiomes, 48)
            );
            if (regionBiomes.isEmpty()) {
                return;
            }

            final Class<?> extendedBiomeSourceClass = Class.forName("terrablender.worldgen.IExtendedBiomeSource");
            final boolean extendedBiomeSource = extendedBiomeSourceClass.isInstance(context.biomeSource());
            context.log(
                    "TerraBlender external biome source append check for {}: source={}, isExtended={}",
                    context.ownerName(),
                    ExternalBiomeSourceContext.describeObject(context.biomeSource()),
                    extendedBiomeSource
            );
            if (extendedBiomeSource) {
                final Method appendDeferredBiomesList = extendedBiomeSourceClass.getMethod(
                        "appendDeferredBiomesList",
                        List.class
                );
                appendDeferredBiomesList.invoke(context.biomeSource(), regionBiomes);
                context.log(
                        "TerraBlender appended deferred biomes for {}: afterAppend={}",
                        context.ownerName(),
                        BiomeSourceHelper.getNamespaces(context.biomeSource().possibleBiomes())
                );
            }
        } catch (ClassNotFoundException ignored) {
            context.log("TerraBlender not present while preparing {}", context.ownerName());
        } catch (ReflectiveOperationException e) {
            BCLib.LOGGER.warning("Unable to initialize TerraBlender external biome source for {}", context.ownerName(), e);
        }
    }

    private static List<Holder<Biome>> combineDeferredBiomes(List<Holder<Biome>> first, List<Holder<Biome>> second) {
        final Set<Holder<Biome>> combined = new LinkedHashSet<>(first);
        combined.addAll(second);
        return List.copyOf(combined);
    }

    private static String regionTypeName(ResourceKey<LevelStem> dimensionKey) {
        if (LevelStem.NETHER.equals(dimensionKey)) {
            return "NETHER";
        }
        if (LevelStem.OVERWORLD.equals(dimensionKey)) {
            return "OVERWORLD";
        }
        return null;
    }

    private static void applyRegionTypeToSettings(
            ExternalBiomeSourceContext context,
            Class<?> regionTypeClass,
            Object regionType
    ) throws ReflectiveOperationException {
        final Object settingsHolder = context.invokeNoArgMethod(
                context.chunkGenerator(),
                "net.minecraft.core.Holder",
                "generatorSettings",
                "m_224261_",
                "m_6331_"
        );
        if (!(settingsHolder instanceof Holder<?> holder)) {
            return;
        }

        final Class<?> extendedSettingsClass = Class.forName("terrablender.worldgen.IExtendedNoiseGeneratorSettings");
        if (extendedSettingsClass.isInstance(holder.value())) {
            final Method setRegionType = extendedSettingsClass.getMethod("setRegionType", regionTypeClass);
            setRegionType.invoke(holder.value(), regionType);
        }
    }

    private static Object getParameterList(ExternalBiomeSourceContext context) throws ReflectiveOperationException {
        return context.invokeNoArgMethod(
                context.biomeSource(),
                "net.minecraft.world.level.biome.Climate$ParameterList",
                "parameters",
                "m_274409_",
                "m_207840_"
        );
    }

    @SuppressWarnings("unchecked")
    private static List<Holder<Biome>> getMissingRegionBiomes(
            ExternalBiomeSourceContext context,
            Class<?> regionTypeClass,
            Object regionType
    ) throws ReflectiveOperationException {
        final Set<ResourceKey<Biome>> existingBiomes = new HashSet<>();
        for (Holder<Biome> holder : context.biomeSource().possibleBiomes()) {
            holder.unwrapKey().ifPresent(existingBiomes::add);
        }

        final Registry<Biome> biomeRegistry = context.registryAccess().registryOrThrow(Registries.BIOME);
        final Set<Holder<Biome>> regionBiomes = new LinkedHashSet<>();
        final Class<?> regionsClass = Class.forName("terrablender.api.Regions");
        final Class<?> regionClass = Class.forName("terrablender.api.Region");
        final Method getRegions = regionsClass.getMethod("get", regionTypeClass);
        final Method addBiomes = regionClass.getMethod("addBiomes", Registry.class, Consumer.class);
        final Method getName = regionClass.getMethod("getName");
        final Method getWeight = regionClass.getMethod("getWeight");
        final List<?> regions = (List<?>) getRegions.invoke(null, regionType);
        context.log(
                "TerraBlender regions for {}: regionType={}, count={}",
                context.ownerName(),
                regionType,
                regions.size()
        );

        for (Object region : regions) {
            final int before = regionBiomes.size();
            addBiomes.invoke(region, biomeRegistry, (Consumer<Object>) pair -> {
                final ResourceKey<Biome> biomeKey = getBiomeKey(pair);
                if (biomeKey != null && !existingBiomes.contains(biomeKey)) {
                    biomeRegistry.getHolder(biomeKey).ifPresent(regionBiomes::add);
                }
            });
            context.log(
                    "TerraBlender region result for {}: region={}, weight={}, addedBiomes={}",
                    context.ownerName(),
                    getName.invoke(region),
                    getWeight.invoke(region),
                    regionBiomes.size() - before
            );
        }

        return List.copyOf(regionBiomes);
    }

    @SuppressWarnings("unchecked")
    private static ResourceKey<Biome> getBiomeKey(Object pair) {
        try {
            final Method getSecond = pair.getClass().getMethod("getSecond");
            final Object biomeKey = getSecond.invoke(pair);
            return biomeKey instanceof ResourceKey<?> key ? (ResourceKey<Biome>) key : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
