package com.randombox.client;

import java.util.Map;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.randombox.Rarity;
import com.randombox.config.RBConfig;
import com.randombox.enchantment.RandomBoxEnchantments;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;

/** Particles around every unopened box plus the light beam of nearby ones. */
public class ClientEvents {
    private static final int PARTICLES_PER_BOX = 3;
    private static final int BEAM_HEIGHT = 24;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            ReelScreen.clearActive();
            return;
        }
        // The lottery cannot be skipped: if something closed the screen, bring it back.
        ReelScreen reel = ReelScreen.active();
        if (reel != null && minecraft.screen != reel) {
            minecraft.setScreen(reel);
        }
        if (minecraft.isPaused()) {
            return;
        }
        RandomSource random = level.getRandom();
        Map<BlockPos, ClientBoxCache.View> boxes = ClientBoxCache.boxes();
        java.util.List<BlockPos> gone = new java.util.ArrayList<>();
        for (Map.Entry<BlockPos, ClientBoxCache.View> entry : boxes.entrySet()) {
            BlockPos pos = entry.getKey();
            Rarity rarity = entry.getValue().rarity();
            if (minecraft.player.blockPosition().distSqr(pos) > RBConfig.effectRadius() * RBConfig.effectRadius()) {
                continue;
            }
            if (!isBox(level, pos)) {
                // the chest was just broken: stop the effect immediately instead of waiting for
                // the next sync packet
                gone.add(pos);
                continue;
            }
            DustParticleOptions dust = new DustParticleOptions(
                    new Vector3f(rarity.red(), rarity.green(), rarity.blue()), 1.0F);
            for (int i = 0; i < PARTICLES_PER_BOX; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double radius = 0.9D + random.nextDouble() * 0.3D;
                double x = pos.getX() + 0.5D + Math.cos(angle) * radius;
                double z = pos.getZ() + 0.5D + Math.sin(angle) * radius;
                double y = pos.getY() + 0.1D + random.nextDouble() * 1.2D;
                level.addParticle(dust, x, y, z, 0.0D, 0.01D, 0.0D);
            }
        }
        for (BlockPos pos : gone) {
            ClientBoxCache.forget(pos);
        }
    }

    /** True while the block at that position still is a loot container. */
    private static boolean isBox(ClientLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return true;
        }
        return level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity;
    }

    @SubscribeEvent
    public void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            return;
        }
        Map<BlockPos, ClientBoxCache.View> boxes = ClientBoxCache.boxes();
        if (boxes.isEmpty()) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        int beamRadius = RandomBoxEnchantments.effectiveBeamRadius(minecraft.player, RBConfig.beamRadius());

        for (Map.Entry<BlockPos, ClientBoxCache.View> entry : boxes.entrySet()) {
            BlockPos pos = entry.getKey();
            // Only boxes that have never been opened get the light beam.
            if (entry.getValue().opened()) {
                continue;
            }
            if (minecraft.player.blockPosition().distSqr(pos) > (double) beamRadius * beamRadius) {
                continue;
            }
            if (!isBox(level, pos)) {
                continue;
            }
            Rarity rarity = entry.getValue().rarity();
            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            BeaconRenderer.renderBeaconBeam(poseStack, buffers, BeaconRenderer.BEAM_LOCATION,
                    event.getPartialTick(), 1.0F, level.getGameTime(), 0, BEAM_HEIGHT,
                    new float[]{rarity.red(), rarity.green(), rarity.blue()}, 0.2F, 0.25F);
            poseStack.popPose();
        }
        buffers.endBatch();
    }
}
