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
        try (FileWriter writer = new FileWriter(CONFIG_FILE))
        {
            GSON.toJson(RadarClient.config, writer);
        }
        catch (IOException e)
        {
            RadarClient.LOGGER.error("Failed to save config: {}", e.getMessage());
        }
    }

    public static void load()
    {
        if (!CONFIG_FILE.exists())
        {
            RadarClient.config = new ConfigManager();
            RadarClient.config.ensureColorPalette();
            RadarClient.config.ensureHudAlignment();
            save();
        }

        try (FileReader reader = new FileReader(CONFIG_FILE))
        {
            RadarClient.config = GSON.fromJson(reader, ConfigManager.class);
            if (RadarClient.config == null)
            {
                RadarClient.LOGGER.error("Config file was empty or invalid. Recreating default config.");
                RadarClient.config = new ConfigManager();
                RadarClient.config.ensureColorPalette();
                RadarClient.config.ensureHudAlignment();
                save();
            }
            else
            {
                RadarClient.config.ensureColorPalette();
                RadarClient.config.ensureHudAlignment();
            }

        } catch (IOException e)
        {
            RadarClient.LOGGER.error("Failed to load config: {}", e.getMessage());
            RadarClient.config = new ConfigManager();
            RadarClient.config.ensureColorPalette();
            RadarClient.config.ensureHudAlignment();
        }
    }
}
