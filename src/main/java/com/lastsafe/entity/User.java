package com.lastsafe.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OauthProvider oauthProvider;

    @Column(nullable = false)
    private String oauthId;

    private String nickname;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime deletedAt;

    @Builder
    public User(OauthProvider oauthProvider, String oauthId, String nickname) {
        this.oauthProvider = oauthProvider;
        this.oauthId = oauthId;
        this.nickname = nickname;
        this.createdAt = LocalDateTime.now();
    }
}
