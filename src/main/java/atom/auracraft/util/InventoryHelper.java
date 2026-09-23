package atom.auracraft.util;

import atom.auracraft.config.AuraCraftConfig;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Utilitaires d'interaction avec la Fabric Transfer API et l'inventaire vanilla.
 * Garantit l'atomicité des retraits et prévient les duplications d'items.
 */
public class InventoryHelper {

    /**
     * Tente d'extraire une quantité donnée d'un item depuis la liste ordonnée des conteneurs.
     * Transaction atomique via TransactionContext.
     */
    public static ItemStack extractFromContainers(List<LinkedContainer> containers, Item item, int amount) {
        if (containers == null || containers.isEmpty() || item == null || amount <= 0) {
            return ItemStack.EMPTY;
        }

        int remainingNeeded = amount;
        ItemVariant targetVariant = null;

        for (LinkedContainer container : containers) {
            Storage<ItemVariant> storage = container.storage();
            if (storage == null || !storage.supportsExtraction()) {
                continue;
            }

            // Recherche des vues contenant l'item demandé
            for (StorageView<ItemVariant> view : storage.nonEmptyViews()) {
                ItemVariant variant = view.getResource();
                if (variant.getItem() == item) {
                    if (targetVariant == null) {
                        targetVariant = variant;
                    }

                    try (Transaction transaction = Transaction.openOuter()) {
                        long extracted = storage.extract(variant, remainingNeeded, transaction);
                        if (extracted > 0) {
                            transaction.commit();
                            container.markDirty();
                            remainingNeeded -= (int) extracted;

                            if (remainingNeeded <= 0) {
                                break;
                            }
                        }
                    }
                }
            }

            if (remainingNeeded <= 0) {
                break;
            }
        }

        int totalExtracted = amount - remainingNeeded;
        if (totalExtracted > 0 && targetVariant != null) {
            return targetVariant.toStack(totalExtracted);
        }

        return ItemStack.EMPTY;
    }

    /**
     * Variante avec Holder<Item>.
     */
    public static ItemStack extractFromContainers(List<LinkedContainer> containers, Holder<Item> itemHolder, int amount) {
        if (itemHolder == null || itemHolder.value() == null) {
            return ItemStack.EMPTY;
        }
        return extractFromContainers(containers, itemHolder.value(), amount);
    }

    /**
     * Agrège l'ensemble des items présents dans les conteneurs pour le carnet de recettes.
     */
    public static List<ItemStack> aggregateItems(List<LinkedContainer> containers) {
        if (containers == null || containers.isEmpty()) {
            return Collections.emptyList();
        }

        Map<ItemVariant, Long> totalCounts = new LinkedHashMap<>();

        for (LinkedContainer container : containers) {
            Storage<ItemVariant> storage = container.storage();
            if (storage == null) {
                continue;
            }

            for (StorageView<ItemVariant> view : storage.nonEmptyViews()) {
                if (view.isResourceBlank()) {
                    continue;
                }
                ItemVariant variant = view.getResource();
                long amount = view.getAmount();
                if (amount > 0) {
                    totalCounts.merge(variant, amount, Long::sum);
                }
            }
        }

        List<ItemStack> result = new ArrayList<>();
        for (Map.Entry<ItemVariant, Long> entry : totalCounts.entrySet()) {
            ItemVariant variant = entry.getKey();
            long count = entry.getValue();

            int maxStack = variant.getItem().getDefaultMaxStackSize();
            while (count > 0) {
                int stackAmount = (int) Math.min(count, maxStack);
                result.add(variant.toStack(stackAmount));
                count -= stackAmount;
            }
        }

        return result;
    }

    /**
     * Réapprovisionne un slot de la grille de craft après consommation d'un ingrédient,
     * selon la priorité configurée (PLAYER_FIRST ou CONTAINERS_FIRST).
     */
    public static void replenishSlot(CraftingContainer craftSlots, int slotIndex, Item neededItem,
                                     Player player, List<LinkedContainer> containers,
                                     AuraCraftConfig.PullPriority priority) {
        if (neededItem == null || player == null) {
            return;
        }

        if (priority == AuraCraftConfig.PullPriority.CONTAINERS_FIRST) {
            // Tente les conteneurs en premier
            ItemStack extracted = extractFromContainers(containers, neededItem, 1);
            if (!extracted.isEmpty()) {
                putInSlot(craftSlots, slotIndex, extracted);
                return;
            }
            // Puis l'inventaire du joueur
            if (pullFromPlayerInventory(player, neededItem, craftSlots, slotIndex)) {
                return;
            }
        } else {
            // PLAYER_FIRST par défaut
            if (pullFromPlayerInventory(player, neededItem, craftSlots, slotIndex)) {
                return;
            }
            // Puis les conteneurs du plus proche au plus éloigné
            ItemStack extracted = extractFromContainers(containers, neededItem, 1);
            if (!extracted.isEmpty()) {
                putInSlot(craftSlots, slotIndex, extracted);
            }
        }
    }

    private static boolean pullFromPlayerInventory(Player player, Item neededItem, CraftingContainer craftSlots, int slotIndex) {
        Inventory inv = player.getInventory();
        int invSlot = inv.findSlotMatchingCraftingIngredient(neededItem.builtInRegistryHolder(), ItemStack.EMPTY);
        if (invSlot != -1) {
            ItemStack taken = inv.removeItem(invSlot, 1);
            if (!taken.isEmpty()) {
                putInSlot(craftSlots, slotIndex, taken);
                inv.setChanged();
                return true;
            }
        }
        return false;
    }

    private static void putInSlot(CraftingContainer craftSlots, int slotIndex, ItemStack stack) {
        ItemStack current = craftSlots.getItem(slotIndex);
        if (current.isEmpty()) {
            craftSlots.setItem(slotIndex, stack);
        } else if (ItemStack.isSameItemSameComponents(current, stack)) {
            current.grow(stack.getCount());
        }
    }

    /**
     * Tente de réinsérer un ItemStack dans les conteneurs liés.
     * Retourne les items restants qui n'ont pas pu être insérés (ou ItemStack.EMPTY si tout est rentré).
     */
    public static ItemStack insertIntoContainers(List<LinkedContainer> containers, ItemStack stack) {
        if (containers == null || containers.isEmpty() || stack.isEmpty()) {
            return stack;
        }

        ItemVariant variant = ItemVariant.of(stack);
        long remaining = stack.getCount();

        for (LinkedContainer container : containers) {
            Storage<ItemVariant> storage = container.storage();
            if (storage == null || !storage.supportsInsertion()) {
                continue;
            }

            try (Transaction transaction = Transaction.openOuter()) {
                long inserted = storage.insert(variant, remaining, transaction);
                if (inserted > 0) {
                    transaction.commit();
                    container.markDirty();
                    remaining -= inserted;
                    if (remaining <= 0) {
                        return ItemStack.EMPTY;
                    }
                }
            }
        }

        return variant.toStack((int) remaining);
    }

    /**
     * Simule sans modifier l'état si les conteneurs peuvent accepter les items spécifiés.
     */
    public static boolean canContainersAccept(List<LinkedContainer> containers, ItemStack stack) {
        if (containers == null || containers.isEmpty() || stack.isEmpty()) {
            return false;
        }

        ItemVariant variant = ItemVariant.of(stack);
        long remaining = stack.getCount();

        try (Transaction transaction = Transaction.openOuter()) {
            for (LinkedContainer container : containers) {
                Storage<ItemVariant> storage = container.storage();
                if (storage == null || !storage.supportsInsertion()) {
                    continue;
                }

                long inserted = storage.insert(variant, remaining, transaction);
                remaining -= inserted;
                if (remaining <= 0) {
                    // Tout rentre en simulation (pas de commit = aucun impact sur le monde)
                    return true;
                }
            }
        }

        return false;
    }
}
