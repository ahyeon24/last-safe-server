package com.lastsafe.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "segment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Segment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_candidate_id", nullable = false)
    private RouteCandidate routeCandidate;

    @Column(nullable = false)
    private int seqOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransportType transportType;

    private String routeName;

    private LocalDateTime lastTimeRaw;

    @Column(nullable = false)
    private boolean isEstimated;

    private Integer bufferSec;

    @Builder
    public Segment(int seqOrder, TransportType transportType, String routeName,
                    LocalDateTime lastTimeRaw, boolean isEstimated, Integer bufferSec) {
        this.seqOrder = seqOrder;
        this.transportType = transportType;
        this.routeName = routeName;
        this.lastTimeRaw = lastTimeRaw;
        this.isEstimated = isEstimated;
        this.bufferSec = bufferSec;
    }

    void assignRouteCandidate(RouteCandidate routeCandidate) {
        this.routeCandidate = routeCandidate;
    }
}
