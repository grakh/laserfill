package com.lasercam.core.geometry;

import com.lasercam.core.model.Point2D;
import com.lasercam.core.model.Polyline;

import java.util.ArrayList;
import java.util.List;

/**
 * Tolerance-based Bezier flattening (quadratic and cubic).
 * Converts SVG Path Beziers to polyline segments.
 */
public class BezierFlattener {

    private final double tolerance;

    public BezierFlattener(double tolerance) {
        this.tolerance = tolerance;
    }

    public Polyline flattenCubic(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
        Polyline poly = new Polyline();
        flattenCubicRecursive(poly, p0, p1, p2, p3, 0);
        poly.addPoint(p3); // ensure endpoint
        return poly;
    }

    private void flattenCubicRecursive(Polyline poly, Point2D p0, Point2D p1, Point2D p2, Point2D p3, int depth) {
        if (depth > 10) { // prevent infinite recursion
            poly.addPoint(p0);
            return;
        }

        // Check flatness
        if (isFlat(p0, p1, p2, p3)) {
            poly.addPoint(p0);
            return;
        }

        // Subdivide
        Point2D p01 = midpoint(p0, p1);
        Point2D p12 = midpoint(p1, p2);
        Point2D p23 = midpoint(p2, p3);
        Point2D p012 = midpoint(p01, p12);
        Point2D p123 = midpoint(p12, p23);
        Point2D p0123 = midpoint(p012, p123);

        flattenCubicRecursive(poly, p0, p01, p012, p0123, depth + 1);
        flattenCubicRecursive(poly, p0123, p123, p23, p3, depth + 1);
    }

    private boolean isFlat(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
        double d1 = distanceToLine(p1, p0, p3);
        double d2 = distanceToLine(p2, p0, p3);
        return (d1 + d2) < tolerance;
    }

    private double distanceToLine(Point2D p, Point2D a, Point2D b) {
        Point2D ab = b.subtract(a);
        Point2D ap = p.subtract(a);
        double proj = ap.x() * ab.x() + ap.y() * ab.y();
        double len2 = ab.x() * ab.x() + ab.y() * ab.y();
        if (len2 == 0) return ap.dist(new Point2D(0,0));
        double t = proj / len2;
        t = Math.max(0, Math.min(1, t));
        Point2D closest = a.add(ab.multiply(t));
        return p.dist(closest);
    }

    private Point2D midpoint(Point2D a, Point2D b) {
        return new Point2D((a.x() + b.x()) / 2, (a.y() + b.y()) / 2);
    }

    // Similar for quadratic Bezier (can be extended)
    public Polyline flattenQuadratic(Point2D p0, Point2D p1, Point2D p2) {
        // Convert quadratic to cubic or implement directly
        Point2D c1 = new Point2D(p0.x() + (2.0/3.0)*(p1.x() - p0.x()), p0.y() + (2.0/3.0)*(p1.y() - p0.y()));
        Point2D c2 = new Point2D(p2.x() + (2.0/3.0)*(p1.x() - p2.x()), p2.y() + (2.0/3.0)*(p1.y() - p2.y()));
        return flattenCubic(p0, c1, c2, p2);
    }
}