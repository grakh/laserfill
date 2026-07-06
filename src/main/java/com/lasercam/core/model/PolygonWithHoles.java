package com.lasercam.core.model;

import java.util.ArrayList;
import java.util.List;

public class PolygonWithHoles {
    private final Polyline outer;
    private final List<Polyline> holes = new ArrayList<>();

    public PolygonWithHoles(Polyline outer) {
        this.outer = outer;
    }

    public Polyline getOuter() {
        return outer;
    }

    public List<Polyline> getHoles() {
        return new ArrayList<>(holes);
    }

    public void addHole(Polyline hole) {
        holes.add(hole);
    }
}