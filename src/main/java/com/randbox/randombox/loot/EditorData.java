package com.randbox.randombox.loot;

import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

public final class EditorData extends SavedData {
    public record Entry(ResourceLocation item,int weight){}
    private final Map<ResourceLocation,List<Entry>> entries=new HashMap<>();
    public static EditorData get(ServerLevel level){return level.getDataStorage().computeIfAbsent(EditorData::load,EditorData::new,"randombox_tables");}
    public void add(ResourceLocation table,ResourceLocation item,int weight){entries.computeIfAbsent(table,k->new ArrayList<>()).add(new Entry(item,Math.max(1,weight)));setDirty();}
    public List<Entry> entries(ResourceLocation table){return entries.getOrDefault(table,List.of());}
    public static EditorData load(CompoundTag root){EditorData d=new EditorData(); ListTag list=root.getList("Entries",Tag.TAG_COMPOUND); for(Tag t:list){CompoundTag c=(CompoundTag)t; ResourceLocation table=ResourceLocation.tryParse(c.getString("Table")), item=ResourceLocation.tryParse(c.getString("Item"));if(table!=null&&item!=null)d.add(table,item,c.getInt("Weight"));}return d;}
    @Override public CompoundTag save(CompoundTag root){ListTag list=new ListTag();entries.forEach((table,es)->es.forEach(e->{CompoundTag c=new CompoundTag();c.putString("Table",table.toString());c.putString("Item",e.item.toString());c.putInt("Weight",e.weight);list.add(c);}));root.put("Entries",list);return root;}
}
