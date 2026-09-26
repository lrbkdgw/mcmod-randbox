package com.randbox.randombox;

import com.mojang.logging.LogUtils;
import com.randbox.randombox.loot.BoxLogic;
import com.randbox.randombox.network.Net;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(RandomBox.ID)
public final class RandomBox {
    public static final String ID = "randombox";
    public static final Logger LOG = LogUtils.getLogger();
    public RandomBox() {
        Net.init();
        MinecraftForge.EVENT_BUS.register(BoxLogic.class);
        MinecraftForge.EVENT_BUS.register(this);
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent e) { Commands.register(e.getDispatcher()); }
}
