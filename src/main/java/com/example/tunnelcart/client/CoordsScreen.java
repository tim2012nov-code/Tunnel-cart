package com.example.tunnelcart.client;

import com.example.tunnelcart.TunnelCartMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

/** "By coordinates" menu: target X / Y / Z (Y may stay empty = current level). */
public class CoordsScreen extends Screen {
    private final int cartId;
    private TextFieldWidget fx, fy, fz;

    public CoordsScreen(int cartId) {
        super(Text.translatable("tunnelcart.coords"));
        this.cartId = cartId;
    }

    private TextFieldWidget field(int x, int y, String name) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, 120, 18, Text.literal(name));
        f.setMaxLength(9);
        f.setTextPredicate(s -> s.matches("-?\\d{0,8}"));
        f.setSuggestion(name);
        f.setChangedListener(t -> f.setSuggestion(t.isEmpty() ? name : ""));
        return addDrawableChild(f);
    }

    @Override
    protected void init() {
        int cx = width / 2, top = height / 2 - 52;
        fx = field(cx - 60, top + 22, "X");
        fy = field(cx - 60, top + 46, "Y");
        fz = field(cx - 60, top + 70, "Z");
        setInitialFocus(fx);
        addDrawableChild(ButtonWidget.builder(Text.translatable("tunnelcart.coords_go"), b -> send())
                .dimensions(cx - 60, top + 98, 58, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("tunnelcart.coords_cancel"), b -> close())
                .dimensions(cx + 2, top + 98, 58, 20).build());
    }

    private void send() {
        try {
            int x = Integer.parseInt(fx.getText()), z = Integer.parseInt(fz.getText());
            boolean hasY = !fy.getText().isEmpty() && !fy.getText().equals("-");
            int y = hasY ? Integer.parseInt(fy.getText()) : 0;
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeInt(cartId); buf.writeInt(x); buf.writeInt(y); buf.writeInt(z); buf.writeBoolean(hasY);
            ClientPlayNetworking.send(TunnelCartMod.COORD_PACKET, buf);
            close();
        } catch (NumberFormatException ignored) {
            // X and Z are required
        }
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 52, 0xFFFFFF);
        super.render(ctx, mx, my, delta);
    }
}
