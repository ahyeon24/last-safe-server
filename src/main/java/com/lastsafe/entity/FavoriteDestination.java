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

@Entity
@Table(name = "favorite_destination")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FavoriteDestination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Builder
    public FavoriteDestination(User user, String label, double latitude, double longitude) {
        this.user = user;
        this.label = label;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
