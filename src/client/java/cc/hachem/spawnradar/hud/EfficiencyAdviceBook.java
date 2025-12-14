package cc.hachem.spawnradar.hud;

import cc.hachem.spawnradar.RadarClient;
import cc.hachem.spawnradar.core.SpawnerEfficiencyManager;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class EfficiencyAdviceBook
{
    public record EfficiencyAdviceEntry(Component title, Component description) {}

    private EfficiencyAdviceBook() {}

    public static void open(SpawnerEfficiencyManager.EfficiencyResult result,
                            SpawnerEfficiencyManager.MobCapStatus mobCapStatus,
                            List<EfficiencyAdviceEntry> entries)
    {
        Minecraft client = Minecraft.getInstance();

        List<Component> pages = new ArrayList<>();
        pages.add(buildSummaryPage(result, mobCapStatus));

        for (EfficiencyAdviceEntry entry : entries)
            pages.add(buildEntryPage(entry));

        Component title = Component.translatable("text.spawn_radar.efficiency_advisor.summary.title");
        openBook(client, title, pages);
    }

    private static Component buildSummaryPage(SpawnerEfficiencyManager.EfficiencyResult result,
                                             SpawnerEfficiencyManager.MobCapStatus mobCapStatus)
    {
        MutableComponent summary = Component.empty()
            .append(Component.translatable("text.spawn_radar.efficiency_advisor.summary.title")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD, ChatFormatting.UNDERLINE))
            .append(Component.literal("\n\n\n"));

        summary = appendMetric(summary,
            "text.spawn_radar.efficiency_advisor.summary.efficiency",
            ChatFormatting.GOLD,
            SpawnerEfficiencyManager.formatPercentage(result.overall()));

        summary = appendMetric(summary,
            "text.spawn_radar.efficiency_advisor.summary.volume",
            ChatFormatting.GRAY,
            SpawnerEfficiencyManager.formatPercentage(result.volumeScore()));

        summary = appendMetric(summary,
            "text.spawn_radar.efficiency_advisor.summary.light",
            ChatFormatting.GRAY,
            SpawnerEfficiencyManager.formatPercentage(result.lightScore()));

        if (mobCapStatus != null)
        {
            summary = appendMetric(summary,
                "text.spawn_radar.efficiency_advisor.summary.mob_cap",
                ChatFormatting.RED,
                mobCapStatus.formatted());
        }

        return summary;
    }

    private static Component buildEntryPage(EfficiencyAdviceEntry entry)
    {
        return Component.literal("")
            .append(entry.title().copy())
            .append(Component.literal("\n\n").withStyle(ChatFormatting.RESET))
            .append(entry.description().copy());
    }

    private static MutableComponent appendMetric(MutableComponent base, String translationKey, ChatFormatting color, Object... args)
    {
        return base
            .append(Component.literal("\n"))
            .append(Component.translatable(translationKey, args).withStyle(color));
    }

    private static void openBook(Minecraft client, Component title, List<Component> pages)
    {
        boolean useDual = RadarClient.config == null || RadarClient.config.useDualPageBookUi;
        if (useDual)
        {
            client.setScreen(new DualPageBookScreen(title, pages));
        }
        else
        {
            client.setScreen(new BookViewScreen(new BookViewScreen.BookAccess(pages)));
        }
    }
}

