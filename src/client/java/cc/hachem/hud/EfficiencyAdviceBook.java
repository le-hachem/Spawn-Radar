package cc.hachem.hud;

import cc.hachem.core.SpawnerEfficiencyManager;
import cc.hachem.core.SpawnerInfo;
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

        client.setScreen(new BookScreen(new BookScreen.Contents(pages)));
    }

    private static Text buildSummaryPage(SpawnerInfo info,
                                         SpawnerEfficiencyManager.EfficiencyResult result,
                                         SpawnerEfficiencyManager.MobCapStatus mobCapStatus)
    {
        MutableText summary = Text.empty()
            .append(Text.translatable("text.spawn_radar.efficiency_advisor.summary.title")
                .formatted(Formatting.DARK_PURPLE, Formatting.BOLD, Formatting.UNDERLINE))
            .append(Text.literal("\n\n\n"))
            .append(Text.translatable("text.spawn_radar.efficiency_advisor.summary.efficiency",
                Math.round(result.overall())).formatted(Formatting.GOLD))
            .append(Text.translatable("text.spawn_radar.efficiency_advisor.summary.volume",
                Math.round(result.volumeScore())).formatted(Formatting.GRAY))
            .append(Text.translatable("text.spawn_radar.efficiency_advisor.summary.light",
                Math.round(result.lightScore())).formatted(Formatting.GRAY));

        if (mobCapStatus != null)
        {
            summary.append(Text.translatable("text.spawn_radar.efficiency_advisor.summary.mob_cap",
                mobCapStatus.formatted()).formatted(Formatting.RED));
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
}

