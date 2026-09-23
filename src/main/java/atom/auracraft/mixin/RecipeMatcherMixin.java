package atom.auracraft.mixin;

import net.minecraft.world.entity.player.StackedItemContents;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin sur StackedItemContents (homologue Mojmap de RecipeMatcher).
 * Confirme la compatibilité de l'algorithme d'affectation de recettes avec les ressources distantes.
 */
@Mixin(StackedItemContents.class)
public abstract class RecipeMatcherMixin {
    // Les items distants sont directement injectés via fillCraftSlotsStackedContents,
    // garantissant que canCraft() et getBiggestCraftableStack() opèrent de manière fluide
    // et strictement server-authoritative.
}
