package net.minecraftforge.api.distmarker;

/** Part of the (closed source) distmarker artifact. */
public enum Dist {
    CLIENT, DEDICATED_SERVER;

    public boolean isClient() { throw new RuntimeException("stub"); }
    public boolean isDedicatedServer() { throw new RuntimeException("stub"); }
}
