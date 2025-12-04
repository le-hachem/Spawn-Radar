package cc.hachem.spawnradar.hud;

import cc.hachem.spawnradar.RadarClient;
import cc.hachem.spawnradar.core.SpawnerEfficiencyManager;
import cc.hachem.spawnradar.core.SpawnerInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public final class EfficiencyAdviceBook
{
    public record EfficiencyAdviceEntry(Text title, Text description) {}

    private EfficiencyAdviceBook() {}

    public static void open(SpawnerInfo info,
                            SpawnerEfficiencyManager.EfficiencyResult result,
                            SpawnerEfficiencyManager.MobCapStatus mobCapStatus,
                            List<EfficiencyAdviceEntry> entries)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null)
            return;

        List<Text> pages = new ArrayList<>();
        pages.add(buildSummaryPage(info, result, mobCapStatus));

        for (EfficiencyAdviceEntry entry : entries)
            pages.add(buildEntryPage(entry));

        if (pages.isEmpty())
            return;

        Text title = Text.translatable("text.spawn_radar.efficiency_advisor.summary.title");
        openBook(client, title, pages);
    }

    private static Text buildSummaryPage(SpawnerInfo info,
                                         SpawnerEfficiencyManager.EfficiencyResult result,
                                         SpawnerEfficiencyManager.MobCapStatus mobCapStatus)
    {
        MutableText summary = Text.empty()
            .append(Text.translatable("text.spawn_radar.efficiency_advisor.summary.title")
                .formatted(Formatting.DARK_PURPLE, Formatting.BOLD, Formatting.UNDERLINE))
            .append(Text.literal("\n\n\n"));

        summary = appendMetric(summary,
            "text.spawn_radar.efficiency_advisor.summary.efficiency",
            Formatting.GOLD,
            SpawnerEfficiencyManager.formatPercentage(result.overall()));

        summary = appendMetric(summary,
            "text.spawn_radar.efficiency_advisor.summary.volume",
            Formatting.GRAY,
            SpawnerEfficiencyManager.formatPercentage(result.volumeScore()));

        summary = appendMetric(summary,
            "text.spawn_radar.efficiency_advisor.summary.light",
            Formatting.GRAY,
            SpawnerEfficiencyManager.formatPercentage(result.lightScore()));

        if (mobCapStatus != null)
        {
            summary = appendMetric(summary,
                "text.spawn_radar.efficiency_advisor.summary.mob_cap",
                Formatting.RED,
                mobCapStatus.formatted());
        }

        return summary;
    }

    private static Text buildEntryPage(EfficiencyAdviceEntry entry)
    {
        return Text.literal("")
            .append(entry.title().copy())
            .append(Text.literal("\n\n").formatted(Formatting.RESET))
            .append(entry.description().copy());
    }

    private static MutableText appendMetric(MutableText base, String translationKey, Formatting color, Object... args)
    {
        return base
            .append(Text.literal("\n"))
            .append(Text.translatable(translationKey, args).formatted(color));
    }

    private static void openBook(MinecraftClient client, Text title, List<Text> pages)
    {
        boolean useDual = RadarClient.config == null || RadarClient.config.useDualPageBookUi;
        if (useDual)
        {
            client.setScreen(new DualPageBookScreen(title, pages));
        }
        else
        {
            client.setScreen(new BookScreen(new BookScreen.Contents(pages)));
        }
    }
}

