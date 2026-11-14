package com.example.tunnelcart;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure geometry of a tunnel route (no world access).
 * Node 0 is the origin rail. Node i (i >= 1) is the i-th rail laid.
 * Straight mode: endless line. Target mode: leg 1, ONE turn, leg 2, and the climb/descent at the very end.
 */
public final class PathPlan {
    public static final int BRAKE = 15;        // slowing blocks before a turn / after a turn / before every slope (climb, descent, bypass)
    public static final int AFTER_CLIMB = 11;  // straight rails after the climb

    public enum Slope { FLAT, UP, DOWN }

    public static final class Node {
        public final BlockPos pos;
        public final Direction in, out;
        public final boolean corner, brake;
        public final Slope slope;
        Node(BlockPos pos, Direction in, Direction out, boolean corner, boolean brake, Slope slope) {
            this.pos = pos; this.in = in; this.out = out; this.corner = corner; this.brake = brake; this.slope = slope;
        }
    }

    /** A bypass over (sigma = +1) or under (sigma = -1) an already built track. */
    public static final class Detour {
        public final int start, m, sigma;
        public int plat; // may be lengthened while the descent has not been built yet
        public Detour(int start, int m, int plat, int sigma) { this.start = start; this.m = m; this.plat = plat; this.sigma = sigma; }
        public int end() { return start + 2 * m + plat - 1; }
        /** index of the first node of the way back down (up) to the base level */
        public int rampStart() { return start + m + plat; }
        /** height offset of node i (relative to the origin level) and its slope: {offset, 0 flat / 1 up / 2 down} */
        int[] at(int i) {
            int j = i - start;
            if (j < m) return sigma > 0 ? new int[]{j, 1} : new int[]{-1 - j, 2};
            if (j < m + plat) return new int[]{sigma * m, 0};
            int r = j - m - plat;
            return sigma > 0 ? new int[]{m - 1 - r, 2} : new int[]{-m + r, 1};
        }
    }

    private final BlockPos origin;
    private final Direction h0, h1;
    private final boolean target;
    private final int l1, l2, dy;
    private final List<Detour> detours = new ArrayList<>();

    private PathPlan(BlockPos origin, Direction h0, Direction h1, boolean target, int l1, int l2, int dy) {
        this.origin = origin.toImmutable(); this.h0 = h0; this.h1 = h1; this.target = target;
        this.l1 = l1; this.l2 = l2; this.dy = dy;
    }

    public static PathPlan straight(BlockPos origin, Direction heading) {
        return new PathPlan(origin, heading, heading, false, 0, 0, 0);
    }

    /** @throws IllegalArgumentException with a translation key suffix (tunnelcart.<message>) */
    public static PathPlan toTarget(BlockPos origin, Direction h0, int tx, int ty, int tz) {
        int dx = tx - origin.getX(), dz = tz - origin.getZ(), dyy = ty - origin.getY();
        int a1 = dx * h0.getOffsetX() + dz * h0.getOffsetZ();
        int lat = h0.getAxis() == Direction.Axis.X ? dz : dx;
        if (a1 == 0 && lat == 0 && dyy == 0) throw new IllegalArgumentException("coord_same");
        Direction h1 = h0;
        int l2 = Math.abs(lat);
        if (l2 > 0) {
            h1 = h0.getAxis() == Direction.Axis.X ? (dz > 0 ? Direction.SOUTH : Direction.NORTH)
                                                  : (dx > 0 ? Direction.EAST : Direction.WEST);
            if (a1 < 0) throw new IllegalArgumentException("coord_behind");
            if (a1 < BRAKE + 1 || l2 < BRAKE + 1) throw new IllegalArgumentException("coord_short");
        } else if (a1 < 0) throw new IllegalArgumentException("coord_behind");
        int leg = l2 > 0 ? l2 : a1;
        if (dyy > 0 && leg < dyy + BRAKE + AFTER_CLIMB) throw new IllegalArgumentException("coord_short");
        if (dyy < 0 && leg < -dyy + BRAKE + AFTER_CLIMB) throw new IllegalArgumentException("coord_short");
        if (a1 == 0 && l2 == 0) throw new IllegalArgumentException("coord_short");
        return new PathPlan(origin, h0, h1, true, a1, l2, dyy);
    }

    // ---------------------------------------------------------------- queries
    public BlockPos origin() { return origin; }
    public Direction heading() { return h0; }
    public boolean hasTarget() { return target; }
    public boolean hasTurn() { return target && l2 > 0; }
    public int turnIndex() { return l1; }
    public int total() { return target ? l1 + l2 : Integer.MAX_VALUE; }
    public List<Detour> detours() { return detours; }
    public int dy() { return dy; }

    public void addDetour(Detour d) { detours.add(d); }
    public boolean inDetour(int i) {
        for (Detour d : detours) if (i >= d.start && i <= d.end()) return true;
        return false;
    }

    /** Horizontal column of node i at the origin level. */
    public BlockPos col(int i) {
        int k = Math.max(i, 0);
        if (!target || k <= l1) return origin.offset(h0, k);
        return origin.offset(h0, l1).offset(h1, k - l1);
    }

    private int[] baseVert(int i) {
        if (!target || dy == 0) return new int[]{0, 0};
        int n = l1 + l2;
        if (dy > 0) {
            int f = n - (AFTER_CLIMB - 1), s = f - dy;
            if (i >= f) return new int[]{dy, 0};
            if (i >= s) return new int[]{i - s, 1};
            return new int[]{0, 0};
        }
        int m = -dy, f = n - (AFTER_CLIMB - 1), s = f - m;   // like a climb: straight rails after the descent
        if (i >= f) return new int[]{-m, 0};
        if (i >= s) return new int[]{-1 - (i - s), 2};
        return new int[]{0, 0};
    }

    /** true when the BASE route is flat, at origin level and not a corner at node i. */
    public boolean baseFlatAt(int i) {
        int[] b = baseVert(i);
        return b[0] == 0 && b[1] == 0 && !(hasTurn() && i == l1);
    }

    public boolean windowFlat(int a, int b) {
        for (int i = a; i <= b; i++) {
            if (i < 1 || i > total() || !baseFlatAt(i) || inDetour(i)) return false;
        }
        return true;
    }

    /** True for rails that belong to the track behind the origin (same line) - never an obstacle. */
    public boolean behindOrigin(BlockPos q) {
        int ax = q.getX() - origin.getX(), az = q.getZ() - origin.getZ();
        int along = ax * h0.getOffsetX() + az * h0.getOffsetZ();
        int lat = h0.getAxis() == Direction.Axis.X ? az : ax;
        return along <= 0; // everything behind the origin is never an obstacle
    }

    /** Highest bore layer of node k: 4 (5 blocks of headroom) on slopes and next to them, 2 elsewhere. */
    public int top(int k) {
        if (node(k).slope != Slope.FLAT) return 4;
        if (k > 0 && node(k - 1).slope != Slope.FLAT) return 4;
        if (node(k + 1).slope != Slope.FLAT) return 4;
        return 2;
    }

    public Node node(int i) {
        int k = Math.max(i, 0);
        BlockPos c = col(k);
        boolean turnHere = hasTurn() && k == l1;
        Direction in = (!target || l2 == 0 || k <= l1) ? h0 : h1;
        Direction out = (!target || l2 == 0 || k < l1) ? h0 : h1;
        int[] v = baseVert(k);
        for (Detour d : detours) if (k >= d.start && k <= d.end()) v = d.at(k);
        Slope sl = Slope.values()[v[1]];
        boolean brake = false;
        if (sl == Slope.FLAT && !turnHere) {
            if (target) {
                if (l2 > 0 && ((k >= l1 - BRAKE && k < l1) || (k > l1 && k <= l1 + BRAKE))) brake = true;
                if (dy != 0) {                                    // before the climb / descent at the end
                    int s = l1 + l2 - (AFTER_CLIMB - 1) - Math.abs(dy);
                    if (k >= s - BRAKE && k < s) brake = true;
                }
            }
            // a bypass over/under an old track: before its way out of the base level and before its way back
            for (Detour d : detours) {
                if (k >= d.start - BRAKE && k < d.start) brake = true;
                if (k >= d.rampStart() - BRAKE && k < d.rampStart()) brake = true;
            }
        }
        return new Node(c.up(v[0]), in, out, turnHere, brake, sl);
    }

    /**
     * Rails that always get a redstone torch (even with the torch button off), so the cart can accelerate again:
     *  - turn: before it the last rail and two more 16 apart; after it the first rail and two more 16 apart;
     *  - slope (climb, descent, bypass): before it the last rail and two more 16 apart; on it every 16th rail;
     *    after it the first rail and three more 16 apart.
     */
    public boolean boost(int k) {
        if (k < 1) return false;
        if (hasTurn()) {
            int before = (l1 - 1) - k, after = k - (l1 + 1);
            if (before == 0 || before == 16 || before == 32) return true;
            if (after == 0 || after == 16 || after == 32) return true;
        }
        Node nd = node(k);
        if (nd.slope != Slope.FLAT) {
            int r = 0;
            while (k - r - 1 >= 0 && node(k - r - 1).slope != Slope.FLAT) r++;
            return r % 16 == 15;
        }
        for (int off = 0; off <= 32; off += 16) {           // slope starts 1, 17 or 33 rails ahead
            int t = k + 1 + off;
            if (node(t).slope != Slope.FLAT && node(t - 1).slope == Slope.FLAT) return true;
        }
        for (int back = 1; back <= 49; back += 16) {        // slope ended 1, 17, 33 or 49 rails ago
            int t = k - back;
            if (t >= 0 && node(t).slope != Slope.FLAT && node(t + 1).slope == Slope.FLAT) return true;
        }
        return false;
    }
}
