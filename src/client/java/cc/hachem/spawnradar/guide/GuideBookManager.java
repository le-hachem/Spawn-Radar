package cc.hachem.spawnradar.guide;

import cc.hachem.spawnradar.RadarClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;import net.minecraft.ChatFormatting;import net.minecraft.client.Minecraft;import net.minecraft.client.gui.screens.inventory.BookViewScreen;import net.minecraft.network.chat.Component;import net.minecraft.network.chat.MutableComponent;import net.minecraft.resources.ResourceLocation;import cc.hachem.spawnradar.hud.DualPageBookScreen;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GuideBookManager
{
    private static final AtomicBoolean PENDING_OPEN = new AtomicBoolean(false);
    private static List<Component> guidePages = List.of();
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
            openGuideScreen(client);
        });
    }

    public static void openGuide()
    {
        Minecraft client = Minecraft.getInstance();
        if (client == null)
            return;
        ensureGuidePages();
        PENDING_OPEN.set(true);
    }

    static List<Component> getGuidePages()
    {
        ensureGuidePages();
        return guidePages;
    }

    private static void ensureGuidePages()
    {
        Minecraft client = Minecraft.getInstance();
        if (client == null)
            return;
        String languageCode = client.getLanguageManager().getSelected();
        if (languageCode == null || languageCode.isBlank())
            languageCode = "en_us";
        languageCode = languageCode.toLowerCase(Locale.ROOT);
        if (!languageCode.equals(cachedLanguageCode) || guidePages.isEmpty())
        {
            guidePages = loadGuidePages(languageCode);
            cachedLanguageCode = languageCode;
        }
    }

    private static List<Component> loadGuidePages(String languageCode)
    {
        Minecraft client = Minecraft.getInstance();
        if (client == null)
            return getDefaultPages();

        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(RadarClient.MOD_ID, "guide/" + languageCode + ".txt");
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

    private static List<Component> getDefaultPages()
    {
        MutableComponent fallback = Component.literal("Spawn Radar Manual")
            .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)
            .append(Component.literal("\n\nGuide data could not be loaded.\nAdd an assets/radar/guide/<lang>.txt file or reinstall the mod.")
                .withStyle(ChatFormatting.GRAY));
        return List.of(fallback);
    }

    private static void openGuideScreen(Minecraft client)
    {
        List<Component> pages = getGuidePages();
        Component title = Component.literal("Spawn Radar Guide");
        boolean useDual = RadarClient.config == null || RadarClient.config.useDualPageBookUi;
        if (useDual)
            client.setScreen(new DualPageBookScreen(title, pages));
        else
            client.setScreen(new BookViewScreen(new BookViewScreen.BookAccess(pages)));
    }
}

