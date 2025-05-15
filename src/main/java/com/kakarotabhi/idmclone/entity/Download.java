package com.kakarotabhi.idmclone.entity;

import com.kakarotabhi.idmclone.enums.DownloadStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "downloads")
@Getter
@Setter
public class Download {
    @Id @GeneratedValue
    private Long id;

    private String url;
    private String fileName;    // local path to save
    private long totalBytes;    // total size (optional, can set after HEAD request)

    @Enumerated(EnumType.STRING)
    private DownloadStatus status = DownloadStatus.PENDING;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // One-to-many relationship to segment info
    @OneToMany(mappedBy = "download", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<SegmentInfo> segments = new ArrayList<>();

}
