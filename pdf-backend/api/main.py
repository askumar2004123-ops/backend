from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from fastapi.responses import FileResponse
import uuid
from typing import List
from pathlib import Path
from starlette.concurrency import run_in_threadpool
from .pdf import html_to_pdf_sync

from .layout import build_html
from .ocr import ocr_image

app = FastAPI(title="Cheatsheet Optimizer API")

BASE_DIR = Path(__file__).resolve().parent.parent / "tmp_jobs"
BASE_DIR.mkdir(parents=True, exist_ok=True)

def pdf_path(job_id: str) -> Path:
    return BASE_DIR / job_id / "output.pdf"

@app.get("/health")
def health():
    return {"ok": True, "service": "cheatsheet-optimizer"}

@app.post("/submit-multi")
async def submit_multi(
    mode: str = Form(...),
    images: List[UploadFile] = File(...)
):
    if mode not in {"full", "less", "more"}:
        raise HTTPException(status_code=400, detail="mode must be full/less/more")

    if not images:
        raise HTTPException(status_code=400, detail="No images uploaded")

    job_id = uuid.uuid4().hex
    job_dir = BASE_DIR / job_id
    job_dir.mkdir(parents=True, exist_ok=True)

    # 1) Save images
    saved = 0
    for i, image in enumerate(images):
        content_type = (image.content_type or "").lower()
        if content_type not in {"image/png", "image/jpeg", "image/webp"}:
            raise HTTPException(status_code=400, detail=f"Unsupported type: {image.content_type}")

        ext = "png" if "png" in content_type else ("webp" if "webp" in content_type else "jpg")
        data = await image.read()
        (job_dir / f"img_{i:02d}.{ext}").write_bytes(data)
        saved += 1

    # 2) Collect saved paths
    image_paths = (
        sorted(job_dir.glob("img_*.jpg")) +
        sorted(job_dir.glob("img_*.jpeg")) +
        sorted(job_dir.glob("img_*.png")) +
        sorted(job_dir.glob("img_*.webp"))
    )

    # 3) OCR each image + store results
    texts: List[str] = []
    for idx, img_path in enumerate(image_paths):
        text = ocr_image(img_path)
        texts.append(text)

        (job_dir / f"ocr_{idx:02d}.txt").write_text(text, encoding="utf-8")

    (job_dir / "ocr_all.txt").write_text("\n\n---\n\n".join(texts), encoding="utf-8")

    print(f"[{job_id}] OCR done for {len(texts)} images")

    # 4) Build HTML with OCR text injected
    html = build_html(job_id, mode, image_paths, texts)

    # 5) Render PDF
    out = pdf_path(job_id)
    await run_in_threadpool(html_to_pdf_sync, html, out)
    print(f"[{job_id}] PDF generated: {out}")

    return {"job_id": job_id, "saved_images": saved, "mode": mode}

@app.get("/status/{job_id}")
def status(job_id: str):
    job_dir = BASE_DIR / job_id
    if not job_dir.exists():
        raise HTTPException(status_code=404, detail="job not found")

    out = pdf_path(job_id)
    state = "ready" if out.exists() else "processing"
    return {"job_id": job_id, "state": state}

@app.get("/download/{job_id}")
def download(job_id: str):
    out = pdf_path(job_id)
    if not out.exists():
        raise HTTPException(status_code=404, detail="PDF not ready")
    return FileResponse(str(out), media_type="application/pdf", filename=f"cheatsheet_{job_id}.pdf")