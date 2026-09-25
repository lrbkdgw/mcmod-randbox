package com.randombox.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.randombox.RandomBoxMod;
import com.randombox.Rarity;
import com.randombox.config.RBConfig;
import com.randombox.data.BoxData;
import com.randombox.data.BoxSavedData;
import com.randombox.loot.LootRoller;
import com.randombox.net.RBNetwork;
import com.randombox.net.StartReelPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Keeps track of the running lottery animations. */
public final class ReelManager {
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private ReelManager() {
    }

    /** @return true when a lottery was started and the vanilla interaction has to be cancelled. */
    public static boolean start(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof RandomizableContainerBlockEntity container)) {
            return false;
        }
        ResourceLocation tableId = lootTableOf(container);
        if (tableId == null) {
            return false;
        }
        if (isLocked(level, pos)) {
            player.displayClientMessage(Component.translatable("randombox.message.busy"), true);
            return true;
        }

        BoxSavedData saved = BoxSavedData.get(level);
        RandomSource random = level.getRandom();
        BoxData data = saved.getOrCreate(pos, tableId, random);
        data.setLootTable(tableId);
        Rarity rarity = data.rarity();

        LootRoller.Result rolled = LootRoller.roll(level, pos, player, tableId, rarity);
        if (rolled.prizes.isEmpty()) {
            return false;
        }

        List<Float> durations = new ArrayList<>();
        float total = 0.0F;
        for (int i = 0; i < rolled.prizes.size(); i++) {
            float spread = RBConfig.timeSpread();
            float factor = 1.0F + (random.nextFloat() * 2.0F - 1.0F) * spread;
            float seconds = Math.max(0.1F, RBConfig.secondsPerItem() * factor);
            total += seconds;
            durations.add(seconds);
        }

        long deadline = level.getGameTime() + (long) (total * 20.0F) + 200L;
        PENDING.put(player.getUUID(), new Pending(pos, rarity, rolled.prizes, deadline));

        RBNetwork.toPlayer(player, new StartReelPacket(pos, rarity, rolled.reels, durations));
        level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.6F, 1.0F);
        return true;
    }

    public static void finish(ServerPlayer player, BlockPos pos) {
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null || !pending.pos.equals(pos)) {
            return;
        }
        PENDING.remove(player.getUUID());
        apply(player, pending, true);
    }

    private static void apply(ServerPlayer player, Pending pending, boolean openMenu) {
        ServerLevel level = player.serverLevel();
        BlockEntity blockEntity = level.getBlockEntity(pending.pos);
        if (!(blockEntity instanceof RandomizableContainerBlockEntity container)) {
            return;
        }

        container.setLootTable(null, 0L);
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).isEmpty()) {
                slots.add(i);
            }
        }
        RandomSource random = level.getRandom();
        for (ItemStack prize : pending.prizes) {
            if (slots.isEmpty()) {
                player.drop(prize.copy(), false);
                continue;
            }
            int index = random.nextInt(slots.size());
            container.setItem(slots.remove(index), prize.copy());
        }
        container.setChanged();
        level.sendBlockUpdated(pending.pos, level.getBlockState(pending.pos), level.getBlockState(pending.pos), 3);

        BoxSavedData saved = BoxSavedData.get(level);
        BoxData data = saved.get(pending.pos);
        if (data != null) {
            data.setOpened(true);
            saved.setDirty();
        }

        player.sendSystemMessage(Component.translatable("randombox.message.result",
                Component.translatable(pending.rarity.translationKey()).withStyle(pending.rarity.format()),
                pending.prizes.size()));
        level.playSound(null, pending.pos,
                pending.rarity.id() >= Rarity.LEGENDARY.id() ? SoundEvents.PLAYER_LEVELUP : SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.BLOCKS, 0.8F, 1.0F);

        if (openMenu) {
            BlockState state = level.getBlockState(pending.pos);
            MenuProvider provider = state.getMenuProvider(level, pending.pos);
            if (provider != null) {
                player.openMenu(provider);
            }
        }
    }

    /** Applies the loot of animations whose client never answered (disconnect, crash, ...). */
    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, Pending> entry : PENDING.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                expired.add(entry.getKey());
                continue;
            }
            if (player.serverLevel().getGameTime() > entry.getValue().deadline) {
                expired.add(entry.getKey());
                RandomBoxMod.LOGGER.debug("Lottery of {} timed out, handing out the loot", player.getGameProfile().getName());
                apply(player, entry.getValue(), false);
            }
        }
        for (UUID id : expired) {
            PENDING.remove(id);
        }
    }

    public static boolean isLocked(ServerLevel level, BlockPos pos) {
        for (Pending pending : PENDING.values()) {
            if (pending.pos.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    public static ResourceLocation lootTableOf(RandomizableContainerBlockEntity container) {
        CompoundTag tag = container.saveWithoutMetadata();
        if (tag.contains("LootTable")) {
            String id = tag.getString("LootTable");
            if (!id.isEmpty()) {
                return new ResourceLocation(id);
            }
        }
        return null;
    }

    private record Pending(BlockPos pos, Rarity rarity, List<ItemStack> prizes, long deadline) {
    }
}
