// src/main/java/com/lab/api/service/LabOrderRepository.java
package com.lab.api.service;

import com.lab.api.domain.LabOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LabOrderRepository extends JpaRepository<LabOrder, Long> {
    Optional<LabOrder> findBySampleIdAndTestType(String sampleId, String testType);
}