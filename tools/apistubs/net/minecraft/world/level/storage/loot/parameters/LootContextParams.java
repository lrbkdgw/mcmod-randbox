package net.minecraft.world.level.storage.loot.parameters;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class LootContextParams {
    public static final LootContextParam<Vec3> ORIGIN = new LootContextParam<>();
    public static final LootContextParam<Entity> THIS_ENTITY = new LootContextParam<>();
}
