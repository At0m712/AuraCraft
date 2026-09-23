package atom.auracraft.mixin;

import atom.auracraft.util.AuraCraftMenuAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Mixin sur RecipeButton pour mettre en évidence les recettes fabricables
 * uniquement grâce aux conteneurs distants (halo + contour vert émeraude + mention tooltip).
 */
@Mixin(RecipeButton.class)
public abstract class RecipeButtonMixin {

    @Inject(method = "extractWidgetRenderState", at = @At("TAIL"))
    private void auracraft$renderChestCraftableOutline(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        RecipeButton button = (RecipeButton) (Object) this;
        if (auracraft$isCraftableOnlyViaChests(button)) {
            int x = button.getX();
            int y = button.getY();
            int w = button.getWidth();
            int h = button.getHeight();

            // Uniquement le contour vert émeraude net, sans aucun filtre opaque sur l'item
            graphics.outline(x, y, w, h, 0xFF00FF7F);
            graphics.outline(x + 1, y + 1, w - 2, h - 2, 0xFF00E676);
        }
    }

    @Inject(method = "getTooltipText", at = @At("RETURN"), cancellable = true)
    private void auracraft$addChestCraftableTooltip(ItemStack itemStack, CallbackInfoReturnable<List<Component>> cir) {
        RecipeButton button = (RecipeButton) (Object) this;
        if (auracraft$isCraftableOnlyViaChests(button)) {
            List<Component> tooltip = new ArrayList<>(cir.getReturnValue());
            tooltip.add(Component.translatable("auracraft.tooltip.craftable_via_chests").withStyle(net.minecraft.ChatFormatting.GREEN));
            cir.setReturnValue(tooltip);
        }
    }

    @Unique
    private static boolean auracraft$isCraftableOnlyViaChests(RecipeButton button) {
        RecipeCollection collection = button.getCollection();
        if (collection == null || !collection.hasCraftable()) {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.containerMenu instanceof AuraCraftMenuAccess access)) {
            return false;
        }

        if (access.auracraft$getLinkedContainerCount() <= 0) {
            return false;
        }

        StackedItemContents playerOnly = access.auracraft$getPlayerOnlyStackedContents();
        if (playerOnly == null) {
            return false;
        }

        RecipeDisplayId currentId = button.getCurrentRecipe();
        if (currentId != null && collection.isCraftable(currentId)) {
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                if (entry.id().equals(currentId)) {
                    return !entry.canCraft(playerOnly);
                }
            }
        }

        // Si variante ou recette multiple dans ce bouton
        for (RecipeDisplayEntry entry : collection.getRecipes()) {
            if (collection.isCraftable(entry.id()) && !entry.canCraft(playerOnly)) {
                return true;
            }
        }

        return false;
    }
}
