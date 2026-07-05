package org.betterx.worlds.together.surfaceRules.compat;

import org.betterx.bclib.BCLib;
import org.betterx.bclib.config.Configs;
import org.betterx.worlds.together.world.event.WorldBootstrap;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.placement.CaveSurface;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class IncisionSurfaceRuleCompat {
    public static List<SurfaceRules.RuleSource> getRules(
            ResourceKey<LevelStem> dimensionKey,
            BiomeSource loadedBiomeSource
    ) {
        if (!LevelStem.NETHER.equals(dimensionKey) || WorldBootstrap.getLastRegistryAccess() == null) {
            return List.of();
        }

        try {
            Class.forName("net.incision.init.IncisionModBiomes");
        } catch (ClassNotFoundException ignored) {
            return List.of();
        }

        List<SurfaceRules.RuleSource> rules = new ArrayList<>();
        addRuleIfPresent(loadedBiomeSource, rules);

        if (Configs.MAIN_CONFIG.verboseLogging() && !rules.isEmpty()) {
            BCLib.LOGGER.info("Added {} Incision surface rule(s) for {}", rules.size(), loadedBiomeSource);
        }
        return rules;
    }

    private static void addRuleIfPresent(
            BiomeSource loadedBiomeSource,
            List<SurfaceRules.RuleSource> rules
    ) {
        final ResourceKey<Biome> biomeKey = ResourceKey.create(
                Registries.BIOME,
                new ResourceLocation("incision", "eroded_yard")
        );
        if (loadedBiomeSource.possibleBiomes().stream().noneMatch(holder -> holder.is(biomeKey))) {
            return;
        }

        getBlockState(new ResourceLocation("incision", "weatherrack"))
                .map(block -> anySurfaceRule(biomeKey, block, block, block))
                .ifPresent(rules::add);
    }

    private static Optional<BlockState> getBlockState(ResourceLocation blockID) {
        Registry<Block> blockRegistry = WorldBootstrap.getLastRegistryAccess().registryOrThrow(Registries.BLOCK);
        return blockRegistry.getOptional(blockID).map(block -> block.defaultBlockState());
    }

    private static SurfaceRules.RuleSource anySurfaceRule(
            ResourceKey<Biome> biomeKey,
            BlockState topBlock,
            BlockState underBlock,
            BlockState fallbackBlock
    ) {
        return SurfaceRules.ifTrue(
                SurfaceRules.isBiome(biomeKey),
                SurfaceRules.sequence(
                        SurfaceRules.ifTrue(
                                SurfaceRules.stoneDepthCheck(0, false, 0, CaveSurface.FLOOR),
                                SurfaceRules.sequence(
                                        SurfaceRules.ifTrue(
                                                SurfaceRules.waterBlockCheck(-1, 0),
                                                SurfaceRules.state(topBlock)
                                        ),
                                        SurfaceRules.state(fallbackBlock)
                                )
                        ),
                        SurfaceRules.ifTrue(
                                SurfaceRules.stoneDepthCheck(0, true, 0, CaveSurface.FLOOR),
                                SurfaceRules.state(underBlock)
                        )
                )
        );
    }
}
