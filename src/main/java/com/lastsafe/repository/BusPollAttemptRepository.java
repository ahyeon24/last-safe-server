package com.lastsafe.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lastsafe.entity.BusPollAttempt;

public interface BusPollAttemptRepository extends JpaRepository<BusPollAttempt, Long> {
}
