package org.betterx.bclib.mixin.common;

import org.betterx.bclib.api.v3.levelgen.features.FeatureConfigAPI;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlacedFeature.class)
public class PlacedFeatureMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void bclib_skipDisabledFeature(
            WorldGenLevel level,
            ChunkGenerator chunkGenerator,
            RandomSource random,
            BlockPos pos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        bclib_skipIfDisabled(level, cir);
    }

    @Inject(method = "placeWithBiomeCheck", at = @At("HEAD"), cancellable = true)
    private void bclib_skipDisabledFeatureWithBiomeCheck(
            WorldGenLevel level,
            ChunkGenerator chunkGenerator,
            RandomSource random,
            BlockPos pos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        bclib_skipIfDisabled(level, cir);
    }

    private void bclib_skipIfDisabled(WorldGenLevel level, CallbackInfoReturnable<Boolean> cir) {
        if (!FeatureConfigAPI.isPlacedFeatureEnabled((PlacedFeature)(Object)this, level.registryAccess())) {
            cir.setReturnValue(false);
        }
    }
}
