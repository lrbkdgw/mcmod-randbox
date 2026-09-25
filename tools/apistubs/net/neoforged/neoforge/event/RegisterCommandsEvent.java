package net.neoforged.neoforge.event;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.neoforged.bus.api.Event;

public class RegisterCommandsEvent extends Event {
    public CommandDispatcher<CommandSourceStack> getDispatcher() { return null; }
}
