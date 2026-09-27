package com.randombox.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;

/**
 * Mod side, simplified representation of a loot table.
 *
 * <p>It is produced either by reading a vanilla / datapack {@code LootTable} (see
 * {@link LootExtractor}) or by the in game loot table editor. Every original pool is preserved,
 * so the original per chest loot lists stay intact.
 */
public class BoxLootTable {
    private final ResourceLocation id;
    private final List<Pool> pools = new ArrayList<>();

    public BoxLootTable(ResourceLocation id) {
        this.id = id;
    }

    public ResourceLocation id() {
        return this.id;
    }

    public List<Pool> pools() {
        return this.pools;
    }

    public Pool addPool() {
        Pool pool = new Pool();
        this.pools.add(pool);
        return pool;
    }

    public boolean isEmpty() {
        for (Pool pool : this.pools) {
            if (!pool.entries().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Deep copy used before runtime-only special loot is injected. */
    public BoxLootTable copy() {
        BoxLootTable copy = new BoxLootTable(this.id);
        for (Pool pool : this.pools) {
            copy.pools.add(pool.copy());
        }
        return copy;
    }

    /**
     * Writes the fixed quality of every item into the matching entry (see {@link ItemQuality}).
     * Does nothing when {@code overrideQuality} is turned off in the config.
     */
    public BoxLootTable applyItemQuality() {
        for (Pool pool : this.pools) {
            for (Entry entry : pool.entries()) {
                entry.setQuality(ItemQuality.effective(entry.item(), entry.quality()));
            }
        }
        return this;
    }

    /** Total number of pools, the {@code k} of the {@code k / 4} stack size multiplier. */
    public int poolCount() {
        return this.pools.size();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(this.id);
        buf.writeVarInt(this.pools.size());
        for (Pool pool : this.pools) {
            pool.write(buf);
        }
    }

    public static BoxLootTable read(FriendlyByteBuf buf) {
        BoxLootTable table = new BoxLootTable(buf.readResourceLocation());
        int poolCount = buf.readVarInt();
        for (int i = 0; i < poolCount; i++) {
            table.pools.add(Pool.read(buf));
        }
        return table;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", this.id.toString());
        JsonArray pools = new JsonArray();
        for (Pool pool : this.pools) {
            pools.add(pool.toJson());
        }
        json.add("pools", pools);
        return json;
    }

    public static BoxLootTable fromJson(JsonObject json) {
        BoxLootTable table = new BoxLootTable(new ResourceLocation(json.get("id").getAsString()));
        JsonArray pools = json.getAsJsonArray("pools");
        for (int i = 0; i < pools.size(); i++) {
            table.pools.add(Pool.fromJson(pools.get(i).getAsJsonObject()));
        }
        return table;
    }

    /** A single loot pool. */
    public static class Pool {
        private float averageRolls = 1.0F;
        private final List<Entry> entries = new ArrayList<>();

        public float averageRolls() {
            return this.averageRolls;
        }

        public void setAverageRolls(float averageRolls) {
            this.averageRolls = Math.max(0.0F, averageRolls);
        }

        public List<Entry> entries() {
            return this.entries;
        }

        public Entry addEntry(Item item) {
            Entry entry = new Entry(item);
            this.entries.add(entry);
            return entry;
        }

        public Pool copy() {
            Pool copy = new Pool();
            copy.averageRolls = this.averageRolls;
            for (Entry entry : this.entries) {
                copy.entries.add(entry.copy());
            }
            return copy;
        }

        /**
         * Weight of this pool when the pool of a drawn item is picked: the average number of
         * <em>rolls</em> the vanilla pool performs, i.e. how many draws the pool contributes to
         * the chest. A table with {@code rolls} 3 / 5 / 0.5 therefore hands out items in exactly
         * that ratio.
         *
         * <p>The optional config value {@code poolWeightMode = "items"} multiplies it with the
         * average stack size of the pool instead.
         */
        public float expectedItems() {
            if (this.entries.isEmpty()) {
                return 0.0F;
            }
            float rolls = Math.max(0.0F, this.averageRolls);
            if (!com.randombox.config.RBConfig.poolWeightUsesStackSize()) {
                return rolls;
            }
            float avgStack = 0.0F;
            for (Entry entry : this.entries) {
                avgStack += (entry.minCount() + entry.maxCount()) * 0.5F;
            }
            avgStack /= this.entries.size();
            return rolls * Math.max(1.0F, avgStack);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeFloat(this.averageRolls);
            buf.writeVarInt(this.entries.size());
            for (Entry entry : this.entries) {
                entry.write(buf);
            }
        }

        public static Pool read(FriendlyByteBuf buf) {
            Pool pool = new Pool();
            pool.averageRolls = buf.readFloat();
            int count = buf.readVarInt();
            for (int i = 0; i < count; i++) {
                pool.entries.add(Entry.read(buf));
            }
            return pool;
        }

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("rolls", this.averageRolls);
            JsonArray entries = new JsonArray();
            for (Entry entry : this.entries) {
                entries.add(entry.toJson());
            }
            json.add("entries", entries);
            return json;
        }

        public static Pool fromJson(JsonObject json) {
            Pool pool = new Pool();
            pool.averageRolls = json.has("rolls") ? json.get("rolls").getAsFloat() : 1.0F;
            JsonArray entries = json.getAsJsonArray("entries");
            for (int i = 0; i < entries.size(); i++) {
                pool.entries.add(Entry.fromJson(entries.get(i).getAsJsonObject()));
            }
            return pool;
        }
    }

    /** A single item entry of a pool. */
    public static class Entry {
        private Item item;
        private int weight = 1;
        private float weightMultiplier = 1.0F;
        private int quality = 0;
        private int minCount = 1;
        private int maxCount = 1;
        /** Vanilla loot functions of the original entry, applied when present. */
        private BiFunction<ItemStack, LootContext, ItemStack> vanillaFunctions;

        public Entry(Item item) {
            this.item = item == null ? Items.AIR : item;
        }

        public Item item() {
            return this.item;
        }

        public void setItem(Item item) {
            this.item = item == null ? Items.AIR : item;
        }

        public int weight() {
            return this.weight;
        }

        public void setWeight(int weight) {
            this.weight = Math.max(0, weight);
        }

        public float weightMultiplier() {
            return this.weightMultiplier;
        }

        public void setWeightMultiplier(float multiplier) {
            this.weightMultiplier = Math.max(0.0F, multiplier);
        }

        public int quality() {
            return this.quality;
        }

        public void setQuality(int quality) {
            this.quality = quality;
        }

        public int minCount() {
            return this.minCount;
        }

        public void setMinCount(int minCount) {
            this.minCount = Math.max(1, minCount);
        }

        public int maxCount() {
            return this.maxCount;
        }

        public void setMaxCount(int maxCount) {
            this.maxCount = Math.max(1, maxCount);
        }

        public BiFunction<ItemStack, LootContext, ItemStack> vanillaFunctions() {
            return this.vanillaFunctions;
        }

        public void setVanillaFunctions(BiFunction<ItemStack, LootContext, ItemStack> functions) {
            this.vanillaFunctions = functions;
        }

        public Entry copy() {
            Entry copy = new Entry(this.item);
            copy.weight = this.weight;
            copy.weightMultiplier = this.weightMultiplier;
            copy.quality = this.quality;
            copy.minCount = this.minCount;
            copy.maxCount = this.maxCount;
            copy.vanillaFunctions = this.vanillaFunctions;
            return copy;
        }

        /**
         * Final weight of this entry: {@code baseWeight * (1 + quality * factor)} where the factor
         * comes from the rarity of the box.
         */
        public float adjustedWeight(float qualityFactor) {
            float weight = this.weight * this.weightMultiplier * (1.0F + this.quality * qualityFactor);
            return Math.max(0.0F, weight);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeResourceLocation(LootExtractor.itemId(this.item));
            buf.writeVarInt(this.weight);
            buf.writeVarInt(this.quality);
            buf.writeVarInt(this.minCount);
            buf.writeVarInt(this.maxCount);
        }

        public static Entry read(FriendlyByteBuf buf) {
            Entry entry = new Entry(LootExtractor.itemById(buf.readResourceLocation()));
            entry.weight = buf.readVarInt();
            entry.quality = buf.readVarInt();
            entry.minCount = buf.readVarInt();
            entry.maxCount = buf.readVarInt();
            return entry;
        }

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("item", LootExtractor.itemId(this.item).toString());
            json.addProperty("weight", this.weight);
            json.addProperty("quality", this.quality);
            json.addProperty("min", this.minCount);
            json.addProperty("max", this.maxCount);
            return json;
        }

        public static Entry fromJson(JsonObject json) {
            Entry entry = new Entry(LootExtractor.itemById(new ResourceLocation(json.get("item").getAsString())));
            entry.weight = json.has("weight") ? json.get("weight").getAsInt() : 1;
            entry.quality = json.has("quality") ? json.get("quality").getAsInt() : 0;
            entry.minCount = json.has("min") ? json.get("min").getAsInt() : 1;
            entry.maxCount = json.has("max") ? json.get("max").getAsInt() : 1;
            return entry;
        }
    }
}
