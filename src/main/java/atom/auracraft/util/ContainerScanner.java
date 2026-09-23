package atom.auracraft.util;

import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * Scan ciblé zéro-lag via les chunks chargés.
 * Ne parcourt JAMAIS les 68 000+ blocs individuellement.
 */
public class ContainerScanner {

    public static List<LinkedContainer> scan(Level level, BlockPos center, int radius, Player player) {
        if (level == null || center == null) {
            return Collections.emptyList();
        }

        double radiusSq = (double) radius * radius;
        AABB boundingBox = new AABB(
                center.getX() - radius, center.getY() - radius, center.getZ() - radius,
                center.getX() + radius + 1, center.getY() + radius + 1, center.getZ() + radius + 1
        );

        int minChunkX = (center.getX() - radius) >> 4;
        int maxChunkX = (center.getX() + radius) >> 4;
        int minChunkZ = (center.getZ() - radius) >> 4;
        int maxChunkZ = (center.getZ() + radius) >> 4;

        List<LinkedContainer> result = new ArrayList<>();
        Set<BlockPos> visitedPositions = new HashSet<>();

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                LevelChunk chunk = null;
                if (level instanceof ServerLevel serverLevel) {
                    // Récupération instantanée du chunk en mémoire, avec fallback sans génération bloquante
                    chunk = serverLevel.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null && serverLevel.getChunkSource().hasChunk(cx, cz)) {
                        var chunkAccess = serverLevel.getChunkSource().getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
                        if (chunkAccess instanceof LevelChunk levelChunk) {
                            chunk = levelChunk;
                        }
                    }
                } else if (level.isLoaded(new BlockPos(cx << 4, 64, cz << 4))) {
                    chunk = level.getChunk(cx, cz);
                }

                if (chunk == null) {
                    continue;
                }

                Map<BlockPos, BlockEntity> blockEntities = chunk.getBlockEntities();
                if (blockEntities == null || blockEntities.isEmpty()) {
                    continue;
                }

                for (Map.Entry<BlockPos, BlockEntity> entry : blockEntities.entrySet()) {
                    BlockPos pos = entry.getKey();
                    if (visitedPositions.contains(pos)) {
                        continue;
                    }

                    // Vérification du rayon cubique (inclusif et régulier)
                    int dx = Math.abs(pos.getX() - center.getX());
                    int dy = Math.abs(pos.getY() - center.getY());
                    int dz = Math.abs(pos.getZ() - center.getZ());
                    if (dx > radius || dy > radius || dz > radius) {
                        continue;
                    }
                    double distSq = center.distSqr(pos);

                    BlockEntity be = entry.getValue();
                    if (be == null || be.isRemoved()) {
                        continue;
                    }

                    // Sécurité : vérification si le joueur a le droit d'ouvrir le conteneur
                    if (be instanceof BaseContainerBlockEntity baseContainer && player != null) {
                        if (!baseContainer.canOpen(player)) {
                            continue;
                        }
                    }

                    BlockState state = be.getBlockState();

                    // Sécurité coffres : coffre obstrué ou chat assis
                    if (be instanceof ChestBlockEntity && ChestBlock.isChestBlockedAt(level, pos)) {
                        continue;
                    }

                    // Gestion des doubles coffres pour éviter toute double transaction
                    if (state.getBlock() instanceof ChestBlock) {
                        ChestType chestType = state.hasProperty(ChestBlock.TYPE) ? state.getValue(ChestBlock.TYPE) : ChestType.SINGLE;
                        if (chestType != ChestType.SINGLE) {
                            Direction connectedDir = ChestBlock.getConnectedDirection(state);
                            BlockPos connectedPos = pos.relative(connectedDir);
                            visitedPositions.add(connectedPos);
                        }
                    }

                    visitedPositions.add(pos);

                    // Résolution du stockage via Fabric Transfer API (UP puis null puis ContainerStorage)
                    Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, Direction.UP);
                    if (storage == null) {
                        storage = ItemStorage.SIDED.find(level, pos, null);
                    }
                    if (storage == null && be instanceof Container container) {
                        storage = ContainerStorage.of(container, null);
                    }

                    if (storage != null && storage.supportsExtraction()) {
                        result.add(new LinkedContainer(pos.immutable(), storage, be, distSq));
                    }
                }
            }
        }

        // Tri des conteneurs du plus proche au plus éloigné
        result.sort(Comparator.comparingDouble(LinkedContainer::distanceSq));
        return result;
    }
}
