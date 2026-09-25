package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;

public class BlockPosArgument implements ArgumentType<Coordinates> {
    public static BlockPosArgument blockPos() { return null; }
    public static BlockPos getLoadedBlockPos(CommandContext<CommandSourceStack> context, String name)
            throws CommandSyntaxException {
        return null;
    }
}
