package net.minecraft.world.level.chunk;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

public abstract class ChunkAccess {
    public Set<BlockPos> getBlockEntitiesPos() { return null; }
    public BlockEntity getBlockEntity(BlockPos pos) { return null; }
}
