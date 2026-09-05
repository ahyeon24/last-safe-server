package com.lastsafe.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.lastsafe.entity.AlarmStatus;
import com.lastsafe.entity.DepartureAlarm;
import com.lastsafe.entity.User;

public interface DepartureAlarmRepository extends JpaRepository<DepartureAlarm, Long> {

    Page<DepartureAlarm> findAllByUser(User user, Pageable pageable);

    Page<DepartureAlarm> findAllByUserAndStatus(User user, AlarmStatus status, Pageable pageable);

    Optional<DepartureAlarm> findByIdAndUser(Long id, User user);
}
