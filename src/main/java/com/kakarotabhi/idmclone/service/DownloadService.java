package com.kakarotabhi.idmclone.service;

import com.kakarotabhi.idmclone.entity.Download;
import com.kakarotabhi.idmclone.repository.DownloadRepository;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class DownloadService {
    private final DownloadRepository downloadRepo;
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    public DownloadService(DownloadRepository downloadRepo) {
        this.downloadRepo = downloadRepo;
    }

    public void queueDownload(String url) {
// Save a new Download record with status "QUEUED"
        Download dl = new Download();
        dl.setUrl(url);
        dl.setStatus("QUEUED");
        downloadRepo.save(dl);
// Submit download task to executor
        executor.execute(() -> {
// TODO: perform actual download logic here,
// update dl.setStatus("IN_PROGRESS" or "COMPLETED"), etc.
        });
    }
}