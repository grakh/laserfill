package com.lasercam.core.io;

import com.lasercam.core.geometry.SvgPathParser;
import com.lasercam.core.model.Point2D;
import com.lasercam.core.model.Polyline;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.awt.geom.AffineTransform;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java SVG importer. Zero external dependencies.
 * Handles: path, rect, circle, ellipse, polygon, polyline, line.
 * Supports compound paths, nested groups, chained transforms.
 */
public class SvgImporter {

    private final SvgPathParser pathParser;
    private final Stack<AffineTransform> transformStack = new Stack<>();

    public SvgImporter(double tolerance) {
        this.pathParser = new SvgPathParser(tolerance);
        transformStack.push(new AffineTransform()); // identity
    }

    public List<Polyline> importSvg(String filePath) throws Exception {
        List<Polyline> polylines = new ArrayList<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        // Disable DTD/external entities for safety & speed
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new File(filePath));
        doc.getDocumentElement().normalize();
        parseNode(doc.getDocumentElement(), polylines);
        return polylines;
    }

    private void parseNode(Node node, List<Polyline> polylines) {
        if (node.getNodeType() != Node.ELEMENT_NODE) return;
        Element elem = (Element) node;
        String tag = elem.getTagName().toLowerCase();
        // Strip namespace prefix if any (e.g., "svg:path" → "path")
        if (tag.contains(":")) tag = tag.substring(tag.indexOf(':') + 1);

        String transformStr = elem.getAttribute("transform");
        boolean pushed = false;
        if (transformStr != null && !transformStr.isEmpty()) {
            AffineTransform parent = transformStack.peek();
            AffineTransform local = parseTransform(transformStr);
            AffineTransform combined = new AffineTransform(parent);
            combined.concatenate(local);
            transformStack.push(combined);
            pushed = true;
        }

        switch (tag) {
            case "path" -> {
                String d = elem.getAttribute("d");
                if (d != null && !d.isEmpty()) {
                    List<Polyline> subPaths = pathParser.parse(d);
                    for (Polyline p : subPaths) {
                        p.applyTransform(transformStack.peek());
                        polylines.add(p);
                    }
                }
            }
            case "rect" -> {
                double x = dbl(elem, "x"), y = dbl(elem, "y");
                double w = dbl(elem, "width"), h = dbl(elem, "height");
                if (w > 0 && h > 0) {
                    Polyline r = new Polyline();
                    r.addPoint(new Point2D(x, y));
                    r.addPoint(new Point2D(x+w, y));
                    r.addPoint(new Point2D(x+w, y+h));
                    r.addPoint(new Point2D(x, y+h));
                    r.addPoint(new Point2D(x, y)); // close
                    r.applyTransform(transformStack.peek());
                    polylines.add(r);
                }
            }
            case "circle" -> {
                double cx = dbl(elem, "cx"), cy = dbl(elem, "cy"), cr = dbl(elem, "r");
                if (cr > 0) {
                    polylines.add(makeEllipse(cx, cy, cr, cr));
                }
            }
            case "ellipse" -> {
                double cx = dbl(elem, "cx"), cy = dbl(elem, "cy");
                double rx = dbl(elem, "rx"), ry = dbl(elem, "ry");
                if (rx > 0 && ry > 0) {
                    polylines.add(makeEllipse(cx, cy, rx, ry));
                }
            }
            case "polygon", "polyline" -> {
                String pts = elem.getAttribute("points");
                if (pts != null && !pts.isEmpty()) {
                    Polyline pl = parsePointsList(pts, "polygon".equals(tag));
                    if (pl != null) {
                        pl.applyTransform(transformStack.peek());
                        polylines.add(pl);
                    }
                }
            }
            case "line" -> {
                Polyline l = new Polyline();
                l.addPoint(new Point2D(dbl(elem, "x1"), dbl(elem, "y1")));
                l.addPoint(new Point2D(dbl(elem, "x2"), dbl(elem, "y2")));
                l.applyTransform(transformStack.peek());
                polylines.add(l);
            }
        }

        // Recurse into container elements
        if ("svg".equals(tag) || "g".equals(tag) || "a".equals(tag)
                || "symbol".equals(tag) || "defs".equals(tag) || tag.isEmpty()) {
            NodeList children = elem.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                parseNode(children.item(i), polylines);
            }
        }

        if (pushed) transformStack.pop();
    }

    // ─── Helpers ───

    private double dbl(Element e, String attr) {
        String v = e.getAttribute(attr);
        if (v == null || v.isEmpty()) return 0;
        try {
            // Strip units (px, mm, pt, etc.)
            v = v.replaceAll("[^\\d.eE+-]", "");
            return Double.parseDouble(v);
        } catch (NumberFormatException ex) { return 0; }
    }

    private Polyline makeEllipse(double cx, double cy, double rx, double ry) {
        int n = Math.max(24, (int)(Math.max(rx, ry) * 1.5));
        Polyline pl = new Polyline();
        for (int i = 0; i <= n; i++) {
            double a = 2 * Math.PI * i / n;
            pl.addPoint(new Point2D(cx + rx * Math.cos(a), cy + ry * Math.sin(a)));
        }
        pl.applyTransform(transformStack.peek());
        return pl;
    }

    private Polyline parsePointsList(String s, boolean close) {
        double[] nums = extractNumbers(s);
        if (nums.length < 4) return null;
        Polyline pl = new Polyline();
        for (int i = 0; i + 1 < nums.length; i += 2) {
            pl.addPoint(new Point2D(nums[i], nums[i + 1]));
        }
        if (close) pl.addPoint(pl.getPoints().get(0));
        return pl;
    }

    // ─── Transform parser ───

    private AffineTransform parseTransform(String transform) {
        AffineTransform combined = new AffineTransform();
        if (transform == null || transform.isEmpty()) return combined;

        Pattern pat = Pattern.compile(
                "(matrix|translate|scale|rotate|skewX|skewY)\\s*\\(([^)]+)\\)",
                Pattern.CASE_INSENSITIVE);
        Matcher mat = pat.matcher(transform);

        while (mat.find()) {
            String func = mat.group(1).toLowerCase();
            double[] v = extractNumbers(mat.group(2));
            AffineTransform at = new AffineTransform();
            switch (func) {
                case "matrix" -> { if (v.length>=6) at.setTransform(v[0],v[1],v[2],v[3],v[4],v[5]); }
                case "translate" -> {
                    if (v.length >= 2) at.translate(v[0], v[1]);
                    else if (v.length == 1) at.translate(v[0], 0);
                }
                case "scale" -> {
                    if (v.length >= 2) at.scale(v[0], v[1]);
                    else if (v.length == 1) at.scale(v[0], v[0]);
                }
                case "rotate" -> {
                    if (v.length >= 3) { at.translate(v[1],v[2]); at.rotate(Math.toRadians(v[0])); at.translate(-v[1],-v[2]); }
                    else if (v.length >= 1) at.rotate(Math.toRadians(v[0]));
                }
                case "skewx" -> { if (v.length>=1) at.shear(Math.tan(Math.toRadians(v[0])),0); }
                case "skewy" -> { if (v.length>=1) at.shear(0,Math.tan(Math.toRadians(v[0]))); }
            }
            combined.concatenate(at);
        }
        return combined;
    }

    private double[] extractNumbers(String s) {
        List<Double> nums = new ArrayList<>();
        Matcher m = Pattern.compile("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?").matcher(s);
        while (m.find()) {
            try { nums.add(Double.parseDouble(m.group())); }
            catch (NumberFormatException ignored) {}
        }
        return nums.stream().mapToDouble(Double::doubleValue).toArray();
    }
}
