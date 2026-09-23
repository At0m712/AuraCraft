package atom.auracraft.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public interface AuraCraftMenuAccess {
    List<LinkedContainer> auracraft$getLinkedContainers();

    void auracraft$refreshContainers(boolean force);

    int auracraft$getLinkedContainerCount();

    List<BlockPos> auracraft$getLinkedChestPositions();

    List<ItemStack> auracraft$getClientAggregatedItems();

    void auracraft$setClientContainerData(int count, List<ItemStack> items, List<BlockPos> chestPositions);

    BlockPos auracraft$getTablePos();

    net.minecraft.world.entity.player.StackedItemContents auracraft$getPlayerOnlyStackedContents();
}
