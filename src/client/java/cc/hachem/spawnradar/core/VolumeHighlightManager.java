package cc.hachem.spawnradar.core;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;import net.minecraft.core.BlockPos;

public final class VolumeHighlightManager
{
    private static final Set<BlockPos> SPAWN_FORCE_SHOW = ConcurrentHashMap.newKeySet();
    private static final Set<BlockPos> SPAWN_FORCE_HIDE = ConcurrentHashMap.newKeySet();
    private static final Set<BlockPos> MOB_FORCE_SHOW = ConcurrentHashMap.newKeySet();
    private static final Set<BlockPos> MOB_FORCE_HIDE = ConcurrentHashMap.newKeySet();

    private VolumeHighlightManager() {}

    public static boolean toggleSpawnVolume(BlockPos pos, boolean defaultState)
    {
        BlockPos immutable = pos.immutable();
        boolean current = isSpawnVolumeEnabled(immutable, defaultState);
        boolean newValue = !current;
        applyOverride(immutable, newValue, defaultState, SPAWN_FORCE_SHOW, SPAWN_FORCE_HIDE);
        return newValue;
    }

    public static boolean toggleMobCapVolume(BlockPos pos, boolean defaultState)
    {
        BlockPos immutable = pos.immutable();
        boolean current = isMobCapVolumeEnabled(immutable, defaultState);
        boolean newValue = !current;
        applyOverride(immutable, newValue, defaultState, MOB_FORCE_SHOW, MOB_FORCE_HIDE);
        return newValue;
    }

    private static void applyOverride(BlockPos pos, boolean newValue, boolean defaultState,
                                      Set<BlockPos> forceShow, Set<BlockPos> forceHide)
    {
        forceShow.remove(pos);
        forceHide.remove(pos);
        if (newValue == defaultState)
            return;
        if (newValue)
            forceShow.add(pos);
        else
            forceHide.add(pos);
    }

    public static boolean isSpawnVolumeEnabled(BlockPos pos, boolean defaultState)
    {
        BlockPos immutable = pos.immutable();
        if (SPAWN_FORCE_HIDE.contains(immutable))
            return false;
        if (SPAWN_FORCE_SHOW.contains(immutable))
            return true;
        return defaultState;
    }

    public static boolean isMobCapVolumeEnabled(BlockPos pos, boolean defaultState)
    {
        BlockPos immutable = pos.immutable();
        if (MOB_FORCE_HIDE.contains(immutable))
            return false;
        if (MOB_FORCE_SHOW.contains(immutable))
            return true;
        return defaultState;
    }

    public static Set<BlockPos> getForcedSpawnShows()
    {
        return Collections.unmodifiableSet(SPAWN_FORCE_SHOW);
    }

    public static Set<BlockPos> getForcedMobCapShows()
    {
        return Collections.unmodifiableSet(MOB_FORCE_SHOW);
    }

    public static void clear()
    {
        SPAWN_FORCE_SHOW.clear();
        SPAWN_FORCE_HIDE.clear();
        MOB_FORCE_SHOW.clear();
        MOB_FORCE_HIDE.clear();
    }
}

