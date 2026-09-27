package com.randombox.item;

import com.randombox.RandomBoxMod;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Item registration for Random Box. */
public final class RandomBoxItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, RandomBoxMod.MOD_ID);

    public static final RegistryObject<Item> WARDEN_TENTACLE = ITEMS.register("warden_tentacle",
            () -> new WardenTentacleItem(new Item.Properties().durability(6).rarity(Rarity.RARE)));

    private RandomBoxItems() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
