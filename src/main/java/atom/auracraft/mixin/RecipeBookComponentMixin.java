package atom.auracraft.mixin;

import atom.auracraft.util.RecipeBookComponentAccess;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin implements RecipeBookComponentAccess {
    @Override
    @Invoker("updateStackedContents")
    public abstract void auracraft$updateStackedContents();
}
