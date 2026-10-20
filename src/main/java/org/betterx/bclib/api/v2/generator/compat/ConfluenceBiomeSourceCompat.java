package org.betterx.bclib.api.v2.generator.compat;

import org.betterx.bclib.BCLib;
import org.betterx.bclib.api.v2.generator.BCLBiomeSource;
import org.betterx.bclib.api.v2.generator.BCLibEndBiomeSource;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.dimension.LevelStem;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Connects Confluence: Otherworld's own biome injector to the vanilla
 * MultiNoise source retained inside BCLib's Nether biome source.
 *
 * <p>Confluence normally installs a handler only when the final dimension
 * exposes a MultiNoiseBiomeSource directly. BCLib wraps that source so it can
 * combine BetterNether with other biome engines, which makes Confluence skip
 * the Nether during its installation pass. This adapter builds Confluence's
 * normal RegionBiomeHandler and registers it on the retained source instead
 * of reimplementing Confluence's climate or weighting logic.</p>
 */
public final class ConfluenceBiomeSourceCompat {
    private static final String INJECTOR_CLASS =
            "org.confluence.mod.common.worldgen.biome.injector.ConfluenceBiomeInjector";
    private static final String REGION_TYPE_CLASS =
            "org.confluence.mod.common.worldgen.biome.injector.BiomeRegionType";
    private static final String SOURCE_INJECTOR_CLASS =
            "org.confluence.mod.common.worldgen.biome.injector.BiomeSourceInjector";
    private static final String END_HOLDER_CLASS = "org.confluence.mod.common.worldgen.TheEndBiomeHolder";
    private static volatile boolean endAccessResolved;
    private static Object endHandler;
    private static Method endResolve;
    private static Method endExtraBiomes;

    private ConfluenceBiomeSourceCompat() {
    }

    /**
     * Installs Confluence after every adapter which may mutate the retained source.
     *
     * <p>Confluence deliberately caches the union returned by {@code possibleBiomes()}.
     * Calling this before another compatibility layer has appended its biomes leaves the
     * chunk generator with an incomplete feature graph. Keep this as the final phase of
     * {@link ExternalBiomeSourceAdapters#prepare(ExternalBiomeSourceContext)}.</p>
     */
    public static void finalizeCompatibility(ExternalBiomeSourceContext context) {
        if (!LevelStem.NETHER.equals(context.dimensionKey())
                || !(context.biomeSource() instanceof MultiNoiseBiomeSource multiNoise)) {
            return;
        }

        try {
            final ClassLoader loader = multiNoise.getClass().getClassLoader();
            final Class<?> injectorClass = Class.forName(INJECTOR_CLASS, false, loader);
            final Class<?> regionTypeClass = Class.forName(REGION_TYPE_CLASS, false, loader);
            final Class<?> sourceInjectorClass = Class.forName(SOURCE_INJECTOR_CLASS, false, loader);
            final Object netherType = regionTypeClass.getField("NETHER").get(null);
            final Registry<Biome> biomeRegistry = context.registryAccess().registryOrThrow(Registries.BIOME);

            final Method createHandler = injectorClass.getDeclaredMethod(
                    "createRegionHandler",
                    regionTypeClass,
                    long.class,
                    Registry.class,
                    MultiNoiseBiomeSource.class,
                    net.minecraft.world.level.chunk.ChunkGenerator.class,
                    boolean.class
            );
            createHandler.setAccessible(true);
            final Object handler = createHandler.invoke(
                    null,
                    netherType,
                    context.seed(),
                    biomeRegistry,
                    multiNoise,
                    context.chunkGenerator(),
                    false
            );

            final Field handlersField = sourceInjectorClass.getDeclaredField("handlers");
            handlersField.setAccessible(true);
            final Map<?, ?> currentHandlers = (Map<?, ?>) handlersField.get(null);
            final Map<BiomeSource, Object> mergedHandlers = new IdentityHashMap<>();
            currentHandlers.forEach((source, existingHandler) ->
                    mergedHandlers.put((BiomeSource) source, existingHandler));
            mergedHandlers.put(multiNoise, handler);

            final Method setHandlers = sourceInjectorClass.getDeclaredMethod("setHandlers", Map.class);
            setHandlers.setAccessible(true);
            setHandlers.invoke(null, mergedHandlers);

            context.log(
                    "Installed Confluence Nether biome handler for {}: source={}, possibleBiomes={}",
                    context.ownerName(),
                    ExternalBiomeSourceContext.describeObject(multiNoise),
                    ExternalBiomeSourceContext.summarizeBiomeKeys(multiNoise.possibleBiomes(), 16)
            );
        } catch (ClassNotFoundException ignored) {
            // Confluence is not installed.
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            BCLib.LOGGER.warning("Unable to install Confluence biome compatibility for {}", context.ownerName(), e);
        }
    }

    public static Holder<Biome> resolveEndBiome(
            BCLBiomeSource source,
            Holder<Biome> original,
            int biomeX,
            int biomeY,
            int biomeZ,
            Climate.Sampler sampler
    ) {
        if (!(source instanceof BCLibEndBiomeSource) || !resolveEndAccess(source.getClass().getClassLoader())) {
            return original;
        }

        try {
            @SuppressWarnings("unchecked")
            Holder<Biome> resolved = (Holder<Biome>) endResolve.invoke(
                    endHandler,
                    biomeX,
                    biomeY,
                    biomeZ,
                    sampler,
                    (Supplier<Holder<Biome>>) () -> original
            );
            return resolved == null ? original : resolved;
        } catch (ReflectiveOperationException | LinkageError e) {
            BCLib.LOGGER.warning("Unable to resolve a Confluence End biome", e);
            return original;
        }
    }

    public static Set<Holder<Biome>> appendEndBiomes(
            BCLBiomeSource source,
            Set<Holder<Biome>> baseBiomes
    ) {
        if (!(source instanceof BCLibEndBiomeSource) || !resolveEndAccess(source.getClass().getClassLoader())) {
            return baseBiomes;
        }

        try {
            @SuppressWarnings("unchecked")
            List<Holder<Biome>> extraBiomes = (List<Holder<Biome>>) endExtraBiomes.invoke(endHandler);
            if (extraBiomes.isEmpty() || baseBiomes.containsAll(extraBiomes)) {
                return baseBiomes;
            }
            Set<Holder<Biome>> combined = new HashSet<>(baseBiomes);
            combined.addAll(extraBiomes);
            return Set.copyOf(combined);
        } catch (ReflectiveOperationException | LinkageError e) {
            BCLib.LOGGER.warning("Unable to expose Confluence End biomes", e);
            return baseBiomes;
        }
    }

    private static synchronized boolean resolveEndAccess(ClassLoader loader) {
        if (endAccessResolved) {
            return endHandler != null;
        }
        endAccessResolved = true;

        try {
            Class<?> holderClass = Class.forName(END_HOLDER_CLASS, false, loader);
            Method handlerMethod = holderClass.getMethod("handler");
            endHandler = handlerMethod.invoke(null);
            endResolve = endHandler.getClass().getMethod(
                    "resolve",
                    int.class,
                    int.class,
                    int.class,
                    Climate.Sampler.class,
                    Supplier.class
            );
            endExtraBiomes = endHandler.getClass().getMethod("extraBiomes");
            endResolve.setAccessible(true);
            endExtraBiomes.setAccessible(true);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (ReflectiveOperationException | LinkageError e) {
            endHandler = null;
            BCLib.LOGGER.warning("Unable to initialize Confluence End biome compatibility", e);
            return false;
        }
    }
}
