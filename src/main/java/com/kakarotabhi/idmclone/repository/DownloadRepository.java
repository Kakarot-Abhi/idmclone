package com.kakarotabhi.idmclone.repository;

import com.kakarotabhi.idmclone.entity.Download;
import com.kakarotabhi.idmclone.entity.SegmentInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DownloadRepository extends JpaRepository<Download, Long> { }

