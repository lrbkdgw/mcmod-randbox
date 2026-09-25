package net.minecraft.commands.arguments;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;

public class ResourceLocationArgument implements ArgumentType<ResourceLocation> {
    public static ResourceLocationArgument id() { return null; }
    public static ResourceLocation getId(CommandContext<CommandSourceStack> context, String name) { return null; }
}
