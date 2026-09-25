package com.randombox.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

/** Registers the client only listeners. */
public final class ClientSetup {
    private ClientSetup() {
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.register(new ClientEvents());
    }
}
