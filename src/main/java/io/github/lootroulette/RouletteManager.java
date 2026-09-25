package io.github.lootroulette;

import io.github.lootroulette.network.ModNetwork;
import io.github.lootroulette.network.OpenRoulettePacket;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative rarity assignment, rolling, particles and debug-stick behavior. */
public final class RouletteManager {
    private static final String ROOT = "LootRoulette";
    private static final String RARITY = "Rarity";
    private static final String OPENED = "Opened";
    private static final String SOURCE = "SourceLootTable";
    private static final String SOURCE_SEED = "SourceLootSeed";
    private static final String STICK_RARITY = "SelectedRarity";
    private static final int ROLL_TICKS = 120;
    private static final Map<ContainerKey, RollSession> ACTIVE = new HashMap<>();

    private RouletteManager() {}

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) return;

        ItemStack held = player.getMainHandItem();
        BlockPos pos = event.getPos();
        BlockEntity raw = level.getBlockEntity(pos);

        if (held.is(LootRouletteMod.DEBUG_STICK.get()) && player.isShiftKeyDown()) {
            cancel(event);
            if (raw instanceof RandomizableContainerBlockEntity container) resetWithStick(player, container);
            else player.displayClientMessage(Component.translatable("message.lootroulette.not_loot_container"), true);
            return;
        }

        if (!(raw instanceof RandomizableContainerBlockEntity container)) return;
        CompoundTag data = data(container);
        if (!rememberAndAssign(container, level.random)) return; // Player-placed/ordinary containers are untouched.
        if (data.getBoolean(OPENED)) return;

        cancel(event);
        ContainerKey key = new ContainerKey(level.dimension(), pos.immutable());
        RollSession existing = ACTIVE.get(key);
        if (existing != null) {
            player.displayClientMessage(Component.translatable("message.lootroulette.busy"), true);
            return;
        }

        Rarity rarity = Rarity.byOrdinal(data.getInt(RARITY));
        List<ItemStack> candidates = rollCandidates(level, player, pos, data, rarity.itemCount());
        if (candidates.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.lootroulette.empty_table"), true);
            return;
        }

        Collections.shuffle(candidates, new java.util.Random(level.random.nextLong()));
        List<ItemStack> rewards = new ArrayList<>(rarity.itemCount());
        for (int i = 0; i < rarity.itemCount(); i++) {
            rewards.add(candidates.get(i % candidates.size()).copy());
        }
        List<List<ItemStack>> reels = buildReels(candidates, rewards, level.random);
        long endTick = level.getServer().getTickCount() + ROLL_TICKS;
        ACTIVE.put(key, new RollSession(key, player.getUUID(), endTick, rewards));
        ModNetwork.send(player, new OpenRoulettePacket(rarity.ordinal(), ROLL_TICKS, reels));
        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.8F, 1.15F);
    }

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide() || !event.getEntity().isShiftKeyDown()) return;
        ItemStack held = event.getEntity().getMainHandItem();
        if (!held.is(LootRouletteMod.DEBUG_STICK.get())) return;
        event.setCanceled(true);
        int next = (held.getOrCreateTag().getInt(STICK_RARITY) + 1) % Rarity.values().length;
        held.getOrCreateTag().putInt(STICK_RARITY, next);
        Rarity rarity = Rarity.byOrdinal(next);
        event.getEntity().displayClientMessage(Component.translatable("message.lootroulette.selected", rarity.displayName()), true);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ContainerKey key = new ContainerKey(level.dimension(), event.getPos().immutable());
        if (ACTIVE.containsKey(key)) {
            event.setCanceled(true);
            event.getPlayer().displayClientMessage(Component.translatable("message.lootroulette.rolling_locked"), true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        finishSessions(server);
        if (server.getTickCount() % 10 == 0) scanAndEmitParticles(server);
    }

    private static void finishSessions(MinecraftServer server) {
        List<ContainerKey> finished = new ArrayList<>();
        for (RollSession session : ACTIVE.values()) {
            if (server.getTickCount() < session.endTick) continue;
            ServerLevel level = server.getLevel(session.key.dimension);
            if (level != null && level.getBlockEntity(session.key.pos) instanceof RandomizableContainerBlockEntity container) {
                installRewards(container, session.rewards, level.random);
                CompoundTag data = data(container);
                data.putBoolean(OPENED, true);
                container.setChanged();
                level.sendBlockUpdated(session.key.pos, container.getBlockState(), container.getBlockState(), 3);
                level.playSound(null, session.key.pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.9F, 1.0F);

                ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);
                if (player != null && player.level() == level && player.distanceToSqr(Vec3.atCenterOf(session.key.pos)) < 64.0) {
                    player.openMenu(container);
                }
            }
            finished.add(session.key);
        }
        finished.forEach(ACTIVE::remove);
    }

    private static void scanAndEmitParticles(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            Set<BlockPos> seen = new HashSet<>();
            for (ServerPlayer player : level.players()) {
                BlockPos center = player.blockPosition();
                for (BlockPos cursor : BlockPos.betweenClosed(center.offset(-7, -5, -7), center.offset(7, 5, 7))) {
                    BlockPos pos = cursor.immutable();
                    if (!seen.add(pos)) continue;
                    if (!(level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container)) continue;
                    if (!rememberAndAssign(container, level.random)) continue;
                    emitParticles(level, pos, container, server.getTickCount());
                }
            }
        }
    }

    private static void emitParticles(ServerLevel level, BlockPos pos, RandomizableContainerBlockEntity container, int tick) {
        CompoundTag data = data(container);
        Rarity rarity = Rarity.byOrdinal(data.getInt(RARITY));
        DustParticleOptions dust = new DustParticleOptions(rarity.particleColor(), 1.0F);
        double phase = tick * 0.08;
        for (int i = 0; i < 8; i++) {
            double angle = phase + i * Math.PI / 4.0;
            level.sendParticles(dust, pos.getX() + 0.5 + Math.cos(angle) * 0.72,
                    pos.getY() + 0.18 + (i % 3) * 0.25,
                    pos.getZ() + 0.5 + Math.sin(angle) * 0.72, 1, 0, 0, 0, 0);
        }
        if (!data.getBoolean(OPENED) && tick % 20 == 0) {
            for (double y = 1.0; y <= 7.0; y += 0.42) {
                level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + y,
                        pos.getZ() + 0.5, 1, 0.035, 0.02, 0.035, 0.001);
            }
        }
    }

    /** Captures the original table before vanilla unpacks it and assigns a weighted tier once. */
    private static boolean rememberAndAssign(RandomizableContainerBlockEntity container, RandomSource random) {
        CompoundTag custom = data(container);
        if (!custom.contains(SOURCE)) {
            CompoundTag saved = container.saveWithFullMetadata();
            if (!saved.contains("LootTable")) return false;
            custom.putString(SOURCE, saved.getString("LootTable"));
            custom.putLong(SOURCE_SEED, saved.getLong("LootTableSeed"));
        }
        if (!custom.contains(RARITY)) {
            custom.putInt(RARITY, Rarity.roll(random.nextInt(10_000)).ordinal());
            custom.putBoolean(OPENED, false);
            container.setChanged();
        }
        return true;
    }

    private static List<ItemStack> rollCandidates(ServerLevel level, ServerPlayer player, BlockPos pos,
                                                   CompoundTag data, int needed) {
        ResourceLocation tableId = ResourceLocation.tryParse(data.getString(SOURCE));
        if (tableId == null) return List.of();
        LootTable table = level.getServer().getLootData().getLootTable(tableId);
        CriteriaTriggers.GENERATE_LOOT.trigger(player, tableId);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.CHEST);
        List<ItemStack> result = new ArrayList<>();
        long storedSeed = data.getLong(SOURCE_SEED);
        for (int attempt = 0; attempt < 12 && result.size() < Math.max(needed, 12); attempt++) {
            SimpleContainer temporary = new SimpleContainer(54);
            long seed = storedSeed != 0L ? storedSeed + attempt * 31L : level.random.nextLong();
            table.fill(temporary, params, seed);
            for (int slot = 0; slot < temporary.getContainerSize(); slot++) {
                ItemStack stack = temporary.getItem(slot);
                if (!stack.isEmpty()) result.add(stack.copy());
            }
        }
        return result;
    }

    private static List<List<ItemStack>> buildReels(List<ItemStack> candidates, List<ItemStack> rewards, RandomSource random) {
        List<List<ItemStack>> reels = new ArrayList<>(rewards.size());
        for (ItemStack reward : rewards) {
            List<ItemStack> reel = new ArrayList<>(18);
            for (int i = 0; i < 17; i++) reel.add(candidates.get(random.nextInt(candidates.size())).copy());
            reel.add(reward.copy());
            reels.add(reel);
        }
        return reels;
    }

    private static void installRewards(RandomizableContainerBlockEntity container, List<ItemStack> rewards, RandomSource random) {
        container.setLootTable(null, 0L);
        container.clearContent();
        List<Integer> slots = new ArrayList<>(container.getContainerSize());
        for (int i = 0; i < container.getContainerSize(); i++) slots.add(i);
        Collections.shuffle(slots, new java.util.Random(random.nextLong()));
        for (int i = 0; i < rewards.size() && i < slots.size(); i++) container.setItem(slots.get(i), rewards.get(i).copy());
        container.setChanged();
    }

    private static void resetWithStick(ServerPlayer player, RandomizableContainerBlockEntity container) {
        ContainerKey key = new ContainerKey(player.level().dimension(), container.getBlockPos().immutable());
        if (ACTIVE.containsKey(key)) {
            player.displayClientMessage(Component.translatable("message.lootroulette.busy"), true);
            return;
        }
        if (!rememberAndAssign(container, player.level().random)) {
            player.displayClientMessage(Component.translatable("message.lootroulette.no_original_table"), true);
            return;
        }
        CompoundTag data = data(container);
        ResourceLocation source = ResourceLocation.tryParse(data.getString(SOURCE));
        if (source == null) return;
        int selected = player.getMainHandItem().getOrCreateTag().getInt(STICK_RARITY);
        Rarity rarity = Rarity.byOrdinal(selected);
        container.clearContent();
        container.setLootTable(source, data.getLong(SOURCE_SEED));
        data.putInt(RARITY, rarity.ordinal());
        data.putBoolean(OPENED, false);
        container.setChanged();
        if (player.level() instanceof ServerLevel level) {
            level.sendBlockUpdated(container.getBlockPos(), container.getBlockState(), container.getBlockState(), 3);
        }
        player.displayClientMessage(Component.translatable("message.lootroulette.reset", rarity.displayName()), true);
    }

    private static CompoundTag data(BlockEntity entity) {
        CompoundTag persistent = entity.getPersistentData();
        if (!persistent.contains(ROOT, CompoundTag.TAG_COMPOUND)) persistent.put(ROOT, new CompoundTag());
        return persistent.getCompound(ROOT);
    }

    private static void cancel(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private record ContainerKey(ResourceKey<Level> dimension, BlockPos pos) {}
    private record RollSession(ContainerKey key, UUID playerId, long endTick, List<ItemStack> rewards) {}
}
