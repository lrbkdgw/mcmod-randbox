package net.minecraft.core;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

public interface Registry<T> extends Iterable<T> {
    ResourceLocation getKey(T value);
    T get(ResourceLocation id);
    Iterable<Holder<T>> getTagOrEmpty(TagKey<T> tag);
}
