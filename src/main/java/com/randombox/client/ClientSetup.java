package com.randombox.client;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.MinecraftForge;

/** Registers the client only listeners. */
public final class ClientSetup {
    private ClientSetup() {
    }

    public static void init(IEventBus modBus) {
        MinecraftForge.EVENT_BUS.register(new ClientEvents());
    }
}
