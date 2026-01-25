package org.betterx.bclib.recipes;

import org.betterx.bclib.BCLib;
import org.betterx.bclib.util.ItemUtil;
import org.betterx.bclib.util.RecipeHelper;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public abstract class AbstractDoubleInputRecipeBuilder<T extends AbstractDoubleInputRecipeBuilder, R extends Recipe<? extends Container>> extends AbstractSingleInputRecipeBuilder<T, R> {
    protected Ingredient secondaryInput;
    private ItemLike[] secondaryInputItems;
    private TagKey<Item> secondaryInputTag;
    private boolean secondaryInputResolved;

    protected AbstractDoubleInputRecipeBuilder(
            ResourceLocation id,
            ItemLike output
    ) {
        super(id, output);
    }

    public T setSecondaryInput(ItemLike... inputs) {
        if (!BCLib.isDatagen()) return (T) this;
        this.secondaryInputItems = inputs;
        this.secondaryInputTag = null;
        this.secondaryInput = null;
        this.secondaryInputResolved = false;
        return (T) this;
    }

    public T setSecondaryInput(TagKey<Item> input) {
        if (!BCLib.isDatagen()) return (T) this;
        this.secondaryInputTag = input;
        this.secondaryInputItems = null;
        this.secondaryInput = null;
        this.secondaryInputResolved = false;
        return (T) this;
    }

    public T setSecondaryInputAndUnlock(TagKey<Item> input) {
        setPrimaryInput(input);
        this.unlockedBy(input);
        return (T) this;
    }

    public T setSecondaryInputAndUnlock(ItemLike... inputs) {
        setSecondaryInput(inputs);
        for (ItemLike item : inputs) unlockedBy(item);

        return (T) this;
    }

    protected void resolveSecondaryInput(boolean force) {
        if (secondaryInputResolved && !force) {
            return;
        }
        secondaryInputResolved = true;
        if (secondaryInputTag != null) {
            secondaryInput = Ingredient.of(secondaryInputTag);
            return;
        }
        if (secondaryInputItems != null) {
            for (ItemLike item : secondaryInputItems) {
                this.alright &= RecipeHelper.exists(item);
            }
            secondaryInput = Ingredient.of(secondaryInputItems);
        }
    }

    @Override
    protected boolean checkRecipe() {
        resolveSecondaryInput(false);
        if (secondaryInput == null) {
            BCLib.LOGGER.warning(
                    "Secondary input for Recipe can't be 'null', recipe {} will be ignored!",
                    id
            );
            return false;
        }
        return super.checkRecipe();
    }

    @Override
    protected void serializeRecipeData(JsonObject root) {
        resolvePrimaryInput(true);
        resolveSecondaryInput(true);
        final JsonArray ingredients = new JsonArray();
        ingredients.add(primaryInput.toJson());
        ingredients.add(secondaryInput.toJson());
        root.add("ingredients", ingredients);

        if (group != null && !group.isEmpty()) {
            root.addProperty("group", group);
        }

        root.add("result", ItemUtil.toJsonRecipe(output));
    }
}
