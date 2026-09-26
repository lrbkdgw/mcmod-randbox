package com.randombox.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.randombox.Rarity;

import net.minecraft.core.BlockPos;

/** Positions, rarities and opened state of the boxes around the client player. */
public final class ClientBoxCache {
    private static final Map<BlockPos, View> BOXES = new HashMap<>();

    private ClientBoxCache() {
    }

    public static void replaceAll(Map<BlockPos, View> boxes) {
        BOXES.clear();
        BOXES.putAll(boxes);
    }

    public static Map<BlockPos, View> boxes() {
        return Collections.unmodifiableMap(BOXES);
    }

    /** Drops one box, used when its block turns out to be gone. */
    public static void forget(BlockPos pos) {
        BOXES.remove(pos);
    }

    public static void clear() {
        BOXES.clear();
    }

    /** What the client knows about a box. */
    public record View(Rarity rarity, boolean opened) {
    }
}
