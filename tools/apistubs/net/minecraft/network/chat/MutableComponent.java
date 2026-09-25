package net.minecraft.network.chat;

import net.minecraft.ChatFormatting;

public class MutableComponent implements Component {
    public MutableComponent withStyle(ChatFormatting format) { return this; }
    public MutableComponent append(Component other) { return this; }
}
