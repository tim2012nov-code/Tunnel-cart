package com.example.tunnelcart;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RailBlock;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
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

    /** 8 blocks/s: what a curved rail can take. */
    private static final double CURVE_SPEED = 0.4;

    /** A plain rail in a curved shape on the cart's block or in any block it will pass this tick (rails of slopes are checked one level up / down too). */
    private static boolean curveAhead(ServerWorld w, AbstractMinecartEntity m, Vec3d v, double h) {
        int steps = (int) Math.ceil(h) + 1;
        double dx = v.x / h, dz = v.z / h;
        BlockPos.Mutable p = new BlockPos.Mutable();
        int by = m.getBlockY();
        for (int i = 0; i <= steps; i++) {
            int bx = MathHelper.floor(m.getX() + dx * i), bz = MathHelper.floor(m.getZ() + dz * i);
            if (!w.isChunkLoaded(p.set(bx, by, bz))) return false;
            for (int dy = -1; dy <= 1; dy++) {
                BlockState s = w.getBlockState(p.set(bx, by + dy, bz));
                if (!s.isOf(Blocks.RAIL)) continue;
                RailShape sh = s.get(RailBlock.SHAPE);
                if (sh == RailShape.NORTH_EAST || sh == RailShape.NORTH_WEST || sh == RailShape.SOUTH_EAST || sh == RailShape.SOUTH_WEST) return true;
            }
        }
        return false;
    }

    @Override
    public void onInitialize() {
        TunnelCartConfig.load();
        // High-Speed Rail file: make sure every fast block of the list is there with the configured speed
        ServerLifecycleEvents.SERVER_STARTED.register(server -> HsrConfig.syncAtStart());
        // ordinary minecarts: the slider is their real speed on High-Speed Rail blocks (300 = no limit),
        // and curves stay at the vanilla 8 b/s
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            double cap = TunnelCartConfig.vanillaLimited() ? TunnelCartConfig.vanillaSpeed() / 20.0 : Double.MAX_VALUE;
            for (Entity e : world.iterateEntities()) {
                if (!(e instanceof AbstractMinecartEntity m) || e instanceof TunnelCartEntity || e instanceof ExtensionCartEntity) continue;
                Vec3d v = m.getVelocity();
                double h = Math.hypot(v.x, v.z);
                if (h <= CURVE_SPEED) continue;
                double c = cap;
                if (curveAhead(world, m, v, h)) c = Math.min(c, CURVE_SPEED);
                if (h > c) m.setVelocity(v.x * c / h, v.y, v.z * c / h);
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
