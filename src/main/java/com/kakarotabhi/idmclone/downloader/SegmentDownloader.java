package com.kakarotabhi.idmclone.downloader;

import com.kakarotabhi.idmclone.task.SegmentInfo;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class SegmentDownloader implements Runnable {
    private final URL url;
    private final SegmentInfo segment;
    private final File targetFile;
    private final AtomicLong bytesDownloaded;
    private final AtomicBoolean errorFlag;
    private final CountDownLatch doneSignal;

    public SegmentDownloader(URL url, File targetFile, SegmentInfo segment, 
                             AtomicLong bytesDownloaded, AtomicBoolean errorFlag, 
                             CountDownLatch doneSignal) {
        this.url = url;
        this.targetFile = targetFile;
        this.segment = segment;
        this.bytesDownloaded = bytesDownloaded;
        this.errorFlag = errorFlag;
        this.doneSignal = doneSignal;
    }

    @Override
    public void run() {
        try {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Range", 
                                    "bytes=" + segment.getStart() + "-" + segment.getEnd());
            conn.setInstanceFollowRedirects(true);  // follow HTTP redirects
            conn.connect();
            // Expect 206 Partial Content for successful range request
            if (conn.getResponseCode() == HttpURLConnection.HTTP_PARTIAL) {
                try (InputStream in = conn.getInputStream();
                     RandomAccessFile raf = new RandomAccessFile(targetFile, "rw")) {
                    raf.seek(segment.getStart());
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        raf.write(buffer, 0, len);
                        bytesDownloaded.addAndGet(len);
                    }
                }
            } else {
                // If server doesn't honor range, mark error
                errorFlag.set(true);
            }
        } catch (Exception e) {
            // On any exception, flag an error (for manual retry)
            errorFlag.set(true);
        } finally {
            doneSignal.countDown(); // signal completion of this segment
        }
    }
}
