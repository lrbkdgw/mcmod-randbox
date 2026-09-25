package com.randombox;

import com.randombox.config.RBConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.util.RandomSource;

/**
 * The five box rarities.
 *
 * <p>chance    : probability a naturally generated loot chest gets this rarity
 * <p>itemCount : how many items the lottery draws
 * <p>qualityFactor : final entry weight = baseWeight * (1 + quality * qualityFactor)
 * <p>color     : particle / beam / ui color
 */
public enum Rarity {
    COMMON(0, "common", 0.500F, 3, 0.00F, 0xFFFFFF, ChatFormatting.WHITE),
    RARE(1, "rare", 0.300F, 4, 0.50F, 0x55FF55, ChatFormatting.GREEN),
    EPIC(2, "epic", 0.120F, 5, 1.00F, 0xAA00FF, ChatFormatting.LIGHT_PURPLE),
    LEGENDARY(3, "legendary", 0.065F, 6, 2.50F, 0xFFD700, ChatFormatting.YELLOW),
    MYTHIC(4, "mythic", 0.015F, 8, 10.00F, 0xFF3030, ChatFormatting.RED);

    public static final Rarity[] VALUES = values();

    private final int id;
    private final String key;
    private final float chance;
    private final int itemCount;
    private final float qualityFactor;
    private final int color;
    private final ChatFormatting format;

    Rarity(int id, String key, float chance, int itemCount, float qualityFactor, int color, ChatFormatting format) {
        this.id = id;
        this.key = key;
        this.chance = chance;
        this.itemCount = itemCount;
        this.qualityFactor = qualityFactor;
        this.color = color;
        this.format = format;
    }

    public int id() {
        return this.id;
    }

    public String key() {
        return this.key;
    }

    public float chance() {
        return this.chance;
    }

    public int itemCount() {
        return this.itemCount;
    }

    public float qualityFactor() {
        return this.qualityFactor;
    }

    public int color() {
        return this.color;
    }

    public float red() {
        return ((this.color >> 16) & 0xFF) / 255.0F;
    }

    public float green() {
        return ((this.color >> 8) & 0xFF) / 255.0F;
    }

    public float blue() {
        return (this.color & 0xFF) / 255.0F;
    }

    public ChatFormatting format() {
        return this.format;
    }

    public String translationKey() {
        return "randombox.rarity." + this.key;
    }

    public static Rarity byId(int id) {
        if (id < 0 || id >= VALUES.length) {
            return COMMON;
        }
        return VALUES[id];
    }

    /** Command / config quality argument: 0 = roll randomly, 1..5 = forced rarity. */
    public static Rarity byCommandValue(int value, RandomSource random) {
        if (value <= 0 || value > VALUES.length) {
            return random(random);
        }
        return VALUES[value - 1];
    }

    public static Rarity random(RandomSource random) {
        float[] chances = RBConfig.rarityChances();
        float total = 0.0F;
        for (Rarity rarity : VALUES) {
            total += chances[rarity.id()];
        }
        float roll = random.nextFloat() * total;
        for (Rarity rarity : VALUES) {
            roll -= chances[rarity.id()];
            if (roll <= 0.0F) {
                return rarity;
            }
        }
        return COMMON;
    }
}
