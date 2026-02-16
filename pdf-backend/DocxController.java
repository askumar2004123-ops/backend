package com.example.pdfbackend;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@CrossOrigin(origins = "*")
@RestController
public class DocxController {

    private static final long MAX_BYTES = 20L * 1024 * 1024; // 20MB
    private static final int MAX_REQ_PER_MINUTE = 10; // Bumped limit for better UX

    private final Map<String, Deque<Long>> ipHits = new ConcurrentHashMap<>();
    
    // This is the "Brain" we will build in the next step
    private final PdfProcessingService pdfService;

    public DocxController(PdfProcessingService pdfService) {
        this.pdfService = pdfService;
    }

    // ===== 1. HEALTH CHECK =====
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    // ===== 2. PAGES PER SHEET =====
    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> convert(
            HttpServletRequest request,
            @RequestPart("file") MultipartFile file,
            @RequestParam("pagesPerSheet") int pagesPerSheet,
            @RequestParam("paperSize") String paperSize,
            @RequestParam("mode") String mode
    ) throws Exception {

        if (!allowRequest(request)) return tooManyRequests();
        validateFile(file);

        // Controller just routes traffic. The Service does the hard work.
        byte[] resultBytes = pdfService.generatePagesPerSheet(file.getBytes(), pagesPerSheet, paperSize, mode);

        return buildPdfResponse(resultBytes, "Pages_Per_Sheet.pdf");
    }

    // ===== 3. MERGE PDFs =====
    @PostMapping(value = "/merge", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> merge(
            HttpServletRequest request,
            @RequestPart("files") MultipartFile[] files
    ) throws Exception {

        if (!allowRequest(request)) return tooManyRequests();
        if (files == null || files.length < 2) {
            throw new IllegalArgumentException("Please upload at least 2 files to merge.");
        }

        byte[] resultBytes = pdfService.mergePdfs(files);
        return buildPdfResponse(resultBytes, "Merged_Document.pdf");
    }

    // ===== 4. SPLIT / CUT PDF =====
    @PostMapping(value = "/split", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> split(
            HttpServletRequest request,
            @RequestPart("file") MultipartFile file,
            @RequestParam("pages") String pages // e.g., "1,3,5" or "1-3"
    ) throws Exception {

        if (!allowRequest(request)) return tooManyRequests();
        validateFile(file);

        byte[] resultBytes = pdfService.splitPdf(file.getBytes(), pages);
        return buildPdfResponse(resultBytes, "Split_Document.pdf");
    }

    // ===== 5. COMPRESS PDF =====
    @PostMapping(value = "/compress", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> compress(
            HttpServletRequest request,
            @RequestPart("file") MultipartFile file,
            @RequestParam("level") String level
    ) throws Exception {

        if (!allowRequest(request)) return tooManyRequests();
        validateFile(file);

        byte[] resultBytes = pdfService.compressPdf(file.getBytes(), level);
        return buildPdfResponse(resultBytes, "Compressed_Document.pdf");
    }


    // ===== HELPER METHODS (Security & Validation) =====

    // ===== 6. DELETE PAGES =====
    @PostMapping(value = "/delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> delete(
            HttpServletRequest request,
            @RequestPart("file") MultipartFile file,
            @RequestParam("pages") String pages // e.g., "1, 5-8"
    ) throws Exception {

        if (!allowRequest(request)) return tooManyRequests();
        validateFile(file);

        byte[] resultBytes = pdfService.deletePages(file.getBytes(), pages);
        return buildPdfResponse(resultBytes, "Deleted_Pages.pdf");
    }

    // ===== 7. REARRANGE PAGES =====
    @PostMapping(value = "/rearrange", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> rearrange(
            HttpServletRequest request,
            @RequestPart("file") MultipartFile file,
            @RequestParam("order") String order // e.g., "3,1,2"
    ) throws Exception {

        if (!allowRequest(request)) return tooManyRequests();
        validateFile(file);

        byte[] resultBytes = pdfService.rearrangePages(file.getBytes(), order);
        return buildPdfResponse(resultBytes, "Rearranged_Document.pdf");
    }

    // ===== 8. ADD PAGE NUMBERS =====
    @PostMapping(value = "/add-page-numbers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> addPageNumbers(
            HttpServletRequest request,
            @RequestPart("file") MultipartFile file,
            @RequestParam("position") String position,
            @RequestParam("margin") String margin,
            @RequestParam("startNumber") int startNumber,
            @RequestParam("format") String format
    ) throws Exception {

        if (!allowRequest(request)) return tooManyRequests();
        validateFile(file);

        byte[] resultBytes = pdfService.addPageNumbers(file.getBytes(), position, margin, startNumber, format);
        return buildPdfResponse(resultBytes, "Numbered_Document.pdf");
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file uploaded.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("File too large. Max allowed is 20MB.");
        }
    }

    private ResponseEntity<byte[]> buildPdfResponse(byte[] bytes, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    private ResponseEntity<byte[]> tooManyRequests() {
        return ResponseEntity.status(429).body("Too many requests. Please try again in a minute.".getBytes());
    }

    private boolean allowRequest(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();
        }

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
}