from pathlib import Path
import pytesseract
from PIL import Image
import os
import pytesseract
pytesseract.pytesseract.tesseract_cmd = r"C:\Program Files\Tesseract-OCR\tesseract.exe"

# If Windows PATH isn't set, uncomment and set your path:
# pytesseract.pytesseract.tesseract_cmd = r"C:\Program Files\Tesseract-OCR\tesseract.exe"

def ocr_image(image_path: Path) -> str:
    """
    Basic OCR. Works for printed notes reasonably.
    Handwriting may be weak (we’ll improve later with preprocessing).
    """
    img = Image.open(image_path)
    text = pytesseract.image_to_string(img, lang="eng")
    return text.strip()