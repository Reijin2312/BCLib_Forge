package org.betterx.bclib.mixin.client;

import org.betterx.bclib.interfaces.CustomColorProvider;
import org.betterx.bclib.networking.VersionCheckerClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.main.GameConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow
    @Nullable
    public Screen screen;

    @Inject(method = "<init>*", at = @At("TAIL"))
    private void bclib_onMCInit(GameConfig args, CallbackInfo info) {
        final Minecraft minecraft = (Minecraft) (Object) this;
        final BlockColors blockColors = bcl$getFieldByType(minecraft, BlockColors.class);
        final ItemColors itemColors = bcl$getFieldByType(minecraft, ItemColors.class);
        if (blockColors == null || itemColors == null) {
            return;
        }

        BuiltInRegistries.BLOCK.forEach(block -> {
            if (block instanceof CustomColorProvider provider) {
                blockColors.register(provider.getProvider(), block);
                Item item = block.asItem();
                if (item != Items.AIR) {
                    itemColors.register(provider.getItemProvider(), item);
                }
            }
        });

        VersionCheckerClient.presentUpdateScreen(screen);
    }

    @Unique
    private static <T> T bcl$getFieldByType(Object instance, Class<T> type) {
        Class<?> current = instance.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (!type.isAssignableFrom(field.getType())) {
                    continue;
                }

                try {
                    field.setAccessible(true);
                    final Object value = field.get(instance);
                    if (value != null) {
                        return type.cast(value);
                    }
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }

        return null;
    }
}
