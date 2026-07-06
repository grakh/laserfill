package com.lasercam.core.geometry;

import com.lasercam.core.model.Point2D;
import com.lasercam.core.model.Polyline;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java SVG path data parser.
 * Replaces Batik dependency. Handles: M/L/H/V/C/S/Q/T/A/Z (abs+rel).
 * Returns list of Polyline subpaths.
 */
public class SvgPathParser {

    private final BezierFlattener flattener;

    public SvgPathParser(double tolerance) {
        this.flattener = new BezierFlattener(tolerance);
    }

    /**
     * Parse SVG path "d" attribute into list of polylines (one per subpath).
     */
    public List<Polyline> parse(String d) {
        if (d == null || d.isBlank()) return List.of();

        List<Polyline> subPaths = new ArrayList<>();
        List<Token> tokens = tokenize(d);

        Polyline current = new Polyline();
        double cx = 0, cy = 0; // current point
        double sx = 0, sy = 0; // subpath start
        double lcx = cx, lcy = cy; // last control point (for S/T)
        char lastCmd = 0;

        int i = 0;
        while (i < tokens.size()) {
            Token tok = tokens.get(i);
            if (tok.isCommand) {
                lastCmd = tok.command;
                i++;
            }
            // If no command, repeat the last one (implicit lineto after moveto, etc.)
            char cmd = lastCmd;
            boolean abs = Character.isUpperCase(cmd);
            char uc = Character.toUpperCase(cmd);

            switch (uc) {
                case 'M': {
                    double x = nextNum(tokens, i++);
                    double y = nextNum(tokens, i++);
                    if (!abs) { x += cx; y += cy; }
                    // Save current subpath
                    if (current.getPoints().size() > 1) subPaths.add(current);
                    current = new Polyline();
                    current.addPoint(new Point2D(x, y));
                    cx = x; cy = y; sx = x; sy = y;
                    // Subsequent coordinates after M are implicit L
                    lastCmd = abs ? 'L' : 'l';
                    break;
                }
                case 'L': {
                    double x = nextNum(tokens, i++);
                    double y = nextNum(tokens, i++);
                    if (!abs) { x += cx; y += cy; }
                    current.addPoint(new Point2D(x, y));
                    cx = x; cy = y;
                    lcx = cx; lcy = cy;
                    break;
                }
                case 'H': {
                    double x = nextNum(tokens, i++);
                    if (!abs) x += cx;
                    current.addPoint(new Point2D(x, cy));
                    cx = x;
                    lcx = cx; lcy = cy;
                    break;
                }
                case 'V': {
                    double y = nextNum(tokens, i++);
                    if (!abs) y += cy;
                    current.addPoint(new Point2D(cx, y));
                    cy = y;
                    lcx = cx; lcy = cy;
                    break;
                }
                case 'C': {
                    double x1 = nextNum(tokens, i++), y1 = nextNum(tokens, i++);
                    double x2 = nextNum(tokens, i++), y2 = nextNum(tokens, i++);
                    double x = nextNum(tokens, i++), y = nextNum(tokens, i++);
                    if (!abs) { x1+=cx;y1+=cy;x2+=cx;y2+=cy;x+=cx;y+=cy; }
                    Polyline seg = flattener.flattenCubic(new Point2D(cx,cy), new Point2D(x1,y1),
                            new Point2D(x2,y2), new Point2D(x,y));
                    addFlattened(current, seg);
                    lcx = x2; lcy = y2;
                    cx = x; cy = y;
                    break;
                }
                case 'S': {
                    double x2 = nextNum(tokens, i++), y2 = nextNum(tokens, i++);
                    double x = nextNum(tokens, i++), y = nextNum(tokens, i++);
                    if (!abs) { x2+=cx;y2+=cy;x+=cx;y+=cy; }
                    // Reflect last control point
                    double x1 = 2*cx - lcx, y1 = 2*cy - lcy;
                    Polyline seg = flattener.flattenCubic(new Point2D(cx,cy), new Point2D(x1,y1),
                            new Point2D(x2,y2), new Point2D(x,y));
                    addFlattened(current, seg);
                    lcx = x2; lcy = y2;
                    cx = x; cy = y;
                    break;
                }
                case 'Q': {
                    double x1 = nextNum(tokens, i++), y1 = nextNum(tokens, i++);
                    double x = nextNum(tokens, i++), y = nextNum(tokens, i++);
                    if (!abs) { x1+=cx;y1+=cy;x+=cx;y+=cy; }
                    Polyline seg = flattener.flattenQuadratic(new Point2D(cx,cy),
                            new Point2D(x1,y1), new Point2D(x,y));
                    addFlattened(current, seg);
                    lcx = x1; lcy = y1;
                    cx = x; cy = y;
                    break;
                }
                case 'T': {
                    double x = nextNum(tokens, i++), y = nextNum(tokens, i++);
                    if (!abs) { x+=cx;y+=cy; }
                    double x1 = 2*cx - lcx, y1 = 2*cy - lcy;
                    Polyline seg = flattener.flattenQuadratic(new Point2D(cx,cy),
                            new Point2D(x1,y1), new Point2D(x,y));
                    addFlattened(current, seg);
                    lcx = x1; lcy = y1;
                    cx = x; cy = y;
                    break;
                }
                case 'A': {
                    double rx = nextNum(tokens, i++), ry = nextNum(tokens, i++);
                    double rotation = nextNum(tokens, i++);
                    double largeArc = nextNum(tokens, i++);
                    double sweep = nextNum(tokens, i++);
                    double x = nextNum(tokens, i++), y = nextNum(tokens, i++);
                    if (!abs) { x += cx; y += cy; }
                    flattenArc(current, cx, cy, rx, ry, rotation, largeArc != 0, sweep != 0, x, y);
                    cx = x; cy = y;
                    lcx = cx; lcy = cy;
                    break;
                }
                case 'Z': {
                    current.addPoint(new Point2D(sx, sy));
                    subPaths.add(current);
                    current = new Polyline();
                    current.addPoint(new Point2D(sx, sy));
                    cx = sx; cy = sy;
                    lcx = cx; lcy = cy;
                    break;
                }
                default:
                    i++; // skip unknown
            }
        }
        if (current.getPoints().size() > 1) subPaths.add(current);
        return subPaths;
    }

    // ─── Tokenizer ───

    private static class Token {
        boolean isCommand;
        char command;
        double number;
        Token(char c) { isCommand = true; command = c; }
        Token(double n) { isCommand = false; number = n; }
    }

    private static List<Token> tokenize(String d) {
        List<Token> tokens = new ArrayList<>();
        // Regex: match commands or numbers (including negative, decimals, exponents)
        Pattern p = Pattern.compile("[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?");
        Matcher m = p.matcher(d);
        while (m.find()) {
            String s = m.group();
            if (s.length() == 1 && "MmLlHhVvCcSsQqTtAaZz".indexOf(s.charAt(0)) >= 0) {
                tokens.add(new Token(s.charAt(0)));
            } else {
                try { tokens.add(new Token(Double.parseDouble(s))); }
                catch (NumberFormatException ignored) {}
            }
        }
        return tokens;
    }

    private static double nextNum(List<Token> tokens, int idx) {
        if (idx < tokens.size() && !tokens.get(idx).isCommand) return tokens.get(idx).number;
        return 0;
    }

    private void addFlattened(Polyline target, Polyline bezierResult) {
        List<Point2D> pts = bezierResult.getPoints();
        // Skip first point (it's the current point, already in target)
        for (int j = 1; j < pts.size(); j++) target.addPoint(pts.get(j));
    }

    // ─── Arc flattening (SVG arc → polyline) ───

    private void flattenArc(Polyline target, double x1, double y1,
                            double rx, double ry, double phiDeg,
                            boolean largeArc, boolean sweep, double x2, double y2) {
        if (rx == 0 || ry == 0) { target.addPoint(new Point2D(x2, y2)); return; }
        rx = Math.abs(rx); ry = Math.abs(ry);
        double phi = Math.toRadians(phiDeg);
        double cosPhi = Math.cos(phi), sinPhi = Math.sin(phi);

        double dx = (x1 - x2) / 2, dy = (y1 - y2) / 2;
        double x1p = cosPhi * dx + sinPhi * dy;
        double y1p = -sinPhi * dx + cosPhi * dy;

        double lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry);
        if (lambda > 1) { double s = Math.sqrt(lambda); rx *= s; ry *= s; }

        double num = Math.max(0, rx*rx*ry*ry - rx*rx*y1p*y1p - ry*ry*x1p*x1p);
        double den = rx*rx*y1p*y1p + ry*ry*x1p*x1p;
        double sq = Math.sqrt(num / den) * (largeArc == sweep ? -1 : 1);
        double cxp = sq * rx * y1p / ry;
        double cyp = -sq * ry * x1p / rx;

        double ccx = cosPhi * cxp - sinPhi * cyp + (x1 + x2) / 2;
        double ccy = sinPhi * cxp + cosPhi * cyp + (y1 + y2) / 2;

        double theta1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry);
        double dtheta = angle((x1p - cxp) / rx, (y1p - cyp) / ry,
                (-x1p - cxp) / rx, (-y1p - cyp) / ry);
        if (!sweep && dtheta > 0) dtheta -= 2 * Math.PI;
        if (sweep && dtheta < 0) dtheta += 2 * Math.PI;

        // Approximate arc with line segments
        int segments = Math.max(8, (int)(Math.abs(dtheta) / (Math.PI / 16)));
        for (int j = 1; j <= segments; j++) {
            double t = theta1 + dtheta * j / segments;
            double ex = cosPhi * rx * Math.cos(t) - sinPhi * ry * Math.sin(t) + ccx;
            double ey = sinPhi * rx * Math.cos(t) + cosPhi * ry * Math.sin(t) + ccy;
            target.addPoint(new Point2D(ex, ey));
        }
    }

    private static double angle(double ux, double uy, double vx, double vy) {
        double dot = ux * vx + uy * vy;
        double len = Math.sqrt(ux*ux + uy*uy) * Math.sqrt(vx*vx + vy*vy);
        double a = Math.acos(Math.max(-1, Math.min(1, dot / len)));
        if (ux * vy - uy * vx < 0) a = -a;
        return a;
    }
}
