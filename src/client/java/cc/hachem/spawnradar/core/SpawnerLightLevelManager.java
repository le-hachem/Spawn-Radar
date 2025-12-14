package cc.hachem.spawnradar.core;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;import net.minecraft.core.BlockPos;

public final class SpawnerLightLevelManager
{
    private static final Set<BlockPos> FORCE_SHOW = ConcurrentHashMap.newKeySet();
    private static final Set<BlockPos> FORCE_HIDE = ConcurrentHashMap.newKeySet();

    private SpawnerLightLevelManager() {}

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
}

