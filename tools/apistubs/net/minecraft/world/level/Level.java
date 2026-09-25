package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public abstract class Level implements LevelAccessor {
    public boolean isClientSide() { return false; }
    public BlockEntity getBlockEntity(BlockPos pos) { return null; }
    public BlockState getBlockState(BlockPos pos) { return null; }
    public boolean setBlockAndUpdate(BlockPos pos, BlockState state) { return true; }
    public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {}
    public void playSound(Player except, BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch) {}
    public RandomSource getRandom() { return null; }
    public long getGameTime() { return 0; }
    public void addParticle(ParticleOptions particle, double x, double y, double z, double xs, double ys, double zs) {}
}
