package com.lastsafe.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@link BusPollAttempt} 1건에서 관측된 차량 위치 원시 기록 (spec.md 5.3절) — 가공 없이
 * TAGO {@code getRouteAcctoBusLcList} 응답 그대로 보존한다. 통과 시각 추정(보간)은
 * {@code com.lastsafe.domain.bus.BusNodePassageEstimator}가 이 원시 기록들을 나중에
 * 배치로 가공해서 {@link BusNodePassage}를 만든다 — 원본을 지우지 않는 이유는 나중에 보간
 * 로직을 고치더라도 원시 데이터로 다시 계산할 수 있어야 하기 때문.
 */
@Entity
@Table(name = "bus_vehicle_position")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusVehiclePosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_attempt_id", nullable = false)
    private BusPollAttempt pollAttempt;

    @Column(nullable = false, length = 20)
    private String vehicleNo;

    @Column(nullable = false, length = 30)
    private String nodeId;

    @Column(nullable = false)
    private int nodeOrd;

    @Builder
    public BusVehiclePosition(String vehicleNo, String nodeId, int nodeOrd) {
        this.vehicleNo = vehicleNo;
        this.nodeId = nodeId;
        this.nodeOrd = nodeOrd;
    }

    void assignPollAttempt(BusPollAttempt pollAttempt) {
        this.pollAttempt = pollAttempt;
    }
}
