package io.github.lootroulette;

import net.minecraft.network.chat.Component;
import org.joml.Vector3f;

/** The five chest tiers. Weight is out of 10,000. */
public enum Rarity {
    COMMON("common", "普通", 5, 5_000, 0xF2F2F2, new Vector3f(0.95F, 0.95F, 0.95F)),
    RARE("rare", "稀有", 6, 3_000, 0x55FF55, new Vector3f(0.25F, 1.00F, 0.25F)),
    EPIC("epic", "史诗", 7, 1_200, 0xBB55FF, new Vector3f(0.70F, 0.25F, 1.00F)),
    LEGENDARY("legendary", "传说", 8, 650, 0xFFE34D, new Vector3f(1.00F, 0.82F, 0.12F)),
    MYTHIC("mythic", "绝世", 9, 150, 0xFF3333, new Vector3f(1.00F, 0.12F, 0.12F));

    private final String id;
    private final String chineseName;
    private final int itemCount;
    private final int weight;
    private final int color;
    private final Vector3f particleColor;

    Rarity(String id, String chineseName, int itemCount, int weight, int color, Vector3f particleColor) {
        this.id = id;
        this.chineseName = chineseName;
        this.itemCount = itemCount;
        this.weight = weight;
        this.color = color;
        this.particleColor = particleColor;
    }

    public String id() { return id; }
    public int itemCount() { return itemCount; }
    public int weight() { return weight; }
    public int color() { return color; }
    public Vector3f particleColor() { return particleColor; }
    public Component displayName() { return Component.translatable("rarity.lootroulette." + id); }
    public String fallbackName() { return chineseName; }

    public Rarity next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static Rarity byOrdinal(int value) {
        return values()[Math.floorMod(value, values().length)];
    }

    public static Rarity roll(int value) {
        int cursor = 0;
        for (Rarity rarity : values()) {
            cursor += rarity.weight;
            if (value < cursor) return rarity;
        }
        return COMMON;
    }
}
