package org.betterx.bclib.api.v2.generator.compat;

import org.betterx.bclib.BCLib;
import org.betterx.worlds.together.biomesource.BiomeSourceHelper;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;

import java.lang.reflect.Method;
import java.util.List;

public class TerraBlenderBiomeSourceCompat {
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

            // Let TerraBlender perform its own initialization. This is the same entry point it
            // invokes from its MinecraftServer mixin and keeps Fabric/Forge implementation
            // details (dimension tags, parameter trees and deferred biome lists) in TerraBlender.
            // Minecraft parameter types must be class literals: string names are not remapped by
            // Loom and fail in a production Fabric jar.
            final Method initializeBiomes = levelUtils.getMethod(
                    "initializeBiomes",
                    RegistryAccess.class,
                    Holder.class,
                    ResourceKey.class,
                    ChunkGenerator.class,
                    long.class
            );
            initializeBiomes.invoke(
                    null,
                    context.registryAccess(),
                    context.dimensionType(),
                    context.dimensionKey(),
                    context.chunkGenerator(),
                    context.seed()
            );
            context.log(
                    "TerraBlender initialized {} with seed {}: biomes={}",
                    context.ownerName(),
                    context.seed(),
                    BiomeSourceHelper.getNamespaces(context.biomeSource().possibleBiomes())
            );

            if (!additionalDeferredBiomes.isEmpty()) {
                final Class<?> extendedBiomeSourceClass = Class.forName("terrablender.worldgen.IExtendedBiomeSource");
                final Method appendDeferredBiomesList = extendedBiomeSourceClass.getMethod(
                        "appendDeferredBiomesList",
                        List.class
                );
                if (extendedBiomeSourceClass.isInstance(context.biomeSource())) {
                    appendDeferredBiomesList.invoke(context.biomeSource(), List.copyOf(additionalDeferredBiomes));
                }
            }
        } catch (ClassNotFoundException ignored) {
            context.log("TerraBlender not present while preparing {}", context.ownerName());
        } catch (ReflectiveOperationException e) {
            BCLib.LOGGER.warning("Unable to initialize TerraBlender external biome source for {}", context.ownerName(), e);
        }
    }

}
