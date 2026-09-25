package net.minecraft.client.gui.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public abstract class Screen {
    protected Minecraft minecraft;
    protected Font font;
    protected Component title;
    public int width;
    public int height;

    protected Screen(Component title) {}

    protected void init() {}

    public void init(Minecraft minecraft, int width, int height) {}

    protected void rebuildWidgets() {}

    protected void clearWidgets() {}

    protected <T> T addRenderableWidget(T widget) { return widget; }

    public void renderBackground(GuiGraphics graphics) {}

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    public void tick() {}

    public void onClose() {}

    public boolean isPauseScreen() { return true; }

    public boolean shouldCloseOnEsc() { return true; }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }

    public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
}
