package cc.hachem.core;

import cc.hachem.RadarClient;
import cc.hachem.hud.EfficiencyAdviceBook;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class SpawnerEfficiencyAdvisor
{
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

            SpawnerEfficiencyManager.EfficiencyResult result = SpawnerEfficiencyManager.evaluate(world, info);
            if (result == null)
                return ActionResult.PASS;

            var mobCapStatus = SpawnerEfficiencyManager.computeMobCapStatus(world, info);
            boolean mobCapIssue = result.mobCapScore() < 100d;

            if (isPerfect(result.overall()) && !mobCapIssue)
            {
                player.sendMessage(Text.translatable("text.spawn_radar.efficiency_advisor.optimal"), false);
                return ActionResult.PASS;
            }

            List<EfficiencyAdviceBook.EfficiencyAdviceEntry> adviceEntries = buildAdviceEntries(result, mobCapStatus);
            if (adviceEntries.isEmpty() && !mobCapIssue)
                return ActionResult.PASS;

            EfficiencyAdviceBook.open(info, result, mobCapStatus, adviceEntries);
            return ActionResult.PASS;
        });
        RadarClient.LOGGER.debug("Initialized SpawnerEfficiencyAdvisor.");
    }

    private static List<EfficiencyAdviceBook.EfficiencyAdviceEntry> buildAdviceEntries(SpawnerEfficiencyManager.EfficiencyResult result,
                                                                  SpawnerEfficiencyManager.MobCapStatus mobCapStatus)
    {
        List<EfficiencyAdviceBook.EfficiencyAdviceEntry> fixes = new ArrayList<>();
        if (!isPerfect(result.volumeScore()))
        {
            fixes.add(new EfficiencyAdviceBook.EfficiencyAdviceEntry(
                Text.translatable("text.spawn_radar.efficiency_advisor.volume.title").formatted(Formatting.GOLD, Formatting.BOLD),
                Text.translatable(
                    "text.spawn_radar.efficiency_advisor.volume.detail",
                    Math.round(result.volumeScore())
                ).formatted(Formatting.GRAY)));
        }
        if (!isPerfect(result.lightScore()))
        {
            fixes.add(new EfficiencyAdviceBook.EfficiencyAdviceEntry(
                Text.translatable("text.spawn_radar.efficiency_advisor.light.title").formatted(Formatting.YELLOW, Formatting.BOLD),
                Text.translatable(
                    "text.spawn_radar.efficiency_advisor.light.detail",
                    Math.round(result.lightScore())
                ).formatted(Formatting.GRAY)));
        }
        if (mobCapStatus != null && result.mobCapScore() < 100d)
            fixes.add(buildMobCapEntry(mobCapStatus));
        return fixes;
    }

    private static boolean isPerfect(double score)
    {
        return Math.round(score) >= 100;
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
}

