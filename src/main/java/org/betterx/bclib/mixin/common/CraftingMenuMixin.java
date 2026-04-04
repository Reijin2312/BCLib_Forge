package org.betterx.bclib.mixin.common;

import org.betterx.worlds.together.tag.v3.CommonBlockTags;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true)
    private void bclib_stillValid(Player player, CallbackInfoReturnable<Boolean> info) {
        final ContainerLevelAccess access = bcl$getContainerLevelAccess((CraftingMenu) (Object) this);
        if (access == null) {
            return;
        }

        if (access.evaluate((world, pos) -> {
            BlockState state = world.getBlockState(pos);
            return state.getBlock() instanceof CraftingTableBlock || state.is(CommonBlockTags.WORKBENCHES);
        }, true)) {
            info.setReturnValue(true);
        }
    }

    @Unique
    private static ContainerLevelAccess bcl$getContainerLevelAccess(CraftingMenu menu) {
        Class<?> current = menu.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (!ContainerLevelAccess.class.isAssignableFrom(field.getType())) {
                    continue;
                }

                try {
                    field.setAccessible(true);
                    Object value = field.get(menu);
                    if (value instanceof ContainerLevelAccess access) {
                        return access;
                    }
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}
