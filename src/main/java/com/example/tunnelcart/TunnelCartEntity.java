package com.example.tunnelcart;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.*;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
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

public class TunnelCartEntity extends ChestMinecartEntity {
    private static final TagKey<Block> C_ORES = TagKey.of(RegistryKeys.BLOCK, new Identifier("c", "ores"));
    private static final int FLAGS = Block.NOTIFY_LISTENERS;
    private static final int SLOPE_TORCH = 16;   // torch spacing
    private static final double SLOPE_SPEED = 0.5; // blocks/tick (10 bps) on and right before slopes
    private static final int LOOK = 26; // how far ahead we look for already built tracks

    private PathPlan plan = null;
    private int built = 0;
    private int lastProg = 0;
    private int scanned = 0;
    private boolean running = false;
    private boolean torches = false;
    private int tx, ty, tz;
    private UUID extUuid = null;
    private String issue = null;
    private Inventory pool = this;
    private final List<ItemStack> junk = new ArrayList<>();

    public TunnelCartEntity(EntityType<? extends TunnelCartEntity> type, World world) { super(type, world); }
    public TunnelCartEntity(World world, double x, double y, double z) {
        super(TunnelCartMod.TUNNEL_CART_ENTITY, world);
        setPosition(x, y, z);
    }

    public void setup(BlockPos origin, Direction heading) {
        plan = PathPlan.straight(origin, heading);
        built = 0; lastProg = 0; scanned = 0;
    }
    public Direction getHeading() { return plan != null ? plan.heading() : Direction.NORTH; }
    public boolean isRunning() { return running; }
    public void toggleRunning() { running = !running; }
    public boolean isTorches() { return torches; }
    public void toggleTorches() { torches = !torches; }
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

    // ------------------------------------------------------------ route by coordinates
    /** Called from the "by coordinates" menu. ty == null -> keep the current level. */
    public void startCoordinates(PlayerEntity player, int x, Integer y, int z) {
        if (plan == null) return;
        String err = null;
        try {
            PathPlan.Node front = plan.node(built);
            if (plan.hasTurn() && built > plan.turnIndex() && lastProg <= plan.turnIndex()) err = "coord_busy";
            else if (front.slope != PathPlan.Slope.FLAT) err = "coord_busy";
            else {
                PathPlan np = PathPlan.toTarget(front.pos, front.out, x, y != null ? y : front.pos.getY(), z);
                plan = np; built = 0; lastProg = 0; scanned = 0;
                tx = x; ty = np.origin().getY() + np.dy(); tz = z;
                running = true;
            }
        } catch (IllegalArgumentException ex) {
            err = ex.getMessage();
        }
        if (err != null) player.sendMessage(Text.translatable("tunnelcart." + err), true);
        else player.sendMessage(Text.translatable("tunnelcart.coord_ok"), true);
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
                    buf.writeInt(self.getId());
                }
            });
        }
        return ActionResult.success(getWorld().isClient);
    }

    // ------------------------------------------------------------ main loop
    @Override
    public void tick() {
        if (!getWorld().isClient && getWorld() instanceof ServerWorld sw) {
            if (plan == null || !running) {
                setVelocity(0, getVelocity().y, 0);
            } else {
                issue = null;
                pool = poolNow();
                double max = getMaxSpeed(); // includes High-Speed Rail override
                Direction h0 = plan.heading();
                BlockPos o = plan.origin();
                double along0 = (getX() - o.getX() - 0.5) * h0.getOffsetX() + (getZ() - o.getZ() - 0.5) * h0.getOffsetZ();
                double prog;
                Direction dir;
                if (lastProg == 0 && along0 <= 0.0) {
                    prog = along0; dir = h0;
                } else {
                    int bx = getBlockX(), bz = getBlockZ();
                    int hi = Math.min(built, lastProg + 24);
                    for (int j = lastProg; j <= hi; j++) {
                        BlockPos np = plan.node(j).pos;
                        if (np.getX() == bx && np.getZ() == bz) lastProg = j;
                    }
                    prog = lastProg;
                    dir = plan.node(lastProg).out;
                }

                int perTick = (int) Math.ceil(max);
                for (int j = Math.max(0, lastProg); j <= Math.min(built, lastProg + perTick + 2); j++) {
                    if (plan.node(j).slope != PathPlan.Slope.FLAT) { max = Math.min(max, SLOPE_SPEED); break; }
                }
                int target = (int) Math.floor(prog) + Math.max(6, perTick * 3 + 3);
                if (plan.hasTarget()) target = Math.min(target, plan.total());
                int budget = perTick + 3;
                while (built < target && budget-- > 0) {
                    if (!ensureDetour(sw, built + 1)) break;
                    if (!buildNode(sw, built + 1)) break;
                    built++;
                }
                boolean finished = plan.hasTarget() && built >= plan.total();
                double speed = Math.max(0.0, Math.min(max, built - prog - (finished ? 0.0 : 1.0)));
                if (finished && lastProg >= plan.total()) {
                    running = false;
                    speed = 0;
                    Text msg = Text.translatable("tunnelcart.done");
                    for (var p : sw.getPlayers(pl -> pl.squaredDistanceTo(this) < 2500)) p.sendMessage(msg, true);
                }
                setVelocity(dir.getOffsetX() * speed, getVelocity().y, dir.getOffsetZ() * speed);

                if (age % 20 == 0) flushJunk(sw);
                if (issue != null && speed < 0.05 && age % 40 == 0) {
                    Text msg = Text.translatable("tunnelcart." + issue);
                    for (var p : sw.getPlayers(pl -> pl.squaredDistanceTo(this) < 2500)) p.sendMessage(msg, true);
                }
            }
        }
        super.tick();
    }

    // ------------------------------------------------------------ detours around existing tracks
    private boolean ensureDetour(ServerWorld w, int n) {
        if (scanned < n - 1) scanned = n - 1;
        int y0 = plan.origin().getY();
        while (scanned < n + LOOK) {
            int j = scanned + 1;
            if (plan.hasTarget() && j > plan.total()) { scanned = j; break; }
            scanned = j;
            if (plan.inDetour(j) || !plan.baseFlatAt(j)) continue;
            List<Integer> ys = railsNear(w, j, y0);
            if (ys.isEmpty()) continue;
            int e = j;
            for (int q = j + 1; q <= j + 40; q++) {
                if (plan.hasTarget() && q > plan.total()) break;
                if (!plan.baseFlatAt(q)) break;
                List<Integer> more = railsNear(w, q, y0);
                if (more.isEmpty()) break;
                ys.addAll(more);
                e = q;
            }
            if (!planDetour(w, n, j, e, ys, y0)) {
                scanned = j - 1;
                issue = "blocked_path";
                return false;
            }
            scanned = Math.max(scanned, plan.detours().get(plan.detours().size() - 1).end());
        }
        return true;
    }

    private boolean planDetour(ServerWorld w, int n, int c, int e, List<Integer> ys, int y0) {
        int top = Integer.MIN_VALUE, bot = Integer.MAX_VALUE;
        for (int y : ys) { top = Math.max(top, y); bot = Math.min(bot, y); }
        int plat = e - c + 1;
        for (int sigma = 1; sigma >= -1; sigma -= 2) {          // above first, then below
            int yP = sigma > 0 ? top + 5 : bot - 5;
            int m = Math.abs(yP - y0);
            if (m < 1 || m > 14) continue;
            int s = c - m;
            if (s < n) continue;
            if (sigma > 0 ? yP + 3 >= w.getTopY() : yP - 1 <= w.getBottomY()) continue;
            PathPlan.Detour d = new PathPlan.Detour(s, m, plat, sigma);
            if (!plan.windowFlat(d.start, d.end())) continue;
            boolean free = true;
            for (int i = d.start; i <= d.end() && free; i++) {
                int yi = y0 + d.at(i)[0];
                if (!railsNear(w, i, yi).isEmpty()) free = false;
            }
            if (!free) continue;
            plan.addDetour(d);
            return true;
        }
        return false;
    }

    /** y-levels of foreign rails within 2 blocks (horizontally) of column j and within 4 levels of yRef. */
    private List<Integer> railsNear(ServerWorld w, int j, int yRef) {
        List<Integer> out = new ArrayList<>();
        BlockPos c = plan.col(j);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            int x = c.getX() + dx, z = c.getZ() + dz;
            if (!w.isChunkLoaded(new BlockPos(x, 0, z))) continue;
            for (int y = yRef - 4; y <= yRef + 4; y++) {
                BlockPos q = new BlockPos(x, y, z);
                if (w.isOutOfHeightLimit(q)) continue;
                if (!w.getBlockState(q).isIn(BlockTags.RAILS)) continue;
                if (isOwn(q, j)) continue;
                out.add(y);
            }
        }
        return out;
    }

    private boolean isOwn(BlockPos q, int j) {
        if (plan.behindOrigin(q)) return true;
        int hi = j + 4;
        if (plan.hasTarget()) hi = Math.min(hi, plan.total());
        for (int i = Math.max(0, j - 4); i <= hi; i++) if (plan.node(i).pos.equals(q)) return true;
        return false;
    }

    // ------------------------------------------------------------ one path node
    /** 5 blocks of headroom on slopes (and next to them), 3 elsewhere. Returns the highest bore layer. */
    private int topOf(int k) {
        if (plan.node(k).slope != PathPlan.Slope.FLAT) return 4;
        if (k > 0 && plan.node(k - 1).slope != PathPlan.Slope.FLAT) return 4;
        if (plan.node(k + 1).slope != PathPlan.Slope.FLAT) return 4;
        return 2;
    }

    private List<BlockPos> boreOf(int k) {
        PathPlan.Node nd = plan.node(k);
        int top = topOf(k);
        List<BlockPos> l = new ArrayList<>();
        BlockPos c = nd.pos;
        if (nd.corner) {
            for (int pu = -1; pu <= 1; pu++) for (int pv = -1; pv <= 1; pv++) for (int h = 0; h <= 2; h++)
                l.add(c.offset(nd.in, pu).offset(nd.out, pv).up(h));
        } else {
            Direction side = nd.out.rotateYClockwise();
            for (int a = -1; a <= 1; a++) for (int h = 0; h <= top; h++) l.add(c.offset(side, a).up(h));
        }
        return l;
    }

    private static RailShape ascending(Direction d) {
        switch (d) {
            case NORTH: return RailShape.ASCENDING_NORTH;
            case SOUTH: return RailShape.ASCENDING_SOUTH;
            case EAST: return RailShape.ASCENDING_EAST;
            default: return RailShape.ASCENDING_WEST;
        }
    }

    private static RailShape curve(Direction in, Direction out) {
        Direction a = in.getOpposite();
        boolean north = a == Direction.NORTH || out == Direction.NORTH;
        boolean south = a == Direction.SOUTH || out == Direction.SOUTH;
        boolean east = a == Direction.EAST || out == Direction.EAST;
        if (north && east) return RailShape.NORTH_EAST;
        if (north) return RailShape.NORTH_WEST;
        if (south && east) return RailShape.SOUTH_EAST;
        return RailShape.SOUTH_WEST;
    }

    /** floor = true for the h = -1 layer (slowing blocks live there). */
    private boolean shellAt(ServerWorld w, BlockPos p, boolean floor) {
        if (w.isOutOfHeightLimit(p)) return true;
        BlockState s = w.getBlockState(p);
        if (s.isOf(Blocks.DEEPSLATE) || s.isOf(Blocks.COBBLED_DEEPSLATE)) return true;
        if (floor && s.isOf(Blocks.COBBLESTONE)) return true; // slowing block of an earlier node
        if (s.hasBlockEntity() || s.getHardness(w, p) < 0) return true;
        if (!take(Items.COBBLED_DEEPSLATE)) { issue = "need_cobbled"; return false; }
        if (isWanted(s)) mine(w, p, s);
        w.setBlockState(p, Blocks.COBBLED_DEEPSLATE.getDefaultState(), FLAGS);
        return true;
    }

    private boolean buildNode(ServerWorld w, int k) {
        PathPlan.Node nd = plan.node(k);
        BlockPos c = nd.pos;
        Direction side = nd.out.rotateYClockwise();
        for (int sx = -3; sx <= 3; sx += 6) for (int sz = -3; sz <= 3; sz += 6)
            if (!w.isChunkLoaded(c.add(sx, 0, sz))) { issue = "chunk"; return false; }

        // 0) cap fluids in the next bore
        if (!(plan.hasTarget() && k >= plan.total())) {
            PathPlan.Node nx = plan.node(k + 1);
            if (w.isChunkLoaded(nx.pos)) {
                for (BlockPos p : boreOf(k + 1)) {
                    if (w.isOutOfHeightLimit(p) || w.getBlockState(p).getFluidState().isEmpty()) continue;
                    if (!take(Items.COBBLED_DEEPSLATE)) { issue = "need_cobbled"; return false; }
                    w.setBlockState(p, Blocks.COBBLED_DEEPSLATE.getDefaultState(), FLAGS);
                }
            }
        }

        // cells that already belong to the bore of earlier nodes must never be filled by this node's shell
        java.util.Set<BlockPos> earlier = new java.util.HashSet<>();
        for (int i = Math.max(0, k - 3); i < k; i++) earlier.addAll(boreOf(i));
        int top = topOf(k);

        // 1) shell: EVERYTHING except deepslate becomes cobbled deepslate; ores go to the cart
        if (nd.corner) {
            for (int pu = -2; pu <= 2; pu++) for (int pv = -2; pv <= 2; pv++) for (int h = -1; h <= 3; h++) {
                if (Math.abs(pu) <= 1 && Math.abs(pv) <= 1 && h >= 0 && h <= 2) continue;
                if (pu == -2 && Math.abs(pv) <= 1) continue; // cross-section of the previous node
                if (pv == 2 && Math.abs(pu) <= 1) continue;  // cross-section of the next node
                BlockPos sp = c.offset(nd.in, pu).offset(nd.out, pv).up(h);
                if (earlier.contains(sp)) continue;
                if (!shellAt(w, sp, h == -1)) return false;
            }
        } else {
            for (int a = -2; a <= 2; a++) for (int h = -1; h <= top + 1; h++) {
                if (Math.abs(a) <= 1 && h >= 0 && h <= top) continue;
                BlockPos sp = c.offset(side, a).up(h);
                if (earlier.contains(sp)) continue;
                if (!shellAt(w, sp, h == -1)) return false;
            }
        }

        // 2) bore
        for (BlockPos p : boreOf(k)) {
            if (p.equals(c)) continue;
            if (nd.corner && p.equals(c.offset(nd.in, -1))) continue; // rail of the previous node
            if (!clear(w, p)) return false;
        }

        // 3) floor under the rail: slowing block (cobblestone) or fast one (cobbled deepslate)
        BlockPos f = c.down();
        if (!w.isOutOfHeightLimit(f)) {
            BlockState fs = w.getBlockState(f);
            boolean keep = fs.hasBlockEntity() || fs.getHardness(w, f) < 0;
            if (!keep) {
                if (nd.brake) {
                    if (!fs.isOf(Blocks.COBBLESTONE)) {
                        if (!take(Items.COBBLESTONE)) { issue = "need_brake"; return false; }
                        w.setBlockState(f, Blocks.COBBLESTONE.getDefaultState(), FLAGS);
                    }
                } else if (fs.isOf(Blocks.COBBLESTONE)) {
                    if (!take(Items.COBBLED_DEEPSLATE)) { issue = "need_cobbled"; return false; }
                    w.setBlockState(f, Blocks.COBBLED_DEEPSLATE.getDefaultState(), FLAGS);
                }
            }
        }

        // 4) rail
        if (!clear(w, c)) return false;
        if (nd.corner) {
            if (!take(Items.RAIL)) { issue = "need_plain_rail"; return false; }
            w.setBlockState(c, Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, curve(nd.in, nd.out)), Block.NOTIFY_ALL);
            return true;
        }
        if (!take(Items.POWERED_RAIL)) { issue = "need_rail"; return false; }
        boolean powerHere;
        if (torches) {
            powerHere = k % SLOPE_TORCH == 0;
            if (plan.hasTurn()) {
                int d = k - (plan.turnIndex() + 1);   // first powered rail after the turn + two more, 16 apart
                if (d == 0 || d == SLOPE_TORCH || d == 2 * SLOPE_TORCH) powerHere = true;
            }
        } else powerHere = k % 8 == 0;
        if (powerHere) {
            if (torches) {
                BlockPos tp = c.offset(side, 1);
                if (w.getBlockState(tp).isAir() && take(Items.REDSTONE_TORCH))
                    w.setBlockState(tp, Blocks.REDSTONE_WALL_TORCH.getDefaultState().with(WallRedstoneTorchBlock.FACING, side.getOpposite()), Block.NOTIFY_ALL);
            } else if (!nd.brake && take(Items.REDSTONE_BLOCK)) {
                w.setBlockState(f, Blocks.REDSTONE_BLOCK.getDefaultState(), FLAGS);
            }
        }
        RailShape shape;
        if (nd.slope == PathPlan.Slope.UP) shape = ascending(nd.out);
        else if (nd.slope == PathPlan.Slope.DOWN) shape = ascending(nd.out.getOpposite());
        else shape = nd.out.getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
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
            w.spawnEntity(ie);
        }
        junk.clear();
    }

    private boolean take(Item item) { return CartInv.take(pool, item); }

    // ------------------------------------------------------------ minecart boilerplate
    public Item getItem() { return TunnelCartMod.TUNNEL_CART_ITEM; }
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
        nbt.putBoolean("Torches", torches);
        if (extUuid != null) nbt.putUuid("Ext", extUuid);
        if (plan != null) {
            BlockPos o = plan.origin();
            nbt.putInt("OX", o.getX()); nbt.putInt("OY", o.getY()); nbt.putInt("OZ", o.getZ());
            nbt.putString("Heading", plan.heading().getName());
            nbt.putInt("Built", built);
            nbt.putInt("Prog", lastProg);
            nbt.putBoolean("HasT", plan.hasTarget());
            nbt.putInt("TX", tx); nbt.putInt("TY", ty); nbt.putInt("TZ", tz);
            int[] arr = new int[plan.detours().size() * 4];
            int i = 0;
            for (PathPlan.Detour d : plan.detours()) { arr[i++] = d.start; arr[i++] = d.m; arr[i++] = d.plat; arr[i++] = d.sigma; }
            nbt.putIntArray("Detours", arr);
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        running = nbt.getBoolean("Running");
        torches = nbt.getBoolean("Torches");
        if (nbt.containsUuid("Ext")) extUuid = nbt.getUuid("Ext");
        if (nbt.contains("OX")) {
            BlockPos o = new BlockPos(nbt.getInt("OX"), nbt.getInt("OY"), nbt.getInt("OZ"));
            Direction d = Direction.byName(nbt.getString("Heading"));
            if (d == null) d = Direction.NORTH;
            tx = nbt.getInt("TX"); ty = nbt.getInt("TY"); tz = nbt.getInt("TZ");
            try {
                plan = nbt.getBoolean("HasT") ? PathPlan.toTarget(o, d, tx, ty, tz) : PathPlan.straight(o, d);
            } catch (IllegalArgumentException ex) {
                plan = PathPlan.straight(o, d);
            }
            int[] arr = nbt.getIntArray("Detours");
            for (int i = 0; i + 3 < arr.length; i += 4) plan.addDetour(new PathPlan.Detour(arr[i], arr[i + 1], arr[i + 2], arr[i + 3]));
            built = nbt.getInt("Built");
            lastProg = nbt.getInt("Prog");
            scanned = built;
        }
    }
}
