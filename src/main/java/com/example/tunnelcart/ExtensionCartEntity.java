package com.example.tunnelcart;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

/** Trailing cart: click = sit, sneak+click = storage (27 extra slots for the Tunnel Cart). */
public class ExtensionCartEntity extends ChestMinecartEntity {
    private UUID leader;

    public ExtensionCartEntity(EntityType<? extends ExtensionCartEntity> type, World world) { super(type, world); }
    public ExtensionCartEntity(World world, double x, double y, double z) {
        super(TunnelCartMod.EXTENSION_CART_ENTITY, world);
        setPosition(x, y, z);
    }

    public void setLeader(UUID id) { leader = id; }

    @Override
    public void tick() {
        if (!getWorld().isClient && getWorld() instanceof ServerWorld sw) {
            if (leader != null && sw.getEntity(leader) instanceof TunnelCartEntity t && t.isAlive()) {
                setVelocity(t.getVelocity());
                super.tick();
                Vec3d p = t.pointBehind(1.5);   // along the built path, so it also follows a turn
                setPosition(p.x, p.y, p.z);
                setYaw(t.getYaw());
                return;
            }
            setVelocity(0, getVelocity().y, 0);
        }
        super.tick();
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        World w = getWorld();
        if (player.isSneaking()) {
            if (w instanceof ServerWorld sw && leader != null && sw.getEntity(leader) instanceof TunnelCartEntity t) return t.openGui(player);
            return super.interact(player, hand);
        }
        if (!w.isClient) player.startRiding(this);
        return ActionResult.success(w.isClient);
    }

    public Item getItem() { return TunnelCartMod.EXTENSION_CART_ITEM; }
    @Override public int size() { return 27; }
    @Override public Type getMinecartType() { return Type.CHEST; }
    @Override public BlockState getDefaultContainedBlock() { return Blocks.BARREL.getDefaultState(); }
    @Override public int getDefaultBlockOffset() { return 8; }
    @Override public ScreenHandler getScreenHandler(int syncId, PlayerInventory inv) {
        return GenericContainerScreenHandler.createGeneric9x3(syncId, inv, this);
    }

    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (leader != null) nbt.putUuid("Leader", leader);
    }
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("Leader")) leader = nbt.getUuid("Leader");
    }
}
