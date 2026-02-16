package com.example.pdfbackend;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.util.Matrix;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@CrossOrigin(origins = "*")
@RestController
public class ConvertController {

    // ===== Limits =====
    private static final long MAX_BYTES = 20L * 1024 * 1024; // 20MB
    private static final int MAX_REQ_PER_MINUTE = 5;

    // Simple in-memory rate limiter: IP -> timestamps (ms)
    private final Map<String, Deque<Long>> ipHits = new ConcurrentHashMap<>();

    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<byte[]> convert(
        HttpServletRequest request,
            @RequestPart("file") MultipartFile file,
            @RequestParam("pagesPerSheet") int pagesPerSheet,
            @RequestParam("paperSize") String paperSize,
            @RequestParam("mode") String mode
    ) throws IOException {

        // ---- Rate limit (5/min/IP) ----
String ip = request.getHeader("X-Forwarded-For");
if (ip == null || ip.isBlank()) {
    ip = request.getRemoteAddr();
} else {
    ip = ip.split(",")[0].trim();
}
        // If behind proxy later, you can use X-Forwarded-For; keep simple for now.
        // ip = request.getRemoteAddr(); (needs HttpServletRequest parameter)
        // For now Spring will still run; we'll upgrade after deployment.
        // We'll use a basic fallback bucket shared by all if we can't read IP.
        // If you want proper IP, tell me and I’ll add HttpServletRequest param.

        if (!allowRequest(ip)) {
            return ResponseEntity.status(429).body("Too many requests. Try again after 1 minute.".getBytes());
        }

        // ---- Validations ----
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("No file uploaded.".getBytes());
        }
        if (file.getSize() > MAX_BYTES) {
            return ResponseEntity.badRequest().body("File too large. Max 20MB.".getBytes());
        }
        if (pagesPerSheet != 2 && pagesPerSheet != 4 && pagesPerSheet != 8 && pagesPerSheet != 16) {
            return ResponseEntity.badRequest().body("pagesPerSheet must be 2/4/8/16".getBytes());
        }

        PDRectangle outSize = switch (paperSize.toUpperCase(Locale.ROOT)) {
            case "A3" -> PDRectangle.A3;
            case "LETTER" -> PDRectangle.LETTER;
            case "LEGAL" -> PDRectangle.LEGAL;
            default -> PDRectangle.A4;
        };

        boolean foldable = mode != null && mode.toLowerCase(Locale.ROOT).startsWith("fold");

        byte[] inputBytes = file.getBytes();

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
                // ===== FOLDABLE: fronts = odd pages, backs = even pages (row-wise mirrored columns) =====
                int totalSheets = (int) Math.ceil(srcCount / (double) (pagesPerSheet * 2));

                // 1) Fronts
                for (int i = 0; i < totalSheets; i++) {
                    PDPage outPage = new PDPage(outSize);
                    dest.addPage(outPage);

                    int batchStart0 = i * (pagesPerSheet * 2); // 0-based index start in "virtual list"

                    try (PDPageContentStream cs = new PDPageContentStream(dest, outPage)) {
                        for (int j = 0; j < pagesPerSheet; j++) {
                            int srcIndex1 = batchStart0 + (j * 2) + 1; // 1-based odd: 1,3,5...
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

                            int srcIndex1 = batchStart0 + (reversedPos * 2) + 2; // 1-based even: 2,4,6...
                            if (srcIndex1 <= srcCount) {
                                drawOne(layer, dest, src, cs, srcIndex1 - 1, j, pagesPerSheet, outSize);
                            }
                        }
                    }
                }
            }

            dest.save(out);
            byte[] bytes = out.toByteArray();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Cheatsheet.pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(bytes);
        }
    }

    // ===== Helpers =====

    private boolean allowRequest(String ip) {
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000;

        Deque<Long> q = ipHits.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < windowStart) q.pollFirst();
            if (q.size() >= MAX_REQ_PER_MINUTE) return false;
            q.addLast(now);
            return true;
        }
    }

    private static int gridCols(int pagesPerSheet) {
        return switch (pagesPerSheet) {
            case 2 -> 1;
            case 4 -> 2;
            case 8 -> 2;
            case 16 -> 4;
            default -> 2;
        };
    }

    private static int gridRows(int pagesPerSheet) {
        return switch (pagesPerSheet) {
            case 2 -> 2;
            case 4 -> 2;
            case 8 -> 4;
            case 16 -> 4;
            default -> 2;
        };
    }

    private static void drawOne(
            LayerUtility layer,
            PDDocument dest,
            PDDocument src,
            PDPageContentStream cs,
            int srcPageIndex0,
            int positionIndex,
            int pagesPerSheet,
            PDRectangle outSize
    ) throws IOException {

        if (srcPageIndex0 < 0 || srcPageIndex0 >= src.getNumberOfPages()) return;

        int cols = gridCols(pagesPerSheet);
        int rows = gridRows(pagesPerSheet);

        float cellW = outSize.getWidth() / cols;
        float cellH = outSize.getHeight() / rows;

        int row = positionIndex / cols;
        int col = positionIndex % cols;

        // PDFBox origin is bottom-left
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
