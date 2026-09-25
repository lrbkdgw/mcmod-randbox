package com.randombox.loot;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import com.randombox.RandomBoxMod;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.CompositeEntryBase;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.entries.TagEntry;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;

/**
 * Reads a vanilla / datapack {@link LootTable} into the simplified {@link BoxLootTable} model.
 *
 * <p>Reflection (by field <em>type</em>, never by name) is used on purpose: it keeps the mod
 * independent from the mapping names of the loot table internals and therefore does not need an
 * access transformer.
 */
public final class LootExtractor {
    private static final Map<String, Field> FIELD_CACHE = new HashMap<>();
    private static final int ROLL_SAMPLES = 16;

    private LootExtractor() {
    }

    public static ResourceLocation itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item == null ? Items.AIR : item);
    }

    public static Item itemById(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == null ? Items.AIR : item;
    }

    public static BoxLootTable extract(ResourceLocation id, LootTable table, LootContext context) {
        BoxLootTable result = new BoxLootTable(id);
        if (table == null) {
            return result;
        }
        LootPool[] pools = readField(table, LootTable.class, LootPool[].class);
        if (pools == null) {
            return result;
        }
        for (LootPool pool : pools) {
            if (pool == null) {
                continue;
            }
            BoxLootTable.Pool modelPool = result.addPool();
            modelPool.setAverageRolls(averageRolls(pool, context));
            LootPoolEntryContainer[] entries = readField(pool, LootPool.class, LootPoolEntryContainer[].class);
            if (entries != null) {
                for (LootPoolEntryContainer entry : entries) {
                    flatten(entry, modelPool);
                }
            }
            if (modelPool.entries().isEmpty()) {
                result.pools().remove(modelPool);
            }
        }
        return result;
    }

    private static float averageRolls(LootPool pool, LootContext context) {
        NumberProvider rolls = readField(pool, LootPool.class, NumberProvider.class);
        if (rolls == null) {
            return 1.0F;
        }
        try {
            if (context == null) {
                return 1.0F;
            }
            float total = 0.0F;
            for (int i = 0; i < ROLL_SAMPLES; i++) {
                total += rolls.getInt(context);
            }
            return Math.max(0.0F, total / ROLL_SAMPLES);
        } catch (Exception exception) {
            return 1.0F;
        }
    }

    private static void flatten(LootPoolEntryContainer entry, BoxLootTable.Pool pool) {
        if (entry == null) {
            return;
        }
        if (entry instanceof CompositeEntryBase) {
            LootPoolEntryContainer[] children = readField(entry, CompositeEntryBase.class, LootPoolEntryContainer[].class);
            if (children != null) {
                for (LootPoolEntryContainer child : children) {
                    flatten(child, pool);
                }
            }
            return;
        }
        if (!(entry instanceof LootPoolSingletonContainer singleton)) {
            return;
        }
        int weight = readInt(singleton, LootPoolSingletonContainer.class, "weight", 0, 1);
        int quality = readInt(singleton, LootPoolSingletonContainer.class, "quality", 1, 0);
        BiFunction<ItemStack, LootContext, ItemStack> functions =
                readFieldUnchecked(singleton, LootPoolSingletonContainer.class, BiFunction.class);

        if (singleton instanceof LootItem lootItem) {
            Item item = readField(lootItem, LootItem.class, Item.class);
            if (item == null || item == Items.AIR) {
                return;
            }
            addEntry(pool, item, weight, quality, functions);
            return;
        }
        if (singleton instanceof TagEntry tagEntry) {
            TagKey<Item> tag = readFieldUnchecked(tagEntry, TagEntry.class, TagKey.class);
            if (tag == null) {
                return;
            }
            for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                addEntry(pool, holder.value(), weight, quality, functions);
            }
        }
    }

    private static void addEntry(BoxLootTable.Pool pool, Item item, int weight, int quality,
                                 BiFunction<ItemStack, LootContext, ItemStack> functions) {
        BoxLootTable.Entry entry = pool.addEntry(item);
        entry.setWeight(Math.max(1, weight));
        entry.setQuality(quality);
        entry.setVanillaFunctions(functions);
        entry.setMinCount(1);
        entry.setMaxCount(1);
    }

    /** Reads the {@code index}-th {@code int} field declared by {@code owner}. */
    private static int readInt(Object instance, Class<?> owner, String hint, int index, int fallback) {
        try {
            int seen = 0;
            for (Field field : owner.getDeclaredFields()) {
                if (field.getType() == int.class) {
                    if (seen == index) {
                        field.setAccessible(true);
                        return field.getInt(instance);
                    }
                    seen++;
                }
            }
        } catch (Exception exception) {
            RandomBoxMod.LOGGER.debug("Unable to read int field {} of {}", hint, owner, exception);
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    private static <T> T readField(Object instance, Class<?> owner, Class<T> type) {
        Field field = findField(owner, type);
        if (field == null) {
            return null;
        }
        try {
            return (T) field.get(instance);
        } catch (Exception exception) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T readFieldUnchecked(Object instance, Class<?> owner, Class<?> type) {
        Field field = findField(owner, type);
        if (field == null) {
            return null;
        }
        try {
            return (T) field.get(instance);
        } catch (Exception exception) {
            return null;
        }
    }

    private static Field findField(Class<?> owner, Class<?> type) {
        String key = owner.getName() + "|" + type.getName();
        Field cached = FIELD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Class<?> current = owner;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (type.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                    } catch (Exception exception) {
                        continue;
                    }
                    FIELD_CACHE.put(key, field);
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        RandomBoxMod.LOGGER.warn("Could not find a field of type {} in {}", type, owner);
        return null;
    }
}
