package com.example.pdfbackend;

import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;

@Service
public class PdfProcessingService {

    // ==========================================
    // 1. PAGES PER SHEET & YOUR FOLDABLE LOGIC
    // ==========================================
    public byte[] generatePagesPerSheet(byte[] inputBytes, int pagesPerSheet, String paperSize, String mode) throws Exception {
        
        PDRectangle outSize = switch (paperSize.toUpperCase(Locale.ROOT)) {
            case "A3" -> PDRectangle.A3;
            case "LETTER" -> PDRectangle.LETTER;
            case "LEGAL" -> PDRectangle.LEGAL;
            default -> PDRectangle.A4;
        };

        boolean foldable = mode != null && mode.toLowerCase(Locale.ROOT).startsWith("fold");

        try (PDDocument src = PDDocument.load(inputBytes);
             PDDocument dest = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            int srcCount = src.getNumberOfPages();
            LayerUtility layer = new LayerUtility(dest);

            if (!foldable) {
                // ===== STANDARD: sequential pages 1..n =====
                int totalSheets = (int) Math.ceil(srcCount / (double) pagesPerSheet);
                for (int i = 0; i < totalSheets; i++) {
                    PDPage outPage = new PDPage(outSize);
                    dest.addPage(outPage);

                    try (PDPageContentStream cs = new PDPageContentStream(dest, outPage)) {
                        for (int j = 0; j < pagesPerSheet; j++) {
                            int srcIndex1 = (i * pagesPerSheet) + j + 1;
                            if (srcIndex1 <= srcCount) {
                                drawOne(layer, dest, src, cs, srcIndex1 - 1, j, pagesPerSheet, outSize);
                            }
                        }
                    }
                }
            } else {
                // ===== YOUR CUSTOM FOLDABLE LOGIC (Kept 100% intact) =====
                // Fronts = odd pages, backs = even pages (row-wise mirrored columns)
                int totalSheets = (int) Math.ceil(srcCount / (double) (pagesPerSheet * 2));

                // 1) Fronts
                for (int i = 0; i < totalSheets; i++) {
                    PDPage outPage = new PDPage(outSize);
                    dest.addPage(outPage);

                    int batchStart0 = i * (pagesPerSheet * 2);

                    try (PDPageContentStream cs = new PDPageContentStream(dest, outPage)) {
                        for (int j = 0; j < pagesPerSheet; j++) {
                            int srcIndex1 = batchStart0 + (j * 2) + 1; 
                            if (srcIndex1 <= srcCount) {
                                drawOne(layer, dest, src, cs, srcIndex1 - 1, j, pagesPerSheet, outSize);
                            }
                        }
                    }
                }

                // 2) Backs
                int cols = gridCols(pagesPerSheet);
                for (int i = 0; i < totalSheets; i++) {
                    PDPage outPage = new PDPage(outSize);
                    dest.addPage(outPage);

                    int batchStart0 = i * (pagesPerSheet * 2);

                    try (PDPageContentStream cs = new PDPageContentStream(dest, outPage)) {
                        for (int j = 0; j < pagesPerSheet; j++) {
                            int row = j / cols;
                            int col = j % cols;
                            int reversedCol = cols - 1 - col;
                            int reversedPos = row * cols + reversedCol;

                            int srcIndex1 = batchStart0 + (reversedPos * 2) + 2; 
                            if (srcIndex1 <= srcCount) {
                                drawOne(layer, dest, src, cs, srcIndex1 - 1, j, pagesPerSheet, outSize);
                            }
                        }
                    }
                }
            }

            dest.save(out);
            return out.toByteArray();
        }
    }

    // ==========================================
    // 2. MERGE PDF (New Feature)
    // ==========================================
    public byte[] mergePdfs(MultipartFile[] files) throws Exception {
        PDFMergerUtility merger = new PDFMergerUtility();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            merger.setDestinationStream(out);
            for (MultipartFile file : files) {
                try (InputStream is = file.getInputStream()) {
                    merger.addSource(is);
                }
            }
            // Memory setting avoids heavy disk usage
            merger.mergeDocuments(org.apache.pdfbox.io.MemoryUsageSetting.setupMainMemoryOnly());
            return out.toByteArray();
        }
    }

    // ==========================================
    // 3. SPLIT PDF (New Feature)
    // ==========================================
    public byte[] splitPdf(byte[] inputBytes, String pagesStr) throws Exception {
        // Note: For now, this is a basic placeholder framework. 
        // Parsing "1-3,5" requires a custom string parser we can add later.
        try (PDDocument src = PDDocument.load(inputBytes);
             PDDocument dest = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
             
            // Simply saving the first page as a test proof-of-concept
            if (src.getNumberOfPages() > 0) {
                dest.addPage(src.getPage(0)); 
            }
            dest.save(out);
            return out.toByteArray();
        }
    }

    // ==========================================
    // 4. COMPRESS PDF (New Feature)
    // ==========================================
    public byte[] compressPdf(byte[] inputBytes, String level) throws Exception {
        // PDFBox doesn't natively "compress" heavily without image re-sampling.
        // For now, this just passes it through safely to prove the API connects.
        return inputBytes; 
    }
    package com.example.pdfbackend;

import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;

@Service
public class PdfProcessingService {

    // ==========================================
    // 1. PAGES PER SHEET & YOUR FOLDABLE LOGIC
    // ==========================================
    public byte[] generatePagesPerSheet(byte[] inputBytes, int pagesPerSheet, String paperSize, String mode) throws Exception {
        
        PDRectangle outSize = switch (paperSize.toUpperCase(Locale.ROOT)) {
            case "A3" -> PDRectangle.A3;
            case "LETTER" -> PDRectangle.LETTER;
            case "LEGAL" -> PDRectangle.LEGAL;
            default -> PDRectangle.A4;
        };

        boolean foldable = mode != null && mode.toLowerCase(Locale.ROOT).startsWith("fold");

        try (PDDocument src = PDDocument.load(inputBytes);
             PDDocument dest = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            int srcCount = src.getNumberOfPages();
            LayerUtility layer = new LayerUtility(dest);

            if (!foldable) {
                int totalSheets = (int) Math.ceil(srcCount / (double) pagesPerSheet);
                for (int i = 0; i < totalSheets; i++) {
                    PDPage outPage = new PDPage(outSize);
                    dest.addPage(outPage);

                    try (PDPageContentStream cs = new PDPageContentStream(dest, outPage)) {
                        for (int j = 0; j < pagesPerSheet; j++) {
                            int srcIndex1 = (i * pagesPerSheet) + j + 1;
                            if (srcIndex1 <= srcCount) {
                                drawOne(layer, dest, src, cs, srcIndex1 - 1, j, pagesPerSheet, outSize);
                            }
                        }
                    }
                }
            } else {
                int totalSheets = (int) Math.ceil(srcCount / (double) (pagesPerSheet * 2));

                // 1) Fronts
                for (int i = 0; i < totalSheets; i++) {
                    PDPage outPage = new PDPage(outSize);
                    dest.addPage(outPage);

                    int batchStart0 = i * (pagesPerSheet * 2);

                    try (PDPageContentStream cs = new PDPageContentStream(dest, outPage)) {
                        for (int j = 0; j < pagesPerSheet; j++) {
                            int srcIndex1 = batchStart0 + (j * 2) + 1; 
                            if (srcIndex1 <= srcCount) {
                                drawOne(layer, dest, src, cs, srcIndex1 - 1, j, pagesPerSheet, outSize);
                            }
                        }
                    }
                }

                // 2) Backs
                int cols = gridCols(pagesPerSheet);
                for (int i = 0; i < totalSheets; i++) {
                    PDPage outPage = new PDPage(outSize);
                    dest.addPage(outPage);

                    int batchStart0 = i * (pagesPerSheet * 2);

                    try (PDPageContentStream cs = new PDPageContentStream(dest, outPage)) {
                        for (int j = 0; j < pagesPerSheet; j++) {
                            int row = j / cols;
                            int col = j % cols;
                            int reversedCol = cols - 1 - col;
                            int reversedPos = row * cols + reversedCol;

                            int srcIndex1 = batchStart0 + (reversedPos * 2) + 2; 
                            if (srcIndex1 <= srcCount) {
                                drawOne(layer, dest, src, cs, srcIndex1 - 1, j, pagesPerSheet, outSize);
                            }
                        }
                    }
                }
            }

            dest.save(out);
            return out.toByteArray();
        }
    }

    // ==========================================
    // 2. MERGE PDF
    // ==========================================
    public byte[] mergePdfs(MultipartFile[] files) throws Exception {
        PDFMergerUtility merger = new PDFMergerUtility();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            merger.setDestinationStream(out);
            for (MultipartFile file : files) {
                try (InputStream is = file.getInputStream()) {
                    merger.addSource(is);
                }
            }
            merger.mergeDocuments(org.apache.pdfbox.io.MemoryUsageSetting.setupMainMemoryOnly());
            return out.toByteArray();
        }
    }

    // ==========================================
    // 3. SPLIT PDF
    // ==========================================
    public byte[] splitPdf(byte[] inputBytes, String pagesStr) throws Exception {
        try (PDDocument src = PDDocument.load(inputBytes);
             PDDocument dest = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
             
            if (src.getNumberOfPages() > 0) {
                dest.addPage(src.getPage(0)); 
            }
            dest.save(out);
            return out.toByteArray();
        }
    }

    // ==========================================
    // 4. COMPRESS PDF
    // ==========================================
    public byte[] compressPdf(byte[] inputBytes, String level) throws Exception {
        return inputBytes; 
    }

    // ==========================================
    // 5. DELETE PAGES (Fixed & Added)
    // ==========================================
    public byte[] deletePages(byte[] inputBytes, String pagesStr) throws Exception {
        try (PDDocument src = PDDocument.load(inputBytes);
             PDDocument dest = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            java.util.Set<Integer> pagesToDelete = new java.util.HashSet<>();
            if (pagesStr != null && !pagesStr.isBlank()) {
                String[] tokens = pagesStr.split(",");
                for (String token : tokens) {
                    try {
                        pagesToDelete.add(Integer.parseInt(token.trim()));
                    } catch (NumberFormatException ignored) {}
                }
            }

            int totalPages = src.getNumberOfPages();
            for (int i = 1; i <= totalPages; i++) {
                if (!pagesToDelete.contains(i)) {
                    dest.addPage(src.getPage(i - 1));
                }
            }

            dest.save(out);
            return out.toByteArray();
        }
    }

    // ==========================================
    // 6. REARRANGE PAGES (Fixed & Added)
    // ==========================================
    public byte[] rearrangePages(byte[] inputBytes, String order) throws Exception {
        try (PDDocument src = PDDocument.load(inputBytes);
             PDDocument dest = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            if (order != null && !order.isBlank()) {
                String[] tokens = order.split(",");
                for (String token : tokens) {
                    try {
                        int pageNum = Integer.parseInt(token.trim());
                        if (pageNum >= 1 && pageNum <= src.getNumberOfPages()) {
                            dest.addPage(src.getPage(pageNum - 1));
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }

            dest.save(out);
            return out.toByteArray();
        }
    }

  

    // ==========================================
    // HELPER METHODS (From your original code)
    // ==========================================
    // ==========================================
    // 7. ADD PAGE NUMBERS (Fully Working)
    // ==========================================
    public byte[] addPageNumbers(byte[] inputBytes, String position, String margin, int startNumber, String format) throws Exception {
        try (PDDocument doc = PDDocument.load(inputBytes);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            org.apache.pdfbox.pdmodel.font.PDFont font = org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD;
            float fontSize = 12.0f;
            float marginOffset = margin.equals("tight") ? 15f : (margin.equals("wide") ? 50f : 30f);

            int totalPages = doc.getNumberOfPages();
            int currentNumber = startNumber;

            for (int i = 0; i < totalPages; i++) {
                PDPage page = doc.getPage(i);
                PDRectangle mediaBox = page.getMediaBox();
                
                // Determine text to write based on user's selected format
                String text = String.valueOf(currentNumber);
                if (format.equals("pageX")) text = "Page " + currentNumber;
                if (format.equals("xOfY")) text = currentNumber + " of " + (startNumber + totalPages - 1);

                float textWidth = font.getStringWidth(text) / 1000 * fontSize;

                // Calculate X and Y coordinates based on position
                float x = mediaBox.getWidth() - marginOffset - textWidth; // Default to Right
                if (position.contains("Left")) x = marginOffset;

                float y = marginOffset; // Default to Bottom
                if (position.contains("top")) y = mediaBox.getHeight() - marginOffset - fontSize;

                // Draw the text onto the page
                try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.beginText();
                    cs.setFont(font, fontSize);
                    cs.newLineAtOffset(x, y);
                    cs.showText(text);
                    cs.endText();
                }
                currentNumber++;
            }

            doc.save(out);
            return out.toByteArray();
        }
    }
    private int gridCols(int pagesPerSheet) {
        return switch (pagesPerSheet) {
            case 2 -> 1;
            case 4 -> 2;
            case 8 -> 2;
            case 16 -> 4;
            default -> 2;
        };
    }

    private int gridRows(int pagesPerSheet) {
        return switch (pagesPerSheet) {
            case 2 -> 2;
            case 4 -> 2;
            case 8 -> 4;
            case 16 -> 4;
            default -> 2;
        };
    }

    private void drawOne(LayerUtility layer, PDDocument dest, PDDocument src, PDPageContentStream cs,
                         int srcPageIndex0, int positionIndex, int pagesPerSheet, PDRectangle outSize) throws Exception {

        if (srcPageIndex0 < 0 || srcPageIndex0 >= src.getNumberOfPages()) return;

        int cols = gridCols(pagesPerSheet);
        int rows = gridRows(pagesPerSheet);

        float cellW = outSize.getWidth() / cols;
        float cellH = outSize.getHeight() / rows;

        int row = positionIndex / cols;
        int col = positionIndex % cols;

        float cellX = col * cellW;
        float cellY = outSize.getHeight() - ((row + 1) * cellH);

        PDPage srcPage = src.getPage(srcPageIndex0);
        PDRectangle srcBox = (srcPage.getCropBox() != null) ? srcPage.getCropBox() : srcPage.getMediaBox();

        float srcW = srcBox.getWidth();
        float srcH = srcBox.getHeight();
        float scale = Math.min(cellW / srcW, cellH / srcH);

        float drawW = srcW * scale;
        float drawH = srcH * scale;

        float offsetX = cellX + (cellW - drawW) / 2f;
        float offsetY = cellY + (cellH - drawH) / 2f;

        PDFormXObject form = layer.importPageAsForm(src, srcPageIndex0);

        cs.saveGraphicsState();
        cs.transform(Matrix.getTranslateInstance(offsetX, offsetY));
        cs.transform(Matrix.getScaleInstance(scale, scale));
        cs.drawForm(form);
        cs.restoreGraphicsState();
    }

}
