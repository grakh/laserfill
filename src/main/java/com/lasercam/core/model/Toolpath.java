package com.lasercam.core.model;

import java.util.ArrayList;
import java.util.List;

public class Toolpath {
    private final List<ToolpathSegment> segments = new ArrayList<>();

    public void addCutSegment(Point2D start, Point2D end) {
        segments.add(new ToolpathSegment(start, end, true));
    }

    public void addTravelSegment(Point2D start, Point2D end) {
        segments.add(new ToolpathSegment(start, end, false));
    }

    public List<ToolpathSegment> getSegments() {
        return new ArrayList<>(segments);
    }

    public void addSegment(ToolpathSegment segment) {
        if (segment.isCut()) {
            addCutSegment(segment.start(), segment.end());
        } else {
            addTravelSegment(segment.start(), segment.end());
        }
    }

    /**
     * Add full closed polyline as cut segments (continuous contour)
     */
    public void addCutPolyline(Polyline polyline) {
        List<Point2D> pts = polyline.getPoints();
        if (pts.size() < 2) return;
        for (int i = 0; i < pts.size() - 1; i++) {
            addCutSegment(pts.get(i), pts.get(i + 1));
        }
    }
}