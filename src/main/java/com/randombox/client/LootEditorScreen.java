package com.randombox.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.randombox.loot.BoxLootTable;
import com.randombox.loot.LootExtractor;
import com.randombox.net.RBNetwork;
import com.randombox.net.RequestEditorPacket;
import com.randombox.net.SaveTablePacket;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * In game creation and editing of loot tables.
 *
 * <p>The left side lists every loot table of the server (the ones already edited by this mod first,
 * then all vanilla / datapack tables) with a search box. Clicking a table that is not loaded yet
 * asks the server to import it, clicking an already loaded one opens it directly. The right side
 * edits the pools and entries of the selected table.
 */
public class LootEditorScreen extends Screen {
    private static final int LIST_WIDTH = 150;
    private static final int ROW_HEIGHT = 14;

    /** Kept between reopenings (the screen is recreated whenever the server answers). */
    private static String lastFilter = "";
    private static String lastNewId = "";

    private final List<BoxLootTable> tables;
    private final List<ResourceLocation> available;
    private final List<ResourceLocation> visible = new ArrayList<>();

    private EditBox searchBox;
    private EditBox newTableBox;
    private final List<Row> rows = new ArrayList<>();

    private int scroll;
    private int entryPage;
    private int selectedPool;
    private BoxLootTable selected;
    private Component status = Component.empty();

    public LootEditorScreen(List<BoxLootTable> tables, List<ResourceLocation> available) {
        this(tables, available, null);
    }

    public LootEditorScreen(List<BoxLootTable> tables, List<ResourceLocation> available, ResourceLocation focus) {
        super(Component.translatable("randombox.screen.editor"));
        this.tables = new ArrayList<>(tables);
        this.available = new ArrayList<>(available);
        this.available.sort(ResourceLocation::compareTo);
        if (focus != null) {
            this.selected = find(focus);
            if (this.selected != null) {
                this.status = Component.translatable("randombox.editor.loaded", focus.toString());
            }
        }
    }

    private BoxLootTable find(ResourceLocation id) {
        for (BoxLootTable table : this.tables) {
            if (table.id().equals(id)) {
                return table;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ list -------------------

    private void refreshVisible() {
        this.visible.clear();
        String filter = this.searchBox == null ? lastFilter : this.searchBox.getValue();
        filter = filter.toLowerCase(Locale.ROOT).trim();
        for (BoxLootTable table : this.tables) {
            if (filter.isEmpty() || table.id().toString().toLowerCase(Locale.ROOT).contains(filter)) {
                this.visible.add(table.id());
            }
        }
        for (ResourceLocation id : this.available) {
            if (find(id) != null) {
                continue;
            }
            if (filter.isEmpty() || id.toString().toLowerCase(Locale.ROOT).contains(filter)) {
                this.visible.add(id);
            }
        }
        int rowsShown = this.listRows();
        int max = Math.max(0, this.visible.size() - rowsShown);
        if (this.scroll > max) {
            this.scroll = max;
        }
        if (this.scroll < 0) {
            this.scroll = 0;
        }
    }

    private int listRows() {
        return Math.max(1, (this.height - 120) / ROW_HEIGHT);
    }

    @Override
    protected void init() {
        this.rows.clear();

        this.searchBox = new EditBox(this.font, 8, 22, LIST_WIDTH, 16,
                Component.translatable("randombox.editor.search"));
        this.searchBox.setMaxLength(128);
        this.searchBox.setValue(lastFilter);
        this.searchBox.setHint(Component.translatable("randombox.editor.search"));
        this.searchBox.setResponder(value -> {
            lastFilter = value;
            this.scroll = 0;
            this.refreshVisible();
        });
        this.addRenderableWidget(this.searchBox);

        this.refreshVisible();

        int listTop = 42;
        int rowsShown = this.listRows();
        for (int i = 0; i < rowsShown; i++) {
            int index = this.scroll + i;
            if (index >= this.visible.size()) {
                break;
            }
            ResourceLocation id = this.visible.get(index);
            boolean loaded = find(id) != null;
            Component label = Component.literal((loaded ? "* " : "") + shorten(id.toString(), 24))
                    .withStyle(loaded ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
            this.addRenderableWidget(Button.builder(label, button -> openTable(id))
                    .bounds(8, listTop + i * ROW_HEIGHT, LIST_WIDTH, ROW_HEIGHT - 1).build());
        }

        int listBottom = listTop + rowsShown * ROW_HEIGHT + 2;
        this.addRenderableWidget(Button.builder(Component.literal("\u25B2"), button -> {
            this.scroll = Math.max(0, this.scroll - this.listRows());
            this.rebuild();
        }).bounds(8, listBottom, 40, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal("\u25BC"), button -> {
            this.scroll = Math.min(Math.max(0, this.visible.size() - this.listRows()), this.scroll + this.listRows());
            this.rebuild();
        }).bounds(118, listBottom, 40, 16).build());

        this.newTableBox = new EditBox(this.font, 8, listBottom + 20, LIST_WIDTH, 16,
                Component.translatable("randombox.editor.new_id"));
        this.newTableBox.setMaxLength(128);
        this.newTableBox.setValue(lastNewId);
        this.newTableBox.setHint(Component.literal("randombox:my_table"));
        this.newTableBox.setResponder(value -> lastNewId = value);
        this.addRenderableWidget(this.newTableBox);

        this.addRenderableWidget(Button.builder(Component.translatable("randombox.editor.create"),
                        button -> createTable())
                .bounds(8, listBottom + 38, 72, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("randombox.editor.close"),
                        button -> this.onClose())
                .bounds(86, listBottom + 38, 72, 18).build());

        // ---- right: selected table --------------------------------------------------------
        BoxLootTable table = this.selected;
        if (table == null) {
            return;
        }
        int right = 168;
        int poolRow = 24;
        int poolCount = table.pools().size();
        for (int i = 0; i < poolCount && i < 8; i++) {
            int index = i;
            Component label = Component.literal("#" + (i + 1))
                    .withStyle(i == this.selectedPool ? ChatFormatting.YELLOW : ChatFormatting.WHITE);
            this.addRenderableWidget(Button.builder(label, button -> {
                this.selectedPool = index;
                this.entryPage = 0;
                this.rebuild();
            }).bounds(right + i * 26, poolRow, 24, 18).build());
        }
        this.addRenderableWidget(Button.builder(Component.literal("+"), button -> {
            table.addPool();
            this.selectedPool = table.pools().size() - 1;
            this.entryPage = 0;
            this.rebuild();
        }).bounds(right + Math.min(poolCount, 8) * 26, poolRow, 24, 18).build());
        if (poolCount > 0) {
            this.addRenderableWidget(Button.builder(Component.literal("-"), button -> {
                if (!table.pools().isEmpty()) {
                    table.pools().remove(Math.min(this.selectedPool, table.pools().size() - 1));
                    this.selectedPool = 0;
                    this.entryPage = 0;
                    this.rebuild();
                }
            }).bounds(right + (Math.min(poolCount, 8) + 1) * 26, poolRow, 24, 18).build());
        }

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

        int rowTop = headerY + 34;
        int entriesPerPage = this.entriesPerPage();
        int firstEntry = this.entryPage * entriesPerPage;
        for (int i = 0; i < entriesPerPage; i++) {
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
            row.item.setResponder(value -> {
                ResourceLocation id = ResourceLocation.tryParse(value.trim());
                if (id == null) {
                    return;
                }
                Item item = LootExtractor.itemById(id);
                if (item != Items.AIR) {
                    entry.setItem(item);
                }
            });
            this.addRenderableWidget(row.item);

            row.weight = numberBox(right + 154, y, 34, entry.weight(), entry::setWeight);
            row.quality = numberBox(right + 192, y, 34, entry.quality(), entry::setQuality);
            row.min = numberBox(right + 230, y, 30, entry.minCount(), entry::setMinCount);
            row.max = numberBox(right + 264, y, 30, entry.maxCount(), entry::setMaxCount);

            this.addRenderableWidget(Button.builder(Component.literal("x"), button -> {
                pool.entries().remove(entry);
                this.rebuild();
            }).bounds(right + 298, y, 18, 18).build());

            this.rows.add(row);
        }

        int bottom = rowTop + entriesPerPage * 22 + 6;
        this.addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            if (this.entryPage > 0) {
                this.entryPage--;
            }
            this.rebuild();
        }).bounds(right, bottom, 30, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            if ((this.entryPage + 1) * this.entriesPerPage() < pool.entries().size()) {
                this.entryPage++;
            }
            this.rebuild();
        }).bounds(right + 34, bottom, 30, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("randombox.editor.add_entry"), button -> {
            pool.addEntry(Items.DIAMOND);
            this.entryPage = (pool.entries().size() - 1) / this.entriesPerPage();
            this.rebuild();
        }).bounds(right + 70, bottom, 90, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("randombox.editor.save"), button -> {
            RBNetwork.toServer(new SaveTablePacket(table, false));
            if (find(table.id()) == null) {
                this.tables.add(table);
            }
            this.status = Component.translatable("randombox.editor.saved", table.id().toString())
                    .withStyle(ChatFormatting.GREEN);
            this.refreshVisible();
        }).bounds(right + 164, bottom, 70, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("randombox.editor.delete"), button -> {
            RBNetwork.toServer(new SaveTablePacket(table, true));
            this.tables.remove(table);
            this.selected = null;
            this.status = Component.translatable("randombox.editor.deleted", table.id().toString())
                    .withStyle(ChatFormatting.RED);
            this.rebuild();
        }).bounds(right + 238, bottom, 70, 18).build());
    }

    private int entriesPerPage() {
        return Math.max(1, (this.height - 130) / 22);
    }

    private EditBox numberBox(int x, int y, int width, int value, java.util.function.IntConsumer sink) {
        EditBox box = new EditBox(this.font, x, y, width, 18, Component.literal("value"));
        box.setValue(Integer.toString(value));
        box.setFilter(text -> text.isEmpty() || text.matches("\\d{0,6}"));
        box.setResponder(text -> {
            try {
                sink.accept(Integer.parseInt(text.trim()));
            } catch (NumberFormatException ignored) {
                // half typed number, keep the old value
            }
        });
        this.addRenderableWidget(box);
        return box;
    }

    /** Opens a table that is already loaded, or asks the server to import it. */
    private void openTable(ResourceLocation id) {
        BoxLootTable table = find(id);
        if (table != null) {
            this.selected = table;
            this.selectedPool = 0;
            this.entryPage = 0;
            this.status = Component.literal(id.toString());
            this.rebuild();
            return;
        }
        this.status = Component.translatable("randombox.editor.importing", id.toString())
                .withStyle(ChatFormatting.GRAY);
        RBNetwork.toServer(new RequestEditorPacket(id));
        this.rebuild();
    }

    private void createTable() {
        String text = this.newTableBox.getValue().trim();
        if (text.isEmpty()) {
            this.status = Component.translatable("randombox.editor.need_id").withStyle(ChatFormatting.RED);
            return;
        }
        if (!text.contains(":")) {
            text = "randombox:" + text;
        }
        ResourceLocation id = ResourceLocation.tryParse(text);
        if (id == null) {
            this.status = Component.translatable("randombox.editor.bad_id", text).withStyle(ChatFormatting.RED);
            return;
        }
        BoxLootTable existing = find(id);
        if (existing != null) {
            this.selected = existing;
        } else {
            BoxLootTable table = new BoxLootTable(id);
            table.addPool().addEntry(Items.DIAMOND);
            this.tables.add(table);
            this.selected = table;
            this.status = Component.translatable("randombox.editor.created", id.toString())
                    .withStyle(ChatFormatting.GREEN);
        }
        this.selectedPool = 0;
        this.entryPage = 0;
        this.refreshVisible();
        this.rebuild();
    }

    private void rebuild() {
        this.rebuildWidgets();
    }

    private static String shorten(String text, int length) {
        return text.length() <= length ? text : "..." + text.substring(text.length() - length + 3);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < LIST_WIDTH + 16) {
            int max = Math.max(0, this.visible.size() - this.listRows());
            int next = this.scroll - (int) Math.signum(delta) * 3;
            this.scroll = Math.max(0, Math.min(max, next));
            this.rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawString(this.font, this.title, 8, 8, 0xFFFFFF);
        graphics.drawString(this.font, Component.translatable("randombox.editor.available", this.visible.size()),
                8, this.height - 12, 0x808080);

        BoxLootTable table = this.selected;
        if (table != null) {
            graphics.drawString(this.font, Component.literal(table.id().toString()), 168, 8, 0xFFD700);
            graphics.drawString(this.font, Component.translatable("randombox.editor.rolls"), 168, 53, 0xA0A0A0);
            graphics.drawString(this.font, Component.translatable("randombox.editor.header"), 168, 74, 0xA0A0A0);
        } else {
            graphics.drawString(this.font, Component.translatable("randombox.editor.hint"), 168, 24, 0xA0A0A0);
        }
        graphics.drawString(this.font, this.status, 168, this.height - 12, 0xFFFFFF);

        super.render(graphics, mouseX, mouseY, partialTick);

        // preview of the item of every entry line
        for (Row row : this.rows) {
            ItemStack stack = new ItemStack(row.entry.item());
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, row.item.getX() + 132, row.item.getY() + 1);
            }
        }
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
