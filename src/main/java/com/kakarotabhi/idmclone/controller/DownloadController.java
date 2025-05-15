package com.kakarotabhi.idmclone.controller;

import com.kakarotabhi.idmclone.service.DownloadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DownloadController {
private final DownloadService downloadService;
public DownloadController(DownloadService downloadService) {
this.downloadService = downloadService;
}
@PostMapping("/download")
public ResponseEntity<String> startDownload(@RequestParam("url")
String url) {
downloadService.queueDownload(url);
return ResponseEntity.ok("Download queued");
}
}
