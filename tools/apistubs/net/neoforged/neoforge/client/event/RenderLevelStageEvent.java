package net.neoforged.neoforge.client.event;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Camera;
import net.neoforged.bus.api.Event;

public class RenderLevelStageEvent extends Event {
    public static class Stage {
        public static final Stage AFTER_TRANSLUCENT_BLOCKS = new Stage();
        public static final Stage AFTER_PARTICLES = new Stage();
    }

    public Stage getStage() { return null; }
    public PoseStack getPoseStack() { return null; }
    public Camera getCamera() { return null; }
    public float getPartialTick() { return 0; }
}
