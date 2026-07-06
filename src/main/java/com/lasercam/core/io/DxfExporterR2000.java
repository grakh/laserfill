package com.lasercam.core.io;

import com.lasercam.core.model.Point2D;
import com.lasercam.core.model.Toolpath;
import com.lasercam.core.model.ToolpathSegment;

import java.io.*;
import java.util.List;

public class DxfExporterR2000 {

    public void export(Toolpath toolpath, String filePath, boolean includeTravel) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writeHeader(writer);
            writeTables(writer);  // Fixed: proper TABLES section
            writeEntities(toolpath, writer, includeTravel);
            writeFooter(writer);
        }
    }

    private void writeHeader(PrintWriter w) {
        w.println("0");
        w.println("SECTION");
        w.println("2");
        w.println("HEADER");
        w.println("9");
        w.println("$ACADVER");
        w.println("1");
        w.println("AC1015"); // R2000
        w.println("0");
        w.println("ENDSEC");
    }

    private void writeTables(PrintWriter w) {
        w.println("0");
        w.println("SECTION");
        w.println("2");
        w.println("TABLES");
        w.println("0");
        w.println("TABLE");
        w.println("2");
        w.println("LAYER");
        w.println("70");
        w.println("1");  // Number of entries
        writeLayer(w, "0", 7);  // Layer 0
        writeLayer(w, "CUT", 1);
        writeLayer(w, "TRAVEL", 8);
        writeLayer(w, "BOUNDARY", 3);
        w.println("0");
        w.println("ENDTAB");
        w.println("0");
        w.println("ENDSEC");
    }

    private void writeLayer(PrintWriter w, String name, int color) {
        w.println("0");
        w.println("LAYER");
        w.println("2");
        w.println(name);
        w.println("70");
        w.println("0");
        w.println("62");
        w.println(color);
        w.println("6");
        w.println("CONTINUOUS");
    }

    private void writeLwpolyline(PrintWriter w, List<Point2D> points, String layer, int handle) {
        w.println("0");
        w.println("LWPOLYLINE");
        w.println("5");
        w.println(String.format("%X", 100 + handle));
        w.println("8");
        w.println(layer);
        w.println("90");
        w.println(points.size());
        w.println("70");
        w.println("0"); // not closed for segments

        for (Point2D p : points) {
            w.println("10");
            w.println(String.format("%.4f", p.x()));
            w.println("20");
            w.println(String.format("%.4f", p.y()));
        }
    }

    private void writeEntities(Toolpath toolpath, PrintWriter w, boolean includeTravel) {
        w.println("0");
        w.println("SECTION");
        w.println("2");
        w.println("ENTITIES");

        int entityCount = 0;
        for (ToolpathSegment seg : toolpath.getSegments()) {
            if (!seg.isCut() && !includeTravel) continue;

            String layer = seg.isCut() ? "CUT" : "TRAVEL";
            writeLwpolyline(w, List.of(seg.start(), seg.end()), layer, entityCount++);
        }

        w.println("0");
        w.println("ENDSEC");
    }

    private void writeFooter(PrintWriter w) {
        w.println("0");
        w.println("EOF");
    }
}