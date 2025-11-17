package cc.hachem.config;

import cc.hachem.RadarClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ConfigSerializer
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(),
                                                    "spawn_radar.json");

    public static void save()
    {
        save(RadarClient.config);
    }

    public static void save(ConfigManager config)
    {
        if (config == null)
        {
            RadarClient.LOGGER.error("Attempted to save config, but the instance was null.");
            return;
        }

        try (FileWriter writer = new FileWriter(CONFIG_FILE))
        {
            GSON.toJson(config, writer);
        }
        catch (IOException e)
        {
            RadarClient.LOGGER.error("Failed to save config: {}", e.getMessage());
        }
    }

    public static void load()
    {
        RadarClient.config = readOrCreateConfig();
    }

    private static ConfigManager readOrCreateConfig()
    {
        if (!CONFIG_FILE.exists())
        {
            ConfigManager defaults = createDefaultConfig();
            save(defaults);
            return defaults;
        }

        try (FileReader reader = new FileReader(CONFIG_FILE))
        {
            ConfigManager loaded = GSON.fromJson(reader, ConfigManager.class);
            if (loaded == null)
            {
                RadarClient.LOGGER.error("Config file was empty or invalid. Recreating default config.");
                ConfigManager defaults = createDefaultConfig();
                save(defaults);
                return defaults;
            }

            loaded.normalize();
            return loaded;
        }
        catch (IOException e)
        {
            RadarClient.LOGGER.error("Failed to load config: {}", e.getMessage());
            return createDefaultConfig();
        }
    }

    private static ConfigManager createDefaultConfig()
    {
        ConfigManager config = new ConfigManager();
        config.normalize();
        return config;
    }
}
