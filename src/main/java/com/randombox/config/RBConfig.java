package com.randombox.config;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.randombox.RandomBoxMod;
import com.randombox.Rarity;

/**
 * Plain JSON configuration ({@code config/randombox.json}) so the mod does not depend on any
 * config framework.
 */
public final class RBConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Chance of every rarity, indexed like {@link Rarity#id()}. */
    private static float[] rarityChances = {0.500F, 0.300F, 0.120F, 0.065F, 0.015F};
    /** Amount of drawn items per rarity. */
    private static int[] itemCounts = {3, 4, 5, 6, 8};
    /** Quality factor per rarity: weight = base * (1 + quality * factor). */
    private static float[] qualityFactors = {0.0F, 0.5F, 1.0F, 2.5F, 10.0F};
    /** Average time a single item needs to be drawn, in seconds. */
    private static float secondsPerItem = 1.2F;
    /** Relative random spread of that time (0.5 = +-50%). */
    private static float timeSpread = 0.5F;
    /** Multiply stack sizes by {@code poolCount / 4}. */
    private static boolean scaleStackSizes = true;
    /** Overwrite the quality of every loot entry with the fixed quality of its item. */
    private static boolean overrideQuality = true;
    /** Radius in which clients are told about unopened boxes (particles / beams). */
    private static int effectRadius = 32;
    /** Radius in which an unopened box shows its light beam. */
    private static int beamRadius = 24;

    private RBConfig() {
    }

    public static void load(Path configDir) {
        Path file = configDir.resolve("randombox.json");
        try {
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    rarityChances = readFloats(json, "rarityChances", rarityChances);
                    itemCounts = readInts(json, "itemCounts", itemCounts);
                    qualityFactors = readFloats(json, "qualityFactors", qualityFactors);
                    secondsPerItem = readFloat(json, "secondsPerItem", secondsPerItem);
                    timeSpread = readFloat(json, "timeSpread", timeSpread);
                    scaleStackSizes = json.has("scaleStackSizes") ? json.get("scaleStackSizes").getAsBoolean() : scaleStackSizes;
                    overrideQuality = json.has("overrideQuality") ? json.get("overrideQuality").getAsBoolean() : overrideQuality;
                    effectRadius = json.has("effectRadius") ? json.get("effectRadius").getAsInt() : effectRadius;
                    beamRadius = json.has("beamRadius") ? json.get("beamRadius").getAsInt() : beamRadius;
                }
            }
            save(file);
        } catch (Exception exception) {
            RandomBoxMod.LOGGER.error("Failed to load config", exception);
        }
    }

    private static void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            JsonObject json = new JsonObject();
            json.add("rarityChances", GSON.toJsonTree(rarityChances));
            json.add("itemCounts", GSON.toJsonTree(itemCounts));
            json.add("qualityFactors", GSON.toJsonTree(qualityFactors));
            json.addProperty("secondsPerItem", secondsPerItem);
            json.addProperty("timeSpread", timeSpread);
            json.addProperty("scaleStackSizes", scaleStackSizes);
            json.addProperty("overrideQuality", overrideQuality);
            json.addProperty("effectRadius", effectRadius);
            json.addProperty("beamRadius", beamRadius);
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (Exception exception) {
            RandomBoxMod.LOGGER.error("Failed to write config", exception);
        }
    }

    private static float[] readFloats(JsonObject json, String name, float[] fallback) {
        if (!json.has(name)) {
            return fallback;
        }
        var array = json.getAsJsonArray(name);
        if (array.size() != fallback.length) {
            return fallback;
        }
        float[] result = new float[array.size()];
        for (int i = 0; i < array.size(); i++) {
            result[i] = array.get(i).getAsFloat();
        }
        return result;
    }

    private static int[] readInts(JsonObject json, String name, int[] fallback) {
        if (!json.has(name)) {
            return fallback;
        }
        var array = json.getAsJsonArray(name);
        if (array.size() != fallback.length) {
            return fallback;
        }
        int[] result = new int[array.size()];
        for (int i = 0; i < array.size(); i++) {
            result[i] = array.get(i).getAsInt();
        }
        return result;
    }

    private static float readFloat(JsonObject json, String name, float fallback) {
        return json.has(name) ? json.get(name).getAsFloat() : fallback;
    }

    public static float rarityChance(Rarity rarity) {
        return rarityChances[rarity.id()];
    }

    public static float[] rarityChances() {
        return rarityChances;
    }

    public static int itemCount(Rarity rarity) {
        return itemCounts[rarity.id()];
    }

    public static float qualityFactor(Rarity rarity) {
        return qualityFactors[rarity.id()];
    }

    public static float secondsPerItem() {
        return secondsPerItem;
    }

    public static float timeSpread() {
        return timeSpread;
    }

    public static boolean scaleStackSizes() {
        return scaleStackSizes;
    }

    public static boolean overrideQuality() {
        return overrideQuality;
    }

    public static int effectRadius() {
        return effectRadius;
    }

    public static int beamRadius() {
        return beamRadius;
    }
}
