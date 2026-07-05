package org.betterx.bclib.api.v2.generator.compat;

import org.betterx.bclib.BCLib;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

public class ElysiumBiomeSourceCompat {
    @SuppressWarnings("unchecked")
    public static void prepare(ExternalBiomeSourceContext context) {
        try {
            final Class<?> elysiumSourceClass = Class.forName(
                    "net.jadenxgamer.elysium_api.impl.biome.ElysiumBiomeSource"
            );
            if (!elysiumSourceClass.isInstance(context.biomeSource())) {
                return;
            }

            final Method setDimension = elysiumSourceClass.getMethod("setDimension", ResourceKey.class);
            final Method setWorldSeed = elysiumSourceClass.getMethod("setWorldSeed", long.class);
            final Method addPossibleBiomes = elysiumSourceClass.getMethod("addPossibleBiomes", Set.class);
            setDimension.invoke(context.biomeSource(), context.dimensionKey());
            setWorldSeed.invoke(context.biomeSource(), context.seed());

            final Class<?> helperClass = Class.forName("net.jadenxgamer.elysium_api.impl.biome.ElysiumBiomeHelper");
            final String fieldName = LevelStem.NETHER.equals(context.dimensionKey())
                    ? "netherPossibleBiomes"
                    : "overworldPossibleBiomes";
            final Field possibleBiomes = helperClass.getField(fieldName);
            addPossibleBiomes.invoke(context.biomeSource(), (Set<Holder<Biome>>) possibleBiomes.get(null));
        } catch (ClassNotFoundException ignored) {
        } catch (ReflectiveOperationException e) {
            BCLib.LOGGER.warning("Unable to initialize Elysium external biome source for {}", context.ownerName(), e);
        }
    }
}
