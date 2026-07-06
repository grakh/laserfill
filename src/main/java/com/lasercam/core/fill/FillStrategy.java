package com.lasercam.core.fill;

import com.lasercam.core.model.PolygonWithHoles;
import com.lasercam.core.model.Toolpath;

public interface FillStrategy {
    Toolpath generate(PolygonWithHoles polygon);
}