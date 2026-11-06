package com.example.tunnelcart;

import net.minecraft.block.*;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/**
 * Self-driving tunnel boring cart. Inventory (27 slots, like a chest minecart) holds
 * powered rails + cobbled deepslate (+ optional redstone blocks) and receives all mined loot.
 */
public class TunnelCartEntity extends ChestMinecartEntity {
    private static final TagKey<Block> C_ORES = TagKey.of(RegistryKeys.BLOCK, new Identifier("c", "ores"));
    private static final int FLAGS = Block.NOTIFY_LISTENERS; // no neighbour updates -> no fluid/gravity chaos

    private BlockPos origin = null;     // rail the cart was placed on (slice 0)
    private Direction heading = Direction.NORTH;
    private int built = 0;              // last fully finished slice
    private String issue = null;

    public TunnelCartEntity(EntityType<? extends TunnelCartEntity> type, World world) {
        super(type, world);
    }

    public TunnelCartEntity(World world, double x, double y, double z) {
        this(TunnelCartMod.TUNNEL_CART_ENTITY, world);
        setPosition(x, y, z);
    }

    public void setup(BlockPos origin, Direction heading) {
        this.origin = origin.toImmutable();
        this.heading = heading;
        this.built = 0;
    }

    // ---------------------------------------------------------------- main loop
    @Override
    public void tick() {
        if (!getWorld().isClient && origin != null && getWorld() instanceof ServerWorld sw) {
            issue = null;
            double max = getMaxSpeed(); // includes High-Speed Rail's override
            double along = (getX() - origin.getX() - 0.5) * heading.getOffsetX()
                         + (getZ() - origin.getZ() - 0.5) * heading.getOffsetZ();

            int perTick = (int) Math.ceil(max);
            int target = (int) Math.floor(along) + Math.max(6, perTick * 3 + 3);
            int budget = perTick + 3;
            while (built < target && budget-- > 0) {
                if (!buildSlice(sw, built + 1)) break;
                built++;
            }

            // never outrun the finished tunnel
            double allowed = built - along - 1.0;
            double speed = Math.max(0.0, Math.min(max, allowed));
            setVelocity(heading.getOffsetX() * speed, getVelocity().y, heading.getOffsetZ() * speed);

            if (issue != null && speed < 0.05 && age % 40 == 0) {
                Text msg = Text.translatable("tunnelcart." + issue);
                for (var p : sw.getPlayers(pl -> pl.squaredDistanceTo(this) < 2500)) p.sendMessage(msg, true);
            }
        }
        super.tick();
    }

    // ---------------------------------------------------------------- one cross-section
    private boolean buildSlice(ServerWorld w, int k) {
        BlockPos c = origin.offset(heading, k);
        Direction side = heading.rotateYClockwise();
        if (!w.isChunkLoaded(c) || !w.isChunkLoaded(c.offset(side, 3)) || !w.isChunkLoaded(c.offset(side, -3))) {
            issue = "chunk"; return false;
        }

        // 0) cap fluid in the next slice's bore so it cannot flood this one
        BlockPos n = c.offset(heading);
        if (w.isChunkLoaded(n)) {
            for (int a = -1; a <= 1; a++) for (int h = 0; h <= 2; h++) {
                BlockPos p = n.offset(side, a).up(h);
                if (w.isOutOfHeightLimit(p)) continue;
                BlockState s = w.getBlockState(p);
                if (!s.getFluidState().isEmpty()) {
                    if (!take(Items.COBBLED_DEEPSLATE)) { issue = "need_cobbled"; return false; }
                    w.setBlockState(p, Blocks.COBBLED_DEEPSLATE.getDefaultState(), FLAGS);
                }
            }
        }

        // 1) shell (floor, ceiling, walls): seal caves / fluids / loose blocks / ores
        for (int a = -2; a <= 2; a++) for (int h = -1; h <= 3; h++) {
            if (Math.abs(a) <= 1 && h >= 0 && h <= 2) continue;
            BlockPos p = c.offset(side, a).up(h);
            if (w.isOutOfHeightLimit(p)) continue;
            BlockState s = w.getBlockState(p);
            if (s.isOf(Blocks.COBBLED_DEEPSLATE)) continue;
            boolean wanted = isWanted(s) || s.getBlock() instanceof FallingBlock;
            boolean weak = s.isAir() || !s.getFluidState().isEmpty() || !s.isFullCube(w, p);
            if (!(wanted || weak) || s.hasBlockEntity() || s.getHardness(w, p) < 0) continue;
            if (!take(Items.COBBLED_DEEPSLATE)) { issue = "need_cobbled"; return false; }
            if (wanted) collect(w, p, s);
            w.setBlockState(p, Blocks.COBBLED_DEEPSLATE.getDefaultState(), FLAGS);
        }

        // 2) bore 3x3 (rail cell handled below)
        for (int a = -1; a <= 1; a++) for (int h = 0; h <= 2; h++) {
            if (a == 0 && h == 0) continue;
            if (!clear(w, c.offset(side, a).up(h))) return false;
        }
