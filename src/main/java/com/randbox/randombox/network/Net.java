package com.randbox.randombox.network;

import com.randbox.randombox.RandomBox;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Net {
 public static final String VERSION="1"; public static SimpleChannel CHANNEL;
 public static void init(){CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(RandomBox.ID,"main"),()->VERSION,VERSION::equals,VERSION::equals);int i=0;CHANNEL.registerMessage(i++,DrawPacket.class,DrawPacket::encode,DrawPacket::decode,DrawPacket::handle);CHANNEL.registerMessage(i++,FinishPacket.class,FinishPacket::encode,FinishPacket::decode,FinishPacket::handle);CHANNEL.registerMessage(i++,OpenEditorPacket.class,OpenEditorPacket::encode,OpenEditorPacket::decode,OpenEditorPacket::handle);CHANNEL.registerMessage(i,SaveEntryPacket.class,SaveEntryPacket::encode,SaveEntryPacket::decode,SaveEntryPacket::handle);}
}
