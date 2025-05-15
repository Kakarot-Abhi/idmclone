package com.kakarotabhi.idmclone.entity;

import com.kakarotabhi.idmclone.enums.SegmentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class SegmentInfo {
    @Id
    @GeneratedValue
    private Long id;

    // Index 0..7 (for 8 segments)
    private int segmentIndex;
    private long startByte;       // inclusive
    private long endByte;         // inclusive
    private long downloadedBytes; // how many bytes have been downloaded so far

    @Enumerated(EnumType.STRING)
    private SegmentStatus status = SegmentStatus.PENDING;

    @ManyToOne
    @JoinColumn(name = "download_id")
    private Download download;

    // Getters and setters omitted for brevity
}