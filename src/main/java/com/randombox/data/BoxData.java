package com.randombox.data;

import com.randombox.Rarity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Per-chest state tracked by the mod. */
public class BoxData {
    private final BlockPos pos;
    private Rarity rarity;
    private boolean opened;
    private ResourceLocation lootTable;

    public BoxData(BlockPos pos, Rarity rarity, boolean opened, ResourceLocation lootTable) {
        this.pos = pos.immutable();
        this.rarity = rarity;
        this.opened = opened;
        this.lootTable = lootTable;
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

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", this.pos.getX());
        tag.putInt("Y", this.pos.getY());
        tag.putInt("Z", this.pos.getZ());
        tag.putInt("Rarity", this.rarity.id());
        tag.putBoolean("Opened", this.opened);
        if (this.lootTable != null) {
            tag.putString("LootTable", this.lootTable.toString());
        }
        return tag;
    }

    public static BoxData load(CompoundTag tag) {
        BlockPos pos = new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
        Rarity rarity = Rarity.byId(tag.getInt("Rarity"));
        boolean opened = tag.getBoolean("Opened");
        ResourceLocation table = tag.contains("LootTable") ? new ResourceLocation(tag.getString("LootTable")) : null;
        return new BoxData(pos, rarity, opened, table);
    }
}
