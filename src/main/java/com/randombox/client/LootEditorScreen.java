package com.randombox.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

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
 * <p>The whole layout is computed from {@link #width} / {@link #height} so nothing can leave the
 * screen, whatever the window size or the GUI scale is.
 */
public class LootEditorScreen extends Screen {
    private static final int MARGIN = 6;
    private static final int ROW_HEIGHT = 14;
    private static final int ENTRY_HEIGHT = 20;
    private static final int GAP = 2;

    /** Kept between reopenings (the screen is recreated whenever the server answers). */
    private static String lastFilter = "";
    private static String lastNewId = "";

    private final List<BoxLootTable> tables;
    private final List<ResourceLocation> available;
    private final List<ResourceLocation> visible = new ArrayList<>();
    private final boolean qualityLocked;

    private EditBox searchBox;
    private EditBox newTableBox;
    private final List<Row> rows = new ArrayList<>();

    private int scroll;
    private int entryPage;
    private int selectedPool;
    private BoxLootTable selected;
    private Component status = Component.empty();
    private boolean listDirty;

    // layout, recomputed in init()
    private int listWidth;
    private int panelX;
    private int panelWidth;
    private int listTop;
    private int listBottom;
    private int entryTop;

    public LootEditorScreen(List<BoxLootTable> tables, List<ResourceLocation> available) {
        this(tables, available, null, true);
    }

    public LootEditorScreen(List<BoxLootTable> tables, List<ResourceLocation> available,
                            ResourceLocation focus, boolean qualityLocked) {
        super(Component.translatable("randombox.screen.editor"));
        this.tables = new ArrayList<>(tables);
        this.available = new ArrayList<>(available);
        this.available.sort(ResourceLocation::compareTo);
        this.qualityLocked = qualityLocked;
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

    // ------------------------------------------------------------------ layout ----------------

    private int listRows() {
        // search box + list + page buttons + new id box + buttons + status line all have to fit
        int bottomBlock = 16 + 4 + 16 + 4 + 18 + 12 + MARGIN;
        return Math.max(1, (this.height - this.listTop - bottomBlock) / ROW_HEIGHT);
    }

    private int entriesPerPage() {
        int bottomBlock = 18 + 4 + 12 + MARGIN;
        return Math.max(1, (this.height - this.entryTop - bottomBlock) / ENTRY_HEIGHT);
    }

    private void refreshVisible() {
        this.visible.clear();
        String filter = (this.searchBox == null ? lastFilter : this.searchBox.getValue())
                .toLowerCase(Locale.ROOT).trim();
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
        this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, this.visible.size() - this.listRows())));
    }

    @Override
    protected void init() {
        this.rows.clear();

        this.listWidth = Math.max(70, Math.min(150, this.width / 3 - 2 * MARGIN));
        this.panelX = MARGIN + this.listWidth + MARGIN;
        this.panelWidth = Math.max(80, this.width - this.panelX - MARGIN);
        this.listTop = 34;
        this.entryTop = 68;

        // ---- left: search + table list ----------------------------------------------------
        this.searchBox = new EditBox(this.font, MARGIN, 16, this.listWidth, 16,
                Component.translatable("randombox.editor.search"));
        this.searchBox.setMaxLength(128);
        this.searchBox.setValue(lastFilter);
        this.searchBox.setHint(Component.translatable("randombox.editor.search"));
        this.searchBox.setResponder(value -> {
            lastFilter = value;
            this.scroll = 0;
            // rebuilding right inside the responder would destroy the box that is being typed in,
            // so the list is refreshed on the next client tick instead
            this.listDirty = true;
        });
        this.addRenderableWidget(this.searchBox);

        this.refreshVisible();

        int rowsShown = this.listRows();
        for (int i = 0; i < rowsShown; i++) {
            int index = this.scroll + i;
            if (index >= this.visible.size()) {
                break;
            }
            ResourceLocation id = this.visible.get(index);
            boolean loaded = find(id) != null;
            Component label = Component.literal((loaded ? "* " : "") + clip(id.toString(), this.listWidth - 8))
                    .withStyle(loaded ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
            this.addRenderableWidget(Button.builder(label, button -> openTable(id))
                    .bounds(MARGIN, this.listTop + i * ROW_HEIGHT, this.listWidth, ROW_HEIGHT - 1).build());
        }

        this.listBottom = this.listTop + rowsShown * ROW_HEIGHT + 2;
        int halfList = (this.listWidth - 4) / 2;
        this.addRenderableWidget(Button.builder(Component.literal("\u25B2"), button -> {
            this.scroll = Math.max(0, this.scroll - this.listRows());
            this.rebuild();
        }).bounds(MARGIN, this.listBottom, halfList, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal("\u25BC"), button -> {
            this.scroll = Math.min(Math.max(0, this.visible.size() - this.listRows()), this.scroll + this.listRows());
            this.rebuild();
        }).bounds(MARGIN + halfList + 4, this.listBottom, halfList, 16).build());

        this.newTableBox = new EditBox(this.font, MARGIN, this.listBottom + 20, this.listWidth, 16,
                Component.translatable("randombox.editor.new_id"));
        this.newTableBox.setMaxLength(128);
        this.newTableBox.setValue(lastNewId);
        this.newTableBox.setHint(Component.literal("randombox:my_table"));
        this.newTableBox.setResponder(value -> lastNewId = value);
        this.addRenderableWidget(this.newTableBox);

        this.addRenderableWidget(Button.builder(Component.translatable("randombox.editor.create"),
                        button -> createTable())
                .bounds(MARGIN, this.listBottom + 40, halfList, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("randombox.editor.close"),
                        button -> this.onClose())
                .bounds(MARGIN + halfList + 4, this.listBottom + 40, halfList, 18).build());

        // ---- right: selected table --------------------------------------------------------
        BoxLootTable table = this.selected;
        if (table == null) {
            return;
        }
        int poolRow = 16;
        int poolCount = table.pools().size();
        int tabWidth = Math.max(16, Math.min(24, (this.panelWidth - 2 * 20 - 8) / Math.max(1, poolCount + 1)));
        int maxTabs = Math.max(1, (this.panelWidth - 2 * 20 - 8) / (tabWidth + GAP));
        int shownTabs = Math.min(poolCount, maxTabs);
        for (int i = 0; i < shownTabs; i++) {
            int index = i;
            Component label = Component.literal(Integer.toString(i + 1))
                    .withStyle(i == this.selectedPool ? ChatFormatting.YELLOW : ChatFormatting.WHITE);
            this.addRenderableWidget(Button.builder(label, button -> {
                this.selectedPool = index;
                this.entryPage = 0;
                this.rebuild();
            }).bounds(this.panelX + i * (tabWidth + GAP), poolRow, tabWidth, 16).build());
        }
        int tabsEnd = this.panelX + shownTabs * (tabWidth + GAP);
        this.addRenderableWidget(Button.builder(Component.literal("+"), button -> {
            table.addPool();
            this.selectedPool = table.pools().size() - 1;
            this.entryPage = 0;
            this.rebuild();
        }).bounds(Math.min(tabsEnd, this.panelX + this.panelWidth - 42), poolRow, 18, 16).build());
        if (poolCount > 0) {
            this.addRenderableWidget(Button.builder(Component.literal("-"), button -> {
                if (!table.pools().isEmpty()) {
                    table.pools().remove(Math.min(this.selectedPool, table.pools().size() - 1));
                    this.selectedPool = 0;
                    this.entryPage = 0;
                    this.rebuild();
                }
            }).bounds(Math.min(tabsEnd + 20, this.panelX + this.panelWidth - 20), poolRow, 18, 16).build());
        }

        if (this.selectedPool >= table.pools().size()) {
            this.selectedPool = Math.max(0, table.pools().size() - 1);
        }
        if (table.pools().isEmpty()) {
            return;
        }
        BoxLootTable.Pool pool = table.pools().get(this.selectedPool);

        int rollsY = poolRow + 20;
        EditBox rollsBox = new EditBox(this.font, this.panelX + 44, rollsY, 44, 16,
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

        // entry table columns
        int deleteWidth = 16;
        int numWidth = Math.max(18, Math.min(32, (this.panelWidth - 60 - deleteWidth - 5 * GAP) / 4));
        int itemWidth = Math.max(48, this.panelWidth - 4 * (numWidth + GAP) - deleteWidth - GAP);
        int entriesPerPage = this.entriesPerPage();
        if (this.entryPage * entriesPerPage >= pool.entries().size()) {
            this.entryPage = Math.max(0, (pool.entries().size() - 1) / entriesPerPage);
        }
        int firstEntry = this.entryPage * entriesPerPage;
        for (int i = 0; i < entriesPerPage; i++) {
            int index = firstEntry + i;
            if (index >= pool.entries().size()) {
                break;
            }
            BoxLootTable.Entry entry = pool.entries().get(index);
            int y = this.entryTop + i * ENTRY_HEIGHT;
            int x = this.panelX;
            Row row = new Row(entry);

            row.item = new EditBox(this.font, x, y, itemWidth, 16, Component.literal("item"));
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
            x += itemWidth + GAP;

            row.weight = numberBox(x, y, numWidth, entry.weight(), entry::setWeight, true);
            x += numWidth + GAP;
            row.quality = numberBox(x, y, numWidth, entry.quality(), entry::setQuality, !this.qualityLocked);
            x += numWidth + GAP;
            row.min = numberBox(x, y, numWidth, entry.minCount(), entry::setMinCount, true);
            x += numWidth + GAP;
            row.max = numberBox(x, y, numWidth, entry.maxCount(), entry::setMaxCount, true);
            x += numWidth + GAP;

            this.addRenderableWidget(Button.builder(Component.literal("x"), button -> {
                pool.entries().remove(entry);
                this.rebuild();
            }).bounds(Math.min(x, this.panelX + this.panelWidth - deleteWidth), y, deleteWidth, 16).build());

            this.rows.add(row);
        }

        // bottom button bar, always inside the screen
        int bottom = Math.min(this.entryTop + entriesPerPage * ENTRY_HEIGHT + 4, this.height - 18 - 14);
        int arrow = 18;
        int actionWidth = Math.max(30, (this.panelWidth - 2 * (arrow + GAP) - 2 * GAP) / 3);
        int x = this.panelX;
        this.addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            if (this.entryPage > 0) {
                this.entryPage--;
            }
            this.rebuild();
        }).bounds(x, bottom, arrow, 18).build());
        x += arrow + GAP;
        this.addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            if ((this.entryPage + 1) * this.entriesPerPage() < pool.entries().size()) {
                this.entryPage++;
            }
            this.rebuild();
        }).bounds(x, bottom, arrow, 18).build());
        x += arrow + GAP;
        this.addRenderableWidget(Button.builder(Component.translatable("randombox.editor.add_entry"), button -> {
            pool.addEntry(Items.DIAMOND);
            this.entryPage = (pool.entries().size() - 1) / this.entriesPerPage();
            this.rebuild();
        }).bounds(x, bottom, actionWidth, 18).build());
        x += actionWidth + GAP;
        this.addRenderableWidget(Button.builder(Component.translatable("randombox.editor.save"), button -> {
            RBNetwork.toServer(new SaveTablePacket(table, false));
            if (find(table.id()) == null) {
                this.tables.add(table);
            }
            this.status = Component.translatable("randombox.editor.saved", table.id().toString())
                    .withStyle(ChatFormatting.GREEN);
            this.refreshVisible();
        }).bounds(x, bottom, actionWidth, 18).build());
        x += actionWidth + GAP;
        this.addRenderableWidget(Button.builder(Component.translatable("randombox.editor.delete"), button -> {
            RBNetwork.toServer(new SaveTablePacket(table, true));
            this.tables.remove(table);
            this.selected = null;
            this.status = Component.translatable("randombox.editor.deleted", table.id().toString())
                    .withStyle(ChatFormatting.RED);
            this.rebuild();
        }).bounds(Math.min(x, this.panelX + this.panelWidth - actionWidth), bottom, actionWidth, 18).build());
    }

    private EditBox numberBox(int x, int y, int width, int value, IntConsumer sink, boolean editable) {
        EditBox box = new EditBox(this.font, x, y, width, 16, Component.literal("value"));
        box.setValue(Integer.toString(value));
        box.setFilter(text -> text.isEmpty() || text.matches("\\d{0,6}"));
        box.setEditable(editable);
        if (editable) {
            box.setResponder(text -> {
                try {
                    sink.accept(Integer.parseInt(text.trim()));
                } catch (NumberFormatException ignored) {
                    // half typed number, keep the old value
                }
            });
        }
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

    @Override
    public void tick() {
        super.tick();
        if (this.listDirty) {
            this.listDirty = false;
            // init() restores the text from lastFilter, so only the caret and focus need help
            this.rebuild();
            this.searchBox.moveCursorToEnd();
            this.setFocused(this.searchBox);
            this.searchBox.setFocused(true);
        }
    }

    /** Cuts a string so it never gets wider than {@code maxWidth} pixels. */
    private String clip(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String result = text;
        while (result.length() > 1 && this.font.width("..." + result) > maxWidth) {
            result = result.substring(1);
        }
        return "..." + result;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < this.panelX) {
            int max = Math.max(0, this.visible.size() - this.listRows());
            this.scroll = Math.max(0, Math.min(max, this.scroll - (int) Math.signum(delta) * 3));
            this.rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawString(this.font, Component.literal(clip(this.title.getString(), this.listWidth)),
                MARGIN, 4, 0xFFFFFF);
        graphics.drawString(this.font, Component.translatable("randombox.editor.available", this.visible.size()),
                MARGIN, this.height - 10, 0x808080);

        BoxLootTable table = this.selected;
        if (table != null) {
            graphics.drawString(this.font, Component.literal(clip(table.id().toString(), this.panelWidth)),
                    this.panelX, 4, 0xFFD700);
            graphics.drawString(this.font, Component.translatable("randombox.editor.rolls"),
                    this.panelX, 40, 0xA0A0A0);
            Component header = this.qualityLocked
                    ? Component.translatable("randombox.editor.header_locked")
                    : Component.translatable("randombox.editor.header");
            graphics.drawString(this.font, Component.literal(clip(header.getString(), this.panelWidth)),
                    this.panelX, 58, 0xA0A0A0);
        } else {
            graphics.drawString(this.font,
                    Component.literal(clip(Component.translatable("randombox.editor.hint").getString(), this.panelWidth)),
                    this.panelX, 20, 0xA0A0A0);
        }
        graphics.drawString(this.font, Component.literal(clip(this.status.getString(), this.panelWidth)),
                this.panelX, this.height - 10, 0xFFFFFF);

        super.render(graphics, mouseX, mouseY, partialTick);

        // small item preview at the right end of every item field
        for (Row row : this.rows) {
            ItemStack stack = new ItemStack(row.entry.item());
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, row.item.getX() + row.item.getWidth() - 18, row.item.getY());
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
