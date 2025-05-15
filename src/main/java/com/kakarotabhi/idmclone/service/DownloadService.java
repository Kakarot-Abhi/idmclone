package com.kakarotabhi.idmclone.service;

import com.kakarotabhi.idmclone.entity.Download;
import com.kakarotabhi.idmclone.entity.SegmentInfo;
import com.kakarotabhi.idmclone.enums.DownloadStatus;
import com.kakarotabhi.idmclone.enums.SegmentStatus;
import com.kakarotabhi.idmclone.repository.DownloadRepository;
import com.kakarotabhi.idmclone.repository.SegmentInfoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@EnableAsync
public class DownloadService {

    @Autowired
    private DownloadRepository downloadRepo;

    @Autowired
    private SegmentInfoRepository segmentRepo;

    // Map of downloadId -> pause flag
    private ConcurrentMap<Long, AtomicBoolean> pauseFlags = new ConcurrentHashMap<>();

    // Create and start a new download asynchronously
    @Async
    public void startDownload(Long downloadId) {
        Download download = downloadRepo.findById(downloadId)
            .orElseThrow(() -> new IllegalArgumentException("Invalid download ID"));
        download.setStatus(DownloadStatus.DOWNLOADING);
        download.setUpdatedAt(LocalDateTime.now());
        downloadRepo.save(download);

        // Initialize pause flag
        pauseFlags.put(downloadId, new AtomicBoolean(false));

        // Launch threads for each segment
        List<SegmentInfo> segments = download.getSegments();
        for (SegmentInfo seg : segments) {
            if (seg.getStatus() != SegmentStatus.COMPLETED) {
                // Mark as DOWNLOADING and save
                seg.setStatus(SegmentStatus.DOWNLOADING);
                segmentRepo.save(seg);

                // Launch a new thread for this segment
                new Thread(() -> downloadSegment(download, seg)).start();
            }
        }
    }

    // Download logic for one segment
    private void downloadSegment(Download download, SegmentInfo seg) {
        long start = seg.getStartByte() + seg.getDownloadedBytes();
        long end = seg.getEndByte();
        String url = download.getUrl();
        String fileName = download.getFileName();
        AtomicBoolean pauseFlag = pauseFlags.get(download.getId());

        try (RandomAccessFile output = new RandomAccessFile(fileName, "rw")) {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestProperty("Range", "bytes=" + start + "-" + end);
            connection.connect();
            // Optional: check response code is 206
            try (InputStream input = connection.getInputStream()) {
                output.seek(start);
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalRead = seg.getDownloadedBytes();

                while ((bytesRead = input.read(buffer)) != -1) {
                    // Check for pause
                    if (pauseFlag.get() || download.getStatus() == DownloadStatus.PAUSED) {
                        seg.setStatus(SegmentStatus.PAUSED);
                        seg.setDownloadedBytes(totalRead);
                        segmentRepo.save(seg);
                        return; // exit thread gracefully
                    }
                    output.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                }
                // Segment completed
                seg.setDownloadedBytes(totalRead);
                seg.setStatus(SegmentStatus.COMPLETED);
                segmentRepo.save(seg);

                // After segment, check if all segments done
                checkAndCompleteDownload(download);
            }
        } catch (Exception e) {
            // On error, mark this segment as FAILED
            seg.setStatus(SegmentStatus.FAILED);
            segmentRepo.save(seg);
        }
    }

    // Pause a download: set flag and update statuses
    public void pauseDownload(Long downloadId) {
        Download download = downloadRepo.findById(downloadId)
            .orElseThrow(() -> new IllegalArgumentException("Invalid download ID"));
        download.setStatus(DownloadStatus.PAUSED);
        downloadRepo.save(download);

        AtomicBoolean flag = pauseFlags.get(downloadId);
        if (flag != null) {
            flag.set(true);
        }
        // Threads will detect flag and exit
    }

    // Resume a paused download: clear flag and restart incomplete segments
    @Async
    public void resumeDownload(Long downloadId) {
        Download download = downloadRepo.findById(downloadId)
            .orElseThrow(() -> new IllegalArgumentException("Invalid download ID"));
        if (download.getStatus() != DownloadStatus.PAUSED) {
            throw new IllegalStateException("Download is not paused");
        }
        download.setStatus(DownloadStatus.DOWNLOADING);
        downloadRepo.save(download);

        AtomicBoolean flag = pauseFlags.get(downloadId);
        if (flag != null) {
            flag.set(false);
        } else {
            pauseFlags.put(downloadId, new AtomicBoolean(false));
        }
        // Relaunch threads for segments that are not completed
        List<SegmentInfo> segments = segmentRepo.findByDownloadId(downloadId);
        for (SegmentInfo seg : segments) {
            if (seg.getStatus() != SegmentStatus.COMPLETED) {
                seg.setStatus(SegmentStatus.DOWNLOADING);
                segmentRepo.save(seg);
                new Thread(() -> downloadSegment(download, seg)).start();
            }
        }
    }

    // Retry download: by default retry failed segments; if full=true, reset all segments
    @Async
    public void retryDownload(Long downloadId, boolean fullRestart) {
        Download download = downloadRepo.findById(downloadId)
            .orElseThrow(() -> new IllegalArgumentException("Invalid download ID"));
        download.setStatus(DownloadStatus.DOWNLOADING);
        downloadRepo.save(download);

        // Prepare for restart
        if (fullRestart) {
            // Reset file and segment offsets
            // Truncate the file to 0 length
            try (RandomAccessFile output = new RandomAccessFile(download.getFileName(), "rw")) {
                output.setLength(0);
            } catch (IOException e) {
                // handle error
            }
            // Reset all segments to start and pending
            List<SegmentInfo> segments = segmentRepo.findByDownloadId(downloadId);
            for (SegmentInfo seg : segments) {
                seg.setDownloadedBytes(0);
                seg.setStatus(SegmentStatus.DOWNLOADING);
                segmentRepo.save(seg);
                new Thread(() -> downloadSegment(download, seg)).start();
            }
        } else {
            // Retry only failed or paused segments
            List<SegmentInfo> segments = segmentRepo.findByDownloadId(downloadId);
            for (SegmentInfo seg : segments) {
                if (seg.getStatus() == SegmentStatus.FAILED || seg.getStatus() == SegmentStatus.PAUSED) {
                    seg.setStatus(SegmentStatus.DOWNLOADING);
                    segmentRepo.save(seg);
                    new Thread(() -> downloadSegment(download, seg)).start();
                }
            }
        }
    }

    // Check if all segments are completed; if so, mark download complete
    private synchronized void checkAndCompleteDownload(Download download) {
        List<SegmentInfo> segments = segmentRepo.findByDownloadId(download.getId());
        boolean allDone = segments.stream()
                            .allMatch(seg -> seg.getStatus() == SegmentStatus.COMPLETED);
        if (allDone) {
            download.setStatus(DownloadStatus.COMPLETED);
            download.setUpdatedAt(LocalDateTime.now());
            downloadRepo.save(download);
        }
    }

    // Get download status and progress
    public DownloadStatus getStatus(Long downloadId) {
        return downloadRepo.findById(downloadId)
                .map(Download::getStatus)
                .orElseThrow(() -> new IllegalArgumentException("Invalid download ID"));
    }

    public Download getDownload(Long downloadId) {
        return downloadRepo.findById(downloadId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid download ID"));
    }
}
