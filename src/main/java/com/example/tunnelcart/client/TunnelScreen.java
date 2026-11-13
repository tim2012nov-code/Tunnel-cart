package com.example.tunnelcart.client;

import com.example.tunnelcart.TunnelScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class TunnelScreen extends HandledScreen<TunnelScreenHandler> {
    private static final Identifier TEX = new Identifier("textures/gui/container/generic_54.png");
    private ButtonWidget button;
    private ButtonWidget torchButton;

    public TunnelScreen(TunnelScreenHandler h, PlayerInventory inv, Text title) {
        super(h, inv, title);
        backgroundHeight = 114 + h.getRows() * 18;
        playerInventoryTitleY = backgroundHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        button = addDrawableChild(ButtonWidget.builder(Text.translatable("tunnelcart.start"),
                b -> client.interactionManager.clickButton(handler.syncId, 0))
                .dimensions(x + backgroundWidth - 84, y + 3, 78, 14).build());
        torchButton = addDrawableChild(ButtonWidget.builder(Text.translatable("tunnelcart.torch_off"),
                b -> client.interactionManager.clickButton(handler.syncId, 1))
                .dimensions(x, y + backgroundHeight + 2, 86, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("tunnelcart.coords"), b -> {
            int cartId = handler.getCartId();
            client.player.closeHandledScreen();
            client.setScreen(new CoordsScreen(cartId));
        }).dimensions(x + 90, y + backgroundHeight + 2, 86, 18).build());
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        if (button != null) button.setMessage(Text.translatable(handler.isRunning() ? "tunnelcart.stop" : "tunnelcart.start"));
        if (torchButton != null) torchButton.setMessage(Text.translatable(handler.isTorches() ? "tunnelcart.torch_on" : "tunnelcart.torch_off"));
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mx, int my) {
        int rows = handler.getRows();
        ctx.drawTexture(TEX, x, y, 0, 0, backgroundWidth, rows * 18 + 17);
        ctx.drawTexture(TEX, x, y + rows * 18 + 17, 0, 126, backgroundWidth, 96);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        super.render(ctx, mx, my, delta);
        drawMouseoverTooltip(ctx, mx, my);
    }
}
