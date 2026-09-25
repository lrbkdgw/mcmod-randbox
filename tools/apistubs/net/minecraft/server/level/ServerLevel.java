package net.minecraft.server.level;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.DimensionDataStorage;

public class ServerLevel extends Level {
    public MinecraftServer getServer() { return null; }
    public DimensionDataStorage getDataStorage() { return null; }
}
