package com.kakarotabhi.idmclone.task;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DownloadTask {
    private String url;
    private String fileName;
    private String status;
    private long totalSize;
    private long downloadedSize;

    public DownloadTask() { }

    public DownloadTask(String url, String fileName) {
        this.url = url;
        this.fileName = fileName;
        this.status = "QUEUED";
        this.totalSize = 0;
        this.downloadedSize = 0;
    }

}
