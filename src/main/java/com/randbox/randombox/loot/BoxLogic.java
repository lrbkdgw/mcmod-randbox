package com.randbox.randombox.loot;

import com.randbox.randombox.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;
import java.util.*;

public final class BoxLogic {
 private static final String TIER="RandomBoxTier", OPENED="RandomBoxOpened", DRAWING="RandomBoxDrawing";
 @SubscribeEvent public static void open(PlayerInteractEvent.RightClickBlock e){
  if(e.getHand()!=InteractionHand.MAIN_HAND||e.getLevel().isClientSide()||!(e.getEntity() instanceof ServerPlayer player)||!(e.getLevel().getBlockEntity(e.getPos()) instanceof ChestBlockEntity chest)||chest.getLootTable()==null)return;
  e.setCanceled(true);
  if(chest.getPersistentData().getBoolean(DRAWING))return;
  ServerLevel level=(ServerLevel)e.getLevel();ResourceLocation tableId=chest.getLootTable();BoxTier tier=chest.getPersistentData().contains(TIER)?BoxTier.byId(chest.getPersistentData().getInt(TIER)):BoxTier.roll(level.random);
  chest.getPersistentData().putInt(TIER,tier.id);chest.getPersistentData().putBoolean(DRAWING,true);
  List<ItemStack> candidates=new ArrayList<>();LootTable table=level.getServer().getLootData().getLootTable(tableId);
  LootParams params=new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,e.getPos().getCenter()).withOptionalParameter(LootContextParams.THIS_ENTITY,player).create(LootContextParamSets.CHEST);
  // Four independent generations preserve every original pool and provide a useful estimate of k.
  for(int i=0;i<4;i++)candidates.addAll(table.getRandomItems(params));
  for(EditorData.Entry custom:EditorData.get(level).entries(tableId)){Item item=ForgeRegistries.ITEMS.getValue(custom.item());if(item!=null)for(int i=0;i<custom.weight();i++)candidates.add(new ItemStack(item));}
  if(candidates.isEmpty())candidates.add(new ItemStack(Items.AIR));
  List<ItemStack> result=new ArrayList<>();int multiplier=Math.max(1,candidates.size()/16); // k/4, with four samples estimating k
  for(int n=0;n<tier.count;n++){ItemStack picked=weighted(candidates,tier,level.random).copy();picked.setCount(Math.min(picked.getMaxStackSize(),Math.max(1,picked.getCount()*multiplier)));result.add(picked);}
  chest.setLootTable(null,0);for(int i=0;i<chest.getContainerSize();i++)chest.setItem(i,ItemStack.EMPTY);for(int i=0;i<result.size()&&i<chest.getContainerSize();i++)chest.setItem(i,result.get(i));
  chest.getPersistentData().putBoolean(OPENED,true);chest.setChanged();int duration=0;for(int i=0;i<result.size();i++)duration+=12+level.random.nextInt(25); // 0.6–1.8 s/item
  Net.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new DrawPacket(e.getPos(),tier.id,result,duration));
 }
 private static ItemStack weighted(List<ItemStack> list,BoxTier tier,RandomSource r){double sum=0;for(ItemStack s:list)sum+=1+ItemQuality.of(s)*tier.bonus;double x=r.nextDouble()*sum;for(ItemStack s:list){x-=1+ItemQuality.of(s)*tier.bonus;if(x<=0)return s;}return list.get(list.size()-1);}
 @SubscribeEvent public static void particles(TickEvent.PlayerTickEvent e){if(e.phase!=TickEvent.Phase.END||e.player.level().isClientSide()||e.player.tickCount%10!=0||!(e.player instanceof ServerPlayer player))return;ServerLevel level=player.serverLevel();BlockPos base=player.blockPosition();for(BlockPos p:BlockPos.betweenClosed(base.offset(-12,-6,-12),base.offset(12,6,12))){if(!(level.getBlockEntity(p)instanceof ChestBlockEntity chest)||chest.getLootTable()==null)continue;BoxTier tier=chest.getPersistentData().contains(TIER)?BoxTier.byId(chest.getPersistentData().getInt(TIER)):BoxTier.roll(level.random);if(!chest.getPersistentData().contains(TIER)){chest.getPersistentData().putInt(TIER,tier.id);chest.setChanged();}float red=((tier.color>>16)&255)/255f,green=((tier.color>>8)&255)/255f,blue=(tier.color&255)/255f;DustParticleOptions dust=new DustParticleOptions(new Vector3f(red,green,blue),1);level.sendParticles(player,dust,false,p.getX()+.5,p.getY()+.7,p.getZ()+.5,8,.55,.35,.55,0);for(int y=1;y<=10;y++)level.sendParticles(player,dust,false,p.getX()+.5,p.getY()+y,p.getZ()+.5,1,.04,0,.04,0);}}
}
