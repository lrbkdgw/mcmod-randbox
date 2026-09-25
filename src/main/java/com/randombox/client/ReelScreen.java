package com.randombox.client;

import java.util.List;

import com.randombox.Rarity;
import com.randombox.net.RBNetwork;
import com.randombox.net.ReelFinishedPacket;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * The unskippable lottery. One column per drawn item, the columns stop one after the other, the
 * item inside the white frame is the prize.
 */
public class ReelScreen extends Screen {
    private static final int SLOT = 18;
    private static final int CELL = 26;
    private static final int VISIBLE_ROWS = 9;
    private static final float END_DELAY = 0.8F;

    private final BlockPos pos;
    private final Rarity rarity;
    private final List<List<ItemStack>> reels;
    private final float[] stopTimes;
    private final boolean[] landed;
    private final float totalTime;

    private long startMillis;
    private boolean finished;

    public ReelScreen(BlockPos pos, Rarity rarity, List<List<ItemStack>> reels, List<Float> durations) {
        super(Component.translatable("randombox.screen.reel"));
        this.pos = pos;
        this.rarity = rarity;
        this.reels = reels;
        this.stopTimes = new float[reels.size()];
        this.landed = new boolean[reels.size()];
        float time = 0.0F;
        for (int i = 0; i < reels.size(); i++) {
            time += i < durations.size() ? durations.get(i) : 1.2F;
            this.stopTimes[i] = time;
        }
        this.totalTime = time;
    }

    @Override
    protected void init() {
        this.startMillis = System.currentTimeMillis();
    }

    private float elapsed() {
        return (System.currentTimeMillis() - this.startMillis) / 1000.0F;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Nothing can skip the lottery.
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return true;
    }

    @Override
    public void onClose() {
        if (this.finished) {
            super.onClose();
        }
    }

    @Override
    public void tick() {
        float time = this.elapsed();
        for (int i = 0; i < this.reels.size(); i++) {
            if (!this.landed[i] && time >= this.stopTimes[i]) {
                this.landed[i] = true;
                if (this.minecraft != null && this.minecraft.player != null) {
                    this.minecraft.player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.7F, 0.8F + 0.1F * i);
                }
            }
        }
        if (!this.finished && time >= this.totalTime + END_DELAY) {
            this.finished = true;
            RBNetwork.toServer(new ReelFinishedPacket(this.pos));
            if (this.minecraft != null) {
                this.minecraft.setScreen(null);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        float time = this.elapsed();

        int columns = this.reels.size();
        int totalWidth = columns * CELL;
        int left = (this.width - totalWidth) / 2;
        int centerY = this.height / 2;

        graphics.fill(0, centerY - CELL / 2 - 2, this.width, centerY + CELL / 2 + 2, 0x40000000 | (this.rarity.color() & 0xFFFFFF));

        for (int column = 0; column < columns; column++) {
            List<ItemStack> reel = this.reels.get(column);
            if (reel.isEmpty()) {
                continue;
            }
            float progress = this.progress(column, time);
            float position = progress * (reel.size() - 1);
            int base = (int) Math.floor(position);
            float fraction = position - base;
            int x = left + column * CELL + (CELL - SLOT) / 2;

            for (int row = -VISIBLE_ROWS / 2 - 1; row <= VISIBLE_ROWS / 2 + 1; row++) {
                int index = base + row;
                if (index < 0 || index >= reel.size()) {
                    continue;
                }
                int y = centerY - SLOT / 2 + (int) ((row - fraction) * CELL);
                if (y < 10 || y > this.height - 30) {
                    continue;
                }
                ItemStack stack = reel.get(index);
                graphics.renderItem(stack, x, y);
                graphics.renderItemDecorations(this.font, stack, x, y);
            }

            // Highlight frame of the winning slot.
            int frameX = left + column * CELL + 1;
            int frameY = centerY - CELL / 2 + 1;
            int color = this.landed[column] ? 0xFF000000 | this.rarity.color() : 0xFFFFFFFF;
            graphics.renderOutline(frameX, frameY, CELL - 2, CELL - 2, color);
        }

        Component title = Component.translatable("randombox.screen.title",
                Component.translatable(this.rarity.translationKey()).withStyle(this.rarity.format()));
        graphics.drawCenteredString(this.font, title, this.width / 2, 20, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("randombox.screen.no_skip"),
                this.width / 2, this.height - 22, 0xA0A0A0);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** Eased scroll progress of one column, 1 when the column has stopped. */
    private float progress(int column, float time) {
        float stop = this.stopTimes[column];
        if (time >= stop) {
            return 1.0F;
        }
        float ratio = Math.max(0.0F, time / stop);
        return 1.0F - (1.0F - ratio) * (1.0F - ratio) * (1.0F - ratio);
    }
}
