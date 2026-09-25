package com.randombox.net;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.randombox.client.ClientPacketHandler;
import com.randombox.loot.BoxLootTable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.DistExecutor;
import net.neoforged.neoforge.network.NetworkEvent;

/** Server -> client: everything the loot table editor needs. */
public class EditorDataPacket {
    private final List<BoxLootTable> tables;
    private final List<ResourceLocation> available;

    public EditorDataPacket(List<BoxLootTable> tables, List<ResourceLocation> available) {
        this.tables = tables;
        this.available = available;
    }

    public List<BoxLootTable> tables() {
        return this.tables;
    }

    public List<ResourceLocation> available() {
        return this.available;
    }

    public static void encode(EditorDataPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.tables.size());
        for (BoxLootTable table : packet.tables) {
            table.write(buf);
        }
        buf.writeVarInt(packet.available.size());
        for (ResourceLocation id : packet.available) {
            buf.writeResourceLocation(id);
        }
    }

    public static EditorDataPacket decode(FriendlyByteBuf buf) {
        int tableCount = buf.readVarInt();
        List<BoxLootTable> tables = new ArrayList<>();
        for (int i = 0; i < tableCount; i++) {
            tables.add(BoxLootTable.read(buf));
        }
        int availableCount = buf.readVarInt();
        List<ResourceLocation> available = new ArrayList<>();
        for (int i = 0; i < availableCount; i++) {
            available.add(buf.readResourceLocation());
        }
        return new EditorDataPacket(tables, available);
    }

    public static void handle(EditorDataPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.handleEditorData(packet)));
        ctx.setPacketHandled(true);
    }
}
