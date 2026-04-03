package org.betterx.bclib.mixin.common;

import org.betterx.worlds.together.world.BiomeSourceWithNoiseRelatedSettings;
import org.betterx.worlds.together.world.BiomeSourceWithSeed;

import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "com.teamabnormals.blueprint.common.world.modification.ModdedBiomeSource")
public abstract class BlueprintModdedBiomeSourceMixin implements BiomeSourceWithSeed, BiomeSourceWithNoiseRelatedSettings {
    @Shadow
    private BiomeSource originalSource;

    @Override
    public void setSeed(long seed) {
        if (this.originalSource instanceof BiomeSourceWithSeed source) {
            source.setSeed(seed);
        }
    }

    @Override
    public void onLoadGeneratorSettings(NoiseGeneratorSettings generator) {
        if (this.originalSource instanceof BiomeSourceWithNoiseRelatedSettings source) {
            source.onLoadGeneratorSettings(generator);
        }
    }
}
