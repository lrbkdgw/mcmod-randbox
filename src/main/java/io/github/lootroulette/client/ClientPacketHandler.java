package io.github.lootroulette.client;

import io.github.lootroulette.network.OpenRoulettePacket;
import net.minecraft.client.Minecraft;

public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    public static void open(OpenRoulettePacket packet) {
        Minecraft.getInstance().setScreen(new RouletteScreen(packet));
    }
}
