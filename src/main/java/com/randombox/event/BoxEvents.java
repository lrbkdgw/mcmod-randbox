package com.randombox.event;

import java.util.List;

import com.randombox.command.RandomBoxCommand;
import com.randombox.config.RBConfig;
import com.randombox.data.BoxData;
import com.randombox.data.BoxSavedData;
import com.randombox.enchantment.RandomBoxEnchantments;
import com.randombox.item.RandomBoxItems;
import com.randombox.item.WardenTentacleItem;
import com.randombox.loot.CustomLootStore;
import com.randombox.loot.ItemQuality;
import com.randombox.net.RBNetwork;
import com.randombox.net.SyncBoxesPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;

/** All server side game events of the mod. */
public class BoxEvents {
    private static final int SYNC_INTERVAL = 20;
    private int syncCounter;

    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        RBConfig.load(FMLPaths.CONFIGDIR.get());
        CustomLootStore.load(FMLPaths.CONFIGDIR.get());
        ItemQuality.load(FMLPaths.CONFIGDIR.get());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        RandomBoxCommand.register(event.getDispatcher());
    }

    /** Warden Tentacle: 10% from a player-killed Warden, +2% per Looting level. */
    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Warden warden)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player)) {
            return;
        }
        float chance = 0.10F + 0.02F * Math.max(0, event.getLootingLevel());
        if (warden.getRandom().nextFloat() >= chance) {
            return;
        }
        ItemStack stack = new ItemStack(RandomBoxItems.WARDEN_TENTACLE.get());
        event.getDrops().add(new ItemEntity(warden.level(), warden.getX(), warden.getY(), warden.getZ(), stack));
    }

    /** First opening of a loot chest becomes the lottery. */
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.isSpectator()) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockEntity blockEntity = event.getLevel().getBlockEntity(pos);
        if (!(blockEntity instanceof RandomizableContainerBlockEntity)) {
            return;
        }
        if (WardenTentacleItem.tryPreview(player, pos, event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (!ReelManager.hasLootTable(player.serverLevel(), pos)) {
            return;
        }
        if (ReelManager.start(player, pos)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** Registers naturally generated loot chests so they can glow before they are opened. */
    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        BoxSavedData saved = BoxSavedData.get(level);
        for (BlockPos pos : chunk.getBlockEntitiesPos()) {
            BlockEntity blockEntity = chunk.getBlockEntity(pos);
            if (!(blockEntity instanceof RandomizableContainerBlockEntity container)) {
                continue;
            }
            ResourceLocation table = ReelManager.lootTableOf(container);
            if (table != null) {
                saved.getOrCreate(pos, table, level.getRandom());
            }
        }
    }

    /**
     * A broken box must not keep glowing. Its (never rolled) loot is intentionally lost, exactly
     * like a vanilla loot chest that is mined before it was opened.
     */
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BoxSavedData saved = BoxSavedData.get(level);
        BlockPos pos = event.getPos();
        ReelManager.cancelForBrokenBox(level, pos);
        for (RandomizableContainerBlockEntity part : ReelManager.parts(level, pos)) {
            if (ReelManager.lootTableOf(part) != null) {
                // Prevent vanilla from unpacking and dropping the still-unrolled loot table while
                // the chest block is being destroyed.
                part.setLootTable(null, 0L);
                part.clearContent();
                part.setChanged();
            }
            saved.remove(part.getBlockPos());
        }
        saved.remove(pos);
        for (BlockPos neighbour : new BlockPos[] {pos.north(), pos.south(), pos.east(), pos.west()}) {
            BoxData data = saved.get(neighbour);
            if (data != null && !(level.getBlockEntity(neighbour) instanceof RandomizableContainerBlockEntity)) {
                saved.remove(neighbour);
            }
        }
    }

    /** Drops boxes whose block is gone (broken, exploded, /setblock, ...). */
    private void forgetMissingBoxes(ServerLevel level, List<BoxData> boxes) {
        BoxSavedData saved = null;
        for (BoxData box : boxes) {
            if (!level.isLoaded(box.pos())) {
                continue;
            }
            if (level.getBlockEntity(box.pos()) instanceof RandomizableContainerBlockEntity) {
                continue;
            }
            if (saved == null) {
                saved = BoxSavedData.get(level);
            }
            saved.remove(box.pos());
        }
        if (saved != null) {
            boxes.removeIf(box -> level.isLoaded(box.pos())
                    && !(level.getBlockEntity(box.pos()) instanceof RandomizableContainerBlockEntity));
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ReelManager.tick(event.getServer());
        if (++this.syncCounter < SYNC_INTERVAL) {
            return;
        }
        this.syncCounter = 0;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            int radius = Math.max(RBConfig.effectRadius(),
                    RandomBoxEnchantments.effectiveBeamRadius(player, RBConfig.beamRadius()));
            List<BoxData> boxes = BoxSavedData.get(level).near(player.blockPosition(), radius);
            this.forgetMissingBoxes(level, boxes);
            RBNetwork.toPlayer(player, SyncBoxesPacket.of(boxes));
        }
    }
}
