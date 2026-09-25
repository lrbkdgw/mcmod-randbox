package net.neoforged.neoforge.event;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;

public class TickEvent extends Event {
    public enum Phase {
        START, END;
    }

    public Phase phase;

    public static class ServerTickEvent extends TickEvent {
        public MinecraftServer getServer() { return null; }
    }

    public static class ClientTickEvent extends TickEvent {
    }
}
