package atom.auracraft.util;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Représente un conteneur lié éligible à portée de la table de craft.
 */
public record LinkedContainer(
        BlockPos pos,
        Storage<ItemVariant> storage,
        BlockEntity blockEntity,
        double distanceSq
) {
    public void markDirty() {
        if (blockEntity != null && !blockEntity.isRemoved()) {
            blockEntity.setChanged();
        }
    }
}
