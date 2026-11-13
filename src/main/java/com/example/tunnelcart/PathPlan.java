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
    public static final int BRAKE = 10;        // slowing blocks before a turn / after a turn / before a climb
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
        public final int start, m, plat, sigma;
        public Detour(int start, int m, int plat, int sigma) { this.start = start; this.m = m; this.plat = plat; this.sigma = sigma; }
        public int end() { return start + 2 * m + plat - 1; }
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
        if (dyy < 0 && leg < -dyy + 1) throw new IllegalArgumentException("coord_short");
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
        int m = -dy, s = n - m;
        if (i >= n) return new int[]{-m, 0};
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
        if (target && sl == Slope.FLAT && !turnHere) {
            if (l2 > 0 && ((k >= l1 - BRAKE && k < l1) || (k > l1 && k <= l1 + BRAKE))) brake = true;
            if (dy > 0) {
                int s = l1 + l2 - (AFTER_CLIMB - 1) - dy;
                if (k >= s - BRAKE && k < s) brake = true;
            }
        }
        return new Node(c.up(v[0]), in, out, turnHere, brake, sl);
    }
}
