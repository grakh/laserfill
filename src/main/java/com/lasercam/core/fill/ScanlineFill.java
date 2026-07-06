package com.lasercam.core.fill;

import com.lasercam.core.model.Point2D;
import com.lasercam.core.model.PolygonWithHoles;
import com.lasercam.core.model.Polyline;
import com.lasercam.core.model.Toolpath;
import com.lasercam.core.model.ToolpathSegment;

import java.util.*;

/**
 * Strip-based serpentine scanline fill.
 *
 * When a hole splits scanlines, each side of the hole becomes its own
 * serpentine (column). Columns are connected by TRAVEL (laser OFF).
 * Within each column the path is continuous (laser ON).
 *
 * Result: minimal travel, no extra passes around holes.
 */
public class ScanlineFill implements FillStrategy {

    /** How to handle holes when the scanline crosses them. */
    public enum HoleMode {
        /** Stop → separate column on other side, connected by TRAVEL. */
        CONTOUR,
        /** Stop → trace along hole boundary to the other side (all CUT). */
        TRACE,
        /** Stop → TRAVEL straight across the hole and continue. */
        SKIP
    }

    private double pitch;
    private double angleDeg;
    private HoleMode holeMode = HoleMode.TRACE;
    private double holeOffset = 0; // extra mm to stay away from hole edges (kerf compensation)

    public ScanlineFill(double pitch) { this(pitch, 0); }
    public ScanlineFill(double pitch, double angleDeg) {
        this.pitch = pitch;
        this.angleDeg = angleDeg;
    }

    public void setPitch(double p) { pitch = p; }
    public void setAngle(double a) { angleDeg = a; }
    public void setHoleMode(HoleMode m) { holeMode = m; }
    public void setHoleOffset(double o) { holeOffset = o; }
    public double getPitch() { return pitch; }
    public double getAngle() { return angleDeg; }
    public HoleMode getHoleMode() { return holeMode; }
    public double getHoleOffset() { return holeOffset; }

    // ─── Data classes ───

    static class Span {
        double x1, x2, y;
        int colId = -1;
        Span(double x1, double x2, double y) { this.x1=x1; this.x2=x2; this.y=y; }
        double midX() { return (x1+x2)/2; }
        boolean overlapsX(Span o) {
            return x1 < o.x2 + 0.1 && o.x1 < x2 + 0.1;
        }
    }

    @Override
    public Toolpath generate(PolygonWithHoles poly) {
        Toolpath tp = new Toolpath();
        if (poly.getOuter() == null) return tp;

        double rad = Math.toRadians(angleDeg);
        boolean rot = Math.abs(rad) > 1e-6;
        PolygonWithHoles work = rot ? rotatePoly(poly, -rad) : poly;

        double minY = work.getOuter().getMinY();
        double maxY = work.getOuter().getMaxY();

        // ── 1. Build rows ──
        // Collect intersections separately from outer and holes so we can shrink
        // hole-side edges by holeOffset (kerf compensation).
        List<List<Span>> rows = new ArrayList<>();
        for (double y = minY + pitch/2; y < maxY; y += pitch) {
            List<Double> outerXs = new ArrayList<>();
            List<Double> holeXs = new ArrayList<>();
            addIx(work.getOuter(), y, outerXs);
            for (Polyline h : work.getHoles()) addIx(h, y, holeXs);

            // Combined sorted list, tagged
            List<double[]> tagged = new ArrayList<>();
            for (double x : outerXs) tagged.add(new double[]{x, 0});
            for (double x : holeXs) tagged.add(new double[]{x, 1});
            tagged.sort(Comparator.comparingDouble(a -> a[0]));

            // Walk in even-odd fashion; between crossings we alternate inside/outside
            List<Span> spans = new ArrayList<>();
            boolean inside = false;
            double spanStart = 0;
            boolean startFromHole = false;
            for (double[] t : tagged) {
                double x = t[0];
                boolean isHole = t[1] > 0.5;
                if (!inside) {
                    spanStart = x;
                    // If we entered material AT a hole edge, offset span start to the right
                    startFromHole = isHole;
                    if (isHole && holeOffset > 0) spanStart += holeOffset;
                    inside = true;
                } else {
                    // Leaving material at x
                    double spanEnd = x;
                    // If leaving at a hole edge, pull the end back
                    if (isHole && holeOffset > 0) spanEnd -= holeOffset;
                    if (spanEnd - spanStart > 0.05)
                        spans.add(new Span(spanStart, spanEnd, y));
                    inside = false;
                }
            }
            rows.add(spans);
        }

        // ── Hole handling mode ──
        switch (holeMode) {
            case SKIP:    return generateSkipHoles(rows, rot, rad);
            case TRACE:   return generateContourTrace(rows, work, rot, rad);
            case CONTOUR: return generateColumns(rows, work, rot, rad);
        }
        return generateContourTrace(rows, work, rot, rad);
    }

    /**
     * Default mode: build a serpentine that goes L→R / R→L each row.
     * When a row has multiple spans (hole splits it), the "gap" between spans
     * is traced along the hole boundary (shortest arc) so laser stays ON.
     */
    private Toolpath generateContourTrace(List<List<Span>> rows, PolygonWithHoles work,
                                          boolean rot, double rad) {
        Toolpath tp = new Toolpath();
        boolean leftToRight = true;
        Point2D cursor = null;
        Point2D cursorWork = null; // cursor in unrotated (work) coords for hole tests

        for (List<Span> row : rows) {
            if (row.isEmpty()) { leftToRight = !leftToRight; continue; }

            List<Span> ordered = new ArrayList<>(row);
            ordered.sort(Comparator.comparingDouble(s -> s.x1));
            if (!leftToRight) Collections.reverse(ordered);

            for (int i = 0; i < ordered.size(); i++) {
                Span s = ordered.get(i);
                double sx, ex;
                if (leftToRight) { sx = s.x1; ex = s.x2; }
                else             { sx = s.x2; ex = s.x1; }

                Point2D aWork = new Point2D(sx, s.y);
                Point2D bWork = new Point2D(ex, s.y);
                Point2D a = rp(aWork, rot, rad);
                Point2D b = rp(bWork, rot, rad);

                if (cursor != null && cursor.dist(a) > 0.05) {
                    if (i == 0) {
                        // Between rows — direct connector (always safe: same column edge)
                        tp.addCutSegment(cursor, a);
                    } else {
                        // Gap between spans in same row = a hole is in the way.
                        // Trace the hole contour instead of jumping.
                        traceHoleGap(tp, work, cursorWork, aWork, rot, rad);
                    }
                }
                tp.addCutSegment(a, b);
                cursor = b;
                cursorWork = bWork;
            }
            leftToRight = !leftToRight;
        }
        return tp;
    }

    /**
     * Trace the hole boundary from point 'from' to point 'to' (both in work coords).
     * Both points lie on the hole boundary (they came from scanline intersections).
     * Uses the shorter arc.
     */
    private void traceHoleGap(Toolpath tp, PolygonWithHoles work,
                              Point2D from, Point2D to, boolean rot, double rad) {
        // Find the hole between them
        double mx = (from.x() + to.x()) / 2;
        double my = (from.y() + to.y()) / 2;
        Polyline hole = null;
        for (Polyline h : work.getHoles()) {
            if (pointInPolyline(new Point2D(mx, my), h)) { hole = h; break; }
        }
        if (hole == null) {
            // No hole — direct cut (shouldn't happen normally)
            tp.addCutSegment(rp(from, rot, rad), rp(to, rot, rad));
            return;
        }

        List<Point2D> pts = hole.getPoints();
        int n = pts.size();
        if (n > 2 && pts.get(0).dist(pts.get(n - 1)) < 1e-9) n--;
        if (n < 3) return;

        // Find nearest vertices to 'from' and 'to'
        int idxFrom = nearestVertex(pts, n, from);
        int idxTo   = nearestVertex(pts, n, to);

        // Compute both arc lengths (CW and CCW), pick shorter
        double lenCW = arcLen(pts, n, idxFrom, idxTo, +1);
        double lenCCW = arcLen(pts, n, idxFrom, idxTo, -1);
        int dir = lenCW <= lenCCW ? +1 : -1;

        Point2D prev = rp(from, rot, rad);
        int i = idxFrom;
        boolean first = true;
        while (true) {
            Point2D vp = rp(pts.get(i), rot, rad);
            // Skip the first vertex if it's essentially the same as 'from'
            if (!first || prev.dist(vp) > 0.5) {
                if (prev.dist(vp) > 0.05) tp.addCutSegment(prev, vp);
                prev = vp;
            }
            first = false;
            if (i == idxTo) break;
            i = ((i + dir) % n + n) % n;
        }
        Point2D end = rp(to, rot, rad);
        if (prev.dist(end) > 0.05) tp.addCutSegment(prev, end);
    }

    private int nearestVertex(List<Point2D> pts, int n, Point2D target) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double d = pts.get(i).dist(target);
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    private double arcLen(List<Point2D> pts, int n, int from, int to, int dir) {
        double len = 0;
        int i = from;
        while (i != to) {
            int next = ((i + dir) % n + n) % n;
            len += pts.get(i).dist(pts.get(next));
            i = next;
        }
        return len;
    }

    // ─── OLD column-based logic below, unused when skipHoles=false ───
    // Kept temporarily; can be removed.
    private Toolpath generateColumns(List<List<Span>> rows, PolygonWithHoles work, boolean rot, double rad) {
        Toolpath tp = new Toolpath();
        // Each prev span matches at most one current span → hole sides get separate columns
        int nextCol = 0;
        for (int r = 0; r < rows.size(); r++) {
            List<Span> row = rows.get(r);
            if (r == 0) {
                for (Span s : row) s.colId = nextCol++;
                continue;
            }
            List<Span> prev = rows.get(r - 1);
            // Build overlap pairs, sort by overlap descending
            record Match(int pi, int ci, double ov) {}
            List<Match> matches = new ArrayList<>();
            for (int pi = 0; pi < prev.size(); pi++)
                for (int ci = 0; ci < row.size(); ci++) {
                    Span p = prev.get(pi), c = row.get(ci);
                    if (p.overlapsX(c)) {
                        double ov = Math.min(p.x2, c.x2) - Math.max(p.x1, c.x1);
                        matches.add(new Match(pi, ci, ov));
                    }
                }
            matches.sort((a, b) -> Double.compare(b.ov, a.ov));

            boolean[] prevUsed = new boolean[prev.size()];
            boolean[] curUsed  = new boolean[row.size()];

            for (Match m : matches) {
                if (prevUsed[m.pi] || curUsed[m.ci]) continue;
                row.get(m.ci).colId = prev.get(m.pi).colId;
                prevUsed[m.pi] = true;
                curUsed[m.ci]  = true;
            }
            // Unmatched current spans get new columns
            for (int ci = 0; ci < row.size(); ci++) {
                if (!curUsed[ci]) row.get(ci).colId = nextCol++;
            }
        }

        // ── 3. Group spans by column ──
        Map<Integer, List<Span>> columns = new TreeMap<>();
        for (List<Span> row : rows)
            for (Span s : row)
                columns.computeIfAbsent(s.colId, k -> new ArrayList<>()).add(s);

        // ── 4. Serpentine each column ──
        // Split a column if there's a Y gap larger than 1.5*pitch (means the column
        // is not actually continuous — it wraps around a hole and would need a jump).
        List<Toolpath> columnPaths = new ArrayList<>();
        for (var entry : columns.entrySet()) {
            List<Span> spans = entry.getValue();
            spans.sort(Comparator.comparingDouble(s -> s.y));

            // Break spans into sub-groups where consecutive Ys are within pitch*1.5
            List<List<Span>> subGroups = new ArrayList<>();
            List<Span> currentGroup = new ArrayList<>();
            for (Span s : spans) {
                if (!currentGroup.isEmpty()) {
                    double gap = s.y - currentGroup.get(currentGroup.size() - 1).y;
                    if (gap > pitch * 1.6) {
                        subGroups.add(currentGroup);
                        currentGroup = new ArrayList<>();
                    }
                }
                currentGroup.add(s);
            }
            if (!currentGroup.isEmpty()) subGroups.add(currentGroup);

            for (List<Span> group : subGroups) {
                // Build serpentine. Between consecutive spans, if the connector
                // (from prev end to cur start) intersects a hole, start a new column.
                List<List<Span>> finalGroups = new ArrayList<>();
                List<Span> subG = new ArrayList<>();
                boolean lr = true;
                Point2D lastEnd = null;

                for (Span s : group) {
                    double startX = lr ? s.x1 : s.x2;
                    double endX   = lr ? s.x2 : s.x1;
                    Point2D curStart = new Point2D(startX, s.y);
                    Point2D curEnd   = new Point2D(endX,   s.y);

                    if (subG.isEmpty()) {
                        subG.add(s);
                        lastEnd = curEnd;
                        lr = !lr;
                        continue;
                    }
                    // Check if connector lastEnd → curStart is safe
                    boolean safe = !connectorHitsHole(work, lastEnd, curStart);
                    if (!safe) {
                        finalGroups.add(subG);
                        subG = new ArrayList<>();
                        lr = true;
                        startX = s.x1; endX = s.x2;
                        curEnd = new Point2D(endX, s.y);
                    }
                    subG.add(s);
                    lastEnd = curEnd;
                    lr = !lr;
                }
                if (!subG.isEmpty()) finalGroups.add(subG);

                for (List<Span> fg : finalGroups) {
                    Toolpath colTp = new Toolpath();
                    boolean leftToRight = true;
                    Point2D cursor = null;

                    for (Span s : fg) {
                        Point2D a, b;
                        if (leftToRight) {
                            a = rp(new Point2D(s.x1, s.y), rot, rad);
                            b = rp(new Point2D(s.x2, s.y), rot, rad);
                        } else {
                            a = rp(new Point2D(s.x2, s.y), rot, rad);
                            b = rp(new Point2D(s.x1, s.y), rot, rad);
                        }
                        if (cursor != null && cursor.dist(a) > 0.05) {
                            colTp.addCutSegment(cursor, a);
                        }
                        colTp.addCutSegment(a, b);
                        cursor = b;
                        leftToRight = !leftToRight;
                    }
                    if (!colTp.getSegments().isEmpty()) columnPaths.add(colTp);
                }
            }
        }

        // ── 5. Order columns by nearest-neighbor, connect with TRAVEL ──
        if (columnPaths.isEmpty()) return tp;

        boolean[] used = new boolean[columnPaths.size()];
        int cur = 0;
        used[0] = true;
        List<Integer> order = new ArrayList<>();
        order.add(0);

        for (int i = 1; i < columnPaths.size(); i++) {
            Point2D lastPt = lastPoint(columnPaths.get(cur));
            double bestD = Double.MAX_VALUE;
            int bestJ = -1;
            for (int j = 0; j < columnPaths.size(); j++) {
                if (used[j]) continue;
                Point2D fp = firstPoint(columnPaths.get(j));
                Point2D lp = lastPoint(columnPaths.get(j));
                double d = Math.min(lastPt.dist(fp), lastPt.dist(lp));
                if (d < bestD) { bestD = d; bestJ = j; }
            }
            used[bestJ] = true;
            order.add(bestJ);
            cur = bestJ;
        }

        // Emit ordered columns
        Point2D head = null;
        for (int idx : order) {
            Toolpath colTp = columnPaths.get(idx);
            List<ToolpathSegment> segs = colTp.getSegments();
            if (segs.isEmpty()) continue;

            // Check if reversing the column path is closer
            Point2D fp = segs.get(0).start();
            Point2D lp = segs.get(segs.size()-1).end();
            boolean reverse = (head != null && head.dist(lp) < head.dist(fp));

            if (reverse) {
                for (int i = segs.size()-1; i >= 0; i--) {
                    ToolpathSegment s = segs.get(i).reversed();
                    if (head != null && head.dist(s.start()) > 0.05)
                        tp.addTravelSegment(head, s.start());
                    tp.addSegment(s);
                    head = s.end();
                }
            } else {
                for (ToolpathSegment s : segs) {
                    if (head != null && head.dist(s.start()) > 0.05)
                        tp.addTravelSegment(head, s.start());
                    tp.addSegment(s);
                    head = s.end();
                }
            }
        }

        return tp;
    }

    // ─── helpers ───

    private Point2D firstPoint(Toolpath t) { return t.getSegments().get(0).start(); }
    private Point2D lastPoint(Toolpath t) { var s=t.getSegments(); return s.get(s.size()-1).end(); }

    private Point2D rp(Point2D p, boolean rot, double rad) {
        if (!rot) return p;
        double c=Math.cos(rad), s=Math.sin(rad);
        return new Point2D(p.x()*c - p.y()*s, p.x()*s + p.y()*c);
    }

    /**
     * Skip-holes mode: single serpentine along X axis. Each row goes L→R or R→L
     * (alternating). If a row has multiple spans (hole in the middle),
     * the gap between spans is TRAVEL (laser OFF), the spans stay CUT.
     * Row-to-row connectors are also CUT (vertical serpentine step at the edge).
     */
    private Toolpath generateSkipHoles(List<List<Span>> rows, boolean rot, double rad) {
        Toolpath tp = new Toolpath();
        boolean leftToRight = true;
        Point2D cursor = null;

        for (List<Span> row : rows) {
            if (row.isEmpty()) { leftToRight = !leftToRight; continue; }

            List<Span> ordered = new ArrayList<>(row);
            ordered.sort(Comparator.comparingDouble(s -> s.x1));
            if (!leftToRight) Collections.reverse(ordered);

            for (int i = 0; i < ordered.size(); i++) {
                Span s = ordered.get(i);
                Point2D a, b;
                if (leftToRight) {
                    a = rp(new Point2D(s.x1, s.y), rot, rad);
                    b = rp(new Point2D(s.x2, s.y), rot, rad);
                } else {
                    a = rp(new Point2D(s.x2, s.y), rot, rad);
                    b = rp(new Point2D(s.x1, s.y), rot, rad);
                }

                if (cursor != null && cursor.dist(a) > 0.05) {
                    if (i == 0) {
                        // Connector between rows — vertical serpentine step, always CUT
                        tp.addCutSegment(cursor, a);
                    } else {
                        // Gap between spans in same row = hole crossing → TRAVEL
                        tp.addTravelSegment(cursor, a);
                    }
                }
                tp.addCutSegment(a, b);
                cursor = b;
            }
            leftToRight = !leftToRight;
        }
        return tp;
    }

    /**
     * Test whether the straight segment from a→b crosses any hole boundary.
     * Samples the connector densely with perpendicular inset points.
     */
    private boolean connectorHitsHole(PolygonWithHoles poly, Point2D a, Point2D b) {
        double dx = b.x() - a.x(), dy = b.y() - a.y();
        double len = Math.hypot(dx, dy);
        if (len < 1e-6) return false;
        double nx = -dy / len, ny = dx / len;
        // Inset scales with segment length so short vertical steps still probe holes
        double inset = Math.min(len * 0.1, Math.max(pitch * 0.15, 0.5));
        // Dense sampling — 12 points along, both perpendicular sides
        for (int i = 1; i < 12; i++) {
            double t = i / 12.0;
            double mx = a.x() + t * dx;
            double my = a.y() + t * dy;
            for (double sign : new double[]{1, -1, 0}) {
                Point2D test = new Point2D(mx + sign * nx * inset, my + sign * ny * inset);
                for (Polyline hole : poly.getHoles()) {
                    if (pointInPolyline(test, hole)) return true;
                }
            }
        }
        return false;
    }

    private boolean pointInPolyline(Point2D pt, Polyline pl) {
        List<Point2D> pts = pl.getPoints();
        int n = pts.size();
        if (n > 2 && pts.get(0).dist(pts.get(n - 1)) < 1e-9) n--;
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = pts.get(i).x(), yi = pts.get(i).y();
            double xj = pts.get(j).x(), yj = pts.get(j).y();
            if ((yi > pt.y()) != (yj > pt.y()) &&
                pt.x() < (xj - xi) * (pt.y() - yi) / (yj - yi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    private List<Double> intersectionsAtY(PolygonWithHoles poly, double y) {
        List<Double> xs = new ArrayList<>();
        addIx(poly.getOuter(), y, xs);
        for (Polyline h : poly.getHoles()) addIx(h, y, xs);
        return xs;
    }

    private void addIx(Polyline pl, double y, List<Double> xs) {
        List<Point2D> pts = pl.getPoints();
        int n = pts.size();
        if (n > 2 && pts.get(0).dist(pts.get(n - 1)) < 1e-9) n--;
        if (n < 3) return;

        for (int i = 0; i < n; i++) {
            Point2D a = pts.get(i), b = pts.get((i + 1) % n);
            double ay = a.y(), by = b.y();
            // Skip horizontal edges — they don't cross the scanline
            if (ay == by) continue;
            // Robust half-open rule: edge covers [min, max) of y.
            // At a vertex shared by two edges, exactly one of them owns that y,
            // so vertices are counted correctly without special-casing.
            double lo = Math.min(ay, by);
            double hi = Math.max(ay, by);
            if (y < lo || y >= hi) continue;
            double t = (y - ay) / (by - ay);
            xs.add(a.x() + t * (b.x() - a.x()));
        }
    }

    private static Polyline rotPl(Polyline pl, double rad) {
        Polyline r = new Polyline(); double c=Math.cos(rad), s=Math.sin(rad);
        for (Point2D p : pl.getPoints()) r.addPoint(new Point2D(p.x()*c-p.y()*s, p.x()*s+p.y()*c));
        return r;
    }
    private static PolygonWithHoles rotatePoly(PolygonWithHoles poly, double rad) {
        PolygonWithHoles r = new PolygonWithHoles(rotPl(poly.getOuter(), rad));
        for (Polyline h : poly.getHoles()) r.addHole(rotPl(h, rad));
        return r;
    }
}
