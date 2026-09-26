package com.randombox.loot;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.randombox.RandomBoxMod;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.Deserializers;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.level.storage.loot.LootDataResolver;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.CompositeEntryBase;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.entries.TagEntry;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;

/**
 * Reads a vanilla / datapack {@link LootTable} into the simplified {@link BoxLootTable} model.
 *
 * <p>The table is first serialised with the <em>vanilla</em> loot table Gson
 * ({@link Deserializers#createLootTableSerializer()}) and the resulting JSON - exactly the json a
 * datapack would contain - is then parsed. That is completely independent from field names,
 * mappings and access transformers, understands every entry type (item, tag, alternatives, group,
 * sequence, nested loot_table references) and even gives back the original loot functions.
 *
 * <p>Only if that fails the old reflection based reader is used as a fallback.
 */
public final class LootExtractor {
    private static final Map<String, Field> FIELD_CACHE = new HashMap<>();
    private static final int ROLL_SAMPLES = 16;
    private static final int MAX_DEPTH = 6;

    private static Gson tableGson;
    private static Gson functionGson;

    private LootExtractor() {
    }

    private static Gson tableGson() {
        if (tableGson == null) {
            tableGson = Deserializers.createLootTableSerializer().create();
        }
        return tableGson;
    }

    private static Gson functionGson() {
        if (functionGson == null) {
            functionGson = Deserializers.createFunctionSerializer().create();
        }
        return functionGson;
    }

    public static ResourceLocation itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item == null ? Items.AIR : item);
    }

    public static Item itemById(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == null ? Items.AIR : item;
    }

    public static BoxLootTable extract(ResourceLocation id, LootTable table, LootContext context) {
        return extract(id, table, context, null);
    }

    /**
     * @param resolver used to resolve nested {@code minecraft:loot_table} entries, may be null
     */
    public static BoxLootTable extract(ResourceLocation id, LootTable table, LootContext context,
                                       LootDataResolver resolver) {
        BoxLootTable result = new BoxLootTable(id);
        if (table == null) {
            return result;
        }
        try {
            JsonElement element = tableGson().toJsonTree(table, LootTable.class);
            if (element != null && element.isJsonObject()) {
                readJson(element.getAsJsonObject(), result, context, resolver, 0);
            }
        } catch (Exception exception) {
            RandomBoxMod.LOGGER.warn("Could not read loot table {} as json, falling back to reflection", id, exception);
        }
        if (result.isEmpty()) {
            extractByReflection(table, result, context);
        }
        if (result.isEmpty()) {
            RandomBoxMod.LOGGER.warn("Loot table {} could not be read", id);
        }
        return result;
    }

    // ------------------------------------------------------------------ json ------------------

    private static void readJson(JsonObject json, BoxLootTable result, LootContext context,
                                 LootDataResolver resolver, int depth) {
        if (!json.has("pools") || !json.get("pools").isJsonArray()) {
            return;
        }
        JsonArray pools = json.getAsJsonArray("pools");
        for (JsonElement poolElement : pools) {
            if (!poolElement.isJsonObject()) {
                continue;
            }
            JsonObject poolJson = poolElement.getAsJsonObject();
            BoxLootTable.Pool pool = result.addPool();
            pool.setAverageRolls(averageRolls(poolJson.get("rolls"), context));
            if (poolJson.has("entries") && poolJson.get("entries").isJsonArray()) {
                for (JsonElement entry : poolJson.getAsJsonArray("entries")) {
                    if (entry.isJsonObject()) {
                        readEntry(entry.getAsJsonObject(), pool, context, resolver, depth);
                    }
                }
            }
            if (pool.entries().isEmpty()) {
                result.pools().remove(pool);
            }
        }
    }

    private static void readEntry(JsonObject json, BoxLootTable.Pool pool, LootContext context,
                                  LootDataResolver resolver, int depth) {
        if (depth > MAX_DEPTH) {
            return;
        }
        String type = json.has("type") ? json.get("type").getAsString() : "minecraft:empty";
        String path = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
        int weight = json.has("weight") ? json.get("weight").getAsInt() : 1;
        int quality = json.has("quality") ? json.get("quality").getAsInt() : 0;

        switch (path) {
            case "item" -> {
                Item item = itemById(new ResourceLocation(json.get("name").getAsString()));
                if (item != Items.AIR) {
                    addJsonEntry(pool, item, weight, quality, json);
                }
            }
            case "tag" -> {
                ResourceLocation tagId = new ResourceLocation(json.get("name").getAsString());
                TagKey<Item> tag = TagKey.create(BuiltInRegistries.ITEM.key(), tagId);
                java.util.List<Holder<Item>> holders = new java.util.ArrayList<>();
                for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                    holders.add(holder);
                }
                if (holders.isEmpty()) {
                    return;
                }
                // "expand": true turns every item of the tag into an own entry with the full
                // weight, "expand": false picks one random item of the tag for a single entry -
                // then the weight has to be spread over the items so the tag does not become
                // "size x weight" times more likely than it is in vanilla.
                boolean expand = !json.has("expand") || json.get("expand").getAsBoolean();
                int each = expand ? weight : Math.max(1, Math.round(weight / (float) holders.size()));
                for (Holder<Item> holder : holders) {
                    addJsonEntry(pool, holder.value(), each, quality, json);
                }
            }
            case "alternatives", "group", "sequence" -> {
                if (json.has("children") && json.get("children").isJsonArray()) {
                    for (JsonElement child : json.getAsJsonArray("children")) {
                        if (child.isJsonObject()) {
                            readEntry(child.getAsJsonObject(), pool, context, resolver, depth + 1);
                        }
                    }
                }
            }
            case "loot_table" -> {
                if (resolver == null || !json.has("name")) {
                    return;
                }
                ResourceLocation nested = new ResourceLocation(json.get("name").getAsString());
                LootTable nestedTable = resolver.getLootTable(nested);
                if (nestedTable == null || nestedTable == LootTable.EMPTY) {
                    return;
                }
                try {
                    JsonElement element = tableGson().toJsonTree(nestedTable, LootTable.class);
                    if (element == null || !element.isJsonObject()) {
                        return;
                    }
                    // flatten every pool of the referenced table into the current pool
                    BoxLootTable temp = new BoxLootTable(nested);
                    readJson(element.getAsJsonObject(), temp, context, resolver, depth + 1);
                    // The reference has one weight for the whole nested table, so its items share
                    // that weight according to their own relative weights.
                    int nestedTotal = 0;
                    for (BoxLootTable.Pool nestedPool : temp.pools()) {
                        for (BoxLootTable.Entry entry : nestedPool.entries()) {
                            nestedTotal += Math.max(1, entry.weight());
                        }
                    }
                    if (nestedTotal <= 0) {
                        return;
                    }
                    for (BoxLootTable.Pool nestedPool : temp.pools()) {
                        for (BoxLootTable.Entry entry : nestedPool.entries()) {
                            BoxLootTable.Entry copy = pool.addEntry(entry.item());
                            copy.setWeight(Math.max(1, Math.round(
                                    Math.max(1, weight) * Math.max(1, entry.weight()) / (float) nestedTotal)));
                            copy.setQuality(Math.max(entry.quality(), quality));
                            copy.setMinCount(entry.minCount());
                            copy.setMaxCount(entry.maxCount());
                            copy.setVanillaFunctions(entry.vanillaFunctions());
                        }
                    }
                } catch (Exception exception) {
                    RandomBoxMod.LOGGER.debug("Could not read nested loot table {}", nested, exception);
                }
            }
            default -> {
                // empty / dynamic / unknown: nothing to show in a lottery
            }
        }
    }

    private static void addJsonEntry(BoxLootTable.Pool pool, Item item, int weight, int quality, JsonObject json) {
        BoxLootTable.Entry entry = pool.addEntry(item);
        entry.setWeight(Math.max(1, weight));
        entry.setQuality(quality);
        entry.setVanillaFunctions(functionsOf(json));
        int[] counts = countsOf(json);
        entry.setMinCount(counts[0]);
        entry.setMaxCount(counts[1]);
    }

    /** Rebuilds the original loot functions of an entry from its json. */
    private static BiFunction<ItemStack, LootContext, ItemStack> functionsOf(JsonObject json) {
        if (!json.has("functions") || !json.get("functions").isJsonArray()) {
            return null;
        }
        JsonArray array = json.getAsJsonArray("functions");
        java.util.List<LootItemFunction> functions = new java.util.ArrayList<>();
        for (JsonElement element : array) {
            try {
                LootItemFunction function = functionGson().fromJson(element, LootItemFunction.class);
                if (function != null) {
                    functions.add(function);
                }
            } catch (Exception exception) {
                RandomBoxMod.LOGGER.debug("Skipping unreadable loot function {}", element, exception);
            }
        }
        if (functions.isEmpty()) {
            return null;
        }
        return LootDataManager.createComposite(functions.toArray(new LootItemFunction[0]));
    }

    /** Reads the {@code set_count} function of an entry so the editor can show the amounts. */
    private static int[] countsOf(JsonObject json) {
        int[] counts = {1, 1};
        if (!json.has("functions") || !json.get("functions").isJsonArray()) {
            return counts;
        }
        for (JsonElement element : json.getAsJsonArray("functions")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject function = element.getAsJsonObject();
            String name = function.has("function") ? function.get("function").getAsString() : "";
            if (!name.endsWith("set_count") || !function.has("count")) {
                continue;
            }
            JsonElement count = function.get("count");
            if (count.isJsonPrimitive()) {
                int value = Math.max(1, count.getAsInt());
                counts[0] = value;
                counts[1] = value;
            } else if (count.isJsonObject()) {
                JsonObject object = count.getAsJsonObject();
                if (object.has("min") && object.has("max")) {
                    counts[0] = Math.max(1, (int) Math.floor(object.get("min").getAsFloat()));
                    counts[1] = Math.max(counts[0], (int) Math.ceil(object.get("max").getAsFloat()));
                } else if (object.has("value")) {
                    JsonElement value = object.get("value");
                    if (value.isJsonPrimitive()) {
                        counts[0] = Math.max(1, value.getAsInt());
                        counts[1] = counts[0];
                    }
                }
            }
        }
        return counts;
    }

    /** Average number of rolls of a pool, read from the json {@code rolls} value. */
    private static float averageRolls(JsonElement element, LootContext context) {
        if (element == null) {
            return 1.0F;
        }
        if (element.isJsonPrimitive()) {
            try {
                return Math.max(0.0F, element.getAsFloat());
            } catch (Exception ignored) {
                return 1.0F;
            }
        }
        if (element.isJsonObject()) {
            JsonObject json = element.getAsJsonObject();
            if (json.has("min") && json.has("max")) {
                try {
                    return Math.max(0.0F, (json.get("min").getAsFloat() + json.get("max").getAsFloat()) * 0.5F);
                } catch (Exception ignored) {
                    // fall through to the provider
                }
            }
            if (context != null) {
                try {
                    NumberProvider provider = functionGson().fromJson(json, NumberProvider.class);
                    if (provider != null) {
                        float total = 0.0F;
                        for (int i = 0; i < ROLL_SAMPLES; i++) {
                            total += provider.getInt(context);
                        }
                        return Math.max(0.0F, total / ROLL_SAMPLES);
                    }
                } catch (Exception ignored) {
                    // unknown provider, assume a single roll
                }
            }
        }
        return 1.0F;
    }

    // ------------------------------------------------------------- reflection fallback --------

    private static void extractByReflection(LootTable table, BoxLootTable result, LootContext context) {
        LootPool[] pools = readField(table, LootTable.class, LootPool[].class);
        if (pools == null) {
            return;
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
    }

    private static float averageRolls(LootPool pool, LootContext context) {
        NumberProvider rolls = readField(pool, LootPool.class, NumberProvider.class);
        if (rolls == null || context == null) {
            return 1.0F;
        }
        try {
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

    private static int readInt(Object instance, Class<?> owner, String hint, int index, int fallback) {
        try {
            int seen = 0;
            for (Field field : owner.getDeclaredFields()) {
                if (field.getType() == int.class && !Modifier.isStatic(field.getModifiers())) {
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
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
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
