package com.randbox.randombox.client;
import com.randbox.randombox.loot.BoxTier;import com.randbox.randombox.network.*;import net.minecraft.client.Minecraft;import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.screens.Screen;import net.minecraft.network.chat.Component;import net.minecraft.world.item.ItemStack;import java.util.*;
public final class DrawScreen extends Screen {
 private final DrawPacket packet;private int ticks;
 private DrawScreen(DrawPacket p){super(Component.translatable("randombox.draw"));packet=p;}
 public static void open(DrawPacket p){Minecraft.getInstance().setScreen(new DrawScreen(p));}
 @Override public void tick(){ticks++;if(ticks>=packet.duration()){Net.CHANNEL.sendToServer(new FinishPacket(packet.pos()));Minecraft.getInstance().setScreen(null);}}
 @Override public boolean shouldCloseOnEsc(){return false;} @Override public void onClose(){} @Override public boolean isPauseScreen(){return false;}
 @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);int tier=packet.tier();String title=Component.translatable("randombox.rarity."+tier).getString();int color=BoxTier.byId(tier).color;g.drawCenteredString(font,title,width/2,35,color);g.drawCenteredString(font,Component.translatable("randombox.draw"),width/2,52,0xFFFFFF);List<ItemStack> items=packet.items();int spacing=42,start=width/2-(items.size()*spacing)/2;long phase=(ticks*3L);for(int i=0;i<items.size();i++){int x=start+i*spacing,y=height/2;g.fill(x-5,y-5,x+29,y+29,0xCC111111);g.renderItem(items.get(i),x+1,y+1);g.renderItemDecorations(font,items.get(i),x+1,y+1);if(ticks<packet.duration()-10){ItemStack ghost=items.get((int)((phase+i*7)%items.size()));g.renderItem(ghost,x+1,y-30);}}g.drawCenteredString(font,(Math.min(100,ticks*100/packet.duration()))+"%",width/2,height/2+55,0xAAAAAA);}
}
