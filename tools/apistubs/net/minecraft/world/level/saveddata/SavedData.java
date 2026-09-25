package net.minecraft.world.level.saveddata;

import net.minecraft.nbt.CompoundTag;

public abstract class SavedData {
    public abstract CompoundTag save(CompoundTag tag);
    public void setDirty() {}
    public void setDirty(boolean dirty) {}
}
