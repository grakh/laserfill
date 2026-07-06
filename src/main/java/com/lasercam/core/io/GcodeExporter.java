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
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writeHeader(writer, feedRateCut, power);

            if (zDepth > 0 && zStep > 0) {
                exportMillMode(writer, toolpath, feedRateCut, power);
            } else {
                exportLaserMode(writer, toolpath, feedRateCut, power);
            }
            writeFooter(writer);
        }
    }

    /** Laser mode — original behavior (M3/M5, no Z axis). */
    private void exportLaserMode(PrintWriter writer, Toolpath toolpath, double feedRateCut, double power) {
        Point2D lastPos = null;
        boolean laserOn = false;

        for (ToolpathSegment seg : toolpath.getSegments()) {
            Point2D start = seg.start(), end = seg.end();

            if (lastPos == null || lastPos.dist(start) > 0.005) {
                if (laserOn) { writer.println("M5 ; laser off"); laserOn = false; }
                writer.printf("G0 X%.4f Y%.4f%n", start.x(), start.y());
            }
            if (seg.isCut()) {
                if (!laserOn) {
                    writer.printf("M3 S%d%n", (int) power);
                    laserOn = true;
                }
                writer.printf("G1 X%.4f Y%.4f F%d%n", end.x(), end.y(), (int) feedRateCut);
            } else {
                if (laserOn) { writer.println("M5 ; laser off"); laserOn = false; }
                writer.printf("G0 X%.4f Y%.4f%n", end.x(), end.y());
            }
            lastPos = end;
        }
    }

    /** Mill mode — repeats toolpath at descending Z levels. */
    private void exportMillMode(PrintWriter writer, Toolpath toolpath, double feedRateCut, double spindleSpeed) {
        int passes = (int) Math.ceil(zDepth / zStep);
        writer.printf("; Mill mode: %d passes, total depth=%.2fmm, step=%.2fmm%n", passes, zDepth, zStep);
        writer.printf("M3 S%d ; spindle on%n", (int) spindleSpeed);
        writer.printf("G0 Z%.3f ; safe height%n", zSafe);

        for (int p = 1; p <= passes; p++) {
            double curZ = -Math.min(p * zStep, zDepth);
            writer.printf("; ─── Pass %d/%d, Z=%.3f ───%n", p, passes, curZ);

            Point2D lastPos = null;
            boolean plunged = false;

            for (ToolpathSegment seg : toolpath.getSegments()) {
                Point2D start = seg.start(), end = seg.end();

                if (lastPos == null || lastPos.dist(start) > 0.005) {
                    // Retract before travel
                    if (plunged) {
                        writer.printf("G0 Z%.3f ; retract%n", zSafe);
                        plunged = false;
                    }
                    writer.printf("G0 X%.4f Y%.4f%n", start.x(), start.y());
                }

                if (seg.isCut()) {
                    if (!plunged) {
                        writer.printf("G1 Z%.3f F%d ; plunge%n", curZ, (int) feedRateZ);
                        plunged = true;
                    }
                    writer.printf("G1 X%.4f Y%.4f F%d%n", end.x(), end.y(), (int) feedRateCut);
                } else {
                    if (plunged) {
                        writer.printf("G0 Z%.3f ; retract%n", zSafe);
                        plunged = false;
                    }
                    writer.printf("G0 X%.4f Y%.4f%n", end.x(), end.y());
                }
                lastPos = end;
            }
            // End of pass — retract
            writer.printf("G0 Z%.3f%n", zSafe);
        }
        writer.println("M5 ; spindle off");
    }

    private void writeHeader(PrintWriter w, double feedRate, double power) {
        w.println("; LaserCam G-code generated");
        w.printf("; Firmware: %s%n", firmware);
        if (zDepth > 0) w.printf("; Mode: MILL (depth=%.2fmm, step=%.2fmm)%n", zDepth, zStep);
        else w.println("; Mode: LASER");
        w.println("G21 ; mm mode");
        w.println("G90 ; absolute");
        if (zDepth == 0) w.println("M5 ; laser off at start");
    }

    private void writeFooter(PrintWriter w) {
        if (zDepth > 0) {
            w.printf("G0 Z%.3f ; retract to safe%n", zSafe);
            w.println("M5 ; spindle off");
        } else {
            w.println("M5 ; laser off");
        }
        w.println("G0 X0 Y0 ; home");
        w.println("M2 ; end");
    }
}
