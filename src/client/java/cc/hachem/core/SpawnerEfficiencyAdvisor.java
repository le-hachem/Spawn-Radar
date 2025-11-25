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
import java.util.Locale;

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
                player.sendMessage(Text.literal("Spawner efficiency is already optimal."), false);
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
                Text.literal("Spawn Volume Blocked").formatted(Formatting.GOLD, Formatting.BOLD),
                Text.literal(String.format(
                    Locale.ROOT,
                    "Only %.0f%% of the spawn volume is open.\nRemove blocks, trapdoors, water, and slabs inside the highlighted volume so mobs have valid positions.",
                    result.volumeScore())).formatted(Formatting.GRAY)));
        }
        if (!isPerfect(result.lightScore()))
        {
            fixes.add(new EfficiencyAdviceBook.EfficiencyAdviceEntry(
                Text.literal("Area Too Bright").formatted(Formatting.YELLOW, Formatting.BOLD),
                Text.literal(String.format(
                    Locale.ROOT,
                    "Darkness is only %.0f%% inside the spawn area.\nBreak or cover light sources and block skylight so the volume reaches light level 0.",
                    result.lightScore())).formatted(Formatting.GRAY)));
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
        String title = saturated ? "Mob Cap Saturated" : "Mob Cap Crowded";
        String detail = saturated
            ? String.format(Locale.ROOT,
                "The mob cap contains %s mobs.\nKill or move mobs outside the 8-block cube so the spawner can run again.",
                status.formatted())
            : String.format(Locale.ROOT,
                "The mob cap is currently %s.\nMove or remove mobs near the spawner to regain headroom and raise efficiency.",
                status.formatted());
        return new EfficiencyAdviceBook.EfficiencyAdviceEntry(
            Text.literal(title).formatted(Formatting.RED, Formatting.BOLD),
            Text.literal(detail).formatted(Formatting.GRAY));
    }
}

