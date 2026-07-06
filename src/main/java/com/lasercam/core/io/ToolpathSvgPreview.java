package com.lasercam.core.io;

import com.lasercam.core.model.Point2D;
import com.lasercam.core.model.Toolpath;
import com.lasercam.core.model.ToolpathSegment;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class ToolpathSvgPreview {

    public void exportToSvg(Toolpath toolpath, String filePath, double width, double height) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            writer.println("<svg width=\"" + width + "mm\" height=\"" + height + "mm\" viewBox=\"0 0 " + width + " " + height + "\" xmlns=\"http://www.w3.org/2000/svg\">");
            
            // Background
            writer.println("  <rect width=\"" + width + "\" height=\"" + height + "\" fill=\"#f8f8f8\"/>");
            
            // Toolpath segments
            for (ToolpathSegment seg : toolpath.getSegments()) {
                String color = seg.isCut() ? "#ff0000" : "#00aa00";
                String strokeWidth = seg.isCut() ? "0.5" : "0.3";
                String dash = seg.isCut() ? "" : " stroke-dasharray=\"1,1\"";
                
                writer.printf("  <line x1=\"%.4f\" y1=\"%.4f\" x2=\"%.4f\" y2=\"%.4f\" stroke=\"%s\" stroke-width=\"%s\"%s />\n",
                        seg.start().x(), seg.start().y(),
                        seg.end().x(), seg.end().y(),
                        color, strokeWidth, dash);
            }
            
            writer.println("</svg>");
        }
    }
}
