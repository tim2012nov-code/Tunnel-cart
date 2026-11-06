package com.example.tunnelcart.client;

import com.example.tunnelcart.TunnelCartMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.entity.MinecartEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;

public class TunnelCartClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(TunnelCartMod.TUNNEL_CART_ENTITY,
                ctx -> new MinecartEntityRenderer<>(ctx, EntityModelLayers.CHEST_MINECART));
        EntityRendererRegistry.register(TunnelCartMod.EXTENSION_CART_ENTITY,
                ctx -> new MinecartEntityRenderer<>(ctx, EntityModelLayers.CHEST_MINECART));
        HandledScreens.register(TunnelCartMod.TUNNEL_HANDLER, TunnelScreen::new);
    }
}
