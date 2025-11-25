package cc.hachem.guide;

import cc.hachem.RadarClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GuideBookManager
{
    private static final AtomicBoolean PENDING_OPEN = new AtomicBoolean(false);
    private static List<Text> guidePages = List.of();
    private static String cachedLanguageCode = "";

    private GuideBookManager() {}

    public static void init()
    {
        ClientTickEvents.END_CLIENT_TICK.register(client ->
        {
            if (!PENDING_OPEN.compareAndSet(true, false))
                return;
            if (client == null || client.isPaused())
                return;
            client.setScreen(new BookScreen(new BookScreen.Contents(getGuidePages())));
        });
    }

    public static void openGuide()
    {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null)
            return;
        ensureGuidePages();
        PENDING_OPEN.set(true);
    }

    static List<Text> getGuidePages()
    {
        ensureGuidePages();
        return guidePages;
    }

    private static void ensureGuidePages()
    {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null)
            return;
        String languageCode = client.getLanguageManager().getLanguage();
        if (languageCode == null || languageCode.isBlank())
            languageCode = "en_us";
        languageCode = languageCode.toLowerCase(Locale.ROOT);
        if (!languageCode.equals(cachedLanguageCode) || guidePages.isEmpty())
        {
            guidePages = loadGuidePages(languageCode);
            cachedLanguageCode = languageCode;
        }
    }

    private static List<Text> loadGuidePages(String languageCode)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null)
            return getDefaultPages();

        Identifier id = Identifier.of(RadarClient.MOD_ID, "guide/" + languageCode + ".txt");
        try (InputStream stream = client.getResourceManager().open(id))
        {
            return GuideScriptParser.parse(stream, GuideBookManager::getDefaultPages);
        }
        catch (Exception e)
        {
            RadarClient.LOGGER.warn("Falling back to default en_us guide due to error loading {}: {}", id, e.getMessage());
        }

        if (!"en_us".equals(languageCode))
            return loadGuidePages("en_us");
        return getDefaultPages();
    }

    private static List<Text> getDefaultPages()
    {
        MutableText fallback = Text.literal("Spawn Radar Manual")
            .formatted(Formatting.DARK_PURPLE, Formatting.BOLD)
            .append(Text.literal("\n\nGuide data could not be loaded.\nAdd an assets/radar/guide/<lang>.txt file or reinstall the mod.")
                .formatted(Formatting.GRAY));
        return List.of(fallback);
    }
}

