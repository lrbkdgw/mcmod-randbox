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
    /** Highest quality value the scale uses. */
    public static final int MAX = 4;

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
                        int value = Math.min(MAX, json.get(key).getAsInt());
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
        quality = Math.min(MAX, quality);
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
     * The built in table. The scale is {@code 0 - 4} and only a hand picked set of items gets a
     * value at all - everything that is not listed here stays at {@code 0}.
     *
     * <ul>
     *   <li>4: unique / end game (dragon egg, nether star, enchanted golden apple, elytra, beacon)</li>
     *   <li>3: netherite ingot, totem of undying, heart of the sea, dragon head, diamond block ...</li>
     *   <li>2: wither skeleton skull, ancient debris, conduit, netherite &amp; diamond gear,
     *       diamond, emerald, shulker shell, mob heads, sniffer egg, sponge ...</li>
     *   <li>1: enchanted book, trident, golden apple, horse armor, music discs, smithing
     *       templates, pottery sherds, saddle, bell, blaze rod, ender pearl ...</li>
     * </ul>
     */
    private static void defaults() {
        // ---- 4: unique, end game --------------------------------------------------------
        put(4, Items.DRAGON_EGG, Items.NETHER_STAR, Items.ENCHANTED_GOLDEN_APPLE, Items.ELYTRA,
                Items.BEACON);

        // ---- 3: legendary drops ---------------------------------------------------------
        put(3, Items.NETHERITE_INGOT, Items.TOTEM_OF_UNDYING, Items.HEART_OF_THE_SEA,
                Items.DRAGON_HEAD, Items.NETHERITE_BLOCK, Items.DIAMOND_BLOCK,
                Items.MUSIC_DISC_PIGSTEP, Items.SNIFFER_EGG);

        // ---- 2: rare ---------------------------------------------------------------------
        put(2, Items.WITHER_SKELETON_SKULL, Items.ANCIENT_DEBRIS, Items.CONDUIT,
                Items.NETHERITE_SCRAP, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                Items.NETHERITE_SWORD, Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE,
                Items.NETHERITE_SHOVEL, Items.NETHERITE_HOE, Items.NETHERITE_HELMET,
                Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS,
                Items.DIAMOND, Items.EMERALD, Items.EMERALD_BLOCK, Items.SHULKER_SHELL,
                Items.ECHO_SHARD, Items.RECOVERY_COMPASS,
                Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE,
                Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE, Items.DIAMOND_HELMET,
                Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
                Items.END_CRYSTAL, Items.DRAGON_BREATH, Items.NAUTILUS_SHELL,
                Items.SKELETON_SKULL, Items.ZOMBIE_HEAD, Items.CREEPER_HEAD,
                Items.SPONGE, Items.WET_SPONGE, Items.LODESTONE, Items.GOLD_BLOCK,
                Items.NAME_TAG);

        // ---- 1: uncommon ------------------------------------------------------------------
        put(1, Items.ENCHANTED_BOOK, Items.TRIDENT, Items.GOLDEN_APPLE, Items.EXPERIENCE_BOTTLE, Items.SADDLE, Items.BELL,
                Items.SEA_LANTERN, Items.ENDER_EYE, Items.ENDER_PEARL, Items.BLAZE_ROD,
                Items.GHAST_TEAR, Items.AMETHYST_SHARD, Items.GOLDEN_CARROT,
                Items.GLISTERING_MELON_SLICE, Items.SPYGLASS, Items.CROSSBOW,
                Items.SUSPICIOUS_STEW, Items.CRYING_OBSIDIAN, Items.GILDED_BLACKSTONE,
                Items.IRON_BLOCK, Items.LAPIS_BLOCK, Items.OBSIDIAN, Items.GLOWSTONE,
                Items.TNT, Items.GOLD_INGOT, Items.CAKE, Items.ENCHANTING_TABLE,
                Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS,
                Items.CHAINMAIL_BOOTS);

        // horse armor
        put(1, Items.LEATHER_HORSE_ARMOR, Items.IRON_HORSE_ARMOR, Items.GOLDEN_HORSE_ARMOR,
                Items.DIAMOND_HORSE_ARMOR);

        // music discs
        put(1, Items.MUSIC_DISC_13, Items.MUSIC_DISC_CAT, Items.MUSIC_DISC_BLOCKS,
                Items.MUSIC_DISC_CHIRP, Items.MUSIC_DISC_FAR, Items.MUSIC_DISC_MALL,
                Items.MUSIC_DISC_MELLOHI, Items.MUSIC_DISC_STAL, Items.MUSIC_DISC_STRAD,
                Items.MUSIC_DISC_WARD, Items.MUSIC_DISC_11, Items.MUSIC_DISC_WAIT,
                Items.MUSIC_DISC_OTHERSIDE, Items.MUSIC_DISC_5, Items.DISC_FRAGMENT_5);

        // armor trim smithing templates
        put(1, Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE,
                Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE, Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE,
                Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE,
                Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE,
                Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE,
                Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE,
                Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE,
                Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE);

        // pottery sherds (archaeology finds)
        put(1, Items.ANGLER_POTTERY_SHERD, Items.ARCHER_POTTERY_SHERD, Items.ARMS_UP_POTTERY_SHERD,
                Items.BLADE_POTTERY_SHERD, Items.BREWER_POTTERY_SHERD, Items.BURN_POTTERY_SHERD,
                Items.DANGER_POTTERY_SHERD, Items.EXPLORER_POTTERY_SHERD, Items.FRIEND_POTTERY_SHERD,
                Items.HEART_POTTERY_SHERD, Items.HEARTBREAK_POTTERY_SHERD, Items.HOWL_POTTERY_SHERD,
                Items.MINER_POTTERY_SHERD, Items.MOURNER_POTTERY_SHERD, Items.PLENTY_POTTERY_SHERD,
                Items.PRIZE_POTTERY_SHERD, Items.SHEAF_POTTERY_SHERD, Items.SHELTER_POTTERY_SHERD,
                Items.SKULL_POTTERY_SHERD, Items.SNORT_POTTERY_SHERD);
    }
}
