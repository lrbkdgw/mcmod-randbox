package com.randombox.net;

import com.randombox.RandomBoxMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** All packets of the mod. */
public final class RBNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(RandomBoxMod.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private RBNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, StartReelPacket.class, StartReelPacket::encode, StartReelPacket::decode,
                StartReelPacket::handle);
        CHANNEL.registerMessage(id++, ReelFinishedPacket.class, ReelFinishedPacket::encode, ReelFinishedPacket::decode,
                ReelFinishedPacket::handle);
        CHANNEL.registerMessage(id++, CancelReelPacket.class, CancelReelPacket::encode, CancelReelPacket::decode,
                CancelReelPacket::handle);
        CHANNEL.registerMessage(id++, SyncBoxesPacket.class, SyncBoxesPacket::encode, SyncBoxesPacket::decode,
                SyncBoxesPacket::handle);
        CHANNEL.registerMessage(id++, RequestEditorPacket.class, RequestEditorPacket::encode, RequestEditorPacket::decode,
                RequestEditorPacket::handle);
        CHANNEL.registerMessage(id++, EditorDataPacket.class, EditorDataPacket::encode, EditorDataPacket::decode,
                EditorDataPacket::handle);
        CHANNEL.registerMessage(id++, SaveTablePacket.class, SaveTablePacket::encode, SaveTablePacket::decode,
                SaveTablePacket::handle);
    }

    public static void toPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void toServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}
