package com.randombox.net;

import java.util.function.Supplier;

import com.randombox.loot.BoxLootTable;
import com.randombox.loot.CustomLootStore;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.NetworkEvent;

/** Client -> server: store (or delete) a loot table edited in the GUI. */
public class SaveTablePacket {
    private final BoxLootTable table;
    private final boolean delete;

    public SaveTablePacket(BoxLootTable table, boolean delete) {
        this.table = table;
        this.delete = delete;
    }

    public static void encode(SaveTablePacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.delete);
        packet.table.write(buf);
    }

    public static SaveTablePacket decode(FriendlyByteBuf buf) {
        boolean delete = buf.readBoolean();
        return new SaveTablePacket(BoxLootTable.read(buf), delete);
    }

    public static void handle(SaveTablePacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !player.hasPermissions(2)) {
                return;
            }
            if (packet.delete) {
                CustomLootStore.remove(packet.table.id());
                player.sendSystemMessage(Component.translatable("randombox.editor.deleted", packet.table.id().toString()));
            } else {
                CustomLootStore.put(packet.table);
                player.sendSystemMessage(Component.translatable("randombox.editor.saved", packet.table.id().toString()));
            }
        });
        ctx.setPacketHandled(true);
    }
}
