package com.lastsafe.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * TAGO 버스위치정보(spec.md 5.3절) 폴링 시도 1건. "버스가 안 옴"과 "우리 폴러가 죽음"을
 * 구분하기 위해 성공 여부(fetchSuccess)를 매 시도마다 명시적으로 남긴다 — 실패한 시도를
 * 조용히 버리면, 나중에 "그 시간대엔 관측이 없었다"는 게 정말 버스가 없었던 건지 폴링
 * 자체가 실패한 건지 구분이 안 된다.
 */
@Entity
@Table(name = "bus_poll_attempt")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusPollAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String cityCode;

    @Column(nullable = false, length = 30)
    private String routeId;

    @Column(nullable = false)
    private LocalDateTime collectedAt;

    @Column(nullable = false)
    private boolean fetchSuccess;

    @Column(nullable = false)
    private int vehicleCount;

    @OneToMany(mappedBy = "pollAttempt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BusVehiclePosition> vehiclePositions = new ArrayList<>();

    @Builder
    public BusPollAttempt(String cityCode, String routeId, LocalDateTime collectedAt,
                           boolean fetchSuccess, int vehicleCount) {
        this.cityCode = cityCode;
        this.routeId = routeId;
        this.collectedAt = collectedAt;
        this.fetchSuccess = fetchSuccess;
        this.vehicleCount = vehicleCount;
    }

    public void addVehiclePosition(BusVehiclePosition vehiclePosition) {
        vehiclePositions.add(vehiclePosition);
        vehiclePosition.assignPollAttempt(this);
    }
}
