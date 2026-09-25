package net.neoforged.fml;

import java.util.function.Supplier;

import net.neoforged.api.distmarker.Dist;

public class DistExecutor {
    public static void unsafeRunWhenOn(Dist dist, Supplier<Runnable> toRun) {}
}
