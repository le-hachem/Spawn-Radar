package cc.hachem.spawnradar.core;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class SpawnVolumeHelper
{
    private SpawnVolumeHelper() {}

    public static SpawnVolume compute(SpawnerInfo info)
    {
        if (info == null)
            return null;

        BlockPos pos = info.pos();
        double centerX = pos.getX() + 0.5;
        double centerZ = pos.getZ() + 0.5;

        double entityWidth = 0.0;
        double entityHeight = 0.0;
        EntityType<?> entityType = info.entityType();
        if (entityType != null)
        {
            EntityDimensions dims = entityType.getDimensions();
            entityWidth = Math.max(0.0, dims.width());
            entityHeight = Math.max(0.0, dims.height());
        }

        double horizontalSpan = entityWidth + 8.0;
        double verticalSpan = entityHeight + 2.0;
        double originY = pos.getY() - 1.0;
        double originX = centerX - horizontalSpan / 2.0;
        double originZ = centerZ - horizontalSpan / 2.0;

        return new SpawnVolume(
            originX,
            originY,
            originZ,
            horizontalSpan,
            verticalSpan,
            horizontalSpan
        );
    }

    public record SpawnVolume(
        double originX,
        double originY,
        double originZ,
        double width,
        double height,
        double depth
    )
    {
        public double maxX()
        {
            return originX + width;
        }

        public double maxY()
        {
            return originY + height;
        }

        public double maxZ()
        {
            return originZ + depth;
        }
    }

    public static VolumeBounds computeBlockBounds(Level world, SpawnVolume volume)
    {
        if (world == null || volume == null)
            return null;

        int minX = Mth.floor(volume.originX());
        int minY = Mth.floor(volume.originY());
        int minZ = Mth.floor(volume.originZ());
        int maxX = Mth.floor(volume.maxX() - 1e-3);
        int maxY = Mth.floor(volume.maxY() - 1e-3);
        int maxZ = Mth.floor(volume.maxZ() - 1e-3);

        if (maxX < minX || maxY < minY || maxZ < minZ)
            return null;

        int worldBottom = world.getMinY();
        int worldTop = world.getHeight() - 1;
        minY = Math.max(worldBottom, minY);
        maxY = Math.min(worldTop, maxY);

        if (maxY < minY)
            return null;

        return new VolumeBounds(minX, maxX, minY, maxY, minZ, maxZ);
    }

    public record VolumeBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {}
}

