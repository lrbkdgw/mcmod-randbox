package com.randombox.net;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.randombox.client.ClientPacketHandler;
import com.randombox.data.BoxData;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.DistExecutor;
import net.neoforged.neoforge.network.NetworkEvent;

/** Server -> client: the unopened boxes around the player, used for particles and light beams. */
public class SyncBoxesPacket {
    private final List<BlockPos> positions;
    private final List<Integer> rarities;
    private final List<Boolean> opened;

    public SyncBoxesPacket(List<BlockPos> positions, List<Integer> rarities, List<Boolean> opened) {
        this.positions = positions;
        this.rarities = rarities;
        this.opened = opened;
    }

    public static SyncBoxesPacket of(List<BoxData> boxes) {
        List<BlockPos> positions = new ArrayList<>();
        List<Integer> rarities = new ArrayList<>();
        List<Boolean> opened = new ArrayList<>();
        for (BoxData box : boxes) {
            positions.add(box.pos());
            rarities.add(box.rarity().id());
            opened.add(box.opened());
        }
        return new SyncBoxesPacket(positions, rarities, opened);
    }

    public List<BlockPos> positions() {
        return this.positions;
    }

    public List<Integer> rarities() {
        return this.rarities;
    }

    public List<Boolean> opened() {
        return this.opened;
    }

    public static void encode(SyncBoxesPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.positions.size());
        for (int i = 0; i < packet.positions.size(); i++) {
            buf.writeBlockPos(packet.positions.get(i));
            buf.writeByte(packet.rarities.get(i) | (packet.opened.get(i) ? 16 : 0));
        }
    }

    public static SyncBoxesPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<BlockPos> positions = new ArrayList<>();
        List<Integer> rarities = new ArrayList<>();
        List<Boolean> opened = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            positions.add(buf.readBlockPos());
            int packed = buf.readByte();
            rarities.add(packed & 15);
            opened.add((packed & 16) != 0);
        }
        return new SyncBoxesPacket(positions, rarities, opened);
    }

    public static void handle(SyncBoxesPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.handleSyncBoxes(packet)));
        ctx.setPacketHandled(true);
    }
}
