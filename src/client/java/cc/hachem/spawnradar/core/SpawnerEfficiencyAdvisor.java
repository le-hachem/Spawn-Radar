package cc.hachem.spawnradar.core;

import cc.hachem.spawnradar.RadarClient;
import cc.hachem.spawnradar.hud.EfficiencyAdviceBook;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class SpawnerEfficiencyAdvisor
{
    public enum AdviceResult
    {
        OPENED,
        OPTIMAL,
        NO_ADVICE,
        NO_DATA
    }

    private static final AtomicReference<BookPayload> PENDING_BOOK = new AtomicReference<>();

    private SpawnerEfficiencyAdvisor() {}

    public static void init()
    {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
        {
            if (!world.isClient() || hand != Hand.MAIN_HAND || hitResult == null)
                return ActionResult.PASS;
            if (!player.isSneaking())
                return ActionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();
            if (!world.getBlockState(pos).isOf(Blocks.SPAWNER))
                return ActionResult.PASS;

            SpawnerInfo info = BlockBank.get(pos);
            if (info == null)
                info = BlockBank.createSpawnerInfo(world, pos);

            AdviceResult outcome = openAdviceBook(world, info);
            if (outcome == AdviceResult.OPTIMAL)
                player.sendMessage(Text.translatable("text.spawn_radar.efficiency_advisor.optimal"), false);
            return ActionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client ->
        {
            if (client == null)
                return;
            BookPayload payload = PENDING_BOOK.getAndSet(null);
            if (payload == null)
                return;
            if (client.currentScreen instanceof ChatScreen)
            {
                PENDING_BOOK.compareAndSet(null, payload);
                return;
            }
            EfficiencyAdviceBook.open(payload.info(), payload.result(), payload.mobCapStatus(), payload.entries());
        });
        RadarClient.LOGGER.debug("Initialized SpawnerEfficiencyAdvisor.");
    }

    public static AdviceResult openAdviceBook(World world, SpawnerInfo info)
    {
        if (world == null || info == null)
            return AdviceResult.NO_DATA;

        SpawnerEfficiencyManager.EfficiencyResult result = SpawnerEfficiencyManager.evaluate(world, info);
        if (result == null)
            return AdviceResult.NO_DATA;

        var mobCapStatus = SpawnerEfficiencyManager.computeMobCapStatus(world, info);
        List<EfficiencyAdviceBook.EfficiencyAdviceEntry> adviceEntries = buildAdviceEntries(result, mobCapStatus);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null)
            return AdviceResult.NO_DATA;

        PENDING_BOOK.set(new BookPayload(info, result, mobCapStatus, List.copyOf(adviceEntries)));
        return AdviceResult.OPENED;
    }

    private static List<EfficiencyAdviceBook.EfficiencyAdviceEntry> buildAdviceEntries(SpawnerEfficiencyManager.EfficiencyResult result,
                                                                                       SpawnerEfficiencyManager.MobCapStatus mobCapStatus)
    {
        List<EfficiencyAdviceBook.EfficiencyAdviceEntry> fixes = new ArrayList<>();
        if (result.volumeScore() < 1d)
        {
            fixes.add(new EfficiencyAdviceBook.EfficiencyAdviceEntry(
                Text.translatable("text.spawn_radar.efficiency_advisor.volume.title").formatted(Formatting.GOLD, Formatting.BOLD),
                Text.translatable(
                    "text.spawn_radar.efficiency_advisor.volume.detail",
                    SpawnerEfficiencyManager.formatPercentage(result.volumeScore())
                ).formatted(Formatting.GRAY)));
        }
        if (result.lightScore() < 1d)
        {
            fixes.add(new EfficiencyAdviceBook.EfficiencyAdviceEntry(
                Text.translatable("text.spawn_radar.efficiency_advisor.light.title").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
                Text.translatable(
                    "text.spawn_radar.efficiency_advisor.light.detail",
                    SpawnerEfficiencyManager.formatPercentage(result.lightScore())
                ).formatted(Formatting.GRAY)));
        }
        if (mobCapStatus != null && mobCapStatus.mobCount() > 2)
            fixes.add(buildMobCapEntry(mobCapStatus));
        return fixes;
    }

    private static EfficiencyAdviceBook.EfficiencyAdviceEntry buildMobCapEntry(SpawnerEfficiencyManager.MobCapStatus status)
    {
        boolean saturated = status.mobCount() >= status.capLimit();
        String titleKey = saturated
            ? "text.spawn_radar.efficiency_advisor.mobcap_saturated.title"
            : "text.spawn_radar.efficiency_advisor.mobcap_crowded.title";
        String detailKey = saturated
            ? "text.spawn_radar.efficiency_advisor.mobcap_saturated.detail"
            : "text.spawn_radar.efficiency_advisor.mobcap_crowded.detail";
        return new EfficiencyAdviceBook.EfficiencyAdviceEntry(
            Text.translatable(titleKey).formatted(Formatting.RED, Formatting.BOLD),
            Text.translatable(detailKey, status.formatted()).formatted(Formatting.GRAY));
    }

    private record BookPayload(SpawnerInfo info,
                               SpawnerEfficiencyManager.EfficiencyResult result,
                               SpawnerEfficiencyManager.MobCapStatus mobCapStatus,
                               List<EfficiencyAdviceBook.EfficiencyAdviceEntry> entries) {}
}

