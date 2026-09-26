package com.randombox.item;

import com.randombox.event.ReelManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A six-use tool that previews and optionally rerolls an unopened Random Box result. */
public class WardenTentacleItem extends Item {
    public WardenTentacleItem(Properties properties) {
        super(properties);
    }

    /**
     * Handles right-clicking an unopened loot chest with the tentacle.
     *
     * @return true when vanilla chest opening should be cancelled
     */
    public static boolean tryPreview(ServerPlayer player, BlockPos pos, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty() || !stack.is(RandomBoxItems.WARDEN_TENTACLE.get())) {
            return false;
        }
        return ReelManager.preview(player, pos, hand, stack);
    }
}
