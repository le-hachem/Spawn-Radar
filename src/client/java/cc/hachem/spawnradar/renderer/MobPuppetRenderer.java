package cc.hachem.spawnradar.renderer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;import net.minecraft.client.Minecraft;import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.screens.inventory.InventoryScreen;import net.minecraft.world.entity.Entity;import net.minecraft.world.entity.EntityDimensions;import net.minecraft.world.entity.EntitySpawnReason;import net.minecraft.world.entity.EntityType;import net.minecraft.world.entity.LivingEntity;import net.minecraft.world.entity.Pose;

public final class MobPuppetRenderer
{
    private static final int MIN_PIXEL_SIZE = 12;
    private static final int MAX_PIXEL_SIZE = 64;
    private static final float BASE_DEPTH_SCALE = 0.0625f;
    private static final Map<EntityType<?>, LivingEntity> CACHE = new ConcurrentHashMap<>();

    private MobPuppetRenderer() {}

    public static void clearCache()
    {
        CACHE.clear();
    }

    public static void render(GuiGraphics context, EntityType<?> entityType, int x, int y, float size)
    {
        if (context == null || entityType == null)
            return;

        LivingEntity puppet = getOrCreate(entityType);
        if (puppet == null)
            return;

        int pixelSize = Math.max(MIN_PIXEL_SIZE, Math.min(MAX_PIXEL_SIZE, Math.round(size)));
        int right = x + pixelSize;
        int bottom = y + pixelSize;
        int renderSize = Math.max(MIN_PIXEL_SIZE, Math.round(pixelSize * 1.35f));

        float focusScale = BASE_DEPTH_SCALE + computeFocusScale(puppet);
        float referenceX = x - pixelSize * 0.6f;
        float referenceY = y - pixelSize * 0.25f;

        InventoryScreen.renderEntityInInventoryFollowsMouse(context,
                                   x, y,
                                   right, bottom,
                                   renderSize, focusScale,
                                   referenceX, referenceY,
                                   puppet);
    }

    private static float computeFocusScale(LivingEntity entity)
    {
        float headBoost = computeHeadFocusBoost(entity);
        if (headBoost <= 0f)
            return 0f;
        float entityScale = Math.max(0.001f, entity.getScale());
        return headBoost / entityScale;
    }

    private static float computeHeadFocusBoost(LivingEntity entity)
    {
        Pose pose = entity.getPose();
        EntityDimensions dims = entity.getDimensions(pose);
        if (isApproximatelyCube(dims))
            return 0f;

        float eyeHeight = entity.getEyeHeight(pose);
        float centerHeight = entity.getBbHeight() * 0.5f;
        float difference = eyeHeight - centerHeight;
        return difference <= 0f ? 0f : difference * 0.8f;
    }

    private static boolean isApproximatelyCube(EntityDimensions dims)
    {
        float epsilon = 0.15f;
        return Math.abs(dims.height() - dims.width()) <= epsilon;
    }

    private static LivingEntity getOrCreate(EntityType<?> type)
    {
        if (type == null)
            return null;
        return CACHE.computeIfAbsent(type, MobPuppetRenderer::createInstance);
    }

    private static LivingEntity createInstance(EntityType<?> type)
    {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null)
            return null;

        Entity entity = type.create(client.level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (!(entity instanceof LivingEntity living))
            return null;

        living.setYBodyRot(180f);
        living.setYHeadRot(180f);
        living.setYRot(180f);
        living.setXRot(0f);
        return living;
    }
}

