package net.neoforged.neoforge.event.level;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.bus.api.Event;

public class ChunkEvent extends Event {
    public LevelAccessor getLevel() { return null; }
    public ChunkAccess getChunk() { return null; }

    public static class Load extends ChunkEvent {
    }
}
