package com.example.tunnelcart;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds already built tracks ahead of the cart and plans a bypass (over, else under) for them.
 *
 * A foreign powered rail is an obstacle only when the cart would really destroy it:
 *  - it stands in the 3-wide bore of a node (y from the rail level up to the roof, because the block
 *    under a rail in the roof layer is part of the bore), or
 *  - it stands right under the rail of a node (the floor block).
 * Rails in the outer shell (2 blocks to the side) are simply left alone by the builder, so a track that
 * only runs NEXT to the new one never triggers a bypass.
 */
public final class DetourPlanner {
    /** What the planner needs to know about the world. */
    public interface Terrain {
        boolean loaded(int x, int z);
        boolean poweredRail(int x, int y, int z);
        int bottomY();
        int topY();
    }

    public enum Result { OK, CHUNK, BLOCKED }

    public static final int LOOK = 40;       // how far ahead the route is scanned (room for the slowing blocks before a bypass)
    public static final int MIN_LOOK = 32;   // the cart waits until at least this much is scanned (longest ramp + slowing zone + 1)
    public static final int MAX_RAMP = 16;
    public static final int GAP = 3;         // gap (no rails) that still counts as the same foreign track
    public static final int GROUP_MAX = 600; // sanity limit for one platform planned in one go
    public static final int MERGE_DIST = 12; // closer than this behind a bypass -> lengthen it instead of a new one
    private static final int OVER = 5;       // platform level above the highest foreign rail
    private static final int UNDER = 7;      // platform level under the lowest foreign rail
    private static final int RAMP_TOP = 4;   // bore height on ramps

    private int scanned = 0;
    private BlockPos found = null;

    public void reset(int s) { scanned = s; found = null; }
    public BlockPos found() { return found; }

    // ------------------------------------------------------------------ scanning
    /** Call before node n is built. OK: build it. CHUNK: wait. BLOCKED: no way around, see found(). */
    public Result ensure(PathPlan plan, Terrain t, int n) {
        if (scanned < n - 1) scanned = n - 1;
        int y0 = plan.origin().getY();
        int need = plan.hasTarget() ? Math.min(n + MIN_LOOK, plan.total()) : n + MIN_LOOK;
        while (scanned < n + LOOK) {
            int j = scanned + 1;
            if (plan.hasTarget() && j > plan.total()) { scanned = j; break; }
            if (!loaded(plan, t, j)) break;                 // never mark an unknown column as checked

            PathPlan.Detour d = detourAt(plan, j);
            if (d != null) {
                // a column of an already planned bypass: it may have been unknown (not loaded) when it was planned
                List<BlockPos> hit = conflicts(plan, t, j, y0 + d.at(j)[0], RAMP_TOP);
                if (hit.isEmpty()) { scanned = j; continue; }
                List<PathPlan.Detour> ds = plan.detours();
                if (d != ds.get(ds.size() - 1) || j < d.rampStart()) { found = hit.get(0); return Result.BLOCKED; }
                // the foreign track goes on beyond our platform: lengthen the platform
                int[] g = group(plan, t, j, y0, hit);
                if (!extend(plan, t, d, n, g[0], g[1], g[2], y0)) { found = hit.get(0); return Result.BLOCKED; }
                scanned = Math.max(j, known(plan, t, j, d.end()));
                continue;
            }
            if (!plan.baseFlatAt(j)) { scanned = j; continue; }
            List<BlockPos> hit = conflicts(plan, t, j, y0, plan.top(j));
            if (hit.isEmpty()) { scanned = j; continue; }

            int[] g = group(plan, t, j, y0, hit);     // the whole stretch of the foreign track (may run parallel to us)
            if (!planDetour(plan, t, n, j, g[0], g[1], g[2], y0)) {
                found = hit.get(0);
                return Result.BLOCKED;
            }
            List<PathPlan.Detour> ds = plan.detours();
            scanned = Math.max(j, known(plan, t, j, ds.get(ds.size() - 1).end()));
        }
        return scanned >= need ? Result.OK : Result.CHUNK;
    }

    private static PathPlan.Detour detourAt(PathPlan plan, int j) {
        for (PathPlan.Detour d : plan.detours()) if (j >= d.start && j <= d.end()) return d;
        return null;
    }

    /** Highest index in [from, to] such that every column from..index is loaded (the rest is still unknown). */
    private static int known(PathPlan plan, Terrain t, int from, int to) {
        for (int i = from; i <= to; i++) if (!loaded(plan, t, i)) return i - 1;
        return to;
    }

    /** Extent of the foreign track that starts at column j: {last column, highest rail y, lowest rail y}. */
    private int[] group(PathPlan plan, Terrain t, int j, int y0, List<BlockPos> first) {
        int e = j, gap = 0;
        int top = Integer.MIN_VALUE, bot = Integer.MAX_VALUE;
        for (BlockPos p : first) { top = Math.max(top, p.getY()); bot = Math.min(bot, p.getY()); }
        for (int q = j + 1; q <= j + GROUP_MAX; q++) {
            if (plan.hasTarget() && q > plan.total()) break;
            if (!plan.baseFlatAt(q) || !loaded(plan, t, q)) break;
            if (detourAt(plan, q) != null && q < lastRamp(plan)) break;
            List<BlockPos> more = conflicts(plan, t, q, y0, plan.top(q));   // the track at its own level
            PathPlan.Detour d = detourAt(plan, q);
            if (d != null) more.addAll(conflicts(plan, t, q, y0 + d.at(q)[0], RAMP_TOP));
            if (more.isEmpty()) { if (++gap > GAP) break; continue; }
            gap = 0; e = q;
            for (BlockPos p : more) { top = Math.max(top, p.getY()); bot = Math.min(bot, p.getY()); }
        }
        return new int[]{e, top, bot};
    }

    /** First index of the way back of the last bypass (columns before it can not be passed through). */
    private static int lastRamp(PathPlan plan) {
        List<PathPlan.Detour> ds = plan.detours();
        return ds.isEmpty() ? Integer.MAX_VALUE : ds.get(ds.size() - 1).rampStart();
    }

    private static boolean loaded(PathPlan plan, Terrain t, int j) {
        BlockPos c = plan.col(j);
        for (int sx = -2; sx <= 2; sx += 4) for (int sz = -2; sz <= 2; sz += 4)
            if (!t.loaded(c.getX() + sx, c.getZ() + sz)) return false;
        return true;
    }

    // ------------------------------------------------------------------ planning
    private boolean planDetour(PathPlan plan, Terrain t, int n, int c, int e, int top, int bot, int y0) {
        List<PathPlan.Detour> ds = plan.detours();
        PathPlan.Detour last = ds.isEmpty() ? null : ds.get(ds.size() - 1);
        boolean close = last != null && c - last.end() <= MERGE_DIST;
        if (close && extend(plan, t, last, n, e, top, bot, y0)) return true;

        for (int sigma = 1; sigma >= -1; sigma -= 2) {          // above first, then below
            int t0 = top, b0 = bot, e0 = e;
            for (int attempt = 0; attempt < 12; attempt++) {
                int yP = sigma > 0 ? t0 + OVER : b0 - UNDER;
                int m = Math.abs(yP - y0);
                if (m < 1 || m > MAX_RAMP) break;
                int s = c - m;
                if (s < n || s < 1) break;
                if (sigma > 0 ? yP + RAMP_TOP + 1 >= t.topY() : yP - 1 <= t.bottomY()) break;
                PathPlan.Detour d = new PathPlan.Detour(s, m, e0 - c + 1, sigma);
                if (!plan.windowFlat(d.start, d.end())) break;
                int bad = -1;
                List<BlockPos> badHit = null;
                // the neighbours of a bypass get 5 blocks of headroom as well (not yet built ones only)
                for (int i = Math.max(n, d.start - 1); i <= d.end() + 1; i++) {
                    if (plan.hasTarget() && i > plan.total()) break;
                    if (!loaded(plan, t, i)) continue;      // unknown column: checked later by the scan
                    List<BlockPos> h = conflicts(plan, t, i, levelAt(d, i, y0), RAMP_TOP);
                    if (!h.isEmpty()) { bad = i; badHit = h; break; }
                }
                if (bad < 0) { plan.addDetour(d); return true; }
                // a foreign rail inside the way back down (another track close behind the first one):
                // the platform has to reach over it as well
                if (bad < d.rampStart()) break;
                e0 = Math.max(e0, bad);   // (also when the neighbour after the bypass is the culprit)
                for (BlockPos p : badHit) { t0 = Math.max(t0, p.getY()); b0 = Math.min(b0, p.getY()); }
            }
        }
        return !close && last != null && extend(plan, t, last, n, e, top, bot, y0);
    }

    /** Lengthen the platform of the last bypass (its way back has not been built yet). */
    private boolean extend(PathPlan plan, Terrain t, PathPlan.Detour last, int n, int e, int top, int bot, int y0) {
        if (n > last.rampStart()) return false;
        int yP = y0 + last.sigma * last.m;
        if (last.sigma > 0 ? yP < top + OVER : yP > bot - UNDER) return false;
        int plat = e - (last.start + last.m) + 1;
        if (plat <= last.plat) return false;
        PathPlan.Detour cand = new PathPlan.Detour(last.start, last.m, plat, last.sigma);
        for (int i = last.end() + 1; i <= cand.end(); i++)
            if (i < 1 || (plan.hasTarget() && i > plan.total()) || !plan.baseFlatAt(i) || plan.inDetour(i)) return false;
        for (int i = last.rampStart(); i <= cand.end() + 1; i++) {
            if (plan.hasTarget() && i > plan.total()) break;
            if (loaded(plan, t, i) && !conflicts(plan, t, i, levelAt(cand, i, y0), RAMP_TOP).isEmpty()) return false;
        }
        last.plat = plat;
        return true;
    }

    private static int levelAt(PathPlan.Detour d, int i, int y0) {
        return (i < d.start || i > d.end()) ? y0 : y0 + d.at(i)[0];
    }

    // ------------------------------------------------------------------ obstacles
    /** Foreign powered rails the node at level yi (bore height top) would destroy. */
    List<BlockPos> conflicts(PathPlan plan, Terrain t, int i, int yi, int top) {
        List<BlockPos> out = new ArrayList<>();
        PathPlan.Node nd = plan.node(i);
        int cx = nd.pos.getX(), cz = nd.pos.getZ();
        if (nd.corner) {
            for (int pu = -1; pu <= 1; pu++) for (int pv = -1; pv <= 1; pv++)
                column(plan, t, i, cx + nd.in.getOffsetX() * pu + nd.out.getOffsetX() * pv,
                        cz + nd.in.getOffsetZ() * pu + nd.out.getOffsetZ() * pv, yi, top, out);
        } else {
            Direction side = nd.out.rotateYClockwise();
            for (int a = -1; a <= 1; a++)
                column(plan, t, i, cx + side.getOffsetX() * a, cz + side.getOffsetZ() * a, yi, top, out);
        }
        // the block under our rail is replaced too
        check(plan, t, i, cx, yi - 1, cz, out);
        return out;
    }

    private void column(PathPlan plan, Terrain t, int i, int x, int z, int yi, int top, List<BlockPos> out) {
        // bore (yi .. yi+top) plus the roof layer: the rail standing there loses its support block
        for (int y = yi; y <= yi + top + 1; y++) check(plan, t, i, x, y, z, out);
    }

    private void check(PathPlan plan, Terrain t, int i, int x, int y, int z, List<BlockPos> out) {
        if (!t.poweredRail(x, y, z)) return;
        BlockPos q = new BlockPos(x, y, z);
        if (isOwn(plan, q, i)) return;
        out.add(q);
    }

    private boolean isOwn(PathPlan plan, BlockPos q, int j) {
        if (plan.behindOrigin(q)) return true;
        int hi = j + 4;
        if (plan.hasTarget()) hi = Math.min(hi, plan.total());
        for (int i = Math.max(0, j - 4); i <= hi; i++) if (plan.node(i).pos.equals(q)) return true;
        return false;
    }
}
