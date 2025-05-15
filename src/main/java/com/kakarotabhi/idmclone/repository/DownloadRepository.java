package com.kakarotabhi.idmclone.repository;

import com.kakarotabhi.idmclone.entity.Download;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DownloadRepository extends JpaRepository<Download,
Long> {
// Custom query methods can be added here
}
