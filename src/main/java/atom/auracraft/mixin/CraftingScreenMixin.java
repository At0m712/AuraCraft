package atom.auracraft.mixin;

import atom.auracraft.client.AuraCraftClient;
import atom.auracraft.config.AuraCraftConfig;
import atom.auracraft.network.AuraCraftRefreshPayload;
import atom.auracraft.util.AuraCraftMenuAccess;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(AbstractContainerScreen.class)
public abstract class CraftingScreenMixin<T extends AbstractContainerMenu> {

    @Shadow
    @Final
    protected int imageWidth;

    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    @Shadow
    @Final
    protected T menu;

    @Inject(method = "init", at = @At("TAIL"))
    private void auracraft$onInit(CallbackInfo ci) {
        if (!((Object) this instanceof CraftingScreen craftingScreen)) {
            return;
        }

        // Si un payload a déjà été mis en cache, on l'applique immédiatement
        if (this.menu instanceof AuraCraftMenuAccess access && AuraCraftClient.lastReceivedPayload != null) {
            var payload = AuraCraftClient.lastReceivedPayload;
            access.auracraft$setClientContainerData(payload.containerCount(), payload.items(), payload.positions());
            AuraCraftClient.refreshRecipeBook(craftingScreen);
        }

        // Demande une synchronisation fraîche au serveur
        ClientPlayNetworking.send(new AuraCraftRefreshPayload(true));
    }

    @Inject(method = "extractLabels", at = @At("TAIL"))
    private void auracraft$renderLinkedContainersLabel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (!((Object) this instanceof CraftingScreen)) {
            return;
        }

        if (this.menu instanceof AuraCraftMenuAccess access) {
            Minecraft mc = Minecraft.getInstance();
            Font font = mc.font;
            if (font == null) {
                return;
            }

            int count = access.auracraft$getLinkedContainerCount();
            Component badgeText = count > 0
                    ? Component.translatable(count == 1 ? "auracraft.gui.chest_count_one" : "auracraft.gui.chests_count", count).withStyle(ChatFormatting.GOLD)
                    : Component.translatable("auracraft.gui.no_chests").withStyle(ChatFormatting.GRAY);

            int textWidth = font.width(badgeText);
            int badgeX = this.imageWidth - textWidth - 8;
            int badgeY = 6;

            // Fond capsule semi-translucide très discret pour assurer un contraste 100% lisible
            // sur fond blanc/gris clair vanilla comme sur les packs Dark GUI (fond noir)
            graphics.fill(badgeX - 3, badgeY - 2, badgeX + textWidth + 3, badgeY + 10, 0x33000000);
            graphics.outline(badgeX - 3, badgeY - 2, textWidth + 6, 12, 0x22FFFFFF);

            // Rendu avec dropShadow = true pour une netteté absolue
            graphics.text(font, badgeText, badgeX, badgeY, 0xFFFFFFFF, true);

            int relMouseX = mouseX - this.leftPos;
            int relMouseY = mouseY - this.topPos;
            if (relMouseX >= badgeX - 3 && relMouseX <= badgeX + textWidth + 3 && relMouseY >= badgeY - 2 && relMouseY <= badgeY + 10) {
                Component priorityName = AuraCraftConfig.get().getPullPriority() == AuraCraftConfig.PullPriority.PLAYER_FIRST
                        ? Component.translatable("auracraft.priority.player_first")
                        : Component.translatable("auracraft.priority.containers_first");

                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.translatable("auracraft.tooltip.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                if (count > 0) {
                    tooltip.add(Component.translatable("auracraft.tooltip.connected", count).withStyle(ChatFormatting.YELLOW));
                } else {
                    tooltip.add(Component.translatable("auracraft.tooltip.no_containers", AuraCraftConfig.get().getRadius()).withStyle(ChatFormatting.RED));
                }
                tooltip.add(Component.translatable("auracraft.tooltip.radius", AuraCraftConfig.get().getRadius()).withStyle(ChatFormatting.AQUA));
                tooltip.add(Component.translatable("auracraft.tooltip.priority", priorityName).withStyle(ChatFormatting.LIGHT_PURPLE));
                tooltip.add(Component.translatable("auracraft.tooltip.description").withStyle(ChatFormatting.DARK_GRAY));

                graphics.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
            }
        }
    }

    @Inject(method = "containerTick", at = @At("TAIL"))
    private void auracraft$tick(CallbackInfo ci) {
        if (!((Object) this instanceof CraftingScreen)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.level != null) {
            // Rafraîchissement périodique (toutes les secondes)
            if (mc.level.getGameTime() % 20 == 0) {
                ClientPlayNetworking.send(new AuraCraftRefreshPayload(false));
            }

            // Particules d'enchantement
            if (AuraCraftConfig.get().isHighlightLinkedChests() && mc.level.getGameTime() % 8 == 0) {
                if (this.menu instanceof AuraCraftMenuAccess access) {
                    var random = mc.level.getRandom();
                    for (BlockPos pos : access.auracraft$getLinkedChestPositions()) {
                        double px = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5;
                        double py = pos.getY() + 0.8 + random.nextDouble() * 0.3;
                        double pz = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5;
                        mc.level.addParticle(ParticleTypes.ENCHANT, px, py, pz, 0.0, 0.05, 0.0);
                    }
                }
            }
        }
    }
}
