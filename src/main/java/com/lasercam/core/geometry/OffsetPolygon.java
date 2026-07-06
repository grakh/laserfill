package com.lasercam.core.geometry;

import com.lasercam.core.model.Point2D;
import com.lasercam.core.model.Polyline;

import java.util.ArrayList;
import java.util.List;

/**
 * Polygon offset by parallel edges + intersection.
 * Each edge is shifted perpendicular by `distance` on the outside (per winding),
 * then adjacent shifted edges are intersected to form new vertices.
 * Handles both CW and CCW input. Miter limit avoids infinite spikes at sharp corners.
 *
 * Positive distance = outward, negative = inward (relative to winding).
 */
public class OffsetPolygon {

    private static final double MITER_LIMIT = 5.0;

    public Polyline offset(Polyline original, double distance) {
        if (original == null) return null;
        List<Point2D> pts = original.getPoints();

        // Strip closing duplicate
        if (pts.size() >= 2 && pts.get(0).dist(pts.get(pts.size() - 1)) < 1e-6) {
            pts = new ArrayList<>(pts.subList(0, pts.size() - 1));
        }
        if (pts.size() < 3) return null;

        // Detect winding sign — positive area = CCW
        double area = signedArea(pts);
        // For CCW: normal pointing left of edge is outside; we want +distance to go outside
        // For CW: normal to the right is outside
        double sign = area > 0 ? 1.0 : -1.0;

        int n = pts.size();

        // Compute shifted edges: for each edge (i → i+1) get its parallel offset line
        // represented as (point_on_line, direction). Both shifted by `distance` along the
        // outward normal (sign-adjusted).
        double[][] edges = new double[n][4]; // [px, py, dx, dy] for shifted edge start + direction
        for (int i = 0; i < n; i++) {
            Point2D a = pts.get(i);
            Point2D b = pts.get((i + 1) % n);
            double dx = b.x() - a.x(), dy = b.y() - a.y();
            double len = Math.hypot(dx, dy);
            if (len < 1e-10) { edges[i] = null; continue; }
            // Outward normal (rotate edge dir -90° for CCW is outward)
            double nx = -dy / len * sign;
            double ny =  dx / len * sign;
            edges[i][0] = a.x() + nx * distance;
            edges[i][1] = a.y() + ny * distance;
            edges[i][2] = dx / len;
            edges[i][3] = dy / len;
        }

        // Compute new vertices by intersecting shifted edge i-1 with edge i
        List<Point2D> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double[] e1 = edges[(i - 1 + n) % n];
            double[] e2 = edges[i];
            if (e1 == null || e2 == null) continue;
            Point2D ip = intersectLines(e1[0], e1[1], e1[2], e1[3],
                                        e2[0], e2[1], e2[2], e2[3]);
            if (ip == null) {
                // Parallel — just use shifted start of edge2
                ip = new Point2D(e2[0], e2[1]);
            }
            // Miter limit: cap distance from original vertex
            Point2D orig = pts.get(i);
            double d = orig.dist(ip);
            double maxD = Math.abs(distance) * MITER_LIMIT;
            if (d > maxD) {
                double t = maxD / d;
                ip = new Point2D(orig.x() + (ip.x() - orig.x()) * t,
                                 orig.y() + (ip.y() - orig.y()) * t);
            }
            result.add(ip);
        }

        if (result.size() < 3) return null;

        // Remove degenerate self-intersections (invalid polygon after inset)
        // If new signed area sign flipped, the polygon collapsed
        double newArea = signedArea(result);
        if (Math.signum(newArea) != Math.signum(area)) return null;

        // Remove closely spaced duplicate points
        List<Point2D> cleaned = new ArrayList<>();
        double minSpacing = Math.abs(distance) * 0.05;
        for (Point2D p : result) {
            if (!cleaned.isEmpty() && cleaned.get(cleaned.size() - 1).dist(p) < minSpacing) continue;
            cleaned.add(p);
        }
        if (cleaned.size() < 3) return null;

        Polyline out = new Polyline();
        for (Point2D p : cleaned) out.addPoint(p);
        out.addPoint(cleaned.get(0)); // close
        return out;
    }

    /**
     * Intersect two lines defined by (point, direction).
     * Returns null if parallel.
     */
    private Point2D intersectLines(double p1x, double p1y, double d1x, double d1y,
                                    double p2x, double p2y, double d2x, double d2y) {
        double denom = d1x * d2y - d1y * d2x;
        if (Math.abs(denom) < 1e-10) return null;
        double t = ((p2x - p1x) * d2y - (p2y - p1y) * d2x) / denom;
        return new Point2D(p1x + d1x * t, p1y + d1y * t);
    }

    private double signedArea(List<Point2D> pts) {
        double a = 0;
        for (int i = 0; i < pts.size(); i++) {
            Point2D p1 = pts.get(i), p2 = pts.get((i + 1) % pts.size());
            a += p1.x() * p2.y() - p2.x() * p1.y();
        }
        return a / 2.0;
    }
}
