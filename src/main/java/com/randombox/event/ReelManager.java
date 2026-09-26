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
import com.randombox.enchantment.RandomBoxEnchantments;
import com.randombox.data.BoxSavedData;
import com.randombox.loot.LootRoller;
import com.randombox.net.PreviewLootPacket;
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
import net.minecraft.world.InteractionHand;
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
    private static final Map<UUID, PreviewToken> PREVIEWS = new HashMap<>();
    private static final long PREVIEW_TIMEOUT = 600L;

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
        BoxData data = dataForBox(saved, parts, pos, tableId, random);
        data.setLootTable(tableId);
        if (data.hasCachedPrizes()) {
            data.setAscensionFixed(true);
            saved.setDirty();
        }
        Rarity rarity = effectiveRarity(saved, data, player, random);
        mirrorBoxData(saved, parts, data, random);

        LootRoller.Result rolled = data.hasCachedPrizes()
                ? LootRoller.fixed(level, pos, player, tableId, rarity, data.cachedPrizes())
                : LootRoller.roll(level, pos, player, tableId, rarity);
        if (rolled.prizes.isEmpty()) {
            return false;
        }

        List<Float> durations = new ArrayList<>();
        float total = 0.0F;
        float timeMultiplier = RandomBoxEnchantments.openingTimeMultiplier(player);
        for (int i = 0; i < rolled.prizes.size(); i++) {
            float spread = RBConfig.timeSpread();
            float factor = 1.0F + (random.nextFloat() * 2.0F - 1.0F) * spread;
            float seconds = Math.max(0.1F, RBConfig.secondsPerItem() * factor * timeMultiplier);
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

    /** Preview a box with a Warden Tentacle and allow one server-authorised reroll. */
    public static boolean preview(ServerPlayer player, BlockPos pos, InteractionHand hand, ItemStack tool) {
        ServerLevel level = player.serverLevel();
        List<RandomizableContainerBlockEntity> parts = parts(level, pos);
        if (parts.isEmpty()) {
            return false;
        }
        ResourceLocation tableId = firstLootTable(parts);
        if (tableId == null) {
            return false;
        }
        for (RandomizableContainerBlockEntity part : parts) {
            if (isLocked(level, part.getBlockPos())) {
                player.displayClientMessage(Component.translatable("randombox.message.busy"), true);
                return true;
            }
        }

        BoxSavedData saved = BoxSavedData.get(level);
        RandomSource random = level.getRandom();
        BoxData data = dataForBox(saved, parts, pos, tableId, random);
        data.setLootTable(tableId);
        Rarity rarity = effectiveRarity(saved, data, player, random);
        // A preview fixes the seen result for the box. If Treasure Ascension was not worn, that
        // "no ascension" outcome is fixed too, so later ESC/reopen or another player cannot alter it.
        data.setAscensionFixed(true);
        saved.setDirty();
        if (!data.hasCachedPrizes()) {
            LootRoller.Result rolled = LootRoller.roll(level, pos, player, tableId, rarity);
            if (rolled.prizes.isEmpty()) {
                return false;
            }
            data.setCachedPrizes(rolled.prizes);
            saved.setDirty();
        }
        mirrorBoxData(saved, parts, data, random);

        if (!player.getAbilities().instabuild) {
            tool.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        }
        PREVIEWS.put(player.getUUID(), new PreviewToken(pos.immutable(), level.getGameTime() + PREVIEW_TIMEOUT));
        RBNetwork.toPlayer(player, new PreviewLootPacket(pos, rarity, data.cachedPrizes(), true));
        level.playSound(null, pos, SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 0.6F, 1.0F);
        return true;
    }

    /** Client response from the preview screen. */
    public static void previewChoice(ServerPlayer player, BlockPos pos, boolean reroll) {
        PreviewToken token = PREVIEWS.get(player.getUUID());
        if (token == null || !token.pos.equals(pos)) {
            return;
        }
        PREVIEWS.remove(player.getUUID());
        if (!reroll) {
            return;
        }

        ServerLevel level = player.serverLevel();
        List<RandomizableContainerBlockEntity> parts = parts(level, pos);
        if (parts.isEmpty()) {
            return;
        }
        ResourceLocation tableId = firstLootTable(parts);
        if (tableId == null) {
            return;
        }
        BoxSavedData saved = BoxSavedData.get(level);
        RandomSource random = level.getRandom();
        BoxData data = dataForBox(saved, parts, pos, tableId, random);
        data.setLootTable(tableId);
        data.setAscensionFixed(true);
        Rarity rarity = effectiveRarity(saved, data, player, random);
        LootRoller.Result rolled = LootRoller.roll(level, pos, player, tableId, rarity);
        if (rolled.prizes.isEmpty()) {
            return;
        }
        data.setCachedPrizes(rolled.prizes);
        saved.setDirty();
        mirrorBoxData(saved, parts, data, random);
        RBNetwork.toPlayer(player, new PreviewLootPacket(pos, rarity, data.cachedPrizes(), false));
        level.playSound(null, pos, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.35F, 1.4F);
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
     * The player aborted the lottery with Esc: nothing is handed out and the box stays untouched.
     * A Warden Tentacle preview, or the once-per-box Treasure Ascension roll, remains saved.
     */
    public static void cancel(ServerPlayer player, BlockPos pos) {
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null || !pending.pos.equals(pos)) {
            return;
        }
        PENDING.remove(player.getUUID());
        player.displayClientMessage(Component.translatable("randombox.message.cancelled"), true);
    }

    private static ResourceLocation firstLootTable(List<RandomizableContainerBlockEntity> parts) {
        for (RandomizableContainerBlockEntity part : parts) {
            ResourceLocation id = lootTableOf(part);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    private static BoxData dataForBox(BoxSavedData saved, List<RandomizableContainerBlockEntity> parts,
                                      BlockPos clicked, ResourceLocation tableId, RandomSource random) {
        BoxData fallback = saved.getOrCreate(clicked, tableId, random);
        BoxData ascended = null;
        for (RandomizableContainerBlockEntity part : parts) {
            BoxData data = saved.get(part.getBlockPos());
            if (data == null) {
                continue;
            }
            if (data.hasCachedPrizes()) {
                return data;
            }
            if (ascended == null && data.ascensionFixed()) {
                ascended = data;
            }
        }
        return ascended == null ? fallback : ascended;
    }

    private static void mirrorBoxData(BoxSavedData saved, List<RandomizableContainerBlockEntity> parts,
                                      BoxData source, RandomSource random) {
        for (RandomizableContainerBlockEntity part : parts) {
            BoxData target = saved.getOrCreate(part.getBlockPos(), source.lootTable(), random);
            target.setLootTable(source.lootTable());
            target.setRarity(source.rarity());
            target.setAscensionFixed(source.ascensionFixed());
            target.setOpened(source.opened());
            if (source.hasCachedPrizes()) {
                target.setCachedPrizes(source.cachedPrizes());
            } else {
                target.clearCachedPrizes();
            }
        }
        saved.setDirty();
    }

    /** Rolls Treasure Ascension at most once for this box and stores that result immediately. */
    private static Rarity effectiveRarity(BoxSavedData saved, BoxData data, ServerPlayer player, RandomSource random) {
        if (!data.ascensionFixed() && RandomBoxEnchantments.hasTreasureAscension(player)) {
            data.setRarity(RandomBoxEnchantments.maybeAscend(data.rarity(), player, random));
            data.setAscensionFixed(true);
            saved.setDirty();
        }
        return data.rarity();
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
            // The unopened loot container was broken before the animation could apply. Match
            // vanilla unopened loot boxes: no rolled or unrolled loot is dropped or handed out.
            BoxSavedData.get(level).remove(pending.pos);
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
                data.setRarity(pending.rarity);
                data.clearCachedPrizes();
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
        expirePreviewTokens(server);
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

    private static void expirePreviewTokens(MinecraftServer server) {
        if (PREVIEWS.isEmpty()) {
            return;
        }
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, PreviewToken> entry : PREVIEWS.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.serverLevel().getGameTime() > entry.getValue().deadline) {
                expired.add(entry.getKey());
            }
        }
        for (UUID id : expired) {
            PREVIEWS.remove(id);
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

    /** Forget running animations for a loot box that is being broken. */
    public static void cancelForBrokenBox(ServerLevel level, BlockPos pos) {
        List<BlockPos> positions = new ArrayList<>();
        for (RandomizableContainerBlockEntity part : parts(level, pos)) {
            positions.add(part.getBlockPos());
        }
        if (positions.isEmpty()) {
            positions.add(pos.immutable());
        }
        PENDING.entrySet().removeIf(entry -> positions.contains(entry.getValue().pos));
        PREVIEWS.entrySet().removeIf(entry -> positions.contains(entry.getValue().pos));
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

    private record PreviewToken(BlockPos pos, long deadline) {
    }
}
