# LaserCAM Engine — Installation

## Requirements

- **Java 17+** — required
- **Python 3 + pymupdf** — optional, only for PDF import

## Quick install

### Linux / macOS

```bash
chmod +x install.sh
./install.sh
```

The installer checks Java and Python, offers to install missing dependencies via your package manager (apt/dnf/pacman/brew), and installs `pymupdf` via pip.

After install, run:
```bash
./lasercam                   # opens file chooser
./lasercam drawing.svg       # loads file immediately
./lasercam drawing.pdf       # PDF too
```

### Windows

Double-click `install.bat` (or run in cmd/PowerShell).

The installer checks Java and Python. If either is missing, it prints direct download links (adoptium.net for Java, python.org for Python). If Python is present, it installs `pymupdf` automatically.

After install, run:
```
lasercam.bat
java -jar lasercam.jar
java -jar lasercam.jar drawing.svg
```

Or just double-click `lasercam.jar`.

## Manual install (if scripts don't work)

1. Install Java 17+ from https://adoptium.net/
2. (Optional, for PDF) Install Python 3 from https://python.org/downloads/
3. (Optional, for PDF) Run: `pip install pymupdf`
4. Run: `java -jar lasercam.jar`

## Contents

- `lasercam.jar` — main application (includes embedded `pdf2svg.py`)
- `install.sh` — Linux/macOS installer
- `install.bat` — Windows installer
- `pdf2svg.py` — PDF→SVG converter (standalone copy, JAR has its own)
- `build.sh` — build script for developers
- `src/` — Java sources
- `README.md` — engine architecture docs
