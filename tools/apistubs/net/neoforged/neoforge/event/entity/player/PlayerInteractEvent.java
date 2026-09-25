package net.neoforged.neoforge.event.entity.player;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;

public class PlayerInteractEvent extends Event {
    public Player getEntity() { return null; }
    public Level getLevel() { return null; }
    public BlockPos getPos() { return null; }

    public static class RightClickBlock extends PlayerInteractEvent {
        public void setCancellationResult(InteractionResult result) {}
    }
}
