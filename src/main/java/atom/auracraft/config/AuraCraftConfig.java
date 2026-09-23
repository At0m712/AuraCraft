package atom.auracraft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class AuraCraftConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("AuraCraft-Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("auracraft.json");

    private static AuraCraftConfig instance;

    public enum PullPriority {
        PLAYER_FIRST,
        CONTAINERS_FIRST
    }

    private int radius = 20;
    private PullPriority pullPriority = PullPriority.PLAYER_FIRST;
    private boolean highlightLinkedChests = true;

    public AuraCraftConfig() {
    }

    public static AuraCraftConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                instance = GSON.fromJson(reader, AuraCraftConfig.class);
                if (instance != null) {
                    instance.validate();
                    return;
                }
            } catch (Exception e) {
                LOGGER.error("Erreur lors de la lecture du fichier de configuration AuraCraft, réinitialisation par défaut.", e);
            }
        }
        instance = new AuraCraftConfig();
        save();
    }

    public static void save() {
        if (instance == null) {
            instance = new AuraCraftConfig();
        }
        instance.validate();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Impossible de sauvegarder le fichier de configuration AuraCraft.", e);
        }
    }

    private void validate() {
        if (this.radius < 1) {
            this.radius = 1;
        } else if (this.radius > 32) {
            this.radius = 32;
        }
        if (this.pullPriority == null) {
            this.pullPriority = PullPriority.PLAYER_FIRST;
        }
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = Math.clamp(radius, 1, 32);
    }

    public PullPriority getPullPriority() {
        return pullPriority;
    }

    public void setPullPriority(PullPriority pullPriority) {
        this.pullPriority = pullPriority != null ? pullPriority : PullPriority.PLAYER_FIRST;
    }

    public boolean isHighlightLinkedChests() {
        return highlightLinkedChests;
    }

    public void setHighlightLinkedChests(boolean highlightLinkedChests) {
        this.highlightLinkedChests = highlightLinkedChests;
    }
}
