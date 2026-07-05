package org.betterx.bclib.mixin.common;

import org.betterx.bclib.api.v3.bonemeal.BonemealAPI;
import org.betterx.bclib.blocks.FeatureSaplingBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BoneMealItem.class)
public class BoneMealItemMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void bclib_onUse(UseOnContext context, CallbackInfoReturnable<InteractionResult> info) {
        Level level = context.getLevel();
        final BlockPos blockPos = context.getClickedPos();
        final Player player = context.getPlayer();
        final boolean creative = player != null && player.getAbilities().instabuild;

        if (BonemealAPI.INSTANCE.runSpreaders(context.getItemInHand(), level, blockPos, true, !creative)) {
            bclib_success(context, blockPos);
            info.setReturnValue(InteractionResult.sidedSuccess(level.isClientSide));
            return;
        }

        if (creative) {
            final BlockState blockState = level.getBlockState(blockPos);
            if (blockState.getBlock() instanceof BonemealableBlock bblock
                    && level instanceof ServerLevel server
                    && blockState.getBlock() instanceof FeatureSaplingBlock<?, ?>
            ) {
                bblock.performBonemeal(server, context.getLevel().getRandom(), blockPos, blockState);
                bclib_success(context, blockPos);
                info.setReturnValue(InteractionResult.sidedSuccess(false));
            }
        }
    }

    private static void bclib_success(UseOnContext context, BlockPos blockPos) {
        Level level = context.getLevel();
        if (!level.isClientSide) {
            Player player = context.getPlayer();
            if (player != null) {
                player.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
            }
            level.levelEvent(1505, blockPos, 15);
        }
    }

    @Inject(method = "growCrop", at = @At("HEAD"), cancellable = true)
    private static void bcl_growCrop(
            ItemStack itemStack,
            Level level,
            BlockPos blockPos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (BonemealAPI.INSTANCE.runSpreaders(itemStack, level, blockPos, false)) {
            cir.setReturnValue(true);
        }
    }
}
