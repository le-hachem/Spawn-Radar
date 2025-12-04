package cc.hachem.spawnradar.core;

import net.minecraft.entity.EntityType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public record SpawnerInfo(BlockPos pos, @Nullable EntityType<?> entityType)
{
    public boolean hasKnownMob()
    {
        return entityType != null;
    }

    public String mobName()
    {
        return hasKnownMob() ? entityType.getName().getString() : "Unknown";
    }

    public @Nullable Identifier mobId()
    {
        return hasKnownMob() ? EntityType.getId(entityType) : null;
    }
}

