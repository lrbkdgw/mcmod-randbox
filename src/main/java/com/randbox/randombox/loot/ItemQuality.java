package com.randbox.randombox.loot;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/** Fixed, deterministic item quality. Datapacks can affect names, never this assignment. */
public final class ItemQuality {
    private ItemQuality() {}
    public static int of(ItemStack stack) {
        Rarity rarity=stack.getRarity();
        if (rarity==Rarity.EPIC) return 4;
        if (rarity==Rarity.RARE) return 3;
        if (rarity==Rarity.UNCOMMON) return 2;
        return stack.isEnchanted()?2:1;
    }
}
