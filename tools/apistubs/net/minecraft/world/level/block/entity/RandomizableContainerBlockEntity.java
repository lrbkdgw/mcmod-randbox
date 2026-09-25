package net.minecraft.world.level.block.entity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;

public abstract class RandomizableContainerBlockEntity extends BlockEntity implements Container, MenuProvider {
    public void setLootTable(ResourceLocation lootTable, long seed) {}
    public int getContainerSize() { return 27; }
    public ItemStack getItem(int slot) { return null; }
    public void setItem(int slot, ItemStack stack) {}
}
