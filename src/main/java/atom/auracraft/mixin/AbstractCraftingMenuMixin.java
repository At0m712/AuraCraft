package atom.auracraft.mixin;

import atom.auracraft.util.AuraCraftMenuAccess;
import atom.auracraft.util.InventoryHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractCraftingMenu.class)
public abstract class AbstractCraftingMenuMixin {

    @Shadow
    protected abstract Player owner();

    @Inject(method = "fillCraftSlotsStackedContents", at = @At("TAIL"))
    private void auracraft$fillContainersStackedContents(StackedItemContents contents, CallbackInfo ci) {
        if (this instanceof AuraCraftMenuAccess access) {
            Player player = this.owner();
            if (player != null && player.level().isClientSide()) {
                for (ItemStack stack : access.auracraft$getClientAggregatedItems()) {
                    contents.accountSimpleStack(stack);
                }
            } else {
                for (ItemStack stack : InventoryHelper.aggregateItems(access.auracraft$getLinkedContainers())) {
                    contents.accountSimpleStack(stack);
                }
            }
        }
    }
}
