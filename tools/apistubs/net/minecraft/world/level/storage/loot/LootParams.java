package net.minecraft.world.level.storage.loot;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;

public class LootParams {
    public static class Builder {
        public Builder(ServerLevel level) {}
        public <T> Builder withParameter(LootContextParam<T> param, T value) { return this; }
        public Builder withLuck(float luck) { return this; }
        public LootParams create(LootContextParamSet paramSet) { return null; }
    }
}
