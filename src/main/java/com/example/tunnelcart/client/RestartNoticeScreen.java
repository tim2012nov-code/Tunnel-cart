package com.example.tunnelcart.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Shown when the new High-Speed Rail speed could not be applied to the running game. */
public class RestartNoticeScreen extends Screen {
    private final Screen parent;

    public RestartNoticeScreen(Screen parent) {
        super(Text.translatable("tunnelcart.restart.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(ScreenTexts.OK, b -> close()).dimensions(width / 2 - 100, height / 2 + 30, 200, 20).build());
    }

    @Override
    public void close() { client.setScreen(parent); }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        ctx.drawCenteredTextWithShadow(textRenderer, title.copy().formatted(Formatting.RED, Formatting.BOLD), width / 2, height / 2 - 40, 0xFF5555);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("tunnelcart.restart.line1"), width / 2, height / 2 - 18, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("tunnelcart.restart.line2"), width / 2, height / 2 - 6, 0xAAAAAA);
        super.render(ctx, mouseX, mouseY, delta);
    }
}
