package com.lasercam.core.io;

import com.lasercam.core.model.Point2D;
import com.lasercam.core.model.Toolpath;
import com.lasercam.core.model.ToolpathSegment;

import java.io.*;

public class GcodeExporter {

    private final String firmware; // "GRBL" or "RUIDA"

    // Mill mode: multi-pass Z-axis cutting
    private double zDepth = 0;        // total cut depth (mm)
    private double zStep = 0;         // depth per pass (mm)
    private double zSafe = 5;         // safe travel height (mm)
    private double feedRateZ = 300;   // Z-axis plunge feed (mm/min)

    public GcodeExporter(String firmware) {
        this.firmware = firmware != null ? firmware.toUpperCase() : "GRBL";
    }

    /**
     * Enable multi-pass milling mode.
     * @param depth total cut depth (0 = laser mode, no Z moves)
     * @param step depth per pass
     * @param safeZ retract height for travels
     * @param plungeFeed Z-axis feed rate
     */
    public void setMillMode(double depth, double step, double safeZ, double plungeFeed) {
        this.zDepth = depth;
        this.zStep = step;
        this.zSafe = safeZ;
        this.feedRateZ = plungeFeed;
    }

    public void export(Toolpath toolpath, String filePath, double feedRateCut, double feedRateTravel, double power) throws IOException {
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(filePath), 1 << 16))) {
            writeHeader(writer, feedRateCut, power);

            if (zDepth > 0 && zStep > 0) {
                exportMillMode(writer, toolpath, feedRateCut, power);
            } else if (toolpath.hasVariablePower()) {
                exportRaster(writer, toolpath, feedRateCut);
            } else {
                exportLaserMode(writer, toolpath, feedRateCut, power);
            }
            writeFooter(writer);
        }
    }

    /**
     * Raster engraving with per-segment power. Uses M4 (dynamic laser power, GRBL
     * laser mode $32=1): power follows the actual speed, so acceleration does not
     * leave dark marks, and G0 turns the beam off. S and F are written only when
     * they change, and coordinates use 3 decimals to keep the file small.
     */
    private void exportRaster(PrintWriter w, Toolpath toolpath, double feedRateCut) {
        w.println("; Raster engraving — requires GRBL laser mode ($32=1)");
        w.println("M4 S0 ; dynamic laser power");
        Point2D lastPos = null;
        int lastS = -1;
        int lastF = -1;
        for (ToolpathSegment seg : toolpath.getSegments()) {
            Point2D start = seg.start(), end = seg.end();
            if (lastPos == null || lastPos.dist(start) > 0.0005) {
                w.printf(java.util.Locale.ROOT, "G0 X%.3f Y%.3f%n", start.x(), start.y());
            }
            if (seg.isCut()) {
                int s = (int) Math.round(Math.max(0, seg.power()));
                int f = (int) feedRateCut;
                StringBuilder sb = new StringBuilder("G1");
                sb.append(String.format(java.util.Locale.ROOT, " X%.3f Y%.3f", end.x(), end.y()));
                if (s != lastS) { sb.append(" S").append(s); lastS = s; }
                if (f != lastF) { sb.append(" F").append(f); lastF = f; }
                w.println(sb);
            } else {
                w.printf(java.util.Locale.ROOT, "G0 X%.3f Y%.3f%n", end.x(), end.y());
            }
            lastPos = end;
        }
        w.println("S0");
    }

    /** Laser mode — original behavior (M3/M5, no Z axis). */
    private void exportLaserMode(PrintWriter writer, Toolpath toolpath, double feedRateCut, double power) {
        Point2D lastPos = null;
        boolean laserOn = false;

        for (ToolpathSegment seg : toolpath.getSegments()) {
            Point2D start = seg.start(), end = seg.end();

            if (lastPos == null || lastPos.dist(start) > 0.005) {
                if (laserOn) { writer.println("M5 ; laser off"); laserOn = false; }
                writer.printf(java.util.Locale.ROOT, "G0 X%.4f Y%.4f%n", start.x(), start.y());
            }
            if (seg.isCut()) {
                if (!laserOn) {
                    writer.printf(java.util.Locale.ROOT, "M3 S%d%n", (int) power);
                    laserOn = true;
                }
                writer.printf(java.util.Locale.ROOT, "G1 X%.4f Y%.4f F%d%n", end.x(), end.y(), (int) feedRateCut);
            } else {
                if (laserOn) { writer.println("M5 ; laser off"); laserOn = false; }
                writer.printf(java.util.Locale.ROOT, "G0 X%.4f Y%.4f%n", end.x(), end.y());
            }
            lastPos = end;
        }
    }

    /** Mill mode — repeats toolpath at descending Z levels. */
    private void exportMillMode(PrintWriter writer, Toolpath toolpath, double feedRateCut, double spindleSpeed) {
        int passes = (int) Math.ceil(zDepth / zStep);
        writer.printf(java.util.Locale.ROOT, "; Mill mode: %d passes, total depth=%.2fmm, step=%.2fmm%n", passes, zDepth, zStep);
        writer.printf(java.util.Locale.ROOT, "M3 S%d ; spindle on%n", (int) spindleSpeed);
        writer.printf(java.util.Locale.ROOT, "G0 Z%.3f ; safe height%n", zSafe);

        for (int p = 1; p <= passes; p++) {
            double curZ = -Math.min(p * zStep, zDepth);
            writer.printf(java.util.Locale.ROOT, "; ─── Pass %d/%d, Z=%.3f ───%n", p, passes, curZ);

            Point2D lastPos = null;
            boolean plunged = false;

            for (ToolpathSegment seg : toolpath.getSegments()) {
                Point2D start = seg.start(), end = seg.end();

                if (lastPos == null || lastPos.dist(start) > 0.005) {
                    // Retract before travel
                    if (plunged) {
                        writer.printf(java.util.Locale.ROOT, "G0 Z%.3f ; retract%n", zSafe);
                        plunged = false;
                    }
                    writer.printf(java.util.Locale.ROOT, "G0 X%.4f Y%.4f%n", start.x(), start.y());
                }

                if (seg.isCut()) {
                    if (!plunged) {
                        writer.printf(java.util.Locale.ROOT, "G1 Z%.3f F%d ; plunge%n", curZ, (int) feedRateZ);
                        plunged = true;
                    }
                    writer.printf(java.util.Locale.ROOT, "G1 X%.4f Y%.4f F%d%n", end.x(), end.y(), (int) feedRateCut);
                } else {
                    if (plunged) {
                        writer.printf(java.util.Locale.ROOT, "G0 Z%.3f ; retract%n", zSafe);
                        plunged = false;
                    }
                    writer.printf(java.util.Locale.ROOT, "G0 X%.4f Y%.4f%n", end.x(), end.y());
                }
                lastPos = end;
            }
            // End of pass — retract
            writer.printf(java.util.Locale.ROOT, "G0 Z%.3f%n", zSafe);
        }
        writer.println("M5 ; spindle off");
    }

    private void writeHeader(PrintWriter w, double feedRate, double power) {
        w.println("; LaserCam G-code generated");
        w.printf(java.util.Locale.ROOT, "; Firmware: %s%n", firmware);
        if (zDepth > 0) w.printf(java.util.Locale.ROOT, "; Mode: MILL (depth=%.2fmm, step=%.2fmm)%n", zDepth, zStep);
        else w.println("; Mode: LASER");
        w.println("G21 ; mm mode");
        w.println("G90 ; absolute");
        if (zDepth == 0) w.println("M5 ; laser off at start");
    }

    private void writeFooter(PrintWriter w) {
        if (zDepth > 0) {
            w.printf(java.util.Locale.ROOT, "G0 Z%.3f ; retract to safe%n", zSafe);
            w.println("M5 ; spindle off");
        } else {
            w.println("M5 ; laser off");
        }
        w.println("G0 X0 Y0 ; home");
        w.println("M2 ; end");
    }
}
