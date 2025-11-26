package cc.hachem.hud;

import cc.hachem.RadarClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class DualPageBookScreen extends Screen
{
    private static final Identifier RIGHT_PAGE_TEXTURE = Identifier.of(RadarClient.MOD_ID, "textures/gui/book_page.png");
    private static final Identifier LEFT_PAGE_TEXTURE = Identifier.of(RadarClient.MOD_ID, "textures/gui/book_page_rotated.png");
    private static final int PAGE_WIDTH = 146;
    private static final int PAGE_HEIGHT = 180;
    private static final int PAGE_CROP = 6;
    private static final int BOOK_WIDTH = PAGE_WIDTH * 2 - PAGE_CROP * 2;
    private static final int BOOK_HEIGHT = PAGE_HEIGHT;
    private static final int PAGE_SIDE_MARGIN = 14;
    private static final int PAGE_TOP_MARGIN = 20;
    private static final int PAGE_TEXT_WIDTH = PAGE_WIDTH - PAGE_CROP - PAGE_SIDE_MARGIN * 2;
    private static final int PAGE_TEXT_HEIGHT = 132;

    private final List<Text> pages;
    private final int totalSpreads;
    private int currentPage;

    private ButtonWidget nextButton;
    private ButtonWidget prevButton;

    public DualPageBookScreen(Text title, List<Text> content)
    {
        super(title == null ? Text.empty() : title);
        List<Text> sanitized = new ArrayList<>();
        if (content != null)
            sanitized.addAll(content);
        if (sanitized.isEmpty())
            sanitized.add(Text.empty());
        sanitized.replaceAll(text -> text == null ? Text.empty() : text);
        if ((sanitized.size() & 1) == 1)
            sanitized.add(Text.empty());
        this.pages = List.copyOf(sanitized);
        this.totalSpreads = Math.max(1, this.pages.size() / 2);
    }

    @Override
    protected void init()
    {
        int bookX = (width - BOOK_WIDTH) / 2;
        int bookY = (height - BOOK_HEIGHT) / 2;
        int leftButtonX = bookX+25;
        int rightButtonX = bookX + BOOK_WIDTH - PageArrowButton.WIDTH-25;
        int buttonY = bookY + BOOK_HEIGHT - PageArrowButton.HEIGHT-10;

        prevButton = addDrawableChild(new PageArrowButton(leftButtonX, buttonY, false, this::goPrevious));
        nextButton = addDrawableChild(new PageArrowButton(rightButtonX, buttonY, true, this::goNext));

        ButtonWidget doneButton = ButtonWidget.builder(Text.translatable("gui.done"), button ->
            {
                close();
            })
            .dimensions(bookX + BOOK_WIDTH / 2 - 40, bookY + BOOK_HEIGHT + 8, 80, 20)
            .build();
        addDrawableChild(doneButton);
        updateButtonState();
    }

    private void goNext()
    {
        if (currentPage + 2 < pages.size())
        {
            currentPage += 2;
            updateButtonState();
        }
    }

    private void goPrevious()
    {
        if (currentPage >= 2)
        {
            currentPage -= 2;
            updateButtonState();
        }
    }

    private void updateButtonState()
    {
        if (prevButton != null)
        {
            prevButton.active = currentPage > 0;
            prevButton.visible = prevButton.active;
        }
        if (nextButton != null)
        {
            nextButton.active = currentPage + 2 < pages.size();
            nextButton.visible = nextButton.active;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        context.fill(0, 0, width, height, 0xC0101010);
        int bookX = (width - BOOK_WIDTH) / 2;
        int bookY = (height - BOOK_HEIGHT) / 2;

        drawLeftPage(context, bookX, bookY);
        drawRightPage(context, bookX + PAGE_WIDTH - PAGE_CROP, bookY);

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int leftPageX = bookX + PAGE_SIDE_MARGIN + 5;
        int rightPageX = bookX + PAGE_WIDTH - PAGE_CROP + PAGE_SIDE_MARGIN;
        int textY = bookY + PAGE_TOP_MARGIN;

        renderPage(context, textRenderer, getPage(currentPage), leftPageX, textY);
        renderPage(context, textRenderer, getPage(currentPage + 1), rightPageX, textY);

        String indicator = (currentPage / 2 + 1) + " / " + totalSpreads;
        context.drawText(textRenderer, indicator,
            bookX + BOOK_WIDTH / 2 - textRenderer.getWidth(indicator) / 2,
            bookY + BOOK_HEIGHT - 12,
            0x3F2F1F,
            false);

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, bookY - 12, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawLeftPage(DrawContext context, int x, int y)
    {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, LEFT_PAGE_TEXTURE,
            x, y,
            0, 0,
            PAGE_WIDTH - PAGE_CROP, PAGE_HEIGHT,
            PAGE_WIDTH, PAGE_HEIGHT);
    }

    private void drawRightPage(DrawContext context, int x, int y)
    {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, RIGHT_PAGE_TEXTURE,
            x, y,
            PAGE_CROP, 0,
            PAGE_WIDTH - PAGE_CROP, PAGE_HEIGHT,
            PAGE_WIDTH, PAGE_HEIGHT);
    }

    private void renderPage(DrawContext context, TextRenderer renderer, Text page, int x, int startY)
    {
        List<OrderedText> lines = renderer.wrapLines(page, PAGE_TEXT_WIDTH);
        int y = startY;
        for (OrderedText line : lines)
        {
            context.drawText(renderer, line, x, y, 0xFF2F1B0C, false);
            y += renderer.fontHeight;
            if (y >= startY + PAGE_TEXT_HEIGHT)
                break;
        }
    }

    private Text getPage(int index)
    {
        if (index < 0 || index >= pages.size())
            return Text.empty();
        return pages.get(index);
    }

    private static class SoundlessButton extends ButtonWidget
    {
        SoundlessButton(int x, int y, int width, int height, Text label, Runnable action)
        {
            super(x, y, width, height, label, button -> action.run(), DEFAULT_NARRATION_SUPPLIER);
        }

        @Override
        public void playDownSound(SoundManager soundManager)
        {
        }
    }

    private static class PageArrowButton extends SoundlessButton
    {
        private static final int WIDTH = 23;
        private static final int HEIGHT = 13;
        private static final Identifier FORWARD = Identifier.ofVanilla("textures/gui/sprites/widget/page_forward.png");
        private static final Identifier FORWARD_HIGHLIGHTED = Identifier.ofVanilla("textures/gui/sprites/widget/page_forward_highlighted.png");
        private static final Identifier BACK = Identifier.ofVanilla("textures/gui/sprites/widget/page_backward.png");
        private static final Identifier BACK_HIGHLIGHTED = Identifier.ofVanilla("textures/gui/sprites/widget/page_backward_highlighted.png");

        private final boolean forward;
        PageArrowButton(int x, int y, boolean forward, Runnable action)
        {
            super(x, y, WIDTH, HEIGHT, Text.empty(), () ->
            {
                action.run();
                playPageSound();
            });
            this.forward = forward;
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta)
        {
            Identifier texture;
            if (!active)
                texture = forward ? FORWARD : BACK;
            else if (isHovered())
                texture = forward ? FORWARD_HIGHLIGHTED : BACK_HIGHLIGHTED;
            else
                texture = forward ? FORWARD : BACK;

            context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, getX(), getY(),
                0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT);
        }
    }

    private static void playPageSound()
    {
        var client = MinecraftClient.getInstance();
        if (client != null)
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0f));
    }
}

