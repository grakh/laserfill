@echo off
setlocal enabledelayedexpansion
title LaserCAM Engine - Installer

echo.
echo ============================================
echo    LaserCAM Engine - Windows Installer
echo ============================================
echo.

REM ==============================
REM [1/3] Java check
REM ==============================
echo [1/3] Checking Java...
set JAVA_OK=0
java -version >nul 2>&1
if %errorlevel% equ 0 (
    for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
        set JAVA_VER=%%v
        set JAVA_VER=!JAVA_VER:"=!
    )
    for /f "delims=. tokens=1" %%m in ("!JAVA_VER!") do set JAVA_MAJ=%%m
    if !JAVA_MAJ! GEQ 17 (
        echo   [OK] Java !JAVA_VER!
        set JAVA_OK=1
    ) else (
        echo   [!!] Java !JAVA_VER! found, need 17+
    )
) else (
    echo   [XX] Java not found
)

if !JAVA_OK! equ 0 (
    echo.
    echo   Please install Java 17 or later:
    echo     Adoptium:  https://adoptium.net/
    echo     Oracle:    https://www.oracle.com/java/technologies/downloads/
    echo.
    echo   After installing Java, re-run install.bat
    pause
    exit /b 1
)

REM ==============================
REM [2/3] Python check
REM ==============================
echo.
echo [2/3] Checking Python 3...
set PYTHON=
where python >nul 2>&1 && (
    for /f %%v in ('python -c "import sys; print(sys.version_info[0])" 2^>nul') do (
        if "%%v"=="3" set PYTHON=python
    )
)
if not defined PYTHON (
    where python3 >nul 2>&1 && (
        for /f %%v in ('python3 -c "import sys; print(sys.version_info[0])" 2^>nul') do (
            if "%%v"=="3" set PYTHON=python3
        )
    )
)
if not defined PYTHON (
    where py >nul 2>&1 && (
        for /f %%v in ('py -3 -c "import sys; print(sys.version_info[0])" 2^>nul') do (
            if "%%v"=="3" set PYTHON=py -3
        )
    )
)

if defined PYTHON (
    for /f "delims=" %%v in ('!PYTHON! --version 2^>^&1') do echo   [OK] %%v  ^(as: !PYTHON!^)
) else (
    echo   [!!] Python 3 not found - PDF import will not work
    echo.
    echo   To enable PDF support:
    echo     Install Python 3 from https://www.python.org/downloads/
    echo     Then re-run install.bat
    echo.
    set /p CONT="Continue without PDF support? [y/N]: "
    if /i not "!CONT!"=="y" exit /b 1
    goto skip_pymupdf
)

REM ==============================
REM [3/3] pymupdf
REM ==============================
echo.
echo [3/3] Checking pymupdf...
!PYTHON! -c "import fitz" >nul 2>&1
if !errorlevel! equ 0 (
    echo   [OK] pymupdf installed
) else (
    echo   [!!] pymupdf not found
    set /p INSTALL_PM="Install pymupdf now? [Y/n]: "
    if /i not "!INSTALL_PM!"=="n" (
        !PYTHON! -m pip install --user pymupdf
        !PYTHON! -c "import fitz" >nul 2>&1
        if !errorlevel! equ 0 (
            echo   [OK] pymupdf installed
        ) else (
            echo   [XX] pymupdf install failed - PDF import won't work
        )
    )
)

:skip_pymupdf

REM ==============================
REM Verify JAR
REM ==============================
if not exist "%~dp0lasercam.jar" (
    echo.
    echo [XX] lasercam.jar not found in %~dp0
    pause
    exit /b 1
)

REM ==============================
REM Create launcher .bat
REM ==============================
echo @echo off > "%~dp0lasercam.bat"
echo java -jar "%%~dp0lasercam.jar" %%* >> "%~dp0lasercam.bat"

REM ==============================
REM Done
REM ==============================
echo.
echo ============================================
echo    Installation complete!
echo ============================================
echo.
echo Run:
echo    lasercam.bat
echo    java -jar lasercam.jar
echo    java -jar lasercam.jar file.svg
echo.
echo Or double-click lasercam.jar to open the file chooser.
echo.
pause
