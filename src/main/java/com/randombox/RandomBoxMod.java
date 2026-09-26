package com.randombox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.randombox.client.ClientSetup;
import com.randombox.event.BoxEvents;
import com.randombox.net.RBNetwork;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.common.MinecraftForge;

/** Mod entry point. */
@Mod(RandomBoxMod.MOD_ID)
public class RandomBoxMod {
    public static final String MOD_ID = "randombox";
    public static final Logger LOGGER = LoggerFactory.getLogger("RandomBox");

    public RandomBoxMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        RBNetwork.register();
        MinecraftForge.EVENT_BUS.register(new BoxEvents());

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.init(modBus));
        LOGGER.info("Random Box loaded: chests are lotteries now.");
    }
}
