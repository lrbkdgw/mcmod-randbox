package net.minecraft.server.level;

import com.mojang.authlib.GameProfile;

import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;

public class ServerPlayer extends Player {
    public ServerLevel serverLevel() { return null; }
    public void openMenu(MenuProvider provider) {}
    public GameProfile getGameProfile() { return null; }
}
