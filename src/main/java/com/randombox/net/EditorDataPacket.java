package com.randombox.net;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.randombox.client.ClientPacketHandler;
import com.randombox.loot.BoxLootTable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: everything the loot table editor needs. */
public class EditorDataPacket {
    private final List<BoxLootTable> tables;
    private final List<ResourceLocation> available;
    /** Table the editor should select right away (freshly imported one), may be null. */
    private final ResourceLocation focus;
    /** True when the quality column is controlled by the fixed item qualities. */
    private final boolean qualityLocked;

    public EditorDataPacket(List<BoxLootTable> tables, List<ResourceLocation> available) {
        this(tables, available, null, com.randombox.config.RBConfig.overrideQuality());
    }

    public EditorDataPacket(List<BoxLootTable> tables, List<ResourceLocation> available, ResourceLocation focus) {
        this(tables, available, focus, com.randombox.config.RBConfig.overrideQuality());
    }

    public EditorDataPacket(List<BoxLootTable> tables, List<ResourceLocation> available,
                            ResourceLocation focus, boolean qualityLocked) {
        this.tables = tables;
        this.available = available;
        this.focus = focus;
        this.qualityLocked = qualityLocked;
    }

    public boolean qualityLocked() {
        return this.qualityLocked;
    }

    public ResourceLocation focus() {
        return this.focus;
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
        buf.writeBoolean(packet.focus != null);
        if (packet.focus != null) {
            buf.writeResourceLocation(packet.focus);
        }
        buf.writeBoolean(packet.qualityLocked);
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
        ResourceLocation focus = buf.readBoolean() ? buf.readResourceLocation() : null;
        boolean qualityLocked = buf.readBoolean();
        return new EditorDataPacket(tables, available, focus, qualityLocked);
    }

    public static void handle(EditorDataPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.handleEditorData(packet)));
        ctx.setPacketHandled(true);
    }
}
