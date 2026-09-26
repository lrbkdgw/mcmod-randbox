package com.randombox.net;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.randombox.Rarity;
import com.randombox.client.ClientPacketHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: show the prizes fixed by a Warden Tentacle preview. */
public class PreviewLootPacket {
    private final BlockPos pos;
    private final Rarity rarity;
    private final List<ItemStack> prizes;
    private final boolean rerollAvailable;

    public PreviewLootPacket(BlockPos pos, Rarity rarity, List<ItemStack> prizes, boolean rerollAvailable) {
        this.pos = pos;
        this.rarity = rarity;
        this.prizes = prizes;
        this.rerollAvailable = rerollAvailable;
    }

    public BlockPos pos() {
        return this.pos;
    }

    public Rarity rarity() {
        return this.rarity;
    }

    public List<ItemStack> prizes() {
        return this.prizes;
    }

    public boolean rerollAvailable() {
        return this.rerollAvailable;
    }

    public static void encode(PreviewLootPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeByte(packet.rarity.id());
        buf.writeVarInt(packet.prizes.size());
        for (ItemStack stack : packet.prizes) {
            buf.writeItem(stack);
        }
        buf.writeBoolean(packet.rerollAvailable);
    }

    public static PreviewLootPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Rarity rarity = Rarity.byId(buf.readByte());
        int count = buf.readVarInt();
        List<ItemStack> prizes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            prizes.add(buf.readItem());
        }
        boolean rerollAvailable = buf.readBoolean();
        return new PreviewLootPacket(pos, rarity, prizes, rerollAvailable);
    }

    public static void handle(PreviewLootPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.handlePreviewLoot(packet)));
        ctx.setPacketHandled(true);
    }
}
