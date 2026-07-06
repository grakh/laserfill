package com.lasercam.core;

import com.lasercam.core.ui.ToolpathSwingViewer;
import javax.swing.*;

/**
 * Launch the viewer. No auto-open — user clicks "Open SVG" in the UI.
 *
 *   java -jar lasercam.jar                  (empty viewer)
 *   java -jar lasercam.jar file.svg         (loads file on start)
 */
public class LaserCamApp {
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        String file = args.length > 0 ? args[0] : null;
        SwingUtilities.invokeLater(() -> {
            ToolpathSwingViewer v = new ToolpathSwingViewer();
            v.setVisible(true);
            if (file != null) v.loadFile(new java.io.File(file));
        });
    }
}
