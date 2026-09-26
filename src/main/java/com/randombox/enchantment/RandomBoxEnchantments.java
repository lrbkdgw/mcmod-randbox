package com.randombox.enchantment;

import com.randombox.RandomBoxMod;
import com.randombox.Rarity;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registration and gameplay helpers for Random Box enchantments. */
public final class RandomBoxEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, RandomBoxMod.MOD_ID);

    /** Helmet, same vanilla rarity weight as Fortune, increases the beam render distance. */
    public static final RegistryObject<Enchantment> TREASURE_SENSE = ENCHANTMENTS.register("treasure_sense",
            () -> new SlotEnchantment(Enchantment.Rarity.RARE, EnchantmentCategory.ARMOR_HEAD,
                    EquipmentSlot.HEAD, 3, false, true, true, 15, 9, 50));

    /** Boots, same vanilla rarity weight as Protection, shortens the lottery animation. */
    public static final RegistryObject<Enchantment> QUICK_TREASURE = ENCHANTMENTS.register("quick_treasure",
            () -> new SlotEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_FEET,
                    EquipmentSlot.FEET, 4, false, true, true, 1, 11, 11));

    /** Leggings treasure enchantment, found only through the mod's Ancient City special loot. */
    public static final RegistryObject<Enchantment> TREASURE_DEEP_DIG = ENCHANTMENTS.register("treasure_deep_dig",
            () -> new SlotEnchantment(Enchantment.Rarity.RARE, EnchantmentCategory.ARMOR_LEGS,
                    EquipmentSlot.LEGS, 2, true, false, false, 25, 25, 50));

    /** Chestplate treasure enchantment, found only through the mod's End City treasure special loot. */
    public static final RegistryObject<Enchantment> TREASURE_ASCENSION = ENCHANTMENTS.register("treasure_ascension",
            () -> new SlotEnchantment(Enchantment.Rarity.VERY_RARE, EnchantmentCategory.ARMOR_CHEST,
                    EquipmentSlot.CHEST, 1, true, false, false, 30, 0, 50));

    private RandomBoxEnchantments() {
    }

    public static void register(IEventBus bus) {
        ENCHANTMENTS.register(bus);
    }

    /** Beam distance multiplier from Treasure Sense: 150% / 200% / 250%. */
    public static float beamDistanceMultiplier(Player player) {
        int level = treasureSenseLevel(player);
        return level <= 0 ? 1.0F : 1.0F + 0.5F * Math.min(3, level);
    }

    public static int effectiveBeamRadius(Player player, int baseRadius) {
        return Math.max(1, (int) Math.ceil(baseRadius * beamDistanceMultiplier(player)));
    }

    /** Lottery time multiplier from Quick Treasure: 80% / 60% / 40% / 20%. */
    public static float openingTimeMultiplier(Player player) {
        int level = quickTreasureLevel(player);
        if (level <= 0) {
            return 1.0F;
        }
        return Math.max(0.2F, 1.0F - 0.2F * Math.min(4, level));
    }

    /** Extra prize columns/items from Treasure Deep Dig: +1 / +2. */
    public static int bonusItemCount(Player player) {
        return Math.max(0, Math.min(2, deepDigLevel(player)));
    }

    /** 15% chance to raise a non-mythic box by one rarity while Treasure Ascension is worn. */
    public static Rarity maybeAscend(Rarity rarity, Player player, RandomSource random) {
        if (rarity == null || rarity == Rarity.MYTHIC || treasureAscensionLevel(player) <= 0) {
            return rarity;
        }
        if (random.nextFloat() >= 0.15F) {
            return rarity;
        }
        return Rarity.byId(rarity.id() + 1);
    }

    public static ItemStack enchantedBook(Enchantment enchantment, int level) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantedBookItem.addEnchantment(book, new EnchantmentInstance(enchantment, Math.max(1, level)));
        return book;
    }

    private static int treasureSenseLevel(Player player) {
        return level(player, EquipmentSlot.HEAD, TREASURE_SENSE);
    }

    private static int quickTreasureLevel(Player player) {
        return level(player, EquipmentSlot.FEET, QUICK_TREASURE);
    }

    private static int deepDigLevel(Player player) {
        return level(player, EquipmentSlot.LEGS, TREASURE_DEEP_DIG);
    }

    private static int treasureAscensionLevel(Player player) {
        return level(player, EquipmentSlot.CHEST, TREASURE_ASCENSION);
    }

    private static int level(Player player, EquipmentSlot slot, RegistryObject<Enchantment> enchantment) {
        if (player == null || !enchantment.isPresent()) {
            return 0;
        }
        ItemStack stack = player.getItemBySlot(slot);
        return stack.isEmpty() ? 0 : EnchantmentHelper.getItemEnchantmentLevel(enchantment.get(), stack);
    }

    private static class SlotEnchantment extends Enchantment {
        private final int maxLevel;
        private final boolean treasureOnly;
        private final boolean tradeable;
        private final boolean discoverable;
        private final int minBase;
        private final int minPerLevel;
        private final int maxRange;

        SlotEnchantment(Enchantment.Rarity rarity, EnchantmentCategory category, EquipmentSlot slot, int maxLevel,
                        boolean treasureOnly, boolean tradeable, boolean discoverable,
                        int minBase, int minPerLevel, int maxRange) {
            super(rarity, category, new EquipmentSlot[] {slot});
            this.maxLevel = maxLevel;
            this.treasureOnly = treasureOnly;
            this.tradeable = tradeable;
            this.discoverable = discoverable;
            this.minBase = minBase;
            this.minPerLevel = minPerLevel;
            this.maxRange = maxRange;
        }

        @Override
        public int getMinCost(int level) {
            return this.minBase + (Math.max(1, level) - 1) * this.minPerLevel;
        }

        @Override
        public int getMaxCost(int level) {
            return this.getMinCost(level) + this.maxRange;
        }

        @Override
        public int getMaxLevel() {
            return this.maxLevel;
        }

        @Override
        public boolean isTreasureOnly() {
            return this.treasureOnly;
        }

        @Override
        public boolean isTradeable() {
            return this.tradeable;
        }

        @Override
        public boolean isDiscoverable() {
            return this.discoverable;
        }
    }
}
