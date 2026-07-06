package com.lasercam.core.io;

import com.lasercam.core.model.Polyline;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF importer. Converts PDF → SVG using pymupdf (via Python subprocess),
 * then parses the SVG with SvgImporter.
 *
 * Requires: python3 with pymupdf installed (pip install pymupdf)
 */
public class PdfImporter {

    private final double tolerance;

    public PdfImporter(double tolerance) {
        this.tolerance = tolerance;
    }

    /**
     * Import vector paths from a PDF file.
     *
     * @param pdfPath  path to the PDF file
     * @param page     page number (0-based), or -1 for all pages
     * @return list of polylines extracted from the PDF
     */
    /** Try to find working Python 3 command. */
    private static String findPython() {
        for (String cmd : new String[]{"python3", "python", "py"}) {
            try {
                Process p = new ProcessBuilder(cmd, "-c", "import sys; print(sys.version_info[0])")
                        .redirectErrorStream(true).start();
                String out;
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    out = br.readLine();
                }
                p.waitFor();
                if (out != null && out.trim().startsWith("3")) return cmd;
            } catch (Exception ignored) {}
        }
        return null;
    }

    public List<Polyline> importPdf(String pdfPath, int page) throws Exception {
        String python = findPython();
        if (python == null) {
            throw new RuntimeException(
                "Python 3 not found in PATH.\n" +
                "PDF import requires Python 3 with pymupdf.\n\n" +
                "Install:\n" +
                "  1) Install Python 3 from python.org\n" +
                "  2) Run: pip install pymupdf");
        }

        String scriptPath = findScript();
        if (scriptPath == null) {
            throw new FileNotFoundException(
                "pdf2svg.py not found next to lasercam.jar.\n" +
                "Extract pdf2svg.py from the archive and place it in the same folder.");
        }

        // Verify pymupdf is installed
        try {
            Process check = new ProcessBuilder(python, "-c", "import fitz")
                    .redirectErrorStream(true).start();
            String checkOut;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(check.getInputStream()))) {
                checkOut = br.lines().reduce("", (a, b) -> a + b);
            }
            if (check.waitFor() != 0) {
                throw new RuntimeException(
                    "pymupdf not installed.\n\n" +
                    "Run in terminal:\n" +
                    "  " + python + " -m pip install pymupdf");
            }
        } catch (RuntimeException re) { throw re; }
        catch (Exception e) { /* fall through - script may still work */ }

        Path tempSvg = Files.createTempFile("lasercam_pdf_", ".svg");
        try {
            String pageArg = page < 0 ? "all" : String.valueOf(page);
            ProcessBuilder pb = new ProcessBuilder(python, scriptPath, pdfPath, tempSvg.toString(), pageArg);
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            String output;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                output = br.lines().reduce("", (a, b) -> a + "\n" + b).trim();
            }
            int exitCode = proc.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("PDF conversion failed:\n" + output);
            }

            SvgImporter svg = new SvgImporter(tolerance);
            return svg.importSvg(tempSvg.toString());
        } finally {
            Files.deleteIfExists(tempSvg);
        }
    }

    /** Import first page. */
    public List<Polyline> importPdf(String pdfPath) throws Exception {
        return importPdf(pdfPath, 0);
    }

    /**
     * Check if PDF import is available (python3 + pymupdf installed).
     */
    public static boolean isAvailable() {
        try {
            Process p = new ProcessBuilder("python3", "-c", "import fitz; print('ok')")
                    .redirectErrorStream(true).start();
            String out;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                out = br.readLine();
            }
            p.waitFor();
            return "ok".equals(out);
        } catch (Exception e) {
            return false;
        }
    }

    private String findScript() throws IOException {
        // 1. Check filesystem locations first (allows user override)
        List<String> dirs = new ArrayList<>();
        dirs.add(System.getProperty("user.dir"));
        String jarDir = getJarDir();
        if (jarDir != null) {
            dirs.add(jarDir);
            File parent = new File(jarDir).getParentFile();
            if (parent != null) dirs.add(parent.getAbsolutePath());
        }
        for (String dir : dirs) {
            if (dir == null) continue;
            File f = new File(dir, "pdf2svg.py");
            if (f.exists()) return f.getAbsolutePath();
        }

        // 2. Fallback: extract embedded script from JAR resources
        try (InputStream in = getClass().getResourceAsStream("/pdf2svg.py")) {
            if (in != null) {
                Path extracted = Files.createTempFile("lasercam_pdf2svg_", ".py");
                extracted.toFile().deleteOnExit();
                Files.copy(in, extracted, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return extracted.toAbsolutePath().toString();
            }
        }
        return null;
    }

    private String getJarDir() {
        try {
            String path = getClass().getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            return new File(path).getParent();
        } catch (Exception e) {
            return null;
        }
    }
}
