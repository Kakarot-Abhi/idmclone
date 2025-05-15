package com.kakarotabhi.idmclone.service;

import com.kakarotabhi.idmclone.downloader.SegmentDownloader;
import com.kakarotabhi.idmclone.entity.Download;
import com.kakarotabhi.idmclone.repository.DownloadRepository;
import com.kakarotabhi.idmclone.task.SegmentInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class DownloadService {

    private final DownloadRepository downloadRepository;
    
    // Directory where files will be saved (configured via application.properties)
    @Value("${download.dir:/download/IDMClone}")
    private String downloadDir;

    public DownloadService(DownloadRepository downloadRepository) {
        this.downloadRepository = downloadRepository;
    }

    /**
     * Starts a new download for the given URL and filename.
     * Returns the database entity representing this download.
     */
    @Transactional
    public Download startDownload(String fileUrl, String fileName) throws Exception {
        // Save initial Download entity with status QUEUED
        Download download = new Download();
        download.setUrl(fileUrl);
        download.setFileName(fileName);
        download.setStatus("QUEUED");
        download.setDownloadedSize(0L);
        downloadRepository.save(download);

        // Perform the actual download (could be @Async for real apps)
        performDownload(download);

        return download;
    }

    private void performDownload(Download download) throws Exception {
        download.setStatus("IN_PROGRESS");
        downloadRepository.save(download);

        // Prepare download URL and connection
        URL url = new URL(download.getUrl());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("HEAD");
        conn.setInstanceFollowRedirects(true);
        conn.connect();
        long totalSize = conn.getContentLengthLong();
        download.setTotalSize(totalSize);
        downloadRepository.save(download);

        // Prepare target file
        File dir = new File(downloadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File targetFile = new File(dir, download.getFileName());
        try (RandomAccessFile raf = new RandomAccessFile(targetFile, "rw")) {
            raf.setLength(totalSize);  // Pre-allocate file size
        }

        // Create 8 segments
        int numSegments = 8;
        long segmentSize = totalSize / numSegments;
        List<SegmentInfo> segments = new ArrayList<>();
        long start = 0;
        for (int i = 0; i < numSegments; i++) {
            long end = (i < numSegments - 1) ? (start + segmentSize - 1) : (totalSize - 1);
            segments.add(new SegmentInfo(i, start, end));
            start = end + 1;
        }

        // Atomic variables for progress and error tracking
        AtomicLong bytesDownloaded = new AtomicLong(0);
        AtomicBoolean errorFlag = new AtomicBoolean(false);
        CountDownLatch doneSignal = new CountDownLatch(numSegments);

        // Start threads for each segment
        ExecutorService executor = Executors.newFixedThreadPool(numSegments);
        for (SegmentInfo segment : segments) {
            executor.submit(new SegmentDownloader(url, targetFile, segment,
                                                  bytesDownloaded, errorFlag, doneSignal));
        }
        executor.shutdown();
        
        // Wait for all segments to finish
        doneSignal.await();

        // Update downloaded size in DB
        download.setDownloadedSize(bytesDownloaded.get());

        if (errorFlag.get()) {
            // If any segment failed, mark INCOMPLETE for manual retry
            download.setStatus("INCOMPLETE");
        } else {
            // All segments completed successfully
            download.setStatus("COMPLETED");
        }
        downloadRepository.save(download);
    }
}
