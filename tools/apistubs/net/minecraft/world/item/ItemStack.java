package net.minecraft.world.item;

public class ItemStack {
    public static final ItemStack EMPTY = new ItemStack((Item) null);
    public ItemStack(Item item) {}
    public ItemStack(Item item, int count) {}
    public boolean isEmpty() { return false; }
    public ItemStack copy() { return this; }
    public int getCount() { return 0; }
    public void setCount(int count) {}
    public int getMaxStackSize() { return 64; }
    public Item getItem() { return null; }
}
