#!/usr/bin/env python3
"""
pdf2svg.py — Extract vector paths from PDF and save as SVG.
Used by LaserCAM engine for PDF import.

Usage: python3 pdf2svg.py input.pdf output.svg [page_number]
       page_number is 0-based, default=0 (first page)
       use 'all' to merge all pages
"""
import sys
import fitz  # pymupdf


def pdf_to_svg(pdf_path, svg_path, page_num=0):
    doc = fitz.open(pdf_path)
    if page_num == "all":
        # Merge all pages into one SVG
        paths = []
        total_h = 0
        max_w = 0
        for p in doc:
            svg = p.get_svg_image()
            w, h = p.rect.width, p.rect.height
            # Offset each page vertically
            paths.append((svg, total_h, w, h))
            total_h += h + 10
            max_w = max(max_w, w)

        # Build combined SVG
        combined = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {max_w} {total_h}">\n'
        for svg_text, y_off, w, h in paths:
            # Extract inner content (between <svg> and </svg>)
            start = svg_text.find(">") + 1
            end = svg_text.rfind("</svg>")
            inner = svg_text[start:end]
            combined += f'<g transform="translate(0,{y_off})">\n{inner}\n</g>\n'
        combined += '</svg>'

        with open(svg_path, "w") as f:
            f.write(combined)
    else:
        page = doc[int(page_num)]
        svg = page.get_svg_image()
        with open(svg_path, "w") as f:
            f.write(svg)

    doc.close()
    print(f"OK: {pdf_path} -> {svg_path}")


if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python3 pdf2svg.py input.pdf output.svg [page]")
        sys.exit(1)

    pdf_path = sys.argv[1]
    svg_path = sys.argv[2]
    page = sys.argv[3] if len(sys.argv) > 3 else "0"

    pdf_to_svg(pdf_path, svg_path, page)
