package org.betterx.bclib.api.v3.datagen;

import org.betterx.bclib.interfaces.tools.AddMineableAxe;
import org.betterx.bclib.interfaces.tools.AddMineableHammer;
import org.betterx.bclib.interfaces.tools.AddMineableHoe;
import org.betterx.bclib.interfaces.tools.AddMineablePickaxe;
import org.betterx.bclib.interfaces.tools.AddMineableShears;
import org.betterx.bclib.interfaces.tools.AddMineableShovel;
import org.betterx.bclib.interfaces.tools.AddMineableSword;
import org.betterx.worlds.together.tag.v3.CommonItemTags;
import org.betterx.worlds.together.tag.v3.ToolTags;

import net.minecraft.advancements.critereon.EnchantmentPredicate;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.predicates.AnyOfCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.MatchTool;

import java.util.ArrayList;
import java.util.List;

public final class LootTableUtil {
    private LootTableUtil() {
    }

    public static Holder<Enchantment> enchantmentHolder(ResourceKey<Enchantment> key) {
        return BuiltInRegistries.ENCHANTMENT.getHolderOrThrow(key);
    }

    public static Holder<Enchantment> enchantmentHolder(Enchantment enchantment) {
        ResourceLocation id = BuiltInRegistries.ENCHANTMENT.getKey(enchantment);
        return BuiltInRegistries.ENCHANTMENT.getHolderOrThrow(ResourceKey.create(Registries.ENCHANTMENT, id));
    }

    public static LootItemCondition.Builder hasSilkTouch() {
        return MatchTool.toolMatches(
                ItemPredicate.Builder.item()
                        .hasEnchantment(new EnchantmentPredicate(Enchantments.SILK_TOUCH, MinMaxBounds.Ints.atLeast(1)))
        );
    }

    public static LootItemCondition.Builder hasShears() {
        return anyOf(List.of(
                MatchTool.toolMatches(ItemPredicate.Builder.item().of(Items.SHEARS)),
                MatchTool.toolMatches(ItemPredicate.Builder.item().of(CommonItemTags.SHEARS)),
                MatchTool.toolMatches(ItemPredicate.Builder.item().of(ToolTags.FABRIC_SHEARS))
        ));
    }

    public static LootItemCondition.Builder hasHoe() {
        return anyOf(List.of(
                MatchTool.toolMatches(ItemPredicate.Builder.item().of(ItemTags.HOES)),
                MatchTool.toolMatches(ItemPredicate.Builder.item().of(ToolTags.FABRIC_HOES))
        ));
    }

    public static LootItemCondition.Builder correctTool(ItemLike block) {
        List<LootItemCondition.Builder> builders = new ArrayList<>();
        if (block instanceof AddMineableAxe) {
            builders.add(MatchTool.toolMatches(ItemPredicate.Builder.item().of(ItemTags.AXES)));
            builders.add(MatchTool.toolMatches(ItemPredicate.Builder.item().of(ToolTags.FABRIC_AXES)));
        }
        if (block instanceof AddMineablePickaxe) {
            builders.add(MatchTool.toolMatches(ItemPredicate.Builder.item().of(ItemTags.PICKAXES)));
            builders.add(MatchTool.toolMatches(ItemPredicate.Builder.item().of(ToolTags.FABRIC_PICKAXES)));
        }
        if (block instanceof AddMineableHoe) {
            builders.add(MatchTool.toolMatches(ItemPredicate.Builder.item().of(ItemTags.HOES)));
            builders.add(MatchTool.toolMatches(ItemPredicate.Builder.item().of(ToolTags.FABRIC_HOES)));
        }
        if (block instanceof AddMineableShovel) {
            builders.add(MatchTool.toolMatches(ItemPredicate.Builder.item().of(ItemTags.SHOVELS)));
            builders.add(MatchTool.toolMatches(ItemPredicate.Builder.item().of(ToolTags.FABRIC_SHOVELS)));
        }
        if (block instanceof AddMineableSword) {
            builders.add(MatchTool.toolMatches(ItemPredicate.Builder.item().of(ItemTags.SWORDS)));
            builders.add(MatchTool.toolMatches(ItemPredicate.Builder.item().of(ToolTags.FABRIC_SWORDS)));
        }
        if (block instanceof AddMineableShears) {
            builders.add(MatchTool.toolMatches(ItemPredicate.Builder.item().of(Items.SHEARS)));
            builders.add(MatchTool.toolMatches(ItemPredicate.Builder.item().of(CommonItemTags.SHEARS)));
            builders.add(MatchTool.toolMatches(ItemPredicate.Builder.item().of(ToolTags.FABRIC_SHEARS)));
        }
        if (block instanceof AddMineableHammer) {
            builders.add(MatchTool.toolMatches(ItemPredicate.Builder.item().of(CommonItemTags.HAMMERS)));
        }

        return anyOf(builders);
    }

    public static LootItemCondition.Builder correctToolOrSilk(ItemLike block) {
        LootItemCondition.Builder tool = correctTool(block);
        LootItemCondition.Builder silk = hasSilkTouch();
        if (tool == null) {
            return silk;
        }
        return AnyOfCondition.anyOf(tool, silk);
    }

    public static LootItemCondition.Builder shearsOrSilkTouch() {
        return AnyOfCondition.anyOf(hasShears(), hasSilkTouch());
    }

    public static LootItemCondition.Builder shearsOrHoeOrSilkTouch() {
        return AnyOfCondition.anyOf(hasShears(), hasHoe(), hasSilkTouch());
    }

    private static LootItemCondition.Builder anyOf(List<LootItemCondition.Builder> builders) {
        if (builders == null || builders.isEmpty()) return null;
        if (builders.size() == 1) return builders.get(0);
        return AnyOfCondition.anyOf(builders.toArray(new LootItemCondition.Builder[0]));
    }
}
