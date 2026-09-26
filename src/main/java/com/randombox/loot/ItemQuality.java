package com.randombox.loot;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.randombox.RandomBoxMod;
import com.randombox.config.RBConfig;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Fixed per item "Quality".
 *
 * <p>Vanilla loot tables almost never define the {@code quality} field, so the rarity of a box
 * would barely change what it contains. This class gives every item of the game one fixed quality
 * value which is then written into the matching entries of <em>every</em> loot table
 * ({@link BoxLootTable#applyItemQuality()}), so the quality weight boost of the rarities
 * ({@code weight = base * (1 + quality * factor)}) actually does something.
 *
 * <p>The vast majority of items keeps quality {@code 0}; only the listed "interesting" items get a
 * value. Everything is stored in {@code config/randombox/item_quality.json} and can be changed
 * there or with {@code /RandomBox Quality <item> <value>}.
 */
public final class ItemQuality {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<ResourceLocation, Integer> QUALITIES = new LinkedHashMap<>();
    private static Path file;

    private ItemQuality() {
    }

    public static void load(Path configDir) {
        file = configDir.resolve("randombox").resolve("item_quality.json");
        QUALITIES.clear();
        defaults();
        try {
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    for (String key : json.keySet()) {
                        ResourceLocation id = ResourceLocation.tryParse(key);
                        if (id == null) {
                            continue;
                        }
                        int value = json.get(key).getAsInt();
                        if (value <= 0) {
                            QUALITIES.remove(id);
                        } else {
                            QUALITIES.put(id, value);
                        }
                    }
                }
            }
            save();
        } catch (Exception exception) {
            RandomBoxMod.LOGGER.error("Failed to load item qualities", exception);
        }
        RandomBoxMod.LOGGER.info("Loaded {} item qualities", QUALITIES.size());
    }

    private static void save() {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            JsonObject json = new JsonObject();
            for (Map.Entry<ResourceLocation, Integer> entry : QUALITIES.entrySet()) {
                json.addProperty(entry.getKey().toString(), entry.getValue());
            }
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (Exception exception) {
            RandomBoxMod.LOGGER.error("Failed to write item qualities", exception);
        }
    }

    /** Fixed quality of an item, {@code 0} for everything that is not listed. */
    public static int get(Item item) {
        if (item == null || item == Items.AIR) {
            return 0;
        }
        Integer value = QUALITIES.get(BuiltInRegistries.ITEM.getKey(item));
        return value == null ? 0 : value;
    }

    public static void set(Item item, int quality) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (quality <= 0) {
            QUALITIES.remove(id);
        } else {
            QUALITIES.put(id, quality);
        }
        save();
    }

    /** The value that has to be used for an entry: the fixed one unless the override is off. */
    public static int effective(Item item, int stored) {
        return RBConfig.overrideQuality() ? get(item) : stored;
    }

    public static int size() {
        return QUALITIES.size();
    }

    private static void put(int quality, Item... items) {
        for (Item item : items) {
            if (item != null && item != Items.AIR) {
                QUALITIES.put(BuiltInRegistries.ITEM.getKey(item), quality);
            }
        }
    }

    /**
     * The built in table. Roughly: 10 = unique / end game, 8 = netherite &amp; boss drops,
     * 6 = diamond tier, 4 = gold / emerald tier, 2 = iron tier, 0 = everything else.
     */
    private static void defaults() {
        put(10, Items.DRAGON_EGG, Items.NETHER_STAR, Items.ENCHANTED_GOLDEN_APPLE, Items.ELYTRA,
                Items.DRAGON_HEAD, Items.BEACON);
        put(9, Items.NETHERITE_INGOT, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, Items.TOTEM_OF_UNDYING,
                Items.HEART_OF_THE_SEA, Items.WITHER_SKELETON_SKULL, Items.MUSIC_DISC_PIGSTEP);
        put(8, Items.NETHERITE_SCRAP, Items.ANCIENT_DEBRIS, Items.TRIDENT, Items.CONDUIT,
                Items.ECHO_SHARD, Items.RECOVERY_COMPASS, Items.SHULKER_SHELL,
                Items.ENCHANTED_BOOK, Items.DISC_FRAGMENT_5);
        put(7, Items.NETHERITE_SWORD, Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE,
                Items.NETHERITE_SHOVEL, Items.NETHERITE_HOE, Items.NETHERITE_HELMET,
                Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);
        put(6, Items.DIAMOND, Items.DIAMOND_BLOCK, Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE,
                Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE, Items.DIAMOND_HELMET,
                Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
                Items.DIAMOND_HORSE_ARMOR, Items.GOLDEN_APPLE, Items.NAUTILUS_SHELL,
                Items.END_CRYSTAL, Items.NAME_TAG);
        put(5, Items.EMERALD_BLOCK, Items.GOLDEN_HORSE_ARMOR, Items.EXPERIENCE_BOTTLE,
                Items.MUSIC_DISC_OTHERSIDE, Items.MUSIC_DISC_5, Items.MUSIC_DISC_WARD,
                Items.MUSIC_DISC_11, Items.MUSIC_DISC_13, Items.MUSIC_DISC_BLOCKS,
                Items.MUSIC_DISC_CAT, Items.MUSIC_DISC_CHIRP, Items.MUSIC_DISC_FAR,
                Items.MUSIC_DISC_MALL, Items.MUSIC_DISC_MELLOHI, Items.MUSIC_DISC_STAL,
                Items.MUSIC_DISC_STRAD, Items.MUSIC_DISC_WAIT, Items.SADDLE);
        put(4, Items.EMERALD, Items.GOLD_BLOCK, Items.IRON_BLOCK, Items.GHAST_TEAR,
                Items.IRON_HORSE_ARMOR, Items.BLAZE_ROD, Items.ENDER_EYE, Items.CRYING_OBSIDIAN,
                Items.GOLDEN_CARROT, Items.LAPIS_BLOCK, Items.AMETHYST_SHARD,
                Items.SEA_LANTERN, Items.BELL, Items.SPYGLASS, Items.CROSSBOW);
        put(3, Items.GOLD_INGOT, Items.IRON_SWORD, Items.IRON_PICKAXE, Items.IRON_AXE,
                Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
                Items.GOLDEN_SWORD, Items.GOLDEN_PICKAXE, Items.GOLDEN_HELMET,
                Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS,
                Items.BOOK, Items.OBSIDIAN, Items.ENDER_PEARL, Items.TNT, Items.GLOWSTONE,
                Items.QUARTZ_BLOCK, Items.HONEYCOMB, Items.SUSPICIOUS_STEW);
        put(2, Items.IRON_INGOT, Items.LAPIS_LAZULI, Items.REDSTONE_BLOCK, Items.QUARTZ,
                Items.PHANTOM_MEMBRANE, Items.BLAZE_POWDER, Items.SLIME_BALL, Items.MAGMA_CREAM,
                Items.CLOCK, Items.COMPASS, Items.MAP, Items.BUCKET, Items.SHIELD,
                Items.FIRE_CHARGE);
        put(1, Items.COAL_BLOCK, Items.GOLD_NUGGET, Items.ARROW, Items.BREAD, Items.COOKED_BEEF,
                Items.APPLE, Items.SUGAR, Items.PAPER, Items.STRING, Items.LEATHER,
                Items.GUNPOWDER, Items.REDSTONE, Items.FLINT, Items.COAL);
    }
}
