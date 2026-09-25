package com.randombox.net;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.randombox.loot.BoxLootTable;
import com.randombox.loot.CustomLootStore;
import com.randombox.loot.LootExtractor;
import com.randombox.loot.LootRoller;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.network.NetworkEvent;

/**
 * Client -> server: open the loot table editor. Optionally asks for one specific vanilla table so
 * it can be imported into the editor.
 */
public class RequestEditorPacket {
    private final ResourceLocation importId;

    public RequestEditorPacket(ResourceLocation importId) {
        this.importId = importId;
    }

    public static void encode(RequestEditorPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.importId != null);
        if (packet.importId != null) {
            buf.writeResourceLocation(packet.importId);
        }
    }

    public static RequestEditorPacket decode(FriendlyByteBuf buf) {
        return new RequestEditorPacket(buf.readBoolean() ? buf.readResourceLocation() : null);
    }

    public static void handle(RequestEditorPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !player.hasPermissions(2)) {
                return;
            }
            ServerLevel level = player.serverLevel();
            List<BoxLootTable> tables = new ArrayList<>(CustomLootStore.all());
            List<ResourceLocation> available = new ArrayList<>(level.getServer().getLootData()
                    .getKeys(net.minecraft.world.level.storage.loot.LootDataType.TABLE));

            if (packet.importId != null && !CustomLootStore.has(packet.importId)) {
                LootContext lootContext = LootRoller.createContext(level, BlockPos.ZERO, player);
                LootTable vanilla = level.getServer().getLootData().getLootTable(packet.importId);
                BoxLootTable imported = LootExtractor.extract(packet.importId, vanilla, lootContext);
                if (!imported.isEmpty()) {
                    tables.add(imported);
                }
            }
            RBNetwork.toPlayer(player, new EditorDataPacket(tables, available));
        });
        ctx.setPacketHandled(true);
    }
}
