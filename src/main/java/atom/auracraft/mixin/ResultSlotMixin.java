package atom.auracraft.mixin;

import atom.auracraft.config.AuraCraftConfig;
import atom.auracraft.util.AuraCraftMenuAccess;
import atom.auracraft.util.InventoryHelper;
import atom.auracraft.util.LinkedContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {

    @Shadow
    @Final
    private CraftingContainer craftSlots;

    @Shadow
    @Final
    private Player player;

    @Unique
    private final Map<Integer, Item> auracraft$beforeItems = new HashMap<>();

    @Inject(method = "onTake", at = @At("HEAD"))
    private void auracraft$beforeTake(Player player, ItemStack stack, CallbackInfo ci) {
        this.auracraft$beforeItems.clear();
        for (int i = 0; i < this.craftSlots.getContainerSize(); i++) {
            ItemStack slotStack = this.craftSlots.getItem(i);
            if (!slotStack.isEmpty()) {
                this.auracraft$beforeItems.put(i, slotStack.getItem());
            }
        }
    }

    @Inject(method = "onTake", at = @At("TAIL"))
    private void auracraft$afterTake(Player player, ItemStack stack, CallbackInfo ci) {
        if (player.level().isClientSide()) {
            return;
        }

        if (player.containerMenu instanceof AuraCraftMenuAccess access) {
            List<LinkedContainer> containers = access.auracraft$getLinkedContainers();
            if (containers.isEmpty()) {
                return;
            }

            AuraCraftConfig.PullPriority priority = AuraCraftConfig.get().getPullPriority();
            boolean changed = false;

            for (Map.Entry<Integer, Item> entry : this.auracraft$beforeItems.entrySet()) {
                int slotIndex = entry.getKey();
                Item originalItem = entry.getValue();

                ItemStack current = this.craftSlots.getItem(slotIndex);
                // Si l'ingrédient a été consommé (slot vidé ou du même type mais diminué)
                if (current.isEmpty() || current.getItem() == originalItem) {
                    InventoryHelper.replenishSlot(this.craftSlots, slotIndex, originalItem, player, containers, priority);
                    changed = true;
                }
            }

            if (changed) {
                this.craftSlots.setChanged();
                player.containerMenu.slotsChanged(this.craftSlots);
            }
        }
    }
}
