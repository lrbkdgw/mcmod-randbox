package com.randombox.net;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.randombox.Rarity;
import com.randombox.client.ClientPacketHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.DistExecutor;
import net.neoforged.neoforge.network.NetworkEvent;

/** Server -> client: start the unskippable lottery animation. */
public class StartReelPacket {
    private final BlockPos pos;
    private final Rarity rarity;
    private final List<List<ItemStack>> reels;
    private final List<Float> durations;

    public StartReelPacket(BlockPos pos, Rarity rarity, List<List<ItemStack>> reels, List<Float> durations) {
        this.pos = pos;
        this.rarity = rarity;
        this.reels = reels;
        this.durations = durations;
    }

    public BlockPos pos() {
        return this.pos;
    }

    public Rarity rarity() {
        return this.rarity;
    }

    public List<List<ItemStack>> reels() {
        return this.reels;
    }

    public List<Float> durations() {
        return this.durations;
    }

    public static void encode(StartReelPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeByte(packet.rarity.id());
        buf.writeVarInt(packet.reels.size());
        for (List<ItemStack> reel : packet.reels) {
            buf.writeVarInt(reel.size());
            for (ItemStack stack : reel) {
                buf.writeItem(stack);
            }
        }
        buf.writeVarInt(packet.durations.size());
        for (Float duration : packet.durations) {
            buf.writeFloat(duration);
        }
    }

    public static StartReelPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Rarity rarity = Rarity.byId(buf.readByte());
        int reelCount = buf.readVarInt();
        List<List<ItemStack>> reels = new ArrayList<>();
        for (int i = 0; i < reelCount; i++) {
            int size = buf.readVarInt();
            List<ItemStack> reel = new ArrayList<>();
            for (int j = 0; j < size; j++) {
                reel.add(buf.readItem());
            }
            reels.add(reel);
        }
        int durationCount = buf.readVarInt();
        List<Float> durations = new ArrayList<>();
        for (int i = 0; i < durationCount; i++) {
            durations.add(buf.readFloat());
        }
        return new StartReelPacket(pos, rarity, reels, durations);
    }

    public static void handle(StartReelPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.handleStartReel(packet)));
        ctx.setPacketHandled(true);
    }
}
