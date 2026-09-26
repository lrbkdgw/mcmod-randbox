package com.randombox.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.randombox.Rarity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

/** Stores the rarity / opened state of every known box of a dimension. */
public class BoxSavedData extends SavedData {
    public static final String NAME = "randombox_boxes";

    private final Map<BlockPos, BoxData> boxes = new HashMap<>();

    public static BoxSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(BoxSavedData::load, BoxSavedData::new, NAME);
    }

    public static BoxSavedData load(CompoundTag tag) {
        BoxSavedData data = new BoxSavedData();
        ListTag list = tag.getList("Boxes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            BoxData box = BoxData.load(list.getCompound(i));
            data.boxes.put(box.pos(), box);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (BoxData box : this.boxes.values()) {
            list.add(box.save());
        }
        tag.put("Boxes", list);
        return tag;
    }

    public BoxData getOrCreate(BlockPos pos, ResourceLocation lootTable, RandomSource random) {
        BoxData existing = this.boxes.get(pos.immutable());
        if (existing != null) {
            if (existing.lootTable() == null && lootTable != null) {
                existing.setLootTable(lootTable);
                this.setDirty();
            }
            return existing;
        }
        BoxData created = new BoxData(pos, Rarity.random(random), false, lootTable);
        this.boxes.put(created.pos(), created);
        this.setDirty();
        return created;
    }

    public void put(BoxData data) {
        this.boxes.put(data.pos(), data);
        this.setDirty();
    }

    public BoxData get(BlockPos pos) {
        return this.boxes.get(pos.immutable());
    }

    public void remove(BlockPos pos) {
        if (this.boxes.remove(pos.immutable()) != null) {
            this.setDirty();
        }
    }

    public Collection<BoxData> all() {
        return this.boxes.values();
    }

    public List<BoxData> near(BlockPos center, int radius) {
        List<BoxData> result = new ArrayList<>();
        int sq = radius * radius;
        for (BoxData box : this.boxes.values()) {
            if (box.pos().distSqr(center) <= sq) {
                result.add(box);
            }
        }
        return result;
    }

    public List<BoxData> unopenedNear(BlockPos center, int radius) {
        List<BoxData> result = new ArrayList<>();
        int sq = radius * radius;
        for (BoxData box : this.boxes.values()) {
            if (!box.opened() && box.pos().distSqr(center) <= sq) {
                result.add(box);
            }
        }
        return result;
    }
}
