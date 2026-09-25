package net.minecraft.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderBuffers;

public class Minecraft {
    public ClientLevel level;
    public LocalPlayer player;
    public Font font;

    public static Minecraft getInstance() { return null; }
    public void setScreen(Screen screen) {}
    public boolean isPaused() { return false; }
    public RenderBuffers renderBuffers() { return null; }
}
