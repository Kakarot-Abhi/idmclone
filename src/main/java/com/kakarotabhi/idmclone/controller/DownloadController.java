package com.kakarotabhi.idmclone.controller;

import com.kakarotabhi.idmclone.entity.Download;
import com.kakarotabhi.idmclone.service.DownloadService;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController  // marks this class as a REST controller
@RequestMapping("/downloads")
public class DownloadController {

    private final DownloadService downloadService;

    public DownloadController(DownloadService downloadService) {
        this.downloadService = downloadService;
    }

    // DTO for incoming download request
    @Setter
    @Getter
    public static class DownloadRequest {
        // getters and setters
        private String url;
        private String fileName;

    }

    @PostMapping
    public ResponseEntity<String> startDownload(@RequestBody DownloadRequest request) {
        try {
            Download download = downloadService.startDownload(request.getUrl(), request.getFileName());
            return ResponseEntity.ok("Download started with ID " + download.getId());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error starting download: " + e.getMessage());
        }
    }
}
