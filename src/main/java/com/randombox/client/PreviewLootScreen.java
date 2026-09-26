package com.randombox.client;

import java.util.List;

import com.randombox.Rarity;
import com.randombox.net.PreviewChoicePacket;
import com.randombox.net.RBNetwork;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Shows the prizes fixed by a Warden Tentacle before the box is opened. */
public class PreviewLootScreen extends Screen {
    private static final int SLOT = 18;
    private static final int CELL = 28;

    private final BlockPos pos;
    private final Rarity rarity;
    private final List<ItemStack> prizes;
    private final boolean rerollAvailable;
    private boolean answered;

    public PreviewLootScreen(BlockPos pos, Rarity rarity, List<ItemStack> prizes, boolean rerollAvailable) {
        super(Component.translatable("randombox.preview.title"));
        this.pos = pos;
        this.rarity = rarity;
        this.prizes = prizes;
        this.rerollAvailable = rerollAvailable;
    }

    @Override
    protected void init() {
        int y = this.height - 34;
        int center = this.width / 2;
        if (this.rerollAvailable) {
            this.addRenderableWidget(Button.builder(Component.translatable("randombox.preview.keep"), button -> {
                this.answer(false);
            }).bounds(center - 104, y, 96, 20).build());
            this.addRenderableWidget(Button.builder(Component.translatable("randombox.preview.reroll"), button -> {
                this.answer(true);
            }).bounds(center + 8, y, 96, 20).build());
        } else {
            this.addRenderableWidget(Button.builder(Component.translatable("randombox.preview.close"), button -> {
                this.answered = true;
                this.onClose();
            }).bounds(center - 48, y, 96, 20).build());
        }
    }

    private void answer(boolean reroll) {
        if (this.answered) {
            return;
        }
        this.answered = true;
        RBNetwork.toServer(new PreviewChoicePacket(this.pos, reroll));
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public void onClose() {
        if (!this.answered && this.rerollAvailable) {
            this.answered = true;
            RBNetwork.toServer(new PreviewChoicePacket(this.pos, false));
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        Component title = Component.translatable("randombox.preview.heading",
                Component.translatable(this.rarity.translationKey()).withStyle(this.rarity.format()));
        graphics.drawCenteredString(this.font, title, this.width / 2, 18, 0xFFFFFF);
        Component hint = Component.translatable(this.rerollAvailable
                ? "randombox.preview.hint"
                : "randombox.preview.rerolled");
        graphics.drawCenteredString(this.font, hint, this.width / 2, 34, 0xA0A0A0);

        int columns = Math.max(1, Math.min(8, this.prizes.size()));
        int rows = (this.prizes.size() + columns - 1) / columns;
        int totalWidth = columns * CELL;
        int left = (this.width - totalWidth) / 2;
        int top = Math.max(54, (this.height - rows * CELL) / 2 - 8);

        for (int i = 0; i < this.prizes.size(); i++) {
            ItemStack stack = this.prizes.get(i);
            int col = i % columns;
            int row = i / columns;
            int x = left + col * CELL + (CELL - SLOT) / 2;
            int y = top + row * CELL;
            graphics.fill(x - 2, y - 2, x + SLOT + 2, y + SLOT + 2, 0x80000000 | (this.rarity.color() & 0xFFFFFF));
            graphics.renderItem(stack, x, y);
            graphics.renderItemDecorations(this.font, stack, x, y);
            if (mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT) {
                graphics.renderTooltip(this.font, stack, mouseX, mouseY);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
