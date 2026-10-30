package com.lasercam.core.raster;

import com.lasercam.core.model.Point2D;
import com.lasercam.core.model.Toolpath;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Grayscale image → laser raster toolpath with variable power.
 *
 * The image is resampled so one pixel = one line step (lineStep × lineStep mm),
 * then scanned row by row in a serpentine. Darkness of each pixel maps to power:
 *     power = minPower + darkness × (maxPower − minPower)
 * so the darkest point gets maxPower. White pixels (and transparent ones) are not
 * burned. Neighbouring pixels with the same power are merged into one segment.
 *
 * Inside a line, white gaps and the overscan run-in/run-out are emitted as cut
 * segments with power 0: the head keeps cutting speed (no acceleration marks at
 * the edges) while the laser is off. Moves between lines are travels.
 *
 * Image origin is at (0,0), Y grows downward like the SVG import.
 */
public class RasterEngraver {

    private double widthMm = 100;
    private double lineStep = 0.1;     // mm between lines and between pixels along a line
    private double minPower = 0;
    private double maxPower = 255;
    private double gamma = 1.0;        // < 1 lifts light tones, > 1 deepens dark ones
    private boolean invert = false;
    private boolean dither = false;    // Floyd–Steinberg: only on/off at maxPower
    private double overscan = 2.0;     // mm run-in / run-out on each line

    public void setWidthMm(double v)  { widthMm = v; }
    public void setLineStep(double v) { lineStep = v; }
    public void setMinPower(double v) { minPower = v; }
    public void setMaxPower(double v) { maxPower = v; }
    public void setGamma(double v)    { gamma = v; }
    public void setInvert(boolean v)  { invert = v; }
    public void setDither(boolean v)  { dither = v; }
    public void setOverscan(double v) { overscan = v; }

    /** Height in mm for a given image at the configured width (keeps aspect ratio). */
    public double heightMm(BufferedImage img) {
        return widthMm * img.getHeight() / (double) img.getWidth();
    }

    public Toolpath generate(BufferedImage img) {
        int cols = Math.max(1, (int) Math.round(widthMm / lineStep));
        int rows = Math.max(1, (int) Math.round(heightMm(img) / lineStep));
        double[][] dark = darkness(resample(img, cols, rows), cols, rows);

        Toolpath tp = new Toolpath();
        Point2D head = null;
        boolean leftToRight = true;

        for (int r = 0; r < rows; r++) {
            int[] s = new int[cols];         // integer power per pixel, 0 = off
            int first = -1, last = -1;
            for (int c = 0; c < cols; c++) {
                double d = dark[r][c];
                if (d <= 0.5 / 255) continue; // white
                s[c] = (int) Math.round(minPower + d * (maxPower - minPower));
                if (s[c] <= 0) continue;
                if (first < 0) first = c;
                last = c;
            }
            if (first < 0) continue;          // empty line — skip it entirely

            double y = (r + 0.5) * lineStep;
            double xFirst = first * lineStep, xLast = (last + 1) * lineStep;
            double runIn  = leftToRight ? xFirst - overscan : xLast + overscan;
            double runOut = leftToRight ? xLast + overscan : xFirst - overscan;

            Point2D start = new Point2D(runIn, y);
            if (head != null && head.dist(start) > 1e-6) tp.addTravelSegment(head, start);

            // run-in, laser off
            Point2D cur = new Point2D(leftToRight ? xFirst : xLast, y);
            if (overscan > 0) tp.addCutSegment(start, cur, 0);

            // merged runs of equal power
            int c = leftToRight ? first : last;
            int step = leftToRight ? 1 : -1;
            int end = leftToRight ? last + 1 : first - 1;
            while (c != end) {
                int p = s[c];
                int k = c;
                while (k + step != end && s[k + step] == p) k += step;
                double xe = leftToRight ? (k + 1) * lineStep : k * lineStep;
                Point2D next = new Point2D(xe, y);
                tp.addCutSegment(cur, next, p);
                cur = next;
                c = k + step;
            }

            // run-out, laser off
            Point2D out = new Point2D(runOut, y);
            if (overscan > 0) tp.addCutSegment(cur, out, 0);
            head = overscan > 0 ? out : cur;
            leftToRight = !leftToRight;
        }
        return tp;
    }

    /** Area-averaged resample to exactly cols × rows; transparency becomes white. */
    private BufferedImage resample(BufferedImage src, int cols, int rows) {
        BufferedImage flat = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = flat.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, flat.getWidth(), flat.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();

        java.awt.Image scaled = flat.getScaledInstance(cols, rows, java.awt.Image.SCALE_AREA_AVERAGING);
        BufferedImage out = new BufferedImage(cols, rows, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(scaled, 0, 0, null);
        g2.dispose();
        return out;
    }

    /** Darkness 0 (white) … 1 (black) after invert, gamma and optional dithering. */
    private double[][] darkness(BufferedImage img, int cols, int rows) {
        double[][] d = new double[rows][cols];
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                int rgb = img.getRGB(c, r);
                double lum = 0.299 * ((rgb >> 16) & 0xff) + 0.587 * ((rgb >> 8) & 0xff) + 0.114 * (rgb & 0xff);
                double v = 1.0 - lum / 255.0;
                if (invert) v = 1.0 - v;
                if (gamma > 0 && Math.abs(gamma - 1.0) > 1e-6) v = Math.pow(v, gamma);
                d[r][c] = v;
            }
        if (dither) floydSteinberg(d, cols, rows);
        return d;
    }

    private void floydSteinberg(double[][] d, int cols, int rows) {
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                double old = d[r][c];
                double nw = old >= 0.5 ? 1.0 : 0.0;
                d[r][c] = nw;
                double e = old - nw;
                if (c + 1 < cols)             d[r][c + 1]     += e * 7 / 16;
                if (r + 1 < rows) {
                    if (c > 0)                d[r + 1][c - 1] += e * 3 / 16;
                                              d[r + 1][c]     += e * 5 / 16;
                    if (c + 1 < cols)         d[r + 1][c + 1] += e * 1 / 16;
                }
            }
    }
}
