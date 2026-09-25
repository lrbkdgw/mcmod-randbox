package net.minecraft.world.level.storage.loot.entries;

import java.util.function.BiFunction;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;

public abstract class LootPoolSingletonContainer extends LootPoolEntryContainer {
    protected final int weight = 1;
    protected final int quality = 0;
    final BiFunction<ItemStack, LootContext, ItemStack> compositeFunction = null;
}
