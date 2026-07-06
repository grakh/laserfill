package com.lasercam.core;

import com.lasercam.core.core.LaserCamEngine;
import com.lasercam.core.fill.ScanlineFill;
import com.lasercam.core.fill.SpiralFill;
import com.lasercam.core.model.Point2D;
import com.lasercam.core.model.PolygonWithHoles;
import com.lasercam.core.model.Polyline;
import com.lasercam.core.model.Toolpath;
import com.lasercam.core.ui.ToolpathSwingViewer;

import java.util.ArrayList;
import java.util.List;

public class DemoMain {

    public static void main(String[] args) {
        System.out.println("=== LaserCam Engine Demo ===");

        // --- Test 1: Square with round hole ---
        PolygonWithHoles polygon = createSquareWithHole();
        System.out.println("Polygon: outer=" + polygon.getOuter().getPoints().size()
                + " pts, holes=" + polygon.getHoles().size());

        // Scanline fill
        LaserCamEngine engine = new LaserCamEngine(new ScanlineFill(3.0));
        Toolpath scanTP = engine.fillPolygon(polygon, 3.0);
        System.out.println("Scanline: " + scanTP.getSegments().size() + " segments");

        // Spiral fill
        engine.setFillStrategy(new SpiralFill(4.0));
        Toolpath spiralTP = engine.fillPolygon(polygon, 4.0);
        System.out.println("Spiral:   " + spiralTP.getSegments().size() + " segments");

        // --- Export ---
        try {
            engine.exportToDxf(scanTP, "output_scanline.dxf", true);
            engine.exportToGcode(scanTP, "output_scanline.nc", 1000, 3000, 255);
            engine.exportToolpathPreview(scanTP, "output_scanline_preview.svg", 120, 120);

            engine.exportToDxf(spiralTP, "output_spiral.dxf", true);
            engine.exportToGcode(spiralTP, "output_spiral.nc", 1000, 3000, 255);

            System.out.println("Exports: OK");
        } catch (Exception e) {
            System.err.println("Export error: " + e.getMessage());
        }

        // --- SVG import test (if file exists) ---
        String svgPath = args.length > 0 ? args[0] : "test_transforms.svg";
        java.io.File svgFile = new java.io.File(svgPath);
        if (svgFile.exists()) {
            try {
                List<Polyline> polylines = engine.importSvg(svgPath);
                System.out.println("SVG import: " + polylines.size() + " polylines from " + svgPath);
                PolygonWithHoles svgPoly = engine.buildPolygon(polylines);
                Toolpath svgTP = engine.fillPolygon(svgPoly, 2.0);
                System.out.println("SVG toolpath: " + svgTP.getSegments().size() + " segments");
                // Show SVG result
                ToolpathSwingViewer.show(svgTP, List.of(svgPoly));
                return; // viewer is open, don't open another
            } catch (Exception e) {
                System.err.println("SVG import error: " + e.getMessage());
            }
        }

        // --- Launch Swing Viewer ---
        // Show scanline by default; change to spiralTP to preview spiral
        ToolpathSwingViewer.show(scanTP, List.of(polygon));

        System.out.println("Viewer launched. Close window to exit.");
    }

    /**
     * 100x100 square with a circular hole (radius 20) in the center.
     */
    static PolygonWithHoles createSquareWithHole() {
        // Outer: CCW square
        Polyline outer = new Polyline();
        outer.addPoint(new Point2D(0, 0));
        outer.addPoint(new Point2D(100, 0));
        outer.addPoint(new Point2D(100, 100));
        outer.addPoint(new Point2D(0, 100));
        outer.addPoint(new Point2D(0, 0));

        // Hole: CW circle at center
        Polyline hole = new Polyline();
        int n = 32;
        double cx = 50, cy = 50, r = 20;
        for (int i = 0; i <= n; i++) {
            double a = 2 * Math.PI * i / n;
            hole.addPoint(new Point2D(cx + r * Math.cos(a), cy + r * Math.sin(a)));
        }

        PolygonWithHoles poly = new PolygonWithHoles(outer);
        poly.addHole(hole);
        return poly;
    }
}
