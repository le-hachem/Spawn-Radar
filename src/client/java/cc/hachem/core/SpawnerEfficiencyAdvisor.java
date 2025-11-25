package cc.hachem.core;

import cc.hachem.RadarClient;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
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
            if (!(hitResult instanceof BlockHitResult blockHit) || !player.isSneaking())
                return ActionResult.PASS;

            BlockPos pos = blockHit.getBlockPos();
            if (!world.getBlockState(pos).isOf(Blocks.SPAWNER))
                return ActionResult.PASS;

            SpawnerInfo info = BlockBank.get(pos);
            if (info == null)
                info = BlockBank.createSpawnerInfo(world, pos);

            SpawnerEfficiencyManager.EfficiencyResult result = SpawnerEfficiencyManager.evaluate(world, info);
            if (result == null)
                return ActionResult.PASS;

            var mobCapStatus = SpawnerEfficiencyManager.computeMobCapStatus(world, info);
            if (isPerfect(result.overall()))
            {
                player.sendMessage(Text.literal("Spawner efficiency is already optimal."), false);
                return ActionResult.PASS;
            }

            List<Text> suggestions = buildSuggestions(result, mobCapStatus);
            boolean mobCapLimited = mobCapStatus != null && result.mobCapScore() < 100d;
            if (mobCapLimited)
                suggestions.add(buildMobCapSuggestion(mobCapStatus));

            if (suggestions.isEmpty())
                return ActionResult.PASS;

            player.sendMessage(
                Text.literal(String.format(Locale.ROOT, "Efficiency %.0f%% - possible fixes:", result.overall())),
                false);
            suggestions.forEach(text -> player.sendMessage(text, false));

            return ActionResult.PASS;
        });
        RadarClient.LOGGER.debug("Initialized SpawnerEfficiencyAdvisor.");
    }

    private static List<Text> buildSuggestions(SpawnerEfficiencyManager.EfficiencyResult result,
                                               SpawnerEfficiencyManager.MobCapStatus mobCapStatus)
    {
        List<Text> fixes = new ArrayList<>();
        if (!isPerfect(result.volumeScore()))
        {
            fixes.add(Text.literal(String.format(
                Locale.ROOT,
                " • Clear blocks inside the spawn volume (%.0f%% open).",
                result.volumeScore())));
        }
        if (!isPerfect(result.lightScore()))
        {
            fixes.add(Text.literal(String.format(
                Locale.ROOT,
                " • Reduce block/sky light around the spawner to 0 (%.0f%% dark).",
                result.lightScore())));
        }
        return fixes;
    }

    private static boolean isPerfect(double score)
    {
        return Math.round(score) >= 100;
    }

    private static Text buildMobCapSuggestion(SpawnerEfficiencyManager.MobCapStatus status)
    {
        String action = status.mobCount() >= status.capLimit()
            ? "Clear mobs within 8 blocks to free the mob-cap"
            : "Reduce mobs near the spawner to improve mob-cap headroom";
        return Text.literal(String.format(
            Locale.ROOT,
            " • %s (Mob Cap %s).",
            action,
            status.formatted()));
    }
}

