#!/bin/bash
# LaserCAM installer for Linux / macOS
# Usage: chmod +x install.sh && ./install.sh

set -e

# ─── Colors ───
G='\033[0;32m'  # green
Y='\033[1;33m'  # yellow
R='\033[0;31m'  # red
B='\033[0;34m'  # blue
N='\033[0m'     # no color

echo -e "${B}╔══════════════════════════════════════╗${N}"
echo -e "${B}║   LaserCAM Engine — Installer        ║${N}"
echo -e "${B}╚══════════════════════════════════════╝${N}"
echo

# ─── Detect OS ───
OS="unknown"
if [[ "$OSTYPE" == "linux-gnu"* ]]; then OS="linux"
elif [[ "$OSTYPE" == "darwin"* ]]; then OS="mac"
fi
echo -e "OS: ${G}$OS${N}"

# ─── Java check ───
echo
echo -e "${B}[1/3] Checking Java…${N}"
JAVA_OK=false
if command -v java &> /dev/null; then
    JAVA_VER=$(java -version 2>&1 | head -n1 | grep -oP '"\K[0-9]+' | head -1)
    if [ "$JAVA_VER" -ge 17 ] 2>/dev/null; then
        echo -e "  ${G}✓${N} Java $JAVA_VER found"
        JAVA_OK=true
    else
        echo -e "  ${Y}!${N} Java $JAVA_VER found, need 17+"
    fi
else
    echo -e "  ${R}✗${N} Java not found"
fi

if [ "$JAVA_OK" = false ]; then
    echo -e "  ${Y}Install Java 17+:${N}"
    if [ "$OS" = "linux" ]; then
        echo "    Ubuntu/Debian:  sudo apt install openjdk-17-jre"
        echo "    Fedora/RHEL:    sudo dnf install java-17-openjdk"
        echo "    Arch:           sudo pacman -S jre17-openjdk"
    elif [ "$OS" = "mac" ]; then
        echo "    Homebrew:       brew install openjdk@17"
        echo "    Or download:    https://adoptium.net/"
    fi
    echo
    read -p "Attempt automatic install? [y/N] " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        if [ "$OS" = "linux" ]; then
            if command -v apt &> /dev/null; then sudo apt update && sudo apt install -y openjdk-17-jre
            elif command -v dnf &> /dev/null; then sudo dnf install -y java-17-openjdk
            elif command -v pacman &> /dev/null; then sudo pacman -S --noconfirm jre17-openjdk
            else echo -e "  ${R}Unsupported package manager. Install manually.${N}"; exit 1
            fi
        elif [ "$OS" = "mac" ]; then
            if command -v brew &> /dev/null; then brew install openjdk@17
            else echo -e "  ${R}Homebrew not found. Install from https://brew.sh/${N}"; exit 1
            fi
        fi
    else
        echo -e "  ${R}Java is required. Install and re-run.${N}"
        exit 1
    fi
fi

# ─── Python check ───
echo
echo -e "${B}[2/3] Checking Python 3…${N}"
PYTHON=""
for cmd in python3 python py; do
    if command -v $cmd &> /dev/null; then
        VER=$($cmd -c "import sys; print(sys.version_info[0])" 2>/dev/null || echo "")
        if [ "$VER" = "3" ]; then PYTHON=$cmd; break; fi
    fi
done

if [ -n "$PYTHON" ]; then
    PYVER=$($PYTHON --version 2>&1)
    echo -e "  ${G}✓${N} $PYVER  (as: $PYTHON)"
else
    echo -e "  ${Y}!${N} Python 3 not found — PDF import will not work"
    echo -e "  ${Y}Install Python 3:${N}"
    if [ "$OS" = "linux" ]; then
        echo "    Ubuntu/Debian:  sudo apt install python3 python3-pip"
        echo "    Fedora:         sudo dnf install python3 python3-pip"
    elif [ "$OS" = "mac" ]; then
        echo "    Homebrew:       brew install python3"
    fi
    echo
    read -p "Continue without PDF support? [y/N] " -n 1 -r
    echo
    [[ ! $REPLY =~ ^[Yy]$ ]] && exit 1
fi

# ─── pymupdf ───
if [ -n "$PYTHON" ]; then
    echo
    echo -e "${B}[3/3] Checking pymupdf…${N}"
    if $PYTHON -c "import fitz" &> /dev/null; then
        echo -e "  ${G}✓${N} pymupdf installed"
    else
        echo -e "  ${Y}!${N} pymupdf not found"
        read -p "Install pymupdf now? [Y/n] " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Nn]$ ]]; then
            $PYTHON -m pip install --user pymupdf || {
                echo -e "  ${Y}Trying with --break-system-packages…${N}"
                $PYTHON -m pip install --user --break-system-packages pymupdf
            }
            if $PYTHON -c "import fitz" &> /dev/null; then
                echo -e "  ${G}✓${N} pymupdf installed"
            else
                echo -e "  ${R}✗${N} pymupdf install failed — PDF import won't work"
            fi
        fi
    fi
fi

# ─── Verify JAR ───
echo
JAR_PATH="$(dirname "$(readlink -f "$0" 2>/dev/null || echo "$0")")/lasercam.jar"
if [ ! -f "$JAR_PATH" ]; then
    echo -e "${R}✗ lasercam.jar not found at $JAR_PATH${N}"
    exit 1
fi

# ─── Create launcher ───
LAUNCHER="$(dirname "$JAR_PATH")/lasercam"
cat > "$LAUNCHER" << EOF
#!/bin/bash
java -jar "\$(dirname "\$(readlink -f "\$0" 2>/dev/null || echo "\$0")")/lasercam.jar" "\$@"
EOF
chmod +x "$LAUNCHER"

# ─── Done ───
echo
echo -e "${G}╔══════════════════════════════════════╗${N}"
echo -e "${G}║   Installation complete!             ║${N}"
echo -e "${G}╚══════════════════════════════════════╝${N}"
echo
echo -e "Run: ${B}./lasercam${N}"
echo -e "  or ${B}java -jar lasercam.jar${N}"
echo -e "  or ${B}java -jar lasercam.jar file.svg${N}"
echo
