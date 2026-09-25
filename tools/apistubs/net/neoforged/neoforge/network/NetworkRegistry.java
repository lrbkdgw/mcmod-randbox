package net.neoforged.neoforge.network;

import java.util.function.Predicate;
import java.util.function.Supplier;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.simple.SimpleChannel;

public class NetworkRegistry {
    public static class ChannelBuilder {
        public static ChannelBuilder named(ResourceLocation name) { return null; }
        public ChannelBuilder networkProtocolVersion(Supplier<String> version) { return this; }
        public ChannelBuilder clientAcceptedVersions(Predicate<String> predicate) { return this; }
        public ChannelBuilder serverAcceptedVersions(Predicate<String> predicate) { return this; }
        public SimpleChannel simpleChannel() { return null; }
    }
}
