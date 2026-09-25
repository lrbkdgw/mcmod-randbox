package net.minecraft.world;

import net.minecraft.world.item.ItemStack;

public interface Container {
    int getContainerSize();
    ItemStack getItem(int slot);
    void setItem(int slot, ItemStack stack);
}
