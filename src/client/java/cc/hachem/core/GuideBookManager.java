package cc.hachem.core;

import cc.hachem.RadarClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GuideBookManager
{
    private static final int ABOUT_PAGE_INDEX = 3;
    private static final int COMMAND_PAGE_INDEX = 5;
    private static final int CONFIG_PAGE_INDEX = 7;
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

    private static List<Text> getGuidePages()
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

        var manager = client.getResourceManager();
        Identifier id = Identifier.of(RadarClient.MOD_ID, "guide/" + languageCode + ".txt");
        try (var resource = manager.open(id))
        {
            return GuideScriptParser.parse(resource);
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
        return List.of(
            buildTitlePage(),
            buildChapterPage(),
            buildAboutPageOne(),
            buildAboutPageTwo(),
            buildCommandPageOne(),
            buildCommandPageTwo(),
            buildConfigPageOne(),
            buildConfigPageTwo()
        );
    }

    private static Text buildTitlePage()
    {
        return compose(
            newline(),
            newline(),
            center(heading("guide.spawn_radar.title")),
            newline(),
            center(subheading("guide.spawn_radar.subtitle")),
            newline(),
            newline(),
            center(author("guide.spawn_radar.author")),
            newline(),
            newline(),
            center(paragraph("guide.spawn_radar.title.cta"))
        );
    }

    private static Text buildChapterPage()
    {
        return compose(
            heading("guide.spawn_radar.chapters.title"),
            newline(),
            info("guide.spawn_radar.toc.hint"),
            newline(),
            chapterLink("guide.spawn_radar.chapter.about", ABOUT_PAGE_INDEX),
            newline(),
            chapterLink("guide.spawn_radar.chapter.commands", COMMAND_PAGE_INDEX),
            newline(),
            chapterLink("guide.spawn_radar.chapter.config", CONFIG_PAGE_INDEX)
        );
    }

    private static Text buildAboutPageOne()
    {
        return compose(
            heading("guide.spawn_radar.heading.about"),
            newline(),
            paragraphBlock("guide.spawn_radar.about.page1"),
            newline(),
            paragraphBlock("guide.spawn_radar.about.page2")
        );
    }

    private static Text buildAboutPageTwo()
    {
        return compose(
            heading("guide.spawn_radar.heading.about"),
            newline(),
            paragraphBlock("guide.spawn_radar.about.page3")
        );
    }

    private static Text buildCommandPageOne()
    {
        return compose(
            heading("guide.spawn_radar.heading.commands"),
            newline(),
            paragraphBlock("guide.spawn_radar.commands.page1"),
            newline(),
            paragraphBlock("guide.spawn_radar.commands.page2")
        );
    }

    private static Text buildCommandPageTwo()
    {
        return compose(
            heading("guide.spawn_radar.heading.commands"),
            newline(),
            paragraphBlock("guide.spawn_radar.commands.page3")
        );
    }

    private static Text buildConfigPageOne()
    {
        return compose(
            heading("guide.spawn_radar.heading.config"),
            newline(),
            paragraphBlock("guide.spawn_radar.config.page1"),
            newline(),
            paragraphBlock("guide.spawn_radar.config.page2")
        );
    }

    private static Text buildConfigPageTwo()
    {
        return compose(
            heading("guide.spawn_radar.heading.config"),
            newline(),
            paragraphBlock("guide.spawn_radar.config.page3")
        );
    }

    private static MutableText heading(String key)
    {
        return Text.translatable(key).setStyle(Style.EMPTY.withBold(true).withColor(Formatting.DARK_PURPLE));
    }

    private static MutableText subheading(String key)
    {
        return Text.translatable(key).setStyle(Style.EMPTY.withColor(Formatting.DARK_BLUE));
    }

    private static MutableText author(String key)
    {
        return Text.translatable(key).setStyle(Style.EMPTY.withColor(Formatting.DARK_GRAY).withItalic(true));
    }

    private static MutableText paragraph(String key)
    {
        return paragraphBlock(key);
    }

    private static MutableText paragraphBlock(String key)
    {
        return Text.translatable(key).setStyle(Style.EMPTY.withColor(Formatting.DARK_GRAY));
    }

    private static MutableText info(String key)
    {
        return Text.translatable(key).setStyle(Style.EMPTY.withColor(Formatting.GRAY));
    }

    private static MutableText chapterLink(String key, int targetPage)
    {
        return Text.literal("• ").formatted(Formatting.DARK_BLUE)
            .append(Text.translatable(key).setStyle(
                Style.EMPTY.withColor(Formatting.BLUE).withUnderline(true)
                    .withClickEvent(new ClickEvent.ChangePage(targetPage))
                    .withHoverEvent(new HoverEvent.ShowText(Text.translatable("guide.spawn_radar.chapter.tooltip", targetPage)))
            ));
    }

    private static Text compose(Text... parts)
    {
        MutableText root = Text.empty();
        for (Text part : parts)
            root.append(part);
        return root;
    }

    private static MutableText newline()
    {
        return Text.literal("\n");
    }

    private static MutableText center(Text text)
    {
        return Text.literal("     ").append(text).append(Text.literal("\n"));
    }

    private static final class GuideScriptParser
    {
        private static final String PAGE_TOKEN = "@page";
        private static final String CENTER_PREFIX = "@center ";
        private static final String COMMENT_PREFIX = "#";
        private static final char FORMAT_CODE = '&';

        static List<Text> parse(InputStream stream) throws IOException
        {
            List<Text> pages = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
            {
                MutableText current = Text.empty();
                String line;
                while ((line = reader.readLine()) != null)
                {
                    line = line.replace("\r", "");
                    if (line.isEmpty())
                    {
                        current.append(newline());
                        continue;
                    }
                    if (line.startsWith(COMMENT_PREFIX))
                        continue;
                    if (line.equalsIgnoreCase(PAGE_TOKEN))
                    {
                        if (!isEmpty(current))
                        {
                            pages.add(current);
                            current = Text.empty();
                        }
                        continue;
                    }
                    boolean center = false;
                    if (line.startsWith(CENTER_PREFIX))
                    {
                        center = true;
                        line = line.substring(CENTER_PREFIX.length());
                    }
                    MutableText parsed = parseFormattedLine(line);
                    if (center)
                        parsed = center(parsed);
                    current.append(parsed).append(newline());
                }
                if (!isEmpty(current))
                    pages.add(current);
            }
            return pages.isEmpty() ? getDefaultPages() : pages;
        }

        private static boolean isEmpty(MutableText text)
        {
            return text.getSiblings().isEmpty() && text.getString().isBlank();
        }

        private static MutableText parseFormattedLine(String raw)
        {
            MutableText result = Text.empty();
            StringBuilder buffer = new StringBuilder();
            Style style = Style.EMPTY;
            for (int i = 0; i < raw.length(); i++)
            {
                char c = raw.charAt(i);
                if (c == FORMAT_CODE && i + 1 < raw.length())
                {
                    char code = Character.toLowerCase(raw.charAt(++i));
                    if (code == FORMAT_CODE)
                    {
                        buffer.append(FORMAT_CODE);
                        continue;
                    }
                    if (buffer.length() > 0)
                    {
                        result.append(Text.literal(buffer.toString()).setStyle(style));
                        buffer.setLength(0);
                    }
                    style = applyFormatting(style, code);
                    continue;
                }
                buffer.append(c);
            }
            if (buffer.length() > 0)
                result.append(Text.literal(buffer.toString()).setStyle(style));
            return result;
        }

        private static Style applyFormatting(Style current, char code)
        {
            switch (code)
            {
                case 'l':
                    return current.withBold(true);
                case 'o':
                    return current.withItalic(true);
                case 'n':
                    return current.withUnderline(true);
                case 'm':
                    return current.withStrikethrough(true);
                case 'r':
                    return Style.EMPTY;
                default:
                    Formatting color = Formatting.byCode(code);
                    if (color != null && color.isColor())
                        return Style.EMPTY.withColor(color);
                    return current;
            }
        }
    }
}
