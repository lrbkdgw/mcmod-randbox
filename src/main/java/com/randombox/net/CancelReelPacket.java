package com.randombox.net;

import java.util.function.Supplier;

import com.randombox.event.ReelManager;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client -> server: the player pressed Esc, the lottery is aborted. No prize is handed out and the
 * box keeps its loot table, so it can be opened again later. Any persisted preview / ascension
 * state of that box is kept.
 */
public class CancelReelPacket {
    private final BlockPos pos;

    public CancelReelPacket(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(CancelReelPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
    }

    public static CancelReelPacket decode(FriendlyByteBuf buf) {
        return new CancelReelPacket(buf.readBlockPos());
    }

    public static void handle(CancelReelPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                ReelManager.cancel(player, packet.pos);
            }
        });
        ctx.setPacketHandled(true);
    }
}
