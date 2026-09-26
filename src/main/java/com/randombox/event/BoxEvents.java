package com.randombox.event;

import java.util.List;

import com.randombox.command.RandomBoxCommand;
import com.randombox.config.RBConfig;
import com.randombox.data.BoxData;
import com.randombox.data.BoxSavedData;
import com.randombox.loot.CustomLootStore;
import com.randombox.net.RBNetwork;
import com.randombox.net.SyncBoxesPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
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
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        RandomBoxCommand.register(event.getDispatcher());
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
            List<BoxData> boxes = BoxSavedData.get(level).near(player.blockPosition(), RBConfig.effectRadius());
            RBNetwork.toPlayer(player, SyncBoxesPacket.of(boxes));
        }
    }
}
