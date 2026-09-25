package net.minecraft.world.level.storage;

import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

public class DimensionDataStorage {
    public <T extends SavedData> T computeIfAbsent(Function<CompoundTag, T> loader, Supplier<T> factory, String name) {
        return null;
    }
}
