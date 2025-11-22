package cc.hachem.renderer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    public static void render(DrawContext context, EntityType<?> entityType, int x, int y, float size)
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

        InventoryScreen.drawEntity(context,
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
        EntityPose pose = entity.getPose();
        EntityDimensions dims = entity.getDimensions(pose);
        if (isApproximatelyCube(dims))
            return 0f;

        float eyeHeight = entity.getEyeHeight(pose);
        float centerHeight = entity.getHeight() * 0.5f;
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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null)
            return null;

        Entity entity = type.create(client.world, SpawnReason.SPAWN_ITEM_USE);
        if (!(entity instanceof LivingEntity living))
            return null;

        living.setBodyYaw(180f);
        living.setHeadYaw(180f);
        living.setYaw(180f);
        living.setPitch(0f);
        return living;
    }
}

