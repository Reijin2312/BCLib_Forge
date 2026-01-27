package org.betterx.bclib.blocks;

import org.betterx.bclib.api.v3.datagen.DropSelfLootProvider;
import org.betterx.bclib.behaviours.interfaces.BehaviourStone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;

import java.util.Collections;
import java.util.List;

public abstract class BaseBlockWithEntity extends BaseEntityBlock implements DropSelfLootProvider<BaseBlockWithEntity> {
    protected BaseBlockWithEntity(Properties settings) {
        super(settings);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return Collections.singletonList(new ItemStack(this));
    }

    @Override
    public Item asItem() {
        Item item = super.asItem();
        if (item == Items.AIR) {
            Item byId = BuiltInRegistries.ITEM.get(BuiltInRegistries.BLOCK.getKey(this));
            if (byId != Items.AIR
                    && BuiltInRegistries.ITEM.getKey(byId) != BuiltInRegistries.ITEM.getDefaultKey()) {
                return byId;
            }
        }
        return item;
    }

    public static class Stone extends BaseBlockWithEntity implements BehaviourStone {
        public Stone(Properties settings) {
            super(settings);
        }
    }
}
