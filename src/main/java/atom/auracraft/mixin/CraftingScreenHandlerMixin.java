package atom.auracraft.mixin;

import atom.auracraft.config.AuraCraftConfig;
import atom.auracraft.network.AuraCraftSyncPayload;
import atom.auracraft.util.AuraCraftMenuAccess;
import atom.auracraft.util.ContainerScanner;
import atom.auracraft.util.InventoryHelper;
import atom.auracraft.util.LinkedContainer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mixin(CraftingMenu.class)
public abstract class CraftingScreenHandlerMixin implements AuraCraftMenuAccess {

    @Shadow
    @Final
    private ContainerLevelAccess access;

    @Shadow
    protected abstract Player owner();

    @Shadow
    public abstract List<Slot> getInputGridSlots();

    @Unique
    private final List<LinkedContainer> auracraft$linkedContainers = new ArrayList<>();

    @Unique
    private long auracraft$lastScanTime = 0L;

    @Unique
    private int auracraft$clientContainerCount = 0;

    @Unique
    private List<BlockPos> auracraft$clientChestPositions = new ArrayList<>();

    @Unique
    private List<ItemStack> auracraft$clientAggregatedItems = new ArrayList<>();

    @Unique
    private BlockPos auracraft$tablePos = null;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("TAIL"))
    private void auracraft$onInit(int containerId, Inventory playerInventory, ContainerLevelAccess access, CallbackInfo ci) {
        auracraft$refreshContainers(true);
    }

    @Override
    public List<LinkedContainer> auracraft$getLinkedContainers() {
        return Collections.unmodifiableList(this.auracraft$linkedContainers);
    }

    @Override
    public void auracraft$refreshContainers(boolean force) {
        long now = System.currentTimeMillis();
        // Vérification de l'intervalle minimal d'inactivité (Lazy Cache)
        if (!force && (now - this.auracraft$lastScanTime < 1000L)) {
            return;
        }
        this.auracraft$lastScanTime = now;

        Player player = this.owner();
        if (player instanceof ServerPlayer serverPlayer) {
            Level level = serverPlayer.level();
            BlockPos center = this.access.evaluate((lvl, pos) -> pos).orElse(serverPlayer.blockPosition());
            this.auracraft$tablePos = center;

            int radius = AuraCraftConfig.get().getRadius();
            List<LinkedContainer> found = ContainerScanner.scan(level, center, radius, serverPlayer);

            this.auracraft$linkedContainers.clear();
            this.auracraft$linkedContainers.addAll(found);

            List<ItemStack> aggregated = InventoryHelper.aggregateItems(this.auracraft$linkedContainers);
            List<BlockPos> positions = found.stream().map(LinkedContainer::pos).toList();

            // Synchronisation vers le client
            ServerPlayNetworking.send(serverPlayer, new AuraCraftSyncPayload(found.size(), aggregated, positions));
        }
    }

    @Override
    public int auracraft$getLinkedContainerCount() {
        Player player = this.owner();
        if (player != null && player.level().isClientSide()) {
            return this.auracraft$clientContainerCount;
        }
        return this.auracraft$linkedContainers.size();
    }

    @Override
    public List<BlockPos> auracraft$getLinkedChestPositions() {
        Player player = this.owner();
        if (player != null && player.level().isClientSide()) {
            return Collections.unmodifiableList(this.auracraft$clientChestPositions);
        }
        return this.auracraft$linkedContainers.stream().map(LinkedContainer::pos).toList();
    }

    @Override
    public List<ItemStack> auracraft$getClientAggregatedItems() {
        return Collections.unmodifiableList(this.auracraft$clientAggregatedItems);
    }

    @Override
    public void auracraft$setClientContainerData(int count, List<ItemStack> items, List<BlockPos> chestPositions) {
        this.auracraft$clientContainerCount = count;
        this.auracraft$clientAggregatedItems = items != null ? new ArrayList<>(items) : new ArrayList<>();
        this.auracraft$clientChestPositions = chestPositions != null ? new ArrayList<>(chestPositions) : new ArrayList<>();
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void auracraft$returnItemsToContainersOnClose(Player player, CallbackInfo ci) {
        if (player.level().isClientSide() || this.auracraft$linkedContainers.isEmpty()) {
            return;
        }

        // Tente de réinsérer les items de la grille dans les conteneurs liés avant que vanilla ne vide la table
        for (Slot slot : this.getInputGridSlots()) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                ItemStack remaining = InventoryHelper.insertIntoContainers(this.auracraft$linkedContainers, stack);
                slot.set(remaining);
            }
        }
    }

    @Override
    public BlockPos auracraft$getTablePos() {
        return this.auracraft$tablePos;
    }

    @Unique
    private StackedItemContents auracraft$cachedPlayerContents = null;
    @Unique
    private int auracraft$lastInventoryChangeCount = -1;

    @Override
    public StackedItemContents auracraft$getPlayerOnlyStackedContents() {
        Player player = this.owner();
        if (player == null) {
            return new StackedItemContents();
        }
        CraftingMenu menu = (CraftingMenu) (Object) this;
        int stateKey = player.getInventory().getTimesChanged() * 31 + menu.getStateId();
        if (this.auracraft$cachedPlayerContents != null && this.auracraft$lastInventoryChangeCount == stateKey) {
            return this.auracraft$cachedPlayerContents;
        }

        StackedItemContents playerOnly = new StackedItemContents();
        player.getInventory().fillStackedContents(playerOnly);
        for (Slot slot : this.getInputGridSlots()) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                playerOnly.accountSimpleStack(stack);
            }
        }
        this.auracraft$cachedPlayerContents = playerOnly;
        this.auracraft$lastInventoryChangeCount = stateKey;
        return playerOnly;
    }
}
