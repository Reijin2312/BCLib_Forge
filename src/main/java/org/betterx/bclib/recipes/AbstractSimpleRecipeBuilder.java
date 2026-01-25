package org.betterx.bclib.recipes;

import org.betterx.bclib.BCLib;
import org.betterx.bclib.util.RecipeHelper;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

public abstract class AbstractSimpleRecipeBuilder<T extends AbstractSimpleRecipeBuilder> extends AbstractBaseRecipeBuilder<T> {
    protected Ingredient primaryInput;
    private ItemLike[] primaryInputItems;
    private TagKey<Item> primaryInputTag;
    private boolean primaryInputResolved;

    protected AbstractSimpleRecipeBuilder(ResourceLocation id, ItemLike output) {
        this(id, new ItemStack(output, 1));
    }

    protected AbstractSimpleRecipeBuilder(ResourceLocation id, ItemStack stack) {
        super(id, stack);
    }

    public T setPrimaryInput(ItemLike... inputs) {
        if (!BCLib.isDatagen()) return (T) this;
        this.primaryInputItems = inputs;
        this.primaryInputTag = null;
        this.primaryInput = null;
        this.primaryInputResolved = false;
        return (T) this;
    }

    public T setPrimaryInput(TagKey<Item> input) {
        if (!BCLib.isDatagen()) return (T) this;
        this.primaryInputTag = input;
        this.primaryInputItems = null;
        this.primaryInput = null;
        this.primaryInputResolved = false;
        return (T) this;
    }

    public T setPrimaryInputAndUnlock(TagKey<Item> input) {
        setPrimaryInput(input);
        this.unlockedBy(input);
        return (T) this;
    }

    public T setPrimaryInputAndUnlock(ItemLike... inputs) {
        setPrimaryInput(inputs);
        for (ItemLike item : inputs) unlockedBy(item);

        return (T) this;
    }

    protected void resolvePrimaryInput(boolean force) {
        if (primaryInputResolved && !force) {
            return;
        }
        primaryInputResolved = true;
        if (primaryInputTag != null) {
            primaryInput = Ingredient.of(primaryInputTag);
            return;
        }
        if (primaryInputItems != null) {
            for (ItemLike item : primaryInputItems) {
                this.alright &= RecipeHelper.exists(item);
            }
            primaryInput = Ingredient.of(primaryInputItems);
        }
    }

    protected boolean checkRecipe() {
        resolvePrimaryInput(false);
        if (primaryInput == null) {
            BCLib.LOGGER.warning(
                    "Primary input for Recipe can't be 'null', recipe {} will be ignored!",
                    id
            );
            return false;
        }
        return super.checkRecipe();
    }
}
