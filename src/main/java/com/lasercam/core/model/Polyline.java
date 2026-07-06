package com.lasercam.core.model;

import java.util.ArrayList;
import java.util.List;

public class Polyline {
    private final List<Point2D> points = new ArrayList<>();

    public void addPoint(Point2D p) {
        points.add(p);
    }

    public List<Point2D> getPoints() {
        return new ArrayList<>(points); // defensive copy
    }

    public double getMinY() {
        return points.stream().mapToDouble(Point2D::y).min().orElse(0);
    }

    public double getMaxY() {
        return points.stream().mapToDouble(Point2D::y).max().orElse(0);
    }

    public double getMinX() {
        return points.stream().mapToDouble(Point2D::x).min().orElse(0);
    }

    public double getMaxX() {
        return points.stream().mapToDouble(Point2D::x).max().orElse(0);
    }

    /**
     * Applies affine transform to all points in place (for SVG transforms)
     */
    public void applyTransform(java.awt.geom.AffineTransform at) {
        for (int i = 0; i < points.size(); i++) {
            Point2D p = points.get(i);
            points.set(i, p.transform(at));
        }
    }
}