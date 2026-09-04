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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "departure_alarm")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DepartureAlarm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_candidate_id", nullable = false)
    private RouteCandidate routeCandidate;

    @Column(nullable = false)
    private double originLat;

    @Column(nullable = false)
    private double originLng;

    @Column(nullable = false)
    private double destLat;

    @Column(nullable = false)
    private double destLng;

    @Column(nullable = false)
    private LocalDateTime alarmTime;

    @Column(nullable = false)
    private int minutesBefore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlarmStatus status;

    @Builder
    public DepartureAlarm(User user, RouteCandidate routeCandidate,
                           double originLat, double originLng, double destLat, double destLng,
                           LocalDateTime alarmTime, int minutesBefore) {
        this.user = user;
        this.routeCandidate = routeCandidate;
        this.originLat = originLat;
        this.originLng = originLng;
        this.destLat = destLat;
        this.destLng = destLng;
        this.alarmTime = alarmTime;
        this.minutesBefore = minutesBefore;
        this.status = AlarmStatus.PENDING;
    }
}
