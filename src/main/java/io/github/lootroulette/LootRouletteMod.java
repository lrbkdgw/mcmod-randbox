package io.github.lootroulette;

import io.github.lootroulette.network.ModNetwork;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(LootRouletteMod.MOD_ID)
public final class LootRouletteMod {
    public static final String MOD_ID = "lootroulette";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> DEBUG_STICK = ITEMS.register("loot_debug_stick",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public LootRouletteMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
        modBus.addListener(this::addCreativeItems);
        MinecraftForge.EVENT_BUS.register(RouletteManager.class);
        ModNetwork.init();
    }

    private void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(DEBUG_STICK);
        }
    }
}
