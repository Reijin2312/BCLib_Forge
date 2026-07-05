package org.betterx.bclib.api.v2.generator.compat;

import org.betterx.bclib.BCLib;
import org.betterx.bclib.api.v2.generator.BiomeDecider;
import org.betterx.bclib.api.v2.levelgen.biomes.BCLBiomeRegistry;
import org.betterx.bclib.api.v2.levelgen.biomes.BiomeAPI;
import org.betterx.bclib.config.Configs;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

public class EndlessBiomesCompat {
    private static final ResourceLocation TWISTED_REEF_ID = new ResourceLocation("endlessbiomes", "twisted_reef");
    private static final BiomeAPI.BiomeType TWISTED_REEF_TYPE = new BiomeAPI.BiomeType(
            "ENDLESSBIOMES_TWISTED_REEF",
            BiomeAPI.BiomeType.END_VOID
    );

    private static boolean checked;
    private static boolean available;
    private static boolean deciderRegistered;

    public static void registerBCLBiomes() {
        if (!isAvailable()) {
            return;
        }

        registerIfUnknown("penumbral_forest", BiomeAPI.BiomeType.END_LAND);
        registerIfUnknown("twisted_reef", TWISTED_REEF_TYPE);
        registerTwistedReefDecider();
    }

    private static void registerTwistedReefDecider() {
        if (deciderRegistered) {
            return;
        }

        deciderRegistered = true;
        BiomeDecider.registerDecider(
                TWISTED_REEF_ID,
                new EndPatchBiomeDecider(
                        TWISTED_REEF_ID,
                        TWISTED_REEF_TYPE,
                        EndlessBiomesCompat::isAvailable,
                        768,
                        96,
                        BiomeAPI.BiomeType.END_VOID
                )
        );
        if (Configs.MAIN_CONFIG.verboseLogging()) {
            BCLib.LOGGER.info("[BiomeSourceCompat] Registered EndlessBiomes {} decider", TWISTED_REEF_ID);
        }
    }

    private static boolean isAvailable() {
        if (!checked) {
            checked = true;
            try {
                Class.forName("net.mcreator.endlessbiomes.EndlessbiomesMod");
                available = true;
            } catch (ClassNotFoundException ignored) {
                available = false;
            }
        }
        return available;
    }

    private static void registerIfUnknown(String biomePath, BiomeAPI.BiomeType type) {
        final ResourceKey<Biome> biomeKey = ResourceKey.create(
                Registries.BIOME,
                new ResourceLocation("endlessbiomes", biomePath)
        );

        if (BCLBiomeRegistry.registerIfUnknown(biomeKey, type) != null && Configs.MAIN_CONFIG.verboseLogging()) {
            BCLib.LOGGER.info("[BiomeSourceCompat] Registered EndlessBiomes {} as {}", biomeKey.location(), type);
        }
    }
}
