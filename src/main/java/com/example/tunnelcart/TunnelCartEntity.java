package com.example.tunnelcart;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.*;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TunnelCartEntity extends StorageMinecartEntity {
    private static final TagKey<Block> C_ORES = TagKey.of(RegistryKeys.BLOCK, new Identifier("c", "ores"));
    private static final int FLAGS = Block.NOTIFY_LISTENERS;

    private BlockPos origin = null;
    private Direction heading = Direction.NORTH;
    private int built = 0;
    private boolean running = false;
    private UUID extUuid = null;
    private String issue = null;
    private Inventory pool = this;
    private final List<ItemStack> junk = new ArrayList<>();

    public TunnelCartEntity(EntityType<? extends TunnelCartEntity> type, World world) { super(type, world); }
    public TunnelCartEntity(World world, double x, double y, double z) {
        super(TunnelCartMod.TUNNEL_CART_ENTITY, x, y, z, world);
    }

    public void setup(BlockPos origin, Direction heading) { this.origin = origin.toImmutable(); this.heading = heading; this.built = 0; }
    public Direction getHeading() { return heading; }
    public boolean isRunning() { return running; }
    public void toggleRunning() { running = !running; }
    public UUID getExtUuid() { return extUuid; }
    public void setExtUuid(UUID id) { extUuid = id; }

    private ExtensionCartEntity getExt() {
        if (extUuid != null && getWorld() instanceof ServerWorld sw && sw.getEntity(extUuid) instanceof ExtensionCartEntity e && e.isAlive()) return e;
        return null;
    }

    private Inventory poolNow() {
        ExtensionCartEntity e = getExt();
        return e != null ? new PairInventory(this, e) : this;
    }

    // ------------------------------------------------------------ GUI
    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) { return openGui(player); }

    public ActionResult openGui(PlayerEntity player) {
        if (!getWorld().isClient) {
            TunnelCartEntity self = this;
            player.openHandledScreen(new ExtendedScreenHandlerFactory() {
                @Override public Text getDisplayName() { return Text.translatable("entity.tunnelcart.tunnel_cart"); }
                @Override public ScreenHandler createMenu(int id, PlayerInventory pi, PlayerEntity p) {
                    return new TunnelScreenHandler(id, pi, self.poolNow(), self);
                }
                @Override public void writeScreenOpeningData(ServerPlayerEntity p, PacketByteBuf buf) {
                    buf.writeInt(self.poolNow().size() / 9);
                }
            });
        }
        return ActionResult.success(getWorld().isClient);
    }

    // ------------------------------------------------------------ main loop
    @Override
    public void tick() {
        if (!getWorld().isClient && getWorld() instanceof ServerWorld sw) {
            if (origin == null || !running) {
                setVelocity(0, getVelocity().y, 0);
            } else {
                issue = null;
                pool = poolNow();
                double max = getMaxSpeed(); // includes High-Speed Rail override
                double along = (getX() - origin.getX() - 0.5) * heading.getOffsetX()
                             + (getZ() - origin.getZ() - 0.5) * heading.getOffsetZ();
                int perTick = (int) Math.ceil(max);
                int target = (int) Math.floor(along) + Math.max(6, perTick * 3 + 3);
                int budget = perTick + 3;
                while (built < target && budget-- > 0) {
                    if (!buildSlice(sw, built + 1)) break;
                    built++;
                }
                double speed = Math.max(0.0, Math.min(max, built - along - 1.0));
                setVelocity(heading.getOffsetX() * speed, getVelocity().y, heading.getOffsetZ() * speed);

                if (age % 10 == 0) flushJunk(sw);
                if (issue != null && speed < 0.05 && age % 40 == 0) {
                    Text msg = Text.translatable("tunnelcart." + issue);
                    for (var p : sw.getPlayers(pl -> pl.squaredDistanceTo(this) < 2500)) p.sendMessage(msg, true);
                }
            }
        }
        super.tick();
    }

    // ------------------------------------------------------------ one cross-section
    private boolean buildSlice(ServerWorld w, int k) {
        BlockPos c = origin.offset(heading, k);
        Direction side = heading.rotateYClockwise();
        if (!w.isChunkLoaded(c) || !w.isChunkLoaded(c.offset(side, 3)) || !w.isChunkLoaded(c.offset(side, -3))) { issue = "chunk"; return false; }

        // 0) cap fluids in the next bore
        BlockPos n = c.offset(heading);
        if (w.isChunkLoaded(n)) {
            for (int a = -1; a <= 1; a++) for (int h = 0; h <= 2; h++) {
                BlockPos p = n.offset(side, a).up(h);
                if (w.isOutOfHeightLimit(p) || w.getBlockState(p).getFluidState().isEmpty()) continue;
                if (!take(Items.COBBLED_DEEPSLATE)) { issue = "need_cobbled"; return false; }
                w.setBlockState(p, Blocks.COBBLED_DEEPSLATE.getDefaultState(), FLAGS);
            }
        }

        // 1) 5x5 shell: EVERYTHING except deepslate becomes cobbled deepslate; ores go to the cart
        for (int a = -2; a <= 2; a++) for (int h = -1; h <= 3; h++) {
            if (Math.abs(a) <= 1 && h >= 0 && h <= 2) continue;
            BlockPos p = c.offset(side, a).up(h);
            if (w.isOutOfHeightLimit(p)) continue;
            BlockState s = w.getBlockState(p);
            if (s.isOf(Blocks.DEEPSLATE) || s.isOf(Blocks.COBBLED_DEEPSLATE)) continue;
            if (s.hasBlockEntity() || s.getHardness(w, p) < 0) continue;
            if (!take(Items.COBBLED_DEEPSLATE)) { issue = "need_cobbled"; return false; }
            if (isWanted(s)) mine(w, p, s);
            w.setBlockState(p, Blocks.COBBLED_DEEPSLATE.getDefaultState(), FLAGS);
        }

        // 2) bore 3x3
        for (int a = -1; a <= 1; a++) for (int h = 0; h <= 2; h++) {
            if (a == 0 && h == 0) continue;
            if (!clear(w, c.offset(side, a).up(h))) return false;
        }

        // 3) rail (+ redstone block under every 8th one for trailing carts)
        if (!clear(w, c)) return false;
        if (!take(Items.POWERED_RAIL)) { issue = "need_rail"; return false; }
        if (k % 8 == 0 && take(Items.REDSTONE_BLOCK)) w.setBlockState(c.down(), Blocks.REDSTONE_BLOCK.getDefaultState(), FLAGS);
        RailShape shape = heading.getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
        w.setBlockState(c, Blocks.POWERED_RAIL.getDefaultState().with(PoweredRailBlock.SHAPE, shape), Block.NOTIFY_ALL);
        return true;
    }

    private boolean clear(ServerWorld w, BlockPos p) {
        if (w.isOutOfHeightLimit(p)) return true;
        BlockState s = w.getBlockState(p);
        if (s.isAir()) return true;
        if (s.getBlock() instanceof FluidBlock) { w.setBlockState(p, Blocks.AIR.getDefaultState(), FLAGS); return true; }
        if (s.hasBlockEntity() || s.getHardness(w, p) < 0) { issue = "blocked"; return false; }
        mine(w, p, s);
        w.setBlockState(p, Blocks.AIR.getDefaultState(), FLAGS);
        return true;
    }

    private static boolean isWanted(BlockState s) {
        return s.isOf(Blocks.GRAVEL) || s.isOf(Blocks.CLAY) || s.isIn(C_ORES)
                || s.isIn(BlockTags.COAL_ORES) || s.isIn(BlockTags.IRON_ORES) || s.isIn(BlockTags.COPPER_ORES)
                || s.isIn(BlockTags.GOLD_ORES) || s.isIn(BlockTags.DIAMOND_ORES) || s.isIn(BlockTags.EMERALD_ORES)
                || s.isIn(BlockTags.LAPIS_ORES) || s.isIn(BlockTags.REDSTONE_ORES);
    }

    // ------------------------------------------------------------ loot routing
    /** Ore drops + cobbled deepslate -> cart (and shulkers); everything else -> floor. */
    private void mine(ServerWorld w, BlockPos p, BlockState s) {
        boolean resource = isWanted(s);
        for (ItemStack d : Block.getDroppedStacks(s, w, p, null, this, new ItemStack(Items.NETHERITE_PICKAXE))) {
            if (resource || d.isOf(Items.COBBLED_DEEPSLATE)) {
                ItemStack left = CartInv.insert(pool, d);
                if (!left.isEmpty()) addJunk(left);
            } else addJunk(d);
        }
    }

    private void addJunk(ItemStack s) {
        for (ItemStack e : junk) {
            if (ItemStack.canCombine(e, s)) {
                int m = Math.min(s.getCount(), e.getMaxCount() - e.getCount());
                if (m > 0) { e.increment(m); s.decrement(m); }
            }
        }
        if (!s.isEmpty()) junk.add(s.copy());
    }

    private void flushJunk(ServerWorld w) {
        for (ItemStack s : junk) {
            ItemEntity ie = new ItemEntity(w, getX(), getY() + 0.3, getZ(), s);
            ie.setVelocity(Vec3d.ZERO);
            ie.setItemAge(5900); // despawns after ~10 s so the tunnel does not drown in junk
            w.spawnEntity(ie);
        }
        junk.clear();
    }

    private boolean take(Item item) { return CartInv.take(pool, item); }

    // ------------------------------------------------------------ minecart boilerplate
    @Override protected Item getItem() { return TunnelCartMod.TUNNEL_CART_ITEM; }
    @Override public int size() { return 27; }
    @Override public Type getMinecartType() { return Type.CHEST; }
    @Override public BlockState getDefaultContainedBlock() { return Blocks.BLAST_FURNACE.getDefaultState(); }
    @Override public int getDefaultBlockOffset() { return 8; }
    @Override public ScreenHandler getScreenHandler(int syncId, PlayerInventory inv) {
        return GenericContainerScreenHandler.createGeneric9x3(syncId, inv, this);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Running", running);
        if (extUuid != null) nbt.putUuid("Ext", extUuid);
        if (origin != null) {
            nbt.putInt("OX", origin.getX()); nbt.putInt("OY", origin.getY()); nbt.putInt("OZ", origin.getZ());
            nbt.putString("Heading", heading.getName());
            nbt.putInt("Built", built);
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        running = nbt.getBoolean("Running");
        if (nbt.containsUuid("Ext")) extUuid = nbt.getUuid("Ext");
        if (nbt.contains("OX")) {
            origin = new BlockPos(nbt.getInt("OX"), nbt.getInt("OY"), nbt.getInt("OZ"));
            Direction d = Direction.byName(nbt.getString("Heading"));
            heading = d != null ? d : Direction.NORTH;
            built = nbt.getInt("Built");
        }
    }
}
