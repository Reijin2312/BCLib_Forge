package org.betterx.bclib.compat.terrablender;

import org.betterx.bclib.BCLib;

import net.minecraft.resources.ResourceLocation;
import terrablender.api.Regions;

public final class BCLibTerraBlender {
    private static final int NETHER_WEIGHT_MULTIPLIER = 10;
    private static final int DEFAULT_NETHER_TOTAL_WEIGHT = 12 * NETHER_WEIGHT_MULTIPLIER;
    private static final int NETHER_VARIANT_COUNT = 12;
    private static boolean initialized = false;

    private BCLibTerraBlender() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        final int baseWeight = Math.max(1, DEFAULT_NETHER_TOTAL_WEIGHT / NETHER_VARIANT_COUNT);
        final int remainder = Math.max(0, DEFAULT_NETHER_TOTAL_WEIGHT - baseWeight * NETHER_VARIANT_COUNT);

        for (int i = 0; i < NETHER_VARIANT_COUNT; i++) {
            final int weight = baseWeight + (i < remainder ? 1 : 0);
            if (weight <= 0) {
                continue;
            }

            final ResourceLocation location = BCLib.makeID("nether_variant_" + i);
            Regions.register(new BCLibNetherRegion(location, weight, i, NETHER_VARIANT_COUNT));
        }

        BCLib.LOGGER.info(
                "Registered {} TerraBlender nether region variants for BCLib biomes (weight multiplier x{})",
                NETHER_VARIANT_COUNT,
                NETHER_WEIGHT_MULTIPLIER
        );
    }
}
