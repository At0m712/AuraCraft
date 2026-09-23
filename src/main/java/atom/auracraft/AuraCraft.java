package atom.auracraft;

import atom.auracraft.config.AuraCraftConfig;
import atom.auracraft.network.AuraCraftRefreshPayload;
import atom.auracraft.network.AuraCraftSyncPayload;
import atom.auracraft.util.AuraCraftMenuAccess;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuraCraft implements ModInitializer {
    public static final String MOD_ID = "auracraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initialisation du mod AuraCraft pour Minecraft 26.1...");

        // Chargement de la configuration
        AuraCraftConfig.load();

        // Enregistrement des paquets réseau Fabric
        PayloadTypeRegistry.clientboundPlay().register(AuraCraftSyncPayload.TYPE, AuraCraftSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AuraCraftRefreshPayload.TYPE, AuraCraftRefreshPayload.STREAM_CODEC);

        // Gestion de la demande de rafraîchissement manuel depuis le client
        ServerPlayNetworking.registerGlobalReceiver(AuraCraftRefreshPayload.TYPE, (payload, context) -> {
            if (context.player().level().getServer() != null) {
                context.player().level().getServer().execute(() -> {
                    if (context.player().containerMenu instanceof AuraCraftMenuAccess access) {
                        access.auracraft$refreshContainers(payload.force());
                    }
                });
            }
        });

        LOGGER.info("AuraCraft initialisé avec succès. Rayon par défaut : {} blocs.", AuraCraftConfig.get().getRadius());
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
