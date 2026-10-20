package org.betterx.worlds.together.surfaceRules.compat;

import org.betterx.bclib.BCLib;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.SurfaceRules;

import java.lang.reflect.Method;

/**
 * Keeps Confluence: Otherworld's generator-local surface-rule cache in sync
 * after BCLib has installed its biome surface rules.
 *
 * <p>Confluence 1.2.9 does not read the rule source from
 * {@code NoiseGeneratorSettings} while building surfaces. It composes its own
 * rules once during world bootstrap, stores the result on the chunk generator,
 * and redirects both surface building and carvers to that stored value. Any
 * later BCLib/TerraBlender update is therefore invisible unless that cache is
 * recomposed.</p>
 */
public final class ConfluenceSurfaceRuleCompat {
    private static final String REGISTRY_CLASS =
            "org.confluence.mod.common.worldgen.biome.injector.SurfaceRuleRegistry";
    private static final String CATEGORY_CLASS = REGISTRY_CLASS + "$Category";
    private static final String SETTER_NAME = "confluence$setSurfaceRules";

    private ConfluenceSurfaceRuleCompat() {
    }

    public static void synchronize(
            NoiseBasedChunkGenerator generator,
            ResourceKey<LevelStem> dimensionKey,
            SurfaceRules.RuleSource baseRules
    ) {
        final String categoryName = categoryName(dimensionKey);
        if (categoryName == null) return;

        try {
            Class<?> registryClass = Class.forName(REGISTRY_CLASS, false, generator.getClass().getClassLoader());
            Class<?> categoryClass = Class.forName(CATEGORY_CLASS, false, generator.getClass().getClassLoader());
            Object category = categoryClass.getField(categoryName).get(null);
            Method compose = registryClass.getMethod("compose", categoryClass, SurfaceRules.RuleSource.class);
            SurfaceRules.RuleSource composedRules = (SurfaceRules.RuleSource) compose.invoke(null, category, baseRules);

            Method setter = generator.getClass().getMethod(SETTER_NAME, SurfaceRules.RuleSource.class);
            setter.invoke(generator, composedRules);

            if (org.betterx.bclib.config.Configs.MAIN_CONFIG.verboseLogging()) {
                BCLib.LOGGER.info("Synchronized Confluence surface rules for " + dimensionKey.location());
            }
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // Confluence is not installed, or this is a version without its generator-local cache.
        } catch (ReflectiveOperationException | LinkageError e) {
            BCLib.LOGGER.warning("Unable to synchronize Confluence surface rules for " + dimensionKey.location(), e);
        }
    }

    private static String categoryName(ResourceKey<LevelStem> dimensionKey) {
        if (LevelStem.OVERWORLD.equals(dimensionKey)) return "OVERWORLD";
        if (LevelStem.NETHER.equals(dimensionKey)) return "NETHER";
        if (LevelStem.END.equals(dimensionKey)) return "END";
        return null;
    }
}
