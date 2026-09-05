package com.lastsafe.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lastsafe.entity.Segment;

public interface SegmentRepository extends JpaRepository<Segment, Long> {
}
