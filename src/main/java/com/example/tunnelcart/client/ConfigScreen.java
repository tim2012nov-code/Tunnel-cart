package com.example.tunnelcart.client;

import com.example.tunnelcart.TunnelCartConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.DoubleConsumer;

/** Settings: speed of the tunnel cart on straight stretches and a speed limit for ordinary minecarts. */
public class ConfigScreen extends Screen {
    private final Screen parent;
    private double tunnel = TunnelCartConfig.buildSpeed();
    private double vanilla = TunnelCartConfig.vanillaSpeed();

    public ConfigScreen(Screen parent) {
        super(Text.translatable("tunnelcart.cfg.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 260, x = width / 2 - w / 2;
        addDrawableChild(new SpeedSlider(x, height / 2 - 52, w, 20, Text.translatable("tunnelcart.cfg.tunnel"), tunnel, v -> tunnel = v));
        addDrawableChild(new SpeedSlider(x, height / 2 + 14, w, 20, Text.translatable("tunnelcart.cfg.vanilla"), vanilla, v -> vanilla = v));
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close()).dimensions(width / 2 - 100, height / 2 + 70, 200, 20).build());
    }

    @Override
    public void close() {
        TunnelCartConfig.set(tunnel, vanilla);
        TunnelCartConfig.save();
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 90, 0xFFFFFF);
        super.render(ctx, mouseX, mouseY, delta);
        int cy = height / 2 - 52 + 24;
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("tunnelcart.cfg.brake", TunnelCartConfig.brakeFor(tunnel)).formatted(Formatting.GRAY), width / 2, cy, 0xAAAAAA);
        if (tunnel > TunnelCartConfig.RECOMMENDED_MAX + 1e-9) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("tunnelcart.cfg.warn", (int) TunnelCartConfig.RECOMMENDED_MAX).formatted(Formatting.RED, Formatting.BOLD),
                    width / 2, cy + 12, 0xFF5555);
        }
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("tunnelcart.cfg.vanilla_hint").formatted(Formatting.GRAY), width / 2, height / 2 + 14 + 24, 0xAAAAAA);
    }

    /** Slider 0.1 .. 300 b/s: the left half is 0.1 .. 75, the right half 75 .. 300. */
    private static final class SpeedSlider extends SliderWidget {
        private final Text label;
        private final DoubleConsumer onChange;

        SpeedSlider(int x, int y, int w, int h, Text label, double bps, DoubleConsumer onChange) {
            super(x, y, w, h, Text.empty(), toPos(bps));
            this.label = label;
            this.onChange = onChange;
            updateMessage();
        }

        static double toBps(double t) {
            double lo = TunnelCartConfig.MIN_SPEED, mid = TunnelCartConfig.RECOMMENDED_MAX, hi = TunnelCartConfig.MAX_SPEED;
            double v = t <= 0.5 ? lo + (mid - lo) * (t / 0.5) : mid + (hi - mid) * ((t - 0.5) / 0.5);
            return v < mid ? Math.round(v * 10.0) / 10.0 : Math.round(v);
        }

        static double toPos(double bps) {
            double lo = TunnelCartConfig.MIN_SPEED, mid = TunnelCartConfig.RECOMMENDED_MAX, hi = TunnelCartConfig.MAX_SPEED;
            double b = Math.max(lo, Math.min(hi, bps));
            return b <= mid ? 0.5 * (b - lo) / (mid - lo) : 0.5 + 0.5 * (b - mid) / (hi - mid);
        }

        private double bps() { return toBps(value); }

        @Override
        protected void updateMessage() {
            double b = bps();
            String num = b == Math.rint(b) ? String.valueOf((long) b) : String.valueOf(b);
            setMessage(Text.empty().append(label).append(": " + num + " b/s"));
        }

        @Override
        protected void applyValue() { onChange.accept(bps()); }
    }
}