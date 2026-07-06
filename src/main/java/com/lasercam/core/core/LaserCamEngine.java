package com.lasercam.core.core;

import com.lasercam.core.fill.FillStrategy;
import com.lasercam.core.fill.ScanlineFill;
import com.lasercam.core.fill.SpiralFill;
import com.lasercam.core.geometry.PolygonBuilder;
import com.lasercam.core.model.Point2D;
import com.lasercam.core.model.PolygonWithHoles;
import com.lasercam.core.model.Polyline;
import com.lasercam.core.model.Toolpath;
import com.lasercam.core.optimizer.ToolpathOptimizer;
import com.lasercam.core.io.DxfExporterR2000;
import com.lasercam.core.io.GcodeExporter;
import com.lasercam.core.io.PdfImporter;
import com.lasercam.core.io.SvgImporter;
import com.lasercam.core.io.ToolpathSvgPreview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LaserCamEngine {
    private FillStrategy fillStrategy;

    public LaserCamEngine() {
        this.fillStrategy = new ScanlineFill(5.0); // default pitch
    }

    public LaserCamEngine(FillStrategy initialStrategy) {
        this.fillStrategy = initialStrategy;
    }

    public void setFillStrategy(FillStrategy strategy) {
        this.fillStrategy = strategy;
    }

    public Toolpath fillPolygon(PolygonWithHoles polygon, double pitch) {
        if (fillStrategy instanceof ScanlineFill s) {
            s.setPitch(pitch);
        } else if (fillStrategy instanceof SpiralFill s) {
            s.setPitch(pitch);
        }
        Toolpath raw = fillStrategy.generate(polygon);
        return optimizeToolpath(raw);
    }

    public PolygonWithHoles buildPolygon(List<Polyline> closedPolylines) {
        PolygonBuilder builder = new PolygonBuilder(0.05);
        return builder.buildFromPolylines(closedPolylines);
    }

    public Toolpath optimizeToolpath(Toolpath toolpath) {
        ToolpathOptimizer optimizer = new ToolpathOptimizer();
        return optimizer.optimize(toolpath);
    }

    public void exportToDxf(Toolpath toolpath, String filePath, boolean includeTravel) throws IOException {
        DxfExporterR2000 exporter = new DxfExporterR2000();
        exporter.export(toolpath, filePath, includeTravel);
    }

    public void exportToGcode(Toolpath toolpath, String filePath, double feedRateCut, double feedRateTravel, double power) throws IOException {
        GcodeExporter exporter = new GcodeExporter("GRBL");
        exporter.export(toolpath, filePath, feedRateCut, feedRateTravel, power);
    }

    public List<Polyline> importSvg(String filePath) throws Exception {
        SvgImporter importer = new SvgImporter(0.05);
        return importer.importSvg(filePath);
    }

    public List<Polyline> importPdf(String filePath) throws Exception {
        PdfImporter importer = new PdfImporter(0.05);
        return importer.importPdf(filePath);
    }

    /**
     * Import any supported file (SVG or PDF).
     */
    public List<Polyline> importFile(String filePath) throws Exception {
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".pdf")) return importPdf(filePath);
        return importSvg(filePath);
    }

    /**
     * Full pipeline for any supported file.
     */
    public ProcessResult processFileFull(String filePath, double pitch) throws Exception {
        List<Polyline> polylines = importFile(filePath);
        List<PolygonWithHoles> polygons = buildAllPolygons(polylines);
        Toolpath combined = new Toolpath();
        for (PolygonWithHoles poly : polygons) {
            Toolpath tp = fillStrategy.generate(poly);
            for (var seg : tp.getSegments()) combined.addSegment(seg);
        }
        // Fill strategies produce continuous/ordered paths; don't optimize
        return new ProcessResult(combined, polygons);
    }

    /**
     * Full pipeline: SVG -> flatten -> build polygon -> fill -> optimize
     */
    public Toolpath processSvg(String svgPath, double pitch) throws Exception {
        List<Polyline> polylines = importSvg(svgPath);
        PolygonWithHoles polygon = buildPolygon(polylines);
        return fillPolygon(polygon, pitch);
    }

    /**
     * Full pipeline returning both toolpath and polygons (for viewer).
     */
    public ProcessResult processSvgFull(String svgPath, double pitch) throws Exception {
        List<Polyline> polylines = importSvg(svgPath);
        List<PolygonWithHoles> polygons = buildAllPolygons(polylines);
        Toolpath combined = new Toolpath();
        for (PolygonWithHoles poly : polygons) {
            Toolpath tp = fillStrategy.generate(poly);
            for (var seg : tp.getSegments()) combined.addSegment(seg);
        }
        Toolpath optimized = optimizeToolpath(combined);
        return new ProcessResult(optimized, polygons);
    }

    /**
     * Build multiple PolygonWithHoles from polylines, using nesting depth:
     * - Depth 0 (outermost) → outer of a new polygon
     * - Depth 1 (inside outer) → hole of that polygon
     * - Depth 2 (inside a hole) → outer of a NEW polygon (island)
     * - Depth 3 → hole of the island polygon, etc.
     */
    public List<PolygonWithHoles> buildAllPolygons(List<Polyline> closedPolylines) {
        List<PolygonWithHoles> result = new ArrayList<>();
        if (closedPolylines.isEmpty()) return result;

        // Precompute area and a representative point for each polyline
        int n = closedPolylines.size();
        double[] areas = new double[n];
        Point2D[] reps = new Point2D[n];
        for (int i = 0; i < n; i++) {
            areas[i] = polyArea(closedPolylines.get(i));
            reps[i] = closedPolylines.get(i).getPoints().get(0);
        }

        // Compute depth: how many other polylines contain this one
        // (a polyline is inside another iff the other polyline's area is larger
        //  AND the representative point of this one is inside the other)
        int[] depth = new int[n];
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            int d = 0, best = -1;
            double bestArea = Double.MAX_VALUE;
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                if (areas[j] <= areas[i]) continue;
                if (pointInPolyline(reps[i], closedPolylines.get(j))) {
                    d++;
                    // Direct parent = smallest containing polygon
                    if (areas[j] < bestArea) { bestArea = areas[j]; best = j; }
                }
            }
            depth[i] = d;
            parent[i] = best;
        }

        // Even depth = outer (start new polygon); odd depth = hole of parent
        // Map from outer polygon index → its position in result list
        java.util.Map<Integer, Integer> outerToResult = new java.util.HashMap<>();
        for (int i = 0; i < n; i++) {
            if (depth[i] % 2 == 0) {
                PolygonWithHoles p = new PolygonWithHoles(closedPolylines.get(i));
                outerToResult.put(i, result.size());
                result.add(p);
            }
        }
        for (int i = 0; i < n; i++) {
            if (depth[i] % 2 == 1) {
                Integer pos = outerToResult.get(parent[i]);
                if (pos != null) result.get(pos).addHole(closedPolylines.get(i));
            }
        }
        return result;
    }

    private double polyArea(Polyline pl) {
        List<Point2D> pts = pl.getPoints();
        int m = pts.size();
        if (m > 2 && pts.get(0).dist(pts.get(m - 1)) < 1e-9) m--;
        if (m < 3) return 0;
        double a = 0;
        for (int i = 0; i < m; i++) {
            Point2D p1 = pts.get(i), p2 = pts.get((i + 1) % m);
            a += p1.x() * p2.y() - p2.x() * p1.y();
        }
        return Math.abs(a) / 2.0;
    }

    private boolean pointInPolyline(Point2D pt, Polyline pl) {
        List<Point2D> pts = pl.getPoints();
        int m = pts.size();
        if (m > 2 && pts.get(0).dist(pts.get(m - 1)) < 1e-9) m--;
        boolean inside = false;
        for (int i = 0, j = m - 1; i < m; j = i++) {
            double xi = pts.get(i).x(), yi = pts.get(i).y();
            double xj = pts.get(j).x(), yj = pts.get(j).y();
            if ((yi > pt.y()) != (yj > pt.y()) &&
                pt.x() < (xj - xi) * (pt.y() - yi) / (yj - yi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    public record ProcessResult(Toolpath toolpath, List<PolygonWithHoles> polygons) {}

    /**
     * Visual preview: export toolpath to SVG for debugging/viewing in browser/Inkscape
     */
    public void exportToolpathPreview(Toolpath toolpath, String svgFilePath, double width, double height) throws IOException {
        ToolpathSvgPreview preview = new ToolpathSvgPreview();
        preview.exportToSvg(toolpath, svgFilePath, width, height);
    }

    // Demo helper
    public static PolygonWithHoles createTestSquare() {
        List<Point2D> points = new ArrayList<>();
        points.add(new Point2D(0, 0));
        points.add(new Point2D(100, 0));
        points.add(new Point2D(100, 100));
        points.add(new Point2D(0, 100));
        points.add(new Point2D(0, 0)); // closed
        Polyline outer = new Polyline();
        for (Point2D p : points) {
            outer.addPoint(p);
        }
        return new PolygonWithHoles(outer);
    }
}