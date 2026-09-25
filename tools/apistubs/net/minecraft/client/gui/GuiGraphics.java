package net.minecraft.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class GuiGraphics {
    public void fill(int x1, int y1, int x2, int y2, int color) {}
    public void renderOutline(int x, int y, int width, int height, int color) {}
    public void renderItem(ItemStack stack, int x, int y) {}
    public void renderItemDecorations(Font font, ItemStack stack, int x, int y) {}
    public void drawString(Font font, Component text, int x, int y, int color) {}
    public void drawString(Font font, String text, int x, int y, int color) {}
    public void drawCenteredString(Font font, Component text, int x, int y, int color) {}
}
