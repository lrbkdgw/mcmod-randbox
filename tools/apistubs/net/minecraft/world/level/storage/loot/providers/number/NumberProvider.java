package net.minecraft.world.level.storage.loot.providers.number;

import net.minecraft.world.level.storage.loot.LootContext;

public interface NumberProvider {
    int getInt(LootContext context);
    float getFloat(LootContext context);
}
