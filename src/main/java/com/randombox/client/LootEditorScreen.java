package com.randombox.client;

import java.util.ArrayList;
import java.util.List;

import com.randombox.loot.BoxLootTable;
import com.randombox.loot.LootExtractor;
import com.randombox.net.RBNetwork;
import com.randombox.net.RequestEditorPacket;
import com.randombox.net.SaveTablePacket;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

/** In game creation and editing of loot tables. */
public class LootEditorScreen extends Screen {
    private static final int TABLES_PER_PAGE = 9;
    private static final int ENTRIES_PER_PAGE = 6;

    private final List<BoxLootTable> tables;
    private final List<ResourceLocation> available;

    private int tablePage;
    private int entryPage;
    private int selectedTable = -1;
    private int selectedPool;

    private EditBox newTableBox;
    private final List<Row> rows = new ArrayList<>();

    public LootEditorScreen(List<BoxLootTable> tables, List<ResourceLocation> available) {
        super(Component.translatable("randombox.screen.editor"));
        this.tables = new ArrayList<>(tables);
        this.available = new ArrayList<>(available);
    }

    @Override
    protected void init() {
        this.rows.clear();
        this.clearWidgets();

        // ---- left: table list -------------------------------------------------------------
        int listTop = 30;
        int shown = Math.min(TABLES_PER_PAGE, Math.max(0, this.tables.size() - this.tablePage * TABLES_PER_PAGE));
        for (int i = 0; i < shown; i++) {
            int index = this.tablePage * TABLES_PER_PAGE + i;
            BoxLootTable table = this.tables.get(index);
            String label = shorten(table.id().toString(), 22);
            this.addRenderableWidget(Button.builder(Component.literal(label), button -> selectTable(index))
                    .bounds(8, listTop + i * 20, 140, 18).build());
        }
        this.addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            this.applyRows();
            if (this.tablePage > 0) {
                this.tablePage--;
            }
            this.rebuild();
        }).bounds(8, listTop + TABLES_PER_PAGE * 20 + 2, 40, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            this.applyRows();
            if ((this.tablePage + 1) * TABLES_PER_PAGE < this.tables.size()) {
                this.tablePage++;
            }
            this.rebuild();
        }).bounds(108, listTop + TABLES_PER_PAGE * 20 + 2, 40, 18).build());

        this.newTableBox = new EditBox(this.font, 8, listTop + TABLES_PER_PAGE * 20 + 26, 140, 18,
                Component.translatable("randombox.editor.new_id"));
        this.newTableBox.setMaxLength(128);
        this.newTableBox.setHint(Component.literal("minecraft:chests/my_table"));
        this.addRenderableWidget(this.newTableBox);

        this.addRenderableWidget(Button.builder(Component.translatable("randombox.editor.create"), button -> createTable())
                .bounds(8, listTop + TABLES_PER_PAGE * 20 + 48, 68, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("randombox.editor.import"), button -> importTable())
                .bounds(80, listTop + TABLES_PER_PAGE * 20 + 48, 68, 18).build());

        // ---- right: selected table --------------------------------------------------------
        BoxLootTable table = this.selected();
        if (table == null) {
            return;
        }
        int right = 160;
        int poolRow = 30;
        int poolCount = table.pools().size();
        for (int i = 0; i < poolCount && i < 8; i++) {
            int index = i;
            this.addRenderableWidget(Button.builder(Component.literal("#" + (i + 1)), button -> {
                this.applyRows();
                this.selectedPool = index;
                this.entryPage = 0;
                this.rebuild();
            }).bounds(right + i * 26, poolRow, 24, 18).build());
        }
        this.addRenderableWidget(Button.builder(Component.literal("+"), button -> {
            this.applyRows();
            table.addPool();
            this.selectedPool = table.pools().size() - 1;
            this.rebuild();
        }).bounds(right + Math.min(poolCount, 8) * 26, poolRow, 24, 18).build());

        if (this.selectedPool >= table.pools().size()) {
            this.selectedPool = Math.max(0, table.pools().size() - 1);
        }
        if (table.pools().isEmpty()) {
            return;
        }
        BoxLootTable.Pool pool = table.pools().get(this.selectedPool);

        int headerY = poolRow + 24;
        EditBox rollsBox = new EditBox(this.font, right + 60, headerY, 50, 18,
                Component.translatable("randombox.editor.rolls"));
        rollsBox.setValue(Float.toString(pool.averageRolls()));
        rollsBox.setResponder(value -> {
            try {
                pool.setAverageRolls(Float.parseFloat(value));
            } catch (NumberFormatException ignored) {
                // keep the old value while typing
            }
        });
        this.addRenderableWidget(rollsBox);

        int rowTop = headerY + 32;
        int firstEntry = this.entryPage * ENTRIES_PER_PAGE;
        for (int i = 0; i < ENTRIES_PER_PAGE; i++) {
            int index = firstEntry + i;
            if (index >= pool.entries().size()) {
                break;
            }
            BoxLootTable.Entry entry = pool.entries().get(index);
            int y = rowTop + i * 22;
            Row row = new Row(entry);

            row.item = new EditBox(this.font, right, y, 150, 18, Component.literal("item"));
            row.item.setMaxLength(128);
            row.item.setValue(LootExtractor.itemId(entry.item()).toString());
            this.addRenderableWidget(row.item);

            row.weight = numberBox(right + 154, y, 34, entry.weight());
            row.quality = numberBox(right + 192, y, 34, entry.quality());
            row.min = numberBox(right + 230, y, 30, entry.minCount());
            row.max = numberBox(right + 264, y, 30, entry.maxCount());

            this.addRenderableWidget(Button.builder(Component.literal("x"), button -> {
                this.applyRows();
                pool.entries().remove(entry);
                this.rebuild();
            }).bounds(right + 298, y, 18, 18).build());

            this.rows.add(row);
        }

        int bottom = rowTop + ENTRIES_PER_PAGE * 22 + 6;
        this.addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            this.applyRows();
            if (this.entryPage > 0) {
                this.entryPage--;
            }
            this.rebuild();
        }).bounds(right, bottom, 30, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            this.applyRows();
            if ((this.entryPage + 1) * ENTRIES_PER_PAGE < pool.entries().size()) {
                this.entryPage++;
            }
            this.rebuild();
        }).bounds(right + 34, bottom, 30, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("randombox.editor.add_entry"), button -> {
            this.applyRows();
            pool.addEntry(Items.DIAMOND);
            this.entryPage = (pool.entries().size() - 1) / ENTRIES_PER_PAGE;
            this.rebuild();
        }).bounds(right + 70, bottom, 90, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("randombox.editor.save"), button -> {
            this.applyRows();
            RBNetwork.toServer(new SaveTablePacket(table, false));
        }).bounds(right + 164, bottom, 70, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("randombox.editor.delete"), button -> {
            RBNetwork.toServer(new SaveTablePacket(table, true));
            this.tables.remove(table);
            this.selectedTable = -1;
            this.rebuild();
        }).bounds(right + 238, bottom, 70, 18).build());
    }

    private EditBox numberBox(int x, int y, int width, int value) {
        EditBox box = new EditBox(this.font, x, y, width, 18, Component.literal("value"));
        box.setValue(Integer.toString(value));
        box.setFilter(text -> text.isEmpty() || text.matches("-?\\d{0,6}"));
        this.addRenderableWidget(box);
        return box;
    }

    private void selectTable(int index) {
        this.applyRows();
        this.selectedTable = index;
        this.selectedPool = 0;
        this.entryPage = 0;
        this.rebuild();
    }

    private void createTable() {
        String id = this.newTableBox.getValue().trim();
        if (id.isEmpty()) {
            return;
        }
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return;
        }
        BoxLootTable table = new BoxLootTable(location);
        table.addPool().addEntry(Items.DIAMOND);
        this.tables.add(table);
        this.selectedTable = this.tables.size() - 1;
        this.selectedPool = 0;
        this.entryPage = 0;
        this.rebuild();
    }

    /** Asks the server for an existing (vanilla / datapack) table so it can be edited. */
    private void importTable() {
        String id = this.newTableBox.getValue().trim();
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return;
        }
        RBNetwork.toServer(new RequestEditorPacket(location));
    }

    private void rebuild() {
        this.rebuildWidgets();
    }

    private BoxLootTable selected() {
        if (this.selectedTable < 0 || this.selectedTable >= this.tables.size()) {
            return null;
        }
        return this.tables.get(this.selectedTable);
    }

    /** Writes the values of the visible edit boxes back into the model. */
    private void applyRows() {
        for (Row row : this.rows) {
            ResourceLocation id = ResourceLocation.tryParse(row.item.getValue().trim());
            if (id != null) {
                row.entry.setItem(LootExtractor.itemById(id));
            }
            row.entry.setWeight(parseInt(row.weight.getValue(), row.entry.weight()));
            row.entry.setQuality(parseInt(row.quality.getValue(), row.entry.quality()));
            row.entry.setMinCount(parseInt(row.min.getValue(), row.entry.minCount()));
            row.entry.setMaxCount(parseInt(row.max.getValue(), row.entry.maxCount()));
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String shorten(String text, int length) {
        return text.length() <= length ? text : "..." + text.substring(text.length() - length + 3);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawString(this.font, this.title, 8, 10, 0xFFFFFF);
        graphics.drawString(this.font, Component.translatable("randombox.editor.tables"), 8, 20, 0xA0A0A0);

        BoxLootTable table = this.selected();
        if (table != null) {
            graphics.drawString(this.font, Component.literal(table.id().toString()), 160, 10, 0xFFD700);
            graphics.drawString(this.font, Component.translatable("randombox.editor.rolls"), 160, 58, 0xA0A0A0);
            graphics.drawString(this.font, Component.translatable("randombox.editor.header"), 160, 80, 0xA0A0A0);
        } else {
            graphics.drawString(this.font, Component.translatable("randombox.editor.hint"), 160, 40, 0xA0A0A0);
            graphics.drawString(this.font, Component.translatable("randombox.editor.available",
                    this.available.size()), 160, 54, 0xA0A0A0);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** One editable entry line. */
    private static class Row {
        private final BoxLootTable.Entry entry;
        private EditBox item;
        private EditBox weight;
        private EditBox quality;
        private EditBox min;
        private EditBox max;

        Row(BoxLootTable.Entry entry) {
            this.entry = entry;
        }
    }
}
