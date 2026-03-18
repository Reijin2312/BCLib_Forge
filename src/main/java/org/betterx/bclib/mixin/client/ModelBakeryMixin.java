package org.betterx.bclib.mixin.client;

import org.betterx.bclib.client.BCLibClient;

import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ModelBakery.class)
public class ModelBakeryMixin {
    @ModifyVariable(
            method = "cacheAndQueueDependencies",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private UnbakedModel bclib_useCustomModel(UnbakedModel model, ResourceLocation location) {
        UnbakedModel custom = BCLibClient.lazyModelbakery().getBlockModel(location);
        return custom != null ? custom : model;
    }
}
