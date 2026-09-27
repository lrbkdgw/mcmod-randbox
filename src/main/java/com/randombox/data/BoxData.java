package com.randombox.data;

import java.util.ArrayList;
import java.util.List;

import com.randombox.Rarity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Per-chest state tracked by the mod. */
public class BoxData {
    private final BlockPos pos;
    private Rarity rarity;
    private boolean opened;
    private ResourceLocation lootTable;
    /** Whether Treasure Ascension has already rolled for this box. */
    private boolean ascensionFixed;
    /** Prizes fixed by a Warden Tentacle preview, if any. */
    private final List<ItemStack> cachedPrizes = new ArrayList<>();

    public BoxData(BlockPos pos, Rarity rarity, boolean opened, ResourceLocation lootTable) {
        this(pos, rarity, opened, lootTable, false);
    }

    public BoxData(BlockPos pos, Rarity rarity, boolean opened, ResourceLocation lootTable,
                   boolean ascensionFixed) {
        this.pos = pos.immutable();
        this.rarity = rarity;
        this.opened = opened;
        this.lootTable = lootTable;
        this.ascensionFixed = ascensionFixed;
    }

    public BlockPos pos() {
        return this.pos;
    }

    public Rarity rarity() {
        return this.rarity;
    }

    public void setRarity(Rarity rarity) {
        this.rarity = rarity;
    }

    public boolean opened() {
        return this.opened;
    }

    public void setOpened(boolean opened) {
        this.opened = opened;
    }

    public ResourceLocation lootTable() {
        return this.lootTable;
    }

    public void setLootTable(ResourceLocation lootTable) {
        this.lootTable = lootTable;
    }

    public boolean ascensionFixed() {
        return this.ascensionFixed;
    }

    public void setAscensionFixed(boolean fixed) {
        this.ascensionFixed = fixed;
    }

    public boolean hasCachedPrizes() {
        return !this.cachedPrizes.isEmpty();
    }

    public List<ItemStack> cachedPrizes() {
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack stack : this.cachedPrizes) {
            if (!stack.isEmpty()) {
                copy.add(stack.copy());
            }
        }
        return copy;
    }

    public void setCachedPrizes(List<ItemStack> prizes) {
        this.cachedPrizes.clear();
        for (ItemStack stack : prizes) {
            if (!stack.isEmpty()) {
                this.cachedPrizes.add(stack.copy());
            }
        }
    }

    public void clearCachedPrizes() {
        this.cachedPrizes.clear();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", this.pos.getX());
        tag.putInt("Y", this.pos.getY());
        tag.putInt("Z", this.pos.getZ());
        tag.putInt("Rarity", this.rarity.id());
        tag.putBoolean("Opened", this.opened);
        tag.putBoolean("AscensionFixed", this.ascensionFixed);
        if (this.lootTable != null) {
            tag.putString("LootTable", this.lootTable.toString());
        }
        if (!this.cachedPrizes.isEmpty()) {
            ListTag prizes = new ListTag();
            for (ItemStack stack : this.cachedPrizes) {
                if (!stack.isEmpty()) {
                    prizes.add(stack.save(new CompoundTag()));
                }
            }
            tag.put("CachedPrizes", prizes);
        }
        return tag;
    }

    public static BoxData load(CompoundTag tag) {
        BlockPos pos = new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
        Rarity rarity = Rarity.byId(tag.getInt("Rarity"));
        boolean opened = tag.getBoolean("Opened");
        ResourceLocation table = tag.contains("LootTable") ? new ResourceLocation(tag.getString("LootTable")) : null;
        BoxData data = new BoxData(pos, rarity, opened, table, tag.getBoolean("AscensionFixed"));
        ListTag prizes = tag.getList("CachedPrizes", Tag.TAG_COMPOUND);
        for (int i = 0; i < prizes.size(); i++) {
            ItemStack stack = ItemStack.of(prizes.getCompound(i));
            if (!stack.isEmpty()) {
                data.cachedPrizes.add(stack);
            }
        }
        return data;
    }
}
