package com.randombox.client;

import java.util.HashMap;
import java.util.Map;

import com.randombox.Rarity;
import com.randombox.net.EditorDataPacket;
import com.randombox.net.StartReelPacket;
import com.randombox.net.SyncBoxesPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Client side packet handling, only ever loaded on the physical client. */
public final class ClientPacketHandler {
    private ClientPacketHandler() {
    }

    public static void handleStartReel(StartReelPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new ReelScreen(packet.pos(), packet.rarity(), packet.reels(), packet.durations()));
    }

    public static void handleSyncBoxes(SyncBoxesPacket packet) {
        Map<BlockPos, ClientBoxCache.View> boxes = new HashMap<>();
        for (int i = 0; i < packet.positions().size(); i++) {
            boxes.put(packet.positions().get(i),
                    new ClientBoxCache.View(Rarity.byId(packet.rarities().get(i)), packet.opened().get(i)));
        }
        ClientBoxCache.replaceAll(boxes);
    }

    public static void handleEditorData(EditorDataPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new LootEditorScreen(packet.tables(), packet.available(), packet.focus()));
    }
}
