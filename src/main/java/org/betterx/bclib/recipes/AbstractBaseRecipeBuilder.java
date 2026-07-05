package org.betterx.bclib.recipes;

import org.betterx.bclib.BCLib;
import org.betterx.bclib.api.v3.datagen.DatapackRecipeBuilder;
import org.betterx.bclib.api.v3.datagen.RecipeDataProvider;
import org.betterx.bclib.util.RecipeHelper;

import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class AbstractBaseRecipeBuilder<T extends AbstractBaseRecipeBuilder> implements DatapackRecipeBuilder {
    protected final ResourceLocation id;
    protected final ItemStack output;
    protected String group;

    protected RecipeCategory category;

    protected boolean alright;

    protected AbstractBaseRecipeBuilder(ResourceLocation id, ItemStack output) {
        this.id = id;
        this.category = RecipeCategory.MISC;
        if (!BCLib.isDatagen()) {
            this.output = ItemStack.EMPTY;
            this.alright = false;
            return;
        }
        this.output = output == null ? ItemStack.EMPTY : output;
        this.alright = output != null && RecipeHelper.exists(output.getItem());
    }

    protected AbstractBaseRecipeBuilder(ResourceLocation id, ItemLike output) {
        this.id = id;
        this.category = RecipeCategory.MISC;
        if (!BCLib.isDatagen()) {
            this.output = ItemStack.EMPTY;
            this.alright = false;
            return;
        }
        if (output == null || !RecipeHelper.exists(output)) {
            this.output = ItemStack.EMPTY;
            this.alright = false;
            return;
        }
        this.output = resolveItemStack(output, 1);
        this.alright = !this.output.isEmpty() && !this.output.is(Items.AIR);
    }

    public T setCategory(RecipeCategory category) {
        this.category = category;
        return (T) this;
    }

    protected T setGroup(String group) {
        this.group = group;
        return (T) this;
    }

    protected T setOutputCount(int count) {
        if (this.output.isEmpty()) return (T) this;
        this.output.setCount(count);
        return (T) this;
    }

    protected T setOutputTag(CompoundTag tag) {
        if (this.output.isEmpty()) return (T) this;
        this.output.setTag(tag);
        return (T) this;
    }

    protected T unlockedBy(ItemLike item) {
        if (!BCLib.isDatagen()) return (T) this;
        Item resolvedItem = resolveItem(item);
        if (resolvedItem == Items.AIR) return (T) this;
        this.unlocks(
                "has_" + resolvedItem.getDescriptionId(),
                hasItem(resolvedItem)
        );

        return (T) this;
    }

    protected T unlockedBy(TagKey<Item> tag) {
        if (!BCLib.isDatagen()) return (T) this;
        this.unlocks(
                "has_tag_" + tag.location().getNamespace() + "_" + tag.location().getPath(),
                hasTag(tag)
        );

        return (T) this;
    }

    /**
     * The Recipe will be unlocked by one of the passed Items. As sonn als players have one in their Inventory
     * the recipe will unlock. Those Items are mostly the input Items for the recipe.
     * <p>
     * This method will automatically derive a unique name for the criterion and call
     * {@link #unlocks(String, ItemLike...)}
     *
     * @param items {@link Item}s or {@link Block}s that will unlock the recipe.
     */
    protected T unlockedBy(ItemLike... items) {
        if (!BCLib.isDatagen()) return (T) this;
        String name = "has_" +
                Arrays.stream(items)
                      .map(block -> (block instanceof Block)
                              ? BuiltInRegistries.BLOCK.getKey((Block) block)
                              : BuiltInRegistries.ITEM.getKey((Item) block))
                      .filter(id -> id != null)
                      .map(id -> id.getPath())
                      .collect(Collectors.joining("_"));
        if (name.length() > 45) name = name.substring(0, 42);
        return unlocks(name, items);
    }

    /**
     * The Recipe will be unlocked by one of the passed Items. As sonn als players have one in their Inventory
     * the recipe will unlock. Those Items are mostly the input Items for the recipe.
     *
     * @param name  The name for this unlock-Criteria
     * @param items {@link Item}s or {@link Block}s that will unlock the recipe.
     */
    protected T unlocks(String name, ItemLike... items) {
        if (!BCLib.isDatagen()) return (T) this;
        ItemLike[] resolvedItems = Arrays.stream(items)
                                         .map(AbstractBaseRecipeBuilder::resolveItem)
                                         .filter(item -> item != Items.AIR)
                                         .toArray(ItemLike[]::new);
        if (resolvedItems.length == 0) return (T) this;
        return unlocks(name, InventoryChangeTrigger.TriggerInstance.hasItems(resolvedItems));
    }

    /**
     * The Recipe will be unlocked by one of the passed Items. As sonn als players have one in their Inventory
     * the recipe will unlock. Those Items are mostly the input Items for the recipe.
     * <p>
     * This method will automatically get the Items from the stacl and call {@link #unlockedBy(ItemLike...)}
     *
     * @param stacks {@link ItemStack}s that will unlock the recipe. The count is ignored.
     */
    protected T unlockedBy(ItemStack... stacks) {
        if (!BCLib.isDatagen()) return (T) this;
        ItemLike[] items = Arrays.stream(stacks)
                                 .filter(stack -> stack.getCount() > 0)
                                 .map(stack -> (ItemLike) stack.getItem())
                                 .toArray(ItemLike[]::new);

        return unlockedBy(items);
    }

    protected abstract T unlocks(String name, CriterionTriggerInstance trigger);

    protected static Item resolveItem(ItemLike itemLike) {
        if (itemLike == null) return Items.AIR;
        Item item = itemLike.asItem();
        if (item != Items.AIR) return item;

        if (itemLike instanceof Block block) {
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
            if (blockId != null && blockId != BuiltInRegistries.BLOCK.getDefaultKey()) {
                Item itemByBlockId = BuiltInRegistries.ITEM.get(blockId);
                if (itemByBlockId != Items.AIR
                        && BuiltInRegistries.ITEM.getKey(itemByBlockId) != BuiltInRegistries.ITEM.getDefaultKey()) {
                    return itemByBlockId;
                }
            }
        }

        return item;
    }

    protected static ItemStack resolveItemStack(ItemLike itemLike) {
        return resolveItemStack(itemLike, 1);
    }

    protected static ItemStack resolveItemStack(ItemLike itemLike, int count) {
        Item item = resolveItem(itemLike);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    private static CriterionTriggerInstance hasItem(ItemLike item) {
        return InventoryChangeTrigger.TriggerInstance.hasItems(resolveItem(item));
    }

    private static CriterionTriggerInstance hasTag(TagKey<Item> tag) {
        return InventoryChangeTrigger.TriggerInstance.hasItems(
                ItemPredicate.Builder.item().of(tag).build()
        );
    }

    public final T build() {
        if (!BCLib.isDatagen()) return (T) this;
        if (!checkRecipe())
            return (T) this;
        RecipeDataProvider.register(this);
        return (T) this;
    }

    protected boolean checkRecipe() {
        if (output == null) {
            BCLib.LOGGER.warning("Output for Recipe can't be 'null', recipe {} will be ignored!", id);
            return false;
        }
        if (!alright) {
            BCLib.LOGGER.debug("Can't add recipe {}! Ingredient or output do not exist.", id);
            return false;
        }
        if (output.isEmpty() || output.is(Items.AIR)) {
            BCLib.LOGGER.warning("Unable to build Recipe " + id + ": Result is AIR");
            return false;
        }
        if (BuiltInRegistries.ITEM.getKey(output.getItem()) == null) {
            BCLib.LOGGER.warning("Unable to build Recipe {}: Result item is not registered", id);
            return false;
        }
        return true;
    }

    protected abstract void buildRecipe(Consumer<FinishedRecipe> cc);

    @Override
    public final void build(Consumer<FinishedRecipe> cc) {
        if (!checkRecipe()) return;
        buildRecipe(cc);
    }

    @Override
    public final ResourceLocation getId() {
        return id;
    }
}
