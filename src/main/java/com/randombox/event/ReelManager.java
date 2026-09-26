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
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Keeps track of the running lottery animations. */
public final class ReelManager {
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private ReelManager() {
    }

    /** @return true when a lottery was started and the vanilla interaction has to be cancelled. */
    public static boolean start(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        List<RandomizableContainerBlockEntity> parts = parts(level, pos);
        if (parts.isEmpty()) {
            return false;
        }
        ResourceLocation tableId = null;
        for (RandomizableContainerBlockEntity part : parts) {
            ResourceLocation id = lootTableOf(part);
            if (id != null) {
                tableId = id;
                break;
            }
        }
        if (tableId == null) {
            return false;
        }
        // The same player opening the box again (disconnect, closed client, ...) simply gets the
        // very same lottery again - the result never changes and it can never be re-rolled.
        Pending own = PENDING.get(player.getUUID());
        if (own != null && isSameBox(parts, own.pos)) {
            RBNetwork.toPlayer(player, new StartReelPacket(own.pos, own.rarity, own.reels,
                    own.durations, own.prizeIndices));
            return true;
        }
        boolean locked = false;
        for (RandomizableContainerBlockEntity part : parts) {
            if (isLocked(level, part.getBlockPos())) {
                locked = true;
            }
        }
        if (locked) {
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
        PENDING.put(player.getUUID(), new Pending(pos, rarity, rolled.prizes, deadline,
                rolled.reels, durations, rolled.prizeIndices));

        RBNetwork.toPlayer(player, new StartReelPacket(pos, rarity, rolled.reels, durations, rolled.prizeIndices));
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

    /**
     * The player aborted the lottery with Esc: throw the draw away. Nothing is handed out and the
     * box stays untouched, so opening it again simply starts a new lottery.
     */
    public static void cancel(ServerPlayer player, BlockPos pos) {
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null || !pending.pos.equals(pos)) {
            return;
        }
        PENDING.remove(player.getUUID());
        player.displayClientMessage(Component.translatable("randombox.message.cancelled"), true);
    }

    /** True when one of the halves of the container the player clicked is that position. */
    private static boolean isSameBox(List<RandomizableContainerBlockEntity> parts, BlockPos pos) {
        for (RandomizableContainerBlockEntity part : parts) {
            if (part.getBlockPos().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    private static void apply(ServerPlayer player, Pending pending, boolean openMenu) {
        ServerLevel level = player.serverLevel();
        List<RandomizableContainerBlockEntity> parts = parts(level, pending.pos);
        if (parts.isEmpty()) {
            // The box is gone (broken, exploded, ...) while the animation was running: the prizes
            // were already decided, so they go to the player instead of vanishing.
            for (ItemStack prize : pending.prizes) {
                if (!prize.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(prize.copy());
                }
            }
            return;
        }

        // Clearing the loot table of *every* half of the chest is important: otherwise the second
        // half of a double chest would still unpack its own vanilla loot when the menu opens and
        // the box would contain far more items than the rarity allows.
        List<Slot> slots = new ArrayList<>();
        for (RandomizableContainerBlockEntity part : parts) {
            part.setLootTable(null, 0L);
            for (int i = 0; i < part.getContainerSize(); i++) {
                if (part.getItem(i).isEmpty()) {
                    slots.add(new Slot(part, i));
                }
            }
        }

        RandomSource random = level.getRandom();
        for (ItemStack prize : pending.prizes) {
            if (slots.isEmpty()) {
                player.drop(prize.copy(), false);
                continue;
            }
            Slot slot = slots.remove(random.nextInt(slots.size()));
            slot.container().setItem(slot.index(), prize.copy());
        }

        BoxSavedData saved = BoxSavedData.get(level);
        for (RandomizableContainerBlockEntity part : parts) {
            part.setChanged();
            BlockPos partPos = part.getBlockPos();
            BlockState partState = level.getBlockState(partPos);
            level.sendBlockUpdated(partPos, partState, partState, 3);
            BoxData data = saved.get(partPos);
            if (data != null) {
                data.setOpened(true);
                saved.setDirty();
            }
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

    /** The container itself plus, for a double chest, its other half. */
    public static List<RandomizableContainerBlockEntity> parts(ServerLevel level, BlockPos pos) {
        List<RandomizableContainerBlockEntity> parts = new ArrayList<>();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof RandomizableContainerBlockEntity container)) {
            return parts;
        }
        parts.add(container);
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)
                && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
            if (level.getBlockEntity(other) instanceof RandomizableContainerBlockEntity second) {
                parts.add(second);
            }
        }
        return parts;
    }

    /** True when this block (or the other half of the double chest) still holds a loot table. */
    public static boolean hasLootTable(ServerLevel level, BlockPos pos) {
        for (RandomizableContainerBlockEntity part : parts(level, pos)) {
            if (lootTableOf(part) != null) {
                return true;
            }
        }
        return false;
    }

    private record Slot(RandomizableContainerBlockEntity container, int index) {
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

    private record Pending(BlockPos pos, Rarity rarity, List<ItemStack> prizes, long deadline,
                           List<List<ItemStack>> reels, List<Float> durations,
                           List<Integer> prizeIndices) {
    }
}
