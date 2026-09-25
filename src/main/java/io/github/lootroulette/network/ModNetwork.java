package io.github.lootroulette.network;

import io.github.lootroulette.LootRouletteMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String VERSION = "1";
    private static SimpleChannel channel;

    private ModNetwork() {}

    public static void init() {
        channel = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(LootRouletteMod.MOD_ID, "main"))
                .networkProtocolVersion(() -> VERSION)
                .clientAcceptedVersions(VERSION::equals)
                .serverAcceptedVersions(VERSION::equals)
                .simpleChannel();
        channel.messageBuilder(OpenRoulettePacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenRoulettePacket::encode)
                .decoder(OpenRoulettePacket::decode)
                .consumerMainThread(OpenRoulettePacket::handle)
                .add();
    }

    public static void send(ServerPlayer player, OpenRoulettePacket packet) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
