package cc.hachem.config;

import cc.hachem.RadarClient;
import cc.hachem.core.SpawnerCluster;
import cc.hachem.hud.HudRenderer;
import cc.hachem.hud.PanelWidget;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ConfigScreen
{
    private ConfigScreen() {}

    public static Screen create(Screen parent)
    {
        ConfigBuilder builder = ConfigBuilder.create()
            .setTitle(text("option.spawn_radar.title"))
            .setSavingRunnable(ConfigSerializer::save)
            .setTransparentBackground(true)
            .setParentScreen(parent);

        ConfigEntryBuilder entries = builder.entryBuilder();
        ConfigCategory scanning = builder.getOrCreateCategory(text("option.spawn_radar.category.scanning"));
        ConfigCategory rendering = builder.getOrCreateCategory(text("option.spawn_radar.category.rendering"));
        ConfigCategory hud = builder.getOrCreateCategory(text("option.spawn_radar.category.hud"));
        ConfigCategory colors = builder.getOrCreateCategory(text("option.spawn_radar.colors"));

        addScanningEntries(scanning, entries);
        addRenderingEntries(rendering, entries);
        addHudEntries(hud, entries);
        addColorEntries(colors, entries);

        return builder.build();
    }

    private static void addScanningEntries(ConfigCategory scanning, ConfigEntryBuilder entries)
    {
        var config = RadarClient.config;

        scanning.addEntry(entries.startIntField(
                text("option.spawn_radar.chunk_search_radius"),
                config.defaultSearchRadius)
            .setSaveConsumer(value -> config.defaultSearchRadius = value)
            .setDefaultValue(ConfigManager.DEFAULT.defaultSearchRadius)
            .build());

        scanning.addEntry(entries.startIntField(
                text("option.spawn_radar.min_spawners"),
                config.minimumSpawnersForRegion)
            .setSaveConsumer(value -> config.minimumSpawnersForRegion = value)
            .setDefaultValue(ConfigManager.DEFAULT.minimumSpawnersForRegion)
            .build());

        scanning.addEntry(entries.startBooleanToggle(
                text("option.spawn_radar.highlight_after_scan"),
                config.highlightAfterScan)
            .setSaveConsumer(value -> config.highlightAfterScan = value)
            .setDefaultValue(ConfigManager.DEFAULT.highlightAfterScan)
            .build());

        scanning.addEntry(entries
            .startEnumSelector(
                text("option.spawn_radar.default_cluster_sort_type"),
                SpawnerCluster.SortType.class,
                config.defaultSortType)
            .setEnumNameProvider(e -> Text.translatable(e.toString()))
            .setDefaultValue(ConfigManager.DEFAULT.defaultSortType)
            .setSaveConsumer(value -> config.defaultSortType = value)
            .build());

        scanning.addEntry(entries
            .startEnumSelector(
                text("option.spawn_radar.cluster_proximity_sort_order"),
                ConfigManager.SortOrder.class,
                config.clusterProximitySortOrder)
            .setEnumNameProvider(e -> Text.translatable(e.toString()))
            .setDefaultValue(ConfigManager.DEFAULT.clusterProximitySortOrder)
            .setSaveConsumer(value -> config.clusterProximitySortOrder = value)
            .build());

        scanning.addEntry(entries
            .startEnumSelector(
                text("option.spawn_radar.cluster_size_sort_order"),
                ConfigManager.SortOrder.class,
                config.clusterSizeSortOrder)
            .setEnumNameProvider(e -> Text.translatable(e.toString()))
            .setDefaultValue(ConfigManager.DEFAULT.clusterSizeSortOrder)
            .setSaveConsumer(value -> config.clusterSizeSortOrder = value)
            .build());

        scanning.addEntry(entries.startBooleanToggle(
                text("option.spawn_radar.frustum_culling"),
                config.frustumCullingEnabled)
            .setSaveConsumer(value -> config.frustumCullingEnabled = value)
            .setDefaultValue(ConfigManager.DEFAULT.frustumCullingEnabled)
            .build());
    }

    private static void addRenderingEntries(ConfigCategory rendering, ConfigEntryBuilder entries)
    {
        var config = RadarClient.config;

        rendering.addEntry(entries.startBooleanToggle(
                text("option.spawn_radar.use_outline_highlight"),
                config.useOutlineSpawnerHighlight)
            .setSaveConsumer(value -> config.useOutlineSpawnerHighlight = value)
            .setDefaultValue(ConfigManager.DEFAULT.useOutlineSpawnerHighlight)
            .build());

        rendering.addEntry(entries.startBooleanToggle(
                text("option.spawn_radar.show_spawn_volume"),
                config.showSpawnerSpawnVolume)
            .setSaveConsumer(value -> config.showSpawnerSpawnVolume = value)
            .setDefaultValue(ConfigManager.DEFAULT.showSpawnerSpawnVolume)
            .build());

        rendering.addEntry(entries.startBooleanToggle(
                text("option.spawn_radar.show_mob_cap_volume"),
                config.showSpawnerMobCapVolume)
            .setSaveConsumer(value -> config.showSpawnerMobCapVolume = value)
            .setDefaultValue(ConfigManager.DEFAULT.showSpawnerMobCapVolume)
            .build());

        rendering.addEntry(entries.startFloatField(
                text("option.spawn_radar.outline_thickness"),
                config.spawnerOutlineThickness)
            .setMin(0.05f)
            .setSaveConsumer(value -> config.spawnerOutlineThickness = Math.max(0.05f, value))
            .setDefaultValue(ConfigManager.DEFAULT.spawnerOutlineThickness)
            .build());
    }

    private static void addHudEntries(ConfigCategory hud, ConfigEntryBuilder entries)
    {
        var config = RadarClient.config;

        hud.addEntry(entries
            .startIntField(
                text("option.spawn_radar.panel_element_count"),
                config.panelElementCount)
            .setDefaultValue(ConfigManager.DEFAULT.panelElementCount)
            .setSaveConsumer(value ->
            {
                config.panelElementCount = value;
                PanelWidget.setElementCount(value);
            })
            .build());

        hud.addEntry(entries
            .startIntSlider(
                text("option.spawn_radar.panel_vertical_offset"),
                (int) (config.verticalPanelOffset * 100),
                0,
                100)
            .setDefaultValue((int) (ConfigManager.DEFAULT.verticalPanelOffset * 100))
            .setTextGetter(value -> Text.of(value + "%"))
            .setSaveConsumer(value ->
            {
                config.verticalPanelOffset = value / 100f;
                HudRenderer.updatePanelPosition();
            })
            .build());

        hud.addEntry(entries
            .startEnumSelector(
                text("option.spawn_radar.panel_horizontal_alignment"),
                ConfigManager.HudHorizontalAlignment.class,
                config.panelHorizontalAlignment)
            .setEnumNameProvider(e -> Text.translatable(e.toString()))
            .setDefaultValue(ConfigManager.DEFAULT.panelHorizontalAlignment)
            .setSaveConsumer(value ->
            {
                config.panelHorizontalAlignment = value;
                HudRenderer.updatePanelPosition();
            })
            .build());
    }

    private static void addColorEntries(ConfigCategory colors, ConfigEntryBuilder entries)
    {
        var config = RadarClient.config;

        colors.addEntry(entries
            .startColorField(
                text("option.spawn_radar.spawner_color"),
                config.spawnerHighlightColor)
            .setSaveConsumer(color -> config.spawnerHighlightColor = color)
            .setDefaultValue(ConfigManager.DEFAULT.spawnerHighlightColor)
            .build());

        colors.addEntry(entries
            .startColorField(
                text("option.spawn_radar.outline_color"),
                config.spawnerOutlineColor)
            .setSaveConsumer(color -> config.spawnerOutlineColor = color)
            .setDefaultValue(ConfigManager.DEFAULT.spawnerOutlineColor)
            .build());

        colors.addEntry(entries
            .startColorField(
                text("option.spawn_radar.spawn_volume_color"),
                config.spawnVolumeColor)
            .setSaveConsumer(color -> config.spawnVolumeColor = color)
            .setDefaultValue(ConfigManager.DEFAULT.spawnVolumeColor)
            .build());

        colors.addEntry(entries
            .startIntSlider(
                text("option.spawn_radar.spawn_volume_opacity"),
                config.spawnVolumeOpacity,
                0,
                100)
            .setSaveConsumer(value -> config.spawnVolumeOpacity = value)
            .setDefaultValue(ConfigManager.DEFAULT.spawnVolumeOpacity)
            .setTextGetter(value -> Text.of(value + "%"))
            .build());

        colors.addEntry(entries
            .startColorField(
                text("option.spawn_radar.mob_cap_volume_color"),
                config.mobCapVolumeColor)
            .setSaveConsumer(color -> config.mobCapVolumeColor = color)
            .setDefaultValue(ConfigManager.DEFAULT.mobCapVolumeColor)
            .build());

        colors.addEntry(entries
            .startIntSlider(
                text("option.spawn_radar.mob_cap_volume_opacity"),
                config.mobCapVolumeOpacity,
                0,
                100)
            .setSaveConsumer(value -> config.mobCapVolumeOpacity = value)
            .setDefaultValue(ConfigManager.DEFAULT.mobCapVolumeOpacity)
            .setTextGetter(value -> Text.of(value + "%"))
            .build());

        for (int i = 0; i < config.clusterColors.size(); i++)
        {
            final int index = i;
            colors.addEntry(entries
                .startColorField(text(clusterColorKey(i, config.clusterColors.size())), config.clusterColors.get(i))
                .setSaveConsumer(color -> config.clusterColors.set(index, color))
                .setDefaultValue(ConfigManager.DEFAULT.clusterColors.get(i))
                .build());
        }

        colors.addEntry(entries
            .startIntSlider(
                text("option.spawn_radar.spawner_opacity"),
                config.spawnerHighlightOpacity,
                0,
                100)
            .setSaveConsumer(value -> config.spawnerHighlightOpacity = value)
            .setDefaultValue(ConfigManager.DEFAULT.spawnerHighlightOpacity)
            .setTextGetter(value -> Text.of(value + "%"))
            .build());

        colors.addEntry(entries
            .startIntSlider(
                text("option.spawn_radar.region_opacity"),
                config.regionHighlightOpacity,
                0,
                100)
            .setSaveConsumer(value -> config.regionHighlightOpacity = value)
            .setDefaultValue(ConfigManager.DEFAULT.regionHighlightOpacity)
            .setTextGetter(value -> Text.of(value + "%"))
            .build());
    }

    private static Text text(String key)
    {
        return Text.translatable(key);
    }

    private static String clusterColorKey(int index, int total)
    {
        boolean isLastSlot = index == total - 1;
        String suffix = isLastSlot ? "_plus" : "";
        return "option.spawn_radar.cluster_" + (index + 1) + suffix;
    }
}
