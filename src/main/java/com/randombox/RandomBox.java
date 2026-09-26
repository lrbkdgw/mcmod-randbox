package com.randombox;
import com.randombox.command.RandomBoxCommand;
import com.randombox.loot.LootManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
@Mod(RandomBox.MODID)
public class RandomBox {
 public static final String MODID="randombox"; public static final LootManager LOOT=new LootManager();
 public RandomBox(){ MinecraftForge.EVENT_BUS.register(this); }
 @SubscribeEvent public void commands(RegisterCommandsEvent e){ RandomBoxCommand.register(e.getDispatcher()); }
 @SubscribeEvent public void click(PlayerInteractEvent.RightClickBlock e){
  if(e.getLevel().isClientSide || !(e.getLevel().getBlockEntity(e.getPos()) instanceof ChestBlockEntity chest)) return;
  if(LOOT.isOpening(e.getPos())) { e.setCanceled(true); return; }
  if(!chest.getPersistentData().getBoolean("randombox_opened")){ e.setCanceled(true); LOOT.begin((ServerLevel)e.getLevel(),e.getPos(),e.getEntity(),chest); }
 }
 @SubscribeEvent public void tick(TickEvent.LevelTickEvent e){ if(e.phase==TickEvent.Phase.END && e.level instanceof ServerLevel s) LOOT.tick(s); }
}
