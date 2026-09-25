package net.minecraft.nbt;

public class CompoundTag implements Tag {
    public void putInt(String key, int value) {}
    public int getInt(String key) { return 0; }
    public void putBoolean(String key, boolean value) {}
    public boolean getBoolean(String key) { return false; }
    public void putString(String key, String value) {}
    public String getString(String key) { return null; }
    public boolean contains(String key) { return false; }
    public void put(String key, Tag value) {}
    public ListTag getList(String key, int type) { return null; }
    public CompoundTag getCompound(String key) { return null; }
}
