package com.kakarotabhi.idmclone.controller;

import com.kakarotabhi.idmclone.entity.Download;
import com.kakarotabhi.idmclone.entity.SegmentInfo;
import com.kakarotabhi.idmclone.enums.DownloadStatus;
import com.kakarotabhi.idmclone.enums.SegmentStatus;
import com.kakarotabhi.idmclone.repository.DownloadRepository;
import com.kakarotabhi.idmclone.repository.SegmentInfoRepository;
import com.kakarotabhi.idmclone.service.DownloadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/downloads")
public class DownloadController {

    @Autowired
    private DownloadService downloadService;

    @Autowired
    private DownloadRepository downloadRepo;

    @Autowired
    private SegmentInfoRepository segmentRepo;

    // DTO for create request
    public static class CreateDownloadRequest {
        public String url;
        public String fileName;
    }

    @PostMapping
    public ResponseEntity<?> createDownload(@RequestBody CreateDownloadRequest req) {
        try {
            // Step 1: Get file size using HEAD request
            URL url = new URL(req.url);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.connect();

            long totalBytes = connection.getContentLengthLong();
            if (totalBytes <= 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Unable to determine file size.");
            }

            // Step 2: Create Download entity
            Download download = new Download();
            download.setUrl(req.url);
            download.setFileName(req.fileName);
            download.setStatus(DownloadStatus.PENDING);
            download.setCreatedAt(LocalDateTime.now());
            download.setUpdatedAt(LocalDateTime.now());
            download.setTotalBytes(totalBytes);
            downloadRepo.save(download);

            // Step 3: Create segments
            int segmentsCount = 8;
            long segmentSize = totalBytes / segmentsCount;
            for (int i = 0; i < segmentsCount; i++) {
                long start = i * segmentSize;
                long end = (i == segmentsCount - 1) ? totalBytes - 1 : (start + segmentSize - 1);

                SegmentInfo seg = new SegmentInfo();
                seg.setSegmentIndex(i);
                seg.setStartByte(start);
                seg.setEndByte(end);
                seg.setDownloadedBytes(0);
                seg.setStatus(SegmentStatus.PENDING);
                seg.setDownload(download);
                download.getSegments().add(seg);
            }
            downloadRepo.save(download); // cascades and saves segments

            // Step 4: Start download asynchronously
            downloadService.startDownload(download.getId());

            // Step 5: Return 202 Accepted with status endpoint
            URI statusUri = URI.create("/downloads/" + download.getId() + "/status");
            return ResponseEntity.accepted()
                    .header(HttpHeaders.LOCATION, statusUri.toString())
                    .body("Download started with ID " + download.getId());

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to connect to URL: " + e.getMessage());
        }
    }


    @PutMapping("/{id}/pause")
    public ResponseEntity<?> pauseDownload(@PathVariable Long id) {
        downloadService.pauseDownload(id);
        return ResponseEntity.ok("Download paused");
    }

    @PutMapping("/{id}/resume")
    public ResponseEntity<?> resumeDownload(@PathVariable Long id) {
        downloadService.resumeDownload(id);
        return ResponseEntity.ok("Download resumed");
    }

    @PutMapping("/{id}/retry")
    public ResponseEntity<?> retryDownload(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean full) {
        downloadService.retryDownload(id, full);
        return ResponseEntity.ok("Download retry initiated");
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<?> getStatus(@PathVariable Long id) {
        Download download = downloadService.getDownload(id);
        // Build a status response (could be a DTO)
        Map<String, Object> resp = new HashMap<>();
        resp.put("downloadId", download.getId());
        resp.put("status", download.getStatus());
        long total = download.getSegments().stream().mapToLong(SegmentInfo::getDownloadedBytes).sum();
        resp.put("downloadedBytes", total);
        List<Map<String, Object>> segments = new ArrayList<>();
        for (SegmentInfo seg : download.getSegments()) {
            Map<String, Object> s = new HashMap<>();
            s.put("segmentIndex", seg.getSegmentIndex());
            s.put("status", seg.getStatus());
            s.put("downloadedBytes", seg.getDownloadedBytes());
            s.put("startByte", seg.getStartByte());
            s.put("endByte", seg.getEndByte());
            segments.add(s);
        }
        resp.put("segments", segments);
        return ResponseEntity.ok(resp);
    }
}
