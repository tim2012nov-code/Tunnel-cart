package com.example.tunnelcart;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public class TunnelCartMod implements ModInitializer {
    public static final String ID = "tunnelcart";
    public static final Identifier COORD_PACKET = new Identifier(ID, "coords");

    public static final EntityType<TunnelCartEntity> TUNNEL_CART_ENTITY = Registry.register(
            Registries.ENTITY_TYPE, new Identifier(ID, "tunnel_cart"),
            EntityType.Builder.<TunnelCartEntity>create(TunnelCartEntity::new, SpawnGroup.MISC)
                    .setDimensions(0.98f, 0.7f).maxTrackingRange(8).trackingTickInterval(1).build("tunnel_cart"));

    public static final EntityType<ExtensionCartEntity> EXTENSION_CART_ENTITY = Registry.register(
            Registries.ENTITY_TYPE, new Identifier(ID, "extension_cart"),
            EntityType.Builder.<ExtensionCartEntity>create(ExtensionCartEntity::new, SpawnGroup.MISC)
                    .setDimensions(0.98f, 0.7f).maxTrackingRange(8).trackingTickInterval(1).build("extension_cart"));

    public static final Item TUNNEL_CART_ITEM = Registry.register(Registries.ITEM, new Identifier(ID, "tunnel_cart"),
            new TunnelCartItem(new Item.Settings().maxCount(1), false));
    public static final Item EXTENSION_CART_ITEM = Registry.register(Registries.ITEM, new Identifier(ID, "extension_cart"),
            new TunnelCartItem(new Item.Settings().maxCount(1), true));

    public static final ScreenHandlerType<TunnelScreenHandler> TUNNEL_HANDLER = Registry.register(
            Registries.SCREEN_HANDLER, new Identifier(ID, "tunnel"),
            new ExtendedScreenHandlerType<>(TunnelScreenHandler::new));

    @Override
    public void onInitialize() {
        TunnelCartConfig.load();
        // Speed cap for ordinary minecarts; 300 blocks/s disables this cap. It layers over High-Speed Rail.
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (!TunnelCartConfig.vanillaLimited()) return;
            double cap = TunnelCartConfig.vanillaSpeed() / 20.0;
            for (Entity e : world.iterateEntities()) {
                if (!(e instanceof AbstractMinecartEntity m) || e instanceof TunnelCartEntity || e instanceof ExtensionCartEntity) continue;
                Vec3d v = m.getVelocity();
                double h = Math.hypot(v.x, v.z);
                if (h > cap) m.setVelocity(v.x * cap / h, v.y, v.z * cap / h);
            }
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e -> { e.add(TUNNEL_CART_ITEM); e.add(EXTENSION_CART_ITEM); });
        ServerPlayNetworking.registerGlobalReceiver(COORD_PACKET, (server, player, handler, buf, sender) -> {
            int id = buf.readInt(), x = buf.readInt(), y = buf.readInt(), z = buf.readInt();
            boolean hasY = buf.readBoolean();
            server.execute(() -> {
                Entity e = player.getServerWorld().getEntityById(id);
                if (e instanceof TunnelCartEntity cart && cart.squaredDistanceTo(player) < 1024) {
                    cart.startCoordinates(player, x, hasY ? Integer.valueOf(y) : null, z);
                }
            });
        });
    }
}