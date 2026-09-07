package com.lastsafe.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lastsafe.entity.BusVehiclePosition;

public interface BusVehiclePositionRepository extends JpaRepository<BusVehiclePosition, Long> {
}
