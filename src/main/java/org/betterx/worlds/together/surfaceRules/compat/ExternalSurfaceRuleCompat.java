package org.betterx.worlds.together.surfaceRules.compat;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.SurfaceRules;

import java.util.ArrayList;
import java.util.List;

public class ExternalSurfaceRuleCompat {
    public static List<SurfaceRules.RuleSource> getRules(
            ResourceKey<LevelStem> dimensionKey,
            BiomeSource loadedBiomeSource
    ) {
        List<SurfaceRules.RuleSource> rules = new ArrayList<>();
        rules.addAll(NetherExorcismRebornSurfaceRuleCompat.getRules(dimensionKey, loadedBiomeSource));
        rules.addAll(IncisionSurfaceRuleCompat.getRules(dimensionKey, loadedBiomeSource));
        return rules;
    }
}
