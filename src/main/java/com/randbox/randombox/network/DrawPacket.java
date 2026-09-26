package com.randbox.randombox.network;

import com.randbox.randombox.client.DrawScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;import java.util.function.Supplier;

public record DrawPacket(BlockPos pos,int tier,List<ItemStack> items,int duration) {
 public static void encode(DrawPacket p,FriendlyByteBuf b){b.writeBlockPos(p.pos);b.writeVarInt(p.tier);b.writeVarInt(p.duration);b.writeCollection(p.items,FriendlyByteBuf::writeItem);}
 public static DrawPacket decode(FriendlyByteBuf b){BlockPos p=b.readBlockPos();int t=b.readVarInt(),d=b.readVarInt();return new DrawPacket(p,t,b.readList(FriendlyByteBuf::readItem),d);}
 public static void handle(DrawPacket p,Supplier<NetworkEvent.Context> s){s.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->DrawScreen.open(p)));s.get().setPacketHandled(true);}
}
