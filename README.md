# LaserCAM Core — CAM Engine for Laser Machines

## Architecture

```
SVG (+ rect/circle/ellipse/polygon/line)
→ SvgImporter (Batik parser + BezierFlattener)
  ├── Full SVG path commands (M/L/H/V/C/S/Q/T/A/Z)
  ├── Compound paths (subpaths → multiple Polylines)
  └── Chained transforms (matrix/translate/scale/rotate/skew)
→ PolygonBuilder (outer + holes via even-odd rule)
→ FillStrategy
  ├── ScanlineFill (zigzag hatching)
  └── SpiralFill  (inward offset contours, holes expand outward)
→ ToolpathOptimizer (nearest-neighbor + segment reversal)
→ Export
  ├── DxfExporterR2000 (LWPOLYLINE, layers: CUT/TRAVEL/BOUNDARY)
  ├── GcodeExporter    (GRBL/Ruida, per-segment M3/M5 laser control)
  └── ToolpathSvgPreview (debug SVG for browser/Inkscape)
→ Viewer
  └── ToolpathSwingViewer (zero-dependency interactive viewer)
```

## Quick Start

```java
LaserCamEngine engine = new LaserCamEngine(new ScanlineFill(3.0));

// From SVG file:
var result = engine.processSvgFull("input.svg", 3.0);
ToolpathSwingViewer.show(result.toolpath(), result.polygons());

// From code polygon:
PolygonWithHoles poly = ...;
Toolpath tp = engine.fillPolygon(poly, 5.0);
engine.exportToGcode(tp, "out.nc", 1000, 3000, 255);
engine.exportToDxf(tp, "out.dxf", true);
```

## Swing Viewer Features

- **Auto-fit** to geometry bounds
- **Mouse wheel zoom** (centered on cursor)
- **Mouse drag pan**
- **Layer toggles**: CUT (green) / TRAVEL (orange) / BOUNDARY (blue) / GRID
- **Toolpath animation** with laser head marker
- **Stats bar**: segments, cut/travel distance, efficiency %
- **Coordinate readout** under cursor
- Zero dependencies — works on any JDK 17+, no JavaFX needed

## Modules

| Package | Classes | Role |
|---------|---------|------|
| `core`  | LaserCamEngine | Pipeline orchestrator |
| `model` | Point2D, Polyline, PolygonWithHoles, Toolpath, ToolpathSegment | Data model |
| `geometry` | BezierFlattener, PolygonBuilder, OffsetPolygon | Geometry operations |
| `fill` | FillStrategy, ScanlineFill, SpiralFill | Fill pattern generators |
| `optimizer` | ToolpathOptimizer | Travel minimization |
| `io` | SvgImporter, DxfExporterR2000, GcodeExporter, ToolpathSvgPreview | I/O |
| `ui` | ToolpathSwingViewer | Interactive viewer |

## Build

```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="com.lasercam.core.DemoMain"
```

## Changelog (Latest)

### Fixed
- **SpiralFill**: holes now offset outward (expand into material), not inward
- **OffsetPolygon**: proper miter-join normals with winding detection, no more inverted offsets
- **GcodeExporter**: laser M3/M5 per segment (was blanket M3 in header)
- **SvgImporter**: full transform parsing (scale/rotate/skew were stubs), chained transforms
- **SvgImporter**: added rect/circle/ellipse/polygon/polyline/line elements
- **SvgImporter**: fixed duplicate endPath, fixed S/Q/T curve reflection
- **SvgImporter**: proper node recursion (svg/g/a/symbol containers)

### Added
- **ToolpathSwingViewer**: full interactive viewer (replaces broken JavaFX viewer)
- **LaserCamEngine.processSvgFull()**: returns both toolpath + polygons for viewer
- **DemoMain**: test with circular hole, demonstrates both fill strategies
