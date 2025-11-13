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
    public static Screen create(Screen parent)
    {
        ConfigBuilder builder = ConfigBuilder.create()
            .setTitle(Text.translatable("option.spawn_radar.title"))
            .setSavingRunnable(ConfigSerializer::save)
            .setTransparentBackground(true)
            .setParentScreen(parent);

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        ConfigCategory general = builder.getOrCreateCategory(Text.translatable("option.spawn_radar.general"));

        general.addEntry(entryBuilder.startIntField(
                Text.translatable("option.spawn_radar.chunk_search_radius"),
                RadarClient.config.defaultSearchRadius
            )
            .setSaveConsumer(number -> RadarClient.config.defaultSearchRadius = number)
            .setDefaultValue(ConfigManager.DEFAULT.defaultSearchRadius)
            .build()
        );

        general.addEntry(entryBuilder.startIntField(
                Text.translatable("option.spawn_radar.min_spawners"),
                RadarClient.config.minimumSpawnersForRegion
            )
            .setSaveConsumer(number -> RadarClient.config.minimumSpawnersForRegion = number)
            .setDefaultValue(ConfigManager.DEFAULT.minimumSpawnersForRegion)
            .build()
        );

        general.addEntry(entryBuilder.startBooleanToggle(
                Text.translatable("option.spawn_radar.highlight_after_scan"),
                RadarClient.config.highlightAfterScan
            )
            .setSaveConsumer(value -> RadarClient.config.highlightAfterScan = value)
            .setDefaultValue(ConfigManager.DEFAULT.highlightAfterScan)
            .build()
        );

        general.addEntry(entryBuilder
            .startEnumSelector(
                Text.translatable("option.spawn_radar.default_cluster_sort_type"),
                SpawnerCluster.SortType.class,
                RadarClient.config.defaultSortType
            )
            .setEnumNameProvider(e -> Text.translatable(e.toString()))
            .setDefaultValue(ConfigManager.DEFAULT.defaultSortType)
            .setSaveConsumer(value -> RadarClient.config.defaultSortType = value)
            .build()
        );

        general.addEntry(entryBuilder
            .startEnumSelector(
                Text.translatable("option.spawn_radar.cluster_proximity_sort_order"),
                ConfigManager.SortOrder.class,
                RadarClient.config.clusterProximitySortOrder
            )
            .setEnumNameProvider(e -> Text.translatable(e.toString()))
            .setDefaultValue(ConfigManager.DEFAULT.clusterProximitySortOrder)
            .setSaveConsumer(value -> RadarClient.config.clusterProximitySortOrder = value)
            .build()
        );

        general.addEntry(entryBuilder
            .startEnumSelector(
                Text.translatable("option.spawn_radar.cluster_size_sort_order"),
                ConfigManager.SortOrder.class,
                RadarClient.config.clusterSizeSortOrder
            )
            .setEnumNameProvider(e -> Text.translatable(e.toString()))
            .setDefaultValue(ConfigManager.DEFAULT.clusterSizeSortOrder)
            .setSaveConsumer(value -> RadarClient.config.clusterSizeSortOrder = value)
            .build()
        );

        general.addEntry(entryBuilder
            .startIntField(
                Text.translatable("option.spawn_radar.panel_element_count"),
                RadarClient.config.panelElementCount
            )
            .setDefaultValue(ConfigManager.DEFAULT.panelElementCount)
            .setSaveConsumer(value ->
            {
                RadarClient.config.panelElementCount = value;
                if (PanelWidget.getInstance() != null)
                    PanelWidget.setElementCount(value);
            })
            .build()
        );

        general.addEntry(entryBuilder
            .startIntSlider(
                Text.translatable("option.spawn_radar.panel_vertical_offset"),
                (int)(RadarClient.config.verticalPanelOffset*100),
                0,
                100
            )
            .setDefaultValue((int)(ConfigManager.DEFAULT.verticalPanelOffset*100))
            .setTextGetter(value -> Text.of(value + "%"))
            .setSaveConsumer(value ->
            {
                RadarClient.config.verticalPanelOffset = value/100.f;
                if (PanelWidget.getInstance() != null)
                    HudRenderer.updatePanelPosition();
            })

            .build()
        );

        ConfigCategory colors = builder.getOrCreateCategory(Text.translatable("option.spawn_radar.colors"));

        colors.addEntry(entryBuilder
            .startColorField(
                Text.translatable("option.spawn_radar.spawner_color"),
                RadarClient.config.spawnerHighlightColor
            )
            .setSaveConsumer(color -> RadarClient.config.spawnerHighlightColor = color)
            .setDefaultValue(ConfigManager.DEFAULT.spawnerHighlightColor)
            .build()
        );

        for (int i = 0; i < RadarClient.config.clusterColors.size(); i++)
        {
            final int index = i;
            String key = i < RadarClient.config.clusterColors.size() - 1
                               ? "option.spawn_radar.cluster_" + (i + 1)
                               : "option.spawn_radar.cluster_" + (i + 1) + "_plus";

            colors.addEntry(entryBuilder
                .startColorField(Text.translatable(key), RadarClient.config.clusterColors.get(i))
                .setSaveConsumer(color -> RadarClient.config.clusterColors.set(index, color))
                .setDefaultValue(ConfigManager.DEFAULT.clusterColors.get(i))
                .build()
            );
        }

        colors.addEntry(entryBuilder
            .startIntSlider(
                Text.translatable("option.spawn_radar.spawner_opacity"),
                RadarClient.config.spawnerHighlightOpacity,
                0,
                100
            )
            .setSaveConsumer(value -> RadarClient.config.spawnerHighlightOpacity = value)
            .setDefaultValue(ConfigManager.DEFAULT.spawnerHighlightOpacity)
            .setTextGetter(value -> Text.of(value + "%"))
            .build()
        );

        colors.addEntry(entryBuilder
            .startIntSlider(
                Text.translatable("option.spawn_radar.region_opacity"),
                RadarClient.config.regionHighlightOpacity,
                0,
                100
            )
            .setSaveConsumer(value -> RadarClient.config.regionHighlightOpacity = value)
            .setDefaultValue(ConfigManager.DEFAULT.regionHighlightOpacity)
            .setTextGetter(value -> Text.of(value + "%"))
            .build()
        );

        return builder.build();
    }
}
