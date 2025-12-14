package cc.hachem.spawnradar.config;

import cc.hachem.spawnradar.RadarClient;
import cc.hachem.spawnradar.core.ChunkProcessingManager;
import cc.hachem.spawnradar.core.ClusterManager;
import cc.hachem.spawnradar.core.SpawnerCluster;
import cc.hachem.spawnradar.hud.HudRenderer;
import cc.hachem.spawnradar.hud.PanelWidget;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
        ConfigCategory general = builder.getOrCreateCategory(text("option.spawn_radar.category.general"));
        ConfigCategory scanning = builder.getOrCreateCategory(text("option.spawn_radar.category.scanning"));
        ConfigCategory rendering = builder.getOrCreateCategory(text("option.spawn_radar.category.rendering"));
        ConfigCategory hud = builder.getOrCreateCategory(text("option.spawn_radar.category.hud"));
        ConfigCategory colors = builder.getOrCreateCategory(text("option.spawn_radar.colors"));
        ConfigCategory debug = builder.getOrCreateCategory(text("option.spawn_radar.category.debug"));

        addGeneralEntries(general, entries);
        addScanningEntries(scanning, entries);
        addRenderingEntries(rendering, entries);
        addHudEntries(hud, entries);
        addColorEntries(colors, entries);
        addDebugEntries(debug, entries);

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
            .setTooltip(text("option.spawn_radar.chunk_search_radius.tooltip"))
            .build());

        scanning.addEntry(entries.startIntSlider(
                text("option.spawn_radar.scan_thread_count"),
                config.scanThreadCount,
                1, 16)
            .setTextGetter(value -> Component.nullToEmpty(String.valueOf(normalizeThreadCountInput(value))))
            .setSaveConsumer(value ->
            {
                config.scanThreadCount = normalizeThreadCountInput(value);
                config.ensureScanThreadCount();
            })
            .setDefaultValue(ConfigManager.DEFAULT.scanThreadCount)
            .setTooltip(text("option.spawn_radar.scan_thread_count.tooltip"))
            .build());

        scanning.addEntry(entries.startIntField(
                text("option.spawn_radar.min_spawners"),
                config.minimumSpawnersForRegion)
            .setSaveConsumer(value -> config.minimumSpawnersForRegion = value)
            .setDefaultValue(ConfigManager.DEFAULT.minimumSpawnersForRegion)
            .setTooltip(text("option.spawn_radar.min_spawners.tooltip"))
            .build());

        scanning.addEntry(entries
            .startEnumSelector(
                text("option.spawn_radar.default_cluster_sort_type"),
                SpawnerCluster.SortType.class,
                config.defaultSortType)
            .setEnumNameProvider(e -> Component.translatable(e.toString()))
            .setDefaultValue(ConfigManager.DEFAULT.defaultSortType)
            .setSaveConsumer(value -> config.defaultSortType = value)
            .setTooltip(text("option.spawn_radar.default_cluster_sort_type.tooltip"))
            .build());

        scanning.addEntry(entries
            .startEnumSelector(
                text("option.spawn_radar.cluster_proximity_sort_order"),
                ConfigManager.SortOrder.class,
                config.clusterProximitySortOrder)
            .setEnumNameProvider(e -> Component.translatable(e.toString()))
            .setDefaultValue(ConfigManager.DEFAULT.clusterProximitySortOrder)
            .setSaveConsumer(value -> config.clusterProximitySortOrder = value)
            .setTooltip(text("option.spawn_radar.cluster_proximity_sort_order.tooltip"))
            .build());

        scanning.addEntry(entries
            .startEnumSelector(
                text("option.spawn_radar.cluster_size_sort_order"),
                ConfigManager.SortOrder.class,
                config.clusterSizeSortOrder)
            .setEnumNameProvider(e -> Component.translatable(e.toString()))
            .setDefaultValue(ConfigManager.DEFAULT.clusterSizeSortOrder)
            .setSaveConsumer(value -> config.clusterSizeSortOrder = value)
            .setTooltip(text("option.spawn_radar.cluster_size_sort_order.tooltip"))
            .build());

        scanning.addEntry(entries.startBooleanToggle(
                text("option.spawn_radar.frustum_culling"),
                config.frustumCullingEnabled)
            .setSaveConsumer(value -> config.frustumCullingEnabled = value)
            .setDefaultValue(ConfigManager.DEFAULT.frustumCullingEnabled)
            .setTooltip(text("option.spawn_radar.frustum_culling.tooltip"))
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
            .setTooltip(text("option.spawn_radar.use_outline_highlight.tooltip"))
            .build());

        rendering.addEntry(entries.startBooleanToggle(
                text("option.spawn_radar.show_spawn_volume"),
                config.showSpawnerSpawnVolume)
            .setSaveConsumer(value -> config.showSpawnerSpawnVolume = value)
            .setDefaultValue(ConfigManager.DEFAULT.showSpawnerSpawnVolume)
            .setTooltip(text("option.spawn_radar.show_spawn_volume.tooltip"))
            .build());

        rendering.addEntry(entries.startBooleanToggle(
                text("option.spawn_radar.show_mob_cap_volume"),
                config.showSpawnerMobCapVolume)
            .setSaveConsumer(value -> config.showSpawnerMobCapVolume = value)
            .setDefaultValue(ConfigManager.DEFAULT.showSpawnerMobCapVolume)
            .setTooltip(text("option.spawn_radar.show_mob_cap_volume.tooltip"))
            .build());

        rendering.addEntry(entries.startBooleanToggle(
                text("option.spawn_radar.show_efficiency_label"),
                config.showSpawnerEfficiencyLabel)
            .setSaveConsumer(value -> config.showSpawnerEfficiencyLabel = value)
            .setDefaultValue(ConfigManager.DEFAULT.showSpawnerEfficiencyLabel)
            .setTooltip(text("option.spawn_radar.show_efficiency_label.tooltip"))
            .build());

        rendering.addEntry(entries.startBooleanToggle(
                text("option.spawn_radar.show_mob_cap_status"),
                config.showSpawnerMobCapStatus)
            .setSaveConsumer(value -> config.showSpawnerMobCapStatus = value)
            .setDefaultValue(ConfigManager.DEFAULT.showSpawnerMobCapStatus)
            .setTooltip(text("option.spawn_radar.show_mob_cap_status.tooltip"))
            .build());

        rendering.addEntry(entries.startBooleanToggle(
                text("option.spawn_radar.auto_highlight_alerts"),
                config.autoHighlightAlertedClusters)
            .setSaveConsumer(value ->
            {
                config.autoHighlightAlertedClusters = value;
                if (!value)
                    ClusterManager.clearBackgroundHighlights();
            })
            .setDefaultValue(ConfigManager.DEFAULT.autoHighlightAlertedClusters)
            .setTooltip(text("option.spawn_radar.auto_highlight_alerts.tooltip"))
            .build());

        rendering.addEntry(entries.startFloatField(
                text("option.spawn_radar.outline_thickness"),
                config.spawnerOutlineThickness)
            .setMin(0.05f)
            .setSaveConsumer(value -> config.spawnerOutlineThickness = Math.max(0.05f, value))
            .setDefaultValue(ConfigManager.DEFAULT.spawnerOutlineThickness)
            .setTooltip(text("option.spawn_radar.outline_thickness.tooltip"))
            .build());

        rendering.addEntry(entries.startBooleanToggle(
                text("option.spawn_radar.use_dual_page_book"),
                config.useDualPageBookUi)
            .setSaveConsumer(value -> config.useDualPageBookUi = value)
            .setDefaultValue(ConfigManager.DEFAULT.useDualPageBookUi)
            .setTooltip(text("option.spawn_radar.use_dual_page_book.tooltip"))
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
            .setTooltip(text("option.spawn_radar.panel_element_count.tooltip"))
            .build());

        hud.addEntry(entries
            .startIntSlider(
                text("option.spawn_radar.panel_vertical_offset"),
                (int) (config.verticalPanelOffset * 100),
                0,
                100)
            .setDefaultValue((int) (ConfigManager.DEFAULT.verticalPanelOffset * 100))
            .setTextGetter(value -> Component.nullToEmpty(value + "%"))
            .setSaveConsumer(value ->
            {
                config.verticalPanelOffset = value / 100f;
                HudRenderer.updatePanelPosition();
            })
            .setTooltip(text("option.spawn_radar.panel_vertical_offset.tooltip"))
            .build());

        hud.addEntry(entries
            .startEnumSelector(
                text("option.spawn_radar.panel_horizontal_alignment"),
                ConfigManager.HudHorizontalAlignment.class,
                config.panelHorizontalAlignment)
            .setEnumNameProvider(e -> Component.translatable(e.toString()))
            .setDefaultValue(ConfigManager.DEFAULT.panelHorizontalAlignment)
            .setSaveConsumer(value ->
            {
                config.panelHorizontalAlignment = value;
                HudRenderer.updatePanelPosition();
            })
            .setTooltip(text("option.spawn_radar.panel_horizontal_alignment.tooltip"))
            .build());

        hud.addEntry(entries
            .startEnumSelector(
                text("option.spawn_radar.spawner_icon_mode"),
                ConfigManager.SpawnerIconMode.class,
                config.spawnerIconMode)
            .setEnumNameProvider(e -> Component.translatable(e.toString()))
            .setDefaultValue(ConfigManager.DEFAULT.spawnerIconMode)
            .setTooltip(text("option.spawn_radar.spawner_icon_mode.tooltip"))
            .setSaveConsumer(value ->
            {
                config.spawnerIconMode = value == null ? ConfigManager.DEFAULT.spawnerIconMode : value;
                PanelWidget.refresh();
            })
            .build());
    }

    private static void addGeneralEntries(ConfigCategory general, ConfigEntryBuilder entries)
    {
        var config = RadarClient.config;

        general.addEntry(entries.startBooleanToggle(
                text("option.spawn_radar.show_welcome_message"),
                Boolean.TRUE.equals(config.showWelcomeMessage))
            .setSaveConsumer(value -> config.showWelcomeMessage = value)
            .setDefaultValue(Boolean.TRUE.equals(ConfigManager.DEFAULT.showWelcomeMessage))
            .setTooltip(text("option.spawn_radar.show_welcome_message.tooltip"))
            .build());

        general.addEntry(entries.startBooleanToggle(
                text("option.spawn_radar.highlight_after_scan"),
                config.highlightAfterScan)
            .setSaveConsumer(value -> config.highlightAfterScan = value)
            .setDefaultValue(ConfigManager.DEFAULT.highlightAfterScan)
            .setTooltip(text("option.spawn_radar.highlight_after_scan.tooltip"))
            .build());

        general.addEntry(entries.startBooleanToggle(
                text("option.spawn_radar.use_cached_spawners"),
                config.useCachedSpawnersForScan)
            .setSaveConsumer(value -> config.useCachedSpawnersForScan = value)
            .setDefaultValue(ConfigManager.DEFAULT.useCachedSpawnersForScan)
            .setTooltip(text("option.spawn_radar.use_cached_spawners.tooltip"))
            .build());

        general.addEntry(entries.startBooleanToggle(
                text("option.spawn_radar.background_processing"),
                config.processChunksOnGeneration)
            .setSaveConsumer(value ->
            {
                config.processChunksOnGeneration = value;
                if (value)
                    ChunkProcessingManager.rescanCurrentWorld();
            })
            .setDefaultValue(ConfigManager.DEFAULT.processChunksOnGeneration)
            .setTooltip(text("option.spawn_radar.background_processing.tooltip"))
            .build());

        general.addEntry(entries.startIntField(
                text("option.spawn_radar.background_alert_threshold"),
                config.backgroundClusterAlertThreshold)
            .setSaveConsumer(value -> config.backgroundClusterAlertThreshold = Math.max(2, value))
            .setDefaultValue(ConfigManager.DEFAULT.backgroundClusterAlertThreshold)
            .setTooltip(text("option.spawn_radar.background_alert_threshold.tooltip"))
            .build());

        int proximityValue = clampBackgroundProximity(config.backgroundClusterProximity);
        int defaultProximity = clampBackgroundProximity(ConfigManager.DEFAULT.backgroundClusterProximity);
        general.addEntry(entries.startIntSlider(
                text("option.spawn_radar.background_proximity"),
                proximityValue,
                8,
                64)
            .setSaveConsumer(value -> config.backgroundClusterProximity = clampBackgroundProximity(value))
            .setDefaultValue(defaultProximity)
            .setTextGetter(value -> Component.translatable("option.spawn_radar.background_proximity.value", value))
            .setTooltip(text("option.spawn_radar.background_proximity.tooltip"))
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
            .setTextGetter(value -> Component.nullToEmpty(value + "%"))
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
            .setTextGetter(value -> Component.nullToEmpty(value + "%"))
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
            .setTextGetter(value -> Component.nullToEmpty(value + "%"))
            .build());

        colors.addEntry(entries
            .startIntSlider(
                text("option.spawn_radar.region_opacity"),
                config.regionHighlightOpacity,
                0,
                100)
            .setSaveConsumer(value -> config.regionHighlightOpacity = value)
            .setDefaultValue(ConfigManager.DEFAULT.regionHighlightOpacity)
            .setTextGetter(value -> Component.nullToEmpty(value + "%"))
            .build());
    }

    private static void addDebugEntries(ConfigCategory debug, ConfigEntryBuilder entries)
    {
        var config = RadarClient.config;

        debug.addEntry(entries.startBooleanToggle(
                text("option.spawn_radar.show_light_levels"),
                config.showSpawnerLightLevels)
            .setSaveConsumer(value -> config.showSpawnerLightLevels = value)
            .setDefaultValue(ConfigManager.DEFAULT.showSpawnerLightLevels)
            .setTooltip(text("option.spawn_radar.show_light_levels.tooltip"))
            .build());
    }

    private static Component text(String key)
    {
        return Component.translatable(key);
    }

    private static int clampBackgroundProximity(int value)
    {
        return Math.max(8, Math.min(64, value));
    }

    private static String clusterColorKey(int index, int total)
    {
        boolean isLastSlot = index == total - 1;
        String suffix = isLastSlot ? "_plus" : "";
        return "option.spawn_radar.cluster_" + (index + 1) + suffix;
    }

    private static int normalizeThreadCountInput(int value)
    {
        int normalized = Math.max(1, Math.min(16, value));
        if (normalized == 1)
            return 1;
        return (normalized & 1) == 0 ? normalized : Math.min(16, normalized + 1);
    }
}
