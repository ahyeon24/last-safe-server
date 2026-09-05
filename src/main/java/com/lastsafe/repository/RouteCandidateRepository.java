package com.lastsafe.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lastsafe.entity.RouteCandidate;

public interface RouteCandidateRepository extends JpaRepository<RouteCandidate, Long> {
}
