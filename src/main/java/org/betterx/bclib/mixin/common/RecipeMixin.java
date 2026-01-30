package org.betterx.bclib.mixin.common;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Recipe;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(Recipe.class)
public interface RecipeMixin<C extends Container> {
    //Water Bottles are potions and they do not return an empty bottle in crafting Recipes
    //This mixin will fix that behaviour

    @Overwrite
    default NonNullList<ItemStack> getRemainingItems(C container) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);

        for (int i = 0; i < remaining.size(); ++i) {
            ItemStack stack = container.getItem(i);
            if (stack.hasCraftingRemainingItem()) {
                remaining.set(i, stack.getCraftingRemainingItem());
            }
            if (stack.getItem() instanceof PotionItem && PotionUtils.getPotion(stack) == Potions.WATER) {
                remaining.set(i, new ItemStack(Items.GLASS_BOTTLE));
            }
        }

        return remaining;
    }
}
