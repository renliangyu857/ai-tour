package com.tourism.rag.repository;

import com.tourism.rag.entity.ItineraryRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItineraryRecordRepository extends JpaRepository<ItineraryRecord, String> {

    List<ItineraryRecord> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ItineraryRecord> findByCityCodeOrderByCreatedAtDesc(String cityCode);
}
