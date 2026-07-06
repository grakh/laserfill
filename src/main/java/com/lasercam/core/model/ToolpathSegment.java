package com.lasercam.core.model;

public record ToolpathSegment(Point2D start, Point2D end, boolean isCut) {
    public ToolpathSegment reversed() {
        return new ToolpathSegment(end, start, isCut);
    }
}