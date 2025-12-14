package cc.hachem.spawnradar.core;

import net.minecraft.core.BlockPos;import net.minecraft.resources.ResourceLocation;import net.minecraft.world.entity.EntityType;import org.jetbrains.annotations.Nullable;

public record SpawnerInfo(BlockPos pos, @Nullable EntityType<?> entityType)
{
    public boolean hasKnownMob()
    {
        return entityType != null;
    }

    public String mobName()
    {
        return hasKnownMob() ? entityType.getDescription().getString() : "Unknown";
    }

    public @Nullable ResourceLocation mobId()
    {
        return hasKnownMob() ? EntityType.getKey(entityType) : null;
    }
}

