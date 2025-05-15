package com.kakarotabhi.idmclone.task;

import lombok.Getter;

@Getter
public class SegmentInfo {
    private final int id;
    private final long start;
    private final long end;

    public SegmentInfo(int id, long start, long end) {
        this.id = id;
        this.start = start;
        this.end = end;
    }
}
