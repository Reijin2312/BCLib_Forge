package org.betterx.bclib.api.v2.generator.compat;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;

public class ExternalBiomeSourceAdapters {
    public static void prepare(ExternalBiomeSourceContext context) {
        final List<Holder<Biome>> directMultiNoiseBiomes = new ArrayList<>();
        directMultiNoiseBiomes.addAll(NetherExorcismRebornBiomeSourceCompat.prepare(context));
        directMultiNoiseBiomes.addAll(IncisionBiomeSourceCompat.prepare(context));
        TerraBlenderBiomeSourceCompat.prepare(context, directMultiNoiseBiomes);
        ElysiumBiomeSourceCompat.prepare(context);
    }
}
