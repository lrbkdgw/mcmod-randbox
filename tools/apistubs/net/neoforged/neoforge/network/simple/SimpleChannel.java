package net.neoforged.neoforge.network.simple;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.NetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public class SimpleChannel {
    public <MSG> void registerMessage(int index, Class<MSG> type,
                                      BiConsumer<MSG, FriendlyByteBuf> encoder,
                                      Function<FriendlyByteBuf, MSG> decoder,
                                      BiConsumer<MSG, Supplier<NetworkEvent.Context>> handler) {
    }

    public void send(PacketDistributor.PacketTarget target, Object message) {}

    public void sendToServer(Object message) {}
}
