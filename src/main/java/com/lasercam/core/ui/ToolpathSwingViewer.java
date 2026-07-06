package com.lasercam.core.ui;

import com.lasercam.core.core.LaserCamEngine;
import com.lasercam.core.fill.ScanlineFill;
import com.lasercam.core.fill.SpiralFill;
import com.lasercam.core.io.DxfExporterR2000;
import com.lasercam.core.io.GcodeExporter;
import com.lasercam.core.model.*;
import com.lasercam.core.optimizer.ToolpathOptimizer;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.io.File;
import java.util.*;
import java.util.List;

public class ToolpathSwingViewer extends JFrame {

    private final Canvas2D canvas;
    private final JLabel statsLabel, coordLabel;
    private List<PolygonWithHoles> polygons = List.of();
    private Toolpath toolpath = new Toolpath();

    private JComboBox<String> fillCombo;
    private JSpinner spPitch, spAngle, spAngle2, spFeed, spPower;
    private JCheckBox cbContour, cbHoleOutline, cbOptimize, cbCut, cbTravel, cbBound, cbGrid, cbMill;
    private JRadioButton rbContour, rbSkip;
    private JSpinner spOffset, spZDepth, spZStep, spZSafe, spZFeed;
    private JButton btnAnim;

    static final Font FB = new Font(Font.SANS_SERIF, Font.BOLD, 16);
    static final Font FN = new Font(Font.SANS_SERIF, Font.PLAIN, 18);
    static final Font FS = new Font(Font.SANS_SERIF, Font.BOLD, 16);
    static final Font FL = new Font(Font.SANS_SERIF, Font.PLAIN, 15);
    static final Font FM = new Font(Font.MONOSPACED, Font.PLAIN, 16);

    static final Color BG=new Color(0x1a1a24), BG2=new Color(0x22222e), BD=new Color(0x33334a);
    static final Color FG=new Color(0xddddee), DIM=new Color(0x9999bb);
    static final Color GRN=new Color(0x00dd77), ORG=new Color(0xff7744), BLU=new Color(0x44aaee);
    static final Color AMB=new Color(0xffbb33), RED=new Color(0xff4455);
    static final Color BFG=new Color(0x222230);
    static final Color B1=new Color(0xe8e8f0), B2=new Color(0xc8f5d8), B3=new Color(0xfff0c8), B4=new Color(0xffe0cc);
    static final Color FBG=new Color(0xf0f0f6);

    public ToolpathSwingViewer() {
        super("LaserCAM Engine");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        canvas = new Canvas2D();

        JPanel innerPanel = buildPanel();
        JScrollPane lp = new JScrollPane(innerPanel,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        lp.setPreferredSize(new Dimension(320, 0));
        lp.setBorder(BorderFactory.createMatteBorder(0,0,0,1,BD));
        lp.getVerticalScrollBar().setUnitIncrement(16);
        lp.getViewport().addChangeListener(ev -> {
            int vw = lp.getViewport().getWidth();
            // Get natural preferred height from layout, not cached
            innerPanel.setPreferredSize(null);
            Dimension pref = innerPanel.getPreferredSize();
            innerPanel.setPreferredSize(new Dimension(vw, pref.height));
            innerPanel.revalidate();
        });

        JPanel bot = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 6));
        bot.setBackground(BG2);
        statsLabel = new JLabel("Open an SVG/PDF file"); statsLabel.setFont(FM); statsLabel.setForeground(DIM);
        coordLabel = new JLabel("X: —  Y: —"); coordLabel.setFont(FM); coordLabel.setForeground(new Color(0x667788));
        bot.add(statsLabel); bot.add(Box.createHorizontalStrut(20)); bot.add(coordLabel);

        setLayout(new BorderLayout());
        add(lp, BorderLayout.WEST); add(canvas, BorderLayout.CENTER); add(bot, BorderLayout.SOUTH);
        setSize(1400, 930); setLocationRelativeTo(null);
    }

    private JPanel buildPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(BG2);
        int r = 0;

        // ── FILE ──
        addSec(p, r++, "FILE");
        addRight(p, r++, btn("Open SVG / PDF...", B2));

        addSep(p, r++);

        // ── FILL ──
        addSec(p, r++, "FILL");
        fillCombo = new JComboBox<>(new String[]{"None", "Scanline", "Spiral", "Cross-hatch"});
        fillCombo.setFont(FN); fillCombo.setBackground(FBG); fillCombo.setForeground(BFG);
        fillCombo.addActionListener(e -> onFillChanged());
        addRight(p, r++, fillCombo);

        spPitch = sp(3.0, 0.1, 50, 0.1);  addRow(p, r++, "Pitch mm", spPitch);
        spAngle = sp(0, 0, 359, 1);        addRow(p, r++, "Angle °", spAngle);
        spAngle2 = sp(90, 0, 359, 1); spAngle2.setEnabled(false);
                                            addRow(p, r++, "2nd angle °", spAngle2);
        spOffset = sp(0, 0, 20, 0.1);       addRow(p, r++, "Kerf mm", spOffset);

        // Hole handling — 2 radio buttons (Column / Jump)
        rbContour = radio("Column", true);
        rbSkip = radio("Jump", false);
        ButtonGroup bg = new ButtonGroup();
        bg.add(rbContour); bg.add(rbSkip);
        JPanel holeRow = new JPanel(new GridLayout(1, 2, 4, 0));
        holeRow.setBackground(BG2);
        holeRow.add(rbContour); holeRow.add(rbSkip);
        addRow(p, r++, "Holes", holeRow);

        cbContour = chk("Cut outline"); cbOptimize = chk("Optimize path"); cbOptimize.setSelected(true);
        cbHoleOutline = chk("Hole outline");
        addRight2(p, r++, cbContour, cbHoleOutline);
        addRight(p, r++, cbOptimize);
        addRight(p, r++, btn("▸  Generate", B2));

        addSep(p, r++);

        // ── DISPLAY ──
        addSec(p, r++, "DISPLAY");
        cbCut = chk("Cut", GRN); cbCut.setSelected(true);
        cbTravel = chk("Travel", ORG); cbTravel.setSelected(true);
        cbBound = chk("Boundary", BLU); cbBound.setSelected(true);
        cbGrid = chk("Grid", DIM); cbGrid.setSelected(true);
        cbCut.addActionListener(e -> canvas.repaint()); cbTravel.addActionListener(e -> canvas.repaint());
        cbBound.addActionListener(e -> canvas.repaint()); cbGrid.addActionListener(e -> canvas.repaint());
        addRight2(p, r++, cbCut, cbTravel);
        addRight2(p, r++, cbBound, cbGrid);
        addRight(p, r++, btn("Fit view", B1));
        btnAnim = btn("▶  Animate", B4);
        btnAnim.addActionListener(e -> toggleAnim());
        addRight(p, r++, btnAnim);

        addSep(p, r++);

        // ── EXPORT ──
        addSec(p, r++, "EXPORT");
        spFeed = sp(1000, 50, 20000, 50);  addRow(p, r++, "Feed mm/min", spFeed);
        spPower = sp(255, 0, 1000, 5);      addRow(p, r++, "Power / RPM", spPower);
        cbMill = chk("Mill mode (multi-pass Z)");
        addRight(p, r++, cbMill);

        // Z fields — remembered for later toggle
        spZDepth = sp(3.0, 0.1, 100, 0.1);
        spZStep  = sp(0.5, 0.05, 20, 0.05);
        spZSafe  = sp(5.0, 0.5, 50, 0.5);
        spZFeed  = sp(300, 10, 5000, 10);
        JPanel rowZDepth = fieldRow("Z depth mm", spZDepth); rowZDepth.setVisible(false);
        JPanel rowZStep  = fieldRow("Z step mm",  spZStep);  rowZStep.setVisible(false);
        JPanel rowZSafe  = fieldRow("Z safe mm",  spZSafe);  rowZSafe.setVisible(false);
        JPanel rowZFeed  = fieldRow("Z feed mm/min", spZFeed); rowZFeed.setVisible(false);
        addRightPanel(p, r++, rowZDepth);
        addRightPanel(p, r++, rowZStep);
        addRightPanel(p, r++, rowZSafe);
        addRightPanel(p, r++, rowZFeed);

        // Export buttons: DXF | G-code side by side (DXF hides in Mill mode)
        JButton bDxf = btn("DXF", B3);
        JButton bGc  = btn("G-code", B3);
        bDxf.addActionListener(e -> exportDxf());
        bGc.addActionListener(e -> exportGcode());
        JPanel expRow = new JPanel(new GridLayout(1, 2, 8, 0));
        expRow.setBackground(BG2);
        expRow.add(bDxf); expRow.add(bGc);
        JPanel expOnly = new JPanel(new GridLayout(1, 1));
        expOnly.setBackground(BG2);
        // Container that swaps between the two layouts
        JPanel expContainer = new JPanel(new BorderLayout());
        expContainer.setBackground(BG2);
        expContainer.add(expRow, BorderLayout.CENTER);
        addRight(p, r++, expContainer);

        cbMill.addActionListener(e -> {
            boolean on = cbMill.isSelected();
            rowZDepth.setVisible(on); rowZStep.setVisible(on);
            rowZSafe.setVisible(on); rowZFeed.setVisible(on);
            bDxf.setVisible(!on);
            SwingUtilities.invokeLater(() -> {
                p.setPreferredSize(null);
                p.revalidate();
                // Walk up to the JScrollPane and force it to update
                Container c = p.getParent();
                while (c != null && !(c instanceof JScrollPane)) c = c.getParent();
                if (c != null) {
                    JScrollPane sp = (JScrollPane) c;
                    int vw = sp.getViewport().getWidth();
                    Dimension pref = p.getPreferredSize();
                    p.setPreferredSize(new Dimension(vw, pref.height));
                    p.revalidate();
                    sp.revalidate();
                    sp.repaint();
                }
            });
        });

        // filler
        var gc = gbc(0, r, 2); gc.weighty = 1;
        p.add(Box.createVerticalGlue(), gc);

        // wire buttons
        wireButtons(p);
        return p;
    }

    // ── grid helpers ──

    private GridBagConstraints gbc(int x, int y, int w) {
        var gc = new GridBagConstraints();
        gc.gridx = x; gc.gridy = y; gc.gridwidth = w;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(3, 6, 3, 6);
        return gc;
    }

    private void addSec(JPanel p, int row, String text) {
        var gc = gbc(0, row, 2);
        gc.insets = new Insets(6, 6, 2, 6);
        JLabel l = new JLabel(text); l.setFont(FS); l.setForeground(GRN);
        p.add(l, gc);
    }

    private void addRow(JPanel p, int row, String label, JComponent field) {
        var gc1 = gbc(0, row, 1); gc1.weightx = 0;
        JLabel l = new JLabel(label); l.setFont(FL); l.setForeground(DIM);
        p.add(l, gc1);
        var gc2 = gbc(1, row, 1); gc2.weightx = 1;
        p.add(field, gc2);
    }

    /** Row that can be shown/hidden as a single component. */
    private JPanel fieldRow(String label, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(BG2);
        JLabel l = new JLabel(label); l.setFont(FL); l.setForeground(DIM);
        l.setPreferredSize(new Dimension(120, 24));
        row.add(l, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    /** Place a panel spanning both grid columns. */
    private void addRightPanel(JPanel p, int row, JPanel panel) {
        var gc = gbc(0, row, 2); gc.weightx = 1;
        p.add(panel, gc);
    }

    private void addRight(JPanel p, int row, JComponent c) {
        var gc = gbc(1, row, 1); gc.weightx = 1;
        p.add(c, gc);
    }

    private void addRight2(JPanel p, int row, JComponent a, JComponent b) {
        JPanel rp = new JPanel(new GridLayout(1, 2, 4, 0));
        rp.setBackground(BG2);
        rp.add(a); rp.add(b);
        addRight(p, row, rp);
    }

    private void addSep(JPanel p, int row) {
        var gc = gbc(0, row, 2);
        gc.insets = new Insets(6, 6, 6, 6);
        JSeparator s = new JSeparator(); s.setForeground(BD);
        p.add(s, gc);
    }

    private void wireButtons(JPanel p) {
        for (Component c : p.getComponents()) {
            if (c instanceof JButton bt) {
                String t = bt.getText();
                if (t.contains("Open")) bt.addActionListener(e -> openFile());
                else if (t.contains("Generate")) bt.addActionListener(e -> regenerate());
                else if (t.contains("Fit")) bt.addActionListener(e -> { canvas.fitToView(); canvas.repaint(); });
                else if (t.contains("G-code")) bt.addActionListener(e -> exportGcode());
                else if (t.contains("DXF")) bt.addActionListener(e -> exportDxf());
            } else if (c instanceof JPanel jp) {
                for (Component cc : jp.getComponents())
                    if (cc instanceof JButton) wireButtons(jp);
            }
        }
    }

    private void onFillChanged() {
        int i = fillCombo.getSelectedIndex();
        boolean isNone = (i == 0);
        boolean isSpiral = (i == 2);
        boolean isCrossHatch = (i == 3);

        spAngle.setEnabled(!isSpiral && !isNone);
        spAngle2.setEnabled(isCrossHatch);
        spPitch.setEnabled(!isNone);

        // Spiral only supports Jump — disable Column
        rbContour.setEnabled(!isSpiral && !isNone);
        rbSkip.setEnabled(!isNone);
        if (isSpiral && rbContour.isSelected()) rbSkip.setSelected(true);

        // Optimize only affects Cross-hatch merging
        cbOptimize.setEnabled(isCrossHatch);
    }

    // ── actions ──

    private void openFile() {
        JFileChooser fc = new JFileChooser(System.getProperty("user.dir"));
        fc.setDialogTitle("Open SVG / PDF");
        fc.setFileFilter(new FileNameExtensionFilter("SVG, PDF", "svg", "pdf"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) loadFile(fc.getSelectedFile());
    }

    public void loadFile(File file) {
        try {
            // Load only geometry — user runs Generate manually
            var eng = new LaserCamEngine(new ScanlineFill(3));
            var polylines = eng.importFile(file.getAbsolutePath());
            polygons = eng.buildAllPolygons(polylines);
            toolpath = new Toolpath();
            setTitle("LaserCAM — " + file.getName());
            canvas.fitToView(); canvas.repaint(); updateStats();
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); ex.printStackTrace(); }
    }

    private void regenerate() {
        if (polygons.isEmpty()) return;
        double pitch = ((Number) spPitch.getValue()).doubleValue();
        double angle = ((Number) spAngle.getValue()).doubleValue();
        double offset = ((Number) spOffset.getValue()).doubleValue();
        int fi = fillCombo.getSelectedIndex();

        ScanlineFill.HoleMode hMode = rbContour.isSelected() ? ScanlineFill.HoleMode.CONTOUR
                                                             : ScanlineFill.HoleMode.SKIP;

        var offsetter = new com.lasercam.core.geometry.OffsetPolygon();

        Toolpath tp = new Toolpath();

        // 1. Hole outline (inner, -kerf) — first
        if (cbHoleOutline.isSelected()) {
            for (var poly : polygons) {
                if (offset > 0) {
                    var outerInset = offsetter.offset(poly.getOuter(), -offset);
                    tp.addCutPolyline(outerInset != null ? outerInset : poly.getOuter());
                    for (var h : poly.getHoles()) {
                        var holeInset = offsetter.offset(h, -offset);
                        tp.addCutPolyline(holeInset != null ? holeInset : h);
                    }
                } else {
                    tp.addCutPolyline(poly.getOuter());
                    for (var h : poly.getHoles()) tp.addCutPolyline(h);
                }
            }
        }

        // 2. Fill
        for (var poly : polygons) {
            if (fi == 1) {
                ScanlineFill f = new ScanlineFill(pitch, angle);
                f.setHoleMode(hMode); f.setHoleOffset(offset);
                merge(tp, f.generate(poly));
            } else if (fi == 2) {
                SpiralFill sf = new SpiralFill(pitch);
                sf.setKerf(offset);
                sf.setHoleMode(SpiralFill.HoleMode.JUMP);
                merge(tp, sf.generate(poly));
            } else if (fi == 3) {
                ScanlineFill f1 = new ScanlineFill(pitch, angle);
                ScanlineFill f2 = new ScanlineFill(pitch, ((Number) spAngle2.getValue()).doubleValue());
                f1.setHoleMode(hMode); f1.setHoleOffset(offset);
                f2.setHoleMode(hMode); f2.setHoleOffset(offset);
                merge(tp, f1.generate(poly));
                merge(tp, f2.generate(poly));
            }
            // fi == 0 → None: no fill (only outline is emitted above if Cut outline checked)
        }
        if (cbOptimize.isSelected() && fi == 3) tp = new ToolpathOptimizer().optimize(tp);

        // 3. Cut outline (outer, +kerf) — LAST operation, cuts the part free
        if (cbContour.isSelected()) {
            for (var poly : polygons) {
                if (offset > 0) {
                    var outerExp = offsetter.offset(poly.getOuter(), offset);
                    tp.addCutPolyline(outerExp != null ? outerExp : poly.getOuter());
                    for (var h : poly.getHoles()) {
                        var holeExp = offsetter.offset(h, offset);
                        tp.addCutPolyline(holeExp != null ? holeExp : h);
                    }
                } else {
                    tp.addCutPolyline(poly.getOuter());
                    for (var h : poly.getHoles()) tp.addCutPolyline(h);
                }
            }
        }

        // Hole outline was moved to the top of this method
        toolpath = tp; canvas.stopAnimation(); canvas.repaint(); updateStats();
    }

    private void merge(Toolpath d, Toolpath s) { for (var seg : s.getSegments()) d.addSegment(seg); }
    private void exportDxf() { if (toolpath.getSegments().isEmpty()) return; JFileChooser fc = saver("toolpath.dxf","DXF","dxf"); if (fc.showSaveDialog(this)==JFileChooser.APPROVE_OPTION) try{new DxfExporterR2000().export(toolpath,fc.getSelectedFile().getAbsolutePath(),true);JOptionPane.showMessageDialog(this,"DXF saved!");}catch(Exception ex){err(ex);} }
    private void exportGcode() {
        if (toolpath.getSegments().isEmpty()) return;
        JFileChooser fc = saver("toolpath.nc","G-code","nc","gcode");
        if (fc.showSaveDialog(this)==JFileChooser.APPROVE_OPTION) try {
            double f = dbl(spFeed), pw = dbl(spPower);
            var exp = new GcodeExporter("GRBL");
            if (cbMill.isSelected()) {
                exp.setMillMode(dbl(spZDepth), dbl(spZStep), dbl(spZSafe), dbl(spZFeed));
            }
            exp.export(toolpath, fc.getSelectedFile().getAbsolutePath(), f, f*0.6, pw);
            JOptionPane.showMessageDialog(this,"G-code saved!");
        } catch(Exception ex) { err(ex); }
    }
    private JFileChooser saver(String n, String d, String... e) { JFileChooser fc = new JFileChooser(System.getProperty("user.dir")); fc.setSelectedFile(new File(n)); fc.setFileFilter(new FileNameExtensionFilter(d, e)); return fc; }
    private void err(Exception ex) { JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); }
    private void toggleAnim() { if (canvas.isAnimating()) { canvas.stopAnimation(); btnAnim.setText("▶  Animate"); } else { canvas.startAnimation(); btnAnim.setText("■  Stop"); } }
    private double dbl(JSpinner s) { return ((Number) s.getValue()).doubleValue(); }

    private void updateStats() {
        double cut = 0, trv = 0; int c = 0; Point2D last = null;
        for (var s : toolpath.getSegments()) { double d = s.start().dist(s.end()); if (s.isCut()) { cut += d; c++; } else trv += d; if (last != null) { double g = last.dist(s.start()); if (g > 0.01) trv += g; } last = s.end(); }
        statsLabel.setText(String.format("Seg: %d  Cut: %.1fmm  Travel: %.1fmm  Eff: %.0f%%", c, cut, trv, cut + trv > 0 ? cut / (cut + trv) * 100 : 0));
    }

    public static void show(Toolpath tp, List<PolygonWithHoles> polys) { SwingUtilities.invokeLater(() -> { var v = new ToolpathSwingViewer(); v.toolpath = tp; v.polygons = polys; v.setVisible(true); v.canvas.fitToView(); v.updateStats(); }); }

    // ── widget builders ──
    private JSpinner sp(double v, double mn, double mx, double s) { JSpinner sp = new JSpinner(new SpinnerNumberModel(v, mn, mx, s)); sp.setFont(FN); if (sp.getEditor() instanceof JSpinner.DefaultEditor de) { de.getTextField().setBackground(FBG); de.getTextField().setForeground(BFG); de.getTextField().setFont(FN); } sp.setPreferredSize(new Dimension(100, 34)); return sp; }
    private JCheckBox chk(String t) { return chk(t, FG); }
    private JCheckBox chk(String t, Color c) { JCheckBox cb = new JCheckBox(t); cb.setFont(FL); cb.setForeground(c); cb.setBackground(BG2); cb.setFocusPainted(false); return cb; }
    private JRadioButton radio(String t, boolean sel) { JRadioButton rb = new JRadioButton(t, sel); rb.setFont(FL); rb.setForeground(FG); rb.setBackground(BG2); rb.setFocusPainted(false); rb.setOpaque(true); return rb; }
    private JButton btn(String t, Color bg) { JButton b = new JButton(t); b.setFont(FB); b.setForeground(BFG); b.setBackground(bg); b.setFocusPainted(false); b.setOpaque(true); b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x888899), 1), BorderFactory.createEmptyBorder(6, 10, 6, 10))); return b; }

    // ══════════════════════════════════════════
    //  CANVAS
    // ══════════════════════════════════════════
    private class Canvas2D extends JPanel {
        double zoom=1,panX=0,panY=0;int animFrame=-1;javax.swing.Timer animTimer;int dsx,dsy;double dpx,dpy;
        Canvas2D(){setBackground(BG);addMouseWheelListener(e->{double f=e.getWheelRotation()<0?1.15:1/1.15;double wx=wX(e.getX()),wy=wY(e.getY());zoom*=f;panX=e.getX()-wx*zoom;panY=e.getY()-wy*zoom;repaint();});addMouseListener(new MouseAdapter(){public void mousePressed(MouseEvent e){dsx=e.getX();dsy=e.getY();dpx=panX;dpy=panY;setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));}public void mouseReleased(MouseEvent e){setCursor(Cursor.getDefaultCursor());}});addMouseMotionListener(new MouseMotionAdapter(){public void mouseDragged(MouseEvent e){panX=dpx+(e.getX()-dsx);panY=dpy+(e.getY()-dsy);repaint();}public void mouseMoved(MouseEvent e){coordLabel.setText(String.format("X: %.2f  Y: %.2f",wX(e.getX()),wY(e.getY())));}});}
        double sX(double wx){return wx*zoom+panX;}double sY(double wy){return wy*zoom+panY;}double wX(double sx){return(sx-panX)/zoom;}double wY(double sy){return(sy-panY)/zoom;}
        void fitToView(){double x0=1e9,y0=1e9,x1=-1e9,y1=-1e9;for(var pp:polygons)if(pp.getOuter()!=null)for(var pt:pp.getOuter().getPoints()){x0=Math.min(x0,pt.x());y0=Math.min(y0,pt.y());x1=Math.max(x1,pt.x());y1=Math.max(y1,pt.y());}for(var s:toolpath.getSegments())for(var pt:new Point2D[]{s.start(),s.end()}){x0=Math.min(x0,pt.x());y0=Math.min(y0,pt.y());x1=Math.max(x1,pt.x());y1=Math.max(y1,pt.y());}if(x0>x1){x0=0;y0=0;x1=100;y1=100;}double gw=Math.max(x1-x0,1),gh=Math.max(y1-y0,1);int w=Math.max(getWidth(),100),h=Math.max(getHeight(),100);zoom=Math.min((w-60)/gw,(h-60)/gh);panX=(w-gw*zoom)/2-x0*zoom;panY=(h-gh*zoom)/2-y0*zoom;}
        boolean isAnimating(){return animTimer!=null&&animTimer.isRunning();}
        void startAnimation(){animFrame=0;int total=toolpath.getSegments().size(),step=Math.max(1,total/300);animTimer=new javax.swing.Timer(12,ev->{animFrame+=step;if(animFrame>=total){animFrame=-1;((javax.swing.Timer)ev.getSource()).stop();btnAnim.setText("▶  Animate");}repaint();});animTimer.start();}
        void stopAnimation(){if(animTimer!=null)animTimer.stop();animFrame=-1;repaint();}
        @Override protected void paintComponent(Graphics g0){super.paintComponent(g0);Graphics2D g=(Graphics2D)g0;g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);if(cbGrid.isSelected())drawGrid(g);if(cbBound.isSelected())drawBounds(g);drawPath(g);}
        private void drawGrid(Graphics2D g){double[]nice={0.1,0.2,0.5,1,2,5,10,20,50,100,200,500,1000};double ws=10;for(double n:nice)if(n*zoom>=20&&n*zoom<=150){ws=n;break;}g.setColor(new Color(0x1e1e2a));g.setStroke(new BasicStroke(0.5f));double sx=Math.floor(wX(0)/ws)*ws,ex=wX(getWidth()),sy=Math.floor(wY(0)/ws)*ws,ey=wY(getHeight());for(double x=sx;x<=ex;x+=ws){int px=(int)sX(x);g.drawLine(px,0,px,getHeight());}for(double y=sy;y<=ey;y+=ws){int py=(int)sY(y);g.drawLine(0,py,getWidth(),py);}g.setColor(new Color(0x2a2a3a));g.setStroke(new BasicStroke(1f));g.drawLine((int)sX(0),0,(int)sX(0),getHeight());g.drawLine(0,(int)sY(0),getWidth(),(int)sY(0));g.setColor(new Color(0x445566));g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,13));for(double x=sx;x<=ex;x+=ws)g.drawString(fmt(x),(int)sX(x)+3,getHeight()-5);}
        private void drawBounds(Graphics2D g){g.setColor(new Color(0x44,0xaa,0xee,0x88));g.setStroke(new BasicStroke(2f));for(var poly:polygons){if(poly.getOuter()!=null)drawPl(g,poly.getOuter());for(Polyline h:poly.getHoles())drawPl(g,h);}}
        private void drawPl(Graphics2D g,Polyline pl){var pts=pl.getPoints();if(pts.size()<2)return;Path2D path=new Path2D.Double();path.moveTo(sX(pts.get(0).x()),sY(pts.get(0).y()));for(int i=1;i<pts.size();i++)path.lineTo(sX(pts.get(i).x()),sY(pts.get(i).y()));g.draw(path);}
        private void drawPath(Graphics2D g){var segs=toolpath.getSegments();int lim=animFrame<0?segs.size():Math.min(animFrame,segs.size());Point2D last=null;for(int i=0;i<lim;i++){var s=segs.get(i);if(cbTravel.isSelected()&&last!=null&&s.isCut()&&last.dist(s.start())>0.01){g.setColor(new Color(0xff,0x77,0x44,0x33));g.setStroke(new BasicStroke(0.8f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10f,new float[]{3,5},0));g.drawLine((int)sX(last.x()),(int)sY(last.y()),(int)sX(s.start().x()),(int)sY(s.start().y()));}if(s.isCut()&&cbCut.isSelected()){g.setColor(GRN);g.setStroke(new BasicStroke(2f));g.drawLine((int)sX(s.start().x()),(int)sY(s.start().y()),(int)sX(s.end().x()),(int)sY(s.end().y()));}else if(!s.isCut()&&cbTravel.isSelected()){g.setColor(new Color(0xff,0x77,0x44,0x99));g.setStroke(new BasicStroke(1f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10f,new float[]{4,4},0));g.drawLine((int)sX(s.start().x()),(int)sY(s.start().y()),(int)sX(s.end().x()),(int)sY(s.end().y()));}last=s.end();}if(animFrame>=0&&lim>0){var h=segs.get(lim-1).end();g.setColor(new Color(0xff,0x44,0x55,0x55));g.fillOval((int)sX(h.x())-10,(int)sY(h.y())-10,20,20);g.setColor(RED);g.fillOval((int)sX(h.x())-5,(int)sY(h.y())-5,10,10);}}
        String fmt(double v){return v==(long)v?String.valueOf((long)v):String.format("%.1f",v);}
    }
}
