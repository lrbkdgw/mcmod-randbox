package net.neoforged.neoforge.network;

import java.util.function.Supplier;

import net.minecraft.server.level.ServerPlayer;

public class PacketDistributor<T> {
    public static final PacketDistributor<ServerPlayer> PLAYER = new PacketDistributor<>();

    public PacketTarget with(Supplier<T> supplier) { return null; }

    public static class PacketTarget {
    }
}
