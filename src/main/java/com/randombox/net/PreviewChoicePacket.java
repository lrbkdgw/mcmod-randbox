package com.randombox.net;

import java.util.function.Supplier;

import com.randombox.event.ReelManager;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: keep or reroll the Warden Tentacle previewed prizes. */
public class PreviewChoicePacket {
    private final BlockPos pos;
    private final boolean reroll;

    public PreviewChoicePacket(BlockPos pos, boolean reroll) {
        this.pos = pos;
        this.reroll = reroll;
    }

    public static void encode(PreviewChoicePacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeBoolean(packet.reroll);
    }

    public static PreviewChoicePacket decode(FriendlyByteBuf buf) {
        return new PreviewChoicePacket(buf.readBlockPos(), buf.readBoolean());
    }

    public static void handle(PreviewChoicePacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                ReelManager.previewChoice(player, packet.pos, packet.reroll);
            }
        });
        ctx.setPacketHandled(true);
    }
}
