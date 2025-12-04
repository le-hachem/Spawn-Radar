package cc.hachem.spawnradar.guide;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

final class GuideScriptParser
{
    private static final int MAX_LINES_PER_PAGE = 14;
    private static final int PAGE_WIDTH = 114;
    private static final String PAGE_TOKEN = "@page";
    private static final String CENTER_PAGE_TOKEN = "@centered_page";
    private static final String TOC_TOKEN = "@toc";
    private static final String CHAPTER_PREFIX = "@chapter";
    private static final String SECTION_PREFIX = "@section";
    private static final String TITLE_PREFIX = "@title";
    private static final String AUTHOR_PREFIX = "@author";
    private static final String COMMENT_PREFIX = "#";
    private static final char FORMAT_CODE = '&';
    private static final Map<String, InlineFormat> INLINE_DIRECTIVES = Map.of(
        "command", InlineFormat.COMMAND,
        "italic", InlineFormat.ITALIC,
        "bold", InlineFormat.BOLD
    );
    private static final int APPROX_LINE_CHAR_WIDTH = 28;

    private GuideScriptParser() {}

    static List<Text> parse(InputStream stream, Supplier<List<Text>> fallback) throws IOException
    {
        ParseContext context = new ParseContext();
        context.parse(stream);
        List<Text> pages = context.buildPages();
        if (pages.isEmpty() && fallback != null)
            return fallback.get();
        return pages;
    }

    private static MutableText newline()
    {
        return Text.literal("\n");
    }

    private static final class ParseContext
    {
        private final List<GuideEntry> entries = new ArrayList<>();
        private final List<GuideLine> navigationLines = new ArrayList<>();
        private final Map<String, TocChapter> tocChapters = new LinkedHashMap<>();
        private boolean hasTocPlaceholder = false;
        private int nextChapterOrder = 1;
        private int chapterAnchorCounter = 0;

        void parse(InputStream stream) throws IOException
        {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
            {
                String raw;
                while ((raw = reader.readLine()) != null)
                    handleLine(raw.replace("\r", ""));
            }

        }

        private void handleLine(String rawLine)
        {
            String trimmed = rawLine.trim();
            if (trimmed.isEmpty())
            {
                addGuideLine(GuideLine.blankLine());
                return;
            }

            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.startsWith(COMMENT_PREFIX))
                return;

            if (lower.equals(PAGE_TOKEN))
            {
                entries.add(PageBreakEntry.INSTANCE);
                return;
            }

            if (lower.equals(CENTER_PAGE_TOKEN))
            {
                startNewPage(PageMode.CENTERED);
                return;
            }

            if (lower.startsWith(CHAPTER_PREFIX))
            {
                handleChapterDirective(trimmed);
                return;
            }

            if (lower.equals(TOC_TOKEN))
            {
                addTocPlaceholder();
                return;
            }

            if (lower.startsWith(TITLE_PREFIX))
            {
                handleTitleLine(trimmed);
                return;
            }

            if (lower.startsWith(AUTHOR_PREFIX))
            {
                handleAuthorLine(trimmed);
                return;
            }

            if (lower.startsWith(SECTION_PREFIX))
            {
                handleSection(trimmed);
                return;
            }

            List<InlinePiece> inlinePieces = parseInlineCommandPieces(trimmed);
            if (!inlinePieces.isEmpty())
            {
                addGuideLine(GuideLine.inlineCommandText(inlinePieces));
                return;
            }

            addGuideLine(GuideLine.textLine(trimmed));
        }

        private void handleTitleLine(String line)
        {
            String title = line.substring(TITLE_PREFIX.length()).trim();
            if (title.isEmpty())
                return;
            for (String word : title.split("\\s+"))
            {
                if (word.isEmpty())
                    continue;
                String spaced = spacedWord(word);
                GuideLine titleLine = GuideLine.titleLine(spaced);
                addGuideLine(titleLine);
            }
        }

        private void handleAuthorLine(String line)
        {
            String author = line.substring(AUTHOR_PREFIX.length()).trim();
            if (author.isEmpty())
                return;
            GuideLine authorLine = GuideLine.authorLine(author);
            addGuideLine(authorLine);
        }

        private void handleSection(String line)
        {
            String title = line.substring(SECTION_PREFIX.length()).trim();
            if (title.isEmpty())
                return;
            GuideLine heading = GuideLine.sectionHeading(title);
            addGuideLine(heading);
        }

        private String spacedWord(String word)
        {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < word.length(); i++)
            {
                if (i > 0)
                    builder.append(' ');
                builder.append(Character.toUpperCase(word.charAt(i)));
            }
            return builder.toString();
        }

        private List<InlinePiece> parseInlineCommandPieces(String text)
        {
            List<InlinePiece> pieces = new ArrayList<>();
            int idx = 0;
            boolean found = false;

            while (idx < text.length())
            {
                int at = text.indexOf('@', idx);
                if (at < 0)
                {
                    String trailing = text.substring(idx);
                    if (!trailing.isEmpty())
                        pieces.add(InlinePiece.text(trailing));
                    break;
                }

                if (at > idx)
                    pieces.add(InlinePiece.text(text.substring(idx, at)));

                int open = text.indexOf('(', at + 1);
                if (open < 0)
                {
                    pieces.add(InlinePiece.text(text.substring(at)));
                    break;
                }

                String directive = text.substring(at + 1, open).trim().toLowerCase(Locale.ROOT);
                int close = text.indexOf(')', open + 1);
                if (close < 0)
                {
                    pieces.add(InlinePiece.text(text.substring(at)));
                    break;
                }

                InlineFormat format = INLINE_DIRECTIVES.get(directive);
                String payload = text.substring(open + 1, close).trim();
                if (format == null || payload.isEmpty())
                {
                    pieces.add(InlinePiece.text(text.substring(at, close + 1)));
                }
                else
                {
                    pieces.add(InlinePiece.formatted(payload, format));
                    found = true;
                }

                idx = close + 1;
            }

            return found ? pieces : List.of();
        }

        private void handleChapterDirective(String line)
        {
            String title = line.substring(CHAPTER_PREFIX.length()).trim();
            if (title.isEmpty())
                title = "Chapter";

            int order = nextChapterOrder++;

            String anchorId = "chapter-" + (++chapterAnchorCounter);
            TocChapter chapter = new TocChapter(anchorId);
            tocChapters.put(anchorId, chapter);
            chapter.order = order;
            chapter.title = title;

            startNewPage(PageMode.CENTERED);
            GuideLine banner = GuideLine.chapterBanner(anchorId, romanNumeral(order), title);
            addGuideLine(banner);
            startNewPage(PageMode.NORMAL);
        }

        private void addGuideLine(GuideLine line)
        {
            if (line == null)
                return;
            entries.add(new LineEntry(line));
        }

        private void startNewPage(PageMode mode)
        {
            if (!entries.isEmpty() && !(entries.getLast() instanceof PageBreakEntry))
                entries.add(PageBreakEntry.INSTANCE);
            entries.add(new PageModeEntry(mode));
        }

        private void addTocPlaceholder()
        {
            hasTocPlaceholder = true;
            entries.add(TocPlaceholderEntry.INSTANCE);
        }

        private void materializeTocPlaceholders()
        {
            if (!hasTocPlaceholder)
                return;

            List<GuideEntry> expanded = new ArrayList<>();
            for (GuideEntry entry : entries)
            {
                if (entry instanceof TocPlaceholderEntry)
                    expanded.addAll(buildTocBlock());
                else
                    expanded.add(entry);
            }
            entries.clear();
            entries.addAll(expanded);
            hasTocPlaceholder = false;
        }

        private List<GuideEntry> buildTocBlock()
        {
            List<TocChapter> ordered = new ArrayList<>(tocChapters.values());
            ordered.sort(Comparator.comparingInt(chapter ->
                chapter.order > 0 ? chapter.order : Integer.MAX_VALUE));

            List<GuideEntry> block = new ArrayList<>();
            if (ordered.isEmpty())
                return block;

            block.add(new LineEntry(GuideLine.tocHeading()));
            block.add(new LineEntry(GuideLine.blankLine()));

            for (TocChapter chapter : ordered)
            {
                String label = formatChapterTitle(chapter);
                GuideLine chapterLine = GuideLine.chapterLink(chapter.anchorId, label);
                navigationLines.add(chapterLine);
                block.add(new LineEntry(chapterLine));
            }
            return block;
        }

        private String formatChapterTitle(TocChapter chapter)
        {
            String title = chapter.title == null ? "" : chapter.title;
            if (chapter.order > 0)
            {
                String numeral = romanNumeral(chapter.order);
                if (!title.isEmpty())
                    return numeral + " - " + title;
                return "Chapter " + numeral;
            }
            return title.isEmpty() ? "Chapter" : title;
        }

        private List<MutableText> splitIntoLines(GuideLine line)
        {
            MinecraftClient client = MinecraftClient.getInstance();
            TextRenderer renderer = client != null ? client.textRenderer : null;
            if (renderer == null)
                return List.of(line.render().copy().append(newline()));

            List<OrderedText> wrapped = renderer.wrapLines(line.render(), PAGE_WIDTH);
            if (wrapped.isEmpty())
                wrapped = List.of(OrderedText.EMPTY);

            List<MutableText> lines = new ArrayList<>();
            for (OrderedText ordered : wrapped)
                lines.add(orderedToText(ordered).append(newline()));
            return lines;
        }

        private static MutableText orderedToText(OrderedText ordered)
        {
            MutableText text = Text.empty();
            ordered.accept((index, style, codePoint) ->
            {
                text.append(Text.literal(String.valueOf(Character.toChars(codePoint))).setStyle(style));
                return true;
            });
            return text;
        }

        private List<Text> buildPages()
        {
            resetRenderedSegments();
            materializeTocPlaceholders();
            List<PageData> pageData = paginate();
            if (pageData.isEmpty())
                return List.of();

            Map<String, Integer> anchors = resolveAnchors(pageData);
            applyNavigationTargets(anchors);

            List<Text> pages = new ArrayList<>();
            for (PageData data : pageData)
                pages.add(data.content());
            return pages;
        }

        private void resetRenderedSegments()
        {
            for (GuideEntry entry : entries)
                if (entry instanceof LineEntry(GuideLine line))
                    line.clearRenderedSegments();
        }

        private List<PageData> paginate()
        {
            List<PageData> pages = new ArrayList<>();
            PageMode currentMode = PageMode.NORMAL;
            PageBuilder builder = new PageBuilder(currentMode);

            for (GuideEntry entry : entries)
            {
                if (entry instanceof PageBreakEntry)
                {
                    if (builder.isNotEmpty())
                        pages.add(builder.finish());
                    builder = new PageBuilder(currentMode);
                    continue;
                }

                if (entry instanceof PageModeEntry(PageMode mode))
                {
                    if (builder.isNotEmpty())
                        pages.add(builder.finish());
                    currentMode = mode;
                    builder = new PageBuilder(currentMode);
                    continue;
                }

                GuideLine line = ((LineEntry) entry).line;
                builder = appendWithOverflow(builder, line, pages);
            }

            if (builder.isNotEmpty())
                pages.add(builder.finish());
            return pages;
        }

        private PageBuilder appendWithOverflow(PageBuilder builder, GuideLine line, List<PageData> pages)
        {
            List<MutableText> segments = splitIntoLines(line);
            for (MutableText segment : segments)
                builder = appendLine(builder, line, segment, pages);
            return builder;
        }

        private PageBuilder appendLine(PageBuilder builder, GuideLine line, MutableText segment, List<PageData> pages)
        {
            if (builder.tryAppend(line, segment))
                return builder;
            if (builder.isNotEmpty())
                pages.add(builder.finish());
            builder = new PageBuilder(builder.mode());
            builder.tryAppend(line, segment);
            return builder;
        }

        private Map<String, Integer> resolveAnchors(List<PageData> pages)
        {
            Map<String, Integer> map = new HashMap<>();
            for (int i = 0; i < pages.size(); i++)
            {
                for (GuideLine line : pages.get(i).lines())
                {
                    if (line.anchorId != null)
                        map.putIfAbsent(line.anchorId, i);
                }
            }
            return map;
        }

        private void applyNavigationTargets(Map<String, Integer> anchors)
        {
            for (GuideLine line : navigationLines)
            {
                if (line.targetId == null)
                    continue;

                Integer pageIndex = anchors.get(line.targetId);
                ClickEvent click = pageIndex == null
                    ? null
                    : new ClickEvent.ChangePage(pageIndex + 1);
                HoverEvent hover = pageIndex == null
                    ? null
                    : new HoverEvent.ShowText(Text.literal("Go to chapter"));

                Formatting color = pageIndex == null ? Formatting.DARK_GRAY : null;

                for (MutableText segment : line.getRenderedSegments())
                    applyInteractiveStyle(segment, click, hover, color);
            }
        }
    }

    private interface GuideEntry {}

    private record LineEntry(GuideLine line) implements GuideEntry {}

    private enum PageBreakEntry implements GuideEntry
    {
        INSTANCE
    }

    private enum TocPlaceholderEntry implements GuideEntry
    {
        INSTANCE
    }

    private record PageModeEntry(PageMode mode) implements GuideEntry {}

    private enum PageMode
    {
        NORMAL,
        CENTERED
    }

    private record PageData(MutableText content, List<GuideLine> lines, PageMode mode) {}

    private static final class TocChapter
    {
        final String anchorId;
        String title = "";
        int order = 0;

        TocChapter(String anchorId)
        {
            this.anchorId = anchorId;
        }
    }

    private static final class PageBuilder
    {
        private final List<MutableText> lineTexts = new ArrayList<>();
        private final List<GuideLine> lineRefs = new ArrayList<>();
        private PageMode mode;

        PageBuilder(PageMode mode)
        {
            this.mode = mode;
        }

        boolean isNotEmpty()
        {
            return !lineTexts.isEmpty();
        }

        boolean tryAppend(GuideLine line, MutableText segment)
        {
            if (lineTexts.size() >= MAX_LINES_PER_PAGE)
                return false;
            lineTexts.add(segment);
            lineRefs.add(line);
            line.registerRenderedSegment(segment);
            return true;
        }

        PageData finish()
        {
            MutableText text = Text.empty();
            int padding = mode == PageMode.CENTERED
                ? Math.max(0, (MAX_LINES_PER_PAGE - lineTexts.size()) / 2)
                : 0;

            for (int i = 0; i < padding; i++)
                text.append(newline());
            for (MutableText segment : lineTexts)
                text.append(segment);
            PageData result = new PageData(text, new ArrayList<>(lineRefs), mode);
            mode = PageMode.NORMAL;
            return result;
        }

        PageMode mode()
        {
            return mode;
        }
    }

    private static final class GuideLine
    {
        private final LineKind kind;
        private final String content;
        private final boolean centered;
        private final String anchorId;
        private final String targetId;
        private final List<MutableText> renderedSegments = new ArrayList<>();
        private MutableText cachedRenderable;
        private final List<InlinePiece> inlinePieces;

        private GuideLine(LineKind kind, String content, boolean centered, String anchorId, String targetId)
        {
            this(kind, content, centered, anchorId, targetId, List.of());
        }

        private GuideLine(LineKind kind, String content, boolean centered, String anchorId, String targetId, List<InlinePiece> inlinePieces)
        {
            this.kind = kind;
            this.content = content;
            this.centered = centered;
            this.anchorId = anchorId;
            this.targetId = targetId;
            this.inlinePieces = inlinePieces;
        }

        static GuideLine blankLine()
        {
            return new GuideLine(LineKind.BLANK, "", false, null, null);
        }

        static GuideLine textLine(String content)
        {
            return new GuideLine(LineKind.TEXT, content, false, null, null);
        }

        static GuideLine chapterLink(String target, String label)
        {
            return new GuideLine(LineKind.TOC_ENTRY, label, false, null, target);
        }

        static GuideLine sectionHeading(String label)
        {
            return new GuideLine(LineKind.SECTION_BODY, label, false, null, null);
        }

        static GuideLine titleLine(String text)
        {
            return new GuideLine(LineKind.TITLE_LINE, text, true, null, null);
        }

        static GuideLine authorLine(String text)
        {
            return new GuideLine(LineKind.AUTHOR_LINE, text, true, null, null);
        }

        static GuideLine tocHeading()
        {
            return new GuideLine(LineKind.TOC_TITLE, "Table of Contents", false, null, null);
        }

        static GuideLine inlineCommandText(List<InlinePiece> pieces)
        {
            return new GuideLine(LineKind.INLINE_COMMAND, "", false, null, null, pieces);
        }

        static GuideLine chapterBanner(String anchorId, String numeral, String title)
        {
            String banner = ("Chapter " + numeral).toUpperCase();
            MutableText text = applyStyle(Text.literal(banner), Formatting.DARK_PURPLE, true, false)
                .append(newline())
                .append(applyStyle(Text.literal(title), Formatting.GRAY, true, false));
            GuideLine line = new GuideLine(LineKind.TITLE_LINE, "", true, anchorId, null);
            line.cachedRenderable = text;
            return line;
        }

        void clearRenderedSegments()
        {
            renderedSegments.clear();
        }

        void registerRenderedSegment(MutableText segment)
        {
            renderedSegments.add(segment);
        }

        List<MutableText> getRenderedSegments()
        {
            return renderedSegments;
        }

        MutableText render()
        {
            if (cachedRenderable != null)
                return cachedRenderable;

            MutableText base;
            switch (kind)
            {
                case BLANK -> base = Text.literal("");
                case CHAPTER_TITLE,
                     TITLE_LINE,
                     TOC_TITLE -> base = applyStyle(parseFormattedLine(content), Formatting.DARK_PURPLE, true, false);
                case SECTION_BODY -> base = applyStyle(parseFormattedLine(content), Formatting.GOLD, true, false);
                case AUTHOR_LINE -> base = applyStyle(parseFormattedLine(content), Formatting.GRAY, false, true);
                case TOC_ENTRY -> base = Text.empty().append(parseFormattedLine(content));
                case INLINE_COMMAND -> base = buildInlineCommandText();
                default -> base = parseFormattedLine(content);
            }

            if (centered && kind != LineKind.BLANK)
                base = centerInline(base);

            cachedRenderable = base;
            return cachedRenderable;
        }

        private MutableText buildInlineCommandText()
        {
            MutableText combined = Text.empty();
            for (InlinePiece piece : inlinePieces)
            {
                MutableText segment = parseFormattedLine(piece.content());
                segment = switch (piece.format())
                {
                    case COMMAND -> applyStyle(segment, Formatting.GRAY, false, true);
                    case ITALIC -> applyStyle(segment, null, false, true);
                    case BOLD -> applyStyle(segment, null, true, false);
                    default -> segment;
                };
                combined.append(segment);
            }
            return combined;
        }
    }

    private enum InlineFormat
    {
        TEXT,
        COMMAND,
        ITALIC,
        BOLD
    }

    private record InlinePiece(String content, InlineFormat format)
    {
        static InlinePiece text(String content) { return new InlinePiece(content, InlineFormat.TEXT); }
        static InlinePiece formatted(String content, InlineFormat format) { return new InlinePiece(content, format); }
    }

    private static MutableText applyStyle(MutableText text, Formatting color, boolean bold, boolean italic)
    {
        return text.styled(style ->
        {
            Style result = style;
            if (color != null && result.getColor() == null)
                result = result.withColor(color);
            if (bold)
                result = result.withBold(true);
            if (italic)
                result = result.withItalic(true);
            return result;
        });
    }

    private enum LineKind
    {
        TEXT,
        CHAPTER_TITLE,
        SECTION_BODY,
        TITLE_LINE,
        AUTHOR_LINE,
        TOC_TITLE,
        INLINE_COMMAND,
        TOC_ENTRY,
        BLANK
    }

    private static MutableText centerInline(MutableText text)
    {
        String raw = text.getString().replace("\n", "");
        int paddingChars = Math.max(0, (APPROX_LINE_CHAR_WIDTH - raw.length()) / 2);
        if (paddingChars == 0)
            paddingChars = 1;
        return Text.literal(" ".repeat(paddingChars)).append(text);
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
                if (!buffer.isEmpty())
                {
                    result.append(Text.literal(buffer.toString()).setStyle(style));
                    buffer.setLength(0);
                }
                style = applyFormatting(style, code);
                continue;
            }
            buffer.append(c);
        }
        if (!buffer.isEmpty())
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

    private static String romanNumeral(int value)
    {
        if (value <= 0)
            return "?";
        String[] numerals =
        {
            "I","II","III","IV","V","VI","VII","VIII","IX","X",
            "XI","XII","XIII","XIV","XV","XVI","XVII","XVIII","XIX","XX"
        };

        if (value <= numerals.length)
            return numerals[value - 1];
        return String.valueOf(value);
    }

    private static void applyInteractiveStyle(MutableText text, ClickEvent click, HoverEvent hover, Formatting fallbackColor)
    {
        Style base = text.getStyle();
        if (fallbackColor != null && base.getColor() == null)
            base = base.withColor(fallbackColor);
        base = base.withClickEvent(click).withHoverEvent(hover);
        text.setStyle(base);
        for (Text sibling : text.getSiblings())
            if (sibling instanceof MutableText mutable)
                applyInteractiveStyle(mutable, click, hover, fallbackColor);
    }
}

