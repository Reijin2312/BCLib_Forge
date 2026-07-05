package org.betterx.bclib.api.v2.generator.compat;

import org.betterx.bclib.api.v2.generator.BCLBiomeSource;
import org.betterx.bclib.api.v2.generator.BCLibEndBiomeSource;
import org.betterx.bclib.api.v2.generator.BiomeDecider;
import org.betterx.bclib.api.v2.levelgen.biomes.BiomeAPI;
import org.betterx.worlds.together.world.event.WorldBootstrap;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;

import java.util.function.BooleanSupplier;

class EndPatchBiomeDecider extends BiomeDecider {
    private final ResourceLocation biomeID;
    private final BiomeAPI.BiomeType providedType;
    private final BooleanSupplier isAvailable;
    private final int cellSize;
    private final int radius;
    private final int salt;
    private final BiomeAPI.BiomeType[] sourceTypes;

    EndPatchBiomeDecider(
            ResourceLocation biomeID,
            BiomeAPI.BiomeType providedType,
            BooleanSupplier isAvailable,
            int cellSize,
            int radius,
            BiomeAPI.BiomeType... sourceTypes
    ) {
        this(currentBiomeLookup(), biomeID, providedType, isAvailable, cellSize, radius, sourceTypes);
    }

    private EndPatchBiomeDecider(
            HolderGetter<Biome> biomeRegistry,
            ResourceLocation biomeID,
            BiomeAPI.BiomeType providedType,
            BooleanSupplier isAvailable,
            int cellSize,
            int radius,
            BiomeAPI.BiomeType... sourceTypes
    ) {
        super(biomeRegistry, biome -> biome.getID().equals(biomeID));
        this.biomeID = biomeID;
        this.providedType = providedType;
        this.isAvailable = isAvailable;
        this.cellSize = cellSize;
        this.radius = radius;
        this.salt = biomeID.toString().hashCode();
        this.sourceTypes = sourceTypes;
    }

    @Override
    public boolean canProvideFor(BiomeSource source) {
        return isAvailable.getAsBoolean() && source instanceof BCLibEndBiomeSource;
    }

    @Override
    public BiomeDecider createInstance(BCLBiomeSource biomeSource) {
        return new EndPatchBiomeDecider(
                currentBiomeLookup(),
                biomeID,
                providedType,
                isAvailable,
                cellSize,
                radius,
                sourceTypes
        );
    }

    @Override
    public BiomeAPI.BiomeType suggestType(
            BiomeAPI.BiomeType originalType,
            BiomeAPI.BiomeType suggestedType,
            double density,
            int maxHeight,
            int blockX,
            int blockY,
            int blockZ,
            int quarterX,
            int quarterY,
            int quarterZ
    ) {
        if (!matchesSourceType(suggestedType) || !isPatch(blockX, blockZ)) {
            return suggestedType;
        }

        return providedType;
    }

    @Override
    public boolean canProvideBiome(BiomeAPI.BiomeType suggestedType) {
        return suggestedType.equals(providedType);
    }

    private boolean matchesSourceType(BiomeAPI.BiomeType suggestedType) {
        for (BiomeAPI.BiomeType sourceType : sourceTypes) {
            if (suggestedType.is(sourceType)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPatch(int blockX, int blockZ) {
        final int minPadding = cellSize / 4;
        final int usableSize = cellSize - minPadding * 2;
        final int cellX = Math.floorDiv(blockX, cellSize);
        final int cellZ = Math.floorDiv(blockZ, cellSize);
        final long hash = mix(cellX, cellZ, salt);
        final int centerX = cellX * cellSize + minPadding + Math.floorMod((int) hash, usableSize);
        final int centerZ = cellZ * cellSize + minPadding + Math.floorMod((int) (hash >>> 32), usableSize);
        final long dx = blockX - centerX;
        final long dz = blockZ - centerZ;
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    private static HolderGetter<Biome> currentBiomeLookup() {
        final RegistryAccess access = WorldBootstrap.getLastRegistryAccess();
        if (access == null) {
            return null;
        }

        final Registry<Biome> biomeRegistry = access.registry(Registries.BIOME).orElse(null);
        return biomeRegistry != null ? biomeRegistry.asLookup() : null;
    }

    private static long mix(int cellX, int cellZ, int salt) {
        long value = 0x9E3779B97F4A7C15L ^ salt;
        value ^= (long) cellX * 0xBF58476D1CE4E5B9L;
        value = Long.rotateLeft(value, 27);
        value ^= (long) cellZ * 0x94D049BB133111EBL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
