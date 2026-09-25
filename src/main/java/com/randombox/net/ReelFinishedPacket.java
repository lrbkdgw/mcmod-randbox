package com.randombox.net;

import java.util.function.Supplier;

import com.randombox.event.ReelManager;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.NetworkEvent;

/** Client -> server: the animation finished, hand out the prizes and open the container. */
public class ReelFinishedPacket {
    private final BlockPos pos;

    public ReelFinishedPacket(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(ReelFinishedPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
    }

    public static ReelFinishedPacket decode(FriendlyByteBuf buf) {
        return new ReelFinishedPacket(buf.readBlockPos());
    }

    public static void handle(ReelFinishedPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                ReelManager.finish(player, packet.pos);
            }
        });
        ctx.setPacketHandled(true);
    }
}
