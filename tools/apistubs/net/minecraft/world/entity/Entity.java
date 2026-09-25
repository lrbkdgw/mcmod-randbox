package net.minecraft.world.entity;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;

public class Entity {
    public UUID getUUID() { return null; }
    public BlockPos blockPosition() { return null; }
    public boolean isSpectator() { return false; }
    public boolean isShiftKeyDown() { return false; }
    public void sendSystemMessage(Component message) {}
    public void displayClientMessage(Component message, boolean actionBar) {}
    public void playSound(SoundEvent sound, float volume, float pitch) {}
}
