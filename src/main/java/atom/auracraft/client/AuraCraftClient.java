package atom.auracraft.client;

import atom.auracraft.mixin.AbstractRecipeBookScreenAccessor;
import atom.auracraft.network.AuraCraftSyncPayload;
import atom.auracraft.util.AuraCraftMenuAccess;
import atom.auracraft.util.RecipeBookComponentAccess;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;

public class AuraCraftClient implements ClientModInitializer {

    public static AuraCraftSyncPayload lastReceivedPayload = null;

    @Override
    public void onInitializeClient() {
        // Enregistrement de la réception des données synchronisées du serveur
        ClientPlayNetworking.registerGlobalReceiver(AuraCraftSyncPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                lastReceivedPayload = payload;

                if (context.client().player != null && context.client().player.containerMenu instanceof AuraCraftMenuAccess access) {
                    access.auracraft$setClientContainerData(
                            payload.containerCount(),
                            payload.items(),
                            payload.positions()
                    );
                }

                // Déclenche immédiatement la mise à jour du Recipe Book
                if (context.client().gui != null && context.client().gui.screen() instanceof CraftingScreen craftingScreen) {
                    refreshRecipeBook(craftingScreen);
                }
            });
        });
    }

    public static void refreshRecipeBook(CraftingScreen screen) {
        try {
            RecipeBookComponent<?> book = ((AbstractRecipeBookScreenAccessor) screen).auracraft$getRecipeBookComponent();
            if (book != null) {
                ((RecipeBookComponentAccess) book).auracraft$updateStackedContents();
                screen.recipesUpdated();
            }
        } catch (Exception ignored) {
        }
    }
}
