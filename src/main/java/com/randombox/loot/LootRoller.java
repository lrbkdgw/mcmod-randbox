package com.randombox.loot;

import java.util.ArrayList;
import java.util.List;

import com.randombox.RandomBoxMod;
import com.randombox.Rarity;
import com.randombox.config.RBConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Turns a loot table into the prize list of a box.
 *
 * <ul>
 *   <li>the amount of drawn items only depends on the rarity (3 / 4 / 5 / 6 / 8),</li>
 *   <li>the pool an item is drawn from is chosen with the average item amount of the pool as
 *       weight, so the original loot lists stay recognisable,</li>
 *   <li>inside a pool the entry weight becomes {@code baseWeight * (1 + quality * factor)},</li>
 *   <li>every resulting stack size is multiplied by {@code k / 4} where {@code k} is the number of
 *       pools of the original table.</li>
 * </ul>
 */
public final class LootRoller {
    /** How many items a reel column scrolls through before it stops. */
    public static final int REEL_LENGTH = 40;

    private LootRoller() {
    }

    public static LootContext createContext(ServerLevel level, BlockPos pos, Player player) {
        LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos));
        if (player != null) {
            builder = builder.withLuck(player.getLuck()).withParameter(LootContextParams.THIS_ENTITY, player);
        }
        LootParams params = builder.create(LootContextParamSets.CHEST);
        return new LootContext.Builder(params).create(null);
    }

    /** Custom (GUI edited) table first, vanilla / datapack table otherwise. */
    public static BoxLootTable model(ServerLevel level, ResourceLocation tableId, LootContext context) {
        if (tableId == null) {
            return new BoxLootTable(new ResourceLocation(RandomBoxMod.MOD_ID, "empty"));
        }
        BoxLootTable custom = CustomLootStore.get(tableId);
        if (custom != null && !custom.isEmpty()) {
            return custom.applyItemQuality();
        }
        LootTable vanilla = level.getServer().getLootData().getLootTable(tableId);
        return LootExtractor.extract(tableId, vanilla, context, level.getServer().getLootData())
                .applyItemQuality();
    }

    public static Result roll(ServerLevel level, BlockPos pos, Player player, ResourceLocation tableId, Rarity rarity) {
        LootContext context = createContext(level, pos, player);
        BoxLootTable model = model(level, tableId, context);
        RandomSource random = level.getRandom();
        Result result = new Result();

        int wanted = Math.max(1, RBConfig.itemCount(rarity));

        if (model.isEmpty()) {
            // Nothing we can read from the table: fall back to repeated vanilla rolls, but still
            // hand out exactly the amount of items the rarity asks for.
            LootTable vanilla = level.getServer().getLootData().getLootTable(tableId);
            List<ItemStack> drawn = new ArrayList<>();
            for (int attempt = 0; attempt < 32 && drawn.size() < wanted; attempt++) {
                List<ItemStack> items = vanilla.getRandomItems(createParams(level, pos, player));
                boolean any = false;
                for (ItemStack stack : items) {
                    if (!stack.isEmpty()) {
                        drawn.add(stack);
                        any = true;
                    }
                }
                if (!any && attempt > 4) {
                    break;
                }
            }
            if (drawn.isEmpty()) {
                return result;
            }
            while (drawn.size() > wanted) {
                drawn.remove(random.nextInt(drawn.size()));
            }
            while (drawn.size() < wanted) {
                drawn.add(drawn.get(random.nextInt(drawn.size())).copy());
            }
            result.prizes.addAll(drawn);
            for (ItemStack prize : result.prizes) {
                int index = prizeIndex(random);
                List<ItemStack> reel = new ArrayList<>();
                for (int i = 0; i < REEL_LENGTH; i++) {
                    reel.add(result.prizes.get(random.nextInt(result.prizes.size())).copy());
                }
                reel.set(index, prize.copy());
                result.reels.add(reel);
                result.prizeIndices.add(index);
            }
            return result;
        }

        float countMultiplier = Math.max(0.05F, model.poolCount() / 4.0F);
        int guard = 0;
        while (result.prizes.size() < wanted && guard++ < wanted * 64) {
            ItemStack stack = drawOne(model, rarity, context, random, countMultiplier);
            if (stack != null && !stack.isEmpty()) {
                result.prizes.add(stack);
            }
        }
        // Never hand out less than the rarity promises: pad with copies of what was drawn.
        while (result.prizes.size() < wanted && !result.prizes.isEmpty()) {
            result.prizes.add(result.prizes.get(random.nextInt(result.prizes.size())).copy());
        }
        while (result.prizes.size() > wanted) {
            result.prizes.remove(result.prizes.size() - 1);
        }

        // Every reel scrolls through the items that this table can actually produce, drawn with
        // the same weights as the prize itself, so what flies by mirrors the real chances.
        for (ItemStack prize : result.prizes) {
            List<ItemStack> reel = new ArrayList<>();
            for (int i = 0; i < REEL_LENGTH; i++) {
                ItemStack filler = drawOne(model, rarity, context, random, countMultiplier);
                reel.add(filler == null || filler.isEmpty() ? prize.copy() : filler);
            }
            int index = prizeIndex(random);
            reel.set(index, prize.copy());
            result.reels.add(reel);
            result.prizeIndices.add(index);
        }
        return result;
    }

    private static LootParams createParams(ServerLevel level, BlockPos pos, Player player) {
        LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos));
        if (player != null) {
            builder = builder.withLuck(player.getLuck()).withParameter(LootContextParams.THIS_ENTITY, player);
        }
        return builder.create(LootContextParamSets.CHEST);
    }

    private static ItemStack drawOne(BoxLootTable model, Rarity rarity, LootContext context,
                                     RandomSource random, float countMultiplier) {
        BoxLootTable.Pool pool = pickPool(model, random);
        if (pool == null) {
            return ItemStack.EMPTY;
        }
        BoxLootTable.Entry entry = pickEntry(pool, rarity, random);
        if (entry == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(entry.item());
        if (entry.vanillaFunctions() != null) {
            try {
                stack = entry.vanillaFunctions().apply(stack, context);
            } catch (Exception exception) {
                RandomBoxMod.LOGGER.debug("Loot function failed for {}", entry.item(), exception);
                stack = new ItemStack(entry.item());
            }
        } else {
            int min = Math.min(entry.minCount(), entry.maxCount());
            int max = Math.max(entry.minCount(), entry.maxCount());
            stack.setCount(min + random.nextInt(max - min + 1));
        }
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (RBConfig.scaleStackSizes()) {
            stack.setCount(scaleCount(stack.getCount(), countMultiplier, random, stack.getMaxStackSize()));
        }
        return stack;
    }

    /** Weighted by the average item amount of every pool. */
    private static BoxLootTable.Pool pickPool(BoxLootTable model, RandomSource random) {
        float total = 0.0F;
        for (BoxLootTable.Pool pool : model.pools()) {
            total += pool.expectedItems();
        }
        if (total <= 0.0F) {
            return model.pools().isEmpty() ? null : model.pools().get(random.nextInt(model.pools().size()));
        }
        float roll = random.nextFloat() * total;
        for (BoxLootTable.Pool pool : model.pools()) {
            roll -= pool.expectedItems();
            if (roll <= 0.0F) {
                return pool;
            }
        }
        return model.pools().get(model.pools().size() - 1);
    }

    /** Weighted by {@code baseWeight * (1 + quality * rarityFactor)}. */
    private static BoxLootTable.Entry pickEntry(BoxLootTable.Pool pool, Rarity rarity, RandomSource random) {
        float factor = RBConfig.qualityFactor(rarity);
        float total = 0.0F;
        for (BoxLootTable.Entry entry : pool.entries()) {
            total += entry.adjustedWeight(factor);
        }
        if (total <= 0.0F) {
            return pool.entries().isEmpty() ? null : pool.entries().get(random.nextInt(pool.entries().size()));
        }
        float roll = random.nextFloat() * total;
        for (BoxLootTable.Entry entry : pool.entries()) {
            roll -= entry.adjustedWeight(factor);
            if (roll <= 0.0F) {
                return entry;
            }
        }
        return pool.entries().get(pool.entries().size() - 1);
    }

    /** Multiplies a stack size, rounding the fractional rest randomly. */
    public static int scaleCount(int count, float multiplier, RandomSource random, int maxStackSize) {
        float scaled = count * multiplier;
        int whole = (int) scaled;
        if (random.nextFloat() < scaled - whole) {
            whole++;
        }
        return Math.max(1, Math.min(Math.max(1, maxStackSize), whole));
    }

    /**
     * Where the winning item sits inside a reel. Never the very last entry: a few more items
     * follow it so the strip keeps looking like an endless reel that stopped somewhere.
     */
    private static int prizeIndex(RandomSource random) {
        int tail = 2 + random.nextInt(4);
        return Math.max(1, REEL_LENGTH - 1 - tail);
    }

    /** Prizes plus the item strips the client scrolls through. */
    public static class Result {
        public final List<ItemStack> prizes = new ArrayList<>();
        public final List<List<ItemStack>> reels = new ArrayList<>();
        /** Index of the winning item inside every reel (never the last one). */
        public final List<Integer> prizeIndices = new ArrayList<>();
    }
}
