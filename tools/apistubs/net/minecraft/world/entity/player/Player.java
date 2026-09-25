package net.minecraft.world.entity.player;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public class Player extends Entity {
    public float getLuck() { return 0; }
    public boolean drop(ItemStack stack, boolean includeThrower) { return true; }
    public boolean hasPermissions(int level) { return false; }
}
