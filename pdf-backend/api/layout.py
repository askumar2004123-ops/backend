from pathlib import Path
from typing import List
import html as _html
import re

def _clean(t: str) -> str:
    t = t.replace("\x0c", "")
    t = t.replace("\r", "")
    t = re.sub(r"[ \t]+", " ", t)
    t = re.sub(r"\n{3,}", "\n\n", t)
    return t.strip()

def _reflow_paragraphs(text: str):
    text = _clean(text)
    if not text:
        return []
    blocks = [b.strip() for b in re.split(r"\n\s*\n", text) if b.strip()]
    paras = []
    for b in blocks:
        ls = [ln.strip() for ln in b.split("\n") if ln.strip()]
        p = " ".join(ls)
        p = re.sub(r"(\w)-\s+(\w)", r"\1\2", p)
        paras.append(p)
    # drop tiny junk
    return [p for p in paras if len(p) >= 20]

def _title_from_text(full_text: str):
    """
    Try to find a heading-like first line.
    If nothing found, return None.
    """
    t = _clean(full_text)
    lines = [ln.strip() for ln in t.split("\n") if ln.strip()]
    if not lines:
        return None

    first = lines[0]
    # heading-like: short, no period end
    if 3 <= len(first) <= 60 and not first.endswith("."):
        return first
    return None

def _title_from_filename(image_paths: List[Path]) -> str:
    """
    fallback: use first image name (cleaned) as a title
    """
    if not image_paths:
        return "TREES"
    name = image_paths[0].stem
    name = re.sub(r"img_\d+","", name, flags=re.IGNORECASE).strip("_- ")
    name = name.replace("_", " ").replace("-", " ").strip()
    return name.upper() if name else "TREES"

def build_html(job_id: str, mode: str, image_paths: List[Path], texts: List[str]) -> str:
    full_text = "\n\n".join([t for t in texts if t and t.strip()])
    paras = _reflow_paragraphs(full_text)

    # TITLE logic:
    title = _title_from_text(full_text)
    if not title:
        # fallback to file name (TEMP) instead of "Notes"
        title = _title_from_filename(image_paths)

    # Typography controls (denser)
    if mode == "more":
        body_pt = 12.5
        line = 1.35
    elif mode == "less":
        body_pt = 14.0
        line = 1.45
    else:
        body_pt = 13.2
        line = 1.4

    body_html = "\n".join(f"<p>{_html.escape(p)}</p>" for p in paras) or "<p>(No text detected)</p>"

    return f"""
    <!doctype html>
    <html>
    <head>
      <meta charset="utf-8" />
      <style>
        @page {{
          size: A4;
          margin: 18mm;
        }}
        body {{
          background: #000;
          color: #fff;
          font-family: Arial, Helvetica, sans-serif;
        }}
        .container {{
          max-width: 720px;
          margin: 0 auto;
        }}
        .title {{
          font-size: 22pt;
          font-weight: 900;
          margin: 0 0 10pt 0;
          letter-spacing: 0.6px;
          text-transform: uppercase;
        }}
        .content {{
          font-size: {body_pt}pt;
          line-height: {line};
          text-align: justify;
          font-weight: 700; /* BODY BOLD */
        }}
        p {{
          margin: 0 0 7pt 0; /* tighter spacing */
        }}
      </style>
    </head>
    <body>
      <div class="container">
        <div class="title">{_html.escape(title)}</div>
        <div class="content">
          {body_html}
        </div>
      </div>
    </body>
    </html>
    """