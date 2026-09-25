package net.minecraft.nbt;

public class ListTag implements Tag {
    public boolean add(Tag tag) { return true; }
    public int size() { return 0; }
    public CompoundTag getCompound(int index) { return null; }
}
