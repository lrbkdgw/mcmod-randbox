package net.minecraft.core;

public class BlockPos extends Vec3i {
    public static final BlockPos ZERO = new BlockPos(0, 0, 0);
    public BlockPos(int x, int y, int z) { super(x, y, z); }
    public BlockPos immutable() { return this; }
}
