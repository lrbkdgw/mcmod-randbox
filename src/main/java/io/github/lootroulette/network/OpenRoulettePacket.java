package io.github.lootroulette.network;

import io.github.lootroulette.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record OpenRoulettePacket(int rarity, int durationTicks, List<List<ItemStack>> reels) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(rarity);
        buffer.writeVarInt(durationTicks);
        buffer.writeVarInt(reels.size());
        for (List<ItemStack> reel : reels) {
            buffer.writeVarInt(reel.size());
            for (ItemStack stack : reel) buffer.writeItem(stack);
        }
    }

    public static OpenRoulettePacket decode(FriendlyByteBuf buffer) {
        int rarity = buffer.readVarInt();
        int duration = buffer.readVarInt();
        int columns = Math.min(9, buffer.readVarInt());
        List<List<ItemStack>> reels = new ArrayList<>(columns);
        for (int column = 0; column < columns; column++) {
            int size = Math.min(32, buffer.readVarInt());
            List<ItemStack> reel = new ArrayList<>(size);
            for (int i = 0; i < size; i++) reel.add(buffer.readItem());
            reels.add(reel);
        }
        return new OpenRoulettePacket(rarity, duration, reels);
    }

    public static void handle(OpenRoulettePacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.open(packet)));
        context.get().setPacketHandled(true);
    }
}
