package cc.hachem.core;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SpawnerEfficiencyManager
{
    private static final double VOLUME_WEIGHT = 0.5;
    private static final double LIGHT_WEIGHT = 0.3;
    private static final double MOB_CAP_WEIGHT = 0.2;

    private static final Set<BlockPos> FORCE_SHOW = ConcurrentHashMap.newKeySet();
    private static final Set<BlockPos> FORCE_HIDE = ConcurrentHashMap.newKeySet();

    private SpawnerEfficiencyManager() {}

    public static boolean toggle(BlockPos pos, boolean defaultState)
    {
        BlockPos immutable = pos.toImmutable();
        boolean enabled = isEnabled(immutable, defaultState);
        boolean newValue = !enabled;
        applyOverride(immutable, newValue, defaultState);
        return newValue;
    }

    public static boolean isEnabled(BlockPos pos, boolean defaultState)
    {
        BlockPos immutable = pos.toImmutable();
        if (FORCE_HIDE.contains(immutable))
            return false;
        if (FORCE_SHOW.contains(immutable))
            return true;
        return defaultState;
    }

    private static void applyOverride(BlockPos pos, boolean newValue, boolean defaultState)
    {
        FORCE_SHOW.remove(pos);
        FORCE_HIDE.remove(pos);
        if (newValue == defaultState)
            return;
        if (newValue)
            FORCE_SHOW.add(pos);
        else
            FORCE_HIDE.add(pos);
    }

    public static Set<BlockPos> getForcedShows()
    {
        return Collections.unmodifiableSet(FORCE_SHOW);
    }

    public static void clear()
    {
        FORCE_SHOW.clear();
        FORCE_HIDE.clear();
    }

    public static EfficiencyResult evaluate(World world, SpawnerInfo info)
    {
        if (world == null || info == null)
            return null;

        SpawnVolumeHelper.SpawnVolume volume = SpawnVolumeHelper.compute(info);
        if (volume == null)
            return null;

        VolumeLimits limits = buildLimits(world, volume);
        if (limits == null)
            return new EfficiencyResult(100d, 100d, 100d, 100d);

        double volumeScore = computeVolumeScore(world, limits);
        double lightScore = computeLightScore(world, limits);
        double mobCapScore = computeMobCapPenalty(world, info);
        double blended = volumeScore * VOLUME_WEIGHT
            + lightScore * LIGHT_WEIGHT
            + mobCapScore * MOB_CAP_WEIGHT;
        double overall = clampScore(Math.min(blended, mobCapScore));

        return new EfficiencyResult(overall, volumeScore, lightScore, mobCapScore);
    }

    public static double computeClusterEfficiency(World world, SpawnerCluster cluster)
    {
        if (world == null || cluster == null || cluster.spawners().size() < 2)
            return -1d;

        double total = 0d;
        int counted = 0;
        for (SpawnerInfo info : cluster.spawners())
        {
            EfficiencyResult result = evaluate(world, info);
            if (result == null)
                continue;
            total += result.overall();
            counted++;
        }
        return counted == 0 ? -1d : total / counted;
    }

    private static VolumeLimits buildLimits(World world, SpawnVolumeHelper.SpawnVolume volume)
    {
        int minX = MathHelper.floor(volume.originX());
        int minY = MathHelper.floor(volume.originY());
        int minZ = MathHelper.floor(volume.originZ());
        int maxX = MathHelper.floor(volume.maxX() - 1e-3);
        int maxY = MathHelper.floor(volume.maxY() - 1e-3);
        int maxZ = MathHelper.floor(volume.maxZ() - 1e-3);

        if (maxX < minX || maxY < minY || maxZ < minZ)
            return null;

        int worldBottom = world.getBottomY();
        int worldTop = world.getHeight() - 1;
        minY = Math.max(worldBottom, minY);
        maxY = Math.min(worldTop, maxY);

        if (maxY < minY)
            return null;

        return new VolumeLimits(minX, maxX, minY, maxY, minZ, maxZ);
    }

    private static double computeVolumeScore(World world, VolumeLimits limits)
    {
        long total = 0;
        long open = 0;
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = limits.minX; x <= limits.maxX; x++)
        {
            for (int y = limits.minY; y <= limits.maxY; y++)
            {
                for (int z = limits.minZ; z <= limits.maxZ; z++)
                {
                    mutable.set(x, y, z);
                    total++;
                    if (world.getBlockState(mutable).isAir())
                        open++;
                }
            }
        }

        if (total == 0)
            return 100d;

        double ratio = (double) open / (double) total;
        return clampScore(ratio * 100d);
    }

    private static double computeLightScore(World world, VolumeLimits limits)
    {
        long total = 0;
        double accumulated = 0d;
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = limits.minX; x <= limits.maxX; x++)
        {
            for (int y = limits.minY; y <= limits.maxY; y++)
            {
                for (int z = limits.minZ; z <= limits.maxZ; z++)
                {
                    mutable.set(x, y, z);
                    int blockLight = world.getLightLevel(LightType.BLOCK, mutable);
                    int skyLight = world.getLightLevel(LightType.SKY, mutable);
                    int combined = Math.min(15, Math.max(blockLight, skyLight));
                    accumulated += 1d - (combined / 15d);
                    total++;
                }
            }
        }

        if (total == 0)
            return 100d;

        double average = accumulated / (double) total;
        return clampScore(average * 100d);
    }

    private record VolumeLimits(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {}

    private static double clampScore(double value)
    {
        return Math.max(0d, Math.min(100d, value));
    }

    public record EfficiencyResult(double overall, double volumeScore, double lightScore, double mobCapScore)
    {
        public Map<String, Double> toBreakdown()
        {
            Map<String, Double> breakdown = new LinkedHashMap<>();
            breakdown.put("volume", volumeScore);
            breakdown.put("light", lightScore);
            breakdown.put("mobCap", mobCapScore);
            return breakdown;
        }
    }

    public record MobCapStatus(int mobCount, int capLimit)
    {
        public String formatted()
        {
            return mobCount + "/" + capLimit;
        }
    }

    public static MobCapStatus computeMobCapStatus(World world, SpawnerInfo info)
    {
        if (world == null || info == null)
            return new MobCapStatus(0, 6);

        EntityType<?> entityType = info.entityType();
        SpawnGroup spawnGroup = entityType != null ? entityType.getSpawnGroup() : SpawnGroup.MONSTER;

        double centerX = info.pos().getX() + 0.5;
        double centerY = info.pos().getY() + 0.5;
        double centerZ = info.pos().getZ() + 0.5;
        double radius = 8.0;
        Box mobCapBox = new Box(
            centerX - radius, centerY - radius, centerZ - radius,
            centerX + radius, centerY + radius, centerZ + radius);

        List<LivingEntity> nearby = world.getEntitiesByClass(
            LivingEntity.class,
            mobCapBox,
            entity -> entity.isAlive()
                && entity.getType() != null
                && entity.getType().getSpawnGroup() == spawnGroup);

        return new MobCapStatus(Math.min(nearby.size(), 6), 6);
    }

    private static double computeMobCapPenalty(World world, SpawnerInfo info)
    {
        MobCapStatus status = computeMobCapStatus(world, info);
        int mobCount = status.mobCount();
        int cap = status.capLimit();
        if (mobCount <= 1)
            return 100d;
        if (mobCount >= cap)
            return 0d;
        double score = 100d * (1d - ((mobCount - 1d) / (double) (cap - 1)));
        return clampScore(score);
    }
}

