package com.lastsafe.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lastsafe.entity.BusNodePassage;

public interface BusNodePassageRepository extends JpaRepository<BusNodePassage, Long> {
}
