package org.betterx.bclib.api.v2.generator.compat;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;

public class ExternalBiomeSourceAdapters {
    public static void prepare(ExternalBiomeSourceContext context) {
        prepareBCLibCompatibility(context);

        // This must remain the final stage. Confluence snapshots possibleBiomes() in a
        // per-source cache as soon as its handler is visible. Installing it any earlier
        // can hide biomes appended later by TerraBlender (and consequently removes their
        // placed features from ChunkGenerator.featuresPerStep).
        ConfluenceBiomeSourceCompat.finalizeCompatibility(context);
    }

    private static void prepareBCLibCompatibility(ExternalBiomeSourceContext context) {
        final List<Holder<Biome>> directMultiNoiseBiomes = new ArrayList<>();
        directMultiNoiseBiomes.addAll(NetherExorcismRebornBiomeSourceCompat.prepare(context));
        directMultiNoiseBiomes.addAll(IncisionBiomeSourceCompat.prepare(context));
        directMultiNoiseBiomes.addAll(MountainsPoemBiomeSourceCompat.prepare(context));
        TerraBlenderBiomeSourceCompat.prepare(context, directMultiNoiseBiomes);
        ElysiumBiomeSourceCompat.prepare(context);
    }
}
