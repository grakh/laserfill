package com.lasercam.core.model;

/**
 * One move of the tool.
 * @param power laser power for this segment (raster engraving); negative = use the
 *              job's global power. A cut segment with power 0 moves at cutting feed
 *              with the laser off (overscan / white gaps in a raster line).
 */
public record ToolpathSegment(Point2D start, Point2D end, boolean isCut, double power) {
    public ToolpathSegment(Point2D start, Point2D end, boolean isCut) {
        this(start, end, isCut, -1);
    }

    public boolean hasPower() { return power >= 0; }

    public ToolpathSegment reversed() {
        return new ToolpathSegment(end, start, isCut, power);
    }
}
