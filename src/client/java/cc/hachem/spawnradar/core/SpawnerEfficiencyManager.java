package cc.hachem.spawnradar.core;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;import net.minecraft.core.BlockPos;import net.minecraft.world.entity.EntityType;import net.minecraft.world.entity.LivingEntity;import net.minecraft.world.level.Level;import net.minecraft.world.level.LightLayer;import net.minecraft.world.phys.AABB;

public final class SpawnerEfficiencyManager
{

    private static final Set<BlockPos> FORCE_SHOW = ConcurrentHashMap.newKeySet();
    private static final Set<BlockPos> FORCE_HIDE = ConcurrentHashMap.newKeySet();

    private SpawnerEfficiencyManager() {}

    public static boolean toggle(BlockPos pos, boolean defaultState)
    {
        BlockPos immutable = pos.immutable();
        boolean enabled = isEnabled(immutable, defaultState);
        boolean newValue = !enabled;
        applyOverride(immutable, newValue, defaultState);
        return newValue;
    }

    public static boolean isEnabled(BlockPos pos, boolean defaultState)
    {
        BlockPos immutable = pos.immutable();
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

    public static EfficiencyResult evaluate(Level world, SpawnerInfo info)
    {
        if (world == null || info == null)
            return null;

        SpawnVolumeHelper.SpawnVolume volume = SpawnVolumeHelper.compute(info);
        if (volume == null)
            return null;

        SpawnVolumeHelper.VolumeBounds bounds = SpawnVolumeHelper.computeBlockBounds(world, volume);
        if (bounds == null)
            return new EfficiencyResult(1d, 1d, 1d, 1d);

        double volumeScore = computeVolumeScore(world, bounds, info.pos());
        double lightScore = computeLightScore(world, bounds);
        double mobCapScore = computeMobCapPenalty(world, info);

        double overall = volumeScore * lightScore * mobCapScore;
        return new EfficiencyResult(overall, volumeScore, lightScore, mobCapScore);
    }

    public static double computeClusterEfficiency(Level world, SpawnerCluster cluster)
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

    private static double computeVolumeScore(Level world, SpawnVolumeHelper.VolumeBounds bounds, BlockPos spawnerPos)
    {
        long total = 0;
        long open = 0;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int x = bounds.minX(); x <= bounds.maxX(); x++)
        {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++)
            {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++)
                {
                    mutable.set(x, y, z);
                    if (mutable.equals(spawnerPos))
                        continue;
                    total++;
                    if (world.getBlockState(mutable).isAir())
                        open++;
                }
            }
        }

        if (total == 0)
            return 1d;

        double ratio = (double) open / (double) total;
        return clamp01(ratio);
    }

    private static double computeLightScore(Level world, SpawnVolumeHelper.VolumeBounds bounds)
    {
        long total = 0;
        long zeroLightBlocks = 0;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int x = bounds.minX(); x <= bounds.maxX(); x++)
        {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++)
            {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++)
                {
                    mutable.set(x, y, z);
                    int blockLight = world.getBrightness(LightLayer.BLOCK, mutable);
                    int skyLight = world.getBrightness(LightLayer.SKY, mutable);
                    int combined = Math.min(15, Math.max(blockLight, skyLight));
                    if (combined <= 0)
                        zeroLightBlocks++;
                    total++;
                }
            }
        }

        if (total == 0)
            return 1d;

        double ratio = (double) zeroLightBlocks / (double) total;
        return clamp01(ratio);
    }

    private static double clamp01(double value)
    {
        return Math.max(0d, Math.min(1d, value));
    }

    public record EfficiencyResult(double overall, double volumeScore, double lightScore, double mobCapScore)
    {
    }

    public record MobCapStatus(int mobCount, int capLimit)
    {
        public String formatted()
        {
            return mobCount + "/" + capLimit;
        }
    }

    public static MobCapStatus computeMobCapStatus(Level world, SpawnerInfo info)
    {
        if (world == null || info == null)
            return new MobCapStatus(0, 6);

        EntityType<?> entityType = info.entityType();
        if (entityType == null)
            return new MobCapStatus(0, 6);

        double centerX = info.pos().getX() + 0.5;
        double centerY = info.pos().getY() + 0.5;
        double centerZ = info.pos().getZ() + 0.5;
        double radius = 4.0;
        AABB mobCapBox = new AABB(
            centerX - radius, centerY - radius, centerZ - radius,
            centerX + radius, centerY + radius, centerZ + radius);

        List<LivingEntity> nearby = world.getEntitiesOfClass(
            LivingEntity.class,
            mobCapBox,
            entity -> entity.isAlive() && entity.getType() == entityType);

        return new MobCapStatus(Math.min(nearby.size(), 6), 6);
    }

    private static double computeMobCapPenalty(Level world, SpawnerInfo info)
    {
        MobCapStatus status = computeMobCapStatus(world, info);
        int mobCount = status.mobCount();
        double excess = Math.max(mobCount - 2d, 0d);
        double score = 1d - (excess / 4d);
        return Math.max(0d, score);
    }

    public static String formatPercentage(double ratio)
    {
        double value = clamp01(ratio) * 100d;
        return String.format(Locale.ROOT, "%.1f", value);
    }
}

