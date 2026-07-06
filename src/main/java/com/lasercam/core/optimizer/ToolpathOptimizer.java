package com.lasercam.core.optimizer;

import com.lasercam.core.model.Point2D;
import com.lasercam.core.model.Toolpath;
import com.lasercam.core.model.ToolpathSegment;

import java.util.ArrayList;
import java.util.List;

public class ToolpathOptimizer {

    private final double connectThreshold = 2.0; // mm, tune as needed

    public Toolpath optimize(Toolpath original) {
        List<ToolpathSegment> segments = new ArrayList<>(original.getSegments());
        if (segments.isEmpty()) return original;

        Toolpath optimized = new Toolpath();
        List<ToolpathSegment> remaining = new ArrayList<>(segments);

        ToolpathSegment current = remaining.remove(0);
        optimized.addSegment(current); // start with first

        while (!remaining.isEmpty()) {
            ToolpathSegment bestNext = null;
            double minDist = Double.MAX_VALUE;
            boolean reverseBest = false;
            int bestIndex = -1;

            for (int i = 0; i < remaining.size(); i++) {
                ToolpathSegment seg = remaining.get(i);

                // Check normal direction
                double distNormal = current.end().dist(seg.start());
                if (distNormal < minDist) {
                    minDist = distNormal;
                    bestNext = seg;
                    reverseBest = false;
                    bestIndex = i;
                }

                // Check reversed direction
                double distReverse = current.end().dist(seg.end());
                if (distReverse < minDist) {
                    minDist = distReverse;
                    bestNext = seg;
                    reverseBest = true;
                    bestIndex = i;
                }
            }

            if (minDist > connectThreshold) {
                // Add travel move
                optimized.addTravelSegment(current.end(), bestNext.start());
            }

            if (reverseBest) {
                bestNext = bestNext.reversed();
            }

            optimized.addSegment(bestNext);
            remaining.remove(bestIndex);
            current = bestNext;
        }

        return optimized;
    }
}
