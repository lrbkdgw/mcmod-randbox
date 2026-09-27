package com.randombox.loot;

import java.util.ArrayList;
import java.util.List;

import com.randombox.enchantment.RandomBoxEnchantments;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Data-compatible additions that are tied to vanilla loot table contents.
 *
 * <p>The special treasure books are injected as extra weighted entries beside the vanilla item that
 * unlocks them. This keeps datapack changes compatible: if a datapack removes the trigger item from
 * the vanilla table, the matching book disappears too; if it changes the trigger item's weight, the
 * book follows that new weight.</p>
 */
public final class SpecialLoot {
    private static final ResourceLocation ANCIENT_CITY = new ResourceLocation("minecraft", "chests/ancient_city");
    private static final ResourceLocation ANCIENT_CITY_ICE_BOX = new ResourceLocation("minecraft", "chests/ancient_city_ice_box");
    private static final ResourceLocation END_CITY_TREASURE = new ResourceLocation("minecraft", "chests/end_city_treasure");

    private SpecialLoot() {
    }

    public static BoxLootTable apply(BoxLootTable table, ResourceLocation id) {
        if (table == null || id == null) {
            return table;
        }
        if (isAncientCity(id)) {
            injectBookBeside(table, Items.ENCHANTED_GOLDEN_APPLE,
                    RandomBoxEnchantments.TREASURE_DEEP_DIG.get(), 1, 3, 1.0F / 3.0F);
        }
        if (END_CITY_TREASURE.equals(id)) {
            injectBookBeside(table, Items.DIAMOND,
                    RandomBoxEnchantments.TREASURE_ASCENSION.get(), 1, 3, 1.0F / 15.0F);
        }
        return table;
    }

    private static boolean isAncientCity(ResourceLocation id) {
        return ANCIENT_CITY.equals(id) || ANCIENT_CITY_ICE_BOX.equals(id);
    }

    private static void injectBookBeside(BoxLootTable table, Item triggerItem, Enchantment enchantment,
                                         int level, int quality, float weightMultiplier) {
        for (BoxLootTable.Pool pool : table.pools()) {
            List<BoxLootTable.Entry> originals = new ArrayList<>(pool.entries());
            for (BoxLootTable.Entry source : originals) {
                if (source.item() != triggerItem || source.weight() <= 0 || source.weightMultiplier() <= 0.0F) {
                    continue;
                }
                BoxLootTable.Entry book = pool.addEntry(Items.ENCHANTED_BOOK);
                book.setWeight(source.weight());
                book.setWeightMultiplier(source.weightMultiplier() * weightMultiplier);
                book.setQuality(quality);
                book.setMinCount(1);
                book.setMaxCount(1);
                book.setVanillaFunctions((stack, context) -> RandomBoxEnchantments.enchantedBook(enchantment, level));
            }
        }
    }
}
