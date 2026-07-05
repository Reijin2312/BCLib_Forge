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

public class UnusualEndCompat {
    private static final ResourceLocation GLOOPSTONE_LANDS_ID = new ResourceLocation("unusualend", "gloopstone_lands");
    private static final ResourceLocation WARPED_REEF_ID = new ResourceLocation("unusualend", "warped_reef");
    private static final BiomeAPI.BiomeType GLOOPSTONE_LANDS_TYPE = new BiomeAPI.BiomeType(
            "UNUSUALEND_GLOOPSTONE_LANDS",
            BiomeAPI.BiomeType.END_LAND
    );
    private static final BiomeAPI.BiomeType WARPED_REEF_TYPE = new BiomeAPI.BiomeType(
            "UNUSUALEND_WARPED_REEF",
            BiomeAPI.BiomeType.END_VOID
    );

    private static boolean checked;
    private static boolean available;
    private static boolean decidersRegistered;

    public static void registerBCLBiomes() {
        if (!isAvailable()) {
            return;
        }

        registerIfUnknown(GLOOPSTONE_LANDS_ID, GLOOPSTONE_LANDS_TYPE);
        registerIfUnknown(WARPED_REEF_ID, WARPED_REEF_TYPE);
        registerDeciders();
    }

    private static void registerDeciders() {
        if (decidersRegistered) {
            return;
        }

        decidersRegistered = true;
        BiomeDecider.registerDecider(
                GLOOPSTONE_LANDS_ID,
                new EndPatchBiomeDecider(
                        GLOOPSTONE_LANDS_ID,
                        GLOOPSTONE_LANDS_TYPE,
                        UnusualEndCompat::isAvailable,
                        768,
                        128,
                        BiomeAPI.BiomeType.END_LAND,
                        BiomeAPI.BiomeType.END_BARRENS
                )
        );
        BiomeDecider.registerDecider(
                WARPED_REEF_ID,
                new EndPatchBiomeDecider(
                        WARPED_REEF_ID,
                        WARPED_REEF_TYPE,
                        UnusualEndCompat::isAvailable,
                        768,
                        96,
                        BiomeAPI.BiomeType.END_VOID
                )
        );

        if (Configs.MAIN_CONFIG.verboseLogging()) {
            BCLib.LOGGER.info("[BiomeSourceCompat] Registered Unusual End end biome deciders");
        }
    }

    private static boolean isAvailable() {
        if (!checked) {
            checked = true;
            try {
                Class.forName("net.mcreator.unusualend.UnusualEnd");
                available = true;
            } catch (ClassNotFoundException ignored) {
                available = false;
            }
        }
        return available;
    }

    private static void registerIfUnknown(ResourceLocation biomeID, BiomeAPI.BiomeType type) {
        final ResourceKey<Biome> biomeKey = ResourceKey.create(Registries.BIOME, biomeID);

        if (BCLBiomeRegistry.registerIfUnknown(biomeKey, type) != null && Configs.MAIN_CONFIG.verboseLogging()) {
            BCLib.LOGGER.info("[BiomeSourceCompat] Registered Unusual End {} as {}", biomeKey.location(), type);
        }
    }
}
