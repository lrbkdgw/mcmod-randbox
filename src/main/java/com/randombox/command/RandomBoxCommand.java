package com.randombox.command;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.randombox.Rarity;
import com.randombox.data.BoxData;
import com.randombox.data.BoxSavedData;
import com.randombox.loot.BoxLootTable;
import com.randombox.loot.CustomLootStore;
import com.randombox.loot.ItemQuality;
import com.randombox.net.EditorDataPacket;
import com.randombox.net.RBNetwork;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootDataType;

/** {@code /RandomBox SetNewBox <pos> <quality> <lootTable>} and {@code /RandomBox GUI}. */
public final class RandomBoxCommand {
    private static final SuggestionProvider<CommandSourceStack> ITEMS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), builder);

    private static final SuggestionProvider<CommandSourceStack> LOOT_TABLES = (context, builder) ->
            SharedSuggestionProvider.suggestResource(allTables(context), builder);

    private RandomBoxCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(build("randombox"));
        // Convenience alias so the command can be typed exactly like in the documentation.
        dispatcher.register(build("RandomBox"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        return Commands.literal(name)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("SetNewBox")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("quality", IntegerArgumentType.integer(0, 5))
                                        .then(Commands.argument("lootTable", ResourceLocationArgument.id())
                                                .suggests(LOOT_TABLES)
                                                .executes(RandomBoxCommand::setNewBox)))))
                .then(Commands.literal("setnewbox")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("quality", IntegerArgumentType.integer(0, 5))
                                        .then(Commands.argument("lootTable", ResourceLocationArgument.id())
                                                .suggests(LOOT_TABLES)
                                                .executes(RandomBoxCommand::setNewBox)))))
                .then(Commands.literal("GUI").executes(RandomBoxCommand::openGui))
                .then(Commands.literal("gui").executes(RandomBoxCommand::openGui))
                .then(quality("Quality"))
                .then(quality("quality"));
    }

    /** {@code /RandomBox Quality <item> [value]} reads or sets the fixed quality of an item. */
    private static LiteralArgumentBuilder<CommandSourceStack> quality(String name) {
        return Commands.literal(name)
                .then(Commands.argument("item", ResourceLocationArgument.id())
                        .suggests(ITEMS)
                        .executes(RandomBoxCommand::getQuality)
                        .then(Commands.argument("value", IntegerArgumentType.integer(0, ItemQuality.MAX))
                                .executes(RandomBoxCommand::setQuality)));
    }

    private static Item item(CommandContext<CommandSourceStack> context) {
        ResourceLocation id = ResourceLocationArgument.getId(context, "item");
        return BuiltInRegistries.ITEM.get(id);
    }

    private static int getQuality(CommandContext<CommandSourceStack> context) {
        Item item = item(context);
        if (item == Items.AIR) {
            context.getSource().sendFailure(Component.translatable("randombox.command.unknown_item"));
            return 0;
        }
        int value = ItemQuality.get(item);
        context.getSource().sendSuccess(() -> Component.translatable("randombox.command.quality_get",
                item.getDescription(), value), false);
        return value;
    }

    private static int setQuality(CommandContext<CommandSourceStack> context) {
        Item item = item(context);
        if (item == Items.AIR) {
            context.getSource().sendFailure(Component.translatable("randombox.command.unknown_item"));
            return 0;
        }
        int value = IntegerArgumentType.getInteger(context, "value");
        ItemQuality.set(item, value);
        context.getSource().sendSuccess(() -> Component.translatable("randombox.command.quality_set",
                item.getDescription(), value), true);
        return 1;
    }

    private static int setNewBox(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        int quality = IntegerArgumentType.getInteger(context, "quality");
        ResourceLocation table = ResourceLocationArgument.getId(context, "lootTable");

        level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof RandomizableContainerBlockEntity container)) {
            source.sendFailure(Component.translatable("randombox.command.failed"));
            return 0;
        }
        container.setLootTable(table, level.getRandom().nextLong());
        container.setChanged();

        Rarity rarity = Rarity.byCommandValue(quality, level.getRandom());
        BoxSavedData saved = BoxSavedData.get(level);
        saved.put(new BoxData(pos, rarity, false, table));

        source.sendSuccess(() -> Component.translatable("randombox.command.created",
                pos.toShortString(),
                Component.translatable(rarity.translationKey()).withStyle(rarity.format()),
                table.toString()), true);
        return 1;
    }

    private static int openGui(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("randombox.command.player_only"));
            return 0;
        }
        List<BoxLootTable> tables = new ArrayList<>(CustomLootStore.all());
        for (BoxLootTable table : tables) {
            table.applyItemQuality();
        }
        List<ResourceLocation> available = new ArrayList<>(allTables(source));
        RBNetwork.toPlayer(player, new EditorDataPacket(tables, available));
        return 1;
    }

    private static List<ResourceLocation> allTables(CommandContext<CommandSourceStack> context) {
        return allTables(context.getSource());
    }

    private static List<ResourceLocation> allTables(CommandSourceStack source) {
        List<ResourceLocation> ids = new ArrayList<>(source.getServer().getLootData().getKeys(LootDataType.TABLE));
        for (BoxLootTable table : CustomLootStore.all()) {
            if (!ids.contains(table.id())) {
                ids.add(table.id());
            }
        }
        return ids;
    }
}
