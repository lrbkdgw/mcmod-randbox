package io.github.lootroulette.client;

import io.github.lootroulette.Rarity;
import io.github.lootroulette.network.OpenRoulettePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Full-screen, deliberately non-closable first-open animation. The server alone ends it. */
public final class RouletteScreen extends Screen {
    private final Rarity rarity;
    private final int durationTicks;
    private final List<List<ItemStack>> reels;
    private long startedAt;
    private int lastClickIndex = -1;

    public RouletteScreen(OpenRoulettePacket packet) {
        super(Component.translatable("screen.lootroulette.title"));
        this.rarity = Rarity.byOrdinal(packet.rarity());
        this.durationTicks = packet.durationTicks();
        this.reels = packet.reels();
        this.startedAt = Util.getMillis();
    }

    @Override
    protected void init() {
        if (startedAt == 0L) startedAt = Util.getMillis();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xE5101018);
        int color = 0xFF000000 | rarity.color();
        graphics.drawCenteredString(font, title, width / 2, Math.max(18, height / 2 - 92), 0xFFFFFFFF);
        graphics.drawCenteredString(font, rarity.displayName(), width / 2, Math.max(34, height / 2 - 75), color);

        double elapsedTicks = (Util.getMillis() - startedAt) / 50.0;
        float linear = Mth.clamp((float) (elapsedTicks / durationTicks), 0.0F, 1.0F);
        // Quintic ease-out gives a fast cascade followed by a readable, tense slowdown.
        float eased = 1.0F - (float) Math.pow(1.0F - linear, 5.0);
        int columnWidth = reels.size() >= 8 ? 28 : 34;
        int totalWidth = reels.size() * columnWidth;
        int left = (width - totalWidth) / 2;
        int centerY = height / 2;
        int clipTop = centerY - 48;
        int clipBottom = centerY + 49;

        graphics.fill(left - 8, clipTop - 8, left + totalWidth + 8, clipBottom + 8, 0xAA050509);
        graphics.fill(left - 7, clipTop - 7, left + totalWidth + 7, clipTop - 4, color);
        graphics.fill(left - 7, clipBottom + 4, left + totalWidth + 7, clipBottom + 7, color);
        graphics.enableScissor(left - 4, clipTop, left + totalWidth + 4, clipBottom);

        int audibleIndex = 0;
        for (int column = 0; column < reels.size(); column++) {
            List<ItemStack> reel = reels.get(column);
            if (reel.isEmpty()) continue;
            float stagger = Math.max(0.0F, eased - column * 0.0035F);
            float position = stagger * (reel.size() - 1);
            if (linear >= 0.995F) position = reel.size() - 1;
            int base = Mth.floor(position);
            float fraction = position - base;
            audibleIndex = Math.max(audibleIndex, base);
            int itemX = left + column * columnWidth + (columnWidth - 16) / 2;
            for (int offset = -3; offset <= 3; offset++) {
                int index = base + offset;
                if (index < 0 || index >= reel.size()) continue;
                int y = centerY + Math.round((offset - fraction) * 28.0F) - 8;
                graphics.renderItem(reel.get(index), itemX, y);
                graphics.renderItemDecorations(font, reel.get(index), itemX, y);
            }
        }
        graphics.disableScissor();

        // Fixed selection windows, matching the multi-column reference layout.
        for (int column = 0; column < reels.size(); column++) {
            int x = left + column * columnWidth + 2;
            int right = x + columnWidth - 4;
            graphics.fill(x, centerY - 13, right, centerY - 11, 0xFFFFFFFF);
            graphics.fill(x, centerY + 11, right, centerY + 13, 0xFFFFFFFF);
            graphics.fill(x, centerY - 13, x + 2, centerY + 13, 0xFFFFFFFF);
            graphics.fill(right - 2, centerY - 13, right, centerY + 13, 0xFFFFFFFF);
        }

        if (audibleIndex != lastClickIndex && minecraft != null && minecraft.player != null && linear < 0.98F) {
            lastClickIndex = audibleIndex;
            minecraft.player.playSound(SoundEvents.NOTE_BLOCK_HAT.value(), 0.25F, 0.8F + linear * 0.8F);
        }
        Component status = linear < 1.0F
                ? Component.translatable("screen.lootroulette.rolling")
                : Component.translatable("screen.lootroulette.opening");
        graphics.drawCenteredString(font, status, width / 2, centerY + 72, 0xFFD8D8D8);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void onClose() {
        // Intentionally ignored. The server replaces this screen with the real container menu.
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Swallow every key so inventory, escape and modded keybinds cannot bypass the roll.
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
