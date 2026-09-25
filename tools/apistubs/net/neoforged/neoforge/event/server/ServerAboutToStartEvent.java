package net.neoforged.neoforge.event.server;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;

public class ServerAboutToStartEvent extends Event {
    public MinecraftServer getServer() { return null; }
}
