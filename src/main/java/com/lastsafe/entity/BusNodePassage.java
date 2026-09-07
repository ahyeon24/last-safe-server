package com.lastsafe.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관심 정류소를 특정 차량이 통과한 시각 추정 (spec.md 5.3절) —
 * {@code com.lastsafe.domain.bus.BusNodePassageEstimator}가 {@link BusVehiclePosition}
 * 원시 기록에서 배치로 뽑아낸다.
 *
 * "그날의 막차 1건"으로 뭉개지 않고 그날 관측된 모든 통과 기록을 이 테이블에 그대로
 * 남긴다 — 5.1절 {@code findLatestDepartureAtOrBefore}가 필요로 하는 건 "가장 늦은 것"이
 * 아니라 "deadline 이하 중 가장 늦은 것"이라, 여기서 미리 막차 1건으로 골라버리면 그
 * 쿼리를 만족시킬 수 없다.
 */
@Entity
@Table(name = "bus_node_passage")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusNodePassage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 자정을 넘겨도 원래 운행일(서비스 데이) 기준 — 달력 날짜 그대로 쓰면 안 됨(4절 참고). */
    @Column(nullable = false)
    private LocalDate serviceDate;

    @Column(nullable = false, length = 30)
    private String routeId;

    @Column(nullable = false, length = 30)
    private String nodeId;

    @Column(nullable = false, length = 20)
    private String vehicleNo;

    /**
     * 정확히 관측됐으면(isObserved=true) lower==upper. 두 관측 사이로 보간했으면
     * (isObserved=false) 직전 관측 시각이 lower, 직후 관측 시각이 upper.
     */
    @Column(nullable = false)
    private LocalDateTime passageTimeLower;

    @Column(nullable = false)
    private LocalDateTime passageTimeUpper;

    @Column(nullable = false)
    private boolean isObserved;

    @Builder
    public BusNodePassage(LocalDate serviceDate, String routeId, String nodeId, String vehicleNo,
                           LocalDateTime passageTimeLower, LocalDateTime passageTimeUpper, boolean isObserved) {
        this.serviceDate = serviceDate;
        this.routeId = routeId;
        this.nodeId = nodeId;
        this.vehicleNo = vehicleNo;
        this.passageTimeLower = passageTimeLower;
        this.passageTimeUpper = passageTimeUpper;
        this.isObserved = isObserved;
    }
}
