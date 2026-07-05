package org.betterx.bclib.api.v2.generator.compat;

import org.betterx.bclib.BCLib;
import org.betterx.bclib.config.Configs;
import org.betterx.bclib.mixin.common.ChunkGeneratorAccessor;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ExternalBiomeSourceContext {
    private BiomeSource biomeSource;
    private final RegistryAccess registryAccess;
    private final Holder<DimensionType> dimensionType;
    private final ResourceKey<LevelStem> dimensionKey;
    private final ChunkGenerator chunkGenerator;
    private final long seed;
    private final String ownerName;

    public ExternalBiomeSourceContext(
            BiomeSource biomeSource,
            RegistryAccess registryAccess,
            Holder<DimensionType> dimensionType,
            ResourceKey<LevelStem> dimensionKey,
            ChunkGenerator chunkGenerator,
            long seed,
            String ownerName
    ) {
        this.biomeSource = biomeSource;
        this.registryAccess = registryAccess;
        this.dimensionType = dimensionType;
        this.dimensionKey = dimensionKey;
        this.chunkGenerator = chunkGenerator;
        this.seed = seed;
        this.ownerName = ownerName;
    }

    public BiomeSource biomeSource() {
        return biomeSource;
    }

    public void setBiomeSource(BiomeSource biomeSource, boolean updateChunkGenerator) {
        this.biomeSource = biomeSource;
        if (updateChunkGenerator && chunkGenerator instanceof ChunkGeneratorAccessor acc) {
            acc.bcl_setBiomeSource(biomeSource);
        }
    }

    public RegistryAccess registryAccess() {
        return registryAccess;
    }

    public Holder<DimensionType> dimensionType() {
        return dimensionType;
    }

    public ResourceKey<LevelStem> dimensionKey() {
        return dimensionKey;
    }

    public ChunkGenerator chunkGenerator() {
        return chunkGenerator;
    }

    public long seed() {
        return seed;
    }

    public String ownerName() {
        return ownerName;
    }

    public Object invokeNoArgMethod(Object target, String returnClassName, String... methodNames)
            throws ReflectiveOperationException {
        final Class<?> returnClass = Class.forName(returnClassName);
        Method method = findNoArgMethod(target.getClass(), returnClass, methodNames);
        if (method == null) {
            throw new NoSuchMethodException(target.getClass().getName() + " no-arg method returning " + returnClassName);
        }

        method.setAccessible(true);
        return method.invoke(target);
    }

    private Method findNoArgMethod(Class<?> sourceClass, Class<?> returnClass, String... methodNames) {
        Class<?> currentClass = sourceClass;
        while (currentClass != null) {
            for (String methodName : methodNames) {
                try {
                    Method method = currentClass.getDeclaredMethod(methodName);
                    if (method.getParameterCount() == 0 && returnClass.isAssignableFrom(method.getReturnType())) {
                        return method;
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }

            for (Method method : currentClass.getDeclaredMethods()) {
                if (method.getParameterCount() == 0 && returnClass.isAssignableFrom(method.getReturnType())) {
                    return method;
                }
            }

            currentClass = currentClass.getSuperclass();
        }

        return null;
    }

    public void log(String message, Object... args) {
        if (Configs.MAIN_CONFIG.verboseLogging()) {
            BCLib.LOGGER.info("[BiomeSourceCompat] " + message, args);
        }
    }

    public static String describeObject(Object object) {
        if (object == null) {
            return "null";
        }
        return object.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(object));
    }

    public static String describeKey(ResourceKey<?> key) {
        return key == null ? "null" : key.location().toString();
    }

    public static String describeHolderKey(Holder<?> holder) {
        return holder == null
                ? "null"
                : holder.unwrapKey().map(ExternalBiomeSourceContext::describeKey).orElse("unbound:" + describeObject(holder.value()));
    }

    public static String summarizeBiomeKeys(Collection<Holder<Biome>> biomes, int limit) {
        List<String> ids = new ArrayList<>();
        for (Holder<Biome> biome : biomes) {
            if (ids.size() >= limit) {
                ids.add("...+" + (biomes.size() - limit));
                break;
            }
            ids.add(biome.unwrapKey().map(ExternalBiomeSourceContext::describeKey).orElse("unbound"));
        }
        return String.join(", ", ids);
    }
}
