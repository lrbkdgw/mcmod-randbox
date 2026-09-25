package net.minecraft.util;

public interface RandomSource {
    int nextInt(int bound);
    float nextFloat();
    double nextDouble();
    long nextLong();
}
