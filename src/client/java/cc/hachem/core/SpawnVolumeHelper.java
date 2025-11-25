package cc.hachem.core;

import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.BlockPos;

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
}

