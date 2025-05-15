package com.kakarotabhi.idmclone.repository;

import com.kakarotabhi.idmclone.entity.SegmentInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SegmentInfoRepository extends JpaRepository<SegmentInfo, Long> {
    List<SegmentInfo> findByDownloadId(Long downloadId);
}
