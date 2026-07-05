package org.betterx.worlds.together.biomesource;

import org.betterx.worlds.together.world.BiomeSourceWithNoiseRelatedSettings;
import org.betterx.worlds.together.world.BiomeSourceWithSeed;

import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class BlueprintBiomeSourceCompat {
    private static final String MODDED_BIOME_SOURCE =
            "com.teamabnormals.blueprint.common.world.modification.ModdedBiomeSource";

    private BlueprintBiomeSourceCompat() {
    }

    public static boolean isModdedBiomeSource(BiomeSource source) {
        return source != null && MODDED_BIOME_SOURCE.equals(source.getClass().getName());
    }

    public static boolean wraps(BiomeSource source, BiomeSource expectedOriginal) {
        if (source == null || expectedOriginal == null) {
            return false;
        }

        BiomeSource cursor = source;
        Set<BiomeSource> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (isModdedBiomeSource(cursor) && seen.add(cursor)) {
            BiomeSource original = getOriginalSource(cursor);
            if (original == expectedOriginal) {
                return true;
            }
            cursor = original;
        }

        return false;
    }

    public static BiomeSource unwrapOriginalSource(BiomeSource source) {
        BiomeSource cursor = source;
        Set<BiomeSource> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (isModdedBiomeSource(cursor) && seen.add(cursor)) {
            BiomeSource original = getOriginalSource(cursor);
            if (original == null) {
                break;
            }
            cursor = original;
        }

        return cursor;
    }

    public static boolean setSeed(BiomeSource source, long seed) {
        BiomeSource target = unwrapOriginalSource(source);
        if (target instanceof BiomeSourceWithSeed seededSource) {
            seededSource.setSeed(seed);
            return true;
        }

        return false;
    }

    public static boolean onLoadGeneratorSettings(BiomeSource source, NoiseGeneratorSettings settings) {
        BiomeSource target = unwrapOriginalSource(source);
        if (target instanceof BiomeSourceWithNoiseRelatedSettings settingsSource) {
            settingsSource.onLoadGeneratorSettings(settings);
            return true;
        }

        return false;
    }

    public static BiomeSource refreshWrappedSource(BiomeSource source, ResourceKey<LevelStem> dimensionKey) {
        if (!isModdedBiomeSource(source) || dimensionKey == null) {
            return source;
        }

        try {
            Object biomes = getField(source, "biomes");
            BiomeSource originalSource = getOriginalSource(source);
            Object[] slices = (Object[]) getField(source, "slices");
            int size = (Integer) getField(source, "size");
            long slicesSeed = (Long) getField(source, "slicesSeed");
            long dimensionSalt = dimensionKey.location().hashCode();
            long seed = slicesSeed - 1791510900L - dimensionSalt;

            Constructor<?> constructor = source.getClass().getConstructor(
                    Class.forName("net.minecraft.core.Registry"),
                    BiomeSource.class,
                    ArrayList.class,
                    int.class,
                    long.class,
                    long.class
            );

            Object refreshed = constructor.newInstance(
                    biomes,
                    originalSource,
                    new ArrayList<>(Arrays.asList(slices)),
                    size,
                    seed,
                    dimensionSalt
            );
            return refreshed instanceof BiomeSource biomeSource ? biomeSource : source;
        } catch (ReflectiveOperationException | ClassCastException e) {
            return source;
        }
    }

    private static BiomeSource getOriginalSource(BiomeSource source) {
        try {
            Object value = getField(source, "originalSource");
            return value instanceof BiomeSource biomeSource ? biomeSource : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Object getField(Object object, String fieldName) throws ReflectiveOperationException {
        Field field = object.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(object);
    }
}
