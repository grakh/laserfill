package com.lasercam.core.model;

public record Point2D(double x, double y) {
    public static final double EPS = 1e-6;

    public double dist(Point2D other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public Point2D add(Point2D other) {
        return new Point2D(x + other.x, y + other.y);
    }

    public Point2D subtract(Point2D other) {
        return new Point2D(x - other.x, y - other.y);
    }

    public Point2D multiply(double scalar) {
        return new Point2D(x * scalar, y * scalar);
    }

    /**
     * Apply affine transform (scale, rotate, translate, shear)
     */
    public Point2D transform(java.awt.geom.AffineTransform at) {
        double[] src = {this.x, this.y};
        double[] dst = new double[2];
        at.transform(src, 0, dst, 0, 1);
        return new Point2D(dst[0], dst[1]);
    }
}