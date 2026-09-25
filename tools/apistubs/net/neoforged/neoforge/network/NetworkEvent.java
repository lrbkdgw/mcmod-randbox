package net.neoforged.neoforge.network;

import net.minecraft.server.level.ServerPlayer;

public class NetworkEvent {
    public static class Context {
        public void enqueueWork(Runnable runnable) {}
        public void setPacketHandled(boolean handled) {}
        public ServerPlayer getSender() { return null; }
    }
}
