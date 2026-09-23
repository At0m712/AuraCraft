package atom.auracraft.network;

import atom.auracraft.AuraCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record AuraCraftRefreshPayload(boolean force) implements CustomPacketPayload {

    public static final Type<AuraCraftRefreshPayload> TYPE = new Type<>(AuraCraft.id("refresh_containers"));

    public static final StreamCodec<FriendlyByteBuf, AuraCraftRefreshPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            AuraCraftRefreshPayload::force,
            AuraCraftRefreshPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
