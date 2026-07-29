package org.betterx.worlds.together.biomesource;

import org.betterx.worlds.together.world.BiomeSourceWithNoiseRelatedSettings;
import org.betterx.worlds.together.world.BiomeSourceWithSeed;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
        return refreshWrappedSource(source, dimensionKey, Map.of());
    }

    public static BiomeSource refreshWrappedSource(
            BiomeSource source,
            ResourceKey<LevelStem> dimensionKey,
            Map<ResourceKey<Biome>, ? extends Collection<Holder<Biome>>> matchExpansions
    ) {
        if (!isModdedBiomeSource(source) || dimensionKey == null) {
            return source;
        }

        try {
            Object biomes = getField(source, "biomes");
            BiomeSource originalSource = getOriginalSource(source);
            Object[] slices = expandOverlayMatches((Object[]) getField(source, "slices"), matchExpansions);
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

    private static Object[] expandOverlayMatches(
            Object[] slices,
            Map<ResourceKey<Biome>, ? extends Collection<Holder<Biome>>> matchExpansions
    ) throws ReflectiveOperationException {
        if (matchExpansions.isEmpty()) {
            return slices;
        }

        Object[] expandedSlices = Arrays.copyOf(slices, slices.length);
        for (int index = 0; index < slices.length; index++) {
            if (!(slices[index] instanceof Pair<?, ?> sliceEntry)) {
                continue;
            }

            Object slice = sliceEntry.getSecond();
            Object provider = invoke(slice, "provider");
            if (!provider.getClass().getName().endsWith("BiomeUtil$OverlayModdedBiomeProvider")) {
                continue;
            }

            Object expandedProvider = expandOverlayProvider(provider, matchExpansions);
            if (expandedProvider == provider) {
                continue;
            }

            Object expandedSlice = construct(
                    slice.getClass(),
                    invoke(slice, "levels"),
                    invoke(slice, "weight"),
                    expandedProvider
            );
            expandedSlices[index] = Pair.of(sliceEntry.getFirst(), expandedSlice);
        }
        return expandedSlices;
    }

    private static Object expandOverlayProvider(
            Object provider,
            Map<ResourceKey<Biome>, ? extends Collection<Holder<Biome>>> matchExpansions
    ) throws ReflectiveOperationException {
        List<?> overlays = (List<?>) invoke(provider, "overlays");
        Collection<Holder<Biome>> configuredCenterBiomes = matchExpansions.get(Biomes.THE_END);
        final Collection<Holder<Biome>> protectedCenterBiomes = configuredCenterBiomes == null
                ? List.of()
                : configuredCenterBiomes;
        List<Object> expandedOverlays = new ArrayList<>(overlays.size());
        boolean changed = false;

        for (Object entry : overlays) {
            if (!(entry instanceof Pair<?, ?> overlay)
                    || !(overlay.getFirst() instanceof HolderSet<?> rawMatches)
                    || !(overlay.getSecond() instanceof BiomeSource biomeSource)) {
                expandedOverlays.add(entry);
                continue;
            }

            @SuppressWarnings("unchecked")
            HolderSet<Biome> matches = (HolderSet<Biome>) rawMatches;
            LinkedHashSet<Holder<Biome>> originalMatches = new LinkedHashSet<>();
            matches.forEach(originalMatches::add);
            LinkedHashSet<Holder<Biome>> expandedMatches = new LinkedHashSet<>(originalMatches);
            matches.forEach(holder -> {
                holder.unwrapKey()
                      .map(matchExpansions::get)
                      .ifPresent(expandedMatches::addAll);
            });

            // A Blueprint overlay that targets outer End zones must never acquire central-island
            // biomes merely because BCL expands its vanilla matches. Explicit author matches stay intact.
            expandedMatches.removeIf(holder -> !originalMatches.contains(holder)
                    && (protectedCenterBiomes.contains(holder) || holder.is(Biomes.THE_END)));

            if (expandedMatches.size() == matches.size()) {
                expandedOverlays.add(entry);
                continue;
            }

            changed = true;
            expandedOverlays.add(Pair.of(HolderSet.direct(List.copyOf(expandedMatches)), biomeSource));
        }

        return changed ? construct(provider.getClass(), expandedOverlays) : provider;
    }

    private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private static Object construct(Class<?> type, Object... arguments) throws ReflectiveOperationException {
        for (Constructor<?> constructor : type.getConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length != arguments.length) {
                continue;
            }

            boolean matches = true;
            for (int index = 0; index < parameterTypes.length; index++) {
                Object argument = arguments[index];
                Class<?> parameterType = wrapPrimitive(parameterTypes[index]);
                if (argument != null && !parameterType.isInstance(argument)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return constructor.newInstance(arguments);
            }
        }
        throw new NoSuchMethodException("No compatible constructor found for " + type.getName());
    }

    private static Class<?> wrapPrimitive(Class<?> type) {
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == boolean.class) return Boolean.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
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
