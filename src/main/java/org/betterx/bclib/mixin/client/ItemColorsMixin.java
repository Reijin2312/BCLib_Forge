package org.betterx.bclib.mixin.client;

import org.betterx.bclib.BCLib;

import net.minecraft.client.color.item.ItemColors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

@Mixin(ItemColors.class)
public class ItemColorsMixin {
    private static boolean bclibLoggedAirItems;

    @ModifyVariable(method = {"register", "m_92689_"}, at = @At("HEAD"), argsOnly = true, require = 0)
    private ItemLike[] bclib_filterAirItemLikes(ItemLike[] items) {
        int count = 0;
        List<String> offenders = null;
        for (ItemLike itemLike : items) {
            Item item = itemLike == null ? null : itemLike.asItem();
            if (isInvalidItem(itemLike, item)) {
                if (offenders == null) {
                    offenders = new ArrayList<>();
                }
                offenders.add(describeItemLike(itemLike, item));
            } else {
                count++;
            }
        }
        if (offenders != null && !bclibLoggedAirItems) {
            bclibLoggedAirItems = true;
            BCLib.LOGGER.warning("ItemColors.register filtered AIR item(s): {}", offenders);
            BCLib.LOGGER.error("ItemColors.register filtered AIR item(s) stacktrace", new RuntimeException("AIR ItemLike"));
        }
        if (count == items.length) {
            return items;
        }
        ItemLike[] filtered = new ItemLike[count];
        int index = 0;
        for (ItemLike itemLike : items) {
            Item item = itemLike == null ? null : itemLike.asItem();
            if (!isInvalidItem(itemLike, item)) {
                filtered[index++] = itemLike;
            }
        }
        return filtered;
    }

    private static boolean isInvalidItem(ItemLike itemLike, Item item) {
        if (itemLike == null || item == null || item == Items.AIR) {
            return true;
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        if (key != null && key.equals(BuiltInRegistries.ITEM.getDefaultKey()) && item != Items.AIR) {
            return true;
        }
        return false;
    }

    private static String describeItemLike(ItemLike itemLike, Item item) {
        if (itemLike == null) {
            return "null";
        }
        StringBuilder desc = new StringBuilder(itemLike.getClass().getName());
        if (itemLike instanceof Block block) {
            desc.append(" blockId=").append(BuiltInRegistries.BLOCK.getKey(block));
        }
        if (item != null) {
            desc.append(" itemId=").append(BuiltInRegistries.ITEM.getKey(item));
        }
        if (itemLike instanceof SpawnEggItem spawnEgg) {
            EntityType<?> type = spawnEgg.getType(null);
            desc.append(" eggType=").append(BuiltInRegistries.ENTITY_TYPE.getKey(type));
        }
        ResourceLocation key = item == null ? null : BuiltInRegistries.ITEM.getKey(item);
        if (key != null && key.equals(BuiltInRegistries.ITEM.getDefaultKey()) && item != Items.AIR) {
            desc.append(" itemKeyIsDefault=true");
        }
        return desc.toString();
    }
}
