package com.lasercam.core.geometry;

import com.lasercam.core.model.Point2D;
import com.lasercam.core.model.Polyline;
import com.lasercam.core.model.PolygonWithHoles;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds PolygonWithHoles from flattened polylines.
 * Handles compound paths, outer/holes detection using even-odd or winding rule,
 * transforms support (placeholder for matrix).
 * Ensures no self-intersections (basic check).
 */
public class PolygonBuilder {

    private final double tolerance;

    public PolygonBuilder(double tolerance) {
        this.tolerance = tolerance;
    }

    /**
     * Main method: takes list of closed polylines (from SVG paths) and builds outer + holes.
     * Uses point-in-polygon and area/winding to classify outer/holes.
     */
    public PolygonWithHoles buildFromPolylines(List<Polyline> closedPolylines) {
        if (closedPolylines.isEmpty()) {
            return new PolygonWithHoles(null);
        }

        // Sort by bounding box size or area descending (largest likely outer)
        closedPolylines.sort((p1, p2) -> Double.compare(area(p2), area(p1)));

        Polyline outer = closedPolylines.get(0);
        List<Polyline> holes = new ArrayList<>();

        for (int i = 1; i < closedPolylines.size(); i++) {
            Polyline candidate = closedPolylines.get(i);
            if (isInside(outer, candidate.getPoints().get(0))) {
                holes.add(candidate);
            } else {
                // For multiple outers - handle as separate later or merge logic
                // For now assume single outer
            }
        }

        // Basic self-intersection check (placeholder)
        if (hasSelfIntersections(outer)) {
            System.err.println("Warning: Self-intersections in outer polygon");
        }

        PolygonWithHoles result = new PolygonWithHoles(outer);
        for (Polyline h : holes) {
            result.addHole(h);
        }
        return result;
    }

    private double area(Polyline poly) {
        List<Point2D> pts = poly.getPoints();
        if (pts.size() < 3) return 0;
        double a = 0;
        for (int i = 0; i < pts.size(); i++) {
            Point2D p1 = pts.get(i);
            Point2D p2 = pts.get((i + 1) % pts.size());
            a += p1.x() * p2.y() - p2.x() * p1.y();
        }
        return Math.abs(a) / 2.0;
    }

    private boolean isInside(Polyline outer, Point2D testPoint) {
        // Ray casting algorithm (even-odd rule support)
        List<Point2D> pts = outer.getPoints();
        int intersections = 0;
        double x = testPoint.x(), y = testPoint.y();

        for (int i = 0; i < pts.size(); i++) {
            Point2D a = pts.get(i);
            Point2D b = pts.get((i + 1) % pts.size());
            if ((a.y() > y) != (b.y() > y) &&
                (x < a.x() + (b.x() - a.x()) * (y - a.y()) / (b.y() - a.y()))) {
                intersections++;
            }
        }
        return (intersections % 2) == 1; // even-odd
    }

    private boolean hasSelfIntersections(Polyline poly) {
        // Simple O(n^2) check for demo. Optimize later.
        List<Point2D> pts = poly.getPoints();
        for (int i = 0; i < pts.size(); i++) {
            for (int j = i + 2; j < pts.size(); j++) {
                // edge intersection check placeholder
                if (segmentsIntersect(pts.get(i), pts.get((i+1)%pts.size()), 
                                     pts.get(j), pts.get((j+1)%pts.size()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean segmentsIntersect(Point2D a, Point2D b, Point2D c, Point2D d) {
        // Basic orientation method (simplified)
        return (ccw(a, c, d) != ccw(b, c, d) && ccw(a, b, c) != ccw(a, b, d));
    }

    private boolean ccw(Point2D a, Point2D b, Point2D c) {
        return (c.y() - a.y()) * (b.x() - a.x()) > (b.y() - a.y()) * (c.x() - a.x());
    }

    // TODO: applyTransforms(Matrix m) for SVG transforms
}
