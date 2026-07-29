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

public final class MountainsPoemSurfaceRuleCompat {
    private MountainsPoemSurfaceRuleCompat() {
    }

    public static List<SurfaceRules.RuleSource> getRules(
            ResourceKey<LevelStem> dimensionKey,
            BiomeSource loadedBiomeSource
    ) {
        if (!LevelStem.NETHER.equals(dimensionKey) || WorldBootstrap.getLastRegistryAccess() == null) {
            return List.of();
        }

        try {
            Class.forName("net.mcreator.mountainspoem.init.MountainsPoemModBiomes");
        } catch (ClassNotFoundException ignored) {
            return List.of();
        }

        List<SurfaceRules.RuleSource> rules = new ArrayList<>();
        addRuleIfPresent(
                loadedBiomeSource,
                rules,
                "ancient_battlefields",
                "under_grass_block",
                "under_dirt"
        );
        addRuleIfPresent(
                loadedBiomeSource,
                rules,
                "under_rock_hill",
                "under_rock",
                "under_rock"
        );

        if (Configs.MAIN_CONFIG.verboseLogging() && !rules.isEmpty()) {
            BCLib.LOGGER.info("Added {} Mountain's Poem surface rule(s) for {}", rules.size(), loadedBiomeSource);
        }
        return rules;
    }

    private static void addRuleIfPresent(
            BiomeSource loadedBiomeSource,
            List<SurfaceRules.RuleSource> rules,
            String biomePath,
            String topBlockPath,
            String underBlockPath
    ) {
        ResourceKey<Biome> biomeKey = ResourceKey.create(
                Registries.BIOME,
                new ResourceLocation("mountains_poem", biomePath)
        );
        if (loadedBiomeSource.possibleBiomes().stream().noneMatch(holder -> holder.is(biomeKey))) {
            return;
        }

        Optional<BlockState> topBlock = getBlockState(new ResourceLocation("mountains_poem", topBlockPath));
        Optional<BlockState> underBlock = getBlockState(new ResourceLocation("mountains_poem", underBlockPath));
        if (topBlock.isPresent() && underBlock.isPresent()) {
            rules.add(anySurfaceRule(biomeKey, topBlock.get(), underBlock.get()));
        }
    }

    private static Optional<BlockState> getBlockState(ResourceLocation blockID) {
        Registry<Block> blockRegistry = WorldBootstrap.getLastRegistryAccess().registryOrThrow(Registries.BLOCK);
        return blockRegistry.getOptional(blockID).map(Block::defaultBlockState);
    }

    private static SurfaceRules.RuleSource anySurfaceRule(
            ResourceKey<Biome> biomeKey,
            BlockState topBlock,
            BlockState underBlock
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
                                        SurfaceRules.state(underBlock)
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
