package net.minecraft.commands;

import java.util.function.Supplier;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class CommandSourceStack implements SharedSuggestionProvider {
    public boolean hasPermission(int level) { return false; }
    public ServerLevel getLevel() { return null; }
    public ServerPlayer getPlayer() { return null; }
    public MinecraftServer getServer() { return null; }
    public void sendSuccess(Supplier<Component> message, boolean broadcast) {}
    public void sendFailure(Component message) {}
}
