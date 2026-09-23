package atom.auracraft.network;

import atom.auracraft.AuraCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record AuraCraftSyncPayload(
        int containerCount,
        List<ItemStack> items,
        List<BlockPos> positions
) implements CustomPacketPayload {

    public static final Type<AuraCraftSyncPayload> TYPE = new Type<>(AuraCraft.id("sync_containers"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AuraCraftSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.containerCount());
                ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, payload.items());
                buf.writeVarInt(payload.positions().size());
                for (BlockPos pos : payload.positions()) {
                    buf.writeBlockPos(pos);
                }
            },
            buf -> {
                int count = buf.readVarInt();
                List<ItemStack> items = ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf);
                int posCount = buf.readVarInt();
                List<BlockPos> positions = new ArrayList<>(posCount);
                for (int i = 0; i < posCount; i++) {
                    positions.add(buf.readBlockPos());
                }
                return new AuraCraftSyncPayload(count, items, positions);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
