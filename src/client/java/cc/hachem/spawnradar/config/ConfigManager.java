package cc.hachem.spawnradar.config;

import cc.hachem.spawnradar.RadarClient;
import cc.hachem.spawnradar.core.SpawnerCluster;

import java.util.ArrayList;
import java.util.List;

public class ConfigManager
{
    private static final List<Integer> DEFAULT_CLUSTER_COLORS = List.of(
        0x00FFFF,
        0x00FF00,
        0xFFFF00,
        0xFF0000,
        0xFF00FF
    );

    public static final ConfigManager DEFAULT = new ConfigManager();

    public enum SortOrder
    {
        ASCENDING("option.spawn_radar.sort_order.ascending"),
        DESCENDING("option.spawn_radar.sort_order.descending");

        private final String name;

        SortOrder(String name) { this.name = name; }

        public String toString() { return name; }
    }

    public enum HudHorizontalAlignment
    {
        LEFT("option.spawn_radar.hud_alignment.left"),
        RIGHT("option.spawn_radar.hud_alignment.right");

        private final String name;

        HudHorizontalAlignment(String name) { this.name = name; }

        public String toString() { return name; }
    }

    public enum SpawnerIconMode
    {
        MOB_PUPPET("option.spawn_radar.spawner_icon_mode.mob_puppet"),
        SPAWN_EGG("option.spawn_radar.spawner_icon_mode.spawn_egg");

        private final String name;

        SpawnerIconMode(String name) { this.name = name; }

        public String toString() { return name; }
    }

    public SpawnerCluster.SortType defaultSortType = SpawnerCluster.SortType.NO_SORT;
    public SortOrder clusterProximitySortOrder = SortOrder.ASCENDING;
    public SortOrder clusterSizeSortOrder = SortOrder.DESCENDING;

    public int spawnerHighlightColor = 0xFFFFFF;
    public int minimumSpawnersForRegion = 1;
    public int defaultSearchRadius = 64;
    public int scanThreadCount = 4;

    public int spawnerHighlightOpacity = 50;
    public int regionHighlightOpacity = 30;
    public boolean useOutlineSpawnerHighlight = false;
    public boolean showSpawnerSpawnVolume = false;
    public int spawnVolumeColor = 0x4BA3FF;
    public int spawnVolumeOpacity = 45;
    public boolean showSpawnerMobCapVolume = false;
    public boolean showSpawnerEfficiencyLabel = false;
    public boolean showSpawnerMobCapStatus = false;
    public boolean showSpawnerLightLevels = false;
    public boolean useDualPageBookUi = true;
    public int mobCapVolumeColor = 0xFFAA00;
    public int mobCapVolumeOpacity = 25;
    public int spawnerOutlineColor = 0xFFFFFF;
    public float spawnerOutlineThickness = 0.5f;

    public boolean highlightAfterScan = false;
    public boolean frustumCullingEnabled = true;
    public Boolean showWelcomeMessage = true;

    public double verticalPanelOffset = 0.1;
    public int panelElementCount = 5;
    public HudHorizontalAlignment panelHorizontalAlignment = HudHorizontalAlignment.LEFT;
    public SpawnerIconMode spawnerIconMode = SpawnerIconMode.MOB_PUPPET;
    public boolean useCachedSpawnersForScan = true;
    public boolean autoHighlightAlertedClusters = false;
    public boolean processChunksOnGeneration = true;
    public int backgroundClusterAlertThreshold = 4;
    public int backgroundClusterProximity = 0;

    public List<Integer> clusterColors = defaultClusterColors();

    public void normalize()
    {
        ensureColorPalette();
        ensureHudAlignment();
        ensureScanThreadCount();
        ensureSpawnerIconMode();
        ensureBackgroundProcessing();

        if (showWelcomeMessage == null)
            showWelcomeMessage = DEFAULT.showWelcomeMessage;
    }

    public void ensureColorPalette()
    {
        if (clusterColors == null)
        {
            resetClusterColors("missing");
            return;
        }

        clusterColors.removeIf(java.util.Objects::isNull);

        if (clusterColors.isEmpty())
        {
            resetClusterColors("empty");
            return;
        }

        int originalSize = clusterColors.size();
        while (clusterColors.size() < DEFAULT_CLUSTER_COLORS.size())
            clusterColors.add(DEFAULT_CLUSTER_COLORS.get(clusterColors.size()));

        if (clusterColors.size() != originalSize)
            RadarClient.LOGGER.warn(
                "Cluster color palette had {} entries; padded to {}",
                originalSize,
                clusterColors.size()
            );
    }

    private void resetClusterColors(String reason)
    {
        clusterColors = defaultClusterColors();
        RadarClient.LOGGER.warn(
            "Cluster color palette {}; restored defaults.",
            reason
        );
    }

    private static List<Integer> defaultClusterColors()
    {
        return new ArrayList<>(DEFAULT_CLUSTER_COLORS);
    }

    public static int getClusterColor(int spawnerCount)
    {
        if (spawnerCount <= 0)
            return 0xFFFFFF;

        RadarClient.config.ensureColorPalette();

        if (spawnerCount <= RadarClient.config.clusterColors.size())
            return RadarClient.config.clusterColors.get(
                Math.max(0, spawnerCount - 1)
            );

        return RadarClient.config.clusterColors.getLast();
    }

    public void ensureHudAlignment()
    {
        if (panelHorizontalAlignment == null)
            panelHorizontalAlignment = DEFAULT.panelHorizontalAlignment;
    }

    public void ensureSpawnerIconMode()
    {
        if (spawnerIconMode == null)
            spawnerIconMode = DEFAULT.spawnerIconMode;
    }

    public void ensureBackgroundProcessing()
    {
        backgroundClusterAlertThreshold =
            Math.max(2, backgroundClusterAlertThreshold);
        backgroundClusterProximity =
            Math.max(8, backgroundClusterProximity);
    }

    public void ensureScanThreadCount()
    {
        int clamped = Math.max(1, Math.min(16, scanThreadCount));
        if (clamped != 1 && (clamped & 1) != 0)
            clamped = Math.min(16, clamped + 1);

        scanThreadCount = clamped;
    }
}
