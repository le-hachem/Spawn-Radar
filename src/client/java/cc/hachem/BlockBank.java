package cc.hachem;

import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class BlockBank
{
    private static final List<BlockPos> HIGHLIGHTED_BLOCKS = new CopyOnWriteArrayList<>();

    public static void add(BlockPos pos)
    {
        if (!HIGHLIGHTED_BLOCKS.contains(pos))
            HIGHLIGHTED_BLOCKS.add(pos);
    }

    public static void remove(BlockPos pos)
    {
        HIGHLIGHTED_BLOCKS.remove(pos);
    }

    public static void clear()
    {
        HIGHLIGHTED_BLOCKS.clear();
    }

    public static List<BlockPos> getAll()
    {
        return Collections.unmodifiableList(HIGHLIGHTED_BLOCKS);
    }
}