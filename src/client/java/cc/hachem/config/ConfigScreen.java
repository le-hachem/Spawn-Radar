package cc.hachem.config;

import cc.hachem.RadarClient;
import cc.hachem.core.SpawnerCluster;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.DropdownMenuBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.Arrays;

public class ConfigScreen
{
    public static Screen create(Screen parent)
    {
        ConfigBuilder builder = ConfigBuilder.create()
            .setTitle(Text.of("Spawn Radar Options"))
            .setSavingRunnable(ConfigSerializer::save)
            .setTransparentBackground(true)
            .setParentScreen(parent);

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        ConfigCategory general = builder.getOrCreateCategory(Text.of("General"));

        general.addEntry(entryBuilder.startIntField(Text.of("Chunk search radius"), RadarClient.config.defaultSearchRadius)
            .setSaveConsumer(number -> RadarClient.config.defaultSearchRadius = number)
            .build());

        general.addEntry(entryBuilder.startIntField(Text.of("Min spawners to highlight cluster"), RadarClient.config.minimumSpawnersForRegion)
            .setSaveConsumer(number -> RadarClient.config.minimumSpawnersForRegion = number)
            .build());

        general.addEntry(entryBuilder
            .startDropdownMenu(Text.of("Default cluster sort type"),
                DropdownMenuBuilder.TopCellElementBuilder.of(RadarClient.config.defaultSortType,
                    str -> Arrays.stream(SpawnerCluster.SortType.values())
                                 .filter(e -> e.getName().equals(str))
                                 .findFirst()
                                 .orElse(SpawnerCluster.SortType.NO_SORT),
                    obj -> Text.of(obj.getName())),
                DropdownMenuBuilder.CellCreatorBuilder.of(20, 150, 5, obj -> Text.of(obj.getName())))
            .setDefaultValue(SpawnerCluster.SortType.NO_SORT)
            .setSelections(Arrays.asList(SpawnerCluster.SortType.values()))
            .setSaveConsumer(selection -> RadarClient.config.defaultSortType = selection)
            .build());

        general.addEntry(entryBuilder
            .startDropdownMenu(Text.of("Cluster proximity sort order"),
                DropdownMenuBuilder.TopCellElementBuilder.of(
                    RadarClient.config.clusterProximitySortOrder,
                    str -> Arrays.stream(ConfigManager.SortOrder.values())
                                .filter(e -> e.getName().equals(str))
                                .findFirst()
                                .orElse(ConfigManager.SortOrder.DESCENDING),
                    obj -> Text.of(obj.getName())
                ),
                DropdownMenuBuilder.CellCreatorBuilder.of(20, 150, 5, obj -> Text.of(obj.getName()))
            )
            .setDefaultValue(ConfigManager.SortOrder.DESCENDING)
            .setSelections(Arrays.asList(ConfigManager.SortOrder.values()))
            .setSaveConsumer(selection -> RadarClient.config.clusterProximitySortOrder = selection)
            .build()
        );

        general.addEntry(entryBuilder
            .startDropdownMenu(Text.of("Cluster size sort order"),
                DropdownMenuBuilder.TopCellElementBuilder.of(
                    RadarClient.config.clusterSizeSortOrder,
                    str -> Arrays.stream(ConfigManager.SortOrder.values())
                                .filter(e -> e.getName().equals(str))
                                .findFirst()
                                .orElse(ConfigManager.SortOrder.ASCENDING),
                    obj -> Text.of(obj.getName())
                ),
                DropdownMenuBuilder.CellCreatorBuilder.of(20, 150, 5, obj -> Text.of(obj.getName()))
            )
            .setDefaultValue(ConfigManager.SortOrder.ASCENDING)
            .setSelections(Arrays.asList(ConfigManager.SortOrder.values()))
            .setSaveConsumer(selection -> RadarClient.config.clusterSizeSortOrder = selection)
            .build()
        );

        ConfigCategory colors = builder.getOrCreateCategory(Text.of("Color Options"));
        colors.addEntry(entryBuilder.startColorField(Text.of("Spawner highlight color"), RadarClient.config.spawnerHighlightColor)
            .setSaveConsumer(color -> RadarClient.config.spawnerHighlightColor = color)
            .build());

        for (int i = 0; i < RadarClient.config.clusterColors.size(); i++)
        {
            final int index = i;
            String label = i < RadarClient.config.clusterColors.size() - 1
                               ? "Cluster with " + (i + 1) + " spawner(s)"
                               : "Cluster with " + (i + 1) + "+ spawners";

            colors.addEntry(entryBuilder.startColorField(Text.of(label), RadarClient.config.clusterColors.get(i))
                .setSaveConsumer(color -> RadarClient.config.clusterColors.set(index, color))
                .build());
        }

        return builder.build();
    }
}