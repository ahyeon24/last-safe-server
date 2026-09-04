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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "route_candidate")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RouteCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int totalDurationSec;

    @Column(nullable = false)
    private int totalCost;

    @Column(nullable = false)
    private LocalDateTime minDepartureTime;

    @Column(nullable = false)
    private LocalDateTime minDepartureTimeRaw;

    @OneToMany(mappedBy = "routeCandidate", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seqOrder asc")
    private List<Segment> segments = new ArrayList<>();

    @Builder
    public RouteCandidate(int totalDurationSec, int totalCost,
                           LocalDateTime minDepartureTime, LocalDateTime minDepartureTimeRaw) {
        this.totalDurationSec = totalDurationSec;
        this.totalCost = totalCost;
        this.minDepartureTime = minDepartureTime;
        this.minDepartureTimeRaw = minDepartureTimeRaw;
    }

    public void addSegment(Segment segment) {
        segments.add(segment);
        segment.assignRouteCandidate(this);
    }
}
