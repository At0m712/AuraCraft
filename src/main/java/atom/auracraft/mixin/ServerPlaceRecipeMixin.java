package atom.auracraft.mixin;

import atom.auracraft.config.AuraCraftConfig;
import atom.auracraft.util.AuraCraftMenuAccess;
import atom.auracraft.util.InventoryHelper;
import atom.auracraft.util.LinkedContainer;
import net.minecraft.core.Holder;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ServerPlaceRecipe.class)
public abstract class ServerPlaceRecipeMixin {

    @Shadow
    @Final
    private Inventory inventory;

    @Shadow
    @Final
    private List<Slot> slotsToClear;

    @Inject(method = "moveItemToGrid", at = @At("HEAD"), cancellable = true)
    private void auracraft$moveItemFromContainers(Slot slot, Holder<Item> item, int count, CallbackInfoReturnable<Integer> cir) {
        Player player = this.inventory.player;
        if (player == null || !(player.containerMenu instanceof AuraCraftMenuAccess access)) {
            return;
        }

        List<LinkedContainer> containers = access.auracraft$getLinkedContainers();
        if (containers.isEmpty()) {
            return;
        }

        AuraCraftConfig.PullPriority priority = AuraCraftConfig.get().getPullPriority();
        ItemStack currentInSlot = slot.getItem();

        // 1. Si la priorité est aux conteneurs distants
        if (priority == AuraCraftConfig.PullPriority.CONTAINERS_FIRST) {
            ItemStack extracted = InventoryHelper.extractFromContainers(containers, item, count);
            if (!extracted.isEmpty()) {
                if (currentInSlot.isEmpty()) {
                    slot.set(extracted);
                } else {
                    currentInSlot.grow(extracted.getCount());
                }
                cir.setReturnValue(count - extracted.getCount());
                return;
            }
        }

        // 2. Si la priorité est au joueur (PLAYER_FIRST) ou si les conteneurs n'avaient pas l'item
        int invSlot = this.inventory.findSlotMatchingCraftingIngredient(item, currentInSlot);
        if (invSlot == -1) {
            // L'item n'est pas dans l'inventaire du joueur, tentative dans les conteneurs
            ItemStack extracted = InventoryHelper.extractFromContainers(containers, item, count);
            if (!extracted.isEmpty()) {
                if (currentInSlot.isEmpty()) {
                    slot.set(extracted);
                } else {
                    currentInSlot.grow(extracted.getCount());
                }
                cir.setReturnValue(count - extracted.getCount());
            }
        }
    }

    /**
     * Permet de vider la grille de craft vers les conteneurs environnants au lieu de saturer
     * l'inventaire du joueur ou de jeter les items au sol.
     */
    @Inject(method = "clearGrid", at = @At("HEAD"))
    private void auracraft$clearGridToContainers(CallbackInfo ci) {
        Player player = this.inventory.player;
        if (player == null || !(player.containerMenu instanceof AuraCraftMenuAccess access)) {
            return;
        }

        List<LinkedContainer> containers = access.auracraft$getLinkedContainers();
        if (containers.isEmpty()) {
            return;
        }

        for (Slot slot : this.slotsToClear) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                ItemStack remaining = InventoryHelper.insertIntoContainers(containers, stack);
                slot.set(remaining);
            }
        }
    }

    /**
     * Si l'inventaire du joueur est totalement plein lors du changement de recette dans le Recipe Book,
     * vérifie si les conteneurs environnants peuvent accueillir les items actuellement posés sur la grille.
     */
    @Inject(method = "testClearGrid", at = @At("RETURN"), cancellable = true)
    private void auracraft$testClearGridWithContainers(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            Player player = this.inventory.player;
            if (player == null || !(player.containerMenu instanceof AuraCraftMenuAccess access)) {
                return;
            }

            List<LinkedContainer> containers = access.auracraft$getLinkedContainers();
            if (containers.isEmpty()) {
                return;
            }

            boolean allCanFit = true;
            for (Slot slot : this.slotsToClear) {
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty() && !InventoryHelper.canContainersAccept(containers, stack)) {
                    allCanFit = false;
                    break;
                }
            }

            if (allCanFit) {
                cir.setReturnValue(true);
            }
        }
    }
}
