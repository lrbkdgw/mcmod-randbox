package net.minecraft.network;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class FriendlyByteBuf {
    public void writeVarInt(int value) {}
    public int readVarInt() { return 0; }
    public void writeFloat(float value) {}
    public float readFloat() { return 0; }
    public void writeByte(int value) {}
    public byte readByte() { return 0; }
    public void writeBoolean(boolean value) {}
    public boolean readBoolean() { return false; }
    public void writeBlockPos(BlockPos pos) {}
    public BlockPos readBlockPos() { return null; }
    public void writeResourceLocation(ResourceLocation id) {}
    public ResourceLocation readResourceLocation() { return null; }
    public void writeItem(ItemStack stack) {}
    public ItemStack readItem() { return null; }
    public void writeUtf(String value) {}
    public String readUtf() { return null; }
}
