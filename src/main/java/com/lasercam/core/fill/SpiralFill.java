package com.lasercam.core.fill;

import com.lasercam.core.geometry.OffsetPolygon;
import com.lasercam.core.model.Point2D;
import com.lasercam.core.model.PolygonWithHoles;
import com.lasercam.core.model.Polyline;
import com.lasercam.core.model.Toolpath;

import java.util.ArrayList;
import java.util.List;

/**
 * Archimedean spiral fill.
 * Generates one continuous outward spiral from the centroid until it reaches
 * the outer boundary. Radius grows linearly with angle so line spacing = pitch.
 * Holes are handled by mode:
 *  - JUMP: laser OFF over the hole (TRAVEL), then ON again
 *  - CONTOUR: (fallback) contour-offset spiral (old behavior)
 */
public class SpiralFill implements FillStrategy {

    public enum HoleMode { JUMP, CONTOUR }

    private double pitch;
    private double kerf = 0;
    private HoleMode holeMode = HoleMode.JUMP;

    public SpiralFill(double pitch) { this.pitch = pitch; }
    public void setPitch(double p) { this.pitch = p; }
    public void setKerf(double k) { this.kerf = k; }
    public void setHoleMode(HoleMode m) { this.holeMode = m; }
    public double getPitch() { return pitch; }
    public double getKerf() { return kerf; }
    public HoleMode getHoleMode() { return holeMode; }

    @Override
    public Toolpath generate(PolygonWithHoles polygon) {
        if (polygon.getOuter() == null) return new Toolpath();
        if (holeMode == HoleMode.CONTOUR) return generateContourSpiral(polygon);
        return generateArchimedean(polygon);
    }

    // ══════════════════════════════════════════
    //  ARCHIMEDEAN SPIRAL
    // ══════════════════════════════════════════

    private Toolpath generateArchimedean(PolygonWithHoles polygon) {
        Toolpath tp = new Toolpath();

        // Find centroid of outer polygon
        double[] c = centroid(polygon.getOuter().getPoints());
        double cx = c[0], cy = c[1];

        // Estimate max radius — spiral must reach the farthest point on the boundary.
        // For rectangular shapes this means the corners (not just closest edge).
        double maxR = 0;
        for (Point2D p : polygon.getOuter().getPoints()) {
            double d = Math.hypot(p.x() - cx, p.y() - cy);
            if (d > maxR) maxR = d;
        }
        // Add extra margin to ensure last winding covers the corners
        maxR += pitch * 2;

        // Archimedean spiral: r = (pitch / 2π) * θ
        // Line spacing = pitch. Θ goes from 0 to θ_max.
        // Sample angular step small enough to keep chord < ~pitch/5
        double a = pitch / (2 * Math.PI);

        // Build spiral points, then process to CUT/TRAVEL based on hole crossings
        Point2D prev = null;
        boolean prevInside = false;

        double theta = 0;
        while (true) {
            double r = a * theta;
            if (r > maxR) break;
            double x = cx + r * Math.cos(theta);
            double y = cy + r * Math.sin(theta);
            Point2D cur = new Point2D(x, y);

            boolean curInside = isInside(cur, polygon);

            if (prev != null) {
                if (prevInside && curInside) {
                    tp.addCutSegment(prev, cur);
                } else if (prevInside && !curInside) {
                    // Cross the outer boundary going out — clip cut to boundary
                    Point2D bp = clipToPolygonBoundary(prev, cur, polygon);
                    if (bp != null) tp.addCutSegment(prev, bp);
                    else tp.addCutSegment(prev, cur);
                } else if (!prevInside && curInside) {
                    // Entering material — travel dashed line to boundary, then cut
                    Point2D bp = clipToPolygonBoundary(cur, prev, polygon);
                    if (bp != null) tp.addTravelSegment(prev, bp);
                    else tp.addTravelSegment(prev, cur);
                }
                // both outside: skip (no segment)
            }

            prev = cur;
            prevInside = curInside;

            // Angular step: chord ≈ pitch/3 — smooth but not overkill
            double stepR = Math.max(pitch / 3, 1.0);
            double dtheta = stepR / Math.max(r, pitch);
            if (dtheta > 0.3) dtheta = 0.3;
            theta += dtheta;
        }
        return tp;
    }

    /**
     * Binary-search along segment (inside → outside) for the boundary crossing point.
     * Returns the point where isInside changes from true to false (or vice versa).
     */
    private Point2D clipToPolygonBoundary(Point2D inside, Point2D outside, PolygonWithHoles poly) {
        Point2D a = inside, b = outside;
        for (int i = 0; i < 20; i++) {
            Point2D mid = new Point2D((a.x() + b.x()) / 2, (a.y() + b.y()) / 2);
            if (isInside(mid, poly)) a = mid;
            else b = mid;
        }
        return a;
    }

    private boolean isInside(Point2D pt, PolygonWithHoles poly) {
        // Inside outer
        if (!pointInPoly(pt, poly.getOuter())) return false;
        // Not inside any hole (with kerf expansion)
        for (Polyline hole : poly.getHoles()) {
            if (pointInPoly(pt, hole)) return false;
            if (kerf > 0 && distToPolyline(pt, hole) < kerf) return false;
        }
        // Also respect kerf from outer boundary
        if (kerf > 0 && distToPolyline(pt, poly.getOuter()) < kerf) return false;
        return true;
    }

    private boolean pointInPoly(Point2D pt, Polyline pl) {
        List<Point2D> pts = pl.getPoints();
        int n = pts.size();
        if (n > 2 && pts.get(0).dist(pts.get(n - 1)) < 1e-9) n--;
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = pts.get(i).x(), yi = pts.get(i).y();
            double xj = pts.get(j).x(), yj = pts.get(j).y();
            if ((yi > pt.y()) != (yj > pt.y()) &&
                pt.x() < (xj - xi) * (pt.y() - yi) / (yj - yi) + xi)
                inside = !inside;
        }
        return inside;
    }

    private double distToPolyline(Point2D pt, Polyline pl) {
        List<Point2D> pts = pl.getPoints();
        int n = pts.size();
        if (n > 2 && pts.get(0).dist(pts.get(n - 1)) < 1e-9) n--;
        double minD = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            Point2D a = pts.get(i), b = pts.get((i + 1) % n);
            double d = distToSegment(pt, a, b);
            if (d < minD) minD = d;
        }
        return minD;
    }

    private double distToSegment(Point2D p, Point2D a, Point2D b) {
        double dx = b.x() - a.x(), dy = b.y() - a.y();
        double len2 = dx * dx + dy * dy;
        if (len2 < 1e-12) return p.dist(a);
        double t = ((p.x() - a.x()) * dx + (p.y() - a.y()) * dy) / len2;
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(p.x() - (a.x() + t * dx), p.y() - (a.y() + t * dy));
    }

    private double[] centroid(List<Point2D> pts) {
        int n = pts.size();
        if (n > 2 && pts.get(0).dist(pts.get(n - 1)) < 1e-9) n--;
        double cx = 0, cy = 0, aSum = 0;
        for (int i = 0; i < n; i++) {
            Point2D p1 = pts.get(i), p2 = pts.get((i + 1) % n);
            double a = p1.x() * p2.y() - p2.x() * p1.y();
            cx += (p1.x() + p2.x()) * a;
            cy += (p1.y() + p2.y()) * a;
            aSum += a;
        }
        if (Math.abs(aSum) < 1e-9) {
            // fallback: average
            double x = 0, y = 0;
            for (int i = 0; i < n; i++) { x += pts.get(i).x(); y += pts.get(i).y(); }
            return new double[]{x / n, y / n};
        }
        return new double[]{cx / (3 * aSum), cy / (3 * aSum)};
    }

    // ══════════════════════════════════════════
    //  CONTOUR SPIRAL (concentric offset rings — fallback)
    // ══════════════════════════════════════════

    private Toolpath generateContourSpiral(PolygonWithHoles polygon) {
        Toolpath tp = new Toolpath();
        OffsetPolygon off = new OffsetPolygon();
        double outerArea = Math.abs(signedArea(polygon.getOuter()));

        List<Polyline> contours = new ArrayList<>();
        int maxIter = Math.max(20, (int)(Math.min(
                polygon.getOuter().getMaxX() - polygon.getOuter().getMinX(),
                polygon.getOuter().getMaxY() - polygon.getOuter().getMinY()) / (pitch * 2)) + 5);

        // Outer inward
        Polyline cur = polygon.getOuter();
        if (kerf > 0) { Polyline inset = off.offset(cur, -kerf); if (inset != null) cur = inset; }
        for (int i = 0; i < maxIter && cur != null; i++) {
            contours.add(cur);
            Polyline next = off.offset(cur, -pitch);
            if (next == null || next.getPoints().size() < 4) break;
            if (Math.abs(signedArea(next)) < pitch * pitch * 0.5) break;
            cur = next;
        }

        // Holes outward
        for (Polyline hole : polygon.getHoles()) {
            Polyline hcur = hole;
            if (kerf > 0) { Polyline exp = off.offset(hcur, kerf); if (exp != null) hcur = exp; }
            for (int i = 0; i < maxIter && hcur != null; i++) {
                contours.add(hcur);
                Polyline next = off.offset(hcur, pitch);
                if (next == null || next.getPoints().size() < 4) break;
                if (Math.abs(signedArea(next)) > outerArea * 0.95) break;
                hcur = next;
            }
        }

        // Emit contours
        Point2D last = null;
        for (Polyline c : contours) {
            List<Point2D> pts = c.getPoints();
            if (pts.size() < 2) continue;
            if (last != null && last.dist(pts.get(0)) > 0.05)
                tp.addTravelSegment(last, pts.get(0));
            for (int i = 1; i < pts.size(); i++) tp.addCutSegment(pts.get(i - 1), pts.get(i));
            last = pts.get(pts.size() - 1);
        }
        return tp;
    }

    private double signedArea(Polyline pl) {
        List<Point2D> pts = pl.getPoints();
        double a = 0;
        for (int i = 0; i < pts.size(); i++) {
            Point2D p1 = pts.get(i), p2 = pts.get((i + 1) % pts.size());
            a += p1.x() * p2.y() - p2.x() * p1.y();
        }
        return a / 2.0;
    }
}
