package com.randombox.loot;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.randombox.RandomBoxMod;

import net.minecraft.resources.ResourceLocation;

/**
 * Server side storage of the loot tables created / edited with the in game GUI.
 *
 * <p>Custom tables shadow the vanilla table with the same id, so the original chest loot lists can
 * be tweaked without touching any datapack.
 */
public final class CustomLootStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<ResourceLocation, BoxLootTable> TABLES = new LinkedHashMap<>();
    private static Path directory;

    private CustomLootStore() {
    }

    public static void load(Path configDir) {
        directory = configDir.resolve("randombox").resolve("loot_tables");
        TABLES.clear();
        try {
            Files.createDirectories(directory);
            try (Stream<Path> files = Files.list(directory)) {
                for (Path file : files.toList()) {
                    if (!file.getFileName().toString().endsWith(".json")) {
                        continue;
                    }
                    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        BoxLootTable table = BoxLootTable.fromJson(json);
                        TABLES.put(table.id(), table);
                    } catch (Exception exception) {
                        RandomBoxMod.LOGGER.error("Failed to read custom loot table {}", file, exception);
                    }
                }
            }
        } catch (IOException exception) {
            RandomBoxMod.LOGGER.error("Failed to load custom loot tables", exception);
        }
        RandomBoxMod.LOGGER.info("Loaded {} custom loot tables", TABLES.size());
    }

    public static boolean has(ResourceLocation id) {
        return TABLES.containsKey(id);
    }

    public static BoxLootTable get(ResourceLocation id) {
        return TABLES.get(id);
    }

    public static List<BoxLootTable> all() {
        return new ArrayList<>(TABLES.values());
    }

    public static void put(BoxLootTable table) {
        TABLES.put(table.id(), table);
        save(table);
    }

    public static void remove(ResourceLocation id) {
        if (TABLES.remove(id) == null || directory == null) {
            return;
        }
        try {
            Files.deleteIfExists(directory.resolve(fileName(id)));
        } catch (IOException exception) {
            RandomBoxMod.LOGGER.error("Failed to delete custom loot table {}", id, exception);
        }
    }

    private static void save(BoxLootTable table) {
        if (directory == null) {
            return;
        }
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve(fileName(table.id()));
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(table.toJson(), writer);
            }
        } catch (IOException exception) {
            RandomBoxMod.LOGGER.error("Failed to save custom loot table {}", table.id(), exception);
        }
    }

    private static String fileName(ResourceLocation id) {
        return (id.getNamespace() + "." + id.getPath()).replace('/', '.') + ".json";
    }
}
